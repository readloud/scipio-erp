/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/
package org.ofbiz.service.eca;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import org.ofbiz.base.component.ComponentConfig;
import org.ofbiz.base.concurrent.ExecutionPool;
import org.ofbiz.base.config.GenericConfigException;
import org.ofbiz.base.config.MainResourceHandler;
import org.ofbiz.base.config.ResourceHandler;
import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.base.util.UtilXml;
import org.ofbiz.service.DispatchContext;
import org.ofbiz.service.GenericServiceException;
import org.ofbiz.service.config.ServiceConfigUtil;
import org.ofbiz.service.config.model.ServiceEcas;
import org.w3c.dom.Element;

/**
 * ServiceEcaUtil
 */
public final class ServiceEcaUtil {

    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());

    // using a cache is dangerous here because if someone clears it the ECAs won't run: public static UtilCache ecaCache = new UtilCache("service.ServiceECAs", 0, 0, false);
    // SCIPIO: use immutable volatile cache instead, plus dedicated lock object (required since ecaCache will change)
    //private static Map<String, Map<String, List<ServiceEcaRule>>> ecaCache = new ConcurrentHashMap<String, Map<String, List<ServiceEcaRule>>>();
    private static volatile Map<String, Map<String, List<ServiceEcaRule>>> ecaCache = Collections.emptyMap();
    private static final Object ecaCacheLock = new Object();

    private ServiceEcaUtil() {}

    // SCIPIO: NOTE: 2018-09-06: STOCK OFBIZ BUGFIX:
    // The write methods reloadConfig(), interior readConfig(), and public addEcaDefinitions
    // MUST write-lock on a common lock;
    // a race could previously occur between threads from different ofbiz containers (parallel to catalina) where
    // two threads managed to pass the "if (UtilValidate.isNotEmpty(ecaCache)) {" check in readConfig at the same time,
    // which happened easily because reading all the SECA definitions from all XML files is slow with many components.
    // IN ADDITION, instead of populating and clearing a single instance, we now re-create and copy the cache
    // so that readers get an atomic view and can't read a partial view of the ECAs while they're loading.

    public static void reloadConfig() {
        synchronized(ecaCacheLock) { // SCIPIO: write-lock
            //ecaCache.clear();
            //readConfig();
            reloadConfigInternal();
        }
    }

    public static void readConfig() {
        // Only proceed if the cache hasn't already been populated, caller should be using reloadConfig() in that situation
        if (!ecaCache.isEmpty()) { // SCIPIO: changed from UtilValidate.isNotEmpty
            return;
        }
        synchronized(ecaCacheLock) { // SCIPIO: write-lock
            if (!ecaCache.isEmpty()) {
                return;
            }
            reloadConfigInternal();
        }
    }

    private static void reloadConfigInternal() { // SCIPIO: refactored for write-lock
        List<Future<List<ServiceEcaRule>>> futures = new ArrayList<>(); // SCIPIO: switched to ArrayList
        List<ServiceEcas> serviceEcasList = null;
        try {
            serviceEcasList = ServiceConfigUtil.getServiceEngine().getServiceEcas();
        } catch (GenericConfigException e) {
            // FIXME: Refactor API so exceptions can be thrown and caught.
            Debug.logError(e, module);
            throw new RuntimeException(e.getMessage());
        }
        for (ServiceEcas serviceEcas : serviceEcasList) {
            ResourceHandler handler = new MainResourceHandler(ServiceConfigUtil.getServiceEngineXmlFileName(), serviceEcas.getLoader(), serviceEcas.getLocation());
            futures.add(ExecutionPool.GLOBAL_FORK_JOIN.submit(createEcaLoaderCallable(handler)));
        }

        // get all of the component resource eca stuff, ie specified in each ofbiz-component.xml file
        for (ComponentConfig.ServiceResourceInfo componentResourceInfo: ComponentConfig.getAllServiceResourceInfos("eca")) {
            futures.add(ExecutionPool.GLOBAL_FORK_JOIN.submit(createEcaLoaderCallable(componentResourceInfo.createResourceHandler())));
        }

        Map<String, Map<String, List<ServiceEcaRule>>> ecaCache = new HashMap<>(); // SCIPIO: new cache, for consistent view for reads
        for (List<ServiceEcaRule> handlerRules: ExecutionPool.getAllFutures(futures)) {
            mergeEcaDefinitions(handlerRules, ecaCache);
        }
        ServiceEcaUtil.ecaCache = ecaCache; // SCIPIO: Wrapper not necessary as long as HashMap not modified after assign to volatile: Collections.unmodifiableMap(ecaCache);
    }

    private static Callable<List<ServiceEcaRule>> createEcaLoaderCallable(final ResourceHandler handler) {
        return new Callable<List<ServiceEcaRule>>() {
            public List<ServiceEcaRule> call() throws Exception {
                return getEcaDefinitions(handler);
            }
        };
    }

    public static void addEcaDefinitions(ResourceHandler handler) {
        synchronized(ecaCacheLock) { // SCIPIO: write-lock, because this method is public
            List<ServiceEcaRule> handlerRules = getEcaDefinitions(handler);
            Map<String, Map<String, List<ServiceEcaRule>>> ecaCache = new HashMap<>(ServiceEcaUtil.ecaCache); // SCIPIO: clone whole cache, for consistent view for reads
            mergeEcaDefinitions(handlerRules, ecaCache);
            ServiceEcaUtil.ecaCache = ecaCache; // SCIPIO: Wrapper not necessary as long as HashMap not modified after assign to volatile: Collections.unmodifiableMap(ecaCache);
        }
    }

    private static List<ServiceEcaRule> getEcaDefinitions(ResourceHandler handler) {
        List<ServiceEcaRule> handlerRules = new ArrayList<>(); // SCIPIO: switched to ArrayList
        Element rootElement = null;
        try {
            rootElement = handler.getDocument().getDocumentElement();
        } catch (GenericConfigException e) {
            Debug.logError(e, module);
            return handlerRules;
        }

        String resourceLocation = handler.getLocation();
        try {
            resourceLocation = handler.getURL().toExternalForm();
        } catch (GenericConfigException e) {
            Debug.logError(e, "Could not get resource URL", module);
        }
        for (Element e: UtilXml.childElementList(rootElement, "eca")) {
            handlerRules.add(new ServiceEcaRule(e, resourceLocation));
        }
        if (Debug.infoOn()) {
            Debug.logInfo("Loaded [" + handlerRules.size() + "] Service ECA Rules from " + resourceLocation, module);
        }
        return handlerRules;
    }

    // SCIPIO: modified to take a ecaCache as parameter
    private static void mergeEcaDefinitions(List<ServiceEcaRule> handlerRules, Map<String, Map<String, List<ServiceEcaRule>>> ecaCache) {
        for (ServiceEcaRule rule: handlerRules) {
            String serviceName = rule.getServiceName();
            String eventName = rule.getEventName();
            Map<String, List<ServiceEcaRule>> eventMap = ecaCache.get(serviceName);
            List<ServiceEcaRule> rules = null;

            if (eventMap == null) {
                eventMap = new HashMap<String, List<ServiceEcaRule>>();
                rules = new ArrayList<>(); // SCIPIO: switched to ArrayList
                ecaCache.put(serviceName, eventMap);
                eventMap.put(eventName, rules);
            } else {
                rules = eventMap.get(eventName);
                if (rules == null) {
                    rules = new ArrayList<>(); // SCIPIO: switched to ArrayList
                    eventMap.put(eventName, rules);
                }
            }
            //remove the old rule if found and keep the recent one
            //This will prevent duplicate rule execution along with enabled/disabled seca workflow
            // SCIPIO: 2018-09-04: Reworked to give better log information
            //if (rules.remove(rule)) {
            //    Debug.logWarning("Duplicate Service ECA [" + serviceName + "] on [" + eventName + "] ", module);
            //}
            int ruleIndex = rules.indexOf(rule);
            if (ruleIndex >= 0) {
                ServiceEcaRule prevRule = rules.get(ruleIndex);
                rules.remove(prevRule);
                if (prevRule.getDefinitionLocation() != null && prevRule.getDefinitionLocation().equals(rule.getDefinitionLocation())) {
                    Debug.logWarning("Duplicate Service ECA [" + serviceName + "] on [" + eventName
                            + "] both from definition [" + prevRule.getDefinitionLocation() + "]", module);
                } else {
                    Debug.logWarning("Duplicate Service ECA [" + serviceName + "] on [" + eventName
                            + "] from definition [" + prevRule.getDefinitionLocation() + "] and overriding definition [" + rule.getDefinitionLocation() + "]", module);
                }
            }
            rules.add(rule);
        }
    }

    public static Map<String, List<ServiceEcaRule>> getServiceEventMap(String serviceName) {
        // SCIPIO: 2018-09-06: the cache is never null
        //if (ServiceEcaUtil.ecaCache == null) ServiceEcaUtil.readConfig();
        ServiceEcaUtil.readConfig();
        return ServiceEcaUtil.ecaCache.get(serviceName);
    }

    public static List<ServiceEcaRule> getServiceEventRules(String serviceName, String event) {
        Map<String, List<ServiceEcaRule>> eventMap = getServiceEventMap(serviceName);
        if (eventMap != null) {
            if (event != null) {
                return eventMap.get(event);
            } else {
                List<ServiceEcaRule> rules = new ArrayList<>(); // SCIPIO: switched to ArrayList
                for (Collection<ServiceEcaRule> col: eventMap.values()) {
                    rules.addAll(col);
                }
                return rules;
            }
        }
        return null;
    }

    public static void evalRules(String serviceName, Map<String, List<ServiceEcaRule>> eventMap, String event, DispatchContext dctx, Map<String, Object> context, Map<String, Object> result, boolean isError, boolean isFailure) throws GenericServiceException {
        // if the eventMap is passed we save a Map lookup, but if not that's okay we'll just look it up now
        if (eventMap == null) eventMap = getServiceEventMap(serviceName);
        if (UtilValidate.isEmpty(eventMap)) {
            return;
        }

        Collection<ServiceEcaRule> rules = eventMap.get(event);
        if (UtilValidate.isEmpty(rules)) {
            return;
        }

        if (Debug.verboseOn()) Debug.logVerbose("Running ECA (" + event + ").", module);
        Set<String> actionsRun = new TreeSet<String>();
        for (ServiceEcaRule eca: rules) {
            eca.eval(serviceName, dctx, context, result, isError, isFailure, actionsRun);
        }
    }
}

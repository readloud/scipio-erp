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
package org.ofbiz.entity.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.InvalidPropertiesFormatException;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;

import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilProperties;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.base.util.collections.ResourceBundleMapWrapper;
import org.ofbiz.entity.Delegator;
import org.ofbiz.entity.DelegatorFactory;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.condition.EntityCondition;
import org.ofbiz.entity.condition.EntityOperator;

@SuppressWarnings("serial")
public final class EntityUtilProperties implements Serializable {

    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());

    private EntityUtilProperties () {}

    /**
     * SCIPIO: Returns the value for the given SystemProperty, or null if missing.
     * If the SystemProperty exists, the result even if no value is an empty string;
     * if it does not exist, the result is null.
     * Added 2018-07-27.
     */
    public static String getSystemPropertyValueOrNull(String resource, String name, Delegator delegator) {
        Optional<String> propMap = getSystemPropertyValue(resource, name, delegator); // SCIPIO: Optional
        return propMap.isPresent() ? propMap.get() : null;
    }

    /**
     * Gets the given SystemPropertyValue, or an empty Optional if does not exist or interpreted
     * to not exist.
     * <p>
     * SCIPIO: 2018-08-17: modified to use Optional instead of inappropriate Map.
     */
    private static Optional<String> getSystemPropertyValue(String resource, String name, Delegator delegator) {
        Optional<String> results = Optional.empty(); // SCIPIO: Optional

        if (UtilValidate.isEmpty(resource) || UtilValidate.isEmpty(name)) {
            return results;
        }

        if (delegator == null) { // SCIPIO: 2019-01: Although should rarely happen, there is no reason to crash here
            Debug.logWarning("Missing delegator when querying for entity property [" + resource + "#" + name
                    + "]; treating as not set in database", module);
            return results;
        }

        // SCIPIO: Bad, only replace at end of string
        //resource = resource.replace(".properties", "");
        if (resource.endsWith(".properties")) {
            resource = resource.substring(0, resource.length() - ".properties".length());
        }
        try {
            // SCIPIO: Support for resource name aliases
            EntityCondition resourceAliasCond = ResourceNameAliases.resourceNameAliasConditionMap.get(resource);
            GenericValue systemProperty;
            if (resourceAliasCond != null) {
                systemProperty = EntityQuery.use(delegator)
                        .from("SystemProperty")
                        .where(EntityCondition.makeCondition(resourceAliasCond,
                                EntityOperator.AND,
                                EntityCondition.makeCondition("systemPropertyId", name)))
                        .cache()
                        .queryFirst();
            } else {
                systemProperty = EntityQuery.use(delegator)
                        .from("SystemProperty")
                        .where("systemResourceId", resource, "systemPropertyId", name)
                        .cache()
                        .queryOne();
            }
            if (systemProperty != null) {
                //property exists in database

                // SCIPIO: 2018-07-27: new useEmpty explicit flag
                // NOTE: The default for useEmpty in Scipio is N, while the logical ofbiz 16+ default
                // of this method is Y, so we effectively invert the logic.
                //results.put("isExistInDb", "Y");
                //results.put("value", (systemProperty.getString("systemPropertyValue") != null) ? systemProperty.getString("systemPropertyValue") : "");

                String value = systemProperty.getString("systemPropertyValue");
                if (value == null) value = "";
                if (value.isEmpty() && !Boolean.TRUE.equals(systemProperty.getBoolean("useEmpty"))) {
                    // keep isExistInDb "N" and value "" (above)
                } else {
                    results = Optional.of(value);
                }
            }
        } catch (GenericEntityException e) {
            Debug.logError("Could not get a system property for " + name + " : " + e.getMessage(), module);
        }
        return results;
    }

    public static boolean propertyValueEquals(String resource, String name, String compareString) {
        return UtilProperties.propertyValueEquals(resource, name, compareString);
    }

    public static boolean propertyValueEqualsIgnoreCase(String resource, String name, String compareString, Delegator delegator) {
        Optional<String> propMap = getSystemPropertyValue(resource, name, delegator); // SCIPIO: Optional
        if (propMap.isPresent()) {
            compareString = (compareString == null) ? "" : compareString;
            return propMap.get().equalsIgnoreCase(compareString);
        } else {
            return UtilProperties.propertyValueEqualsIgnoreCase(resource, name, compareString);
        }
    }

    public static String getPropertyValue(String resource, String name, String defaultValue, Delegator delegator) {
        Optional<String> propMap = getSystemPropertyValue(resource, name, delegator); // SCIPIO: Optional
        if (propMap.isPresent()) {
            String s = propMap.get();
            return (UtilValidate.isEmpty(s)) ? defaultValue : s;
        } else {
            return UtilProperties.getPropertyValue(resource, name, defaultValue);
        }
    }

    public static String getPropertyValueFromDelegatorName(String resource, String name, String defaultValue, String delegatorName) {
        Delegator delegator = DelegatorFactory.getDelegator(delegatorName);
        if (delegator == null) { // This should not happen, but in case...
            Debug.logError("Could not get a delegator. Using the 'default' delegator", module);
            // this will be the common case for now as the delegator isn't available where we want to do this
            // we'll cheat a little here and assume the default delegator
            delegator = DelegatorFactory.getDelegator("default");
            Debug.logError("Could not get a delegator. Using the 'default' delegator", module);
            if (delegator == null) {
                Debug.logError("Could not get a system property for " + name + ". Reason: the delegator is null", module);
            }
        }
        Optional<String> propMap = getSystemPropertyValue(resource, name, delegator); // SCIPIO: Optional
        if (propMap.isPresent()) {
            String s = propMap.get();
            return (UtilValidate.isEmpty(s)) ? defaultValue : s;
        } else {
            return UtilProperties.getPropertyValue(resource, name, defaultValue);
        }
    }


    /**
     * getPropertyNumber, as double.
     * <p>
     * SCIPIO: <strong>WARN:</strong> This method is inconsistent; you should use {@link #getPropertyAsDouble(String, String, double, Delegator)} instead.
     */
    public static double getPropertyNumber(String resource, String name, double defaultValue, Delegator delegator) { // SCIPIO: added 2018-09-26
        String str = getPropertyValue(resource, name, delegator);
        if (UtilValidate.isEmpty(str)) { // SCIPIO: 2018-09-26: don't try/warn if empty
            return defaultValue;
        }
        try {
            return Double.parseDouble(str);
        } catch (NumberFormatException nfe) {
            Debug.logWarning("Error converting String \"" + str + "\" to double; using defaultNumber: " + defaultValue + ".", module); // SCIPIO: 2018-09-26: don't swallow
            return defaultValue;
        }
    }

    /**
     * getPropertyNumber, as double.
     * <p>
     * SCIPIO: <strong>WARN:</strong> This method is inconsistent; you should use {@link #getPropertyAsDouble(String, String, double)} instead.
     */
    public static double getPropertyNumber(String resource, String name, double defaultValue) {
        return UtilProperties.getPropertyNumber(resource, name, defaultValue);
    }

    /**
     * getPropertyNumber, as double, with default value 0.00000.
     * <p>
     * SCIPIO: <strong>WARN:</strong> This method is inconsistent; you should use {@link #getPropertyAsDouble(String, String, double)} instead.
     */
    public static double getPropertyNumber(String resource, String name) {
        return UtilProperties.getPropertyNumber(resource, name);
    }

    public static Boolean getPropertyAsBoolean(String resource, String name, boolean defaultValue, Delegator delegator) { // SCIPIO: added 2018-09-26
        return UtilProperties.asBoolean(getPropertyValue(resource, name, delegator), defaultValue);
    }

    public static Boolean getPropertyAsBoolean(String resource, String name, boolean defaultValue) {
        return UtilProperties.getPropertyAsBoolean(resource, name, defaultValue);
    }

    public static Integer getPropertyAsInteger(String resource, String name, int defaultNumber, Delegator delegator) { // SCIPIO: added 2018-09-26
        return UtilProperties.asInteger(getPropertyValue(resource, name, delegator), defaultNumber);
    }

    public static Integer getPropertyAsInteger(String resource, String name, int defaultNumber) {
        return UtilProperties.getPropertyAsInteger(resource, name, defaultNumber);
    }

    public static Long getPropertyAsLong(String resource, String name, long defaultNumber, Delegator delegator) { // SCIPIO: added 2018-09-26
        return UtilProperties.asLong(getPropertyValue(resource, name, delegator), defaultNumber);
    }

    public static Long getPropertyAsLong(String resource, String name, long defaultNumber) {
        return UtilProperties.getPropertyAsLong(resource, name, defaultNumber);
    }

    public static Float getPropertyAsFloat(String resource, String name, float defaultNumber, Delegator delegator) { // SCIPIO: added 2018-09-26
        return UtilProperties.asFloat(getPropertyValue(resource, name, delegator), defaultNumber);
    }

    public static Float getPropertyAsFloat(String resource, String name, float defaultNumber) {
        return UtilProperties.getPropertyAsFloat(resource, name, defaultNumber);
    }

    public static Double getPropertyAsDouble(String resource, String name, double defaultNumber, Delegator delegator) { // SCIPIO: added 2018-09-26
        return UtilProperties.asDouble(getPropertyValue(resource, name, delegator), defaultNumber);
    }

    public static Double getPropertyAsDouble(String resource, String name, double defaultNumber) {
        return UtilProperties.getPropertyAsDouble(resource, name, defaultNumber);
    }

    public static BigInteger getPropertyAsBigInteger(String resource, String name, BigInteger defaultNumber, Delegator delegator) { // SCIPIO: added 2018-09-26
        return UtilProperties.asBigInteger(getPropertyValue(resource, name, delegator), defaultNumber);
    }

    public static BigInteger getPropertyAsBigInteger(String resource, String name, BigInteger defaultNumber) {
        return UtilProperties.getPropertyAsBigInteger(resource, name, defaultNumber);
    }

    public static BigDecimal getPropertyAsBigDecimal(String resource, String name, BigDecimal defaultNumber, Delegator delegator) { // SCIPIO: added 2018-09-26
        return UtilProperties.asBigDecimal(getPropertyValue(resource, name, delegator), defaultNumber);
    }

    public static BigDecimal getPropertyAsBigDecimal(String resource, String name, BigDecimal defaultNumber) {
        return UtilProperties.getPropertyAsBigDecimal(resource, name, defaultNumber);
    }

    public static String getPropertyValue(String resource, String name, Delegator delegator) {
        Optional<String> propMap = getSystemPropertyValue(resource, name, delegator); // SCIPIO: Optional
        if (propMap.isPresent()) {
            return propMap.get();
        } else {
            return UtilProperties.getPropertyValue(resource, name);
        }
    }

    public static String getPropertyValueFromDelegatorName(String resource, String name, String delegatorName) {
        Delegator delegator = DelegatorFactory.getDelegator(delegatorName);
        if (delegator == null) { // This should not happen, but in case...
            Debug.logError("Could not get a delegator. Using the 'default' delegator", module);
            // this will be the common case for now as the delegator isn't available where we want to do this
            // we'll cheat a little here and assume the default delegator
            delegator = DelegatorFactory.getDelegator("default");
            Debug.logError("Could not get a delegator. Using the 'default' delegator", module);
            if (delegator == null) {
                Debug.logError("Could not get a system property for " + name + ". Reason: the delegator is null", module);
            }
        }
        Optional<String> propMap = getSystemPropertyValue(resource, name, delegator); // SCIPIO: Optional
        if (propMap.isPresent()) {
            return propMap.get();
        } else {
            return UtilProperties.getPropertyValue(resource, name);
        }
    }

    public static Properties getProperties(String resource) {
        return UtilProperties.getProperties(resource);
    }

    public static Properties getProperties(URL url) {
        return UtilProperties.getProperties(url);
    }

    public static Properties getProperties(Delegator delegator, String resourceName) {
        Properties properties = UtilProperties.getProperties(resourceName);
        List<GenericValue> gvList;
        try {
            gvList = EntityQuery.use(delegator)
                    .from("SystemProperty")
                    .where("systemResourceId", resourceName)
                    .queryList();
            if (UtilValidate.isNotEmpty(gvList)) {
                for (Iterator<GenericValue> i = gvList.iterator(); i.hasNext();) {
                    GenericValue gv = i.next();
                    if (UtilValidate.isNotEmpty(gv.getString("systemPropertyValue"))) {
                        properties.setProperty(gv.getString("systemPropertyId"), gv.getString("systemPropertyValue"));
                    }
                }
            }
        } catch (GenericEntityException e) {
            Debug.logError(e.getMessage(), module);
        }
        return properties;
    }

    public static boolean propertyValueEquals(URL url, String name, String compareString) {
        return UtilProperties.propertyValueEquals(url, name, compareString);
    }

    public static boolean propertyValueEqualsIgnoreCase(URL url, String name, String compareString) {
        return UtilProperties.propertyValueEqualsIgnoreCase(url, name, compareString);
    }

    public static String getPropertyValue(URL url, String name, String defaultValue) {
        return UtilProperties.getPropertyValue(url, name, defaultValue);
    }

    public static double getPropertyNumber(URL url, String name, double defaultValue) {
        return UtilProperties.getPropertyNumber(url, name, defaultValue);
    }

    public static double getPropertyNumber(URL url, String name) {
        return UtilProperties.getPropertyNumber(url, name);
    }

    public static String getPropertyValue(URL url, String name) {
        return UtilProperties.getPropertyValue(url, name);
    }

    public static String getSplitPropertyValue(URL url, String name) {
        return UtilProperties.getSplitPropertyValue(url, name);
    }

     public static void setPropertyValue(String resource, String name, String value) {
         UtilProperties.setPropertyValue(resource, name, value);
     }

      public static void setPropertyValueInMemory(String resource, String name, String value) {
          UtilProperties.setPropertyValueInMemory(resource, name, value);
      }

    public static String setPropertyValue(Delegator delegator, String resourceName, String name, String value) {
        GenericValue gv = null;
        String prevValue = null;
        try {
            gv = EntityQuery.use(delegator)
                    .from("SystemProperty")
                    .where("systemResourceId", resourceName, "systemPropertyId", name)
                    .queryOne();
            if (gv != null) {
                prevValue = gv.getString("systemPropertyValue");
                gv.set("systemPropertyValue", value);
            } else {
                gv = delegator.makeValue("SystemProperty", UtilMisc.toMap("systemResourceId", resourceName, "systemPropertyId", name, "systemPropertyValue", value, "description", null));
            }
            gv.store();
        } catch (GenericEntityException e) {
            Debug.logError(String.format("tenantId=%s, exception=%s, message=%s", delegator.getDelegatorTenantId(), e.getClass().getName(), e.getMessage()), module);
        }
        return prevValue;
    }

    public static String getMessage(String resource, String name, Locale locale, Delegator delegator) {
        Optional<String> propMap = getSystemPropertyValue(resource, name, delegator); // SCIPIO: Optional
        if (propMap.isPresent()) {
            return propMap.get();
        } else {
            return UtilProperties.getMessage(resource, name, locale);
        }
    }

    public static String getMessage(String resource, String name, Object[] arguments, Locale locale) {
        return UtilProperties.getMessage(resource, name, arguments, locale);
    }

    public static <E> String getMessage(String resource, String name, List<E> arguments, Locale locale) {
        return UtilProperties.getMessage(resource, name, arguments, locale);
    }

    public static String getMessageList(String resource, String name, Locale locale, Object... arguments) {
        return UtilProperties.getMessageList(resource, name, locale, arguments);
    }

    public static String getMessage(String resource, String name, Map<String, ? extends Object> context, Locale locale) {
        return UtilProperties.getMessage(resource, name, context, locale);
    }

    public static String getMessageMap(String resource, String name, Locale locale, Object... context) {
        return UtilProperties.getMessageMap(resource, name, locale, context);
    }

    public static ResourceBundle getResourceBundle(String resource, Locale locale) {
        return UtilProperties.getResourceBundle(resource, locale);
    }

    public static ResourceBundleMapWrapper getResourceBundleMap(String resource, Locale locale) {
        return UtilProperties.getResourceBundleMap(resource, locale);
    }

    public static ResourceBundleMapWrapper getResourceBundleMap(String resource, Locale locale, Map<String, Object> context) {
        return UtilProperties.getResourceBundleMap(resource, locale, context);
    }

    public static Properties getProperties(String resource, Locale locale) {
        return UtilProperties.getProperties(resource, locale);
    }

    @Deprecated
    public static Locale getFallbackLocale() {
        return UtilProperties.getFallbackLocale();
    }

    public static List<Locale> localeToCandidateList(Locale locale) {
        return UtilProperties.localeToCandidateList(locale);
    }

    public static Set<Locale> getDefaultCandidateLocales() {
        return UtilProperties.getDefaultCandidateLocales();
    }

    @Deprecated
    public static List<Locale> getCandidateLocales(Locale locale) {
        return UtilProperties.getCandidateLocales(locale);
    }

    public static String createResourceName(String resource, Locale locale, boolean removeExtension) {
        return UtilProperties.createResourceName(resource, locale, removeExtension);
    }

    public static boolean isPropertiesResourceNotFound(String resource, Locale locale, boolean removeExtension) {
        return UtilProperties.isPropertiesResourceNotFound(resource, locale, removeExtension);
    }

    public static URL resolvePropertiesUrl(String resource, Locale locale) {
        return UtilProperties.resolvePropertiesUrl(resource, locale);
    }

    public static Properties xmlToProperties(InputStream in, Locale locale, Properties properties) throws IOException, InvalidPropertiesFormatException {
        return UtilProperties.xmlToProperties(in, locale, properties);
    }

    /**
     * SCIPIO: Resource name alias support core handling.
     * <p>
     * Added 2018-10-02.
     */
    private static class ResourceNameAliases {
        static final Map<String, EntityCondition> resourceNameAliasConditionMap = readResourceNameAliasConditionMap();

        /**
         * SCIPIO: Pre-builds lookup conditions for resource name aliases
         * (this is the best can optimize this without adding an extra cache layer).
         */
        static Map<String, EntityCondition> readResourceNameAliasConditionMap() {
            Map<String, EntityCondition> condMap = new HashMap<>();
            for(Map.Entry<String, List<String>> entry : UtilProperties.getResourceNameAliasAndReverseAliasMap().entrySet()) {
                List<String> aliases = entry.getValue();
                List<EntityCondition> condList = new ArrayList<>(aliases.size() + 1);
                condList.add(EntityCondition.makeCondition("systemResourceId", entry.getKey()));
                for(String alias : aliases) {
                    condList.add(EntityCondition.makeCondition("systemResourceId", alias));
                }
                condMap.put(entry.getKey(), EntityCondition.makeCondition(condList, EntityOperator.OR));
            }
            return condMap;
        }
    }
}

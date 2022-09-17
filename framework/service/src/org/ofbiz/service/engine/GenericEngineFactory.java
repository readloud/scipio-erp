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
package org.ofbiz.service.engine;

import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.ofbiz.base.config.GenericConfigException;
import org.ofbiz.base.util.UtilGenerics;
import org.ofbiz.service.GenericServiceException;
import org.ofbiz.service.ServiceDispatcher;
import org.ofbiz.service.config.ServiceConfigUtil;

/**
 * Generic Engine Factory
 */
public class GenericEngineFactory {

    protected final ServiceDispatcher dispatcher; // SCIPIO: 2018-10-16: final
    protected Map<String, GenericEngine> engines;

    public GenericEngineFactory(ServiceDispatcher dispatcher) {
        this.dispatcher = dispatcher;
        // SCIPIO: 2018-10-16: use unmodifiable for read thread safety
        //engines = new HashMap<>();
        engines = Collections.emptyMap();
    }

    /**
     * Gets the GenericEngine instance that corresponds to given the name
     *@param engineName Name of the engine
     *@return GenericEngine that corresponds to the engineName
     */
    public GenericEngine getGenericEngine(String engineName) throws GenericServiceException {
        // SCIPIO: 2018-10-16: this is absurd, only need to do this below...
        //String className = null;
        //try {
        //    className = ServiceConfigUtil.getServiceEngine().getEngine(engineName).getClassName();
        //} catch (GenericConfigException e) {
        //    throw new GenericServiceException(e);
        //}

        GenericEngine engine = engines.get(engineName);
        if (engine == null) {
            synchronized (GenericEngineFactory.class) {
                engine = engines.get(engineName);
                if (engine == null) {
                    // SCIPIO: 2018-10-16: className read moved here from above
                    String className = null;
                    try {
                        className = ServiceConfigUtil.getServiceEngine().getEngine(engineName).getClassName();
                    } catch (GenericConfigException e) {
                        throw new GenericServiceException(e);
                    }
                    try {
                        ClassLoader loader = Thread.currentThread().getContextClassLoader();
                        Class<?> c = loader.loadClass(className);
                        Constructor<GenericEngine> cn = UtilGenerics.cast(c.getConstructor(ServiceDispatcher.class));
                        engine = cn.newInstance(dispatcher);
                    } catch (Exception e) {
                        throw new GenericServiceException(e.getMessage(), e);
                    }
                    // SCIPIO: 2018-10-16: this is not thread-safe for reads; make map copy and unmodifiable
                    //if (engine != null) {
                    //    engines.put(engineName, engine);
                    //}
                    Map<String, GenericEngine> newEngines = new HashMap<>(this.engines);
                    newEngines.put(engineName, engine);
                    this.engines = Collections.unmodifiableMap(newEngines);
                }
            }
        }

        return engine;
    }
}


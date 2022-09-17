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
package org.ofbiz.service.engine

import java.util.Map

import javax.servlet.http.HttpServletRequest

import groovy.lang.MissingPropertyException

import org.ofbiz.base.util.Debug
import org.ofbiz.entity.util.EntityQuery
import org.ofbiz.service.DispatchContext
import org.ofbiz.service.LocalDispatcher
import org.ofbiz.service.ModelService
import org.ofbiz.service.ServiceUtil
import org.ofbiz.service.ExecutionServiceException
import org.ofbiz.entity.GenericValue

abstract class GroovyBaseScript extends Script {
    public static final String module = GroovyBaseScript.class.getName()

    /**
     * SCIPIO: Returns the binding variable with given name, or null if does not exist.
     */
    def getBindingVarSafe(String varName) {
        try {
            return this.binding.getVariable(varName);
        } catch(MissingPropertyException e) {
            return null;
        }
    }

    Map runService(String serviceName, Map serviceCtx) throws ExecutionServiceException {
        LocalDispatcher dispatcher = binding.getVariable('dispatcher');
        DispatchContext dctx = dispatcher.getDispatchContext();
        /* SCIPIO: 2019-01-31: security: These were flawed and potentially dangerous due to
         * 'parameters' map potentially containing request parameters; also flawed presence checks.
        if (!serviceCtx.userLogin) {
            serviceCtx.userLogin = this.binding.getVariable('parameters').userLogin
        }
        if (!serviceCtx.timeZone) {
            serviceCtx.timeZone = this.binding.getVariable('parameters').timeZone
        }
        if (!serviceCtx.locale) {
            serviceCtx.locale = this.binding.getVariable('parameters').locale
        }
        */
        // SCIPIO: NOTE: We ONLY use the request/session for default fields (userLogin, locale, timeZone) 
        // as backward-compatibility IF their keys are not set in current context, as the renderer should have set them.
        ServiceUtil.checkSetServiceContextDefaults(serviceCtx, ModelService.COMMON_INTERNAL_IN_FIELDS,
            getBindingVarSafe('context'), getBindingVarSafe('request'))
        Map serviceContext = dctx.makeValidContext(serviceName, ModelService.IN_PARAM, serviceCtx)
        Map result = dispatcher.runSync(serviceName, serviceContext)
        if (ServiceUtil.isError(result)) {
            throw new ExecutionServiceException(ServiceUtil.getErrorMessage(result))
        }
        return result
    }
    
    Map run(Map args) throws ExecutionServiceException {
        return runService((String)args.get('service'), (Map)args.get('with', new HashMap()))
    }

    Map makeValue(String entityName) throws ExecutionServiceException {
        return result = binding.getVariable('delegator').makeValue(entityName)
    }

    Map makeValue(String entityName, Map inputMap) throws ExecutionServiceException {
        return result = binding.getVariable('delegator').makeValidValue(entityName, inputMap)
    }

    EntityQuery from(def entity) {
        return EntityQuery.use(binding.getVariable('delegator')).from(entity)
    }

    EntityQuery select(String... fields) {
        return EntityQuery.use(binding.getVariable('delegator')).select(fields)
    }

    EntityQuery select(Set<String> fields) {
        return EntityQuery.use(binding.getVariable('delegator')).select(fields)
    }

    GenericValue findOne(String entityName, Map<String, ? extends Object> fields, boolean useCache) {
        return binding.getVariable('delegator').findOne(entityName, fields, useCache)
    }

    def success(String message) {
        // TODO: implement some clever i18n mechanism based on the userLogin and locale in the binding
        if (this.binding.hasVariable('request')) {
            // the script is invoked as an "event"
            if (message) {
                this.binding.getVariable('request').setAttribute("_EVENT_MESSAGE_", message)
            }
            return 'success'
        } else {
            // the script is invoked as a "service"
            if (message) {
                return ServiceUtil.returnSuccess(message)
            } else {
                return ServiceUtil.returnSuccess()
            }
        }
    }
    Map failure(String message) {
        // TODO: implement some clever i18n mechanism based on the userLogin and locale in the binding
        if (message) {
            return ServiceUtil.returnFailure(message)
        } else {
            return ServiceUtil.returnFailure()
        }
    }
    def error(String message) {
        // TODO: implement some clever i18n mechanism based on the userLogin and locale in the binding
        if (this.binding.hasVariable('request')) {
            // the script is invoked as an "event"
            if (message) {
                this.binding.getVariable('request').setAttribute("_ERROR_MESSAGE_", message)
            }
            return 'error'
        } else {
            if (message) {
                return ServiceUtil.returnError(message)
            } else {
                return ServiceUtil.returnError()
            }
        }
    }
    def logInfo(String message) {
        Debug.logInfo(message, module)
    }
    def logWarning(String message) {
        Debug.logWarning(message, module)
    }
    def logError(String message) {
        Debug.logError(message, module)
    }

    def logVerbose(String message) {
        Debug.logVerbose(message, module)
    }
}

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
package org.ofbiz.service.mail;

import java.util.HashMap;
import java.util.Map;

import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.service.GenericServiceException;
import org.ofbiz.service.LocalDispatcher;
import org.ofbiz.service.ServiceUtil;
import org.w3c.dom.Element;

@SuppressWarnings("serial")
public class ServiceMcaAction implements java.io.Serializable {

    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());

    protected String serviceName = null;
    protected String serviceMode = null;
    protected String runAsUser = null;
    protected boolean persist = false;

    protected ServiceMcaAction() { }

    protected ServiceMcaAction(Element action) {
        this.serviceName = action.getAttribute("service");
        this.serviceMode = action.getAttribute("mode");
        this.runAsUser = action.getAttribute("run-as-user");
        // support the old, inconsistent attribute name
        if (UtilValidate.isEmail(this.runAsUser)) {
            this.runAsUser = action.getAttribute("runAsUser");
        }
        this.persist = "true".equals(action.getAttribute("persist"));
    }

    public boolean runAction(LocalDispatcher dispatcher, MimeMessageWrapper messageWrapper, GenericValue userLogin) throws GenericServiceException {
        Map<String, Object> serviceContext = new HashMap<>();
        serviceContext.putAll(UtilMisc.toMap("messageWrapper", messageWrapper, "userLogin", userLogin));
        serviceContext.put("userLogin", ServiceUtil.getUserLogin(dispatcher.getDispatchContext(), serviceContext, runAsUser));

        if ("sync".equals(serviceMode)) {
            Map<String, Object> result = dispatcher.runSync(serviceName, serviceContext);
            if (ServiceUtil.isError(result)) {
                Debug.logError(ServiceUtil.getErrorMessage(result), module);
                return false;
            }
            return true;
        } else if ("async".equals(serviceMode)) {
            dispatcher.runAsync(serviceName, serviceContext, persist);
            return true;
        } else {
            Debug.logError("Invalid service mode [" + serviceMode + "] unable to invoke MCA action (" + serviceName + ").", module);
            return false;
        }
    }
}

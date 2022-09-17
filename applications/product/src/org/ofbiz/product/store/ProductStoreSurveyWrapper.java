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
package org.ofbiz.product.store;

import java.io.Writer;
import java.util.Map;

import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.content.survey.SurveyWrapper;
import org.ofbiz.entity.GenericValue;

/**
 * Product Store Survey Wrapper
 */
public class ProductStoreSurveyWrapper extends SurveyWrapper {
    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());

    protected GenericValue productStoreSurveyAppl = null;
    protected String surveyTemplate = null;
    protected String resultTemplate = null;
    protected boolean callResult = false;

    protected ProductStoreSurveyWrapper() {}

    public ProductStoreSurveyWrapper(GenericValue productStoreSurveyAppl, String partyId, Map<String, Object> passThru, Map<String, Object> defaultValues) {
        this.productStoreSurveyAppl = productStoreSurveyAppl;

        if (this.productStoreSurveyAppl != null) {
            this.partyId = partyId;
            this.delegator = productStoreSurveyAppl.getDelegator();
            this.surveyId = productStoreSurveyAppl.getString("surveyId");
            this.surveyTemplate = productStoreSurveyAppl.getString("surveyTemplate");
            this.resultTemplate = productStoreSurveyAppl.getString("resultTemplate");
        } else {
            throw new IllegalArgumentException("Required parameter productStoreSurveyAppl missing");
        }
        this.setDefaultValues(defaultValues);
        // sanitize pass-thru, we need to remove hidden fields values that are set
        // by the survey so they won't be duplicated in additionalFields
        passThru.remove("surveyId");
        passThru.remove("partyId");
        passThru.remove("surveyResponseId");
        this.setPassThru(passThru);
        this.checkParameters();
    }

    public ProductStoreSurveyWrapper(GenericValue productStoreSurveyAppl, String partyId, Map<String, Object> passThru) {
        this(productStoreSurveyAppl, partyId, passThru, null);
    }

    public void callResult(boolean b) {
        this.callResult = b;
    }

    public Writer render(Map<String, Object> parentContext) throws SurveyWrapperException {
        if (canRespond() && !callResult) {
            return renderSurvey(parentContext);
        } else if (UtilValidate.isNotEmpty(resultTemplate)) {
            return renderResult(parentContext);
        } else {
            // SCIPIO: 2019-03-06: Template can't safely catch this, so this can't be safely handled
            Debug.logWarning("Cannot render survey result: no result template defined [productStoreSurveyId="
                    + productStoreSurveyAppl.get("productStoreSurveyId") + ", callResult=" + callResult + "]", module);
            //throw new SurveyWrapperException("Error template not implemented yet; cannot update survey; no result template defined!");
            return null;
        }
    }

    public Writer renderSurvey(Map<String, Object> parentContext) throws SurveyWrapperException {
        return this.render(surveyTemplate, parentContext);
    }

    public Writer renderResult(Map<String, Object> parentContext) throws SurveyWrapperException {
        return this.render(resultTemplate, parentContext);
    }

    public GenericValue getProductStoreSurveyAppl() { // SCIPIO
        return productStoreSurveyAppl;
    }

    public String getResultTemplate() { // SCIPIO
        return resultTemplate;
    }
}

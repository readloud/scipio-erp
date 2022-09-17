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
package com.ilscipio.scipio.ce.webapp.ftl.lang;

import java.util.HashSet;
import java.util.List;

import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;

/**
 * SCIPIO: ToSetMethod - Converts a sequence or collection to bean-wrapped set.
 */
public class ToSetMethod implements TemplateMethodModelEx {

    //private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());

    @Override
    public Object exec(@SuppressWarnings("rawtypes") List args) throws TemplateModelException {
        if (args == null || args.size() < 0 || args.size() > 1) {
            throw new TemplateModelException("Invalid number of arguments (expected: 0-1)");
        }
        //Environment env = CommonFtlUtil.getCurrentEnvironment();

        if (args.size() > 0) {
            TemplateModel object = (TemplateModel) args.get(0);

            //ObjectWrapper objectWrapper = LangFtlUtil.getCurrentObjectWrapper(env);
            return LangFtlUtil.toSet(object);
        }
        else {
            return new HashSet<Object>();
        }
    }

}

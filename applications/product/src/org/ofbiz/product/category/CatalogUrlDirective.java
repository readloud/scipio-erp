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
package org.ofbiz.product.category;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.entity.Delegator;
import org.ofbiz.service.LocalDispatcher;
import org.ofbiz.webapp.FullWebappInfo;
import org.ofbiz.webapp.renderer.RenderEnvType;

import com.ilscipio.scipio.ce.webapp.ftl.context.ContextFtlUtil;
import com.ilscipio.scipio.ce.webapp.ftl.context.TransformUtil;
import com.ilscipio.scipio.ce.webapp.ftl.context.UrlTransformUtil;

import freemarker.core.Environment;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;

/**
 * CatalogUrlDirective - Freemarker Template Directive for generating URLs suitable for use by the CatalogUrlServlet
 * <p>
 * Accepts the following arguments (see CatalogUrlServlet for their definition):
 * <ul>
 * <li>productId</li>
 * <li>currentCategoryId</li>
 * <li>previousCategoryId</li>
 * </ul>
 * <p>
 * SCIPIO: This transform is augmented to support the following parameters:
 * <ul>
 * <li>fullPath (boolean)</li>
 * <li>secure (boolean)</li>
 * <li>encode (boolean)</li>
 * <li>rawParams (boolean)</li>
 * <li>strict (boolean)</li>
 * <li>escapeAs (string)</li>
 * </ul>
 * <p>
 * In addition, it now supports inter-webapp links. If either of the parameters
 * <ul>
 * <li>webSiteId</li>
 * <li>prefix</li>
 * </ul>
 * are specified, it enables inter-webapp mode, where no session information
 * is used and a purely static link is built instead.
 * For staticly-rendered templates such as emails, webSiteId or prefix is always required.
 * <p>
 * It is also now possible to specify a string of parameters (with or without starting "?") using:
 * <ul>
 * <li>params (TODO: support map of parameters)</li>
 * </ul>
 */
public class CatalogUrlDirective implements TemplateDirectiveModel {
    //private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());

    @Override
    public void execute(Environment env, @SuppressWarnings("rawtypes") Map args, TemplateModel[] loopVars, TemplateDirectiveBody body) throws TemplateException, IOException {
        final String escapeAs = TransformUtil.getStringArg(args, "escapeAs"); // SCIPIO: new
        boolean rawParamsDefault = UtilValidate.isNotEmpty(escapeAs) ? true : false; // SCIPIO: if we're post-escaping, we can assume we should get rawParams
        final boolean rawParams = TransformUtil.getBooleanArg(args, "rawParams", rawParamsDefault); // SCIPIO: new
        boolean strictDefault = UtilValidate.isNotEmpty(escapeAs) ? true : false; // SCIPIO: if we're post-escaping, we can assume we want strict handling
        final Boolean strict = TransformUtil.getBooleanArg(args, "strict", strictDefault); // SCIPIO: new

        String productId = TransformUtil.getStringArg(args, "productId", rawParams);
        String currentCategoryId = TransformUtil.getStringArg(args, "currentCategoryId", rawParams);
        String previousCategoryId = TransformUtil.getStringArg(args, "previousCategoryId", rawParams);

        HttpServletRequest request = ContextFtlUtil.getRequest(env);
        RenderEnvType renderEnvType = ContextFtlUtil.getRenderEnvType(env, request);

        Boolean secure = TransformUtil.getBooleanArg(args, "secure");
        Boolean encode = TransformUtil.getBooleanArg(args, "encode");
        Boolean fullPath = UrlTransformUtil.determineFullPath(TransformUtil.getBooleanArg(args, "fullPath"), renderEnvType, env);

        Object urlParams = TransformUtil.getStringArg(args, "params", rawParams); // SCIPIO: new; TODO: support map (but needs special handling to respect rawParams)
        Locale locale = TransformUtil.getOfbizLocaleArgOrCurrent(args, "locale", env); // SCIPIO: 2018-08-02: get proper locale

        String url;
        try {
            if (request != null) {
                FullWebappInfo targetWebappInfo = FullWebappInfo.fromWebSiteIdOrContextPathOrNull(TransformUtil.getStringArg(args, "webSiteId", rawParams),
                        TransformUtil.getStringArg(args, "prefix", rawParams), request, null);
                HttpServletResponse response = ContextFtlUtil.getResponse(env);
                url = CatalogUrlServlet.makeCatalogLink(request, response, locale, productId, currentCategoryId, previousCategoryId, urlParams,
                        targetWebappInfo, fullPath, secure, encode);
            } else { // SCIPIO: New: Handle non-request cases
                Map<String, Object> context = ContextFtlUtil.getContext(env);
                Delegator delegator = ContextFtlUtil.getDelegator(request, env);
                LocalDispatcher dispatcher = ContextFtlUtil.getDispatcher(env);
                FullWebappInfo targetWebappInfo = FullWebappInfo.fromWebSiteIdOrContextPathOrNull(TransformUtil.getStringArg(args, "webSiteId", rawParams),
                        TransformUtil.getStringArg(args, "prefix", rawParams), null, context);
                url = CatalogUrlServlet.makeCatalogLink(context, delegator, dispatcher, locale, productId,
                        currentCategoryId, previousCategoryId, urlParams, targetWebappInfo, fullPath, secure, encode);
            }
            if (url != null) {
                url = UrlTransformUtil.escapeGeneratedUrl(url, escapeAs, strict, env);
            }
        } catch(Exception e) {
            throw new TemplateException(e, env);
        }
        if (url != null) { // SCIPIO: NOTE: Let IOException propagate ONLY for write call (see TemplateDirectiveModel description)
            env.getOut().write(url);
        }
    }
}

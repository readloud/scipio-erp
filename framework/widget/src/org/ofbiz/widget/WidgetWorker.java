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
package org.ofbiz.widget;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.text.StringEscapeUtils;
import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.UtilCodec;
import org.ofbiz.base.util.UtilGenerics;
import org.ofbiz.base.util.UtilHttp;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.base.util.string.FlexibleStringExpander;
import org.ofbiz.entity.Delegator;
import org.ofbiz.service.LocalDispatcher;
import org.ofbiz.webapp.control.ConfigXMLReader;
import org.ofbiz.webapp.control.RequestHandler;
import org.ofbiz.webapp.taglib.ContentUrlTag;
import org.ofbiz.widget.model.ModelForm;
import org.ofbiz.widget.model.ModelFormField;

public final class WidgetWorker {

    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());

    private WidgetWorker () {}

    public static void buildHyperlinkUrl(Appendable externalWriter, String target, String targetType, Map<String, String> parameterMap,
            String prefix, Boolean fullPath, Boolean secure, Boolean encode, HttpServletRequest request, HttpServletResponse response, Map<String, Object> context) throws IOException {
        // We may get an encoded request like: &#47;projectmgr&#47;control&#47;EditTaskContents&#63;workEffortId&#61;10003
        // Try to reducing a possibly encoded string down to its simplest form: /projectmgr/control/EditTaskContents?workEffortId=10003
        // This step make sure the following appending externalLoginKey operation to work correctly
        // SCIPIO: NOTE: we are less likely to have this escaping issue, because we turn off the HTML early escaping in Scipio and leave it to Freemarker.
        String localRequestName = StringEscapeUtils.unescapeHtml4(target);
        localRequestName = UtilHttp.encodeAmpersands(localRequestName);

        Appendable localWriter = new StringWriter();

        if ("intra-app".equals(targetType)) {
            if (request != null && response != null) {
                ServletContext servletContext = request.getServletContext(); // SCIPIO: NOTE: no longer need getSession() for getServletContext(), since servlet API 3.0
                RequestHandler rh = (RequestHandler) servletContext.getAttribute("_REQUEST_HANDLER_");
                externalWriter.append(rh.makeLink(request, response, localRequestName, fullPath, secure, encode)); // SCIPIO: doesn't need slash, only makes less clear errors:  "/" + localRequestName
            } else if (prefix != null) {
                externalWriter.append(prefix);
                externalWriter.append(localRequestName);
            } else {
                externalWriter.append(localRequestName);
            }
        } else if ("inter-app".equals(targetType)) {
            // SCIPIO: We want to pass this through encodeURL and smart inter-webapp building logic.
            // NOTE: The use of localWriter and externalWriter here is dodgy, but should work.
            /*
            String fullTarget = localRequestName;
            localWriter.append(fullTarget);
            String externalLoginKey = (String) request.getAttribute("externalLoginKey");
            if (UtilValidate.isNotEmpty(externalLoginKey)) {
                if (fullTarget.indexOf('?') == -1) {
                    localWriter.append('?');
                } else {
                    localWriter.append("&amp;");
                }
                localWriter.append("externalLoginKey=");
                localWriter.append(externalLoginKey);
            }
            */
            StringWriter tempWriter = new StringWriter(); // SCIPIO: DON'T use localWriter
            String fullTarget = localRequestName;

            // SCIPIO: 2016-10-21: special "#extLoginKey=OFF" override to prevent adding ext login key
            if (fullTarget.endsWith("#extLoginKey=OFF")) {
                tempWriter.append(fullTarget, 0, fullTarget.length() - "#extLoginKey=OFF".length());
            } else {
                tempWriter.append(fullTarget);
                String externalLoginKey = (String) request.getAttribute("externalLoginKey");
                if (UtilValidate.isNotEmpty(externalLoginKey)) {
                    if (fullTarget.indexOf('?') < 0) {
                        tempWriter.append('?');
                    } else {
                        tempWriter.append("&amp;");
                    }
                    tempWriter.append("externalLoginKey=");
                    tempWriter.append(externalLoginKey);
                }
            }
            if (request != null && response != null) {
                // SCIPIO: We want to make sure this goes through encodeURL, and we now also want to send this
                // through makeLinkAuto so it can produce smarter inter-webapp links.
                // TODO? widgets currently don't support specifying target webSiteId, so absPath always true
                // SCIPIO: 2018-08-15: this is now static method
                //ServletContext servletContext = request.getServletContext(); // SCIPIO: NOTE: no longer need getSession() for getServletContext(), since servlet API 3.0
                //RequestHandler rh = (RequestHandler) servletContext.getAttribute("_REQUEST_HANDLER_");
                externalWriter.append(RequestHandler.makeLinkAuto(request, response, tempWriter.toString(), true, true, null, null, fullPath, secure, encode));
            } else {
                localWriter = tempWriter;
            }
        } else if ("content".equals(targetType)) {
            appendContentUrl(localWriter, localRequestName, request);
        } else if ("plain".equals(targetType)) { // SCIPIO: NOTE: 2018-09-04: this was removed by upstream, I am leaving in, this was not bad...
            localWriter.append(localRequestName);
        } else {
            localWriter.append(localRequestName);
        }

        if (UtilValidate.isNotEmpty(parameterMap)) {
            String localUrl = localWriter.toString();
            externalWriter.append(localUrl);
            boolean needsAmp = true;
            // SCIPIO: This needs to check externalWriter, which already contains localWriter
            //if (localUrl.indexOf('?') == -1) {
            if (externalWriter.toString().indexOf('?') < 0) {
                externalWriter.append('?');
                needsAmp = false;
            }

            for (Map.Entry<String, String> parameter: parameterMap.entrySet()) {
                String parameterValue = null;
                if (parameter.getValue() instanceof String) {
                    parameterValue = parameter.getValue();
                } else {
                    Object parameterObject = parameter.getValue();

                    // skip null values
                    if (parameterObject == null) continue;

                    if (parameterObject instanceof String[]) {
                        // it's probably a String[], just get the first value
                        String[] parameterArray = (String[]) parameterObject;
                        parameterValue = parameterArray[0];
                        Debug.logInfo("Found String array value for parameter [" + parameter.getKey() + "], using first value: " + parameterValue, module);
                    } else {
                        // not a String, and not a String[], just use toString
                        parameterValue = parameterObject.toString();
                    }
                }

                if (needsAmp) {
                    externalWriter.append("&amp;");
                } else {
                    needsAmp = true;
                }
                externalWriter.append(parameter.getKey());
                externalWriter.append('=');
                // SCIPIO: simplified
                UtilCodec.SimpleEncoder simpleEncoder = WidgetWorker.getEarlyEncoder(context);
                if (parameterValue != null) { // simpleEncoder != null &&
                    externalWriter.append(simpleEncoder.encode(URLEncoder.encode(parameterValue, Charset.forName("UTF-8").displayName())));
                //} else {
                //    // SCIPIO: even if HTML encoding were disabled, the link param should have been URL-encoded; they're two different layers
                //    //externalWriter.append(parameterValue);
                //    externalWriter.append(URLEncoder.encode(parameterValue, Charset.forName("UTF-8").displayName()));
                }
            }
        } else {
            externalWriter.append(localWriter.toString());
        }
    }

    public static void appendContentUrl(Appendable writer, String location, HttpServletRequest request) throws IOException {
        StringBuilder buffer = new StringBuilder();
        ContentUrlTag.appendContentPrefix(request, buffer);
        writer.append(buffer.toString());
        writer.append(location);
    }
    
    /**
     * @deprecated SCIPIO: 2018-10-18: The macro form renderer method must be used instead
     * ({@link org.ofbiz.widget.renderer.macro.MacroFormRenderer#makeHyperlinkByType}); this method may be unmaintained and insecure.
     */
    @Deprecated
    public static void makeHyperlinkByType(Appendable writer, String linkType, String linkStyle, String targetType, String target,
            Map<String, String> parameterMap, String description, String targetWindow, String confirmation, ModelFormField modelFormField,
            HttpServletRequest request, HttpServletResponse response, Map<String, Object> context) throws IOException {
        Debug.logWarning("makeHyperlinkByType: Deprecated method called (target: " + target + "); use the MacroFormRenderer method instead", module); // SCIPIO
        /* SCIPIO: 2018-09-04: TODO: REVIEW: from upstream...
        if (modelFormField == null) {
            throw new IllegalArgumentException("modelFormField in WidgetWorker.makeHyperlinkByType has turned out to be null");
        }
        */
        String realLinkType = WidgetWorker.determineAutoLinkType(linkType, target, targetType, request);
        if ("hidden-form".equals(realLinkType)) {
            if (modelFormField != null && "multi".equals(modelFormField.getModelForm().getType())) {
                WidgetWorker.makeHiddenFormLinkAnchor(writer, linkStyle, description, confirmation, modelFormField, request, response, context);

                // this is a bit trickier, since we can't do a nested form we'll have to put the link to submit the form in place, but put the actual form def elsewhere, ie after the big form is closed
                Map<String, Object> wholeFormContext = UtilGenerics.checkMap(context.get("wholeFormContext"));
                Appendable postMultiFormWriter = wholeFormContext != null ? (Appendable) wholeFormContext.get("postMultiFormWriter") : null;
                if (postMultiFormWriter == null) {
                    postMultiFormWriter = new StringWriter();
                    wholeFormContext.put("postMultiFormWriter", postMultiFormWriter);
                }
                WidgetWorker.makeHiddenFormLinkForm(postMultiFormWriter, target, targetType, targetWindow, parameterMap, modelFormField, request, response, context);
            } else {
                WidgetWorker.makeHiddenFormLinkForm(writer, target, targetType, targetWindow, parameterMap, modelFormField, request, response, context);
                WidgetWorker.makeHiddenFormLinkAnchor(writer, linkStyle, description, confirmation, modelFormField, request, response, context);
            }
        } else {
            WidgetWorker.makeHyperlinkString(writer, linkStyle, targetType, target, parameterMap, description, confirmation, modelFormField, request, response, context, targetWindow);
        }
    }

    /**
     * @deprecated SCIPIO: 2018-10-18: Macro form renderer (Freemarker) makeHyperlinkString macro must be used instead
     * (via {@link org.ofbiz.widget.renderer.macro.MacroFormRenderer#makeHyperlinkString}); this method may be unmaintained and insecure.
     */
    @Deprecated
    public static void makeHyperlinkString(Appendable writer, String linkStyle, String targetType, String target, Map<String, String> parameterMap,
            String description, String confirmation, ModelFormField modelFormField, HttpServletRequest request, HttpServletResponse response, Map<String, Object> context, String targetWindow)
            throws IOException {
        Debug.logWarning("makeHyperlinkString: Deprecated method called (target: " + target + "); use the MacroFormRenderer (Freemarker) macro instead", module); // SCIPIO
        if (UtilValidate.isNotEmpty(description) || UtilValidate.isNotEmpty(request.getAttribute("image"))) {
            writer.append("<a");

            if (UtilValidate.isNotEmpty(linkStyle)) {
                writer.append(" class=\"");
                writer.append(linkStyle);
                writer.append("\"");
            }

            writer.append(" href=\"");

            buildHyperlinkUrl(writer, target, targetType, parameterMap, null, null, null, null, request, response, context);

            writer.append("\"");

            if (UtilValidate.isNotEmpty(targetWindow)) {
                writer.append(" target=\"");
                writer.append(targetWindow);
                writer.append("\"");
            }

            if (UtilValidate.isNotEmpty(modelFormField.getEvent()) && UtilValidate.isNotEmpty(modelFormField.getAction(context))) {
                writer.append(" ");
                writer.append(modelFormField.getEvent());
                writer.append("=\"");
                writer.append(modelFormField.getAction(context));
                writer.append('"');
            }
            if (UtilValidate.isNotEmpty(confirmation)){
                writer.append(" onclick=\"return confirm('");
                writer.append(UtilCodec.getJsStringEncoder().encode(confirmation)); // SCIPIO: js encode
                writer.append("')\"");
            }
            writer.append('>');

            if (UtilValidate.isNotEmpty(request.getAttribute("image"))) {
                writer.append("<img src=\"");
                writer.append(request.getAttribute("image").toString());
                writer.append("\"/>");
            }

            writer.append(description);
            writer.append("</a>");
        }
    }

    /**
     * @deprecated SCIPIO: 2018-10-18: The macro form renderer (Freemarker) makeHiddenFormLinkAnchor macro must be used instead
     * (via {@link org.ofbiz.widget.renderer.macro.MacroFormRenderer#makeHiddenFormLinkAnchor}); this method may be unmaintained and insecure.
     */
    @Deprecated
    public static void makeHiddenFormLinkAnchor(Appendable writer, String linkStyle, String description, String confirmation, ModelFormField modelFormField, HttpServletRequest request, HttpServletResponse response, Map<String, Object> context) throws IOException {
        Debug.logWarning("makeHiddenFormLinkAnchor: Deprecated method called; use the MacroFormRenderer (Freemarker) macros instead", module); // SCIPIO
        if (UtilValidate.isNotEmpty(description) || UtilValidate.isNotEmpty(request.getAttribute("image"))) {
            writer.append("<a");

            if (UtilValidate.isNotEmpty(linkStyle)) {
                writer.append(" class=\"");
                writer.append(linkStyle);
                writer.append("\"");
            }

            writer.append(" href=\"javascript:document['");
            writer.append(UtilCodec.getJsStringEncoder().encode(makeLinkHiddenFormName(context, modelFormField))); // SCIPIO: JS escaping
            writer.append("'].submit()\"");

            if (UtilValidate.isNotEmpty(modelFormField.getEvent()) && UtilValidate.isNotEmpty(modelFormField.getAction(context))) {
                writer.append(" ");
                writer.append(modelFormField.getEvent());
                writer.append("=\"");
                writer.append(modelFormField.getAction(context));
                writer.append('"');
            }

            if (UtilValidate.isNotEmpty(confirmation)){
                writer.append(" onclick=\"return confirm('");
                writer.append(UtilCodec.getJsStringEncoder().encode(confirmation)); // SCIPIO: JS escaping
                writer.append("')\"");
            }

            writer.append('>');

            if (UtilValidate.isNotEmpty(request.getAttribute("image"))) {
                writer.append("<img src=\"");
                writer.append(request.getAttribute("image").toString());
                writer.append("\"/>");
            }

            writer.append(description);
            writer.append("</a>");
        }
    }

    /**
     * @deprecated SCIPIO: 2018-10-18: The macro form renderer (Freemarker) makeHiddenFormLinkForm macro must be used instead
     * (via {@link org.ofbiz.widget.renderer.macro.MacroFormRenderer#makeHiddenFormLinkForm}); this method may be unmaintained and insecure.
     */
    @Deprecated
    public static void makeHiddenFormLinkForm(Appendable writer, String target, String targetType, String targetWindow, Map<String, String> parameterMap, ModelFormField modelFormField, HttpServletRequest request, HttpServletResponse response, Map<String, Object> context) throws IOException {
        Debug.logWarning("makeHiddenFormLinkForm: Deprecated method called (target: " + target + "); use the MacroFormRenderer (Freemarker) macro instead", module); // SCIPIO
        writer.append("<form method=\"post\"");
        writer.append(" action=\"");
        // note that this passes null for the parameterList on purpose so they won't be put into the URL
        WidgetWorker.buildHyperlinkUrl(writer, target, targetType, null, null, null, null, null, request, response, context); // SCIPIO: pass null for fullPath/secure/encode
        writer.append("\"");

        if (UtilValidate.isNotEmpty(targetWindow)) {
            writer.append(" target=\"");
            writer.append(targetWindow);
            writer.append("\"");
        }

        writer.append(" onsubmit=\"javascript:submitFormDisableSubmits(this)\"");

        writer.append(" name=\"");
        writer.append(makeLinkHiddenFormName(context, modelFormField));
        writer.append("\">");

        for (Map.Entry<String, String> parameter: parameterMap.entrySet()) {
            if (parameter.getValue() != null) {
                writer.append("<input name=\"");
                writer.append(parameter.getKey());
                writer.append("\" value=\"");
                writer.append(UtilCodec.getEncoder("html").encode(parameter.getValue()));
                writer.append("\" type=\"hidden\"/>");
            }
        }

        writer.append("</form>");
    }

    public static String makeLinkHiddenFormName(Map<String, Object> context, ModelForm modelForm, String prefix) {
        if (UtilValidate.isNotEmpty(modelForm.getName()))
            return prefix + modelForm.getItemIndexSeparator() + modelForm.getName();
        else if (UtilValidate.isNotEmpty(context.get("formName")))
            return prefix + modelForm.getItemIndexSeparator()+ context.get("formName");
        return prefix;
    }

    public static String makeLinkHiddenFormName(Map<String, Object> context, ModelFormField modelFormField) {
        ModelForm modelForm = null;
        // SCIPIO: make sure model form field not empty
        if (modelFormField != null) {
            modelForm = modelFormField.getModelForm();
        }
        Integer itemIndex = (Integer) context.get("itemIndex");
        String iterateId = "";
        String formUniqueId = "";
        String formName = (String) context.get("formName");
        if (modelForm != null && UtilValidate.isEmpty(formName)) { // SCIPIO: make sure modelForm not empty
            formName = modelForm.getName();
        }
        if (UtilValidate.isNotEmpty(context.get("iterateId"))) {
            iterateId = (String) context.get("iterateId");
        }
        if (UtilValidate.isNotEmpty(context.get("formUniqueId"))) {
            formUniqueId = (String) context.get("formUniqueId");
        }
        if (itemIndex != null) {
            return formName + modelForm.getItemIndexSeparator() + itemIndex + iterateId + formUniqueId + modelForm.getItemIndexSeparator() + modelFormField.getName();
        }
        return formName + modelForm.getItemIndexSeparator() + modelFormField.getName();
    }

    public static String determineAutoLinkType(String linkType, String target, String targetType, HttpServletRequest request) {
        if ("auto".equals(linkType)) {
            if (target != null && "intra-app".equals(targetType)) { // SCIPIO: Added null target check, in case
                String requestUri = (target.indexOf('?') > -1) ? target.substring(0, target.indexOf('?')) : target;
                ServletContext servletContext = request.getServletContext(); // SCIPIO: NOTE: no longer need getSession() for getServletContext(), since servlet API 3.0
                RequestHandler rh = (RequestHandler) servletContext.getAttribute("_REQUEST_HANDLER_");
                ConfigXMLReader.RequestMap requestMap = null;
                try {
                    requestMap = rh.getControllerConfig().getRequestMapMap().get(requestUri);
                // SCIPIO: There's no reason for this to crash or print huge error; will be handled elsewhere
                //} catch (WebAppConfigurationException e) {
                //    Debug.logError(e, "Exception thrown while parsing controller.xml file: ", module);
                } catch (Exception e) {
                    Debug.logError("determineAutoLinkType: Cannot determine widget link type for target '" + target 
                            + "': Exception thrown while parsing controller.xml file: " + e.toString(), module);
                }
                if (requestMap != null && requestMap.event != null) {
                    return "hidden-form";
                }
            }
            return "anchor";
        }
        return linkType;
    }

    /** Returns the script location based on a script combined name:
     * <code>location#methodName</code>.
     *
     * @param combinedName The combined location/method name
     * @return The script location
     */
    public static String getScriptLocation(String combinedName) {
        int pos = combinedName.lastIndexOf('#');
        if (pos == -1) {
            return combinedName;
        }
        return combinedName.substring(0, pos);
    }

    /** Returns the script method name based on a script combined name:
     * <code>location#methodName</code>. Returns <code>null</code> if
     * no method name is found.
     *
     * @param combinedName The combined location/method name
     * @return The method name or <code>null</code>
     */
    public static String getScriptMethodName(String combinedName) {
        int pos = combinedName.lastIndexOf('#');
        if (pos == -1) {
            return null;
        }
        return combinedName.substring(pos + 1);
    }

    public static int getPaginatorNumber(Map<String, Object> context) {
        int paginator_number = 0;
        if (context != null) {
            /* SCIPIO: 2018-09-04: TODO: REVIEW: new code form upstream...
            Integer paginateNumberInt= (Integer)context.get("PAGINATOR_NUMBER");
            if (paginateNumberInt == null) {
                paginateNumberInt = 0;
                context.put("PAGINATOR_NUMBER", paginateNumberInt);
                Map<String, Object> globalCtx = UtilGenerics.checkMap(context.get("globalContext"));
                if (globalCtx != null) {
                    globalCtx.put("PAGINATOR_NUMBER", paginateNumberInt);
                }
            }
            paginator_number = paginateNumberInt;
            */
            Map<String, Object> globalCtx = UtilGenerics.checkMap(context.get("globalContext"));
            if (globalCtx != null) {
                Integer paginateNumberInt= (Integer)globalCtx.get("PAGINATOR_NUMBER");
                if (paginateNumberInt == null) {
                    paginateNumberInt = 0;
                    globalCtx.put("PAGINATOR_NUMBER", paginateNumberInt);
                }
                paginator_number = paginateNumberInt;
            }
        }
        return paginator_number;
    }

    public static void incrementPaginatorNumber(Map<String, Object> context) {
        Map<String, Object> globalCtx = UtilGenerics.checkMap(context.get("globalContext"));
        if (globalCtx != null) {
            Boolean NO_PAGINATOR = (Boolean) globalCtx.get("NO_PAGINATOR");
            if (UtilValidate.isNotEmpty(NO_PAGINATOR)) {
                globalCtx.remove("NO_PAGINATOR");
            } else {
                /* SCIPIO: 2018-09-04: TODO: REVIEW: new code from upstream
                Integer paginateNumberInt= (Integer)globalCtx.get("PAGINATOR_NUMBER");
                if (paginateNumberInt == null) {
                    paginateNumberInt = 0;
                }
                paginateNumberInt = paginateNumberInt + 1;
                globalCtx.put("PAGINATOR_NUMBER", paginateNumberInt);
                context.put("PAGINATOR_NUMBER", paginateNumberInt);
                */
                Integer paginateNumberInt = getPaginatorNumber(context) + 1;
                globalCtx.put("PAGINATOR_NUMBER", paginateNumberInt);
            }
        }
    }

    public static LocalDispatcher getDispatcher(Map<String, Object> context) {
        LocalDispatcher dispatcher = (LocalDispatcher) context.get("dispatcher");
        return dispatcher;
    }

    public static Delegator getDelegator(Map<String, Object> context) {
        Delegator delegator = (Delegator) context.get("delegator");
        return delegator;
    }

    /** Extracts parameters from a target URL string, prepares them for an Ajax
     * JavaScript call. This method is currently set to return a parameter string
     * suitable for the Prototype.js library.
     * <p>
     * SCIPIO: 2018-09-07: Method was moved here from {@link org.ofbiz.widget.renderer.html.HtmlWidgetRenderer}.
     * NOTE: The previous Prototype.js comment is probably irrelevant.
     * @param target Target URL string
     * @return Parameter string
     */
    public static String getAjaxParamsFromTarget(String target) {
        String targetParams = UtilHttp.getQueryStringFromTarget(target);
        targetParams = targetParams.replace("?", "");
        targetParams = targetParams.replace("&amp;", "&");
        return targetParams;
    }

    /**
     * SCIPIO: Returns the generic platform simpleEncoder from context, appropriate for point-of-use encoding and other purposes.
     * If none, returns a dummy raw encoder so null tests never needed.
     * <p>
     * NOTE: For macro rendering, this is conceptually inappropriate to use in most cases
     * (because point-of-use is within the macros); all the java code is prior to point-of-use
     * which means you should call {@link #getEarlyEncoder} instead, which we will disable
     * by default in Scipio in favor of encoding from macros (point-of-use).
     */
    public static UtilCodec.SimpleEncoder getEncoder(Map<String, ?> context) {
        UtilCodec.SimpleEncoder encoder = (context != null) ? (UtilCodec.SimpleEncoder) context.get("simpleEncoder") : null;
        if (encoder == null) {
            encoder = UtilCodec.getRawEncoder();
        }
        return encoder;
    }

    /**
     * SCIPIO: Returns the renderer simpleEarlyEncoder, meant to encode widget values typically
     * at a point before their point-of-use (which is usually wrong in design, but here for legacy reasons).
     * If none, returns the platform encoder or a dummy raw encoder so null tests never needed.
     * <p>
     * This falls back to the generic platform encoder unless it is configured explicitly to use raw encoder.
     * <p>
     * This may be different from the generic platform simpleEncoder.
     */
    public static UtilCodec.SimpleEncoder getEarlyEncoder(Map<String, ?> context) {
        UtilCodec.SimpleEncoder encoder = (context != null) ? (UtilCodec.SimpleEncoder) context.get("simpleEarlyEncoder") : null;
        if (encoder == null) {
            encoder = getEncoder(context);
        }
        return encoder;
    }

    private static final int minWidgetFolderPathLength =
            ("component://".length() + 1 + "/widget/".length()); // SCIPIO

    /**
     * SCIPIO: Returns base widget folder from component:// path, including terminating slash.
     */
    public static String getBaseWidgetFolderFromComponentPath(String path) throws IllegalArgumentException {
        if (!path.startsWith("component://")) {
            throw new IllegalArgumentException("Path '" + path + "' is not a valid component path");
        }
        if (path.length() < minWidgetFolderPathLength) {
            throw new IllegalArgumentException("Path '" + path + "' is not a valid widget folder component path");
        }
        int i = path.indexOf("/widget/", "component://".length());
        if (i < 0) {
            throw new IllegalArgumentException("Path '" + path + "' is not a valid widget folder component path");
        }
        return path.substring(0, i + "/widget/".length());
    }

    /**
     * SCIPIO: Extracts ONLY the non-interpreted styles from a style string.
     * Skips anything that contains a flexible expression, removes + and = prefixes.
     */
    public static <T extends Collection<String>> T extractSimpleStyles(String style, T out) {
        String[] entries = style.split("\\s+");
        for(String entry : entries) {
            if (entry.isEmpty() || entry.contains(FlexibleStringExpander.openBracket) || entry.contains(FlexibleStringExpander.closeBracket))
                continue;
            if (entry.startsWith("=") || entry.startsWith("+")) {
                entry = entry.substring(1);
            }
            out.add(entry);
        }
        return out;
    }
}

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
package org.ofbiz.widget.renderer.macro;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.StringUtil;
import org.ofbiz.base.util.UtilGenerics;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.base.util.template.FreeMarkerWorker;
import org.ofbiz.base.util.template.FtlScriptFormatter;
import org.ofbiz.webapp.control.RequestHandler;
import org.ofbiz.webapp.taglib.ContentUrlTag;
import org.ofbiz.widget.WidgetWorker;
import org.ofbiz.widget.model.ModelTree;
import org.ofbiz.widget.model.ModelWidget;
import org.ofbiz.widget.renderer.ScreenRenderer;
import org.ofbiz.widget.renderer.ScreenStringRenderer;
import org.ofbiz.widget.renderer.TreeStringRenderer;

import freemarker.core.Environment;
import freemarker.template.Template;
import freemarker.template.TemplateException;

/**
 * Widget Library - Tree Renderer implementation based on Freemarker macros
 *
 */
public class MacroTreeRenderer implements TreeStringRenderer {

    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());
    private Template macroLibrary;

    // SCIPIO: new
    private final FtlScriptFormatter ftlFmt = new FtlScriptFormatter();
    private ContextHandler contextHandler = new ContextHandler("tree");
    private final String rendererName;

    /**
     * Constructor.
     * <p>
     * SCIPIO: modified to require name.
     * <p>
     * SCIPIO: environments now stored in WeakHashMap, same as other macro renderers.
     */
    private final WeakHashMap<Appendable, Environment> environments = new WeakHashMap<Appendable, Environment>();

    public MacroTreeRenderer(String name, String macroLibraryPath) throws TemplateException, IOException {
        // SCIPIO: use abstracted template build
        this.macroLibrary = MacroScreenRenderer.getTemplate(name, macroLibraryPath);
        this.rendererName = name; // SCIPIO: new
    }

    /**
     * Old tree renderer constructor.
     * <p>
     * SCIPIO: modified to require name.
     *
     * @deprecated environments now stored in WeakHashMap so writer from individual calls used instead.
     */
    @Deprecated
    public MacroTreeRenderer(String name, String macroLibraryPath, Appendable writer) throws TemplateException, IOException {
        this(name, macroLibraryPath);
    }

    /**
     * SCIPIO: Returns macro library path used for this renderer.
     */
    public String getMacroLibraryPath() {
        return macroLibrary.getName();
    }

    /**
     * SCIPIO: Returns the renderer name (html, xml, etc.).
     */
    public String getRendererName() {
        return rendererName;
    }

    private void executeMacro(Appendable writer, String macro) throws IOException {
        try {
            Environment environment = getEnvironment(writer);
            Reader templateReader = new StringReader(macro);
            // FIXME: I am using a Date as an hack to provide a unique name for the template...
            Template template = new Template((new java.util.Date()).toString(), templateReader,
                    FreeMarkerWorker.getDefaultOfbizConfig());
            templateReader.close();
            FreeMarkerWorker.includeTemplate(template, environment); // SCIPIO: use FreeMarkerWorker instead of Environment
        } catch (TemplateException | IOException e) {
            Debug.logError(e, "Error rendering tree thru ftl", module);
            handleError(writer, e); // SCIPIO
        }
    }

    /**
     * SCIPIO: makes exception handling decision for executeMacro exceptions.
     */
    private void handleError(Appendable writer, Throwable t) throws IOException, RuntimeException {
        MacroScreenRenderer.handleError(writer, contextHandler.getInitialContext(writer), t);
    }

    private Environment getEnvironment(Appendable writer) throws TemplateException, IOException {
        Environment environment = environments.get(writer);
        if (environment == null) {
            // SCIPIO: custom render context
            Map<String, Object> input = contextHandler.createRenderContext(writer, null, UtilMisc.toMap("key", null));
            environment = FreeMarkerWorker.renderTemplate(macroLibrary, input, writer);
            environments.put(writer, environment);
        }
        return environment;
    }

    /**
     * Renders the beginning boundary comment string.
     * @param writer The writer to write to
     * @param widgetType The widget type: "Screen Widget", "Tree Widget", etc.
     * @param modelWidget The widget
     */
    public void renderBeginningBoundaryComment(Appendable writer, String widgetType, ModelWidget modelWidget) throws IOException {
        StringWriter sr = new StringWriter();
        sr.append("<@formatBoundaryComment ");
        sr.append(" boundaryType=");
        sr.append(ftlFmt.makeStringLiteral("Begin"));
        sr.append(" widgetType=");
        sr.append(ftlFmt.makeStringLiteral(widgetType));
        sr.append(" widgetName=");
        sr.append(ftlFmt.makeStringLiteral(modelWidget.getBoundaryCommentName()));
        sr.append(" />");
        executeMacro(writer, sr.toString());
    }

    /**
     * Renders the ending boundary comment string.
     * @param writer The writer to write to
     * @param widgetType The widget type: "Screen Widget", "Tree Widget", etc.
     * @param modelWidget The widget
     */
    public void renderEndingBoundaryComment(Appendable writer, String widgetType, ModelWidget modelWidget) throws IOException {
        StringWriter sr = new StringWriter();
        sr.append("<@formatBoundaryComment ");
        sr.append(" boundaryType=");
        sr.append(ftlFmt.makeStringLiteral("End"));
        sr.append(" widgetType=");
        sr.append(ftlFmt.makeStringLiteral(widgetType));
        sr.append(" widgetName=");
        sr.append(ftlFmt.makeStringLiteral(modelWidget.getBoundaryCommentName()));
        sr.append(" />");
        executeMacro(writer, sr.toString());
    }

    public void renderNodeBegin(Appendable writer, Map<String, Object> context, ModelTree.ModelNode node, int depth) throws IOException {
        contextHandler.registerContext(writer, context);
        String currentNodeTrailPiped = null;
        List<String> currentNodeTrail = UtilGenerics.toList(context.get("currentNodeTrail"));

        String style = "";
        if (node.isRootNode()) {
            if (ModelWidget.widgetBoundaryCommentsEnabled(context)) {
                renderBeginningBoundaryComment(writer, "Tree Widget", node.getModelTree());
            }
            // SCIPIO: 2018-05: need something unique to identify this type of tree
            //style = "basic-tree";
            style = "basic-tree scp-tree-widget";
        }

        StringWriter sr = new StringWriter();
        sr.append("<@renderNodeBegin ");
        sr.append(" style=");
        sr.append(ftlFmt.makeStringLiteral(style));
        sr.append(" />");
        executeMacro(writer, sr.toString());

        String pkName = node.getPkName(context);
        String entityId = null;
        String entryName = node.getEntryName();
        if (UtilValidate.isNotEmpty(entryName)) {
            Map<String, String> map = UtilGenerics.checkMap(context.get(entryName));
            entityId = map.get(pkName);
        } else {
            entityId = (String) context.get(pkName);
        }
        boolean hasChildren = node.hasChildren(context);

        // check to see if this node needs to be expanded.
        if (hasChildren && node.isExpandCollapse()) {
            // FIXME: Using a widget model in this way is an ugly hack.
            ModelTree.ModelNode.Link expandCollapseLink = null;
            String targetEntityId = null;
            List<String> targetNodeTrail = UtilGenerics.toList(context.get("targetNodeTrail"));
            if (depth < targetNodeTrail.size()) {
                targetEntityId = targetNodeTrail.get(depth);
            }

            int openDepth = node.getModelTree().getOpenDepth();
            if (depth >= openDepth && (targetEntityId == null || !targetEntityId.equals(entityId))) {
                // Not on the trail
                if (node.showPeers(depth, context)) {
                    context.put("processChildren", Boolean.FALSE);
                    // SCIPIO: 2018-05: This character must be url-escaped otherwise newer Tomcat will crash
                    //currentNodeTrailPiped = StringUtil.join(currentNodeTrail, "|");
                    currentNodeTrailPiped = StringUtil.join(currentNodeTrail, "%7C");
                    StringBuilder target = new StringBuilder(node.getModelTree().getExpandCollapseRequest(context));
                    String trailName = node.getModelTree().getTrailName(context);
                    if (target.indexOf("?") < 0) {
                        target.append("?");
                    } else {
                        target.append("&");
                    }
                    target.append(trailName).append("=").append(currentNodeTrailPiped);
                    expandCollapseLink = new ModelTree.ModelNode.Link("collapsed", target.toString(), " ");
                }
            } else {
                context.put("processChildren", Boolean.TRUE);
                String lastContentId = currentNodeTrail.remove(currentNodeTrail.size() - 1);
                // SCIPIO: 2018-05: This character must be url-escaped otherwise newer Tomcat will crash
                //currentNodeTrailPiped = StringUtil.join(currentNodeTrail, "|");
                currentNodeTrailPiped = StringUtil.join(currentNodeTrail, "%7C");
                if (currentNodeTrailPiped == null) {
                    currentNodeTrailPiped = "";
                }
                StringBuilder target = new StringBuilder(node.getModelTree().getExpandCollapseRequest(context));
                String trailName = node.getModelTree().getTrailName(context);
                if (target.indexOf("?") < 0) {
                    target.append("?");
                } else {
                    target.append("&");
                }
                target.append(trailName).append("=").append(currentNodeTrailPiped);
                expandCollapseLink = new ModelTree.ModelNode.Link("expanded", target.toString(), " ");
                // add it so it can be remove in renderNodeEnd
                currentNodeTrail.add(lastContentId);
            }
            if (expandCollapseLink != null) {
                renderLink(writer, context, expandCollapseLink);
            }
        } else if (!hasChildren) {
            context.put("processChildren", Boolean.FALSE);
            ModelTree.ModelNode.Link expandCollapseLink = new ModelTree.ModelNode.Link("leafnode", "", " ");
            renderLink(writer, context, expandCollapseLink);
        }
    }

    public void renderNodeEnd(Appendable writer, Map<String, Object> context, ModelTree.ModelNode node) throws IOException {
        Boolean processChildren = (Boolean) context.get("processChildren");
        StringWriter sr = new StringWriter();
        sr.append("<@renderNodeEnd ");
        sr.append(" processChildren=");
        sr.append(Boolean.toString(processChildren));
        sr.append(" isRootNode=");
        sr.append(Boolean.toString(node.isRootNode()));
        sr.append(" />");
        executeMacro(writer, sr.toString());
        if (node.isRootNode()) {
            if (ModelWidget.widgetBoundaryCommentsEnabled(context)) {
                renderEndingBoundaryComment(writer, "Tree Widget", node.getModelTree());
            }
        }
    }

    public void renderLastElement(Appendable writer, Map<String, Object> context, ModelTree.ModelNode node) throws IOException {
        Boolean processChildren = (Boolean) context.get("processChildren");
        if (processChildren) {
            StringWriter sr = new StringWriter();
            sr.append("<@renderLastElement ");
            sr.append("style=");
            sr.append(ftlFmt.makeStringLiteral("basic-tree"));
            sr.append("/>");
            executeMacro(writer, sr.toString());
        }
    }

    public void renderLabel(Appendable writer, Map<String, Object> context, ModelTree.ModelNode.Label label) throws IOException {
        String id = label.getId(context);
        String style = label.getStyle(context);
        String labelText = label.getText(context);

        StringWriter sr = new StringWriter();
        sr.append("<@renderLabel ");
        sr.append("id=");
        sr.append(ftlFmt.makeStringLiteral(id));
        sr.append(" style=");
        sr.append(ftlFmt.makeStringLiteral(style));
        sr.append(" labelText=");
        sr.append(ftlFmt.makeStringLiteral(labelText));
        sr.append(" />");
        executeMacro(writer, sr.toString());
    }

    public void renderLink(Appendable writer, Map<String, Object> context, ModelTree.ModelNode.Link link) throws IOException {
        String target = link.getTarget(context);
        StringBuilder linkUrl = new StringBuilder();
        HttpServletResponse response = (HttpServletResponse) context.get("response");
        HttpServletRequest request = (HttpServletRequest) context.get("request");

        if (UtilValidate.isNotEmpty(target)) {
            WidgetWorker.buildHyperlinkUrl(linkUrl, target, link.getUrlMode(), link.getParameterMap(context), link.getPrefix(context),
                    link.getFullPath(), link.getSecure(), link.getEncode(), request, response, context);
        }

        String id = link.getId(context);
        String style = link.getStyle(context);
        String name = link.getName(context);
        String title = link.getTitle(context);
        String targetWindow = link.getTargetWindow(context);
        String linkText = link.getText(context);

        String imgStr = "";
        ModelTree.ModelNode.Image img = link.getImage();
        if (img != null) {
            StringWriter sw = new StringWriter();
            renderImage(sw, context, img);
            imgStr = sw.toString();
        }

        StringWriter sr = new StringWriter();
        sr.append("<@renderLink ");
        sr.append("id=");
        sr.append(ftlFmt.makeStringLiteral(id));
        sr.append(" style=");
        sr.append(ftlFmt.makeStringLiteral(style));
        sr.append(" name=");
        sr.append(ftlFmt.makeStringLiteral(name));
        sr.append(" title=");
        sr.append(ftlFmt.makeStringLiteral(title));
        sr.append(" targetWindow=");
        sr.append(ftlFmt.makeStringLiteral(targetWindow));
        sr.append(" linkUrl=");
        sr.append(ftlFmt.makeStringLiteral(linkUrl.toString()));
        sr.append(" linkText=");
        sr.append(ftlFmt.makeStringLiteral(linkText));
        sr.append(" imgStr=");
        sr.append(ftlFmt.makeStringLiteral(imgStr));
        sr.append(" />");
        executeMacro(writer, sr.toString());
    }

    public void renderImage(Appendable writer, Map<String, Object> context, ModelTree.ModelNode.Image image) throws IOException {
        if (image == null) {
            return;
        }
        HttpServletResponse response = (HttpServletResponse) context.get("response");
        HttpServletRequest request = (HttpServletRequest) context.get("request");

        String urlMode = image.getUrlMode();
        String src = image.getSrc(context);
        String id = image.getId(context);
        String style = image.getStyle(context);
        String wid = image.getWidth(context);
        String hgt = image.getHeight(context);
        String border = image.getBorder(context);
        String alt = ""; //TODO add alt to tree images image.getAlt(context);

        Boolean fullPath = null; // SCIPIO: changed from boolean to Boolean
        Boolean secure = null; // SCIPIO: changed from boolean to Boolean
        Boolean encode = false; // SCIPIO: changed from boolean to Boolean
        String urlString = "";

        if (urlMode != null && "intra-app".equalsIgnoreCase(urlMode)) {
            if (request != null && response != null) {
                ServletContext ctx = request.getServletContext(); // SCIPIO: get context using servlet API 3.0
                RequestHandler rh = (RequestHandler) ctx.getAttribute("_REQUEST_HANDLER_");
                urlString = rh.makeLink(request, response, src, fullPath, secure, encode);
            } else {
                urlString = src;
            }
        } else  if (urlMode != null && "content".equalsIgnoreCase(urlMode)) {
            if (request != null && response != null) {
                StringBuilder newURL = new StringBuilder();
                ContentUrlTag.appendContentPrefix(request, newURL);
                newURL.append(src);
                urlString = newURL.toString();
            }
        } else {
            urlString = src;
        }
        StringWriter sr = new StringWriter();
        sr.append("<@renderImage ");
        sr.append("src=");
        sr.append(ftlFmt.makeStringLiteral(src));
        sr.append(" id=");
        sr.append(ftlFmt.makeStringLiteral(id));
        sr.append(" style=");
        sr.append(ftlFmt.makeStringLiteral(style));
        sr.append(" wid=");
        sr.append(ftlFmt.makeStringLiteral(wid));
        sr.append(" hgt=");
        sr.append(ftlFmt.makeStringLiteral(hgt));
        sr.append(" border=");
        sr.append(ftlFmt.makeStringLiteral(border));
        sr.append(" alt=");
        sr.append(ftlFmt.makeStringLiteral(alt));
        sr.append(" urlString=");
        sr.append(ftlFmt.makeStringLiteral(urlString));
        sr.append(" />");
        executeMacro(writer, sr.toString());
    }

    public ScreenStringRenderer getScreenStringRenderer(Map<String, Object> context) {
        ScreenRenderer screenRenderer = (ScreenRenderer)context.get("screens");
        if (screenRenderer != null) {
            return screenRenderer.getScreenStringRenderer();
        }
        return null;
    }
}

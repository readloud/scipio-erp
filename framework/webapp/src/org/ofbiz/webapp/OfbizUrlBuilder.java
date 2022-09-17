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
package org.ofbiz.webapp;

import java.io.IOException;
import java.net.URL;

import javax.servlet.http.HttpServletRequest;

import org.ofbiz.base.component.ComponentConfig.WebappInfo;
import org.ofbiz.base.util.Assert;
import org.ofbiz.base.util.StringUtil;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.entity.Delegator;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.util.EntityQuery;
import org.ofbiz.webapp.control.ConfigXMLReader;
import org.ofbiz.webapp.control.ConfigXMLReader.ControllerConfig;
import org.ofbiz.webapp.control.ConfigXMLReader.RequestMap;
import org.ofbiz.webapp.control.RequestHandler;
import org.ofbiz.webapp.control.RequestLinkUtil;
import org.ofbiz.webapp.control.WebAppConfigurationException;
import org.ofbiz.webapp.website.WebSiteProperties;
import org.xml.sax.SAXException;

/**
 * OFBiz URL builder.
 * <p>
 * SCIPIO: WARNING: This is a lower-level link building utility that does not perform
 * HttpServletRequest.encodeURL, URL rewriting, and other high-level logic such as that
 * normally performed by the Freemarker link-building macros.
 * For common webapp/control link-building from applications, the higher-level RequestLinkUtil
 * and RequestHandle makeLink(Auto) utilities may be used instead.
 * <p>
 * SCIPIO: Some noteworthy changes:
 * <ul>
 * <li>Controller is now optional (no exceptions for webapps with no controller) (added 2017-11-18).</li>
 * <li>Encompasses the new webappPathPrefix mostly automatically (2018-08)</li>
 * <li>Many new overloads and fixes</li>
 * <li>Generalized, can build more different types of links</li>
 * </ul>
 * DEV NOTE: In general, this class is mostly rewritten by Scipio, so that it can
 * be used to centralize part of the link-building (but not all; it does not take
 * care of ofbizUrl links in templates yet; TODO?).
 */
public final class OfbizUrlBuilder {

    //private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());

    /**
     * Returns an <code>OfbizUrlBuilder</code> instance.
     *
     * @param request
     * @throws GenericEntityException
     * @throws WebAppConfigurationException
     */
    public static OfbizUrlBuilder from(HttpServletRequest request) throws GenericEntityException, WebAppConfigurationException {
        Assert.notNull("request", request);
        OfbizUrlBuilder builder = (OfbizUrlBuilder) request.getAttribute("_OFBIZ_URL_BUILDER_");
        if (builder == null) {
            WebSiteProperties webSiteProps = WebSiteProperties.from(request);
            URL url = ConfigXMLReader.getControllerConfigURL(request.getServletContext());
            ControllerConfig config = (url != null) ? ConfigXMLReader.getControllerConfig(url, true) : null; // SCIPIO: 2017-11-18: controller now fully optional (2 change)
            // SCIPIO: Use more reliable call
            //String servletPath = (String) request.getAttribute("_CONTROL_PATH_");
            String servletPath = RequestHandler.getControlPath(request);
            String contextPath = request.getContextPath();
            builder = new OfbizUrlBuilder(config, webSiteProps, servletPath, contextPath); // SCIPIO
            request.setAttribute("_OFBIZ_URL_BUILDER_", builder);
        }
        return builder;
    }

    /**
     * SCIPIO: Returns an <code>OfbizUrlBuilder</code> instance for a specific webapp, but exploiting
     * current request information - mainly for inter-webapp links.
     * <p>
     * NOTE: This is vital so that the WebSiteProperties request overload is called instead of the
     * delegator one.
     */
    public static OfbizUrlBuilder from(ExtWebappInfo extWebappInfo, HttpServletRequest request) throws GenericEntityException, WebAppConfigurationException {
        WebSiteProperties webSiteProps = WebSiteProperties.from(extWebappInfo, request);
        ControllerConfig config = extWebappInfo.getControllerConfig();
        String servletPath = extWebappInfo.getFullControlPath();
        String contextPath = extWebappInfo.getContextPath();
        return new OfbizUrlBuilder(config, webSiteProps, servletPath, contextPath);
    }

    /**
     * Returns an <code>OfbizUrlBuilder</code> instance. Use this method when you
     * don't have a <code>HttpServletRequest</code> object - like in scheduled jobs.
     *
     * @param webAppInfo Optional - if <code>null</code>, the builder can only build the host part,
     * and that will be based only on the settings in <code>url.properties</code> (the WebSite
     * entity will be ignored).
     * @param delegator
     * @throws WebAppConfigurationException
     * @throws IOException
     * @throws SAXException
     * @throws GenericEntityException
     */
    public static OfbizUrlBuilder from(WebappInfo webAppInfo, Delegator delegator) throws WebAppConfigurationException, IOException, SAXException, GenericEntityException {
        WebSiteProperties webSiteProps = null;
        if (webAppInfo != null) {
            Assert.notNull("delegator", delegator);
            String webSiteId = WebAppUtil.getWebSiteId(webAppInfo);
            if (webSiteId != null) {
                GenericValue webSiteValue = EntityQuery.use(delegator).from("WebSite").where("webSiteId", webSiteId).cache().queryOne();
                if (webSiteValue != null) {
                    webSiteProps = WebSiteProperties.from(webSiteValue);
                }
            }
        }
        return from(webAppInfo, webSiteProps, delegator);
    }

    /**
     * SCIPIO: Returns an <code>OfbizUrlBuilder</code> instance. Mixed method that allows
     * using WebSiteProperties different than the WebappInfo instance.
     * <p>
     * This is needed because not every webapp has its own webSiteId, which means
     * another source for WebSiteProperties must be used in its place.
     *
     * @param webAppInfo Optional - if <code>null</code>, the builder can only build the host part,
     * and that will be based only on the settings in <code>url.properties</code> (the WebSite
     * entity will be ignored).
     * @param delegator
     * @throws WebAppConfigurationException
     * @throws IOException
     * @throws SAXException
     * @throws GenericEntityException
     */
    public static OfbizUrlBuilder from(WebappInfo webAppInfo, WebSiteProperties webSiteProps, Delegator delegator) throws WebAppConfigurationException, IOException, SAXException, GenericEntityException {
        ControllerConfig config = null;
        String servletPath = null;
        String contextPath = null;
        if (webAppInfo != null) {
            Assert.notNull("delegator", delegator);
            config = ConfigXMLReader.getControllerConfig(webAppInfo, true); // SCIPIO: 2017-11-18: controller now optional
            servletPath = WebAppUtil.getControlServletPath(webAppInfo, true); // SCIPIO: 2017-11-18: ControlServlet now optional
            contextPath = webAppInfo.getContextRoot();
        }
        if (webSiteProps == null) {
            webSiteProps = WebSiteProperties.defaults(delegator);
        }
        return new OfbizUrlBuilder(config, webSiteProps, servletPath, contextPath);
    }

    /**
     * SCIPIO: Returns an <code>OfbizUrlBuilder</code> instance. Use this method when you
     * don't have a <code>HttpServletRequest</code> object - like in scheduled jobs.
     * <p>
     * Added 2018-08-02.
     *
     * @param extWebAppInfo Optional - if <code>null</code>, the builder can only build the host part,
     * and that will be based only on the settings in <code>url.properties</code> (the WebSite
     * entity will be ignored).
     * @param delegator
     * @throws WebAppConfigurationException
     * @throws IOException
     * @throws SAXException
     * @throws GenericEntityException
     */
    public static OfbizUrlBuilder from(ExtWebappInfo extWebAppInfo, Delegator delegator) throws WebAppConfigurationException, IOException, SAXException, GenericEntityException {
        WebSiteProperties webSiteProps = null;
        ControllerConfig config = null;
        String servletPath = null;
        String contextPath = null;
        if (extWebAppInfo != null) {
            Assert.notNull("delegator", delegator);
            String webSiteId = extWebAppInfo.getWebSiteId();
            if (webSiteId != null) {
                GenericValue webSiteValue = EntityQuery.use(delegator).from("WebSite").where("webSiteId", webSiteId).cache().queryOne();
                if (webSiteValue != null) {
                    webSiteProps = WebSiteProperties.from(webSiteValue);
                }
            }
            config = extWebAppInfo.getControllerConfig();
            servletPath = extWebAppInfo.getFullControlPath();
            contextPath = extWebAppInfo.getContextPath();
        }
        if (webSiteProps == null) {
            webSiteProps = WebSiteProperties.defaults(delegator);
        }
        return new OfbizUrlBuilder(config, webSiteProps, servletPath, contextPath);
    }

    /**
     * SCIPIO: Returns an <code>OfbizUrlBuilder</code> instance. Use this method when you
     * don't have a <code>HttpServletRequest</code> object - like in scheduled jobs.
     * <p>
     * Added 2018-08-02.
     *
     * @param extWebAppInfo Optional - if <code>null</code>, the builder can only build the host part,
     * and that will be based only on the settings in <code>url.properties</code> (the WebSite
     * entity will be ignored).
     * @param delegator
     * @throws WebAppConfigurationException
     * @throws IOException
     * @throws SAXException
     * @throws GenericEntityException
     */
    public static OfbizUrlBuilder from(ExtWebappInfo extWebAppInfo, WebSiteProperties webSiteProps, Delegator delegator) throws WebAppConfigurationException, IOException, SAXException, GenericEntityException {
        ControllerConfig config = null;
        String servletPath = null;
        String contextPath = null;
        if (extWebAppInfo != null) {
            Assert.notNull("delegator", delegator);
            config = extWebAppInfo.getControllerConfig();
            servletPath = extWebAppInfo.getFullControlPath();
            contextPath = extWebAppInfo.getContextPath();
        }
        if (webSiteProps == null) {
            webSiteProps = WebSiteProperties.defaults(delegator);
        }
        return new OfbizUrlBuilder(config, webSiteProps, servletPath, contextPath);
    }

    /**
     * SCIPIO: Returns a new <code>OfbizUrlBuilder</code> instance from the given FullWebappInfo.
     * Roughly same as {@link FullWebappInfo#getOfbizUrlBuilder()}.
     * <p>
     * Added 2018-08-02.
     *
     * @param extWebAppInfo Optional - if <code>null</code>, the builder can only build the host part,
     * and that will be based only on the settings in <code>url.properties</code> (the WebSite
     * entity will be ignored).
     * @param delegator
     * @throws WebAppConfigurationException
     * @throws IOException
     * @throws SAXException
     * @throws GenericEntityException
     */
    public static OfbizUrlBuilder from(FullWebappInfo extWebAppInfo, Delegator delegator) throws WebAppConfigurationException, IOException, SAXException, GenericEntityException {
        return from(extWebAppInfo.getExtWebappInfo(), extWebAppInfo.getWebSiteProperties(), delegator);
    }

    /**
     * SCIPIO: Returns an <code>OfbizUrlBuilder</code> instance using the given webSiteId.
     * Added 2017-11.
     *
     * @param webSiteId Optional - if <code>null</code>, the builder can only build the host part,
     * and that will be based only on the settings in <code>url.properties</code> (the WebSite
     * entity will be ignored).
     * @param delegator
     * @throws WebAppConfigurationException
     * @throws IOException
     * @throws SAXException
     * @throws GenericEntityException
     */
    public static OfbizUrlBuilder fromWebSiteId(String webSiteId, Delegator delegator) throws WebAppConfigurationException,
        IOException, SAXException, GenericEntityException, IllegalArgumentException {
        WebappInfo webAppInfo = null;
        WebSiteProperties webSiteProps = null;
        if (webSiteId != null && !webSiteId.isEmpty()) {
            webAppInfo = WebAppUtil.getWebappInfoFromWebsiteId(webSiteId);
            webSiteProps = WebSiteProperties.from(webSiteId, delegator);
        }
        if (webSiteProps == null) {
            webSiteProps = WebSiteProperties.defaults(delegator);
        }
        return from(webAppInfo, webSiteProps, delegator);
    }

    /**
     * SCIPIO: Returns an <code>OfbizUrlBuilder</code> instance from system defaults.
     * WARN: This type of instance can only build host parts!
     * <p>
     * Added 2018-08-02.
     */
    public static OfbizUrlBuilder fromServerDefaults(HttpServletRequest request) throws WebAppConfigurationException, IOException, SAXException, GenericEntityException {
        // TODO: cache in request
        return fromServerDefaults((Delegator) request.getAttribute("delegator"));
    }

    /**
     * SCIPIO: Returns an <code>OfbizUrlBuilder</code> instance from system defaults.
     * WARN: This type of instance can only build host parts!
     * <p>
     * Added 2018-08-02.
     */
    public static OfbizUrlBuilder fromServerDefaults(Delegator delegator) throws WebAppConfigurationException, IOException, SAXException, GenericEntityException {
        WebSiteProperties webSiteProps = WebSiteProperties.defaults(delegator);
        return new OfbizUrlBuilder(null, webSiteProps, null, null);
    }

    private final ControllerConfig config;
    private final WebSiteProperties webSiteProps;
    private final String servletPath;
    private final String contextPath;   // SCIPIO: this class should record the context path (webapp mount-point)

    private OfbizUrlBuilder(ControllerConfig config, WebSiteProperties webSiteProps, String servletPath, String contextPath) {
        this.config = config;
        this.webSiteProps = webSiteProps;
        // SCIPIO: 2018-08-01: this comes in with a trailing slash half the time, strip it here
        // because it complicates everything else for no reason...
        if (servletPath != null && servletPath.endsWith("/")) {
            servletPath = servletPath.substring(0, servletPath.length() - 1);
        }
        this.servletPath = servletPath;
        this.contextPath = contextPath;
    }

    /**
     * Builds a full URL - including scheme, host, servlet path and resource.
     * SCIPIO: NOTE: This builds a link to a controller entry - the uri specifies a controller.xml request URI.
     *
     * @param buffer
     * @param uri
     * @param useSSL Default value to use - will be replaced by request-map setting
     * if one is found.
     * @return <code>true</code> if the URL uses https
     * @throws WebAppConfigurationException
     * @throws IOException
     */
    public boolean buildFullUrl(Appendable buffer, String uri, boolean useSSL) throws WebAppConfigurationException, IOException {
        boolean makeSecure = buildHostPart(buffer, uri, useSSL);
        buildPathPart(buffer, uri);
        return makeSecure;
    }

    /**
     * SCIPIO: Builds a full URL - including scheme, host, context root and resource (custom servlet),
     * but does NOT consult controller. The uri can point to any servlet.
     * Added 2018-08-01.
     */
    public boolean buildFullUrlWithContextPath(Appendable buffer, String uri, boolean useSSL) throws WebAppConfigurationException, IOException {
        boolean makeSecure = buildHostPart(buffer, useSSL);
        buildPathPartWithContextPath(buffer, uri);
        return makeSecure;
    }

    /**
     * Builds a partial URL - including the scheme and host, but not the servlet path or resource.
     * <p>
     * SCIPIO: Modified to support omitting controller lookup. Also supports Boolean instead of boolean.
     * This does NOT include webappPathPrefix. This can be used mainly to split the host server part and path part building,
     * for specific implementations.
     *
     * @param buffer
     * @param uri
     * @param useSSL Default value to use - will be replaced by request-map setting
     * if one is found with security=true set.
     * @param controller
     * @return <code>true</code> if the URL uses https
     * @throws WebAppConfigurationException
     * @throws IOException
     */
    public boolean buildHostPart(Appendable buffer, String uri, Boolean useSSL, Boolean controller) throws WebAppConfigurationException, IOException {
        // SCIPIO: support Boolean
        useSSL = Boolean.TRUE.equals(useSSL); // default false
        controller = !Boolean.FALSE.equals(controller); // default true // SCIPIO: re-fixed 2017-11-17

        boolean makeSecure = useSSL;
        // SCIPIO: only lookup in controller if controller lookup requested
        if (controller) {
            String requestMapUri = null;
            if (UtilValidate.isNotEmpty(uri)) { // SCIPIO: added null check and controller test
                String[] pathElements = uri.split("/");
                requestMapUri = pathElements[0];
                int queryIndex = requestMapUri.indexOf("?");
                if (queryIndex != -1) {
                    requestMapUri = requestMapUri.substring(0, queryIndex);
                }
            }
            RequestMap requestMap = null;
            if (config != null) {
                requestMap = config.getRequestMapMap().get(requestMapUri);
            }
            if (!makeSecure && requestMap != null) { // if the request has security="true" then use it
                makeSecure = requestMap.securityHttps;
            }
        }
        makeSecure = webSiteProps.getEnableHttps() & makeSecure;
        if (makeSecure) {
            String server = webSiteProps.getHttpsHost();
            if (server.isEmpty()) {
                server = "localhost";
            }
            buffer.append("https://");
            buffer.append(server);
            if (!webSiteProps.getHttpsPort().isEmpty()) {
                // SCIPIO: only append port if it's not default, otherwise not required and ugly
                if (!"443".equals(webSiteProps.getHttpsPort())) {
                    buffer.append(":").append(webSiteProps.getHttpsPort());
                }
            }
        } else {
            String server = webSiteProps.getHttpHost();
            if (server.isEmpty()) {
                server = "localhost";
            }
            buffer.append("http://");
            buffer.append(server);
            if (!webSiteProps.getHttpPort().isEmpty()) {
                // SCIPIO: only append port if it's not default, otherwise not required and ugly
                if (!"80".equals(webSiteProps.getHttpsPort())) {
                    buffer.append(":").append(webSiteProps.getHttpPort());
                }
            }
        }
        return makeSecure;
    }

    /**
     * Builds a partial URL - including the scheme and host, but not the servlet path or resource.
     * <p>
     * SCIPIO: This is the original overload that assumes controller is to be used. Also accepts Boolean instead of boolean.
     *
     * @param buffer
     * @param uri
     * @param useSSL Default value to use - will be replaced by request-map setting
     * if one is found with security=true set.
     * @return <code>true</code> if the URL uses https
     * @throws WebAppConfigurationException
     * @throws IOException
     */
    public boolean buildHostPart(Appendable buffer, String uri, Boolean useSSL) throws WebAppConfigurationException, IOException {
        return buildHostPart(buffer, uri, useSSL, true);
    }

    /**
     * SCIPIO: Builds a partial URL - including the scheme and host, but not the servlet path or resource.
     * Does NOT consult controller. useSSL false by default.
     * Added 2017-11-17.
     */
    public boolean buildHostPart(Appendable buffer, Boolean useSSL) throws WebAppConfigurationException, IOException {
        return buildHostPart(buffer, null, useSSL, false);
    }

    /**
     * Builds a partial URL - including the servlet path and resource, but not the scheme or host.
     * <p>
     * SCIPIO: 2018-08-01: If uri is null, this only appends up to the servlet path, with no trailing slash.
     * If uri is empty string, does the same but appends trailing slash.
     *
     * @param buffer
     * @param uri
     * @param appendDirSep (SCIPIO) If true, leaves a trailing slash if uri null, empty or starts with a param string (before the params); 
     *                      if null (legacy scipio), same as true except if uri is null, appendDirSep is interpreted as false (next case);
     *                      if false, slash is not added unless required by the uri
     * @throws WebAppConfigurationException
     * @throws IOException
     */
    public void buildPathPart(Appendable buffer, String uri, Boolean appendDirSep) throws WebAppConfigurationException, IOException {
        if (servletPath == null) {
            throw new IllegalStateException("Servlet path is unknown");
        }
        appendPathPart(buffer, uri, appendDirSep,
                webSiteProps.isWebappPathPrefixUrlBuild() ? webSiteProps.getWebappPathPrefix()+servletPath : servletPath); // SCIPIO
    }

    /**
     * Builds a partial URL - including the servlet path and resource, but not the scheme or host.
     * <p>
     * SCIPIO: 2018-08-01: If uri is null, this only appends up to the servlet path, with no trailing slash.
     * If uri is empty string, does the same but appends trailing slash.
     *
     * @param buffer
     * @param uri
     * @throws WebAppConfigurationException
     * @throws IOException
     */
    public void buildPathPart(Appendable buffer, String uri) throws WebAppConfigurationException, IOException {
        buildPathPart(buffer, uri, null);
    }
    
    public void buildPathPartNoPathPrefix(Appendable buffer, String uri, Boolean appendDirSep) throws WebAppConfigurationException, IOException {
        if (servletPath == null) {
            throw new IllegalStateException("Servlet path is unknown");
        }
        appendPathPart(buffer, uri, appendDirSep, servletPath); // SCIPIO
    }

    public void buildPathPartNoPathPrefix(Appendable buffer, String uri) throws WebAppConfigurationException, IOException {
        buildPathPartNoPathPrefix(buffer, uri, null);
    }
    
    /**
     * SCIPIO: Builds path part up to servlet path.
     * Alias for {@link #buildPathPart(Appendable, String, Boolean)}.
     * Added 2018-08-01.
     * @param buffer
     * @param uri
     * @param appendDirSep (SCIPIO) If true, leaves a trailing slash if uri null, empty or starts with a param string (before the params); 
     *                      if null (legacy scipio), same as true except if uri is null, appendDirSep is interpreted as false (next case);
     *                      if false, slash is not added unless required by the uri
     */
    public void buildPathPartWithServletPath(Appendable buffer, String uri, Boolean appendDirSep) throws WebAppConfigurationException, IOException {
        buildPathPart(buffer, uri, appendDirSep);
    }
    
    /**
     * SCIPIO: Builds path part up to servlet path.
     * Alias for {@link #buildPathPart(Appendable, String)}.
     * Added 2018-08-01.
     */
    public void buildPathPartWithServletPath(Appendable buffer, String uri) throws WebAppConfigurationException, IOException {
        buildPathPart(buffer, uri, null);
    }

    /**
     * SCIPIO: Builds path part up to servlet path, with no trailing slash.
     * Added 2018-08-01.
     */
    public void buildPathPartWithServletPath(Appendable buffer) throws WebAppConfigurationException, IOException {
        buildPathPart(buffer, null, false);
    }

    /**
     * SCIPIO: Adds a path to the buffer, handling slash (/).
     * WARN: This assumes the buffer is a StringBuilder, StringWriter, or other whose toString()
     * returns the url, and not some other type of Writer.
     * Added 2018-07-09.
     */
    private static void appendPathPart(Appendable buffer, String path, Boolean appendDirSep, String prefix) throws IOException {
        // Test if adding a slash between (buffer+prefix) and (path) is needed
        if ((appendDirSep == null && path != null) || Boolean.TRUE.equals(appendDirSep)
                || (UtilValidate.isNotEmpty(path) && !RequestLinkUtil.isUrlDelimNonDir(path.charAt(0)))) {
            // Slash required or requested
            if (path == null) {
                path = "";
            }
            buffer.append(prefix);
            if (!StringUtil.endsWith(buffer, '/') && !(path != null && StringUtil.startsWith(path, '/'))) {
                buffer.append('/');
            }
            buffer.append(path);
        } else {
            // No slash required or requested
            // NOTE: If (appendDirSep==false), path never starts with "/" here (due to isUrlDelimNonDir)
            StringUtil.appendWithoutSuffix(buffer, prefix, '/');
            if (path != null) {
                buffer.append(path);
            }
        }
    }

    /**
     * SCIPIO: Builds a partial URL - including the context path, but not the scheme or host or servlet.
     * <p>
     * 2018-08-01: If uri is null, this only appends up to the context root, with no trailing slash.
     * If uri is empty string, does the same but appends trailing slash.
     * @param buffer
     * @param uri
     * @param appendDirSep (SCIPIO) If true, leaves a trailing slash if uri null, empty or starts with a param string (before the params); 
     *                      if null (legacy scipio), same as true except if uri is null, appendDirSep is interpreted as false (next case);
     *                      if false, slash is not added unless required by the uri (uri is non-empty and starts with a non-separator)
     */
    public void buildPathPartWithContextPath(Appendable buffer, String uri, Boolean appendDirSep) throws WebAppConfigurationException, IOException {
        if (contextPath == null) {
            throw new IllegalStateException("Context path is unknown; there may be no webSiteId or webapp available to determine"); // SCIPIO: message
        }
        appendPathPart(buffer, uri, appendDirSep,
                webSiteProps.isWebappPathPrefixUrlBuild() ? webSiteProps.getWebappPathPrefix()+contextPath : contextPath); // SCIPIO
    }


    /**
     * SCIPIO: Builds a partial URL - including the context path, but not the scheme or host or servlet.
     * <p>
     * 2018-08-01: If uri is null, this only appends up to the context root, with no trailing slash.
     * If uri is empty string, does the same but appends trailing slash.
     */
    public void buildPathPartWithContextPath(Appendable buffer, String uri) throws WebAppConfigurationException, IOException {
        buildPathPartWithContextPath(buffer, uri, null);
    }

    /**
     * SCIPIO: Builds path part up to webapp context root, with no trailing slash.
     * Added 2018-08-01.
     */
    public void buildPathPartWithContextPath(Appendable buffer) throws WebAppConfigurationException, IOException {
        buildPathPartWithContextPath(buffer, null, false);
    }

    /**
     * SCIPIO: Builds a partial URL - including the webapp path prefix, but not the context path.
     * <p>
     * WARN: This overload should only be used by very specific implementations,
     * because it usually makes no sense to append the webappPathPrefix with a contextPath
     * different than the one from the same target webapp.
     * <p>
     * 2018-08-01: If uri is null, this only appends up to the webapp path prefix, with no trailing slash.
     * If uri is empty string, does the same but appends trailing slash.
     * @param buffer
     * @param uri
     * @param appendDirSep (SCIPIO) If true, leaves a trailing slash if uri null, empty or starts with a param string (before the params); 
     *                      if null (legacy scipio), same as true except if uri is null, appendDirSep is interpreted as false (next case);
     *                      if false, slash is not added unless required by the uri
     */
    public void buildPathPartWithWebappPathPrefix(Appendable buffer, String uri, Boolean appendDirSep) throws WebAppConfigurationException, IOException {
        appendPathPart(buffer, uri, appendDirSep,
                webSiteProps.isWebappPathPrefixUrlBuild() ? webSiteProps.getWebappPathPrefix() : ""); // SCIPIO
    }

    /**
     * SCIPIO: Builds a partial URL - including the webapp path prefix, but not the context path.
     * <p>
     * WARN: This overload should only be used by very specific implementations,
     * because it usually makes no sense to append the webappPathPrefix with a contextPath
     * different than the one from the same target webapp.
     * <p>
     * 2018-08-01: If uri is null, this only appends up to the webapp path prefix, with no trailing slash.
     * If uri is empty string, does the same but appends trailing slash.
     */
    public void buildPathPartWithWebappPathPrefix(Appendable buffer, String uri) throws WebAppConfigurationException, IOException {
        buildPathPartWithWebappPathPrefix(buffer, uri, null);

    }

    /**
     * SCIPIO: Builds path part up to webapp path prefix, with no trailing slash.
     * <p>
     * WARN: This overload should only be used by very specific implementations,
     * because it usually makes no sense to append the webappPathPrefix with a contextPath
     * different than the one from the same target webapp.
     * <p>
     * Added 2018-08-01.
     */
    public void buildPathPartWithWebappPathPrefix(Appendable buffer) throws WebAppConfigurationException, IOException {
        buildPathPartWithWebappPathPrefix(buffer, null, false);
    }

    /**
     * SCIPIO: Returns the WebSiteProperties this builder is using.
     * Added 2018-08-03.
     */
    public WebSiteProperties getWebSiteProperties() {
        return webSiteProps;
    }

    /**
     * SCIPIO: Returns the ControllerConfig this builder is using. May be null.
     * Added 2018-08-03.
     */
    public ControllerConfig getControllerConfig() {
        return config;
    }

    /**
     * SCIPIO: Get servlet path including the webapp context path, but NOT
     * including the webappPathPrefix ({@link #getWebappPathPrefix()});
     * <p>
     * NOTE: 2018-08-01: Excludes terminating slash.
     */
    public String getContextAndServletPath() {
        return servletPath;
    }

    /**
     * SCIPIO: Get webapp context path, but NOT
     * including the webappPathPrefix ({@link #getWebappPathPrefix()});
     * <p>
     * NOTE: 2018-08-01: Excludes terminating slash.
     */
    public String getContextPath() {
        return contextPath;
    }

    /**
     * SCIPIO: Get the webapp path prefix (e.g. from WebSiteProperties or other).
     * Added 2018-07-27.
     */
    public String getWebappPathPrefix() {
        return webSiteProps.getWebappPathPrefix();
    }

    /**
     * SCIPIO: Determines if the webapp path prefix is supposed to and being included
     * in this URL building; otherwise assumed to be done by URL rewriting later.
     */
    public boolean isWebappPathPrefixUrlBuild() {
        return webSiteProps.isWebappPathPrefixUrlBuild();
    }
}

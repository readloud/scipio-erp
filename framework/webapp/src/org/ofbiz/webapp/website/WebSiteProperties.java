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
package org.ofbiz.webapp.website;

import javax.servlet.http.HttpServletRequest;

import org.ofbiz.base.lang.ThreadSafe;
import org.ofbiz.base.start.Start;
import org.ofbiz.base.util.Assert;
import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilProperties;
import org.ofbiz.entity.Delegator;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.util.EntityQuery;
import org.ofbiz.entity.util.EntityUtilProperties;
import org.ofbiz.webapp.ExtWebappInfo;
import org.ofbiz.webapp.control.RequestLinkUtil;

/**
 * Web site properties.
 * <p>
 * SCIPIO: NOTE: 2018-08: It is recommended not to load this class directly anymore
 * but instead to go through {@link org.ofbiz.webapp.FullWebappInfo} and for link-building
 * obtain its {@link org.ofbiz.webapp.OfbizUrlBuilder}.
 * Future improvements will likely be abstracted by those classes rather than by this one
 * (due to its limited scope).
 */
@ThreadSafe
public final class WebSiteProperties {

    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());

    /**
     * Returns a <code>WebSiteProperties</code> instance initialized to the settings found
     * in the <code>url.properties</code> file.
     */
    public static WebSiteProperties defaults(Delegator delegator) {
        return new WebSiteProperties(delegator);
    }

    /**
     * SCIPIO: Returns a <code>WebSiteProperties</code> instance initialized to the settings found
     * in the <code>url.properties</code> file. Cached in request.
     * Added 2018-07-31.
     */
    public static WebSiteProperties defaults(HttpServletRequest request) {
        Assert.notNull("request", request);
        WebSiteProperties webSiteProps = (WebSiteProperties) request.getAttribute("_DEF_WEBSITE_PROPS_");
        if (webSiteProps == null) {
            Delegator delegator = (Delegator) request.getAttribute("delegator");
            webSiteProps = new WebSiteProperties(delegator);
            request.setAttribute("_DEF_WEBSITE_PROPS_", webSiteProps);
        }
        return webSiteProps;
    }

    /**
     * Returns a <code>WebSiteProperties</code> instance initialized to the settings found
     * in the application's WebSite entity value. If the application does not have a
     * WebSite entity value then the instance is initialized to the settings found
     * in the <code>url.properties</code> file.
     * SCIPIO: Intended for intra-webapp operations only. Cached in request.
     *
     * @param request
     * @throws GenericEntityException
     */
    public static WebSiteProperties from(HttpServletRequest request) throws GenericEntityException, WebSiteEntityNotFoundException {
        Assert.notNull("request", request);
        WebSiteProperties webSiteProps = (WebSiteProperties) request.getAttribute("_WEBSITE_PROPS_");
        if (webSiteProps == null) {

            // SCIPIO: 2018-09-25: Use ExtWebappInfo from the current request (by contextPath) in all cases,
            // even when there is a webSiteId, to prevent this code giving issues with the
            // poorly or (un-)suppported 1-to-many webapp->webSiteId mappings
            ExtWebappInfo extWebappInfo = null;
            try {
                extWebappInfo = ExtWebappInfo.fromRequest(request);
            } catch(Exception e) {
                Debug.logError("Could not get webapp information (web.xml) for current request: " + e.toString(), module);
            }
            Delegator delegator = (Delegator) request.getAttribute("delegator");
            GenericValue webSiteValue = null;
            String webSiteId = WebSiteWorker.getWebSiteId(request);
            if (webSiteId != null) {
                delegator = (Delegator) request.getAttribute("delegator");
                webSiteValue = EntityQuery.use(delegator).from("WebSite").where("webSiteId", webSiteId).cache().queryOne();
                if (webSiteValue == null) {
                    // SCIPIO (12/04/2018): Throwing this new WebSiteException so it can be caught in GlobalDecorator early stages
                    throw new WebSiteEntityNotFoundException("Scipio: Could not find WebSite '" + webSiteId + "'", webSiteId);
                }
                // 2018-09-25: emergency fallback case: this should not happen, but will help both debugging and emergency cases work
                if (extWebappInfo == null) {
                    Debug.logWarning("Looking up current request ExtWebappInfo using webSiteId '" 
                            + webSiteId + "' as fallback; this should not normally be needed"
                            + "; please ensure your ofbiz-component.xml webapp configurations are correct"
                            + "; if they appear correct, please report this issue", module);
                    try {
                        extWebappInfo = ExtWebappInfo.fromWebSiteId(webSiteId);
                    } catch(Exception e) {
                        Debug.logError("Could not get webapp information (web.xml) for webSiteId: " + webSiteId, module);
                    }
                }
            }
            // sanity check (this is cached so acceptable)
            if (extWebappInfo != null) {
                if ((webSiteId != null && !webSiteId.equals(extWebappInfo.getWebSiteId())) || 
                    (webSiteId == null && extWebappInfo.getWebSiteId() != null)) {
                    Debug.logError("Sanity check failed: current request webSiteId (" + webSiteId 
                        + ") does not match detected current webapp info webSiteId (" + extWebappInfo.getWebSiteId()
                        + ", webapp: " + extWebappInfo.toString() + "); please verify configuration and report this issue", module);
                }
            }
            webSiteProps = newFrom(webSiteValue, extWebappInfo, request, delegator);

            request.setAttribute("_WEBSITE_PROPS_", webSiteProps);
        }
        return webSiteProps;
    }

    /**
     * SCIPIO: Shared factory for intra- and inter-webapp live request links.
     * <p>
     * DEV NOTE: WARN: Here, always get info from the target extWebappInfo (if non null) and not from request,
     * except for server path settings.
     * <p>
     * DEV NOTE: WARN: Avoid lookups by webSiteId if contextPath is available.
     */
    private static WebSiteProperties newFrom(String webSiteId, HttpServletRequest request) throws GenericEntityException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        ExtWebappInfo extWebappInfo = null;
        GenericValue webSiteValue = null;
        if (webSiteId != null) {
            try {
                extWebappInfo = ExtWebappInfo.fromWebSiteId(webSiteId);
            } catch(Exception e) {
                Debug.logWarning("Could not get webapp information (web.xml) for webSiteId '" + webSiteId + "': " + e.toString(), module);
            }
            webSiteValue = EntityQuery.use(delegator).from("WebSite").where("webSiteId", webSiteId).cache().queryOne();
            if (webSiteValue == null) {
                throw new GenericEntityException("Scipio: Could not find WebSite for webSiteId '" + webSiteId + "'");
            }
        } else {
            ; // not needed in this case yet (TODO? if new features only)
        }
        return newFrom(webSiteValue, extWebappInfo, request, delegator);
    }

    /**
     * SCIPIO: Shared factory for intra- and inter-webapp live request links.
     * <p>
     * DEV NOTE: WARN: Here, always get info from the target extWebappInfo (if non null) and not from request,
     * except for server path settings.
     */
    private static WebSiteProperties newFrom(GenericValue webSiteValue, ExtWebappInfo extWebappInfo, HttpServletRequest request, Delegator delegator) throws GenericEntityException {
        boolean interWebapp;
        if (extWebappInfo != null) {
            interWebapp = !extWebappInfo.isRequestWebapp(request);
        } else if (webSiteValue != null) { // emergency fallback detection (this should practically never happen)
            interWebapp = !webSiteValue.getString("webSiteId").equals(WebSiteWorker.getWebSiteId(request));
        } else {
            interWebapp = false;
        }

        WebSiteProperties defaults = defaults(request); // SCIPIO: defaults now cached in request

        boolean overrideRequestHostPort = "Y".equalsIgnoreCase(EntityUtilProperties.getPropertyValue("url.properties", "override.request.host.port", delegator));
        boolean requestOverridesStatic = !overrideRequestHostPort;

        // SCIPIO: SPECIAL CASE: If inter-webapp, we can only use the current request settings (request.getServerName(), etc.)
        // if the current webapp did not force its own settings via WebSite, because in that
        // case the current request will have those settings (returned by request.getServerName(), etc.),
        // which should not be applied to the target webapp.
        if (interWebapp) {
            try {
                WebSiteProperties currentWebSiteProps = from(request);
                if (!defaults.equalsServerFieldsWithHardDefaults(currentWebSiteProps)) {
                    requestOverridesStatic = false;
                }
            } catch (WebSiteEntityNotFoundException we) {
                throw new GenericEntityException(we);
            }
            
        }

        boolean requestOverridesStaticHttpPort = requestOverridesStatic;
        boolean requestOverridesStaticHttpHost = requestOverridesStatic;
        boolean requestOverridesStaticHttpsPort = requestOverridesStatic;
        boolean requestOverridesStaticHttpsHost = requestOverridesStatic;
        boolean requestOverridesStaticWebappPathPrefix = requestOverridesStatic;

        String httpPort = defaults.getHttpPort();
        String httpHost = defaults.getHttpHost();
        String httpsPort = defaults.getHttpsPort();
        String httpsHost = defaults.getHttpsHost();
        boolean enableHttps = defaults.getEnableHttps();
        // SCIPIO: new
        String webappPathPrefix = defaults.getWebappPathPrefix();
        String webappPathPrefixHeader = defaults.getWebappPathPrefixHeader();

        if (webSiteValue != null) {
            if (webSiteValue.get("httpPort") != null) {
                httpPort = webSiteValue.getString("httpPort");
                requestOverridesStaticHttpPort = false;
            }
            if (webSiteValue.get("httpHost") != null) {
                httpHost = webSiteValue.getString("httpHost");
                requestOverridesStaticHttpHost = false;
            }
            if (webSiteValue.get("httpsPort") != null) {
                httpsPort = webSiteValue.getString("httpsPort");
                requestOverridesStaticHttpsPort = false;
            }
            if (webSiteValue.get("httpsHost") != null) {
                httpsHost = webSiteValue.getString("httpsHost");
                requestOverridesStaticHttpsHost = false;
            }
            if (webSiteValue.get("enableHttps") != null) {
                enableHttps = webSiteValue.getBoolean("enableHttps");
            }
            if (webSiteValue.get("webappPathPrefix") != null) { // SCIPIO: new
                webappPathPrefix = webSiteValue.getString("webappPathPrefix");
                requestOverridesStaticWebappPathPrefix = false;
            }
        }

        // SCIPIO: NOTE: this has been factored and moved to before the request value lookups.
        httpPort = adjustPort(delegator, httpPort);
        httpsPort = adjustPort(delegator, httpsPort);

        boolean isSecure = RequestLinkUtil.isEffectiveSecure(request); // SCIPIO: 2018: replace request.isSecure()

        // SCIPIO: this may override the url.properties settings, though not the WebSite settings
        if ((requestOverridesStaticHttpPort || httpPort.isEmpty()) && !isSecure) {
            httpPort = String.valueOf(request.getServerPort());
        }
        if (requestOverridesStaticHttpHost || httpHost.isEmpty()) {
            httpHost = request.getServerName();
        }
        if ((requestOverridesStaticHttpsPort || httpsPort.isEmpty()) && isSecure) {
            httpsPort = String.valueOf(request.getServerPort());
        }
        if (requestOverridesStaticHttpsHost || httpsHost.isEmpty()) {
            httpsHost = request.getServerName();
        }

        if ((requestOverridesStaticWebappPathPrefix || webappPathPrefix.isEmpty()) && !webappPathPrefixHeader.isEmpty()) {
            webappPathPrefix = request.getHeader(webappPathPrefixHeader);
            if (webappPathPrefix == null) webappPathPrefix = "";
        }
        webappPathPrefix = normalizeWebappPathPrefix(webappPathPrefix);

        if (webappPathPrefixOnlyIfForward && request.getHeader("X-Forwarded-Proto") == null) {
            webappPathPrefix = "";
            webappPathPrefixHeader = "";
        }

        boolean webappPathPrefixUrlBuild = isWebappPathPrefixUrlBuild(webSiteValue, extWebappInfo, delegator);

        return new WebSiteProperties(httpPort, httpHost, httpsPort, httpsHost, enableHttps,
                webappPathPrefix, webappPathPrefixHeader, webappPathPrefixUrlBuild);
    }

    /**
     * SCIPIO: Returns web site properties for the given web site; any host or port fields
     * not specified are taken from request instead, as would be returned by {@link #from(HttpServletRequest)}.
     * Intended for inter-webapp operations. Currently NOT cached (go through {@link org.ofbiz.webapp.FullWebappInfo} for caching).
     */
    public static WebSiteProperties from(GenericValue webSiteValue, HttpServletRequest request) throws GenericEntityException {
        ExtWebappInfo extWebappInfo = null;
        try {
            extWebappInfo = ExtWebappInfo.fromWebSiteId(webSiteValue.getString("webSiteId"));
        } catch(Exception e) {
            Debug.logWarning("Could not get webapp information (web.xml) for webSiteId '"
                    + webSiteValue.getString("webSiteId") + "': " + e.toString(), module);
        }
        return newFrom(webSiteValue, extWebappInfo, request, webSiteValue.getDelegator());
    }

    /**
     * SCIPIO: Returns web site properties for the given webSiteId, or for any fields missing,
     * the values for the request or system defaults.
     * Intended for inter-webapp operations. Currently NOT cached.
     */
    public static WebSiteProperties from(String webSiteId, HttpServletRequest request) throws GenericEntityException {
        return newFrom(webSiteId, request);
    }

    /**
     * SCIPIO: Returns web site properties for the webapp, or for any fields missing,
     * the values for the current request (or system defaults).
     * Intended for inter-webapp operations. Currently NOT cached.
     * <p>
     * This overload supports extWebappInfo with null webSiteId; in this case it will mostly
     * use system defaults (other than anything configurable using only web.xml and ofbiz-component.xml).
     */
    public static WebSiteProperties from(ExtWebappInfo extWebappInfo, HttpServletRequest request) throws GenericEntityException {
        String webSiteId = extWebappInfo.getWebSiteId();
        if (webSiteId == null) {
            return newFrom(null, extWebappInfo, request, (Delegator) request.getAttribute("delegator"));
        } else {
            Delegator delegator = (Delegator) request.getAttribute("delegator");
            GenericValue webSiteValue = EntityQuery.use(delegator).from("WebSite").where("webSiteId", webSiteId).cache().queryOne();
            if (webSiteValue != null) {
                return newFrom(webSiteValue, extWebappInfo, request, delegator);
            } else {
                throw new GenericEntityException("Could not find WebSite for webSiteId '" + webSiteId + "'");
            }
        }
    }

    /**
     * Returns a <code>WebSiteProperties</code> instance initialized to the settings found
     * in the WebSite entity value.
     *
     * @param webSiteValue
     */
    public static WebSiteProperties from(GenericValue webSiteValue) {
        Assert.notNull("webSiteValue", webSiteValue);
        if (!"WebSite".equals(webSiteValue.getEntityName())) {
            throw new IllegalArgumentException("webSiteValue is not a WebSite entity value");
        }
        WebSiteProperties defaults = new WebSiteProperties(webSiteValue.getDelegator());
        String httpPort = (webSiteValue.get("httpPort") != null) ? webSiteValue.getString("httpPort") : defaults.getHttpPort();
        String httpHost = (webSiteValue.get("httpHost") != null) ? webSiteValue.getString("httpHost") : defaults.getHttpHost();
        String httpsPort = (webSiteValue.get("httpsPort") != null) ? webSiteValue.getString("httpsPort") : defaults.getHttpsPort();
        String httpsHost = (webSiteValue.get("httpsHost") != null) ? webSiteValue.getString("httpsHost") : defaults.getHttpsHost();
        boolean enableHttps = (webSiteValue.get("enableHttps") != null) ? webSiteValue.getBoolean("enableHttps") : defaults.getEnableHttps();

        // SCIPIO: factored out
        httpPort = adjustPort(webSiteValue.getDelegator(), httpPort);
        httpsPort = adjustPort(webSiteValue.getDelegator(), httpsPort);

        // SCIPIO: new
        String webappPathPrefix = (webSiteValue.get("webappPathPrefix") != null) ? normalizeWebappPathPrefix(webSiteValue.getString("webappPathPrefix")) : defaults.getWebappPathPrefix();
        String webappPathPrefixHeader = defaults.getWebappPathPrefixHeader();

        ExtWebappInfo extWebappInfo = null;
        try {
            extWebappInfo = ExtWebappInfo.fromWebSiteId(webSiteValue.getString("webSiteId"));
        } catch(Exception e) {
            Debug.logWarning("Could not get webapp information (web.xml) for webSiteId '" + webSiteValue.getString("webSiteId") + "'", module);
        }
        boolean webappPathPrefixUrlBuild = isWebappPathPrefixUrlBuild(webSiteValue, extWebappInfo, webSiteValue.getDelegator());

        return new WebSiteProperties(httpPort, httpHost, httpsPort, httpsHost, enableHttps,
                webappPathPrefix, webappPathPrefixHeader, webappPathPrefixUrlBuild);
    }

    /**
     * SCIPIO: Returns a <code>WebSiteProperties</code> instance initialized to the settings found
     * in the WebSite entity value for the given webSiteId.
     * @param webSiteId
     * @param delegator
     */
    public static WebSiteProperties from(String webSiteId, Delegator delegator) throws GenericEntityException {
        Assert.notNull("webSiteId", webSiteId);
        GenericValue webSiteValue = EntityQuery.use(delegator).from("WebSite").where("webSiteId", webSiteId).cache().queryOne();
        if (webSiteValue != null) {
            return from(webSiteValue);
        } else {
            throw new GenericEntityException("Scipio: Could not find WebSite for webSiteId '" + webSiteId + "'");
        }
    }

    private final String httpPort;
    private final String httpHost;
    private final String httpsPort;
    private final String httpsHost;
    private final boolean enableHttps;

    private final String webappPathPrefix; // SCIPIO: added 2018-07-27
    private final String webappPathPrefixHeader; // SCIPIO: added 2018-07-27
    private final boolean webappPathPrefixUrlBuild; // SCIPIO: 2018-08-10
    private static final boolean webappPathPrefixOnlyIfForward = UtilProperties.getPropertyAsBoolean("url", "webapp.url.path.prefix.onlyIfForward", false); // SCIPIO: added 2018-07-27
    private static final boolean defaultWebappPathPrefixUrlBuild = UtilProperties.getPropertyAsBoolean("url", "webapp.url.path.prefix.urlBuild", true); // SCIPIO

    private WebSiteProperties(Delegator delegator) {
        this.httpPort = EntityUtilProperties.getPropertyValue("url", "port.http", delegator);
        this.httpHost = EntityUtilProperties.getPropertyValue("url", "force.http.host", delegator);
        this.httpsPort = EntityUtilProperties.getPropertyValue("url", "port.https", delegator);
        this.httpsHost = EntityUtilProperties.getPropertyValue("url", "force.https.host", delegator);
        this.enableHttps = EntityUtilProperties.propertyValueEqualsIgnoreCase("url", "port.https.enabled", "Y", delegator);

        this.webappPathPrefix = normalizeWebappPathPrefix(EntityUtilProperties.getPropertyValue("url", "webapp.url.path.prefix", delegator)); // SCIPIO
        this.webappPathPrefixHeader = EntityUtilProperties.getPropertyValue("url", "webapp.url.path.prefix.httpHeader", delegator); // SCIPIO
        this.webappPathPrefixUrlBuild = defaultWebappPathPrefixUrlBuild;
    }

    private WebSiteProperties(String httpPort, String httpHost, String httpsPort, String httpsHost, boolean enableHttps,
            String webappPathPrefix, String webappPathPrefixHeader, boolean webappPathPrefixUrlBuild) { // SCIPIO: new fields
        this.httpPort = httpPort;
        this.httpHost = httpHost;
        this.httpsPort = httpsPort;
        this.httpsHost = httpsHost;
        this.enableHttps = enableHttps;

        this.webappPathPrefix = webappPathPrefix;
        this.webappPathPrefixHeader = webappPathPrefixHeader;
        this.webappPathPrefixUrlBuild = webappPathPrefixUrlBuild;
    }

    /**
     * Returns the configured http port, or an empty <code>String</code> if not configured.
     */
    public String getHttpPort() {
        return httpPort;
    }

    /**
     * Returns the configured http host, or an empty <code>String</code> if not configured.
     */
    public String getHttpHost() {
        return httpHost;
    }

    /**
     * Returns the configured https port, or an empty <code>String</code> if not configured.
     */
    public String getHttpsPort() {
        return httpsPort;
    }

    /**
     * Returns the configured https host, or an empty <code>String</code> if not configured.
     */
    public String getHttpsHost() {
        return httpsHost;
    }

    /**
     * Returns <code>true</code> if https is enabled.
     */
    public boolean getEnableHttps() {
        return enableHttps;
    }

    /**
     * SCIPIO: Returns the webapp/navigation URL path prefix.
     * <p>
     * DEV NOTE: Prefer using {@link org.ofbiz.webapp.OfbizUrlBuilder} methods over calling this.
     */
    public String getWebappPathPrefix() {
        return webappPathPrefix;
    }

    /**
     * SCIPIO: Returns the webapp/navigation URL path prefix HTTP header name.
     * TODO: REVIEW: protected for now; probably no reason for any other class to use.
     */
    protected String getWebappPathPrefixHeader() {
        return webappPathPrefixHeader;
    }

    /**
     * SCIPIO: If true, the webappPathPrefix should be included in URL building
     * code by default; if false, it is left up to URL rewriting to append it.
     * <p>
     * NOTE: 2018-08-03: At current time this setting is stored only in url.properties
     * and web.xml, NOT the WebSite entity, to reflect its coded nature.
     */
    public boolean isWebappPathPrefixUrlBuild() {
        return webappPathPrefixUrlBuild;
    }

    private static boolean isWebappPathPrefixUrlBuild(GenericValue webSiteValue, ExtWebappInfo webappInfo, Delegator delegator) {
        if (webappInfo == null) return defaultWebappPathPrefixUrlBuild;
        return UtilMisc.booleanValue(webappInfo.getContextParams().get("urlWebappPathPrefixUrlBuild"), defaultWebappPathPrefixUrlBuild);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{httpPort=");
        sb.append(httpPort).append(", ");
        sb.append("httpHost=").append(httpHost).append(", ");
        sb.append("httpsPort=").append(httpsPort).append(", ");
        sb.append("httpsHost=").append(httpsHost).append(", ");
        // SCIPIO
        //sb.append("enableHttps=").append(enableHttps).append("}");
        sb.append("enableHttps=").append(enableHttps).append(", ");
        sb.append("webappPathPrefix=").append(webappPathPrefix).append(", ");
        sb.append("webappPathPrefixHeader=").append(webappPathPrefixHeader);
        sb.append("}");
        return sb.toString();
    }

    /**
     * SCIPIO: Returns true if and only if all fields in this object match
     * the ones in the other WebSiteProperties.
     *
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object other) {
        return equalsServerFields(other) &&
               sameFields(this.webappPathPrefix, ((WebSiteProperties) other).webappPathPrefix) &&
               sameFields(this.webappPathPrefixHeader, ((WebSiteProperties) other).webappPathPrefixHeader);
    }

    /**
     * SCIPIO: Returns true if and only if all fields in this object match
     * the ones in the other WebSiteProperties. Fields which are missing,
     * such as hosts or ports, are substituted with hardcoded Ofbiz defaults when
     * performing the comparison.
     * <p>
     * Currently, the hard defaults are "localhost" for host fields, "80" for httpPort
     * and "443" for httpsPort.
     *
     * @see java.lang.Object#equals(java.lang.Object)
     */
    public boolean equalsWithHardDefaults(Object other) {
        return equalsServerFieldsWithHardDefaults(other) &&
               sameFields(this.webappPathPrefix, ((WebSiteProperties) other).webappPathPrefix) &&
               sameFields(this.webappPathPrefixHeader, ((WebSiteProperties) other).webappPathPrefixHeader);
    }

    /**
     * SCIPIO: Returns true if and only if all server-related fields in this object match
     * the ones in the other WebSiteProperties.
     */
    public boolean equalsServerFields(Object other) {
        if (this == other) {
            return true;
        } else if (other == null) {
            return false;
        } else if (!(other instanceof WebSiteProperties)) {
            return false;
        }
        WebSiteProperties o = (WebSiteProperties) other;
        return sameFields(this.httpHost, o.httpHost) &&
               sameFields(this.httpPort, o.httpPort) &&
               sameFields(this.httpsHost, o.httpsHost) &&
               sameFields(this.httpsPort, o.httpsPort) &&
               (this.enableHttps == o.enableHttps);
    }

    /**
     * SCIPIO: Returns true if and only if all server-related fields in this object match
     * the ones in the other WebSiteProperties. Fields which are missing,
     * such as hosts or ports, are substituted with hardcoded Ofbiz defaults when
     * performing the comparison.
     * <p>
     * Currently, the hard defaults are "localhost" for host fields, "80" for httpPort
     * and "443" for httpsPort.
     */
    public boolean equalsServerFieldsWithHardDefaults(Object other) {
        if (this == other) {
            return true;
        } else if (other == null) {
            return false;
        } else if (!(other instanceof WebSiteProperties)) {
            return false;
        }
        WebSiteProperties o = (WebSiteProperties) other;
        return sameFields(this.httpHost, o.httpHost, "localhost") &&
               sameFields(this.httpPort, o.httpPort, "80") &&
               sameFields(this.httpsHost, o.httpsHost, "localhost") &&
               sameFields(this.httpsPort, o.httpsPort, "443") &&
               (this.enableHttps == o.enableHttps);
    }

    private static boolean sameFields(String first, String second) {
        // SCIPIO: treat null and empty the same, just to be safe
        if (first != null && !first.isEmpty()) {
            return first.equals(second);
        } else {
            return (second == null || second.isEmpty());
        }
    }

    private static boolean sameFields(String first, String second, String defaultVal) {
        if (first == null || first.isEmpty()) {
            first = defaultVal;
        }
        if (second == null || second.isEmpty()) {
            second = defaultVal;
        }
        return first.equals(second);
    }

    /**
     * SCIPIO: Adjusts the given port value (as string) by the port offset configuration value, if applicable.
     */
    public static String adjustPort(Delegator delegator, String port) {
        if (port != null && !port.isEmpty() && Start.getInstance().getConfig().portOffset != 0) {
            Integer portValue = Integer.valueOf(port);
            portValue += Start.getInstance().getConfig().portOffset;
            return portValue.toString();
        } else {
            return port;
        }
    }

    /**
     * SCIPIO: Adjusts the given port value by the port offset configuration value, if applicable.
     */
    public static Integer adjustPort(Delegator delegator, Integer port) {
        if (port != null && Start.getInstance().getConfig().portOffset != 0) {
            return port + Start.getInstance().getConfig().portOffset;
        } else {
            return port;
        }
    }

    private static String normalizeWebappPathPrefix(String path) { // SCIPIO: 2018-07-27
        if ("/".equals(path)) return "";
        // nobody will do this
        //else if (path.endsWith("/")) return path.substring(0, path.length() - 1);
        else return path;
    }
}

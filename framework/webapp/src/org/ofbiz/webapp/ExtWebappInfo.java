package org.ofbiz.webapp;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;

import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.WebXml;
import org.ofbiz.base.component.ComponentConfig;
import org.ofbiz.base.component.ComponentConfig.WebappInfo;
import org.ofbiz.base.lang.ThreadSafe;
import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilProperties;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.webapp.control.ConfigXMLReader;
import org.ofbiz.webapp.control.ConfigXMLReader.ControllerConfig;
import org.ofbiz.webapp.control.ContextFilter;
import org.xml.sax.SAXException;

/**
 * SCIPIO: Extended static webapp info class, which contains both component WebappInfo
 * and WebXml info in a single class linked to the webSiteId in a global cache.
 * Should only be used for webapps that have a defined webSiteId (in web.xml).
 * In general, this represents a server webapp from the point of the view of 
 * the webserver (instead of from of components' point of view - see note below).
 * <p>
 * It will cache several settings to avoid constantly re-traversing the WebXml
 * and preload a bunch of calls from {@link WebAppUtil}.
 * <p>
 * This does not contain DB data such as WebSiteProperties, only statically-accessible
 * files and configuration.
 * <p>
 * NOTE: The first accesses have to be done at late enough times in the loading, because
 * the instances are cached, and it would be bad to cache partially-loaded results.
 * In other words this should not be accessed within filter or servlet init methods.
 * If you need an instance in such times, call the {@link #fromWebSiteIdNew} method.
 * TODO?: We could code the webapp loader to invoke {@link #clearCaches()} at end of loading
 * to avoid issues.
 * <p>
 * NOTE: 2019-09-25: For the purposes of this class, a ExtWebappInfo is considered equal
 * to another if it has the same {@link #getContextPath()} and {@link #getServerId()},
 * in other words if {@link #isSameServerWebapp(ExtWebappInfo)} returns true,
 * in other words they are the same webapp from the perspective of the webserver (not the components).
 * This is slightly different from {@link org.ofbiz.base.component.ComponentConfig.WebappInfo}
 * because of the webapp overriding that one has to deal with, meaning it is more concerned with
 * the webapps from the components' perspective.
 * In other words, ExtWebappInfo could have been better named "ServerWebappInfo".
 */
@SuppressWarnings("serial")
@ThreadSafe
public class ExtWebappInfo implements Serializable {

    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());

    public static final String DEFAULT_WEBAPP_URLREWRITE_FILE = "WEB-INF/urlrewrite.xml";

    private static final Object cacheLock = new Object(); // NOTE: both caches lock together
    private static Map<String, ExtWebappInfo> webSiteIdCache = Collections.emptyMap();
    private static Map<String, ExtWebappInfo> serverContextPathCache = Collections.emptyMap();

    // REQUIRED FIELDS (for this instance to exist)
    private final WebappInfo webappInfo;
    private final WebXml webXml;

    // OPTIONAL FIELDS (instance may be created/cached even if not set or lookup fails)
    private final String webSiteId;
    private final String controlServletPath; // empty string for root
    private final String controlServletMapping; // single slash for root
    private final String fullControlPath; // with context root and trailing slash

    private Optional<Boolean> forwardRootControllerUris;
    private Optional<Boolean> forwardRootControllerUrisValid;

    private final boolean urlRewriteFilter;
    private final String urlRewriteConfPath;
    private final String urlRewriteFullConfPath;
    private final String urlRewriteRealConfPath;

    private final boolean urlManualInterWebappFilter;

    private final Map<String, String> contextParams; // Added 2018-09-25

    /**
     * Main constructor.
     */
    protected ExtWebappInfo(String webSiteId, WebappInfo webappInfo, WebXml webXml) {
        // sanity check
        if (webappInfo == null) throw new IllegalArgumentException("Missing ofbiz webapp info (WebappInfo) for website ID '" + webSiteId + "' - required to instantiate ExtWebappInfo");
        if (webXml == null) throw new IllegalArgumentException("Missing webapp container info (web.xml) for website ID '" + webSiteId + "' (mount-point '" + webappInfo.getContextRoot() + "')");

        this.webSiteId = webSiteId;
        this.webappInfo = webappInfo;
        this.webXml = webXml;
        // 2018-09-25: we need to cache the results; note that the returned map is already unmodifiable
        this.contextParams = WebAppUtil.getWebappContextParams(this.webappInfo, this.webXml);
        try {
            this.fullControlPath = WebAppUtil.getControlServletPath(webappInfo, true);
            if (this.fullControlPath == null) {
                Debug.logInfo(getLogMsgPrefix()+"No ControlServlet mapping for website"
                        + " (this is only an error if the website was meant to have a controller)", module);
            }

            this.controlServletMapping = WebAppUtil.getControlServletOnlyPathFromFull(webappInfo, fullControlPath);
            this.controlServletPath = ("/".equals(this.controlServletMapping)) ? "" : this.controlServletMapping;
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not determine ControlServlet mapping for website ID '" + webSiteId + "': " + e.getMessage(), e);
        }

        this.urlRewriteConfPath = getUrlRewriteConfPathFromWebXml(webXml);
        if (this.urlRewriteConfPath != null) {
            this.urlRewriteFullConfPath = Paths.get(webappInfo.getLocation(), this.urlRewriteConfPath).toString();
            if (new File(this.urlRewriteFullConfPath).exists()) {
                this.urlRewriteRealConfPath = this.urlRewriteFullConfPath;
            } else {
                Debug.logWarning("Webapp " + this.toString() + " has UrlRewriterFilter configured"
                        + ", but no urlrewrite xml file could be found at expected location: " + this.urlRewriteFullConfPath, module);
                this.urlRewriteRealConfPath = null;
            }
        } else {
            this.urlRewriteFullConfPath = null;
            this.urlRewriteRealConfPath = null;
        }
        this.urlRewriteFilter = (this.urlRewriteRealConfPath != null);

        String urlManualInterWebappFilterStr = getContextParams().get("urlManualInterWebappFilter");
        if (UtilValidate.isNotEmpty(urlManualInterWebappFilterStr)) {
            this.urlManualInterWebappFilter = UtilMisc.booleanValueVersatile(urlManualInterWebappFilterStr,
                    (this.urlRewriteRealConfPath == null));
        } else {
            this.urlManualInterWebappFilter = UtilProperties.getPropertyAsBoolean("url", "webapp.url.interwebapp.manualFilter", (this.urlRewriteRealConfPath == null));
        }
    }

    /**
     * Clears all the ExtWebSiteInfo global caches.
     */
    public static void clearCaches() {
        synchronized(cacheLock) { // this only prevents other thread from accidentally restoring the map we just deleted
            webSiteIdCache = Collections.emptyMap();
            serverContextPathCache = Collections.emptyMap();
        }
    }

    /**
     * Gets from webSiteId, with caching.
     * Cache only allows websites with registered WebappInfo and WebXml.
     * NOTE: If accessing from early loading process, do not call this, instead call
     * {@link #fromWebSiteIdNew(String)}, otherwise there is a risk of caching
     * incomplete instances.
     */
    public static ExtWebappInfo fromWebSiteId(String webSiteId) throws IllegalArgumentException {
        ExtWebappInfo info = webSiteIdCache.get(webSiteId);
        if (info != null) return info;
        synchronized(cacheLock) {
            info = webSiteIdCache.get(webSiteId);
            if (info != null) return info;

            info = fromWebSiteIdNew(webSiteId);

            // copy cache for synch semantics
            Map<String, ExtWebappInfo> newCache = new HashMap<>(webSiteIdCache);
            newCache.put(webSiteId, info);
            webSiteIdCache = Collections.unmodifiableMap(newCache);

            // also put into context root cache to prevent duplicates
            newCache = new HashMap<>(serverContextPathCache);
            newCache.put(info.getServerId() + "::" + info.getContextPath(), info);
            serverContextPathCache = Collections.unmodifiableMap(newCache);
        }
        return info;
    }

    /**
     * Gets from webSiteId, no caching.
     * NOTE: This is the factory method that should be used during loading.
     * @see #fromWebSiteId(String)
     */
    public static ExtWebappInfo fromWebSiteIdNew(String webSiteId) throws IllegalArgumentException {
        WebappInfo webappInfo = getWebappInfoAlways(webSiteId);
        WebXml webXml = getWebXmlAlways(webSiteId, webappInfo);
        return new ExtWebappInfo(webSiteId, webappInfo, webXml);
    }

    /**
     * Gets from contextPath, with caching.
     * Cache only allows websites with registered WebappInfo and WebXml.
     * NOTE: If accessing from early loading process, do not call this, instead call
     * {@link #fromWebSiteIdNew(String)}, otherwise there is a risk of caching
     * incomplete instances.
     */
    public static ExtWebappInfo fromContextPath(String serverName, String contextPath) throws IllegalArgumentException {
        String cacheKey = (serverName != null ? serverName : "default-server") + "::" + contextPath;
        ExtWebappInfo info = serverContextPathCache.get(cacheKey);
        if (info != null) return info;
        synchronized(cacheLock) {
            info = serverContextPathCache.get(cacheKey);
            if (info != null) return info;

            info = fromContextPathNew(serverName, contextPath);

            // copy cache for synch semantics
            Map<String, ExtWebappInfo> newCache = new HashMap<>(serverContextPathCache);
            newCache.put(cacheKey, info);
            serverContextPathCache = Collections.unmodifiableMap(newCache);

            if (info.getWebSiteId() != null) {
                newCache = new HashMap<>(webSiteIdCache);
                newCache.put(info.getWebSiteId(), info);
                webSiteIdCache = Collections.unmodifiableMap(newCache);
            }
        }
        return info;
    }

    @Deprecated
    public static ExtWebappInfo fromContextPath(String contextPath) throws IllegalArgumentException {
        return fromContextPath(null, contextPath);
    }

    /**
     * Gets from webSiteId, no caching.
     * NOTE: This is the factory method that should be used during loading.
     * @see #fromWebSiteId(String)
     */
    public static ExtWebappInfo fromContextPathNew(String serverName, String contextPath) throws IllegalArgumentException {
        WebappInfo webappInfo;
        try {
            webappInfo = WebAppUtil.getWebappInfoFromContextPath(serverName, contextPath);
        } catch(IllegalArgumentException e) {
            throw new IllegalArgumentException(e);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not read or find ofbiz webapp info (WebappInfo) for context path '" + contextPath + "': " + e.getMessage(), e);
        }
        WebXml webXml = getWebXmlAlways(null, webappInfo);
        String webSiteId = null;
        try {
            webSiteId = WebAppUtil.getWebSiteId(webXml);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not get webSiteId for context path '" + contextPath + "': " + e.getMessage(), e);
        }
        return new ExtWebappInfo(webSiteId, webappInfo, webXml);
    }

    /**
     * Gets from a URI that starts with a webapp contextPath, with caching.
     * <p>
     * Cache only allows websites with registered WebappInfo and WebXml.
     * NOTE: If accessing from early loading process, do not call this, instead call
     * {@link #fromWebSiteIdNew(String)}, otherwise there is a risk of caching
     * incomplete instances.
     * <p>
     * NOTE: This only works for paths starting from webapp contextPath;
     * if it contains extra prefix such as webappPathPrefix, this will throw exception
     * or return the root webapp (if any mapped to /).
     * <p>
     * WARN: Uses/may-use caching, do not call during system loading.
     */
    public static ExtWebappInfo fromPath(String serverName, String path, boolean stripQuery) throws IllegalArgumentException {
        WebappInfo webappInfo;
        try {
            webappInfo = WebAppUtil.getWebappInfoFromPath(serverName, path, stripQuery);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        } catch (SAXException e) {
            throw new IllegalArgumentException(e);
        }
        return fromContextPath(serverName, webappInfo.getContextRoot());
    }

    @Deprecated
    public static ExtWebappInfo fromPath(String path) throws IllegalArgumentException {
        return fromPath(null, path, true);
    }

    /**
     * Returns the server webapp for the current request.
     */
    public static ExtWebappInfo fromRequest(HttpServletRequest request) throws IllegalArgumentException {
        return fromContextPath(WebAppUtil.getServerId(request), request.getContextPath());
    }

    /**
     * Returns the <em>effectively server-viewable</em> webapp for the given component webapp.
     * What this means is, if you request accounting/accounting webapp but it has been overridden
     * by mount-point by the accountingde/accountingde webapp, the latter will be returned.
     * <p>
     * WARN: Uses/may-use caching, do not call during system loading.
     */
    public static ExtWebappInfo fromEffectiveComponentWebapp(WebappInfo webappInfo) throws IllegalArgumentException {
        return fromContextPath(webappInfo.getServer(), webappInfo.getContextRoot());
    }

    /**
     * Returns the <em>effectively server-viewable</em> webapp for the given component webapp.
     * What this means is, if you request accounting/accounting webapp but it has been overridden
     * by mount-point by the accountingde/accountingde webapp, the latter will be returned.
     * <p>
     * WARN: Uses/may-use caching, do not call during system loading.
     */
    public static ExtWebappInfo fromEffectiveComponentWebappName(String componentName, String webappName) throws IllegalArgumentException {
        // TODO?: delegate and cache this somewhere, very inefficient, will see if/when this gets more usage...
        WebappInfo webappInfo = null;
        // get the last entry (should override previous ones)
        for(ComponentConfig cc : ComponentConfig.getAllComponents()) {
            for (WebappInfo wInfo : cc.getWebappInfos()) {
                if (cc.getGlobalName().equals(componentName) && wInfo.getName().equals(webappName)) {
                    webappInfo = wInfo; 
                }
            }
        }
        if (webappInfo == null) {
            throw new IllegalArgumentException("Could not find webapp info for component '" 
                    + componentName + "' and webapp name '" + webappName + "'");
        }
        return fromEffectiveComponentWebapp(webappInfo);
    }

    /**
     * @deprecated 2018-09-25: This method was named ambiguously; use {@link #fromEffectiveComponentWebapp(WebappInfo)} instead;
     * NOTE: This method always returned the <em>effectively server-viewable</em> webapp, which may
     * be different from the passed webapp info; see new method for details.
     */
    @Deprecated
    public static ExtWebappInfo fromWebappInfo(WebappInfo webappInfo) throws IllegalArgumentException {
        return fromEffectiveComponentWebapp(webappInfo);
    }

    public String getWebSiteId() {
        return webSiteId;
    }

    public WebappInfo getWebappInfo() {
        return webappInfo;
    }

    /**
     * Gets the Tomcat WebXml descriptor for the webapp.
     * WARN: Avoid using WebXml directly; use helper methods on this class
     * and in other classes that accept a WebXml instead!
     * <p>
     * WARN: 2018-09-25: If you need to get the context params statically,
     * <strong>always</strong> use {@link #getContextParams()} on this class instead!
     * This descriptor may not include context-params added from other
     * sources such as ofbiz-component.xml.
     */
    public WebXml getWebXml() {
        return webXml;
    }

    public String getServerId() {
        if (webappInfo.getServer() != null) {
            return webappInfo.getServer();
        } else {
            return getContextParams().get("ofbizServerName");
        }
    }

    public String getWebappName() {
        return webappInfo.getName();
    }

    /**
     * Gets context path.
     * If mapped to root, the result is empty string ""
     * (same format as {@link javax.servlet.http.HttpServletRequest#getContextPath()}.
     */
    public String getContextPath() {
        return webappInfo.getContextRoot();
    }

    /**
     * Gets the context root directly from {@link org.ofbiz.base.component.ComponentConfig.WebappInfo#getContextRoot()}.
     * @deprecated use {@link #getContextPath()} instead
     */
    @Deprecated
    public String getContextRoot() {
        return webappInfo.getContextRoot();
    }

    /**
     * The control servlet mapping, or empty string "" if it is the root mapping.
     * No trailing slash.
     * <p>
     * This is same as {@link #getControlServletMapping()} except it uses empty string
     * instead of single slash for the root mapping, meant for easy prepending.
     */
    public String getControlServletPath() {
        return controlServletPath;
    }

    /**
     * The control servlet mapping, or "/" if it is the root mapping.
     * No trailing slash unless it's the root mapping.
     * <p>
     * This is same as {@link #getControlServletPath()} except it uses single slash
     * instead of empty string for the root mapping, meant for easy prepending.
     */
    public String getControlServletMapping() {
        return controlServletMapping;
    }

    /**
     * Returns the control path prefixed with the webapp context root and
     * suffixed with trailing slash.
     * <p>
     * Essentially {@link #getContextPath()} + {@link #getControlServletMapping()} + "/".
     * Includes a trailing slash.
     * <p>
     * NOTE: The trailing slash inconsistency is due to this coming from the stock ofbiz
     * {@link WebAppUtil#getControlServletPath(WebappInfo)} (FIXME?)
     */
    public String getFullControlPath() {
        return fullControlPath;
    }

    /**
     * Gets controller config or null if none for this webapp.
     * <p>
     * NOTE: This is NOT cached, because the ExtWebappInfo caches are static
     * and they would cause a second-layer caching around the ControllerConfig cache.
     */
    public ControllerConfig getControllerConfig() {
        try {
            return ConfigXMLReader.getControllerConfig(getWebappInfo(), true);
        } catch (Exception e) {
            // Do not throw this because caller would probably not expect it;
            // he would expect it to be thrown during construction, but can't
            Debug.logError(e, "Error getting controller config for webapp " + this.toString() + "'", module);
            return null;
        }
    }

    /**
     * Returns the static context-params defined in web.xml plus those injected from ofbiz-component.xml
     * webapp element init-param definitions.
     */
    public Map<String, String> getContextParams() {
        return contextParams;
    }

    /**
     * Returns whether the forwardRootControllerUris ContextFilter settings is
     * on, off, or undetermined (null).
     */
    public Boolean getForwardRootControllerUris() {
        Optional<Boolean> forwardRootControllerUris = this.forwardRootControllerUris;
        if (forwardRootControllerUris == null) {
            Boolean setting;
            try {
                setting = ContextFilter.readForwardRootControllerUrisSetting(getWebXml(), getLogMsgPrefix());
            } catch(Exception e) {
                Debug.logError(e, getLogMsgPrefix()+"Error while trying to determine forwardRootControllerUris ContextFilter setting: "
                        + e.getMessage(), module);
                setting = null;
            }
            forwardRootControllerUris = Optional.ofNullable(setting);
            this.forwardRootControllerUris = forwardRootControllerUris;
        }
        return forwardRootControllerUris.orElse(null);
    }

    /**
     * Returns whether the forwardRootControllerUris ContextFilter settings is
     * on, off, or undetermined (null), and whether the setting makes sense
     * against the current control mount and potentially other settings.
     */
    public Boolean getForwardRootControllerUrisValidated() {
        Optional<Boolean> forwardRootControllerUrisValid = this.forwardRootControllerUrisValid;
        if (forwardRootControllerUrisValid == null) {
            Boolean setting;
            try {
                setting = ContextFilter.verifyForwardRootControllerUrisSetting(getForwardRootControllerUris(), getControlServletMapping(), getLogMsgPrefix());
            } catch(Exception e) {
                Debug.logError(e, getLogMsgPrefix()+"Error while trying to determine forwardRootControllerUris ContextFilter setting: "
                        + e.getMessage(), module);
                setting = null;
            }
            forwardRootControllerUrisValid = Optional.ofNullable(setting);
            this.forwardRootControllerUrisValid = forwardRootControllerUrisValid;
        }
        return forwardRootControllerUrisValid.orElse(null);
    }

    public boolean hasUrlRewriteFilter() {
        return urlRewriteFilter;
    }

    /**
     * Returns relation location of the urlrewrite file for this webapp, or null if no
     * UrlRewriteFilter for this webapp or error.
     */
    public String getUrlRewriteConfPath() {
        return urlRewriteConfPath;
    }

    /**
     * Returns full location of the urlrewrite file for this webapp, or null if no
     * UrlRewriteFilter for this webapp or error. Does not check if file exists.
     */
    public String getUrlRewriteFullConfPath() {
        return urlRewriteFullConfPath;
    }

    /**
     * Returns full file location of the urlrewrite file for this webapp, or null if no
     * UrlRewriteFilter for this webapp, file does not exist or error.
     */
    public String getUrlRewriteRealConfPath() {
        return urlRewriteRealConfPath;
    }

    public boolean useUrlManualInterWebappFilter() {
        return urlManualInterWebappFilter;
    }

    public boolean isRequestWebapp(HttpServletRequest request) {
        return getContextPath().equals(request.getContextPath()) && getServerId().equals(WebAppUtil.getServerId(request));
    }

    /**
     * Returns true if other and this refer to the same webapp.
     */
    public boolean isSameServerWebapp(ExtWebappInfo other) {
        return (this == other) || (getContextPath().equals(other.getContextPath()) && getServerId().equals(other.getServerId()));
    }

    public boolean isSameWebSite(ExtWebappInfo other) {
        return (this == other) || ((webSiteId != null) && webSiteId.equals(other.webSiteId));
    }

    private String getLogMsgPrefix() {
        if (webSiteId != null) {
            return "Website '" + webSiteId + "': ";
        } else {
            return "Webapp '" + getContextPath() + "': ";
        }
    }

    @Override
    public String toString() {
        return "[contextPath=" + getContextPath()
                + ", server=" + getServerId()
                + ", webSiteId=" + getWebSiteId() + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + getContextPath().hashCode();
        result = prime * result + getServerId().hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        return isSameServerWebapp((ExtWebappInfo) obj);   
    }

    /**
     * Helper wrapper to read WebappInfo reliably.
     */
    public static WebappInfo getWebappInfoAlways(String webSiteId) throws IllegalArgumentException {
        try {
            return WebAppUtil.getWebappInfoFromWebsiteId(webSiteId);
        } catch(IllegalArgumentException e) {
            throw new IllegalArgumentException(e); // exception message already good
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not read or find ofbiz webapp info (WebappInfo) for website ID '" + webSiteId + "': " + e.getMessage(), e);
        }
    }

    /**
     * Helper wrapper to read WebXml reliably.
     */
    public static WebXml getWebXmlAlways(String webSiteId, WebappInfo webappInfo) throws IllegalArgumentException {
        try {
            return WebAppUtil.getWebXml(webappInfo);
        } catch(Exception e) {
            throw new IllegalArgumentException("Could not read or find webapp container info (web.xml) for website ID '" + webSiteId
                        + "' (mount-point '" + webappInfo.getContextRoot() + "'): " + e.getMessage(), e);
        }
    }

    private static String getUrlRewriteConfPathFromWebXml(WebXml webXml) {
        for (FilterDef filterDef : webXml.getFilters().values()) {
            String filterClassName = filterDef.getFilterClass();
            // exact name is the original Ofbiz solution, return exact if found
            if (org.tuckey.web.filters.urlrewrite.UrlRewriteFilter.class.getName().equals(filterClassName)) {
                String confPath = filterDef.getParameterMap().get("confPath");
                if (UtilValidate.isNotEmpty(confPath)) {
                    return confPath;
                }
                return DEFAULT_WEBAPP_URLREWRITE_FILE;
            }
        }
        return null;
    }
}

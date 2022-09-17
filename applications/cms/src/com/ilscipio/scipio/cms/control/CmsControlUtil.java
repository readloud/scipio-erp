package com.ilscipio.scipio.cms.control;

import java.io.IOException;
import java.io.Writer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.ofbiz.base.util.UtilHttp;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.webapp.control.RequestHandler;

import com.ilscipio.scipio.ce.util.PathUtil;
import com.ilscipio.scipio.cms.control.cmscall.CmsCallType;
import com.ilscipio.scipio.cms.webapp.CmsWebappUtil;

/**
 * Cms control-related util methods; unlike WebappUtil this is Cms-specific control code
 * and factoring points.
 */
public abstract class CmsControlUtil {

    //private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());

    //public static final String CMS_NOCACHERESPONSESET_REQATTRNAME = "_CMS_NOCACHERESPONSE_SET_";

    private CmsControlUtil() {
    }

    /**
     * Gets a system-wide unique ID for the current request. WARNING: We cheat and use current thread ID for now!
     * Note the name: "current".
     */
    public static long getCurrentRequestUniqueId(HttpServletRequest request) {
        return CmsWebappUtil.getCurrentRequestUniqueId(request);
    }

    public static void checkSetNoCacheResponse(HttpServletRequest request, HttpServletResponse response) {
        // Always set, even if multiple times; don't think there's an issue with it and overrides anything else
        //if (!Boolean.TRUE.equals((Boolean) request.getAttribute(CMS_NOCACHERESPONSESET_REQATTRNAME))) {
        UtilHttp.setResponseBrowserProxyNoCache(response);
        // DEV NOTE: If ever uncomment this, must be careful so it is excluded from session through RequestHandler
        //request.setAttribute(CMS_NOCACHERESPONSESET_REQATTRNAME, Boolean.TRUE);
        //}
    }

    public static boolean getPreviewModeParam(HttpServletRequest request, CmsWebSiteConfig webSiteConfig) {
        String previewMode = (String) request.getAttribute(webSiteConfig.getPreviewModeParamName());
        if (previewMode == null) {
            previewMode = request.getParameter(webSiteConfig.getPreviewModeParamName());
            if ("Y".equals(previewMode) || (previewMode != null && previewMode.length() >= 5)) {
                previewMode = "Y";
            } else {
                previewMode = "N";
            }
            request.setAttribute(webSiteConfig.getPreviewModeParamName(), previewMode);
        }
        return "Y".equals(previewMode);
    }

    public static CmsCallType getRenderModeParam(HttpServletRequest request, CmsWebSiteConfig webSiteConfig) {
        CmsCallType renderMode;
        if (webSiteConfig.isAllowPreviewMode()) {
            renderMode = CmsControlUtil.getPreviewModeParam(request, webSiteConfig) ?
                    CmsCallType.OFBIZ_PREVIEW : CmsCallType.OFBIZ_RENDER;
        } else {
            renderMode = CmsCallType.OFBIZ_RENDER;
        }
        return renderMode;
    }

    public static String getAccessTokenParam(HttpServletRequest request, CmsWebSiteConfig webSiteConfig) {
        String accessToken = (String) request.getAttribute(webSiteConfig.getAccessTokenParamName());
        if (accessToken == null) {
            accessToken = request.getParameter(webSiteConfig.getAccessTokenParamName());
            if (accessToken == null) {
                // access token may also be inlined into cmsPreviewMode param
                String inlineAccessToken = request.getParameter(webSiteConfig.getPreviewModeParamName());
                if (inlineAccessToken != null && inlineAccessToken.length() >= 5) {
                    accessToken = inlineAccessToken;
                } else {
                    accessToken = "";
                }
            }
            request.setAttribute(webSiteConfig.getAccessTokenParamName(), accessToken);
        }
        return accessToken.isEmpty() ? null : accessToken;
    }

    public static boolean verifyValidAccessToken(HttpServletRequest request, CmsWebSiteConfig webSiteConfig, CmsCallType renderMode) {
        if (renderMode == CmsCallType.OFBIZ_PREVIEW || webSiteConfig.isRequireLiveAccessToken()) {
            String accessToken = CmsControlUtil.getAccessTokenParam(request, webSiteConfig);
            // TODO: REVIEW: the request URI here might not necessarily match one of the page's URIs
            // but won't matter until isValidAccessToken actively checks it
            if (!CmsAccessHandler.isValidAccessToken(request, request.getRequestURI(), accessToken)) {
                return false;
            }
        }
        return true;
    }

    public static String normalizeServletPath(String servletPath) { // Servlet path only
        if (servletPath == null) return null;
        return PathUtil.ensureStartAndNoTrailDelim(servletPath);
    }

    public static String normalizeServletPathNoNull(String servletPath) { // Servlet path only
        if (servletPath == null) return "/";
        return PathUtil.ensureStartAndNoTrailDelim(servletPath);
    }

    public static String normalizeServerRootRequestPath(String requestPath) { // Path from server root to before query string
        if (requestPath == null) return null;
        return PathUtil.ensureStartAndNoTrailDelim(requestPath);
    }

    public static String normalizeServerRootRequestPathNoNull(String requestPath) { // Path from server root to before query string
        if (requestPath == null) return "/";
        return PathUtil.ensureStartAndNoTrailDelim(requestPath);
    }

    public static String normalizeContextRootRequestPath(String requestPath) { // Path from servlet context (webapp) root to before query string
        if (requestPath == null) return null;
        return PathUtil.ensureStartAndNoTrailDelim(requestPath);
    }

    public static String normalizeContextRootRequestPathNoNull(String requestPath) { // Path from servlet context (webapp) root to before query string
        if (requestPath == null) return "/";
        return PathUtil.ensureStartAndNoTrailDelim(requestPath);
    }

    public static String normalizeServletRootRequestPath(String requestPath) { // Path from servlet (controller) root to before query string
        if (requestPath == null) return null;
        return PathUtil.ensureStartAndNoTrailDelim(requestPath);
    }

    public static String normalizeServletRootRequestPathNoNull(String requestPath) { // Path from servlet (controller) root to before query string
        if (requestPath == null) return "/";
        return PathUtil.ensureStartAndNoTrailDelim(requestPath);
    }

    /**
     * @deprecated not really appropriate for local cms.
     */
    @Deprecated
    public static String normalizeCmsReqPath(String cmsReqPath) {
        if (cmsReqPath == null) return null;
        return PathUtil.ensureStartDelim(cmsReqPath);
    }

    public static String getSessionIdForLog(HttpSession session) {
        return RequestHandler.getSessionIdForLog(session);
    }

    public static String getSessionIdForLog(HttpServletRequest request) {
        return RequestHandler.getSessionIdForLog(request);
    }
    
    public static String getReqLogIdDelimStr(HttpServletRequest request) {
        HttpSession session = (request != null) ? request.getSession(false) : null;
        return (request != null ? "; sessionId=" + getSessionIdForLog(session) : "");
    }

    // only use if request is not available
    public static String getReqLogIdDelimStr(HttpSession session) {
        return "; sessionId=" + getSessionIdForLog(session);
    }

    /**
     * Gets the response writer, in the same fashion as done by
     * {@link org.ofbiz.widget.renderer.macro.MacroScreenViewHandler}.
     */
    public static Writer getResponseWriter(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // 2016: don't do this; do exact same as MacroScreenViewHandler
        //      Writer writer;
        //      if (servletCtx != null) {
        //          writer = response.getWriter();
        //      } else {
        //          ServletOutputStream ros = response.getOutputStream();
        //          writer = new OutputStreamWriter(ros, "UTF-8");
        //      }
        return response.getWriter();
    }

    public static String getPagePreviewVersionId(HttpServletRequest request) {
        String cmsPageVersionId = (String) request.getAttribute("cmsPageVersionId");
        if (cmsPageVersionId == null) {
            cmsPageVersionId = request.getParameter("cmsPageVersionId");
        }
        if (UtilValidate.isEmpty(cmsPageVersionId)) {
            cmsPageVersionId = null;
        }
        return cmsPageVersionId;
    }
}

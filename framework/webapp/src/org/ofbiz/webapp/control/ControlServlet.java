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
package org.ofbiz.webapp.control;

import java.io.IOException;
import java.util.Enumeration;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.bsf.BSFManager;
import org.apache.tomcat.util.descriptor.web.ServletDef;
import org.apache.tomcat.util.descriptor.web.WebXml;
import org.ofbiz.base.component.ComponentConfig.WebappInfo;
import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.UtilGenerics;
import org.ofbiz.base.util.UtilHttp;
import org.ofbiz.base.util.UtilProperties;
import org.ofbiz.base.util.UtilRender;
import org.ofbiz.base.util.UtilTimer;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.base.util.template.FreeMarkerWorker;
import org.ofbiz.entity.Delegator;
import org.ofbiz.entity.DelegatorFactory;
import org.ofbiz.entity.GenericDelegator;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.transaction.GenericTransactionException;
import org.ofbiz.entity.transaction.TransactionUtil;
import org.ofbiz.security.Security;
import org.ofbiz.service.LocalDispatcher;
import org.ofbiz.webapp.renderer.RenderTargetUtil;
import org.ofbiz.webapp.stats.ServerHitBin;
import org.ofbiz.webapp.stats.VisitHandler;

import freemarker.ext.servlet.ServletContextHashModel;

/**
 * ControlServlet.java - Master servlet for the web application.
 */
@SuppressWarnings("serial")
public class ControlServlet extends HttpServlet {

    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());

    public ControlServlet() {
        super();
    }

    /**
     * @see javax.servlet.Servlet#init(javax.servlet.ServletConfig)
     */
    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        if (Debug.infoOn()) {
            ServletContext servletContext = config.getServletContext();
            String webappName = servletContext.getContextPath().length() != 0 ? servletContext.getContextPath().substring(1) : "";
            Debug.logInfo("Loading webapp [" + webappName + "], located at " + servletContext.getRealPath("/"), module);
        }

        // SCIPIO: 2017-11-14: new _CONTROL_MAPPING_ and _CONTROL_SERVPATH_ servlet attributes; setting
        // these here allows them to be available from early filters (instead of hardcoding there).
        String servletMapping = ServletUtil.getBaseServletMapping(config.getServletContext(), config.getServletName());
        String servletPath = "/".equals(servletMapping) ? "" : servletMapping;
        config.getServletContext().setAttribute("_CONTROL_MAPPING_", servletMapping);
        config.getServletContext().setAttribute("_CONTROL_SERVPATH_", servletPath);
        if (servletPath == null) {
            Debug.logError("Scipio: ERROR: Control servlet with name '" +  config.getServletName() + "' has no servlet mapping! Cannot set _CONTROL_SERVPATH_! Please fix web.xml or app will crash!", module);
        }

        // configure custom BSF engines
        try {  
            configureBsf();
        } catch(Exception e) { // SCIPIO
            Debug.logError(e, "init: Error configuring BSF engines for webapp [" + config.getServletContext().getContextPath() + "]", module);
        }
        // initialize the request handler
        // SCIPIO: NOTE: getRequestHandler may throw an exception if bad controller...
        // We could let the init fail and the servlet container will try again later, but this results
        // in inconsistent error handling because we can also have an initialized ControlServlet but then
        // further controller changes break the controller again.
        // So to minimize strange behavior, we have to prevent exception here and let init finish;
        // instead it will be doGet, ContextFilter or another filter that will trigger a crash on request.
        try {
            getRequestHandler();
        } catch(Exception e) {
            Debug.logError(e, "init: Error initializing RequestHandler for webapp [" + config.getServletContext().getContextPath() + "]", module);
        }
    }

    /**
     * @see javax.servlet.http.HttpServlet#doPost(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doGet(request, response);
    }

    /**
     * @see javax.servlet.http.HttpServlet#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        long requestStartTime = System.currentTimeMillis();
        RequestHandler requestHandler = this.getRequestHandler();
        HttpSession session = request.getSession();

        // setup DEFAULT character encoding and content type, this will be overridden in the RequestHandler for view rendering
        String charset = request.getCharacterEncoding();

        // setup content type
        String contentType = "text/html";
        if (UtilValidate.isNotEmpty(charset) && !"none".equals(charset)) {
            response.setContentType(contentType + "; charset=" + charset);
            response.setCharacterEncoding(charset);
        } else {
            response.setContentType(contentType);
        }

        GenericValue userLogin = (GenericValue) session.getAttribute("userLogin");
        //Debug.logInfo("Cert Chain: " + request.getAttribute("javax.servlet.request.X509Certificate"), module);

        // set the Entity Engine user info if we have a userLogin
        if (userLogin != null) {
            GenericDelegator.pushUserIdentifier(userLogin.getString("userLoginId"));
        }

        // workaraound if we are in the root webapp
        String webappName = UtilHttp.getApplicationName(request);

        String rname = "";
        if (request.getPathInfo() != null) {
            rname = request.getPathInfo().substring(1);
        }
        if (rname.indexOf('/') > 0) {
            rname = rname.substring(0, rname.indexOf('/'));
        }

        UtilTimer timer = null;
        if (Debug.timingOn()) {
            timer = new UtilTimer();
            timer.setLog(true);
            timer.timerString("[" + rname + "(Domain:" + request.getScheme() + "://" + request.getServerName() + ")] Request Begun, encoding=[" + charset + "]", module);
        }

        // Setup the CONTROL_PATH for JSP dispatching.
        String contextPath = request.getContextPath();
        if (contextPath == null || "/".equals(contextPath)) {
            contextPath = "";
        }
        request.setAttribute("_CONTROL_PATH_", contextPath + request.getServletPath());
        if (Debug.verboseOn()) {
             Debug.logVerbose("Control Path: " + request.getAttribute("_CONTROL_PATH_"), module);
        }

        // for convenience, and necessity with event handlers, make security and delegator available in the request:
        // try to get it from the session first so that we can have a delegator/dispatcher/security for a certain user if desired
        Delegator delegator = null;
        String delegatorName = (String) session.getAttribute("delegatorName");
        if (UtilValidate.isNotEmpty(delegatorName)) {
            delegator = DelegatorFactory.getDelegator(delegatorName);
        }
        if (delegator == null) {
            delegator = (Delegator) getServletContext().getAttribute("delegator");
        }
        if (delegator == null) {
            Debug.logError("[ControlServlet] ERROR: delegator not found in ServletContext", module);
        } else {
            request.setAttribute("delegator", delegator);
            // always put this in the session too so that session events can use the delegator
            session.setAttribute("delegatorName", delegator.getDelegatorName());
            /* Uncomment this to enable the EntityClassLoader
            ClassLoader loader = EntityClassLoader.getInstance(delegator.getDelegatorName(), Thread.currentThread().getContextClassLoader());
            Thread.currentThread().setContextClassLoader(loader);
            */
        }

        LocalDispatcher dispatcher = (LocalDispatcher) session.getAttribute("dispatcher");
        if (dispatcher == null) {
            dispatcher = (LocalDispatcher) getServletContext().getAttribute("dispatcher");
        }
        if (dispatcher == null) {
            Debug.logError("[ControlServlet] ERROR: dispatcher not found in ServletContext", module);
        }
        request.setAttribute("dispatcher", dispatcher);

        Security security = (Security) session.getAttribute("security");
        if (security == null) {
            security = (Security) getServletContext().getAttribute("security");
        }
        if (security == null) {
            Debug.logError("[ControlServlet] ERROR: security not found in ServletContext", module);
        }
        request.setAttribute("security", security);

        request.setAttribute("_REQUEST_HANDLER_", requestHandler);

        ServletContextHashModel ftlServletContext = new ServletContextHashModel(this, FreeMarkerWorker.getDefaultOfbizWrapper());
        request.setAttribute("ftlServletContext", ftlServletContext);

        // setup some things that should always be there
        UtilHttp.setInitialRequestInfo(request);
        VisitHandler.getVisitor(request, response);

        // set the Entity Engine user info if we have a userLogin
        String visitId = VisitHandler.getVisitId(session);
        if (UtilValidate.isNotEmpty(visitId)) {
            GenericDelegator.pushSessionIdentifier(visitId);
        }

        // display details on the servlet objects
        if (Debug.verboseOn()) {
            logRequestInfo(request);
        }

        // some containers call filters on EVERY request, even forwarded ones, so let it know that it came from the control servlet
        request.setAttribute(ContextFilter.FORWARDED_FROM_SERVLET, Boolean.TRUE);

        String errorPage = null;
        try {
            // the ServerHitBin call for the event is done inside the doRequest method
            requestHandler.doRequest(request, response, null, userLogin, delegator);
        } catch (MethodNotAllowedException e) {
            // SCIPIO: Use error page for this too; users can too easily trigger this
            //response.setContentType("text/plain");
            //response.setCharacterEncoding(request.getCharacterEncoding());
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            //response.getWriter().print(e.getMessage());
            Debug.logError("Error in request handler: " + e.getMessage(), module);
            // SCIPIO: Here it should be safe to show a friendly message, no real need to fallback to 
            // generic one in high security, this is safe enough
            //request.setAttribute("_ERROR_MESSAGE_", RequestUtil.getSecureErrorMessage(request, e));
            request.setAttribute("_ERROR_MESSAGE_", UtilProperties.getMessage("WebappUiLabels", 
                    "RequestMethodNotMatchConfigDesc", UtilHttp.getLocale(request)));
            errorPage = requestHandler.getDefaultErrorPage(request);
        } catch (RequestHandlerException e) {
            Throwable throwable = e.getNested() != null ? e.getNested() : e;
            if (throwable instanceof IOException) {
                // when an IOException occurs (most of the times caused by the browser window being closed before the request is completed)
                // the connection with the browser is lost and so there is no need to serve the error page; a message is logged to record the event
                if (Debug.warningOn()) Debug.logWarning(e, "Communication error with the client while processing the request: " + request.getAttribute("_CONTROL_PATH_") + request.getPathInfo(), module);
                if (Debug.verboseOn()) Debug.logVerbose(throwable, module);
            } else {
                Debug.logError(throwable, "Error in request handler: ", module);
                request.setAttribute("_ERROR_MESSAGE_", RequestUtil.getSecureErrorMessage(request, throwable)); // SCIPIO: 2018-02-26: removed hard HTML escaping here, now handled by error.ftl/other (at point-of-use)
                errorPage = requestHandler.getDefaultErrorPage(request);
            }
        } catch (RequestHandlerExceptionAllowExternalRequests e) {
            errorPage = requestHandler.getDefaultErrorPage(request);
            Debug.logInfo("Going to external page: " + request.getPathInfo(), module);
        } catch (Exception e) {
            Debug.logError(e, "Error in request handler: ", module);
            request.setAttribute("_ERROR_MESSAGE_", RequestUtil.getSecureErrorMessage(request, e)); // SCIPIO: 2018-02-26: removed hard HTML escaping here, now handled by error.ftl/other (at point-of-use)
            errorPage = requestHandler.getDefaultErrorPage(request);
        }

        // Forward to the JSP
        // if (Debug.infoOn()) Debug.logInfo("[" + rname + "] Event done, rendering page: " + nextPage, module);
        // if (Debug.timingOn()) timer.timerString("[" + rname + "] Event done, rendering page: " + nextPage, module);

        if (errorPage != null) {
            Debug.logError("An error occurred, going to the errorPage: " + errorPage, module);

            RequestDispatcher rd = request.getRequestDispatcher(errorPage);

            // use this request parameter to avoid infinite looping on errors in the error page...
            if (request.getAttribute("_ERROR_OCCURRED_") == null && rd != null) {
                // SCIPIO: 2017-05-15: special case for targeted rendering of error page
                Object scpErrorRenderTargetExpr = RenderTargetUtil.getRawRenderTargetExpr(request, RenderTargetUtil.ERRORRENDERTARGETEXPR_REQPARAM);
                if (scpErrorRenderTargetExpr != null) {
                    RenderTargetUtil.setRawRenderTargetExpr(request, scpErrorRenderTargetExpr);
                }

                request.setAttribute("_ERROR_OCCURRED_", Boolean.TRUE);
                Debug.logError("Including errorPage: " + errorPage, module);

                // NOTE DEJ20070727 after having trouble with all of these, try to get the page out and as a last resort just send something back
                try {
                    rd.forward(request, response); // SCIPIO: Changed from include to forward so that the response can be handled appropriately
                } catch (Throwable t) {
                    Debug.logWarning("Error while trying to send error page using rd.forward (will try response.getOutputStream or response.getWriter): " + t.toString(), module);

                    // SCIPIO: 2018-02-26: we must now HTML-encode the error here (at point-of-use) because no longer done above
                    String causeMsg = RequestUtil.encodeErrorMessage(request, (String) request.getAttribute("_ERROR_MESSAGE_"));
                    String errorMessage = "ERROR rendering error page [" + errorPage + "], but here is the error text: " + causeMsg;
                    // SCIPIO: 2017-03-23: ONLY print out the error if we're in DEBUG mode
                    if (UtilRender.getRenderExceptionMode(request) == UtilRender.RenderExceptionMode.DEBUG) {
                        try {
                            response.getWriter().print(errorMessage);
                        } catch (Throwable t2) {
                            try {
                                int errorToSend = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
                                Debug.logWarning("Error while trying to write error message using response.getOutputStream or response.getWriter: " + t.toString() + "; sending error code [" + errorToSend + "], and message [" + errorMessage + "]", module);
                                response.sendError(errorToSend, errorMessage);
                            } catch (Throwable t3) {
                                // wow, still bad... just throw an IllegalStateException with the message and let the servlet container handle it
                                throw new IllegalStateException(errorMessage);
                            }
                        }
                    } else {
                        // SCIPIO: NOTE: here all posted error messages to client must be completely generic, for security reasons.
                        final String genericErrorMessage = RequestUtil.getGenericErrorMessage();
                        try {
                            response.getWriter().print(genericErrorMessage);
                        } catch (Throwable t2) {
                            try {
                                int errorToSend = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
                                Debug.logWarning("Error while trying to write error message using response.getOutputStream or response.getWriter: " + t.toString()
                                    + "; sending error code [" + errorToSend + "], but NOT message [" + errorMessage + "] because we are in secure RETHROW mode", module);
                                response.sendError(errorToSend, genericErrorMessage);
                            } catch (Throwable t3) {
                                // wow, still bad... just throw an IllegalStateException with the message and let the servlet container handle it
                                throw new IllegalStateException(genericErrorMessage);
                            }
                        }
                    }
                }

            } else {
                if (rd == null) {
                    Debug.logError("Could not get RequestDispatcher for errorPage: " + errorPage, module);
                }

                // SCIPIO: 2018-02-26: we must now HTML-encode the error here (at point-of-use) because no longer done above
                String causeMsg = RequestUtil.encodeErrorMessage(request, (String) request.getAttribute("_ERROR_MESSAGE_"));
                String errorMessage = "<html><body>ERROR in error page, (infinite loop or error page not found with name [" + errorPage + "]), but here is the text just in case it helps you: " + causeMsg + "</body></html>";
                response.getWriter().print(errorMessage);
            }
        }

        // sanity check: make sure we don't have any transactions in place
        try {
            // roll back current TX first
            if (TransactionUtil.isTransactionInPlace()) {
                Debug.logWarning("*** NOTICE: ControlServlet finished w/ a transaction in place! Rolling back.", module);
                TransactionUtil.rollback();
            }

            // now resume/rollback any suspended txs
            if (TransactionUtil.suspendedTransactionsHeld()) {
                int suspended = TransactionUtil.cleanSuspendedTransactions();
                Debug.logWarning("Resumed/Rolled Back [" + suspended + "] transactions.", module);
            }
        } catch (GenericTransactionException e) {
            Debug.logWarning(e, module);
        }

        // run these two again before the ServerHitBin.countRequest call because on a logout this will end up creating a new visit
        if (response.isCommitted() && request.getSession(false) == null) {
            // response committed and no session, and we can't get a new session, what to do!
            // without a session we can't log the hit, etc; so just do nothing; this should NOT happen much!
            Debug.logError("Error in ControlServlet output where response isCommitted and there is no session (probably because of a logout); not saving ServerHit/Bin information because there is no session and as the response isCommitted we can't get a new one. The output was successful, but we just can't save ServerHit/Bin info.", module);
        } else {
            try {
                UtilHttp.setInitialRequestInfo(request);
                VisitHandler.getVisitor(request, response);
                if (requestHandler.trackStats(request)) {
                    ServerHitBin.countRequest(webappName + "." + rname, request, requestStartTime, System.currentTimeMillis() - requestStartTime, userLogin);
                }
            } catch (Throwable t) {
                Debug.logError(t, "Error in ControlServlet saving ServerHit/Bin information; the output was successful, but can't save this tracking information. The error was: " + t.toString(), module);
            }
        }
        if (Debug.timingOn()) timer.timerString("[" + rname + "(Domain:" + request.getScheme() + "://" + request.getServerName() + ")] Request Done", module);

        // sanity check 2: make sure there are no user or session infos in the delegator, ie clear the thread
        GenericDelegator.clearUserIdentifierStack();
        GenericDelegator.clearSessionIdentifierStack();
    }

    /**
     * @see javax.servlet.Servlet#destroy()
     */
    @Override
    public void destroy() {
        super.destroy();
    }

    protected RequestHandler getRequestHandler() {
        return RequestHandler.getRequestHandler(getServletContext());
    }

    protected void configureBsf() {
        String[] jsExtensions = {"js"};
        BSFManager.registerScriptingEngine("javascript", "org.ofbiz.base.util.OfbizJsBsfEngine", jsExtensions);

        String[] smExtensions = {"sm"};
        BSFManager.registerScriptingEngine("simplemethod", "org.ofbiz.minilang.SimpleMethodBsfEngine", smExtensions);
    }

    protected void logRequestInfo(HttpServletRequest request) {
        ServletContext servletContext = this.getServletContext();
        HttpSession session = request.getSession();

        if (Debug.verboseOn()) Debug.logVerbose("--- Start Request Headers: ---", module);
        Enumeration<String> headerNames = UtilGenerics.cast(request.getHeaderNames());
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            Debug.logVerbose(headerName + ":" + request.getHeader(headerName), module);
        }
        if (Debug.verboseOn()) Debug.logVerbose("--- End Request Headers: ---", module);

        if (Debug.verboseOn()) Debug.logVerbose("--- Start Request Parameters: ---", module);
        Enumeration<String> paramNames = UtilGenerics.cast(request.getParameterNames());
        while (paramNames.hasMoreElements()) {
            String paramName = paramNames.nextElement();
            Debug.logVerbose(paramName + ":" + request.getParameter(paramName), module);
        }
        if (Debug.verboseOn()) Debug.logVerbose("--- End Request Parameters: ---", module);

        if (Debug.verboseOn()) Debug.logVerbose("--- Start Request Attributes: ---", module);
        Enumeration<String> reqNames = UtilGenerics.cast(request.getAttributeNames());
        while (reqNames != null && reqNames.hasMoreElements()) {
            String attName = reqNames.nextElement();
            Debug.logVerbose(attName + ":" + request.getAttribute(attName), module);
        }
        if (Debug.verboseOn()) Debug.logVerbose("--- End Request Attributes ---", module);

        if (Debug.verboseOn()) Debug.logVerbose("--- Start Session Attributes: ---", module);
        Enumeration<String> sesNames = null;
        try {
            sesNames = UtilGenerics.cast(session.getAttributeNames());
        } catch (IllegalStateException e) {
            if (Debug.verboseOn()) Debug.logVerbose("Cannot get session attributes : " + e.getMessage(), module);
        }
        while (sesNames != null && sesNames.hasMoreElements()) {
            String attName = sesNames.nextElement();
            Debug.logVerbose(attName + ":" + session.getAttribute(attName), module);
        }
        if (Debug.verboseOn()) Debug.logVerbose("--- End Session Attributes ---", module);

        Enumeration<String> appNames = UtilGenerics.cast(servletContext.getAttributeNames());
        if (Debug.verboseOn()) Debug.logVerbose("--- Start ServletContext Attributes: ---", module);
        while (appNames != null && appNames.hasMoreElements()) {
            String attName = appNames.nextElement();
            Debug.logVerbose(attName + ":" + servletContext.getAttribute(attName), module);
        }
        if (Debug.verboseOn()) Debug.logVerbose("--- End ServletContext Attributes ---", module);
    }

    /**
     * SCIPIO: Locates the ControlServlet servlet definition in the given WebXml, or null
     * if does not appear to be present.
     * Best-effort operation.
     * <p>
     * Factored out and modified from stock method {@link #getControlServletPath(WebappInfo, boolean)}.
     * <p>
     * SCIPIO: 2017-12-05: Adds subclass support, oddly missing from stock ofbiz code.
     * <p>
     * Added 2017-12.
     */
    public static ServletDef getControlServletDefFromWebXml(WebXml webXml) {
        ServletDef bestServletDef = null;
        for (ServletDef servletDef : webXml.getServlets().values()) {
            String servletClassName = servletDef.getServletClass();
            // exact name is the original Ofbiz solution, return exact if found
            if (ControlServlet.class.getName().equals(servletClassName)) {
                return servletDef;
            }
            // we must now also check for class that extends ControlServlet (this will return the last one)
            if (servletClassName != null) {
                try {
                    Class<?> cls = Thread.currentThread().getContextClassLoader().loadClass(servletClassName);
                    if (ControlServlet.class.isAssignableFrom(cls)) bestServletDef = servletDef;
                } catch(Exception e) {
                    // NOTE: 2018-05-11: this should not be a warning because this is a regular occurrence
                    // for webapps which have servlet classes in libs under WEB-INF/lib
                    //Debug.logWarning("Could not load or test servlet class (" + servletClassName + "); may be invalid or a classloader issue: "
                    //        + e.getMessage(), module);
                }
            }
        }
        return bestServletDef;
    }
}

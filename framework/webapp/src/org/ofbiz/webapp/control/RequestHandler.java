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

import static org.ofbiz.base.util.UtilGenerics.checkMap;

import java.io.IOException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.ofbiz.base.component.ComponentConfig.WebappInfo;
import org.ofbiz.base.start.Start;
import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.SSLUtil;
import org.ofbiz.base.util.StringUtil;
import org.ofbiz.base.util.UtilCodec;
import org.ofbiz.base.util.UtilFormatOut;
import org.ofbiz.base.util.UtilGenerics;
import org.ofbiz.base.util.UtilHttp;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilObject;
import org.ofbiz.base.util.UtilProperties;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.base.util.string.FlexibleStringExpander;
import org.ofbiz.entity.Delegator;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.util.EntityQuery;
import org.ofbiz.entity.util.EntityUtilProperties;
import org.ofbiz.webapp.ExtWebappInfo;
import org.ofbiz.webapp.FullWebappInfo;
import org.ofbiz.webapp.OfbizUrlBuilder;
import org.ofbiz.webapp.WebAppUtil;
import org.ofbiz.webapp.control.ConfigXMLReader.ControllerConfig;
import org.ofbiz.webapp.control.ConfigXMLReader.Event;
import org.ofbiz.webapp.control.ConfigXMLReader.RequestMap;
import org.ofbiz.webapp.control.ConfigXMLReader.RequestResponse;
import org.ofbiz.webapp.control.ConfigXMLReader.RequestResponse.AttributesSpec;
import org.ofbiz.webapp.control.RequestAttrPolicy.RedirectAttrPolicy;
import org.ofbiz.webapp.control.RequestAttrPolicy.RequestAttrNamePolicy;
import org.ofbiz.webapp.control.RequestAttrPolicy.RequestSavingAttrPolicy;
import org.ofbiz.webapp.control.RequestAttrPolicy.RestoreAttrPolicyInvoker;
import org.ofbiz.webapp.control.RequestAttrPolicy.SaveAttrPolicyInvoker;
import org.ofbiz.webapp.control.RequestAttrPolicy.ViewLastAttrPolicy;
import org.ofbiz.webapp.event.EventFactory;
import org.ofbiz.webapp.event.EventHandler;
import org.ofbiz.webapp.event.EventHandlerException;
import org.ofbiz.webapp.event.EventHandlerWrapper;
import org.ofbiz.webapp.event.EventUtil;
import org.ofbiz.webapp.renderer.RenderTargetUtil;
import org.ofbiz.webapp.stats.ServerHitBin;
import org.ofbiz.webapp.view.ViewFactory;
import org.ofbiz.webapp.view.ViewHandler;
import org.ofbiz.webapp.view.ViewHandlerException;
import org.ofbiz.webapp.view.ViewHandlerExt;
import org.ofbiz.webapp.website.WebSiteProperties;
import org.ofbiz.webapp.website.WebSiteWorker;

import com.ilscipio.scipio.ce.webapp.filter.UrlFilterHelper;
import com.ilscipio.scipio.ce.webapp.filter.urlrewrite.ScipioUrlRewriter;

/**
 * RequestHandler - XML webapp controller logic implementation for "*controller.xml" files.
 * <p>
 * SCIPIO: This class has been extensively revamped and may bear no resemblance to its original.
 */
public class RequestHandler {

    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());
    private static final boolean showSessionIdInLog = UtilProperties.propertyValueEqualsIgnoreCase("requestHandler", "show-sessionId-in-log", "Y"); // SCIPIO: made static var & remove delegator

    /**
     * SCIPIO: If true, force a slash after the context root when building links, meaning even
     * when the path is empty; if false, does not force them and instead respects caller-specified path, 
     * but then Tomcat may perform unnecessary redirects to add them itself (due to web.xml?).
     */
    private static final boolean urlForceContextRootDirSep = true;

    private static final Set<String> logCallerExcludeClasses = UtilMisc.toSet(RequestHandler.class.getName()); // SCIPIO

    // SCIPIO: changed status code field to static and keep only number instead
    //private static final String defaultStatusCodeString = UtilProperties.getPropertyValue("requestHandler", "status-code", "301");
    private static final Integer defaultStatusCodeNumber = UtilProperties.getPropertyAsInteger("requestHandler", "status-code", 301);

    private final ViewFactory viewFactory;
    private final EventFactory eventFactory;
    private final URL controllerConfigURL;
    private final boolean trackServerHit;
    private final boolean trackVisit;
    private final String charset;

    /**
     * SCIPIO: Allows or prevents override view URIs, based on web.xml config. Default: true (stock behavior).
     */
    private final boolean allowOverrideViewUri;

    public static RequestHandler getRequestHandler(ServletContext servletContext) {
        RequestHandler rh = (RequestHandler) servletContext.getAttribute("_REQUEST_HANDLER_");
        if (rh == null) {
            rh = new RequestHandler(servletContext);
            servletContext.setAttribute("_REQUEST_HANDLER_", rh);
        }
        return rh;
    }
    
    /**
     * SCIPIO: Gets request handler for the ServletContext associated to current request.
     * Added 2017-05-08.
     */
    public static RequestHandler getRequestHandler(HttpServletRequest request) {
        return getRequestHandler(request.getServletContext()); // NOTE: requires servlet API 3.0+
    }

    private RequestHandler(ServletContext context) {
        // init the ControllerConfig, but don't save it anywhere, just load it into the cache
        this.controllerConfigURL = ConfigXMLReader.getControllerConfigURL(context);
        ControllerConfig controllerConfig = null; // SCIPIO
        try {
            //ConfigXMLReader.getControllerConfig(this.controllerConfigURL);
            controllerConfig = ConfigXMLReader.getControllerConfig(this.controllerConfigURL);
        } catch (WebAppConfigurationException e) {
            // FIXME: controller.xml errors should throw an exception.
            Debug.logError(e, "Exception thrown while parsing controller.xml file: ", module);
        }
        if (controllerConfig == null) {
            // SCIPIO: 2018-11-08: If the controller fails to load here, what happens is ViewFactory/EventFactory
            // constructor throws a confusing GeneralRuntimeException following an NPE. This is because getControllerConfig returns
            // null on exception, which we can't change in code. So instead we'll handle null here and throw
            // an exception so that the server crashes with a much more informative message.
            // NOTE: We cannot allow initialization to continue, because then an incomplete RequestHandler will get stored
            // in servlet context attributes and prevent any chance at recovering after fix.
            throw new IllegalStateException("Could not initialize a RequestHandler"
                    + " for webapp [" + context.getContextPath() + "] because its controller failed to load ("
                    + this.controllerConfigURL + ")");
        }
        this.viewFactory = new ViewFactory(context, this.controllerConfigURL);
        this.eventFactory = new EventFactory(context, this.controllerConfigURL);

        this.trackServerHit = !"false".equalsIgnoreCase(context.getInitParameter("track-serverhit"));
        this.trackVisit = !"false".equalsIgnoreCase(context.getInitParameter("track-visit"));
        this.charset = context.getInitParameter("charset");

        // SCIPIO: New (currently true by default)
        this.allowOverrideViewUri = !"false".equalsIgnoreCase(context.getInitParameter("allowOverrideViewUri"));
    }

    public ConfigXMLReader.ControllerConfig getControllerConfig() {
        try {
            return ConfigXMLReader.getControllerConfig(this.controllerConfigURL);
        } catch (WebAppConfigurationException e) {
            // FIXME: controller.xml errors should throw an exception.
            Debug.logError(e, "Exception thrown while parsing controller.xml file: ", module);
        }
        return null;
    }

    /**
     * SCIPIO: Check if the request satisfies the given HTTP method.
     * Added 2018-09-19.
     */
    static boolean acceptsMethod(String method, RequestMap requestMap) {
        return (requestMap.methods.isEmpty() || requestMap.methods.contains(method.toLowerCase()));
    }

    /**
     * SCIPIO: Check if the request satisfies the given HTTP method, except if not.
     * Added 2018-09-19.
     */
    static void checkMethod(HttpServletRequest request, RequestMap requestMap) throws MethodNotAllowedException {
        String method = request.getMethod();
        if (!acceptsMethod(method, requestMap)) {
            String msg = UtilProperties.getMessage("WebappUiLabels", "RequestMethodNotMatchConfig",
                    UtilMisc.toList(requestMap.getUri(), method),
                    // SCIPIO: this is wrong, exception messages should be english (we show a better one from ControlServlet)
                    //UtilHttp.getLocale(request)
                    Locale.ENGLISH
                    );
            throw new MethodNotAllowedException(msg);
        }
    }
    
    public void doRequest(HttpServletRequest request, HttpServletResponse response, String requestUri) throws RequestHandlerException, RequestHandlerExceptionAllowExternalRequests {
        HttpSession session = request.getSession();
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        GenericValue userLogin = (GenericValue) session.getAttribute("userLogin");
        doRequest(request, response, requestUri, userLogin, delegator);
    }

    public void doRequest(HttpServletRequest request, HttpServletResponse response, String chain,
            GenericValue userLogin, Delegator delegator) throws RequestHandlerException, RequestHandlerExceptionAllowExternalRequests {

        final boolean throwRequestHandlerExceptionOnMissingLocalRequest = EntityUtilProperties.propertyValueEqualsIgnoreCase(
                "requestHandler", "throwRequestHandlerExceptionOnMissingLocalRequest", "Y", delegator);
        long startTime = System.currentTimeMillis();
        HttpSession session = request.getSession();

        // get the controllerConfig once for this method so we don't have to get it over and over inside the method
        ConfigXMLReader.ControllerConfig controllerConfig = this.getControllerConfig();
        
        if (controllerConfig == null) { // SCIPIO: 2018-11-08: Handle error more cleanly
            throw new RequestHandlerException("Could not process controller request"
                    + " for webapp [" + request.getContextPath() + "] because its controller failed to load ("
                    + this.controllerConfigURL + ")");
        }

        Map<String, ConfigXMLReader.RequestMap> requestMapMap = null;
        // SCIPIO: Use pre-parsed number
        //String statusCodeString = null;
        Integer statusCode = null;
        try {
            requestMapMap = controllerConfig.getRequestMapMap();
            //statusCodeString = controllerConfig.getStatusCode();
            statusCode = controllerConfig.getStatusCodeNumber();
        } catch (WebAppConfigurationException e) {
            Debug.logError(e, "Exception thrown while parsing controller.xml file: ", module);
            throw new RequestHandlerException(e);
        }
        //if (UtilValidate.isEmpty(statusCodeString)) {
        if (statusCode == null) {
            statusCode = defaultStatusCodeNumber;
        }

        // workaround if we are in the root webapp
        String cname = UtilHttp.getApplicationName(request);

        // Grab data from request object to process
        String defaultRequestUri = RequestHandler.getRequestUri(request.getPathInfo());
        if (request.getAttribute("targetRequestUri") == null) {
            if (request.getSession().getAttribute("_PREVIOUS_REQUEST_") != null) {
                request.setAttribute("targetRequestUri", request.getSession().getAttribute("_PREVIOUS_REQUEST_"));
            } else {
                request.setAttribute("targetRequestUri", "/" + defaultRequestUri);
            }
        }

        // SCIPIO: may now prevent this
        //String overrideViewUri = RequestHandler.getOverrideViewUri(request.getPathInfo());
        String overrideViewUri = null;
        if (allowOverrideViewUri) {
            overrideViewUri = RequestHandler.getOverrideViewUri(request.getPathInfo());
        }

        String requestMissingErrorMessage = "Unknown request [" + defaultRequestUri + "]; this request does not exist or cannot be called directly.";
        ConfigXMLReader.RequestMap requestMap = null;
        if (defaultRequestUri != null) {
            requestMap = requestMapMap.get(defaultRequestUri);
        }
        // check for default request
        if (requestMap == null) {
            String defaultRequest;
            try {
                defaultRequest = controllerConfig.getDefaultRequest();
            } catch (WebAppConfigurationException e) {
                Debug.logError(e, "Exception thrown while parsing controller.xml file: ", module);
                throw new RequestHandlerException(e);
            }
            if (defaultRequest != null) { // required! to avoid a null pointer exception and generate a requesthandler exception if default request not found.
                requestMap = requestMapMap.get(defaultRequest);
            }
        }

        // check for override view
        if (overrideViewUri != null) {
            ConfigXMLReader.ViewMap viewMap;
            try {
                viewMap = getControllerConfig().getViewMapMap().get(overrideViewUri);
                if (viewMap == null) {
                    String defaultRequest = controllerConfig.getDefaultRequest();
                    if (defaultRequest != null) { // required! to avoid a null pointer exception and generate a requesthandler exception if default request not found.
                        requestMap = requestMapMap.get(defaultRequest);
                    }
                }
            } catch (WebAppConfigurationException e) {
                Debug.logError(e, "Exception thrown while parsing controller.xml file: ", module);
                throw new RequestHandlerException(e);
            }
        }

        // if no matching request is found in the controller, depending on throwRequestHandlerExceptionOnMissingLocalRequest
        //  we throw a RequestHandlerException or RequestHandlerExceptionAllowExternalRequests
        if (requestMap == null) {
            if (throwRequestHandlerExceptionOnMissingLocalRequest) throw new RequestHandlerException(requestMissingErrorMessage);
            else throw new RequestHandlerExceptionAllowExternalRequests();
        }

        String eventReturn = null;
        if (requestMap.metrics != null && requestMap.metrics.getThreshold() != 0.0 && requestMap.metrics.getTotalEvents() > 3 && requestMap.metrics.getThreshold() < requestMap.metrics.getServiceRate()) {
            eventReturn = "threshold-exceeded";
        }
        ConfigXMLReader.RequestMap originalRequestMap = requestMap; // Save this so we can update the correct performance metrics.


        boolean interruptRequest = false;

        // Check for chained request.
        if (chain != null) {
            String chainRequestUri = RequestHandler.getRequestUri(chain);
            requestMap = requestMapMap.get(chainRequestUri);
            if (requestMap == null) {
                throw new RequestHandlerException("Unknown chained request [" + chainRequestUri + "]; this request does not exist");
            }
            if (request.getAttribute("_POST_CHAIN_VIEW_") != null) {
                overrideViewUri = (String) request.getAttribute("_POST_CHAIN_VIEW_");
            } else {
                // SCIPIO: may now prevent this
                if (allowOverrideViewUri) {
                    overrideViewUri = RequestHandler.getOverrideViewUri(chain);
                }
            }
            if (overrideViewUri != null) {
                // put this in a request attribute early in case an event needs to access it
                // not using _POST_CHAIN_VIEW_ because it shouldn't be set unless the event execution is successful
                request.setAttribute("_CURRENT_CHAIN_VIEW_", overrideViewUri);
            }
            if (Debug.infoOn()) Debug.logInfo("[RequestHandler]: Chain in place: requestUri=" + chainRequestUri + " overrideViewUri=" + overrideViewUri + showSessionId(request), module);
        } else {
            // Check if X509 is required and we are not secure; throw exception
            if (!request.isSecure() && requestMap.securityCert) {
                throw new RequestHandlerException(requestMissingErrorMessage);
            }

            // Check to make sure we are allowed to access this request directly. (Also checks if this request is defined.)
            // If the request cannot be called, or is not defined, check and see if there is a default-request we can process
            if (!requestMap.securityDirectRequest) {
                String defaultRequest;
                try {
                    defaultRequest = controllerConfig.getDefaultRequest();
                } catch (WebAppConfigurationException e) {
                    Debug.logError(e, "Exception thrown while parsing controller.xml file: ", module);
                    throw new RequestHandlerException(e);
                }
                if (defaultRequest == null || !requestMapMap.get(defaultRequest).securityDirectRequest) {
                    // use the same message as if it was missing for security reasons, ie so can't tell if it is missing or direct request is not allowed
                    throw new RequestHandlerException(requestMissingErrorMessage);
                } else {
                    requestMap = requestMapMap.get(defaultRequest);
                }
            }
            // Check if we SHOULD be secure and are not. (SCIPIO: 2017-11-18: factored out dispersed secure checks)
            boolean isSecure = RequestLinkUtil.isEffectiveSecure(request); // SCIPIO: 2018: replace request.isSecure()
            if (!isSecure && requestMap.securityHttps) {
                // If the request method was POST then return an error to avoid problems with XSRF where the request may have come from another machine/program and had the same session ID but was not encrypted as it should have been (we used to let it pass to not lose data since it was too late to protect that data anyway)
                if ("POST".equalsIgnoreCase(request.getMethod())) {
                    // we can't redirect with the body parameters, and for better security from XSRF, just return an error message
                    Locale locale = UtilHttp.getLocale(request);
                    String errMsg = UtilProperties.getMessage("WebappUiLabels", "requestHandler.InsecureFormPostToSecureRequest", locale);
                    Debug.logError("Got a insecure (non-https) form POST to a secure (http) request [" + requestMap.uri + "], returning error", module);

                    // see if HTTPS is enabled, if not then log a warning instead of throwing an exception
                    Boolean enableHttps = null;
                    String webSiteId = WebSiteWorker.getWebSiteId(request);
                    if (webSiteId != null) {
                        try {
                            GenericValue webSite = EntityQuery.use(delegator).from("WebSite").where("webSiteId", webSiteId).cache().queryOne();
                            if (webSite != null) enableHttps = webSite.getBoolean("enableHttps");
                        } catch (GenericEntityException e) {
                            Debug.logWarning(e, "Problems with WebSite entity; using global defaults", module);
                        }
                    }
                    if (enableHttps == null) {
                        enableHttps = EntityUtilProperties.propertyValueEqualsIgnoreCase("url", "port.https.enabled", "Y", delegator);
                    }

                    if (Boolean.FALSE.equals(enableHttps)) {
                        Debug.logWarning("HTTPS is disabled for this site, so we can't tell if this was encrypted or not which means if a form was POSTed and it was not over HTTPS we don't know, but it would be vulnerable to an XSRF and other attacks: " + errMsg, module);
                    } else {
                        throw new RequestHandlerException(errMsg);
                    }
                } else {
                    String newUrl = getFullIncomingURL(request, response, null); // SCIPIO: refactored
                    // SCIPIO: this is poor
                    //if (newUrl.toUpperCase().startsWith("HTTPS")) {
                    if (RequestLinkUtil.isUrlProtocol(newUrl, "https")) {
                        // if we are supposed to be secure, redirect secure.
                        callRedirect(newUrl, response, request, statusCode, AttributesSpec.NONE, "close"); // SCIPIO: save-request="none" here
                        return;
                    }
                }
            }

            // Check for HTTPS client (x.509) security
            if (request.isSecure() && requestMap.securityCert) {
                X509Certificate[] clientCerts = (X509Certificate[]) request.getAttribute("javax.servlet.request.X509Certificate"); // 2.2 spec
                if (clientCerts == null) {
                    clientCerts = (X509Certificate[]) request.getAttribute("javax.net.ssl.peer_certificates"); // 2.1 spec
                }
                if (clientCerts == null) {
                    Debug.logWarning("Received no client certificates from browser", module);
                }

                // check if the client has a valid certificate (in our db store)
                boolean foundTrustedCert = false;

                if (clientCerts == null) {
                    throw new RequestHandlerException(requestMissingErrorMessage);
                } else {
                    if (Debug.infoOn()) {
                        for (int i = 0; i < clientCerts.length; i++) {
                            Debug.logInfo(clientCerts[i].getSubjectX500Principal().getName(), module);
                        }
                    }

                    // check if this is a trusted cert
                    if (SSLUtil.isClientTrusted(clientCerts, null)) {
                        foundTrustedCert = true;
                    }
                }

                if (!foundTrustedCert) {
                    Debug.logWarning(requestMissingErrorMessage, module);
                    throw new RequestHandlerException(requestMissingErrorMessage);
                }
            }

            // SCIPIO: Check request HTTP method
            checkMethod(request, requestMap);

            // If its the first visit run the first visit events.
            if (this.trackVisit(request) && session.getAttribute("_FIRST_VISIT_EVENTS_") == null) {
                if (Debug.infoOn())
                    Debug.logInfo("This is the first request in this visit." + showSessionId(request), module);
                session.setAttribute("_FIRST_VISIT_EVENTS_", "complete");
                try {
                    for (ConfigXMLReader.Event event: controllerConfig.getFirstVisitEventList().values()) {
                        try {
                            String returnString = this.runEvent(request, response, event, null, "firstvisit");
                            if (returnString == null || "none".equalsIgnoreCase(returnString)) {
                                interruptRequest = true;
                            } else if (!"success".equalsIgnoreCase(returnString)) {
                                throw new EventHandlerException("First-Visit event did not return 'success'.");
                            }
                        } catch (EventHandlerException e) {
                            Debug.logError(e, module);
                        }
                    }
                } catch (WebAppConfigurationException e) {
                    Debug.logError(e, "Exception thrown while parsing controller.xml file: ", module);
                    throw new RequestHandlerException(e);
                }
            }

            // Invoke the pre-processor (but NOT in a chain)
            try {
                for (ConfigXMLReader.Event event: controllerConfig.getPreprocessorEventList().values()) {
                    try {
                        String returnString = this.runEvent(request, response, event, null, "preprocessor");
                        if (returnString == null || "none".equalsIgnoreCase(returnString)) {
                            interruptRequest = true;
                        } else if (!"success".equalsIgnoreCase(returnString)) {
                            if (!returnString.contains(":_protect_:")) {
                                throw new EventHandlerException("Pre-Processor event [" + event.invoke + "] did not return 'success'.");
                            } else { // protect the view normally rendered and redirect to error response view
                                returnString = returnString.replace(":_protect_:", "");
                                if (returnString.length() > 0) {
                                    request.setAttribute("_ERROR_MESSAGE_", returnString);
                                }
                                eventReturn = null;
                                // check to see if there is a "protect" response, if so it's ok else show the default_error_response_view
                                if (!requestMap.requestResponseMap.containsKey("protect")) {
                                    String protectView = controllerConfig.getProtectView();
                                    if (protectView != null) {
                                        overrideViewUri = protectView;
                                    } else {
                                        overrideViewUri = EntityUtilProperties.getPropertyValue("security", "default.error.response.view", delegator);
                                        overrideViewUri = overrideViewUri.replace("view:", "");
                                        if ("none:".equals(overrideViewUri)) {
                                            interruptRequest = true;
                                        }
                                    }
                                }
                            }
                        }
                    } catch (EventHandlerException e) {
                        Debug.logError(e, module);
                    }
                }
            } catch (WebAppConfigurationException e) {
                Debug.logError(e, "Exception thrown while parsing controller.xml file: ", module);
                throw new RequestHandlerException(e);
            }
        }

        // Pre-Processor/First-Visit event(s) can interrupt the flow by returning null.
        // Warning: this could cause problems if more then one event attempts to return a response.
        if (interruptRequest) {
            if (Debug.infoOn()) Debug.logInfo("[Pre-Processor Interrupted Request, not running: [" + requestMap.uri + "]. " + showSessionId(request), module);
            return;
        }

        if (Debug.verboseOn()) Debug.logVerbose("[Processing Request]: " + requestMap.uri + showSessionId(request), module);
        request.setAttribute("thisRequestUri", requestMap.uri); // store the actual request URI

        // SCIPIO
        ConfigXMLReader.ViewAsJsonConfig viewAsJsonConfig;
        try {
            viewAsJsonConfig = controllerConfig.getViewAsJsonConfigOrDefault();
        } catch (WebAppConfigurationException e) {
            Debug.logError(e, "Exception thrown while parsing controller.xml file: ", module);
            throw new RequestHandlerException(e);
        }
        boolean viewAsJson = ViewAsJsonUtil.isViewAsJson(request, viewAsJsonConfig);

        // Perform security check.
        if (requestMap.securityAuth) {
            // Invoke the security handler
            // catch exceptions and throw RequestHandlerException if failed.
            if (Debug.verboseOn()) Debug.logVerbose("[RequestHandler]: AuthRequired. Running security check." + showSessionId(request), module);
            ConfigXMLReader.Event checkLoginEvent = requestMapMap.get("checkLogin").event;
            String checkLoginReturnString = null;

            try {
                checkLoginReturnString = this.runEvent(request, response, checkLoginEvent, null, "security-auth");
            } catch (EventHandlerException e) {
                throw new RequestHandlerException(e.getMessage(), e);
            }
            if (!"success".equalsIgnoreCase(checkLoginReturnString)) {
                // previous URL already saved by event, so just do as the return says...
                eventReturn = checkLoginReturnString;
                // if the request is an ajax request we don't want to return the default login check
                if (!"XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
                    requestMap = requestMapMap.get("checkLogin");
                } else {
                    // SCIPIO: 2017-05-15: for viewAsJson we have to check if we should
                    // use the regular or not
                    if (viewAsJson) {
                        if (ViewAsJsonUtil.isViewAsJsonRegularLogin(request, viewAsJsonConfig)) {
                            requestMap = requestMapMap.get("checkLogin");
                        } else {
                            // SCIPIO: If not using the regular login, we have to discard the render target expression, if any
                            requestMap = requestMapMap.get("ajaxCheckLogin");
                        }
                    } else {
                        requestMap = requestMapMap.get("ajaxCheckLogin");
                    }
                }

                // SCIPIO: if we require login, we may need to support an alternate render expr to handle login case
                Object scpLoginRenderTargetExpr = RenderTargetUtil.getRawRenderTargetExpr(request, RenderTargetUtil.LOGINRENDERTARGETEXPR_REQPARAM);
                if (scpLoginRenderTargetExpr != null) {
                    RenderTargetUtil.setRawRenderTargetExpr(request, scpLoginRenderTargetExpr);
                }
            }
            // SCIPIO: we have to mark a flag to say if was logged in for viewAsJson
            ViewAsJsonUtil.setRenderOutParam(request, ViewAsJsonUtil.LOGGEDIN_OUTPARAM, "success".equalsIgnoreCase(checkLoginReturnString));
        }

        // after security check but before running the event, see if a post-login redirect has completed and we have data from the pre-login request form to use now
        // we know this is the case if the _PREVIOUS_PARAM_MAP_ attribute is there, but the _PREVIOUS_REQUEST_ attribute has already been removed
        if (request.getSession().getAttribute("_PREVIOUS_PARAM_MAP_FORM_") != null && request.getSession().getAttribute("_PREVIOUS_REQUEST_") == null) {
            Map<String, Object> previousParamMap = UtilGenerics.checkMap(request.getSession().getAttribute("_PREVIOUS_PARAM_MAP_FORM_"), String.class, Object.class);
            for (Map.Entry<String, Object> previousParamEntry: previousParamMap.entrySet()) {
                request.setAttribute(previousParamEntry.getKey(), previousParamEntry.getValue());
            }

            // to avoid this data being included again, now remove the _PREVIOUS_PARAM_MAP_ attribute
            request.getSession().removeAttribute("_PREVIOUS_PARAM_MAP_FORM_");
        }

        // now we can start looking for the next request response to use
        ConfigXMLReader.RequestResponse nextRequestResponse = null;

        // Invoke the defined event (unless login failed)
        if (eventReturn == null && requestMap.event != null) {
            if (requestMap.event.type != null && requestMap.event.path != null && requestMap.event.invoke != null) {
                try {
                    long eventStartTime = System.currentTimeMillis();

                    // run the request event
                    eventReturn = this.runEvent(request, response, requestMap.event, requestMap, "request");

                    if (requestMap.event.metrics != null) {
                        requestMap.event.metrics.recordServiceRate(1, System.currentTimeMillis() - startTime);
                    }

                    // save the server hit for the request event
                    if (this.trackStats(request)) {
                        ServerHitBin.countEvent(cname + "." + requestMap.event.invoke, request, eventStartTime,
                                System.currentTimeMillis() - eventStartTime, userLogin);
                    }

                    // set the default event return
                    if (eventReturn == null) {
                        nextRequestResponse = ConfigXMLReader.emptyNoneRequestResponse;
                    }
                } catch (EventHandlerException e) {
                    // check to see if there is an "error" response, if so go there and make an request error message
                    if (requestMap.requestResponseMap.containsKey("error")) {
                        eventReturn = "error";
                        Locale locale = UtilHttp.getLocale(request);
                        String errMsg = UtilProperties.getMessage("WebappUiLabels", "requestHandler.error_call_event", locale);
                        request.setAttribute("_ERROR_MESSAGE_", errMsg + ": " + e.toString());
                    } else {
                        throw new RequestHandlerException("Error calling event and no error response was specified", e);
                    }
                }
            }
        }

        // Process the eventReturn
        // at this point eventReturnString is finalized, so get the RequestResponse
        ConfigXMLReader.RequestResponse eventReturnBasedRequestResponse;
        if (eventReturn == null) {
            eventReturnBasedRequestResponse = null;
        } else {
            eventReturnBasedRequestResponse = requestMap.requestResponseMap.get(eventReturn);
            if (eventReturnBasedRequestResponse == null && "none".equals(eventReturn)) {
                eventReturnBasedRequestResponse = ConfigXMLReader.emptyNoneRequestResponse;
            }
        }
        if (eventReturnBasedRequestResponse != null) {
            //String eventReturnBasedResponse = requestResponse.value;
            if (Debug.verboseOn()) Debug.logVerbose("[Response Qualified]: " + eventReturnBasedRequestResponse.name + ", " + eventReturnBasedRequestResponse.type + ":" + eventReturnBasedRequestResponse.value + showSessionId(request), module);

            // If error, then display more error messages:
            if ("error".equals(eventReturnBasedRequestResponse.name)) {
                if (Debug.errorOn()) {
                    String errorMessageHeader = "Request " + requestMap.uri + " caused an error with the following message: ";
                    if (request.getAttribute("_ERROR_MESSAGE_") != null) {
                        Debug.logError(errorMessageHeader + request.getAttribute("_ERROR_MESSAGE_"), module);
                    }
                    if (request.getAttribute("_ERROR_MESSAGE_LIST_") != null) {
                        Debug.logError(errorMessageHeader + request.getAttribute("_ERROR_MESSAGE_LIST_"), module);
                    }
                }
            }
        } else if (eventReturn != null) {
            // only log this warning if there is an eventReturn (ie skip if no event, etc)
            Debug.logWarning("Could not find response in request [" + requestMap.uri + "] for event return [" + eventReturn + "]", module);
        }

        // Set the next view (don't use event return if success, default to nextView (which is set to eventReturn later if null); also even if success if it is a type "none" response ignore the nextView, ie use the eventReturn)
        if (eventReturnBasedRequestResponse != null && (!"success".equals(eventReturnBasedRequestResponse.name) || "none".equals(eventReturnBasedRequestResponse.type))) nextRequestResponse = eventReturnBasedRequestResponse;

        // get the previous request info
        String previousRequest = (String) request.getSession().getAttribute("_PREVIOUS_REQUEST_");
        String loginPass = (String) request.getAttribute("_LOGIN_PASSED_");

        // restore previous redirected request's attribute, so redirected page can display previous request's error msg etc.
        String preReqAttStr = (String) request.getSession().getAttribute("_REQ_ATTR_MAP_");
        if (preReqAttStr != null) {
            request.getSession().removeAttribute("_REQ_ATTR_MAP_");
            byte[] reqAttrMapBytes = StringUtil.fromHexString(preReqAttStr);
            Map<String, Object> preRequestMap = checkMap(UtilObject.getObject(reqAttrMapBytes), String.class, Object.class);
            if (UtilValidate.isNotEmpty(preRequestMap)) {
                RestoreAttrPolicyInvoker<?> attrPolicyInvoker = RedirectAttrPolicy.RestorePolicy.getInvoker(request);
                for (Map.Entry<String, Object> entry: preRequestMap.entrySet()) {
                    String key = entry.getKey();
                    // SCIPIO: Let's be smarter
                    //if ("_ERROR_MESSAGE_LIST_".equals(key) || "_ERROR_MESSAGE_MAP_".equals(key) || "_ERROR_MESSAGE_".equals(key) ||
                    //        "_EVENT_MESSAGE_LIST_".equals(key) || "_EVENT_MESSAGE_".equals(key)) {
                    if (EventUtil.isEventErrorMsgAttrName(key)) {
                        // SCIPIO: New RequestAttrPolicy callbacks
                        //request.setAttribute(key, entry.getValue());
                        attrPolicyInvoker.filterRestoreAttrToRequest(entry, preRequestMap);
                    }
                }
            }
        }

        if (Debug.verboseOn()) Debug.logVerbose("[RequestHandler]: previousRequest - " + previousRequest + " (" + loginPass + ")" + showSessionId(request), module);

        // if previous request exists, and a login just succeeded, do that now.
        if (previousRequest != null && loginPass != null && "TRUE".equalsIgnoreCase(loginPass)) {
            request.getSession().removeAttribute("_PREVIOUS_REQUEST_");
            // special case to avoid login/logout looping: if request was "logout" before the login, change to null for default success view; do the same for "login" to avoid going back to the same page
            if ("logout".equals(previousRequest) || "/logout".equals(previousRequest) || "login".equals(previousRequest) || "/login".equals(previousRequest) || "checkLogin".equals(previousRequest) || "/checkLogin".equals(previousRequest) || "/checkLogin/login".equals(previousRequest)) {
                Debug.logWarning("Found special _PREVIOUS_REQUEST_ of [" + previousRequest + "], setting to null to avoid problems, not running request again", module);
            } else {
                if (Debug.infoOn()) Debug.logInfo("[Doing Previous Request]: " + previousRequest + showSessionId(request), module);

                // note that the previous form parameters are not setup (only the URL ones here), they will be found in the session later and handled when the old request redirect comes back
                Map<String, Object> previousParamMap = UtilGenerics.checkMap(request.getSession().getAttribute("_PREVIOUS_PARAM_MAP_URL_"), String.class, Object.class);
                String queryString = UtilHttp.urlEncodeArgs(previousParamMap, false);
                String redirectTarget = previousRequest;
                if (UtilValidate.isNotEmpty(queryString)) {
                    redirectTarget += "?" + queryString;
                }

                // SCIPIO: Always make full link early
                //callRedirect(makeLink(request, response, redirectTarget), response, request, statusCodeString);
                callRedirect(makeLinkFull(request, response, redirectTarget), response, request, statusCode, AttributesSpec.NONE, null); // SCIPIO: save-request="none" here
                return;
            }
        }

        ConfigXMLReader.RequestResponse successResponse = requestMap.requestResponseMap.get("success");
        if ((eventReturn == null || "success".equals(eventReturn)) && successResponse != null && "request".equals(successResponse.type)) {
            // chains will override any url defined views; but we will save the view for the very end
            if (UtilValidate.isNotEmpty(overrideViewUri)) {
                request.setAttribute("_POST_CHAIN_VIEW_", overrideViewUri);
            }
            nextRequestResponse = successResponse;
        }

        // Make sure we have some sort of response to go to
        if (nextRequestResponse == null) nextRequestResponse = successResponse;

        if (nextRequestResponse == null) {
            throw new RequestHandlerException("Illegal response; handler could not process request [" + requestMap.uri + "] and event return [" + eventReturn + "].");
        }

        // SCIPIO: Parse value
        String nextRequestResponseValue = parseResponseValue(request, response, nextRequestResponse, requestMap);
        // SCIPIO: Determine if should prevent view-saving operations
        boolean allowViewSave = nextRequestResponse.getTypeEnum().isViewType() ?
                isAllowViewSave(nextRequestResponseValue, request, controllerConfig, requestMap, nextRequestResponse, viewAsJson, viewAsJsonConfig) : false;

        if (Debug.verboseOn()) Debug.logVerbose("[Event Response Selected]  type=" + nextRequestResponse.type + ", value=" + nextRequestResponse.value + " (effective: " + nextRequestResponseValue + "). " + showSessionId(request), module); // SCIPIO: effective

        // ========== Handle the responses - chains/views ==========

        // if the request has the save-last-view attribute set, save it now before the view can be rendered or other chain done so that the _LAST* session attributes will represent the previous request
        if (nextRequestResponse.saveLastView && allowViewSave) { // SCIPIO: don't save for viewAsJson unless enabled
            // Debug.logInfo("======save last view: " + session.getAttribute("_LAST_VIEW_NAME_"));
            String lastViewName = (String) session.getAttribute("_LAST_VIEW_NAME_");
            // Do not save the view if the last view is the same as the current view and saveCurrentView is false
            if (!(!nextRequestResponse.saveCurrentView && "view".equals(nextRequestResponse.type) && nextRequestResponseValue.equals(lastViewName))) {
                session.setAttribute("_SAVED_VIEW_NAME_", session.getAttribute("_LAST_VIEW_NAME_"));
                session.setAttribute("_SAVED_VIEW_PARAMS_", session.getAttribute("_LAST_VIEW_PARAMS_"));
            }
        }
        String saveName = null;
        if (nextRequestResponse.saveCurrentView) { saveName = "SAVED"; }
        if (nextRequestResponse.saveHomeView) { saveName = "HOME"; }

        if ("request".equals(nextRequestResponse.type)) {
            // chained request
            Debug.logInfo("[RequestHandler.doRequest]: Response is a chained request." + showSessionId(request), module);
            doRequest(request, response, nextRequestResponseValue, userLogin, delegator);
        } else {
            // ======== handle views ========

            // first invoke the post-processor events.
            try {
                for (ConfigXMLReader.Event event: controllerConfig.getPostprocessorEventList().values()) {
                    try {
                        String returnString = this.runEvent(request, response, event, requestMap, "postprocessor");
                        if (returnString != null && !"success".equalsIgnoreCase(returnString)) {
                            throw new EventHandlerException("Post-Processor event did not return 'success'.");
                        }
                    } catch (EventHandlerException e) {
                        Debug.logError(e, module);
                    }
                }
            } catch (WebAppConfigurationException e) {
                Debug.logError(e, "Exception thrown while parsing controller.xml file: ", module);
                throw new RequestHandlerException(e);
            }

            // SCIPIO: 2019-03-06: Cleanup event messages (e.g. the default service event success message).
            cleanupEventMessages(request);

            // SCIPIO: Use straight ints
            //String responseStatusCode  = nextRequestResponse.statusCode;
            Integer responseStatusCode = nextRequestResponse.getStatusCodeNumber();
            if(responseStatusCode != null) {
                statusCode = responseStatusCode;
            }

            // SCIPIO: Optimized
            if (RequestResponse.Type.URL == nextRequestResponse.getTypeEnum()) { //if ("url".equals(nextRequestResponse.type)) {
                if (Debug.verboseOn()) Debug.logVerbose("[RequestHandler.doRequest]: Response is a URL redirect." + showSessionId(request), module);
                // SCIPIO: Sanity check
                if (nextRequestResponseValue == null || nextRequestResponseValue.isEmpty()) {
                    Debug.logError("Scipio: Redirect URL is empty (request map URI: " + requestMap.uri + ")", module);
                    throw new RequestHandlerException("Scipio: Redirect URL is empty (request map URI: " + requestMap.uri + ")");
                }
                // SCIPIO: NOTE: Contrary to others, currently leaving this unchanged; full URLs may be completely external, and not sure want to pass them through encodeURL...
                callRedirect(nextRequestResponseValue, response, request, statusCode, nextRequestResponse.getRedirectAttributes(), nextRequestResponse.getConnectionState()); // SCIPIO: save-request
            } else if (RequestResponse.Type.CROSS_REDIRECT == nextRequestResponse.getTypeEnum()) { //} else if ("cross-redirect".equals(nextRequestResponse.type)) {
                // check for a cross-application redirect
                if (Debug.verboseOn()) Debug.logVerbose("[RequestHandler.doRequest]: Response is a Cross-Application redirect." + showSessionId(request), module);
                // SCIPIO: Sanity check
                if (nextRequestResponseValue == null || nextRequestResponseValue.isEmpty()) {
                    Debug.logError("Scipio: Cross-redirect URL is empty (request map URI: " + requestMap.uri + ")", module);
                    throw new RequestHandlerException("Scipio: Cross-redirect URL is empty (request map URI: " + requestMap.uri + ")");
                }
                String url = nextRequestResponseValue.startsWith("/") ? nextRequestResponseValue : "/" + nextRequestResponseValue;
                // SCIPIO: Modified to pass through encodeURL and more intelligent link-building method
                // NOTE: no support for webSiteId, so absPath assumed true
                //callRedirect(url + this.makeQueryString(request, nextRequestResponse), response, request, statusCodeString);
                // SCIPIO: We MUST pass fullPath=true so that the host part will be looked up in Ofbiz entities as opposed to decided by Tomcat during redirect operation
                String targetUrl = makeLinkAutoFull(request, response, url + this.makeQueryString(request, nextRequestResponse), true, true, null, null);
                // SCIPIO: Sanity check
                if (targetUrl == null || targetUrl.isEmpty()) {
                    Debug.logError("Scipio: Could not build link for or resolve cross-redirect URI ('" + nextRequestResponseValue + "') (request map URI: " + requestMap.uri + ")", module);
                    throw new RequestHandlerException("Scipio: Could not build link for or resolve cross-redirect URI ('" + nextRequestResponseValue + "') (request map URI: " + requestMap.uri + ")");
                }
                callRedirect(targetUrl, response, request, statusCode, nextRequestResponse.getRedirectAttributes(), nextRequestResponse.getConnectionState()); // SCIPIO: save-request
            } else if (RequestResponse.Type.REQUEST_REDIRECT == nextRequestResponse.getTypeEnum()) { //} else if ("request-redirect".equals(nextRequestResponse.type)) {
                if (Debug.verboseOn()) Debug.logVerbose("[RequestHandler.doRequest]: Response is a Request redirect." + showSessionId(request), module);
                // SCIPIO: Sanity check
                if (nextRequestResponseValue == null || nextRequestResponseValue.isEmpty()) {
                    Debug.logError("Scipio: Request-redirect URI is empty (request map URI: " + requestMap.uri + ")", module);
                    throw new RequestHandlerException("Scipio: Request-redirect URI is empty (request map URI: " + requestMap.uri + ")");
                }
                // SCIPIO: We MUST pass fullPath=true so that the host part will be looked up in Ofbiz entities as opposed to decided by Tomcat during redirect operation
                //callRedirect(makeLinkWithQueryString(request, response, "/" + nextRequestResponseValue, nextRequestResponse), response, request, statusCodeString);
                String targetUrl = makeLinkFullWithQueryString(request, response, "/" + nextRequestResponseValue, nextRequestResponse);
                // SCIPIO: Sanity check
                if (targetUrl == null || targetUrl.isEmpty()) {
                    Debug.logError("Scipio: Could not build link for or resolve request-redirect URI ('" + nextRequestResponseValue + "') (request map URI: " + requestMap.uri + ")", module);
                    throw new RequestHandlerException("Scipio: Could not build link for or resolve request-redirect URI ('" + nextRequestResponseValue + "') (request map URI: " + requestMap.uri + ")");
                }
                callRedirect(targetUrl, response, request, statusCode, nextRequestResponse.getRedirectAttributes(), nextRequestResponse.getConnectionState()); // SCIPIO: save-request
            } else if (RequestResponse.Type.REQUEST_REDIRECT_NOPARAM == nextRequestResponse.getTypeEnum()) { //} else if ("request-redirect-noparam".equals(nextRequestResponse.type)) {
                if (Debug.verboseOn()) Debug.logVerbose("[RequestHandler.doRequest]: Response is a Request redirect with no parameters." + showSessionId(request), module);
                // SCIPIO: Sanity check
                if (nextRequestResponseValue == null || nextRequestResponseValue.isEmpty()) {
                    Debug.logError("Scipio: Request-redirect-noparam URI is empty (request map URI: " + requestMap.uri + ")", module);
                    throw new RequestHandlerException("Scipio: Request-redirect-noparam URI is empty (request map URI: " + requestMap.uri + ")");
                }
                // SCIPIO: We MUST pass fullPath=true so that the host part will be looked up in Ofbiz entities as opposed to decided by Tomcat during redirect operation
                //callRedirect(makeLink(request, response, nextRequestResponseValue), response, request, statusCodeString);
                String targetUrl = makeLinkFull(request, response, nextRequestResponseValue);
                // SCIPIO: Sanity check
                if (targetUrl == null || targetUrl.isEmpty()) {
                    Debug.logError("Scipio: Could not build link for or resolve request-redirect-noparam URI ('" + nextRequestResponseValue + "') (request map URI: " + requestMap.uri + ")", module);
                    throw new RequestHandlerException("Scipio: Could not build link for or resolve request-redirect-noparam URI ('" + nextRequestResponseValue + "') (request map URI: " + requestMap.uri + ")");
                }
                callRedirect(targetUrl, response, request, statusCode, nextRequestResponse.getRedirectAttributes(), nextRequestResponse.getConnectionState()); // SCIPIO: save-request
            } else if (RequestResponse.Type.REQUEST_REDIRECT_LAST == nextRequestResponse.getTypeEnum()) {
                String lastGetUrl = (String) session.getAttribute("_SCP_LAST_GET_URL_");
                if (UtilValidate.isNotEmpty(lastGetUrl)) {
                    if (Debug.verboseOn()) Debug.logVerbose("[RequestHandler.doRequest]: Response is a Request redirect to last Get URL." + showSessionId(request), module);
                    // Perform URL encoding
                    lastGetUrl = response.encodeURL(lastGetUrl);
                    callRedirect(lastGetUrl, response, request, statusCode, nextRequestResponse.getRedirectAttributes(), nextRequestResponse.getConnectionState()); // SCIPIO: save-request
                } else {
                    // SCIPIO: New type: request-redirect-last
                    if (Debug.verboseOn()) Debug.logVerbose("[RequestHandler.doRequest]: Response is a Request redirect to last Get URL, but there is not last get; going to default: " 
                            + nextRequestResponseValue.isEmpty() + showSessionId(request), module);
                    // SCIPIO: Sanity check
                    if (nextRequestResponseValue == null || nextRequestResponseValue.isEmpty()) {
                        Debug.logError("Scipio: Request-redirect-noparam URI is empty (request map URI: " + requestMap.uri + ")", module);
                        throw new RequestHandlerException("Scipio: Request-redirect-noparam URI is empty (request map URI: " + requestMap.uri + ")");
                    }
                    String targetUrl = makeLinkFull(request, response, nextRequestResponseValue);
                    // SCIPIO: Sanity check
                    if (targetUrl == null || targetUrl.isEmpty()) {
                        Debug.logError("Scipio: Could not build link for or resolve request-redirect-noparam URI ('" + nextRequestResponseValue + "') (request map URI: " + requestMap.uri + ")", module);
                        throw new RequestHandlerException("Scipio: Could not build link for or resolve request-redirect-noparam URI ('" + nextRequestResponseValue + "') (request map URI: " + requestMap.uri + ")");
                    }
                    callRedirect(targetUrl, response, request, statusCode, nextRequestResponse.getRedirectAttributes(), nextRequestResponse.getConnectionState()); // SCIPIO: save-request
                }
            } else if (RequestResponse.Type.VIEW == nextRequestResponse.getTypeEnum()) { //} else if ("view".equals(nextRequestResponse.type)) {
                if (Debug.verboseOn()) Debug.logVerbose("[RequestHandler.doRequest]: Response is a view." + showSessionId(request), module);

                // check for an override view, only used if "success" = eventReturn
                String viewName = (UtilValidate.isNotEmpty(overrideViewUri) && (eventReturn == null || "success".equals(eventReturn))) ? overrideViewUri : nextRequestResponseValue;
                // SCIPIO: Sanity check
                if (viewName == null || viewName.isEmpty()) {
                    Debug.logError("Scipio: view name is empty (request map URI: " + requestMap.uri + ")", module);
                    throw new RequestHandlerException("Scipio: view name is empty (request map URI: " + requestMap.uri + ")");
                }
                renderView(viewName, requestMap.securityExternalView, request, response, saveName, controllerConfig, viewAsJsonConfig, viewAsJson, allowViewSave);
            } else if (RequestResponse.Type.VIEW_LAST == nextRequestResponse.getTypeEnum()) { //} else if ("view-last".equals(nextRequestResponse.type)) {
                if (Debug.verboseOn()) Debug.logVerbose("[RequestHandler.doRequest]: Response is a view." + showSessionId(request), module);

                // check for an override view, only used if "success" = eventReturn
                String viewName = (UtilValidate.isNotEmpty(overrideViewUri) && (eventReturn == null || "success".equals(eventReturn))) ? overrideViewUri : nextRequestResponseValue;

                // as a further override, look for the _SAVED and then _HOME and then _LAST session attributes
                Map<String, Object> urlParams = null;
                if (session.getAttribute("_SAVED_VIEW_NAME_") != null) {
                    viewName = (String) session.getAttribute("_SAVED_VIEW_NAME_");
                    urlParams = UtilGenerics.<String, Object>checkMap(session.getAttribute("_SAVED_VIEW_PARAMS_"));
                } else if (session.getAttribute("_HOME_VIEW_NAME_") != null) {
                    viewName = (String) session.getAttribute("_HOME_VIEW_NAME_");
                    urlParams = UtilGenerics.<String, Object>checkMap(session.getAttribute("_HOME_VIEW_PARAMS_"));
                } else if (session.getAttribute("_LAST_VIEW_NAME_") != null) {
                    viewName = (String) session.getAttribute("_LAST_VIEW_NAME_");
                    urlParams = UtilGenerics.<String, Object>checkMap(session.getAttribute("_LAST_VIEW_PARAMS_"));
                } else if (UtilValidate.isNotEmpty(nextRequestResponseValue)) {
                    viewName = nextRequestResponseValue;
                }
                if (viewName == null || viewName.isEmpty()) { // SCIPIO: 2018-10-26: Default/fallback view
                    viewName = getDefaultViewLastView(viewName, nextRequestResponse, requestMap, controllerConfig, request);
                }
                if (urlParams != null) {
                    RestoreAttrPolicyInvoker<?> attrPolicyInvoker = ViewLastAttrPolicy.RestorePolicy.getInvoker(request);
                    for (Map.Entry<String, Object> urlParamEntry: urlParams.entrySet()) {
                        String key = urlParamEntry.getKey();
                        // Don't overwrite messages coming from the current event
                        if (!("_EVENT_MESSAGE_".equals(key) || "_ERROR_MESSAGE_".equals(key)
                                || "_EVENT_MESSAGE_LIST_".equals(key) || "_ERROR_MESSAGE_LIST_".equals(key))) {
                            // SCIPIO: New RequestAttrPolicy callbacks
                            //request.setAttribute(key, urlParamEntry.getValue());
                            attrPolicyInvoker.filterRestoreAttrToRequest(urlParamEntry,  urlParams); 
                        }
                    }
                }
                renderView(viewName, requestMap.securityExternalView, request, response, null, controllerConfig, viewAsJsonConfig, viewAsJson, allowViewSave);
            } else if (RequestResponse.Type.VIEW_LAST_NOPARAM == nextRequestResponse.getTypeEnum()) { //} else if ("view-last-noparam".equals(nextRequestResponse.type)) {
                 if (Debug.verboseOn()) Debug.logVerbose("[RequestHandler.doRequest]: Response is a view." + showSessionId(request), module);

                 // check for an override view, only used if "success" = eventReturn
                 String viewName = (UtilValidate.isNotEmpty(overrideViewUri) && (eventReturn == null || "success".equals(eventReturn))) ? overrideViewUri : nextRequestResponseValue;

                 // as a further override, look for the _SAVED and then _HOME and then _LAST session attributes
                 if (session.getAttribute("_SAVED_VIEW_NAME_") != null) {
                     viewName = (String) session.getAttribute("_SAVED_VIEW_NAME_");
                 } else if (session.getAttribute("_HOME_VIEW_NAME_") != null) {
                     viewName = (String) session.getAttribute("_HOME_VIEW_NAME_");
                 } else if (session.getAttribute("_LAST_VIEW_NAME_") != null) {
                     viewName = (String) session.getAttribute("_LAST_VIEW_NAME_");
                 } else if (UtilValidate.isNotEmpty(nextRequestResponseValue)) {
                     viewName = nextRequestResponseValue;
                 }
                 if (viewName == null || viewName.isEmpty()) { // SCIPIO: 2018-10-26: Default/fallback view
                     viewName = getDefaultViewLastView(viewName, nextRequestResponse, requestMap, controllerConfig, request);
                 }
                 renderView(viewName, requestMap.securityExternalView, request, response, null, controllerConfig, viewAsJsonConfig, viewAsJson, allowViewSave);
            } else if (RequestResponse.Type.VIEW_HOME == nextRequestResponse.getTypeEnum()) { //} else if ("view-home".equals(nextRequestResponse.type)) {
                if (Debug.verboseOn()) Debug.logVerbose("[RequestHandler.doRequest]: Response is a view." + showSessionId(request), module);

                // check for an override view, only used if "success" = eventReturn
                String viewName = (UtilValidate.isNotEmpty(overrideViewUri) && (eventReturn == null || "success".equals(eventReturn))) ? overrideViewUri : nextRequestResponseValue;

                // as a further override, look for the _HOME session attributes
                Map<String, Object> urlParams = null;
                if (session.getAttribute("_HOME_VIEW_NAME_") != null) {
                    viewName = (String) session.getAttribute("_HOME_VIEW_NAME_");
                    urlParams = UtilGenerics.<String, Object>checkMap(session.getAttribute("_HOME_VIEW_PARAMS_"));
                }
                if (viewName == null || viewName.isEmpty()) { // SCIPIO: 2018-10-26: Default/fallback view
                    viewName = getDefaultViewLastView(viewName, nextRequestResponse, requestMap, controllerConfig, request);
                }
                if (urlParams != null) {
                    for (Map.Entry<String, Object> urlParamEntry: urlParams.entrySet()) {
                        request.setAttribute(urlParamEntry.getKey(), urlParamEntry.getValue());
                    }
                }
                renderView(viewName, requestMap.securityExternalView, request, response, null, controllerConfig, viewAsJsonConfig, viewAsJson, allowViewSave);
            } else if (RequestResponse.Type.NONE == nextRequestResponse.getTypeEnum()) { //} else if ("none".equals(nextRequestResponse.type)) {
                // no view to render (meaning the return was processed by the event)
                if (Debug.verboseOn()) Debug.logVerbose("[RequestHandler.doRequest]: Response is handled by the event." + showSessionId(request), module);
            }
        }
        if (originalRequestMap.metrics != null) {
            originalRequestMap.metrics.recordServiceRate(1, System.currentTimeMillis() - startTime);
        }
    }

    /**
     * SCIPIO: Checks default view for view-last and view-home.
     * Added 2018-10-26.
     */
    String getDefaultViewLastView(String viewName, ConfigXMLReader.RequestResponse nextRequestResponse, RequestMap requestMap, 
            ControllerConfig controllerConfig, HttpServletRequest request) throws RequestHandlerException {
        try {
            viewName = controllerConfig.getDefaultViewLastView();
        } catch (WebAppConfigurationException e) {
            Debug.logError(e, module);
            viewName = null;
        }
        if (viewName != null) {
            Debug.logWarning("[uri: " + requestMap.uri + "] " + nextRequestResponse.type + " response could not determine view"
                    + "; showing controller default-view: " + viewName, module);
            return viewName;
        }
        Debug.logError("[uri: " + requestMap.uri + "] " + nextRequestResponse.type + " response could not determine view", module);
        throw new RequestHandlerException("Could not determine a view to render");
    }

    /**
     * SCIPIO: New feature that allows controller responses to dig values out of request attributes using the
     * EL-like syntax: ${scope.name}. The value must be a string otherwise an error is logged.
     * returned.
     * <p>
     * Currently the supported scopes are:
     * requestAttributes
     * requestParameters
     * requestAttrParam - checks attributes; if null, uses params
     * sessionAttributes
     * applicationAttributes
     * <p>
     * Returns empty string instead of null if missing, for compatibility with existing Ofbiz code.
     */
    String parseResponseValue(HttpServletRequest request, HttpServletResponse response, ConfigXMLReader.RequestResponse responseDef, ConfigXMLReader.RequestMap requestMap) {
        Object attrValue = responseDef.getValueExpr().getValue(request, response);
        if (attrValue != null) {
            if (attrValue instanceof String) {
                String attrStr = (String) attrValue;
                return attrStr;
            } else {
                if (requestMap != null) {
                    Debug.logError("Scipio: Error in request handler: The interpreted request response value '" +
                            responseDef.getValue() + "' from request URI '" + requestMap.uri + "' did not evaluate to a string; treating as empty", module);
                } else {
                    Debug.logError("Scipio: Error in request handler: The interpreted request response value '" +
                            responseDef.getValue() + "' did not evaluate to a string; treating as empty", module);
                }
            }
        }
        return "";
    }

    /**
     * SCIPIO: 2019-03-06: Cleanup event messages (e.g. the default service event success message).
     * <p>
     * If there is a service error and the _DEF_EVENT_MSG_ request attribute is present and equal
     * to the _EVENT_MESSAGE_ attribute, _EVENT_MESSAGE_ is removed. NOTE: _DEF_EVENT_MSG_ is always removed.
     * <p>
     * This is an extra patch for {@link #doRequest} to prevent the default success message when an error occurs.
     * See {@link org.ofbiz.webapp.event.ServiceEventHandler#invoke} for the code that sets these.
     */
    void cleanupEventMessages(HttpServletRequest request) {
        if (EventUtil.hasErrorMsg(request) && request.getAttribute("_DEF_EVENT_MSG_") != null && 
                request.getAttribute("_DEF_EVENT_MSG_").equals(request.getAttribute("_EVENT_MESSAGE_"))) {
            request.removeAttribute("_EVENT_MESSAGE_");
        }
        request.removeAttribute("_DEF_EVENT_MSG_"); // Always remove this, to limit its lifespan
    }

    /** Find the event handler and invoke an event. */
    public String runEvent(HttpServletRequest request, HttpServletResponse response,
            ConfigXMLReader.Event event, ConfigXMLReader.RequestMap requestMap, String trigger) throws EventHandlerException {

        // SCIPIO: 2018-11-19: implement synchronize
        String eventReturn = runEventImpl(request, response, event.getSynchronizeExprList(), 0, event, requestMap, trigger);
        if (Debug.verboseOn() || (Debug.infoOn() && "request".equals(trigger))) Debug.logInfo("Ran Event [" + event.type + ":" + event.path + "#" + event.invoke + "] from [" + trigger + "], result is [" + eventReturn + "]", module);
        return eventReturn;
    }
    
    private String runEventImpl(HttpServletRequest request, HttpServletResponse response, List<ConfigXMLReader.ValueExpr> synchronizeExprList, int synchronizeObjIndex, // SCIPIO
            ConfigXMLReader.Event event, ConfigXMLReader.RequestMap requestMap, String trigger) throws EventHandlerException {
        if (synchronizeExprList == null || synchronizeObjIndex >= synchronizeExprList.size()) {
            final EventHandler eventHandler = eventFactory.getEventHandler(event.type);
            final List<EventHandlerWrapper> wrapperList = eventFactory.getEventHandlerWrappersForTrigger(trigger);
            if (UtilValidate.isNotEmpty(wrapperList)) {
                Iterator<EventHandlerWrapper> handlers = new DelegatingEventWrapper() {
                    private int index = 0;
                    @Override
                    public boolean hasNext() {
                        return (index < wrapperList.size());
                    }
                    @Override
                    public EventHandlerWrapper next() {
                        if (index < wrapperList.size()) {
                            return wrapperList.get(index++);
                        }
                        return this;
                    }
                    @Override
                    public void init(ServletContext context) throws EventHandlerException {
                    }
                    @Override
                    public String invoke(Iterator<EventHandlerWrapper> handlers, Event event, RequestMap requestMap,
                            HttpServletRequest request, HttpServletResponse response) throws EventHandlerException {
                        return eventHandler.invoke(event, requestMap, request, response);
                    }
                };
                return handlers.next().invoke(handlers, event, requestMap, request, response);
            }
            return eventHandler.invoke(event, requestMap, request, response);
        } else {
            Object synchronizeObj = synchronizeExprList.get(synchronizeObjIndex).getValue(request, response);
            if (synchronizeObj != null) {
                synchronized(synchronizeObj) {
                    return runEventImpl(request, response, synchronizeExprList, synchronizeObjIndex + 1, event, requestMap, trigger);
                }
            } else {
                Debug.logWarning("[uri=" + requestMap.getUri() + "] Event could not synchronize on object (null): " + synchronizeExprList.get(synchronizeObjIndex).getOrigValue(), module);
                return runEventImpl(request, response, synchronizeExprList, synchronizeObjIndex + 1, event, requestMap, trigger);
            }
        }
    }
    
    private static interface DelegatingEventWrapper extends Iterator<EventHandlerWrapper>, EventHandlerWrapper { // SCIPIO
    }
    
    
    /** Returns the default error page for this request. */
    public String getDefaultErrorPage(HttpServletRequest request) {
        String errorpage = null;
        try {
            // SCIPIO: 2018-11-08: Handle controller load fail more cleanly
            //errorpage = getControllerConfig().getErrorpage();
            ControllerConfig controllerConfig = getControllerConfig();
            if (controllerConfig == null) {
                return "/error/error.jsp";
            }
            errorpage = controllerConfig.getErrorpage();
            // SCIPIO: 2017-11-14: now supports flexible expressions contains ServletContext attributes
            Map<String, Object> exprCtx = new HashMap<>();
            exprCtx.putAll(UtilHttp.getServletContextMap(request));
            exprCtx.putAll(UtilHttp.getAttributeMap(request));
            errorpage = FlexibleStringExpander.expandString(errorpage, exprCtx);
        } catch (WebAppConfigurationException e) {
            Debug.logError(e, "Exception thrown while parsing controller.xml file: ", module);
        }
        if (UtilValidate.isNotEmpty(errorpage)) return errorpage;
        return "/error/error.jsp";
    }

    /** Returns the default status-code for this request. */
    public String getStatusCode(HttpServletRequest request) {
        // SCIPIO: Now delegating
        Integer statusCode = getStatusCodeNumber(request);
        return (statusCode != null) ? statusCode.toString() : null;
    }

    /** SCIPIO: Returns the default status-code for this request, as a number. */
    public Integer getStatusCodeNumber(HttpServletRequest request) {
        Integer statusCode = null;
        try {
            // SCIPIO: 2018-11-08: Handle controller load fail more cleanly
            //statusCode = getControllerConfig().getStatusCode();
            ControllerConfig controllerConfig = getControllerConfig();
            if (controllerConfig == null) {
                return null;
            }
            statusCode = controllerConfig.getStatusCodeNumber();
        } catch (WebAppConfigurationException e) {
            Debug.logError(e, "Exception thrown while parsing controller.xml file: ", module);
        }
        return statusCode;
    }

    /** Returns the ViewFactory Object. */
    public ViewFactory getViewFactory() {
        return viewFactory;
    }

    /** Returns the EventFactory Object. */
    public EventFactory getEventFactory() {
        return eventFactory;
    }

    public static String getRequestUri(String path) {
        List<String> pathInfo = StringUtil.split(path, "/");
        if (UtilValidate.isEmpty(pathInfo)) {
            Debug.logWarning("Got nothing when splitting URI: " + path, module);
            return null;
        }
        if (pathInfo.get(0).indexOf('?') > -1) {
            return pathInfo.get(0).substring(0, pathInfo.get(0).indexOf('?'));
        } else {
            return pathInfo.get(0);
        }
    }

    public static String getOverrideViewUri(String path) {
        List<String> pathItemList = StringUtil.split(path, "/");
        if (pathItemList == null) {
            return null;
        }
        pathItemList = pathItemList.subList(1, pathItemList.size());

        String nextPage = null;
        for (String pathItem: pathItemList) {
            if (pathItem.indexOf('~') != 0) {
                if (pathItem.indexOf('?') > -1) {
                    pathItem = pathItem.substring(0, pathItem.indexOf('?'));
                }
                nextPage = (nextPage == null ? pathItem : nextPage + "/" + pathItem);
            }
        }
        return nextPage;
    }

    /**
     * Performs HTTP redirect to the given URL.
     * <p>
     * SCIPIO: NOTE: All the code currently calling this may append jsessionIds (through processing
     * of changing encode to true to correct filter hook behavior).
     * Currently I don't see how this is bad.
     * If need to remove jsessionId from redirects, could uncomment the lines below.
     * <p>
     * SCIPIO: 2018-12-12: Modified to take Integer statusCode instead of statusCodeString.
     */
    private void callRedirect(String url, HttpServletResponse resp, HttpServletRequest req, int statusCode, AttributesSpec saveAttrMap, String httpConnectionHeader) throws RequestHandlerException {
        // SCIPIO: Uncomment this to force remove jsessionId from controller redirects...
        //RequestUtil.removeJsessionId(url);
        if (Debug.infoOn()) Debug.logInfo("Sending redirect to: [" + url + "]." + showSessionId(req), module);
        // SCIPIO: sanity check
        if (url == null || url.isEmpty()) {
            Debug.logError("Scipio: Redirect URL is empty", module);
            throw new RequestHandlerException("Scipio: Redirect URL is empty");
        }
        if (!saveAttrMap.isNone()) { // SCIPIO: not for all redirects!
            // set the attributes in the session so we can access it.
            Enumeration<String> attributeNameEnum = UtilGenerics.cast(req.getAttributeNames());
            Map<String, Object> reqAttrMap = new HashMap<>();
            SaveAttrPolicyInvoker<?> attrPolicyInvoker = RedirectAttrPolicy.SavePolicy.getInvoker(req); // SCIPIO
            while (attributeNameEnum.hasMoreElements()) {
                String name = attributeNameEnum.nextElement();
                Object obj = req.getAttribute(name);
                if (obj instanceof Serializable) {
                    if (saveAttrMap.includeAttribute(name)) { // SCIPIO: includeRequestAttribute filter
                        // SCIPIO: New RequestAttrPolicy callbacks
                        //reqAttrMap.put(name, obj);
                        attrPolicyInvoker.filterSaveAttrToMap(reqAttrMap, name, obj); 
                    }
                }
            }
            // SCIPIO: NOTE: 2019-01-24: the "multiPartMap" exclude has been moved to the RedirectAttrPolicy invoker(s) for reuse
            //reqAttrMap.remove("multiPartMap");
            if (reqAttrMap.size() > 0) {
                reqAttrMap.remove("_REQUEST_HANDLER_");  // RequestHandler is not serializable and must be removed first.  See http://issues.apache.org/jira/browse/OFBIZ-750
                byte[] reqAttrMapBytes = UtilObject.getBytes(reqAttrMap);
                if (reqAttrMapBytes != null) {
                    req.getSession().setAttribute("_REQ_ATTR_MAP_", StringUtil.toHexString(reqAttrMapBytes));
                }
            }
        }
        /* SCIPIO: already int
        Integer statusCode;
        try {
            statusCode = Integer.valueOf(statusCodeString);
        } catch (NumberFormatException e) {
            statusCode = 303;
        }
        */

        // send the redirect
        try {
            resp.setStatus(statusCode);
            resp.setHeader("Location", url);
            // SCIPIO: This is not appropriate; majority of redirects in scipio are intra-webapp, followed by inter-webapp
            //resp.setHeader("Connection", "close");
            if (httpConnectionHeader != null) {
                resp.setHeader("Connection", httpConnectionHeader);
            }
        } catch (IllegalStateException ise) {
            throw new RequestHandlerException(ise.getMessage(), ise);
        }
    }

    private void renderView(String view, boolean allowExtView, HttpServletRequest req, HttpServletResponse resp, String saveName, ControllerConfig controllerConfig, ConfigXMLReader.ViewAsJsonConfig viewAsJsonConfig, boolean viewAsJson, boolean allowViewSave) throws RequestHandlerException, RequestHandlerExceptionAllowExternalRequests {
        // SCIPIO: sanity check
        if (view == null || view.isEmpty()) {
            Debug.logError("Scipio: View name is empty", module);
            throw new RequestHandlerException("Scipio: View name is empty");
        }

        GenericValue userLogin = (GenericValue) req.getSession().getAttribute("userLogin");
        // workaround if we are in the root webapp
        String cname = UtilHttp.getApplicationName(req);
        String oldView = view;

        if (UtilValidate.isNotEmpty(view) && view.charAt(0) == '/') {
            view = view.substring(1);
        }

        // if the view name starts with the control servlet name and a /, then it was an
        // attempt to override the default view with a call back into the control servlet,
        // so just get the target view name and use that
        String servletName = req.getServletPath();
        if (servletName.startsWith("/")) {
            servletName = servletName.substring(1);
        }

        if (Debug.infoOn()) Debug.logInfo("Rendering View [" + view + "]." + showSessionId(req), module);
        if (view.startsWith(servletName + "/")) {
            view = view.substring(servletName.length() + 1);
            if (Debug.infoOn()) Debug.logInfo("a manual control servlet request was received, removing control servlet path resulting in: view=" + view, module);
        }

        if (Debug.verboseOn()) Debug.logVerbose("[Getting View Map]: " + view + showSessionId(req), module);

        // before mapping the view, set a request attribute so we know where we are
        req.setAttribute("_CURRENT_VIEW_", view);

        if (allowViewSave) {
            // save the view in the session for the last view, plus the parameters Map (can use all parameters as they 
            // will never go into a URL, will only stay in the session and extra data will be ignored as we
            // won't go to the original request just the view); note that this is saved after the request/view processing
            // has finished so when those run they will get the value from the previous request
            Map<String, Object> paramMap = UtilHttp.getParameterMap(req, ViewAsJsonUtil.VIEWASJSON_RENDERTARGET_REQPARAM_ALL, false); // SCIPIO: SPECIAL EXCLUDES: these will mess up rendering if they aren't excluded
            // add in the attributes as well so everything needed for the rendering context will be in place if/when we get back to this view
            paramMap.putAll(UtilHttp.getAttributeMap(req));
            // SCIPIO: 2017-10-04: NEW VIEW-SAVE ATTRIBUTE EXCLUDES - these can be set by event to prevent cached and volatile results from going into session
            // NOTE: These also must prevent request parameters with same name, so just remove all these names from the map
            SaveAttrPolicyInvoker<?> attrPolicyInvoker = ViewLastAttrPolicy.SavePolicy.getInvoker(req);
            attrPolicyInvoker.filterMapAttr(paramMap); // SCIPIO: New RequestAttrPolicy callbacks
            UtilMisc.makeMapSerializable(paramMap);
            if (paramMap.containsKey("_LAST_VIEW_NAME_")) { // Used by lookups to keep the real view (request)
                req.getSession().setAttribute("_LAST_VIEW_NAME_", paramMap.get("_LAST_VIEW_NAME_"));
            } else {
                req.getSession().setAttribute("_LAST_VIEW_NAME_", view);
            }
            req.getSession().setAttribute("_LAST_VIEW_PARAMS_", paramMap);

            if ("SAVED".equals(saveName)) {
                //Debug.logInfo("======save current view: " + view);
                req.getSession().setAttribute("_SAVED_VIEW_NAME_", view);
                req.getSession().setAttribute("_SAVED_VIEW_PARAMS_", paramMap);
            }

            if ("HOME".equals(saveName)) {
                //Debug.logInfo("======save home view: " + view);
                req.getSession().setAttribute("_HOME_VIEW_NAME_", view);
                req.getSession().setAttribute("_HOME_VIEW_PARAMS_", paramMap);
                // clear other saved views
                req.getSession().removeAttribute("_SAVED_VIEW_NAME_");
                req.getSession().removeAttribute("_SAVED_VIEW_PARAMS_");
            }

            // SCIPIO: request-redirect-last
            // FIXME?: may not work when viewAsJson==true
            if ("get".equalsIgnoreCase(req.getMethod())) {
                String lastGetUrl = getFullIncomingURL(req, resp, null); // SCIPIO: refactored
                req.getSession().setAttribute("_SCP_LAST_GET_URL_", lastGetUrl);
            }
        }

        ConfigXMLReader.ViewMap viewMap = null;
        try {
            viewMap = (view == null ? null : getControllerConfig().getViewMapMap().get(view));
        } catch (WebAppConfigurationException e) {
            Debug.logError(e, "Exception thrown while parsing controller.xml file: ", module);
            throw new RequestHandlerException(e);
        }
        if (viewMap == null) {
            throw new RequestHandlerException("No definition found for view with name [" + view + "]");
        }

        String nextPage;

        if (viewMap.page == null) {
            if (!allowExtView || viewAsJson) { // SCIPIO: NOTE: 2017-05-12: don't allow weird nextPage stuff for json for now - implications unclear
                throw new RequestHandlerException("No view to render.");
            } else {
                nextPage = "/" + oldView;
            }
        } else {
            nextPage = viewMap.page;
        }

        if (Debug.verboseOn()) Debug.logVerbose("[Mapped To]: " + nextPage + showSessionId(req), module);

        long viewStartTime = System.currentTimeMillis();

        // setup character encoding and content type
        String charset;
        if (viewAsJson) {
            // SCIPIO: NOTE: we hardcode UTF-8 because JSON requests will be like this
            charset = "UTF-8";
        } else {
            charset = UtilFormatOut.checkEmpty(this.charset, req.getCharacterEncoding(), "UTF-8");
        }

        if (!viewAsJson) {
            String viewCharset = viewMap.encoding;
            //NOTE: if the viewCharset is "none" then no charset will be used
            if (UtilValidate.isNotEmpty(viewCharset)) {
                charset = viewCharset;
            }
        }

        if (!"none".equals(charset)) {
            try {
                req.setCharacterEncoding(charset);
            } catch (UnsupportedEncodingException e) {
                throw new RequestHandlerException("Could not set character encoding to " + charset, e);
            } catch (IllegalStateException e) {
                Debug.logInfo(e, "Could not set character encoding to " + charset + ", something has probably already committed the stream", module);
            }
        }

        // setup content type
        String contentType = "text/html";
        String viewContentType = viewMap.contentType;
        if (UtilValidate.isNotEmpty(viewContentType)) {
            contentType = viewContentType;
        }

        if (!viewAsJson) {
            if (UtilValidate.isNotEmpty(charset) && !"none".equals(charset)) {
                resp.setContentType(contentType + "; charset=" + charset);
            } else {
                resp.setContentType(contentType);
            }
        }

        if (Debug.verboseOn()) Debug.logVerbose("The ContentType for the " + view + " view is: " + contentType, module);

        boolean viewNoCache = viewMap.noCache;
        if (viewNoCache) {
           UtilHttp.setResponseBrowserProxyNoCache(resp);
           if (Debug.verboseOn()) Debug.logVerbose("Sending no-cache headers for view [" + nextPage + "]", module);
        }

        // Security headers vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv
        // See https://cwiki.apache.org/confluence/display/OFBIZ/How+to+Secure+HTTP+Headers
        String xFrameOption = viewMap.xFrameOption;
        // default to sameorigin
        if (UtilValidate.isNotEmpty(xFrameOption)) {
            if(!"none".equals(xFrameOption)) {
                resp.addHeader("x-frame-options", xFrameOption);
            }
        } else {
            resp.addHeader("x-frame-options", "sameorigin");
        }

        String strictTransportSecurity = viewMap.strictTransportSecurity;
        // default to "max-age=31536000; includeSubDomains" 31536000 secs = 1 year
        if (UtilValidate.isNotEmpty(strictTransportSecurity)) {
            if (!"none".equals(strictTransportSecurity)) {
                resp.addHeader("strict-transport-security", strictTransportSecurity);
            }
        } else {
            if (EntityUtilProperties.getPropertyAsBoolean("requestHandler", "strict-transport-security", true)) { // FIXME later pass req.getAttribute("delegator") as last argument
                resp.addHeader("strict-transport-security", "max-age=31536000; includeSubDomains");
            }
        }

        //The only x-content-type-options defined value, "nosniff", prevents Internet Explorer from MIME-sniffing a response away from the declared content-type.
        // This also applies to Google Chrome, when downloading extensions.
        resp.addHeader("x-content-type-options", "nosniff");

        // This header enables the Cross-site scripting (XSS) filter built into most recent web browsers.
        // It's usually enabled by default anyway, so the role of this header is to re-enable the filter for this particular website if it was disabled by the user.
        // This header is supported in IE 8+, and in Chrome (not sure which versions). The anti-XSS filter was added in Chrome 4. Its unknown if that version honored this header.
        // FireFox has still an open bug entry and "offers" only the noscript plugin
        // https://wiki.mozilla.org/Security/Features/XSS_Filter
        // https://bugzilla.mozilla.org/show_bug.cgi?id=528661
        resp.addHeader("X-XSS-Protection","1; mode=block");

        resp.setHeader("Referrer-Policy", "no-referrer-when-downgrade"); // This is the default (in Firefox at least)

        //resp.setHeader("Content-Security-Policy", "default-src 'self'");
        //resp.setHeader("Content-Security-Policy-Report-Only", "default-src 'self'; report-uri webtools/control/ContentSecurityPolicyReporter");
        // SCIPIO: 2018-07-10: the following line is commented BY US (but not the two above);
        // this is inappropriate for production and is missing configuration.
        //resp.setHeader("Content-Security-Policy-Report-Only", "default-src 'self'");

        // TODO in custom project. Public-Key-Pins-Report-Only is interesting but can't be used OOTB because of demos (the letsencrypt certificate is renewed every 3 months)

        // Security headers ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

        try {
            if (Debug.verboseOn()) Debug.logVerbose("Rendering view [" + nextPage + "] of type [" + viewMap.type + "]", module);
            ViewHandler vh = viewFactory.getViewHandler(viewMap.type);
            if (viewAsJson) {
                invokeViewHandlerAsJson(vh, viewAsJsonConfig, view, nextPage, viewMap.info, contentType, charset, req, resp);
            } else {
                vh.render(view, nextPage, viewMap.info, contentType, charset, req, resp);
            }
        } catch (ViewHandlerException e) {
            Throwable throwable = e.getNested() != null ? e.getNested() : e;

            throw new RequestHandlerException(e.getNonNestedMessage(), throwable);
        }

        if (viewAsJson) {
            // SCIPIO: NOTE: we go to handler URI so potentially a webapp can tweak json output behavior.
            ViewAsJsonUtil.addDefaultRenderOutAttrNames(req);
            String jsonRequestUri;
            try {
                jsonRequestUri = ViewAsJsonUtil.getViewAsJsonRequestUri(req, viewAsJsonConfig);
            } catch (WebAppConfigurationException e) {
                Debug.logError(e, "Exception thrown while parsing controller.xml file: ", module);
                throw new RequestHandlerException(e);
            }
            doRequest(req, resp, jsonRequestUri, userLogin, (Delegator) req.getAttribute("delegator"));
        }

        // before getting the view generation time flush the response output to get more consistent results
        try {
            resp.flushBuffer();
        } catch (java.io.IOException e) {
            /* If any request gets aborted before completing, i.e if a user requests a page and cancels that request before the page is rendered and returned
               or if request is an ajax request and user calls abort() method for on ajax request then its showing broken pipe exception on console,
               skip throwing of RequestHandlerException. JIRA Ticket - OFBIZ-254
            */
            if (Debug.verboseOn()) Debug.logVerbose("Skip Request Handler Exception that is caused due to aborted requests. " + e.getMessage(), module);
        }

        String vname = (String) req.getAttribute("_CURRENT_VIEW_");

        if (this.trackStats(req) && vname != null) {
            ServerHitBin.countView(cname + "." + vname, req, viewStartTime,
                System.currentTimeMillis() - viewStartTime, userLogin);
        }
    }

    /**
     * SCIPIO: factored out viewAsJson view handler render wrapper code.
     */
    public static void invokeViewHandlerAsJson(ViewHandler vh, ConfigXMLReader.ViewAsJsonConfig viewAsJsonConfig, String view, String nextPage, String info, String contentType, String charset, HttpServletRequest req, HttpServletResponse resp) throws ViewHandlerException {
        if (vh instanceof ViewHandlerExt) {
            ViewHandlerExt vhe = (ViewHandlerExt) vh;
            // SPECIAL: we must save _ERROR_MESSAGE_ and the like because the screen handler destroys them!
            Map<String, Object> msgAttrMap = ViewAsJsonUtil.getMessageAttributes(req);
            Writer sw = ViewAsJsonUtil.prepareWriterAndMode(req, viewAsJsonConfig);
            try {
                vhe.render(view, nextPage, info, contentType, charset, req, resp, sw);
            } finally {
                ViewAsJsonUtil.setRenderOutParamFromWriter(req, sw);
                ViewAsJsonUtil.setMessageAttributes(req, msgAttrMap);
            }
        } else {
            throw new ViewHandlerException("View handler does not support extended interface (ViewHandlerExt)");
        }
    }

    /**
     * SCIPIO: Determines if view saving may happen for this view for this request response.
     * <p>
     * Added 2018-06-13.
     */
    static boolean isAllowViewSave(String viewName, HttpServletRequest req,
            ConfigXMLReader.ControllerConfig controllerConfig, ConfigXMLReader.RequestMap requestMap,
            ConfigXMLReader.RequestResponse requestResponse, boolean viewAsJson, ConfigXMLReader.ViewAsJsonConfig viewAsJsonConfig) {

        // Static configuration lookup first
        Boolean allowViewSave = requestResponse.getAllowViewSave();
        if (allowViewSave == null) {
            try {
                for(ConfigXMLReader.NameFilter<Boolean> viewNameFilter : controllerConfig.getAllowViewSaveViewNameFilters()) {
                    if (viewNameFilter.matches(viewName)) {
                        allowViewSave = viewNameFilter.getUseValue();
                    }
                }
                if (allowViewSave == null) {
                    allowViewSave = controllerConfig.getAllowViewSaveDefault();
                }
            } catch (WebAppConfigurationException e) {
                Debug.logError(e, module);
            }
        }
        if (Boolean.FALSE.equals(allowViewSave)) {
            return false;
        }

        // Dynamic request circumstances lookups
        return !"N".equals(req.getParameter("_ALLOW_VIEW_SAVE_"))
                && (!viewAsJson || ViewAsJsonUtil.isViewAsJsonUpdateSession(req, viewAsJsonConfig));
    }

    /**
     * Returns a URL String that contains only the scheme and host parts. This method
     * should not be used because it ignores settings in the WebSite entity.
     *
     * @param request
     * @param secure
     * @deprecated Use OfbizUrlBuilder
     */
    @Deprecated
    public static String getDefaultServerRootUrl(HttpServletRequest request, boolean secure) {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        String httpsPort = EntityUtilProperties.getPropertyValue("url", "port.https", "443", delegator);
        String httpsServer = EntityUtilProperties.getPropertyValue("url", "force.https.host", delegator);
        String httpPort = EntityUtilProperties.getPropertyValue("url", "port.http", "80", delegator);
        String httpServer = EntityUtilProperties.getPropertyValue("url", "force.http.host", delegator);
        boolean useHttps = EntityUtilProperties.propertyValueEqualsIgnoreCase("url", "port.https.enabled", "Y", delegator);

        if (Start.getInstance().getConfig().portOffset != 0) {
            // SCIPIO: ensure has value
            if (!httpPort.isEmpty()) {
                Integer httpPortValue = Integer.valueOf(httpPort);
                httpPortValue += Start.getInstance().getConfig().portOffset;
                httpPort = httpPortValue.toString();
            }
            if (!httpsPort.isEmpty()) {
                Integer httpsPortValue = Integer.valueOf(httpsPort);
                httpsPortValue += Start.getInstance().getConfig().portOffset;
                httpsPort = httpsPortValue.toString();
            }
        }

        StringBuilder newURL = new StringBuilder();

        if (secure && useHttps) {
            String server = httpsServer;
            if (UtilValidate.isEmpty(server)) {
                server = request.getServerName();
            }

            newURL.append("https://");
            newURL.append(server);
            if (!httpsPort.equals("443")) {
                newURL.append(":").append(httpsPort);
            }

        } else {
            String server = httpServer;
            if (UtilValidate.isEmpty(server)) {
                server = request.getServerName();
            }

            newURL.append("http://");
            newURL.append(server);
            if (!httpPort.equals("80")) {
                newURL.append(":").append(httpPort);
            }
        }
        return newURL.toString();
    }


    /**
     * Creates a query string based on the redirect parameters for a request response, if specified, or for all request parameters if no redirect parameters are specified.
     * <p>
     * SCIPIO: 2017-04-24: ENHANCED (see site-conf.xsd).
     *
     * @param request the Http request
     * @param requestResponse the RequestResponse Object
     * @return return the query string
     */
    public String makeQueryString(HttpServletRequest request, ConfigXMLReader.RequestResponse requestResponse, Map<String, Object> extraParameters) {
        if (requestResponse == null ||
                ("auto".equals(requestResponse.includeMode) && requestResponse.redirectParameterMap.size() == 0 && requestResponse.redirectParameterValueMap.size() == 0) ||
                !"url-params".equals(requestResponse.includeMode) || "all-params".equals(requestResponse.includeMode)) {
            Map<String, Object> urlParams;
            if (requestResponse != null && "all-params".equals(requestResponse.includeMode)) {
                urlParams = UtilHttp.getParameterMap(request, requestResponse.excludeParameterSet, false);
            } else {
                urlParams = UtilHttp.getUrlOnlyParameterMap(request);

                if (requestResponse != null) {
                    // SCIPIO: remove excluded
                    if (requestResponse.excludeParameterSet != null) {
                        for(String name : requestResponse.excludeParameterSet) {
                            urlParams.remove(name);
                        }
                    }
                }
            }

            // SCIPIO: we now support adding extra params
            if (requestResponse != null) {
                for (Map.Entry<String, String> entry: requestResponse.redirectParameterMap.entrySet()) {
                    String name = entry.getKey();
                    String from = entry.getValue();
    
                    Object value = request.getAttribute(from);
                    if (value == null) {
                        value = request.getParameter(from);
                    }
    
                    urlParams.put(name, value);
                }

                for (Map.Entry<String, String> entry: requestResponse.redirectParameterValueMap.entrySet()) {
                    String name = entry.getKey();
                    String value = entry.getValue();
    
                    urlParams.put(name, value);
                }
            }

            if (extraParameters != null) { // SCIPIO
                urlParams.putAll(extraParameters);
            }

            String queryString = UtilHttp.urlEncodeArgs(urlParams, false);
            if(UtilValidate.isEmpty(queryString)) {
                return queryString;
            }
            return "?" + queryString;
        } else {
            StringBuilder queryString = new StringBuilder();
            queryString.append("?");
            for (Map.Entry<String, String> entry: requestResponse.redirectParameterMap.entrySet()) {
                String name = entry.getKey();
                String from = entry.getValue();

                Object value = request.getAttribute(from);
                if (value == null) {
                    value = request.getParameter(from);
                }

                addNameValuePairToQueryString(queryString, name, (String) value);
            }

            for (Map.Entry<String, String> entry: requestResponse.redirectParameterValueMap.entrySet()) {
                String name = entry.getKey();
                String value = entry.getValue();

                addNameValuePairToQueryString(queryString, name, value);
            }


            if (extraParameters != null) { // SCIPIO
                for (Map.Entry<String, Object> entry: extraParameters.entrySet()) {
                    String name = entry.getKey();
                    String value = (entry.getValue() != null) ? entry.getValue().toString() : "";

                    addNameValuePairToQueryString(queryString, name, value);
                }
            }

            return queryString.toString();
        }
    }

    /**
     * Creates a query string based on the redirect parameters for a request response, if specified, or for all request parameters if no redirect parameters are specified.
     * <p>
     * SCIPIO: 2017-04-24: ENHANCED (see site-conf.xsd).
     *
     * @param request the Http request
     * @param requestResponse the RequestResponse Object
     * @return return the query string
     */
    public String makeQueryString(HttpServletRequest request, ConfigXMLReader.RequestResponse requestResponse) {
        return makeQueryString(request, requestResponse, null);
    }

    private void addNameValuePairToQueryString(StringBuilder queryString, String name, String value) {
        if (UtilValidate.isNotEmpty(value)) {
            if (queryString.length() > 1) {
                queryString.append("&");
            }
            String encodedName = UtilCodec.getEncoder("url").encode(name);
            if (encodedName != null) {
                queryString.append(encodedName);
                queryString.append("=");
                queryString.append(UtilCodec.getEncoder("url").encode(value));
            }
        }
    }

    /**
     * Builds links with added query string, with optional extra parameters.
     * <p>
     * SCIPIO: Modified overload to allow boolean flags.
     * SCIPIO: Modified to include query string in makeLink call.
     */
    public String makeLinkWithQueryString(HttpServletRequest request, HttpServletResponse response, String url, Boolean fullPath, Boolean secure, Boolean encode,
            ConfigXMLReader.RequestResponse requestResponse, Map<String, Object> extraParameters) {
        // SCIPIO: 2017-11-21: include the query string inside the makeLink call
        //String initialLink = this.makeLink(request, response, url, fullPath, secure, encode);
        //String queryString = this.makeQueryString(request, requestResponse);
        //return initialLink + queryString;
        return this.makeLink(request, response, url + this.makeQueryString(request, requestResponse), fullPath, secure, encode);
    }

    /**
     * Builds links with added query string.
     * <p>
     * SCIPIO: Modified overload to allow boolean flags.
     * SCIPIO: Modified to include query string in makeLink call.
     */
    public String makeLinkWithQueryString(HttpServletRequest request, HttpServletResponse response, String url, Boolean fullPath, Boolean secure, Boolean encode,
            ConfigXMLReader.RequestResponse requestResponse) {
        return makeLinkWithQueryString(request, response, url, fullPath, secure, encode, requestResponse, null);
    }

    /**
     * Builds links with added query string with added query string.
     * <p>
     * SCIPIO: Original signature method, now delegates.
     */
    public String makeLinkWithQueryString(HttpServletRequest request, HttpServletResponse response, String url, ConfigXMLReader.RequestResponse requestResponse, Map<String, Object> extraParameters) {
        return makeLinkWithQueryString(request, response, url, null, null, null, requestResponse, extraParameters);
    }
    
    /**
     * Builds links with added query string.
     * <p>
     * SCIPIO: Original signature method, now delegates.
     */
    public String makeLinkWithQueryString(HttpServletRequest request, HttpServletResponse response, String url, ConfigXMLReader.RequestResponse requestResponse) {
        return makeLinkWithQueryString(request, response, url, null, null, null, requestResponse, null);
    }

    /**
     * SCIPIO: Builds a full-path link (HTTPS as necessary) with added query string, with optional extra parameters.
     */
    public String makeLinkFullWithQueryString(HttpServletRequest request, HttpServletResponse response, String url, ConfigXMLReader.RequestResponse requestResponse, Map<String, Object> extraParameters) {
        return makeLinkWithQueryString(request, response, url, true, null, null, requestResponse, extraParameters);
    }

    /**
     * SCIPIO: Builds a full-path link (HTTPS as necessary) with added query string.
     */
    public String makeLinkFullWithQueryString(HttpServletRequest request, HttpServletResponse response, String url, ConfigXMLReader.RequestResponse requestResponse) {
        return makeLinkWithQueryString(request, response, url, true, null, null, requestResponse, null);
    }

    public String makeLink(HttpServletRequest request, HttpServletResponse response, String url) {
        return makeLink(request, response, url, null, null, null);
    }

    /**
     * SCIPIO: Builds a full-path link (HTTPS as necessary).
     */
    public String makeLinkFull(HttpServletRequest request, HttpServletResponse response, String url) {
        return makeLink(request, response, url, true, null, null);
    }

    /**
     * Builds an Ofbiz navigation link.
     * <p>
     * SCIPIO: This function is heavily modified to support non-controller intra-webapp links
     * as well as inter-webapp links. It should be able to generate all possible types of webapp
     * navigation links. However, it will only build links for webapps recognized by the server,
     * because in most cases we require information from the webapp.
     * <p>
     * <strong>fullPath behavior change</strong>: In Scipio, when fullPath is specified for a controller request, if the
     * request is defined as secure, a secure URL will be created. This method will now never allow an
     * insecure URL to built for a controller request marked secure. In stock Ofbiz, this behavior was
     * different: fullPath could generate insecure URLs to secure requests. In addition, fullPath will
     * by default no longer downgrade HTTPS connections. To allow downgrades, you must explicitly specify
     * request it by passing secure false, and this may still produce a secure link if the target
     * is marked secure. Currently, this applies to all links including inter-webapp links.
     * <p>
     * <strong>secure behavior change</strong>: In Scipio, if current browsing is secure, we NEVER downgrade to HTTPS unless
     * explicitly requested by passing secure false, and secure false may still produce a secure link if
     * needed. Currently (2016-04-06), for security reasons, this
     * downgrading request request only applies to the case where the target link is marked as non-secure (or is missing/unknown, as of 2016-07-14), such
     * that in general, setting secure false does not mean the link will be insecure in all cases.
     * In addition, in Scipio, secure flag no longer forces a fullPath link. Specify fullPath true in addition to
     * secure to force a fullPath link. Links may still generate full-path secure links when needed even
     * if not requested, however.
     * <p>
     * <strong>encode behavior</strong>: The <code>encode</code> flag controls whether the link should
     * be passed through <code>HttpServletResponse.encodeURL</code> method. For our purposes, this is <strong>NOT</strong>
     * equivalent to appending <code>jsessionid</code>; it has other functionality such as calling servlet filter hooks.
     * Almost all navigation links to Ofbiz webapps whether inter-webapp or intra-webapp should have encode <code>true</code>.
     * If jsessionid must be prevented for a link, currently this can be done by calling
     * {@link RequestLinkUtil#removeJsessionId}.
     * TODO: Could use an extra Boolean arg to force jsessionid on/off (null for default behavior).
     * <p>
     * <strong>URL format</strong>: The passed <code>url</code> should either be a controller URI (if <code>controller</code> true)
     * or a path relative to webapp context root. It should NEVER include the webapp context root (mount-point).
     * <p>
     * If both <code>interWebapp</code> and <code>controller</code> are false, it means we're building an intra-webapp URL
     * for an arbitrary servlet.
     * <p>
     * The caller sets <code>interWebapp</code> to the value he wants the link to be interpreted as; this method
     * will not try to detect if the link falls within current request webapp or not; it may be valid
     * to want to generate an intra-webapp link using inter-webapp building logic.
     * <p>
     * <em>DEV NOTE</em>: The ability to specify arbitrary absolute path as url has been explicitly prevented and removed
     * from this method, to simplify. The only case we can't generate
     * links is if for some reason a webapp is not recognized by the current server (no <code>WebappInfo</code>
     * available).
     * <p>
     * <em>DEV NOTE</em>: <code>interWebapp</code> must remain a separate boolean because it may be possible
     * to pass <code>webappInfo</code> even when intra-webapp or for other optimizations.
     * <p>
     * TODO: fullPath, secure, encode should be Boolean not boolean to allow null and finer grained control, current interface too limited for some cases.
     *
     * @param request the request (required)
     * @param response the response (required)
     * @param url the path or URI (required), relative (relative to controller servlet if controller true, or relative to webapp context root if controller false)
     * @param interWebapp if true, treat the link as inter-webapp (default: null/false) (Scipio: new parameter)
     * @param targetWebappInfo the webapp info of the link's target webapp (optional, conditionally required) (Scipio: new parameter)
     * @param controller if true, assume is a controller link and refer to controller for building link (default: null/true) (Scipio: new parameter)
     * @param fullPath if true, always produce full URL (HTTP or HTTPS) (default: null/false) (Scipio: changed to Boolean instead of boolean, and changed behavior)
     * @param secure if true, resulting links is guaranteed to be secure (default: null/false) (Scipio: changed to Boolean instead of boolean, and changed behavior)
     * @param encode if true, pass through response.encodeURL (default: null/true) (Scipio: changed to Boolean instead of boolean)
     * @return the resulting URL
     */
    public static String makeLink(HttpServletRequest request, HttpServletResponse response, String url, Boolean interWebapp, FullWebappInfo targetWebappInfo, Boolean controller,
            Boolean fullPath, Boolean secure, Boolean encode) {
        // SCIPIO: We now accept nulls for all booleans to prevent rehardcoding defaults and allow more options
        if (interWebapp == null) {
            interWebapp = Boolean.FALSE;
        }
        if (controller == null) {
            controller = Boolean.TRUE;
        }
        // SCIPIO: Code must be aware of whether these were explicitly requested or not
        // SCIPIO: NOTE: change to Boolean not fully exploited yet
        //if (fullPath == null) {
        //    fullPath = Boolean.FALSE;
        //}
        //if (secure == null) {
        //    // SCIPIO: NOTE: this does not mean the link is "insecure"!
        //    secure = Boolean.FALSE;
        //}
        if (encode == null) {
            encode = Boolean.TRUE;
        }

        Delegator delegator = (Delegator) request.getAttribute("delegator"); // SCIPIO: need delegator
        OfbizUrlBuilder builder = null; // SCIPIO: reuse this outside

        // SCIPIO: enforce this check for time being
        if (interWebapp && targetWebappInfo == null) {
            throw new IllegalArgumentException("makeLink: Cannot build inter-webapp URL without target webapp info");
        }

        // SCIPIO: Sanity check: null/missing URL
        // 2018-12-07: NOTE: Now accepting url.isEmpty(), because may be inter-webapp root webapp request
        if (url == null) { // || url.isEmpty()
            Debug.logError("makeLink: Received null URL; returning null" + getMakeLinkLogSuffix(), module);
            return null;
        }

        WebSiteProperties webSiteProps;
        WebSiteProperties requestWebSiteProps;

        // SCIPIO: always get current request webSiteProps
        FullWebappInfo currentWebappInfo;
        try {
            currentWebappInfo = FullWebappInfo.fromRequest(request);
            requestWebSiteProps = currentWebappInfo.getWebSiteProperties();
        } catch (Exception e) { // SCIPIO: just catch everything: GenericEntityException
            // If the entity engine is throwing exceptions, then there is no point in continuing.
            Debug.logError("makeLink: Error getting current webapp info from request: " + e.toString() + getMakeLinkLogSuffix(), module);
            return null;
        }

        // SCIPIO: Multiple possible ways to get webSiteProps
        if (interWebapp) {
            try {
                webSiteProps = targetWebappInfo.getWebSiteProperties();
            } catch (Exception e) {
                Debug.logError("makeLink: Error getting web site properties for webapp "
                        + targetWebappInfo + ": " + e.toString() + getMakeLinkLogSuffix(), module);
                return null;
            }
        } else {
            // SCIPIO: stock case (get from request, or defaults)
            webSiteProps = requestWebSiteProps;
        }

        // SCIPIO: Special case: If we have inter-webapp, we need to check if the web site properties
        // for this link different from the current request's. If so, we have to force full-path
        // link. Here we compare the effective values to ensure correctness.
        // TODO? It is possible we could want to always force fullPath for all inter-webapp links.
        // Maybe make a url.properties option and allow force fullPath and force secure.
        if (interWebapp) {
            if (!webSiteProps.equalsServerFieldsWithHardDefaults(requestWebSiteProps)) {
                fullPath = true;
            }
        }

        String requestUri = null;
        ConfigXMLReader.RequestMap requestMap = null;

        // SCIPIO: only lookup if we want to use controller
        if (controller) {
            if (url.isEmpty()) { // SCIPIO
                Debug.log(getMakeLinkErrorLogLevel(request, delegator), null,
                        "makeLink: Cannot build link: empty uri, cannot locate controller request for webapp " 
                        + (interWebapp ? targetWebappInfo : currentWebappInfo) + getMakeLinkLogSuffix(), module);
                return null;
            }
            requestUri = RequestHandler.getRequestUri(url);

            if (requestUri != null) {
                if (interWebapp) {
                    try {
                        requestMap = targetWebappInfo.getControllerConfig().getRequestMapMap().get(requestUri);
                    } catch (Exception e) {
                        Debug.logError("makeLink: Error parsing controller.xml file for webapp "
                                + targetWebappInfo + ": " + e.toString() + getMakeLinkLogSuffix(), module);
                        return null;
                    }
                } else {
                    try {
                        // SCIPIO: stock case
                        requestMap = currentWebappInfo.getControllerConfig().getRequestMapMap().get(requestUri);
                    } catch (Exception e) {
                        Debug.logError("makeLink: Error parsing controller.xml file for webapp "
                                + currentWebappInfo + ": " + e.toString() + getMakeLinkLogSuffix(), module);
                        return null;
                    }
                }
            }

            // SCIPIO: 2016-05-06: If controller requested and request could not be found, show an error and return null.
            // There is virtually no case where this is not a coding error we want to catch, and if we don't show an error,
            // then we can't use this as a security check. Likely also to make some template errors clearer.
            if (requestMap == null) {
                Debug.log(getMakeLinkErrorLogLevel(request, delegator), null, "makeLink: Cannot build link: could not locate the expected request '"
                        + requestUri + "' in controller config for webapp " + (interWebapp ? targetWebappInfo : currentWebappInfo) + getMakeLinkLogSuffix(), module);
                return null;
            }
        }

        boolean didFullSecure = false;
        boolean didFullStandard = false;
        // SCIPIO: We need to enter even if no controller (and other cases)
        //if (requestMap != null && (webSiteProps.getEnableHttps() || fullPath || secure)) {
        // We don't need this condition anymore, because it doesn't make sense to require enableHttps to produce full path URLs
        //if (webSiteProps.getEnableHttps() || Boolean.TRUE.equals(fullPath) || Boolean.TRUE.equals(secure) || secure == null) {
        {
            if (Debug.verboseOn()) {
                Debug.logVerbose("In makeLink requestUri=" + requestUri + getMakeLinkLogSuffix(), module);
            }
            // SCIPIO: These conditions have been change (see method)
            //if (secure || (webSiteProps.getEnableHttps() && requestMap.securityHttps && !request.isSecure())) {
            //    didFullSecure = true;
            //} else if (fullPath || (webSiteProps.getEnableHttps() && !requestMap.securityHttps && request.isSecure())) {
            //    didFullStandard = true;
            //}
            // SCIPIO: 2018-08-01: now passing webSiteProps here; previous was passing requestWebSiteProps, probably was not right
            Boolean secureFullPathFlag = checkFullSecureOrStandard(request, webSiteProps, requestMap, interWebapp, fullPath, secure);
            if (secureFullPathFlag == Boolean.TRUE) {
                didFullSecure = true;
            } else if (secureFullPathFlag == Boolean.FALSE) {
                didFullStandard = true;
            } else {
                ;
            }
        }
        StringBuilder newURL = new StringBuilder(250);
        if (didFullSecure || didFullStandard) {
            // Build the scheme and host part
            try {
                if (builder == null) {
                    if (interWebapp) {
                        // SCIPIO: builder should be made using webappInfo if one was passed to us
                        builder = targetWebappInfo.getOfbizUrlBuilder();
                    } else {
                        // SCIPIO: stock case
                        builder = currentWebappInfo.getOfbizUrlBuilder();
                    }
                }
                builder.buildHostPart(newURL, url, didFullSecure, controller); // SCIPIO: controller flag
            } catch (Exception e) {
                // If we can't read the controller.xml file, then there is no point in continuing.
                Debug.logError("makeLink: Error building url for webapp "
                        + (interWebapp ? targetWebappInfo : currentWebappInfo) + ": " + e.toString() + getMakeLinkLogSuffix(), module);
                return null;
            }
        }

        // SCIPIO: build the path part (context root, servlet/controller path)
        if (interWebapp) {
            try {
                if (builder == null) {
                    builder = targetWebappInfo.getOfbizUrlBuilder();
                }
                if (controller) {
                    builder.buildPathPart(newURL, url, false); // SCIPIO: appendDirSep=false (avoid unless necessary)
                } else {
                    builder.buildPathPartWithContextPath(newURL, url, urlForceContextRootDirSep);
                }
            } catch (Exception e) {
                // SCIPIO: new case
                Debug.logError("makeLink: Error building url path part for webapp "
                        + targetWebappInfo + ": " + e.toString() + getMakeLinkLogSuffix(), module);
                return null;
            }
            // SCIPIO: It's technically possible to be missing a slash here, if a root webapp is configured
            if (!(didFullSecure || didFullStandard) && (newURL.length() == 0 || RequestLinkUtil.isUrlAppendNeedsDirSep(newURL))) {
                newURL.insert(0, '/');
            }
        } else {
            // SCIPIO: 2018-07-27: new path prefix (included in builder.buildPathPart above)
            try {
                if (builder == null) {
                    builder = currentWebappInfo.getOfbizUrlBuilder();
                }
                builder.buildPathPartWithWebappPathPrefix(newURL);
            } catch (Exception e) {
                // SCIPIO: new case
                Debug.logError("makeLink: Error building url path part for webapp "
                        + currentWebappInfo + ": " + e.toString() + getMakeLinkLogSuffix(), module);
                return null;
            }

            if (controller) {
                // SCIPIO: This is the original stock case: intra-webapp, controller link
                // create the path to the control servlet
                //String controlPath = (String) request.getAttribute("_CONTROL_PATH_");
                newURL.append(getControlPath(request));

                if (Boolean.TRUE.equals(RequestLinkUtil.isUrlAppendNeedsDirSep(url, newURL))) { // SCIPIO: improved check: !url.startsWith("/")
                    newURL.append("/");
                }
            } else {
                // SCIPIO: Here we point to any servlet or file in the webapp, so only append context path
                String contextPath = request.getContextPath();
                // SCIPIO: This test is useless; HttpServletRequest.getContextPath() never returns a trailing slash, per servlet API
                //newURL.append(contextPath.endsWith("/") ? contextPath.substring(0, contextPath.length() - 1) : contextPath);
                newURL.append(contextPath);

                if (urlForceContextRootDirSep) {
                    if (!StringUtil.endsWith(newURL, '/') && !StringUtil.startsWith(url, '/')) {
                        newURL.append("/");
                    }
                } else {
                    if (Boolean.TRUE.equals(RequestLinkUtil.isUrlAppendNeedsDirSep(url, newURL))) { // SCIPIO: improved check: !url.startsWith("/")
                        newURL.append("/");
                    }
                }
            }

            // now add the actual passed url, but if it doesn't start with a / add one first
            // SCIPIO: Moved above due to special cases
            //if (Boolean.TRUE.equals(RequestLinkUtil.isFullUrlAppendNeedsDirSep(url, newURL))) { // SCIPIO: improved check: !url.startsWith("/")
            //    newURL.append("/");
            //}
            newURL.append(url);
        }

        String encodedUrl;
        if (encode) {
            // SCIPIO: Delegated code
            encodedUrl = doLinkURLEncode(request, response, newURL, interWebapp, targetWebappInfo, currentWebappInfo, didFullStandard, didFullSecure);
        } else {
            encodedUrl = newURL.toString();
        }

        return encodedUrl;
    }

    private static String getMakeLinkLogSuffix() { // SCIPIO: better info when logging link errors
        return " (" + Debug.getCallerShortInfo(logCallerExcludeClasses) + ")";
    }

    public static String makeLink(HttpServletRequest request, HttpServletResponse response, String url, Boolean interWebapp, WebappInfo webappInfo, Boolean controller,
            Boolean fullPath, Boolean secure, Boolean encode) {
        FullWebappInfo targetWebappInfo = null;
        if (webappInfo != null) {
            try {
                targetWebappInfo = FullWebappInfo.fromWebapp(ExtWebappInfo.fromEffectiveComponentWebapp(webappInfo), request);
            } catch (Exception e) {
                Debug.logError("makeLink: Could not get current webapp info for context path: " + (webappInfo != null ? webappInfo.getContextRoot() : "(missing input)"), module);
                return null;
            }
        }
        return makeLink(request, response, url, interWebapp, targetWebappInfo, controller, fullPath, secure, encode);
    }

    /**
     * SCIPIO: makeLink overload that works without request; requires explicit webapp.
     * Added 2018-08-01.
     */
    public static String makeLink(Delegator delegator, Locale locale, String url, FullWebappInfo targetWebappInfo, Boolean controller,
            Boolean fullPath, Boolean secure, Boolean encode, FullWebappInfo currentWebappInfo, Map<String, Object> context) {
        if (controller == null) {
            controller = Boolean.TRUE;
        }
        if (encode == null) {
            encode = Boolean.TRUE;
        }

        OfbizUrlBuilder builder = null; // SCIPIO: reuse this outside
        WebSiteProperties webSiteProps; // SCIPIO: NOTE: we *possibly* could want to accept this var as method parameter (as optimization/special), but to be safe, don't for now

        // SCIPIO: enforce this check for time being
        if (targetWebappInfo == null) {
            targetWebappInfo = currentWebappInfo;
            if (targetWebappInfo == null) {
                Debug.logError("makeLink: Cannot build URL: No target webapp specified or current webapp info could "
                        + "be determined from context" + getMakeLinkLogSuffix(), module);
                return null;
            }
        }
        boolean interWebapp = !targetWebappInfo.equals(currentWebappInfo);

        // SCIPIO: Sanity check: null/missing URL
        if (url == null) { // Allow empty for root webapp requests: || url.isEmpty()
            Debug.logError("makeLink: Received null URL; returning null" + getMakeLinkLogSuffix(), module);
            return null;
        }

        // SCIPIO: Multiple possible ways to get webSiteProps
        try {
            webSiteProps = targetWebappInfo.getWebSiteProperties();
        } catch (Exception e) { // SCIPIO: just catch everything: GenericEntityException
            // If the entity engine is throwing exceptions, then there is no point in continuing.
            Debug.logError("makeLink: Error while getting web site properties for webapp "
                    + targetWebappInfo + ": " + e.toString() + getMakeLinkLogSuffix(), module);
            return null;
        }

        if (interWebapp) {
            if (!targetWebappInfo.equalsProtoHostPortWithHardDefaults(currentWebappInfo)) {
                fullPath = true;
            }
        }

        String requestUri = null;
        ConfigXMLReader.RequestMap requestMap = null;

        // SCIPIO: only lookup if we want to use controller
        if (controller) {
            if (url.isEmpty()) { // SCIPIO
                Debug.log(getMakeLinkErrorLogLevel((HttpServletRequest) context.get("request"), delegator), null,
                        "makeLink: Cannot build link: empty uri, cannot locate controller request for webapp " 
                        + targetWebappInfo + getMakeLinkLogSuffix(), module);
                return null;
            }
            requestUri = RequestHandler.getRequestUri(url);

            if (requestUri != null) {
                try {
                    requestMap = targetWebappInfo.getControllerConfig().getRequestMapMap().get(requestUri);
                } catch (Exception e) {
                    // If we can't read the controller.xml file, then there is no point in continuing.
                    Debug.logError("makeLink: Error while parsing controller.xml file for webapp "
                            + targetWebappInfo + ": " + e.toString() + getMakeLinkLogSuffix(), module);
                    return null;
                }
            }

            // SCIPIO: 2016-05-06: If controller requested and request could not be found, show an error and return null.
            // There is virtually no case where this is not a coding error we want to catch, and if we don't show an error,
            // then we can't use this as a security check. Likely also to make some template errors clearer.
            if (requestMap == null) {
                Debug.log(getMakeLinkErrorLogLevel((HttpServletRequest) context.get("request"), delegator), null,
                        "makeLink: Cannot build link: could not locate the expected request '"
                        + requestUri + "' in controller config for webapp " + targetWebappInfo + getMakeLinkLogSuffix(), module);
                return null;
            }
        }

        boolean didFullSecure = false;
        boolean didFullStandard = false;
        // SCIPIO: We need to enter even if no controller (and other cases)
        //if (requestMap != null && (webSiteProps.getEnableHttps() || fullPath || secure)) {
        // We don't need this condition anymore, because it doesn't make sense to require enableHttps to produce full path URLs
        //if (webSiteProps.getEnableHttps() || Boolean.TRUE.equals(fullPath) || Boolean.TRUE.equals(secure) || secure == null) {
        {
            if (Debug.verboseOn()) {
                Debug.logVerbose("In makeLink requestUri=" + requestUri + getMakeLinkLogSuffix(), module);
            }
            // SCIPIO: These conditions have been change (see method)
            //if (secure || (webSiteProps.getEnableHttps() && requestMap.securityHttps && !request.isSecure())) {
            //    didFullSecure = true;
            //} else if (fullPath || (webSiteProps.getEnableHttps() && !requestMap.securityHttps && request.isSecure())) {
            //    didFullStandard = true;
            //}
            Boolean secureFullPathFlag = checkFullSecureOrStandard((HttpServletRequest) context.get("request"), webSiteProps, requestMap, interWebapp, fullPath, secure);
            if (secureFullPathFlag == Boolean.TRUE) {
                didFullSecure = true;
            } else if (secureFullPathFlag == Boolean.FALSE) {
                didFullStandard = true;
            } else {
                ;
            }
        }
        StringBuilder newURL = new StringBuilder(250);

        try {
            if (builder == null) {
                builder = targetWebappInfo.getOfbizUrlBuilder();
            }
        } catch (Exception e) {
            // If the entity engine is throwing exceptions, then there is no point in continuing.
            Debug.logError("makeLink: Error while getting URL builder for webapp "
                    + targetWebappInfo + ": " + e.toString() + getMakeLinkLogSuffix(), module);
            return null;
        }

        if (didFullSecure || didFullStandard) {
            try {
                builder.buildHostPart(newURL, url, didFullSecure, controller);
            } catch (Exception e) {
                Debug.logError("makeLink: Error while building url host part for webapp "
                        + targetWebappInfo + ": " + e.toString() + getMakeLinkLogSuffix(), module);
                return null;
            }
        }

        // SCIPIO: build the path part (context root, servlet/controller path)
        try {
            if (controller) {
                builder.buildPathPart(newURL, url);
            } else {
                builder.buildPathPartWithContextPath(newURL, url);
            }
        } catch (Exception e) {
            Debug.logError("makeLink: Error while building url path part for webapp "
                    + targetWebappInfo + ": " + e.toString() + getMakeLinkLogSuffix(), module);
            return null;
        }

        String encodedUrl;
        if (encode) {
            encodedUrl = doLinkURLEncode(delegator, locale, newURL, targetWebappInfo, currentWebappInfo,
                        didFullStandard, didFullSecure, context);
        } else {
            encodedUrl = newURL.toString();
        }
        return encodedUrl;
    }

    public static String makeLink(Map<String, Object> context, Delegator delegator, Locale locale, String url, FullWebappInfo targetWebappInfo, Boolean controller,
            Boolean fullPath, Boolean secure, Boolean encode) {
        FullWebappInfo currentWebappInfo;
        try {
            currentWebappInfo = FullWebappInfo.fromContext(context);
        } catch (Exception e) {
            Debug.logError("makeLink: Could not get current webapp info from context: " + e.toString() + getMakeLinkLogSuffix(), module);
            return null;
        }
        return makeLink(delegator, locale, url, targetWebappInfo, controller, fullPath, secure, encode,
                currentWebappInfo, context);
    }

    private static int getMakeLinkErrorLogLevel(HttpServletRequest request, Delegator delegator) {
        Integer level = (request != null) ? (Integer) request.getAttribute("_SCP_LINK_ERROR_LEVEL_") : null;
        return (level != null) ? level : Debug.ERROR;
    }

    /**
     * SCIPIO: Determines whether the link should be fullpath secure/https, fullpath plain/http,
     * or absolute from server root (factored-out makeLink code).
     * <p>
     * 2018-08-02: Conditions rewritten to match new HTTPS-high priority policy.
     * <p>
     * NOTES AND CHANGES (2018-08-02):
     * <ul>
     * <li>Plain http fullpath: The only case where it is possible to generate a plain fullpath http link is
     *     if explicit <code>secure=false</code> are passed AND the request is not pointing
     *     to a controller request map that has <code>https="true"</code>.
     *     In all other cases, fullpath links always produce secure https links.
     *     NOTE: <code>secure=false</code> is now ignored if explicit <code>fullpath=false</code>
     *     is received, because this is highly likely to reflect a deprecated old-style use of the ofbizUrl
     *     interface rather than a request to produce an insecure link.
     * <li>We never automatically downgrade from https to http, only upgrade. Downgrade requires explicit <code>secure=false</code>.
     * <li>The WebSiteProperties.getEnableHttps flag is now ignored. All webapps are assumed to support HTTPS.
     * </ul>
     * <p>
     * @return null if no full required, true if secure fullpath required, false if standard fullpath required.
     */
    protected static Boolean checkFullSecureOrStandard(HttpServletRequest request, Boolean isCurrentSecure, WebSiteProperties webSiteProps, ConfigXMLReader.RequestMap requestMap,
            Boolean interWebapp, Boolean fullPath, Boolean secure) {
        // The only case we can produce plain http link is if secure=false requested and the target request map is not marked https=true.
        // NOTE: 2018-08-02: We now ignore secure=false if also have explicit fullPath=false (see method description)
        if ((Boolean.FALSE.equals(secure) && (requestMap == null || !requestMap.securityHttps)) &&
                (Boolean.TRUE.equals(fullPath) || (fullPath == null && Boolean.TRUE.equals(isCurrentSecure)))) {
            return Boolean.FALSE; // http://
        } else if (Boolean.TRUE.equals(fullPath) // all other fullPath=true requests produce https
                || (!Boolean.TRUE.equals(isCurrentSecure) && (Boolean.TRUE.equals(secure) || (requestMap != null && requestMap.securityHttps)))) { // automatic upgrade to https
            return Boolean.TRUE; // https://
        } else {
            return null; // absolute /path from server root (same protocol)
        }
    }

    protected static Boolean checkFullSecureOrStandard(HttpServletRequest request, WebSiteProperties webSiteProps, ConfigXMLReader.RequestMap requestMap,
            Boolean interWebapp, Boolean fullPath, Boolean secure) {
        return checkFullSecureOrStandard(request, (request != null) ? RequestLinkUtil.isEffectiveSecure(request) : null, webSiteProps, requestMap, interWebapp, fullPath, secure);
    }

    /**
     * SCIPIO: Factored-out makeLink code, that we must expose so other link-building code may reuse.
     * <p>
     * WARN: newURL is modified in-place, and then discarded, so only use the result.
     */
    protected static String doLinkURLEncode(HttpServletRequest request, HttpServletResponse response, StringBuilder newURL, boolean interWebapp,
            FullWebappInfo targetWebappInfo, FullWebappInfo currentWebappInfo, boolean didFullStandard, boolean didFullSecure) {
        String encodedUrl;
        if (response != null) {
            try {
                // SCIPIO: 2018-08-10: OUT_URL_WEBAPP: this is both an optimization and partly necessary,
                // because here encodeURL may end up triggering UrlFilterHelper.doInterWebappUrlRewrite,
                // currently relies on it...
                // WARN: TODO: REVIEW: because this is going through a chain of filters, there is a chance
                // that the filters could change URL completely to point to something else, making
                // OUT_URL_WEBAPP invalid! For now we assume this is not the case, otherwise the lookups
                // in UrlFilterHelper may become prohibitive...
                request.setAttribute(UrlFilterHelper.OUT_URL_WEBAPP, targetWebappInfo);
                encodedUrl = response.encodeURL(newURL.toString());
                if (interWebapp) {
                    // SCIPIO: SPECIAL: Since urlrewrite.xml is what normally delegates the inter-webapp rewriting
                    // (through response.encodeURL above and urlrewrite.xml invokes UrlFilterHelper.doInterWebappUrlRewrite),
                    // if the "current" webapp web.xml doesn't have UrlRewriterFilter, we will
                    // have to emulate by adding an extra rewrite, which will behave as if
                    // the very first filter had been UrlRewriterFilter, which is almost always
                    // where it's chained.
                    // TODO: try to find a more natural and built-in way to handle this...
                    // This could be done using a response wrapper in ContextFilter, but currently
                    // it would make no difference because the only place the target webapp info is set, is just above.
                    // If there is another significant location that response.encodeURL is called for common links,
                    // we will run into problems...
                    if (targetWebappInfo != null) {
                        if (currentWebappInfo == null) {
                            try {
                                currentWebappInfo = FullWebappInfo.fromRequest(request);
                            } catch(Exception e) {
                                Debug.logError("doLinkURLEncode: Error looking up webapp info from request (inter-webapp"
                                        + " URL-encoding not possible): " + e.toString() + getMakeLinkLogSuffix(), module);
                            }
                        }
                        if (currentWebappInfo != null && currentWebappInfo.useUrlManualInterWebappFilter()) {
                            try {
                                ScipioUrlRewriter rewriter = ScipioUrlRewriter.getForRequest(targetWebappInfo, request, response, true);
                                encodedUrl = rewriter.processOutboundUrl(encodedUrl, targetWebappInfo, request, response);
                            } catch (Exception e) {
                                Debug.logError("doLinkURLEncode: Error URL-encoding (rewriting) inter-webapp link for webapp " + targetWebappInfo
                                        + ": " + e.toString() + getMakeLinkLogSuffix(), module);
                            }
                        }
                    }
                }
            } finally {
                request.removeAttribute(UrlFilterHelper.OUT_URL_WEBAPP);
            }
        } else {
            encodedUrl = newURL.toString();
        }
        return encodedUrl;
    }

    protected static String doLinkURLEncode(Delegator delegator, Locale locale, StringBuilder newURL, FullWebappInfo targetWebappInfo,
            FullWebappInfo currentWebappInfo, boolean didFullStandard, boolean didFullSecure, Map<String, Object> context) {
        FullWebappInfo webappInfo = (targetWebappInfo != null) ? targetWebappInfo : currentWebappInfo;
        try {
            return ScipioUrlRewriter.getForContext(webappInfo, context, true)
                .processOutboundUrl(newURL.toString(), targetWebappInfo, context);
        } catch (IOException e) {
            Debug.logError("doLinkURLEncode: Error URL-encoding (rewriting) link for webapp " + webappInfo
                    + ": " + e.toString() + getMakeLinkLogSuffix(), module);
            return newURL.toString();
        }
    }

    public String makeLink(HttpServletRequest request, HttpServletResponse response, String url, Boolean fullPath, Boolean secure, Boolean encode) {
        return makeLink(request, response, url, null, (FullWebappInfo) null, null, fullPath, secure, encode);
    }

    /**
     * SCIPIO: Builds an Ofbiz navigation link, where possible inferring <em>some</em> of its properties by analyzing the passed URI (<code>url</code>)
     * and <code>webSiteId</code>.
     * <p>
     * The <code>url</code> may be a relative URI such as controller URI, a servlet path relative to context root,
     * or a full absolute path including context root. In all cases, the method will parse the target
     * and it must point to a valid webapp on the server, as matched by mount-point.
     * <p>
     * For inter-webapp, if <code>webSiteId</code> is specified, it will determine the target webapp.
     * Some webapps don't have their own webSiteId, in which case the webapp will be inferred from
     * the absolute target link. If not enough information is available to pinpoint a web app (<code>WebappInfo<code>),
     * the call will fail and return null.
     * <p>
     * Each of the options can be passed null to let the method figure out. If specified it
     * will be taken into consideration. <strong>Exceptions</strong>: Currently, <code>interWebapp</code>
     * should be specified, otherwise <code>false</code> is always assumed regardless of <code>url</code> format
     * (there are currently no known cases where we need to infer this and creates ambiguities).
     * <p>
     * <strong>WARN</strong>: Currently, <code>absPath</code> is assumed to be <code>false</code>
     * for all intra-webapp links (interWebapp false), false for inter-webapp links with webSiteId specified,
     * and true for inter-webapp links without webSiteId specified. Is it NOT reactive to format of passed url.
     * <p>
     * <strong>WARN</strong>: Due to technical limitations (notably Java servlet spec), this method may
     * be forced to make inexact assumptions, which is one reason why it is implemented as a distinct method.
     */
    public static String makeLinkAuto(HttpServletRequest request, HttpServletResponse response, String url, Boolean absPath, Boolean interWebapp, String webSiteId, Boolean controller,
            Boolean fullPath, Boolean secure, Boolean encode) {
        return makeLinkAutoCore(true, request, response, null, url, absPath, interWebapp, webSiteId, controller, fullPath, secure, encode, null, null);
    }

    /**
     * SCIPIO: makeLinkAuto version that takes a render context instead of request/response.
     * <p>
     * NOTE: For this overload, request/response are not considered determinant of the method behavior,
     * and are instead mainly here for logging purposes (e.g. trying to render a static template in static fashion within another unrelated webapp request).
     */
    public static String makeLinkAuto(Map<String, Object> context, Delegator delegator, Locale locale, String webSiteId, String url, Boolean absPath, Boolean interWebapp, Boolean controller,
            Boolean fullPath, Boolean secure, Boolean encode) {
        return makeLinkAutoCore(false, null, null, context, url, absPath, interWebapp, webSiteId, controller, fullPath, secure, encode, delegator, locale);
    }

    private static String makeLinkAutoCore(boolean requestBased, HttpServletRequest request, HttpServletResponse response, Map<String, Object> context, String url, Boolean absPath, Boolean interWebapp, String webSiteId, Boolean controller,
            Boolean fullPath, Boolean secure, Boolean encode, Delegator delegator, Locale locale) {

        boolean absControlPathChecked = false;
        boolean absContextPathChecked = false;

        // Check inter-webapp
        if (interWebapp == null) {
            // For now, can assume false unless requested otherwise.
            // SPECIAL CASE: for intra-webapp non-control links, we must determine this...
            // FIXME?: This case could try to detect if webSiteId is different from current, but not necessary (yet)...
            if (Boolean.FALSE.equals(absPath) && Boolean.FALSE.equals(controller) && webSiteId != null && !webSiteId.isEmpty()) {
                interWebapp = true;
            } else {
                interWebapp = false;
            }
        }

        // Check if absolute path
        if (absPath == null) {
            // FIXME?: Current default behavior is predictable but is non-reactive to the url format.
            // It is safer this way but templates must be aware...
            // I don't think safe to use the starting "/" as indicator...
            if (interWebapp) {
                if (webSiteId != null && !webSiteId.isEmpty()) {
                    absPath = false;
                } else {
                    absPath = true;
                }
            } else {
                // For non-inter-webapp links, just assume is relative.
                absPath = false;
            }
        }

        // Get target webapp info
        FullWebappInfo targetWebappInfo = null;
        FullWebappInfo currentWebappInfo = null;
        if (!requestBased) {
            try {
                currentWebappInfo = FullWebappInfo.fromContext(context);
            } catch (Exception e) {
                Debug.logError("makeLinkAuto: Could not get current webapp info from context: " + e.toString() + getMakeLinkLogSuffix(), module);
                return null;
            }
        }
        if (interWebapp) {
            if (webSiteId != null && !webSiteId.isEmpty()) {
                try {
                    if (requestBased) {
                        targetWebappInfo = FullWebappInfo.fromWebapp(ExtWebappInfo.fromWebSiteId(webSiteId), request);
                    } else {
                        targetWebappInfo = FullWebappInfo.fromWebapp(ExtWebappInfo.fromWebSiteId(webSiteId), context);
                    }
                } catch (Exception e) {
                    Debug.logError("makeLinkAuto: Could not get webapp for webSiteId '" + webSiteId + "': " + e.toString() + getMakeLinkLogSuffix(), module);
                    return null;
                }
            } else {
                // We should have an absolute path here. If not, we won't have enough info
                // to build the link.
                try {
                    if (requestBased) {
                        targetWebappInfo = FullWebappInfo.fromWebapp(ExtWebappInfo.fromPath(WebAppUtil.getServerId(request), url, true), request);
                    } else {
                        targetWebappInfo = FullWebappInfo.fromWebapp(ExtWebappInfo.fromPath(WebAppUtil.getServerId(context), url, true), context);
                    }
                } catch (Exception e) {
                    Debug.logError("makeLinkAuto: Could not get webapp from absolute path '" + url + "': " + e.toString() + getMakeLinkLogSuffix(), module);
                    return null;
                }
                absContextPathChecked = true; // since we built the webapp info from the URL, this is guaranteed fine
            }
        } else {
            if (requestBased) {
                try {
                    targetWebappInfo = FullWebappInfo.fromRequest(request);
                } catch (Exception e) {
                    Debug.logError("makeLinkAuto: Could not get current webapp info from request: " + e.toString() + getMakeLinkLogSuffix(), module);
                    return null;
                }
            } else {
                targetWebappInfo = currentWebappInfo;
            }
        }
        String contextPath = targetWebappInfo.getContextPath();
        if (!contextPath.endsWith("/")) {
            contextPath += "/";
        }

        // Check if controller should be used
        String controlPath = null;
        if (controller == null) {
            // in some cases we need to infer this...
            if (absPath) {
                controlPath = targetWebappInfo.getFullControlPath();
                controller = (controlPath != null && url.startsWith(controlPath));
                absControlPathChecked = true;
            } else {
                // If intra-webapp and this boolean wasn't set, by default we assume TRUE (stock)
                // This means if want non-controller intra-webapp, it must be requested with controller=false
                controller = Boolean.TRUE;
            }
        }
        if (controller && controlPath == null) {
            // In some cases need to get controlPath AND this provides a sanity check
            controlPath = targetWebappInfo.getFullControlPath();
            if (controlPath == null) {
                Debug.logError("makeLinkAuto: Trying to make a controller link"
                        + " for a webapp (" + targetWebappInfo + ") that has no valid controller" + getMakeLinkLogSuffix(), module);
                return null;
            }
        }

        // Sanity check only, for absolute paths (NOTE: slows things down... but do it for now);
        // make sure it starts with the same webapp we're targeting
        if (absPath) {
            if (controller) {
                if (!absControlPathChecked) {
                    if (!url.startsWith(controlPath)) {
                        Debug.logError("makeLinkAuto: Trying to make a controller link for webapp " + targetWebappInfo
                                + " using absolute path url, but prefix does not match (uri: " + url + ", control path: " 
                                + controlPath + ")" + getMakeLinkLogSuffix(), module);
                        return null;
                    }
                    absControlPathChecked = true;
                }
            } else {
                if (!absContextPathChecked) {
                    if (!url.startsWith(contextPath)) {
                        // We may still have a root request
                        String ctxPath = contextPath.substring(0, contextPath.length() - 1); // remove '/'
                        if (!(url.startsWith(ctxPath) && (url.length() == ctxPath.length() 
                                || RequestLinkUtil.isUrlDelimNonDir(url.charAt(ctxPath.length())) ))) {
                            Debug.logError("makeLinkAuto: trying to make a webapp link for webapp " + targetWebappInfo
                                    + " using absolute path url, but context root does not match (uri: " + url 
                                    + ", context path: " + contextPath + ")" + getMakeLinkLogSuffix(), module);
                            return null;
                        }
                    }
                    absContextPathChecked = true;
                }
            }
        }

        // Extract the relative part of the URL; this simplifies the makeLink function (and moves sanity checks here)
        String relUrl;
        if (absPath) {
            if (controller) {
                relUrl = url.substring(controlPath.length());
            } else {
                if (url.length() <= (contextPath.length() - 1)) { // Check if no-param root webapp request (NOTE: contextPath ends with slash)
                    relUrl = "";
                } else if ((url.charAt(contextPath.length() - 1) == '/')) { // Check for subdir vs root webapp request with params
                    if (url.length() == contextPath.length() || RequestLinkUtil.isUrlDelimNonDir(url.charAt(contextPath.length()))) {
                        // root request, preserve the slash
                        relUrl = url.substring(contextPath.length() - 1);
                    } else {
                        relUrl = url.substring(contextPath.length());
                    }
                } else {
                    relUrl = url.substring(contextPath.length() - 1); // if not '/', it's a parameter delimiter
                }
            }
        } else {
            relUrl = url;
        }

        if (requestBased) {
            return makeLink(request, response, relUrl, interWebapp, targetWebappInfo, controller, fullPath, secure, encode);
        } else {
            return makeLink(delegator, locale, relUrl, targetWebappInfo, controller, fullPath, secure, encode, currentWebappInfo, context);
        }
    }

    /**
     * SCIPIO: Builds an Ofbiz navigation link, where possible inferring <em>some</em> of its properties by analyzing the passed URI (<code>url</code>)
     * and <code>webSiteId</code>.
     *
     * @see #makeLinkAuto(HttpServletRequest, HttpServletResponse, String, Boolean, Boolean, String, Boolean, Boolean, Boolean, Boolean)
     */
    public static String makeLinkAuto(HttpServletRequest request, HttpServletResponse response, String url, Boolean absPath, Boolean interWebapp, String webSiteId, Boolean controller) {
        return makeLinkAuto(request, response, url, absPath, interWebapp, webSiteId, controller, null, null, null);
    }

    /**
     * SCIPIO: Builds an Ofbiz navigation full link (with HTTPS as necessary), where possible inferring <em>some</em> of its properties by analyzing the passed URI (<code>url</code>)
     * and <code>webSiteId</code>.
     *
     * @see #makeLinkAuto(HttpServletRequest, HttpServletResponse, String, Boolean, Boolean, String, Boolean, Boolean, Boolean, Boolean)
     */
    public static String makeLinkAutoFull(HttpServletRequest request, HttpServletResponse response, String url, Boolean absPath, Boolean interWebapp, String webSiteId, Boolean controller) {
        return makeLinkAuto(request, response, url, absPath, interWebapp, webSiteId, controller, true, null, null);
    }

    /**
     * Builds an Ofbiz URL.
     * <p>
     * SCIPIO: This is modified to pass encode <code>true</code> (<code>null</code>) instead of <code>false</code>.
     * This is <string>necessary</strong> to achieve filter hooks.
     */
    public static String makeUrl(HttpServletRequest request, HttpServletResponse response, String url) {
        // SCIPIO: Pass encode = true
        //return makeUrl(request, response, url, false, false, false);
        return makeUrl(request, response, url, null, null, null);
    }

    public static String makeUrl(HttpServletRequest request, HttpServletResponse response, String url, Boolean fullPath, Boolean secure, Boolean encode) {
        ServletContext ctx = request.getServletContext(); // SCIPIO: get context using servlet API 3.0
        RequestHandler rh = (RequestHandler) ctx.getAttribute("_REQUEST_HANDLER_");
        return rh.makeLink(request, response, url, fullPath, secure, encode);
    }

    /**
     * SCIPIO: Builds a full-path link (HTTPS as necessary).
     */
    public static String makeUrlFull(HttpServletRequest request, HttpServletResponse response, String url) {
        return makeUrl(request, response, url, true, null, null);
    }

    public void runAfterLoginEvents(HttpServletRequest request, HttpServletResponse response) {
        try {
            for (ConfigXMLReader.Event event: getControllerConfig().getAfterLoginEventList().values()) {
                try {
                    String returnString = this.runEvent(request, response, event, null, "after-login");
                    if (returnString != null && !"success".equalsIgnoreCase(returnString)) {
                        throw new EventHandlerException("Pre-Processor event did not return 'success'.");
                    }
                } catch (EventHandlerException e) {
                    Debug.logError(e, module);
                }
            }
        } catch (WebAppConfigurationException e) {
            Debug.logError(e, "Exception thrown while parsing controller.xml file: ", module);
        }
    }

    public void runBeforeLogoutEvents(HttpServletRequest request, HttpServletResponse response) {
        try {
            for (ConfigXMLReader.Event event: getControllerConfig().getBeforeLogoutEventList().values()) {
                try {
                    String returnString = this.runEvent(request, response, event, null, "before-logout");
                    if (returnString != null && !"success".equalsIgnoreCase(returnString)) {
                        throw new EventHandlerException("Pre-Processor event did not return 'success'.");
                    }
                } catch (EventHandlerException e) {
                    Debug.logError(e, module);
                }
            }
        } catch (WebAppConfigurationException e) {
            Debug.logError(e, "Exception thrown while parsing controller.xml file: ", module);
        }
    }

    public void runAfterLogoutEvents(HttpServletRequest request, HttpServletResponse response) { // SCIPIO
        try {
            for (ConfigXMLReader.Event event: getControllerConfig().getAfterLogoutEventList().values()) {
                try {
                    String returnString = this.runEvent(request, response, event, null, "after-logout");
                    if (returnString != null && !"success".equalsIgnoreCase(returnString)) {
                        throw new EventHandlerException("Pre-Processor event did not return 'success'.");
                    }
                } catch (EventHandlerException e) {
                    Debug.logError(e, module);
                }
            }
        } catch (WebAppConfigurationException e) {
            Debug.logError(e, "Exception thrown while parsing controller.xml file: ", module);
        }
    }

    public boolean trackStats(HttpServletRequest request) {
        if (trackServerHit) {
            String uriString = RequestHandler.getRequestUri(request.getPathInfo());
            if (uriString == null) {
                uriString="";
            }
            ConfigXMLReader.RequestMap requestMap = null;
            try {
                // SCIPIO: 2018-11-08: Handle controller load fail more cleanly
                //requestMap = getControllerConfig().getRequestMapMap().get(uriString);
                ControllerConfig controllerConfig = getControllerConfig();
                if (controllerConfig == null) {
                    return false;
                }
                requestMap = controllerConfig.getRequestMapMap().get(uriString);
            } catch (WebAppConfigurationException e) {
                Debug.logError(e, "Exception thrown while parsing controller.xml file: ", module);
            }
            if (requestMap == null) return false;
            return requestMap.trackServerHit;
        } else {
            return false;
        }
    }

    public boolean trackVisit(HttpServletRequest request) {
        if (trackVisit) {
            String uriString = RequestHandler.getRequestUri(request.getPathInfo());
            if (uriString == null) {
                uriString="";
            }
            ConfigXMLReader.RequestMap requestMap = null;
            try {
                // SCIPIO: 2018-11-08: Handle controller load fail more cleanly
                //requestMap = getControllerConfig().getRequestMapMap().get(uriString);
                ControllerConfig controllerConfig = getControllerConfig();
                if (controllerConfig == null) {
                    return false;
                }
                requestMap = controllerConfig.getRequestMapMap().get(uriString);
            } catch (WebAppConfigurationException e) {
                Debug.logError(e, "Exception thrown while parsing controller.xml file: ", module);
            }
            if (requestMap == null) return false;
            return requestMap.trackVisit;
        } else {
            return false;
        }
    }

    private static String showSessionId(HttpServletRequest request) { // SCIPIO: made static
        // SCIPIO: avoid expensive lookup just for log line, not worth it
        //Delegator delegator = (Delegator) request.getAttribute("delegator");
        //EntityUtilProperties.propertyValueEqualsIgnoreCase("requestHandler", "show-sessionId-in-log", "Y", delegator);
        if (showSessionIdInLog) {
            return " sessionId=" + UtilHttp.getSessionId(request);
        }
        // SCIPIO: needlessly verbose
        //return " hidden sessionId by default.";
        return " sessionId=[hidden]";
    }

    /**
     * SCIPIO: Returns the session ID itself, for log display, without space and prefix; 
     * if no session, returns "[none]"; if hidden, returns "[hidden]".
     */
    public static String getSessionIdForLog(HttpServletRequest request) {
        return getSessionIdForLog(request.getSession(false));
    }
    
    /**
     * SCIPIO: Returns the session ID itself, for log display, without space and prefix; 
     * if no session, returns "[none]"; if hidden, returns "[hidden]".
     * <p>
     * NOTE: If request is available, prefer using {@link #getSessionIdForLog(HttpServletRequest)} instead.
     */
    public static String getSessionIdForLog(HttpSession session) {
        return showSessionIdInLog ? (session != null ? session.getId() : "[none]") : "[hidden]";
    }

    /**
     * SCIPIO: Utility method that can be used for security checks to check if controller of current webapp
     * has the specified URI and allows direct/public access.
     */
    public static boolean controllerHasRequestUriDirect(HttpServletRequest request, String uri) {
        if (request == null) {
            return false;
        }
        RequestHandler rh = RequestHandler.getRequestHandler(request.getServletContext());
        return rh.controllerHasRequestUriDirect(uri);
    }

    /**
     * SCIPIO: Utility method that can be used for security checks to check if controller of current webapp
     * has the specified URI and allows direct/public access.
     */
    public boolean controllerHasRequestUriDirect(String uri) {
        try {
            ConfigXMLReader.RequestMap requestMap = getControllerConfig().getRequestMapMap().get(uri);

            if (requestMap != null && requestMap.securityDirectRequest) {
                return true;
            }

        } catch (Exception e) {
            ;
        }
        return false;
    }

    /**
     * SCIPIO: Necessary accessor method for external code.
     */
    public boolean isUseCookies() {
        return true; // 2018-07-09: now always true (assumed)
    }

    /**
     * SCIPIO: Returns the static charset (only).
     */
    public String getCharset() {
        return charset;
    }

    /**
     * SCIPIO: Returns the _CONTROL_PATH_ request attribute or otherwise tries to determine
     * it newly, for the current webapp.
     * <p>
     * NOTE: Most code should now use this getter instead of accessing _CONTROL_PATH_ directly.
     * <p>
     * Added 2018-12-12.
     */
    public static String getControlPath(HttpServletRequest request) {
        String controlPath = (String) request.getAttribute("_CONTROL_PATH_");
        return (controlPath != null) ? controlPath : request.getContextPath() + getControlServletPath(request);
    }
    
    /**
     * SCIPIO: Returns the servlet path for the controller or empty string if it's the catch-all path ("/").
     * (same rules as {@link HttpServletRequest#getServletPath()}).
     * NOTE: Unlike ofbiz's _CONTROL_PATH_ request attribute, this is accessible to early filters,
     * because it's determined during servlet initialization.
     * Added 2017-11-14.
     */
    public static String getControlServletPath(ServletRequest request) {
        return getControlServletPath(request.getServletContext());
    }

    /**
     * SCIPIO: Returns the servlet path for the controller or empty string if it's the catch-all path ("/").
     * (same rules as {@link HttpServletRequest#getServletPath()}).
     * NOTE: Unlike ofbiz's _CONTROL_PATH_ request attribute, this is accessible to early filters,
     * because it's determined during servlet initialization - HOWEVER, it may not accessible
     * from filter/servlet initialization!
     * NOTE: If request is available, always call the {@link #getControlServletPath(ServletRequest)} overload instead!
     * Added 2017-11-14.
     */
    public static String getControlServletPath(ServletContext servletContext) {
        return (String) servletContext.getAttribute("_CONTROL_SERVPATH_");
    }

    /**
     * SCIPIO: Returns the servlet mapping for the controller.
     * NOTE: Unlike ofbiz's _CONTROL_PATH_ request attribute, this is accessible to early filters,
     * because it's determined during servlet initialization.
     * Added 2017-11-14.
     */
    public static String getControlServletMapping(ServletRequest request) {
        return getControlServletMapping(request.getServletContext());
    }

    /**
     * SCIPIO: Returns the servlet mapping for the controller.
     * NOTE: Unlike ofbiz's _CONTROL_PATH_ request attribute, this is accessible to early filters,
     * because it's determined during servlet initialization.
     * Added 2017-11-14.
     */
    public static String getControlServletMapping(ServletContext servletContext) {
        return (String) servletContext.getAttribute("_CONTROL_MAPPING_");
    }

    /**
     * SCIPIO: Adds to the request attribute/param names which should be excluded from saving
     * into session by "view-last", "request-redirect" and similar responses; this collection is editable in-place and
     * caller may simply add names to it.
     * @deprecated 2019-01: Use {@link RequestAttrNamePolicy#from(HttpServletRequest)} instead.
     */
    @Deprecated
    public static void addNoSaveRequestAttr(HttpServletRequest request, String attrName) {
        RequestAttrNamePolicy.from(request).addExclude(RequestSavingAttrPolicy.NotSaveable.class, attrName);
    }

    /**
     * SCIPIO: Adds to the request attribute/param names which should be excluded from saving
     * into session by "view-last", "request-redirect" and similar responses; this collection is editable in-place and
     * caller may simply add names to it.
     * @deprecated 2019-01: Use {@link RequestAttrNamePolicy#from(HttpServletRequest)} instead.
     */
    @Deprecated
    public static void addNoSaveRequestAttr(HttpServletRequest request, Collection<String> attrName) {
        RequestAttrNamePolicy.from(request).addExcludes(RequestSavingAttrPolicy.NotSaveable.class, attrName);
    }

    /**
     * SCIPIO: Returns set of request attribute/param names which should be excluded from saving
     * into session by "view-last", "request-redirect" and similar responses.
     * @deprecated 2019-01: Use {@link RequestAttrNamePolicy#from(HttpServletRequest)} instead;
     * note that the 
     * <p>
     * 2018-12-03: Previously this list was called "_SCP_VIEW_SAVE_ATTR_EXCL_", now is more generic
     * and called "_SCP_NOSAVEREQATTR_".
     */
    @Deprecated
    public static Set<String> getNoSaveRequestAttr(HttpServletRequest request) {
        return RequestAttrNamePolicy.from(request); // NOTE: implements Set<String> interface for backward-compat
    }

    /**
     * SCIPIO: getDefaultNotSaveRequestAttr.
     * @deprecated 2019-01: Use {@link RequestSavingAttrNamePolicy#from(HttpServletRequest))} instead.
     */
    @Deprecated
    public static Collection<String> getDefaultNotSaveRequestAttr(HttpServletRequest request) {
        return RequestSavingAttrPolicy.NotSaveable.DEFAULT_ATTR_NAME_EXCL;
    }

    /**
     * SCIPIO: Controls URL format for http-to-https redirects.
     * Added 2018-07-18.
     */
    private enum SecureUrlRedirFmt {
        INCOMING_URL,
        INCOMING_URL_STATICHOST,
        OFBIZ_URL;

        public static final SecureUrlRedirFmt VALUE;
        static {
            switch(UtilProperties.getPropertyValue("requestHandler", "secure-redirect-url-format", "ofbiz-url")) {
            case "incoming-url": VALUE = INCOMING_URL; break;
            case "incoming-url-statichost": VALUE = INCOMING_URL_STATICHOST; break;
            default: VALUE = OFBIZ_URL;
            }
        }
        public boolean isIncoming() { return this != OFBIZ_URL; }
        public boolean isStaticHost() { return this == INCOMING_URL_STATICHOST; }
    }

    private static String getFullIncomingURL(HttpServletRequest request, HttpServletResponse response, Boolean encode) { // SCIPIO
        String newUrl;
        if (SecureUrlRedirFmt.VALUE.isIncoming()) {
            // SCIPIO: 2018-07-18: new http-to-https redirect url format option
            newUrl = RequestLinkUtil.rebuildOriginalRequestURL(request, response, UtilHttp.getLocaleExistingSession(request),
                    true, SecureUrlRedirFmt.VALUE.isStaticHost(), true, true, true);
            if (!Boolean.FALSE.equals(encode)) {
                newUrl = response.encodeURL(newUrl); // for URL rewriting, etc.
            }
        } else {
            StringBuilder urlBuf = new StringBuilder();
            urlBuf.append(request.getPathInfo());
            if (request.getQueryString() != null) {
                urlBuf.append("?").append(request.getQueryString());
            }
            // SCIPIO: Always make full URL for redirect so uses host from entities
            //String newUrl = RequestHandler.makeUrl(request, response, urlBuf.toString());
            newUrl = RequestHandler.makeUrl(request, response, urlBuf.toString(), true, null, encode);
        }
        return newUrl;
    }

    /**
     * SCIPIO: Public redirect helper method that honors the status codes configured in the current controller
     * or requestHandler.properties.
     * <p>
     * NOTE: The url is NOT sent through URL encoding automatically; caller must do this (through {@link #makeLink} or other)!
     */
    public static void sendRedirect(HttpServletRequest request, HttpServletResponse response, String url) throws IllegalStateException {
        Integer statusCode = null;
        try {
            //statusCodeString = controllerConfig.getStatusCode();
            statusCode = getRequestHandler(request).getControllerConfig().getStatusCodeNumber();
        } catch (Exception e) {
            Debug.logError(e, "Exception thrown while parsing controller.xml file: ", module);
        }
        //if (UtilValidate.isEmpty(statusCodeString)) {
        if (statusCode == null) {
            statusCode = defaultStatusCodeNumber;
        }

        // send the redirect
        response.setStatus(statusCode);
        response.setHeader("Location", url);
        // SCIPIO: This is not appropriate; majority of redirects in scipio are intra-webapp, followed by inter-webapp
        //response.setHeader("Connection", "close");
    }

    /**
     * SCIPIO: Public redirect helper method that honors the status codes configured in the current controller
     * or requestHandler.properties.
     */
    public static void sendControllerUriRedirect(HttpServletRequest request, HttpServletResponse response, String uri) throws IllegalStateException {
        String url = makeUrlFull(request, response, uri);
        if (url != null) {
            sendRedirect(request, response, url);
        } else {
            throw new IllegalStateException("Cannot redirect to controller uri because failed to generate link: " + uri);
        }
    }

    /**
     * SCIPIO: Public redirect helper method that honors the status codes configured in the current controller
     * or requestHandler.properties, preserving the incoming query string.
     * <p>
     * FIXME: If uri provides any parameters, they may be crushed or duplicated by the incoming ones.
     */
    public static void sendControllerUriRedirectWithQueryString(HttpServletRequest request, HttpServletResponse response, String uri) throws IllegalStateException {
        ServletContext ctx = request.getServletContext(); // SCIPIO: get context using servlet API 3.0
        RequestHandler rh = (RequestHandler) ctx.getAttribute("_REQUEST_HANDLER_");
        String url = rh.makeLinkFull(request, response, uri + rh.makeQueryString(request, null, null));
        if (url != null) {
            sendRedirect(request, response, url);
        } else {
            throw new IllegalStateException("Cannot redirect to controller uri because failed to generate link: " + uri);
        }
    }
}

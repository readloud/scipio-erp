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
package org.ofbiz.service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.transaction.Transaction;

import org.ofbiz.base.config.GenericConfigException;
import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.PropertyMessage;
import org.ofbiz.base.util.PropertyMessageExUtil;
import org.ofbiz.base.util.UtilDateTime;
import org.ofbiz.base.util.UtilGenerics;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilProperties;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.entity.Delegator;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.condition.EntityCondition;
import org.ofbiz.entity.condition.EntityExpr;
import org.ofbiz.entity.condition.EntityOperator;
import org.ofbiz.entity.transaction.GenericTransactionException;
import org.ofbiz.entity.transaction.TransactionUtil;
import org.ofbiz.entity.util.EntityListIterator;
import org.ofbiz.entity.util.EntityQuery;
import org.ofbiz.security.Security;
import org.ofbiz.service.config.ServiceConfigUtil;

import com.ibm.icu.util.Calendar;

/**
 * Generic Service Utility Class
 */
public final class ServiceUtil {

    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());
    public static final String resource = "ServiceErrorUiLabels"; // SCIPIO: 2018-08-29: keep public for backward-compat

    /** A little short-cut method to check to see if a service returned an error */
    public static boolean isError(Map<String, ? extends Object> results) {
        if (results == null || results.get(ModelService.RESPONSE_MESSAGE) == null) {
            return false;
        }
        return ModelService.RESPOND_ERROR.equals(results.get(ModelService.RESPONSE_MESSAGE));
    }

    public static boolean isFailure(Map<String, ? extends Object> results) {
        if (results == null || results.get(ModelService.RESPONSE_MESSAGE) == null) {
            return false;
        }
        return ModelService.RESPOND_FAIL.equals(results.get(ModelService.RESPONSE_MESSAGE));
    }

    /** A little short-cut method to check to see if a service was successful (neither error or failed) */
    public static boolean isSuccess(Map<String, ? extends Object> results) {
        if (ServiceUtil.isError(results) || ServiceUtil.isFailure(results)) {
            return false;
        }
        return true;
    }

    /** A small routine used all over to improve code efficiency, make a result map with the message and the error response code */
    public static Map<String, Object> returnError(String errorMessage) {
        return returnProblem(ModelService.RESPOND_ERROR, errorMessage, null, null, null);
    }

    /** A small routine used all over to improve code efficiency, make a result map with the message and the error response code */
    public static Map<String, Object> returnError(String errorMessage, List<? extends Object> errorMessageList) {
        return returnProblem(ModelService.RESPOND_ERROR, errorMessage, errorMessageList, null, null);
    }

    /** A small routine used all over to improve code efficiency, make a result map with the message and the error response code */
    public static Map<String, Object> returnError(List<? extends Object> errorMessageList) {
        return returnProblem(ModelService.RESPOND_ERROR, null, errorMessageList, null, null);
    }

    public static Map<String, Object> returnFailure(String errorMessage) {
        return returnProblem(ModelService.RESPOND_FAIL, errorMessage, null, null, null);
    }

    /** Returns fail service message. SCIPIO: added missing service overload, 2017-11-01. */
    public static Map<String, Object> returnFailure(String errorMessage, List<? extends Object> errorMessageList) {
        return returnProblem(ModelService.RESPOND_FAIL, errorMessage, errorMessageList, null, null);
    }

    public static Map<String, Object> returnFailure(List<? extends Object> errorMessageList) {
        return returnProblem(ModelService.RESPOND_FAIL, null, errorMessageList, null, null);
    }

    public static Map<String, Object> returnFailure() {
        return returnProblem(ModelService.RESPOND_FAIL, null, null, null, null);
    }

    /** A small routine used all over to improve code efficiency, make a result map with the message and the error response code, also forwards any error messages from the nestedResult */
    public static Map<String, Object> returnError(String errorMessage, List<? extends Object> errorMessageList, Map<String, ? extends Object> errorMessageMap, Map<String, ? extends Object> nestedResult) {
        return returnProblem(ModelService.RESPOND_ERROR, errorMessage, errorMessageList, errorMessageMap, nestedResult);
    }

    public static Map<String, Object> returnProblem(String returnType, String errorMessage, List<? extends Object> errorMessageList, Map<String, ? extends Object> errorMessageMap, Map<String, ? extends Object> nestedResult) {
        Map<String, Object> result = new HashMap<>();
        result.put(ModelService.RESPONSE_MESSAGE, returnType);
        if (errorMessage != null) {
            result.put(ModelService.ERROR_MESSAGE, errorMessage);
        }

        List<Object> errorList = new ArrayList<>(); // SCIPIO: switched to ArrayList
        if (errorMessageList != null) {
            errorList.addAll(errorMessageList);
        }

        Map<String, Object> errorMap = new HashMap<>();
        if (errorMessageMap != null) {
            errorMap.putAll(errorMessageMap);
        }

        if (nestedResult != null) {
            if (nestedResult.get(ModelService.ERROR_MESSAGE) != null) {
                errorList.add(nestedResult.get(ModelService.ERROR_MESSAGE));
            }
            if (nestedResult.get(ModelService.ERROR_MESSAGE_LIST) != null) {
                errorList.addAll(UtilGenerics.checkList(nestedResult.get(ModelService.ERROR_MESSAGE_LIST)));
            }
            if (nestedResult.get(ModelService.ERROR_MESSAGE_MAP) != null) {
                errorMap.putAll(UtilGenerics.<String, Object>checkMap(nestedResult.get(ModelService.ERROR_MESSAGE_MAP)));
            }
        }

        if (errorList.size() > 0) {
            result.put(ModelService.ERROR_MESSAGE_LIST, errorList);
        }
        if (errorMap.size() > 0) {
            result.put(ModelService.ERROR_MESSAGE_MAP, errorMap);
        }
        // SCIPIO: NOTE: 2018-08-29: upstream added this, I do not agree with it, enough logs already
        //Debug.logError(result.toString(), module);
        return result;
    }

    /** A small routine used all over to improve code efficiency, make a result map with the message and the success response code */
    public static Map<String, Object> returnSuccess(String successMessage) {
        return returnMessage(ModelService.RESPOND_SUCCESS, successMessage);
    }

    /** A small routine used all over to improve code efficiency, make a result map with the message and the success response code */
    public static Map<String, Object> returnSuccess() {
        return returnMessage(ModelService.RESPOND_SUCCESS, null);
    }

    /** A small routine used all over to improve code efficiency, make a result map with the message and the success response code */
    public static Map<String, Object> returnSuccess(List<String> successMessageList) {
        Map<String, Object> result = returnMessage(ModelService.RESPOND_SUCCESS, null);
        result.put(ModelService.SUCCESS_MESSAGE_LIST, successMessageList);
        return result;
    }

    /** A small routine to make a result map with the message and the response code
     * NOTE: This brings out some bad points to our message convention: we should be using a single message or message list
     *  and what type of message that is should be determined by the RESPONSE_MESSAGE (and there's another annoyance, it should be RESPONSE_CODE)
     */
    public static Map<String, Object> returnMessage(String code, String message) {
        Map<String, Object> result = new HashMap<>();
        if (code != null) {
            result.put(ModelService.RESPONSE_MESSAGE, code);
        }
        if (message != null) {
            result.put(ModelService.SUCCESS_MESSAGE, message);
        }
        return result;
    }

    /** SCIPIO: Creates a service error result map from the given exception using the given localizable intro message
     * combined with a suffix message taken from either a localizable property message via {@link org.ofbiz.base.util.PropertyMessageEx#getPropertyMessage()}
     * stored in the exception if it implements PropertyMessageEx or the exception detail message if any other exception type,
     * in addition to any message lists stored in the exception via {@link org.ofbiz.base.util.PropertyMessageEx#getPropertyMessageList()}.
     * In other words this abstracts and automated the service result building from exception messages.
     * Added 2017-12-07.
     */
    public static Map<String, Object> returnError(PropertyMessage messageIntro, Throwable t, Locale locale) {
        return returnError(PropertyMessageExUtil.makeServiceMessage(messageIntro, t, locale),
                PropertyMessageExUtil.getExceptionMessageList(t, locale));
    }

    /** SCIPIO: Creates a service error result map from the given exception using the given static intro message
     * combined with a suffix message taken from either a localizable property message via {@link org.ofbiz.base.util.PropertyMessageEx#getPropertyMessage()}
     * stored in the exception if it implements PropertyMessageEx or the exception detail message if any other exception type,
     * in addition to any message lists stored in the exception via {@link org.ofbiz.base.util.PropertyMessageEx#getPropertyMessageList()}.
     * In other words this abstracts and automated the service result building from exception messages.
     * Added 2017-12-07.
     */
    public static Map<String, Object> returnError(String messageIntro, Throwable t, Locale locale) {
        return returnError(PropertyMessageExUtil.makeServiceMessage(messageIntro, t, locale),
                PropertyMessageExUtil.getExceptionMessageList(t, locale));
    }

    /** SCIPIO: Creates a service error result map from the given exception using the given localizable intro message
     * combined with a suffix message taken from either a localizable property message via {@link org.ofbiz.base.util.PropertyMessageEx#getPropertyMessage()}
     * stored in the exception if it implements PropertyMessageEx or the exception detail message if any other exception type,
     * in addition to any message lists stored in the exception via {@link org.ofbiz.base.util.PropertyMessageEx#getPropertyMessageList()}.
     * In other words this abstracts and automated the service result building from exception messages.
     * Added 2017-12-07.
     */
    public static Map<String, Object> returnFailure(PropertyMessage messageIntro, Throwable t, Locale locale) {
        return returnFailure(PropertyMessageExUtil.makeServiceMessage(messageIntro, t, locale),
                PropertyMessageExUtil.getExceptionMessageList(t, locale));
    }

    /** SCIPIO: Creates a service error result map from the given exception using the given localizable intro message
     * combined with a suffix message taken from either a localizable property message via {@link org.ofbiz.base.util.PropertyMessageEx#getPropertyMessage()}
     * stored in the exception if it implements PropertyMessageEx or the exception detail message if any other exception type,
     * in addition to any message lists stored in the exception via {@link org.ofbiz.base.util.PropertyMessageEx#getPropertyMessageList()}.
     * In other words this abstracts and automated the service result building from exception messages.
     * Added 2017-12-07.
     */
    public static Map<String, Object> returnFailure(String messageIntro, Throwable t, Locale locale) {
        return returnFailure(PropertyMessageExUtil.makeServiceMessage(messageIntro, t, locale),
                PropertyMessageExUtil.getExceptionMessageList(t, locale));
    }

    /** A small routine used all over to improve code efficiency, get the partyId and does a security check
     *<b>security check</b>: userLogin partyId must equal partyId, or must have [secEntity][secOperation] permission
     */
    public static String getPartyIdCheckSecurity(GenericValue userLogin, Security security, Map<String, ? extends Object> context, Map<String, Object> result, String secEntity, String secOperation) {
        return getPartyIdCheckSecurity(userLogin, security, context, result, secEntity, secOperation, null, null);
    }
    public static String getPartyIdCheckSecurity(GenericValue userLogin, Security security, Map<String, ? extends Object> context, Map<String, Object> result, String secEntity, String secOperation, String adminSecEntity, String adminSecOperation) {
        String partyId = (String) context.get("partyId");
        Locale locale = getLocale(context);
        if (UtilValidate.isEmpty(partyId)) {
            partyId = userLogin.getString("partyId");
        }

        // partyId might be null, so check it
        if (UtilValidate.isEmpty(partyId)) {
            result.put(ModelService.RESPONSE_MESSAGE, ModelService.RESPOND_ERROR);
            String errMsg = UtilProperties.getMessage(ServiceUtil.resource, "serviceUtil.party_id_missing", locale) + ".";
            result.put(ModelService.ERROR_MESSAGE, errMsg);
            return partyId;
        }

        // <b>security check</b>: userLogin partyId must equal partyId, or must have either of the two permissions
        if (!partyId.equals(userLogin.getString("partyId"))) {
            if (!security.hasEntityPermission(secEntity, secOperation, userLogin) && !(adminSecEntity != null && adminSecOperation != null && security.hasEntityPermission(adminSecEntity, adminSecOperation, userLogin))) {
                result.put(ModelService.RESPONSE_MESSAGE, ModelService.RESPOND_ERROR);
                String errMsg = UtilProperties.getMessage(ServiceUtil.resource, "serviceUtil.no_permission_to_operation", locale) + ".";
                result.put(ModelService.ERROR_MESSAGE, errMsg);
                return partyId;
            }
        }
        return partyId;
    }

    public static void setMessages(HttpServletRequest request, String errorMessage, String eventMessage, String defaultMessage) {
        if (UtilValidate.isNotEmpty(errorMessage)) {
            request.setAttribute("_ERROR_MESSAGE_", errorMessage);
        }

        if (UtilValidate.isNotEmpty(eventMessage)) {
            request.setAttribute("_EVENT_MESSAGE_", eventMessage);
        }

        if (UtilValidate.isEmpty(errorMessage) && UtilValidate.isEmpty(eventMessage) && UtilValidate.isNotEmpty(defaultMessage)) {
            request.setAttribute("_EVENT_MESSAGE_", defaultMessage);
        }

    }

    /**
     * Concatenates the error and event messages from the given service result into a single error string and a single event string,
     * and sets them as the request attributes _ERROR_MESSAGE_ and _EVENT_MESSAGE_, respectively.
     * <p>
     * SCIPIO: NOTE: This is ambiguously named and does not preserve event/error message lists; because of this,
     * you may want to use {@link #appendRequestMessages} or {@link #setRequestMessages(HttpServletRequest, Map)}.
     */
    public static void getMessages(HttpServletRequest request, Map<String, ? extends Object> result) { // SCIPIO: Added missing overload
        getMessages(request, result, null, null, null, null, null, null, null);
    }

    /**
     * Concatenates the error and event messages from the given service result into a single error string and a single event string,
     * and sets them as the request attributes _ERROR_MESSAGE_ and _EVENT_MESSAGE_, respectively.
     * <p>
     * SCIPIO: NOTE: This is ambiguously named and does not preserve event/error message lists; because of this,
     * you may want to use {@link #appendRequestMessages} or {@link #setRequestMessages(HttpServletRequest, Map)}.
     */
    public static void getMessages(HttpServletRequest request, Map<String, ? extends Object> result, String defaultMessage) {
        getMessages(request, result, defaultMessage, null, null, null, null, null, null);
    }

    /**
     * Concatenates the error and event messages from the given service result into a single error string and a single event string,
     * and sets them as the request attributes _ERROR_MESSAGE_ and _EVENT_MESSAGE_, respectively.
     * <p>
     * SCIPIO: NOTE: This is ambiguously named and does not preserve event/error message lists; because of this,
     * you may want to use {@link #appendRequestMessages} or {@link #setRequestMessages(HttpServletRequest, Map)}.
     */
    public static void getMessages(HttpServletRequest request, Map<String, ? extends Object> result, String defaultMessage,
                                   String msgPrefix, String msgSuffix, String errorPrefix, String errorSuffix, String successPrefix, String successSuffix) {
        String errorMessage = ServiceUtil.makeErrorMessage(result, msgPrefix, msgSuffix, errorPrefix, errorSuffix);
        String successMessage = ServiceUtil.makeSuccessMessage(result, msgPrefix, msgSuffix, successPrefix, successSuffix);
        setMessages(request, errorMessage, successMessage, defaultMessage);
    }

    /**
     * SCIPIO: Alternative to {@link #getMessages(HttpServletRequest, Map, String)} that preserves lists when setting in request.
     * The lists are appended to existing, but single message is replaced.
     * @deprecated 2019-02-05: This method was ambiguous, use {@link #appendRequestMessages(HttpServletRequest, Map)} instead.
     */
    @Deprecated
    public static void appendMessageLists(HttpServletRequest request, Map<String, ? extends Object> serviceResult) {
        appendRequestMessages(request, serviceResult);
    }

    /**
     * SCIPIO: Alternative to {@link #getMessages(HttpServletRequest, Map, String)} that preserves lists when setting in request.
     * The lists are appended to existing, but single message is replaced.
     */
    public static void appendRequestMessages(HttpServletRequest request, Map<String, ? extends Object> serviceResult) {
        String successMessage = (String) serviceResult.get(ModelService.SUCCESS_MESSAGE);
        List<?> successList = UtilGenerics.checkList(serviceResult.get(ModelService.SUCCESS_MESSAGE_LIST));
        String errorMessage = (String) serviceResult.get(ModelService.ERROR_MESSAGE);
        List<?> errorList = UtilGenerics.checkList(serviceResult.get(ModelService.ERROR_MESSAGE_LIST));
        Map<?, ?> errorMap = UtilGenerics.checkMap(serviceResult.get(ModelService.ERROR_MESSAGE_MAP));
        if (UtilValidate.isNotEmpty(successMessage)) {
            request.setAttribute("_EVENT_MESSAGE_", successMessage);
        }
        if (UtilValidate.isNotEmpty(successList)) {
            List<Object> reqEventList = UtilGenerics.checkList(request.getAttribute("_EVENT_MESSAGE_LIST_"));
            if (reqEventList == null) {
                reqEventList = new ArrayList<Object>();
            }
            reqEventList.addAll(successList);
            request.setAttribute("_EVENT_MESSAGE_LIST_", reqEventList);
        }
        if (UtilValidate.isNotEmpty(errorMessage)) {
            request.setAttribute("_ERROR_MESSAGE_", errorMessage);
        }
        if (UtilValidate.isNotEmpty(errorList)) {
            List<Object> reqErrorList = UtilGenerics.checkList(request.getAttribute("_ERROR_MESSAGE_LIST_"));
            if (reqErrorList == null) {
                reqErrorList = new ArrayList<Object>();
            }
            reqErrorList.addAll(errorList);
            request.setAttribute("_ERROR_MESSAGE_LIST_", reqErrorList);
        }
        if (UtilValidate.isNotEmpty(errorMap)) {
            Map<Object, Object> reqErrorMap = UtilGenerics.checkMap(request.getAttribute("_ERROR_MESSAGE_MAP_"));
            if (reqErrorMap == null) {
                reqErrorMap = new HashMap<>();
            }
            reqErrorMap.putAll(errorMap);
            request.setAttribute("_ERROR_MESSAGE_MAP_", reqErrorMap);
        }
    }

    /**
     * SCIPIO: Alternative to {@link #getMessages(HttpServletRequest, Map, String)} that preserves lists when setting in request.
     * Unlike {@link #appendMessageLists(HttpServletRequest, Map)}, all existing messages are completely replaced, efficiently.
     */
    public static void setRequestMessages(HttpServletRequest request, Map<String, ? extends Object> serviceResult) {
        // NOTE: null values simply cause the servlet API to remove the attribute, which is fine here.
        request.setAttribute("_EVENT_MESSAGE_", serviceResult.get(ModelService.SUCCESS_MESSAGE));
        request.setAttribute("_EVENT_MESSAGE_LIST_", serviceResult.get(ModelService.SUCCESS_MESSAGE_LIST));
        request.setAttribute("_ERROR_MESSAGE_", serviceResult.get(ModelService.ERROR_MESSAGE));
        request.setAttribute("_ERROR_MESSAGE_LIST_", serviceResult.get(ModelService.ERROR_MESSAGE_LIST));
        request.setAttribute("_ERROR_MESSAGE_MAP_", serviceResult.get(ModelService.ERROR_MESSAGE_MAP));
    }

    /**
     * SCIPIO: Clears all the event message request attributes.
     */
    public static void clearRequestMessages(HttpServletRequest request) {
        request.removeAttribute("_EVENT_MESSAGE_");
        request.removeAttribute("_EVENT_MESSAGE_LIST_");
        request.removeAttribute("_ERROR_MESSAGE_");
        request.removeAttribute("_ERROR_MESSAGE_LIST_");
        request.removeAttribute("_ERROR_MESSAGE_MAP_");
    }

    public static String getErrorMessage(Map<String, ? extends Object> result) {
        /* SCIPIO: 2018-10-09: This popular function is enhanced to avoid string handling
           overhead; 99% of cases do not need string building.
        StringBuilder errorMessage = new StringBuilder();

        if (result.get(ModelService.ERROR_MESSAGE) != null) {
            errorMessage.append((String) result.get(ModelService.ERROR_MESSAGE));
        }

        if (result.get(ModelService.ERROR_MESSAGE_LIST) != null) {
            List<? extends Object> errors = UtilGenerics.checkList(result.get(ModelService.ERROR_MESSAGE_LIST));
            for (Object message: errors) {
                // NOTE: this MUST use toString and not cast to String because it may be a MessageString object
                String curMessage = message.toString();
                if (errorMessage.length() > 0) {
                    errorMessage.append(", ");
                }
                errorMessage.append(curMessage);
            }
        }

        return errorMessage.toString();
        */
        return getMessage(result.get(ModelService.ERROR_MESSAGE), result.get(ModelService.ERROR_MESSAGE_LIST));
    }

    /**
     * SCIPIO: Gets message from service result message and message list.
     * Derived from {@link #getErrorMessage(Map)} but modified to avoid string building
     * unless actually have multiple messages to concat, and to support toString()
     * on the single messageObj value.
     * Added 2018-10-09.
     */
    private static String getMessage(Object messageObj, Object messageListObj) {
        if (messageObj != null) {
            String message = messageObj.toString();
            if (messageListObj == null) {
                return message;
            }
            List<?> messages = (List<?>) messageListObj;
            if (messages.size() == 0) {
                return message;
            }
            return concatMessages(new StringBuilder(message), messages);
        } else if (messageListObj != null) {
            List<?> messageList = (List<?>) messageListObj;
            if (messageList.size() == 1) {
                return messageList.get(0).toString();
            } else if (messageList.size() >= 2) { // NOTE: Though rarely false, check size so can use getXxxMessage as a fast message presence check
                return concatMessages(new StringBuilder(), messageList);
            }
        }
        return "";
    }

    private static String concatMessages(StringBuilder message, List<?> messages) { // SCIPIO
        for (Object msg : messages) {
            // NOTE: this MUST use toString and not cast to String because it may be a MessageString object
            String curMessage = msg.toString();
            if (message.length() > 0) {
                message.append(", ");
            }
            message.append(curMessage);
        }
        return message.toString();
    }
    
    public static String makeErrorMessage(Map<String, ? extends Object> result, String msgPrefix, String msgSuffix, String errorPrefix, String errorSuffix) {
        if (result == null) {
            Debug.logWarning("A null result map was passed", module);
            return null;
        }
        String errorMsg = (String) result.get(ModelService.ERROR_MESSAGE);
        List<? extends Object> errorMsgList = UtilGenerics.checkList(result.get(ModelService.ERROR_MESSAGE_LIST));
        Map<String, ? extends Object> errorMsgMap = UtilGenerics.checkMap(result.get(ModelService.ERROR_MESSAGE_MAP));
        StringBuilder outMsg = new StringBuilder();

        if (errorMsg != null) {
            if (msgPrefix != null) {
                outMsg.append(msgPrefix);
            }
            outMsg.append(errorMsg);
            if (msgSuffix != null) {
                outMsg.append(msgSuffix);
            }
        }

        outMsg.append(makeMessageList(errorMsgList, msgPrefix, msgSuffix));

        if (errorMsgMap != null) {
            for (Map.Entry<String, ? extends Object> entry: errorMsgMap.entrySet()) {
                outMsg.append(msgPrefix);
                outMsg.append(entry.getKey());
                outMsg.append(": ");
                outMsg.append(entry.getValue());
                outMsg.append(msgSuffix);
            }
        }

        if (outMsg.length() > 0) {
            StringBuilder strBuf = new StringBuilder();

            if (errorPrefix != null) {
                strBuf.append(errorPrefix);
            }
            strBuf.append(outMsg.toString());
            if (errorSuffix != null) {
                strBuf.append(errorSuffix);
            }
            return strBuf.toString();
        }
        return null;
    }

    /**
     * SCIPIO: Gets concatenated success message from single message and message list.
     * Analogous to {@link #getErrorMessage(Map)}, success message version.
     * Added 2017-11-28.
     */
    public static String getSuccessMessage(Map<String, ? extends Object> result) {
        return getMessage(result.get(ModelService.SUCCESS_MESSAGE), result.get(ModelService.SUCCESS_MESSAGE_LIST));
    }

    public static String makeSuccessMessage(Map<String, ? extends Object> result, String msgPrefix, String msgSuffix, String successPrefix, String successSuffix) {
        if (result == null) {
            return "";
        }
        String successMsg = (String) result.get(ModelService.SUCCESS_MESSAGE);
        List<? extends Object> successMsgList = UtilGenerics.checkList(result.get(ModelService.SUCCESS_MESSAGE_LIST));
        StringBuilder outMsg = new StringBuilder();

        outMsg.append(makeMessageList(successMsgList, msgPrefix, msgSuffix));

        if (successMsg != null) {
            if (msgPrefix != null) {
                outMsg.append(msgPrefix);
            }
            outMsg.append(successMsg);
            if (msgSuffix != null) {
                outMsg.append(msgSuffix);
            }
        }

        if (outMsg.length() > 0) {
            StringBuilder strBuf = new StringBuilder();
            if (successPrefix != null) {
                strBuf.append(successPrefix);
            }
            strBuf.append(outMsg.toString());
            if (successSuffix != null) {
                strBuf.append(successSuffix);
            }
            return strBuf.toString();
        }
        return null;
    }

    /**
     * SCIPIO: Concatenation of {@link #getErrorMessage(Map)} followed by {@link #getSuccessMessage(Map)}.
     * Intentionally avoids checking responseMessage.
     * Added 2017-11-28.
     */
    public static String getErrorAndSuccessMessage(Map<String, ? extends Object> result, String joinStr, String errorPrefix, String successPrefix) {
        StringBuilder fullMessage = new StringBuilder();

        String errorMessage = getErrorMessage(result);
        if (errorMessage.length() > 0) {
            if (errorPrefix != null) fullMessage.append(errorPrefix);
            fullMessage.append(errorMessage);
        }

        String successMessage = getSuccessMessage(result);
        if (successMessage.length() > 0) {
            if (fullMessage.length() > 0) fullMessage.append((joinStr != null) ? joinStr : "; ");
            if (successPrefix != null) fullMessage.append(successPrefix);
            fullMessage.append(successMessage);
        }

        return fullMessage.toString();
    }

    /**
     * SCIPIO: Concatenation of {@link #getErrorMessage(Map)} followed by {@link #getSuccessMessage(Map)}.
     * Intentionally does not check responseMessage.
     * Added 2017-11-28.
     */
    public static String getErrorAndSuccessMessage(Map<String, ? extends Object> result) {
        return getErrorAndSuccessMessage(result, "; ", null, null);
    }

    /**
     * SCIPIO: The default message prefix.
     */
    private static final String defaultMessagePrefix = UtilProperties.getMessageNoTrim("DefaultMessages", "service.message.prefix", Locale.getDefault());;

    /**
     * Joins messages from a list into a single string.
     * <p>
     * SCIPIO: <strong>NOTE</strong> This method has been modified so that
     * by default, if msgPrefix is null, a default will be looked up (service.message.prefix in DefaultMessages resource).
     * This is because a number of cases fail to set a proper space separator and text ends up scrunched.
     * This can be overridden by passing the empty string.
     */
    public static String makeMessageList(List<? extends Object> msgList, String msgPrefix, String msgSuffix) {
        StringBuilder outMsg = new StringBuilder();
        // SCIPIO: Lookup default prefix
        if (msgPrefix == null) {
            msgPrefix = defaultMessagePrefix;
        }
        if (UtilValidate.isNotEmpty(msgList)) {
            for (Object msg: msgList) {
                if (msg == null) {
                    continue;
                }
                String curMsg = msg.toString();
                if (msgPrefix != null) {
                    outMsg.append(msgPrefix);
                }
                outMsg.append(curMsg);
                if (msgSuffix != null) {
                    outMsg.append(msgSuffix);
                }
            }
        }
        return outMsg.toString();
    }

    /**
     * Takes the result of an invocation and extracts any error messages
     * and adds them to the targetList or targetMap. This will handle both List and String
     * error messags.
     *
     * @param targetList    The List to add the error messages to
     * @param targetMap The Map to add any Map error messages to
     * @param callResult The result from an invocation
     */
    public static void addErrors(List<String> targetList, Map<String, Object> targetMap, Map<String, ? extends Object> callResult) {
        List<String> newList;
        Map<String, Object> errorMsgMap;

        //See if there is a single message
        if (callResult.containsKey(ModelService.ERROR_MESSAGE)) {
            targetList.add((String) callResult.get(ModelService.ERROR_MESSAGE));
        }

        //See if there is a message list
        if (callResult.containsKey(ModelService.ERROR_MESSAGE_LIST)) {
            newList = UtilGenerics.checkList(callResult.get(ModelService.ERROR_MESSAGE_LIST));
            targetList.addAll(newList);
        }

        //See if there are an error message map
        if (callResult.containsKey(ModelService.ERROR_MESSAGE_MAP)) {
            errorMsgMap = UtilGenerics.checkMap(callResult.get(ModelService.ERROR_MESSAGE_MAP));
            targetMap.putAll(errorMsgMap);
        }
    }

    public static Map<String, Object> purgeOldJobs(DispatchContext dctx, Map<String, ? extends Object> context) {
        Locale locale = (Locale)context.get("locale");
        Debug.logWarning("purgeOldJobs service invoked. This service is obsolete - the Job Scheduler will purge old jobs automatically.", module);
        String sendPool = null;
        Calendar cal = Calendar.getInstance();
        try {
            sendPool = ServiceConfigUtil.getServiceEngine().getThreadPool().getSendToPool();
            int daysToKeep = ServiceConfigUtil.getServiceEngine().getThreadPool().getPurgeJobDays();
            cal.add(Calendar.DAY_OF_YEAR, -daysToKeep);
        } catch (GenericConfigException e) {
            Debug.logWarning(e, "Exception thrown while getting service configuration: ", module);
            return returnError(UtilProperties.getMessage(ServiceUtil.resource, "ServiceExceptionThrownWhileGettingServiceConfiguration", UtilMisc.toMap("errorString", e), locale));
        }
        Delegator delegator = dctx.getDelegator();

        Timestamp purgeTime = new Timestamp(cal.getTimeInMillis());

        // create the conditions to query
        EntityCondition pool = EntityCondition.makeCondition("poolId", sendPool);

        List<EntityExpr> finExp = UtilMisc.toList(EntityCondition.makeCondition("finishDateTime", EntityOperator.NOT_EQUAL, null));
        finExp.add(EntityCondition.makeCondition("finishDateTime", EntityOperator.LESS_THAN, purgeTime));

        List<EntityExpr> canExp = UtilMisc.toList(EntityCondition.makeCondition("cancelDateTime", EntityOperator.NOT_EQUAL, null));
        canExp.add(EntityCondition.makeCondition("cancelDateTime", EntityOperator.LESS_THAN, purgeTime));

        EntityCondition cancelled = EntityCondition.makeCondition(canExp);
        EntityCondition finished = EntityCondition.makeCondition(finExp);

        EntityCondition doneCond = EntityCondition.makeCondition(UtilMisc.toList(cancelled, finished), EntityOperator.OR);

        // always suspend the current transaction; use the one internally
        Transaction parent = null;
        try {
            if (TransactionUtil.getStatus() != TransactionUtil.STATUS_NO_TRANSACTION) {
                parent = TransactionUtil.suspend();
            }

            // lookup the jobs - looping 1000 at a time to avoid problems with cursors
            // also, using unique transaction to delete as many as possible even with errors
            boolean noMoreResults = false;
            boolean beganTx1 = false;
            while (!noMoreResults) {
                // current list of records
                List<GenericValue> curList = null;
                try {
                    // begin this transaction
                    beganTx1 = TransactionUtil.begin();
                    EntityQuery eq = EntityQuery.use(delegator)
                            .select("jobId")
                            .from("JobSandbox")
                            .where(EntityCondition.makeCondition(UtilMisc.toList(doneCond, pool)))
                            .cursorScrollInsensitive()
                            .maxRows(1000);

                    try (EntityListIterator foundJobs = eq.queryIterator()) {
                        curList = foundJobs.getPartialList(1, 1000);
                    }

                } catch (GenericEntityException e) {
                    Debug.logError(e, "Cannot obtain job data from datasource", module);
                    try {
                        TransactionUtil.rollback(beganTx1, e.getMessage(), e);
                    } catch (GenericTransactionException e1) {
                        Debug.logWarning(e1, module);
                    }
                    return ServiceUtil.returnError(e.getMessage());
                } finally {
                    try {
                        TransactionUtil.commit(beganTx1);
                    } catch (GenericTransactionException e) {
                        Debug.logWarning(e, module);
                    }
                }
                // remove each from the list in its own transaction
                if (UtilValidate.isNotEmpty(curList)) {
                    for (GenericValue job: curList) {
                        String jobId = job.getString("jobId");
                        boolean beganTx2 = false;
                        try {
                            beganTx2 = TransactionUtil.begin();
                            job.remove();
                        } catch (GenericEntityException e) {
                            Debug.logInfo("Cannot remove job data for ID: " + jobId, module);
                            try {
                                TransactionUtil.rollback(beganTx2, e.getMessage(), e);
                            } catch (GenericTransactionException e1) {
                                Debug.logWarning(e1, module);
                            }
                        } finally {
                            try {
                                TransactionUtil.commit(beganTx2);
                            } catch (GenericTransactionException e) {
                                Debug.logWarning(e, module);
                            }
                        }
                    }
                } else {
                    noMoreResults = true;
                }
            }

            // Now JobSandbox data is cleaned up. Now process Runtime data and remove the whole data in single shot that is of no need.
            boolean beganTx3 = false;
            GenericValue runtimeData = null;
            List<GenericValue> runtimeDataToDelete = new ArrayList<>(); // SCIPIO: switched to ArrayList
            long jobsandBoxCount = 0;
            try {
                // begin this transaction
                beganTx3 = TransactionUtil.begin();

                EntityQuery eq = EntityQuery.use(delegator).select("runtimeDataId").from("RuntimeData");
                try (EntityListIterator runTimeDataIt = eq.queryIterator()) {
                    while ((runtimeData = runTimeDataIt.next()) != null) {
                        EntityCondition whereCondition = EntityCondition.makeCondition(UtilMisc.toList(EntityCondition.makeCondition("runtimeDataId", EntityOperator.NOT_EQUAL, null),
                                EntityCondition.makeCondition("runtimeDataId", EntityOperator.EQUALS, runtimeData.getString("runtimeDataId"))), EntityOperator.AND);
                        jobsandBoxCount = EntityQuery.use(delegator).from("JobSandbox").where(whereCondition).queryCount();
                        if (BigDecimal.ZERO.compareTo(BigDecimal.valueOf(jobsandBoxCount)) == 0) {
                            runtimeDataToDelete.add(runtimeData);
                        }
                    }
                }
                // Now we are ready to delete runtimeData, we can safely delete complete list that we have recently fetched i.e runtimeDataToDelete.
                delegator.removeAll(runtimeDataToDelete);
            } catch (GenericEntityException e) {
                Debug.logError(e, "Cannot obtain runtime data from datasource", module);
                try {
                    TransactionUtil.rollback(beganTx3, e.getMessage(), e);
                } catch (GenericTransactionException e1) {
                    Debug.logWarning(e1, module);
                }
                return ServiceUtil.returnError(e.getMessage());
            } finally {
                try {
                    TransactionUtil.commit(beganTx3);
                } catch (GenericTransactionException e) {
                    Debug.logWarning(e, module);
                }
            }
        } catch (GenericTransactionException e) {
            Debug.logError(e, "Unable to suspend transaction; cannot purge jobs!", module);
            return ServiceUtil.returnError(e.getMessage());
        } finally {
            if (parent != null) {
                try {
                    TransactionUtil.resume(parent);
                } catch (GenericTransactionException e) {
                    Debug.logWarning(e, module);
                }
            }
        }

        return ServiceUtil.returnSuccess();
    }

    public static Map<String, Object> cancelJob(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        Security security = dctx.getSecurity();
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        Locale locale = getLocale(context);

        if (!security.hasPermission("SERVICE_INVOKE_ANY", userLogin)) {
            String errMsg = UtilProperties.getMessage(ServiceUtil.resource, "serviceUtil.no_permission_to_run", locale) + ".";
            return ServiceUtil.returnError(errMsg);
        }

        String jobId = (String) context.get("jobId");
        Map<String, Object> fields = UtilMisc.<String, Object>toMap("jobId", jobId);

        GenericValue job = null;
        try {
            job = EntityQuery.use(delegator).from("JobSandbox").where("jobId", jobId).queryOne();
            if (job != null) {
                job.set("cancelDateTime", UtilDateTime.nowTimestamp());
                job.set("statusId", "SERVICE_CANCELLED");
                job.store();
            }
        } catch (GenericEntityException e) {
            Debug.logError(e, module);
            String errMsg = UtilProperties.getMessage(ServiceUtil.resource, "serviceUtil.unable_to_cancel_job", locale) + " : " + fields;
            return ServiceUtil.returnError(errMsg);
        }

        if (job != null) {
            Timestamp cancelDate = job.getTimestamp("cancelDateTime");
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("cancelDateTime", cancelDate);
            result.put("statusId", "SERVICE_PENDING"); // To more easily see current pending jobs and possibly cancel some others
            return result;
        }
        String errMsg = UtilProperties.getMessage(ServiceUtil.resource, "serviceUtil.unable_to_cancel_job", locale) + " : " + null;
        return ServiceUtil.returnError(errMsg);
    }

    public static Map<String, Object> cancelJobRetries(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        Security security = dctx.getSecurity();
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        Locale locale = getLocale(context);
        if (!security.hasPermission("SERVICE_INVOKE_ANY", userLogin)) {
            String errMsg = UtilProperties.getMessage(ServiceUtil.resource, "serviceUtil.no_permission_to_run", locale) + ".";
            return ServiceUtil.returnError(errMsg);
        }

        String jobId = (String) context.get("jobId");
        Map<String, Object> fields = UtilMisc.<String, Object>toMap("jobId", jobId);

        GenericValue job = null;
        try {
            job = EntityQuery.use(delegator).from("JobSandbox").where("jobId", jobId).queryOne();
            if (job != null) {
                job.set("maxRetry", 0L);
                job.store();
            }
        } catch (GenericEntityException e) {
            Debug.logError(e, module);
            String errMsg = UtilProperties.getMessage(ServiceUtil.resource, "serviceUtil.unable_to_cancel_job_retries", locale) + " : " + fields;
            return ServiceUtil.returnError(errMsg);
        }

        if (job != null) {
            return ServiceUtil.returnSuccess();
        }
        String errMsg = UtilProperties.getMessage(ServiceUtil.resource, "serviceUtil.unable_to_cancel_job_retries", locale) + " : " + null;
        return ServiceUtil.returnError(errMsg);
    }

    public static Map<String, Object> genericDateCondition(DispatchContext dctx, Map<String, ? extends Object> context) {
        Timestamp fromDate = (Timestamp) context.get("fromDate");
        Timestamp thruDate = (Timestamp) context.get("thruDate");
        Timestamp now = UtilDateTime.nowTimestamp();
        boolean reply = true;

        if (fromDate != null && fromDate.after(now)) {
            reply = false;
        }
        if (thruDate != null && thruDate.before(now)) {
            reply = false;
        }

        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("conditionReply", reply);
        return result;
    }

    public static GenericValue getUserLogin(DispatchContext dctx, Map<String, ? extends Object> context, String runAsUser) {
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        Delegator delegator = dctx.getDelegator();
        if (UtilValidate.isNotEmpty(runAsUser)) {
            try {
                GenericValue runAs = EntityQuery.use(delegator).from("UserLogin").where("userLoginId", runAsUser).cache().queryOne();
                if (runAs != null) {
                    userLogin = runAs;
                }
            } catch (GenericEntityException e) {
                Debug.logError(e, module);
            }
        }
        return userLogin;
    }

    private static Locale getLocale(Map<String, ? extends Object> context) {
        Locale locale = (Locale) context.get("locale");
        if (locale == null) {
            locale = Locale.getDefault();
        }
        return locale;
    }

    @SafeVarargs
    public static <T extends Object> Map<String, Object> makeContext(T... args) {
        if (args == null) {
            throw new IllegalArgumentException("args is null in makeContext, this would throw a NullPointerExcption.");
        }
        for (int i = 0; i < args.length; i += 2) {
            if (!(args[i] instanceof String)) {
                throw new IllegalArgumentException("Arg(" + i + "), value(" + args[i] + ") is not a string.");
            }
        }
        return UtilGenerics.checkMap(UtilMisc.toMap(args));
    }

    public static Map<String, Object> resetJob(DispatchContext dctx, Map<String, Object> context) {
        Delegator delegator = dctx.getDelegator();
        Security security = dctx.getSecurity();
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        Locale locale = getLocale(context);

        if (!security.hasPermission("SERVICE_INVOKE_ANY", userLogin)) {
            String errMsg = UtilProperties.getMessage(ServiceUtil.resource, "serviceUtil.no_permission_to_run", locale) + ".";
            return ServiceUtil.returnError(errMsg);
        }

        String jobId = (String) context.get("jobId");
        GenericValue job;
        try {
            job = EntityQuery.use(delegator).from("JobSandbox").where("jobId", jobId).cache().queryOne();
        } catch (GenericEntityException e) {
            Debug.logError(e, module);
            return ServiceUtil.returnError(e.getMessage());
        }

        // update the job
        if (job != null) {
            job.set("statusId", "SERVICE_PENDING");
            job.set("startDateTime", null);
            job.set("finishDateTime", null);
            job.set("cancelDateTime", null);
            job.set("runByInstanceId", null);

            // save the job
            try {
                job.store();
            } catch (GenericEntityException e) {
                Debug.logError(e, module);
                return ServiceUtil.returnError(e.getMessage());
            }
        }

        return ServiceUtil.returnSuccess();
    }

    /**
     * Checks all incoming service attributes and look for fields with the same
     * name in the incoming map and copy those onto the outgoing map. Also
     * includes a userLogin if service requires one.
     *
     * @param dispatcher
     * @param serviceName
     * @param fromMap
     * @param userLogin
     *            (optional) - will be added to the map if is required
     * @param timeZone
     * @param locale
     * @return filled Map or null on error
     * @throws GeneralServiceException
     */
    public static Map<String, Object> setServiceFields(LocalDispatcher dispatcher, String serviceName, Map<String, Object> fromMap, GenericValue userLogin,
            TimeZone timeZone, Locale locale) throws GeneralServiceException {
        Map<String, Object> outMap = new HashMap<>();

        ModelService modelService = null;
        try {
            modelService = dispatcher.getDispatchContext().getModelService(serviceName);
        } catch (GenericServiceException e) {
            String errMsg = "Could not get service definition for service name [" + serviceName + "]: ";
            Debug.logError(e, errMsg, module);
            throw new GeneralServiceException(e);
        }
        outMap.putAll(modelService.makeValid(fromMap, ModelService.IN_PARAM, true, null, timeZone, locale));

        if (userLogin != null && modelService.auth) {
            outMap.put("userLogin", userLogin);
        }

        return outMap;
    }

    public static String getResource() {
        return resource;
    }

    /**
     * SCIPIO: Returns a new map containing only the common system service response fields from the
     * given service results.
     * This can be used to copy a success/failure/error message but excluding all the service-specific return values.
     * In other words, returning this from a service can never trigger an out parameter service validation exception.
     * Added 2017-11-28.
     */
    public static Map<String, Object> getSysResponseFields(Map<String, ?> results) {
        Map<String, Object> outMap = new HashMap<>();
        for(String name : ModelService.SYS_RESPONSE_FIELDS) {
            if (results.containsKey(name)) {
                outMap.put(name, results.get(name));
            }
        }
        return outMap;
    }

    /**
     * SCIPIO: For every field name, checks if the serviceContext already contains its key; if it does, do nothing;
     * if it does not, attempt to fetch it from the source context, request, or session (in that order).
     * <p>
     * <strong>WARNING:</strong> In webapp request, the current request/session fallbacks are technically wrong
     * for some variables such as "locale" and "timeZone"; it is only done here for limited backward-compatibility
     * for bad old code in GroovyBaseScript.runService, but it may be removed from this method at a future date!
     * <p>
     * NOTE: If the source context contains the key but it is null, the null value is used, rather
     * than falling back to request; this is an implicit context-detect mechanism; in fact,
     * in most rendering cases, there will be no fallback on request.
     * <p>
     * NOTE: We ONLY use the request/session for default fields (userLogin, locale, timeZone),
     * as backward-compatibility IF their keys are not set in current context, because the renderer
     * should have set them.
     * <p>
     * Used mainly by {@link org.ofbiz.service.engine.GroovyBaseScript#runService(String, Map)} and
     * {@link org.ofbiz.webapp.ftl.RunServiceMethod#exec(List)}.
     * <p>
     * Added 2019-01-31.
     */
    public static void checkSetServiceContextDefaults(Map<String, Object> dstServiceContext, Collection<String> fieldNames,
            Map<String, Object> srcContext, HttpServletRequest srcRequestAndSession) {
        for(String fieldName : fieldNames) {
            //  Do NOT do this, because it prevents caller from setting null
            //if (inputMap[fieldName]) { // stock code
            if (dstServiceContext.containsKey(fieldName)) {
                continue;
            }
            // Do NOT do this, because 'parameters' map contains request parameters,
            // plus this can also triggers exceptions
            //inputMap[fieldName] = this.binding.getVariable('parameters')[fieldName];         // stock code
            if (srcContext != null && srcContext.containsKey(fieldName)) {
                dstServiceContext.put(fieldName, srcContext.get(fieldName));
                continue;
            }
            if (srcRequestAndSession == null) {
                continue;
            }
            // NOTE: For servlet API request's and session's getAttribute(String), null is equivalent to missing key.
            Object value = srcRequestAndSession.getAttribute(fieldName);
            if (value != null) {
                dstServiceContext.put(fieldName, value);
                continue;
            }
            HttpSession currentSession = srcRequestAndSession.getSession(false);
            if (currentSession == null) {
                continue;
            }
            value = currentSession.getAttribute(fieldName);
            if (value != null) {
                dstServiceContext.put(fieldName, value);
                //continue;
            }
        }
    }
}

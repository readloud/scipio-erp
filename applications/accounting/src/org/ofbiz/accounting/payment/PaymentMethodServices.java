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
package org.ofbiz.accounting.payment;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.StringUtil;
import org.ofbiz.base.util.UtilDateTime;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilProperties;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.entity.Delegator;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.util.EntityQuery;
import org.ofbiz.entity.util.EntityUtil;
import org.ofbiz.order.finaccount.FinAccountHelper;
import org.ofbiz.security.Security;
import org.ofbiz.service.DispatchContext;
import org.ofbiz.service.GenericServiceException;
import org.ofbiz.service.LocalDispatcher;
import org.ofbiz.service.ModelService;
import org.ofbiz.service.ServiceUtil;

/**
 * Services for Payment maintenance
 */
public class PaymentMethodServices {

    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());
    public final static String resource = "AccountingUiLabels";
    // SCIPIO: Fix wrong error resource!
    //public static final String resourceError = "AccountingUiLabels";
    public static final String resourceError = "AccountingErrorUiLabels";

    /**
     * Deletes a PaymentMethod entity according to the parameters passed in the context
     * <b>security check</b>: userLogin partyId must equal paymentMethod partyId, or must have PAY_INFO_DELETE permission
     * @param ctx The DispatchContext that this service is operating in
     * @param context Map containing the input parameters
     * @return Map with the result of the service, the output parameters
     */
    public static Map<String, Object> deletePaymentMethod(DispatchContext ctx, Map<String, ? extends Object> context) {
        Map<String, Object> result = new HashMap<>();
        Delegator delegator = ctx.getDelegator();
        Security security = ctx.getSecurity();
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        Locale locale = (Locale) context.get("locale");

        Timestamp now = UtilDateTime.nowTimestamp();

        // never delete a PaymentMethod, just put a to date on the link to the party
        String paymentMethodId = (String) context.get("paymentMethodId");
        GenericValue paymentMethod = null;

        try {
            paymentMethod = EntityQuery.use(delegator).from("PaymentMethod").where("paymentMethodId", paymentMethodId).queryOne();
        } catch (GenericEntityException e) {
            Debug.logWarning(e.toString(), module);
            return ServiceUtil.returnError(UtilProperties.getMessage(resourceError,
                    "AccountingPaymentMethodCannotBeDeleted",
                    UtilMisc.toMap("errorString", e.getMessage()), locale));
        }

        if (paymentMethod == null) {
            return ServiceUtil.returnError(UtilProperties.getMessage(resourceError,
                    "AccountingPaymentMethodCannotBeDeleted",
                    UtilMisc.toMap("errorString", ""), locale));
        }

        // <b>security check</b>: userLogin partyId must equal paymentMethod partyId, or must have PAY_INFO_DELETE permission
        if (paymentMethod.get("partyId") == null || !paymentMethod.getString("partyId").equals(userLogin.getString("partyId"))) {
            if (!security.hasEntityPermission("PAY_INFO", "_DELETE", userLogin) && !security.hasEntityPermission("ACCOUNTING", "_DELETE", userLogin)) {
                return ServiceUtil.returnError(UtilProperties.getMessage(resourceError,
                        "AccountingPaymentMethodNoPermissionToDelete", locale));
            }
        }

        paymentMethod.set("thruDate", now);
        try {
            paymentMethod.store();
        } catch (GenericEntityException e) {
            Debug.logWarning(e.toString(), module);
            return ServiceUtil.returnError(UtilProperties.getMessage(resourceError,
                    "AccountingPaymentMethodCannotBeDeletedWriteFailure",
                    UtilMisc.toMap("errorString", e.getMessage()), locale));
        }

        result.put(ModelService.RESPONSE_MESSAGE, ModelService.RESPOND_SUCCESS);
        return result;
    }

    public static Map<String, Object> makeExpireDate(DispatchContext ctx, Map<String, ? extends Object> context) {
        Map<String, Object> result = new HashMap<>();
        String expMonth = (String) context.get("expMonth");
        String expYear = (String) context.get("expYear");

        StringBuilder expDate = new StringBuilder();
        expDate.append(expMonth);
        expDate.append("/");
        expDate.append(expYear);
        result.put("expireDate", expDate.toString());
        result.put(ModelService.RESPONSE_MESSAGE, ModelService.RESPOND_SUCCESS);
        return result;
    }

    /**
     * Creates CreditCard and PaymentMethod entities according to the parameters passed in the context
     * <b>security check</b>: userLogin partyId must equal partyId, or must have PAY_INFO_CREATE permission
     * @param ctx The DispatchContext that this service is operating in
     * @param context Map containing the input parameters
     * @return Map with the result of the service, the output parameters
     */
    public static Map<String, Object> createCreditCard(DispatchContext ctx, Map<String, Object> context) {
        Map<String, Object> result = new HashMap<>();
        Delegator delegator = ctx.getDelegator();
        Security security = ctx.getSecurity();
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        Locale locale = (Locale) context.get("locale");

        Timestamp now = UtilDateTime.nowTimestamp();

        String partyId = ServiceUtil.getPartyIdCheckSecurity(userLogin, security, context, result, "PAY_INFO", "_CREATE", "ACCOUNTING", "_CREATE");

        if (result.size() > 0) {
            return result;
        }

        // do some more complicated/critical validation...
        List<String> messages = new LinkedList<>();

        // first remove all spaces from the credit card number
        context.put("cardNumber", StringUtil.removeSpaces((String) context.get("cardNumber")));
        if (!UtilValidate.isCardMatch((String) context.get("cardType"), (String) context.get("cardNumber"))) {
            messages.add(
                UtilProperties.getMessage(resource, "AccountingCreditCardNumberInvalid",
                    UtilMisc.toMap("cardType", (String) context.get("cardType"),
                                   "validCardType", UtilValidate.getCardType((String) context.get("cardNumber"))), locale));
        }

        if (!UtilValidate.isDateAfterToday((String) context.get("expireDate"))) {
            messages.add(
                UtilProperties.getMessage(resource, "AccountingCreditCardExpireDateBeforeToday",
                    UtilMisc.toMap("expireDate", (String) context.get("expireDate")), locale));
        }

        if (messages.size() > 0) {
            return ServiceUtil.returnError(messages);
        }

        List<GenericValue> toBeStored = new LinkedList<>();
        GenericValue newPm = delegator.makeValue("PaymentMethod");

        toBeStored.add(newPm);
        GenericValue newCc = delegator.makeValue("CreditCard");

        toBeStored.add(newCc);

        String newPmId = (String) context.get("paymentMethodId");
        if (UtilValidate.isEmpty(newPmId)) {
            try {
                newPmId = delegator.getNextSeqId("PaymentMethod");
            } catch (IllegalArgumentException e) {
                return ServiceUtil.returnError(UtilProperties.getMessage(resource,
                        "AccountingCreditCardCreateIdGenerationFailure", locale));
            }
        }

        newPm.set("partyId", partyId);
        newPm.set("description",context.get("description"));
        newPm.set("fromDate", (context.get("fromDate") != null ? context.get("fromDate") : now));
        newPm.set("thruDate", context.get("thruDate"));
        newCc.set("companyNameOnCard", context.get("companyNameOnCard"));
        newCc.set("titleOnCard", context.get("titleOnCard"));
        newCc.set("firstNameOnCard", context.get("firstNameOnCard"));
        newCc.set("middleNameOnCard", context.get("middleNameOnCard"));
        newCc.set("lastNameOnCard", context.get("lastNameOnCard"));
        newCc.set("suffixOnCard", context.get("suffixOnCard"));
        newCc.set("cardType", context.get("cardType"));
        newCc.set("cardNumber", context.get("cardNumber"));
        newCc.set("expireDate", context.get("expireDate"));

        newPm.set("paymentMethodId", newPmId);
        newPm.set("paymentMethodTypeId", "CREDIT_CARD");
        newCc.set("paymentMethodId", newPmId);

        GenericValue newPartyContactMechPurpose = null;
        String contactMechId = (String) context.get("contactMechId");

        if (UtilValidate.isNotEmpty(contactMechId) && !"_NEW_".equals(contactMechId)) {
            // set the contactMechId on the credit card
            newCc.set("contactMechId", context.get("contactMechId"));
            // add a PartyContactMechPurpose of BILLING_LOCATION if necessary
            String contactMechPurposeTypeId = "BILLING_LOCATION";

            // SCIPIO: 2017-10-10: refactored
            newPartyContactMechPurpose = checkMakePartyContactMechPurpose(delegator, partyId, contactMechId, contactMechPurposeTypeId, now);
        }

        if (newPartyContactMechPurpose != null) {
            toBeStored.add(newPartyContactMechPurpose);
        }

        try {
            delegator.storeAll(toBeStored);
        } catch (GenericEntityException e) {
            Debug.logWarning(e.getMessage(), module);
            return ServiceUtil.returnError(UtilProperties.getMessage(resource,
                    "AccountingCreditCardCreateWriteFailure", locale) + e.getMessage());
        }

        result.put("paymentMethodId", newCc.getString("paymentMethodId"));
        result.put(ModelService.RESPONSE_MESSAGE, ModelService.RESPOND_SUCCESS);
        return result;
    }

    /**
     * Updates CreditCard and PaymentMethod entities according to the parameters passed in the context
     * <b>security check</b>: userLogin partyId must equal partyId, or must have PAY_INFO_UPDATE permission
     * @param ctx The DispatchContext that this service is operating in
     * @param context Map containing the input parameters
     * @return Map with the result of the service, the output parameters
     */
    public static Map<String, Object> updateCreditCard(DispatchContext ctx, Map<String, Object> context) {
        Map<String, Object> result = new HashMap<>();
        Delegator delegator = ctx.getDelegator();
        Security security = ctx.getSecurity();
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        Locale locale = (Locale) context.get("locale");

        Timestamp now = UtilDateTime.nowTimestamp();

        String partyId = ServiceUtil.getPartyIdCheckSecurity(userLogin, security, context, result, "PAY_INFO", "_UPDATE", "ACCOUNTING", "_UPDATE");

        if (result.size() > 0) {
            return result;
        }

        List<GenericValue> toBeStored = new LinkedList<>();
        boolean isModified = false;

        GenericValue paymentMethod = null;
        GenericValue newPm = null;
        GenericValue creditCard = null;
        GenericValue newCc = null;
        String paymentMethodId = (String) context.get("paymentMethodId");

        try {
            creditCard = EntityQuery.use(delegator).from("CreditCard").where("paymentMethodId", paymentMethodId).queryOne();
            paymentMethod = EntityQuery.use(delegator).from("PaymentMethod").where("paymentMethodId", paymentMethodId).queryOne();
        } catch (GenericEntityException e) {
            Debug.logWarning(e.getMessage(), module);
            return ServiceUtil.returnError(UtilProperties.getMessage(resource,
                    "AccountingCreditCardUpdateReadFailure", locale) + e.getMessage());
        }

        if (creditCard == null || paymentMethod == null) {
            return ServiceUtil.returnError(UtilProperties.getMessage(resource,
                    "AccountingCreditCardUpdateWithPaymentMethodId", locale) + paymentMethodId);
        }
        if (!paymentMethod.getString("partyId").equals(partyId) && !security.hasEntityPermission("PAY_INFO", "_UPDATE", userLogin) && !security.hasEntityPermission("ACCOUNTING", "_UPDATE", userLogin)) {
            return ServiceUtil.returnError(UtilProperties.getMessage(resource,
                    "AccountingCreditCardUpdateWithoutPermission", UtilMisc.toMap("partyId", partyId,
                            "paymentMethodId", paymentMethodId), locale));
        }

        // do some more complicated/critical validation...
        List<String> messages = new LinkedList<>();

        // first remove all spaces from the credit card number
        String updatedCardNumber = StringUtil.removeSpaces((String) context.get("cardNumber"));
        /* SCIPIO: This test was too strict; prevented the UI from deciding masking behavior
         * and needlessly accepted mask characters in different positions as numbers
        if (updatedCardNumber.startsWith("*")) {
            // get the masked card number from the db
            String origCardNumber = creditCard.getString("cardNumber");
            int cardLength = origCardNumber.length() - 4;

            // use builder for better performance
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < cardLength; i++) {
                builder.append("*");
            }
            String origMaskedNumber = builder.append(origCardNumber.substring(cardLength)).toString();

            // compare the two masked numbers
            if (updatedCardNumber.equals(origMaskedNumber)) {
                updatedCardNumber = origCardNumber;
            }
        }
        */
        Character maskChar = PaymentWorker.getNumberMaskChar(delegator);
        if (maskChar != null && updatedCardNumber.indexOf(maskChar) >= 0) {
            // get the masked card number from the db
            String origCardNumber = creditCard.getString("cardNumber");
            // compare the two masked numbers
            if (StringUtil.matchesMaskedAny(origCardNumber, updatedCardNumber, maskChar)) {
                updatedCardNumber = origCardNumber;
            }
        }
        context.put("cardNumber", updatedCardNumber);

        if (!UtilValidate.isCardMatch((String) context.get("cardType"), (String) context.get("cardNumber"))) {
            messages.add(
                UtilProperties.getMessage(resource, "AccountingCreditCardNumberInvalid",
                    UtilMisc.toMap("cardType", (String) context.get("cardType"),
                                   "validCardType", UtilValidate.getCardType((String) context.get("cardNumber"))), locale));
        }

        if (!UtilValidate.isDateAfterToday((String) context.get("expireDate"))) {
            messages.add(
                UtilProperties.getMessage(resource, "AccountingCreditCardExpireDateBeforeToday",
                    UtilMisc.toMap("expireDate", (String) context.get("expireDate")), locale));
        }

        if (messages.size() > 0) {
            return ServiceUtil.returnError(messages);
        }

        newPm = GenericValue.create(paymentMethod);
        toBeStored.add(newPm);
        newCc = GenericValue.create(creditCard);
        toBeStored.add(newCc);

        String newPmId = null;
        try {
            newPmId = delegator.getNextSeqId("PaymentMethod");
        } catch (IllegalArgumentException e) {
            return ServiceUtil.returnError(UtilProperties.getMessage(resource,
                    "AccountingCreditCardUpdateIdGenerationFailure", locale));

        }

        newPm.set("partyId", partyId);
        newPm.set("fromDate", context.get("fromDate"), false);
        newPm.set("description",context.get("description"));
        // The following check is needed to avoid to reactivate an expired pm
        if (newPm.get("thruDate") == null) {
            newPm.set("thruDate", context.get("thruDate"));
        }
        newCc.set("companyNameOnCard", context.get("companyNameOnCard"));
        newCc.set("titleOnCard", context.get("titleOnCard"));
        newCc.set("firstNameOnCard", context.get("firstNameOnCard"));
        newCc.set("middleNameOnCard", context.get("middleNameOnCard"));
        newCc.set("lastNameOnCard", context.get("lastNameOnCard"));
        newCc.set("suffixOnCard", context.get("suffixOnCard"));

        newCc.set("cardType", context.get("cardType"));
        newCc.set("cardNumber", context.get("cardNumber"));
        newCc.set("expireDate", context.get("expireDate"));

        GenericValue newPartyContactMechPurpose = null;
        String contactMechId = (String) context.get("contactMechId");

        if (UtilValidate.isNotEmpty(contactMechId) && !"_NEW_".equals(contactMechId)) {
            // set the contactMechId on the credit card
            newCc.set("contactMechId", contactMechId);
        }

        if (!newCc.equals(creditCard) || !newPm.equals(paymentMethod)) {
            newPm.set("paymentMethodId", newPmId);
            newCc.set("paymentMethodId", newPmId);

            newPm.set("fromDate", (context.get("fromDate") != null ? context.get("fromDate") : now));
            isModified = true;
        }

        if (UtilValidate.isNotEmpty(contactMechId) && !"_NEW_".equals(contactMechId)) {

            // add a PartyContactMechPurpose of BILLING_LOCATION if necessary
            String contactMechPurposeTypeId = "BILLING_LOCATION";

            // SCIPIO: 2017-10-10: refactored
            newPartyContactMechPurpose = checkMakePartyContactMechPurpose(delegator, partyId, contactMechId, contactMechPurposeTypeId, now);
        }

        if (isModified) {
            if (newPartyContactMechPurpose != null) {
                toBeStored.add(newPartyContactMechPurpose);
            }

            // set thru date on old paymentMethod
            paymentMethod.set("thruDate", now);
            toBeStored.add(paymentMethod);

            try {
                delegator.storeAll(toBeStored);
            } catch (GenericEntityException e) {
                Debug.logWarning(e.getMessage(), module);
                return ServiceUtil.returnError(UtilProperties.getMessage(resource,
                        "AccountingCreditCardUpdateWriteFailure", locale) + e.getMessage());
            }
        } else {
            result.put("paymentMethodId", paymentMethodId);
            result.put("oldPaymentMethodId", paymentMethodId);
            result.put(ModelService.RESPONSE_MESSAGE, ModelService.RESPOND_SUCCESS);
            if (contactMechId == null || !"_NEW_".equals(contactMechId)) {
                result.put(ModelService.SUCCESS_MESSAGE, UtilProperties.getMessage(resource,
                        "AccountingNoChangesMadeNotUpdatingCreditCard", locale));
            }

            return result;
        }

        result.put("oldPaymentMethodId", paymentMethodId);
        result.put("paymentMethodId", newCc.getString("paymentMethodId"));

        result.put(ModelService.RESPONSE_MESSAGE, ModelService.RESPOND_SUCCESS);
        return result;
    }

    public static Map<String, Object> clearCreditCardData(DispatchContext dctx, Map<String, ? extends Object> context) {
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        String paymentMethodId = (String) context.get("paymentMethodId");

        // get the cc object
        Delegator delegator = dctx.getDelegator();
        GenericValue creditCard;
        try {
            creditCard = EntityQuery.use(delegator).from("CreditCard").where("paymentMethodId", paymentMethodId).queryOne();
        } catch (GenericEntityException e) {
            Debug.logError(e, module);
            return ServiceUtil.returnError(e.getMessage());
        }

        // clear the info and store it
        creditCard.set("cardNumber", "0000000000000000"); // set so it doesn't blow up in UIs
        creditCard.set("expireDate", "01/1970"); // same here
        try {
            delegator.store(creditCard);
        } catch (GenericEntityException e) {
            Debug.logError(e, module);
            return ServiceUtil.returnError(e.getMessage());
        }

        // expire the payment method
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Map<String, Object> expireCtx = UtilMisc.<String, Object>toMap("userLogin", userLogin,
                "paymentMethodId", paymentMethodId);
        Map<String, Object> expireResp;
        try {
            expireResp = dispatcher.runSync("deletePaymentMethod", expireCtx);
        } catch (GenericServiceException e) {
            Debug.logError(e, module);
            return ServiceUtil.returnError(e.getMessage());
        }
        if (ServiceUtil.isError(expireResp)) {
            return ServiceUtil.returnError(ServiceUtil.getErrorMessage(expireResp));
        }

        return ServiceUtil.returnSuccess();
    }

    public static Map<String, Object> createGiftCard(DispatchContext ctx, Map<String, ? extends Object> context) {
        Map<String, Object> result = new HashMap<>();
        Delegator delegator = ctx.getDelegator();
        Security security = ctx.getSecurity();
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        Locale locale = (Locale) context.get("locale");

        Timestamp now = UtilDateTime.nowTimestamp();

        String partyId = ServiceUtil.getPartyIdCheckSecurity(userLogin, security, context, result, "PAY_INFO", "_CREATE", "ACCOUNTING", "_CREATE");

        if (result.size() > 0) {
            return result;
        }

        // SCIPIO: 2019-03-12: Deny any modified masked numbers (causes issues for verifyGiftCard and other places)
        String cardNumber = StringUtil.removeSpaces((String) context.get("cardNumber"));
        Character maskChar = PaymentWorker.getNumberMaskChar(delegator);
        if (maskChar != null && cardNumber.indexOf(maskChar) >= 0) {
            return ServiceUtil.returnError(UtilProperties.getMessage("AccountingUiLabels", "AccountingCardNumberIncorrect", locale));
        }

        List<GenericValue> toBeStored = new ArrayList<>(); // SCIPIO: Switched to ArrayList
        GenericValue newPm = delegator.makeValue("PaymentMethod");
        toBeStored.add(newPm);
        GenericValue newGc = delegator.makeValue("GiftCard");
        toBeStored.add(newGc);

        String newPmId = (String) context.get("paymentMethodId");
        if (UtilValidate.isEmpty(newPmId)) {
            try {
                newPmId = delegator.getNextSeqId("PaymentMethod");
            } catch (IllegalArgumentException e) {
                return ServiceUtil.returnError(UtilProperties.getMessage(resourceError,
                        "AccountingGiftCardCannotBeCreated", locale));
            }
        }

        newPm.set("partyId", partyId);
        newPm.set("fromDate", (context.get("fromDate") != null ? context.get("fromDate") : now));
        newPm.set("thruDate", context.get("thruDate"));
        newPm.set("description",context.get("description"));

        newGc.set("cardNumber", cardNumber); // SCIPIO: Use number with trimmed spaces: context.get("cardNumber")
        newGc.set("pinNumber", context.get("pinNumber"));
        newGc.set("expireDate", context.get("expireDate"));

        newPm.set("paymentMethodId", newPmId);
        newPm.set("paymentMethodTypeId", "GIFT_CARD");
        newGc.set("paymentMethodId", newPmId);

        try {
            delegator.storeAll(toBeStored);
        } catch (GenericEntityException e) {
            Debug.logWarning(e.getMessage(), module);
            return ServiceUtil.returnError(UtilProperties.getMessage(resourceError,
                    "AccountingGiftCardCannotBeCreatedWriteFailure",
                    UtilMisc.toMap("errorString", e.getMessage()), locale));
        }

        result.put("paymentMethodId", newGc.getString("paymentMethodId"));
        result.put(ModelService.RESPONSE_MESSAGE, ModelService.RESPOND_SUCCESS);
        return result;
    }

    public static Map<String, Object> updateGiftCard(DispatchContext ctx, Map<String, Object> context) {
        Map<String, Object> result = new HashMap<>();
        Delegator delegator = ctx.getDelegator();
        Security security = ctx.getSecurity();
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        Locale locale = (Locale) context.get("locale");

        Timestamp now = UtilDateTime.nowTimestamp();

        String partyId = ServiceUtil.getPartyIdCheckSecurity(userLogin, security, context, result, "PAY_INFO", "_UPDATE", "ACCOUNTING", "_UPDATE");

        if (result.size() > 0) {
            return result;
        }

        List<GenericValue> toBeStored = new LinkedList<>();
        boolean isModified = false;

        GenericValue paymentMethod = null;
        GenericValue newPm = null;
        GenericValue giftCard = null;
        GenericValue newGc = null;
        String paymentMethodId = (String) context.get("paymentMethodId");

        try {
            giftCard = EntityQuery.use(delegator).from("GiftCard").where("paymentMethodId", paymentMethodId).queryOne();
            paymentMethod = EntityQuery.use(delegator).from("PaymentMethod").where("paymentMethodId", paymentMethodId).queryOne();
        } catch (GenericEntityException e) {
            Debug.logWarning(e.getMessage(), module);
            return ServiceUtil.returnError(UtilProperties.getMessage(resourceError,
                    "AccountingGiftCardCannotBeUpdated",
                    UtilMisc.toMap("errorString", e.getMessage()), locale));
        }

        if (giftCard == null || paymentMethod == null) {
            return ServiceUtil.returnError(UtilProperties.getMessage(resourceError,
                    "AccountingGiftCardCannotBeUpdated",
                    UtilMisc.toMap("errorString", paymentMethodId), locale));
        }
        if (!paymentMethod.getString("partyId").equals(partyId) && !security.hasEntityPermission("PAY_INFO", "_UPDATE", userLogin) && !security.hasEntityPermission("ACCOUNTING", "_UPDATE", userLogin)) {
            return ServiceUtil.returnError(UtilProperties.getMessage(resourceError,
                    "AccountingGiftCardPartyNotAuthorized",
                    UtilMisc.toMap("partyId", partyId, "paymentMethodId", paymentMethodId), locale));
        }

        // card number (masked)
        String cardNumber = StringUtil.removeSpaces((String) context.get("cardNumber"));
        /* SCIPIO: This test was too strict; prevented the UI from deciding masking behavior
         * and needlessly accepted mask characters in different positions as numbers
        if (cardNumber.startsWith("*")) {
            // get the masked card number from the db
            String origCardNumber = giftCard.getString("cardNumber");
            StringBuilder origMaskedNumber = new StringBuilder("");
            int cardLength = origCardNumber.length() - 4;
            if (cardLength > 0) {
                for (int i = 0; i < cardLength; i++) {
                    origMaskedNumber.append("*");
                }
                origMaskedNumber.append(origCardNumber.substring(cardLength));
            } else {
                origMaskedNumber.append(origCardNumber);
            }

            // compare the two masked numbers
            if (cardNumber.equals(origMaskedNumber.toString())) {
                cardNumber = origCardNumber;
            }
        }
        */
        Character maskChar = PaymentWorker.getNumberMaskChar(delegator);
        if (maskChar != null && cardNumber.indexOf(maskChar) >= 0) {
            // get the masked card number from the db
            String origCardNumber = giftCard.getString("cardNumber");
            // compare the two masked numbers
            if (StringUtil.matchesMaskedAny(origCardNumber, cardNumber, maskChar)) {
                cardNumber = origCardNumber;
            } else {
                // SCIPIO: 2019-03-12: Deny any modified masked numbers (causes issues for verifyGiftCard and other places)
                return ServiceUtil.returnError(UtilProperties.getMessage("AccountingUiLabels", "AccountingCardNumberIncorrect", locale));
            }
        }
        context.put("cardNumber", cardNumber);

        newPm = GenericValue.create(paymentMethod);
        toBeStored.add(newPm);
        newGc = GenericValue.create(giftCard);
        toBeStored.add(newGc);

        String newPmId = null;
        try {
            newPmId = delegator.getNextSeqId("PaymentMethod");
        } catch (IllegalArgumentException e) {
            return ServiceUtil.returnError(UtilProperties.getMessage(resourceError,
                    "AccountingGiftCardCannotBeCreated", locale));
        }

        newPm.set("partyId", partyId);
        newPm.set("fromDate", context.get("fromDate"), false);
        newPm.set("thruDate", context.get("thruDate"));
        newPm.set("description",context.get("description"));

        newGc.set("cardNumber", context.get("cardNumber"));
        newGc.set("pinNumber", context.get("pinNumber"));
        newGc.set("expireDate", context.get("expireDate"));

        if (!newGc.equals(giftCard) || !newPm.equals(paymentMethod)) {
            newPm.set("paymentMethodId", newPmId);
            newGc.set("paymentMethodId", newPmId);

            newPm.set("fromDate", (context.get("fromDate") != null ? context.get("fromDate") : now));
            isModified = true;
        }

        if (isModified) {
            // set thru date on old paymentMethod
            paymentMethod.set("thruDate", now);
            toBeStored.add(paymentMethod);

            try {
                delegator.storeAll(toBeStored);
            } catch (GenericEntityException e) {
                Debug.logWarning(e.getMessage(), module);
                return ServiceUtil.returnError(UtilProperties.getMessage(resourceError,
                        "AccountingGiftCardCannotBeUpdated",
                        UtilMisc.toMap("errorString", e.getMessage()), locale));
            }
        } else {
            result.put("paymentMethodId", paymentMethodId);
            result.put("oldPaymentMethodId", paymentMethodId);
            result.put(ModelService.RESPONSE_MESSAGE, ModelService.RESPOND_SUCCESS);
            result.put(ModelService.SUCCESS_MESSAGE, UtilProperties.getMessage(resource,
                    "AccountingNoChangesMadeNotUpdatingGiftCard", locale));

            return result;
        }

        result.put("paymentMethodId", newGc.getString("paymentMethodId"));
        result.put("oldPaymentMethodId", paymentMethodId);
        result.put(ModelService.RESPONSE_MESSAGE, ModelService.RESPOND_SUCCESS);
        return result;
    }
    
    /**
     * SCIPIO: Verifies if the given gift card number and pin are valid (best-effort)
     * (based on: {@link org.ofbiz.order.shoppingcart.CheckOutHelper#checkGiftCard}).
     */
    public static Map<String, Object> verifyGiftCard(DispatchContext dctx, Map<String, Object> context, boolean ignoreMasked) {
        final String resource_error = "OrderErrorUiLabels";
        Delegator delegator = dctx.getDelegator();
        Locale locale = (Locale) context.get("locale");
        List<String> errorMessages = new ArrayList<>();
        String errMsg = null;

        String gcNum = StringUtil.removeSpaces((String) context.get("cardNumber"));
        String gcPin = (String) context.get("pinNumber");
        String productStoreId = (String) context.get("productStoreId");

        boolean maskedNum = false;
        if (ignoreMasked) {
            Character maskChar = PaymentWorker.getNumberMaskChar(delegator);
            maskedNum = (maskChar != null && gcNum != null && gcNum.indexOf(maskChar) >= 0);
        }
        
        if (UtilValidate.isEmpty(gcNum)) {
            errMsg = UtilProperties.getMessage(resource_error,"checkhelper.enter_gift_card_number", locale);
            errorMessages.add(errMsg);
        }
        if (isPinRequiredForGC(delegator, productStoreId)) {
            //  if a PIN is required, make sure the PIN is valid
            if (UtilValidate.isEmpty(gcPin)) {
                errMsg = UtilProperties.getMessage(resource_error,"checkhelper.enter_gift_card_pin_number", locale);
                errorMessages.add(errMsg);
            }
        }
        // See if we should validate gift card code against FinAccount's accountCode
        if (isValidateGCFinAccount(delegator, productStoreId)) {
            try {
                // No PIN required - validate gift card number against account code
                // SCIPIO: Don't bother if masked card number (see createGiftCard/updateGiftCard)
                // SCIPIO: TODO: REVIEW: technically we should probably use paymentMethodId to lookup GiftCard the same way updateGiftCard does,
                // so we can deny any changes to the gift card number; however, in practice, this will precede updateGiftCard or similar
                // calls, so it doesn't add much exception make the lookup slower...
                if (!maskedNum && !isPinRequiredForGC(delegator, productStoreId)) { 
                    GenericValue finAccount = FinAccountHelper.getFinAccountFromCode(gcNum, delegator);
                    if (finAccount == null) {
                        errMsg = UtilProperties.getMessage(resource_error,"checkhelper.gift_card_does_not_exist", locale);
                        errorMessages.add(errMsg);
                    } else if ((finAccount.getBigDecimal("availableBalance") == null) ||
                            !((finAccount.getBigDecimal("availableBalance")).compareTo(FinAccountHelper.ZERO) > 0)) {
                        // if account's available balance (including authorizations) is not greater than zero, then return an error
                        errMsg = UtilProperties.getMessage(resource_error,"checkhelper.gift_card_has_no_value", locale);
                        errorMessages.add(errMsg);
                    }
                }
                // TODO: else case when pin is required - we should validate gcNum and gcPin
            } catch (GenericEntityException ex) {
                errorMessages.add(ex.getMessage());
            }
        }

        // see whether we need to return an error or not
        return (errorMessages.size() > 0) ? ServiceUtil.returnError(errorMessages) : ServiceUtil.returnSuccess();
    }

    /**
     * SCIPIO: Verifies if the given gift card number and pin are valid (best-effort)
     * (based on: {@link org.ofbiz.order.shoppingcart.CheckOutHelper#checkGiftCard}).
     */
    public static Map<String, Object> verifyGiftCard(DispatchContext dctx, Map<String, Object> context) {
        return verifyGiftCard(dctx, context, false);
    }

    /**
    * SCIPIO: Verifies if the given gift card number and pin are valid (best-effort)
    * (based on: {@link org.ofbiz.order.shoppingcart.CheckOutHelper#checkGiftCard}),
    * but ignores cardNumber if it is in masked format (************XXXX)
    */
    public static Map<String, Object> verifyGiftCardIgnoreMasked(DispatchContext dctx, Map<String, Object> context) {
        return verifyGiftCard(dctx, context, true);
    }
    
    private static GenericValue getGiftCertSettingFromStore(Delegator delegator, String productStoreId) throws GenericEntityException { // SCIPIO: Copied from ShoppingCart
        return EntityQuery.use(delegator).from("ProductStoreFinActSetting")
                .where("productStoreId", productStoreId, "finAccountTypeId", FinAccountHelper.giftCertFinAccountTypeId).cache().queryOne();
    }

    private static boolean isPinRequiredForGC(Delegator delegator, String productStore) { // SCIPIO: Copied from ShoppingCart
        try {
            GenericValue giftCertSettings = getGiftCertSettingFromStore(delegator, productStore);
            if (giftCertSettings != null) {
                if ("Y".equals(giftCertSettings.getString("requirePinCode"))) {
                    return true;
                }
                return false;
            }
            Debug.logWarning("No product store gift certificate settings found for store [" + productStore + "]",
                    module);
            return true;
        } catch (GenericEntityException ex) {
            Debug.logError("Error checking if store requires pin number for GC: " + ex.getMessage(), module);
            return true;
        }
    }

    private static boolean isValidateGCFinAccount(Delegator delegator, String productStore) { // SCIPIO: Copied from ShoppingCart
        try {
            GenericValue giftCertSettings = getGiftCertSettingFromStore(delegator, productStore);
            if (giftCertSettings != null) {
                if ("Y".equals(giftCertSettings.getString("validateGCFinAcct"))) {
                    return true;
                }
                return false;
            }
            Debug.logWarning("No product store gift certificate settings found for store [" + productStore + "]",
                    module);
            return false;
        } catch (GenericEntityException ex) {
            Debug.logError("Error checking if store requires pin number for GC: " + ex.getMessage(), module);
            return false;
        }
    }

    /**
     * Creates EftAccount and PaymentMethod entities according to the parameters passed in the context
     * <b>security check</b>: userLogin partyId must equal partyId, or must have PAY_INFO_CREATE permission
     * @param ctx The DispatchContext that this service is operating in
     * @param context Map containing the input parameters
     * @return Map with the result of the service, the output parameters
     */
    public static Map<String, Object> createEftAccount(DispatchContext ctx, Map<String, ? extends Object> context) {
        Map<String, Object> result = new HashMap<>();
        Delegator delegator = ctx.getDelegator();
        Security security = ctx.getSecurity();
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        Locale locale = (Locale) context.get("locale");

        Timestamp now = UtilDateTime.nowTimestamp();

        String partyId = ServiceUtil.getPartyIdCheckSecurity(userLogin, security, context, result, "PAY_INFO", "_CREATE", "ACCOUNTING", "_CREATE");

        if (result.size() > 0) {
            return result;
        }

        List<GenericValue> toBeStored = new LinkedList<>();
        GenericValue newPm = delegator.makeValue("PaymentMethod");

        toBeStored.add(newPm);
        GenericValue newEa = delegator.makeValue("EftAccount");

        toBeStored.add(newEa);

        String newPmId = (String) context.get("paymentMethodId");
        if (UtilValidate.isEmpty(newPmId)) {
            try {
                newPmId = delegator.getNextSeqId("PaymentMethod");
            } catch (IllegalArgumentException e) {
                return ServiceUtil.returnError(UtilProperties.getMessage(resourceError,
                        "AccountingEftAccountCannotBeCreated", locale));
            }
        }

        newPm.set("partyId", partyId);
        newPm.set("fromDate", (context.get("fromDate") != null ? context.get("fromDate") : now));
        newPm.set("thruDate", context.get("thruDate"));
        newPm.set("description",context.get("description"));
        newEa.set("bankName", context.get("bankName"));
        newEa.set("routingNumber", context.get("routingNumber"));
        newEa.set("accountType", context.get("accountType"));
        newEa.set("accountNumber", context.get("accountNumber"));
        newEa.set("nameOnAccount", context.get("nameOnAccount"));
        newEa.set("companyNameOnAccount", context.get("companyNameOnAccount"));
        // SCIPIO: Only set this below if not _NEW_
        //newEa.set("contactMechId", context.get("contactMechId"));

        newPm.set("paymentMethodId", newPmId);
        newPm.set("paymentMethodTypeId", "EFT_ACCOUNT");
        newEa.set("paymentMethodId", newPmId);

        GenericValue newPartyContactMechPurpose = null;
        String contactMechId = (String) context.get("contactMechId");

        // SCIPIO: Ignore if _NEW_
        //if (UtilValidate.isNotEmpty(contactMechId)) {
        if (UtilValidate.isNotEmpty(contactMechId) && !contactMechId.equals("_NEW_")) {
            // SCIPIO: Only set this if not _NEW_
            newEa.set("contactMechId", context.get("contactMechId"));

            // add a PartyContactMechPurpose of BILLING_LOCATION if necessary
            String contactMechPurposeTypeId = "BILLING_LOCATION";

            // SCIPIO: 2017-10-10: refactored
            newPartyContactMechPurpose = checkMakePartyContactMechPurpose(delegator, partyId, contactMechId, contactMechPurposeTypeId, now);
        }

        if (newPartyContactMechPurpose != null) {
            toBeStored.add(newPartyContactMechPurpose);
        }

        try {
            delegator.storeAll(toBeStored);
        } catch (GenericEntityException e) {
            Debug.logWarning(e.getMessage(), module);
            return ServiceUtil.returnError(UtilProperties.getMessage(resourceError,
                    "AccountingEftAccountCannotBeCreatedWriteFailure",
                    UtilMisc.toMap("errorString", e.getMessage()), locale));
        }

        result.put("paymentMethodId", newEa.getString("paymentMethodId"));
        result.put(ModelService.RESPONSE_MESSAGE, ModelService.RESPOND_SUCCESS);
        return result;
    }

    /**
     * Updates EftAccount and PaymentMethod entities according to the parameters passed in the context
     * <b>security check</b>: userLogin partyId must equal partyId, or must have PAY_INFO_UPDATE permission
     * @param ctx The DispatchContext that this service is operating in
     * @param context Map containing the input parameters
     * @return Map with the result of the service, the output parameters
     */
    public static Map<String, Object> updateEftAccount(DispatchContext ctx, Map<String, ? extends Object> context) {
        Map<String, Object> result = new HashMap<>();
        Delegator delegator = ctx.getDelegator();
        Security security = ctx.getSecurity();
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        Locale locale = (Locale) context.get("locale");

        Timestamp now = UtilDateTime.nowTimestamp();

        String partyId = ServiceUtil.getPartyIdCheckSecurity(userLogin, security, context, result, "PAY_INFO", "_UPDATE", "ACCOUNTING", "_UPDATE");

        if (result.size() > 0) {
            return result;
        }

        List<GenericValue> toBeStored = new LinkedList<>();
        boolean isModified = false;

        GenericValue paymentMethod = null;
        GenericValue newPm = null;
        GenericValue eftAccount = null;
        GenericValue newEa = null;
        String paymentMethodId = (String) context.get("paymentMethodId");

        try {
            eftAccount = EntityQuery.use(delegator).from("EftAccount").where("paymentMethodId", paymentMethodId).queryOne();
            paymentMethod =
                EntityQuery.use(delegator).from("PaymentMethod").where("paymentMethodId", paymentMethodId).queryOne();
        } catch (GenericEntityException e) {
            Debug.logWarning(e.getMessage(), module);
            return ServiceUtil.returnError(UtilProperties.getMessage(resourceError,
                    "AccountingEftAccountCannotBeUpdatedReadFailure",
                    UtilMisc.toMap("errorString", e.getMessage()), locale));
        }

        if (eftAccount == null || paymentMethod == null) {
            return ServiceUtil.returnError(UtilProperties.getMessage(resourceError,
                    "AccountingEftAccountCannotBeUpdated",
                    UtilMisc.toMap("errorString", paymentMethodId), locale));
        }
        if (!paymentMethod.getString("partyId").equals(partyId) && !security.hasEntityPermission("PAY_INFO", "_UPDATE", userLogin) && !security.hasEntityPermission("ACCOUNTING", "_UPDATE", userLogin)) {
            return ServiceUtil.returnError(UtilProperties.getMessage(resourceError,
                    "AccountingEftAccountCannotBeUpdated",
                    UtilMisc.toMap("partyId", partyId, "paymentMethodId", paymentMethodId), locale));
        }

        newPm = GenericValue.create(paymentMethod);
        toBeStored.add(newPm);
        newEa = GenericValue.create(eftAccount);
        toBeStored.add(newEa);

        String newPmId = null;
        try {
            newPmId = delegator.getNextSeqId("PaymentMethod");
        } catch (IllegalArgumentException e) {
            return ServiceUtil.returnError(UtilProperties.getMessage(resourceError,
                    "AccountingEftAccountCannotBeCreated", locale));
        }

        newPm.set("partyId", partyId);
        newPm.set("fromDate", context.get("fromDate"), false);
        newPm.set("thruDate", context.get("thruDate"));
        newPm.set("description",context.get("description"));
        newEa.set("bankName", context.get("bankName"));
        newEa.set("routingNumber", context.get("routingNumber"));
        newEa.set("accountType", context.get("accountType"));
        newEa.set("accountNumber", context.get("accountNumber"));
        newEa.set("nameOnAccount", context.get("nameOnAccount"));
        newEa.set("companyNameOnAccount", context.get("companyNameOnAccount"));
        newEa.set("contactMechId", context.get("contactMechId"));

        if (!newEa.equals(eftAccount) || !newPm.equals(paymentMethod)) {
            newPm.set("paymentMethodId", newPmId);
            newEa.set("paymentMethodId", newPmId);
            newPm.set("fromDate", (context.get("fromDate") != null ? context.get("fromDate") : now));
            isModified = true;
        }

        GenericValue newPartyContactMechPurpose = null;
        String contactMechId = (String) context.get("contactMechId");

        if (UtilValidate.isNotEmpty(contactMechId)) {
            // add a PartyContactMechPurpose of BILLING_LOCATION if necessary
            String contactMechPurposeTypeId = "BILLING_LOCATION";

            // SCIPIO: 2017-10-10: refactored
            newPartyContactMechPurpose = checkMakePartyContactMechPurpose(delegator, partyId, contactMechId, contactMechPurposeTypeId, now);
        }

        if (isModified) {
            // Debug.logInfo("yes, is modified", module);
            if (newPartyContactMechPurpose != null) {
                toBeStored.add(newPartyContactMechPurpose);
            }

            // set thru date on old paymentMethod
            paymentMethod.set("thruDate", now);
            toBeStored.add(paymentMethod);

            try {
                delegator.storeAll(toBeStored);
            } catch (GenericEntityException e) {
                Debug.logWarning(e.getMessage(), module);
                return ServiceUtil.returnError(UtilProperties.getMessage(resourceError,
                        "AccountingEftAccountCannotBeUpdated",
                        UtilMisc.toMap("errorString", e.getMessage()), locale));
            }
        } else {
            result.put("paymentMethodId", paymentMethodId);
            result.put("oldPaymentMethodId", paymentMethodId);
            result.put(ModelService.RESPONSE_MESSAGE, ModelService.RESPOND_SUCCESS);
            result.put(ModelService.SUCCESS_MESSAGE, UtilProperties.getMessage(resource,
                    "AccountingNoChangesMadeNotUpdatingEftAccount", locale));

            return result;
        }

        result.put("paymentMethodId", newEa.getString("paymentMethodId"));
        result.put("oldPaymentMethodId", paymentMethodId);
        result.put(ModelService.RESPONSE_MESSAGE, ModelService.RESPOND_SUCCESS);
        return result;
    }

    /**
     * SCIPIO: If a PartyContactMechPurpose for given party/contactMechId/purpose does not already exist,
     * makes and returns a non-committed GenericValue for one.
     * <p>
     * Code factored out from 4 updateXxx methods above.
     * <p>
     * 2017-10-10: PARTIAL FIX: This method has been PARTIALLY fixed (where noted below) to prevent creation of duplicate
     * PartyContactMechPurpose records in the case where no PartyContactMech exists (TODO: REVIEW: even this
     * fix is less than ideal, but trying to avoid any large changes until further review).
     * However this does NOT resolve the following issues...
     * <p>
     * FIXME?: REVIEW: SKETCHY BEHAVIOR: This method (from the original ofbiz code, 2017-10-10) actually
     * creates new PartyContactMechPurpose records for ContactMechs that do not yet have any
     * PartyContactMech records. This can happen because these methods are called by updatePaymentPostalAddress
     * which is triggered by a SECA on updatePostalAddress, which is called within updatePartyPostalAddress
     * BEFORE the updatePartyContactMech call is invoked. We receive the "new" contactMechId, not the old one,
     * which has no PartyContactMech yet.
     * <p>
     * This is not trivial to address because existing code may have been relying on this behavior to guarantee
     * BILLING_ADDRESS is added to the contactMechId through the SECA. So we cannot simply prevent creation
     * if no PartyContactMech record exists or risk breaking code, even though having standalone
     * PartyContactMechPurpose seems wrong.
     * Is unclear whether the "old" contactMechId could be used instead (technically it could work, but
     * modifying the old's purposes might be considered wrong).
     * <p>
     * Added 2017-10-10.
     */
    private static GenericValue checkMakePartyContactMechPurpose(Delegator delegator, String partyId, String contactMechId, String contactMechPurposeTypeId, Timestamp now) {
        GenericValue tempVal = null;

        try {
            List<GenericValue> allPCWPs = EntityQuery.use(delegator).from("PartyContactWithPurpose")
                    .where("partyId", partyId, "contactMechId", contactMechId, "contactMechPurposeTypeId", contactMechPurposeTypeId).queryList();
            allPCWPs = EntityUtil.filterByDate(allPCWPs, now, "contactFromDate", "contactThruDate", true);
            allPCWPs = EntityUtil.filterByDate(allPCWPs, now, "purposeFromDate", "purposeThruDate", true);
            tempVal = EntityUtil.getFirst(allPCWPs);
        } catch (GenericEntityException e) {
            Debug.logWarning(e.getMessage(), module);
            tempVal = null;
        }

        if (tempVal == null) {
            try {
                // SCIPIO: 2017-10-10: NEW QUERY: we must first verify that there is not already a PartyContactMechPurpose.
                // The query above does NOT catch this. TODO: REVIEW: this adds more overhead, not ideal.
                List<GenericValue> allPCMPs = EntityQuery.use(delegator).from("PartyContactMechPurpose")
                        .where("partyId", partyId, "contactMechId", contactMechId, "contactMechPurposeTypeId", contactMechPurposeTypeId).queryList();
                allPCMPs = EntityUtil.filterByDate(allPCMPs, now);
                tempVal = EntityUtil.getFirst(allPCMPs);
            } catch (GenericEntityException e) {
                Debug.logWarning(e.getMessage(), module);
                tempVal = null;
            }

            if (tempVal == null) {
                // no value found, create a new one
                return delegator.makeValue("PartyContactMechPurpose",
                        UtilMisc.toMap("partyId", partyId, "contactMechId", contactMechId,
                                "contactMechPurposeTypeId", contactMechPurposeTypeId, "fromDate", now));
            }
        }
        return null;
    }
}

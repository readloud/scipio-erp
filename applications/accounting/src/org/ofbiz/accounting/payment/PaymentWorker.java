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

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletRequest;

import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.StringUtil;
import org.ofbiz.base.util.UtilFormatOut;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilNumber;
import org.ofbiz.base.util.UtilProperties;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.entity.Delegator;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.condition.EntityCondition;
import org.ofbiz.entity.condition.EntityExpr;
import org.ofbiz.entity.condition.EntityOperator;
import org.ofbiz.entity.util.EntityQuery;
import org.ofbiz.entity.util.EntityUtil;


/**
 * Worker methods for Payments
 */
public final class PaymentWorker {

    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());
    private static final int decimals = UtilNumber.getBigDecimalScale("invoice.decimals");
    private static final RoundingMode rounding = UtilNumber.getRoundingMode("invoice.rounding");

    private static final Character NUMBER_MASK_CHAR; // SCIPIO
    static {
        String maskCharStr = UtilProperties.getPropertyValue("payment", "payment.general.number.maskChar");
        NUMBER_MASK_CHAR = maskCharStr.isEmpty() ? null : maskCharStr.charAt(0);
        Debug.logInfo("payment.general.number.maskChar: " + NUMBER_MASK_CHAR, module);
    }
    private static final int NUMBER_MASK_LENGTH = UtilProperties.getPropertyAsInteger("payment",
            "payment.general.number.maskLength", -4); // SCIPIO

    private PaymentWorker() {}

    // to be able to use in minilanguage where Boolean cannot be used
    public static List<Map<String, GenericValue>> getPartyPaymentMethodValueMaps(Delegator delegator, String partyId) {
        return(getPartyPaymentMethodValueMaps(delegator, partyId, false));
    }

    public static List<Map<String, GenericValue>> getPartyPaymentMethodValueMaps(Delegator delegator, String partyId, Boolean showOld) {
        List<Map<String, GenericValue>> paymentMethodValueMaps = new LinkedList<>();
        try {
            List<GenericValue> paymentMethods = EntityQuery.use(delegator).from("PaymentMethod").where("partyId", partyId).queryList();

            if (!showOld) {
                paymentMethods = EntityUtil.filterByDate(paymentMethods, true);
            }

            for (GenericValue paymentMethod : paymentMethods) {
                Map<String, GenericValue> valueMap = new HashMap<>();

                paymentMethodValueMaps.add(valueMap);
                valueMap.put("paymentMethod", paymentMethod);
                if ("CREDIT_CARD".equals(paymentMethod.getString("paymentMethodTypeId"))) {
                    GenericValue creditCard = paymentMethod.getRelatedOne("CreditCard", false);
                    if (creditCard != null) {
                        valueMap.put("creditCard", creditCard);
                    }
                } else if ("GIFT_CARD".equals(paymentMethod.getString("paymentMethodTypeId"))) {
                    GenericValue giftCard = paymentMethod.getRelatedOne("GiftCard", false);
                    if (giftCard != null) {
                        valueMap.put("giftCard", giftCard);
                    }
                } else if ("EFT_ACCOUNT".equals(paymentMethod.getString("paymentMethodTypeId"))) {
                    GenericValue eftAccount = paymentMethod.getRelatedOne("EftAccount", false);
                    if (eftAccount != null) {
                        valueMap.put("eftAccount", eftAccount);
                    }
                }
            }
        } catch (GenericEntityException e) {
            Debug.logWarning(e, module);
        }
        return paymentMethodValueMaps;
    }

    public static Map<String, Object> getPaymentMethodAndRelated(ServletRequest request, String partyId) {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        Map<String, Object> results = new HashMap<>();

        Boolean tryEntity = true;
        if (request.getAttribute("_ERROR_MESSAGE_") != null) {
            tryEntity = false;
        }

        String donePage = request.getParameter("DONE_PAGE");
        if (UtilValidate.isEmpty(donePage)) {
            donePage = "viewprofile";
        }
        results.put("donePage", donePage);

        String paymentMethodId = request.getParameter("paymentMethodId");

        // check for a create
        if (request.getAttribute("paymentMethodId") != null) {
            paymentMethodId = (String) request.getAttribute("paymentMethodId");
        }

        results.put("paymentMethodId", paymentMethodId);

        GenericValue paymentMethod = null;
        GenericValue creditCard = null;
        GenericValue giftCard = null;
        GenericValue eftAccount = null;

        if (UtilValidate.isNotEmpty(paymentMethodId)) {
            try {
                paymentMethod = EntityQuery.use(delegator).from("PaymentMethod").where("paymentMethodId", paymentMethodId).queryOne();
                creditCard = EntityQuery.use(delegator).from("CreditCard").where("paymentMethodId", paymentMethodId).queryOne();
                giftCard = EntityQuery.use(delegator).from("GiftCard").where("paymentMethodId", paymentMethodId).queryOne();
                eftAccount = EntityQuery.use(delegator).from("EftAccount").where("paymentMethodId", paymentMethodId).queryOne();
            } catch (GenericEntityException e) {
                Debug.logWarning(e, module);
            }
        }
        if (paymentMethod != null) {
            results.put("paymentMethod", paymentMethod);
        } else {
            tryEntity = false;
        }

        if (creditCard != null) {
            results.put("creditCard", creditCard);
        }
        if (giftCard != null) {
            results.put("giftCard", giftCard);
        }
        if (eftAccount != null) {
            results.put("eftAccount", eftAccount);
        }

        String curContactMechId = null;

        if (creditCard != null) {
            curContactMechId = UtilFormatOut.checkNull(tryEntity ? creditCard.getString("contactMechId") : request.getParameter("contactMechId"));
        } else if (giftCard != null) {
            curContactMechId = UtilFormatOut.checkNull(tryEntity ? giftCard.getString("contactMechId") : request.getParameter("contactMechId"));
        } else if (eftAccount != null) {
            curContactMechId = UtilFormatOut.checkNull(tryEntity ? eftAccount.getString("contactMechId") : request.getParameter("contactMechId"));
        }
        if (curContactMechId != null) {
            results.put("curContactMechId", curContactMechId);
        }

        results.put("tryEntity", tryEntity);

        return results;
    }

    public static GenericValue getPaymentAddress(Delegator delegator, String partyId) {
        GenericValue purpose = null;
        try {
            purpose = EntityQuery.use(delegator).from("PartyContactWithPurpose")
                    .where("partyId", partyId, "contactMechPurposeTypeId", "PAYMENT_LOCATION")
                    .orderBy("-purposeFromDate").filterByDate("contactFromDate", "contactThruDate", "purposeFromDate", "purposeThruDate")
                    .queryFirst();
        } catch (GenericEntityException e) {
            Debug.logError(e, "Trouble getting PartyContactWithPurpose view entity list", module);
        }

        // get the address for the primary contact mech
        GenericValue postalAddress = null;
        if (purpose != null) {
            try {
                postalAddress = EntityQuery.use(delegator).from("PostalAddress").where("contactMechId", purpose.getString("contactMechId")).queryOne();
            } catch (GenericEntityException e) {
                Debug.logError(e, "Trouble getting PostalAddress record for contactMechId: " + purpose.getString("contactMechId"), module);
            }
        }

        return postalAddress;
    }

    /**
     * Returns the total from a list of Payment entities
     *
     * @param payments List of Payment GenericValue items
     * @return total payments as BigDecimal
     */

    public static BigDecimal getPaymentsTotal(List<GenericValue> payments) {
        if (payments == null) {
            throw new IllegalArgumentException("Payment list cannot be null");
        }

        BigDecimal paymentsTotal = BigDecimal.ZERO;
        for (GenericValue payment : payments) {
            paymentsTotal = paymentsTotal.add(payment.getBigDecimal("amount")).setScale(decimals, rounding);
        }
        return paymentsTotal;
    }

    /**
     * Method to return the total amount of an payment which is applied to a payment
     * @param delegator the delegator
     * @param paymentId paymentId of the Payment
     * @return the applied total as BigDecimal
     */
    public static BigDecimal getPaymentApplied(Delegator delegator, String paymentId) {
        return getPaymentApplied(delegator, paymentId, false);
    }

    public static BigDecimal getPaymentApplied(Delegator delegator, String paymentId, Boolean actual) {
        if (delegator == null) {
            throw new IllegalArgumentException("Null delegator is not allowed in this method");
        }

        GenericValue payment = null;
        try {
            payment = EntityQuery.use(delegator).from("Payment").where("paymentId", paymentId).queryOne();
        } catch (GenericEntityException e) {
            Debug.logError(e, "Problem getting Payment", module);
        }

        if (payment == null) {
            throw new IllegalArgumentException("The paymentId passed does not match an existing payment");
        }

        return getPaymentApplied(payment, actual);
    }
    /**
     * Method to return the amount applied converted to the currency of payment
     * @param paymentApplicationId the payment application id
     * @return appliedAmount the applied amount as BigDecimal
     */
    public static BigDecimal getPaymentAppliedAmount(Delegator delegator, String paymentApplicationId) {
        GenericValue paymentApplication = null;
        BigDecimal appliedAmount = BigDecimal.ZERO;
        try {
            paymentApplication = EntityQuery.use(delegator).from("PaymentApplication").where("paymentApplicationId", paymentApplicationId).queryOne();
            appliedAmount = paymentApplication.getBigDecimal("amountApplied");
            if (paymentApplication.get("paymentId") != null) {
                GenericValue payment = paymentApplication.getRelatedOne("Payment", false);
                if (paymentApplication.get("invoiceId") != null && payment.get("actualCurrencyAmount") != null && payment.get("actualCurrencyUomId") != null) {
                    GenericValue invoice = paymentApplication.getRelatedOne("Invoice", false);
                    if (payment.getString("actualCurrencyUomId").equals(invoice.getString("currencyUomId"))) {
                           appliedAmount = appliedAmount.multiply(payment.getBigDecimal("amount")).divide(payment.getBigDecimal("actualCurrencyAmount"),new MathContext(100));
                    }
                }
            }
        } catch (GenericEntityException e) {
            Debug.logError(e, "Problem getting Payment", module);
        }
        return appliedAmount;
    }

    /**
     * Method to return the total amount of an payment which is applied to a payment
     * @param payment GenericValue object of the Payment
     * @return the applied total as BigDecimal in the currency of the payment
     */
    public static BigDecimal getPaymentApplied(GenericValue payment) {
        return getPaymentApplied(payment, false);
    }

    /**
     * Method to return the total amount of a payment which is applied to a payment
     * @param payment GenericValue object of the Payment
     * @param actual false for currency of the payment, true for the actual currency
     * @return the applied total as BigDecimal in the currency of the payment
     */
    public static BigDecimal getPaymentApplied(GenericValue payment, Boolean actual) {
        BigDecimal paymentApplied = BigDecimal.ZERO;
        List<GenericValue> paymentApplications = null;
        try {
            List<EntityExpr> cond = UtilMisc.toList(
                    EntityCondition.makeCondition("paymentId", EntityOperator.EQUALS, payment.getString("paymentId")),
                    EntityCondition.makeCondition("toPaymentId", EntityOperator.EQUALS, payment.getString("paymentId"))
                   );
            EntityCondition partyCond = EntityCondition.makeCondition(cond, EntityOperator.OR);
            paymentApplications = payment.getDelegator().findList("PaymentApplication", partyCond, null, UtilMisc.toList("invoiceId", "billingAccountId"), null, false);
            if (UtilValidate.isNotEmpty(paymentApplications)) {
                for (GenericValue paymentApplication : paymentApplications) {
                    BigDecimal amountApplied = paymentApplication.getBigDecimal("amountApplied");
                    // check currency invoice and if different convert amount applied for display
                    if (actual.equals(Boolean.FALSE) && paymentApplication.get("invoiceId") != null && payment.get("actualCurrencyAmount") != null && payment.get("actualCurrencyUomId") != null) {
                        GenericValue invoice = paymentApplication.getRelatedOne("Invoice", false);
                        if (payment.getString("actualCurrencyUomId").equals(invoice.getString("currencyUomId"))) {
                               amountApplied = amountApplied.multiply(payment.getBigDecimal("amount")).divide(payment.getBigDecimal("actualCurrencyAmount"),new MathContext(100));
                        }
                    }
                    paymentApplied = paymentApplied.add(amountApplied).setScale(decimals,rounding);
                }
            }
        } catch (GenericEntityException e) {
            Debug.logError(e, "Trouble getting entities", module);
        }
        return paymentApplied;
    }

    public static BigDecimal getPaymentNotApplied(GenericValue payment) {
        if (payment != null) {
            return payment.getBigDecimal("amount").subtract(getPaymentApplied(payment)).setScale(decimals,rounding);
        }
        return BigDecimal.ZERO;
    }

    public static BigDecimal getPaymentNotApplied(GenericValue payment, Boolean actual) {
        if (actual.equals(Boolean.TRUE) && UtilValidate.isNotEmpty(payment.getBigDecimal("actualCurrencyAmount"))) {
            return payment.getBigDecimal("actualCurrencyAmount").subtract(getPaymentApplied(payment, actual)).setScale(decimals,rounding);
        }
        return payment.getBigDecimal("amount").subtract(getPaymentApplied(payment)).setScale(decimals,rounding);
    }

    public static BigDecimal getPaymentNotApplied(Delegator delegator, String paymentId) {
        return getPaymentNotApplied(delegator,paymentId, false);
    }

    public static BigDecimal getPaymentNotApplied(Delegator delegator, String paymentId, Boolean actual) {
        if (delegator == null) {
            throw new IllegalArgumentException("Null delegator is not allowed in this method");
        }

        GenericValue payment = null;
        try {
            payment = EntityQuery.use(delegator).from("Payment").where("paymentId", paymentId).queryOne();
        } catch (GenericEntityException e) {
            Debug.logError(e, "Problem getting Payment", module);
        }

        if (payment == null) {
            throw new IllegalArgumentException("The paymentId passed does not match an existing payment");
        }
        return payment.getBigDecimal("amount").subtract(getPaymentApplied(delegator,paymentId, actual)).setScale(decimals,rounding);
    }

    /**
     * SCIPIO: Returns the general account/card masking character, as configured in
     * payment.properties#payment.general.number.maskChar.
     * NOTE: This may return null for testing purposes, in which case there should be no masking.
     */
    public static Character getNumberMaskChar(Delegator delegator) {
        return NUMBER_MASK_CHAR;
    }

    /**
     * SCIPIO: Returns the general account/card masking character, as configured in
     * payment.properties#payment.general.number.maskChar.
     * NOTE: This may return null for testing purposes, in which case there should be no masking.
     * NOTE: If a delegator is available, please call {@link #getNumberMaskChar(Delegator)} instead.
     */
    public static Character getNumberMaskChar() {
        return NUMBER_MASK_CHAR;
    }

    /**
     * SCIPIO: Returns the general account/card masking length, as configured in
     * payment.properties#payment.general.number.maskLength,
     * as the number of characters to mask (negative value means how many to leave unmasked).
     */
    public static int getNumberMaskLength(Delegator delelegator) {
        return NUMBER_MASK_LENGTH;
    }

    /**
     * SCIPIO: Returns the general account/card masking length, as configured in
     * payment.properties#payment.general.number.maskLength,
     * as the number of characters to mask (negative value means how many to leave unmasked).
     * NOTE: If a delegator is available, please call {@link #getNumberMaskLength(Delegator)} instead.
     */
    public static int getNumberMaskLength() {
        return NUMBER_MASK_LENGTH;
    }

    /**
     * SCIPIO: Applies the default number mask for general card numbers as defined in
     * payment.properties#payment.general.number.*.
     * <p>
     * NOTE: This method should generally not be used for UI display as-is; added mainly for logging
     * purposes. For UI display examples, see: component://accounting/webapp/accounting/common/acctlib.ftl.
     * <p>
     * TODO?: This could support unhardcoding the left- vs right- masking via properties, but it's almost always
     * left-masking that are used...
     */
    public static String applyGeneralNumberMask(CharSequence number, Delegator delegator) {
        return StringUtil.maskLeft(number, getNumberMaskLength(delegator), getNumberMaskChar(delegator));
    }
}

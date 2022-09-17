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
package org.ofbiz.accounting.invoice;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.UtilDateTime;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilNumber;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.entity.Delegator;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.condition.EntityCondition;
import org.ofbiz.entity.condition.EntityConditionList;
import org.ofbiz.entity.condition.EntityExpr;
import org.ofbiz.entity.condition.EntityOperator;
import org.ofbiz.entity.util.EntityQuery;
import org.ofbiz.entity.util.EntityUtil;
import org.ofbiz.entity.util.EntityUtilProperties;
import org.ofbiz.product.product.ProductContentWrapper;
import org.ofbiz.service.LocalDispatcher;

/**
 * InvoiceWorker - Worker methods of invoices
 */
public final class InvoiceWorker {

    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());
    private static final int decimals = UtilNumber.getBigDecimalScale("invoice.decimals");
    private static final RoundingMode rounding = UtilNumber.getRoundingMode("invoice.rounding");
    private static final int taxDecimals = UtilNumber.getBigDecimalScale("salestax.calc.decimals");
    private static final RoundingMode taxRounding = UtilNumber.getRoundingMode("salestax.rounding");

    private InvoiceWorker () {}

    /**
     * Return the total amount of the invoice (including tax) using the the invoiceId as input.
     * @param delegator the delegator
     * @param invoiceId the invoice id
     * @return Return the total amount of the invoice
     */
    public static BigDecimal getInvoiceTotal(Delegator delegator, String invoiceId) {
        return getInvoiceTotal(delegator, invoiceId, Boolean.TRUE);
    }

    /**
     * Return the total amount of the invoice (including tax) using the the invoiceId as input.
     * with the ability to specify if the actual currency is required.
     * @param delegator the delegator
     * @param invoiceId the invoice Id
     * @param actualCurrency true: provide the actual currency of the invoice (could be different from the system currency)
     *                       false: if required convert the actual currency into the system currency.
     * @return Return the total amount of the invoice
     */
    public static BigDecimal getInvoiceTotal(Delegator delegator, String invoiceId, Boolean actualCurrency) {
        if (delegator == null) {
            throw new IllegalArgumentException("Null delegator is not allowed in this method");
        }

        GenericValue invoice = null;
        try {
            invoice = EntityQuery.use(delegator).from("Invoice").where("invoiceId", invoiceId).queryOne();
        } catch (GenericEntityException e) {
            Debug.logError(e, "Problem getting Invoice", module);
        }

        if (invoice == null) {
            throw new IllegalArgumentException("The passed invoiceId [" +invoiceId + "] does not match an existing invoice");
        }

        return getInvoiceTotal(invoice, actualCurrency);
    }

    /**
     * Method to return the total amount of an invoice item i.e. quantity * amount
     * @param invoiceItem GenericValue object of the invoice item
     * @return the invoice total as BigDecimal
     */
    public static BigDecimal getInvoiceItemTotal(GenericValue invoiceItem) {
        BigDecimal quantity = invoiceItem.getBigDecimal("quantity");
        if (quantity == null) {
            quantity = BigDecimal.ONE;
        }
        BigDecimal amount = invoiceItem.getBigDecimal("amount");
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }
        return quantity.multiply(amount).setScale(decimals, rounding);
    }

    /**
     * Method to return the invoice item description with following step
     * 1. take the item description field
     * 2. if tax associate, resolve the taxAuthorityRateProduct description
     * 3. if product associate, call content wrapper to resolve PRODUCT_NAME or take the brandName
     * 4. take the item Type line description
     * <p>
     * SCIPIO: 2018-09-26: TODO: REVIEW: html product name escaping...
     *
     * @param dispatcher
     * @param invoiceItem
     * @param locale
     * @return the item description
     * @throws GenericEntityException
     */
    public static String getInvoiceItemDescription(LocalDispatcher dispatcher, GenericValue invoiceItem, Locale locale) throws GenericEntityException {
        Delegator delegator = invoiceItem.getDelegator();
        String description = invoiceItem.getString("description");
        if (UtilValidate.isEmpty(description)) {
            String taxAuthorityRateSeqId = invoiceItem.getString("taxAuthorityRateSeqId");
            if (UtilValidate.isNotEmpty(taxAuthorityRateSeqId)) {
                GenericValue taxRate = invoiceItem.getRelatedOne("TaxAuthorityRateProduct", true);
                if (taxRate != null) {
                    description = (String) taxRate.get("description", locale);
                }
            }
        }
        if (UtilValidate.isEmpty(description)) {
            String productId = invoiceItem.getString("productId");
            if (UtilValidate.isNotEmpty(productId)) {
                GenericValue product = EntityQuery.use(delegator).from("Product").where("productId", productId).cache().queryOne();
                ProductContentWrapper productContentWrapper = new ProductContentWrapper(dispatcher, product, locale, "text/html");
                // SCIPIO
                //StringUtil.StringWrapper stringWrapper = productContentWrapper.get("PRODUCT_NAME", "html");
                //if (stringWrapper != null) {
                //    description = stringWrapper.toString();
                //}
                description = productContentWrapper.get("PRODUCT_NAME", "html"); // SCIPIO: TODO: REVIEW: html
                if (UtilValidate.isEmpty(description)) {
                    description = product.getString("brandName");
                }
            }
        }
        if (UtilValidate.isEmpty(description)) {
            description = (String) invoiceItem.getRelatedOne("InvoiceItemType", true).get("description",locale);
        }
        return description;
    }

    /** Method to get the taxable invoice item types as a List of invoiceItemTypeIds.  These are identified in Enumeration with enumTypeId TAXABLE_INV_ITM_TY. */
    public static List<String> getTaxableInvoiceItemTypeIds(Delegator delegator) throws GenericEntityException {
        List<String> typeIds = new LinkedList<>();
        List<GenericValue> invoiceItemTaxTypes = EntityQuery.use(delegator).from("Enumeration").where("enumTypeId", "TAXABLE_INV_ITM_TY")
                .cache().queryList();
        for (GenericValue invoiceItemTaxType : invoiceItemTaxTypes) {
            typeIds.add(invoiceItemTaxType.getString("enumId"));
        }
        return typeIds;
    }

    public static BigDecimal getInvoiceTaxTotal(GenericValue invoice) {
        BigDecimal taxTotal = BigDecimal.ZERO;
        Map<String, Set<String>> taxAuthPartyAndGeos = InvoiceWorker.getInvoiceTaxAuthPartyAndGeos(invoice);
        for (Map.Entry<String, Set<String>> taxAuthPartyGeos : taxAuthPartyAndGeos.entrySet()) {
            String taxAuthPartyId = taxAuthPartyGeos.getKey();
            for (String taxAuthGeoId : taxAuthPartyGeos.getValue()) {
                taxTotal = taxTotal.add(InvoiceWorker.getInvoiceTaxTotalForTaxAuthPartyAndGeo(invoice, taxAuthPartyId, taxAuthGeoId));
            }
        }
        taxTotal = taxTotal.add(InvoiceWorker.getInvoiceUnattributedTaxTotal(invoice));
        return taxTotal;
    }

    public static BigDecimal getInvoiceNoTaxTotal(GenericValue invoice) {
        return getInvoiceTotal(invoice, Boolean.TRUE).subtract(getInvoiceTaxTotal(invoice));
    }

    /**
     * Method to return the total amount of an invoice
     * @param invoice GenericValue object of the Invoice
     * @return the invoice total as BigDecimal
     */
     public static BigDecimal getInvoiceTotal(GenericValue invoice) {
        return getInvoiceTotal(invoice, Boolean.TRUE);
    }

     /**
      *
      * Return the total amount of the invoice (including tax) using the the invoice GenericValue as input.
      * with the ability to specify if the actual currency is required.
      * @param invoice GenericValue object of the Invoice
      * @param actualCurrency true: provide the actual currency of the invoice (could be different from the system currency)
      *                       false: if required convert the actual currency into the system currency.
      * @return Return the total amount of the invoice
      */
     public static BigDecimal getInvoiceTotal(GenericValue invoice, Boolean actualCurrency) {
        BigDecimal invoiceTotal = BigDecimal.ZERO;
        BigDecimal invoiceTaxTotal = InvoiceWorker.getInvoiceTaxTotal(invoice);

        List<GenericValue> invoiceItems = null;
        try {
            invoiceItems = invoice.getRelated("InvoiceItem", null, null, false);
            invoiceItems = EntityUtil.filterByAnd(
                    invoiceItems, UtilMisc.toList(
                            EntityCondition.makeCondition("invoiceItemTypeId", EntityOperator.NOT_IN, getTaxableInvoiceItemTypeIds(invoice.getDelegator()))
                    ));
        } catch (GenericEntityException e) {
            Debug.logError(e, "Trouble getting InvoiceItem list", module);
        }
        if (invoiceItems != null) {
            for (GenericValue invoiceItem : invoiceItems) {
                invoiceTotal = invoiceTotal.add(getInvoiceItemTotal(invoiceItem)).setScale(decimals,rounding);
            }
        }
        invoiceTotal = invoiceTotal.add(invoiceTaxTotal).setScale(decimals, rounding);
        if (UtilValidate.isNotEmpty(invoiceTotal) && !actualCurrency) {
            invoiceTotal = invoiceTotal.multiply(getInvoiceCurrencyConversionRate(invoice)).setScale(decimals,rounding);
        }
        return invoiceTotal;
    }

    /**
     * Method to obtain the bill to party for an invoice. Note that invoice.partyId is the bill to party.
     * @param invoice GenericValue object of the Invoice
     * @return GenericValue object of the Party
     */
    public static GenericValue getBillToParty(GenericValue invoice) {
        try {
            GenericValue billToParty = invoice.getRelatedOne("Party", false);
            if (billToParty != null) {
                return billToParty;
            }
        } catch (GenericEntityException e) {
            Debug.logError(e, "Trouble getting Party from Invoice", module);
        }

        // remaining code is the old method, which we leave here for compatibility purposes
        List<GenericValue> billToRoles = null;
        try {
            billToRoles = invoice.getRelated("InvoiceRole", UtilMisc.toMap("roleTypeId", "BILL_TO_CUSTOMER"), UtilMisc.toList("-datetimePerformed"), false);
        } catch (GenericEntityException e) {
            Debug.logError(e, "Trouble getting InvoiceRole list", module);
        }

        if (billToRoles != null) {
            GenericValue role = EntityUtil.getFirst(billToRoles);
            GenericValue party = null;
            try {
                party = role.getRelatedOne("Party", false);
            } catch (GenericEntityException e) {
                Debug.logError(e, "Trouble getting Party from InvoiceRole", module);
            }
            if (party != null) {
                return party;
            }
        }
        return null;
    }

    /** Convenience method to obtain the bill from party for an invoice. Note that invoice.partyIdFrom is the bill from party. */
    public static GenericValue getBillFromParty(GenericValue invoice) {
        try {
            return invoice.getRelatedOne("FromParty", false);
        } catch (GenericEntityException e) {
            Debug.logError(e, "Trouble getting FromParty from Invoice", module);
        }
        return null;
    }

    /**
      * Method to obtain the send from party for an invoice
      * @param invoice GenericValue object of the Invoice
      * @return GenericValue object of the Party
      */
    public static GenericValue getSendFromParty(GenericValue invoice) {
        GenericValue billFromParty = getBillFromParty(invoice);
        if (billFromParty != null) {
            return billFromParty;
        }

        // remaining code is the old method, which we leave here for compatibility purposes
        List<GenericValue> sendFromRoles = null;
        try {
            sendFromRoles = invoice.getRelated("InvoiceRole", UtilMisc.toMap("roleTypeId", "BILL_FROM_VENDOR"), UtilMisc.toList("-datetimePerformed"), false);
        } catch (GenericEntityException e) {
            Debug.logError(e, "Trouble getting InvoiceRole list", module);
        }

        if (sendFromRoles != null) {
            GenericValue role = EntityUtil.getFirst(sendFromRoles);
            GenericValue party = null;
            try {
                party = role.getRelatedOne("Party", false);
            } catch (GenericEntityException e) {
                Debug.logError(e, "Trouble getting Party from InvoiceRole", module);
            }
            if (party != null) {
                return party;
            }
        }
        return null;
    }

    /**
      * Method to obtain the shipping address from an invoice
      * first resolve from InvoiceContactMech and if not found try from Shipment if present
      * @param invoice GenericValue object of the Invoice
      * @return GenericValue object of the PostalAddress
      */
    public static GenericValue getShippingAddress(GenericValue invoice) {
        GenericValue postalAddress = getInvoiceAddressByType(invoice, "SHIPPING_LOCATION", false);
        Delegator delegator = invoice.getDelegator();
        if (postalAddress == null) {
            try {
                GenericValue shipmentView = EntityQuery.use(delegator).from("InvoiceItemAndShipmentView")
                        .where("invoiceId", invoice.get("invoiceId")).queryFirst();
                if (shipmentView != null) {
                    GenericValue shipment = EntityQuery.use(delegator).from("Shipment")
                        .where("shipmentId", shipmentView.get("shipmentId")).queryOne();
                    postalAddress = shipment.getRelatedOne("DestinationPostalAddress", false);
                }
            } catch (GenericEntityException e) {
                Debug.logError("Touble getting ContactMech entity from OISG", module);
            }
        }
        return postalAddress;
    }

    /**
      * Method to obtain the billing address for an invoice
      * @param invoice GenericValue object of the Invoice
      * @return GenericValue object of the PostalAddress
      */
    public static GenericValue getBillToAddress(GenericValue invoice) {
        return getInvoiceAddressByType(invoice, "BILLING_LOCATION");
    }

    /**
      * Method to obtain the sending address for an invoice
      * @param invoice GenericValue object of the Invoice
      * @return GenericValue object of the PostalAddress
      */
    public static GenericValue getSendFromAddress(GenericValue invoice) {
        return getInvoiceAddressByType(invoice, "PAYMENT_LOCATION");
    }

    public static GenericValue getInvoiceAddressByType(GenericValue invoice, String contactMechPurposeTypeId) {
        return getInvoiceAddressByType(invoice, contactMechPurposeTypeId, true);
    }

    public static GenericValue getInvoiceAddressByType(GenericValue invoice, String contactMechPurposeTypeId, boolean fetchPartyAddress) {
        Delegator delegator = invoice.getDelegator();
        List<GenericValue> locations = null;
        // first try InvoiceContactMech to see if we can find the address needed
        try {
            locations = invoice.getRelated("InvoiceContactMech", UtilMisc.toMap("contactMechPurposeTypeId", contactMechPurposeTypeId), null, false);
        } catch (GenericEntityException e) {
            Debug.logError("Touble getting InvoiceContactMech entity list", module);
        }

        if (UtilValidate.isEmpty(locations) && fetchPartyAddress)    {
            // if no locations found get it from the PartyAndContactMech using the from and to party on the invoice
            String destinationPartyId = null;
            Timestamp now = UtilDateTime.nowTimestamp();
            if ("SALES_INVOICE".equals(invoice.getString("invoiceTypeId"))) {
                destinationPartyId = invoice.getString("partyId");
            }
            if ("PURCHASE_INVOICE".equals(invoice.getString("invoiceTypeId"))) {
                destinationPartyId = invoice.getString("partyId");
            }
            try {
                locations = EntityQuery.use(delegator).from("PartyContactWithPurpose")
                        .where("partyId", destinationPartyId, "contactMechPurposeTypeId", contactMechPurposeTypeId).queryList();
                locations = EntityUtil.filterByDate(locations, now, "contactFromDate", "contactThruDate", true);
                locations = EntityUtil.filterByDate(locations, now, "purposeFromDate", "purposeThruDate", true);
            } catch (GenericEntityException e) {
                Debug.logError("Trouble getting contact party purpose list", module);
            }
            //if still not found get it from the general location
            if (UtilValidate.isEmpty(locations))    {
                try {
                    locations = EntityQuery.use(delegator).from("PartyContactWithPurpose")
                            .where("partyId", destinationPartyId, "contactMechPurposeTypeId", "GENERAL_LOCATION").queryList();
                    locations = EntityUtil.filterByDate(locations, now, "contactFromDate", "contactThruDate", true);
                    locations = EntityUtil.filterByDate(locations, now, "purposeFromDate", "purposeThruDate", true);
                } catch (GenericEntityException e) {
                    Debug.logError("Trouble getting contact party purpose list", module);
                }
            }
        }

        // now return the first PostalAddress from the locations
        GenericValue postalAddress = null;
        GenericValue contactMech = null;
        if (UtilValidate.isNotEmpty(locations)) {
            try {
                contactMech = locations.get(0).getRelatedOne("ContactMech", false);
            } catch (GenericEntityException e) {
                Debug.logError(e, "Trouble getting Contact for contactMechId: " + locations.get(0).getString("contactMechId"), module);
            }

            if (contactMech != null && "POSTAL_ADDRESS".equals(contactMech.getString("contactMechTypeId")))    {
                try {
                    postalAddress = contactMech.getRelatedOne("PostalAddress", false);
                    return postalAddress;
                } catch (GenericEntityException e) {
                    Debug.logError(e, "Trouble getting PostalAddress for contactMechId: " + contactMech.getString("contactMechId"), module);
                }
            }
        }
        return contactMech;
    }

    /**
     * Method to return the total amount of an invoice which is not yet applied to a payment
     * @param delegator the delegator
     * @param invoiceId the invoice id
     * @param actualCurrency the currency
     * @return the invoice total as BigDecimal
     */
    public static BigDecimal getInvoiceNotApplied(Delegator delegator, String invoiceId, Boolean actualCurrency) {
        return InvoiceWorker.getInvoiceTotal(delegator, invoiceId, actualCurrency).subtract(getInvoiceApplied(delegator, invoiceId,  UtilDateTime.nowTimestamp(), actualCurrency));
    }
    public static BigDecimal getInvoiceNotApplied(Delegator delegator, String invoiceId) {
        return InvoiceWorker.getInvoiceTotal(delegator, invoiceId).subtract(getInvoiceApplied(delegator, invoiceId));
    }
    public static BigDecimal getInvoiceNotApplied(GenericValue invoice) {
        return InvoiceWorker.getInvoiceTotal(invoice, Boolean.TRUE).subtract(getInvoiceApplied(invoice));
    }
    public static BigDecimal getInvoiceNotApplied(GenericValue invoice, Boolean actualCurrency) {
        return InvoiceWorker.getInvoiceTotal(invoice, actualCurrency).subtract(getInvoiceApplied(invoice, actualCurrency));
    }
    /**
     * Returns amount not applied (i.e., still outstanding) of an invoice at an asOfDate, based on Payment.effectiveDate &lt;= asOfDateTime
     *
     * @param invoice GenericValue object of the invoice
     * @param asOfDateTime the date to use
     * @return Returns amount not applied of the invoice
     */
    public static BigDecimal getInvoiceNotApplied(GenericValue invoice, Timestamp asOfDateTime) {
        return InvoiceWorker.getInvoiceTotal(invoice, Boolean.TRUE).subtract(getInvoiceApplied(invoice, asOfDateTime));
    }


    /**
     * Method to return the total amount of an invoice which is applied to a payment
     * @param delegator the delegator
     * @param invoiceId the invoice id
     * @return the invoice total as BigDecimal
     */
    public static BigDecimal getInvoiceApplied(Delegator delegator, String invoiceId) {
        return getInvoiceApplied(delegator, invoiceId, UtilDateTime.nowTimestamp(), Boolean.TRUE);
    }

    /**
     * Returns amount applied to invoice before an asOfDateTime, based on Payment.effectiveDate &lt;= asOfDateTime
     *
     * @param delegator the delegator
     * @param invoiceId the invoice id
     * @param asOfDateTime - a Timestamp
     * @return returns amount applied to invoice before an asOfDateTime
     */
    public static BigDecimal getInvoiceApplied(Delegator delegator, String invoiceId, Timestamp asOfDateTime, Boolean actualCurrency) {
        if (delegator == null) {
            throw new IllegalArgumentException("Null delegator is not allowed in this method");
        }

        BigDecimal invoiceApplied = BigDecimal.ZERO;
        List<GenericValue> paymentApplications = null;

        // lookup payment applications which took place before the asOfDateTime for this invoice
        EntityConditionList<EntityExpr> dateCondition = EntityCondition.makeCondition(UtilMisc.toList(
                EntityCondition.makeCondition("effectiveDate", EntityOperator.EQUALS, null),
                EntityCondition.makeCondition("effectiveDate", EntityOperator.LESS_THAN_EQUAL_TO, asOfDateTime)), EntityOperator.OR);
        EntityConditionList<EntityCondition> conditions = EntityCondition.makeCondition(UtilMisc.toList(
                dateCondition,
                EntityCondition.makeCondition("invoiceId", EntityOperator.EQUALS, invoiceId)),
                EntityOperator.AND);

        try {
            paymentApplications = EntityQuery.use(delegator).from("PaymentAndApplication")
                    .where(conditions).orderBy("effectiveDate").queryList();
        } catch (GenericEntityException e) {
            Debug.logError(e, "Trouble getting paymentApplicationlist", module);
        }
        if (paymentApplications != null) {
            for (GenericValue paymentApplication : paymentApplications) {
                invoiceApplied = invoiceApplied.add(paymentApplication.getBigDecimal("amountApplied")).setScale(decimals,rounding);
            }
        }
        if (UtilValidate.isNotEmpty(invoiceApplied) && !actualCurrency) {
            invoiceApplied = invoiceApplied.multiply(getInvoiceCurrencyConversionRate(delegator, invoiceId)).setScale(decimals,rounding);
        }
        return invoiceApplied;
    }
    /**
     * Method to return the total amount of an invoice which is applied to a payment
     * @param invoice GenericValue object of the invoice
     * @return the applied total as BigDecimal
     */
    public static BigDecimal getInvoiceApplied(GenericValue invoice) {
        return getInvoiceApplied(invoice, UtilDateTime.nowTimestamp());
    }

    /**
     * Return the amount applied to the invoice
     * @param invoice GenericValue object of the invoice
     * @param actualCurrency the currency of the invoice
     * @return returns the amount applied to the invoice
     */
    public static BigDecimal getInvoiceApplied(GenericValue invoice, Boolean actualCurrency) {
        return getInvoiceApplied(invoice.getDelegator(), invoice.getString("invoiceId"), UtilDateTime.nowTimestamp(), actualCurrency);
    }
    public static BigDecimal getInvoiceApplied(GenericValue invoice, Timestamp asOfDateTime) {
        return getInvoiceApplied(invoice.getDelegator(), invoice.getString("invoiceId"), asOfDateTime, Boolean.TRUE);
    }
    /**
     * Method to return the amount of an invoiceItem which is applied to a payment
     * @param delegator the delegator
     * @param invoiceId the invoice id
     * @param invoiceItemSeqId the invoice item id
     * @return the invoice total as BigDecimal
     */
    public static BigDecimal getInvoiceItemApplied(Delegator delegator, String invoiceId, String invoiceItemSeqId) {
        if (delegator == null) {
            throw new IllegalArgumentException("Null delegator is not allowed in this method");
        }

        GenericValue invoiceItem = null;
        try {
            invoiceItem = EntityQuery.use(delegator).from("Invoice").where("invoiceId", invoiceId,"invoiceItemSeqId", invoiceItemSeqId).queryOne();
        } catch (GenericEntityException e) {
            Debug.logError(e, "Problem getting InvoiceItem", module);
        }

        if (invoiceItem == null) {
            throw new IllegalArgumentException("The invoiceId/itemSeqId passed does not match an existing invoiceItem");
        }

        return getInvoiceItemApplied(invoiceItem);
    }

    /**
     * Method to return the total amount of an invoiceItem which is applied to a payment
     * @param invoiceItem GenericValue object of the invoice item
     * @return the applied total as BigDecimal
     */
    public static BigDecimal getInvoiceItemApplied(GenericValue invoiceItem) {
        BigDecimal invoiceItemApplied = BigDecimal.ZERO;
        List<GenericValue> paymentApplications = null;
        try {
            paymentApplications = invoiceItem.getRelated("PaymentApplication", null, null, false);
        } catch (GenericEntityException e) {
            Debug.logError(e, "Trouble getting paymentApplicationlist", module);
        }
        if (paymentApplications != null) {
            for (GenericValue paymentApplication : paymentApplications) {
                invoiceItemApplied = invoiceItemApplied.add(paymentApplication.getBigDecimal("amountApplied")).setScale(decimals,rounding);
            }
        }
        return invoiceItemApplied;
    }
    public static BigDecimal getInvoiceCurrencyConversionRate(GenericValue invoice) {
        BigDecimal conversionRate = null;
        Delegator delegator = invoice.getDelegator();
        String otherCurrencyUomId = null;
        // find the organization party currencyUomId which different from the invoice currency
        try {
            GenericValue party  = EntityQuery.use(delegator).from("PartyAcctgPreference").where("partyId", invoice.get("partyIdFrom")).queryOne();
            if (UtilValidate.isEmpty(party) || party.getString("baseCurrencyUomId").equals(invoice.getString("currencyUomId"))) {
                party  = EntityQuery.use(delegator).from("PartyAcctgPreference").where("partyId", invoice.get("partyId")).queryOne();
            }
            if (UtilValidate.isNotEmpty(party) && party.getString("baseCurrencyUomId") != null) {
                otherCurrencyUomId = party.getString("baseCurrencyUomId");
            } else {
                otherCurrencyUomId = EntityUtilProperties.getPropertyValue("general", "currency.uom.id.default", "USD", delegator);
            }
        } catch (GenericEntityException e) {
            Debug.logError(e, "Trouble getting database records....", module);
        }
        if (invoice.getString("currencyUomId").equals(otherCurrencyUomId)) {
            return BigDecimal.ONE;  // organization party has the same currency so conversion not required.
        }

        try {
            // check if the invoice is posted and get the conversion from there
            List<GenericValue> acctgTransEntries = invoice.getRelated("AcctgTrans", null, null, false);
            if (UtilValidate.isNotEmpty(acctgTransEntries)) {
                GenericValue acctgTransEntry = (acctgTransEntries.get(0)).getRelated("AcctgTransEntry", null, null, false).get(0);
                BigDecimal origAmount = acctgTransEntry.getBigDecimal("origAmount");
                if (origAmount.compareTo(BigDecimal.ZERO) == 1) {
                    conversionRate = acctgTransEntry.getBigDecimal("amount").divide(acctgTransEntry.getBigDecimal("origAmount"), new MathContext(100)).setScale(decimals,rounding);
                }
            }
            // check if a payment is applied and use the currency conversion from there
            if (UtilValidate.isEmpty(conversionRate)) {
                List<GenericValue> paymentAppls = invoice.getRelated("PaymentApplication", null, null, false);
                for (GenericValue paymentAppl : paymentAppls) {
                    GenericValue payment = paymentAppl.getRelatedOne("Payment", false);
                    if (UtilValidate.isNotEmpty(payment.getBigDecimal("actualCurrencyAmount"))) {
                        if (UtilValidate.isEmpty(conversionRate)) {
                            conversionRate = payment.getBigDecimal("amount").divide(payment.getBigDecimal("actualCurrencyAmount"),new MathContext(100)).setScale(decimals,rounding);
                        } else {
                            conversionRate = conversionRate.add(payment.getBigDecimal("amount").divide(payment.getBigDecimal("actualCurrencyAmount"),new MathContext(100))).divide(new BigDecimal("2"),new MathContext(100)).setScale(decimals,rounding);
                        }
                    }
                }
            }
            // use the dated conversion entity
            if (UtilValidate.isEmpty(conversionRate)) {
                GenericValue rate = EntityQuery.use(delegator).from("UomConversionDated").where("uomIdTo", invoice.get("currencyUomId"), "uomId", otherCurrencyUomId).filterByDate(invoice.getTimestamp("invoiceDate")).queryFirst();
                if (rate != null) {
                    conversionRate = BigDecimal.ONE.divide(rate.getBigDecimal("conversionFactor"), new MathContext(100)).setScale(decimals,rounding);
                } else {
                    Debug.logError("Could not find conversionrate for invoice: " + invoice.getString("invoiceId"), module);
                    return new BigDecimal("1");
                }
            }

        } catch (GenericEntityException e) {
            Debug.logError(e, "Trouble getting database records....", module);
        }
        return(conversionRate);
    }

    public static BigDecimal getInvoiceCurrencyConversionRate(Delegator delegator, String invoiceId) {
        if (delegator == null) {
            throw new IllegalArgumentException("Null delegator is not allowed in this method");
        }

        GenericValue invoice = null;
        try {
            invoice = EntityQuery.use(delegator).from("Invoice").where("invoiceId", invoiceId).queryOne();
        } catch (GenericEntityException e) {
            Debug.logError(e, "Problem getting Invoice", module);
        }

        if (invoice == null) {
            throw new IllegalArgumentException("The invoiceId passed does not match an existing invoice");
        }

        return getInvoiceCurrencyConversionRate(invoice);
    }

    /**
     * Return a list of taxes separated by Geo and party and return the tax grand total
     * @param invoice Generic Value
     * @return Map taxByTaxAuthGeoAndPartyList(List) and taxGrandTotal(BigDecimal)
     */
    @Deprecated
    public static Map<String, Object> getInvoiceTaxByTaxAuthGeoAndParty(GenericValue invoice) {
        BigDecimal taxGrandTotal = BigDecimal.ZERO;
        List<Map<String, Object>> taxByTaxAuthGeoAndPartyList = new LinkedList<>();
        List<GenericValue> invoiceItems = null;
        if (invoice != null) {
            try {
                invoiceItems = invoice.getRelated("InvoiceItem", null, null, false);
            } catch (GenericEntityException e) {
                Debug.logError(e, "Trouble getting InvoiceItem list", module);
            }
            if ("SALES_INVOICE".equals(invoice.getString("invoiceTypeId"))) {
                invoiceItems = EntityUtil.filterByOr(
                        invoiceItems, UtilMisc.toList(
                                EntityCondition.makeCondition("invoiceItemTypeId", EntityOperator.EQUALS, "INV_SALES_TAX"),
                                EntityCondition.makeCondition("invoiceItemTypeId", EntityOperator.EQUALS, "ITM_SALES_TAX")));
            } else if (("PURCHASE_INVOICE".equals(invoice.getString("invoiceTypeId")))) {
                invoiceItems = EntityUtil.filterByOr(
                        invoiceItems, UtilMisc.toList(
                                EntityCondition.makeCondition("invoiceItemTypeId", EntityOperator.EQUALS, "PINV_SALES_TAX"),
                                EntityCondition.makeCondition("invoiceItemTypeId", EntityOperator.EQUALS, "PITM_SALES_TAX")));
            } else {
                invoiceItems = null;
            }
            if (UtilValidate.isNotEmpty(invoiceItems)) {
                invoiceItems = EntityUtil.orderBy(invoiceItems, UtilMisc.toList("taxAuthGeoId","taxAuthPartyId"));
                // get the list of all distinct taxAuthGeoId and taxAuthPartyId. It is for getting the number of taxAuthGeoId and taxAuthPartyId in invoiceItems.
                List<String> distinctTaxAuthGeoIdList = EntityUtil.getFieldListFromEntityList(invoiceItems, "taxAuthGeoId", true);
                List<String> distinctTaxAuthPartyIdList = EntityUtil.getFieldListFromEntityList(invoiceItems, "taxAuthPartyId", true);
                for (String taxAuthGeoId : distinctTaxAuthGeoIdList ) {
                    for (String taxAuthPartyId : distinctTaxAuthPartyIdList) {
                        //get all records for invoices filtered by taxAuthGeoId and taxAurhPartyId
                        List<GenericValue> invoiceItemsByTaxAuthGeoAndPartyIds = EntityUtil.filterByAnd(invoiceItems, UtilMisc.toMap("taxAuthGeoId", taxAuthGeoId, "taxAuthPartyId", taxAuthPartyId));
                        if (UtilValidate.isNotEmpty(invoiceItemsByTaxAuthGeoAndPartyIds)) {
                            BigDecimal totalAmount = BigDecimal.ZERO;
                            //Now for each invoiceItem record get and add amount.
                            for (GenericValue invoiceItem : invoiceItemsByTaxAuthGeoAndPartyIds) {
                                BigDecimal amount = invoiceItem.getBigDecimal("amount");
                                if (amount == null) {
                                    amount = BigDecimal.ZERO;
                                }
                                totalAmount = totalAmount.add(amount).setScale(taxDecimals, taxRounding);
                            }
                            totalAmount = totalAmount.setScale(UtilNumber.getBigDecimalScale("salestax.calc.decimals"), UtilNumber.getRoundingMode("salestax.rounding"));
                            taxByTaxAuthGeoAndPartyList.add(UtilMisc.<String, Object>toMap("taxAuthPartyId", taxAuthPartyId, "taxAuthGeoId", taxAuthGeoId, "totalAmount", totalAmount));
                            taxGrandTotal = taxGrandTotal.add(totalAmount);
                        }
                    }
                }
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("taxByTaxAuthGeoAndPartyList", taxByTaxAuthGeoAndPartyList);
        result.put("taxGrandTotal", taxGrandTotal);
        return result;
    }

    /**
     * Returns a List of the TaxAuthority Party and Geos for the given Invoice.
     * @param invoice GenericValue object representing the Invoice
     * @return A Map containing the each taxAuthPartyId as a key and a Set of taxAuthGeoIds for that taxAuthPartyId as the values.  Note this method
     *         will not account for tax lines that do not contain a taxAuthPartyId
     */
    public static Map<String, Set<String>> getInvoiceTaxAuthPartyAndGeos (GenericValue invoice) {
        Map<String, Set<String>> result = new HashMap<>();

        if (invoice == null) {
            throw new IllegalArgumentException("Invoice cannot be null.");
        }
        List<GenericValue> invoiceTaxItems = null;
        try {
            Delegator delegator = invoice.getDelegator();
            invoiceTaxItems = EntityQuery.use(delegator).from("InvoiceItem")
                    .where(EntityCondition.makeCondition("invoiceId", invoice.getString("invoiceId")),
                            EntityCondition.makeCondition("invoiceItemTypeId", EntityOperator.IN, getTaxableInvoiceItemTypeIds(delegator))
                    ).queryList();
        } catch (GenericEntityException e) {
            Debug.logError(e, "Trouble getting InvoiceItem list", module);
            return null;
        }
        if (invoiceTaxItems != null) {
            for (GenericValue invoiceItem : invoiceTaxItems) {
                String taxAuthPartyId = invoiceItem.getString("taxAuthPartyId");
                String taxAuthGeoId = invoiceItem.getString("taxAuthGeoId");
                if (UtilValidate.isNotEmpty(taxAuthPartyId)) {
                    if (!result.containsKey(taxAuthPartyId)) {
                        Set<String> taxAuthGeos = new HashSet<>();
                        taxAuthGeos.add(taxAuthGeoId);
                        result.put(taxAuthPartyId, taxAuthGeos);
                    } else {
                        Set<String> taxAuthGeos = result.get(taxAuthPartyId);
                        taxAuthGeos.add(taxAuthGeoId);
                    }
                }
            }
        }
        // SCIPIO: Add Vat included
        try {
            String orderId = getOrderIdByInvoiceId(invoice.getDelegator(), invoice.getString("invoiceId"));
            List<GenericValue> orderAdjustments = EntityQuery.use(invoice.getDelegator()).from("OrderAdjustment")
                    .where(UtilMisc.toMap("orderId", orderId, "orderAdjustmentTypeId", "VAT_TAX")).queryList();
            for (GenericValue orderAdjustment : orderAdjustments) {
                String taxAuthPartyId = orderAdjustment.getString("taxAuthPartyId");
                String taxAuthGeoId = orderAdjustment.getString("taxAuthGeoId");
                if (UtilValidate.isNotEmpty(taxAuthPartyId)) {
                    if (!result.containsKey(taxAuthPartyId)) {
                        Set<String> taxAuthGeos = new HashSet<String>();
                        taxAuthGeos.add(taxAuthGeoId);
                        result.put(taxAuthPartyId, taxAuthGeos);
                    } else {
                        Set<String> taxAuthGeos = result.get(taxAuthPartyId);
                        taxAuthGeos.add(taxAuthGeoId);
                    }
                }
            }
        } catch (GenericEntityException e) {
            Debug.logError(e, module); // SCIPIO: 2018-08-13: remove printStackTrace
        }
        return result;
    }

    /**
     * @param invoice GenericValue object representing the invoice
     * @param taxAuthPartyId
     * @param taxAuthGeoId
     * @return The invoice tax total for a given tax authority and geo location
     */
    public static BigDecimal getInvoiceTaxTotalForTaxAuthPartyAndGeo(GenericValue invoice, String taxAuthPartyId, String taxAuthGeoId) {
        List<GenericValue> invoiceTaxItems = null;
        try {
            Delegator delegator = invoice.getDelegator();
            invoiceTaxItems = EntityQuery.use(delegator).from("InvoiceItem")
                    .where(EntityCondition.makeCondition("invoiceId", invoice.getString("invoiceId")),
                            EntityCondition.makeCondition("invoiceItemTypeId", EntityOperator.IN, getTaxableInvoiceItemTypeIds(delegator)),
                            EntityCondition.makeCondition("taxAuthPartyId", taxAuthPartyId),
                            EntityCondition.makeCondition("taxAuthGeoId", taxAuthGeoId)
                    ).queryList();
        } catch (GenericEntityException e) {
            Debug.logError(e, "Trouble getting InvoiceItem list", module);
            return null;
        }
        // SCIPIO: Vat Included
        /* TODO?: REVIEW: is taxAlreadyIncluded still needed later, or can be removed?
        BigDecimal taxAlreadyIncluded = BigDecimal.ZERO;
        try {
            String orderId = getOrderIdByInvoiceId(invoice.getDelegator(), invoice.getString("invoiceId"));
            List<GenericValue> orderAdjustments = EntityQuery.use(invoice.getDelegator()).from("OrderAdjustment")
                    .where(UtilMisc.toMap("orderId", orderId, "orderAdjustmentTypeId", "VAT_TAX"
                            , "taxAuthPartyId", taxAuthPartyId, "taxAuthGeoId", taxAuthGeoId)).queryList();
            taxAlreadyIncluded = getTaxAmountIncluded(orderAdjustments);
        } catch (GenericEntityException e) {
            Debug.logError(e, module); // SCIPIO: 2018-08-13: remove printStackTrace
        }
        return getTaxTotalForInvoiceItems(invoiceTaxItems).add(taxAlreadyIncluded);
        */
        return getTaxTotalForInvoiceItems(invoiceTaxItems);
    }

    /** Returns the invoice tax total for unattributed tax items, that is items which have no taxAuthPartyId value
     * @param invoice GenericValue object representing the invoice
     * @return Returns the invoice tax total for unattributed tax items
     */
    public static BigDecimal getInvoiceUnattributedTaxTotal(GenericValue invoice) {
         List<GenericValue> invoiceTaxItems = null;
         try {
             Delegator delegator = invoice.getDelegator();
             invoiceTaxItems = EntityQuery.use(delegator).from("InvoiceItem")
                     .where(EntityCondition.makeCondition("invoiceId", invoice.get("invoiceId")),
                             EntityCondition.makeCondition("invoiceItemTypeId", EntityOperator.IN, getTaxableInvoiceItemTypeIds(delegator)),
                             EntityCondition.makeCondition("taxAuthPartyId", null)
                     ).queryList();
         } catch (GenericEntityException e) {
             Debug.logError(e, "Trouble getting InvoiceItem list", module);
             return null;
         }
        return getTaxTotalForInvoiceItems(invoiceTaxItems);
    }

    /** Returns the tax total for a given list of tax typed InvoiceItem records
     * @param taxInvoiceItems
     * @return
     */
    private static BigDecimal getTaxTotalForInvoiceItems(List<GenericValue> taxInvoiceItems) {
        if (taxInvoiceItems == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal taxTotal = BigDecimal.ZERO;
        for (GenericValue taxInvoiceItem : taxInvoiceItems) {
            BigDecimal amount = taxInvoiceItem.getBigDecimal("amount");
            if (amount == null) {
                amount = BigDecimal.ZERO;
            }
            BigDecimal quantity = taxInvoiceItem.getBigDecimal("quantity");
            if (quantity == null) {
                quantity = BigDecimal.ONE;
            }
            amount = amount.multiply(quantity);
            amount = amount.setScale(taxDecimals, taxRounding);
            taxTotal = taxTotal.add(amount);
        }
        return taxTotal.setScale(decimals, rounding);
    }

    /**
     * SCIPIO: Get the order id for a specific invoice
     *
     * @param delegator
     * @param invoiceId
     * @return
     * @throws GenericEntityException
     */
    public static String getOrderIdByInvoiceId(Delegator delegator, String invoiceId) throws GenericEntityException {

        GenericValue orderItemBilling = EntityQuery.use(delegator)
            .from("OrderItemBilling")
            .where("invoiceId", invoiceId)
            .queryFirst();

        if (UtilValidate.isEmpty(orderItemBilling)) {
            Debug.logInfo("No OrderItemBilling for invoice [" + invoiceId + "]", module);

            return null;
        }

        return orderItemBilling.getString("orderId");
    }

    /**
     * SCIPIO: Get the orderItem+orderItemSeqId for a specific invoice item.
     * NOTE: This does not necessarily give an OrderItem value.
     */
    public static Map<String, Object> getInvoiceItemOrderItemInfo(Delegator delegator, String invoiceId, String invoiceItemSeqId) throws GenericEntityException {
        return EntityQuery.use(delegator).from("OrderItemBilling")
            .where("invoiceId", invoiceId, "invoiceItemSeqId", invoiceItemSeqId)
            .queryFirst();
    }

    /**
     * SCIPIO: Get the orderItem+orderItemSeqId for a specific invoice item.
     * NOTE: This does not necessarily give an OrderItem value.
     */
    public static Map<String, Object> getInvoiceItemOrderItemInfo(Delegator delegator, Map<String, ?> invoiceItem) throws GenericEntityException {
        return getInvoiceItemOrderItemInfo(delegator, (String) invoiceItem.get("invoiceId"), (String) invoiceItem.get("invoiceItemSeqId"));
    }

    /**
     * SCIPIO: Get the OrderItem for a specific invoice item.
     */
    public static GenericValue getInvoiceItemOrderItem(Delegator delegator, String invoiceId, String invoiceItemSeqId) throws GenericEntityException {
        GenericValue oib = EntityQuery.use(delegator).from("OrderItemBilling")
            .where("invoiceId", invoiceId, "invoiceItemSeqId", invoiceItemSeqId)
            .queryFirst();
        return (oib != null) ? oib.getRelatedOne("OrderItem") : null;
    }

    /**
     * SCIPIO: Get the OrderItem for a specific invoice item.
     */
    public static GenericValue getInvoiceItemOrderItem(Delegator delegator, Map<String, ?> invoiceItem) throws GenericEntityException {
        return getInvoiceItemOrderItem(delegator, (String) invoiceItem.get("invoiceId"), (String) invoiceItem.get("invoiceItemSeqId"));
    }

    /**
     * SCIPIO: Gets included tax amount out of Order Adjustments (either from TaxAuthority Services or OrderAdjustment)
     *
     * @param adjustments
     * @return Tax Amount, Zero if there are no adjustments
     * @throws GenericEntityException
     */
    public static BigDecimal getTaxAmountIncluded(List<GenericValue> adjustments) throws GenericEntityException {
        BigDecimal taxAmountIncluded = BigDecimal.ZERO;
        for (GenericValue adjustment : adjustments) {
            BigDecimal amountAlreadyIncluded = adjustment.getBigDecimal("amountAlreadyIncluded");
            taxAmountIncluded = taxAmountIncluded.add(amountAlreadyIncluded);
            BigDecimal exemptAmount = adjustment.getBigDecimal("exemptAmount");
            if (exemptAmount != null) {
                taxAmountIncluded = taxAmountIncluded.subtract(exemptAmount);
            }
        }
        return taxAmountIncluded.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * SCIPIO: Returns a List of the TaxAuthority RateProducts for the given Invoice.
     * @param invoice GenericValue object representing the Invoice
     * @return A Map containing the each taxAuthRateProduct as a key and a Set of ... (was: taxAuthGeoIds for that taxAuthPartyId as the values.  Note this method
     *         will not account for tax lines that do not contain a taxAuthPartyId)
     */
    public static Map<String, String> getInvoiceTaxRateProducts(GenericValue invoice) {
        Map<String, String> result = new HashMap<String, String>();

        if (invoice == null)
            throw new IllegalArgumentException("Invoice cannot be null.");
        Delegator delegator = invoice.getDelegator();
        List<GenericValue> invoiceTaxItems = null;
        try {
            EntityConditionList<EntityExpr> condition = EntityCondition.makeCondition(
                    UtilMisc.toList(
                            EntityCondition.makeCondition("invoiceId", invoice.getString("invoiceId")),
                            EntityCondition.makeCondition("invoiceItemTypeId", EntityOperator.IN,
                                    InvoiceWorker.getTaxableInvoiceItemTypeIds(delegator))), EntityOperator.AND);
            invoiceTaxItems = delegator.findList("InvoiceItem", condition, null, null, null, false);
        } catch (GenericEntityException e) {
            Debug.logError(e, "Trouble getting InvoiceItem list", module);
            return null;
        }
        try {
            if (invoiceTaxItems != null) {
                for (GenericValue invoiceItem : invoiceTaxItems) {
                    String taxAuthorityRateSeqId = invoiceItem.getString("taxAuthorityRateSeqId");
                    if (UtilValidate.isNotEmpty(taxAuthorityRateSeqId)) {
                        if (!result.containsKey(taxAuthorityRateSeqId)) {
                            String taxGlAccountId = null;
                            GenericValue taxAuthorityRateProduct = invoiceItem.getRelatedOne("TaxAuthorityRateProduct", true);
                            if (taxAuthorityRateProduct != null && taxAuthorityRateProduct.getModelEntity().isField("taxGlAccountId")) { // taxGlAccountId may only be present in addon...
                                taxGlAccountId = taxAuthorityRateProduct.getString("taxGlAccountId");
                            }
                            if (taxGlAccountId == null) {
                                // if there is no TaxAuthorityRateProduct then try with TaxAuthorityGlAccount
                                String organizationPartyId = invoice.getString("partyId");
                                String taxAuthGeoId = invoiceItem.getString("taxAuthGeoId");
                                String taxAuthPartyId = invoiceItem.getString("taxAuthPartyId");
                                GenericValue taxAuthorityGlAccount = delegator.findOne("TaxAuthorityGlAccount", UtilMisc.toMap(
                                        "organizationPartyId", organizationPartyId, "taxAuthGeoId", taxAuthGeoId, "taxAuthPartyId", taxAuthPartyId), true);
                                if (UtilValidate.isNotEmpty(taxAuthorityGlAccount)) {
                                    taxGlAccountId = taxAuthorityGlAccount.getString("taxGlAccountId");
                                }
                                // } else {
                                // taxAuthGeos.add(taxAuthGeoId);
                            }
                            if (taxGlAccountId != null) {
                                result.put(taxAuthorityRateSeqId, taxGlAccountId);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Debug.logError(e, "Trouble getting TaxAuthorityRateProduct/TaxAuthorityGlAccount for InvoiceItem: " + e, module);
            return null;
        }
        // SCIPIO: Vat Included
        // TODO: REVIEW: is taxAlreadyIncluded still needed later, or can be removed?
        //BigDecimal taxAlreadyIncluded = BigDecimal.ZERO;
        try {
            String orderId = getOrderIdByInvoiceId(invoice.getDelegator(), invoice.getString("invoiceId"));
            List<GenericValue> orderAdjustments = EntityQuery.use(invoice.getDelegator()).from("OrderAdjustment")
                    .where(UtilMisc.toMap("orderId", orderId, "orderAdjustmentTypeId", "VAT_TAX")).queryList();
            //taxAlreadyIncluded = getTaxAmountIncluded(orderAdjustments);
            for (GenericValue orderAdjustment : orderAdjustments) {
                String taxAuthorityRateSeqId = orderAdjustment.getString("taxAuthorityRateSeqId");
                if (UtilValidate.isNotEmpty(taxAuthorityRateSeqId)) {
                    if (!result.containsKey(taxAuthorityRateSeqId)) {
                        String taxGlAccountId = null;
                        GenericValue taxAuthorityRateProduct = orderAdjustment.getRelatedOne("TaxAuthorityRateProduct", true);
                        if (taxAuthorityRateProduct != null && taxAuthorityRateProduct.getModelEntity().isField("taxGlAccountId")) { // taxGlAccountId may only be present in addon...
                            taxGlAccountId = taxAuthorityRateProduct.getString("taxGlAccountId");
                        }
                        if (taxGlAccountId == null) {
                            // if there is no TaxAuthorityRateProduct then try with TaxAuthorityGlAccount
                            String organizationPartyId = invoice.getString("partyId");
                            String taxAuthGeoId = orderAdjustment.getString("taxAuthGeoId");
                            String taxAuthPartyId = orderAdjustment.getString("taxAuthPartyId");
                            GenericValue taxAuthorityGlAccount = delegator.findOne("TaxAuthorityGlAccount", UtilMisc.toMap(
                                    "organizationPartyId", organizationPartyId, "taxAuthGeoId", taxAuthGeoId, "taxAuthPartyId", taxAuthPartyId), true);
                            if (UtilValidate.isNotEmpty(taxAuthorityGlAccount)) {
                                taxGlAccountId = taxAuthorityGlAccount.getString("taxGlAccountId");
                            }
                        }
                        if (taxGlAccountId != null) {
                            result.put(taxAuthorityRateSeqId, taxGlAccountId);
                        }
                    }
                }
            }
        } catch (GenericEntityException e) {
            Debug.logError(e, module); // SCIPIO: 2018-08-13: remove printStackTrace
        }
        return result;
    }

    /**
     * SCIPIO: getInvoiceTaxTotalForTaxGlAccount.
     * @param invoice GenericValue object representing the invoice
     * @param taxAuthPartyId
     * @param taxAuthGeoId
     * @return The invoice tax total for a given tax authority and geo location
     */
    public static BigDecimal getInvoiceTaxTotalForTaxGlAccount(GenericValue invoice, String glAccountId) {
        List<GenericValue> invoiceTaxItems = null;
        try {
            Delegator delegator = invoice.getDelegator();
            EntityConditionList<EntityExpr> condition = EntityCondition.makeCondition(
                    UtilMisc.toList(
                        EntityCondition.makeCondition("invoiceId", invoice.getString("invoiceId")),
                        EntityCondition.makeCondition("invoiceItemTypeId", EntityOperator.IN,
                                InvoiceWorker.getTaxableInvoiceItemTypeIds(delegator))), EntityOperator.AND);
            invoiceTaxItems = delegator.findList("InvoiceItem", condition, null, null, null, false);
            for (GenericValue invoiceItem : invoiceTaxItems) {
                String taxGlAccountId = null;
                // TODO: getTaxGlAccountForInvoiceItem(invoiceTaxItem);
                GenericValue taxAuthorityRateProduct = invoiceItem.getRelatedOne("TaxAuthorityRateProduct", true);
                if (taxAuthorityRateProduct != null && taxAuthorityRateProduct.getModelEntity().isField("taxGlAccountId")) { // taxGlAccountId may only be present in addon...
                    taxGlAccountId = taxAuthorityRateProduct.getString("taxGlAccountId");
                }
                if (taxGlAccountId == null) {
                    // if there is no TaxAuthorityRateProduct then try with TaxAuthorityGlAccount
                    String organizationPartyId = invoice.getString("partyId");
                    String taxAuthGeoId = invoiceItem.getString("taxAuthGeoId");
                    String taxAuthPartyId = invoiceItem.getString("taxAuthPartyId");
                    GenericValue taxAuthorityGlAccount = delegator.findOne("TaxAuthorityGlAccount", UtilMisc.toMap(
                            "organizationPartyId", organizationPartyId, "taxAuthGeoId", taxAuthGeoId, "taxAuthPartyId", taxAuthPartyId), true);
                    if (UtilValidate.isNotEmpty(taxAuthorityGlAccount)) {
                        taxGlAccountId = taxAuthorityGlAccount.getString("taxGlAccountId");
                    }
                }
                if (taxGlAccountId != null && !taxGlAccountId.equals(glAccountId)) {
                    invoiceTaxItems.remove(invoiceItem);
                }
            }
        } catch (GenericEntityException e) {
            Debug.logError(e, "Trouble getting InvoiceItem list", module);
            return null;
        }
        // Vat Included
        BigDecimal taxAlreadyIncluded = BigDecimal.ZERO;
        List<GenericValue> orderAdjustmentsTaxItems ;
        List<GenericValue> orderAdjustmentsTaxItemsToProcess ;
        try {
            String orderId = getOrderIdByInvoiceId(invoice.getDelegator(), invoice.getString("invoiceId"));
            orderAdjustmentsTaxItems = EntityQuery.use(invoice.getDelegator()).from("OrderAdjustment")
                    .where(UtilMisc.toMap("orderId", orderId, "orderAdjustmentTypeId", "VAT_TAX")).queryList();
            //taxAlreadyIncluded = getTaxAmountIncluded(orderAdjustments);
            orderAdjustmentsTaxItemsToProcess = new ArrayList<>(orderAdjustmentsTaxItems);
            for (GenericValue orderAdjustment : orderAdjustmentsTaxItems) {
                String taxGlAccountId = null;
                // TODO: getTaxGlAccountForInvoiceItem(orderAdjustment);
                GenericValue taxAuthorityRateProduct = orderAdjustment.getRelatedOne("TaxAuthorityRateProduct", true);
                if (taxAuthorityRateProduct != null && taxAuthorityRateProduct.getModelEntity().isField("taxGlAccountId")) { // taxGlAccountId may only be present in addon...
                    taxGlAccountId = taxAuthorityRateProduct.getString("taxGlAccountId");
                }
                if (taxGlAccountId == null) {
                    // if there is no TaxAuthorityRateProduct then try with TaxAuthorityGlAccount
                    String organizationPartyId = invoice.getString("partyId");
                    String taxAuthGeoId = orderAdjustment.getString("taxAuthGeoId");
                    String taxAuthPartyId = orderAdjustment.getString("taxAuthPartyId");
                    GenericValue taxAuthorityGlAccount = invoice.getDelegator().findOne("TaxAuthorityGlAccount", UtilMisc.toMap(
                            "organizationPartyId", organizationPartyId, "taxAuthGeoId", taxAuthGeoId, "taxAuthPartyId", taxAuthPartyId), true);
                    if (UtilValidate.isNotEmpty(taxAuthorityGlAccount)) {
                        taxGlAccountId = taxAuthorityGlAccount.getString("taxGlAccountId");
                    }
                }
                if (taxGlAccountId != null && !taxGlAccountId.equals(glAccountId)) {
                    orderAdjustmentsTaxItemsToProcess.remove(orderAdjustment);
                }
            }
            taxAlreadyIncluded = taxAlreadyIncluded.add(getTaxAmountIncluded(orderAdjustmentsTaxItemsToProcess));
        } catch (GenericEntityException e) {
            Debug.logError(e, "Error while getting", module);
        }
        BigDecimal taxForInvoiceItems = InvoiceWorker.getTaxTotalForInvoiceItems(invoiceTaxItems);
        BigDecimal taxTotal = taxForInvoiceItems.add(taxAlreadyIncluded);
       return taxTotal;
    }

    /**
     * SCIPIO: Method to return the amount of tax included of an invoiceItem
     *
     * @param invoiceItem GenericValue object of the invoice item
     * @return tax amount included as BigDecimal
     * @throws GenericEntityException
     */
    public static BigDecimal getInvoiceItemTaxIncluded(GenericValue invoiceItem) throws GenericEntityException {
        BigDecimal taxIncluded = null;
        if (UtilValidate.isNotEmpty(invoiceItem)) {
            taxIncluded = BigDecimal.ZERO;
            List<GenericValue> orderItemBillings = invoiceItem.getRelated("OrderItemBilling", null, null, false);
            for (GenericValue orderItemBilling : orderItemBillings) {
                GenericValue orderItem = orderItemBilling.getRelatedOne("OrderItem", true);
                taxIncluded = getOrderItemTaxIncluded(orderItem);
            }
        }
        return taxIncluded;
    }

    /**
     * SCIPIO: Method to return the amount of tax included of an orderItem
     *
     * @param orderItem
     *            GenericValue object of the order item
     * @return tax amount included as BigDecimal
     * @throws GenericEntityException
     */
    public static BigDecimal getOrderItemTaxIncluded(GenericValue orderItem)
            throws GenericEntityException {
        BigDecimal taxIncluded = null;
        if (UtilValidate.isNotEmpty(orderItem)) {
            taxIncluded = BigDecimal.ZERO;
            List<GenericValue> orderAdjustments = orderItem.getRelated(
                    "OrderAdjustment",
                    UtilMisc.toMap("orderAdjustmentTypeId", "VAT_TAX"), null,
                    true);
            taxIncluded = getTaxAmountIncluded(orderAdjustments);
        }
        return taxIncluded;
    }
}

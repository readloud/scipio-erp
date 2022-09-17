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
package org.ofbiz.order.shoppingcart;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.GeneralException;
import org.ofbiz.base.util.GeneralRuntimeException;
import org.ofbiz.base.util.StringUtil;
import org.ofbiz.base.util.UtilDateTime;
import org.ofbiz.base.util.UtilFormatOut;
import org.ofbiz.base.util.UtilGenerics;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilNumber;
import org.ofbiz.base.util.UtilProperties;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.common.DataModelConstants;
import org.ofbiz.entity.Delegator;
import org.ofbiz.entity.DelegatorFactory;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericPK;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.util.EntityQuery;
import org.ofbiz.entity.util.EntityUtil;
import org.ofbiz.entity.util.EntityUtilProperties;
import org.ofbiz.order.finaccount.FinAccountHelper;
import org.ofbiz.order.order.OrderReadHelper;
import org.ofbiz.order.shoppingcart.product.ProductPromoWorker;
import org.ofbiz.order.shoppingcart.shipping.ShippingEstimateWrapper;
import org.ofbiz.order.shoppinglist.ShoppingListEvents;
import org.ofbiz.party.contact.ContactHelper;
import org.ofbiz.party.contact.ContactMechWorker;
import org.ofbiz.product.category.CategoryWorker;
import org.ofbiz.product.config.ProductConfigWrapper;
import org.ofbiz.product.product.ProductWorker;
import org.ofbiz.product.store.ProductStoreWorker;
import org.ofbiz.service.GenericServiceException;
import org.ofbiz.service.LocalDispatcher;
import org.ofbiz.service.ServiceUtil;

/**
 * Shopping Cart Object
 * <p>
 * SCIPIO: IMPORTANT: 2018-11-22: Any event or code which modifies the main shopping cart stored in session ("shoppingCart") 
 * must now wrap its update code in a {@link CartUpdate#updateSection} or {@link CartSync#synchronizedSection} section.
 * The main instance currently stored in session/request attribute is now considered immutable and can only be updated using
 * an update section (which creates a modifiable copy that may be committed to replace the main session cart instance).
 *
 * @see CartUpdate
 * @see CartSync
 */
@SuppressWarnings("serial")
public class ShoppingCart implements Iterable<ShoppingCartItem>, Serializable {

    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());
    public static final String resource_error = "OrderErrorUiLabels";

    // modes for getting OrderItemAttributes
    public static final int ALL = 1;
    public static final int EMPTY_ONLY = 2;
    public static final int FILLED_ONLY = 3;

    // scales and rounding modes for BigDecimal math
    public static final int scale = UtilNumber.getBigDecimalScale("order.decimals");
    public static final RoundingMode rounding = UtilNumber.getRoundingMode("order.rounding");
    public static final int taxCalcScale = UtilNumber.getBigDecimalScale("salestax.calc.decimals");
    public static final int taxFinalScale = UtilNumber.getBigDecimalScale("salestax.final.decimals");
    public static final RoundingMode taxRounding = UtilNumber.getRoundingMode("salestax.rounding");
    /**
     * @deprecated SCIPIO: 2018-10-09: use {@link BigDecimal#ZERO}.
     */
    @Deprecated
    public static final BigDecimal ZERO = BigDecimal.ZERO;
    public static final BigDecimal percentage = new BigDecimal("0.01");
    public static final MathContext generalRounding = new MathContext(10);

    public static final boolean DEBUG = UtilProperties.getPropertyAsBoolean("order", "shoppingcart.debug", false); // SCIPIO

    // SCIPIO: NOTE: 2018-11-22: Many default values have been moved to the constructors, or removed and defaults used (null/false)

    private String orderType = "SALES_ORDER"; // default orderType
    private String channel = "UNKNWN_SALES_CHANNEL"; // default channel enum

    private String poNumber;
    private String orderId;
    private String orderName;
    private String orderStatusId;
    private String orderStatusString;
    private String firstAttemptOrderId;
    private String externalId;
    private String internalCode;
    private String billingAccountId;
    private BigDecimal billingAccountAmt = BigDecimal.ZERO;
    private String agreementId;
    private String quoteId;
    private String workEffortId;
    private long nextItemSeq = 1;

    private String defaultItemDeliveryDate;
    private String defaultItemComment;

    private String orderAdditionalEmails;
    private boolean viewCartOnAdd; // = false;
    private boolean readOnlyCart; // = false;

    private Timestamp lastListRestore;
    private String autoSaveListId;

    // SCIPIO: Changed all LinkedList to ArrayList

    /** Holds value of order adjustments. */
    private List<GenericValue> adjustments = new ArrayList<>();
    // OrderTerms
    private boolean orderTermSet; // = false;
    private List<GenericValue> orderTerms = new ArrayList<>();

    private List<ShoppingCartItem> cartLines = new ArrayList<>();
    private Map<String, ShoppingCartItemGroup> itemGroupByNumberMap = new HashMap<>();
    protected long nextGroupNumber = 1;
    private List<CartPaymentInfo> paymentInfo = new ArrayList<>();
    private List<CartShipInfo> shipInfo = new ArrayList<>();
    private Map<String, String> contactMechIdsMap = new HashMap<>();
    private Map<String, String> orderAttributes = new HashMap<>();
    private Map<String, Object> attributes = new HashMap<>(); // user defined attributes
    // Lists of internal/public notes: when the order is stored they are transformed into OrderHeaderNotes
    private List<String> internalOrderNotes = new ArrayList<>(); // internal notes
    private List<String> orderNotes = new ArrayList<>(); // public notes (printed on documents etc.)

    /** contains a list of partyId for each roleTypeId (key) */
    private Map<String, List<String>> additionalPartyRole = new HashMap<>();

    /** these are defaults for all ship groups */
    private Timestamp defaultShipAfterDate;
    private Timestamp defaultShipBeforeDate;

    /** Contains a List for each productPromoId (key) containing a productPromoCodeId (or empty string for no code) for each use of the productPromoId */
    private List<ProductPromoUseInfo> productPromoUseInfoList = new ArrayList<>();
    /** Contains the promo codes entered */
    private Set<String> productPromoCodes = new HashSet<>();
    private List<GenericValue> freeShippingProductPromoActions = new ArrayList<>();
    /** Note that even though this is promotion info, it should NOT be cleared when the promos are cleared, it is a preference that will be used in the next promo calculation */
    private Map<GenericPK, String> desiredAlternateGiftByAction = new HashMap<>();
    private Timestamp cartCreatedTs = UtilDateTime.nowTimestamp();

    private transient Delegator delegator;
    private String delegatorName;

    protected String productStoreId;
    protected boolean doPromotions = true;
    protected String transactionId;
    protected String facilityId;
    protected String webSiteId;
    protected String terminalId;
    protected String autoOrderShoppingListId;

    /** General partyId for the Order, all other IDs default to this one if not specified explicitly */
    protected String orderPartyId;

    // sales order parties
    protected String placingCustomerPartyId;
    protected String billToCustomerPartyId;
    protected String shipToCustomerPartyId;
    protected String endUserCustomerPartyId;

    // purchase order parties
    protected String billFromVendorPartyId;
    protected String shipFromVendorPartyId;
    protected String supplierAgentPartyId;

    protected GenericValue userLogin;
    protected GenericValue autoUserLogin;

    protected Locale locale;  // holds the locale from the user session
    protected String currencyUom;
    protected boolean holdOrder; // = false;
    protected Timestamp orderDate;
    protected Timestamp cancelBackOrderDate;

    // SCIPIO: Cart item subscriptions
    /* 2018-11-22: This cache is problematic due to both thread safety and because it risks going out of
     * sync with some cart operations, so we simply omit it for now... the lookups will rely on entity cache instead.
     * NOTE: ShoppingCart cannot be treated like OrderReadHelper (global vs local).
     * DEV NOTE: If a transient cache is ever really needed on ShoppingCart, this should go on
     * ShoppingCartItem instead as a List<GenericValue> field.
    protected Map<String, List<GenericValue>> cartSubscriptionItems;
    */

    protected boolean allowMissingShipEstimates; // = false; // SCIPIO: see WebShoppingCart for implementation

    /** don't allow empty constructor */
    protected ShoppingCart() {}

    /** Creates a new cloned ShoppingCart Object.
     * SCIPIO: NOTE: This is legacy copy constructor with exactCopy==false. */
    public ShoppingCart(ShoppingCart cart) {
        this(cart, false);
    }

    /** Creates a new cloned ShoppingCart Object.
     * SCIPIO: Added exactCopy flag, toggles between legacy (partial) and full/exact cloning the whole cart. */
    public ShoppingCart(ShoppingCart cart, boolean exactCopy) {
        if (exactCopy) {
            // Exact/full instance copy (SCIPIO)
            this.delegator = cart.delegator;
            this.delegatorName = cart.delegatorName;
            this.productStoreId = cart.productStoreId;
            this.doPromotions = cart.doPromotions;
            this.poNumber = cart.poNumber;
            this.orderId = cart.orderId;
            this.orderName = cart.orderName;
            this.workEffortId = cart.workEffortId;
            this.firstAttemptOrderId = cart.firstAttemptOrderId;
            this.billingAccountId = cart.billingAccountId;
            this.agreementId = cart.agreementId;
            this.quoteId = cart.quoteId;
            this.orderAdditionalEmails = cart.orderAdditionalEmails;
            // SCIPIO: Replace it
            //this.adjustments.addAll(cart.getAdjustments());
            this.adjustments = new ArrayList<>(cart.adjustments);
            this.contactMechIdsMap = new HashMap<>(cart.contactMechIdsMap);
            this.freeShippingProductPromoActions = new ArrayList<>(cart.freeShippingProductPromoActions);
            this.desiredAlternateGiftByAction = cart.getAllDesiredAlternateGiftByActionCopy();
            
            // clone the groups
            this.itemGroupByNumberMap = copyItemGroupByNumberMap(exactCopy, cart.itemGroupByNumberMap);
            // clone the items
            Map<ShoppingCartItem, ShoppingCartItem> oldToNewItemMap = new HashMap<>();
            List<ShoppingCartItem> cartLines = new ArrayList<>(); // SCIPIO: Use local var
            for (ShoppingCartItem item : cart.items()) {
                ShoppingCartItem newItem = new ShoppingCartItem(item, exactCopy, itemGroupByNumberMap);
                cartLines.add(newItem);
                oldToNewItemMap.put(item, newItem);
            }
            this.cartLines = cartLines;

            // SCIPIO: Replace it
            //this.productPromoUseInfoList.addAll(cart.productPromoUseInfoList);
            List<ProductPromoUseInfo> productPromoUseInfoList = new ArrayList<>(cart.productPromoUseInfoList.size());
            for(ProductPromoUseInfo ppui : cart.productPromoUseInfoList) {
                productPromoUseInfoList.add(new ProductPromoUseInfo(ppui, exactCopy, oldToNewItemMap));
            }
            this.productPromoUseInfoList = productPromoUseInfoList;
            this.productPromoCodes = new HashSet<>(cart.productPromoCodes);
            this.locale = cart.locale;
            this.currencyUom = cart.currencyUom;
            this.externalId = cart.externalId;
            this.internalCode = cart.internalCode;
            this.viewCartOnAdd = cart.viewCartOnAdd;
            this.defaultShipAfterDate = cart.defaultShipAfterDate;
            this.defaultShipBeforeDate = cart.defaultShipBeforeDate;
            this.cancelBackOrderDate = cart.cancelBackOrderDate;
    
            this.terminalId = cart.terminalId;
            this.transactionId = cart.transactionId;
            this.autoOrderShoppingListId = cart.autoOrderShoppingListId;

            // SCIPIO: Stock fields not covered by legacy copy constructor
            this.orderType = cart.orderType;
            this.channel = cart.channel;
            this.orderStatusId = cart.orderStatusId;
            this.orderStatusString = cart.orderStatusString;
            this.billingAccountAmt = cart.billingAccountAmt;
            this.nextItemSeq = cart.nextItemSeq;
            this.defaultItemDeliveryDate = cart.defaultItemDeliveryDate;
            this.defaultItemComment = cart.defaultItemComment;
            this.readOnlyCart = cart.readOnlyCart;
            this.lastListRestore = cart.lastListRestore;
            this.autoSaveListId = cart.autoSaveListId;
            this.orderTermSet = cart.orderTermSet;
            this.orderTerms = new ArrayList<>(cart.orderTerms);
            this.nextGroupNumber = cart.nextGroupNumber;
            
            List<CartPaymentInfo> paymentInfo = new ArrayList<>();
            for(CartPaymentInfo cpi : cart.paymentInfo) {
                paymentInfo.add(new CartPaymentInfo(cpi, exactCopy));
            }
            this.paymentInfo = paymentInfo;
            
            List<CartShipInfo> shipInfo = new ArrayList<>();
            for(CartShipInfo csi : cart.shipInfo) {
                shipInfo.add(new CartShipInfo(csi, exactCopy, oldToNewItemMap));
            }
            this.shipInfo = shipInfo;

            this.orderAttributes = new HashMap<>(cart.orderAttributes);
            this.attributes = new HashMap<>(cart.attributes);
            this.internalOrderNotes = new ArrayList<>(cart.internalOrderNotes);
            this.orderNotes = new ArrayList<>(cart.orderNotes);
            this.cartCreatedTs = cart.cartCreatedTs;
            this.orderPartyId = cart.orderPartyId;
            this.placingCustomerPartyId = cart.placingCustomerPartyId;
            this.billToCustomerPartyId = cart.billToCustomerPartyId;
            this.shipToCustomerPartyId = cart.shipToCustomerPartyId;
            this.endUserCustomerPartyId = cart.endUserCustomerPartyId;
            this.billFromVendorPartyId = cart.billFromVendorPartyId;
            this.shipFromVendorPartyId = cart.shipFromVendorPartyId;
            this.supplierAgentPartyId = cart.supplierAgentPartyId;
            this.userLogin = cart.userLogin;
            this.autoUserLogin = cart.autoUserLogin;
            this.holdOrder = cart.holdOrder;
            this.orderDate = cart.orderDate;
            
            // SCIPIO: new fields

            /* 2018-11-22: Removed: cartSubscriptionItems cache is counter-productive
            // SCIPIO
            Map<String, List<GenericValue>> cartSubscriptionItems = null;
            if (cart.cartSubscriptionItems != null) {
                cartSubscriptionItems = new HashMap<>();
                for(Map.Entry<String, List<GenericValue>> entry : cart.cartSubscriptionItems.entrySet()) {
                    cartSubscriptionItems.put(entry.getKey(), (entry.getValue() != null) ? new ArrayList<>(entry.getValue()) : null);
                }
            }
            this.cartSubscriptionItems = cartSubscriptionItems;
            */
        } else {
            // Partial/high-level instance copy (legacy)
            this.delegator = cart.getDelegator();
            this.delegatorName = delegator.getDelegatorName();
            this.productStoreId = cart.getProductStoreId();
            this.doPromotions = cart.getDoPromotions();
            this.poNumber = cart.getPoNumber();
            this.orderId = cart.getOrderId();
            this.orderName = "Copy of " + cart.getOrderName();
            this.workEffortId = cart.getWorkEffortId();
            this.firstAttemptOrderId = cart.getFirstAttemptOrderId();
            this.billingAccountId = cart.getBillingAccountId();
            this.agreementId = cart.getAgreementId();
            this.quoteId = cart.getQuoteId();
            this.orderAdditionalEmails = cart.getOrderAdditionalEmails();
            // SCIPIO: Replace it
            //this.adjustments.addAll(cart.getAdjustments());
            this.adjustments = new ArrayList<>(cart.getAdjustments());
            this.contactMechIdsMap = new HashMap<>(cart.getOrderContactMechIds());
            this.freeShippingProductPromoActions = new ArrayList<>(cart.getFreeShippingProductPromoActions());
            this.desiredAlternateGiftByAction = cart.getAllDesiredAlternateGiftByActionCopy();
            // SCIPIO: Replace it
            //this.productPromoUseInfoList.addAll(cart.productPromoUseInfoList);
            this.productPromoUseInfoList = new ArrayList<>(cart.productPromoUseInfoList);
            this.productPromoCodes = new HashSet<>(cart.productPromoCodes);
            this.locale = cart.getLocale();
            this.currencyUom = cart.getCurrency();
            this.externalId = cart.getExternalId();
            this.internalCode = cart.getInternalCode();
            this.viewCartOnAdd = cart.viewCartOnAdd();
            this.defaultShipAfterDate = cart.getDefaultShipAfterDate();
            this.defaultShipBeforeDate = cart.getDefaultShipBeforeDate();
            this.cancelBackOrderDate = cart.getCancelBackOrderDate();
    
            this.terminalId = cart.getTerminalId();
            this.transactionId = cart.getTransactionId();
            this.autoOrderShoppingListId = cart.getAutoOrderShoppingListId();
            
            // clone the groups
            /* SCIPIO: This is wrong; there is nothing guarantees parents will be iterated before children in a HashMap
            for (ShoppingCartItemGroup itemGroup : cart.itemGroupByNumberMap.values()) {
                // get the new parent group by number from the existing set; as before the parent must come before all children to work...
                ShoppingCartItemGroup parentGroup = null;
                if (itemGroup.getParentGroup() != null) {
                    parentGroup = this.getItemGroupByNumber(itemGroup.getParentGroup().getGroupNumber());
                }
                ShoppingCartItemGroup newGroup = new ShoppingCartItemGroup(itemGroup, parentGroup);
                itemGroupByNumberMap.put(newGroup.getGroupNumber(), newGroup);
            }
            */
            this.itemGroupByNumberMap = copyItemGroupByNumberMap(false, cart.itemGroupByNumberMap);

            // clone the items
            List<ShoppingCartItem> cartLines = new ArrayList<>(); // SCIPIO: Use local var
            for (ShoppingCartItem item : cart.items()) {
                cartLines.add(new ShoppingCartItem(item));
            }
            this.cartLines = cartLines;
        }

        // clone the additionalPartyRoleMap
        // SCIPIO: Use local var
        //this.additionalPartyRole = new HashMap<>();
        Map<String, List<String>> additionalPartyRole = new HashMap<>();
        for (Map.Entry<String, List<String>> me : cart.additionalPartyRole.entrySet()) {
            additionalPartyRole.put(me.getKey(), new ArrayList<>(me.getValue()));
        }
        this.additionalPartyRole = additionalPartyRole;

        this.facilityId = cart.facilityId;
        this.webSiteId = cart.webSiteId;

        this.allowMissingShipEstimates = cart.allowMissingShipEstimates; // SCIPIO
    }

    /**
     * SCIPIO: Deep copy of itemGroupByNumberMap (fixes flawed stock logic) with parent references resolved.
     */
    private Map<String, ShoppingCartItemGroup> copyItemGroupByNumberMap(boolean exactCopy, Map<String, ShoppingCartItemGroup> itemGroupByNumberMap) {
        Map<String, ShoppingCartItemGroup> newMap = new HashMap<>(); // SCIPIO: Use local var
        for (ShoppingCartItemGroup itemGroup : itemGroupByNumberMap.values()) {
            if (!newMap.containsKey(itemGroup.getGroupNumber())) { // Parent may be already created
                copyItemGroupParentsFirst(itemGroup, exactCopy, newMap);
            }
        }
        return newMap;
    }

    /**
     * SCIPIO: Recursively registers the group, depth-first starting with parent.
     */
    private ShoppingCartItemGroup copyItemGroupParentsFirst(ShoppingCartItemGroup itemGroup, boolean exactCopy, Map<String, ShoppingCartItemGroup> newMap) {
        ShoppingCartItemGroup newParentGroup = null;
        if (itemGroup.getParentGroup() != null) {
            newParentGroup = newMap.get(itemGroup.getParentGroup().getGroupNumber());
            if (newParentGroup == null) {
                newParentGroup = copyItemGroupParentsFirst(itemGroup.getParentGroup(), exactCopy, newMap);
            }
        }
        ShoppingCartItemGroup newGroup = new ShoppingCartItemGroup(itemGroup, newParentGroup);
        newMap.put(newGroup.getGroupNumber(), newGroup);
        return newGroup;
    }
    
    /** Creates new empty ShoppingCart object. */
    public ShoppingCart(Delegator delegator, String productStoreId, String webSiteId, Locale locale, String currencyUom, String billToCustomerPartyId, String billFromVendorPartyId) {

        this.delegator = delegator;
        this.delegatorName = delegator.getDelegatorName();
        this.productStoreId = productStoreId;
        this.webSiteId = webSiteId;
        this.locale = (locale != null) ? locale : Locale.getDefault();
        this.currencyUom = (currencyUom != null) ? currencyUom : EntityUtilProperties.getPropertyValue("general", "currency.uom.id.default", "USD", delegator);
        this.billToCustomerPartyId = billToCustomerPartyId;
        this.billFromVendorPartyId = billFromVendorPartyId;

        if (productStoreId != null) {

            // set the default view cart on add for this store
            GenericValue productStore = ProductStoreWorker.getProductStore(productStoreId, delegator);
            if (productStore == null) {
                throw new IllegalArgumentException("Unable to locate ProductStore by ID [" + productStoreId + "]");
            }

            String storeViewCartOnAdd = productStore.getString("viewCartOnAdd");
            if (storeViewCartOnAdd != null && "Y".equalsIgnoreCase(storeViewCartOnAdd)) {
                this.viewCartOnAdd = true;
            }

            if (billFromVendorPartyId == null) {
                // since default cart is of type SALES_ORDER, set to store's payToPartyId
                this.billFromVendorPartyId = productStore.getString("payToPartyId");
            }
            this.facilityId = productStore.getString("inventoryFacilityId");
        }

    }


    /** Creates new empty ShoppingCart object. */
    public ShoppingCart(Delegator delegator, String productStoreId, String webSiteId, Locale locale, String currencyUom) {
        this(delegator, productStoreId, webSiteId, locale, currencyUom, null, null);
    }

    /** Creates a new empty ShoppingCart object. */
    public ShoppingCart(Delegator delegator, String productStoreId, Locale locale, String currencyUom) {
        this(delegator, productStoreId, null, locale, currencyUom);
    }

    /** SCIPIO: Performs an exact, deep copy of the cart.
     * Changes to this copy do not affect the main cart. Added 2018-11-16. */
    public ShoppingCart exactCopy() {
        return new ShoppingCart(this, true);
    }
    
    /**
     * SCIPIO: Tests to ensure the cart is an exact copy of the other; used to verify {@link #exactCopy}.
     * NOTE: This is NOT the same as a logical Object equals override! This is mainly for testing.
     */
    void ensureExactEquals(ShoppingCart other) throws IllegalStateException {
        try {
            ShoppingCart.ensureExactEquals(this.orderType, other.orderType);
            ShoppingCart.ensureExactEquals(this.channel, other.channel);
            ShoppingCart.ensureExactEquals(this.poNumber, other.poNumber);
            ShoppingCart.ensureExactEquals(this.orderId, other.orderId);
            ShoppingCart.ensureExactEquals(this.orderName, other.orderName);
            ShoppingCart.ensureExactEquals(this.orderStatusId, other.orderStatusId);
            ShoppingCart.ensureExactEquals(this.orderStatusString, other.orderStatusString);
            ShoppingCart.ensureExactEquals(this.firstAttemptOrderId, other.firstAttemptOrderId);
            ShoppingCart.ensureExactEquals(this.externalId, other.externalId);
            ShoppingCart.ensureExactEquals(this.internalCode, other.internalCode);
            ShoppingCart.ensureExactEquals(this.billingAccountId, other.billingAccountId);
            ShoppingCart.ensureExactEquals(this.billingAccountAmt, other.billingAccountAmt);
            ShoppingCart.ensureExactEquals(this.agreementId, other.agreementId);
            ShoppingCart.ensureExactEquals(this.quoteId, other.quoteId);
            ShoppingCart.ensureExactEquals(this.workEffortId, other.workEffortId);
            ShoppingCart.ensureExactEquals(this.nextItemSeq, other.nextItemSeq);
            ShoppingCart.ensureExactEquals(this.defaultItemDeliveryDate, other.defaultItemDeliveryDate);
            ShoppingCart.ensureExactEquals(this.defaultItemComment, other.defaultItemComment);
            ShoppingCart.ensureExactEquals(this.orderAdditionalEmails, other.orderAdditionalEmails);
            ShoppingCart.ensureExactEquals(this.viewCartOnAdd, other.viewCartOnAdd);
            ShoppingCart.ensureExactEquals(this.readOnlyCart, other.readOnlyCart);
            ShoppingCart.ensureExactEquals(this.lastListRestore, other.lastListRestore);
            ShoppingCart.ensureExactEquals(this.autoSaveListId, other.autoSaveListId);
            ShoppingCart.ensureExactEquals(this.adjustments, other.adjustments);
            ShoppingCart.ensureExactEquals(this.orderTermSet, other.orderTermSet);
            ShoppingCart.ensureExactEquals(this.orderTerms, other.orderTerms);
            ShoppingCart.ensureExactEquals(this.cartLines, other.cartLines);
            ShoppingCart.ensureExactEquals(this.itemGroupByNumberMap, other.itemGroupByNumberMap);
            ShoppingCart.ensureExactEquals(this.nextGroupNumber, other.nextGroupNumber);
            ShoppingCart.ensureExactEquals(this.paymentInfo, other.paymentInfo);
            ShoppingCart.ensureExactEquals(this.shipInfo, other.shipInfo);
            ShoppingCart.ensureExactEquals(this.contactMechIdsMap, other.contactMechIdsMap);
            ShoppingCart.ensureExactEquals(this.orderAttributes, other.orderAttributes);
            ShoppingCart.ensureExactEquals(this.attributes, other.attributes);
            ShoppingCart.ensureExactEquals(this.internalOrderNotes, other.internalOrderNotes);
            ShoppingCart.ensureExactEquals(this.orderNotes, other.orderNotes);
            ShoppingCart.ensureExactEquals(this.additionalPartyRole, other.additionalPartyRole);
            ShoppingCart.ensureExactEquals(this.defaultShipAfterDate, other.defaultShipAfterDate);
            ShoppingCart.ensureExactEquals(this.defaultShipBeforeDate, other.defaultShipBeforeDate);
            ShoppingCart.ensureExactEquals(this.productPromoUseInfoList, other.productPromoUseInfoList);
            ShoppingCart.ensureExactEquals(this.productPromoCodes, other.productPromoCodes);
            ShoppingCart.ensureExactEquals(this.freeShippingProductPromoActions, other.freeShippingProductPromoActions);
            ShoppingCart.ensureExactEquals(this.desiredAlternateGiftByAction, other.desiredAlternateGiftByAction);
            ShoppingCart.ensureExactEquals(this.cartCreatedTs, other.cartCreatedTs);
            ShoppingCart.ensureExactEquals(this.delegator, other.delegator);
            ShoppingCart.ensureExactEquals(this.delegatorName, other.delegatorName);
            ShoppingCart.ensureExactEquals(this.productStoreId, other.productStoreId);
            ShoppingCart.ensureExactEquals(this.doPromotions, other.doPromotions);
            ShoppingCart.ensureExactEquals(this.transactionId, other.transactionId);
            ShoppingCart.ensureExactEquals(this.facilityId, other.facilityId);
            ShoppingCart.ensureExactEquals(this.webSiteId, other.webSiteId);
            ShoppingCart.ensureExactEquals(this.terminalId, other.terminalId);
            ShoppingCart.ensureExactEquals(this.autoOrderShoppingListId, other.autoOrderShoppingListId);
            ShoppingCart.ensureExactEquals(this.orderPartyId, other.orderPartyId);
            ShoppingCart.ensureExactEquals(this.placingCustomerPartyId, other.placingCustomerPartyId);
            ShoppingCart.ensureExactEquals(this.billToCustomerPartyId, other.billToCustomerPartyId);
            ShoppingCart.ensureExactEquals(this.shipToCustomerPartyId, other.shipToCustomerPartyId);
            ShoppingCart.ensureExactEquals(this.endUserCustomerPartyId, other.endUserCustomerPartyId);
            ShoppingCart.ensureExactEquals(this.billFromVendorPartyId, other.billFromVendorPartyId);
            ShoppingCart.ensureExactEquals(this.shipFromVendorPartyId, other.shipFromVendorPartyId);
            ShoppingCart.ensureExactEquals(this.supplierAgentPartyId, other.supplierAgentPartyId);
            ShoppingCart.ensureExactEquals(this.userLogin, other.userLogin);
            ShoppingCart.ensureExactEquals(this.autoUserLogin, other.autoUserLogin);
            ShoppingCart.ensureExactEquals(this.locale, other.locale);
            ShoppingCart.ensureExactEquals(this.currencyUom, other.currencyUom);
            ShoppingCart.ensureExactEquals(this.holdOrder, other.holdOrder);
            ShoppingCart.ensureExactEquals(this.orderDate, other.orderDate);
            ShoppingCart.ensureExactEquals(this.cancelBackOrderDate, other.cancelBackOrderDate);
            ShoppingCart.ensureExactEquals(this.allowMissingShipEstimates, other.allowMissingShipEstimates);
        } catch(IllegalStateException e) {
            throw new IllegalStateException("ShoppingCart field not equal: " + e.getMessage(), e);
        }
    }

    static void ensureExactEquals(Object first, Object second) {
        if (first == null) {
            if (second != null) {
                throw new IllegalStateException("values not equal: " + first + ", " + second);
            } else {
                return;
            }
        }
        if (!first.getClass().equals(second.getClass())) {
            throw new IllegalStateException("values not equal: " + first + " (" + first.getClass() + "), " 
                    + second + " (" + second.getClass() + ")");
        }
        if (first instanceof ShoppingCartItem) {
            ((ShoppingCartItem) first).ensureExactEquals((ShoppingCartItem) second);
        } else if (first instanceof ShoppingCartItemGroup) {
            ((ShoppingCartItemGroup) first).ensureExactEquals((ShoppingCartItemGroup) second);
        } else if (first instanceof CartPaymentInfo) {
            ((CartPaymentInfo) first).ensureExactEquals((CartPaymentInfo) second);
        } else if (first instanceof CartShipInfo) {
            ((CartShipInfo) first).ensureExactEquals((CartShipInfo) second);
        } else if (first instanceof CartShipInfo.CartShipItemInfo) {
            ((CartShipInfo.CartShipItemInfo) first).ensureExactEquals((CartShipInfo.CartShipItemInfo) second);
        } else if (first instanceof ProductPromoUseInfo) {
            ((ProductPromoUseInfo) first).ensureExactEquals((ProductPromoUseInfo) second);
        } else if (first instanceof ProductConfigWrapper) {
            ((ProductConfigWrapper) first).ensureExactEquals((ProductConfigWrapper) second);
        } else if (first instanceof GenericValue) {
            if (!first.equals(second)) {
                throw new IllegalStateException("GenericValues not equal: " + first + ", " + second);
            }
        } else if (first instanceof Map) {
            Map<?, ?> firstMap = (Map<?, ?>) first;
            Map<?, ?> secondMap = (Map<?, ?>) second;
            if (firstMap.size() != secondMap.size()) {
                throw new IllegalStateException("Maps not equal: " + first + ", " + second);
            }
            boolean comparable = true;
            for(Map.Entry<?, ?> entry : firstMap.entrySet()) {
                if (entry.getKey() instanceof ShoppingCartItem) {
                    // FIXME: can't verify this type of key because ShoppingCartItem is missing formal equals/hashcode
                    comparable = false;
                    continue;
                }
                ensureExactEquals(entry.getValue(), secondMap.get(entry.getKey()));
            }
            if (comparable) {
                if (!first.equals(second)) { // WARN: This may break on new fields
                    throw new IllegalStateException("Maps not equal: " + first + ", " + second);
                }
            }
        } else if (first instanceof List) {
            List<?> firstList = (List<?>) first;
            List<?> secondList = (List<?>) second;
            if (firstList.size() != secondList.size()) {
                throw new IllegalStateException("Lists not equal: " + first + ", " + second);
            }
            for(int i=0; i<firstList.size(); i++) {
                ensureExactEquals(firstList.get(i), secondList.get(i));
            }
        } else if (first.getClass().isArray()) {
            if (!Arrays.equals((Object[]) first, (Object[]) second)) {
                throw new IllegalStateException("Arrays not equal: " + first + ", " + second);
            }
        } else {
            if (!first.equals(second)) {
                throw new IllegalStateException("Values not equal: " + first + ", " + second);
            }
        }
    }
    
    public Delegator getDelegator() {
        if (delegator == null) {
            delegator = DelegatorFactory.getDelegator(delegatorName);
        }
        return delegator;
    }

    public String getProductStoreId() {
        return this.productStoreId;
    }

    public boolean getDoPromotions() {
        return this.doPromotions;
    }

    public void setDoPromotions(boolean doPromotions) {
        this.doPromotions = doPromotions;
    }

    /**
     * This is somewhat of a dangerous method, changing the productStoreId changes a lot of stuff including:
     * - some items in the cart may not be valid in any catalog in the new store
     * - promotions need to be recalculated for the products that remain
     * - what else? lots of settings on the ProductStore...
     *
     * So for now this can only be called if the cart is empty... otherwise it wil throw an exception
     *
     */
    public void setProductStoreId(String productStoreId) {
        if ((productStoreId == null && this.productStoreId == null) || (productStoreId != null && productStoreId.equals(this.productStoreId))) {
            return;
        }

        if (this.size() == 0) {
            this.productStoreId = productStoreId;
        } else {
            throw new IllegalArgumentException("Cannot set productStoreId when the cart is not empty; cart size is " + this.size());
        }
    }

    public String getTransactionId() {
        return this.transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getTerminalId() {
        return this.terminalId;
    }

    public void setTerminalId(String terminalId) {
        this.terminalId = terminalId;
    }

    public String getAutoOrderShoppingListId() {
        return this.autoOrderShoppingListId;
    }

    public void setAutoOrderShoppingListId(String autoOrderShoppingListId) {
        this.autoOrderShoppingListId = autoOrderShoppingListId;
    }

    public String getFacilityId() {
        return this.facilityId;
    }

    public void setFacilityId(String facilityId) {
        this.facilityId = facilityId;
    }

    public Locale getLocale() {
        return locale;
    }

    public void setLocale(Locale locale) {
        this.locale = locale;
        for (ShoppingCartItem cartItem : cartLines) {
            cartItem.setLocale(locale);
        }
    }

    public void setOrderName(String orderName) {
        this.orderName = orderName;
    }

    public String getOrderName() {
        return orderName;
    }

    public void setWorkEffortId(String workEffortId) {
        this.workEffortId = workEffortId;
    }

    public String getWorkEffortId() {
        return workEffortId;
    }

    public void setAttribute(String name, Object value) {
        this.attributes.put(name, value);
    }

    public void removeAttribute(String name) {
        this.attributes.remove(name);
    }

    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String name) {
        return (T) this.attributes.get(name);
    }

    public void removeOrderAttribute(String name) {
        this.orderAttributes.remove(name);
    }

    public void setOrderAttribute(String name, String value) {
        this.orderAttributes.put(name, value);
    }

    public String getOrderAttribute(String name) {
        return this.orderAttributes.get(name);
    }

    public void setHoldOrder(boolean b) {
        this.holdOrder = b;
    }

    public boolean getHoldOrder() {
        return this.holdOrder;
    }

    public void setOrderDate(Timestamp t) {
        this.orderDate = t;
    }

    public Timestamp getOrderDate() {
        return this.orderDate;
    }

    /** Sets the currency for the cart. */
    public void setCurrency(LocalDispatcher dispatcher, String currencyUom) throws CartItemModifyException {
        if (isReadOnlyCart()) {
           throw new CartItemModifyException("Cart items cannot be changed");
        }
        String previousCurrency = this.currencyUom;
        this.currencyUom = currencyUom;
        if (!previousCurrency.equals(this.currencyUom)) {
            for (ShoppingCartItem item : this) {
                item.updatePrice(dispatcher, this);
            }
        }
    }

    /** Get the current currency setting. */
    public String getCurrency() {
        if (this.currencyUom != null) {
            return this.currencyUom;
        }
        // uh oh, not good, should always be passed in on init, we can't really do
        // anything without it, so throw an exception
        throw new IllegalStateException(
                "The Currency UOM is not set in the shopping cart, this is not a valid state, it should always be passed in when the cart is created.");
    }

    public Timestamp getCartCreatedTime() {
        return this.cartCreatedTs;
    }

    /**
     * SCIPIO: reads back the default supplier ID (currently in attributes), or null if none/empty.
     */
    public String getSupplierPartyId() {
        String supplierPartyId = getAttribute("supplierPartyId");
        if (UtilValidate.isEmpty(supplierPartyId)) {
            return null;
        } else {
            return supplierPartyId;
        }
    }

    /**
     * SCIPIO: set the default supplier ID (currently in attributes)
     */
    public void setSupplierPartyId(String supplierPartyId) {
        setAttribute("supplierPartyId", (UtilValidate.isEmpty(supplierPartyId)) ? null : supplierPartyId);
    }

    public GenericValue getSupplierProduct(String productId, BigDecimal quantity, LocalDispatcher dispatcher) {
        GenericValue supplierProduct = null;
        Map<String, Object> params = UtilMisc.<String, Object>toMap("productId", productId,
                                    "partyId", this.getSupplierPartyId(), // SCIPIO: this was wrong: this.getPartyId()
                                    "currencyUomId", this.getCurrency(),
                                    "quantity", quantity);
        try {
            Map<String, Object> result = dispatcher.runSync("getSuppliersForProduct", params);
            if (ServiceUtil.isError(result)) {
                Debug.logError(ServiceUtil.getErrorMessage(result), module);
                return null;
            }
            List<GenericValue> productSuppliers = UtilGenerics.checkList(result.get("supplierProducts"));
            if ((productSuppliers != null) && (productSuppliers.size() > 0)) {
                supplierProduct = productSuppliers.get(0);
            }
        } catch (GenericServiceException e) {
            Debug.logWarning(UtilProperties.getMessage(resource_error,"OrderRunServiceGetSuppliersForProductError", Debug.getLogLocale()) + e.getMessage(), module); // SCIPIO: log locale
        } catch (Exception e) {
            Debug.logWarning(UtilProperties.getMessage(resource_error,"OrderRunServiceGetSuppliersForProductError", Debug.getLogLocale()) + e.getMessage(), module); // SCIPIO: log locale
        }
        return supplierProduct;
    }

    // =======================================================================
    // Methods for cart items
    // =======================================================================

    /** Add an item to the shopping cart, or if already there, increase the quantity.
     * @return the new/increased item index
     * @throws CartItemModifyException
     */
    public int addOrIncreaseItem(String productId, BigDecimal selectedAmount, BigDecimal quantity, Timestamp reservStart, BigDecimal reservLength, BigDecimal reservPersons,
            Timestamp shipBeforeDate, Timestamp shipAfterDate, Map<String, GenericValue> features, Map<String, Object> attributes, String prodCatalogId,
            ProductConfigWrapper configWrapper, String itemType, String itemGroupNumber, String parentProductId, LocalDispatcher dispatcher) throws CartItemModifyException, ItemNotFoundException {

       return addOrIncreaseItem(productId,selectedAmount,quantity,reservStart,reservLength,reservPersons, null,null,shipBeforeDate,shipAfterDate,features,attributes,prodCatalogId,
                configWrapper,itemType,itemGroupNumber,parentProductId,dispatcher);
    }

    /** add rental (with accommodation) item to cart  */
    public int addOrIncreaseItem(String productId, BigDecimal selectedAmount, BigDecimal quantity, Timestamp reservStart, BigDecimal reservLength, BigDecimal reservPersons,
               String accommodationMapId, String accommodationSpotId, Timestamp shipBeforeDate, Timestamp shipAfterDate, Map<String, GenericValue> features, Map<String, Object> attributes,
               String prodCatalogId, ProductConfigWrapper configWrapper, String itemType, String itemGroupNumber, String parentProductId, LocalDispatcher dispatcher) throws CartItemModifyException, ItemNotFoundException {
        return addOrIncreaseItem(productId, selectedAmount, quantity, reservStart, reservLength, reservPersons, accommodationMapId, accommodationSpotId, shipBeforeDate, shipAfterDate, features, attributes, null, prodCatalogId, configWrapper, itemType, itemGroupNumber, parentProductId, dispatcher);
    }

    /** add rental (with accommodation) item to cart and order item attributes*/
    public int addOrIncreaseItem(String productId, BigDecimal selectedAmount, BigDecimal quantity, Timestamp reservStart, BigDecimal reservLength, BigDecimal reservPersons,
               String accommodationMapId, String accommodationSpotId, Timestamp shipBeforeDate, Timestamp shipAfterDate, Map<String, GenericValue> features, Map<String,Object> attributes,
               Map<String, String> orderItemAttributes, String prodCatalogId, ProductConfigWrapper configWrapper, String itemType, String itemGroupNumber,
               String parentProductId, LocalDispatcher dispatcher) throws CartItemModifyException, ItemNotFoundException {
        if (isReadOnlyCart()) {
           throw new CartItemModifyException("Cart items cannot be changed");
        }

        selectedAmount = selectedAmount == null ? BigDecimal.ZERO : selectedAmount;
        reservLength = reservLength == null ? BigDecimal.ZERO : reservLength;
        reservPersons = reservPersons == null ? BigDecimal.ZERO : reservPersons;

        ShoppingCart.ShoppingCartItemGroup itemGroup = this.getItemGroupByNumber(itemGroupNumber);
        GenericValue supplierProduct = null;
        // Check for existing cart item.
        for (int i = 0; i < this.cartLines.size(); i++) {
            ShoppingCartItem sci = cartLines.get(i);


            if (sci.equals(productId, reservStart, reservLength, reservPersons, accommodationMapId, accommodationSpotId, features, attributes, orderItemAttributes, prodCatalogId,selectedAmount, configWrapper, itemType, itemGroup, false)) {
                BigDecimal newQuantity = sci.getQuantity().add(quantity);
                try {
                    BigDecimal minQuantity = getMinimumOrderQuantity(getDelegator(),sci.getBasePrice(), productId);
                    if(newQuantity.compareTo(minQuantity) < 0) {
                        newQuantity = minQuantity;
                    }
                } catch (GenericEntityException e) {
                    Debug.logError(e, module);
                }
                if ("RENTAL_ORDER_ITEM".equals(sci.getItemType())) {
                    // check to see if the related fixed asset is available for the new quantity
                    String isAvailable = ShoppingCartItem.checkAvailability(productId, newQuantity, reservStart, reservLength, this);
                    if (isAvailable.compareTo("OK") != 0) {
                        Map<String, Object> messageMap = UtilMisc.<String, Object>toMap("productId", productId, "availableMessage", isAvailable);
                        String excMsg = UtilProperties.getMessage(ShoppingCartItem.resource, "item.product_not_available", messageMap, this.getLocale());
                        Debug.logInfo(excMsg, module);
                        throw new CartItemModifyException(isAvailable);
                    }
                }

                if (Debug.verboseOn()) {
                    Debug.logVerbose("Found a match for id " + productId + " on line " + i + ", updating quantity to " + newQuantity, module);
                }
                sci.setQuantity(newQuantity, dispatcher, this);

                if ("PURCHASE_ORDER".equals(getOrderType())) {
                    supplierProduct = getSupplierProduct(productId, newQuantity, dispatcher);
                    if (supplierProduct != null && supplierProduct.getBigDecimal("lastPrice") != null) {
                        sci.setSupplierProductId(supplierProduct.getString("supplierProductId"));
                        sci.setBasePrice(supplierProduct.getBigDecimal("lastPrice"));
                        sci.setName(ShoppingCartItem.getPurchaseOrderItemDescription(sci.getProduct(), supplierProduct, this.getLocale()));
                    } else {
                       throw new CartItemModifyException("SupplierProduct not found");
                    }
                 }
                return i;
            }
        }
        // Add the new item to the shopping cart if it wasn't found.
        ShoppingCartItem item = null;
        if ("PURCHASE_ORDER".equals(getOrderType())) {
            supplierProduct = getSupplierProduct(productId, quantity, dispatcher);
            if (supplierProduct != null || "_NA_".equals(this.getPartyId())) {
                 item = ShoppingCartItem.makePurchaseOrderItem(0, productId, selectedAmount, quantity, features, attributes, prodCatalogId, configWrapper, itemType, itemGroup, dispatcher, this, supplierProduct, shipBeforeDate, shipAfterDate, cancelBackOrderDate,
                         new ShoppingCartItem.ExtraPurchaseOrderInitArgs(orderItemAttributes)); // SCIPIO: 2018-07-17: orderItemAttributes here for early assignment in item
            } else {
                throw new CartItemModifyException("SupplierProduct not found");
            }
        } else {
            try {
                BigDecimal minQuantity = getMinimumOrderQuantity(getDelegator(),null, productId);
                if(quantity.compareTo(minQuantity) < 0) {
                    quantity = minQuantity;
                }
            } catch (GenericEntityException e) {
                Debug.logError(e, module);
            }
            item = ShoppingCartItem.makeItem(0, productId, selectedAmount, quantity, null,
                    reservStart, reservLength, reservPersons, accommodationMapId, accommodationSpotId, shipBeforeDate, shipAfterDate,
                    features, attributes, prodCatalogId, configWrapper, itemType, itemGroup, dispatcher,
                    this, Boolean.TRUE, Boolean.TRUE, parentProductId, Boolean.FALSE, Boolean.FALSE,
                    new ShoppingCartItem.ExtraInitArgs(orderItemAttributes)); // SCIPIO: 2018-07-17: orderItemAttributes here for early assignment in item
        }
        /* SCIPIO: 2018-07-17: This code is now in ShoppingCartItem.setOrderItemAttributes
         * and is called by the makeItem and makePurchaseItem methods, earlier in the item creation
        // add order item attributes
        if (UtilValidate.isNotEmpty(orderItemAttributes)) {
            for (Entry<String, String> entry : orderItemAttributes.entrySet()) {
                item.setOrderItemAttribute(entry.getKey(), entry.getValue());
            }
        }
        */

        return this.addItem(0, item);

    }

    /** Add a non-product item to the shopping cart.
     * @return the new item index
     * @throws CartItemModifyException
     */
    public int addNonProductItem(String itemType, String description, String categoryId, BigDecimal price, BigDecimal quantity,
            Map<String, Object> attributes, String prodCatalogId, String itemGroupNumber, LocalDispatcher dispatcher) throws CartItemModifyException {
        ShoppingCart.ShoppingCartItemGroup itemGroup = this.getItemGroupByNumber(itemGroupNumber);
        return this.addItem(0, ShoppingCartItem.makeItem(0, itemType, description, categoryId, price, null, quantity, attributes, prodCatalogId, itemGroup, dispatcher, this, Boolean.TRUE));
    }

    /** Add an item to the shopping cart. */
    public int addItem(int index, ShoppingCartItem item) throws CartItemModifyException {
        if (isReadOnlyCart()) {
           throw new CartItemModifyException("Cart items cannot be changed");
        }
        if (!cartLines.contains(item)) {
            // If the billing address is already set, verify if the new product
            // is available in the address' geo
            GenericValue product = item.getProduct();
            if (product != null && isSalesOrder()) {
                GenericValue billingAddress = this.getBillingAddress();
                if (billingAddress != null) {
                    if (!ProductWorker.isBillableToAddress(product, billingAddress)) {
                        throw new CartItemModifyException("The billing address is not compatible with ProductGeos rules of this product.");
                    }
                }
            }
            cartLines.add(index, item);
            return index;
        }
        return this.getItemIndex(item);
    }

    /** Add an item to the shopping cart. */
    public int addItemToEnd(String productId, BigDecimal amount, BigDecimal quantity, BigDecimal unitPrice, HashMap<String, GenericValue> features, HashMap<String, Object> attributes, String prodCatalogId, String itemType, ProductConfigWrapper configWrapper, LocalDispatcher dispatcher, Boolean triggerExternalOps, Boolean triggerPriceRules) throws CartItemModifyException, ItemNotFoundException {
        return addItemToEnd(ShoppingCartItem.makeItem(null, productId, amount, quantity, unitPrice, null, null, null, null, null, features, attributes, prodCatalogId, configWrapper, itemType, null, dispatcher, this, triggerExternalOps, triggerPriceRules, null, Boolean.FALSE, Boolean.FALSE));
    }

    /** Add an item to the shopping cart. */
    public int addItemToEnd(String productId, BigDecimal amount, BigDecimal quantity, BigDecimal unitPrice, HashMap<String, GenericValue> features, HashMap<String, Object> attributes, String prodCatalogId, String itemType, LocalDispatcher dispatcher, Boolean triggerExternalOps, Boolean triggerPriceRules) throws CartItemModifyException, ItemNotFoundException {
        return addItemToEnd(productId, amount, quantity, unitPrice, features, attributes, prodCatalogId, itemType, dispatcher, triggerExternalOps, triggerPriceRules, Boolean.FALSE, Boolean.FALSE);
    }

    /** Add an (rental)item to the shopping cart. */
    public int addItemToEnd(String productId, BigDecimal amount, BigDecimal quantity, BigDecimal unitPrice, Timestamp reservStart, BigDecimal reservLength, BigDecimal reservPersons, HashMap<String, GenericValue> features, HashMap<String, Object> attributes, String prodCatalogId, String itemType, LocalDispatcher dispatcher, Boolean triggerExternalOps, Boolean triggerPriceRules) throws CartItemModifyException, ItemNotFoundException {
        return addItemToEnd(ShoppingCartItem.makeItem(null, productId, amount, quantity, unitPrice, reservStart, reservLength, reservPersons, null, null, features, attributes, prodCatalogId, null, itemType, null, dispatcher, this, triggerExternalOps, triggerPriceRules, null, Boolean.FALSE, Boolean.FALSE));
    }

    /** Add an (rental)item to the shopping cart. */
    public int addItemToEnd(String productId, BigDecimal amount, BigDecimal quantity, BigDecimal unitPrice, Timestamp reservStart, BigDecimal reservLength, BigDecimal reservPersons, HashMap<String, GenericValue> features, HashMap<String, Object> attributes, String prodCatalogId, String itemType, LocalDispatcher dispatcher, Boolean triggerExternalOps, Boolean triggerPriceRules, Boolean skipInventoryChecks, Boolean skipProductChecks) throws CartItemModifyException, ItemNotFoundException {
        return addItemToEnd(ShoppingCartItem.makeItem(null, productId, amount, quantity, unitPrice, reservStart, reservLength, reservPersons, null, null, features, attributes, prodCatalogId, null, itemType, null, dispatcher, this, triggerExternalOps, triggerPriceRules, null, skipInventoryChecks, skipProductChecks));
    }

    /** Add an (rental/aggregated)item to the shopping cart. */
    public int addItemToEnd(String productId, BigDecimal amount, BigDecimal quantity, BigDecimal unitPrice, Timestamp reservStart, BigDecimal reservLength, BigDecimal reservPersons, HashMap<String, GenericValue> features, HashMap<String, Object> attributes, String prodCatalogId, ProductConfigWrapper configWrapper, String itemType, LocalDispatcher dispatcher, Boolean triggerExternalOps, Boolean triggerPriceRules, Boolean skipInventoryChecks, Boolean skipProductChecks) throws CartItemModifyException, ItemNotFoundException {
        return addItemToEnd(ShoppingCartItem.makeItem(null, productId, amount, quantity, unitPrice, reservStart, reservLength, reservPersons, null, null, features, attributes, prodCatalogId, configWrapper, itemType, null, dispatcher, this, triggerExternalOps, triggerPriceRules, null, skipInventoryChecks, skipProductChecks));
    }

    /** Add an accommodation(rental)item to the shopping cart. */
    public int addItemToEnd(String productId, BigDecimal amount, BigDecimal quantity, BigDecimal unitPrice, Timestamp reservStart, BigDecimal reservLength, BigDecimal reservPersons, String accommodationMapId, String accommodationSpotId, HashMap<String, GenericValue> features, HashMap<String, Object> attributes, String prodCatalogId, String itemType, LocalDispatcher dispatcher, Boolean triggerExternalOps, Boolean triggerPriceRules) throws CartItemModifyException, ItemNotFoundException {
        return addItemToEnd(ShoppingCartItem.makeItem(null, productId, amount, quantity, unitPrice, reservStart, reservLength, reservPersons, accommodationMapId, accommodationSpotId, null, null, features, attributes, prodCatalogId, null, itemType, null, dispatcher, this, triggerExternalOps, triggerPriceRules, null, Boolean.FALSE, Boolean.FALSE));
    }

    /** Add an accommodation(rental)item to the shopping cart. */
    public int addItemToEnd(String productId, BigDecimal amount, BigDecimal quantity, BigDecimal unitPrice, Timestamp reservStart, BigDecimal reservLength, BigDecimal reservPersons, String accommodationMapId, String accommodationSpotId, HashMap<String, GenericValue> features, HashMap<String, Object> attributes, String prodCatalogId, String itemType, LocalDispatcher dispatcher, Boolean triggerExternalOps, Boolean triggerPriceRules, Boolean skipInventoryChecks, Boolean skipProductChecks) throws CartItemModifyException, ItemNotFoundException {
        return addItemToEnd(ShoppingCartItem.makeItem(null, productId, amount, quantity, unitPrice, reservStart, reservLength, reservPersons, accommodationMapId, accommodationSpotId, null, null, features, attributes, prodCatalogId, null, itemType, null, dispatcher, this, triggerExternalOps, triggerPriceRules, null, skipInventoryChecks, skipProductChecks));
    }

    /** Add an accommodation(rental/aggregated)item to the shopping cart. */
    public int addItemToEnd(String productId, BigDecimal amount, BigDecimal quantity, BigDecimal unitPrice, Timestamp reservStart, BigDecimal reservLength, BigDecimal reservPersonsDbl,String accommodationMapId, String accommodationSpotId, HashMap<String, GenericValue> features, HashMap<String, Object> attributes, String prodCatalogId, ProductConfigWrapper configWrapper, String itemType, LocalDispatcher dispatcher, Boolean triggerExternalOps, Boolean triggerPriceRules, Boolean skipInventoryChecks, Boolean skipProductChecks) throws CartItemModifyException, ItemNotFoundException {
        return addItemToEnd(ShoppingCartItem.makeItem(null, productId, amount, quantity, unitPrice, reservStart, reservLength, reservPersonsDbl, accommodationMapId, accommodationSpotId, null, null, features, attributes, prodCatalogId, configWrapper, itemType, null, dispatcher, this, triggerExternalOps, triggerPriceRules, null, skipInventoryChecks, skipProductChecks));
    }

    /** Add an item to the shopping cart. */
    public int addItemToEnd(String productId, BigDecimal amount, BigDecimal quantity, BigDecimal unitPrice, HashMap<String, GenericValue> features, HashMap<String, Object> attributes, String prodCatalogId, String itemType, LocalDispatcher dispatcher, Boolean triggerExternalOps, Boolean triggerPriceRules, Boolean skipInventoryChecks, Boolean skipProductChecks) throws CartItemModifyException, ItemNotFoundException {
        return addItemToEnd(ShoppingCartItem.makeItem(null, productId, amount, quantity, unitPrice, null, null, null, null, null, features, attributes, prodCatalogId, null, itemType, null, dispatcher, this, triggerExternalOps, triggerPriceRules, null, skipInventoryChecks, skipProductChecks));
    }

    /** Add an item to the shopping cart. */
    public int addItemToEnd(ShoppingCartItem item) throws CartItemModifyException {
        return addItem(cartLines.size(), item);
    }

    /** Get a ShoppingCartItem from the cart object. */
    public ShoppingCartItem findCartItem(String productId, Map<String, GenericValue> features, Map<String, Object> attributes, String prodCatalogId, BigDecimal selectedAmount) {
        // Check for existing cart item.
        for (int i = 0; i < this.cartLines.size(); i++) {
            ShoppingCartItem cartItem = cartLines.get(i);

            if (cartItem.equals(productId, features, attributes, prodCatalogId, selectedAmount)) {
                return cartItem;
            }
        }
        return null;
    }

    /** Get all ShoppingCartItems from the cart object with the given productId. */
    public List<ShoppingCartItem> findAllCartItems(String productId) {
        return this.findAllCartItems(productId, null);
    }
    /** Get all ShoppingCartItems from the cart object with the given productId and optional groupNumber to limit it to a specific item group */
    public List<ShoppingCartItem> findAllCartItems(String productId, String groupNumber) {
        if (productId == null) {
            return this.items();
        }

        List<ShoppingCartItem> itemsToReturn = new ArrayList<>();
        // Check for existing cart item.
        for (ShoppingCartItem cartItem : cartLines) {
            if (UtilValidate.isNotEmpty(groupNumber) && !cartItem.isInItemGroup(groupNumber)) {
                continue;
            }
            if (productId.equals(cartItem.getProductId())) {
                itemsToReturn.add(cartItem);
            }
        }
        return itemsToReturn;
    }

    /** Get all ShoppingCartItems from the cart object with the given productCategoryId and optional groupNumber to limit it to a specific item group */
    public List<ShoppingCartItem> findAllCartItemsInCategory(String productCategoryId, String groupNumber) {
        if (productCategoryId == null) {
            return this.items();
        }

        Delegator delegator = this.getDelegator();
        List<ShoppingCartItem> itemsToReturn = new ArrayList<>();
        try {
            // Check for existing cart item
            for (ShoppingCartItem cartItem : cartLines) {

                if (UtilValidate.isNotEmpty(groupNumber) && !cartItem.isInItemGroup(groupNumber)) {
                    continue;
                }
                if (CategoryWorker.isProductInCategory(delegator, cartItem.getProductId(), productCategoryId)) {
                    itemsToReturn.add(cartItem);
                } else {
                }
            }
        } catch (GenericEntityException e) {
            Debug.logError(e, "Error getting cart items that are in a category: " + e.toString(), module);
        }
        return itemsToReturn;
    }

    /** Remove quantity 0 ShoppingCartItems from the cart object. */
    public void removeEmptyCartItems() {
        // Check for existing cart item.
        for (int i = 0; i < this.cartLines.size();) {
            ShoppingCartItem cartItem = cartLines.get(i);

            if (cartItem.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
                this.clearItemShipInfo(cartItem);
                cartLines.remove(i);
            } else {
                i++;
            }
        }
    }

    // =============== some misc utility methods, mostly for dealing with lists of items =================
    public void removeExtraItems(List<ShoppingCartItem> multipleItems, LocalDispatcher dispatcher, int maxItems) throws CartItemModifyException {
        // if 1 or 0 items, do nothing
        if (multipleItems.size() <= maxItems) {
            return;
        }

        // remove all except first <maxItems> in list from the cart, first because new cart items are added to the beginning...
        List<ShoppingCartItem> localList = new ArrayList<>();
        localList.addAll(multipleItems);
        // the ones to keep...
        for (int i=0; i<maxItems; i++) {
            localList.remove(0);
        }
        for (ShoppingCartItem item : localList) {
            this.removeCartItem(item, dispatcher);
        }
    }

    public static BigDecimal getItemsTotalQuantity(List<ShoppingCartItem> cartItems) {
        BigDecimal totalQuantity = BigDecimal.ZERO;
        for (ShoppingCartItem item : cartItems) {
            totalQuantity = totalQuantity.add(item.getQuantity());
        }
        return totalQuantity;
    }

    public static List<GenericValue> getItemsProducts(List<ShoppingCartItem> cartItems) {
        List<GenericValue> productList = new ArrayList<>();
        for (ShoppingCartItem item : cartItems) {
            GenericValue product = item.getProduct();
            if (product != null) {
                productList.add(product);
            }
        }
        return productList;
    }

    public void ensureItemsQuantity(List<ShoppingCartItem> cartItems, LocalDispatcher dispatcher, BigDecimal quantity) throws CartItemModifyException {
        for (ShoppingCartItem item : cartItems) {
            if (item.getQuantity() != quantity) {
                item.setQuantity(quantity, dispatcher, this);
            }
        }
    }

    public BigDecimal ensureItemsTotalQuantity(List<ShoppingCartItem> cartItems, LocalDispatcher dispatcher, BigDecimal quantity) throws CartItemModifyException {
        BigDecimal quantityRemoved = BigDecimal.ZERO;
        // go through the items and reduce quantityToKeep by the item quantities until it is 0, then remove the remaining...
        BigDecimal quantityToKeep = quantity;
        for (ShoppingCartItem item : cartItems) {
            if (quantityToKeep.compareTo(item.getQuantity()) >= 0) {
                // quantityToKeep sufficient to keep it all... just reduce quantityToKeep and move on
                quantityToKeep = quantityToKeep.subtract(item.getQuantity());
            } else {
                // there is more in this than we want to keep, so reduce the quantity, or remove altogether...
                if (quantityToKeep.compareTo(BigDecimal.ZERO) == 0) {
                    // nothing left to keep, just remove it...
                    quantityRemoved = quantityRemoved.add(item.getQuantity());
                    this.removeCartItem(item, dispatcher);
                } else {
                    // there is some to keep, so reduce quantity to quantityToKeep, at this point we know we'll take up all of the rest of the quantityToKeep
                    quantityRemoved = quantityRemoved.add(item.getQuantity().subtract(quantityToKeep));
                    item.setQuantity(quantityToKeep, dispatcher, this);
                    quantityToKeep = BigDecimal.ZERO;
                }
            }
        }
        return quantityRemoved;
    }

    // ============== WorkEffort related methods ===============
    public boolean containAnyWorkEffortCartItems() {
        // Check for existing cart item.
        for (ShoppingCartItem cartItem : this.cartLines) {
            if ("RENTAL_ORDER_ITEM".equals(cartItem.getItemType())) {  // create workeffort items?
                return true;
            }
        }
        return false;
    }

    public boolean containAllWorkEffortCartItems() {
        // Check for existing cart item.
        for (ShoppingCartItem cartItem : this.cartLines) {
            if (!"RENTAL_ORDER_ITEM".equals(cartItem.getItemType())) { // not a item to create workefforts?
                return false;
            }
        }
        return true;
    }

    /**
     * Check to see if the cart contains only Digital Goods, ie no Finished Goods and no Finished/Digital Goods, et cetera.
     * This is determined by making sure no Product has a type where ProductType.isPhysical!=N.
     */
    public boolean containOnlyDigitalGoods() {
        for (ShoppingCartItem cartItem : this.cartLines) {
            GenericValue product = cartItem.getProduct();
            try {
                GenericValue productType = product.getRelatedOne("ProductType", true);
                if (productType == null || !"N".equals(productType.getString("isPhysical"))) {
                    return false;
                }
            } catch (GenericEntityException e) {
                Debug.logError(e, "Error looking up ProductType: " + e.toString(), module);
                // consider this not a digital good if we don't have "proof"
                return false;
            }
        }
        return true;
    }

    /**
     * Check to see if the ship group contains only Digital Goods, ie no Finished Goods and no Finished/Digital Goods, et cetera.
     * This is determined by making sure no Product has a type where ProductType.isPhysical!=N.
     */
    public boolean containOnlyDigitalGoods(int shipGroupIdx) {
        CartShipInfo shipInfo = getShipInfo(shipGroupIdx);
        for (ShoppingCartItem cartItem: shipInfo.getShipItems()) {
            GenericValue product = cartItem.getProduct();
            try {
                GenericValue productType = product.getRelatedOne("ProductType", true);
                if (productType == null || !"N".equals(productType.getString("isPhysical"))) {
                    return false;
                }
            } catch (GenericEntityException e) {
                Debug.logError(e, "Error looking up ProductType: " + e.toString(), module);
                // consider this not a digital good if we don't have "proof"
                return false;
            }
        }
        return true;
    }

    /** Returns this item's index. */
    public int getItemIndex(ShoppingCartItem item) {
        return cartLines.indexOf(item);
    }

    /** Get a ShoppingCartItem from the cart object. */
    public ShoppingCartItem findCartItem(int index) {
        if (cartLines.size() <= index) {
            return null;
        }
        return cartLines.get(index);

    }

    public ShoppingCartItem findCartItem(String orderItemSeqId) {
        if (orderItemSeqId != null) {
            for (int i = 0; i < this.cartLines.size(); i++) {
                ShoppingCartItem cartItem = cartLines.get(i);
                String itemSeqId = cartItem.getOrderItemSeqId();
                if (itemSeqId != null && orderItemSeqId.equals(itemSeqId)) {
                    return cartItem;
                }
            }
        }
        return null;
    }

    /**
     * SCIPIO: Remove an item from the cart object.
     * <p>
     * SCIPIO: Modified to support triggerExternalOps bool.
     */
    public void removeCartItem(ShoppingCartItem item, boolean triggerExternalOps, LocalDispatcher dispatcher) throws CartItemModifyException {
        if (item == null) return;
        this.removeCartItem(this.getItemIndex(item), triggerExternalOps, dispatcher);
    }

    public void removeCartItem(ShoppingCartItem item, LocalDispatcher dispatcher) throws CartItemModifyException {
        if (item == null) {
            return;
        }
        this.removeCartItem(this.getItemIndex(item), dispatcher);
    }

    /**
     * SCIPIO: Remove an item from the cart object.
     * <p>
     * SCIPIO: Modified to support triggerExternalOps bool.
     */
    public void removeCartItem(int index, boolean triggerExternalOps, LocalDispatcher dispatcher) throws CartItemModifyException {
        if (isReadOnlyCart()) {
           throw new CartItemModifyException("Cart items cannot be changed");
        }
        if (index < 0) {
            return;
        }
        if (cartLines.size() <= index) {
            return;
        }
        ShoppingCartItem item;
        /* 2018-11-22: Removed: cartSubscriptionItems cache is counter-productive
        // SCIPIO: Removing cart item from subscriptions map, if exists
        item = cartLines.get(index);
        String prodId = item.getProductId();
        if (UtilValidate.isNotEmpty(cartSubscriptionItems)
                && UtilValidate.isNotEmpty(prodId)
                && cartSubscriptionItems.containsKey(prodId)) {
            cartSubscriptionItems.remove(prodId);
        }
        */
        item = cartLines.remove(index);


        // set quantity to 0 to trigger necessary events, but skip price calc and inventory checks
        item.setQuantity(BigDecimal.ZERO, dispatcher, this, triggerExternalOps, true, false, true);
    }

    /**
     * Remove an item from the cart object.
     * <p>
     * SCIPIO: Implies triggerExternalOps true.
     */
    public void removeCartItem(int index, LocalDispatcher dispatcher) throws CartItemModifyException {
        removeCartItem(index, true, dispatcher);
    }

    /** Moves a line item to a different index. */
    public void moveCartItem(int fromIndex, int toIndex) {
        if (toIndex < fromIndex) {
            cartLines.add(toIndex, cartLines.remove(fromIndex));
        } else if (toIndex > fromIndex) {
            cartLines.add(toIndex - 1, cartLines.remove(fromIndex));
        }
    }

    /** Returns the number of items in the cart object. */
    public int size() {
        return cartLines.size();
    }

    /** Returns a Collection of items in the cart object. */
    public List<ShoppingCartItem> items() {
        List<ShoppingCartItem> result = new ArrayList<>();
        result.addAll(cartLines);
        return result;
    }

    /** Returns an iterator of cart items. */
    @Override
    public Iterator<ShoppingCartItem> iterator() {
        return cartLines.iterator();
    }

    public ShoppingCart.ShoppingCartItemGroup getItemGroupByNumber(String groupNumber) {
        if (UtilValidate.isEmpty(groupNumber)) {
            return null;
        }
        return this.itemGroupByNumberMap.get(groupNumber);
    }

    /** Creates a new Item Group and returns the groupNumber that represents it */
    public String addItemGroup(String groupName, String parentGroupNumber) {
        ShoppingCart.ShoppingCartItemGroup parentGroup = this.getItemGroupByNumber(parentGroupNumber);
        ShoppingCart.ShoppingCartItemGroup newGroup = new ShoppingCart.ShoppingCartItemGroup(this.nextGroupNumber, groupName, parentGroup);
        this.nextGroupNumber++;
        this.itemGroupByNumberMap.put(newGroup.getGroupNumber(), newGroup);
        return newGroup.getGroupNumber();
    }

    public ShoppingCartItemGroup addItemGroup(GenericValue itemGroupValue) throws GenericEntityException {
        if (itemGroupValue == null) {
            return null;
        }
        String itemGroupNumber = itemGroupValue.getString("orderItemGroupSeqId");
        ShoppingCartItemGroup itemGroup = this.getItemGroupByNumber(itemGroupNumber);
        if (itemGroup == null) {
            ShoppingCartItemGroup parentGroup = addItemGroup(itemGroupValue.getRelatedOne("ParentOrderItemGroup", true));
            itemGroup = new ShoppingCartItemGroup(itemGroupNumber, itemGroupValue.getString("groupName"), parentGroup);
            int parsedGroupNumber = Integer.parseInt(itemGroupNumber);
            if (parsedGroupNumber > this.nextGroupNumber) {
                this.nextGroupNumber = parsedGroupNumber + 1;
            }
            this.itemGroupByNumberMap.put(itemGroupNumber, itemGroup);
        }
        return itemGroup;
    }

    public List<ShoppingCartItem> getCartItemsInNoGroup() {
        List<ShoppingCartItem> cartItemList = new ArrayList<>();
        for (ShoppingCartItem cartItem : cartLines) {
            if (cartItem.getItemGroup() == null) {
                cartItemList.add(cartItem);
            }
        }
        return cartItemList;
    }

    public List<ShoppingCartItem> getCartItemsInGroup(String groupNumber) {
        List<ShoppingCartItem> cartItemList = new ArrayList<>();
        ShoppingCart.ShoppingCartItemGroup itemGroup = this.getItemGroupByNumber(groupNumber);
        if (itemGroup != null) {
            for (ShoppingCartItem cartItem : cartLines) {
                if (itemGroup.equals(cartItem.getItemGroup())) {
                    cartItemList.add(cartItem);
                }
            }
        }
        return cartItemList;
    }

    public void deleteItemGroup(String groupNumber) {
        ShoppingCartItemGroup itemGroup = this.getItemGroupByNumber(groupNumber);
        if (itemGroup != null) {
            // go through all cart items and remove from group if they are in it
            List<ShoppingCartItem> cartItemList = this.getCartItemsInGroup(groupNumber);
            for (ShoppingCartItem cartItem : cartItemList) {
                cartItem.setItemGroup(null);
            }

            // if this is a parent of any set them to this group's parent (or null)
            for (ShoppingCartItemGroup otherItemGroup : this.itemGroupByNumberMap.values()) {
                if (itemGroup.equals(otherItemGroup.getParentGroup())) {
                    otherItemGroup.inheritParentsParent();
                }
            }

            // finally, remove the itemGroup...
            this.itemGroupByNumberMap.remove(groupNumber);
        }
    }

    //=======================================================
    // Other General Info Maintenance Methods
    //=======================================================

    /** Gets the userLogin associated with the cart; may be null */
    public GenericValue getUserLogin() {
        return this.userLogin;
    }

    public void setUserLogin(GenericValue userLogin, LocalDispatcher dispatcher) throws CartItemModifyException {
        this.userLogin = userLogin;
        this.handleNewUser(dispatcher);
    }

    protected void setUserLogin(GenericValue userLogin) {
        if (this.userLogin == null) {
            this.userLogin = userLogin;
        } else {
            throw new IllegalArgumentException("Cannot change UserLogin object with this method");
        }
    }

    public GenericValue getAutoUserLogin() {
        return this.autoUserLogin;
    }

    public void setAutoUserLogin(GenericValue autoUserLogin, LocalDispatcher dispatcher) throws CartItemModifyException {
        this.autoUserLogin = autoUserLogin;
        if (getUserLogin() == null) {
            this.handleNewUser(dispatcher);
        }
    }

    protected void setAutoUserLogin(GenericValue autoUserLogin) {
        if (this.autoUserLogin == null) {
            this.autoUserLogin = autoUserLogin;
        } else {
            throw new IllegalArgumentException("Cannot change AutoUserLogin object with this method");
        }
    }

    /**
     * SCIPIO: Sets autoUserLogin even if null - for internal use only.
     */
    protected void setAutoUserLoginAlways(GenericValue autoUserLogin) {
        this.autoUserLogin = autoUserLogin;
    }

    public void handleNewUser(LocalDispatcher dispatcher) throws CartItemModifyException {
        String partyId = this.getPartyId();
        if (UtilValidate.isNotEmpty(partyId)) {
            // recalculate all prices
            for (ShoppingCartItem cartItem : this) {
                cartItem.updatePrice(dispatcher, this);
            }

            // check all promo codes, remove on failed check
            Iterator<String> promoCodeIter = this.productPromoCodes.iterator();
            while (promoCodeIter.hasNext()) {
                String promoCode = promoCodeIter.next();
                String checkResult = ProductPromoWorker.checkCanUsePromoCode(promoCode, partyId, this.getDelegator(), locale);
                if (checkResult != null) {
                    promoCodeIter.remove();
                    Debug.logWarning(UtilProperties.getMessage(resource_error,"OrderOnUserChangePromoCodeWasRemovedBecause", UtilMisc.toMap("checkResult",checkResult), Debug.getLogLocale()), module); // SCIPIO: log locale
                }
            }

            // rerun promotions
            ProductPromoWorker.doPromotions(this, dispatcher);
        }
    }

    public String getExternalId() {
        return this.externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getInternalCode() {
        return this.internalCode;
    }

    public void setInternalCode(String internalCode) {
        this.internalCode = internalCode;
    }

    public String getWebSiteId() {
        return this.webSiteId;
    }

    public void setWebSiteId(String webSiteId) {
        this.webSiteId = webSiteId;
    }

    /**
     * Set ship before date for a particular ship group
     * @param idx
     * @param shipBeforeDate
     */
   public void setShipBeforeDate(int idx, Timestamp shipBeforeDate) {
       CartShipInfo csi = this.getShipInfo(idx);
       csi.shipBeforeDate  = shipBeforeDate;
   }

   /**
    * Set ship before date for ship group 0
    * @param shipBeforeDate
    */
   public void setShipBeforeDate(Timestamp shipBeforeDate) {
       this.setShipBeforeDate(0, shipBeforeDate);
   }

   /**
    * Get ship before date for a particular ship group
    * @param idx the ship group number
    * @return ship before date for a given ship group
    */
   public Timestamp getShipBeforeDate(int idx) {
       CartShipInfo csi = this.getShipInfo(idx);
       return csi.shipBeforeDate;
   }

   /**
    * Get ship before date for ship group 0
    * @return ship before date for the first ship group
    */
   public Timestamp getShipBeforeDate() {
       return this.getShipBeforeDate(0);
   }

   /**
    * Set ship after date for a particular ship group
    * @param idx the ship group number
    * @param shipAfterDate the ship after date to be set for the given ship group
    */
   public void setShipAfterDate(int idx, Timestamp shipAfterDate) {
       CartShipInfo csi = this.getShipInfo(idx);
       csi.shipAfterDate  = shipAfterDate;
   }

   /**
    * Set ship after date for a particular ship group
    * @param shipAfterDate the ship after date to be set for the first ship group
    */
   public void setShipAfterDate(Timestamp shipAfterDate) {
       this.setShipAfterDate(0, shipAfterDate);
   }

   /**
    * Get ship after date for a particular ship group
    * @param idx the ship group number
    * @return return the ship after date for the given ship group
    */
   public Timestamp getShipAfterDate(int idx) {
       CartShipInfo csi = this.getShipInfo(idx);
       return csi.shipAfterDate;
   }

   /**
    * Get ship after date for ship group 0
    * @return return the ship after date for the first ship group
    */
   public Timestamp getShipAfterDate() {
       return this.getShipAfterDate(0);
   }

   public void setDefaultShipBeforeDate(Timestamp defaultShipBeforeDate) {
      this.defaultShipBeforeDate = defaultShipBeforeDate;
   }

   public Timestamp getDefaultShipBeforeDate() {
       return this.defaultShipBeforeDate;
   }

   public void setDefaultShipAfterDate(Timestamp defaultShipAfterDate) {
       this.defaultShipAfterDate = defaultShipAfterDate;
   }

    public void setCancelBackOrderDate(Timestamp cancelBackOrderDate) {
        this.cancelBackOrderDate = cancelBackOrderDate;
    }

    public Timestamp getCancelBackOrderDate() {
        return this.cancelBackOrderDate;
    }

   public Timestamp getDefaultShipAfterDate() {
       return this.defaultShipAfterDate;
   }

    public String getOrderPartyId() {
        return this.orderPartyId != null ? this.orderPartyId : this.getPartyId();
    }

    public void setOrderPartyId(String orderPartyId) {
        this.orderPartyId = orderPartyId;
    }

    public String getPlacingCustomerPartyId() {
        return this.placingCustomerPartyId != null ? this.placingCustomerPartyId : this.getPartyId();
    }

    public void setPlacingCustomerPartyId(String placingCustomerPartyId) {
        this.placingCustomerPartyId = placingCustomerPartyId;
        if (UtilValidate.isEmpty(this.orderPartyId)) {
            this.orderPartyId = placingCustomerPartyId;
        }
    }

    public String getBillToCustomerPartyId() {
        return this.billToCustomerPartyId != null ? this.billToCustomerPartyId : this.getPartyId();
    }

    public void setBillToCustomerPartyId(String billToCustomerPartyId) {
        this.billToCustomerPartyId = billToCustomerPartyId;
        if ((UtilValidate.isEmpty(this.orderPartyId)) && !("PURCHASE_ORDER".equals(orderType))) {
            this.orderPartyId = billToCustomerPartyId;  // orderPartyId should be bill-to-customer when it is not a purchase order
        }
    }

    public String getShipToCustomerPartyId() {
        return this.shipToCustomerPartyId != null ? this.shipToCustomerPartyId : this.getPartyId();
    }

    public void setShipToCustomerPartyId(String shipToCustomerPartyId) {
        this.shipToCustomerPartyId = shipToCustomerPartyId;
        if (UtilValidate.isEmpty(this.orderPartyId)) {
            this.orderPartyId = shipToCustomerPartyId;
        }
    }

    public String getEndUserCustomerPartyId() {
        return this.endUserCustomerPartyId != null ? this.endUserCustomerPartyId : this.getPartyId();
    }

    public void setEndUserCustomerPartyId(String endUserCustomerPartyId) {
        this.endUserCustomerPartyId = endUserCustomerPartyId;
        if (UtilValidate.isEmpty(this.orderPartyId)) {
            this.orderPartyId = endUserCustomerPartyId;
        }
    }

    public String getBillFromVendorPartyId() {
        return this.billFromVendorPartyId != null ? this.billFromVendorPartyId : this.getPartyId();
    }

    public void setBillFromVendorPartyId(String billFromVendorPartyId) {
        this.billFromVendorPartyId = billFromVendorPartyId;
        if ((UtilValidate.isEmpty(this.orderPartyId)) && ("PURCHASE_ORDER".equals(orderType))) {
            this.orderPartyId = billFromVendorPartyId;  // orderPartyId should be bill-from-vendor when it is a purchase order
        }

    }

    public String getShipFromVendorPartyId() {
        return this.shipFromVendorPartyId != null ? this.shipFromVendorPartyId : this.getPartyId();
    }

    public void setShipFromVendorPartyId(String shipFromVendorPartyId) {
        this.shipFromVendorPartyId = shipFromVendorPartyId;
        if (UtilValidate.isEmpty(this.orderPartyId)) {
            this.orderPartyId = shipFromVendorPartyId;
        }
    }

    public String getSupplierAgentPartyId() {
        return this.supplierAgentPartyId != null ? this.supplierAgentPartyId : this.getPartyId();
    }

    public void setSupplierAgentPartyId(String supplierAgentPartyId) {
        this.supplierAgentPartyId = supplierAgentPartyId;
        if (UtilValidate.isEmpty(this.orderPartyId)) {
            this.orderPartyId = supplierAgentPartyId;
        }
    }

    public String getPartyId() {
        String partyId = this.orderPartyId;

        if (partyId == null && getUserLogin() != null) {
            partyId = getUserLogin().getString("partyId");
        }
        if (partyId == null && getAutoUserLogin() != null) {
            partyId = getAutoUserLogin().getString("partyId");
        }
        return partyId;
    }

    public void setAutoSaveListId(String id) {
        this.autoSaveListId = id;
    }

    public String getAutoSaveListId() {
        return this.autoSaveListId;
    }

    public void setLastListRestore(Timestamp time) {
        this.lastListRestore = time;
    }

    public Timestamp getLastListRestore() {
        return this.lastListRestore;
    }

    public BigDecimal getPartyDaysSinceCreated(Timestamp nowTimestamp) {
        String partyId = this.getPartyId();
        if (UtilValidate.isEmpty(partyId)) {
            return null;
        }
        try {
            GenericValue party = this.getDelegator().findOne("Party", UtilMisc.toMap("partyId", partyId), true);
            if (party == null) {
                return null;
            }
            Timestamp createdDate = party.getTimestamp("createdDate");
            if (createdDate == null) {
                return null;
            }
            BigDecimal diffMillis = new BigDecimal(nowTimestamp.getTime() - createdDate.getTime());
            // millis per day: 1000.0 * 60.0 * 60.0 * 24.0 = 86400000.0
            return (diffMillis).divide(new BigDecimal("86400000"), generalRounding);
        } catch (GenericEntityException e) {
            Debug.logError(e, "Error looking up party when getting createdDate", module);
            return null;
        }
    }

    // =======================================================================
    // Methods for cart fields
    // =======================================================================

    /** Clears out the cart. */
    public void clear() {
        this.poNumber = null;
        this.orderId = null;
        this.firstAttemptOrderId = null;
        this.billingAccountId = null;
        this.billingAccountAmt = BigDecimal.ZERO;
        this.nextItemSeq = 1;

        this.agreementId = null;
        this.quoteId = null;

        this.defaultItemDeliveryDate = null;
        this.defaultItemComment = null;
        this.orderAdditionalEmails = null;

        this.readOnlyCart = false;

        this.lastListRestore = null;

        this.orderTermSet = false;
        this.orderTerms.clear();

        this.adjustments.clear();

        this.expireSingleUsePayments();
        this.cartLines.clear();
        this.itemGroupByNumberMap.clear();
        this.clearPayments();
        this.shipInfo.clear();
        this.contactMechIdsMap.clear();
        this.internalOrderNotes.clear();
        this.orderNotes.clear();
        this.attributes.clear();
        this.orderAttributes.clear();

        // clear the additionalPartyRole Map
        for (Map.Entry<String, List<String>> me : this.additionalPartyRole.entrySet()) {
            ((ArrayList<String>) me.getValue()).clear();
        }
        this.additionalPartyRole.clear();

        this.freeShippingProductPromoActions.clear();
        this.desiredAlternateGiftByAction.clear();
        this.productPromoUseInfoList.clear();
        this.productPromoCodes.clear();

        /* 2018-11-22: Removed: cartSubscriptionItems cache is counter-productive
        // SCIPIO: Clearing subscription items
        if (this.cartSubscriptionItems != null) {
            this.cartSubscriptionItems.clear();
        }
        */

        // clear the auto-save info
        if (ProductStoreWorker.autoSaveCart(this.getDelegator(), this.getProductStoreId())) {
            GenericValue ul = this.getUserLogin();
            if (ul == null) {
                ul = this.getAutoUserLogin();
            }
            // autoSaveListId shouldn't be set to null for anonymous user until the list is not cleared from the database
            if (ul != null && !"anonymous".equals(ul.getString("userLoginId"))) {
                this.autoSaveListId = null;
            }
            // load the auto-save list ID
            if (autoSaveListId == null) {
                try {
                    autoSaveListId = ShoppingListEvents.getAutoSaveListId(this.getDelegator(), null, null, ul, this.getProductStoreId());
                } catch (GeneralException e) {
                    Debug.logError(e, module);
                }
            }

            // clear the list
            if (autoSaveListId != null) {
                try {
                    org.ofbiz.order.shoppinglist.ShoppingListEvents.clearListInfo(this.getDelegator(), autoSaveListId);
                } catch (GenericEntityException e) {
                    Debug.logError(e, module);
                }
            }
            this.lastListRestore = null;
            this.autoSaveListId = null;
        }
    }

    /** Sets the order type. */
    public void setOrderType(String orderType) {
        this.orderType = orderType;
    }

    /** Returns the order type. */
    public String getOrderType() {
        return this.orderType;
    }

    public void setChannelType(String channelType) {
        this.channel = channelType;
    }

    public String getChannelType() {
        return this.channel;
    }

    public boolean isPurchaseOrder() {
        return "PURCHASE_ORDER".equals(this.orderType);
    }

    public boolean isSalesOrder() {
        return "SALES_ORDER".equals(this.orderType);
    }

    /** Sets the PO Number in the cart. */
    public void setPoNumber(String poNumber) {
        this.poNumber = poNumber;
    }

    /** Returns the po number. */
    public String getPoNumber() {
        return poNumber;
    }

    public void setDefaultItemDeliveryDate(String date) {
        this.defaultItemDeliveryDate = date;
    }

    public String getDefaultItemDeliveryDate() {
        return this.defaultItemDeliveryDate;
    }

    public void setDefaultItemComment(String comment) {
        this.defaultItemComment = comment;
    }

    public String getDefaultItemComment() {
        return this.defaultItemComment;
    }

    public void setAgreementId(String agreementId) {
        this.agreementId = agreementId;
    }

    public String getAgreementId() {
        return this.agreementId;
    }

    public void setQuoteId(String quoteId) {
        this.quoteId = quoteId;
    }

    public String getQuoteId() {
        return this.quoteId;
    }

    // =======================================================================
    // Payment Method
    // =======================================================================

    public String getPaymentMethodTypeId(String paymentMethodId) {
        try {
            GenericValue pm = this.getDelegator().findOne("PaymentMethod", UtilMisc.toMap("paymentMethodId", paymentMethodId), false);
            if (pm != null) {
                return pm.getString("paymentMethodTypeId");
            }
        } catch (GenericEntityException e) {
            Debug.logError(e, module);
        }
        return null;
    }

    /** Creates a CartPaymentInfo object */
    public CartPaymentInfo makePaymentInfo(String id, String refNum, BigDecimal amount) {
        CartPaymentInfo inf = new CartPaymentInfo(this.getPartyId()); // SCIPIO: 2018-07-19: billing address default: partyId
        inf.refNum[0] = refNum;
        inf.amount = amount;
        inf.origAmount = amount;    // SCIPIO: Save the original amount, that was specified upon creation

        if (!isPaymentMethodType(id)) {
            inf.paymentMethodTypeId = this.getPaymentMethodTypeId(id);
            inf.paymentMethodId = id;
        } else {
            inf.paymentMethodTypeId = id;
        }
        return inf;
    }

    /** Creates a CartPaymentInfo object with a possible authCode (may be null) */
    public CartPaymentInfo makePaymentInfo(String id, String refNum, String authCode, BigDecimal amount) {
        CartPaymentInfo inf = new CartPaymentInfo(this.getPartyId()); // SCIPIO: 2018-07-19: billing address default: partyId
        inf.refNum[0] = refNum;
        inf.refNum[1] = authCode;
        inf.amount = amount;
        inf.origAmount = amount;    // SCIPIO: Save the original amount, that was specified upon creation

        if (!isPaymentMethodType(id)) {
            inf.paymentMethodTypeId = this.getPaymentMethodTypeId(id);
            inf.paymentMethodId = id;
        } else {
            inf.paymentMethodTypeId = id;
        }
        return inf;
    }

    /** Locates the index of an existing CartPaymentInfo object or -1 if none found */
    public int getPaymentInfoIndex(String id, String refNum) {
        CartPaymentInfo thisInf = this.makePaymentInfo(id, refNum, null);
        for (int i = 0; i < paymentInfo.size(); i++) {
            CartPaymentInfo inf = paymentInfo.get(i);
            if (inf.compareTo(thisInf) == 0) {
                return i;
            }
        }
        return -1;
    }

    /** Returns the CartPaymentInfo objects which have matching fields */
    public List<CartPaymentInfo> getPaymentInfos(boolean isPaymentMethod, boolean isPaymentMethodType, boolean hasRefNum) {
        List<CartPaymentInfo> foundRecords = new ArrayList<>();
        for (CartPaymentInfo inf : paymentInfo) {
            if (isPaymentMethod && inf.paymentMethodId != null) {
                if (hasRefNum && inf.refNum != null) {
                    foundRecords.add(inf);
                } else if (!hasRefNum && inf.refNum == null) {
                    foundRecords.add(inf);
                }
            } else if (isPaymentMethodType && inf.paymentMethodTypeId != null) {
                if (hasRefNum && inf.refNum != null) {
                    foundRecords.add(inf);
                } else if (!hasRefNum && inf.refNum == null) {
                    foundRecords.add(inf);
                }
            }
        }
        return foundRecords;
    }

    /** SCIPIO: Returns all payment infos  */
    public List<CartPaymentInfo> getPaymentInfos() {
        return paymentInfo;
    }

    /** Locates an existing CartPaymentInfo object by index */
    public CartPaymentInfo getPaymentInfo(int index) {
        return paymentInfo.get(index);
    }

    /** Locates an existing (or creates a new) CartPaymentInfo object */
    public CartPaymentInfo getPaymentInfo(String id, String refNum, String authCode, BigDecimal amount, boolean update) {
        CartPaymentInfo thisInf = this.makePaymentInfo(id, refNum, authCode, amount);
        for (CartPaymentInfo inf : paymentInfo) {
            if (inf.compareTo(thisInf) == 0) {
                // update the info
                if (update) {
                    inf.refNum[0] = refNum;
                    inf.refNum[1] = authCode;
                    inf.amount = amount;
                }
                Debug.logInfo("Returned existing PaymentInfo - " + inf.toString(), module);
                return inf;
            }
        }

        Debug.logInfo("Returned new PaymentInfo - " + thisInf.toString(), module);
        return thisInf;
    }

    /** Locates an existing (or creates a new) CartPaymentInfo object */
    public CartPaymentInfo getPaymentInfo(String id, String refNum, String authCode, BigDecimal amount) {
        return this.getPaymentInfo(id, refNum, authCode, amount, false);
    }

    /** Locates an existing (or creates a new) CartPaymentInfo object */
    public CartPaymentInfo getPaymentInfo(String id) {
        return this.getPaymentInfo(id, null, null, null, false);
    }

    /** adds a payment method/payment method type */
    public CartPaymentInfo addPaymentAmount(String id, BigDecimal amount, String refNum, String authCode, boolean isSingleUse, boolean isPresent, boolean replace) {
        CartPaymentInfo inf = this.getPaymentInfo(id, refNum, authCode, amount, replace);
        if (isSalesOrder()) {
            GenericValue billingAddress = inf.getBillingAddress(this.getDelegator());
            if (billingAddress != null) {
                // this payment method will set the billing address for the order;
                // before it is set we have to verify if the billing address is
                // compatible with the ProductGeos
                for (GenericValue product : ShoppingCart.getItemsProducts(this.cartLines)) {
                    if (!ProductWorker.isBillableToAddress(product, billingAddress)) {
                        throw new IllegalArgumentException("The billing address is not compatible with ProductGeos rules.");
                    }
                }
            }
        }
        inf.singleUse = isSingleUse;
        inf.isPresent = isPresent;
        if (replace) {
            paymentInfo.remove(inf);
        }
        paymentInfo.add(inf);

        return inf;
    }

    /** adds a payment method/payment method type */
    public CartPaymentInfo addPaymentAmount(String id, BigDecimal amount, boolean isSingleUse) {
        return this.addPaymentAmount(id, amount, null, null, isSingleUse, false, true);
    }

    /** adds a payment method/payment method type */
    public CartPaymentInfo addPaymentAmount(String id, BigDecimal amount) {
        return this.addPaymentAmount(id, amount, false);
    }

    /** adds a payment method/payment method type */
    public CartPaymentInfo addPayment(String id) {
        return this.addPaymentAmount(id, null, false);
    }

    /** returns the payment method/payment method type amount */
    public BigDecimal getPaymentAmount(String id) {
        return this.getPaymentInfo(id).amount;
    }

    /**
     * SCIPIO: returns the original payment method/payment method type amount as specified
     * upon payment meth info creation (usually by the user).
     */
    public BigDecimal getPaymentOrigAmount(String id) {
        return this.getPaymentInfo(id).origAmount;
    }

    /** SCIPIO: Returns all payment amounts */
    public Map<String, BigDecimal> getPaymentAmountsByIdOrType() {
        // BASED ON OrderReadHelper.getOrderPaymentPreferenceTotalsByIdOrType
        // NOTE: Summing may be redundant but should not hurt...
        Map<String, BigDecimal> totals = new HashMap<>();
        for(CartPaymentInfo info : paymentInfo) {
            if (info.amount == null) continue;
            if (UtilValidate.isNotEmpty(info.paymentMethodId)) {
                BigDecimal total = totals.get(info.paymentMethodId);
                if (total == null) {
                    total = BigDecimal.ZERO;
                }
                total = total.add(info.amount).setScale(scale, rounding);
                totals.put(info.paymentMethodId, total);
            } else if (UtilValidate.isNotEmpty(info.paymentMethodTypeId)) {
                BigDecimal total = totals.get(info.paymentMethodTypeId);
                if (total == null) {
                    total = BigDecimal.ZERO;
                }
                total = total.add(info.amount).setScale(scale, rounding);
                totals.put(info.paymentMethodTypeId, total);
            }
        }
        return totals;
    }

    public void addPaymentRef(String id, String ref, String authCode) {
        this.getPaymentInfo(id).refNum[0] = ref;
        this.getPaymentInfo(id).refNum[1] = authCode;
    }

    public String getPaymentRef(String id) {
        for (CartPaymentInfo inf : paymentInfo) {
            if (inf.paymentMethodId.equals(id) || inf.paymentMethodTypeId.equals(id)) {
                return inf.refNum[0];
            }
        }
        return null;
    }

    /** returns the total payment amounts */
    public BigDecimal getPaymentTotal() {
        BigDecimal total = BigDecimal.ZERO;
        for (CartPaymentInfo inf : paymentInfo) {
            if (inf.amount != null) {
                total = total.add(inf.amount);
            }
        }
        return total;
    }

    /**
     * SCIPIO: Verifies if current payment methods in cart are adequate enough to cover the current order, or in
     * other words the cart payments in current state can effectively be used to pay for the order.
     */
    public boolean isPaymentsAdequate() {
        return CheckOutHelper.isPaymentsAdequate(this);
    }

    public int selectedPayments() {
        return paymentInfo.size();
    }

    public boolean isPaymentSelected(String id) {
        CartPaymentInfo inf = this.getPaymentInfo(id);
        return paymentInfo.contains(inf);
    }

    /** removes a specific payment method/payment method type */
    public void clearPayment(String id) {
        CartPaymentInfo inf = this.getPaymentInfo(id);
        paymentInfo.remove(inf);
    }

    /** removes a specific payment info from the list */
    public void clearPayment(int index) {
        paymentInfo.remove(index);
    }

    /** clears all payment method/payment method types */
    public void clearPayments() {
        this.expireSingleUsePayments();
        paymentInfo.clear();
    }

    /** remove all the paymentMethods based on the paymentMethodIds */
    public void clearPaymentMethodsById(List<String> paymentMethodIdsToRemove) {
        if (UtilValidate.isEmpty(paymentMethodIdsToRemove)) {
            return;
        }
        for (Iterator<CartPaymentInfo> iter = paymentInfo.iterator(); iter.hasNext();) {
            CartPaymentInfo info = iter.next();
            if (paymentMethodIdsToRemove.contains(info.paymentMethodId)) {
                iter.remove();
            }
        }
    }

    /** remove declined payment methods for an order from cart.  The idea is to call this after an attempted order is rejected */
    public void clearDeclinedPaymentMethods(Delegator delegator) {
        String orderId = this.getOrderId();
        if (UtilValidate.isNotEmpty(orderId)) {
            try {
                List<GenericValue> declinedPaymentMethods = EntityQuery.use(delegator).from("OrderPaymentPreference").where("orderId", orderId, "statusId", "PAYMENT_DECLINED").queryList();
                if (UtilValidate.isNotEmpty(declinedPaymentMethods)) {
                    List<String> paymentMethodIdsToRemove = new ArrayList<>();
                    for (GenericValue opp : declinedPaymentMethods) {
                        paymentMethodIdsToRemove.add(opp.getString("paymentMethodId"));
                    }
                    clearPaymentMethodsById(paymentMethodIdsToRemove);
                }
            } catch (GenericEntityException ex) {
                Debug.logError("Unable to remove declined payment methods from cart due to " + ex.getMessage(), module);
                return;
            }
        }
    }

    private void expireSingleUsePayments() {
        Timestamp now = UtilDateTime.nowTimestamp();
        for (CartPaymentInfo inf : paymentInfo) {
            if (inf.paymentMethodId == null || !inf.singleUse) {
                continue;
            }

            GenericValue paymentMethod = null;
            try {
                paymentMethod = this.getDelegator().findOne("PaymentMethod", UtilMisc.toMap("paymentMethodId", inf.paymentMethodId), false);
            } catch (GenericEntityException e) {
                Debug.logError(e, "ERROR: Unable to get payment method record to expire : " + inf.paymentMethodId, module);
            }
            if (paymentMethod != null) {
                paymentMethod.set("thruDate", now);
                try {
                    paymentMethod.store();
                } catch (GenericEntityException e) {
                    Debug.logError(e, "Unable to store single use PaymentMethod record : " + paymentMethod, module);
                }
            } else {
                Debug.logError("ERROR: Received back a null payment method record for expired ID : " + inf.paymentMethodId, module);
            }
        }
    }

    /** Returns the Payment Method Ids */
    public List<String> getPaymentMethodIds() {
        List<String> pmi = new ArrayList<>();
        for (CartPaymentInfo inf : paymentInfo) {
            if (inf.paymentMethodId != null) {
                pmi.add(inf.paymentMethodId);
            }
        }
        return pmi;
    }

    /** Returns the Payment Method Type Ids */
    public List<String> getPaymentMethodTypeIds() {
        List<String> pmt = new ArrayList<>();
        for (CartPaymentInfo inf : paymentInfo) {
            if (inf.paymentMethodTypeId != null) {
                pmt.add(inf.paymentMethodTypeId);
            }
        }
        return pmt;
    }

    /** SCIPIO: Returns the Payment Method Ids that have no paymentMethodIds */
    public List<String> getPaymentMethodTypeIdsNoPaymentMethodIds() {
        List<String> pmt = new ArrayList<>();
        for (CartPaymentInfo inf : paymentInfo) {
            if (inf.paymentMethodId == null && inf.paymentMethodTypeId != null) {
                pmt.add(inf.paymentMethodTypeId);
            }
        }
        return pmt;
    }

    /** Returns a list of PaymentMethod value objects selected in the cart */
    public List<GenericValue> getPaymentMethods() {
        List<GenericValue> methods = new ArrayList<>();
        if (UtilValidate.isNotEmpty(paymentInfo)) {
            for (String paymentMethodId : getPaymentMethodIds()) {
                try {
                    GenericValue paymentMethod = this.getDelegator().findOne("PaymentMethod", UtilMisc.toMap("paymentMethodId", paymentMethodId), true);
                    if (paymentMethod != null) {
                        methods.add(paymentMethod);
                    } else {
                        Debug.logError("Error getting cart payment methods, the paymentMethodId [" + paymentMethodId +"] is not valid", module);
                    }
                } catch (GenericEntityException e) {
                    Debug.logError(e, "Unable to get payment method from the database", module);
                }
            }
        }

        return methods;
    }

    /** Returns a list of PaymentMethodType value objects selected in the cart */
    public List<GenericValue> getPaymentMethodTypes() {
        List<GenericValue> types = new ArrayList<>();
        if (UtilValidate.isNotEmpty(paymentInfo)) {
            for (String id : getPaymentMethodTypeIds()) {
                try {
                    types.add(this.getDelegator().findOne("PaymentMethodType", UtilMisc.toMap("paymentMethodTypeId", id), true));
                } catch (GenericEntityException e) {
                    Debug.logError(e, "Unable to get payment method type from the database", module);
                }
            }
        }

        return types;
    }

    public List<GenericValue> getCreditCards() {
        List<GenericValue> paymentMethods = this.getPaymentMethods();
        List<GenericValue> creditCards = new ArrayList<>();
        for (GenericValue pm : paymentMethods) {
            if ("CREDIT_CARD".equals(pm.getString("paymentMethodTypeId"))) {
                try {
                    GenericValue cc = pm.getRelatedOne("CreditCard", false);
                    creditCards.add(cc);
                } catch (GenericEntityException e) {
                    Debug.logError(e, "Unable to get credit card record from payment method : " + pm, module);
                }
            }
        }

        return creditCards;
    }

    public List<GenericValue> getGiftCards() {
        List<GenericValue> paymentMethods = this.getPaymentMethods();
        List<GenericValue> giftCards = new ArrayList<>();
        for (GenericValue pm : paymentMethods) {
            if ("GIFT_CARD".equals(pm.getString("paymentMethodTypeId"))) {
                try {
                    GenericValue gc = pm.getRelatedOne("GiftCard", false);
                    giftCards.add(gc);
                } catch (GenericEntityException e) {
                    Debug.logError(e, "Unable to get gift card record from payment method : " + pm, module);
                }
            }
        }

        return giftCards;
    }

    /* determines if the id supplied is a payment method or not by searching in the entity engine */
    public boolean isPaymentMethodType(String id) {
        GenericValue paymentMethodType = null;
        try {
            paymentMethodType = this.getDelegator().findOne("PaymentMethodType", UtilMisc.toMap("paymentMethodTypeId", id), true);
        } catch (GenericEntityException e) {
            Debug.logInfo(e, "Problems getting PaymentMethodType", module);
        }
        if (paymentMethodType == null) {
            return false;
        }
        return true;
    }

    public GenericValue getBillingAddress() {
        GenericValue billingAddress = null;
        for (CartPaymentInfo inf : paymentInfo) {
            billingAddress = inf.getBillingAddress(this.getDelegator());
            if (billingAddress != null) {
                break;
            }
        }
        return billingAddress;
    }

    /**
     * Returns ProductStoreFinActSetting based on cart's productStoreId and FinAccountHelper's defined giftCertFinAcctTypeId
     * @param delegator the delegator
     * @return returns ProductStoreFinActSetting based on cart's productStoreId
     * @throws GenericEntityException
     */
    public GenericValue getGiftCertSettingFromStore(Delegator delegator) throws GenericEntityException {
        return EntityQuery.use(delegator).from("ProductStoreFinActSetting").where("productStoreId", getProductStoreId(), "finAccountTypeId", FinAccountHelper.giftCertFinAccountTypeId).cache().queryOne();
    }

    /**
     * Determines whether pin numbers are required for gift cards, based on ProductStoreFinActSetting.  Default to true.
     * @param delegator the delegator
     * @return returns true whether pin numbers are required for gift card
     */
    public boolean isPinRequiredForGC(Delegator delegator) {
        try {
            GenericValue giftCertSettings = getGiftCertSettingFromStore(delegator);
            if (giftCertSettings != null) {
                if ("Y".equals(giftCertSettings.getString("requirePinCode"))) {
                    return true;
                }
                return false;
            }
            Debug.logWarning("No product store gift certificate settings found for store [" + getProductStoreId() + "]",
                    module);
            return true;
        } catch (GenericEntityException ex) {
            Debug.logError("Error checking if store requires pin number for GC: " + ex.getMessage(), module);
            return true;
        }
    }

    /**
     * Returns whether the cart should validate gift cards against FinAccount (ie, internal gift certificates).  Defaults to false.
     * @param delegator the delegator
     * @return returns true whether the cart should validate gift cards against FinAccount
     */
    public boolean isValidateGCFinAccount(Delegator delegator) {
        try {
            GenericValue giftCertSettings = getGiftCertSettingFromStore(delegator);
            if (giftCertSettings != null) {
                if ("Y".equals(giftCertSettings.getString("validateGCFinAcct"))) {
                    return true;
                }
                return false;
            }
            Debug.logWarning("No product store gift certificate settings found for store [" + getProductStoreId() + "]",
                    module);
            return false;
        } catch (GenericEntityException ex) {
            Debug.logError("Error checking if store requires pin number for GC: " + ex.getMessage(), module);
            return false;
        }
    }

    // =======================================================================
    // Billing Accounts
    // =======================================================================

    /** Sets the billing account id string. */
    public void setBillingAccount(String billingAccountId, BigDecimal amount) {
        this.billingAccountId = billingAccountId;
        this.billingAccountAmt = amount;
    }

    /** Returns the billing message string. */
    public String getBillingAccountId() {
        return this.billingAccountId;
    }

    /** Returns the amount to be billed to the billing account.*/
    public BigDecimal getBillingAccountAmount() {
        return this.billingAccountAmt;
    }

    // =======================================================================
    // Shipping Charges
    // =======================================================================

    /** Returns the order level shipping amount */
    public BigDecimal getOrderShipping() {
        return OrderReadHelper.calcOrderAdjustments(this.getAdjustments(), this.getSubTotal(), false, false, true);
    }

    // ----------------------------------------
    // Ship Group Methods
    // ----------------------------------------

    public int addShipInfo() {
        CartShipInfo csi = new CartShipInfo();
        csi.orderTypeId = getOrderType();
        shipInfo.add(csi);
        return (shipInfo.size() - 1);
    }

    public List<CartShipInfo> getShipGroups() {
        return this.shipInfo;
    }

    public Map<Integer, BigDecimal> getShipGroups(ShoppingCartItem item) {
        Map<Integer, BigDecimal> shipGroups = new LinkedHashMap<>();
        if (item != null) {
            for (int i = 0; i < this.shipInfo.size(); i++) {
                CartShipInfo csi = shipInfo.get(i);
                CartShipInfo.CartShipItemInfo csii = csi.shipItemInfo.get(item);
                if (csii != null) {
                    if (this.checkShipItemInfo(csi, csii)) {
                        shipGroups.put(i, csii.quantity);
                    }
                }
            }
        }
        return shipGroups;
    }

    public Map<Integer, BigDecimal> getShipGroups(int itemIndex) {
        return this.getShipGroups(this.findCartItem(itemIndex));
    }

    public CartShipInfo getShipInfo(int idx) {
        if (idx == -1) {
            return null;
        }

        if (shipInfo.size() == idx) {
            CartShipInfo csi = new CartShipInfo();
            csi.orderTypeId = getOrderType();
            shipInfo.add(csi);
        }

        return shipInfo.get(idx);
    }

    public int getShipGroupSize() {
        return this.shipInfo.size();
    }

    /** Returns the ShoppingCartItem (key) and quantity (value) associated with the ship group */
    public Map<ShoppingCartItem, BigDecimal> getShipGroupItems(int idx) {
        CartShipInfo csi = this.getShipInfo(idx);
        Map<ShoppingCartItem, BigDecimal> qtyMap = new HashMap<>();
        for (ShoppingCartItem item : csi.shipItemInfo.keySet()) {
            CartShipInfo.CartShipItemInfo csii = csi.shipItemInfo.get(item);
            qtyMap.put(item, csii.quantity);
        }
        return qtyMap;
    }

    public void clearItemShipInfo(ShoppingCartItem item) {
        for (int i = 0; i < shipInfo.size(); i++) {
            CartShipInfo csi = this.getShipInfo(i);
            csi.shipItemInfo.remove(item);
        }

        // DEJ20100107: commenting this out because we do NOT want to clear out ship group info since there is information there that will be lost; good enough to clear the item/group association which can be restored later (though questionable, the whole processes using this should be rewritten to not destroy information!
        // this.cleanUpShipGroups();
    }

    public void setItemShipGroupEstimate(BigDecimal amount, int idx) {
        CartShipInfo csi = this.getShipInfo(idx);
        csi.shipEstimate = amount;
    }

    /**
     * Updates the shipBefore and shipAfterDates of all ship groups that the item belongs to, re-setting
     * ship group ship before date if item ship before date is before it and ship group ship after date if
     * item ship after date is before it.
     * @param item
     */
    public void setShipGroupShipDatesFromItem(ShoppingCartItem item) {
        Map<Integer, BigDecimal> shipGroups = this.getShipGroups(item);

        if (shipGroups.keySet() != null) {
            for (Integer shipGroup : shipGroups.keySet()) {
                CartShipInfo cartShipInfo = this.getShipInfo(shipGroup);

                cartShipInfo.resetShipAfterDateIfBefore(item.getShipAfterDate());
                cartShipInfo.resetShipBeforeDateIfAfter(item.getShipBeforeDate());
            }
        }
    }

    public BigDecimal getItemShipGroupEstimate(int idx) {
        CartShipInfo csi = this.getShipInfo(idx);
        return csi.shipEstimate;
    }

    public void setItemShipGroupQty(int itemIndex, BigDecimal quantity, int idx) {
        ShoppingCartItem itemIdx = this.findCartItem(itemIndex);
        if(itemIdx != null) {
            this.setItemShipGroupQty(itemIdx, itemIndex, quantity, idx);
        }
    }

    public void setItemShipGroupQty(ShoppingCartItem item, BigDecimal quantity, int idx) {
        this.setItemShipGroupQty(item, this.getItemIndex(item), quantity, idx);
    }

    public void setItemShipGroupQty(ShoppingCartItem item, int itemIndex, BigDecimal quantity, int idx) {
        if (itemIndex > -1) {
            CartShipInfo csi = this.getShipInfo(idx);

            // never set less than zero
            if (quantity.compareTo(BigDecimal.ZERO) < 0) {
                quantity = BigDecimal.ZERO;
            }

            // never set more than quantity ordered
            if (item != null) {
                if (quantity.compareTo(item.getQuantity()) > 0) {
                    quantity = item.getQuantity();
                }


                // re-set the ship group's before and after dates based on the item's
                csi.resetShipBeforeDateIfAfter(item.getShipBeforeDate());
                csi.resetShipAfterDateIfBefore(item.getShipAfterDate());

                CartShipInfo.CartShipItemInfo csii = csi.setItemInfo(item, quantity);
                this.checkShipItemInfo(csi, csii);
            }
        }
    }

    public BigDecimal getItemShipGroupQty(ShoppingCartItem item, int idx) {
        if (item != null) {
            CartShipInfo csi = this.getShipInfo(idx);
            CartShipInfo.CartShipItemInfo csii = csi.shipItemInfo.get(item);
            if (csii != null) {
                return csii.quantity;
            }
        }
        return BigDecimal.ZERO;
    }

    public BigDecimal getItemShipGroupQty(int itemIndex, int idx) {
        return this.getItemShipGroupQty(this.findCartItem(itemIndex), idx);
    }

    public void positionItemToGroup(int itemIndex, BigDecimal quantity, int fromIndex, int toIndex, boolean clearEmptyGroups) {
        this.positionItemToGroup(this.findCartItem(itemIndex), quantity, fromIndex, toIndex, clearEmptyGroups);
    }

    public void positionItemToGroup(ShoppingCartItem item, BigDecimal quantity, int fromIndex, int toIndex, boolean clearEmptyGroups) {
        if (fromIndex == toIndex || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            // do nothing
            return;
        }

        // get the ship groups; create the TO group if needed
        CartShipInfo fromGroup = this.getShipInfo(fromIndex);
        CartShipInfo toGroup = null;
        if (toIndex == -1) {
            toGroup = new CartShipInfo();
            toGroup.orderTypeId = getOrderType();
            this.shipInfo.add(toGroup);
            toIndex = this.shipInfo.size() - 1;
        } else {
            toGroup = this.getShipInfo(toIndex);
        }

        // adjust the quantities
        if (fromGroup != null && toGroup != null) {
            BigDecimal fromQty = this.getItemShipGroupQty(item, fromIndex);
            BigDecimal toQty = this.getItemShipGroupQty(item, toIndex);
            if (fromQty.compareTo(BigDecimal.ZERO) > 0) {
                if (quantity.compareTo(fromQty) > 0) {
                    quantity = fromQty;
                }
                fromQty = fromQty.subtract(quantity);
                toQty = toQty.add(quantity);
                this.setItemShipGroupQty(item, fromQty, fromIndex);
                this.setItemShipGroupQty(item, toQty, toIndex);
            }

            if (clearEmptyGroups) {
                // remove any empty ship groups
                this.cleanUpShipGroups();
            }
        }
    }

    // removes 0 quantity items
    protected boolean checkShipItemInfo(CartShipInfo csi, CartShipInfo.CartShipItemInfo csii) {
        if (csii.quantity.compareTo(BigDecimal.ZERO) == 0 || csii.item.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
            csi.shipItemInfo.remove(csii.item);
            return false;
        }
        return true;
    }

    public void cleanUpShipGroups() {
        Iterator<CartShipInfo> csi = this.shipInfo.iterator();
        while (csi.hasNext()) {
            CartShipInfo info = csi.next();
            Iterator<ShoppingCartItem> si = info.shipItemInfo.keySet().iterator();
            while (si.hasNext()) {
                ShoppingCartItem item = si.next();
                if (item.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
                    si.remove();
                }
            }
            if (info.shipItemInfo.size() == 0) {
                csi.remove();
            }
        }
    }

    public int getShipInfoIndex (String shipGroupSeqId) {
        int idx = -1;
        for (int i=0; i<shipInfo.size(); i++) {
            CartShipInfo csi = shipInfo.get(i);
            if (shipGroupSeqId.equals(csi.shipGroupSeqId)) {
                idx = i;
                break;
            }
        }
        return idx;
    }

    /**
    * Return index of the ship group where the item is located
    * @return
    */
    public int getItemShipGroupIndex(int itemId) {
        int shipGroupIndex = this.getShipGroupSize() - 1;
        ShoppingCartItem item = this.findCartItem(itemId);
        int result=0;
        for (int i = 0; i <(shipGroupIndex + 1); i++) {
           CartShipInfo csi = this.getShipInfo(i);
           Iterator<ShoppingCartItem> it = csi.shipItemInfo.keySet().iterator();
            while (it.hasNext()) {
                ShoppingCartItem item2 = it.next();
                if (item.equals(item2) ) {
                    result = i;
                }
            }
        }
        return result;
    }

    /** Sets the shipping contact mech id. */
    public void setShippingContactMechId(int idx, String shippingContactMechId) {
        CartShipInfo csi = this.getShipInfo(idx);
        if (isSalesOrder() && UtilValidate.isNotEmpty(shippingContactMechId)) {
            // Verify if the new address is compatible with the ProductGeos rules of
            // the products already in the cart
            GenericValue shippingAddress = null;
            try {
                shippingAddress = this.getDelegator().findOne("PostalAddress", UtilMisc.toMap("contactMechId", shippingContactMechId), false);
            } catch (GenericEntityException gee) {
                Debug.logError(gee, "Error retrieving the shipping address for contactMechId [" + shippingContactMechId + "].", module);
            }
            if (shippingAddress != null) {
                Set<ShoppingCartItem> shipItems = csi.getShipItems();
                if (UtilValidate.isNotEmpty(shipItems)) {
                    for (ShoppingCartItem cartItem : shipItems) {
                        GenericValue product = cartItem.getProduct();
                        if (product != null) {
                            if (!ProductWorker.isShippableToAddress(product, shippingAddress)) {
                                throw new IllegalArgumentException("The shipping address is not compatible with ProductGeos rules.");
                            }
                        }
                    }
                }
            }
        }
        csi.setContactMechId(shippingContactMechId);
    }

    /**
     * Sets @param shippingContactMechId in all ShipInfo(ShipGroups) associated
     * with this ShoppingCart
     * <p>
     * @param shippingContactMechId
     */
    public void setAllShippingContactMechId(String shippingContactMechId) {
        for (int x=0; x < shipInfo.size(); x++) {
            this.setShippingContactMechId(x, shippingContactMechId);
        }
    }

    /**
     * SCIPIO: Get all shipping contact mech IDs.
     */
    public List<String> getAllShippingContactMechId() {
        List<String> res = new ArrayList<>(shipInfo.size());
        for (CartShipInfo info : this.shipInfo) {
            res.add(info.getContactMechId());
        }
        return res;
    }

    /** Returns the shipping contact mech id. */
    public String getShippingContactMechId(int idx) {
        CartShipInfo csi = this.getShipInfo(idx);
        return csi.getContactMechId();
    }

    public String getShippingContactMechId() {
        return this.getShippingContactMechId(0);
    }

    /** Sets the shipment method type. */
    public void setShipmentMethodTypeId(int idx, String shipmentMethodTypeId) {
        CartShipInfo csi = this.getShipInfo(idx);
        csi.shipmentMethodTypeId = shipmentMethodTypeId;
    }

    /**
     * Sets @param shipmentMethodTypeId in all ShipInfo(ShipGroups) associated
     * with this ShoppingCart
     * <p>
     * @param shipmentMethodTypeId
     */
    public void setAllShipmentMethodTypeId(String shipmentMethodTypeId) {
        for (int x=0; x < shipInfo.size(); x++) {
            this.setShipmentMethodTypeId(x, shipmentMethodTypeId);
        }
    }

    /** Returns the shipment method type ID */
    public String getShipmentMethodTypeId(int idx) {
        CartShipInfo csi = this.getShipInfo(idx);
        return csi.shipmentMethodTypeId;
    }

    public String getShipmentMethodTypeId() {
        return this.getShipmentMethodTypeId(0);
    }

    /** Returns the shipment method type. */
    public GenericValue getShipmentMethodType(int idx) {
        String shipmentMethodTypeId = this.getShipmentMethodTypeId(idx);
        if (UtilValidate.isNotEmpty(shipmentMethodTypeId)) {
            try {
                return this.getDelegator().findOne("ShipmentMethodType",
                        UtilMisc.toMap("shipmentMethodTypeId", shipmentMethodTypeId), false);
            } catch (GenericEntityException e) {
                Debug.logWarning(e, module);
            }
        }
        return null;
    }

    /** Sets the supplier for the given ship group (drop shipment). */
    public void setSupplierPartyId(int idx, String supplierPartyId) {
        CartShipInfo csi = this.getShipInfo(idx);
        // TODO: before we set the value we have to verify if all the products
        //       already in this ship group are drop shippable from the supplier
        csi.supplierPartyId = supplierPartyId;
    }

    /** Returns the supplier for the given ship group (drop shipment). */
    public String getSupplierPartyId(int idx) {
        CartShipInfo csi = this.getShipInfo(idx);
        return csi.supplierPartyId;
    }

    /** Sets the shipping instructions. */
    public void setShippingInstructions(int idx, String shippingInstructions) {
        CartShipInfo csi = this.getShipInfo(idx);
        csi.shippingInstructions = shippingInstructions;
    }

    /**
     * Sets @param shippingInstructions in all ShipInfo(ShipGroups) associated
     * with this ShoppingCart
     * <p>
     * @param shippingInstructions
     */
    public void setAllShippingInstructions(String shippingInstructions) {
        for (int x=0; x < shipInfo.size(); x++) {
            this.setShippingInstructions(x, shippingInstructions);
        }
    }

    /** Returns the shipping instructions. */
    public String getShippingInstructions(int idx) {
        CartShipInfo csi = this.getShipInfo(idx);
        return csi.shippingInstructions;
    }

    public String getShippingInstructions() {
        return this.getShippingInstructions(0);
    }

    public void setMaySplit(int idx, Boolean maySplit) {
        CartShipInfo csi = this.getShipInfo(idx);
        if (UtilValidate.isNotEmpty(maySplit)) {
            csi.setMaySplit(maySplit);
        }
    }

    /**
     * Sets @param maySplit in all ShipInfo(ShipGroups) associated
     * with this ShoppingCart
     * <p>
     * @param maySplit
     */
    public void setAllMaySplit(Boolean maySplit) {
        for (int x=0; x < shipInfo.size(); x++) {
            this.setMaySplit(x, maySplit);
        }
    }


    /** Returns Boolean.TRUE if the order may be split (null if unspecified) */
    public String getMaySplit(int idx) {
        CartShipInfo csi = this.getShipInfo(idx);
        return csi.maySplit;
    }

    public String getMaySplit() {
        return this.getMaySplit(0);
    }

    public void setGiftMessage(int idx, String giftMessage) {
        CartShipInfo csi = this.getShipInfo(idx);
        csi.giftMessage = giftMessage;
    }

    /**
     * Sets @param giftMessage in all ShipInfo(ShipGroups) associated
     * with this ShoppingCart
     * <p>
     * @param giftMessage
     */
    public void setAllGiftMessage(String giftMessage) {
        for (int x=0; x < shipInfo.size(); x++) {
            this.setGiftMessage(x, giftMessage);
        }
    }

    public String getGiftMessage(int idx) {
        CartShipInfo csi = this.getShipInfo(idx);
        return csi.giftMessage;
    }

    public String getGiftMessage() {
        return this.getGiftMessage(0);
    }

    public void setIsGift(int idx, Boolean isGift) {
        CartShipInfo csi = this.getShipInfo(idx);
        if (UtilValidate.isNotEmpty(isGift)) {
            csi.isGift = isGift ? "Y" : "N";
        }
    }

    /**
     * Sets @param isGift in all ShipInfo(ShipGroups) associated
     * with this ShoppingCart
     * <p>
     * @param isGift
     */
    public void setAllIsGift(Boolean isGift) {
        for (int x=0; x < shipInfo.size(); x++) {
            this.setIsGift(x, isGift);
        }
    }

    public String getIsGift(int idx) {
        CartShipInfo csi = this.getShipInfo(idx);
        return csi.isGift;
    }

    public String getIsGift() {
        return this.getIsGift(0);
    }

    public void setCarrierPartyId(int idx, String carrierPartyId) {
        CartShipInfo csi = this.getShipInfo(idx);
        csi.carrierPartyId = carrierPartyId;
    }

    /**
     * Sets @param carrierPartyId in all ShipInfo(ShipGroups) associated
     * with this ShoppingCart
     * <p>
     * @param carrierPartyId
     */
    public void setAllCarrierPartyId(String carrierPartyId) {
        for (int x=0; x < shipInfo.size(); x++) {
            this.setCarrierPartyId(x, carrierPartyId);
        }
    }

    public String getCarrierPartyId(int idx) {
        CartShipInfo csi = this.getShipInfo(idx);
        return csi.carrierPartyId;
    }

    public String getCarrierPartyId() {
        return this.getCarrierPartyId(0);
    }

    public String getProductStoreShipMethId(int idx) {
        CartShipInfo csi = this.getShipInfo(idx);
        return csi.productStoreShipMethId;
    }

    public String getProductStoreShipMethId() {
        return this.getProductStoreShipMethId(0);
    }

    public void setProductStoreShipMethId(int idx, String productStoreShipMethId) {
        CartShipInfo csi = this.getShipInfo(idx);
        csi.productStoreShipMethId = productStoreShipMethId;
    }

    /**
     * Sets @param productStoreShipMethId in all ShipInfo(ShipGroups) associated
     * with this ShoppingCart
     * <p>
     * @param productStoreShipMethId
     */
    public void setAllProductStoreShipMethId(String productStoreShipMethId) {
        for (int x=0; x < shipInfo.size(); x++) {
            this.setProductStoreShipMethId(x, productStoreShipMethId);
        }
    }

    public void setShipGroupFacilityId(int idx, String facilityId) {
        CartShipInfo csi = this.getShipInfo(idx);
        csi.facilityId = facilityId;
    }

    public String getShipGroupFacilityId(int idx) {
        CartShipInfo csi = this.getShipInfo(idx);
        return csi.facilityId;
    }

    public void setShipGroupVendorPartyId(int idx, String vendorPartyId) {
        CartShipInfo csi = this.getShipInfo(idx);
        csi.vendorPartyId = vendorPartyId;
    }

    public String getShipGroupVendorPartyId(int idx) {
        CartShipInfo csi = this.getShipInfo(idx);
        return csi.vendorPartyId;
    }

    public void setShipGroupSeqId(int idx, String shipGroupSeqId) {
        CartShipInfo csi = this.getShipInfo(idx);
        csi.shipGroupSeqId = shipGroupSeqId;
    }

    public String getShipGroupSeqId(int idx) {
        CartShipInfo csi = this.getShipInfo(idx);
        return csi.shipGroupSeqId;
    }

    public void setOrderAdditionalEmails(String orderAdditionalEmails) {
        this.orderAdditionalEmails = orderAdditionalEmails;
    }

    public String getOrderAdditionalEmails() {
        return orderAdditionalEmails;
    }

    public GenericValue getShippingAddress(int idx) {
        if (this.getShippingContactMechId(idx) != null) {
            try {
                return getDelegator().findOne("PostalAddress", UtilMisc.toMap("contactMechId", this.getShippingContactMechId(idx)), false);
            } catch (GenericEntityException e) {
                Debug.logWarning(e.toString(), module);
                return null;
            }
        }
        return null;
    }

    public GenericValue getShippingAddress() {
        return this.getShippingAddress(0);
    }

    // ----------------------------------------
    // internal/public notes
    // ----------------------------------------

    public List<String> getInternalOrderNotes() {
        return this.internalOrderNotes;
    }

    public List<String> getOrderNotes() {
        return this.orderNotes;
    }

    public void addInternalOrderNote(String note) {
        this.internalOrderNotes.add(note);
    }

    public void clearInternalOrderNotes() {
        this.internalOrderNotes.clear();
    }
    public void clearOrderNotes() {
        this.orderNotes.clear();
    }

    public void addOrderNote(String note) {
        this.orderNotes.add(note);
    }

    // Preset with default values some of the checkout options to get a quicker checkout process.
    public void setDefaultCheckoutOptions(LocalDispatcher dispatcher) {
        // skip the add party screen
        this.setAttribute("addpty", "Y");
        if ("SALES_ORDER".equals(getOrderType())) {
            // checkout options for sales orders
            // set as the default shipping location the first from the list of available shipping locations
            if (this.getPartyId() != null && !"_NA_".equals(this.getPartyId())) {
                try {
                    GenericValue orderParty = this.getDelegator().findOne("Party", UtilMisc.toMap("partyId", this.getPartyId()), false);
                    Collection<GenericValue> shippingContactMechList = ContactHelper.getContactMech(orderParty, "SHIPPING_LOCATION", "POSTAL_ADDRESS", false);
                    if (UtilValidate.isNotEmpty(shippingContactMechList)) {
                        GenericValue shippingContactMech = (shippingContactMechList.iterator()).next();
                        this.setAllShippingContactMechId(shippingContactMech.getString("contactMechId"));
                    }
                } catch (GenericEntityException e) {
                    Debug.logError(e, "Error setting shippingContactMechId in setDefaultCheckoutOptions() method.", module);
                }
            }
            // set the default shipment method
            ShippingEstimateWrapper shipEstimateWrapper = org.ofbiz.order.shoppingcart.shipping.ShippingEstimateWrapper.getWrapper(dispatcher, this, 0);
            GenericValue carrierShipmentMethod = EntityUtil.getFirst(shipEstimateWrapper.getShippingMethods());
            if (carrierShipmentMethod != null) {
                this.setAllShipmentMethodTypeId(carrierShipmentMethod.getString("shipmentMethodTypeId"));
                this.setAllCarrierPartyId(carrierShipmentMethod.getString("partyId"));
            }
        } else {
            // checkout options for purchase orders
            // TODO: should we select a default agreement? For now we don't do this.
            // skip the order terms selection step
            this.setOrderTermSet(true);
            // set as the default shipping location the first from the list of available shipping locations
            String companyId = this.getBillToCustomerPartyId();
            if (companyId != null) {
                // the facilityId should be set prior to triggering default options, otherwise we do not set up facility information
                String defaultFacilityId = getFacilityId();
                if (defaultFacilityId != null) {
                    GenericValue facilityContactMech = ContactMechWorker.getFacilityContactMechByPurpose(this.getDelegator(), facilityId, UtilMisc.toList("SHIPPING_LOCATION", "PRIMARY_LOCATION"));
                    if (facilityContactMech != null) {
                        this.setShippingContactMechId(0, facilityContactMech.getString("contactMechId"));
                    }
                }
            }
            // shipping options
            this.setAllShipmentMethodTypeId("NO_SHIPPING");
            this.setAllCarrierPartyId("_NA_");
            this.setAllShippingInstructions("");
            this.setAllGiftMessage("");
            this.setAllMaySplit(Boolean.TRUE);
            this.setAllIsGift(Boolean.FALSE);
        }
    }

    // Returns the tax amount for a ship group. */
    public BigDecimal getTotalSalesTax(int shipGroup) {
        CartShipInfo csi = this.getShipInfo(shipGroup);
        return csi.getTotalTax(this);
    }

    /** Returns the tax amount from the cart object. */
    public BigDecimal getTotalSalesTax() {
        BigDecimal totalTax = BigDecimal.ZERO;
        for (int i = 0; i < shipInfo.size(); i++) {
            CartShipInfo csi = this.getShipInfo(i);
            totalTax = totalTax.add(csi.getTotalTax(this)).setScale(taxCalcScale, taxRounding);
        }
        return totalTax.setScale(taxFinalScale, taxRounding);
    }

    /** SCIPIO: Returns the tax amount from the cart object. */
    public BigDecimal getTotalVATTax() {
        BigDecimal totalTax = ZERO;
        for (int i = 0; i < shipInfo.size(); i++) {
            CartShipInfo csi = this.getShipInfo(i);
            totalTax = totalTax.add(csi.getTotalVATTax(this)).setScale(taxCalcScale, taxRounding);
        }
        return totalTax.setScale(taxFinalScale, taxRounding);
    }

    /** Returns the shipping amount from the cart object. */
    public BigDecimal getTotalShipping() {
        BigDecimal tempShipping = BigDecimal.ZERO;

        for (CartShipInfo csi : this.shipInfo) {
            tempShipping = tempShipping.add(csi.shipEstimate);
        }

        return tempShipping;
    }

    /** Returns the item-total in the cart (not including discount/tax/shipping). */
    public BigDecimal getItemTotal() {
        BigDecimal itemTotal = BigDecimal.ZERO;
        for (ShoppingCartItem cartItem : this.cartLines) {
            itemTotal = itemTotal.add(cartItem.getBasePrice());
        }
        return itemTotal;
    }

    /** Returns the sub-total in the cart (item-total - discount). */
    public BigDecimal getSubTotal() {
        BigDecimal itemsTotal = BigDecimal.ZERO;
        for (ShoppingCartItem cartItem : this.cartLines) {
            itemsTotal = itemsTotal.add(cartItem.getItemSubTotal());
        }
        return itemsTotal;
    }

    /** Returns the total from the cart, including tax/shipping. */
    public BigDecimal getGrandTotal() {
        // sales tax and shipping are not stored as adjustments but rather as part of the ship group
        return this.getSubTotal().add(this.getTotalShipping()).add(this.getTotalSalesTax()).add(this.getOrderOtherAdjustmentTotal()).add(this.getOrderGlobalAdjustments());
    }

    public BigDecimal getDisplaySubTotal() {
        BigDecimal itemsTotal = BigDecimal.ZERO;
        for (ShoppingCartItem cartItem : this.cartLines) {
            itemsTotal = itemsTotal.add(cartItem.getDisplayItemSubTotal());
        }
        return itemsTotal;
    }
    public BigDecimal getOrderGlobalAdjustments() {
        List<GenericValue> cartAdjustments = this.getAdjustments();
        List<GenericValue> tempAdjustmentsList = new ArrayList<>();
        if (cartAdjustments != null) {
            Iterator<GenericValue> cartAdjustmentIter = cartAdjustments.iterator();
            while (cartAdjustmentIter.hasNext()) {
                GenericValue checkOrderAdjustment = cartAdjustmentIter.next();
                if (UtilValidate.isEmpty(checkOrderAdjustment.getString("shipGroupSeqId")) || DataModelConstants.SEQ_ID_NA.equals(checkOrderAdjustment.getString("shipGroupSeqId"))) {
                    tempAdjustmentsList.add(checkOrderAdjustment);
                }
            }
        }
        return OrderReadHelper.calcOrderAdjustments(tempAdjustmentsList, this.getSubTotal(), false, true, true);
    }
    public BigDecimal getDisplayTaxIncluded() {
        BigDecimal taxIncluded  = getDisplaySubTotal().subtract(getSubTotal());
        return taxIncluded.setScale(taxFinalScale, taxRounding);
    }

    public BigDecimal getDisplayRecurringSubTotal() {
        BigDecimal itemsTotal = BigDecimal.ZERO;
        for (ShoppingCartItem cartItem : this.cartLines) {
            itemsTotal = itemsTotal.add(cartItem.getDisplayItemRecurringSubTotal());
        }
        return itemsTotal;
    }

    /** Returns the total from the cart, including tax/shipping. */
    public BigDecimal getDisplayGrandTotal() {
        return this.getDisplaySubTotal().add(this.getTotalShipping()).add(this.getTotalSalesTax()).add(this.getOrderOtherAdjustmentTotal()).add(this.getOrderGlobalAdjustments());
    }

    public BigDecimal getOrderOtherAdjustmentTotal() {
        return OrderReadHelper.calcOrderAdjustments(this.getAdjustments(), this.getSubTotal(), true, false, false);
    }

    /** Returns the sub-total in the cart (item-total - discount). */
    public BigDecimal getSubTotalForPromotions() {
        BigDecimal itemsTotal = BigDecimal.ZERO;
        for (ShoppingCartItem cartItem : this.cartLines) {
            GenericValue product = cartItem.getProduct();
            if (product != null && "N".equals(product.getString("includeInPromotions"))) {
                // don't include in total if this is the case...
                continue;
            }
            itemsTotal = itemsTotal.add(cartItem.getItemSubTotal());
        }
        return itemsTotal.add(this.getOrderOtherAdjustmentTotal());
    }
    public BigDecimal getSubTotalForPromotions(Set<String> productIds) {
        BigDecimal itemsTotal = BigDecimal.ZERO;
        for (ShoppingCartItem cartItem : this.cartLines) {
            GenericValue product = cartItem.getProduct();
            if (product == null || "N".equals(product.getString("includeInPromotions")) || !productIds.contains(cartItem.getProductId())) {
                // don't include in total if this is the case...
                continue;
            }
            itemsTotal = itemsTotal.add(cartItem.getItemSubTotal());
        }
        return itemsTotal;
    }
    
    /** SCIPIO (10/28/2018): Gets other adjustments and tax totals */
    public BigDecimal getOrderOtherAndTaxAdjustmentTotal() {
        return OrderReadHelper.calcOrderAdjustments(this.getAdjustments(), this.getSubTotal(), true, true, false);
    }
    /** SCIPIO (10/28/2018): Returns the total in the cart ((item-total + tax)  - discount). */
    public BigDecimal getTotalForPromotions() {
        BigDecimal itemsTotal = BigDecimal.ZERO;
        for (ShoppingCartItem cartItem : this.cartLines) {
            GenericValue product = cartItem.getProduct();
            if (product != null && "N".equals(product.getString("includeInPromotions"))) {
                // don't include in total if this is the case...
                continue;
            }
            itemsTotal = itemsTotal.add(cartItem.getItemSubTotal());
        }
        return itemsTotal.add(this.getOrderOtherAndTaxAdjustmentTotal());
    }
    public BigDecimal getTotalForPromotions(Set<String> productIds) {
        BigDecimal itemsTotal = BigDecimal.ZERO;
        for (ShoppingCartItem cartItem : this.cartLines) {
            GenericValue product = cartItem.getProduct();
            if (product == null || "N".equals(product.getString("includeInPromotions")) || !productIds.contains(cartItem.getProductId())) {
                // don't include in total if this is the case...
                continue;
            }
            itemsTotal = itemsTotal.add(cartItem.getItemSubTotal());
        }
        return itemsTotal.add(this.getOrderOtherAndTaxAdjustmentTotal());
    }

    /**
     * Get the total payment amount by payment type.  Specify null to get amount
     * over all types.
     */
    public BigDecimal getOrderPaymentPreferenceTotalByType(String paymentMethodTypeId) {
        BigDecimal total = BigDecimal.ZERO;
        String thisPaymentMethodTypeId = null;
        for (CartPaymentInfo payment : paymentInfo) {
            if (payment.amount == null) {
                continue;
            }
            if (payment.paymentMethodId != null) {
                try {
                    // need to determine the payment method type from the payment method
                    GenericValue paymentMethod = this.getDelegator().findOne("PaymentMethod", UtilMisc.toMap("paymentMethodId", payment.paymentMethodId), true);
                    if (paymentMethod != null) {
                        thisPaymentMethodTypeId = paymentMethod.getString("paymentMethodTypeId");
                    }
                } catch (GenericEntityException e) {
                    Debug.logError(e, e.getMessage(), module);
                }
            } else {
                thisPaymentMethodTypeId = payment.paymentMethodTypeId;
            }

            // add the amount according to paymentMethodType
            if (paymentMethodTypeId == null || paymentMethodTypeId.equals(thisPaymentMethodTypeId)) {
                total = total.add(payment.amount);
            }
        }
        return total;
    }

    public BigDecimal getCreditCardPaymentPreferenceTotal() {
        return getOrderPaymentPreferenceTotalByType("CREDIT_CARD");
    }

    public BigDecimal getBillingAccountPaymentPreferenceTotal() {
        return getOrderPaymentPreferenceTotalByType("EXT_BILLACT");
    }

    public BigDecimal getGiftCardPaymentPreferenceTotal() {
        return getOrderPaymentPreferenceTotalByType("GIFT_CARD");
    }

    /** Add a contact mech to this purpose; the contactMechPurposeTypeId is required */
    public void addContactMech(String contactMechPurposeTypeId, String contactMechId) {
        if (contactMechPurposeTypeId == null) {
            throw new IllegalArgumentException("You must specify a contactMechPurposeTypeId to add a ContactMech");
        }
        contactMechIdsMap.put(contactMechPurposeTypeId, contactMechId);
    }

    /** Get the contactMechId for this cart given the contactMechPurposeTypeId */
    public String getContactMech(String contactMechPurposeTypeId) {
        return contactMechIdsMap.get(contactMechPurposeTypeId);
    }

    /** Remove the contactMechId from this cart given the contactMechPurposeTypeId */
    public String removeContactMech(String contactMechPurposeTypeId) {
        return contactMechIdsMap.remove(contactMechPurposeTypeId);
    }

    public Map<String, String> getOrderContactMechIds() {
        return this.contactMechIdsMap;
    }

    /** Get a List of adjustments on the order (ie cart) */
    public List<GenericValue> getAdjustments() {
        return adjustments;
    }

    public int getAdjustmentPromoIndex(String productPromoId) {
        if (UtilValidate.isEmpty(productPromoId)) {
            return -1;
        }
        int index = adjustments.size();
        while (index > 0) {
            index--;
            if (productPromoId.equals(adjustments.get(index).getString("productPromoId"))) {
                return (index);
            }
        }
        return -1;
    }

    /** Add an adjustment to the order; don't worry about setting the orderId, orderItemSeqId or orderAdjustmentId; they will be set when the order is created */
    public int addAdjustment(GenericValue adjustment) {
        adjustments.add(adjustment);
        return adjustments.indexOf(adjustment);
    }

    public void removeAdjustment(int index) {
        adjustments.remove(index);
    }

    public GenericValue getAdjustment(int index) {
        return adjustments.get(index);
    }

    /** Get a List of orderTerms on the order (ie cart) */
    public List<GenericValue> getOrderTerms() {
        return orderTerms;
    }

    /** Add an orderTerm to the order */
    public int addOrderTerm(String termTypeId, BigDecimal termValue, Long termDays) {
        return addOrderTerm(termTypeId, termValue, termDays, null);
    }

    /** Add an orderTerm to the order */
    public int addOrderTerm(String termTypeId, BigDecimal termValue, Long termDays, String textValue) {
        GenericValue orderTerm = this.getDelegator().makeValue("OrderTerm");
        orderTerm.put("termTypeId", termTypeId);
        orderTerm.put("termValue", termValue);
        orderTerm.put("termDays", termDays);
        orderTerm.put("textValue", textValue);
        return addOrderTerm(orderTerm);
    }

    public int addOrderTerm(String termTypeId, String orderItemSeqId,BigDecimal termValue, Long termDays, String textValue, String description) {
        GenericValue orderTerm = this.getDelegator().makeValue("OrderTerm");
        orderTerm.put("termTypeId", termTypeId);
        if (UtilValidate.isEmpty(orderItemSeqId)) {
            orderItemSeqId = "_NA_";
        }
        orderTerm.put("orderItemSeqId", orderItemSeqId);
        orderTerm.put("termValue", termValue);
        orderTerm.put("termDays", termDays);
        orderTerm.put("textValue", textValue);
        orderTerm.put("description", description);
        return addOrderTerm(orderTerm);
    }

    /** Add an orderTerm to the order */
    public int addOrderTerm(GenericValue orderTerm) {
        orderTerms.add(orderTerm);
        return orderTerms.indexOf(orderTerm);
    }

    public void removeOrderTerm(int index) {
        orderTerms.remove(index);
    }

    public void removeOrderTerms() {
        orderTerms.clear();
    }

    public boolean isOrderTermSet() {
       return orderTermSet;
    }

    public void setOrderTermSet(boolean orderTermSet) {
         this.orderTermSet = orderTermSet;
     }

    public boolean hasOrderTerm(String termTypeId) {
        if (termTypeId == null) {
            return false;
        }
        for (GenericValue orderTerm : orderTerms) {
            if (termTypeId.equals(orderTerm.getString("termTypeId"))) {
                return true;
            }
        }
        return false;
    }

    public boolean isReadOnlyCart() {
       return readOnlyCart;
    }

    public void setReadOnlyCart(boolean readOnlyCart) {
         this.readOnlyCart = readOnlyCart;
     }

    /** go through the order adjustments and remove all adjustments with the given type */
    public void removeAdjustmentByType(String orderAdjustmentTypeId) {
        if (orderAdjustmentTypeId == null) {
            return;
        }

        // make a list of adjustment lists including the cart adjustments and the cartItem adjustments for each item
        List<List<GenericValue>> adjsLists = new ArrayList<>();

        adjsLists.add(this.getAdjustments());

        for (ShoppingCartItem item : this) {
            if (item.getAdjustments() != null) {
                adjsLists.add(item.getAdjustments());
            }
        }

        for (List<GenericValue> adjs: adjsLists) {

            if (adjs != null) {
                for (int i = 0; i < adjs.size();) {
                    GenericValue orderAdjustment = adjs.get(i);

                    if (orderAdjustmentTypeId.equals(orderAdjustment.getString("orderAdjustmentTypeId"))) {
                        adjs.remove(i);
                    } else {
                        i++;
                    }
                }
            }
        }
    }

    /** Returns the total weight in the cart. */
    public BigDecimal getTotalWeight() {
        BigDecimal weight = BigDecimal.ZERO;

        for (ShoppingCartItem item : this.cartLines) {
            weight = weight.add(item.getWeight().multiply(item.getQuantity()));
        }
        return weight;
    }

    /** Returns the total quantity in the cart. */
    public BigDecimal getTotalQuantity() {
        BigDecimal count = BigDecimal.ZERO;

        for (ShoppingCartItem item : this.cartLines) {
            count = count.add(item.getQuantity());
        }
        return count;
    }

    /** Returns the SHIPPABLE item-total in the cart for a specific ship group. */
    public BigDecimal getShippableTotal(int idx) {
        CartShipInfo info = this.getShipInfo(idx);
        BigDecimal itemTotal = BigDecimal.ZERO;

        for (ShoppingCartItem item : info.shipItemInfo.keySet()) {
            CartShipInfo.CartShipItemInfo csii = info.shipItemInfo.get(item);
            if (csii != null && csii.quantity.compareTo(BigDecimal.ZERO) > 0) {
                if (item.shippingApplies()) {
                    itemTotal = itemTotal.add(item.getItemSubTotal(csii.quantity));
                }
            }
        }

        return itemTotal;
    }

    /** Returns the total SHIPPABLE quantity in the cart for a specific ship group. */
    public BigDecimal getShippableQuantity(int idx) {
        CartShipInfo info = this.getShipInfo(idx);
        BigDecimal count = BigDecimal.ZERO;

        for (ShoppingCartItem item : info.shipItemInfo.keySet()) {
            CartShipInfo.CartShipItemInfo csii = info.shipItemInfo.get(item);
            if (csii != null && csii.quantity.compareTo(BigDecimal.ZERO) > 0) {
                if (item.shippingApplies()) {
                    count = count.add(csii.quantity);
                }
            }
        }

        return count;
    }

    /** Returns the total SHIPPABLE weight in the cart for a specific ship group. */
    public BigDecimal getShippableWeight(int idx) {
        CartShipInfo info = this.getShipInfo(idx);
        BigDecimal weight = BigDecimal.ZERO;

        for (ShoppingCartItem item : info.shipItemInfo.keySet()) {
            CartShipInfo.CartShipItemInfo csii = info.shipItemInfo.get(item);
            if (csii != null && csii.quantity.compareTo(BigDecimal.ZERO) > 0) {
                if (item.shippingApplies()) {
                    weight = weight.add(item.getWeight().multiply(csii.quantity));
                }
            }
        }

        return weight;
    }

    /** Returns a List of shippable item's size for a specific ship group. */
    public List<BigDecimal> getShippableSizes(int idx) {
        CartShipInfo info = this.getShipInfo(idx);
        List<BigDecimal> shippableSizes = new ArrayList<>();

        for (ShoppingCartItem item : info.shipItemInfo.keySet()) {
            CartShipInfo.CartShipItemInfo csii = info.shipItemInfo.get(item);
            if (csii != null && csii.quantity.compareTo(BigDecimal.ZERO) > 0) {
                if (item.shippingApplies()) {
                    shippableSizes.add(item.getSize());
                }
            }
        }

        return shippableSizes;
    }

    /** Returns a List of shippable item info (quantity, size, weight) for a specific ship group */
    public List<Map<String, Object>> getShippableItemInfo(int idx) {
        CartShipInfo info = this.getShipInfo(idx);
        List<Map<String, Object>> itemInfos = new ArrayList<>();

        for (ShoppingCartItem item : info.shipItemInfo.keySet()) {
            CartShipInfo.CartShipItemInfo csii = info.shipItemInfo.get(item);
            if (csii != null && csii.quantity.compareTo(BigDecimal.ZERO) > 0) {
                if (item.shippingApplies()) {
                    Map<String, Object> itemInfo = item.getItemProductInfo();
                    itemInfo.put("quantity", csii.quantity);
                    itemInfos.add(itemInfo);
                }
            }
        }

        return itemInfos;
    }

    /** Returns true when there are shippable items in the cart */
    public boolean shippingApplies() {
        boolean shippingApplies = false;
        for (ShoppingCartItem item : this) {
            if (item.shippingApplies()) {
                shippingApplies = true;
                break;
            }
        }
        return shippingApplies;
    }

    /** Returns true when there are taxable items in the cart */
    public boolean taxApplies() {
        boolean taxApplies = false;
        for (ShoppingCartItem item : this) {
            if (item.taxApplies()) {
                taxApplies = true;
                break;
            }
        }
        return taxApplies;
    }

    /** Returns a Map of all features applied to products in the cart with quantities for a specific ship group. */
    public Map<String, BigDecimal> getFeatureIdQtyMap(int idx) {
        CartShipInfo info = this.getShipInfo(idx);
        Map<String, BigDecimal> featureMap = new HashMap<>();

        for (ShoppingCartItem item : info.shipItemInfo.keySet()) {
            CartShipInfo.CartShipItemInfo csii = info.shipItemInfo.get(item);
            if (csii != null && csii.quantity.compareTo(BigDecimal.ZERO) > 0) {
                featureMap.putAll(item.getFeatureIdQtyMap(csii.quantity));
            }
        }

        return featureMap;
    }

    /** Returns true if the user wishes to view the cart everytime an item is added. */
    public boolean viewCartOnAdd() {
        return viewCartOnAdd;
    }

    /** Returns true if the user wishes to view the cart everytime an item is added. */
    public void setViewCartOnAdd(boolean viewCartOnAdd) {
        this.viewCartOnAdd = viewCartOnAdd;
    }

    /** Returns the order ID associated with this cart or null if no order has been created yet. */
    public String getOrderId() {
        return this.orderId;
    }

    /** Returns the first attempt order ID associated with this cart or null if no order has been created yet. */
    public String getFirstAttemptOrderId() {
        return this.firstAttemptOrderId;
    }

    /** Sets the orderId associated with this cart. */
    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public void setNextItemSeq(long seq) throws GeneralException {
        if (this.nextItemSeq != 1) {
            throw new GeneralException("Cannot set the item sequence once the sequence has been incremented!");
        }
        this.nextItemSeq = seq;
    }

    /** TODO: Sets the first attempt orderId for this cart. */
    public void setFirstAttemptOrderId(String orderId) {
        this.firstAttemptOrderId = orderId;
    }

    public void removeAllFreeShippingProductPromoActions() {
        this.freeShippingProductPromoActions.clear();
    }
    /** Removes a free shipping ProductPromoAction by trying to find one in the list with the same primary key. */
    public void removeFreeShippingProductPromoAction(GenericPK productPromoActionPK) {
        if (productPromoActionPK == null) {
            return;
        }

        Iterator<GenericValue> fsppas = this.freeShippingProductPromoActions.iterator();
        while (fsppas.hasNext()) {
            if (productPromoActionPK.equals((fsppas.next()).getPrimaryKey())) {
                fsppas.remove();
            }
        }
    }
    /** Adds a ProductPromoAction to be used for free shipping (must be of type free shipping, or nothing will be done). */
    public void addFreeShippingProductPromoAction(GenericValue productPromoAction) {
        if (productPromoAction == null) {
            return;
        }
        // is this a free shipping action?
        if (!"PROMO_FREE_SHIPPING".equals(productPromoAction.getString("productPromoActionEnumId"))) {
            return; // Changed 1-5-04 by Si Chen
        }

        // to easily make sure that no duplicate exists, do a remove first
        this.removeFreeShippingProductPromoAction(productPromoAction.getPrimaryKey());
        this.freeShippingProductPromoActions.add(productPromoAction);
    }
    public List<GenericValue> getFreeShippingProductPromoActions() {
        return this.freeShippingProductPromoActions;
    }

    public void removeAllDesiredAlternateGiftByActions() {
        this.desiredAlternateGiftByAction.clear();
    }
    public void setDesiredAlternateGiftByAction(GenericPK productPromoActionPK, String productId) {
        this.desiredAlternateGiftByAction.put(productPromoActionPK, productId);
    }
    public String getDesiredAlternateGiftByAction(GenericPK productPromoActionPK) {
        return this.desiredAlternateGiftByAction.get(productPromoActionPK);
    }
    public Map<GenericPK, String> getAllDesiredAlternateGiftByActionCopy() {
        return new HashMap<>(this.desiredAlternateGiftByAction);
    }

    public void addProductPromoUse(String productPromoId, String productPromoCodeId, BigDecimal totalDiscountAmount, BigDecimal quantityLeftInActions, Map<ShoppingCartItem,BigDecimal> usageInfoMap) {
        if (UtilValidate.isNotEmpty(productPromoCodeId) && !this.productPromoCodes.contains(productPromoCodeId)) {
            throw new IllegalStateException("Cannot add a use to a promo code use for a code that has not been entered.");
        }
        if (Debug.verboseOn()) {
            Debug.logVerbose("Used promotion [" + productPromoId + "] with code [" + productPromoCodeId + "] for total discount [" + totalDiscountAmount + "] and quantity left in actions [" + quantityLeftInActions + "]", module);
        }
        this.productPromoUseInfoList.add(new ProductPromoUseInfo(productPromoId, productPromoCodeId, totalDiscountAmount, quantityLeftInActions, usageInfoMap));
    }

    public void removeProductPromoUse(String productPromoId) {
        if (!productPromoId.isEmpty()) {
            int index = -1;
            for (ProductPromoUseInfo productPromoUseInfo : this.productPromoUseInfoList) {
                if (productPromoId.equals(productPromoUseInfo.productPromoId)) {
                    index = this.productPromoUseInfoList.indexOf(productPromoUseInfo);
                    break;
                }
            }
            if (index != -1) {
                this.productPromoUseInfoList.remove(index);
            }
        }
    }

    public void clearProductPromoUseInfo() {
        // clear out info for general promo use
        this.productPromoUseInfoList.clear();
    }

    public void clearCartItemUseInPromoInfo() {
        // clear out info about which cart items have been used in promos
        for (ShoppingCartItem cartLine : this) {
            cartLine.clearPromoRuleUseInfo();
        }
    }

    public Iterator<ProductPromoUseInfo> getProductPromoUseInfoIter() {
        return productPromoUseInfoList.iterator();
    }

    public BigDecimal getProductPromoTotal() {
        BigDecimal totalDiscount = BigDecimal.ZERO;
        List<GenericValue> cartAdjustments = this.getAdjustments();
        if (cartAdjustments != null) {
            for (GenericValue checkOrderAdjustment : cartAdjustments) {
                if (UtilValidate.isNotEmpty(checkOrderAdjustment.getString("productPromoId")) &&
                        UtilValidate.isNotEmpty(checkOrderAdjustment.getString("productPromoRuleId")) &&
                        UtilValidate.isNotEmpty(checkOrderAdjustment.getString("productPromoActionSeqId"))) {
                    if (checkOrderAdjustment.get("amount") != null) {
                        totalDiscount = totalDiscount.add(checkOrderAdjustment.getBigDecimal("amount"));
                    }
                }
            }
        }

        // add cart line adjustments from promo actions
        for (ShoppingCartItem checkItem : this) {
            Iterator<GenericValue> checkOrderAdjustments = UtilMisc.toIterator(checkItem.getAdjustments());
            while (checkOrderAdjustments != null && checkOrderAdjustments.hasNext()) {
                GenericValue checkOrderAdjustment = checkOrderAdjustments.next();
                if (UtilValidate.isNotEmpty(checkOrderAdjustment.getString("productPromoId")) &&
                        UtilValidate.isNotEmpty(checkOrderAdjustment.getString("productPromoRuleId")) &&
                        UtilValidate.isNotEmpty(checkOrderAdjustment.getString("productPromoActionSeqId"))) {
                    if (checkOrderAdjustment.get("amount") != null) {
                        totalDiscount = totalDiscount.add(checkOrderAdjustment.getBigDecimal("amount"));
                    }
                }
            }
        }

        return totalDiscount;
    }

    /** Get total discount for a given ProductPromo, or for ANY ProductPromo if the passed in productPromoId is null. */
    public BigDecimal getProductPromoUseTotalDiscount(String productPromoId) {
        BigDecimal totalDiscount = BigDecimal.ZERO;
        for (ProductPromoUseInfo productPromoUseInfo: this.productPromoUseInfoList) {
            if (productPromoId == null || productPromoId.equals(productPromoUseInfo.productPromoId)) {
                totalDiscount = totalDiscount.add(productPromoUseInfo.getTotalDiscountAmount());
            }
        }
        return totalDiscount;
    }

    public int getProductPromoUseCount(String productPromoId) {
        if (productPromoId == null) {
            return 0;
        }
        int useCount = 0;
        for (ProductPromoUseInfo productPromoUseInfo: this.productPromoUseInfoList) {
            if (productPromoId.equals(productPromoUseInfo.productPromoId)) {
                useCount++;
            }
        }
        return useCount;
    }

    public int getProductPromoCodeUse(String productPromoCodeId) {
        if (productPromoCodeId == null) {
            return 0;
        }
        int useCount = 0;
        for (ProductPromoUseInfo productPromoUseInfo: this.productPromoUseInfoList) {
            if (productPromoCodeId.equals(productPromoUseInfo.productPromoCodeId)) {
                useCount++;
            }
        }
        return useCount;
    }

    public void clearAllPromotionInformation() {
        this.clearAllPromotionAdjustments();

        // remove all free shipping promo actions
        this.removeAllFreeShippingProductPromoActions();

        // clear promo uses & reset promo code uses, and reset info about cart items used for promos (ie qualifiers and benefiters)
        this.clearProductPromoUseInfo();
        this.clearCartItemUseInPromoInfo();
    }

    public void clearAllPromotionAdjustments() {
        // remove cart adjustments from promo actions
        List<GenericValue> cartAdjustments = this.getAdjustments();
        if (cartAdjustments != null) {
            Iterator<GenericValue> cartAdjustmentIter = cartAdjustments.iterator();
            while (cartAdjustmentIter.hasNext()) {
                GenericValue checkOrderAdjustment = cartAdjustmentIter.next();
                if (UtilValidate.isNotEmpty(checkOrderAdjustment.getString("productPromoId")) &&
                        UtilValidate.isNotEmpty(checkOrderAdjustment.getString("productPromoRuleId")) &&
                        UtilValidate.isNotEmpty(checkOrderAdjustment.getString("productPromoActionSeqId"))) {
                    cartAdjustmentIter.remove();
                }
            }
        }

        // remove cart lines that are promos (ie GWPs) and cart line adjustments from promo actions
        Iterator<ShoppingCartItem> cartItemIter = this.iterator();
        while (cartItemIter.hasNext()) {
            ShoppingCartItem checkItem = cartItemIter.next();
            if (checkItem.getIsPromo()) {
                this.clearItemShipInfo(checkItem);
                cartItemIter.remove();
            } else {
                // found a promo item with the productId, see if it has a matching adjustment on it
                Iterator<GenericValue> checkOrderAdjustments = UtilMisc.toIterator(checkItem.getAdjustments());
                while (checkOrderAdjustments != null && checkOrderAdjustments.hasNext()) {
                    GenericValue checkOrderAdjustment = checkOrderAdjustments.next();
                    if (UtilValidate.isNotEmpty(checkOrderAdjustment.getString("productPromoId")) &&
                            UtilValidate.isNotEmpty(checkOrderAdjustment.getString("productPromoRuleId")) &&
                            UtilValidate.isNotEmpty(checkOrderAdjustment.getString("productPromoActionSeqId"))) {
                        checkOrderAdjustments.remove();
                    }
                }
            }
        }
    }

    public void clearAllAdjustments() {
        // remove all the promotion information (including adjustments)
        clearAllPromotionInformation();
        // remove all cart adjustments
        this.adjustments.clear();
        // remove all cart item adjustments
        for (ShoppingCartItem checkItem : this) {
            checkItem.getAdjustments().clear();
        }
    }

    public void clearAllItemStatus() {
        for (ShoppingCartItem item : this) {
            item.setStatusId(null);
        }
    }

    /** Adds a promotion code to the cart, checking if it is valid. If it is valid this will return null, otherwise it will return a message stating why it was not valid
     * @param productPromoCodeId The promotion code to check and add
     * @return String that is null if valid, and added to cart, or an error message of the code was not valid and not added to the cart.
     */
    public String addProductPromoCode(String productPromoCodeId, LocalDispatcher dispatcher) {
        if (this.productPromoCodes.contains(productPromoCodeId)) {
            return UtilProperties.getMessage(resource_error, "productpromoworker.promotion_code_already_been_entered", UtilMisc.toMap("productPromoCodeId", productPromoCodeId), locale);
        }
        if (!this.getDoPromotions()) {
            this.productPromoCodes.add(productPromoCodeId);
            return null;
        }
        // if the promo code requires it make sure the code is valid
        String checkResult = ProductPromoWorker.checkCanUsePromoCode(productPromoCodeId, this.getPartyId(), this.getDelegator(), this, locale);
        if (checkResult == null) {
            this.productPromoCodes.add(productPromoCodeId);
            // new promo code, re-evaluate promos
            ProductPromoWorker.doPromotions(this, dispatcher);
            return null;
        }
        return checkResult;
    }

    public Set<String> getProductPromoCodesEntered() {
        return this.productPromoCodes;
    }

    public synchronized void resetPromoRuleUse(String productPromoId, String productPromoRuleId) {
        for (ShoppingCartItem cartItem : this) {
            cartItem.resetPromoRuleUse(productPromoId, productPromoRuleId);
        }
    }

    public synchronized void confirmPromoRuleUse(String productPromoId, String productPromoRuleId) {
        for (ShoppingCartItem cartItem : this) {
            cartItem.confirmPromoRuleUse(productPromoId, productPromoRuleId);
        }
    }

    /**
     * Associates a party with a role to the order.
     * @param partyId identifier of the party to associate to order
     * @param roleTypeId identifier of the role used in party-order association
     */
    public void addAdditionalPartyRole(String partyId, String roleTypeId) {
        // search if there is an existing entry
        List<String> parties = additionalPartyRole.get(roleTypeId);
        if (parties != null) {
            for (String pi : parties) {
                if (pi.equals(partyId)) {
                    return;
                }
            }
        } else {
            parties = new ArrayList<>();
            additionalPartyRole.put(roleTypeId, parties);
        }

        parties.add(0, partyId);
    }

    /**
     * Removes a previously associated party to the order.
     * @param partyId identifier of the party to associate to order
     * @param roleTypeId identifier of the role used in party-order association
     */
    public void removeAdditionalPartyRole(String partyId, String roleTypeId) {
        List<String> parties = additionalPartyRole.get(roleTypeId);

        if (parties != null) {
            Iterator<String> it = parties.iterator();
            while (it.hasNext()) {
                if ((it.next()).equals(partyId)) {
                    it.remove();

                    if (parties.isEmpty()) {
                        additionalPartyRole.remove(roleTypeId);
                    }
                    return;
                }
            }
        }
    }

    public Map<String, List<String>> getAdditionalPartyRoleMap() {
        return additionalPartyRole;
    }

    // =======================================================================
    // Methods used for order creation
    // =======================================================================

    /**
     * Returns the Id of an AGGREGATED_CONF product having exact configId.
     * If AGGREGATED_CONF product do not exist, creates one, associates it to the AGGREGATED product, and copy its production run template.
     * @param item
     * @param dispatcher
     */
    public String getAggregatedInstanceId (ShoppingCartItem item, LocalDispatcher dispatcher) {
        if (UtilValidate.isEmpty(item.getConfigWrapper()) || UtilValidate.isEmpty(item.getConfigWrapper().getConfigId())) {
            return null;
        }
        String newProductId = null;
        String configId = item.getConfigWrapper().getConfigId();
        try {
            //first search for existing productId
            newProductId = ProductWorker.getAggregatedInstanceId(getDelegator(), item.getProductId(), configId);
            if (newProductId != null) {
                return newProductId;
            }

            Delegator delegator = this.getDelegator();

            //create new product and associate it
            GenericValue product = item.getProduct();
            String productName = product.getString("productName");
            String description = product.getString("description");
            Map<String, Object> serviceContext = new HashMap<>();
            GenericValue permUserLogin = EntityQuery.use(delegator).from("UserLogin").where("userLoginId", "system").queryOne();
            String internalName = item.getProductId() + "_" + configId;
            serviceContext.put("internalName", internalName);
            serviceContext.put("productName", productName);
            serviceContext.put("description", description);
            if(ProductWorker.isAggregateService(delegator, item.getProductId())) {
                serviceContext.put("productTypeId", "AGGREGATEDSERV_CONF");
            }
            else {
                serviceContext.put("productTypeId", "AGGREGATED_CONF");
            }

            serviceContext.put("configId", configId);
            if (UtilValidate.isNotEmpty(product.getString("requirementMethodEnumId"))) {
                serviceContext.put("requirementMethodEnumId", product.getString("requirementMethodEnumId"));
            }
            serviceContext.put("userLogin", permUserLogin);

            Map<String, Object> result = dispatcher.runSync("createProduct", serviceContext);
            if (ServiceUtil.isError(result)) {
                Debug.logError(ServiceUtil.getErrorMessage(result), module);
                return null;
            }

            serviceContext.clear();
            newProductId = (String) result.get("productId");
            serviceContext.put("productId", item.getProductId());
            serviceContext.put("productIdTo", newProductId);
            serviceContext.put("productAssocTypeId", "PRODUCT_CONF");
            serviceContext.put("fromDate", UtilDateTime.nowTimestamp());
            serviceContext.put("userLogin", permUserLogin);

            result = dispatcher.runSync("createProductAssoc", serviceContext);
            if (ServiceUtil.isError(result)) {
                Debug.logError(ServiceUtil.getErrorMessage(result), module);
                return null;
            }

            //create a new WorkEffortGoodStandard based on existing one of AGGREGATED product .
            //Another approach could be to get WorkEffortGoodStandard of the AGGREGATED product while creating production run.
            GenericValue productionRunTemplate = EntityQuery.use(delegator).from("WorkEffortGoodStandard").where("productId", item.getProductId(), "workEffortGoodStdTypeId", "ROU_PROD_TEMPLATE", "statusId", "WEGS_CREATED").filterByDate().queryFirst();
            if (productionRunTemplate != null) {
                serviceContext.clear();
                serviceContext.put("workEffortId", productionRunTemplate.getString("workEffortId"));
                serviceContext.put("productId", newProductId);
                serviceContext.put("workEffortGoodStdTypeId", "ROU_PROD_TEMPLATE");
                serviceContext.put("statusId", "WEGS_CREATED");
                serviceContext.put("userLogin", permUserLogin);

                result = dispatcher.runSync("createWorkEffortGoodStandard", serviceContext);
                if (ServiceUtil.isError(result)) {
                    Debug.logError(ServiceUtil.getErrorMessage(result), module);
                    return null;
                }
            }

        } catch (GenericEntityException | GenericServiceException e) {
            Debug.logError(e, module);
            return null;
        } catch (Exception e) { // SCIPIO: 2018-10-09: Keep Exception here just in case
            Debug.logError(e, module);
            return null;
        }

        return newProductId;
    }

    public List<GenericValue> makeOrderItemGroups() {
        List<GenericValue> result = new ArrayList<>();
        for (ShoppingCart.ShoppingCartItemGroup itemGroup : this.itemGroupByNumberMap.values()) {
            result.add(itemGroup.makeOrderItemGroup(this.getDelegator()));
        }
        return result;
    }

    private void explodeItems(LocalDispatcher dispatcher) {
        if (dispatcher == null) {
            return;
        }
        synchronized (cartLines) {
            List<ShoppingCartItem> cartLineItems = new ArrayList<>(cartLines);
            for (ShoppingCartItem item : cartLineItems) {
                try {
                    int thisIndex = items().indexOf(item);
                    List<ShoppingCartItem> explodedItems = item.explodeItem(this, dispatcher);

                    // Add exploded items into cart with order item sequence id and item ship group quantity
                    for (ShoppingCartItem explodedItem : explodedItems) {
                        String orderItemSeqId = UtilFormatOut.formatPaddedNumber(nextItemSeq, 5);
                        explodedItem.setOrderItemSeqId(orderItemSeqId);
                        addItemToEnd(explodedItem);
                        setItemShipGroupQty(explodedItem, BigDecimal.ONE, thisIndex);
                        nextItemSeq++;
                    }
                } catch (CartItemModifyException e) {
                    Debug.logError(e, "Problem exploding item! Item not exploded.", module);
                }
            }
        }
    }

    /**
     * Does an "explode", or "unitize" operation on a list of cart items.
     * Resulting state for each item with quantity X is X items of quantity 1.
     *
     * @param shoppingCartItems
     * @param dispatcher
     */
    public void explodeItems(List<ShoppingCartItem> shoppingCartItems, LocalDispatcher dispatcher) {
        if (dispatcher == null) {
            return;
        }
        synchronized (cartLines) {
            for (ShoppingCartItem item : shoppingCartItems) {
                try {
                    int thisIndex = items().indexOf(item);
                    List<ShoppingCartItem> explodedItems = item.explodeItem(this, dispatcher);

                    // Add exploded items into cart with order item sequence id and item ship group quantity
                    for (ShoppingCartItem explodedItem : explodedItems) {
                        String orderItemSeqId = UtilFormatOut.formatPaddedNumber(nextItemSeq, 5);
                        explodedItem.setOrderItemSeqId(orderItemSeqId);
                        addItemToEnd(explodedItem);
                        setItemShipGroupQty(explodedItem, BigDecimal.ONE, thisIndex);
                        nextItemSeq++;
                    }
                } catch (CartItemModifyException e) {
                    Debug.logError(e, "Problem exploding (unitizing) item! Item not exploded.", module);
                }
            }
        }
    }

    public List<GenericValue> makeOrderItems() {
        return makeOrderItems(false, false, null);
    }

    public List<GenericValue> makeOrderItems(LocalDispatcher dispatcher) {
        return makeOrderItems(false, false, dispatcher);
    }

    public List<GenericValue> makeOrderItems(boolean explodeItems, boolean replaceAggregatedId, LocalDispatcher dispatcher) {
        // do the explosion
        if (explodeItems && dispatcher != null) {
            explodeItems(dispatcher);
        }

        // now build the lines
        synchronized (cartLines) {
            List<GenericValue> result = new ArrayList<>();

            for (ShoppingCartItem item : cartLines) {
                if (UtilValidate.isEmpty(item.getOrderItemSeqId())) {
                    String orderItemSeqId = UtilFormatOut.formatPaddedNumber(nextItemSeq, 5);
                    item.setOrderItemSeqId(orderItemSeqId);
                    nextItemSeq++;
                } else {
                    try {
                        int thisSeqId = Integer.parseInt(item.getOrderItemSeqId());
                        if (thisSeqId > nextItemSeq) {
                            nextItemSeq = thisSeqId + 1;
                        }
                    } catch (NumberFormatException e) {
                        Debug.logError(e, module);
                    }
                }

                // the initial status for all item types
                String initialStatus = "ITEM_CREATED";
                String status = item.getStatusId();
                if (status == null) {
                    status = initialStatus;
                }
                //check for aggregated products
                String aggregatedInstanceId = null;
                if (replaceAggregatedId && UtilValidate.isNotEmpty(item.getConfigWrapper())) {
                    aggregatedInstanceId = getAggregatedInstanceId(item, dispatcher);
                }

                GenericValue orderItem = getDelegator().makeValue("OrderItem");
                orderItem.set("orderItemSeqId", item.getOrderItemSeqId());
                orderItem.set("externalId", item.getExternalId());
                orderItem.set("orderItemTypeId", item.getItemType());
                if (item.getItemGroup() != null) {
                    orderItem.set("orderItemGroupSeqId", item.getItemGroup().getGroupNumber());
                }
                orderItem.set("productId", UtilValidate.isNotEmpty(aggregatedInstanceId) ? aggregatedInstanceId : item.getProductId());
                orderItem.set("supplierProductId", item.getSupplierProductId());
                orderItem.set("prodCatalogId", item.getProdCatalogId());
                orderItem.set("productCategoryId", item.getProductCategoryId());
                orderItem.set("quantity", item.getQuantity());
                orderItem.set("selectedAmount", item.getSelectedAmount());
                orderItem.set("unitPrice", item.getBasePrice());
                orderItem.set("unitListPrice", item.getListPrice());
                orderItem.set("isModifiedPrice",item.getIsModifiedPrice() ? "Y" : "N");
                orderItem.set("isPromo", item.getIsPromo() ? "Y" : "N");

                orderItem.set("shoppingListId", item.getShoppingListId());
                orderItem.set("shoppingListItemSeqId", item.getShoppingListItemSeqId());

                orderItem.set("itemDescription", item.getName());
                orderItem.set("comments", item.getItemComment());
                orderItem.set("estimatedDeliveryDate", item.getDesiredDeliveryDate());
                orderItem.set("correspondingPoId", this.getPoNumber());
                orderItem.set("quoteId", item.getQuoteId());
                orderItem.set("quoteItemSeqId", item.getQuoteItemSeqId());
                orderItem.set("statusId", status);

                orderItem.set("shipBeforeDate", item.getShipBeforeDate());
                orderItem.set("shipAfterDate", item.getShipAfterDate());
                orderItem.set("estimatedShipDate", item.getEstimatedShipDate());
                orderItem.set("cancelBackOrderDate", item.getCancelBackOrderDate());
                if (this.getUserLogin() != null) {
                    orderItem.set("changeByUserLoginId", this.getUserLogin().get("userLoginId"));
                }

                String fromInventoryItemId = (String) item.getAttribute("fromInventoryItemId");
                if (fromInventoryItemId != null) {
                    orderItem.set("fromInventoryItemId", fromInventoryItemId);
                }

                result.add(orderItem);
                // don't do anything with adjustments here, those will be added below in makeAllAdjustments
            }
            return result;
        }
    }

    /**
     * SCIPIO: Returns the ProductConfigWrapper for each order item.
     * WARNING: This ONLY works if the order items have already been expanded, using {@link #makeOrderItems} or other,
     * otherwise there will be no orderItemSeqIds to use!
     */
    public Map<String, ProductConfigWrapper> getProductConfigWrappersByOrderItemSeqId() {
        Map<String, ProductConfigWrapper> result = new HashMap<>();
        for (ShoppingCartItem item : cartLines) {
            if (item.getOrderItemSeqId() != null && item.getConfigWrapper() != null) {
                result.put(item.getOrderItemSeqId(), item.getConfigWrapper());
            }
        }
        return result;
    }

    /** create WorkEfforts from the shoppingcart items when itemType = RENTAL_ORDER_ITEM */
    public List<GenericValue> makeWorkEfforts() {
        List<GenericValue> allWorkEfforts = new ArrayList<>();
        for (ShoppingCartItem item : cartLines) {
            if ("RENTAL_ORDER_ITEM".equals(item.getItemType())) {         // prepare workeffort when the order item is a rental item
                GenericValue workEffort = getDelegator().makeValue("WorkEffort");
                workEffort.set("workEffortId",item.getOrderItemSeqId());  // fill temporary with sequence number
                workEffort.set("estimatedStartDate",item.getReservStart());
                workEffort.set("estimatedCompletionDate",item.getReservStart(item.getReservLength()));
                workEffort.set("reservPersons", item.getReservPersons());
                workEffort.set("reserv2ndPPPerc", item.getReserv2ndPPPerc());
                workEffort.set("reservNthPPPerc", item.getReservNthPPPerc());
                workEffort.set("accommodationMapId", item.getAccommodationMapId());
                workEffort.set("accommodationSpotId",item.getAccommodationSpotId());
                allWorkEfforts.add(workEffort);
            }
        }
        return allWorkEfforts;
    }

    /** make a list of all adjustments including order adjustments, order line adjustments, and special adjustments (shipping and tax if applicable) */
    public List<GenericValue> makeAllAdjustments() {
        List<GenericValue> allAdjs = new ArrayList<>();

        // before returning adjustments, go through them to find all that need counter adjustments (for instance: free shipping)
        for (GenericValue orderAdjustment: this.getAdjustments()) {

            allAdjs.add(orderAdjustment);

            if ("SHIPPING_CHARGES".equals(orderAdjustment.get("orderAdjustmentTypeId"))) {
                Iterator<GenericValue> fsppas = this.freeShippingProductPromoActions.iterator();

                while (fsppas.hasNext()) {
                    // TODO - we need to change the way free shipping promotions work
                }
            }
        }

        // add all of the item adjustments to this list too
        for (ShoppingCartItem item : cartLines) {
            Collection<GenericValue> adjs = item.getAdjustments();

            if (adjs != null) {
                for (GenericValue orderAdjustment: adjs) {

                    orderAdjustment.set("orderItemSeqId", item.getOrderItemSeqId());
                    allAdjs.add(orderAdjustment);

                    if ("SHIPPING_CHARGES".equals(orderAdjustment.get("orderAdjustmentTypeId"))) {
                        Iterator<GenericValue> fsppas = this.freeShippingProductPromoActions.iterator();

                        while (fsppas.hasNext()) {
                            // TODO - fix the free shipping promotions!!
                        }
                    }
                }
            }
        }

        return allAdjs;
    }

    /** make a list of all quote adjustments including header adjustments, line adjustments, and special adjustments (shipping and tax if applicable).
     *  Internally, the quote adjustments are created from the order adjustments.
     */
    public List<GenericValue> makeAllQuoteAdjustments() {
        List<GenericValue> quoteAdjs = new ArrayList<>();

        for (GenericValue orderAdj: makeAllAdjustments()) {
            GenericValue quoteAdj = this.getDelegator().makeValue("QuoteAdjustment");
            quoteAdj.put("quoteAdjustmentId", orderAdj.get("orderAdjustmentId"));
            quoteAdj.put("quoteAdjustmentTypeId", orderAdj.get("orderAdjustmentTypeId"));
            quoteAdj.put("quoteItemSeqId", orderAdj.get("orderItemSeqId"));
            quoteAdj.put("comments", orderAdj.get("comments"));
            quoteAdj.put("description", orderAdj.get("description"));
            quoteAdj.put("amount", orderAdj.get("amount"));
            quoteAdj.put("productPromoId", orderAdj.get("productPromoId"));
            quoteAdj.put("productPromoRuleId", orderAdj.get("productPromoRuleId"));
            quoteAdj.put("productPromoActionSeqId", orderAdj.get("productPromoActionSeqId"));
            quoteAdj.put("productFeatureId", orderAdj.get("productFeatureId"));
            quoteAdj.put("correspondingProductId", orderAdj.get("correspondingProductId"));
            quoteAdj.put("sourceReferenceId", orderAdj.get("sourceReferenceId"));
            quoteAdj.put("sourcePercentage", orderAdj.get("sourcePercentage"));
            quoteAdj.put("customerReferenceId", orderAdj.get("customerReferenceId"));
            quoteAdj.put("primaryGeoId", orderAdj.get("primaryGeoId"));
            quoteAdj.put("secondaryGeoId", orderAdj.get("secondaryGeoId"));
            quoteAdj.put("exemptAmount", orderAdj.get("exemptAmount"));
            quoteAdj.put("taxAuthGeoId", orderAdj.get("taxAuthGeoId"));
            quoteAdj.put("taxAuthPartyId", orderAdj.get("taxAuthPartyId"));
            quoteAdj.put("overrideGlAccountId", orderAdj.get("overrideGlAccountId"));
            quoteAdj.put("includeInTax", orderAdj.get("includeInTax"));
            quoteAdj.put("includeInShipping", orderAdj.get("includeInShipping"));
            quoteAdj.put("createdDate", orderAdj.get("createdDate"));
            quoteAdj.put("createdByUserLogin", orderAdj.get("createdByUserLogin"));
            quoteAdjs.add(quoteAdj);
        }

        return quoteAdjs;
    }

    /** make a list of all OrderPaymentPreferences and Billing info including all payment methods and types */
    public List<GenericValue> makeAllOrderPaymentInfos(LocalDispatcher dispatcher) {
        Delegator delegator = this.getDelegator();
        List<GenericValue> allOpPrefs = new ArrayList<>();
        BigDecimal remainingAmount = this.getGrandTotal().subtract(this.getPaymentTotal());
        remainingAmount = remainingAmount.setScale(2, RoundingMode.HALF_UP);
        if (getBillingAccountId() != null && this.billingAccountAmt.compareTo(BigDecimal.ZERO) <= 0) {
            BigDecimal billingAccountAvailableAmount = CheckOutHelper.availableAccountBalance(getBillingAccountId(), dispatcher);
            if (this.billingAccountAmt.compareTo(BigDecimal.ZERO) == 0 && billingAccountAvailableAmount.compareTo(BigDecimal.ZERO) > 0) {
                this.billingAccountAmt = billingAccountAvailableAmount;
            }
            if (remainingAmount.compareTo(getBillingAccountAmount()) < 0) {
                this.billingAccountAmt = remainingAmount;
            }
            if (billingAccountAvailableAmount.compareTo(getBillingAccountAmount()) < 0) {
                this.billingAccountAmt = billingAccountAvailableAmount;
            }
        }
        for (CartPaymentInfo inf : paymentInfo) {
            if (inf.amount == null) {
                inf.amount = remainingAmount;
                remainingAmount = BigDecimal.ZERO;
            }
            allOpPrefs.addAll(inf.makeOrderPaymentInfos(delegator, this));
        }
        return allOpPrefs;
    }

    /** make a list of OrderItemPriceInfos from the ShoppingCartItems */
    public List<GenericValue> makeAllOrderItemPriceInfos() {
        List<GenericValue> allInfos = new ArrayList<>();

        // add all of the item adjustments to this list too
        for (ShoppingCartItem item : cartLines) {
            Collection<GenericValue> infos = item.getOrderItemPriceInfos();

            if (infos != null) {
                for (GenericValue orderItemPriceInfo : infos) {
                    orderItemPriceInfo.set("orderItemSeqId", item.getOrderItemSeqId());
                    allInfos.add(orderItemPriceInfo);
                }
            }
        }

        return allInfos;
    }

    public List<GenericValue> makeProductPromoUses() {
        List<GenericValue> productPromoUses = new ArrayList<>();
        String partyId = this.getPartyId();
        int sequenceValue = 0;
        for (ProductPromoUseInfo productPromoUseInfo: this.productPromoUseInfoList) {
            GenericValue productPromoUse = this.getDelegator().makeValue("ProductPromoUse");
            productPromoUse.set("promoSequenceId", UtilFormatOut.formatPaddedNumber(sequenceValue, 5));
            productPromoUse.set("productPromoId", productPromoUseInfo.getProductPromoId());
            productPromoUse.set("productPromoCodeId", productPromoUseInfo.getProductPromoCodeId());
            productPromoUse.set("totalDiscountAmount", productPromoUseInfo.getTotalDiscountAmount());
            productPromoUse.set("quantityLeftInActions", productPromoUseInfo.getQuantityLeftInActions());
            productPromoUse.set("partyId", partyId);
            productPromoUses.add(productPromoUse);
            sequenceValue++;
        }
        return productPromoUses;
    }

    /** make a list of SurveyResponse object to update with order information set */
    public List<GenericValue> makeAllOrderItemSurveyResponses() {
        List<GenericValue> allInfos = new ArrayList<>();
        for (ShoppingCartItem item : this) {
            List<String> responses = UtilGenerics.checkList(item.getAttribute("surveyResponses"));
            GenericValue response = null;
            if (responses != null) {
                for (String responseId : responses) {
                    try {
                        response = this.getDelegator().findOne("SurveyResponse", UtilMisc.toMap("surveyResponseId", responseId), false);
                    } catch (GenericEntityException e) {
                        Debug.logError(e, "Unable to obtain SurveyResponse record for ID : " + responseId, module);
                    }
                }
             // this case is executed when user selects "Create as new Order" for Gift cards
             } else {
                 String surveyResponseId = (String) item.getAttribute("surveyResponseId");
                 try {
                     response = this.getDelegator().findOne("SurveyResponse", UtilMisc.toMap("surveyResponseId", surveyResponseId), false);
                 } catch (GenericEntityException e) {
                     Debug.logError(e, "Unable to obtain SurveyResponse record for ID : " + surveyResponseId, module);
                 }
            }
            if (response != null) {
                response.set("orderItemSeqId", item.getOrderItemSeqId());
                allInfos.add(response);
            }
        }
        return allInfos;
    }

    /** SCIPIO: make a map of orderItemSeqId to SurveyResponse */
    public Map<String, List<GenericValue>> makeAllOrderItemSurveyResponsesByOrderItemSeqId() {
        Map<String, List<GenericValue>> allInfos = new HashMap<>();
        for (ShoppingCartItem item : this) {
            List<GenericValue> surveyResponses = item.getSurveyResponses();
            // TODO: REVIEW: Is it safe or a good idea to set orderId and/or orderItemSeqId here?
            // It might be used as an "order not placed yet" flag somewhere....
            //response.set("orderItemSeqId", item.getOrderItemSeqId());
            if (UtilValidate.isNotEmpty(surveyResponses)) {
                allInfos.put(item.getOrderItemSeqId(), surveyResponses);
            }
        }
        return allInfos;
    }

    /** make a list of OrderContactMechs from the ShoppingCart and the ShoppingCartItems */
    public List<GenericValue> makeAllOrderContactMechs() {
        List<GenericValue> allOrderContactMechs = new ArrayList<>();

        Map<String, String> contactMechIds = this.getOrderContactMechIds();

        if (contactMechIds != null) {
            for (Map.Entry<String, String> entry : contactMechIds.entrySet()) {
                GenericValue orderContactMech = getDelegator().makeValue("OrderContactMech");
                orderContactMech.set("contactMechPurposeTypeId", entry.getKey());
                orderContactMech.set("contactMechId", entry.getValue());
                allOrderContactMechs.add(orderContactMech);
            }
        }

        return allOrderContactMechs;
    }

    /** make a list of OrderContactMechs from the ShoppingCart and the ShoppingCartItems */
    public List<GenericValue> makeAllOrderItemContactMechs() {
        List<GenericValue> allOrderContactMechs = new ArrayList<>();

        for (ShoppingCartItem item : cartLines) {
            Map<String, String> itemContactMechIds = item.getOrderItemContactMechIds();

            if (itemContactMechIds != null) {
                for (Map.Entry<String, String> entry: itemContactMechIds.entrySet()) {
                    GenericValue orderContactMech = getDelegator().makeValue("OrderItemContactMech");

                    orderContactMech.set("contactMechPurposeTypeId", entry.getKey());
                    orderContactMech.set("contactMechId", entry.getValue());
                    orderContactMech.set("orderItemSeqId", item.getOrderItemSeqId());
                    allOrderContactMechs.add(orderContactMech);
                }
            }
        }

        return allOrderContactMechs;
    }

    /**
     * Return all OrderItemShipGroup, OrderContactMech, OrderAdjustment and OrderItemShipGroupAssoc from ShoppingCart
     * in a single list of {@link GenericValue}
     */
    public List<GenericValue> makeAllShipGroupInfos() {
        List<GenericValue> groups = new ArrayList<>();
        long seqId = 1;
        for (CartShipInfo csi : this.shipInfo) {
            String shipGroupSeqId = csi.shipGroupSeqId;
            if (shipGroupSeqId != null) {
                groups.addAll(csi.makeItemShipGroupAndAssoc(this.getDelegator(), this, shipGroupSeqId));
            } else {
                groups.addAll(csi.makeItemShipGroupAndAssoc(this.getDelegator(), this, UtilFormatOut.formatPaddedNumber(seqId, 5), true));
            }
            seqId++;
        }
        return groups;
    }

    public int getShipInfoSize() {
        return this.shipInfo.size();
    }

    public Map<String, List<GenericValue>> makeAllOrderItemAttributesByOrderItemSeqId() { // SCIPIO
        return UtilMisc.groupMapsByKey(makeAllOrderItemAttributes(), "orderItemSeqId");
    }

    public List<GenericValue> makeAllOrderItemAttributes() {
        return makeAllOrderItemAttributes(null, ALL);
    }

    public List<GenericValue> makeAllOrderItemAttributes(String orderId, int mode) {

        // now build order item attributes
        synchronized (cartLines) {
            List<GenericValue> result = new ArrayList<>();

            for (ShoppingCartItem item : cartLines) {
                Map<String, String> orderItemAttributes = item.getOrderItemAttributes();
                for (Entry<String, String> entry : orderItemAttributes.entrySet()) {
                    String value = entry.getValue();
                    String key = entry.getKey();

                    if (ALL == mode || (FILLED_ONLY == mode && UtilValidate.isNotEmpty(value)) || (EMPTY_ONLY == mode && UtilValidate.isEmpty(value))
                            || (mode != ALL && mode != FILLED_ONLY && mode != EMPTY_ONLY)) {

                        GenericValue orderItemAttribute = getDelegator().makeValue("OrderItemAttribute");
                        if (UtilValidate.isNotEmpty(orderId)) {
                            orderItemAttribute.set("orderId", orderId);
                        }
                        orderItemAttribute.set("orderItemSeqId", item.getOrderItemSeqId());
                        orderItemAttribute.set("attrName", key);
                        orderItemAttribute.set("attrValue", value);

                        result.add(orderItemAttribute);
                    }
                }
            }
            return result;
        }
    }

    public List<GenericValue> makeAllOrderAttributes() {

        return makeAllOrderAttributes(null, ALL);
    }

    public List<GenericValue> makeAllOrderAttributes(String orderId, int mode) {

        List<GenericValue> allOrderAttributes = new ArrayList<>();

        for (Map.Entry<String, String> entry: orderAttributes.entrySet()) {
            GenericValue orderAtt = this.getDelegator().makeValue("OrderAttribute");
            if (UtilValidate.isNotEmpty(orderId)) {
                orderAtt.set("orderId", orderId);
            }
            String key = entry.getKey();
            String value = entry.getValue();

            orderAtt.put("attrName", key);
            orderAtt.put("attrValue", value);

            switch (mode) {
            case FILLED_ONLY:
                if (UtilValidate.isNotEmpty(value)) {
                    allOrderAttributes.add(orderAtt);
                }
                break;
            case EMPTY_ONLY:
                if (UtilValidate.isEmpty(value)) {
                    allOrderAttributes.add(orderAtt);
                }
                break;
            case ALL:
            default:
                allOrderAttributes.add(orderAtt);
                break;
            }

        }
        return allOrderAttributes;
    }

    public List<GenericValue> makeAllOrderItemAssociations() {
        List<GenericValue> allOrderItemAssociations = new ArrayList<>();

        for (CartShipInfo csi : shipInfo) {
            Set<ShoppingCartItem> items = csi.getShipItems();
            for (ShoppingCartItem item : items) {
                String requirementId = item.getRequirementId();
                if (requirementId != null) {
                    try {
                        // TODO: multiple commitments for the same requirement are still not supported
                        GenericValue commitment = EntityQuery.use(getDelegator())
                                                         .from("OrderRequirementCommitment")
                                                         .where("requirementId", requirementId)
                                                         .queryFirst();
                        if (commitment != null) {
                            GenericValue orderItemAssociation = getDelegator().makeValue("OrderItemAssoc");
                            orderItemAssociation.set("orderId", commitment.getString("orderId"));
                            orderItemAssociation.set("orderItemSeqId", commitment.getString("orderItemSeqId"));
                            orderItemAssociation.set("shipGroupSeqId", "_NA_");
                            orderItemAssociation.set("toOrderItemSeqId", item.getOrderItemSeqId());
                            orderItemAssociation.set("toShipGroupSeqId", "_NA_");
                            orderItemAssociation.set("orderItemAssocTypeId", "PURCHASE_ORDER");
                            allOrderItemAssociations.add(orderItemAssociation);
                        }
                    } catch (GenericEntityException e) {
                        Debug.logError(e, "Unable to load OrderRequirementCommitment records for requirement ID : " + requirementId, module);
                    }
                }
                if (item.getAssociatedOrderId() != null && item.getAssociatedOrderItemSeqId() != null) {
                    GenericValue orderItemAssociation = getDelegator().makeValue("OrderItemAssoc");
                    orderItemAssociation.set("orderId", item.getAssociatedOrderId());
                    orderItemAssociation.set("orderItemSeqId", item.getAssociatedOrderItemSeqId());
                    orderItemAssociation.set("shipGroupSeqId", csi.getAssociatedShipGroupSeqId() != null ? csi.getAssociatedShipGroupSeqId() : "_NA_");
                    orderItemAssociation.set("toOrderItemSeqId", item.getOrderItemSeqId());
                    orderItemAssociation.set("toShipGroupSeqId", csi.getShipGroupSeqId() != null ? csi.getShipGroupSeqId() : "_NA_");
                    orderItemAssociation.set("orderItemAssocTypeId", item.getOrderItemAssocTypeId());
                    allOrderItemAssociations.add(orderItemAssociation);
                }
            }
        }
        return allOrderItemAssociations;
    }

    /** Returns a Map of cart values to pass to the storeOrder service */
    public Map<String, Object> makeCartMap(LocalDispatcher dispatcher, boolean explodeItems) {
        Map<String, Object> result = new HashMap<>();

        result.put("orderTypeId", this.getOrderType());
        result.put("orderName", this.getOrderName());
        result.put("externalId", this.getExternalId());
        result.put("orderDate", this.getOrderDate());
        result.put("internalCode", this.getInternalCode());
        result.put("salesChannelEnumId", this.getChannelType());
        result.put("orderItemGroups", this.makeOrderItemGroups());
        result.put("orderItems", this.makeOrderItems(explodeItems, Boolean.TRUE, dispatcher));
        result.put("workEfforts", this.makeWorkEfforts());
        result.put("orderAdjustments", this.makeAllAdjustments());
        result.put("orderTerms", this.getOrderTerms());
        result.put("orderItemPriceInfos", this.makeAllOrderItemPriceInfos());
        result.put("orderProductPromoUses", this.makeProductPromoUses());
        result.put("orderProductPromoCodes", this.getProductPromoCodesEntered());

        result.put("orderAttributes", this.makeAllOrderAttributes());
        result.put("orderItemAttributes", this.makeAllOrderItemAttributes());
        result.put("orderContactMechs", this.makeAllOrderContactMechs());
        result.put("orderItemContactMechs", this.makeAllOrderItemContactMechs());
        result.put("orderPaymentInfo", this.makeAllOrderPaymentInfos(dispatcher));
        result.put("orderItemShipGroupInfo", this.makeAllShipGroupInfos());
        result.put("orderItemSurveyResponses", this.makeAllOrderItemSurveyResponses());
        result.put("orderAdditionalPartyRoleMap", this.getAdditionalPartyRoleMap());
        result.put("orderItemAssociations", this.makeAllOrderItemAssociations());
        result.put("orderInternalNotes", this.getInternalOrderNotes());
        result.put("orderNotes", this.getOrderNotes());

        result.put("firstAttemptOrderId", this.getFirstAttemptOrderId());
        result.put("currencyUom", this.getCurrency());
        result.put("billingAccountId", this.getBillingAccountId());

        result.put("partyId", this.getPartyId());
        result.put("productStoreId", this.getProductStoreId());
        result.put("transactionId", this.getTransactionId());
        result.put("originFacilityId", this.getFacilityId());
        result.put("terminalId", this.getTerminalId());
        result.put("workEffortId", this.getWorkEffortId());
        result.put("autoOrderShoppingListId", this.getAutoOrderShoppingListId());

        result.put("billToCustomerPartyId", this.getBillToCustomerPartyId());
        result.put("billFromVendorPartyId", this.getBillFromVendorPartyId());

        if (this.isSalesOrder()) {
            result.put("placingCustomerPartyId", this.getPlacingCustomerPartyId());
            result.put("shipToCustomerPartyId", this.getShipToCustomerPartyId());
            result.put("endUserCustomerPartyId", this.getEndUserCustomerPartyId());
        }

        if (this.isPurchaseOrder()) {
            result.put("shipFromVendorPartyId", this.getShipFromVendorPartyId());
            result.put("supplierAgentPartyId", this.getSupplierAgentPartyId());
        }

        return result;
    }

    public List<ShoppingCartItem> getLineListOrderedByBasePrice(boolean ascending) {
        List<ShoppingCartItem> result = new ArrayList<>(this.cartLines);
        Collections.sort(result, new BasePriceOrderComparator(ascending));
        return result;
    }

    public TreeMap<Integer, CartShipInfo> getShipGroupsBySupplier(String supplierPartyId) {
        TreeMap<Integer, CartShipInfo> shipGroups = new TreeMap<>();
        for (int i = 0; i < this.shipInfo.size(); i++) {
            CartShipInfo csi = shipInfo.get(i);
            if ((csi.supplierPartyId == null && supplierPartyId == null) ||
                (UtilValidate.isNotEmpty(csi.supplierPartyId) && csi.supplierPartyId.equals(supplierPartyId))) {
                    shipGroups.put(i, csi);
            }
        }
        return shipGroups;
    }

    /**
     * Examine each item of each ship group and create new ship groups if the item should be drop shipped
     * @param dispatcher
     * @throws CartItemModifyException
     */
    public void createDropShipGroups(LocalDispatcher dispatcher) throws CartItemModifyException {

        // Retrieve the facilityId from the cart's productStoreId because ShoppingCart.setFacilityId() doesn't seem to be used anywhere
        String facilityId = null;
        if (UtilValidate.isNotEmpty(this.getProductStoreId())) {
            try {
                GenericValue productStore = this.getDelegator().findOne("ProductStore", UtilMisc.toMap("productStoreId", this.getProductStoreId()), true);
                facilityId = productStore.getString("inventoryFacilityId");
            } catch (GenericEntityException gee) {
                Debug.logError(UtilProperties.getMessage(resource_error,"OrderProblemGettingProductStoreRecords", Debug.getLogLocale()) + gee.getMessage(), module); // SCIPIO: log locale
                return;
            } catch (Exception e) {
                Debug.logError(UtilProperties.getMessage(resource_error,"OrderProblemGettingProductStoreRecords", Debug.getLogLocale()) + e.getMessage(), module); // SCIPIO: log locale
                return;
            }
        }

        List<CartShipInfo> shipGroups = getShipGroups();
        if (shipGroups == null) {
            return;
        }

        // Intermediate structure supplierPartyId -> { ShoppingCartItem = { originalShipGroupIndex = dropShipQuantity } } to collect drop-shippable items
        Map<String, Map<ShoppingCartItem, Map<Integer, BigDecimal>>> dropShipItems = new HashMap<>();

        for (int shipGroupIndex = 0; shipGroupIndex < shipGroups.size(); shipGroupIndex++) {

            CartShipInfo shipInfo = shipGroups.get(shipGroupIndex);

            // Ignore ship groups that are already drop shipped
            String shipGroupSupplierPartyId = shipInfo.getSupplierPartyId();
            if (UtilValidate.isNotEmpty(shipGroupSupplierPartyId)) {
                continue;
            }

            // Ignore empty ship groups
            Set<ShoppingCartItem> shipItems = shipInfo.getShipItems();
            if (UtilValidate.isEmpty(shipItems)) {
                continue;
            }

            for (ShoppingCartItem cartItem : shipItems) {
                BigDecimal itemQuantity = cartItem.getQuantity();
                BigDecimal dropShipQuantity = BigDecimal.ZERO;

                GenericValue product = cartItem.getProduct();
                if (product == null) {
                    continue;
                }
                String productId = product.getString("productId");
                String requirementMethodEnumId = product.getString("requirementMethodEnumId");

                if ("PRODRQM_DS".equals(requirementMethodEnumId)) {

                    // Drop ship the full quantity if the product is marked drop-ship only
                    dropShipQuantity = itemQuantity;

                } else if ("PRODRQM_DSATP".equals(requirementMethodEnumId)) {

                    // Drop ship the quantity not available in inventory if the product is marked drop-ship on low inventory
                    try {

                        // Get ATP for the product
                        Map<String, Object> getProductInventoryAvailableResult = dispatcher.runSync("getInventoryAvailableByFacility", UtilMisc.toMap("productId", productId, "facilityId", facilityId));
                        if (ServiceUtil.isError(getProductInventoryAvailableResult)) {
                            String errorMessage = ServiceUtil.getErrorMessage(getProductInventoryAvailableResult);
                            Debug.logError(errorMessage, module);
                            return;
                        }
                        BigDecimal availableToPromise = (BigDecimal) getProductInventoryAvailableResult.get("availableToPromiseTotal");

                        if (itemQuantity.compareTo(availableToPromise) <= 0) {
                            dropShipQuantity = BigDecimal.ZERO;
                        } else {
                            dropShipQuantity = itemQuantity.subtract(availableToPromise);
                        }

                    } catch (GenericServiceException gee) {
                        Debug.logWarning(UtilProperties.getMessage(resource_error,"OrderRunServiceGetInventoryAvailableByFacilityError", Debug.getLogLocale()) + gee.getMessage(), module); // SCIPIO: log locale
                    } catch (Exception e) {
                        Debug.logWarning(UtilProperties.getMessage(resource_error,"OrderRunServiceGetInventoryAvailableByFacilityError", Debug.getLogLocale()) + e.getMessage(), module); // SCIPIO: log locale
                    }
                } else {

                    // Don't drop ship anything if the product isn't so marked
                    dropShipQuantity = BigDecimal.ZERO;
                }

                if (dropShipQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                // Find a supplier for the product
                String supplierPartyId = null;
                try {
                    Map<String, Object> getSuppliersForProductResult = dispatcher.runSync("getSuppliersForProduct", UtilMisc.<String, Object>toMap("productId", productId, "quantity", dropShipQuantity, "canDropShip", "Y", "currencyUomId", getCurrency()));
                    if (ServiceUtil.isError(getSuppliersForProductResult)) {
                        String errorMessage = ServiceUtil.getErrorMessage(getSuppliersForProductResult);
                        Debug.logError(errorMessage, module);
                        return;
                    }
                    List<GenericValue> supplierProducts = UtilGenerics.checkList(getSuppliersForProductResult.get("supplierProducts"));

                    // Order suppliers by supplierPrefOrderId so that preferred suppliers are used first
                    supplierProducts = EntityUtil.orderBy(supplierProducts, UtilMisc.toList("supplierPrefOrderId"));
                    GenericValue supplierProduct = EntityUtil.getFirst(supplierProducts);
                    if (! UtilValidate.isEmpty(supplierProduct)) {
                        supplierPartyId = supplierProduct.getString("partyId");
                    }
                } catch (GenericServiceException e) {
                    Debug.logWarning(UtilProperties.getMessage(resource_error,"OrderRunServiceGetSuppliersForProductError", Debug.getLogLocale()) + e.getMessage(), module); // SCIPIO: log locale
                }

                // Leave the items untouched if we couldn't find a supplier
                if (UtilValidate.isEmpty(supplierPartyId)) {
                    continue;
                }

                if (! dropShipItems.containsKey(supplierPartyId)) {
                    dropShipItems.put(supplierPartyId, new HashMap<ShoppingCartItem, Map<Integer, BigDecimal>>());
                }
                Map<ShoppingCartItem, Map<Integer, BigDecimal>> supplierCartItems = UtilGenerics.checkMap(dropShipItems.get(supplierPartyId));

                if (! supplierCartItems.containsKey(cartItem)) {
                    supplierCartItems.put(cartItem, new HashMap<Integer, BigDecimal>());
                }
                Map<Integer, BigDecimal> cartItemGroupQuantities = UtilGenerics.checkMap(supplierCartItems.get(cartItem));

                cartItemGroupQuantities.put(shipGroupIndex, dropShipQuantity);
            }
        }

        // Reassign the drop-shippable item quantities to new or existing drop-ship groups
        for (Entry<String, Map<ShoppingCartItem, Map<Integer, BigDecimal>>> supplierPartyEntry : dropShipItems.entrySet()) {
            String supplierPartyId = supplierPartyEntry.getKey();
            CartShipInfo shipInfo = null;
            int newShipGroupIndex = -1 ;

            // Attempt to get the first ship group for the supplierPartyId
            TreeMap<Integer, CartShipInfo> supplierShipGroups = this.getShipGroupsBySupplier(supplierPartyId);
            if (! UtilValidate.isEmpty(supplierShipGroups)) {
                newShipGroupIndex = supplierShipGroups.firstKey();
                shipInfo = supplierShipGroups.get(supplierShipGroups.firstKey());
            }
            if (newShipGroupIndex == -1) {
                newShipGroupIndex = addShipInfo();
                shipInfo = this.shipInfo.get(newShipGroupIndex);
            }
            shipInfo.supplierPartyId = supplierPartyId;

            Map<ShoppingCartItem, Map<Integer, BigDecimal>> supplierCartItems = UtilGenerics.checkMap(supplierPartyEntry.getValue());
            for (Entry<ShoppingCartItem, Map<Integer, BigDecimal>> cartItemEntry : supplierCartItems.entrySet()) {
                ShoppingCartItem cartItem = cartItemEntry.getKey();
                Map<Integer, BigDecimal> cartItemGroupQuantities = UtilGenerics.checkMap(cartItemEntry.getValue());
                for (Entry<Integer, BigDecimal> previousShipGroupIndexEntry : cartItemGroupQuantities.entrySet()) {
                    Integer previousShipGroupIndex = previousShipGroupIndexEntry.getKey();
                    BigDecimal dropShipQuantity = previousShipGroupIndexEntry.getValue();
                    positionItemToGroup(cartItem, dropShipQuantity, previousShipGroupIndex, newShipGroupIndex, true);
                }
            }
        }
    }

    static class BasePriceOrderComparator implements Comparator<Object>, Serializable {
        private boolean ascending = false;

        BasePriceOrderComparator(boolean ascending) {
            this.ascending = ascending;
        }

        @Override
        public int compare(java.lang.Object obj, java.lang.Object obj1) {
            ShoppingCartItem cartItem = (ShoppingCartItem) obj;
            ShoppingCartItem cartItem1 = (ShoppingCartItem) obj1;

            return this.ascending
                    ? cartItem.getBasePrice().compareTo(cartItem1.getBasePrice())
                    : cartItem1.getBasePrice().compareTo(cartItem.getBasePrice());
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + (ascending ? 1231 : 1237);
            return result;
        }

        @Override
        public boolean equals(java.lang.Object obj) {
            if (obj instanceof BasePriceOrderComparator) {
                return this.ascending == ((BasePriceOrderComparator) obj).ascending;
            }
            return false;
        }
    }

    public static class ShoppingCartItemGroup implements Serializable {
        private String groupNumber;
        private String groupName;
        private ShoppingCartItemGroup parentGroup;

        // don't allow empty constructor
        @SuppressWarnings("unused")
        private ShoppingCartItemGroup() {}

        protected ShoppingCartItemGroup(long groupNumber, String groupName) {
            this(groupNumber, groupName, null);
        }

        /** Note that to avoid foreign key issues when the groups are created a parentGroup should have a lower number than the child group. */
        protected ShoppingCartItemGroup(long groupNumber, String groupName, ShoppingCartItemGroup parentGroup) {
            this(UtilFormatOut.formatPaddedNumber(groupNumber, 2), groupName, parentGroup);
        }

        protected ShoppingCartItemGroup(String groupNumber, String groupName, ShoppingCartItemGroup parentGroup) {
            this.groupNumber = groupNumber;
            this.groupName = groupName;
            this.parentGroup = parentGroup;
        }

        protected ShoppingCartItemGroup(ShoppingCartItemGroup itemGroup, ShoppingCartItemGroup parentGroup) {
            this.groupNumber = itemGroup.groupNumber;
            this.groupName = itemGroup.groupName;
            this.parentGroup = parentGroup;
        }

        /**
         * SCIPIO: Tests to ensure the cart is an exact copy of the other; used to verify {@link #exactCopy}.
         * NOTE: This is NOT the same as a logical Object equals override! This is mainly for testing.
         */
        void ensureExactEquals(ShoppingCartItemGroup other) {
            try {
                ShoppingCart.ensureExactEquals(this.groupNumber, other.groupNumber);
                ShoppingCart.ensureExactEquals(this.groupName, other.groupName);
                ShoppingCart.ensureExactEquals(this.parentGroup, other.parentGroup);
            } catch(IllegalStateException e) {
                throw new IllegalStateException("ShoppingCartItemGroup field not equal: " + e.getMessage(), e);
            }
        }

        public String getGroupNumber() {
            return this.groupNumber;
        }

        public String getGroupName() {
            return this.groupName;
        }

        public void setGroupName(String str) {
            this.groupName = str;
        }

        public ShoppingCartItemGroup getParentGroup () {
            return this.parentGroup;
        }

        protected GenericValue makeOrderItemGroup(Delegator delegator) {
            GenericValue orderItemGroup = delegator.makeValue("OrderItemGroup");
            orderItemGroup.set("orderItemGroupSeqId", this.getGroupNumber());
            orderItemGroup.set("groupName", this.getGroupName());
            if (this.parentGroup != null) {
                orderItemGroup.set("parentGroupSeqId", this.parentGroup.getGroupNumber());
            }
            return orderItemGroup;
        }

        public void inheritParentsParent() {
            if (this.parentGroup != null) {
                this.parentGroup = this.parentGroup.getParentGroup();
            }
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            // SCIPIO: 2018-10-09: TODO: REVIEW: not part of equals, so can't be here - should be in equals?
            //result = prime * result + ((groupName == null) ? 0 : groupName.hashCode());
            result = prime * result + ((groupNumber == null) ? 0 : groupNumber.hashCode());
            // SCIPIO: 2018-10-09: TODO: REVIEW: not part of equals, so can't be here - should be in equals?
            //result = prime * result + ((parentGroup == null) ? 0 : parentGroup.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof ShoppingCartItemGroup) {
                ShoppingCartItemGroup that = (ShoppingCartItemGroup) obj;
                if (that.groupNumber.equals(this.groupNumber)) {
                    return true;
                }
            }
            return false;
        }
    }

    public static class ProductPromoUseInfo implements Serializable, Comparable<ProductPromoUseInfo> {
        // SCIPIO: 2018-11-12: All fields now final and not public
        protected final String productPromoId;
        protected final String productPromoCodeId;
        protected final BigDecimal totalDiscountAmount; // = BigDecimal.ZERO
        protected final BigDecimal quantityLeftInActions; // = BigDecimal.ZERO
        private final Map<ShoppingCartItem, BigDecimal> usageInfoMap;

        public ProductPromoUseInfo(String productPromoId, String productPromoCodeId, BigDecimal totalDiscountAmount, BigDecimal quantityLeftInActions, Map<ShoppingCartItem,BigDecimal> usageInfoMap) {
            this.productPromoId = productPromoId;
            this.productPromoCodeId = productPromoCodeId;
            this.totalDiscountAmount = totalDiscountAmount;
            this.quantityLeftInActions = quantityLeftInActions;
            this.usageInfoMap = (usageInfoMap != null) ? Collections.unmodifiableMap(usageInfoMap) : null; // SCIPIO: unmodifiableMap
        }

        /**
         * SCIPIO: Copy constructor.
         */
        public ProductPromoUseInfo(ProductPromoUseInfo other, boolean exactCopy, Map<ShoppingCartItem, ShoppingCartItem> oldToNewItemMap) {
            this.productPromoId = other.productPromoId;
            this.productPromoCodeId = other.productPromoCodeId;
            this.totalDiscountAmount = other.totalDiscountAmount;
            this.quantityLeftInActions = other.quantityLeftInActions;
            Map<ShoppingCartItem, BigDecimal> usageInfoMap = null;
            if (other.usageInfoMap != null) {
                usageInfoMap = new HashMap<>();
                for(Map.Entry<ShoppingCartItem, BigDecimal> entry : other.usageInfoMap.entrySet()) {
                    ShoppingCartItem newItem = oldToNewItemMap.get(entry.getKey());
                    if (newItem == null) {
                        Debug.logError("ShoppingCartItem " + entry.getKey() + " was not cloned properly", module);
                        continue;
                    }
                    usageInfoMap.put(newItem, entry.getValue());
                }
                usageInfoMap = Collections.unmodifiableMap(usageInfoMap);
            }
            this.usageInfoMap = usageInfoMap;
        }

        /**
         * SCIPIO: Tests to ensure the cart is an exact copy of the other; used to verify {@link #exactCopy}.
         * NOTE: This is NOT the same as a logical Object equals override! This is mainly for testing.
         */
        void ensureExactEquals(ProductPromoUseInfo other) {
            try {
                ShoppingCart.ensureExactEquals(this.productPromoId, other.productPromoId);
                ShoppingCart.ensureExactEquals(this.productPromoCodeId, other.productPromoCodeId);
                ShoppingCart.ensureExactEquals(this.totalDiscountAmount, other.totalDiscountAmount);
                ShoppingCart.ensureExactEquals(this.quantityLeftInActions, other.quantityLeftInActions);
                ShoppingCart.ensureExactEquals(this.usageInfoMap, other.usageInfoMap);
            } catch(IllegalStateException e) {
                throw new IllegalStateException("ProductPromoUseInfo field not equal: " + e.getMessage(), e);
            }
        }

        public String getProductPromoId() { return this.productPromoId; }
        public String getProductPromoCodeId() { return this.productPromoCodeId; }
        public BigDecimal getTotalDiscountAmount() { return this.totalDiscountAmount; }
        public BigDecimal getQuantityLeftInActions() { return this.quantityLeftInActions; }
        public Map<ShoppingCartItem,BigDecimal> getUsageInfoMap() { return this.usageInfoMap; }
        public BigDecimal getUsageWeight() {
            Iterator<ShoppingCartItem> lineItems = this.usageInfoMap.keySet().iterator();
            BigDecimal totalAmount = BigDecimal.ZERO;
            while (lineItems.hasNext()) {
                ShoppingCartItem lineItem = lineItems.next();
                totalAmount = totalAmount.add(lineItem.getBasePrice().multiply(usageInfoMap.get(lineItem)));
            }
            if (totalAmount.compareTo(BigDecimal.ZERO) == 0) {
                return BigDecimal.ZERO;
            }
            return getTotalDiscountAmount().negate().divide(totalAmount, scale, rounding);
        }

        @Override
        public int compareTo(ProductPromoUseInfo other) {
            return other.getUsageWeight().compareTo(getUsageWeight());
        }
    }

    public static class CartShipInfo implements Serializable {
        // SCIPIO: 2018-11-22: Defaults moved into constructor
        public Map<ShoppingCartItem, CartShipItemInfo> shipItemInfo;
        public List<GenericValue> shipTaxAdj;
        public String orderTypeId;
        private String internalContactMechId;
        public String telecomContactMechId;
        public String shipmentMethodTypeId;
        public String supplierPartyId;
        public String carrierRoleTypeId;
        public String carrierPartyId;
        private String facilityId;
        public String giftMessage;
        public String shippingInstructions;
        public String maySplit;
        public String isGift;
        public BigDecimal shipEstimate; // = BigDecimal.ZERO
        public Timestamp shipBeforeDate;
        public Timestamp shipAfterDate;
        private String shipGroupSeqId;
        private String associatedShipGroupSeqId;
        public String vendorPartyId;
        public String productStoreShipMethId;
        public Map<String, Object> attributes;

        /**
         * SCIPIO: Default constructor.
         */
        public CartShipInfo() {
            this.shipItemInfo = new HashMap<>();
            this.shipTaxAdj = new ArrayList<>();
            this.orderTypeId = null;
            this.internalContactMechId = null;
            this.telecomContactMechId = null;
            this.shipmentMethodTypeId = null;
            this.supplierPartyId = null;
            this.carrierRoleTypeId = null;
            this.carrierPartyId = null;
            this.facilityId = null;
            this.giftMessage = null;
            this.shippingInstructions = null;
            this.maySplit = "N";
            this.isGift = "N";
            this.shipEstimate = BigDecimal.ZERO;
            this.shipBeforeDate = null;
            this.shipAfterDate = null;
            this.shipGroupSeqId = null;
            this.associatedShipGroupSeqId = null;
            this.vendorPartyId = null;
            this.productStoreShipMethId = null;
            this.attributes = new HashMap<>();
        }

        /**
         * SCIPIO: Copy constructor.
         */
        public CartShipInfo(CartShipInfo other, boolean exactCopy, Map<ShoppingCartItem, ShoppingCartItem> oldToNewItemMap) {
            Map<ShoppingCartItem, CartShipItemInfo> shipItemInfo = new HashMap<>();
            for(Map.Entry<ShoppingCartItem, CartShipItemInfo> entry : other.shipItemInfo.entrySet()) {
                ShoppingCartItem newItem = oldToNewItemMap.get(entry.getKey());
                if (newItem == null) {
                    Debug.logError("ShoppingCartItem " + entry.getKey() + " was not cloned properly", module);
                    continue;
                }
                if (entry.getValue() == null) {
                    shipItemInfo.put(newItem, null);
                } else {
                    shipItemInfo.put(newItem, new CartShipItemInfo(entry.getValue(), exactCopy, newItem));
                }
            }
            this.shipItemInfo = shipItemInfo;
            this.shipTaxAdj = new ArrayList<>(other.shipTaxAdj);
            this.orderTypeId = other.orderTypeId;
            this.internalContactMechId = other.internalContactMechId;
            this.telecomContactMechId = other.telecomContactMechId;
            this.shipmentMethodTypeId = other.shipmentMethodTypeId;
            this.supplierPartyId = other.supplierPartyId;
            this.carrierRoleTypeId = other.carrierRoleTypeId;
            this.carrierPartyId = other.carrierPartyId;
            this.facilityId = other.facilityId;
            this.giftMessage = other.giftMessage;
            this.shippingInstructions = other.shippingInstructions;
            this.maySplit = other.maySplit;
            this.isGift = other.isGift;
            this.shipEstimate = other.shipEstimate;
            this.shipBeforeDate = other.shipBeforeDate;
            this.shipAfterDate = other.shipAfterDate;
            this.shipGroupSeqId = other.shipGroupSeqId;
            this.associatedShipGroupSeqId = other.associatedShipGroupSeqId;
            this.vendorPartyId = other.vendorPartyId;
            this.productStoreShipMethId = other.productStoreShipMethId;
            this.attributes = other.attributes;
        }

        /**
         * SCIPIO: Tests to ensure the cart is an exact copy of the other; used to verify {@link #exactCopy}.
         * NOTE: This is NOT the same as a logical Object equals override! This is mainly for testing.
         */
        void ensureExactEquals(CartShipInfo other) {
            try {
                ShoppingCart.ensureExactEquals(this.shipItemInfo, other.shipItemInfo);
                ShoppingCart.ensureExactEquals(this.shipTaxAdj, other.shipTaxAdj);
                ShoppingCart.ensureExactEquals(this.orderTypeId, other.orderTypeId);
                ShoppingCart.ensureExactEquals(this.internalContactMechId, other.internalContactMechId);
                ShoppingCart.ensureExactEquals(this.telecomContactMechId, other.telecomContactMechId);
                ShoppingCart.ensureExactEquals(this.shipmentMethodTypeId, other.shipmentMethodTypeId);
                ShoppingCart.ensureExactEquals(this.supplierPartyId, other.supplierPartyId);
                ShoppingCart.ensureExactEquals(this.carrierRoleTypeId, other.carrierRoleTypeId);
                ShoppingCart.ensureExactEquals(this.carrierPartyId, other.carrierPartyId);
                ShoppingCart.ensureExactEquals(this.facilityId, other.facilityId);
                ShoppingCart.ensureExactEquals(this.giftMessage, other.giftMessage);
                ShoppingCart.ensureExactEquals(this.shippingInstructions, other.shippingInstructions);
                ShoppingCart.ensureExactEquals(this.maySplit, other.maySplit);
                ShoppingCart.ensureExactEquals(this.isGift, other.isGift);
                ShoppingCart.ensureExactEquals(this.shipEstimate, other.shipEstimate);
                ShoppingCart.ensureExactEquals(this.shipBeforeDate, other.shipBeforeDate);
                ShoppingCart.ensureExactEquals(this.shipAfterDate, other.shipAfterDate);
                ShoppingCart.ensureExactEquals(this.shipGroupSeqId, other.shipGroupSeqId);
                ShoppingCart.ensureExactEquals(this.associatedShipGroupSeqId, other.associatedShipGroupSeqId);
                ShoppingCart.ensureExactEquals(this.vendorPartyId, other.vendorPartyId);
                ShoppingCart.ensureExactEquals(this.productStoreShipMethId, other.productStoreShipMethId);
                ShoppingCart.ensureExactEquals(this.attributes, other.attributes);
            } catch(IllegalStateException e) {
                throw new IllegalStateException("CartShipInfo field not equal: " + e.getMessage(), e);
            }
        }
        
        public void setAttribute(String name, Object value) {
            this.attributes.put(name, value);
        }

        public void removeAttribute(String name) {
            this.attributes.remove(name);
        }

        @SuppressWarnings("unchecked")
        public <T> T getAttribute(String name) {
            return (T) this.attributes.get(name);
        }

        public String getOrderTypeId() { return orderTypeId; }

        public String getContactMechId() { return internalContactMechId; }
        public void setContactMechId(String contactMechId) {
            this.internalContactMechId = contactMechId;
        }

        public String getCarrierPartyId() { return carrierPartyId; }
        public String getSupplierPartyId() { return supplierPartyId; }
        public String getShipmentMethodTypeId() { return shipmentMethodTypeId; }
        public BigDecimal getShipEstimate() { return shipEstimate; }

        public String getShipGroupSeqId() { return shipGroupSeqId; }
        public void setShipGroupSeqId(String shipGroupSeqId) {
            this.shipGroupSeqId = shipGroupSeqId;
        }

        public String getAssociatedShipGroupSeqId() { return associatedShipGroupSeqId; }
        public void setAssociatedShipGroupSeqId(String shipGroupSeqId) {
            this.associatedShipGroupSeqId = shipGroupSeqId;
        }

        public String getFacilityId() { return facilityId; }
        public void setFacilityId(String facilityId) {
            this.facilityId = facilityId;
        }

        public String getVendorPartyId() { return vendorPartyId;}
        public void setVendorPartyId(String vendorPartyId) {
            this.vendorPartyId = vendorPartyId;
        }

        public void setMaySplit(Boolean maySplit) {
            if (UtilValidate.isNotEmpty(maySplit)) {
                this.maySplit = maySplit ? "Y" : "N";
            }
        }

        public void clearAllTaxInfo() {
            this.shipTaxAdj.clear();
            for (CartShipItemInfo itemInfo : shipItemInfo.values()) {
                itemInfo.itemTaxAdj.clear();
            }
        }

        public List<GenericValue> makeItemShipGroupAndAssoc(Delegator delegator, ShoppingCart cart, String shipGroupSeqId) {
            return makeItemShipGroupAndAssoc(delegator, cart, shipGroupSeqId, false);
        }

        public List<GenericValue> makeItemShipGroupAndAssoc(Delegator delegator, ShoppingCart cart, String shipGroupSeqId, boolean newShipGroup) {
            List<GenericValue> values = new ArrayList<>();

            // create order contact mech for shipping address
            if (this.internalContactMechId != null) {
                GenericValue orderCm = delegator.makeValue("OrderContactMech");
                orderCm.set("contactMechPurposeTypeId", "SHIPPING_LOCATION");
                orderCm.set("contactMechId", this.internalContactMechId);
                values.add(orderCm);
            }

            // create the ship group
            GenericValue shipGroup = delegator.makeValue("OrderItemShipGroup");
            shipGroup.set("shipmentMethodTypeId", shipmentMethodTypeId);
            shipGroup.set("carrierRoleTypeId", carrierRoleTypeId);
            shipGroup.set("carrierPartyId", carrierPartyId);
            shipGroup.set("supplierPartyId", supplierPartyId);
            shipGroup.set("shippingInstructions", shippingInstructions);
            shipGroup.set("giftMessage", giftMessage);
            shipGroup.set("contactMechId", this.internalContactMechId);
            shipGroup.set("telecomContactMechId", this.telecomContactMechId);
            shipGroup.set("maySplit", maySplit);
            shipGroup.set("isGift", isGift);
            shipGroup.set("shipGroupSeqId", shipGroupSeqId);
            shipGroup.set("vendorPartyId", vendorPartyId);
            shipGroup.set("facilityId", facilityId);

            // use the cart's default ship before and after dates here
            if ((shipBeforeDate == null) && (cart.getDefaultShipBeforeDate() != null)) {
                shipGroup.set("shipByDate", cart.getDefaultShipBeforeDate());
            } else {
                shipGroup.set("shipByDate", shipBeforeDate);
            }
            if ((shipAfterDate == null) && (cart.getDefaultShipAfterDate() != null)) {
                shipGroup.set("shipAfterDate", cart.getDefaultShipAfterDate());
            } else {
                shipGroup.set("shipAfterDate", shipAfterDate);
            }

            values.add(shipGroup);

            //set estimated ship dates
            List<Timestamp> estimatedShipDates = new ArrayList<>();
            for (ShoppingCartItem item : shipItemInfo.keySet()) {
                Timestamp estimatedShipDate = item.getEstimatedShipDate();
                if (estimatedShipDate != null) {
                    estimatedShipDates.add(estimatedShipDate);
                }
            }
            if (estimatedShipDates.size() > 0) {
                Collections.sort(estimatedShipDates);
                Timestamp estimatedShipDate  = estimatedShipDates.get(estimatedShipDates.size() - 1);
                shipGroup.set("estimatedShipDate", estimatedShipDate);
            }

            //set estimated delivery dates
            List<Timestamp> estimatedDeliveryDates = new ArrayList<>();
            for (ShoppingCartItem item : shipItemInfo.keySet()) {
                Timestamp estimatedDeliveryDate = item.getDesiredDeliveryDate();
                if (estimatedDeliveryDate != null) {
                    estimatedDeliveryDates.add(estimatedDeliveryDate);
                }
            }
            if (UtilValidate.isNotEmpty(estimatedDeliveryDates)) {
                Collections.sort(estimatedDeliveryDates);
                Timestamp estimatedDeliveryDate = estimatedDeliveryDates.get(estimatedDeliveryDates.size() - 1);
                shipGroup.set("estimatedDeliveryDate", estimatedDeliveryDate);
            }

            // create the shipping estimate adjustments
            if (shipEstimate.compareTo(BigDecimal.ZERO) != 0) {
                GenericValue shipAdj = delegator.makeValue("OrderAdjustment");
                shipAdj.set("orderAdjustmentTypeId", "SHIPPING_CHARGES");
                shipAdj.set("amount", shipEstimate);
                shipAdj.set("shipGroupSeqId", shipGroupSeqId);
                values.add(shipAdj);
            }

            // create the top level tax adjustments
            for (GenericValue taxAdj : shipTaxAdj) {
                taxAdj.set("shipGroupSeqId", shipGroupSeqId);
                values.add(taxAdj);
            }

            // create the ship group item associations
            for (Entry<ShoppingCartItem, CartShipItemInfo> entry : shipItemInfo.entrySet()) {
                ShoppingCartItem item = entry.getKey();
                CartShipItemInfo itemInfo = entry.getValue();

                GenericValue assoc = delegator.makeValue("OrderItemShipGroupAssoc");
                assoc.set("orderItemSeqId", item.getOrderItemSeqId());
                assoc.set("shipGroupSeqId", shipGroupSeqId);
                assoc.set("quantity", itemInfo.quantity);
                values.add(assoc);

                // create the item tax adjustment
                for (GenericValue taxAdj : itemInfo.itemTaxAdj) {
                    taxAdj.set("orderItemSeqId", item.getOrderItemSeqId());
                    taxAdj.set("shipGroupSeqId", shipGroupSeqId);
                    values.add(taxAdj);
                }
            }

            return values;
        }

        public CartShipItemInfo setItemInfo(ShoppingCartItem item, BigDecimal quantity, List<GenericValue> taxAdj) {
            CartShipItemInfo itemInfo = shipItemInfo.get(item);
            if (itemInfo == null) {
                if (!isShippableToAddress(item)) {
                    throw new IllegalArgumentException("The shipping address is not compatible with ProductGeos rules.");
                }
                itemInfo = new CartShipItemInfo();
                itemInfo.item = item;
                shipItemInfo.put(item, itemInfo);
            }
            if (quantity.compareTo(BigDecimal.ZERO) >= 0) {
                itemInfo.quantity = quantity;
            }
            if (taxAdj != null) {
                itemInfo.itemTaxAdj.clear();
                itemInfo.itemTaxAdj.addAll(taxAdj);
            }
            return itemInfo;
        }

        public CartShipItemInfo setItemInfo(ShoppingCartItem item, List<GenericValue> taxAdj) {
            return setItemInfo(item, BigDecimal.ONE.negate(), taxAdj);
        }

        public CartShipItemInfo setItemInfo(ShoppingCartItem item, BigDecimal quantity) {
            return setItemInfo(item, quantity, null);
        }

        public CartShipItemInfo getShipItemInfo(ShoppingCartItem item) {
            return shipItemInfo.get(item);
        }

        public Set<ShoppingCartItem> getShipItems() {
            return shipItemInfo.keySet();
        }

        private boolean isShippableToAddress(ShoppingCartItem item) {
            if ("SALES_ORDER".equals(getOrderTypeId())) {
                // Verify if the new address is compatible with the ProductGeos rules of
                // the products already in the cart
                GenericValue shippingAddress = null;
                try {
                    shippingAddress = item.getDelegator().findOne("PostalAddress", UtilMisc.toMap("contactMechId", this.internalContactMechId), false);
                } catch (GenericEntityException gee) {
                    Debug.logError(gee, "Error retrieving the shipping address for contactMechId [" + this.internalContactMechId + "].", module);
                }
                if (shippingAddress != null) {
                    GenericValue product = item.getProduct();
                    if (product != null) {
                        return ProductWorker.isShippableToAddress(product, shippingAddress);
                    }
                }
            }
            return true;
        }

        /**
         * Reset the ship group's shipBeforeDate if it is after the parameter
         * @param newShipBeforeDate the ship group's shipBeforeDate to be reset
         */
        public void resetShipBeforeDateIfAfter(Timestamp newShipBeforeDate) {
                if (newShipBeforeDate != null) {
                if ((this.shipBeforeDate == null) || (!this.shipBeforeDate.before(newShipBeforeDate))) {
                    this.shipBeforeDate = newShipBeforeDate;
                }
            }
        }

        /**
         * Reset the ship group's shipAfterDate if it is before the parameter
         * @param newShipAfterDate the ship group's shipAfterDate to be reset
         */
        public void resetShipAfterDateIfBefore(Timestamp newShipAfterDate) {
            if (newShipAfterDate != null) {
                if ((this.shipAfterDate == null) || (!this.shipAfterDate.after(newShipAfterDate))) {
                    this.shipAfterDate = newShipAfterDate;
                }
            }
        }

        public BigDecimal getTotalTax(ShoppingCart cart) {
            List<GenericValue> taxAdjustments = new ArrayList<>();
            taxAdjustments.addAll(shipTaxAdj);
            for (CartShipItemInfo info : shipItemInfo.values()) {
                taxAdjustments.addAll(info.itemTaxAdj);
            }
            Map<String, Object> taxByAuthority = OrderReadHelper.getOrderTaxByTaxAuthGeoAndParty(taxAdjustments);
            BigDecimal taxTotal = (BigDecimal) taxByAuthority.get("taxGrandTotal");
            return taxTotal;
        }

        /** SCIPIO: VAT Tax calculation. */
        public BigDecimal getTotalVATTax(ShoppingCart cart){
            List<GenericValue> taxAdjustments = new ArrayList<GenericValue>();
            taxAdjustments.addAll(shipTaxAdj);
            for (CartShipItemInfo info : shipItemInfo.values()) {
                taxAdjustments.addAll(info.itemTaxAdj);
            }
            Map<String, Object> taxByAuthority = OrderReadHelper.getOrderVATTaxByTaxAuthGeoAndParty(taxAdjustments);
            BigDecimal taxTotal = (BigDecimal) taxByAuthority.get("taxGrandTotal");
            return taxTotal;

        }

        public BigDecimal getTotal() {
            BigDecimal shipItemTotal = BigDecimal.ZERO;
            for (CartShipItemInfo info : shipItemInfo.values()) {
                shipItemTotal = shipItemTotal.add(info.getItemSubTotal());
            }

            return shipItemTotal;
        }

        public static class CartShipItemInfo implements Serializable {
            // SCIPIO: 2018-11-23: Defaults moved to constructor
            public List<GenericValue> itemTaxAdj;
            public ShoppingCartItem item;
            public BigDecimal quantity;

            /**
             * SCIPIO: Default constructor.
             */
            public CartShipItemInfo() {
                this.itemTaxAdj = new ArrayList<>();
                this.item = null;
                this.quantity = BigDecimal.ZERO;
            }

            /**
             * SCIPIO: Copy constructor.
             */
            public CartShipItemInfo(CartShipItemInfo other, boolean exactCopy, ShoppingCartItem parentItem) {
                this.itemTaxAdj = new ArrayList<>(other.itemTaxAdj);
                this.item = (parentItem != null) ? parentItem : other.item;
                this.quantity = other.quantity;
            }

            /**
             * SCIPIO: Tests to ensure the cart is an exact copy of the other; used to verify {@link #exactCopy}.
             * NOTE: This is NOT the same as a logical Object equals override! This is mainly for testing.
             */
            void ensureExactEquals(CartShipItemInfo other) {
                try {
                    ShoppingCart.ensureExactEquals(this.itemTaxAdj, other.itemTaxAdj);
                    ShoppingCart.ensureExactEquals(this.item, other.item);
                    ShoppingCart.ensureExactEquals(this.quantity, other.quantity);
                } catch(IllegalStateException e) {
                    throw new IllegalStateException("CartShipItemInfo field not equal: " + e.getMessage(), e);
                }
            }

            public BigDecimal getItemTax(ShoppingCart cart) {
                BigDecimal itemTax = BigDecimal.ZERO;

                for (int i = 0; i < itemTaxAdj.size(); i++) {
                    GenericValue v = itemTaxAdj.get(i);
                    itemTax = itemTax.add(OrderReadHelper.calcItemAdjustment(v, quantity, item.getBasePrice()));
                }

                return itemTax.setScale(taxCalcScale, taxRounding);
            }

            public ShoppingCartItem getItem() {
                return this.item;
            }

            public BigDecimal getItemQuantity() {
                return this.quantity;
            }

            public BigDecimal getItemSubTotal() {
                return item.getItemSubTotal(quantity);
            }
        }
    }

    public static class CartPaymentInfo implements Serializable, Comparable<Object> {
        public String paymentMethodTypeId;
        public String paymentMethodId;
        public String finAccountId;
        public String securityCode;
        public String postalCode;
        public String[] refNum;
        public String track2;
        public String partyId; // SCIPIO: 2018-07-19: billing address default: partyId
        public BigDecimal amount;
        public boolean singleUse; // = false
        public boolean isPresent; // = false
        public boolean isSwiped; // = false
        public boolean overflow; // = false
        public BigDecimal origAmount; // SCIPIO: original amount as specified upon creation. should not change.

        /**
         * SCIPIO: Default constructor.
         */
        public CartPaymentInfo(String partyId) { // SCIPIO: 2018-07-19: billing address default: partyId
            super();
            this.partyId = partyId;
            this.refNum = new String[2];
            // All others are null or false
        }

        /**
         * SCIPIO: Copy constructor.
         */
        public CartPaymentInfo(CartPaymentInfo other, boolean exactCopy) {
            this.paymentMethodTypeId = other.paymentMethodTypeId;
            this.paymentMethodId = other.paymentMethodId;
            this.finAccountId = other.finAccountId;
            this.securityCode = other.securityCode;
            this.postalCode = other.postalCode;
            this.refNum = other.refNum.clone();
            this.track2 = other.track2;
            this.partyId = other.partyId;
            this.amount = other.amount;
            this.singleUse = other.singleUse;
            this.isPresent = other.isPresent;
            this.isSwiped = other.isSwiped;
            this.overflow = other.overflow;
            this.origAmount = other.origAmount;
        }

        /**
         * SCIPIO: Tests to ensure the cart is an exact copy of the other; used to verify {@link #exactCopy}.
         * NOTE: This is NOT the same as a logical Object equals override! This is mainly for testing.
         */
        void ensureExactEquals(CartPaymentInfo other) {
            try {
                ShoppingCart.ensureExactEquals(this.paymentMethodTypeId, other.paymentMethodTypeId);
                ShoppingCart.ensureExactEquals(this.paymentMethodId, other.paymentMethodId);
                ShoppingCart.ensureExactEquals(this.finAccountId, other.finAccountId);
                ShoppingCart.ensureExactEquals(this.securityCode, other.securityCode);
                ShoppingCart.ensureExactEquals(this.postalCode, other.postalCode);
                ShoppingCart.ensureExactEquals(this.refNum, other.refNum);
                ShoppingCart.ensureExactEquals(this.track2, other.track2);
                ShoppingCart.ensureExactEquals(this.partyId, other.partyId);
                ShoppingCart.ensureExactEquals(this.amount, other.amount);
                ShoppingCart.ensureExactEquals(this.singleUse, other.singleUse);
                ShoppingCart.ensureExactEquals(this.isPresent, other.isPresent);
                ShoppingCart.ensureExactEquals(this.isSwiped, other.isSwiped);
                ShoppingCart.ensureExactEquals(this.overflow, other.overflow);
                ShoppingCart.ensureExactEquals(this.origAmount, other.origAmount);
            } catch(IllegalStateException e) {
                throw new IllegalStateException("CartPaymentInfo field not equal: " + e.getMessage(), e);
            }
        }
        
        public GenericValue getValueObject(Delegator delegator) {
            String entityName = null;
            Map<String, String> lookupFields = null;
            if (paymentMethodId != null) {
                lookupFields = UtilMisc.<String, String>toMap("paymentMethodId", paymentMethodId);
                entityName = "PaymentMethod";
            } else if (paymentMethodTypeId != null) {
                lookupFields = UtilMisc.<String, String>toMap("paymentMethodTypeId", paymentMethodTypeId);
                entityName = "PaymentMethodType";
            } else {
                throw new IllegalArgumentException("Could not create value object because paymentMethodId and paymentMethodTypeId are null");
            }

            try {
                return EntityQuery.use(delegator).from(entityName).where(lookupFields).cache(true).queryOne();
            } catch (GenericEntityException e) {
                Debug.logError(e, module);
            }

            return null;
        }

        public GenericValue getBillingAddressFromParty(Delegator delegator) {  // SCIPIO: 2018-07-19: billing address default
            GenericValue postalAddress = null;
            try {
                GenericValue partyContactMechPurpose = EntityQuery.use(delegator).from("PartyContactMechPurpose")
                        .where("partyId", partyId, "contactMechPurposeTypeId", "BILLING_LOCATION").orderBy("-fromDate").filterByDate().queryFirst();
                if (UtilValidate.isEmpty(partyContactMechPurpose)) {
                    partyContactMechPurpose = EntityQuery.use(delegator).from("PartyContactMechPurpose")
                            .where("partyId", partyId, "contactMechPurposeTypeId", "GENERAL_LOCATION").orderBy("-fromDate").filterByDate().queryFirst();
                }
                if (UtilValidate.isEmpty(partyContactMechPurpose)) {
                    partyContactMechPurpose = EntityQuery.use(delegator).from("PartyContactMechPurpose")
                            .where("partyId", partyId, "contactMechPurposeTypeId", "SHIPPING_LOCATION").orderBy("-fromDate").filterByDate().queryFirst();
                }
                if (UtilValidate.isNotEmpty(partyContactMechPurpose)) {
                    postalAddress = partyContactMechPurpose.getRelatedOne("PostalAddress", false);
                }
            } catch (GenericEntityException e) {
                Debug.logError(e, module);
            }
            return postalAddress;
        }

        public GenericValue getBillingAddress(Delegator delegator) {
            GenericValue valueObj = this.getValueObject(delegator);
            GenericValue postalAddress = null;

            if ("PaymentMethod".equals(valueObj.getEntityName())) {
                String paymentMethodTypeId = valueObj.getString("paymentMethodTypeId");
                String paymentMethodId = valueObj.getString("paymentMethodId");

                // billing account, credit card, gift card, eft account all have postal address
                try {
                    GenericValue pmObj = null;
                    if ("CREDIT_CARD".equals(paymentMethodTypeId)) {
                        pmObj = EntityQuery.use(delegator).from("CreditCard").where("paymentMethodId", paymentMethodId).queryOne();
                    } else if ("GIFT_CARD".equals(paymentMethodTypeId)) {
                        pmObj = EntityQuery.use(delegator).from("GiftCard").where("paymentMethodId", paymentMethodId).queryOne();
                    } else if ("EFT_ACCOUNT".equals(paymentMethodTypeId)) {
                        pmObj = EntityQuery.use(delegator).from("EftAccount").where("paymentMethodId", paymentMethodId).queryOne();
                    } else if ("EXT_BILLACT".equals(paymentMethodTypeId)) {
                        pmObj = EntityQuery.use(delegator).from("BillingAccount").where("paymentMethodId", paymentMethodId).queryOne();
                    } else if ("EXT_PAYPAL".equals(paymentMethodTypeId)) {
                        pmObj = EntityQuery.use(delegator).from("PayPalPaymentMethod").where("paymentMethodId", paymentMethodId).queryOne();
                    } else { // SCIPIO: 2018-07-19: billing address default
                        pmObj = getBillingAddressFromParty(delegator);
                    }
                    if (pmObj != null) {
                        postalAddress = pmObj.getRelatedOne("PostalAddress", false);
                    } else {
                        Debug.logInfo("No PaymentMethod Object Found - " + paymentMethodId, module);
                    }
                } catch (GenericEntityException e) {
                    Debug.logError(e, module);
                }
            } else { // SCIPIO: 2018-07-19: billing address default
                postalAddress = getBillingAddressFromParty(delegator);
            }

            return postalAddress;
        }

        public List<GenericValue> makeOrderPaymentInfos(Delegator delegator, ShoppingCart cart) {
            BigDecimal maxAmount = BigDecimal.ZERO;
            GenericValue valueObj = this.getValueObject(delegator);
            List<GenericValue> values = new ArrayList<>();
            if (valueObj != null) {
                // first create a BILLING_LOCATION for the payment method address if there is one
                if ("PaymentMethod".equals(valueObj.getEntityName())) {
                    String billingAddressId = null;

                    GenericValue billingAddress = this.getBillingAddress(delegator);
                    if (billingAddress != null) {
                        billingAddressId = billingAddress.getString("contactMechId");
                    }

                    if (UtilValidate.isNotEmpty(billingAddressId)) {
                        GenericValue orderCm = delegator.makeValue("OrderContactMech");
                        orderCm.set("contactMechPurposeTypeId", "BILLING_LOCATION");
                        orderCm.set("contactMechId", billingAddressId);
                        values.add(orderCm);
                    }
                }

                GenericValue productStore = null;
                String splitPayPrefPerShpGrp = null;
                try {
                    productStore = EntityQuery.use(delegator).from("ProductStore").where("productStoreId", cart.getProductStoreId()).queryOne();
                } catch (GenericEntityException e) {
                    Debug.logError(e.toString(), module);
                }
                if (productStore != null) {
                    splitPayPrefPerShpGrp = productStore.getString("splitPayPrefPerShpGrp");
                }
                if (splitPayPrefPerShpGrp == null) {
                    splitPayPrefPerShpGrp = "N";
                }
                if ("Y".equals(splitPayPrefPerShpGrp) && cart.paymentInfo.size() > 1) {
                    throw new GeneralRuntimeException("Split Payment Preference per Ship Group does not yet support multiple Payment Methods");
                }
                if ("Y".equals(splitPayPrefPerShpGrp)  && cart.paymentInfo.size() == 1) {
                    for (CartShipInfo csi : cart.getShipGroups()) {
                        maxAmount = csi.getTotal().add(cart.getOrderOtherAdjustmentTotal().add(cart.getOrderGlobalAdjustments()).divide(new BigDecimal(cart.getShipGroupSize()), generalRounding)).add(csi.getShipEstimate().add(csi.getTotalTax(cart)));
                        maxAmount = maxAmount.setScale(scale, rounding);

                        // create the OrderPaymentPreference record
                        GenericValue opp = delegator.makeValue("OrderPaymentPreference");
                        opp.set("paymentMethodTypeId", valueObj.getString("paymentMethodTypeId"));
                        opp.set("presentFlag", isPresent ? "Y" : "N");
                        opp.set("swipedFlag", isSwiped ? "Y" : "N");
                        opp.set("overflowFlag", overflow ? "Y" : "N");
                        opp.set("paymentMethodId", paymentMethodId);
                        opp.set("finAccountId", finAccountId);
                        opp.set("billingPostalCode", postalCode);
                        opp.set("maxAmount", maxAmount);
                        opp.set("shipGroupSeqId", csi.getShipGroupSeqId());
                        if (refNum != null) {
                            opp.set("manualRefNum", refNum[0]);
                            opp.set("manualAuthCode", refNum[1]);
                        }
                        if (securityCode != null) {
                            opp.set("securityCode", securityCode);
                        }
                        if (track2 != null) {
                           opp.set("track2", track2);
                        }
                        if (paymentMethodId != null || "FIN_ACCOUNT".equals(paymentMethodTypeId)) {
                            opp.set("statusId", "PAYMENT_NOT_AUTH");
                        } else if (paymentMethodTypeId != null) {
                            // external payment method types require notification when received
                            // internal payment method types are assumed to be in-hand
                            if (paymentMethodTypeId.startsWith("EXT_")) {
                                opp.set("statusId", "PAYMENT_NOT_RECEIVED");
                            } else {
                                opp.set("statusId", "PAYMENT_RECEIVED");
                            }
                        }
                        Debug.logInfo("ShipGroup [" + csi.getShipGroupSeqId() +"]", module);
                        Debug.logInfo("Creating OrderPaymentPreference - " + opp, module);
                        values.add(opp);
                    }
                } else if ("N".equals(splitPayPrefPerShpGrp)) {
                    maxAmount = maxAmount.add(amount);
                    maxAmount = maxAmount.setScale(scale, rounding);

                    // create the OrderPaymentPreference record
                    GenericValue opp = delegator.makeValue("OrderPaymentPreference");
                    opp.set("paymentMethodTypeId", valueObj.getString("paymentMethodTypeId"));
                    opp.set("presentFlag", isPresent ? "Y" : "N");
                    opp.set("swipedFlag", isSwiped ? "Y" : "N");
                    opp.set("overflowFlag", overflow ? "Y" : "N");
                    opp.set("paymentMethodId", paymentMethodId);
                    opp.set("finAccountId", finAccountId);
                    opp.set("billingPostalCode", postalCode);
                    opp.set("maxAmount", maxAmount);
                    if (refNum != null) {
                        opp.set("manualRefNum", refNum[0]);
                        opp.set("manualAuthCode", refNum[1]);
                    }
                    if (securityCode != null) {
                        opp.set("securityCode", securityCode);
                    }
                    if (track2 != null) {
                        opp.set("track2", securityCode);
                    }
                    if (paymentMethodId != null || "FIN_ACCOUNT".equals(paymentMethodTypeId)) {
                        opp.set("statusId", "PAYMENT_NOT_AUTH");
                    } else if (paymentMethodTypeId != null) {
                        // external payment method types require notification when received
                        // internal payment method types are assumed to be in-hand
                        if (paymentMethodTypeId.startsWith("EXT_")) {
                            opp.set("statusId", "PAYMENT_NOT_RECEIVED");
                        } else {
                            opp.set("statusId", "PAYMENT_RECEIVED");
                        }
                    }
                    Debug.logInfo("Creating OrderPaymentPreference - " + opp, module);
                    values.add(opp);
                }
            }

            return values;
        }

        @Override
        public int compareTo(Object o) {
            CartPaymentInfo that = (CartPaymentInfo) o;
            Debug.logInfo("Compare [" + this.toString() + "] to [" + that.toString() + "]", module);

            if (this.finAccountId == null) {
                if (that.finAccountId != null) {
                    return -1;
                }
            } else if (!this.finAccountId.equals(that.finAccountId)) {
                return -1;
            }

            if (this.paymentMethodId != null) {
                if (that.paymentMethodId == null) {
                    return 1;
                }
                int pmCmp = this.paymentMethodId.compareTo(that.paymentMethodId);
                if (pmCmp == 0) {
                    if (this.refNum != null && this.refNum[0] != null) {
                        if (that.refNum != null && that.refNum[0] != null) {
                            return this.refNum[0].compareTo(that.refNum[0]);
                        }
                        return 1;
                    }
                    if (that.refNum != null && that.refNum[0] != null) {
                        return -1;
                    }
                    return 0;
                }
                return pmCmp;
            }
            if (that.paymentMethodId != null) {
                return -1;
            }

            int pmtCmp = this.paymentMethodTypeId.compareTo(that.paymentMethodTypeId);
            if (pmtCmp == 0) {
                if (this.refNum != null && this.refNum[0] != null) {
                    if (that.refNum != null && that.refNum[0] != null) {
                        return this.refNum[0].compareTo(that.refNum[0]);
                    }
                    return 1;
                }
                if (that.refNum != null && that.refNum[0] != null) {
                    return -1;
                }
                return 0;
            }
            return pmtCmp;
        }

        /* SCIPIO: 2018-10-09: TODO: REVIEW: this is much more precise than compareTo, but usage
           is unclear, so will have to omit this until real case clarifies as used in a Map, Set or other
        @Override
        public int hashCode() {
            // SCIPIO: 2018-10-09: TODO: REVIEW: this is much more precise than compareTo zero, usage unclear
            final int prime = 31;
            int result = 1;
            result = prime * result + ((amount == null) ? 0 : amount.hashCode());
            result = prime * result + ((finAccountId == null) ? 0 : finAccountId.hashCode());
            result = prime * result + (isPresent ? 1231 : 1237);
            result = prime * result + (isSwiped ? 1231 : 1237);
            result = prime * result + (overflow ? 1231 : 1237);
            result = prime * result + ((paymentMethodId == null) ? 0 : paymentMethodId.hashCode());
            result = prime * result + ((paymentMethodTypeId == null) ? 0 : paymentMethodTypeId.hashCode());
            result = prime * result + ((postalCode == null) ? 0 : postalCode.hashCode());
            result = prime * result + Arrays.hashCode(refNum);
            result = prime * result + ((securityCode == null) ? 0 : securityCode.hashCode());
            result = prime * result + (singleUse ? 1231 : 1237);
            result = prime * result + ((track2 == null) ? 0 : track2.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            CartPaymentInfo other = (CartPaymentInfo) obj;
            if (amount == null) {
                if (other.amount != null) {
                    return false;
                }
            } else if (!amount.equals(other.amount)) {
                return false;
            }
            if (finAccountId == null) {
                if (other.finAccountId != null) {
                    return false;
                }
            } else if (!finAccountId.equals(other.finAccountId)) {
                return false;
            }
            if (isPresent != other.isPresent) {
                return false;
            }
            if (isSwiped != other.isSwiped) {
                return false;
            }
            if (overflow != other.overflow) {
                return false;
            }
            if (paymentMethodId == null) {
                if (other.paymentMethodId != null) {
                    return false;
                }
            } else if (!paymentMethodId.equals(other.paymentMethodId)) {
                return false;
            }
            if (paymentMethodTypeId == null) {
                if (other.paymentMethodTypeId != null) {
                    return false;
                }
            } else if (!paymentMethodTypeId.equals(other.paymentMethodTypeId)) {
                return false;
            }
            if (postalCode == null) {
                if (other.postalCode != null) {
                    return false;
                }
            } else if (!postalCode.equals(other.postalCode)) {
                return false;
            }
            if (!Arrays.equals(refNum, other.refNum)) {
                return false;
            }
            if (securityCode == null) {
                if (other.securityCode != null) {
                    return false;
                }
            } else if (!securityCode.equals(other.securityCode)) {
                return false;
            }
            if (singleUse != other.singleUse) {
                return false;
            }
            if (track2 == null) {
                if (other.track2 != null) {
                    return false;
                }
            } else if (!track2.equals(other.track2)) {
                return false;
            }
            return true;
        }
        */

        @Override
        public String toString() {
            return "Pm: " + paymentMethodId + " / PmType: " + paymentMethodTypeId + " / Amt: " + amount + " / Ref: " + refNum[0] + "!" + refNum[1];
        }
    }

    public Map<String, String> getOrderAttributes() {
        return orderAttributes;
    }

    public void setOrderAttributes(Map<String, String> orderAttributes) {
        this.orderAttributes = orderAttributes;
    }

    public String getOrderStatusId() {
        return orderStatusId;
    }

    public void setOrderStatusId(String orderStatusId) {
        this.orderStatusId = orderStatusId;
    }

    public String getOrderStatusString() {
        return orderStatusString;
    }

    public void setOrderStatusString(String orderStatusString) {
        this.orderStatusString = orderStatusString;
    }

    public static BigDecimal getMinimumOrderQuantity(Delegator delegator, BigDecimal itemBasePrice, String itemProductId) throws GenericEntityException {
        BigDecimal minQuantity = BigDecimal.ZERO;
        BigDecimal minimumOrderPrice = BigDecimal.ZERO;

        List<GenericValue> minimumOrderPriceList =  EntityQuery.use(delegator).from("ProductPrice")
                                                        .where("productId", itemProductId, "productPriceTypeId", "MINIMUM_ORDER_PRICE")
                                                        .filterByDate()
                                                        .queryList();
        if (itemBasePrice == null) {
            List<GenericValue> productPriceList = EntityQuery.use(delegator).from("ProductPrice")
                                                      .where("productId", itemProductId)
                                                      .filterByDate()
                                                      .queryList();
            Map<String, BigDecimal> productPriceMap = new HashMap<>();
            for (GenericValue productPrice : productPriceList) {
                productPriceMap.put(productPrice.getString("productPriceTypeId"), productPrice.getBigDecimal("price"));
            }
            if (UtilValidate.isNotEmpty(productPriceMap.get("SPECIAL_PROMO_PRICE"))) {
                itemBasePrice = productPriceMap.get("SPECIAL_PROMO_PRICE");
            } else if (UtilValidate.isNotEmpty(productPriceMap.get("PROMO_PRICE"))) {
                itemBasePrice = productPriceMap.get("PROMO_PRICE");
            } else if (UtilValidate.isNotEmpty(productPriceMap.get("DEFAULT_PRICE"))) {
                itemBasePrice = productPriceMap.get("DEFAULT_PRICE");
            } else if (UtilValidate.isNotEmpty(productPriceMap.get("LIST_PRICE"))) {
                itemBasePrice = productPriceMap.get("LIST_PRICE");
            }
        }
        if (UtilValidate.isNotEmpty(minimumOrderPriceList)) {
            minimumOrderPrice = EntityUtil.getFirst(minimumOrderPriceList).getBigDecimal("price");
        }
        if (itemBasePrice != null && minimumOrderPrice.compareTo(itemBasePrice) > 0) {
            minQuantity = minimumOrderPrice.divide(itemBasePrice, 0, RoundingMode.UP);
        }
        return minQuantity;
    }

    /**
     * SCIPIO: Gets all emails that are OR are to be associated with the order, including
     * party's to-be-associated emails and order additional emails.
     * <p>
     * WARN: This is not guaranteed to match the final order! The party's
     * emails are not stored in cart.
     * <p>
     * This attempts to emulate {@link OrderReadHelper#getOrderEmailList()}.
     */
    public List<String> getOrderEmailList() {
        List<String> emailList = new ArrayList<>();
        // WARN: This must match what is done in CheckOutHelper!
        String partyId = this.getPlacingCustomerPartyId();
        if (UtilValidate.isNotEmpty(partyId)) {
            GenericValue party;
            try {
                party = EntityQuery.use(delegator).from("Party").where("partyId", partyId).queryOne();
                Collection<GenericValue> contactMechList = ContactHelper.getContactMechByType(party, "EMAIL_ADDRESS", false);
                for(GenericValue contactMech : contactMechList) {
                    emailList.add(contactMech.getString("infoString"));
                }
            } catch (GenericEntityException e) {
                Debug.logError("Could not get order emails for party '" + partyId + "'", module);
            }
        }
        // WARN: This must match waht is done in CheckOutHelper!
        List<String> addEmailList = StringUtil.split(this.getOrderAdditionalEmails(), ",");
        if (UtilValidate.isNotEmpty(addEmailList)) {
            emailList.addAll(addEmailList);
        }
        return emailList;
    }

    // ================= Cart Subscriptions =================

    /**
     * SCIPIO: Retrieve all subscription of the entire cart.
     */
    public Map<String, List<GenericValue>> getItemSubscriptions() {
        List<ShoppingCartItem> cartItems = items();
        if (cartItems != null) {
            Map<String, List<GenericValue>> cartSubscriptionItems = new HashMap<>();
            for (ShoppingCartItem cartItem : cartItems) {
                List<GenericValue> productSubscriptions = getItemSubscriptions(cartItem.getDelegator(), cartItem.getProductId());
                if (Debug.infoOn()) {
                    for (GenericValue productSubscription : productSubscriptions) {
                        Debug.logInfo("Found cartItem [" + cartItem.getOrderItemSeqId() + "#" + cartItem.getProductId() + "] with subscription id["
                                + productSubscription.getString("subscriptionResourceId") + "]", module);
                    }
                }
                if (UtilValidate.isNotEmpty(productSubscriptions)) {
                    cartSubscriptionItems.put(cartItem.getProductId(), productSubscriptions);
                }
            }
            /* 2018-11-22: Removed: cartSubscriptionItems cache poses several issues
            return this.cartSubscriptionItems;
            */
            return cartSubscriptionItems;
        }
        return null;
    }

    /**
     * SCIPIO: Retrieve all subscriptions associated to an productId.
     */
    public List<GenericValue> getItemSubscriptions(Delegator delegator, String productId) {
        /*
         * DEV NOTE: 2018-11-22: This currently relies on entity cache for performance;
         * if this proves unacceptable, maybe can add a transient List<GenericValue> productSubscriptionResources cache
         * to ShoppingCartItem instead - it is less likely to go out of sync with the cart if there instead of this.cartSubscriptionItems.
         */
        
        /* 2018-11-22: Removed: cartSubscriptionItems cache poses several issues
        if (this.cartSubscriptionItems == null) {
            this.cartSubscriptionItems = new HashMap<>();
        }
        */
        List<GenericValue> productSubscriptionResources;
        try {
            productSubscriptionResources = EntityQuery.use(delegator).from("ProductSubscriptionResource").where("productId", productId)
                    .cache(true).filterByDate().queryList();
        } catch (GenericEntityException e) {
            Debug.logError(e, "Error while looking up ProductSubscriptionResource for product '" + productId + "'", module);
            return new ArrayList<>();
        }
        /* 2018-11-22: Removed: cartSubscriptionItems cache poses several issues
        if (UtilValidate.isNotEmpty(productSubscriptionResources)) {
            this.cartSubscriptionItems.put(productId, productSubscriptionResources);
        } else {
            // 2018-11-12: Keep sync when no subscription
            this.cartSubscriptionItems.remove(productId);
        }
        */
        return productSubscriptionResources;
    }

    /**
     * SCIPIO: Checks if any order item has an underlying subscription/s bound to it.
     */
    public boolean hasSubscriptions() {
        return UtilValidate.isNotEmpty(this.getItemSubscriptions());
    }

    /**
     * SCIPIO: Checks if an order item has an underlying subscription/s bound to it.
     */
    public boolean hasSubscriptions(GenericValue cartItem) {
        Map<String, List<GenericValue>> cartSubscriptionItems = getItemSubscriptions();
        return UtilValidate.isNotEmpty(cartSubscriptionItems) && cartSubscriptionItems.containsKey(cartItem.getString("productId"));
    }

    /**
     * SCIPIO: Check if the order contains only subscription items.
     */
    public boolean orderContainsSubscriptionItemsOnly() {
        List<ShoppingCartItem> cartItems = items();
        Map<String, List<GenericValue>> cartSubscriptionItems = getItemSubscriptions();
        if (cartItems != null && cartSubscriptionItems != null) {
            for (ShoppingCartItem orderItem : cartItems) {
                if (!cartSubscriptionItems.containsKey(orderItem.getProductId())) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * SCIPIO: Returns true if should allow selecting ship method even if estimate was null ("calculated offline").
     * Default: false.
     * See {@link WebShoppingCart} for how this is set.
     * Added 2018-11-14.
     */
    public boolean isAllowMissingShipEstimates() {
        return allowMissingShipEstimates;
    }

    /**
     * SCIPIO: Sets if should allow selecting ship method even if estimate was null ("calculated offline").
     * Default: false.
     * See {@link WebShoppingCart} for how this is set.
     * Added 2018-11-14.
     */
    public void setAllowMissingShipEstimates(boolean allowMissingShipEstimates) {
        this.allowMissingShipEstimates = allowMissingShipEstimates;
    }

    /**
     * SCIPIO: Returns true if verbose logging is on for cart-related processes. Roughly same as verbose logging.
     * WARN: Some things controlled by this may lower performance when enabled.
     * Added 2018-11-30. 
     */
    public static boolean verboseOn() {
        return (ShoppingCart.DEBUG || Debug.verboseOn());
    }
}

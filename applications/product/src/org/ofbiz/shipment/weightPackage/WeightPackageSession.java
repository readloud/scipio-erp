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
package org.ofbiz.shipment.weightPackage;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.GeneralException;
import org.ofbiz.base.util.UtilFormatOut;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilNumber;
import org.ofbiz.base.util.UtilProperties;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.entity.Delegator;
import org.ofbiz.entity.DelegatorFactory;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.util.EntityQuery;
import org.ofbiz.service.LocalDispatcher;
import org.ofbiz.service.ServiceContainer;
import org.ofbiz.service.ServiceUtil;

/**
 * WeightPackageSession.
 * <p>
 * SCIPIO: 2018-11-28: (Nearly) All public methods now synchronized (except where not needed).
 */
@SuppressWarnings("serial")
public class WeightPackageSession implements Serializable {

    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());

    protected GenericValue userLogin = null;
    protected String dispatcherName = null;
    protected String delegatorName = null;
    protected String primaryOrderId = null;
    protected String primaryShipGrpSeqId = null;
    protected String picklistBinId = null;
    protected String shipmentId = null;
    protected String invoiceId = null;
    protected String facilityId = null;
    protected String carrierPartyId = null;
    protected String dimensionUomId = null;
    protected String weightUomId = null;
    protected BigDecimal estimatedShipCost = null;
    protected BigDecimal actualShipCost = null;
    protected int weightPackageSeqId = 1;
    protected List<WeightPackageSessionLine> weightPackageLines = null;

    private transient Delegator _delegator = null;
    private transient LocalDispatcher _dispatcher = null;
    private static RoundingMode rounding = UtilNumber.getRoundingMode("invoice.rounding");

    public WeightPackageSession() {
    }

    public WeightPackageSession(LocalDispatcher dispatcher, GenericValue userLogin, String facilityId, String picklistBinId, String orderId, String shipGrpSeqId) {
        this._dispatcher = dispatcher;
        this.dispatcherName = dispatcher.getName();

        this._delegator = _dispatcher.getDelegator();
        this.delegatorName = _delegator.getDelegatorName();

        this.primaryOrderId = orderId;
        this.primaryShipGrpSeqId = shipGrpSeqId;
        this.picklistBinId = picklistBinId;
        this.userLogin = userLogin;
        this.facilityId = facilityId;
        this.weightPackageLines = new LinkedList<WeightPackageSessionLine>();
    }

    public WeightPackageSession(LocalDispatcher dispatcher, GenericValue userLogin, String facilityId) {
        this(dispatcher, userLogin, facilityId, null, null, null);
    }

    public WeightPackageSession(LocalDispatcher dispatcher, GenericValue userLogin) {
        this(dispatcher, userLogin, null, null, null, null);
    }

    public LocalDispatcher getDispatcher() {
        if (_dispatcher == null) {
            _dispatcher = ServiceContainer.getLocalDispatcher(dispatcherName, this.getDelegator());
        }
        return _dispatcher;
    }

    public Delegator getDelegator() {
        if (_delegator == null) {
            _delegator = DelegatorFactory.getDelegator(delegatorName);
        }
        return _delegator;
    }

    public synchronized void createWeightPackageLine(String orderId, BigDecimal packageWeight, BigDecimal packageLength, BigDecimal packageWidth, BigDecimal packageHeight, String shipmentBoxTypeId) throws GeneralException {
        weightPackageLines.add(new WeightPackageSessionLine(orderId, packageWeight, packageLength, packageWidth, packageHeight, shipmentBoxTypeId, this.weightPackageSeqId));
        this.weightPackageSeqId++;
    }

    public synchronized int getWeightPackageSeqId() {
        return this.weightPackageSeqId;
    }

    public synchronized String getFacilityId() {
        return this.facilityId;
    }

    public synchronized void setFacilityId(String facilityId) {
        this.facilityId = facilityId;
    }

    public synchronized String getPrimaryOrderId() {
        return this.primaryOrderId;
    }

    public synchronized void setPrimaryOrderId(String primaryOrderId) {
        this.primaryOrderId = primaryOrderId;
    }

    public synchronized String getPrimaryShipGroupSeqId() {
        return this.primaryShipGrpSeqId;
    }

    public synchronized void setPrimaryShipGroupSeqId(String primaryShipGrpSeqId) {
        this.primaryShipGrpSeqId = primaryShipGrpSeqId;
    }

    public synchronized void setPicklistBinId(String picklistBinId) {
        this.picklistBinId = picklistBinId;
    }

    public synchronized String getPicklistBinId() {
        return this.picklistBinId;
    }

    public synchronized void setEstimatedShipCost(BigDecimal estimatedShipCost) {
        this.estimatedShipCost = estimatedShipCost;
    }

    public synchronized BigDecimal getEstimatedShipCost() {
        return this.estimatedShipCost;
    }

    public synchronized void setActualShipCost(BigDecimal actualShipCost) {
        this.actualShipCost = actualShipCost;
    }

    public synchronized BigDecimal getActualShipCost() {
        return this.actualShipCost;
    }

    public synchronized String getShipmentId() {
        return this.shipmentId;
    }

    public synchronized void setShipmentId(String shipmentId) {
        this.shipmentId = shipmentId;
    }

    public synchronized String getInvoiceId() {
        return this.invoiceId;
    }

    public synchronized void setInvoiceId(String invoiceId) {
        this.invoiceId = invoiceId;
    }

    public synchronized String getWeightUomId() {
        return weightUomId;
    }

    public synchronized void setWeightUomId(String weightUomId) {
        this.weightUomId = weightUomId;
    }

    public synchronized String getDimensionUomId() {
        return dimensionUomId;
    }

    public synchronized void setCarrierPartyId(String carrierPartyId) {
        this.carrierPartyId = carrierPartyId;
    }

    public synchronized void setDimensionUomId(String dimensionUomId) {
        this.dimensionUomId = dimensionUomId;
    }

    public synchronized BigDecimal getShippableWeight(String orderId) {
        BigDecimal shippableWeight = BigDecimal.ZERO;
        for (WeightPackageSessionLine packedLine : this.getPackedLines(orderId)) {
            shippableWeight = shippableWeight.add(packedLine.getPackageWeight());
        }
        return shippableWeight;
    }

    public synchronized List<WeightPackageSessionLine> getPackedLines() {
        return this.weightPackageLines;
    }

    public synchronized List<WeightPackageSessionLine> getPackedLines(String orderId) {
        List<WeightPackageSessionLine> packedLines = new LinkedList<WeightPackageSessionLine>();
        if (UtilValidate.isNotEmpty(orderId)) {
            for (WeightPackageSessionLine packedLine: this.getPackedLines()) {
               if (orderId.equals(packedLine.getOrderId()))
                    packedLines.add(packedLine);
            }
        }
        return packedLines;
    }

    public synchronized WeightPackageSessionLine getPackedLine(int weightPackageSeqId) {
        WeightPackageSessionLine packedLine = null;
        if (weightPackageSeqId > 0) {
            for (WeightPackageSessionLine line : this.getPackedLines()) {
                if ((line.getWeightPackageSeqId()) == weightPackageSeqId)
                    packedLine = line;
            }
        }
        return packedLine;
    }

    public synchronized void setPackageWeight(BigDecimal packageWeight, int weightPackageSeqId) {
        if (weightPackageSeqId > 0) {
            WeightPackageSessionLine packedLine = this.getPackedLine(weightPackageSeqId);
            if (UtilValidate.isNotEmpty(packedLine))
                packedLine.setPackageWeight(packageWeight);
        }
    }

    public synchronized void setPackageLength(BigDecimal packageLength, int weightPackageSeqId) {
        if (weightPackageSeqId > 0) {
            WeightPackageSessionLine packedLine = this.getPackedLine(weightPackageSeqId);
            if (UtilValidate.isNotEmpty(packedLine))
                packedLine.setPackageLength(packageLength);
        }
    }

    public synchronized void setPackageWidth(BigDecimal packageWidth, int weightPackageSeqId) {
        if (weightPackageSeqId > 0) {
            WeightPackageSessionLine packedLine = this.getPackedLine(weightPackageSeqId);
            if (UtilValidate.isNotEmpty(packedLine))
                packedLine.setPackageWidth(packageWidth);
        }
    }

    public synchronized void setPackageHeight(BigDecimal packageHeight, int weightPackageSeqId) {
        if (weightPackageSeqId > 0) {
            WeightPackageSessionLine packedLine = this.getPackedLine(weightPackageSeqId);
            if (UtilValidate.isNotEmpty(packedLine))
                packedLine.setPackageHeight(packageHeight);
        }
    }

    public synchronized void setShipmentBoxTypeId(String shipmentBoxTypeId, int weightPackageSeqId) {
        if (weightPackageSeqId > 0) {
            WeightPackageSessionLine packedLine = this.getPackedLine(weightPackageSeqId);
            if (UtilValidate.isNotEmpty(packedLine))
                packedLine.setShipmentBoxTypeId(shipmentBoxTypeId);
        }
    }

    public synchronized void deletePackedLine(int weightPackageSeqId) {
        if (weightPackageSeqId > 0) {
            WeightPackageSessionLine packedLine = this.getPackedLine(weightPackageSeqId);
            this.weightPackageLines.remove(packedLine);
        }
    }

    public synchronized void setDimensionAndShipmentBoxType(int weightPackageSeqId) {
        if (weightPackageSeqId > 0) {
            WeightPackageSessionLine packedLine = this.getPackedLine(weightPackageSeqId);
            packedLine.setPackageLength(null);
            packedLine.setPackageWidth(null);
            packedLine.setPackageHeight(null);
            packedLine.setShipmentBoxTypeId(null);
        }
    }

    public synchronized void clearPackedLines(String orderId) {
        for (WeightPackageSessionLine packedLine : this.getPackedLines(orderId)) {
            this.weightPackageLines.remove(packedLine);
        }
    }

    public synchronized String complete(String orderId, Locale locale, String calculateOnlineShippingRateFromUps) throws GeneralException {
        //create the package(s)
        this.createPackages(orderId);
        // calculate the actual shipping charges according to package(s) weight and dimensions
        BigDecimal actualShippingCost;
        // Check if UPS integration is done
        if ("UPS".equals(this.carrierPartyId) && "Y".equals(calculateOnlineShippingRateFromUps)) {
            // call upsShipmentConfirm service, it will calculate the online shipping rate from UPS and save in ShipmentRouteSegment entity in actualCost field
            actualShippingCost = this.upsShipmentConfirm();
        } else {
            // calculate the shipping charges manually
            actualShippingCost = this.getActualShipCost();
        }
        // calculate the difference between estimated shipping charges and actual shipping charges
        if (diffInShipCost(actualShippingCost)) {
            return "showWarningForm";
        } else if ("UPS".equals(this.carrierPartyId) && "Y".equals(calculateOnlineShippingRateFromUps)) {
            // call upsShipmentAccept service, it will made record(s) in ShipmentPackageRouteSeg entity
            this.upsShipmentAccept();
        }
        // change order item(s) status
        this.changeOrderItemStatus(orderId);
        // assign item(s) to package(s)
        this.applyItemsToPackages(orderId);
        // update the ShipmentRouteSegments with total weight and weightUomId
        this.updateShipmentRouteSegments(orderId);
        // set the shipment to packed
        this.setShipmentToPacked();

        return "success";
    }

    public synchronized boolean completeShipment(String orderId, String calculateOnlineShippingRateFromUps) throws GeneralException {
        // Check if UPS integration is done
        if ("UPS".equals(this.carrierPartyId) && "Y".equals(calculateOnlineShippingRateFromUps)) {
            // call upsShipmentAccept service, it will made record(s) in ShipmentPackageRouteSeg entity
            this.upsShipmentAccept();
        }
        // change order item(s) status
        this.changeOrderItemStatus(orderId);
        // assign item(s) to package(s)
        this.applyItemsToPackages(orderId);
        // update the ShipmentRouteSegments with total weight and weightUomId
        this.updateShipmentRouteSegments(orderId);
        // set the shipment to packed
        this.setShipmentToPacked();

        return true;
    }

    protected BigDecimal upsShipmentConfirm() throws GeneralException {
        Delegator delegator = this.getDelegator();
        BigDecimal actualCost = BigDecimal.ZERO;
        List<GenericValue> shipmentRouteSegments = EntityQuery.use(delegator).from("ShipmentRouteSegment").where("shipmentId", shipmentId).queryList();
        if (UtilValidate.isNotEmpty(shipmentRouteSegments)) {
            for (GenericValue shipmentRouteSegment : shipmentRouteSegments) {
                Map<String, Object> shipmentRouteSegmentMap = new HashMap<String, Object>();
                shipmentRouteSegmentMap.put("shipmentId", shipmentId);
                shipmentRouteSegmentMap.put("shipmentRouteSegmentId", shipmentRouteSegment.getString("shipmentRouteSegmentId"));
                shipmentRouteSegmentMap.put("userLogin", userLogin);
                Map<String, Object> shipmentRouteSegmentResult = this.getDispatcher().runSync("upsShipmentConfirm", shipmentRouteSegmentMap);
                if (ServiceUtil.isError(shipmentRouteSegmentResult)) {
                    throw new GeneralException(ServiceUtil.getErrorMessage(shipmentRouteSegmentResult));
                }
                GenericValue shipRouteSeg = EntityQuery.use(delegator).from("ShipmentRouteSegment").where("shipmentId", shipmentId, "shipmentRouteSegmentId", shipmentRouteSegment.getString("shipmentRouteSegmentId")).queryOne();
                actualCost = actualCost.add(shipRouteSeg.getBigDecimal("actualCost"));
            }
        }
        return actualCost;
    }

    protected void upsShipmentAccept() throws GeneralException {
        List<GenericValue> shipmentRouteSegments = this.getDelegator().findByAnd("ShipmentRouteSegment", UtilMisc.toMap("shipmentId", shipmentId), null, false);
        if (UtilValidate.isNotEmpty(shipmentRouteSegments)) {
            for (GenericValue shipmentRouteSegment : shipmentRouteSegments) {
                Map<String, Object> shipmentRouteSegmentMap = new HashMap<String, Object>();
                shipmentRouteSegmentMap.put("shipmentId", shipmentId);
                shipmentRouteSegmentMap.put("shipmentRouteSegmentId", shipmentRouteSegment.getString("shipmentRouteSegmentId"));
                shipmentRouteSegmentMap.put("userLogin", userLogin);
                Map<String, Object> shipmentRouteSegmentResult = this.getDispatcher().runSync("upsShipmentAccept", shipmentRouteSegmentMap);
                if (ServiceUtil.isError(shipmentRouteSegmentResult)) {
                    throw new GeneralException(ServiceUtil.getErrorMessage(shipmentRouteSegmentResult));
                }
            }
        }
    }

    protected boolean diffInShipCost(BigDecimal actualShippingCost) throws GeneralException {
        BigDecimal estimatedShipCost = this.getEstimatedShipCost();
        BigDecimal doEstimates = new BigDecimal(UtilProperties.getPropertyValue("shipment", "shipment.default.cost_actual_over_estimated_percent_allowed", "10"));
        BigDecimal diffInShipCostInPerc;
        if (estimatedShipCost.compareTo(BigDecimal.ZERO) == 0) {
            diffInShipCostInPerc = actualShippingCost;
        } else {
            diffInShipCostInPerc = (((actualShippingCost.subtract(estimatedShipCost)).divide(estimatedShipCost, 2, rounding)).multiply(new BigDecimal(100))).abs();
        }
        if (doEstimates.compareTo(diffInShipCostInPerc) == -1) {
            return true;
        }
        return false;
    }

    protected void createPackages(String orderId) throws GeneralException {
        int shipPackSeqId = 0;
        for (WeightPackageSessionLine packedLine : this.getPackedLines(orderId)) {
            String shipmentPackageSeqId = UtilFormatOut.formatPaddedNumber(++shipPackSeqId, 5);

            Map<String, Object> shipmentPackageMap = new HashMap<String, Object>();
            shipmentPackageMap.put("shipmentId", shipmentId);
            shipmentPackageMap.put("shipmentPackageSeqId", shipmentPackageSeqId);
            shipmentPackageMap.put("weight", packedLine.getPackageWeight());
            shipmentPackageMap.put("boxLength", packedLine.getPackageLength());
            shipmentPackageMap.put("boxWidth", packedLine.getPackageWidth());
            shipmentPackageMap.put("boxHeight", packedLine.getPackageHeight());
            shipmentPackageMap.put("dimensionUomId", getDimensionUomId());
            shipmentPackageMap.put("shipmentBoxTypeId", packedLine.getShipmentBoxTypeId());
            shipmentPackageMap.put("weightUomId", getWeightUomId());
            shipmentPackageMap.put("userLogin", userLogin);

            Map<String, Object> shipmentPackageResult;
            GenericValue shipmentPackage = this.getDelegator().findOne("ShipmentPackage", UtilMisc.toMap("shipmentId", shipmentId, "shipmentPackageSeqId", shipmentPackageSeqId), false);
            if (UtilValidate.isEmpty(shipmentPackage)) {
                shipmentPackageResult = this.getDispatcher().runSync("createShipmentPackage", shipmentPackageMap);
            } else {
                shipmentPackageResult = this.getDispatcher().runSync("updateShipmentPackage", shipmentPackageMap);
            }
            if (ServiceUtil.isError(shipmentPackageResult)) {
                throw new GeneralException(ServiceUtil.getErrorMessage(shipmentPackageResult));
            }
        }
    }

    protected void changeOrderItemStatus(String orderId) throws GeneralException {
        List<GenericValue> shipmentItems = this.getDelegator().findByAnd("ShipmentItem", UtilMisc.toMap("shipmentId", shipmentId), null, false);
        for (GenericValue shipmentItem : shipmentItems) {
            for (WeightPackageSessionLine packedLine : this.getPackedLines(orderId)) {
                packedLine.setShipmentItemSeqId(shipmentItem.getString("shipmentItemSeqId"));
            }
        }
        List<GenericValue> orderItems = this.getDelegator().findByAnd("OrderItem", UtilMisc.toMap("orderId", orderId, "statusId", "ITEM_APPROVED"), null, false);
        for (GenericValue orderItem : orderItems) {
            List<GenericValue> orderItemShipGrpInvReserves = orderItem.getRelated("OrderItemShipGrpInvRes", null, null, false);
            if (UtilValidate.isEmpty(orderItemShipGrpInvReserves)) {
                Map<String, Object> orderItemStatusMap = new HashMap<String, Object>();
                orderItemStatusMap.put("orderId", orderId);
                orderItemStatusMap.put("orderItemSeqId", orderItem.getString("orderItemSeqId"));
                orderItemStatusMap.put("userLogin", userLogin);
                orderItemStatusMap.put("statusId", "ITEM_COMPLETED");
                Map<String, Object> orderItemStatusResult = this.getDispatcher().runSync("changeOrderItemStatus", orderItemStatusMap);
                if (ServiceUtil.isError(orderItemStatusResult)) {
                    throw new GeneralException(ServiceUtil.getErrorMessage(orderItemStatusResult));
                }
            }
        }
    }

    protected void applyItemsToPackages(String orderId) throws GeneralException {
        if (UtilValidate.isNotEmpty(orderId) && UtilValidate.isNotEmpty(this.getPackedLines(orderId))) {
            int shipPackSeqId = 0;
            for (WeightPackageSessionLine line: this.getPackedLines(orderId)) {
                line.applyLineToPackage(shipmentId, userLogin, getDispatcher(), ++shipPackSeqId);
            }
        }
    }

    protected void updateShipmentRouteSegments(String orderId) throws GeneralException {
        if (UtilValidate.isNotEmpty(orderId)) {
            BigDecimal shipmentWeight = getShippableWeight(orderId);
            if (UtilValidate.isNotEmpty(shipmentWeight) && shipmentWeight.compareTo(BigDecimal.ZERO) <= 0) return;
            List<GenericValue> shipmentRouteSegments = getDelegator().findByAnd("ShipmentRouteSegment", UtilMisc.toMap("shipmentId", this.getShipmentId()), null, false);
            if (UtilValidate.isNotEmpty(shipmentRouteSegments)) {
                for (GenericValue shipmentRouteSegment : shipmentRouteSegments) {
                    shipmentRouteSegment.set("billingWeight", shipmentWeight);
                    shipmentRouteSegment.set("billingWeightUomId", getWeightUomId());
                }
                getDelegator().storeAll(shipmentRouteSegments);
            }
        }
    }

    protected void setShipmentToPacked() throws GeneralException {
        Map<String, Object> shipmentMap = new HashMap<String, Object>();
        shipmentMap.put("shipmentId", shipmentId);
        shipmentMap.put("statusId", "SHIPMENT_PACKED");
        shipmentMap.put("userLogin", userLogin);
        Map<String, Object> shipmentResult = this.getDispatcher().runSync("updateShipment", shipmentMap);
        if (UtilValidate.isNotEmpty(shipmentResult) && ServiceUtil.isError(shipmentResult)) {
            throw new GeneralException(ServiceUtil.getErrorMessage(shipmentResult));
        }
    }

    public BigDecimal getShipmentCostEstimate(GenericValue orderItemShipGroup, String orderId, String productStoreId, List<GenericValue> shippableItemInfo, BigDecimal shippableTotal, BigDecimal shippableWeight, BigDecimal shippableQuantity) {
        return getShipmentCostEstimate(orderItemShipGroup.getString("contactMechId"), orderItemShipGroup.getString("shipmentMethodTypeId"),
                                       orderItemShipGroup.getString("carrierPartyId"), orderItemShipGroup.getString("carrierRoleTypeId"),
                                       orderId, productStoreId, shippableItemInfo, shippableTotal, shippableWeight, shippableQuantity);
    }

    public synchronized BigDecimal getShipmentCostEstimate(String shippingContactMechId, String shipmentMethodTypeId, String carrierPartyId, String carrierRoleTypeId, String orderId, String productStoreId, List<GenericValue> shippableItemInfo, BigDecimal shippableTotal, BigDecimal shippableWeight, BigDecimal shippableQuantity) {
        BigDecimal shipmentCostEstimate = BigDecimal.ZERO;
        Map<String, Object> shipCostEstimateResult = null;
        try {
            Map<String, Object> shipCostEstimateMap = new HashMap<String, Object>();
            shipCostEstimateMap.put("shippingContactMechId", shippingContactMechId);
            shipCostEstimateMap.put("shipmentMethodTypeId", shipmentMethodTypeId);
            shipCostEstimateMap.put("carrierPartyId", carrierPartyId);
            shipCostEstimateMap.put("carrierRoleTypeId", carrierRoleTypeId);
            shipCostEstimateMap.put("productStoreId", productStoreId);
            shipCostEstimateMap.put("shippableItemInfo", shippableItemInfo);
            if (UtilValidate.isEmpty(shippableWeight) && UtilValidate.isNotEmpty(orderId)) {
                shippableWeight = this.getShippableWeight(orderId);
            }
            shipCostEstimateMap.put("shippableWeight", shippableWeight);
            shipCostEstimateMap.put("shippableQuantity", shippableQuantity);
            if (UtilValidate.isEmpty(shippableTotal)) {
                shippableTotal = BigDecimal.ZERO;
            }
            shipCostEstimateMap.put("shippableTotal", shippableTotal);
            shipCostEstimateResult = getDispatcher().runSync("calcShipmentCostEstimate", shipCostEstimateMap);
            if (ServiceUtil.isError(shipCostEstimateResult)) {
                Debug.logError(ServiceUtil.getErrorMessage(shipCostEstimateResult), module);
            }
        } catch (GeneralException e) {
            Debug.logError(e, module);
        }
        if (UtilValidate.isNotEmpty(shipCostEstimateResult.get("shippingEstimateAmount"))) {
            shipmentCostEstimate = (BigDecimal) shipCostEstimateResult.get("shippingEstimateAmount");
        }
        return shipmentCostEstimate;
    }

    protected void savePackagesInfo(String orderId, String calculateOnlineShippingRateFromUps) throws GeneralException {
        //create the package(s)
        this.createPackages(orderId);
        // Check if UPS integration is done
        if ("UPS".equals(this.carrierPartyId) && "Y".equals(calculateOnlineShippingRateFromUps)) {
            // call upsShipmentConfirm service, it will calculate the online shipping rate from UPS and save in ShipmentRouteSegment entity in actualCost field
            this.upsShipmentConfirm();
        }
    }

    protected Integer getOrderedQuantity(String orderId) {
        BigDecimal orderedQuantity = BigDecimal.ZERO;
        try {
            List<GenericValue> orderItems = getDelegator().findByAnd("OrderItem", UtilMisc.toMap("orderId", orderId, "statusId", "ITEM_APPROVED"), null, false);
            for (GenericValue orderItem : orderItems) {
                orderedQuantity = orderedQuantity.add(orderItem.getBigDecimal("quantity"));
            }
        } catch (GenericEntityException e) {
            Debug.logError(e, module);
        }
        return orderedQuantity.intValue();
    }

}

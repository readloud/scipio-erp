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
package org.ofbiz.product.product;

import java.math.BigDecimal;
import java.math.MathContext;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.GeneralException;
import org.ofbiz.base.util.UtilDateTime;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.common.geo.GeoWorker;
import org.ofbiz.entity.Delegator;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.condition.EntityCondition;
import org.ofbiz.entity.condition.EntityOperator;
import org.ofbiz.entity.util.EntityQuery;
import org.ofbiz.entity.util.EntityTypeUtil;
import org.ofbiz.entity.util.EntityUtil;
import org.ofbiz.product.category.CategoryWorker;
import org.ofbiz.product.config.ProductConfigWrapper;
import org.ofbiz.product.config.ProductConfigWrapper.ConfigOption;
import org.ofbiz.service.GenericServiceException;
import org.ofbiz.service.LocalDispatcher;
import org.ofbiz.service.ModelService;
import org.ofbiz.service.ServiceUtil;

/**
 * Product Worker class to reduce code in JSPs.
 */
public final class ProductWorker {

    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());
    public static final String resource = "ProductUiLabels";

    public static final MathContext generalRounding = new MathContext(10);

    private ProductWorker () {}

    public static boolean shippingApplies(GenericValue product) {
        String errMsg = "";
        if (product != null) {
            String productTypeId = product.getString("productTypeId");
            if ("SERVICE".equals(productTypeId) || "SERVICE_PRODUCT".equals(productTypeId) || (ProductWorker.isDigital(product) && !ProductWorker.isPhysical(product))) {
                // don't charge shipping on services or digital goods
                return false;
            }
            Boolean chargeShipping = product.getBoolean("chargeShipping");

            if (chargeShipping == null) {
                return true;
            }
            return chargeShipping;
        }
        throw new IllegalArgumentException(errMsg);
    }

    public static boolean isBillableToAddress(GenericValue product, GenericValue postalAddress) {
        return isAllowedToAddress(product, postalAddress, "PG_PURCH_");
    }
    public static boolean isShippableToAddress(GenericValue product, GenericValue postalAddress) {
        return isAllowedToAddress(product, postalAddress, "PG_SHIP_");
    }
    private static boolean isAllowedToAddress(GenericValue product, GenericValue postalAddress, String productGeoPrefix) {
        if (product != null && postalAddress != null) {
            Delegator delegator = product.getDelegator();
            List<GenericValue> productGeos = null;
            try {
                productGeos = product.getRelated("ProductGeo", null, null, false);
            } catch (GenericEntityException e) {
                Debug.logError(e, module);
            }
            List<GenericValue> excludeGeos = EntityUtil.filterByAnd(productGeos, UtilMisc.toMap("productGeoEnumId", productGeoPrefix + "EXCLUDE"));
            List<GenericValue> includeGeos = EntityUtil.filterByAnd(productGeos, UtilMisc.toMap("productGeoEnumId", productGeoPrefix + "INCLUDE"));
            if (UtilValidate.isEmpty(excludeGeos) && UtilValidate.isEmpty(includeGeos)) {
                // If no GEOs are configured the default is TRUE
                return true;
            }
            // exclusion
            for (GenericValue productGeo: excludeGeos) {
                List<GenericValue> excludeGeoGroup = GeoWorker.expandGeoGroup(productGeo.getString("geoId"), delegator);
                if (GeoWorker.containsGeo(excludeGeoGroup, postalAddress.getString("countryGeoId"), delegator) ||
                      GeoWorker.containsGeo(excludeGeoGroup, postalAddress.getString("stateProvinceGeoId"), delegator) ||
                      GeoWorker.containsGeo(excludeGeoGroup, postalAddress.getString("postalCodeGeoId"), delegator)) {
                    return false;
                }
            }
            if (UtilValidate.isEmpty(includeGeos)) {
                // If no GEOs are configured the default is TRUE
                return true;
            }
            // inclusion
            for (GenericValue productGeo: includeGeos) {
                List<GenericValue> includeGeoGroup = GeoWorker.expandGeoGroup(productGeo.getString("geoId"), delegator);
                if (GeoWorker.containsGeo(includeGeoGroup, postalAddress.getString("countryGeoId"), delegator) ||
                      GeoWorker.containsGeo(includeGeoGroup, postalAddress.getString("stateProvinceGeoId"), delegator) ||
                      GeoWorker.containsGeo(includeGeoGroup, postalAddress.getString("postalCodeGeoId"), delegator)) {
                    return true;
                }
            }

        } else {
            throw new IllegalArgumentException("product and postalAddress cannot be null.");
        }
        return false;
    }
    public static boolean isSerialized (Delegator delegator, String productId) {
        try {
            GenericValue product = EntityQuery.use(delegator).from("Product").where("productId", productId).cache().queryOne();
            if (product != null) {
                return "SERIALIZED_INV_ITEM".equals(product.getString("inventoryItemTypeId"));
            }
        } catch (GenericEntityException e) {
            Debug.logWarning(e.getMessage(), module);
        }
        return false;
    }

    public static boolean taxApplies(GenericValue product) {
        String errMsg = "";
        if (product != null) {
            Boolean taxable = product.getBoolean("taxable");

            if (taxable == null) {
                return true;
            }
            return taxable;
        }
        throw new IllegalArgumentException(errMsg);
    }

    public static String getInstanceAggregatedId(Delegator delegator, String instanceProductId) throws GenericEntityException {
        GenericValue instanceProduct = EntityQuery.use(delegator).from("Product").where("productId", instanceProductId).queryOne();

        if (instanceProduct != null && EntityTypeUtil.hasParentType(delegator, "ProductType", "productTypeId", instanceProduct.getString("productTypeId"), "parentTypeId", "AGGREGATED")) {
            GenericValue productAssoc = EntityUtil.getFirst(EntityUtil.filterByDate(instanceProduct.getRelated("AssocProductAssoc", UtilMisc.toMap("productAssocTypeId", "PRODUCT_CONF"), null, false)));
            if (productAssoc != null) {
                return productAssoc.getString("productId");
            }
        }
        return null;
    }

    public static String getAggregatedInstanceId(Delegator delegator, String  aggregatedProductId, String configId) throws GenericEntityException {
        List<GenericValue> productAssocs = getAggregatedAssocs(delegator, aggregatedProductId);
        if (UtilValidate.isNotEmpty(productAssocs) && UtilValidate.isNotEmpty(configId)) {
            for (GenericValue productAssoc: productAssocs) {
                GenericValue product = productAssoc.getRelatedOne("AssocProduct", false);
                if (configId.equals(product.getString("configId"))) {
                    return productAssoc.getString("productIdTo");
                }
            }
        }
        return null;
    }

    public static List<GenericValue> getAggregatedAssocs(Delegator delegator, String  aggregatedProductId) throws GenericEntityException {
        GenericValue aggregatedProduct = EntityQuery.use(delegator).from("Product").where("productId", aggregatedProductId).queryOne();

        if (aggregatedProduct != null && ("AGGREGATED".equals(aggregatedProduct.getString("productTypeId")) || "AGGREGATED_SERVICE".equals(aggregatedProduct.getString("productTypeId")))) {
            List<GenericValue> productAssocs = EntityUtil.filterByDate(aggregatedProduct.getRelated("MainProductAssoc", UtilMisc.toMap("productAssocTypeId", "PRODUCT_CONF"), null, false));
            return productAssocs;
        }
        return null;
    }

    /**
     * Get variant product's parent virtual product's id.
     * SCIPIO: added useCache flag (2017-12-19).
     */
    public static String getVariantVirtualId(GenericValue variantProduct, boolean useCache) throws GenericEntityException {
        List<GenericValue> productAssocs = getVariantVirtualAssocs(variantProduct, useCache); // SCIPIO: useCache
        if (productAssocs == null) {
            return null;
        }
        GenericValue productAssoc = EntityUtil.getFirst(productAssocs);
        if (productAssoc != null) {
            return productAssoc.getString("productId");
        }
        return null;
    }

    /**
     * Get variant product's parent virtual product's id, with caching enabled.
     * SCIPIO: now delegating (2017-12-19).
     */
    public static String getVariantVirtualId(GenericValue variantProduct) throws GenericEntityException {
        return getVariantVirtualId(variantProduct, true);
    }

    // SCIPIO: 2017-09-14: now support useCache
    public static List<GenericValue> getVariantVirtualAssocs(GenericValue variantProduct, boolean useCache) throws GenericEntityException {
        if (variantProduct != null && "Y".equals(variantProduct.getString("isVariant"))) {
            List<GenericValue> productAssocs = EntityUtil.filterByDate(variantProduct.getRelated("AssocProductAssoc", UtilMisc.toMap("productAssocTypeId", "PRODUCT_VARIANT"), null, useCache));
            return productAssocs;
        }
        return null;
    }

    public static List<GenericValue> getVariantVirtualAssocs(GenericValue variantProduct) throws GenericEntityException {
        return getVariantVirtualAssocs(variantProduct, true);
    }

    /**
     * invokes the getInventoryAvailableByFacility service, returns true if specified quantity is available, else false
     * this is only used in the related method that uses a ProductConfigWrapper, until that is refactored into a service as well...
     */
    private static boolean isProductInventoryAvailableByFacility(String productId, String inventoryFacilityId, BigDecimal quantity, LocalDispatcher dispatcher) {
        BigDecimal availableToPromise = null;

        try {
            Map<String, Object> result = dispatcher.runSync("getInventoryAvailableByFacility",
                                            UtilMisc.toMap("productId", productId, "facilityId", inventoryFacilityId));

            availableToPromise = (BigDecimal) result.get("availableToPromiseTotal");

            if (availableToPromise == null) {
                Debug.logWarning("The getInventoryAvailableByFacility service returned a null availableToPromise, the error message was:\n" + result.get(ModelService.ERROR_MESSAGE), module);
                return false;
            }
        } catch (GenericServiceException e) {
            Debug.logWarning(e, "Error invoking getInventoryAvailableByFacility service in isCatalogInventoryAvailable", module);
            return false;
        }

        // check to see if we got enough back...
        if (availableToPromise.compareTo(quantity) >= 0) {
            if (Debug.infoOn()) {
                Debug.logInfo("Inventory IS available in facility with id " + inventoryFacilityId + " for product id " + productId + "; desired quantity is " + quantity + ", available quantity is " + availableToPromise, module);
            }
            return true;
        }
        if (Debug.infoOn()) {
            Debug.logInfo("Returning false because there is insufficient inventory available in facility with id "
                    + inventoryFacilityId + " for product id " + productId + "; desired quantity is " + quantity
                    + ", available quantity is " + availableToPromise, module);
        }
        return false;
    }

    /**
     * Invokes the getInventoryAvailableByFacility service, returns true if specified quantity is available for all the selected parts, else false.
     * Also, set the available flag for all the product configuration's options.
     **/
    public static boolean isProductInventoryAvailableByFacility(ProductConfigWrapper productConfig, String inventoryFacilityId, BigDecimal quantity, LocalDispatcher dispatcher) {
        boolean available = true;
        List<ConfigOption> options = productConfig.getSelectedOptions();
        for (ConfigOption ci: options) {
            List<GenericValue> products = ci.getComponents();
            for (GenericValue product: products) {
                String productId = product.getString("productId");
                BigDecimal cmpQuantity = product.getBigDecimal("quantity");
                BigDecimal neededQty = BigDecimal.ZERO;
                if (cmpQuantity != null) {
                    neededQty = quantity.multiply(cmpQuantity);
                }
                if (!isProductInventoryAvailableByFacility(productId, inventoryFacilityId, neededQty, dispatcher)) {
                    ci.setAvailable(false);
                }
            }
            if (!ci.isAvailable()) {
                available = false;
            }
        }
        return available;
    }

    /**
     * Gets ProductFeature GenericValue for all distinguishing features of a variant product.
     * Distinguishing means all features that are selectable on the corresponding virtual product and standard on the variant plus all DISTINGUISHING_FEAT assoc type features on the variant.
     */
    public static Set<GenericValue> getVariantDistinguishingFeatures(GenericValue variantProduct) throws GenericEntityException {
        if (variantProduct == null) {
            return new HashSet<>();
        }
        if (!"Y".equals(variantProduct.getString("isVariant"))) {
            throw new IllegalArgumentException("Cannot get distinguishing features for a product that is not a variant (ie isVariant!=Y).");
        }
        Delegator delegator = variantProduct.getDelegator();
        String virtualProductId = getVariantVirtualId(variantProduct);

        // find all selectable features on the virtual product that are also standard features on the variant
        Set<GenericValue> distFeatures = new HashSet<>();

        List<GenericValue> variantDistinguishingFeatures = EntityQuery.use(delegator).from("ProductFeatureAndAppl").where("productId", variantProduct.get("productId"), "productFeatureApplTypeId", "DISTINGUISHING_FEAT").cache(true).queryList();

        for (GenericValue variantDistinguishingFeature: EntityUtil.filterByDate(variantDistinguishingFeatures)) {
            GenericValue dummyFeature = delegator.makeValue("ProductFeature");
            dummyFeature.setAllFields(variantDistinguishingFeature, true, null, null);
            distFeatures.add(dummyFeature);
        }

        List<GenericValue> virtualSelectableFeatures = EntityQuery.use(delegator).from("ProductFeatureAndAppl").where("productId", virtualProductId, "productFeatureApplTypeId", "SELECTABLE_FEATURE").cache(true).queryList();

        Set<String> virtualSelectableFeatureIds = new HashSet<>();
        for (GenericValue virtualSelectableFeature: EntityUtil.filterByDate(virtualSelectableFeatures)) {
            virtualSelectableFeatureIds.add(virtualSelectableFeature.getString("productFeatureId"));
        }

        List<GenericValue> variantStandardFeatures = EntityQuery.use(delegator).from("ProductFeatureAndAppl").where("productId", variantProduct.get("productId"), "productFeatureApplTypeId", "STANDARD_FEATURE").cache(true).queryList();

        for (GenericValue variantStandardFeature: EntityUtil.filterByDate(variantStandardFeatures)) {
            if (virtualSelectableFeatureIds.contains(variantStandardFeature.get("productFeatureId"))) {
                GenericValue dummyFeature = delegator.makeValue("ProductFeature");
                dummyFeature.setAllFields(variantStandardFeature, true, null, null);
                distFeatures.add(dummyFeature);
            }
        }

        return distFeatures;
    }

    /**
     *  Get the name to show to the customer for GWP alternative options.
     *  If the alternative is a variant, find the distinguishing features and show those instead of the name; if it is not a variant then show the PRODUCT_NAME content.
     */
    public static String getGwpAlternativeOptionName(LocalDispatcher dispatcher, Delegator delegator, String alternativeOptionProductId, Locale locale) {
        try {
            GenericValue alternativeOptionProduct = EntityQuery.use(delegator).from("Product").where("productId", alternativeOptionProductId).cache().queryOne();
            if (alternativeOptionProduct != null) {
                if ("Y".equals(alternativeOptionProduct.getString("isVariant"))) {
                    Set<GenericValue> distFeatures = getVariantDistinguishingFeatures(alternativeOptionProduct);
                    if (UtilValidate.isNotEmpty(distFeatures)) {
                        StringBuilder nameBuf = new StringBuilder();
                        for (GenericValue productFeature: distFeatures) {
                            if (nameBuf.length() > 0) {
                                nameBuf.append(", ");
                            }
                            GenericValue productFeatureType = productFeature.getRelatedOne("ProductFeatureType", true);
                            if (productFeatureType != null) {
                                nameBuf.append(productFeatureType.get("description", locale));
                                nameBuf.append(":");
                            }
                            nameBuf.append(productFeature.get("description", locale));
                        }
                        return nameBuf.toString();
                    }
                }

                // got to here, default to PRODUCT_NAME
                // SCIPIO: Do NOT HTML-escape this here
                String alternativeProductName = ProductContentWrapper.getProductContentAsText(alternativeOptionProduct, "PRODUCT_NAME", locale, dispatcher, "raw");
                return alternativeProductName;
            }
        } catch (GenericEntityException e) {
            Debug.logError(e, module);
        } catch (Exception e) { // SCIPIO: 2018-10-09: Kept Exception here for now
            Debug.logError(e, module);
        }
        // finally fall back to the ID in square braces
        return "[" + alternativeOptionProductId + "]";
    }

    /**
     * gets productFeatures given a productFeatureApplTypeId
     * @param delegator
     * @param productId
     * @param productFeatureApplTypeId - if null, returns ALL productFeatures, regardless of applType
     * @return List
     */
    public static List<GenericValue> getProductFeaturesByApplTypeId(Delegator delegator, String productId, String productFeatureApplTypeId) {
        if (productId == null) {
            return null;
        }
        try {
            return getProductFeaturesByApplTypeId(EntityQuery.use(delegator).from("Product").where("productId", productId).queryOne(),
                    productFeatureApplTypeId);
        } catch (GenericEntityException e) {
            Debug.logError(e, module);
        }
        return null;
    }

    public static List<GenericValue> getProductFeaturesByApplTypeId(GenericValue product, String productFeatureApplTypeId) {
        if (product == null) {
            return null;
        }
        List<GenericValue> features = null;
        try {
            List<GenericValue> productAppls;
            List<EntityCondition> condList = UtilMisc.toList(
                    EntityCondition.makeCondition("productId", product.getString("productId")),
                    EntityUtil.getFilterByDateExpr()
            );
            if (productFeatureApplTypeId != null) {
                condList.add(EntityCondition.makeCondition("productFeatureApplTypeId", productFeatureApplTypeId));
            }
            EntityCondition cond = EntityCondition.makeCondition(condList);
            productAppls = product.getDelegator().findList("ProductFeatureAppl", cond, null, null, null, false);
            features = EntityUtil.getRelated("ProductFeature", null, productAppls, false);
            features = EntityUtil.orderBy(features, UtilMisc.toList("description"));
        } catch (GenericEntityException e) {
            Debug.logError(e, module);
            features = new LinkedList<>();
        }
        return features;
    }

    public static String getProductVirtualVariantMethod(Delegator delegator, String productId) {
        GenericValue product = null;
        try {
            product = EntityQuery.use(delegator).from("Product").where("productId", productId).cache().queryOne();
        } catch (GenericEntityException e) {
            Debug.logError(e, module);
        }

        if (product != null) {
            return product.getString("virtualVariantMethodEnum");
        }
        return null;
    }

    /**
     * @deprecated SCIPIO: Replaced by {@link #getProductFeatures}
     * @param product
     * @return list featureType and related featuresIds, description and feature price for this product ordered by type and sequence
     */
    @Deprecated
    public static List<List<Map<String,String>>> getSelectableProductFeaturesByTypesAndSeq(GenericValue product) {
        if (product == null) {
            return null;
        }
        List <List<Map<String,String>>> featureTypeFeatures = new LinkedList<>();
        try {
            Delegator delegator = product.getDelegator();
            List<GenericValue> featuresSorted = EntityQuery.use(delegator)
                                                    .from("ProductFeatureAndAppl")
                                                    .where("productId", product.getString("productId"), "productFeatureApplTypeId", "SELECTABLE_FEATURE")
                                                    .orderBy("productFeatureTypeId", "sequenceNum")
                                                    .cache(true)
                                                    .queryList();
            String oldType = null;
            List<Map<String,String>> featureList = new LinkedList<>();
            for (GenericValue productFeatureAppl: featuresSorted) {
                if (oldType == null || !oldType.equals(productFeatureAppl.getString("productFeatureTypeId"))) {
                    // use first entry for type and description
                    if (oldType != null) {
                        featureTypeFeatures.add(featureList);
                        featureList = new LinkedList<>();
                    }
                    GenericValue productFeatureType = EntityQuery.use(delegator).from("ProductFeatureType").where("productFeatureTypeId", productFeatureAppl.getString("productFeatureTypeId")).queryOne();
                    featureList.add(UtilMisc.<String, String>toMap("productFeatureTypeId", productFeatureAppl.getString("productFeatureTypeId"),
                            "description", productFeatureType.getString("description")));
                    oldType = productFeatureAppl.getString("productFeatureTypeId");
                }
                // fill other entries with featureId, description and default price and currency
                Map<String,String> featureData = UtilMisc.toMap("productFeatureId", productFeatureAppl.getString("productFeatureId"));
                if (UtilValidate.isNotEmpty(productFeatureAppl.get("description"))) {
                    featureData.put("description", productFeatureAppl.getString("description"));
                } else {
                    featureData.put("description", productFeatureAppl.getString("productFeatureId"));
                }
                List<GenericValue> productFeaturePrices = EntityQuery.use(delegator).from("ProductFeaturePrice")
                        .where("productFeatureId", productFeatureAppl.getString("productFeatureId"), "productPriceTypeId", "DEFAULT_PRICE")
                        .filterByDate()
                        .queryList();
                if (UtilValidate.isNotEmpty(productFeaturePrices)) {
                    GenericValue productFeaturePrice = productFeaturePrices.get(0);
                    if (UtilValidate.isNotEmpty(productFeaturePrice.get("price"))) {
                        featureData.put("price", productFeaturePrice.getBigDecimal("price").toString());
                        featureData.put("currencyUomId", productFeaturePrice.getString("currencyUomId"));
                    }
                }
                featureList.add(featureData);
            }
            if (oldType != null) {
                // last map
                featureTypeFeatures.add(featureList);
            }
        } catch (GenericEntityException e) {
            Debug.logError(e, module);
        }
        return featureTypeFeatures;
    }


    public static List<Map<String, Object>> getProductFeatures(GenericValue product) {
        return getProductFeatures(product, Locale.getDefault());
    }

    /**
     * SCIPIO: Returns a list of Product features by Type and sequence Id. Replaces getSelectableProductFeaturesByTypesAndSeq
     * NOTE: 2018-09-06: The return type has been corrected.
     * @return list featureType and related featuresIds, description and feature price for this product ordered by type and sequence
     * */
    public static List<Map<String, Object>> getProductFeatures(GenericValue product, Locale locale) {
        if (product == null) {
            return null;
        }
        List<Map<String, Object>> featureTypeFeatures = new ArrayList<>();
        try {
            Delegator delegator = product.getDelegator();
            List<GenericValue> featuresSorted = EntityQuery.use(delegator)
                    .from("ProductFeatureAndAppl")
                    .where("productId", product.getString("productId"), "productFeatureApplTypeId", "SELECTABLE_FEATURE")
                    .orderBy("productFeatureTypeId", "sequenceNum")
                    .cache(true)
                    .queryList();
            for(GenericValue productFeatureAppl: featuresSorted) {
                Map<String, Object> featureType = null;
                // Map to previous featureType if exists
                for(Map<String, Object> ftype : featureTypeFeatures) {
                    String productFeatureTypeId = ftype.get("productFeatureTypeId") != null ? (String) ftype.get("productFeatureTypeId") :"";
                    if(productFeatureTypeId.equals(productFeatureAppl.getString("productFeatureTypeId")))
                    featureType = ftype;
                }
                // otherwise create a new featureType
                if (featureType == null) {
                    featureType = new HashMap<>();
                    GenericValue productFeatureType = EntityQuery.use(delegator).from("ProductFeatureType").where("productFeatureTypeId", productFeatureAppl.getString("productFeatureTypeId")).queryOne();
                    featureType.put("description",productFeatureType.get("description", locale));
                    featureType.put("productFeatureTypeId", productFeatureAppl.get("productFeatureTypeId", locale));
                    featureType.put("features", new ArrayList<Map<String, String>>());
                    featureTypeFeatures.add(featureType);
                }
                @SuppressWarnings("unchecked")
                List<Map<String, String>> features = (List<Map<String, String>>) featureType.get("features");

                // Add Product features
                Map<String, String> featureData = UtilMisc.toMap("productFeatureId", productFeatureAppl.getString("productFeatureId"));
                if (UtilValidate.isNotEmpty(productFeatureAppl.get("description"))) {
                    featureData.put("description", productFeatureAppl.getString("description"));
                } else {
                    featureData.put("description", productFeatureAppl.getString("productFeatureId"));
                }
                List<GenericValue> productFeaturePrices = EntityQuery.use(delegator).from("ProductFeaturePrice")
                        .where("productFeatureId", productFeatureAppl.getString("productFeatureId"), "productPriceTypeId", "DEFAULT_PRICE")
                        .filterByDate()
                        .queryList();
                if (UtilValidate.isNotEmpty(productFeaturePrices)) {
                    GenericValue productFeaturePrice = productFeaturePrices.get(0);
                    if (UtilValidate.isNotEmpty(productFeaturePrice.get("price"))) {
                        featureData.put("price", productFeaturePrice.getBigDecimal("price").toString());
                        featureData.put("currencyUomId", productFeaturePrice.getString("currencyUomId"));
                    }
                }
                features.add(featureData);
            }
        } catch (GenericEntityException e) {
            Debug.logError(e, module);
        }
        return featureTypeFeatures;
    }


    /**
     * For a given variant product, returns the list of features that would qualify it for
     * selection from the virtual product
     * @param variantProduct - the variant from which to derive the selection features
     * @return a List of ProductFeature GenericValues
     */
    public static List<GenericValue> getVariantSelectionFeatures(GenericValue variantProduct) {
        if (!"Y".equals(variantProduct.getString("isVariant"))) {
            return null;
        }
        GenericValue virtualProduct = ProductWorker.getParentProduct(variantProduct.getString("productId"), variantProduct.getDelegator());
        if (virtualProduct == null || !"Y".equals(virtualProduct.getString("productId"))) {
            return null;
        }
        // The selectable features from the virtual product
        List<GenericValue> selectableFeatures = ProductWorker.getProductFeaturesByApplTypeId(virtualProduct, "SELECTABLE_FEATURE");
        // A list of distinct ProductFeatureTypes derived from the selectable features
        List<String> selectableTypes = EntityUtil.getFieldListFromEntityList(selectableFeatures, "productFeatureTypeId", true);
        // The standard features from the variant product
        List<GenericValue> standardFeatures = ProductWorker.getProductFeaturesByApplTypeId(variantProduct, "STANDARD_FEATURE");
        List<GenericValue> result = new LinkedList<>();
        for (GenericValue standardFeature : standardFeatures) {
            // For each standard variant feature check it is also a virtual selectable feature and
            // if a feature of the same type hasn't already been added to the list
            if (selectableTypes.contains(standardFeature.getString("productFeatureTypeId")) && selectableFeatures.contains(standardFeature)) {
                result.add(standardFeature);
                selectableTypes.remove(standardFeature.getString("productFeatureTypeId"));
            }
        }
        return result;
    }

    public static Map<String, List<GenericValue>> getOptionalProductFeatures(Delegator delegator, String productId) {
        Map<String, List<GenericValue>> featureMap = new LinkedHashMap<>();

        List<GenericValue> productFeatureAppls = null;
        try {
            productFeatureAppls = EntityQuery.use(delegator).from("ProductFeatureAndAppl").where("productId", productId, "productFeatureApplTypeId", "OPTIONAL_FEATURE").orderBy("productFeatureTypeId", "sequenceNum").queryList();
        } catch (GenericEntityException e) {
            Debug.logError(e, module);
        }

        if (productFeatureAppls != null) {
            for (GenericValue appl: productFeatureAppls) {
                String featureType = appl.getString("productFeatureTypeId");
                List<GenericValue> features = featureMap.get(featureType);
                if (features == null) {
                    features = new LinkedList<>();
                }
                features.add(appl);
                featureMap.put(featureType, features);
            }
        }

        return featureMap;
    }

    // product calc methods

    public static BigDecimal calcOrderAdjustments(List<GenericValue> orderHeaderAdjustments, BigDecimal subTotal, boolean includeOther, boolean includeTax, boolean includeShipping) {
        BigDecimal adjTotal = BigDecimal.ZERO;

        if (UtilValidate.isNotEmpty(orderHeaderAdjustments)) {
            List<GenericValue> filteredAdjs = filterOrderAdjustments(orderHeaderAdjustments, includeOther, includeTax, includeShipping, false, false);
            for (GenericValue orderAdjustment: filteredAdjs) {
                adjTotal = adjTotal.add(calcOrderAdjustment(orderAdjustment, subTotal));
            }
        }
        return adjTotal;
    }

    public static BigDecimal calcOrderAdjustment(GenericValue orderAdjustment, BigDecimal orderSubTotal) {
        BigDecimal adjustment = BigDecimal.ZERO;

        if (orderAdjustment.get("amount") != null) {
            adjustment = adjustment.add(orderAdjustment.getBigDecimal("amount"));
        }
        else if (orderAdjustment.get("sourcePercentage") != null) {
            adjustment = adjustment.add(orderAdjustment.getBigDecimal("sourcePercentage").multiply(orderSubTotal));
        }
        return adjustment;
    }

    public static List<GenericValue> filterOrderAdjustments(List<GenericValue> adjustments, boolean includeOther, boolean includeTax, boolean includeShipping, boolean forTax, boolean forShipping) {
        List<GenericValue> newOrderAdjustmentsList = new LinkedList<>();

        if (UtilValidate.isNotEmpty(adjustments)) {
            for (GenericValue orderAdjustment: adjustments) {
                boolean includeAdjustment = false;

                if ("SALES_TAX".equals(orderAdjustment.getString("orderAdjustmentTypeId"))) {
                    if (includeTax) {
                        includeAdjustment = true;
                    }
                } else if ("SHIPPING_CHARGES".equals(orderAdjustment.getString("orderAdjustmentTypeId"))) {
                    if (includeShipping) {
                        includeAdjustment = true;
                    }
                } else {
                    if (includeOther) {
                        includeAdjustment = true;
                    }
                }

                // default to yes, include for shipping; so only exclude if includeInShipping is N, or false; if Y or null or anything else it will be included
                if (forTax && "N".equals(orderAdjustment.getString("includeInTax"))) {
                    includeAdjustment = false;
                }

                // default to yes, include for shipping; so only exclude if includeInShipping is N, or false; if Y or null or anything else it will be included
                if (forShipping && "N".equals(orderAdjustment.getString("includeInShipping"))) {
                    includeAdjustment = false;
                }

                if (includeAdjustment) {
                    newOrderAdjustmentsList.add(orderAdjustment);
                }
            }
        }
        return newOrderAdjustmentsList;
    }

    public static BigDecimal getAverageProductRating(Delegator delegator, String productId) {
        return getAverageProductRating(delegator, productId, null);
    }

    public static BigDecimal getAverageProductRating(Delegator delegator, String productId, String productStoreId) {
        GenericValue product = null;
        try {
            product = EntityQuery.use(delegator).from("Product").where("productId", productId).cache().queryOne();
        } catch (GenericEntityException e) {
            Debug.logError(e, module);
        }
        return ProductWorker.getAverageProductRating(product, productStoreId);
    }

    public static BigDecimal getAverageProductRating(GenericValue product, String productStoreId) {
        return getAverageProductRating(product, null, productStoreId);
    }

    public static BigDecimal getAverageProductRating(GenericValue product, List<GenericValue> reviews, String productStoreId) {
        if (product == null) {
            Debug.logWarning("Invalid product entity passed; unable to obtain valid product rating", module);
            return BigDecimal.ZERO;
        }

        BigDecimal productRating = BigDecimal.ZERO;
        BigDecimal productEntityRating = product.getBigDecimal("productRating");
        String entityFieldType = product.getString("ratingTypeEnum");

        // null check
        if (productEntityRating == null) {
            productEntityRating = BigDecimal.ZERO;
        }
        if (entityFieldType == null) {
            entityFieldType = "";
        }

        if ("PRDR_FLAT".equals(entityFieldType)) {
            productRating = productEntityRating;
        } else {
            // get the product rating from the ProductReview entity; limit by product store if ID is passed
            Map<String, String> reviewByAnd = UtilMisc.toMap("statusId", "PRR_APPROVED");
            if (productStoreId != null) {
                reviewByAnd.put("productStoreId", productStoreId);
            }

            // lookup the reviews if we didn't pass them in
            if (reviews == null) {
                try {
                    reviews = product.getRelated("ProductReview", reviewByAnd, UtilMisc.toList("-postedDateTime"), true);
                } catch (GenericEntityException e) {
                    Debug.logError(e, module);
                }
            }

            // tally the average
            BigDecimal ratingTally = BigDecimal.ZERO;
            BigDecimal numRatings = BigDecimal.ZERO;
            if (reviews != null) {
                for (GenericValue productReview: reviews) {
                    BigDecimal rating = productReview.getBigDecimal("productRating");
                    if (rating != null) {
                        ratingTally = ratingTally.add(rating);
                        numRatings = numRatings.add(BigDecimal.ONE);
                    }
                }
            }
            if (ratingTally.compareTo(BigDecimal.ZERO) > 0 && numRatings.compareTo(BigDecimal.ZERO) > 0) {
                productRating = ratingTally.divide(numRatings, generalRounding);
            }

            if ("PRDR_MIN".equals(entityFieldType)) {
                // check for min
                if (productEntityRating.compareTo(productRating) > 0) {
                    productRating = productEntityRating;
                }
            } else if ("PRDR_MAX".equals(entityFieldType)) {
                // check for max
                if (productRating.compareTo(productEntityRating) > 0) {
                    productRating = productEntityRating;
                }
            }
        }

        return productRating;
    }

    public static List<GenericValue> getCurrentProductCategories(Delegator delegator, String productId) {
        GenericValue product = null;
        try {
            product = EntityQuery.use(delegator).from("Product").where("productId", productId).queryOne();
        } catch (GenericEntityException e) {
            Debug.logError(e, module);
        }
        return getCurrentProductCategories(product);
    }

    public static List<GenericValue> getCurrentProductCategories(GenericValue product) {
        if (product == null) {
            return null;
        }
        List<GenericValue> categories = new LinkedList<>();
        try {
            List<GenericValue> categoryMembers = product.getRelated("ProductCategoryMember", null, null, false);
            categoryMembers = EntityUtil.filterByDate(categoryMembers);
            categories = EntityUtil.getRelated("ProductCategory", null, categoryMembers, false);
        } catch (GenericEntityException e) {
            Debug.logError(e, module);
        }
        return categories;
    }

    /**
     * SCIPIO: Gets the product's parent (virtual or other association) ProductAssoc to itself.
     * Factored out from {@link #getParentProduct}.
     * Added 2017-09-12.
     */
    public static GenericValue getParentProductAssoc(String productId, Delegator delegator, boolean useCache) { // SCIPIO: added useCache 2017-09-05
        if (productId == null) {
            Debug.logWarning("Bad product id", module);
        }

        try {
            List<GenericValue> virtualProductAssocs = EntityQuery.use(delegator).from("ProductAssoc")
                    .where("productIdTo", productId, "productAssocTypeId", "PRODUCT_VARIANT")
                    .orderBy("-fromDate")
                    .cache(useCache)
                    .filterByDate()
                    .queryList();
            if (UtilValidate.isEmpty(virtualProductAssocs)) {
                //okay, not a variant, try a UNIQUE_ITEM
                virtualProductAssocs = EntityQuery.use(delegator).from("ProductAssoc")
                        .where("productIdTo", productId, "productAssocTypeId", "UNIQUE_ITEM")
                        .orderBy("-fromDate")
                        .cache(useCache)
                        .filterByDate()
                        .queryList();
            }
            if (UtilValidate.isNotEmpty(virtualProductAssocs)) {
                //found one, set this first as the parent product
                return EntityUtil.getFirst(virtualProductAssocs);
            }
        } catch (GenericEntityException e) {
            throw new RuntimeException("Entity Engine error getting Parent Product (" + e.getMessage() + ")");
        }
        return null;
    }

    //get parent product
    public static GenericValue getParentProduct(String productId, Delegator delegator, boolean useCache) { // SCIPIO: added useCache 2017-09-05
        // SCIPIO: 2017-09-12: factored out into getParentProductAssoc
        GenericValue _parentProduct = null;
        try {
            GenericValue productAssoc = getParentProductAssoc(productId, delegator, useCache);
            if (productAssoc != null) {
                _parentProduct = productAssoc.getRelatedOne("MainProduct", useCache);
            }
        } catch (GenericEntityException e) {
            throw new RuntimeException("Entity Engine error getting Parent Product (" + e.getMessage() + ")");
        }
        return _parentProduct;
    }

    public static GenericValue getParentProduct(String productId, Delegator delegator) {
        // SCIPIO: 2017-09-05: now delegates
        return getParentProduct(productId, delegator, true);
    }

    /**
     * SCIPIO: Gets the parent product ID (only).
     */
    public static String getParentProductId(String productId, Delegator delegator, boolean useCache) {
        String parentProductId = null;
        //try {
        GenericValue productAssoc = getParentProductAssoc(productId, delegator, useCache);
        if (productAssoc != null) {
            parentProductId = productAssoc.getString("productId");
        }
        //} catch (GenericEntityException e) {
        //    throw new RuntimeException("Entity Engine error getting Parent Product (" + e.getMessage() + ")");
        //}
        return parentProductId;
    }

    public static boolean isDigital(GenericValue product) {
        boolean isDigital = false;
        if (product != null) {
            GenericValue productType = null;
            try {
                productType = product.getRelatedOne("ProductType", true);
            } catch (GenericEntityException e) {
                Debug.logWarning(e.getMessage(), module);
            }
            String isDigitalValue = (productType != null? productType.getString("isDigital"): null);
            isDigital = isDigitalValue != null && "Y".equalsIgnoreCase(isDigitalValue);
        }
        return isDigital;
    }

    public static boolean isPhysical(GenericValue product) {
        boolean isPhysical = false;
        if (product != null) {
            GenericValue productType = null;
            try {
                productType = product.getRelatedOne("ProductType", true);
            } catch (GenericEntityException e) {
                Debug.logWarning(e.getMessage(), module);
            }
            String isPhysicalValue = (productType != null? productType.getString("isPhysical"): null);
            isPhysical = isPhysicalValue != null && "Y".equalsIgnoreCase(isPhysicalValue);
        }
        return isPhysical;
    }

    public static boolean isVirtual(Delegator delegator, String productI) {
        try {
            GenericValue product = EntityQuery.use(delegator).from("Product").where("productId", productI).cache().queryOne();
            if (product != null) {
                return "Y".equals(product.getString("isVirtual"));
            }
        } catch (GenericEntityException e) {
            Debug.logWarning(e.getMessage(), module);
        }

        return false;
    }

    /**
     * SCIPIO: Checks if isVariant is set on the product (analogous to {@link #isVirtual}).
     * Added 2017-08-17.
     */
    public static boolean isVariant(Delegator delegator, String productId) {
        try {
            GenericValue product = EntityQuery.use(delegator).from("Product").where("productId", productId).cache().queryOne();
            if (product != null) {
                return "Y".equals(product.getString("isVariant"));
            }
        } catch (GenericEntityException e) {
            Debug.logWarning(e.getMessage(), module);
        }

        return false;
    }

    public static boolean isAmountRequired(Delegator delegator, String productI) {
        try {
            GenericValue product = EntityQuery.use(delegator).from("Product").where("productId", productI).cache().queryOne();
            if (product != null) {
                return "Y".equals(product.getString("requireAmount"));
            }
        } catch (GenericEntityException e) {
            Debug.logWarning(e.getMessage(), module);
        }

        return false;
    }

    public static String getProductTypeId(Delegator delegator, String productId) {
        try {
            GenericValue product = EntityQuery.use(delegator).from("Product").where("productId", productId).cache().queryOne();
            if (product != null) {
                return product.getString("productTypeId");
            }
        } catch (GenericEntityException e) {
            Debug.logWarning(e.getMessage(), module);
        }

        return null;
    }

    /*
     * Returns the product's unit weight converted to the desired Uom.  If the weight is null,
     * then a check is made for an associated virtual product to retrieve the weight from.  If the
     * weight is still null then null is returned.  If a weight is found and a desiredUomId has
     * been supplied and the product specifies a weightUomId then an attempt will be made to
     * convert the value otherwise the weight is returned as is.
     */
    public static BigDecimal getProductWeight(GenericValue product, String desiredUomId, Delegator delegator, LocalDispatcher dispatcher) {
        BigDecimal weight = product.getBigDecimal("weight");
        String weightUomId = product.getString("weightUomId");

        if (weight == null) {
            GenericValue parentProduct = getParentProduct(product.getString("productId"), delegator);
            if (parentProduct != null) {
                weight = parentProduct.getBigDecimal("weight");
                weightUomId = parentProduct.getString("weightUomId");
            }
        }

        if (weight == null) {
            return null;
        }
        // attempt a conversion if necessary
        if (desiredUomId != null && product.get("weightUomId") != null && !desiredUomId.equals(product.get("weightUomId"))) {
            Map<String, Object> result = new HashMap<>();
            try {
                result = dispatcher.runSync("convertUom", UtilMisc.<String, Object>toMap("uomId", weightUomId, "uomIdTo", desiredUomId, "originalValue", weight));
            } catch (GenericServiceException e) {
                Debug.logError(e, module);
            }

            if (result.get(ModelService.RESPONSE_MESSAGE).equals(ModelService.RESPOND_SUCCESS) && result.get("convertedValue") != null) {
                weight = (BigDecimal) result.get("convertedValue");
            } else {
                Debug.logError("Unsupported conversion from [" + weightUomId + "] to [" + desiredUomId + "]", module);
                return null;
            }
        }
        return weight;
    }



    /**
     * Generic service to find product by id.
     * By default return the product find by productId
     * but you can pass searchProductFirst at false if you want search in goodIdentification before
     * or pass searchAllId at true to find all product with this id (product.productId and goodIdentification.idValue)
     * @param delegator the delegator
     * @param idToFind the product id to find
     * @param goodIdentificationTypeId the good identification type id to use
     * @param searchProductFirst search first by product id
     * @param searchAllId search all product ids
     * @return return the list of products founds
     * @throws GenericEntityException
     */
    public static List<GenericValue> findProductsById(Delegator delegator,
            String idToFind, String goodIdentificationTypeId,
            boolean searchProductFirst, boolean searchAllId) throws GenericEntityException {

        if (Debug.verboseOn()) {
            Debug.logVerbose("Analyze goodIdentification: entered id = " + idToFind + ", goodIdentificationTypeId = " + goodIdentificationTypeId, module);
        }

        GenericValue product = null;
        List<GenericValue> productsFound = null;

        // 1) look if the idToFind given is a real productId
        if (searchProductFirst) {
            product = EntityQuery.use(delegator).from("Product").where("productId", idToFind).cache().queryOne();
        }

        if (searchAllId || (searchProductFirst && UtilValidate.isEmpty(product))) {
            // 2) Retrieve product in GoodIdentification
            Map<String, String> conditions = UtilMisc.toMap("idValue", idToFind);
            if (UtilValidate.isNotEmpty(goodIdentificationTypeId)) {
                conditions.put("goodIdentificationTypeId", goodIdentificationTypeId);
            }
            productsFound = EntityQuery.use(delegator).from("GoodIdentificationAndProduct").where(conditions).orderBy("productId").cache(true).queryList();
        }

        if (! searchProductFirst) {
            product = EntityQuery.use(delegator).from("Product").where("productId", idToFind).cache().queryOne();
        }

        if (product != null) {
            if (UtilValidate.isNotEmpty(productsFound)) {
                productsFound.add(product);
            } else {
                productsFound = UtilMisc.toList(product);
            }
        }
        if (Debug.verboseOn()) {
            Debug.logVerbose("Analyze goodIdentification: found product.productId = " + product + ", and list : " + productsFound, module);
        }
        return productsFound;
    }

    public static List<GenericValue> findProductsById(Delegator delegator, String idToFind, String goodIdentificationTypeId)
    throws GenericEntityException {
        return findProductsById(delegator, idToFind, goodIdentificationTypeId, true, false);
    }

    public static String findProductId(Delegator delegator, String idToFind, String goodIdentificationTypeId) throws GenericEntityException {
        GenericValue product = findProduct(delegator, idToFind, goodIdentificationTypeId);
        if (product != null) {
            return product.getString("productId");
        } else {
            return null;
        }
    }

    public static String findProductId(Delegator delegator, String idToFind) throws GenericEntityException {
        return findProductId(delegator, idToFind, null);
    }

    public static GenericValue findProduct(Delegator delegator, String idToFind, String goodIdentificationTypeId) throws GenericEntityException {
        List<GenericValue> products = findProductsById(delegator, idToFind, goodIdentificationTypeId);
        GenericValue product = EntityUtil.getFirst(products);
        return product;
    }

    public static List<GenericValue> findProducts(Delegator delegator, String idToFind, String goodIdentificationTypeId) throws GenericEntityException {
        List<GenericValue> productsByIds = findProductsById(delegator, idToFind, goodIdentificationTypeId);
        List<GenericValue> products = null;
        if (UtilValidate.isNotEmpty(productsByIds)) {
            for (GenericValue product : productsByIds) {
                GenericValue productToAdd = product;
                //retreive product GV if the actual genericValue came from viewEntity
                if (! "Product".equals(product.getEntityName())) {
                    productToAdd = EntityQuery.use(delegator).from("Product").where("productId", product.get("productId")).cache().queryOne();
                }

                if (UtilValidate.isEmpty(products)) {
                    products = UtilMisc.toList(productToAdd);
                }
                else {
                    products.add(productToAdd);
                }
            }
        }
        return products;
    }

    public static List<GenericValue> findProducts(Delegator delegator, String idToFind) throws GenericEntityException {
        return findProducts(delegator, idToFind, null);
    }

    public static GenericValue findProduct(Delegator delegator, String idToFind) throws GenericEntityException {
        return findProduct(delegator, idToFind, null);
    }

    public static boolean isSellable(Delegator delegator, String productId, Timestamp atTime) throws GenericEntityException {
        return isSellable(findProduct(delegator, productId), atTime);
    }

    public static boolean isSellable(Delegator delegator, String productId) throws GenericEntityException {
        return isSellable(findProduct(delegator, productId));
    }

    public static boolean isSellable(GenericValue product) {
        return isSellable(product, UtilDateTime.nowTimestamp());
    }

    public static boolean isSellable(GenericValue product, Timestamp atTime) {
        if (product != null) {
            Timestamp introDate = product.getTimestamp("introductionDate");
            Timestamp discDate = product.getTimestamp("salesDiscontinuationDate");
            if (introDate == null || introDate.before(atTime)) {
                if (discDate == null || discDate.after(atTime)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static Set<String> getRefurbishedProductIdSet(String productId, Delegator delegator) throws GenericEntityException {
        Set<String> productIdSet = new HashSet<>();

        // find associated refurb items, we want serial number for main item or any refurb items too
        List<GenericValue> refubProductAssocs = EntityQuery.use(delegator).from("ProductAssoc").where("productId", productId, "productAssocTypeId", "PRODUCT_REFURB").filterByDate().queryList();
        for (GenericValue refubProductAssoc: refubProductAssocs) {
            productIdSet.add(refubProductAssoc.getString("productIdTo"));
        }

        // see if this is a refurb productId to, and find product(s) it is a refurb of
        List<GenericValue> refubProductToAssocs = EntityQuery.use(delegator).from("ProductAssoc").where("productIdTo", productId, "productAssocTypeId", "PRODUCT_REFURB").filterByDate().queryList();
        for (GenericValue refubProductToAssoc: refubProductToAssocs) {
            productIdSet.add(refubProductToAssoc.getString("productId"));
        }

        return productIdSet;
    }

    public static String getVariantFromFeatureTree(String productId, List<String> selectedFeatures, Delegator delegator) {

        //  all method code moved here from ShoppingCartEvents.addToCart event
        String variantProductId = null;
        try {

            for (String paramValue: selectedFeatures) {
                // find incompatibilities..
                List<GenericValue> incompatibilityVariants = EntityQuery.use(delegator).from("ProductFeatureIactn")
                        .where("productId", productId, "productFeatureIactnTypeId","FEATURE_IACTN_INCOMP").cache(true).queryList();
                for (GenericValue incompatibilityVariant: incompatibilityVariants) {
                    String featur = incompatibilityVariant.getString("productFeatureId");
                    if (paramValue.equals(featur)) {
                        String featurTo = incompatibilityVariant.getString("productFeatureIdTo");
                        for (String paramValueTo: selectedFeatures) {
                            if (featurTo.equals(paramValueTo)) {
                                Debug.logWarning("Incompatible features", module);
                                return null;
                            }
                        }

                    }
                }
                // find dependencies..
                List<GenericValue> dependenciesVariants = EntityQuery.use(delegator).from("ProductFeatureIactn")
                        .where("productId", productId, "productFeatureIactnTypeId","FEATURE_IACTN_DEPEND").cache(true).queryList();
                for (GenericValue dpVariant: dependenciesVariants) {
                    String featur = dpVariant.getString("productFeatureId");
                    if (paramValue.equals(featur)) {
                        String featurTo = dpVariant.getString("productFeatureIdTo");
                        boolean found = false;
                        for (String paramValueTo: selectedFeatures) {
                            if (featurTo.equals(paramValueTo)) {
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            Debug.logWarning("Dependency features", module);
                            return null;
                        }
                    }
                }
            }
            // find variant
            List<GenericValue> productAssocs = EntityQuery.use(delegator).from("ProductAssoc").where("productId", productId, "productAssocTypeId","PRODUCT_VARIANT").filterByDate().queryList();
            boolean productFound = false;
nextProd:
            for (GenericValue productAssoc: productAssocs) {
                for (String featureId: selectedFeatures) {
                    List<GenericValue> pAppls = EntityQuery.use(delegator).from("ProductFeatureAppl").where("productId", productAssoc.getString("productIdTo"), "productFeatureId", featureId, "productFeatureApplTypeId","STANDARD_FEATURE").cache(true).queryList();
                    if (UtilValidate.isEmpty(pAppls)) {
                        continue nextProd;
                    }
                }
                productFound = true;
                variantProductId = productAssoc.getString("productIdTo");
                break;
            }

            /**
             * 1. variant not found so create new variant product and use the virtual product as basis, new one  is a variant type and not a virtual type.
             *    adjust the prices according the selected features
             */
            if (!productFound) {
                // copy product to be variant
                GenericValue product = EntityQuery.use(delegator).from("Product").where("productId",  productId).queryOne();
                product.put("isVariant", "Y");
                product.put("isVirtual", "N");
                product.put("productId", delegator.getNextSeqId("Product"));
                product.remove("virtualVariantMethodEnum"); // not relevant for a non virtual product.
                product.create();
                // add the selected/standard features as 'standard features' to the 'ProductFeatureAppl' table
                GenericValue productFeatureAppl = delegator.makeValue("ProductFeatureAppl",
                        UtilMisc.toMap("productId", product.getString("productId"), "productFeatureApplTypeId", "STANDARD_FEATURE"));
                productFeatureAppl.put("fromDate", UtilDateTime.nowTimestamp());
                for (String productFeatureId: selectedFeatures) {
                    productFeatureAppl.put("productFeatureId",  productFeatureId);
                    productFeatureAppl.create();
                }
                //add standard features too
                List<GenericValue> stdFeaturesAppls = EntityQuery.use(delegator).from("ProductFeatureAppl").where("productId", productId, "productFeatureApplTypeId", "STANDARD_FEATURE").filterByDate().queryList();
                for (GenericValue stdFeaturesAppl: stdFeaturesAppls) {
                    stdFeaturesAppl.put("productId",  product.getString("productId"));
                    stdFeaturesAppl.create();
                }
                /* 3. use the price of the virtual product(Entity:ProductPrice) as a basis and adjust according the prices in the feature price table.
                 *  take the default price from the vitual product, go to the productfeature table and retrieve all the prices for the difFerent features
                 *  add these to the price of the virtual product, store the result as the default price on the variant you created.
                 */
                List<GenericValue> productPrices = EntityQuery.use(delegator).from("ProductPrice").where("productId", productId).filterByDate().queryList();
                for (GenericValue productPrice: productPrices) {
                    for (String selectedFeaturedId: selectedFeatures) {
                        List<GenericValue> productFeaturePrices = EntityQuery.use(delegator).from("ProductFeaturePrice")
                                .where("productFeatureId", selectedFeaturedId, "productPriceTypeId", productPrice.getString("productPriceTypeId"))
                                .filterByDate().queryList();
                        if (UtilValidate.isNotEmpty(productFeaturePrices)) {
                            GenericValue productFeaturePrice = productFeaturePrices.get(0);
                            if (productFeaturePrice != null) {
                                productPrice.put("price", productPrice.getBigDecimal("price").add(productFeaturePrice.getBigDecimal("price")));
                            }
                        }
                    }
                    if (productPrice.get("price") == null) {
                        productPrice.put("price", productPrice.getBigDecimal("price"));
                    }
                    productPrice.put("productId",  product.getString("productId"));
                    productPrice.create();
                }
                // add the product association
                GenericValue productAssoc = delegator.makeValue("ProductAssoc", UtilMisc.toMap("productId", productId, "productIdTo", product.getString("productId"), "productAssocTypeId", "PRODUCT_VARIANT"));
                productAssoc.put("fromDate", UtilDateTime.nowTimestamp());
                productAssoc.create();
                Debug.logInfo("set the productId to: " + product.getString("productId"), module);

                // copy the supplier
                List<GenericValue> supplierProducts = EntityQuery.use(delegator).from("SupplierProduct").where("productId", productId).cache(true).queryList();
                for (GenericValue supplierProduct: supplierProducts) {
                    supplierProduct = (GenericValue) supplierProduct.clone();
                    supplierProduct.set("productId",  product.getString("productId"));
                    supplierProduct.create();
                }

                // copy the content
                List<GenericValue> productContents = EntityQuery.use(delegator).from("ProductContent").where("productId", productId).cache(true).queryList();
                for (GenericValue productContent: productContents) {
                    productContent = (GenericValue) productContent.clone();
                    productContent.set("productId",  product.getString("productId"));
                    productContent.create();
                }

                // finally use the new productId to be added to the cart
                variantProductId = product.getString("productId"); // set to the new product
            }

        } catch (GenericEntityException e) {
            Debug.logError(e, module);
        }

        return variantProductId;
    }

    public static boolean isAlternativePacking(Delegator delegator, String productId, String virtualVariantId) {
        boolean isAlternativePacking = false;
        if(productId != null || virtualVariantId != null){
            List<GenericValue> alternativePackingProds = null;
            try {
                List<EntityCondition> condList = new LinkedList<>();

                if (UtilValidate.isNotEmpty(productId)) {
                    condList.add(EntityCondition.makeCondition("productIdTo", productId));
                }
                if (UtilValidate.isNotEmpty(virtualVariantId)) {
                    condList.add(EntityCondition.makeCondition("productId", virtualVariantId));
                }
                condList.add(EntityCondition.makeCondition("productAssocTypeId", "ALTERNATIVE_PACKAGE"));
                alternativePackingProds = EntityQuery.use(delegator).from("ProductAssoc").where(condList).cache(true).queryList();
                if(UtilValidate.isNotEmpty(alternativePackingProds)) {
                    isAlternativePacking = true;
                }
            } catch (GenericEntityException e) {
                Debug.logWarning(e, "Could not found alternative product: " + e.getMessage(), module);
            }
        }
        return isAlternativePacking;
    }

    /**
     * Returns the original product ID IF the product is as alternative packing.
     * <p>
     * SCIPIO: 2019-02-28: Renamed from {@link #getOriginalProductId(Delegator, String)}.
     */
    public static String getAlternativePackingOriginalProductId(Delegator delegator, String productId) {
        boolean isAlternativePacking = isAlternativePacking(delegator, null, productId);
        if (isAlternativePacking) {
            List<GenericValue> productAssocs = null;
            try {
                productAssocs = EntityQuery.use(delegator).from("ProductAssoc").where("productId", productId , "productAssocTypeId", "ALTERNATIVE_PACKAGE").filterByDate().queryList();
            } catch (GenericEntityException e) {
                Debug.logError(e, module);
            }

            if (productAssocs != null) {
                GenericValue productAssoc = EntityUtil.getFirst(productAssocs);
                return productAssoc.getString("productIdTo");
            }
            return null;
        }
        return null;
    }

    /**
     * Returns the original product ID IF the product is as alternative packing.
     * @deprecated SCIPIO: 2019-02-28: Please use the non-ambiguous {@link #getAlternativePackingOriginalProductId} instead.
     */
    public static String getOriginalProductId(Delegator delegator, String productId) {
        return getAlternativePackingOriginalProductId(delegator, productId);
    }

    /**
     * worker to test if product can be order with a decimal quantity
     * @param delegator : access to DB
     * @param productId : ref. of product
     * * @param productStoreId : ref. of store
     * @return true if it can be ordered by decimal quantity
     * @throws GenericEntityException to catch
     */
    public static Boolean isDecimalQuantityOrderAllowed(Delegator delegator, String productId, String productStoreId) throws GenericEntityException{
        //sometime productStoreId may be null (ie PO), then return default value which is TRUE
        if(UtilValidate.isEmpty(productStoreId)){
            return Boolean.TRUE;
        }
        String allowDecimalStore = EntityQuery.use(delegator).from("ProductStore").where("productStoreId", productStoreId).cache(true).queryOne().getString("orderDecimalQuantity");
        String allowDecimalProduct = EntityQuery.use(delegator).from("Product").where("productId", productId).cache(true).queryOne().getString("orderDecimalQuantity");

        if("N".equals(allowDecimalProduct) || (UtilValidate.isEmpty(allowDecimalProduct) && "N".equals(allowDecimalStore))){
            return Boolean.FALSE;
        }
        return Boolean.TRUE;
    }

    public static boolean isAggregateService(Delegator delegator, String aggregatedProductId) {
        try {
            GenericValue aggregatedProduct = EntityQuery.use(delegator).from("Product").where("productId", aggregatedProductId).cache().queryOne();
            if (UtilValidate.isNotEmpty(aggregatedProduct) && "AGGREGATED_SERVICE".equals(aggregatedProduct.getString("productTypeId"))) {
                return true;
            }
        } catch (GenericEntityException e) {
            Debug.logWarning(e.getMessage(), module);
        }

        return false;
    }

    // Method to filter-out out of stock products
    public static List<GenericValue> filterOutOfStockProducts (List<GenericValue> productsToFilter, LocalDispatcher dispatcher, Delegator delegator) throws GeneralException {
        List<GenericValue> productsInStock = new ArrayList<>();
        if (UtilValidate.isNotEmpty(productsToFilter)) {
            for (GenericValue genericRecord : productsToFilter) {
                String productId = genericRecord.getString("productId");
                GenericValue product = null;
                product = EntityQuery.use(delegator).from("Product").where("productId", productId).cache(true).queryOne();
                Boolean isMarketingPackage = EntityTypeUtil.hasParentType(delegator, "ProductType", "productTypeId", product.getString("productTypeId"), "parentTypeId", "MARKETING_PKG");

                if ( UtilValidate.isNotEmpty(isMarketingPackage) && isMarketingPackage) {
                    Map<String, Object> resultOutput = new HashMap<>();
                    resultOutput = dispatcher.runSync("getMktgPackagesAvailable", UtilMisc.toMap("productId" ,productId));
                    Debug.logWarning("Error getting available marketing package.", module);

                    BigDecimal availableInventory = (BigDecimal) resultOutput.get("availableToPromiseTotal");
                    if(availableInventory.compareTo(BigDecimal.ZERO) > 0) {
                        productsInStock.add(genericRecord);
                    }
                } else {
                    List<GenericValue> facilities = EntityQuery.use(delegator).from("ProductFacility").where("productId", productId).queryList();
                    BigDecimal availableInventory = BigDecimal.ZERO;
                    if (UtilValidate.isNotEmpty(facilities)) {
                        for (GenericValue facility : facilities) {
                            BigDecimal lastInventoryCount = facility.getBigDecimal("lastInventoryCount");
                            if (lastInventoryCount != null) {
                                availableInventory = lastInventoryCount.add(availableInventory);
                            }
                        }
                        if (availableInventory.compareTo(BigDecimal.ZERO) > 0) {
                            productsInStock.add(genericRecord);
                        }
                    }
                }
            }
        }
        return productsInStock;
    }

    /**
     * SCIPIO: Returns a last inventory count (based on ProductFacility.lastInventoryCount) of the product
     * for each specified product store, as a map of productStoreId to inventory counts; can also return
     * total for all facilities as "_total_" key.
     * This method calculates for the specific productId only and does NOT honor ProductStore.useVariantStockCalc.
     * <p>
     * Based on {@link #filterOutOfStockProducts}.
     * <p>
     * Added 2018-05-29.
     */
    public static Map<String, BigDecimal> getIndividualProductStockPerProductStore(Delegator delegator, LocalDispatcher dispatcher,
            GenericValue product, Collection<GenericValue> productStores, boolean useTotal, Timestamp moment, boolean useCache) throws GeneralException {
        Map<String, BigDecimal> countMap = new HashMap<>();

        String productId = product.getString("productId");
        if (!"Product".equals(product.getEntityName())) {
            product = EntityQuery.use(delegator).from("Product").where("productId", productId).cache(useCache).queryOne();
        }
        boolean isMarketingPackage = EntityTypeUtil.hasParentType(delegator, "ProductType", "productTypeId", product.getString("productTypeId"), "parentTypeId", "MARKETING_PKG");

        List<GenericValue> productFacilities = EntityQuery.use(delegator).from("ProductFacility")
                .where("productId", productId).cache(useCache).queryList();
        Map<String, GenericValue> productFacilityIdMap = UtilMisc.extractValuesForKeyAsMap(productFacilities, "facilityId", new HashMap<>());

        for(GenericValue productStore : productStores) {
            BigDecimal storeInventory = BigDecimal.ZERO;
            String productStoreId = productStore.getString("productStoreId");
            List<GenericValue> productStoreFacilities = EntityQuery.use(delegator).from("ProductStoreFacility")
                    .where("productStoreId", productStoreId).filterByDate(moment).cache(useCache).queryList();
            for(GenericValue productStoreFacility : productStoreFacilities) {
                String facilityId = productStoreFacility.getString("facilityId");
                GenericValue productFacility = productFacilityIdMap.get(facilityId);
                if (productFacility != null) {
                    if (Boolean.TRUE.equals(isMarketingPackage)) {
                        Map<String, Object> resultOutput = dispatcher.runSync("getMktgPackagesAvailable",
                                UtilMisc.toMap("productId", productId, "facilityId", facilityId));
                        if (!ServiceUtil.isSuccess(resultOutput)) {
                            Debug.logWarning("Error getting available marketing package.", module);
                        }
                        BigDecimal availableInventory = (BigDecimal) resultOutput.get("availableToPromiseTotal");
                        if (availableInventory != null) {
                            storeInventory = storeInventory.add(availableInventory);
                        }
                    } else {
                        BigDecimal lastInventoryCount = productFacility.getBigDecimal("lastInventoryCount");
                        if (lastInventoryCount != null) {
                            storeInventory = storeInventory.add(lastInventoryCount);
                        }
                    }
                }
            }
            countMap.put(productStoreId, storeInventory);
        }

        if (useTotal) {
            BigDecimal totalInventory = getTotalIndividualProductStock(delegator, dispatcher, product,
                    moment, useCache, productId, isMarketingPackage, productFacilities);
            countMap.put("_total_", totalInventory);
        }

        return countMap;
    }

    /**
     * SCIPIO: Returns a last inventory count (based on ProductFacility.lastInventoryCount) of the product
     * for each for all stores/facilities.
     * This method calculates for the specific productId only and does NOT honor ProductStore.useVariantStockCalc.
     * <p>
     * Based on {@link #filterOutOfStockProducts}.
     * <p>
     * Added 2018-06-06.
     */
    public static BigDecimal getTotalIndividualProductStock(Delegator delegator, LocalDispatcher dispatcher,
            GenericValue product, Timestamp moment, boolean useCache) throws GeneralException {
        String productId = product.getString("productId");
        boolean isMarketingPackage = EntityTypeUtil.hasParentType(delegator, "ProductType", "productTypeId", product.getString("productTypeId"), "parentTypeId", "MARKETING_PKG");
        List<GenericValue> productFacilities = EntityQuery.use(delegator).from("ProductFacility")
                .where("productId", productId).cache(useCache).queryList();
        return getTotalIndividualProductStock(delegator, dispatcher, product, moment, useCache,
                productId, isMarketingPackage, productFacilities);
    }

    private static BigDecimal getTotalIndividualProductStock(Delegator delegator, LocalDispatcher dispatcher,
            GenericValue product, Timestamp moment, boolean useCache, String productId, boolean isMarketingPackage, List<GenericValue> productFacilities) throws GeneralException {
        BigDecimal totalInventory = BigDecimal.ZERO;
        if (Boolean.TRUE.equals(isMarketingPackage)) {
            Map<String, Object> resultOutput = dispatcher.runSync("getMktgPackagesAvailable",
                    UtilMisc.toMap("productId", productId));
            if (!ServiceUtil.isSuccess(resultOutput)) {
                Debug.logWarning("Error getting available marketing package.", module);
            }
            BigDecimal availableInventory = (BigDecimal) resultOutput.get("availableToPromiseTotal");
            if (availableInventory != null) {
                totalInventory = availableInventory;
            }
        } else {
            for(GenericValue productFacility : productFacilities) {
                BigDecimal lastInventoryCount = productFacility.getBigDecimal("lastInventoryCount");
                if (lastInventoryCount != null) {
                    totalInventory = totalInventory.add(lastInventoryCount);
                }
            }
        }
        return totalInventory;
    }

    /**
     * SCIPIO: Returns a last inventory count (based on ProductFacility.lastInventoryCount) of the product
     * for each specified product store, as a map of productStoreId to inventory counts; can also return
     * total for all facilities as "_total_" key.
     * This method can calculate stock for virtual products from variants and honors ProductStore.useVariantStockCalc.
     * <p>
     * Based on {@link #filterOutOfStockProducts} but with additional support for variant inventory calculation.
     * <p>
     * NOTE: The useVariantStockCalcForTotal is used when useTotal true and determines if the total should use
     * the variant-based total calc or not - this is ambiguous because useVariantStockCalc is per-product store.
     * <p>
     * Added 2018-06-06.
     */
    public static Map<String, BigDecimal> getProductStockPerProductStore(Delegator delegator, LocalDispatcher dispatcher,
            GenericValue product, Collection<GenericValue> productStores, boolean useTotal, boolean useVariantStockCalcForTotal,
            Timestamp moment, boolean useCache) throws GeneralException {
        if (!Boolean.TRUE.equals(product.getBoolean("isVirtual"))) {
            // non-virtual product - calculate individual stock
            return getIndividualProductStockPerProductStore(delegator, dispatcher, product, productStores, useTotal, moment, useCache);
        }

        // virtual product - for each store, check useVariantStockCalc setting and split them up
        List<GenericValue> variantStockStores = new ArrayList<>();
        List<GenericValue> indivStockStores = new ArrayList<>();
        // sort stores by useVariantStockCalc flag
        for(GenericValue productStore : productStores) {
            if (Boolean.TRUE.equals(productStore.getBoolean("useVariantStockCalc"))) {
                variantStockStores.add(productStore);
            } else {
                indivStockStores.add(productStore);
            }
        }

        Map<String, BigDecimal> countMap = new HashMap<>();

        BigDecimal totalInventory = BigDecimal.ZERO;
        if (variantStockStores.size() > 0) {
            List<GenericValue> variantProducts = getVariantProductsForStockCalc(delegator, dispatcher, product, moment, useCache);
            if (variantProducts.size() > 0) {
                for (GenericValue variantProduct : variantProducts) {
                    Map<String, BigDecimal> variantStoreInventories = ProductWorker.getIndividualProductStockPerProductStore(delegator, dispatcher,
                            variantProduct, variantStockStores, (useTotal && useVariantStockCalcForTotal), moment, useCache);
                    for (Map.Entry<String, BigDecimal> entry : variantStoreInventories.entrySet()) {
                        if ("_total_".equals(entry.getKey())) {
                            totalInventory = totalInventory.add(entry.getValue());
                        } else {
                            BigDecimal storeInventory = countMap.get(entry.getKey());
                            if (storeInventory == null) storeInventory = BigDecimal.ZERO;
                            countMap.put(entry.getKey(), storeInventory.add(entry.getValue()));
                        }
                    }
                }
            } else {
                for(GenericValue productStore : variantStockStores) {
                    countMap.put(productStore.getString("productStoreId"), BigDecimal.ZERO);
                }
            }
        }
        if (useTotal && useVariantStockCalcForTotal) {
            countMap.put("_total_", totalInventory);
        }

        if (indivStockStores.size() > 0) {
            Map<String, BigDecimal> indivStoreInventories = ProductWorker.getIndividualProductStockPerProductStore(delegator, dispatcher,
                    product, indivStockStores, (useTotal && !useVariantStockCalcForTotal), moment, useCache);
            countMap.putAll(indivStoreInventories); // already includes _total_, if was requested
        } else {
            if (useTotal && !useVariantStockCalcForTotal) {
                totalInventory = ProductWorker.getTotalIndividualProductStock(delegator, dispatcher, product, moment, useCache);
                countMap.put("_total_", totalInventory);
            }
        }

        return countMap;
    }

    /**
     * SCIPIO: Returns the virtual products of a variant product, first-level only.
     * The returned instances are specifically Product instances.
     * <p>
     * NOTE: Normally, orderBy is set to "-fromDate" and only the first product is consulted;
     * this method is a generalization.
     * <p>
     * Added 2018-07-24.
     */
    public static List<GenericValue> getVirtualProducts(Delegator delegator, LocalDispatcher dispatcher,
            GenericValue product, List<String> orderBy, Integer maxResults, Timestamp moment, boolean useCache) throws GeneralException {
        List<GenericValue> variantProductAssocs = EntityQuery.use(delegator).from("ProductAssoc")
                .where("productIdTo", product.getString("productId"), "productAssocTypeId", "PRODUCT_VARIANT").orderBy(orderBy)
                .cache(useCache).filterByDate(moment).queryList();
        List<GenericValue> variantProducts = new ArrayList<>(variantProductAssocs.size());
        int i = 0;
        for (GenericValue assoc : variantProductAssocs) {
            variantProducts.add(assoc.getRelatedOne("MainProduct", useCache));
            i++;
            if (maxResults != null && i >= maxResults) break;
        }
        return variantProducts;
    }

    /**
     * SCIPIO: Returns the virtual products of a variant product, deep, results depth-first.
     * The returned instances are specifically Product instances.
     * <p>
     * NOTE: Normally, orderBy is set to "-fromDate" and only the first product is returned
     * on each level; this method is a generalization.
     * This method accepts a maxPerLevel to implement this.
     * <p>
     * Added 2018-07-24.
     */
    public static List<GenericValue> getVirtualProductsDeepDfs(Delegator delegator, LocalDispatcher dispatcher,
            GenericValue product, List<String> orderBy, Integer maxPerLevel, Timestamp moment, boolean useCache) throws GeneralException {
        List<GenericValue> virtualProductAssocs = EntityQuery.use(delegator).from("ProductAssoc")
                .where("productIdTo", product.getString("productId"), "productAssocTypeId", "PRODUCT_VARIANT").orderBy(orderBy)
                .cache(useCache).filterByDate(moment).queryList();
        List<GenericValue> virtualProducts = new ArrayList<>();
        int i = 0;
        for (GenericValue assoc : virtualProductAssocs) {
            GenericValue virtualProduct = assoc.getRelatedOne("MainProduct", useCache);
            virtualProducts.add(virtualProduct);
            if (Boolean.TRUE.equals(virtualProduct.getBoolean("isVariant"))) {
                List<GenericValue> subVariantProducts = getVirtualProductsDeepDfs(delegator, dispatcher,
                        virtualProduct, orderBy, maxPerLevel, moment, useCache);
                virtualProducts.addAll(subVariantProducts);
            }
            i++;
            if (maxPerLevel != null && i >= maxPerLevel) break;
        }
        return virtualProducts;
    }

    /**
     * SCIPIO: Returns the variant products of a virtual product, first-level only.
     * The returned instances are specifically Product instances.
     * <p>
     * Added 2018-07-24.
     */
    public static List<GenericValue> getVariantProducts(Delegator delegator, LocalDispatcher dispatcher,
            GenericValue product, List<String> orderBy, Timestamp moment, boolean useCache) throws GeneralException {
        List<GenericValue> variantProductAssocs = EntityQuery.use(delegator).from("ProductAssoc")
                .where("productId", product.getString("productId"), "productAssocTypeId", "PRODUCT_VARIANT").orderBy(orderBy)
                .cache(useCache).filterByDate(moment).queryList();
        List<GenericValue> variantProducts = new ArrayList<>(variantProductAssocs.size());
        for (GenericValue assoc : variantProductAssocs) {
            variantProducts.add(assoc.getRelatedOne("AssocProduct", useCache));
        }
        return variantProducts;
    }

    /**
     * SCIPIO: Returns the variant products of a virtual product, deep, results depth-first.
     * The returned instances are specifically Product instances.
     * <p>
     * Added 2018-07-24.
     */
    public static List<GenericValue> getVariantProductsDeepDfs(Delegator delegator, LocalDispatcher dispatcher,
            GenericValue product, List<String> orderBy, Timestamp moment, boolean useCache) throws GeneralException {
        List<GenericValue> variantProductAssocs = EntityQuery.use(delegator).from("ProductAssoc")
                .where("productId", product.getString("productId"), "productAssocTypeId", "PRODUCT_VARIANT").orderBy(orderBy)
                .cache(useCache).filterByDate(moment).queryList();
        List<GenericValue> variantProducts = new ArrayList<>();
        for (GenericValue assoc : variantProductAssocs) {
            GenericValue variantProduct = assoc.getRelatedOne("AssocProduct", useCache);
            variantProducts.add(variantProduct);
            if (Boolean.TRUE.equals(variantProduct.getBoolean("isVirtual"))) {
                List<GenericValue> subVariantProducts = getVariantProductsDeepDfs(delegator, dispatcher,
                        variantProduct, orderBy, moment, useCache);
                variantProducts.addAll(subVariantProducts);
            }
        }
        return variantProducts;
    }

    /**
     * SCIPIO: Returns the variant products of a virtual product whose stock may be summed when
     * useVariantStockCalc is honored.
     * <p>
     * Added 2018-06-06.
     */
    public static List<GenericValue> getVariantProductsForStockCalc(Delegator delegator, LocalDispatcher dispatcher,
            GenericValue product, Timestamp moment, boolean useCache) throws GeneralException {
        List<GenericValue> variantProductAssocs = EntityQuery.use(delegator).from("ProductAssoc")
                .where("productId", product.getString("productId"), "productAssocTypeId", "PRODUCT_VARIANT").orderBy("-fromDate")
                .cache(useCache).filterByDate(moment).queryList();
        List<GenericValue> variantProducts = new ArrayList<>(variantProductAssocs.size());
        for (GenericValue assoc : variantProductAssocs) {
            variantProducts.add(assoc.getRelatedOne("AssocProduct", useCache));
        }
        return variantProducts;
    }

    /**
     * SCIPIO: For each simple-text-compatible productContentTypeIdList, returns a list of complex record views,
     * where the first entry is ProductContentAndElectronicText and the following entries (if any)
     * are ContentAssocToElectronicText views.
     * <p>
     * NOTE: If there are multiple ProductContent for same product/type, this fetches the lastest only (logs warning).
     * System or user is expected to prevent this.
     * <p>
     * filterByDate must be set to a value in order to filter by date.
     * Added 2017-10-27.
     */
    public static Map<String, List<GenericValue>> getProductContentLocalizedSimpleTextViews(Delegator delegator, LocalDispatcher dispatcher,
            String productId, Collection<String> productContentTypeIdList, java.sql.Timestamp filterByDate, boolean useCache) throws GenericEntityException {
        Map<String, List<GenericValue>> fieldMap = new HashMap<>();

        List<EntityCondition> typeIdCondList = new ArrayList<>(productContentTypeIdList.size());
        if (productContentTypeIdList != null) {
            for(String productContentTypeId : productContentTypeIdList) {
                typeIdCondList.add(EntityCondition.makeCondition("productContentTypeId", productContentTypeId));
            }
        }
        List<EntityCondition> condList = new ArrayList<>();
        condList.add(EntityCondition.makeCondition("productId", productId));
        if (typeIdCondList.size() > 0) {
            condList.add(EntityCondition.makeCondition(typeIdCondList, EntityOperator.OR));
        }
        condList.add(EntityCondition.makeCondition("drDataResourceTypeId", "ELECTRONIC_TEXT"));

        EntityQuery query = EntityQuery.use(delegator).from("ProductContentAndElectronicText")
                .where(condList).orderBy("-fromDate").cache(useCache);
        if (filterByDate != null) {
            query = query.filterByDate(filterByDate);
        }
        List<GenericValue> productContentList = query.queryList();
        for(GenericValue productContent : productContentList) {
            String productContentTypeId = productContent.getString("productContentTypeId");
            if (fieldMap.containsKey(productContentTypeId)) {
                Debug.logWarning("getProductContentLocalizedSimpleTextViews: multiple ProductContentAndElectronicText"
                        + " records found for productContentTypeId '" + productContentTypeId + "' for productId '" + productId + "'; "
                        + " returning first found only (this may cause unexpected texts to appear)", module);
                continue;
            }
            String contentIdStart = productContent.getString("contentId");

            condList = new ArrayList<>();
            condList.add(EntityCondition.makeCondition("contentIdStart", contentIdStart));
            condList.add(EntityCondition.makeCondition("contentAssocTypeId", "ALTERNATE_LOCALE"));
            condList.add(EntityCondition.makeCondition("drDataResourceTypeId", "ELECTRONIC_TEXT"));

            query = EntityQuery.use(delegator).from("ContentAssocToElectronicText").where(condList).orderBy("-fromDate").cache(useCache);
            if (filterByDate != null) {
                query = query.filterByDate(filterByDate);
            }
            List<GenericValue> contentAssocList = query.queryList();

            List<GenericValue> valueList = new ArrayList<>(contentAssocList.size() + 1);
            valueList.add(productContent);
            valueList.addAll(contentAssocList);
            fieldMap.put(productContentTypeId, valueList);
        }

        return fieldMap;
    }

    /**
     * SCIPIO: Returns all rollups for a product that have the given top categories.
     * TODO: REVIEW: maybe this can be optimized with a smarter algorithm?
     * Added 2017-11-09.
     */
    public static List<List<String>> getProductRollupTrails(Delegator delegator, String productId, Collection<String> topCategoryIds, boolean useCache) {
        List<GenericValue> prodCatMembers;
        try {
            prodCatMembers = EntityQuery.use(delegator).from("ProductCategoryMember")
                    .where("productId", productId).orderBy("-fromDate").filterByDate().cache(useCache).queryList();
        } catch (GenericEntityException e) {
            Debug.logError("Cannot generate trail from product '" + productId + "'", productId);
            return new ArrayList<>();
        }
        if (prodCatMembers.size() == 0) return new ArrayList<>();

        List<List<String>> possibleTrails = null;
        for(GenericValue prodCatMember : prodCatMembers) {
            List<List<String>> trails = CategoryWorker.getCategoryRollupTrails(delegator, prodCatMember.getString("productCategoryId"), topCategoryIds, useCache);
            if (possibleTrails == null) possibleTrails = trails;
            else possibleTrails.addAll(trails);
        }
        return possibleTrails;
    }

    /**
     * SCIPIO: Returns all rollups for a product (any top category).
     * Added 2019-01.
     */
    public static List<List<String>> getProductRollupTrails(Delegator delegator, String productId, boolean useCache) {
        return getProductRollupTrails(delegator, productId, null, useCache);
    }

    /**
     * SCIPIO: Gets the product's "main" or "original" product association; for variant products -> its virtual,
     * for configurable product -> its original unconfigured product.
     * Added 2018-08-17.
     */
    public static GenericValue getMainProductAssoc(Delegator delegator, String productId, boolean useCache) { // SCIPIO: added useCache 2017-09-05
        if (UtilValidate.isEmpty(productId)) {
            // Allow empty for convenience
            //Debug.logWarning("Bad product id", module);
            return null;
        }
        try {
            List<EntityCondition> productAssocTypeIdCondList = UtilMisc.toList(
                    EntityCondition.makeCondition("productAssocTypeId", "PRODUCT_VARIANT"),
                    EntityCondition.makeCondition("productAssocTypeId", "UNIQUE_ITEM"),
                    EntityCondition.makeCondition("productAssocTypeId", "PRODUCT_CONF"));
            EntityCondition cond = EntityCondition.makeCondition(EntityCondition.makeCondition("productIdTo", productId),
                    EntityOperator.AND,
                    EntityCondition.makeCondition(productAssocTypeIdCondList, EntityOperator.OR));
            List<GenericValue> virtualProductAssocs = EntityQuery.use(delegator).from("ProductAssoc")
                    .where(cond)
                    .orderBy("-fromDate")
                    .cache(useCache)
                    .filterByDate()
                    .queryList();
            return EntityUtil.getFirst(virtualProductAssocs);
        } catch (GenericEntityException e) {
            Debug.logError(e, "Entity engine error while getting original product for '" + productId + "'", module);
        }
        return null;
    }

    /**
     * SCIPIO: Gets the product's "main" or "original" product; for variant products -> its virtual,
     * for configurable product -> its original unconfigured product.
     * Added 2018-08-17.
     */
    public static GenericValue getMainProduct(Delegator delegator, String productId, boolean useCache) {
        GenericValue origProduct = null;
        try {
            GenericValue productAssoc = getMainProductAssoc(delegator, productId, useCache);
            if (productAssoc != null) {
                origProduct = productAssoc.getRelatedOne("MainProduct", useCache);
            }
        } catch (GenericEntityException e) {
            Debug.logError(e, "Entity engine error while getting original product for '" + productId + "'", module);
        }
        return origProduct;
    }

    /**
     * SCIPIO: Gets the product's "main" or "original" product; for variant products -> its virtual,
     * for configurable product -> its original unconfigured product.
     * Added 2018-08-17.
     */
    public static GenericValue getMainProduct(Delegator delegator, String productId) {
        return getMainProduct(delegator, productId, true);
    }

    /**
     * SCIPIO: Gets the product's "main" or "original" product; for variant products -> its virtual,
     * for configurable product -> its original unconfigured product.
     * Added 2018-08-17.
     */
    public static String getMainProductId(Delegator delegator, String productId, boolean useCache) {
        String origProduct = null;
        GenericValue productAssoc = getMainProductAssoc(delegator, productId, useCache);
        if (productAssoc != null) {
            origProduct = productAssoc.getString("productId");
        }
        return origProduct;
    }

    /**
     * SCIPIO: Gets the product's "main" or "original" product; for variant products -> its virtual,
     * for configurable product -> its original unconfigured product.
     * <p>
     * Added 2018-08-17.
     */
    public static String getMainProductId(Delegator delegator, String productId) {
        return getMainProductId(delegator, productId, true);
    }

    /**
     * @deprecated SCIPIO: 2019-02-28: Please use {@link #getMainProductAssoc(Delegator, String, boolean)} instead (name conflict with stock method).
     */
    @Deprecated
    public static GenericValue getOriginalProductAssoc(String productId, Delegator delegator, boolean useCache) {
        return getMainProductAssoc(delegator, productId, useCache);
    }

    /**
     * @deprecated SCIPIO: 2019-02-28: Please use {@link #getMainProduct(Delegator, String, boolean)} instead (name conflict with stock method).
     */
    @Deprecated
    public static GenericValue getOriginalProduct(String productId, Delegator delegator, boolean useCache) { // SCIPIO: added useCache 2017-09-05
        return getMainProduct(delegator, productId, useCache);
    }

    /**
     * @deprecated SCIPIO: 2019-02-28: Please use {@link #getMainProduct(Delegator, String)} instead (name conflict with stock method).
     */
    @Deprecated
    public static GenericValue getOriginalProduct(String productId, Delegator delegator) {
        return getMainProduct(delegator, productId);
    }

    /**
     * @deprecated SCIPIO: 2019-02-28: Please use {@link #getMainProductId(Delegator, String, boolean)} instead (name conflict with stock method).
     */
    @Deprecated
    public static String getOriginalProductId(String productId, Delegator delegator, boolean useCache) { // SCIPIO: added useCache 2017-09-05
        return getMainProductId(delegator, productId, useCache);
    }

    /**
     * @deprecated SCIPIO: 2019-02-28: Please use {@link #getMainProductId(Delegator, String)} instead (name conflict with stock method).
     */
    @Deprecated
    public static String getOriginalProductId(String productId, Delegator delegator) {
        return getMainProductId(delegator, productId);
    }

    /**
     * SCIPIO: Returns true if the product is a configurable product (productTypeId AGGREGATED or AGGREGATED_SERVICE).
     */
    public static boolean isConfigProduct(GenericValue product) {
        return (product != null) && ("AGGREGATED".equals(product.get("productTypeId")) || "AGGREGATED_SERVICE".equals(product.get("productTypeId")));
    }

    /**
     * SCIPIO: Returns true if the product is a configurable product configuration (productTypeId AGGREGATED_CONF or AGGREGATEDSERV_CONF).
     */
    public static boolean isConfigProductConfig(GenericValue product) {
        return (product != null) && ("AGGREGATED_CONF".equals(product.get("productTypeId")) || "AGGREGATEDSERV_CONF".equals(product.get("productTypeId")));
    }

    /**
     * SCIPIO: Returns true if the product is a configurable product OR configuration (productTypeId AGGREGATED, AGGREGATED_SERVICE, AGGREGATED_CONF, AGGREGATEDSERV_CONF).
     */
    public static boolean isConfigProductOrConfig(GenericValue product) {
        return isConfigProduct(product) || isConfigProductConfig(product);
    }
}

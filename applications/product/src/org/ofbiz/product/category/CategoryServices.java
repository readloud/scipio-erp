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
package org.ofbiz.product.category;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.GeneralException;
import org.ofbiz.base.util.PropertyMessage;
import org.ofbiz.base.util.PropertyMessageExUtil;
import org.ofbiz.base.util.UtilDateTime;
import org.ofbiz.base.util.UtilGenerics;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilProperties;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.content.content.LocalizedContentWorker;
import org.ofbiz.entity.Delegator;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.condition.EntityCondition;
import org.ofbiz.entity.condition.EntityOperator;
import org.ofbiz.entity.model.ModelEntity;
import org.ofbiz.entity.util.EntityListIterator;
import org.ofbiz.entity.util.EntityQuery;
import org.ofbiz.entity.util.EntityUtil;
import org.ofbiz.product.catalog.CatalogWorker;
import org.ofbiz.product.product.ProductWorker;
import org.ofbiz.service.DispatchContext;
import org.ofbiz.service.LocalDispatcher;
import org.ofbiz.service.ModelService;
import org.ofbiz.service.ServiceUtil;

/**
 * CategoryServices - Category Services
 */
public class CategoryServices {

    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());
    public static final String resourceError = "ProductErrorUiLabels";

    public static Map<String, Object> getCategoryMembers(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String categoryId = (String) context.get("categoryId");
        Locale locale = (Locale) context.get("locale");
        GenericValue productCategory = null;
        List<GenericValue> members = null;

        try {
            productCategory = EntityQuery.use(delegator).from("ProductCategory").where("productCategoryId", categoryId).cache().queryOne();
            members = EntityUtil.filterByDate(productCategory.getRelated("ProductCategoryMember", null, UtilMisc.toList("sequenceNum"), true), true);
            if (Debug.verboseOn()) Debug.logVerbose("Category: " + productCategory + " Member Size: " + members.size() + " Members: " + members, module);
        } catch (GenericEntityException e) {
            Debug.logError(e, "Problem reading product categories: " + e.getMessage(), module);
            return ServiceUtil.returnError(UtilProperties.getMessage(resourceError, 
                    "categoryservices.problems_reading_category_entity", 
                    UtilMisc.toMap("errMessage", e.getMessage()), locale));
        }
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("category", productCategory);
        result.put("categoryMembers", members);
        return result;
    }

    public static Map<String, Object> getPreviousNextProducts(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String categoryId = (String) context.get("categoryId");
        String productId = (String) context.get("productId");
        boolean activeOnly = (context.get("activeOnly") != null ? (Boolean) context.get("activeOnly") : true);
        Integer index = (Integer) context.get("index");
        Timestamp introductionDateLimit = (Timestamp) context.get("introductionDateLimit");
        Timestamp releaseDateLimit = (Timestamp) context.get("releaseDateLimit");
        Locale locale = (Locale) context.get("locale");

        if (index == null && productId == null) {
            return ServiceUtil.returnFailure(UtilProperties.getMessage(resourceError, "categoryservices.problems_getting_next_products", locale));
        }

        List<String> orderByFields = UtilGenerics.checkList(context.get("orderByFields"));
        if (orderByFields == null) orderByFields = new LinkedList<String>();
        String entityName = getCategoryFindEntityName(delegator, orderByFields, introductionDateLimit, releaseDateLimit);

        GenericValue productCategory;
        List<GenericValue> productCategoryMembers;
        try {
            productCategory = EntityQuery.use(delegator).from("ProductCategory").where("productCategoryId", categoryId).cache().queryOne();
            productCategoryMembers = EntityQuery.use(delegator).from(entityName).where("productCategoryId", categoryId).orderBy(orderByFields).cache(true).queryList();
        } catch (GenericEntityException e) {
            Debug.logInfo(e, "Error finding previous/next product info: " + e.toString(), module);
            return ServiceUtil.returnFailure(UtilProperties.getMessage(resourceError, "categoryservices.error_find_next_products", UtilMisc.toMap("errMessage", e.getMessage()), locale));
        }
        if (activeOnly) {
            productCategoryMembers = EntityUtil.filterByDate(productCategoryMembers, true);
        }
        List<EntityCondition> filterConditions = new LinkedList<EntityCondition>();
        if (introductionDateLimit != null) {
            EntityCondition condition = EntityCondition.makeCondition(EntityCondition.makeCondition("introductionDate", EntityOperator.EQUALS, null), EntityOperator.OR, EntityCondition.makeCondition("introductionDate", EntityOperator.LESS_THAN_EQUAL_TO, introductionDateLimit));
            filterConditions.add(condition);
        }
        if (releaseDateLimit != null) {
            EntityCondition condition = EntityCondition.makeCondition(EntityCondition.makeCondition("releaseDate", EntityOperator.EQUALS, null), EntityOperator.OR, EntityCondition.makeCondition("releaseDate", EntityOperator.LESS_THAN_EQUAL_TO, releaseDateLimit));
            filterConditions.add(condition);
        }
        if (!filterConditions.isEmpty()) {
            productCategoryMembers = EntityUtil.filterByCondition(productCategoryMembers, EntityCondition.makeCondition(filterConditions, EntityOperator.AND));
        }

        if (productId != null && index == null) {
            for (GenericValue v: productCategoryMembers) {
                if (v.getString("productId").equals(productId)) {
                    index = productCategoryMembers.indexOf(v);
                }
            }
        }

        if (index == null) {
            // this is not going to be an error condition because we don't want it to be so critical, ie rolling back the transaction and such
            return ServiceUtil.returnFailure(UtilProperties.getMessage(resourceError, "categoryservices.product_not_found", locale));
        }

        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("category", productCategory);

        String previous = null;
        String next = null;

        if (index - 1 >= 0 && index - 1 < productCategoryMembers.size()) {
            previous = productCategoryMembers.get(index - 1).getString("productId");
            result.put("previousProductId", previous);
        } else {
            previous = productCategoryMembers.get(productCategoryMembers.size() - 1).getString("productId");
            result.put("previousProductId", previous);
        }

        if (index + 1 < productCategoryMembers.size()) {
            next = productCategoryMembers.get(index + 1).getString("productId");
            result.put("nextProductId", next);
        } else {
            next = productCategoryMembers.get(0).getString("productId");
            result.put("nextProductId", next);
        }
        return result;
    }

    private static String getCategoryFindEntityName(Delegator delegator, List<String> orderByFields, Timestamp introductionDateLimit, Timestamp releaseDateLimit) {
        // allow orderByFields to contain fields from the Product entity, if there are such fields
        String entityName = introductionDateLimit == null && releaseDateLimit == null ? "ProductCategoryMember" : "ProductAndCategoryMember";
        if (orderByFields == null) {
            return entityName;
        }
        if (orderByFields.size() == 0) {
            orderByFields.add("sequenceNum");
            orderByFields.add("productId");
        }

        ModelEntity productModel = delegator.getModelEntity("Product");
        ModelEntity productCategoryMemberModel = delegator.getModelEntity("ProductCategoryMember");
        for (String orderByField: orderByFields) {
            // Get the real field name from the order by field removing ascending/descending order
            if (UtilValidate.isNotEmpty(orderByField)) {
                int startPos = 0, endPos = orderByField.length();

                if (orderByField.endsWith(" DESC")) {
                    endPos -= 5;
                } else if (orderByField.endsWith(" ASC")) {
                    endPos -= 4;
                } else if (orderByField.startsWith("-")) {
                    startPos++;
                } else if (orderByField.startsWith("+")) {
                    startPos++;
                }

                if (startPos != 0 || endPos != orderByField.length()) {
                    orderByField = orderByField.substring(startPos, endPos);
                }
            }

            if (!productCategoryMemberModel.isField(orderByField)) {
                if (productModel.isField(orderByField)) {
                    entityName = "ProductAndCategoryMember";
                    // that's what we wanted to find out, so we can quit now
                    break;
                } else {
                    // ahh!! bad field name, don't worry, it will blow up in the query
                }
            }
        }
        return entityName;
    }

    public static Map<String, Object> getProductCategoryAndLimitedMembers(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        LocalDispatcher dispatcher = dctx.getDispatcher();
        String productCategoryId = (String) context.get("productCategoryId");
        boolean limitView = (Boolean) context.get("limitView");
        int defaultViewSize = (Integer) context.get("defaultViewSize");
        Timestamp introductionDateLimit = (Timestamp) context.get("introductionDateLimit");
        Timestamp releaseDateLimit = (Timestamp) context.get("releaseDateLimit");

        List<String> orderByFields = UtilGenerics.checkList(context.get("orderByFields"));
        if (orderByFields == null) orderByFields = new LinkedList<String>();
        String entityName = getCategoryFindEntityName(delegator, orderByFields, introductionDateLimit, releaseDateLimit);

        String prodCatalogId = (String) context.get("prodCatalogId");

        boolean useCacheForMembers = (context.get("useCacheForMembers") == null || (Boolean) context.get("useCacheForMembers"));
        boolean activeOnly = (context.get("activeOnly") == null || (Boolean) context.get("activeOnly"));

        // checkViewAllow defaults to false, must be set to true and pass the prodCatalogId to enable
        boolean checkViewAllow = (prodCatalogId != null && context.get("checkViewAllow") != null &&
                (Boolean) context.get("checkViewAllow"));

        String viewProductCategoryId = null;
        if (checkViewAllow) {
            viewProductCategoryId = CatalogWorker.getCatalogViewAllowCategoryId(delegator, prodCatalogId);
        }

        Timestamp nowTimestamp = UtilDateTime.nowTimestamp();
        int viewIndex = 0;
        if (UtilValidate.isNotEmpty((String) context.get("viewIndexString"))) { // SCIPIO: Added empty test
            try {
                viewIndex = Integer.parseInt((String) context.get("viewIndexString"));
            } catch (NumberFormatException e) { // SCIPIO: Switched: Exception
                //viewIndex = 0; // SCIPIO: redundant
                Debug.logError("getProductCategoryAndLimitedMembers: error parsing viewIndexString: " + e.getMessage(), module); // SCIPIO
            }
        }

        int viewSize = defaultViewSize;
        if (UtilValidate.isNotEmpty((String) context.get("viewSizeString"))) { // SCIPIO: Added empty test
            try {
                viewSize = Integer.parseInt((String) context.get("viewSizeString"));
            } catch (NumberFormatException e) {
                // SCIPIO: unhelpful
                //Debug.logError(e.getMessage(), module);
                Debug.logError("getProductCategoryAndLimitedMembers: error parsing viewSizeString: " + e.getMessage(), module); // SCIPIO
            }
        }

        GenericValue productCategory = null;
        try {
            productCategory = EntityQuery.use(delegator).from("ProductCategory").where("productCategoryId", productCategoryId).cache().queryOne();
        } catch (GenericEntityException e) {
            Debug.logWarning(e.getMessage(), module);
            productCategory = null;
        }

        int listSize = 0;
        int lowIndex = 0;
        int highIndex = 0;

        if (limitView) {
            // get the indexes for the partial list
            lowIndex = ((viewIndex * viewSize) + 1);
            highIndex = (viewIndex + 1) * viewSize;
        } else {
            lowIndex = 0;
            highIndex = 0;
        }

        boolean filterOutOfStock = false;
        try {
            String productStoreId = (String) context.get("productStoreId");
            if (UtilValidate.isNotEmpty(productStoreId)) {
                GenericValue productStore = EntityQuery.use(delegator).from("ProductStore").where("productStoreId", productStoreId).queryOne();
                if (productStore != null && "N".equals(productStore.getString("showOutOfStockProducts"))) {
                    filterOutOfStock = true;
                }
            }
        } catch (GenericEntityException e) {
            Debug.logWarning(e.getMessage(), module);
        }

        List<GenericValue> productCategoryMembers = null;
        if (productCategory != null) {
            try {
                if (useCacheForMembers) {
                    productCategoryMembers = EntityQuery.use(delegator).from(entityName).where("productCategoryId", productCategoryId).orderBy(orderByFields).cache(true).queryList();
                    if (activeOnly) {
                        productCategoryMembers = EntityUtil.filterByDate(productCategoryMembers, true);
                    }
                    List<EntityCondition> filterConditions = new LinkedList<EntityCondition>();
                    if (introductionDateLimit != null) {
                        EntityCondition condition = EntityCondition.makeCondition(EntityCondition.makeCondition("introductionDate", EntityOperator.EQUALS, null), EntityOperator.OR, EntityCondition.makeCondition("introductionDate", EntityOperator.LESS_THAN_EQUAL_TO, introductionDateLimit));
                        filterConditions.add(condition);
                    }
                    if (releaseDateLimit != null) {
                        EntityCondition condition = EntityCondition.makeCondition(EntityCondition.makeCondition("releaseDate", EntityOperator.EQUALS, null), EntityOperator.OR, EntityCondition.makeCondition("releaseDate", EntityOperator.LESS_THAN_EQUAL_TO, releaseDateLimit));
                        filterConditions.add(condition);
                    }
                    if (!filterConditions.isEmpty()) {
                        productCategoryMembers = EntityUtil.filterByCondition(productCategoryMembers, EntityCondition.makeCondition(filterConditions, EntityOperator.AND));
                    }

                    // filter out of stock products
                    if (filterOutOfStock) {
                        try {
                            productCategoryMembers = ProductWorker.filterOutOfStockProducts(productCategoryMembers, dispatcher, delegator);
                        } catch (GeneralException e) {
                            Debug.logWarning("Problem filtering out of stock products :"+e.getMessage(), module);
                        }
                    }
                    // filter out the view allow before getting the sublist
                    if (UtilValidate.isNotEmpty(viewProductCategoryId)) {
                        productCategoryMembers = CategoryWorker.filterProductsInCategory(delegator, productCategoryMembers, viewProductCategoryId);
                        listSize = productCategoryMembers.size();
                    }

                    // set the index and size
                    listSize = productCategoryMembers.size();
                    if (highIndex > listSize) {
                        highIndex = listSize;
                    }

                    // get only between low and high indexes
                    if (limitView) {
                        if (UtilValidate.isNotEmpty(productCategoryMembers)) {
                            productCategoryMembers = productCategoryMembers.subList(lowIndex-1, highIndex);
                        }
                    } else {
                        lowIndex = 1;
                        highIndex = listSize;
                    }
                } else {
                    List<EntityCondition> mainCondList = new LinkedList<EntityCondition>();
                    mainCondList.add(EntityCondition.makeCondition("productCategoryId", EntityOperator.EQUALS, productCategory.getString("productCategoryId")));
                    if (activeOnly) {
                        mainCondList.add(EntityCondition.makeCondition("fromDate", EntityOperator.LESS_THAN_EQUAL_TO, nowTimestamp));
                        mainCondList.add(EntityCondition.makeCondition(EntityCondition.makeCondition("thruDate", EntityOperator.EQUALS, null), EntityOperator.OR, EntityCondition.makeCondition("thruDate", EntityOperator.GREATER_THAN, nowTimestamp)));
                    }
                    if (introductionDateLimit != null) {
                        mainCondList.add(EntityCondition.makeCondition(EntityCondition.makeCondition("introductionDate", EntityOperator.EQUALS, null), EntityOperator.OR, EntityCondition.makeCondition("introductionDate", EntityOperator.LESS_THAN_EQUAL_TO, introductionDateLimit)));
                    }
                    if (releaseDateLimit != null) {
                        mainCondList.add(EntityCondition.makeCondition(EntityCondition.makeCondition("releaseDate", EntityOperator.EQUALS, null), EntityOperator.OR, EntityCondition.makeCondition("releaseDate", EntityOperator.LESS_THAN_EQUAL_TO, releaseDateLimit)));
                    }
                    EntityCondition mainCond = EntityCondition.makeCondition(mainCondList, EntityOperator.AND);

                    // set distinct on using list iterator
                    EntityQuery eq = EntityQuery.use(delegator)
                            .from(entityName)
                            .where(mainCond)
                            .orderBy(orderByFields)
                            .cursorScrollInsensitive()
                            .maxRows(highIndex);

                    try (EntityListIterator pli = eq.queryIterator()) {
                        // get the partial list for this page
                        if (limitView) {
                            if (viewProductCategoryId != null) {
                                // do manual checking to filter view allow
                                productCategoryMembers = new LinkedList<GenericValue>();
                                GenericValue nextValue;
                                int chunkSize = 0;
                                listSize = 0;

                                while ((nextValue = pli.next()) != null) {
                                    String productId = nextValue.getString("productId");
                                    if (CategoryWorker.isProductInCategory(delegator, productId, viewProductCategoryId)) {
                                        if (listSize + 1 >= lowIndex && chunkSize < viewSize) {
                                            productCategoryMembers.add(nextValue);
                                            chunkSize++;
                                        }
                                        listSize++;
                                    }
                                }
                            } else {
                                productCategoryMembers = pli.getPartialList(lowIndex, viewSize);
                                listSize = pli.getResultsSizeAfterPartialList();
                            }
                        } else {
                            productCategoryMembers = pli.getCompleteList();
                            if (UtilValidate.isNotEmpty(viewProductCategoryId)) {
                                // filter out the view allow
                                productCategoryMembers = CategoryWorker.filterProductsInCategory(delegator, productCategoryMembers, viewProductCategoryId);
                            }

                            listSize = productCategoryMembers.size();
                            lowIndex = 1;
                            highIndex = listSize;
                        }
                    }
                    // filter out of stock products
                    if (filterOutOfStock) {
                        try {
                            productCategoryMembers = ProductWorker.filterOutOfStockProducts(productCategoryMembers, dispatcher, delegator);
                            listSize = productCategoryMembers.size();
                        } catch (GeneralException e) {
                            Debug.logWarning("Problem filtering out of stock products :"+e.getMessage(), module);
                        }
                    }

                    // null safety
                    if (productCategoryMembers == null) {
                        productCategoryMembers = new LinkedList<GenericValue>();
                    }

                    if (highIndex > listSize) {
                        highIndex = listSize;
                    }
                }
            } catch (GenericEntityException e) {
                Debug.logError(e, module);
            }
        }

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("viewIndex", viewIndex);
        result.put("viewSize", viewSize);
        result.put("lowIndex", lowIndex);
        result.put("highIndex", highIndex);
        result.put("listSize", listSize);
        if (productCategory != null) result.put("productCategory", productCategory);
        if (productCategoryMembers != null) result.put("productCategoryMembers", productCategoryMembers);
        return result;
    }

    /**
     * @deprecated SCIPIO: To be removed in future (TODO) - use
     * {@link com.ilscipio.scipio.product.category.CategoryEvents#getChildCategoryTree(HttpServletRequest, HttpServletResponse)}
     * or buildCategoryTree service instead.
     */
    @Deprecated
    public static String getChildCategoryTree(HttpServletRequest request, HttpServletResponse response) {
        return com.ilscipio.scipio.product.category.CategoryEvents.getChildCategoryTree(request, response);
    }

    /**
     * SCIPIO: getProductCategoryContentLocalizedSimpleTextViews.
     * Added 2017-10-27.
     */
    public static Map<String, Object> getProductCategoryContentLocalizedSimpleTextViews(DispatchContext dctx, Map<String, ?> context) {
        Delegator delegator = dctx.getDelegator();
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Locale locale = (Locale) context.get("locale");

        String productCategoryId = (String) context.get("productCategoryId");
        Collection<String> prodCatContentTypeIdList = UtilGenerics.checkCollection(context.get("prodCatContentTypeIdList"));
        boolean filterByDate = !Boolean.FALSE.equals(context.get("filterByDate"));
        boolean useCache = Boolean.TRUE.equals(context.get("useCache"));

        Map<String, List<GenericValue>> viewsByType = null;
        try {
            viewsByType = CategoryWorker.getProductCategoryContentLocalizedSimpleTextViews(delegator, dispatcher,
                    productCategoryId, prodCatContentTypeIdList, filterByDate ? UtilDateTime.nowTimestamp() : null, useCache);
            Map<String, Object> result = ServiceUtil.returnSuccess();
            postprocessProductCategoryContentLocalizedSimpleTextContentAssocViews(dctx, context, viewsByType, result);
            return result;
        } catch (Exception e) {
            PropertyMessage msgIntro = PropertyMessage.makeWithVars("ProductErrorUiLabels",
                    "productservices.error_reading_ProductCategoryContent_simple_texts_for_alternate_locale_for_category",
                    "productCategoryId", productCategoryId);
            Debug.logError(e, PropertyMessageExUtil.makeLogMessage(msgIntro, e), module);
            return ServiceUtil.returnFailure(msgIntro, e, locale);
        }
    }

    public static void postprocessProductCategoryContentLocalizedSimpleTextContentAssocViews(DispatchContext dctx, Map<String, ?> context,
            Map<String, List<GenericValue>> viewsByType, Map<String, Object> result) {
        if (!Boolean.FALSE.equals(context.get("getViewsByType"))) {
            result.put("viewsByType", viewsByType);
        }
        if (Boolean.TRUE.equals(context.get("getViewsByTypeAndLocale"))) {
            result.put("viewsByTypeAndLocale", LocalizedContentWorker.splitContentLocalizedSimpleTextContentAssocViewsByLocale(viewsByType));
        }
        if (Boolean.TRUE.equals(context.get("getTextByTypeAndLocale"))) {
            result.put("textByTypeAndLocale", LocalizedContentWorker.extractContentLocalizedSimpleTextDataByLocale(viewsByType));
        }
    }

    /**
     * SCIPIO: replaceProductCategoryContentLocalizedSimpleTexts.
     * Added 2017-12-06.
     */
    public static Map<String, Object> replaceProductCategoryContentLocalizedSimpleTexts(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Locale locale = (Locale) context.get("locale");
        String productCategoryId = (String) context.get("productCategoryId");

        try {
            GenericValue productCategory = delegator.findOne("ProductCategory", UtilMisc.toMap("productCategoryId", productCategoryId), false);
            if (productCategory == null) {
                throw new IllegalArgumentException(UtilProperties.getMessage("ProductUiLabels", "ProductCategoryNotFoundForCategoryID", locale) + ": " + productCategoryId);
            }

            Map<String, Object> contentFieldsUnparsed = UtilGenerics.checkMap(context.get("contentFields"));
            Map<String, List<Map<String, Object>>> contentFields = LocalizedContentWorker.parseLocalizedSimpleTextContentFieldParams(contentFieldsUnparsed, null, true);

            for(Map.Entry<String, List<Map<String, Object>>> entry : contentFields.entrySet()) {
                String prodCatContentTypeId = entry.getKey();
                List<Map<String, Object>> entries = entry.getValue();

                List<GenericValue> productCategoryContentList = EntityQuery.use(delegator).from("ProductCategoryContent")
                        .where("productCategoryId", productCategoryId, "prodCatContentTypeId", prodCatContentTypeId).filterByDate()
                        .orderBy("-fromDate").queryList();
                GenericValue productCategoryContent = EntityUtil.getFirst(productCategoryContentList);
                if (productCategoryContentList.size() > 1) {
                    Debug.logWarning("replaceProductCategoryContentLocalizedSimpleTexts: Multiple active ProductCategoryContent found for prodCatContentTypeId '"
                            + prodCatContentTypeId + "' for category '" + productCategoryId + "'; updating only latest (contentId: '" + productCategoryContent.getString("contentId") + "')", module);
                }

                String mainContentId = null;
                if (productCategoryContent != null) {
                    mainContentId = productCategoryContent.getString("contentId");
                }

                Map<String, Object> servCtx = dctx.makeValidContext("replaceContentLocalizedSimpleTexts", ModelService.IN_PARAM, context);
                servCtx.put("mainContentId", mainContentId);
                servCtx.put("entries", entries);
                Map<String, Object> servResult = dispatcher.runSync("replaceContentLocalizedSimpleTexts", servCtx);
                if (!ServiceUtil.isSuccess(servResult)) {
                    return ServiceUtil.returnError(getReplStcAltLocErrorPrefix(context, locale) + ": " + ServiceUtil.getErrorMessage(servResult));
                }
                if (mainContentId == null && servResult.get("mainContentId") != null) {
                    // must create a new ProductContent record
                    mainContentId = (String) servResult.get("mainContentId");

                    productCategoryContent = delegator.makeValue("ProductCategoryContent");
                    productCategoryContent.put("productCategoryId", productCategoryId);
                    productCategoryContent.put("contentId", mainContentId);
                    productCategoryContent.put("prodCatContentTypeId", prodCatContentTypeId);
                    productCategoryContent.put("fromDate", UtilDateTime.nowTimestamp());
                    productCategoryContent = delegator.create(productCategoryContent);
                } else if (servResult.get("mainContentId") != null && Boolean.TRUE.equals(servResult.get("allContentEmpty"))) {
                    mainContentId = (String) servResult.get("mainContentId");
                    if (Boolean.TRUE.equals(context.get("mainContentDelete"))) {
                        delegator.removeByAnd("ProductCategoryContent", UtilMisc.toMap("contentId", mainContentId));
                        LocalizedContentWorker.removeContentAndRelated(delegator, dispatcher, context, mainContentId);
                    }
                }
            }
            return ServiceUtil.returnSuccess();
        } catch(Exception e) {
            Debug.logError(e, getReplStcAltLocErrorPrefix(context, Locale.ENGLISH) + ": " + e.getMessage(), module);
            return ServiceUtil.returnError(getReplStcAltLocErrorPrefix(context, locale) + ": " + e.getMessage());
        }
    }

    private static String getReplStcAltLocErrorPrefix(Map<String, ?> context, Locale locale) {
        return UtilProperties.getMessage("ProductErrorUiLabels", "productservices.error_updating_ProductCategoryContent_simple_texts_for_alternate_locale_for_category",
                context, locale);
    }
}

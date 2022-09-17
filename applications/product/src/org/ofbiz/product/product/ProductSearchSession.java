/*
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
 */
package org.ofbiz.product.product;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionEvent;

import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.UtilDateTime;
import org.ofbiz.base.util.UtilGenerics;
import org.ofbiz.base.util.UtilHttp;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilProperties;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.entity.Delegator;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.condition.EntityCondition;
import org.ofbiz.entity.condition.EntityOperator;
import org.ofbiz.entity.model.DynamicViewEntity;
import org.ofbiz.entity.model.ModelKeyMap;
import org.ofbiz.entity.util.EntityListIterator;
import org.ofbiz.entity.util.EntityQuery;
import org.ofbiz.entity.util.EntityUtilProperties;
import org.ofbiz.product.catalog.CatalogWorker;
import org.ofbiz.product.category.CategoryWorker;
import org.ofbiz.product.feature.ParametricSearch;
import org.ofbiz.product.product.ProductSearch.CategoryConstraint;
import org.ofbiz.product.product.ProductSearch.FeatureConstraint;
import org.ofbiz.product.product.ProductSearch.KeywordConstraint;
import org.ofbiz.product.product.ProductSearch.ProductSearchConstraint;
import org.ofbiz.product.product.ProductSearch.ProductSearchContext;
import org.ofbiz.product.product.ProductSearch.ResultSortOrder;
import org.ofbiz.product.product.ProductSearch.SortKeywordRelevancy;
import org.ofbiz.product.store.ProductStoreWorker;
import org.ofbiz.webapp.control.RequestHandler;
import org.ofbiz.webapp.stats.VisitHandler;

/**
 * Utility class with methods to prepare and perform ProductSearch operations in the content of an HttpSession
 */
public class ProductSearchSession {

    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());

    // SCIPIO: NOTE: 2018-11-27: All LinkedList changed to ArrayList (a few may have been already ArrayList).

    /**
     * Product search options recording class. Stored in session.
     * <p>
     * SCIPIO: WARNING: 2018-11-27: ProductSearchOptions instance in session is now assumed to be immutable.
     * Do not call any of the methods on ProductSearchOptions that modify the 
     * instance outside of a {@link SearchOptionsUpdate} wrapped block -
     * see {@link ProductSearchSession#processSearchParameters(Map, HttpServletRequest)} for example;
     * to keep it simple, it's best to leave it to <code>processSearchParameters</code> to do all the updates.
     */
    @SuppressWarnings("serial")
    public static class ProductSearchOptions implements java.io.Serializable {
        /**
         * SCIPIO: Used to bypass the session variable lookup.
         * Added 2018-11-27.
         */
        private static final ThreadLocal<ProductSearchOptions> currentOptions = new ThreadLocal<>();

        // SCIPIO: 2018-11-27: Initializations moved to constructor
   
        protected List<ProductSearchConstraint> constraintList; // = null;
        protected String topProductCategoryId; // = null;
        protected ResultSortOrder resultSortOrder; // = new SortKeywordRelevancy(); // SCIPIO: 2018-11-27: Added init, moved from getResultSortOrder
        protected Integer viewIndex; // = null;
        protected Integer viewSize; // = null;
        protected boolean changed; // = false;
        protected String paging; // = "Y";
        protected Integer previousViewSize; // = null;

        public ProductSearchOptions() {
            this.resultSortOrder = new SortKeywordRelevancy(); // SCIPIO: 2018-11-27: Added init, moved from getResultSortOrder
            this.paging = "Y";
        }

        /** Basic copy constructor */
        public ProductSearchOptions(ProductSearchOptions productSearchOptions) {
            // SCIPIO: rewrote constraintList copy
            this.constraintList = (productSearchOptions.constraintList != null) ? new ArrayList<>(productSearchOptions.constraintList) : null;
            this.topProductCategoryId = productSearchOptions.topProductCategoryId;
            this.resultSortOrder = productSearchOptions.resultSortOrder;
            this.viewIndex = productSearchOptions.viewIndex;
            this.viewSize = productSearchOptions.viewSize;
            this.changed = productSearchOptions.changed;
            this.paging = productSearchOptions.paging;
            this.previousViewSize = productSearchOptions.previousViewSize;
        }

        public List<ProductSearchConstraint> getConstraintList() {
            return this.constraintList;
        }
        public static List<ProductSearchConstraint> getConstraintList(HttpSession session) {
            return getProductSearchOptions(session).constraintList;
        }
        public static void addConstraint(ProductSearchConstraint productSearchConstraint, HttpSession session) {
            ProductSearchOptions productSearchOptions = getProductSearchOptions(session);
            if (productSearchOptions.constraintList == null) {
                productSearchOptions.constraintList = new ArrayList<>();
            }
            if (!productSearchOptions.constraintList.contains(productSearchConstraint)) {
                productSearchOptions.constraintList.add(productSearchConstraint);
                productSearchOptions.changed = true;
            }
        }

        /**
         * SCIPIO: Removes constraints by class type.
         * Added 2017-09-14.
         */
        public static void removeConstraintsByType(Class<? extends ProductSearchConstraint> constraintCls, HttpSession session) {
            ProductSearchOptions productSearchOptions = getProductSearchOptions(session);
            if (productSearchOptions.constraintList == null) {
                productSearchOptions.constraintList = new ArrayList<>();
            }
            Iterator<ProductSearchConstraint> it = productSearchOptions.constraintList.iterator();
            while(it.hasNext()) {
                ProductSearchConstraint constraint = it.next();
                if (constraintCls.isAssignableFrom(constraint.getClass())) it.remove();
            }
        }

        public ResultSortOrder getResultSortOrder() {
            // SCIPIO: 2018-11-27: changed flag makes no sense here, plus this is thread-unfriendly
            //if (this.resultSortOrder == null) {
            //    this.resultSortOrder = new SortKeywordRelevancy();
            //    this.changed = true;
            //}
            return this.resultSortOrder;
        }
        public static ResultSortOrder getResultSortOrder(HttpServletRequest request) {
            ProductSearchOptions productSearchOptions = getProductSearchOptions(request.getSession());
            return productSearchOptions.getResultSortOrder();
        }
        public static void setResultSortOrder(ResultSortOrder resultSortOrder, HttpSession session) {
            ProductSearchOptions productSearchOptions = getProductSearchOptions(session);
            productSearchOptions.resultSortOrder = resultSortOrder;
            productSearchOptions.changed = true;
        }

        public static void clearSearchOptions(HttpSession session) {
            ProductSearchOptions productSearchOptions = getProductSearchOptions(session);
            productSearchOptions.constraintList = null;
            productSearchOptions.topProductCategoryId = null;
            productSearchOptions.resultSortOrder = null;
        }

        public void clearViewInfo() {
            this.viewIndex = null;
            this.viewSize = null;
            this.paging = "Y";
            this.previousViewSize = null;
        }

        /**
         * Get the view size
         * @return returns the viewIndex.
         */
        public Integer getViewIndex() {
            return viewIndex;
        }
        /**
         * Set the view index
         * @param viewIndex the viewIndex to set.
         */
        public void setViewIndex(Integer viewIndex) {
            this.viewIndex = viewIndex;
        }
        /**
         * Set the view index
         * @param viewIndexStr the viewIndex to set.
         */
        public void setViewIndex(String viewIndexStr) {
            if (UtilValidate.isEmpty(viewIndexStr)) {
                return;
            }
            try {
                this.setViewIndex(Integer.valueOf(viewIndexStr));
            } catch (Exception e) {
                Debug.logError(e, "Error in formatting of VIEW_INDEX [" + viewIndexStr + "], setting to 20", module);
                if (this.viewIndex == null) {
                    this.setViewIndex(20);
                }
            }
        }

        /**
         * Get the view size
         * @return returns the view size.
         */
        public Integer getViewSize() {
            return viewSize;
        }

        /**
         * Set the view size
         * @param viewSize the view size to set.
         */
        public void setViewSize(Integer viewSize) {
            setPreviousViewSize(getViewSize());
            this.viewSize = viewSize;
        }

        /**
         * Set the view size
         * @param viewSizeStr the view size to set.
         */
        public void setViewSize(String viewSizeStr) {
            if (UtilValidate.isEmpty(viewSizeStr)) {
                return;
            }
            try {
                this.setViewSize(Integer.valueOf(viewSizeStr));
            } catch (Exception e) {
                Debug.logError(e, "Error in formatting of VIEW_SIZE [" + viewSizeStr + "], setting to 20", module);
                if (this.viewSize == null) {
                    this.setViewSize(20);
                }
            }
        }

        /**
         * Get the paging
         * @return Returns the paging
         */
        public String getPaging() {
            return paging;
        }

        /**
         * Set the paging
         * @param paging the paging to set
         */
        public void setPaging(String paging) {
            if (paging == null) {
                paging = "Y";
            }
            this.paging = paging;
        }

        /**
         * Get the previous view size
         * @return returns the previous view size
         */
        public Integer getPreviousViewSize() {
            return previousViewSize;
        }
        /**
         * Set the previous view size
         * @param previousViewSize the previousViewSize to set.
         */
        public void setPreviousViewSize(Integer previousViewSize) {
            if (previousViewSize == null) {
                this.previousViewSize = 20;
            } else {
                this.previousViewSize = previousViewSize;
            }
        }

        public String getTopProductCategoryId() {
            return topProductCategoryId;
        }

        public static void setTopProductCategoryId(String topProductCategoryId, HttpSession session) {
            ProductSearchOptions productSearchOptions = getProductSearchOptions(session);
            productSearchOptions.setTopProductCategoryId(topProductCategoryId);
        }

        public void setTopProductCategoryId(String topProductCategoryId) {
            if (this.topProductCategoryId != null && topProductCategoryId != null) {
                if (!this.topProductCategoryId.equals(topProductCategoryId)) {
                    this.topProductCategoryId = topProductCategoryId;
                    this.changed = true;
                }
            } else {
                if (this.topProductCategoryId != null || topProductCategoryId != null) {
                    this.topProductCategoryId = topProductCategoryId;
                    this.changed = true;
                }
            }
        }

        public List<String> searchGetConstraintStrings(boolean detailed, Delegator delegator, Locale locale) {
            List<ProductSearchConstraint> productSearchConstraintList = this.getConstraintList();
            List<String> constraintStrings = new ArrayList<>();
            if (productSearchConstraintList == null) {
                return constraintStrings;
            }
            for (ProductSearchConstraint productSearchConstraint: productSearchConstraintList) {
                if (productSearchConstraint == null) {
                    continue;
                }
                String constraintString = productSearchConstraint.prettyPrintConstraint(delegator, detailed, locale);
                if (UtilValidate.isNotEmpty(constraintString)) {
                    constraintStrings.add(constraintString);
                } else {
                    constraintStrings.add("Description not available");
                }
            }
            return constraintStrings;
        }

        /**
         * SCIPIO: Returns (only) the keyword constraints.
         * Added 2017-08-24.
         */
        public List<KeywordConstraint> getKeywordConstraints() {
            return getConstraintsByType(KeywordConstraint.class);
        }

        /**
         * SCIPIO: Returns (only) the constraints of the given class.
         * Added 2017-08-24.
         */
        public <T extends ProductSearchConstraint> List<T> getConstraintsByType(Class<T> constraintCls) {
            return Collections.unmodifiableList(extractConstraints(getConstraintList(), constraintCls));
        }

        /**
         * SCIPIO: Returns (only) the constraints of specified class.
         * Added 2017-08-24.
         */
        @SuppressWarnings("unchecked")
        protected static <T> List<T> extractConstraints(List<? extends ProductSearchConstraint> contraintList, Class<T> constraintCls) {
            List<T> kwcList = new ArrayList<>();
            if (contraintList != null) {
                for(ProductSearchConstraint constraint : contraintList) {
                    if (constraintCls.isAssignableFrom(constraint.getClass())) kwcList.add((T) constraint);
                }
            }
            return kwcList;
        }
    }

    public static ProductSearchOptions getProductSearchOptions(HttpServletRequest request) { // SCIPIO: new overload
        return getProductSearchOptions(request.getSession());
    }

    // SCIPIO: TODO?: This overload should ultimately be deprecated and removed, but cannot do it now...
    ///**
    // * @deprecated SCIPIO: 2018-11-27: Use {@link #getProductSearchOptions(HttpServletRequest)} instead.
    // */
    //@Deprecated
    public static ProductSearchOptions getProductSearchOptions(HttpSession session) {
        // SCIPIO: 2018-11-27: Update code may use thread-local to bypass lack of request argument
        ProductSearchOptions productSearchOptions = ProductSearchOptions.currentOptions.get();
        if (productSearchOptions == null) {
            productSearchOptions = (ProductSearchOptions) session.getAttribute("_PRODUCT_SEARCH_OPTIONS_CURRENT_");
            if (productSearchOptions == null) {
                synchronized (ProductSearchSession.getSyncObject(session)) { // SCIPIO
                    productSearchOptions = (ProductSearchOptions) session.getAttribute("_PRODUCT_SEARCH_OPTIONS_CURRENT_");
                    if (productSearchOptions == null) {
                        productSearchOptions = new ProductSearchOptions();
                        session.setAttribute("_PRODUCT_SEARCH_OPTIONS_CURRENT_", productSearchOptions);
                    }
                }
            }
        }
        return productSearchOptions;
    }

    public static ProductSearchOptions getProductSearchOptionsIfExist(HttpServletRequest request) { // SCIPIO
        ProductSearchOptions productSearchOptions = ProductSearchOptions.currentOptions.get();
        if (productSearchOptions == null) {
            productSearchOptions = (ProductSearchOptions) request.getSession().getAttribute("_PRODUCT_SEARCH_OPTIONS_CURRENT_");
        }
        return productSearchOptions;
    }

    private static ProductSearchOptions getProductSearchOptionsIfExist(HttpSession session) { // SCIPIO
        ProductSearchOptions productSearchOptions = ProductSearchOptions.currentOptions.get();
        if (productSearchOptions == null) {
            productSearchOptions = (ProductSearchOptions) session.getAttribute("_PRODUCT_SEARCH_OPTIONS_CURRENT_");
        }
        return productSearchOptions;
    }
    
    @SuppressWarnings("unused")
    private static ProductSearchOptions getProductSearchOptionsCopyOrNew(HttpServletRequest request) { // SCIPIO
        ProductSearchOptions options = getProductSearchOptionsIfExist(request);
        return (options != null) ? new ProductSearchOptions(options) : new ProductSearchOptions();
    }

    private static ProductSearchOptions getProductSearchOptionsCopyOrNew(HttpSession session) { // SCIPIO
        ProductSearchOptions options = getProductSearchOptionsIfExist(session);
        return (options != null) ? new ProductSearchOptions(options) : new ProductSearchOptions();
    }

    @SuppressWarnings("unused")
    private static void setProductSearchOptions(HttpServletRequest request, ProductSearchOptions options) { // SCIPIO
        setProductSearchOptions(request.getSession(), options);
    }

    private static void setProductSearchOptions(HttpSession session, ProductSearchOptions options) { // SCIPIO
        session.setAttribute("_PRODUCT_SEARCH_OPTIONS_CURRENT_", options);
    }
    
    public static void checkSaveSearchOptionsHistory(HttpSession session) {
        ProductSearchOptions productSearchOptions = getProductSearchOptions(session);
        // if the options have changed since the last search, add it to the beginning of the search options history
        if (productSearchOptions.changed) {
            synchronized (ProductSearchSession.getSyncObject(session)) { // SCIPIO
                List<ProductSearchOptions> optionsHistoryList = getSearchOptionsHistoryList(session);

                // SCIPIO: 2018-11-27: clone and re-store the list
                //optionsHistoryList.add(0, new ProductSearchOptions(productSearchOptions));
                optionsHistoryList = new ArrayList<>(optionsHistoryList);
                optionsHistoryList.add(0, new ProductSearchOptions(productSearchOptions));
                session.setAttribute("_PRODUCT_SEARCH_OPTIONS_HISTORY_", Collections.unmodifiableList(optionsHistoryList));

                // SCIPIO: 2018-11-27: Cannot edit in-place; make another copy and switch the instance
                //productSearchOptions.changed = false;
                setProductSearchOptions(session, new ProductSearchOptions(productSearchOptions));
            }
        }
    }

    /**
     * getSearchOptionsHistoryList.
     * <p>
     * SCIPIO: NOTE: 2018-11-27: The returned list is now immutable.
     */
    public static List<ProductSearchOptions> getSearchOptionsHistoryList(HttpSession session) {
        List<ProductSearchOptions> optionsHistoryList = UtilGenerics.checkList(session.getAttribute("_PRODUCT_SEARCH_OPTIONS_HISTORY_"));
        if (optionsHistoryList == null) {
            synchronized (ProductSearchSession.getSyncObject(session)) { // SCIPIO
                optionsHistoryList = UtilGenerics.checkList(session.getAttribute("_PRODUCT_SEARCH_OPTIONS_HISTORY_"));
                if (optionsHistoryList == null) {
                    optionsHistoryList = Collections.emptyList(); // SCIPIO: enforce unmodifiable on this one
                    session.setAttribute("_PRODUCT_SEARCH_OPTIONS_HISTORY_", optionsHistoryList);
                }
            }
        }
        return optionsHistoryList;
    }

    public static void clearSearchOptionsHistoryList(HttpSession session) {
        synchronized (ProductSearchSession.getSyncObject(session)) { // SCIPIO
            session.removeAttribute("_PRODUCT_SEARCH_OPTIONS_HISTORY_");
        }
    }

    public static void setCurrentSearchFromHistory(int index, boolean removeOld, HttpSession session) {
        synchronized (ProductSearchSession.getSyncObject(session)) { // SCIPIO
        List<ProductSearchOptions> searchOptionsHistoryList = getSearchOptionsHistoryList(session);
        if (index < searchOptionsHistoryList.size()) {
            ProductSearchOptions productSearchOptions = searchOptionsHistoryList.get(index);
            if (removeOld) {
                // SCIPIO: do not edit this list in-place
                //searchOptionsHistoryList.remove(index);
                searchOptionsHistoryList = new ArrayList<>(searchOptionsHistoryList);
                searchOptionsHistoryList.remove(index);
                session.setAttribute("_PRODUCT_SEARCH_OPTIONS_HISTORY_", Collections.unmodifiableList(searchOptionsHistoryList));
            }
            if (productSearchOptions != null) {
                session.setAttribute("_PRODUCT_SEARCH_OPTIONS_CURRENT_", new ProductSearchOptions(productSearchOptions));
            }
        } else {
            throw new IllegalArgumentException("Could not set current search options to history index [" + index + "], only [" + searchOptionsHistoryList.size() + "] entries in the history list.");
        }
        }
    }

    public static String clearSearchOptionsHistoryList(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession();
        clearSearchOptionsHistoryList(session);
        return "success";
    }

    public static String setCurrentSearchFromHistory(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession();
        String searchHistoryIndexStr = request.getParameter("searchHistoryIndex");
        String removeOldStr = request.getParameter("removeOld");

        if (UtilValidate.isEmpty(searchHistoryIndexStr)) {
            request.setAttribute("_ERROR_MESSAGE_", "No search history index passed, cannot set current search to previous.");
            return "error";
        }

        try {
            int searchHistoryIndex = Integer.parseInt(searchHistoryIndexStr);
            boolean removeOld = true;
            if (UtilValidate.isNotEmpty(removeOldStr)) {
                removeOld = !"false".equals(removeOldStr);
            }
            setCurrentSearchFromHistory(searchHistoryIndex, removeOld, session);
        } catch (Exception e) {
            request.setAttribute("_ERROR_MESSAGE_", e.toString());
            return "error";
        }

        return "success";
    }

    /** A ControlServlet event method used to check to see if there is an override for any of the current keywords in the search */
    public static final String checkDoKeywordOverride(HttpServletRequest request, HttpServletResponse response) {
        return checkDoKeywordOverride(request, response, DefaultKeywordOverrideHandler.INSTANCE); // SCIPIO: now delegating
    }

    /** A ControlServlet event method used to check to see if there is an override for any of the current keywords in the search */
    public static final String checkDoKeywordOverride(HttpServletRequest request, HttpServletResponse response, KeywordOverrideHandler handler) { // SCIPIO: CheckDoKeywordOverrideHandler
        HttpSession session = request.getSession();
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        Map<String, Object> requestParams = UtilHttp.getParameterMap(request);
        ProductSearchSession.processSearchParameters(requestParams, request);

        // get the current productStoreId
        String productStoreId = ProductStoreWorker.getProductStoreId(request);
        if (productStoreId != null) {
            // get a Set of all keywords in the search, if there are any...
            Set<String> keywords = new HashSet<>();
            List<ProductSearchConstraint> constraintList = ProductSearchOptions.getConstraintList(session);
            if (constraintList != null) {
                for (ProductSearchConstraint constraint: constraintList) {
                    if (constraint instanceof KeywordConstraint) {
                        KeywordConstraint keywordConstraint = (KeywordConstraint) constraint;
                        Set<String> keywordSet = keywordConstraint.makeFullKeywordSet(delegator);
                        if (keywordSet != null) {
                            keywords.addAll(keywordSet);
                        }
                    }
                }
            }

            if (keywords.size() > 0) {
                List<GenericValue> productStoreKeywordOvrdList = null;
                try {
                    productStoreKeywordOvrdList = EntityQuery.use(delegator).from("ProductStoreKeywordOvrd").where("productStoreId", productStoreId).orderBy("-fromDate").cache(true).filterByDate().queryList();
                } catch (GenericEntityException e) {
                    Debug.logError(e, "Error reading ProductStoreKeywordOvrd list, not doing keyword override", module);
                }

                if (UtilValidate.isNotEmpty(productStoreKeywordOvrdList)) {
                    for (GenericValue productStoreKeywordOvrd: productStoreKeywordOvrdList) {
                        String ovrdKeyword = productStoreKeywordOvrd.getString("keyword");
                        if (keywords.contains(ovrdKeyword)) {
                            String targetTypeEnumId = productStoreKeywordOvrd.getString("targetTypeEnumId");
                            String target = productStoreKeywordOvrd.getString("target");
                            // SCIPIO: leave to handler
                            //ServletContext ctx = request.getServletContext(); // SCIPIO: get context using servlet API 3.0
                            //RequestHandler rh = (RequestHandler) ctx.getAttribute("_REQUEST_HANDLER_");
                            if ("KOTT_PRODCAT".equals(targetTypeEnumId)) {
                                target = handler.handleCategoryLink(request, response, target, ovrdKeyword, productStoreKeywordOvrd); // SCIPIO: handler
                            } else if ("KOTT_PRODUCT".equals(targetTypeEnumId)) {
                                target = handler.handleProductLink(request, response, target, ovrdKeyword, productStoreKeywordOvrd); // SCIPIO: handler
                            } else if ("KOTT_OFBURL".equals(targetTypeEnumId)) {
                                target = handler.handleNavLink(request, response, target, ovrdKeyword, productStoreKeywordOvrd); // SCIPIO: handler
                            } else if ("KOTT_AURL".equals(targetTypeEnumId)) {
                                target = handler.handleAbsoluteLink(request, response, target, ovrdKeyword, productStoreKeywordOvrd); // SCIPIO: handler
                            } else {
                                target = handler.handleOther(request, response, target, ovrdKeyword, productStoreKeywordOvrd, targetTypeEnumId); // SCIPIO: handler
                            }
                            if (target != null) {
                                try {
                                    response.sendRedirect(target);
                                    return "none";
                                } catch (IOException e) {
                                    Debug.logError(e, "Could not send redirect to: " + target, module);
                                    continue;
                                }
                            }
                        }
                    }
                }
            }
        }

        return "success";
    }

    /**
     * SCIPIO: Can be overridden for different keyword handling.
     * Added 2018-10-18.
     */
    public interface KeywordOverrideHandler {
        String handleCategoryLink(HttpServletRequest request, HttpServletResponse response, String target, String keyword, GenericValue productStoreKeywordOvrd);
        String handleProductLink(HttpServletRequest request, HttpServletResponse response, String target, String keyword, GenericValue productStoreKeywordOvrd);
        String handleNavLink(HttpServletRequest request, HttpServletResponse response, String target, String keyword, GenericValue productStoreKeywordOvrd);
        String handleAbsoluteLink(HttpServletRequest request, HttpServletResponse response, String target, String keyword, GenericValue productStoreKeywordOvrd);
        default String handleOther(HttpServletRequest request, HttpServletResponse response, String target, String keyword, GenericValue productStoreKeywordOvrd, String targetTypeEnumId) {
            Debug.logError("The targetTypeEnumId [" + targetTypeEnumId + "] is not recognized, not doing keyword override", module); // SCIPIO: fixed logging (targetTypeEnumId was missing)
            // might as well see if there are any others...
            return null;
        }
    }

    /**
     * SCIPIO: Default checkDoKeywordOverride handler, based on stock code from the original method; can be overridden.
     * Added 2018-10-18.
     */
    public static class DefaultKeywordOverrideHandler implements KeywordOverrideHandler {
        public static final DefaultKeywordOverrideHandler INSTANCE = new DefaultKeywordOverrideHandler();

        public String handleCategoryLink(HttpServletRequest request, HttpServletResponse response, String target, String keyword, GenericValue productStoreKeywordOvrd) {
            String requestName = "/category/~category_id=" + target;
            return RequestHandler.makeUrl(request, response, requestName, false, null, true); // SCIPIO: 2018-07-09: changed secure to null, encode to true
        }

        public String handleProductLink(HttpServletRequest request, HttpServletResponse response, String target, String keyword, GenericValue productStoreKeywordOvrd) {
            String requestName = "/product/~product_id=" + target;
            return RequestHandler.makeUrl(request, response, requestName, false, null, true); // SCIPIO: 2018-07-09: changed secure to null, encode to true
        }

        public String handleNavLink(HttpServletRequest request, HttpServletResponse response, String target, String keyword, GenericValue productStoreKeywordOvrd) {
            return RequestHandler.makeUrl(request, response, target, false, null, true); // SCIPIO: 2018-07-09: changed secure to null, encode to true
        }

        public String handleAbsoluteLink(HttpServletRequest request, HttpServletResponse response, String target, String keyword, GenericValue productStoreKeywordOvrd) {
            // do nothing, is absolute URL
            return target;
        }
    }

    public static ArrayList<String> searchDo(HttpSession session, Delegator delegator, String prodCatalogId) {
        String visitId = VisitHandler.getVisitId(session);
        ProductSearchOptions productSearchOptions = getProductSearchOptions(session);
        List<ProductSearchConstraint> productSearchConstraintList = productSearchOptions.getConstraintList();
        if (UtilValidate.isEmpty(productSearchConstraintList)) {
            // no constraints, don't do a search...
            return new ArrayList<>();
        }

        ResultSortOrder resultSortOrder = productSearchOptions.getResultSortOrder();

        // if the search options have changed since the last search, put at the beginning of the options history list
        checkSaveSearchOptionsHistory(session);

        return ProductSearch.searchProducts(productSearchConstraintList, resultSortOrder, delegator, visitId);
    }

    public static void searchClear(HttpSession session) {
        new SearchOptionsUpdate<Void>(session) { // SCIPIO: Wrap operation in a thread-safe update section
            @Override
            protected Void runCore() {
                searchClearCore(session);
                return null;
            }
        }.run();
    }

    private static void searchClearCore(HttpSession session) { // SCIPIO: New, refactored from non-Core method
        ProductSearchOptions.clearSearchOptions(session);
    }
    
    public static List<String> searchGetConstraintStrings(boolean detailed, HttpSession session, Delegator delegator) {
        Locale locale = UtilHttp.getLocale(session);
        ProductSearchOptions productSearchOptions = getProductSearchOptions(session);
        return productSearchOptions.searchGetConstraintStrings(detailed, delegator, locale);
    }

    public static String searchGetSortOrderString(boolean detailed, HttpServletRequest request) {
        Locale locale = UtilHttp.getLocale(request);
        ResultSortOrder resultSortOrder = ProductSearchOptions.getResultSortOrder(request);
        if (resultSortOrder == null) return "";
        return resultSortOrder.prettyPrintSortOrder(detailed, locale);
    }

    public static void searchSetSortOrder(ResultSortOrder resultSortOrder, HttpSession session) {
        new SearchOptionsUpdate<Void>(session) { // SCIPIO: Wrap operation in a thread-safe update section
            @Override
            protected Void runCore() {
                searchSetSortOrderCore(resultSortOrder, session);
                return null;
            }
        }.run();
    }
    
    private static void searchSetSortOrderCore(ResultSortOrder resultSortOrder, HttpSession session) { // SCIPIO: New, refactored from non-Core method
        ProductSearchOptions.setResultSortOrder(resultSortOrder, session);
    }

    public static void searchAddFeatureIdConstraints(Collection<String> featureIds, Boolean exclude, HttpServletRequest request) {
        new SearchOptionsUpdate<Void>(request) { // SCIPIO: Wrap operation in a thread-safe update section
            @Override
            protected Void runCore() {
                searchAddFeatureIdConstraintsCore(featureIds, exclude, request);
                return null;
            }
        }.run();
    }

    private static void searchAddFeatureIdConstraintsCore(Collection<String> featureIds, Boolean exclude, HttpServletRequest request) { // SCIPIO: New, refactored from non-Core method
        HttpSession session = request.getSession();
        if (UtilValidate.isEmpty(featureIds)) {
            return;
        }
        for (String productFeatureId: featureIds) {
            searchAddConstraintCore(new FeatureConstraint(productFeatureId, exclude), session);
        }
    }

    public static void searchAddConstraint(ProductSearchConstraint productSearchConstraint, HttpSession session) {
        new SearchOptionsUpdate<Void>(session) { // SCIPIO: Wrap operation in a thread-safe update section
            @Override
            protected Void runCore() {
                searchAddConstraintCore(productSearchConstraint, session);
                return null;
            }
        }.run();
    }
    
    private static void searchAddConstraintCore(ProductSearchConstraint productSearchConstraint, HttpSession session) { // SCIPIO: New, refactored from non-Core method
        ProductSearchOptions.addConstraint(productSearchConstraint, session);
    }

    public static void searchRemoveConstraint(int index, HttpSession session) { 
        new SearchOptionsUpdate<Void>(session) { // SCIPIO: Wrap operation in a thread-safe update section
            @Override
            protected Void runCore() {
                searchRemoveConstraintCore(index, session);
                return null;
            }
        }.run();
    }
    
    private static void searchRemoveConstraintCore(int index, HttpSession session) { // SCIPIO: New, refactored from non-Core method
        List<ProductSearchConstraint> productSearchConstraintList = ProductSearchOptions.getConstraintList(session);
        if (productSearchConstraintList == null) {
            return;
        } else if (index >= productSearchConstraintList.size()) {
            return;
        } else {
            productSearchConstraintList.remove(index);
        }
    }

    /**
     * SCIPIO: Provides a safe update section.
     */
    public static abstract class SearchOptionsUpdate<R> {
        
        private final HttpSession session;
        
        public SearchOptionsUpdate(HttpServletRequest request) {
            this.session = request.getSession();
        }
        
        private SearchOptionsUpdate(HttpSession session) {
            this.session = session;
        }
        
        public final R run() {
            synchronized (ProductSearchSession.getSyncObject(session)) {
                ProductSearchOptions options = ProductSearchOptions.currentOptions.get();
                boolean topLevel = (options == null);
                if (topLevel) {
                    options = getProductSearchOptionsCopyOrNew(session);
                }
                boolean ok = false;
                try {
                    if (topLevel) {
                        ProductSearchOptions.currentOptions.set(options);
                    }

                    R result = runCore();

                    ok = true;
                    
                    return result;
                } finally {
                    if (topLevel) {
                        ProductSearchOptions.currentOptions.remove();
                        if (ok) { // (if no exceptions)
                            setProductSearchOptions(session, options); // store result back in session
                        }
                    }
                }
            }
        }
        
        protected abstract R runCore();
    }

    public static void processSearchParameters(Map<String, Object> parameters, HttpServletRequest request) {
        // SCIPIO: delegated and wrapped in synchronized
        
        // SCIPIO: alreadyRun check duplicated and modified from processSearchParametersCore
        Boolean alreadyRun = (Boolean) request.getAttribute("processSearchParametersAlreadyRun");
        if (Boolean.TRUE.equals(alreadyRun)) {
            ProductSearchOptions productSearchOptions = getProductSearchOptions(request);
            // SCIPIO: Optimize: here, check if any of these parameters have changed; if not, can skip expensive sync
            // If not, we can skip expensive sync
            ProductSearchOptions testOptions = new ProductSearchOptions();
            testOptions.setViewIndex((String) parameters.get("VIEW_INDEX"));
            testOptions.setViewSize((String) parameters.get("VIEW_SIZE"));
            testOptions.setPaging((String) parameters.get("PAGING"));
            if (testOptions.getViewIndex() == productSearchOptions.getViewIndex() &&
                testOptions.getViewSize() == productSearchOptions.getViewSize() &&
                Objects.equals(testOptions.getPaging(), productSearchOptions.getPaging())) {
                return;
            }
        }

        new SearchOptionsUpdate<Void>(request) { // SCIPIO
            @Override
            protected Void runCore() {
                processSearchParametersCore(parameters, request, alreadyRun);
                return null;
            }
        }.run();
    }

    private static void processSearchParametersCore(Map<String, Object> parameters, HttpServletRequest request, Boolean alreadyRun) {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        //Boolean alreadyRun = (Boolean) request.getAttribute("processSearchParametersAlreadyRun"); // SCIPIO
        if (Boolean.TRUE.equals(alreadyRun)) {
            // even if already run, check the VIEW_SIZE and VIEW_INDEX again, just for kicks
            ProductSearchOptions productSearchOptions = getProductSearchOptions(request);
            productSearchOptions.setViewIndex((String) parameters.get("VIEW_INDEX"));
            productSearchOptions.setViewSize((String) parameters.get("VIEW_SIZE"));
            productSearchOptions.setPaging((String) parameters.get("PAGING"));
            return;
        } else {
            request.setAttribute("processSearchParametersAlreadyRun", Boolean.TRUE);
        }

        HttpSession session = request.getSession();
        boolean constraintsChanged = false;
        GenericValue productStore = ProductStoreWorker.getProductStore(request);

        // clear search? by default yes, but if the clearSearch parameter is N then don't
        String clearSearchString = (String) parameters.get("clearSearch");
        boolean replaceConstraints = false; // SCIPIO: added 2017-09-14
        if (!"N".equals(clearSearchString)) {
            searchClearCore(session);
            constraintsChanged = true;
        } else {
            String removeConstraint = (String) parameters.get("removeConstraint");
            if (UtilValidate.isNotEmpty(removeConstraint)) {
                try {
                    searchRemoveConstraintCore(Integer.parseInt(removeConstraint), session);
                    constraintsChanged = true;
                } catch (Exception e) {
                    Debug.logError(e, "Error removing constraint [" + removeConstraint + "]", module);
                }
            }

            // SCIPIO: partial functionality to replace in-place, added 2017-09-14
            // TODO: INCOMPLETE: only a few parameters below support this!
            replaceConstraints = UtilMisc.booleanValueIndicator(parameters.get("replaceConstraints"), false);
        }

        String prioritizeCategoryId = null;
        if (UtilValidate.isNotEmpty(parameters.get("PRIORITIZE_CATEGORY_ID"))) {
            prioritizeCategoryId = (String) parameters.get("PRIORITIZE_CATEGORY_ID");
        } else if (UtilValidate.isNotEmpty(parameters.get("S_TPC"))) {
            prioritizeCategoryId = (String) parameters.get("S_TPC");
        }
        if (UtilValidate.isNotEmpty(prioritizeCategoryId)) {
            ProductSearchOptions.setTopProductCategoryId(prioritizeCategoryId, session);
            constraintsChanged = true;
        }

        // if there is another category, add a constraint for it
        if (UtilValidate.isNotEmpty(parameters.get("SEARCH_CATEGORY_ID"))) {
            String searchSubCategories = (String) parameters.get("SEARCH_SUB_CATEGORIES");
            String searchCategoryExc = (String) parameters.get("SEARCH_CATEGORY_EXC");
            Boolean exclude = UtilValidate.isEmpty(searchCategoryExc) ? null : !"N".equals(searchCategoryExc);
            // SCIPIO: 2017-08-25: support multiple values for categoryId (the other options applied to each ID equally)
            if (parameters.get("SEARCH_CATEGORY_ID") instanceof Collection) {
                Collection<String> searchCategoryIds = UtilGenerics.checkCollection(parameters.get("SEARCH_CATEGORY_ID"));
                for(String searchCategoryId : searchCategoryIds) {
                    searchAddConstraintCore(new ProductSearch.CategoryConstraint(searchCategoryId, !"N".equals(searchSubCategories), exclude), session);
                }
            } else {
                String searchCategoryId = (String) parameters.get("SEARCH_CATEGORY_ID");
                searchAddConstraintCore(new ProductSearch.CategoryConstraint(searchCategoryId, !"N".equals(searchSubCategories), exclude), session);
            }
            constraintsChanged = true;
        }

        for (int catNum = 1; catNum < 10; catNum++) {
            if (UtilValidate.isNotEmpty(parameters.get("SEARCH_CATEGORY_ID" + catNum))) {
                String searchCategoryId = (String) parameters.get("SEARCH_CATEGORY_ID" + catNum);
                String searchSubCategories = (String) parameters.get("SEARCH_SUB_CATEGORIES" + catNum);
                String searchCategoryExc = (String) parameters.get("SEARCH_CATEGORY_EXC" + catNum);
                Boolean exclude = UtilValidate.isEmpty(searchCategoryExc) ? null : !"N".equals(searchCategoryExc);
                searchAddConstraintCore(new ProductSearch.CategoryConstraint(searchCategoryId, !"N".equals(searchSubCategories), exclude), session);
                constraintsChanged = true;
            }
        }

        // a shorter variation for categories
        for (int catNum = 1; catNum < 10; catNum++) {
            if (UtilValidate.isNotEmpty(parameters.get("S_CAT" + catNum))) {
                String searchCategoryId = (String) parameters.get("S_CAT" + catNum);
                String searchSubCategories = (String) parameters.get("S_CSB" + catNum);
                String searchCategoryExc = (String) parameters.get("S_CEX" + catNum);
                Boolean exclude = UtilValidate.isEmpty(searchCategoryExc) ? null : !"N".equals(searchCategoryExc);
                searchAddConstraintCore(new ProductSearch.CategoryConstraint(searchCategoryId, !"N".equals(searchSubCategories), exclude), session);
                constraintsChanged = true;
            }
        }

        // if there is any category selected try to use catalog and add a constraint for it
        if (UtilValidate.isNotEmpty(parameters.get("SEARCH_CATALOG_ID"))) {
            String searchCatalogId = (String) parameters.get("SEARCH_CATALOG_ID");
            if (searchCatalogId != null && !searchCatalogId.equalsIgnoreCase("")) {
                String topCategory = CatalogWorker.getCatalogTopCategoryId(request, searchCatalogId);
                if (UtilValidate.isEmpty(topCategory)) {
                    topCategory = CatalogWorker.getCatalogTopEbayCategoryId(request, searchCatalogId);
                }
                List<GenericValue> categories = CategoryWorker.getRelatedCategoriesRet(request, "topLevelList", topCategory, true, false, true);
                searchAddConstraintCore(new ProductSearch.CatalogConstraint(searchCatalogId, categories), session);
                constraintsChanged = true;
            }
        }

        // if keywords were specified, add a constraint for them
        if (UtilValidate.isNotEmpty(parameters.get("SEARCH_STRING"))) {
            // SCIPIO: new: replace constraints in-place, so remove all keyword constraints
            if (replaceConstraints) {
                ProductSearchOptions.removeConstraintsByType(ProductSearch.KeywordConstraint.class, session);
            }

            String keywordString = (String) parameters.get("SEARCH_STRING");
            String searchOperator = (String) parameters.get("SEARCH_OPERATOR");
            // defaults to true/Y, ie anything but N is true/Y
            boolean anyPrefixSuffix = !"N".equals(parameters.get("SEARCH_ANYPRESUF"));
            searchAddConstraintCore(new ProductSearch.KeywordConstraint(keywordString, anyPrefixSuffix, anyPrefixSuffix, null, "AND".equals(searchOperator)), session);
            constraintsChanged = true;
        }

        // if productName were specified, add a constraint for them
        if (UtilValidate.isNotEmpty(parameters.get("SEARCH_PRODUCT_NAME"))) {
            String productName = (String) parameters.get("SEARCH_PRODUCT_NAME");
            searchAddConstraintCore(new ProductSearch.ProductFieldConstraint(productName, "productName"), session);
            constraintsChanged = true;
        }

        // if internalName were specified, add a constraint for them
        if (UtilValidate.isNotEmpty(parameters.get("SEARCH_INTERNAL_PROD_NAME"))) {
            String internalName = (String) parameters.get("SEARCH_INTERNAL_PROD_NAME");
            searchAddConstraintCore(new ProductSearch.ProductFieldConstraint(internalName, "internalName"), session);
            constraintsChanged = true;
        }

        for (int kwNum = 1; kwNum < 10; kwNum++) {
            if (UtilValidate.isNotEmpty(parameters.get("SEARCH_STRING" + kwNum))) {
                String keywordString = (String) parameters.get("SEARCH_STRING" + kwNum);
                String searchOperator = (String) parameters.get("SEARCH_OPERATOR" + kwNum);
                // defaults to true/Y, ie anything but N is true/Y
                boolean anyPrefixSuffix = !"N".equals(parameters.get("SEARCH_ANYPRESUF" + kwNum));
                searchAddConstraintCore(new ProductSearch.KeywordConstraint(keywordString, anyPrefixSuffix, anyPrefixSuffix, null, "AND".equals(searchOperator)), session);
                constraintsChanged = true;
            }
        }

        for (Entry<String, Object> entry : parameters.entrySet()) {
            String parameterName = entry.getKey();
            if (parameterName.startsWith("SEARCH_FEAT") && !parameterName.startsWith("SEARCH_FEAT_EXC")) {
                String productFeatureId = (String) parameters.get(parameterName);
                if (UtilValidate.isNotEmpty(productFeatureId)) {
                    String paramNameExt = parameterName.substring("SEARCH_FEAT".length());
                    String searchCategoryExc = (String) parameters.get("SEARCH_FEAT_EXC" + paramNameExt);
                    Boolean exclude = UtilValidate.isEmpty(searchCategoryExc) ? null : !"N".equals(searchCategoryExc);
                    //Debug.logInfo("parameterName=" + parameterName + ", paramNameExt=" + paramNameExt + ", searchCategoryExc=" + searchCategoryExc + ", exclude=" + exclude, module);
                    searchAddConstraintCore(new ProductSearch.FeatureConstraint(productFeatureId, exclude), session);
                    constraintsChanged = true;
                }
            }
            // a shorter feature variation
            if (parameterName.startsWith("S_PFI")) {
                String productFeatureId = (String) parameters.get(parameterName);
                if (UtilValidate.isNotEmpty(productFeatureId)) {
                    String paramNameExt = parameterName.substring("S_PFI".length());
                    String searchCategoryExc = (String) parameters.get("S_PFX" + paramNameExt);
                    Boolean exclude = UtilValidate.isEmpty(searchCategoryExc) ? null : !"N".equals(searchCategoryExc);
                    searchAddConstraintCore(new ProductSearch.FeatureConstraint(productFeatureId, exclude), session);
                    constraintsChanged = true;
                }
            }

            //if product features category were selected add a constraint for each
            if (parameterName.startsWith("SEARCH_PROD_FEAT_CAT") && !parameterName.startsWith("SEARCH_PROD_FEAT_CAT_EXC")) {
                String productFeatureCategoryId = (String) parameters.get(parameterName);
                if (UtilValidate.isNotEmpty(productFeatureCategoryId)) {
                    String paramNameExt = parameterName.substring("SEARCH_PROD_FEAT_CAT".length());
                    String searchProdFeatureCategoryExc = (String) parameters.get("SEARCH_PROD_FEAT_CAT_EXC" + paramNameExt);
                    Boolean exclude = UtilValidate.isEmpty(searchProdFeatureCategoryExc) ? null : !"N".equals(searchProdFeatureCategoryExc);
                    searchAddConstraintCore(new ProductSearch.FeatureCategoryConstraint(productFeatureCategoryId, exclude), session);
                    constraintsChanged = true;
                }
            }
            // a shorter variation for feature category
            if (parameterName.startsWith("S_FCI")) {
                String productFeatureCategoryId = (String) parameters.get(parameterName);
                if (UtilValidate.isNotEmpty(productFeatureCategoryId)) {
                    String paramNameExt = parameterName.substring("S_FCI".length());
                    String searchProdFeatureCategoryExc = (String) parameters.get("S_FCX" + paramNameExt);
                    Boolean exclude = UtilValidate.isEmpty(searchProdFeatureCategoryExc) ? null : !"N".equals(searchProdFeatureCategoryExc);
                    searchAddConstraintCore(new ProductSearch.FeatureCategoryConstraint(productFeatureCategoryId, exclude), session);
                    constraintsChanged = true;
                }
            }

            //if product features group were selected add a constraint for each
            if (parameterName.startsWith("SEARCH_PROD_FEAT_GRP") && !parameterName.startsWith("SEARCH_PROD_FEAT_GRP_EXC")) {
                String productFeatureGroupId = (String) parameters.get(parameterName);
                if (UtilValidate.isNotEmpty(productFeatureGroupId)) {
                    String paramNameExt = parameterName.substring("SEARCH_PROD_FEAT_GRP".length());
                    String searchProdFeatureGroupExc = (String) parameters.get("SEARCH_PROD_FEAT_GRP_EXC" + paramNameExt);
                    Boolean exclude = UtilValidate.isEmpty(searchProdFeatureGroupExc) ? null : !"N".equals(searchProdFeatureGroupExc);
                    searchAddConstraintCore(new ProductSearch.FeatureGroupConstraint(productFeatureGroupId, exclude), session);
                    constraintsChanged = true;
                }
            }
            // a shorter variation for feature group
            if (parameterName.startsWith("S_FGI")) {
                String productFeatureGroupId = (String) parameters.get(parameterName);
                if (UtilValidate.isNotEmpty(productFeatureGroupId)) {
                    String paramNameExt = parameterName.substring("S_FGI".length());
                    String searchProdFeatureGroupExc = (String) parameters.get("S_FGX" + paramNameExt);
                    Boolean exclude = UtilValidate.isEmpty(searchProdFeatureGroupExc) ? null : !"N".equals(searchProdFeatureGroupExc);
                    searchAddConstraintCore(new ProductSearch.FeatureGroupConstraint(productFeatureGroupId, exclude), session);
                    constraintsChanged = true;
                }
            }
        }

        // if features were selected add a constraint for each
        Map<String, String> featureIdByType = ParametricSearch.makeFeatureIdByTypeMap(parameters);
        if (featureIdByType.size() > 0) {
            constraintsChanged = true;
            searchAddFeatureIdConstraintsCore(featureIdByType.values(), null, request);
        }

        // add a supplier to the search
        if (UtilValidate.isNotEmpty(parameters.get("SEARCH_SUPPLIER_ID")) || UtilValidate.isNotEmpty(parameters.get("S_SUP"))) {
            String supplierPartyId = (String) parameters.get("SEARCH_SUPPLIER_ID");
            if (UtilValidate.isEmpty(supplierPartyId)) {
                supplierPartyId = (String) parameters.get("S_SUP");
            }
            searchAddConstraintCore(new ProductSearch.SupplierConstraint(supplierPartyId), session);
            constraintsChanged = true;
        }

        // add a list price range to the search
        if (UtilValidate.isNotEmpty(parameters.get("LIST_PRICE_LOW")) || UtilValidate.isNotEmpty(parameters.get("LIST_PRICE_HIGH"))) {
            BigDecimal listPriceLow = null;
            BigDecimal listPriceHigh = null;
            String listPriceCurrency = UtilHttp.getCurrencyUom(request);
            if (UtilValidate.isNotEmpty(parameters.get("LIST_PRICE_LOW"))) {
                try {
                    listPriceLow = new BigDecimal((String) parameters.get("LIST_PRICE_LOW"));
                } catch (NumberFormatException e) {
                    Debug.logError("Error parsing LIST_PRICE_LOW parameter [" + (String) parameters.get("LIST_PRICE_LOW") + "]: " + e.toString(), module);
                }
            }
            if (UtilValidate.isNotEmpty(parameters.get("LIST_PRICE_HIGH"))) {
                try {
                    listPriceHigh = new BigDecimal((String) parameters.get("LIST_PRICE_HIGH"));
                } catch (NumberFormatException e) {
                    Debug.logError("Error parsing LIST_PRICE_HIGH parameter [" + (String) parameters.get("LIST_PRICE_HIGH") + "]: " + e.toString(), module);
                }
            }
            searchAddConstraintCore(new ProductSearch.ListPriceRangeConstraint(listPriceLow, listPriceHigh, listPriceCurrency), session);
            constraintsChanged = true;
        }
        if (UtilValidate.isNotEmpty(parameters.get("LIST_PRICE_RANGE")) || UtilValidate.isNotEmpty(parameters.get("S_LPR"))) {
            String listPriceRangeStr = (String) parameters.get("LIST_PRICE_RANGE");
            if (UtilValidate.isEmpty(listPriceRangeStr)) {
                listPriceRangeStr = (String) parameters.get("S_LPR");
            }
            int underscoreIndex = listPriceRangeStr.indexOf('_');
            String listPriceLowStr;
            String listPriceHighStr;
            if (underscoreIndex >= 0) {
                listPriceLowStr = listPriceRangeStr.substring(0, listPriceRangeStr.indexOf('_'));
                listPriceHighStr = listPriceRangeStr.substring(listPriceRangeStr.indexOf('_') + 1);
            } else {
                // no underscore: assume it is a low range with no high range, ie the ending underscore was left off
                listPriceLowStr = listPriceRangeStr;
                listPriceHighStr = null;
            }

            BigDecimal listPriceLow = null;
            BigDecimal listPriceHigh = null;
            String listPriceCurrency = UtilHttp.getCurrencyUom(request);
            if (UtilValidate.isNotEmpty(listPriceLowStr)) {
                try {
                    listPriceLow = new BigDecimal(listPriceLowStr);
                } catch (NumberFormatException e) {
                    Debug.logError("Error parsing low part of LIST_PRICE_RANGE parameter [" + listPriceLowStr + "]: " + e.toString(), module);
                }
            }
            if (UtilValidate.isNotEmpty(listPriceHighStr)) {
                try {
                    listPriceHigh = new BigDecimal(listPriceHighStr);
                } catch (NumberFormatException e) {
                    Debug.logError("Error parsing high part of LIST_PRICE_RANGE parameter [" + listPriceHighStr + "]: " + e.toString(), module);
                }
            }
            searchAddConstraintCore(new ProductSearch.ListPriceRangeConstraint(listPriceLow, listPriceHigh, listPriceCurrency), session);
            constraintsChanged = true;
        }

        // check the ProductStore to see if we should add the ExcludeVariantsConstraint
        if (productStore != null && !"N".equals(productStore.getString("prodSearchExcludeVariants"))) {
            searchAddConstraintCore(new ProductSearch.ExcludeVariantsConstraint(), session);
            // not consider this a change for now, shouldn't change often: constraintsChanged = true;
        }

        if ("true".equalsIgnoreCase((String) parameters.get("AVAILABILITY_FILTER"))) {
            searchAddConstraintCore(new ProductSearch.AvailabilityDateConstraint(), session);
            constraintsChanged = true;
        }

        if (UtilValidate.isNotEmpty(parameters.get("SEARCH_GOOD_IDENTIFICATION_TYPE")) ||
            UtilValidate.isNotEmpty(parameters.get("SEARCH_GOOD_IDENTIFICATION_VALUE"))) {
            String include = (String) parameters.get("SEARCH_GOOD_IDENTIFICATION_INCL");
            if (UtilValidate.isEmpty(include)) {
                include = "Y";
            }
            Boolean inc =  Boolean.TRUE;
            if ("N".equalsIgnoreCase(include)) {
                inc =  Boolean.FALSE;
            }

            searchAddConstraintCore(new ProductSearch.GoodIdentificationConstraint((String)parameters.get("SEARCH_GOOD_IDENTIFICATION_TYPE"),
                                (String) parameters.get("SEARCH_GOOD_IDENTIFICATION_VALUE"), inc), session);
            constraintsChanged = true;
        }

        String prodCatalogId = CatalogWorker.getCurrentCatalogId(request);
        String viewProductCategoryId = CatalogWorker.getCatalogViewAllowCategoryId(delegator, prodCatalogId);
        if (UtilValidate.isNotEmpty(viewProductCategoryId)) {
            ProductSearchConstraint viewAllowConstraint = new CategoryConstraint(viewProductCategoryId, true, null);
            searchAddConstraintCore(viewAllowConstraint, session);
            // not consider this a change for now, shouldn't change often: constraintsChanged = true;
        }

        // set the sort order
        String sortOrder = (String) parameters.get("sortOrder");
        if (UtilValidate.isEmpty(sortOrder)) {
            sortOrder = (String) parameters.get("S_O");
        }
        String sortAscending = (String) parameters.get("sortAscending");
        if (UtilValidate.isEmpty(sortAscending)) {
            sortAscending = (String) parameters.get("S_A");
        }
        boolean ascending = !"N".equals(sortAscending);
        if (sortOrder != null) {
            ProductSearch.ResultSortOrder resultSortOrder = parseSortOrder(sortOrder, ascending);
            if (resultSortOrder != null) {
                searchSetSortOrderCore(resultSortOrder, session); // SCIPIO: refactored
            }
        }

        ProductSearchOptions productSearchOptions = getProductSearchOptions(session);
        if (constraintsChanged) {
            // query changed, clear out the VIEW_INDEX & VIEW_SIZE
            productSearchOptions.clearViewInfo();
        }

        productSearchOptions.setViewIndex((String) parameters.get("VIEW_INDEX"));
        productSearchOptions.setViewSize((String) parameters.get("VIEW_SIZE"));
        productSearchOptions.setPaging((String) parameters.get("PAGING"));
    }

    /**
     * SCIPIO: Refactored from {@link #processSearchParameters(Map, HttpServletRequest)}.
     */
    public static ProductSearch.ResultSortOrder parseSortOrder(String sortOrder, boolean ascending) {
        if ("SortKeywordRelevancy".equals(sortOrder) || "SKR".equals(sortOrder)) {
            return new ProductSearch.SortKeywordRelevancy();
        } else if (sortOrder.startsWith("SortProductField:")) {
            String fieldName = sortOrder.substring("SortProductField:".length());
            return new ProductSearch.SortProductField(fieldName, ascending);
        } else if (sortOrder.startsWith("SPF:")) {
            String fieldName = sortOrder.substring("SPF:".length());
            return new ProductSearch.SortProductField(fieldName, ascending);
        } else if (sortOrder.startsWith("SortProductPrice:")) {
            String priceTypeId = sortOrder.substring("SortProductPrice:".length());
            return new ProductSearch.SortProductPrice(priceTypeId, ascending);
        } else if (sortOrder.startsWith("SPP:")) {
            String priceTypeId = sortOrder.substring("SPP:".length());
            return new ProductSearch.SortProductPrice(priceTypeId, ascending);
        } else if (sortOrder.startsWith("SortProductFeature:")) {
            String featureId = sortOrder.substring("SortProductFeature:".length());
            return new ProductSearch.SortProductFeature(featureId, ascending);
        } else if (sortOrder.startsWith("SPFT:")) {
            String priceTypeId = sortOrder.substring("SPFT:".length());
            return new ProductSearch.SortProductPrice(priceTypeId, ascending);
        }
        return null;
    }
    
    @SuppressWarnings("unchecked")
    public static Map<String, Object> getProductSearchResult(HttpServletRequest request, Delegator delegator, String prodCatalogId) {

        // ========== Create View Indexes
        int viewIndex = 0;
        // SCIPIO: unhardcode default
        //int viewSize = 20;
        int viewSize = UtilProperties.getPropertyAsInteger("general.properties", "record.paginate.defaultViewSize", 20);
        int highIndex = 0;
        int lowIndex = 0;
        int listSize = 0;
        String paging = "Y";
        // SCIPIO: unhardcode default
        //int previousViewSize = 20;
        int previousViewSize = viewSize;
        Map<String, Object> requestParams = UtilHttp.getCombinedMap(request);
        List<String> keywordTypeIds = new ArrayList<>();
        if (requestParams.get("keywordTypeId") instanceof String) {
            keywordTypeIds.add((String) requestParams.get("keywordTypeId"));
        } else if (requestParams.get("keywordTypeId") instanceof List){
            keywordTypeIds = (List<String>) requestParams.get("keywordTypeId");
        }
        String statusId = (String) requestParams.get("statusId");

        HttpSession session = request.getSession();
        ProductSearchOptions productSearchOptions = getProductSearchOptions(request);

        String addOnTopProdCategoryId = productSearchOptions.getTopProductCategoryId();

        Integer viewIndexInteger = productSearchOptions.getViewIndex();
        if (viewIndexInteger != null) {
            viewIndex = viewIndexInteger;
        }

        Integer viewSizeInteger = productSearchOptions.getViewSize();
        if (viewSizeInteger != null) {
            viewSize = viewSizeInteger;
        }

        Integer previousViewSizeInteger = productSearchOptions.getPreviousViewSize();
        if (previousViewSizeInteger != null) {
            previousViewSize = previousViewSizeInteger;
        }

        // SCIPIO: FIXME: paging is completely ignored in the stock code below! Not implemented!
        String pag = productSearchOptions.getPaging();
        if (pag != null && !pag.isEmpty()) {
            paging = pag;
        }

        lowIndex = viewIndex * viewSize;
        highIndex = (viewIndex + 1) * viewSize;

        // ========== Do the actual search
        List<String> productIds = new ArrayList<>();
        String visitId = VisitHandler.getVisitId(session);
        List<ProductSearchConstraint> productSearchConstraintList = ProductSearchOptions.getConstraintList(session);
        String noConditionFind = (String) requestParams.get("noConditionFind");
        if (UtilValidate.isEmpty(noConditionFind)) {
            noConditionFind = EntityUtilProperties.getPropertyValue("widget", "widget.defaultNoConditionFind", delegator);
        }
        // if noConditionFind to Y then find without conditions otherwise search according to constraints.
        if ("Y".equals(noConditionFind) || UtilValidate.isNotEmpty(productSearchConstraintList)) {
            // if the search options have changed since the last search, put at the beginning of the options history list
            checkSaveSearchOptionsHistory(session);

            int addOnTopTotalListSize = 0;
            int addOnTopListSize = 0;
            List<GenericValue> addOnTopProductCategoryMembers;
            if (UtilValidate.isNotEmpty(addOnTopProdCategoryId)) {
                // always include the members of the addOnTopProdCategoryId
                Timestamp now = UtilDateTime.nowTimestamp();
                List<EntityCondition> addOnTopProdCondList = new ArrayList<>();
                addOnTopProdCondList.add(EntityCondition.makeCondition(EntityCondition.makeCondition("thruDate", EntityOperator.EQUALS, null), EntityOperator.OR, EntityCondition.makeCondition("thruDate", EntityOperator.GREATER_THAN, now)));
                addOnTopProdCondList.add(EntityCondition.makeCondition("fromDate", EntityOperator.LESS_THAN, now));
                addOnTopProdCondList.add(EntityCondition.makeCondition("productCategoryId", EntityOperator.EQUALS, addOnTopProdCategoryId));
                EntityQuery eq = EntityQuery.use(delegator)
                        .select(UtilMisc.toSet("productId", "sequenceNum"))
                        .from("ProductCategoryMember")
                        .where(addOnTopProdCondList)
                        .orderBy("sequenceNum")
                        .cursorScrollInsensitive()
                        .distinct()
                        .maxRows(highIndex);

                try (EntityListIterator pli = eq.queryIterator()) {
                    addOnTopProductCategoryMembers = pli.getPartialList(lowIndex, viewSize);
                    addOnTopListSize = addOnTopProductCategoryMembers.size();
                    for (GenericValue alwaysAddProductCategoryMember: addOnTopProductCategoryMembers) {
                        productIds.add(alwaysAddProductCategoryMember.getString("productId"));
                    }
                    addOnTopTotalListSize = pli.getResultsSizeAfterPartialList();
                    listSize = listSize + addOnTopTotalListSize;
                } catch (GenericEntityException e) {
                    Debug.logError(e, module);
                }
            }

            // setup resultOffset and maxResults, noting that resultOffset is 1 based, not zero based as these numbers
            int resultOffsetInt = lowIndex - addOnTopTotalListSize + 1;
            if (resultOffsetInt < 1) {
                resultOffsetInt = 1;
            }
            int maxResultsInt = viewSize - addOnTopListSize;
            Integer resultOffset = resultOffsetInt;
            Integer maxResults = maxResultsInt;

            ResultSortOrder resultSortOrder = ProductSearchOptions.getResultSortOrder(request);

            ProductSearchContext productSearchContext = new ProductSearchContext(delegator, visitId);
            if (UtilValidate.isNotEmpty(productSearchConstraintList)) {
                productSearchContext.addProductSearchConstraints(productSearchConstraintList);
            }
            productSearchContext.setResultSortOrder(resultSortOrder);
            productSearchContext.setResultOffset(resultOffset);
            productSearchContext.setMaxResults(maxResults);

            if (UtilValidate.isNotEmpty(keywordTypeIds)) {
                productSearchContext.keywordTypeIds = keywordTypeIds;
            } else {
                 productSearchContext.keywordTypeIds = UtilMisc.toList("KWT_KEYWORD");
            }

            if (UtilValidate.isNotEmpty(statusId)) {
                productSearchContext.statusId = statusId;
            }

            List<String> foundProductIds = productSearchContext.doSearch();
            if (maxResultsInt > 0) {
                productIds.addAll(foundProductIds);
            }

            Integer totalResults = productSearchContext.getTotalResults();
            if (totalResults != null) {
                listSize = listSize + totalResults;
            }
        }

        if (listSize < highIndex) {
            highIndex = listSize;
        }

        // ========== Setup other display info
        List<String> searchConstraintStrings = searchGetConstraintStrings(false, session, delegator);
        String searchSortOrderString = searchGetSortOrderString(false, request);

        // ========== populate the result Map
        Map<String, Object> result = new HashMap<>();

        result.put("productIds", productIds);
        result.put("viewIndex", viewIndex);
        result.put("viewSize", viewSize);
        result.put("listSize", listSize);
        result.put("lowIndex", lowIndex);
        result.put("highIndex", highIndex);
        result.put("paging", paging);
        result.put("previousViewSize", previousViewSize);
        result.put("searchConstraintStrings", searchConstraintStrings);
        result.put("searchSortOrderString", searchSortOrderString);
        result.put("noConditionFind", noConditionFind);

        return result;
    }

    public static String makeSearchParametersString(HttpSession session) {
        return makeSearchParametersString(getProductSearchOptions(session));
    }
    public static String makeSearchParametersString(ProductSearchOptions productSearchOptions) {
        StringBuilder searchParamString = new StringBuilder();

        List<ProductSearchConstraint> constraintList = productSearchOptions.getConstraintList();
        if (UtilValidate.isEmpty(constraintList)) {
            constraintList = new ArrayList<>();
        }
        int categoriesCount = 0;
        int featuresCount = 0;
        int featureCategoriesCount = 0;
        int featureGroupsCount = 0;
        int keywordsCount = 0;
        boolean isNotFirst = false;
        for (ProductSearchConstraint psc: constraintList) {
            if (psc instanceof ProductSearch.CategoryConstraint) {
                ProductSearch.CategoryConstraint cc = (ProductSearch.CategoryConstraint) psc;
                categoriesCount++;
                if (isNotFirst) {
                    // SCIPIO: FIXME: The paramlist should not be escaped this early; it should be escaped by Freemarker
                    // Same applies to all others below
                    searchParamString.append("&amp;");
                } else {
                    isNotFirst = true;
                }
                searchParamString.append("S_CAT");
                searchParamString.append(categoriesCount);
                searchParamString.append("=");
                searchParamString.append(cc.productCategoryId);
                searchParamString.append("&amp;S_CSB");
                searchParamString.append(categoriesCount);
                searchParamString.append("=");
                searchParamString.append(cc.includeSubCategories ? "Y" : "N");
                if (cc.exclude != null) {
                    searchParamString.append("&amp;S_CEX");
                    searchParamString.append(categoriesCount);
                    searchParamString.append("=");
                    searchParamString.append(cc.exclude ? "Y" : "N");
                }
            } else if (psc instanceof ProductSearch.FeatureConstraint) {
                ProductSearch.FeatureConstraint fc = (ProductSearch.FeatureConstraint) psc;
                featuresCount++;
                if (isNotFirst) {
                    searchParamString.append("&amp;");
                } else {
                    isNotFirst = true;
                }
                searchParamString.append("S_PFI");
                searchParamString.append(featuresCount);
                searchParamString.append("=");
                searchParamString.append(fc.productFeatureId);
                if (fc.exclude != null) {
                    searchParamString.append("&amp;S_PFX");
                    searchParamString.append(featuresCount);
                    searchParamString.append("=");
                    searchParamString.append(fc.exclude ? "Y" : "N");
                }
            /* No way to specify parameters for these right now, so table until later
            } else if (psc instanceof ProductSearch.FeatureSetConstraint) {
                ProductSearch.FeatureSetConstraint fsc = (ProductSearch.FeatureSetConstraint) psc;
             */
            } else if (psc instanceof ProductSearch.FeatureCategoryConstraint) {
                ProductSearch.FeatureCategoryConstraint pfcc = (ProductSearch.FeatureCategoryConstraint) psc;
                featureCategoriesCount++;
                if (isNotFirst) {
                    searchParamString.append("&amp;");
                } else {
                    isNotFirst = true;
                }
                searchParamString.append("S_FCI");
                searchParamString.append(featureCategoriesCount);
                searchParamString.append("=");
                searchParamString.append(pfcc.productFeatureCategoryId);
                if (pfcc.exclude != null) {
                    searchParamString.append("&amp;S_FCX");
                    searchParamString.append(featureCategoriesCount);
                    searchParamString.append("=");
                    searchParamString.append(pfcc.exclude ? "Y" : "N");
                }
            } else if (psc instanceof ProductSearch.FeatureGroupConstraint) {
                ProductSearch.FeatureGroupConstraint pfgc = (ProductSearch.FeatureGroupConstraint) psc;
                featureGroupsCount++;
                if (isNotFirst) {
                    searchParamString.append("&amp;");
                } else {
                    isNotFirst = true;
                }
                searchParamString.append("S_FGI");
                searchParamString.append(featureGroupsCount);
                searchParamString.append("=");
                searchParamString.append(pfgc.productFeatureGroupId);
                if (pfgc.exclude != null) {
                    searchParamString.append("&amp;S_FGX");
                    searchParamString.append(featureGroupsCount);
                    searchParamString.append("=");
                    searchParamString.append(pfgc.exclude ? "Y" : "N");
                }
            } else if (psc instanceof ProductSearch.KeywordConstraint) {
                ProductSearch.KeywordConstraint kc = (ProductSearch.KeywordConstraint) psc;
                keywordsCount++;
                if (isNotFirst) {
                    searchParamString.append("&amp;");
                } else {
                    isNotFirst = true;
                }
                searchParamString.append("SEARCH_STRING");
                searchParamString.append(keywordsCount);
                searchParamString.append("=");
                searchParamString.append(UtilHttp.encodeBlanks(kc.keywordsString));
                searchParamString.append("&amp;SEARCH_OPERATOR");
                searchParamString.append(keywordsCount);
                searchParamString.append("=");
                searchParamString.append(kc.isAnd ? "AND" : "OR");
                searchParamString.append("&amp;SEARCH_ANYPRESUF");
                searchParamString.append(keywordsCount);
                searchParamString.append("=");
                searchParamString.append(kc.anyPrefix | kc.anySuffix ? "Y" : "N");
            } else if (psc instanceof ProductSearch.ListPriceRangeConstraint) {
                ProductSearch.ListPriceRangeConstraint lprc = (ProductSearch.ListPriceRangeConstraint) psc;
                if (lprc.lowPrice != null || lprc.highPrice != null) {
                    if (isNotFirst) {
                        searchParamString.append("&amp;");
                    } else {
                        isNotFirst = true;
                    }
                    searchParamString.append("S_LPR");
                    searchParamString.append("=");
                    if (lprc.lowPrice != null) {
                        searchParamString.append(lprc.lowPrice);
                    }
                    searchParamString.append("_");
                    if (lprc.highPrice != null) {
                        searchParamString.append(lprc.highPrice);
                    }
                }
            } else if (psc instanceof ProductSearch.SupplierConstraint) {
                ProductSearch.SupplierConstraint suppc = (ProductSearch.SupplierConstraint) psc;
                if (suppc.supplierPartyId != null) {
                    if (isNotFirst) {
                        searchParamString.append("&amp;");
                    } else {
                        isNotFirst = true;
                    }
                    searchParamString.append("S_SUP");
                    searchParamString.append("=");
                    searchParamString.append(suppc.supplierPartyId);
                }
            }
        }

        String topProductCategoryId = productSearchOptions.getTopProductCategoryId();
        if (topProductCategoryId != null) {
            searchParamString.append("&amp;S_TPC");
            searchParamString.append("=");
            searchParamString.append(topProductCategoryId);
        }
        ResultSortOrder resultSortOrder = productSearchOptions.getResultSortOrder();
        if (resultSortOrder instanceof ProductSearch.SortKeywordRelevancy) {
            searchParamString.append("&amp;S_O=SKR");
        } else if (resultSortOrder instanceof ProductSearch.SortProductField) {
            ProductSearch.SortProductField spf = (ProductSearch.SortProductField) resultSortOrder;
            searchParamString.append("&amp;S_O=SPF:");
            searchParamString.append(spf.fieldName);
        } else if (resultSortOrder instanceof ProductSearch.SortProductPrice) {
            ProductSearch.SortProductPrice spp = (ProductSearch.SortProductPrice) resultSortOrder;
            searchParamString.append("&amp;S_O=SPP:");
            searchParamString.append(spp.productPriceTypeId);
        } else if (resultSortOrder instanceof ProductSearch.SortProductFeature) {
            ProductSearch.SortProductFeature spf = (ProductSearch.SortProductFeature) resultSortOrder;
            searchParamString.append("&amp;S_O=SPFT:");
            searchParamString.append(spf.productFeatureTypeId);
        }
        searchParamString.append("&amp;S_A=");
        searchParamString.append(resultSortOrder.isAscending() ? "Y" : "N");

        return searchParamString.toString();
    }

    /**
     * This method returns a list of productId counts grouped by productFeatureId's of input productFeatureTypeId,
     * the constraint being applied on current ProductSearchConstraint list in session.
     * @param productFeatureTypeId The productFeatureTypeId, productFeatureId's of which should be considered.
     * @param session Current session.
     * @param delegator The delegator object.
     * @return List of Maps containing productFeatureId, productFeatureTypeId, description, featureCount.
     */
    public static List<Map<String, String>> listCountByFeatureForType(String productFeatureTypeId, HttpSession session, Delegator delegator) {
        String visitId = VisitHandler.getVisitId(session);

        ProductSearchContext productSearchContext = new ProductSearchContext(delegator, visitId);
        List<ProductSearchConstraint> productSearchConstraintList = ProductSearchOptions.getConstraintList(session);
        if (UtilValidate.isNotEmpty(productSearchConstraintList)) {
            productSearchContext.addProductSearchConstraints(productSearchConstraintList);
        }
        productSearchContext.finishKeywordConstraints();
        productSearchContext.finishCategoryAndFeatureConstraints();

        DynamicViewEntity dynamicViewEntity = productSearchContext.dynamicViewEntity;
        List<EntityCondition> entityConditionList = productSearchContext.entityConditionList;

        dynamicViewEntity.addMemberEntity("PFAC", "ProductFeatureAppl");
        dynamicViewEntity.addAlias("PFAC", "pfacProductFeatureId", "productFeatureId", null, null, Boolean.TRUE, null);
        dynamicViewEntity.addAlias("PFAC", "pfacFromDate", "fromDate", null, null, null, null);
        dynamicViewEntity.addAlias("PFAC", "pfacThruDate", "thruDate", null, null, null, null);
        dynamicViewEntity.addAlias("PFAC", "featureCount", "productId", null, null, null, "count-distinct");
        dynamicViewEntity.addViewLink("PROD", "PFAC", Boolean.FALSE, ModelKeyMap.makeKeyMapList("productId"));
        entityConditionList.add(EntityCondition.makeCondition(EntityCondition.makeCondition("pfacThruDate", EntityOperator.EQUALS, null), EntityOperator.OR, EntityCondition.makeCondition("pfacThruDate", EntityOperator.GREATER_THAN, UtilDateTime.nowTimestamp())));
        entityConditionList.add(EntityCondition.makeCondition("pfacFromDate", EntityOperator.LESS_THAN, UtilDateTime.nowTimestamp()));

        dynamicViewEntity.addMemberEntity("PFC", "ProductFeature");
        dynamicViewEntity.addAlias("PFC", "pfcProductFeatureTypeId", "productFeatureTypeId", null, null, Boolean.TRUE, null);
        dynamicViewEntity.addAlias("PFC", "pfcDescription", "description", null, null, Boolean.TRUE, null);
        dynamicViewEntity.addViewLink("PFAC", "PFC", Boolean.FALSE, ModelKeyMap.makeKeyMapList("productFeatureId"));
        entityConditionList.add(EntityCondition.makeCondition("pfcProductFeatureTypeId", EntityOperator.EQUALS, productFeatureTypeId));

        List<Map<String, String>> featureCountList = null;
        try (EntityListIterator eli = EntityQuery.use(delegator)
                .select(UtilMisc.toSet("pfacProductFeatureId", "featureCount", "pfcDescription", "pfcProductFeatureTypeId"))
                .from(dynamicViewEntity)
                .where(entityConditionList)
                .orderBy(productSearchContext.orderByList)
                .cursorScrollInsensitive()
                .queryIterator()) {

            featureCountList = new ArrayList<>();
            GenericValue searchResult = null;
            while ((searchResult = eli.next()) != null) {
                featureCountList.add(UtilMisc.<String, String>toMap("productFeatureId", (String) searchResult.get("pfacProductFeatureId"), "productFeatureTypeId", (String) searchResult.get("pfcProductFeatureTypeId"), "description", (String) searchResult.get("pfcDescription"), "featureCount", Long.toString((Long) searchResult.get("featureCount"))));
            }
        } catch (GenericEntityException e) {
            Debug.logError(e, "Error in product search", module);
            return null;
        }

        return featureCountList;
    }

    public static int getCategoryCostraintIndex(HttpSession session) {
        int index = 0;
        List<ProductSearchConstraint> productSearchConstraintList = ProductSearchOptions.getConstraintList(session);
        for (ProductSearchConstraint constraint: productSearchConstraintList) {
            if (constraint instanceof CategoryConstraint) {
                index++;
            }
        }
        return index;
    }

    /**
     * This method returns count of products within a given price range, the constraint being
     * applied on current ProductSearchConstraint list in session.
     * @param priceLow The low price.
     * @param priceHigh The high price.
     * @param session Current session.
     * @param delegator The delegator object.
     * @return The long value of count of products.
     */
    public static long getCountForListPriceRange(BigDecimal priceLow, BigDecimal priceHigh, HttpSession session, Delegator delegator) {
        String visitId = VisitHandler.getVisitId(session);

        ProductSearchContext productSearchContext = new ProductSearchContext(delegator, visitId);
        List<ProductSearchConstraint> productSearchConstraintList = ProductSearchOptions.getConstraintList(session);
        if (UtilValidate.isNotEmpty(productSearchConstraintList)) {
            productSearchContext.addProductSearchConstraints(productSearchConstraintList);
        }
        productSearchContext.finishKeywordConstraints();
        productSearchContext.finishCategoryAndFeatureConstraints();

        DynamicViewEntity dynamicViewEntity = productSearchContext.dynamicViewEntity;
        List<EntityCondition> entityConditionList = productSearchContext.entityConditionList;
        List<String> fieldsToSelect = new ArrayList<>();

        dynamicViewEntity.addMemberEntity("PPC", "ProductPrice");
        dynamicViewEntity.addAlias("PPC", "ppcProductPriceTypeId", "productPriceTypeId", null, null, null, null);
        dynamicViewEntity.addAlias("PPC", "ppcFromDate", "fromDate", null, null, null, null);
        dynamicViewEntity.addAlias("PPC", "ppcThruDate", "thruDate", null, null, null, null);
        dynamicViewEntity.addAlias("PPC", "ppcPrice", "price", null, null, null, null);
        dynamicViewEntity.addAlias("PPC", "priceRangeCount", "productId", null, null, null, "count-distinct");
        dynamicViewEntity.addViewLink("PROD", "PPC", Boolean.FALSE, ModelKeyMap.makeKeyMapList("productId"));
        fieldsToSelect.add("priceRangeCount");
        entityConditionList.add(EntityCondition.makeCondition(EntityCondition.makeCondition("ppcThruDate", EntityOperator.EQUALS, null), EntityOperator.OR, EntityCondition.makeCondition("ppcThruDate", EntityOperator.GREATER_THAN, UtilDateTime.nowTimestamp())));
        entityConditionList.add(EntityCondition.makeCondition("ppcFromDate", EntityOperator.LESS_THAN, UtilDateTime.nowTimestamp()));
        entityConditionList.add(EntityCondition.makeCondition("ppcPrice", EntityOperator.GREATER_THAN_EQUAL_TO, priceLow));
        entityConditionList.add(EntityCondition.makeCondition("ppcPrice", EntityOperator.LESS_THAN_EQUAL_TO, priceHigh));
        entityConditionList.add(EntityCondition.makeCondition("ppcProductPriceTypeId", EntityOperator.EQUALS, "LIST_PRICE"));

        Long priceRangeCount = 0L;
        try (EntityListIterator eli = EntityQuery.use(delegator)
                .select(UtilMisc.toSet(fieldsToSelect))
                .from(dynamicViewEntity)
                .where(entityConditionList)
                .orderBy(productSearchContext.orderByList)
                .cursorScrollInsensitive()
                .queryIterator()) {

            GenericValue searchResult = null;
            while ((searchResult = eli.next()) != null) {
                priceRangeCount = searchResult.getLong("priceRangeCount");
            }
        } catch (GenericEntityException e) {
            Debug.logError(e, "Error in product search", module);
        }
        return priceRangeCount;
    }

    /**
     * This method returns count of products in a given category (including all sub categories), the constraint being
     * applied on current ProductSearchConstraint list in session.
     * @param productCategoryId productCategoryId for which the count should be returned.
     * @param session Current session.
     * @param delegator The delegator object.
     * @return The long value of count of products.
     */
    public static long getCountForProductCategory(String productCategoryId, HttpSession session, Delegator delegator) {
        String visitId = VisitHandler.getVisitId(session);

        ProductSearchContext productSearchContext = new ProductSearchContext(delegator, visitId);
        List<ProductSearchConstraint> productSearchConstraintList = ProductSearchOptions.getConstraintList(session);
        if (UtilValidate.isNotEmpty(productSearchConstraintList)) {
            productSearchContext.addProductSearchConstraints(productSearchConstraintList);
        }
        productSearchContext.finishKeywordConstraints();
        productSearchContext.finishCategoryAndFeatureConstraints();

        DynamicViewEntity dynamicViewEntity = productSearchContext.dynamicViewEntity;
        List<EntityCondition> entityConditionList = productSearchContext.entityConditionList;
        List<String> fieldsToSelect = new ArrayList<>();

        dynamicViewEntity.addMemberEntity("PCMC", "ProductCategoryMember");
        dynamicViewEntity.addAlias("PCMC", "pcmcProductCategoryId", "productCategoryId", null, null, null, null);
        dynamicViewEntity.addAlias("PCMC", "pcmcFromDate", "fromDate", null, null, null, null);
        dynamicViewEntity.addAlias("PCMC", "pcmcThruDate", "thruDate", null, null, null, null);
        dynamicViewEntity.addAlias("PCMC", "categoryCount", "productId", null, null, null, "count-distinct");
        dynamicViewEntity.addViewLink("PROD", "PCMC", Boolean.FALSE, ModelKeyMap.makeKeyMapList("productId"));
        fieldsToSelect.add("categoryCount");
        entityConditionList.add(EntityCondition.makeCondition(EntityCondition.makeCondition("pcmcThruDate", EntityOperator.EQUALS, null), EntityOperator.OR, EntityCondition.makeCondition("pcmcThruDate", EntityOperator.GREATER_THAN, productSearchContext.nowTimestamp)));
        entityConditionList.add(EntityCondition.makeCondition("pcmcFromDate", EntityOperator.LESS_THAN, productSearchContext.nowTimestamp));

        Set<String> productCategoryIdSet = new HashSet<>();
        ProductSearch.getAllSubCategoryIds(productCategoryId, productCategoryIdSet, delegator, productSearchContext.nowTimestamp);
        entityConditionList.add(EntityCondition.makeCondition("pcmcProductCategoryId", EntityOperator.IN, productCategoryIdSet));

        Long categoryCount = 0L;
        try (EntityListIterator eli = EntityQuery.use(delegator)
                .select(UtilMisc.toSet(fieldsToSelect))
                .from(dynamicViewEntity)
                .where(entityConditionList)
                .orderBy(productSearchContext.orderByList)
                .cursorScrollInsensitive()
                .queryIterator()) {

            GenericValue searchResult = null;
            while ((searchResult = eli.next()) != null) {
                categoryCount = searchResult.getLong("categoryCount");
            }
        } catch (GenericEntityException e) {
            Debug.logError(e, "Error in product search", module);
        }
        return categoryCount;
    }
    
    /**
     * SCIPIO: Gets the search session sync object from session.
     * <p>
     * NOTE: Also set by {@link org.ofbiz.order.shoppingcart.CartEventListener#sessionCreated(HttpSessionEvent)}.
     * <p>
     * Added 2018-11-27.
     */
    public static Object getSyncObject(HttpServletRequest request) {
        return getSyncObject(request.getSession());
    }

    /**
     * SCIPIO: Gets the search session sync object from session.
     * <p>
     * NOTE: Also set by {@link org.ofbiz.order.shoppingcart.CartEventListener#sessionCreated(HttpSessionEvent)}.
     * <p>
     * Added 2018-11-27.
     */
    public static Object getSyncObject(HttpSession session) { // SCIPIO
        Object syncObj = session.getAttribute("_PRODUCT_SEARCH_SYNC_");
        if (syncObj == null) {
            synchronized (UtilHttp.getSessionSyncObject(session)) {
                syncObj = session.getAttribute("_PRODUCT_SEARCH_SYNC_");
                if (syncObj == null) {
                    if (Debug.verboseOn()) {
                        Debug.logVerbose("Product search session sync object not found in session; creating", module);
                    }
                    syncObj = createSyncObject();
                    session.setAttribute("_PRODUCT_SEARCH_SYNC_", syncObj);
                }
            }
        }
        return syncObj;
    }

    @SuppressWarnings("serial")
    public static Object createSyncObject() { // SCIPIO
        return new java.io.Serializable() {};
    }

    public static Object createSetSyncObject(HttpSession session) { // SCIPIO
        Object syncObj = createSyncObject();
        session.setAttribute("_PRODUCT_SEARCH_SYNC_", syncObj);
        return syncObj;
    }
}

<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->
<#assign searchOptionsHistoryList = Static["org.ofbiz.product.product.ProductSearchSession"].getSearchOptionsHistoryList(session)>
<#assign currentCatalogId = Static["org.ofbiz.product.catalog.CatalogWorker"].getCurrentCatalogId(request)>

<form name="advtokeywordsearchform" method="post" action="<@pageUrl>keywordsearch</@pageUrl>">
  
<@section title=uiLabelMap.ProductAdvancedSearchInCategory>
    <#-- SCIPIO: don't hardcode these
    <input type="hidden" name="VIEW_SIZE" value="10" />-->
    <input type="hidden" name="SEARCH_CATALOG_ID" value="${currentCatalogId}" />
    <#if searchCategory?has_content>
        <input type="hidden" name="SEARCH_CATEGORY_ID" value="${searchCategoryId!}" />
        <@field type="generic" label=uiLabelMap.ProductCategory>
            <b>"${(searchCategory.description)!}"</b>${uiLabelMap.ProductIncludeSubCategories}
            <@field type="radio" name="SEARCH_SUB_CATEGORIES" value="Y" checked=true label=uiLabelMap.CommonYes />
            <@field type="radio" name="SEARCH_SUB_CATEGORIES" value="N" label=uiLabelMap.CommonNo />
        </@field>
    </#if>
    <@field type="generic" label=uiLabelMap.ProductKeywords>
        <@field type="input" name="SEARCH_STRING" size="40" value=(requestParameters.SEARCH_STRING!) />&nbsp;
        <@field type="radio" name="SEARCH_OPERATOR" value="OR" checked=(searchOperator == "OR") label=uiLabelMap.CommonAny />
        <@field type="radio" name="SEARCH_OPERATOR" value="AND" checked=(searchOperator == "AND") label=uiLabelMap.CommonAll />
    </@field>
    <#list productFeatureTypeIdsOrdered as productFeatureTypeId>
      <#assign findPftMap = {"productFeatureTypeId":productFeatureTypeId}>
      <#assign productFeatureType = delegator.findOne("ProductFeatureType", findPftMap, true)>
      <#assign productFeatures = productFeaturesByTypeMap[productFeatureTypeId]>
      <@field type="select" label=((productFeatureType.get('description',locale))!) name="pft_${productFeatureTypeId}">
          <option value="">- ${uiLabelMap.CommonSelectAny} -</option>
        <#list productFeatures as productFeature>
          <option value="${productFeature.productFeatureId}">${productFeature.get("description",locale)?default(productFeature.productFeatureId)}</option>
        </#list>
      </@field>
    </#list>
    <@field type="select" label=uiLabelMap.ProductSupplier name="SEARCH_SUPPLIER_ID">
        <option value="">- ${uiLabelMap.CommonSelectAny} -</option>
      <#assign orderPartyId = orderPartyId!sessionAttributes.orderPartyId!><#-- SCIPIO: Access session only once -->
      <#list supplerPartyRoleAndPartyDetails as supplerPartyRoleAndPartyDetail>
        <option value="${supplerPartyRoleAndPartyDetail.partyId}"<#if ((orderPartyId!"") == supplerPartyRoleAndPartyDetail.partyId)> selected="selected"</#if>>${supplerPartyRoleAndPartyDetail.groupName!} ${supplerPartyRoleAndPartyDetail.firstName!} ${supplerPartyRoleAndPartyDetail.lastName!} [${supplerPartyRoleAndPartyDetail.partyId}]</option>
      </#list>
    </@field>
    <@field type="generic" label=uiLabelMap.CommonSortedBy>
        <@field type="select" name="sortOrder">
            <option value="SortKeywordRelevancy">${uiLabelMap.ProductKeywordRelevancy}</option>
            <option value="SortProductField:productName">${uiLabelMap.ProductProductName}</option>
            <option value="SortProductField:internalName">${uiLabelMap.ProductInternalName}</option>
            <option value="SortProductField:totalQuantityOrdered">${uiLabelMap.ProductPopularityByOrders}</option>
            <option value="SortProductField:totalTimesViewed">${uiLabelMap.ProductPopularityByViews}</option>
            <option value="SortProductField:averageCustomerRating">${uiLabelMap.ProductCustomerRating}</option>
            <option value="SortProductPrice:LIST_PRICE">${uiLabelMap.ProductListPrice}</option>
            <option value="SortProductPrice:DEFAULT_PRICE">${uiLabelMap.ProductDefaultPrice}</option>
            <option value="SortProductPrice:AVERAGE_COST">${uiLabelMap.ProductAverageCost}</option>
        </@field>
        <@field type="radio" name="sortAscending" value="Y" checked=true label=uiLabelMap.ProductLowToHigh />
        <@field type="radio" name="sortAscending" value="N" label=uiLabelMap.ProductHighToLow />
    </@field>
    <#if searchConstraintStrings?has_content>
      <@field type="generic" label=uiLabelMap.ProductLastSearch>
          <#list searchConstraintStrings as searchConstraintString>
              <div>&nbsp;-&nbsp;${searchConstraintString}</div>
          </#list>
          <div>${uiLabelMap.CommonSortedBy}: ${searchSortOrderString}</div>
          <div>
              <@field type="radio" name="clearSearch" value="Y" checked=true label=uiLabelMap.ProductNewSearch />
              <@field type="radio" name="clearSearch" value="N" label=uiLabelMap.CommonRefineSearch />
          </div>
      </@field>
    </#if>
    <@field type="submit" submitType="link" href="javascript:document.advtokeywordsearchform.submit()" class="+${styles.link_run_sys!} ${styles.action_find!}" text=uiLabelMap.CommonFind />
</@section>

  <#if searchOptionsHistoryList?has_content>
    <hr />

    <@section title="${rawLabel('OrderLastSearches')}...">

    <div>
      <a href="<@pageUrl>clearSearchOptionsHistoryList</@pageUrl>" class="${styles.link_run_sys!} ${styles.action_clear!}">${uiLabelMap.OrderClearSearchHistory}</a>
      ${uiLabelMap.OrderClearSearchHistoryNote}
    </div>
    <#list searchOptionsHistoryList as searchOptions>
    <#-- searchOptions type is ProductSearchSession.ProductSearchOptions -->
        <div>
          <b>${uiLabelMap.CommonSearch} #${searchOptions_index + 1}</b>
          <a href="<@pageUrl>setCurrentSearchFromHistoryAndSearch?searchHistoryIndex=${searchOptions_index}&amp;clearSearch=N</@pageUrl>" class="${styles.link_run_sys!} ${styles.action_find!}">${uiLabelMap.CommonSearch}</a>
          <a href="<@pageUrl>setCurrentSearchFromHistory?searchHistoryIndex=${searchOptions_index}</@pageUrl>" class="${styles.link_nav!} ${styles.action_find!}">${uiLabelMap.CommonRefine}</a>
        </div>
        <#assign constraintStrings = searchOptions.searchGetConstraintStrings(false, delegator, locale)>
        <#list constraintStrings as constraintString>
          <div>&nbsp;-&nbsp;${constraintString}</div>
        </#list>
        <#if searchOptions_has_next>
          <hr />
        </#if>
    </#list>
    </@section>
  </#if>
</form>


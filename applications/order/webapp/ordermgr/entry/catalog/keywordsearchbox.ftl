<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->

    <form name="keywordsearchform" id="keywordsearchbox_keywordsearchform" method="post" action="<@pageUrl secure=true>keywordsearch</@pageUrl>">
        <@row collapse=true class="keywordsearch">
            <@cell small=10>
                <@field type="input" name="SEARCH_STRING" value=(requestParameters.SEARCH_STRING!) widgetOnly=true/>
            </@cell>
            <@cell small=2>
              <a href="javascript:{}" onclick="document.getElementById('keywordsearchbox_keywordsearchform').submit();" class="${styles.button!} ${styles.expand!}"><i class="${styles.icon!} ${styles.icon_prefix!}magnifying-glass ${styles.icon_prefix!}search"></i></a>
            </@cell>
        </@row>
        <#-- ALTERNATIVE (example):
        <#assign postfixContent><a href="javascript:{}" onclick="document.getElementById('keywordsearchbox_keywordsearchform').submit();" class="${styles.button!} ${styles.expand!}"><i class="${styles.icon!} ${styles.icon_prefix!}magnifying-search ${styles.icon_prefix!}magnify"></i></a></#assign>
        <@field type="input" name="SEARCH_STRING" value=(requestParameters.SEARCH_STRING!) collapse=true labelArea=false postfixColumns=3 postfix=true postfixContent=postfixContent/>
        -->
    </form>

<#-- SCIPIO: Replaced by tiny form
<@section title=uiLabelMap.ProductSearchCatalog id="keywordsearchbox">
    <form name="keywordsearchform" id="keywordsearchbox_keywordsearchform" method="post" action="<@pageUrl>keywordsearch</@pageUrl>">
      <@fields type="default-manual">
        <@field type="input" name="SEARCH_STRING" size="14" maxlength="50" value=(requestParameters.SEARCH_STRING!) />
        
        <#if (0 < otherSearchProdCatalogCategories?size)>
          <@field type="select" label=uiLabelMap.ProductCategoryId name="SEARCH_CATEGORY_ID" size="1">
              <option value="${searchCategoryId!}">${uiLabelMap.ProductEntireCatalog}</option>
            <#list otherSearchProdCatalogCategories as otherSearchProdCatalogCategory>
              <#assign searchProductCategory = otherSearchProdCatalogCategory.getRelatedOne("ProductCategory", true)>
              <#if searchProductCategory??>
                <option value="${searchProductCategory.productCategoryId}">${searchProductCategory.description?default("No Description " + searchProductCategory.productCategoryId)}</option>
              </#if>
            </#list>
          </@field>
        <#else>
          <input type="hidden" name="SEARCH_CATEGORY_ID" value="${searchCategoryId!}" />
        </#if>
          <@field type="radio" name="SEARCH_OPERATOR" id="SEARCH_OPERATOR_OR" value="OR" checked=(searchOperator == "OR") label=uiLabelMap.CommonAny/>
          <@field type="radio" name="SEARCH_OPERATOR" id="SEARCH_OPERATOR_AND" value="AND" checked=(searchOperator == "AND") label=uiLabelMap.CommonAll/>
          <@field type="submit" text=uiLabelMap.CommonFind class="+${styles.link_run_sys!} ${styles.action_find!}" />
      </@fields>
    </form>
    
    <form name="advancedsearchform" id="keywordsearchbox_advancedsearchform" method="post" action="<@pageUrl>advancedsearch</@pageUrl>">
      <@fieldset title=uiLabelMap.CommonAdvancedSearch collapsed=true>
        <#if (0 < otherSearchProdCatalogCategories?size)>
            <@field type="select" label=uiLabelMap.ProductAdvancedSearchIn name="SEARCH_CATEGORY_ID" id="SEARCH_CATEGORY_ID">
              <option value="${searchCategoryId!}">${uiLabelMap.ProductEntireCatalog}</option>
              <#list otherSearchProdCatalogCategories as otherSearchProdCatalogCategory>
                <#assign searchProductCategory = otherSearchProdCatalogCategory.getRelatedOne("ProductCategory", true)>
                <#if searchProductCategory??>
                  <option value="${searchProductCategory.productCategoryId}">${searchProductCategory.description?default("No Description " + searchProductCategory.productCategoryId)}</option>
                </#if>
              </#list>
            </@field>
        <#else>
          <input type="hidden" name="SEARCH_CATEGORY_ID" value="${searchCategoryId!}" />
        </#if>
          <@field type="submit" text=uiLabelMap.ProductAdvancedSearch class="+${styles.link_run_sys!} ${styles.action_find!}" />
      </@fieldset>
    </form>
</@section>
-->
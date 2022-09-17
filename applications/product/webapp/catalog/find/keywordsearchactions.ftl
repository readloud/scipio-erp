<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->

<#if productIds?has_content>
    <hr />
    <p><b>${uiLabelMap.ProductNote}:</b> ${uiLabelMap.ProductNoteKeywordSearch}</p>
    <hr />

    <@render resource="component://product/widget/catalog/ProductScreens.xml#CreateVirtualWithVariantsFormInclude" />

    <@section title="${rawLabel('ProductRemoveResultsFrom')} ${rawLabel('ProductCategory')}">
        <form method="post" action="<@pageUrl>searchRemoveFromCategory</@pageUrl>" name="searchRemoveFromCategory">
          <input type="hidden" name="clearSearch" value="N" />
          <@field type="lookup" label=uiLabelMap.ProductCategory formName="searchRemoveFromCategory" name="SE_SEARCH_CATEGORY_ID" id="SE_SEARCH_CATEGORY_ID" fieldFormName="LookupProductCategory"/>
          <@field type="submit" text=uiLabelMap.CommonRemove class="+${styles.link_run_session!} ${styles.action_remove!}" />
        </form>
    </@section>

    <@section title="${rawLabel('ProductExpireResultsFrom')} ${rawLabel('ProductCategory')}">
        <form method="post" action="<@pageUrl>searchExpireFromCategory</@pageUrl>" name="searchExpireFromCategory">
          <input type="hidden" name="clearSearch" value="N" />
          <@field type="lookup" label=uiLabelMap.ProductCategory formName="searchExpireFromCategory" name="SE_SEARCH_CATEGORY_ID" id="SE_SEARCH_CATEGORY_ID" fieldFormName="LookupProductCategory"/>
          <@field type="datetime" label=uiLabelMap.CommonThru name="thruDate" value="" size="25" maxlength="30" id="thruDate1"/>
          <@field type="submit" text=uiLabelMap.CommonExpire class="+${styles.link_run_session!} ${styles.action_terminate!}" />
        </form>
    </@section>

    <@section title="${rawLabel('ProductAddResultsTo')} ${rawLabel('ProductCategory')}">
        <form method="post" action="<@pageUrl>searchAddToCategory</@pageUrl>" name="searchAddToCategory">
          <input type="hidden" name="clearSearch" value="N" />
          <@field type="lookup" label=uiLabelMap.ProductCategory formName="searchAddToCategory" name="SE_SEARCH_CATEGORY_ID" id="SE_SEARCH_CATEGORY_ID" fieldFormName="LookupProductCategory"/>
          <@field type="datetime" label=uiLabelMap.CommonFrom name="fromDate" value="" size="25" maxlength="30" id="fromDate1"/>
          <@field type="submit" text=uiLabelMap.ProductAddToCategory class="+${styles.link_run_session!} ${styles.action_add!}" />
        </form>
    </@section>

    <@section title=uiLabelMap.ProductAddFeatureToResults>
        <form method="post" action="<@pageUrl>searchAddFeature</@pageUrl>" name="searchAddFeature">
          <input type="hidden" name="clearSearch" value="N" />
          <@field type="input" label=uiLabelMap.ProductFeatureId size="10" name="productFeatureId" value="" />
          <@field type="datetime" label=uiLabelMap.CommonFrom name="fromDate" value="" size="25" maxlength="30" id="fromDate2"/>
          <@field type="datetime" label=uiLabelMap.CommonThru name="thruDate" value="" size="25" maxlength="30" id="thruDate2"/>

          <@field type="input" label=uiLabelMap.CommonAmount size="5" name="amount" value="" />
          <@field type="input" label=uiLabelMap.CommonSequence size="5" name="sequenceNum" value="" />
          <#-- label for the following field used to be: ${uiLabelMap.ProductCategoryId} ??? -->
          <@field type="select" label=uiLabelMap.ProductFeatureApplicationType name="productFeatureApplTypeId" size="1">
                <#list applicationTypes as applicationType>
                   <#assign displayDesc = applicationType.get("description", locale)?default("No Description")>
                   <#if 18 < displayDesc?length>
                       <#assign displayDesc = displayDesc[0..15] + "...">
                   </#if>
                   <option value="${applicationType.productFeatureApplTypeId}">${displayDesc}</option>
                </#list>
          </@field>
          
          <@field type="submit" text=uiLabelMap.ProductAddFeature class="+${styles.link_run_session!} ${styles.action_add!}" />
        </form>
    </@section>

    <@section title=uiLabelMap.ProductRemoveFeatureFromResults>
        <form method="post" action="<@pageUrl>searchRemoveFeature</@pageUrl>" name="searchRemoveFeature">
          <input type="hidden" name="clearSearch" value="N" />
          <@field type="input" label=uiLabelMap.ProductFeatureId size="10" name="productFeatureId" value="" />
          
          <@field type="submit" text=uiLabelMap.ProductRemoveFeature class="+${styles.link_run_session!} ${styles.action_remove!}" />
        </form>
    </@section>

    <@section title=uiLabelMap.ProductSearchParameters>
        <form method="post" action="" name="searchShowParams">
          <input type="hidden" name="clearSearch" value="N" />
          <#assign searchParams = Static["org.ofbiz.product.product.ProductSearchSession"].makeSearchParametersString(session)>
          <#-- SCIPIO: TODO: Review the escaping here -->
          <@field type="input" label=uiLabelMap.ProductPlainSearchParameters size="60" name="searchParameters" readonly=true value=raw(searchParams) />
          <@field type="input" label=uiLabelMap.ProductHtmlSearchParameters size="60" name="searchParameters" readonly=true value=raw(searchParams)?html />
        </form>
    </@section>

    <@section title=uiLabelMap.ProductSearchExportProductList>
        <@field type="submit" submitType="link" href=makePageUrl("searchExportProductList?clearSearch=N") class="+${styles.link_nav!} ${styles.action_find!}" text=uiLabelMap.ProductSearchExport />
    </@section>
</#if>

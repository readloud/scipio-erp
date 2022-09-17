<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->
<#macro menuContent menuArgs={}>
  <@menu args=menuArgs>
    <@menuitem type="link" href=makePageUrl("CreateFeature?productFeatureCategoryId=${productFeatureCategoryId!}") text=uiLabelMap.ProductCreateNewFeature class="+${styles.action_nav!} ${styles.action_add!}" />
  </@menu>
</#macro>
<@section title="${rawLabel('ProductEditFeaturesForFeatureCategory')} \"${raw((curProductFeatureCategory.description)!)}\"" menuContent=menuContent>
    <form action="<@pageUrl>QuickAddProductFeatures</@pageUrl>" method="post">
      <div>
        ${uiLabelMap.CommonAdd}
        <input type="text" name="featureNum" value="1" size="3" />
        ${uiLabelMap.ProductAddFeatureToCategory}
        <input class="${styles.link_nav!} ${styles.action_add!}" type="submit" value="${uiLabelMap.CommonCreate}" />
      </div>
      <input type="hidden" name="productFeatureCategoryId" value="${productFeatureCategoryId}" />
    </form>
</@section>

<@section title=uiLabelMap.ProductProductFeatureMaintenance>

    <#assign productString = "">
    <#if productId?has_content>
      <#assign productString = "&amp;productId=" + productId>
    </#if>
    <@paginate mode="content" url=makePageUrl("EditFeatureCategoryFeatures") paramStr="productFeatureCategoryId=${productFeatureCategoryId!}${productString!}" viewSize=viewSize!1 viewIndex=viewIndex!0 listSize=listSize!0>
      <form method="post" action="<@pageUrl>UpdateProductFeatureInCategory</@pageUrl>" name="selectAllForm">
        <input type="hidden" name="_useRowSubmit" value="Y" />
        <input type="hidden" name="_checkGlobalScope" value="N" />
        <input type="hidden" name="productFeatureCategoryId" value="${productFeatureCategoryId}" />
        <@table type="data-list" autoAltRows=true>
           <@thead>
              <@tr class="header-row">
                <@th>${uiLabelMap.CommonId}</@th>
                <@th>${uiLabelMap.CommonDescription}</@th>
                <@th>${uiLabelMap.ProductFeatureType}</@th>
                <@th>${uiLabelMap.ProductFeatureCategory}</@th>
                <@th>${uiLabelMap.ProductUnitOfMeasureId}</@th>
                <@th>${uiLabelMap.ProductQuantity}</@th>
                <@th>${uiLabelMap.ProductAmount}</@th>
                <@th>${uiLabelMap.ProductIdSeqNum}</@th>
                <@th>${uiLabelMap.ProductIdCode}</@th>
                <@th>${uiLabelMap.ProductAbbrev}</@th>
                <@th align="right">${uiLabelMap.CommonAll}<input type="checkbox" name="selectAll" value="${uiLabelMap.CommonY}" onclick="javascript:toggleAll(this, 'selectAllForm');highlightAllRows(this, 'productFeatureId_tableRow_', 'selectAllForm');" /></@th>
             </@tr>
           </@thead>
        <#if (listSize > 0)>
            <@tbody>
            <#assign rowCount = 0>
            <#list productFeatures as productFeature>
            <#assign curProductFeatureType = productFeature.getRelatedOne("ProductFeatureType", true)>
            <@tr id="productFeatureId_tableRow_${rowCount}" valign="middle">
              <@td><input type="hidden" name="productFeatureId_o_${rowCount}" value="${productFeature.productFeatureId}" />
              <a href="<@pageUrl>EditFeature?productFeatureId=${productFeature.productFeatureId}</@pageUrl>" class="${styles.link_nav_info_id!}">${productFeature.productFeatureId}</a></@td>
              <@td><input type="text" size="15" name="description_o_${rowCount}" value="${productFeature.description}" /></@td>
              <@td><select name="productFeatureTypeId_o_${rowCount}" size="1">
                <#if productFeature.productFeatureTypeId?has_content>
                  <option value="${productFeature.productFeatureTypeId}"><#if curProductFeatureType??>${curProductFeatureType.get("description",locale)!}<#else> [${productFeature.productFeatureTypeId}]</#if></option>
                  <option value="${productFeature.productFeatureTypeId}">---</option>
                </#if>
                <#list productFeatureTypes as productFeatureType>
                  <option value="${productFeatureType.productFeatureTypeId}">${productFeatureType.get("description",locale)!}</option>
                </#list>
              </select></@td>
              <@td><select name="productFeatureCategoryId_o_${rowCount}" size="1">
                <#if productFeature.productFeatureCategoryId?has_content>
                  <#assign curProdFeatCat = productFeature.getRelatedOne("ProductFeatureCategory", false)>
                  <option value="${productFeature.productFeatureCategoryId}">${(curProdFeatCat.description)!} [${productFeature.productFeatureCategoryId}]</option>
                  <option value="${productFeature.productFeatureCategoryId}">---</option>
                </#if>
                <#list productFeatureCategories as productFeatureCategory>
                  <option value="${productFeatureCategory.productFeatureCategoryId}">${productFeatureCategory.get("description",locale)!} [${productFeatureCategory.productFeatureCategoryId}]</option>
                </#list>
              </select></@td>
              <@td><input type="text" size="10" name="uomId_o_${rowCount}" value="${productFeature.uomId!}" /></@td>
              <@td><input type="text" size="5" name="numberSpecified_o_${rowCount}" value="${productFeature.numberSpecified!}" /></@td>
              <@td><input type="text" size="5" name="defaultAmount_o_${rowCount}" value="${productFeature.defaultAmount!}" /></@td>
              <@td><input type="text" size="5" name="defaultSequenceNum_o_${rowCount}" value="${productFeature.defaultSequenceNum!}" /></@td>
              <@td><input type="text" size="5" name="idCode_o_${rowCount}" value="${productFeature.idCode!}" /></@td>
              <@td><input type="text" size="5" name="abbrev_o_${rowCount}" value="${productFeature.abbrev!}" /></@td>
              <@td align="right"><input type="checkbox" name="_rowSubmit_o_${rowCount}" value="Y" onclick="javascript:checkToggle(this, 'selectAllForm');highlightRow(this,'productFeatureId_tableRow_${rowCount}');" /></@td>
            </@tr>
            <#assign rowCount = rowCount + 1>
            </#list>
            </@tbody>
            <@tfoot>
            <@tr><@td colspan="11" align="center">
              <input type="hidden" name="_rowCount" value="${rowCount}" />
              <input type="submit" value="${uiLabelMap.CommonUpdate}" class="${styles.link_run_sys!} ${styles.action_update!}"/>
            </@td></@tr>
            </@tfoot>
        </#if>
        </@table>
      </form>
    </@paginate>
 
</@section>

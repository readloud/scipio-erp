<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->

<#if requestParameters.lookupFlag?default("N") == "Y">

<#if selectedFeatures?has_content>
  <#assign sectionTitle = uiLabelMap.ManufacturingSelectedFeatures/>
  <#macro menuContent menuArgs={}>
    <@menu args=menuArgs></@menu>
  </#macro>
<#else>
  <#assign sectionTitle = uiLabelMap.ManufacturingBomSimulation/>
  <#macro menuContent menuArgs={}>
    <@menu args=menuArgs></@menu>
  </#macro>
</#if>
  <@section title=sectionTitle menuContent=menuContent>
     <#if selectedFeatures?has_content>
       <#list selectedFeatures as selectedFeature>
         <p>${selectedFeature.productFeatureTypeId} = ${selectedFeature.description!} [${selectedFeature.productFeatureId}]</p>
       </#list>
     </#if>
    <@section title=getLabel("ContentTree", "ContentUiLabels")>
    <#if tree?has_content>
      <@table type="data-list" autoAltRows=true>
       <@thead>
        <@tr class="header-row">
          <@th width="10%">${uiLabelMap.ManufacturingProductLevel}</@th>
          <@th width="20%">${uiLabelMap.ProductProductId}</@th>
          <@th width="10%">${uiLabelMap.ManufacturingProductVirtual}</@th>
          <@th width="40%">${uiLabelMap.ProductProductName}</@th>
          <@th width="10%" align="right">${uiLabelMap.CommonQuantity}</@th>
          <@th width="10%" align="right">&nbsp;</@th>
        </@tr>
        </@thead>
        <@tbody>
          <#list tree as node>
            <@tr valign="middle">
              <@td>
                <@table type="generic" cellspacing="1">
                  <@tr>
                    <@td>${node.depth}</@td>
                  <#list 0..(node.depth) as level>
                    <@td bgcolor="red">&nbsp;&nbsp;</@td>
                  </#list>
                  </@tr>
                </@table>
              </@td>
              <@td>
                <@table type="generic" cellspacing="1">
                  <@tr>
                  <#list 0..(node.depth) as level>
                    <@td>&nbsp;&nbsp;</@td>
                  </#list>
                    <@td>
                      ${node.product.productId}
                    </@td>
                  </@tr>
                </@table>
              </@td>
              <@td>
                <#if node.product.isVirtual?default("N") == "Y">
                    ${node.product.isVirtual}
                </#if>
                ${(node.ruleApplied.ruleId)!}
              </@td>
              <@td>${node.product.internalName?default("&nbsp;")}</@td>
              <@td align="right">${node.quantity}</@td>
              <@td align="right"><a href="<@pageUrl>EditProductBom?productId=${(node.product.productId)!}&amp;productAssocTypeId=${(node.bomTypeId)!}</@pageUrl>" class="${styles.link_nav!} ${styles.action_update!}">${uiLabelMap.CommonEdit}</a></@td>
            </@tr>
          </#list>
        </@tbody>
      </@table>
    <#else>
      <@commonMsg type="result-norecord">${uiLabelMap.CommonNoElementFound}.</@commonMsg>
    </#if>
    </@section>
    
    <@section title=uiLabelMap.ProductProducts>
    <#if productsData?has_content>
      <@table type="data-list" autoAltRows=true>
       <@thead>
        <@tr class="header-row">
          <@th width="20%">${uiLabelMap.ProductProductId}</@th>
          <@th width="50%">${uiLabelMap.ProductProductName}</@th>
          <@th width="6%" align="right">${uiLabelMap.CommonQuantity}</@th>
          <@th width="6%" align="right">${uiLabelMap.ProductQoh}</@th>
          <@th width="6%" align="right">${uiLabelMap.ProductWeight}</@th>
          <@th width="6%" align="right">${uiLabelMap.FormFieldTitle_cost}</@th>
          <@th width="6%" align="right">${uiLabelMap.CommonTotalCost}</@th>
        </@tr>
        </@thead>
        <@tbody>
          <#list productsData as productData>
            <#assign node = productData.node>
            <@tr valign="middle">
              <@td><a href="<@serverUrl>/catalog/control/ViewProduct?productId=${node.product.productId}${raw(externalKeyParam)}</@serverUrl>" class="${styles.link_nav_info_id!} ${styles.action_update!}">${node.product.productId}</a></@td>
              <@td>${node.product.internalName?default("&nbsp;")}</@td>
              <@td align="right">${node.quantity}</@td>
              <@td align="right">${productData.qoh!}</@td>
              <@td align="right">${node.product.productWeight!}</@td>
              <#if productData.unitCost?? && (productData.unitCost > 0)>
              <@td align="right">${productData.unitCost!}</@td>
              <#else>
              <@td align="right"><a href="<@serverUrl>/catalog/control/EditProductCosts?productId=${node.product.productId}${raw(externalKeyParam)}</@serverUrl>" class="${styles.link_nav!} ${styles.action_update!}">NA</a></@td>
              </#if>
              <@td align="right">${productData.totalCost!}</@td>
            </@tr>
          </#list>
        </@tbody>
        <#--
        <@tfoot>
          <#if grandTotalCost??>
          <@tr>
            <@td colspan="6" align="right">${grandTotalCost}</@td>
          </@tr>
          </#if>
        </@tfoot>
        -->
      </@table>
    <#else>
      <@commonMsg type="result-norecord">${uiLabelMap.CommonNoElementFound}.</@commonMsg>
    </#if>
    </@section>

  </@section>
</#if>

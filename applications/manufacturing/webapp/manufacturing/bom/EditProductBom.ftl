<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->

<@script>
function lookupBom() {
    document.searchbom.productId.value=document.editProductAssocForm.productId.value;
    document.searchbom.productAssocTypeId.value=document.editProductAssocForm.productAssocTypeId.options[document.editProductAssocForm.productAssocTypeId.selectedIndex].value;
    document.searchbom.submit();
}
</@script>
<#-- NOTE: this is a little different: we "create"/add even if have existing product... it's presence of association that determine if we create or add -->
<#if productAssoc??>
  <#assign sectionTitle>${rawLabel('PageTitleEditProductBom')}<#if product??>: ${(raw(product.internalName)!)} [${rawLabel('CommonId')} ${raw(productId!)}]</#if></#assign>
<#else>
  <#assign sectionTitle>${rawLabel('ManufacturingCreateProductBom')}<#if product??>: ${(raw(product.internalName)!)} [${rawLabel('CommonId')} ${raw(productId!)}]</#if></#assign>
</#if>
<#macro menuContent menuArgs={}>
  <@menu args=menuArgs>
  <#if product?has_content>
    <@menuitem type="link" href=makePageUrl("BomSimulation?productId=${productId}&bomType=${productAssocTypeId!}") text=uiLabelMap.ManufacturingBomSimulation class="+${styles.action_nav!}" />
  </#if>
  </@menu>
</#macro>
<@section title=sectionTitle menuContent=menuContent>
    
  <form name="searchform" action="<@pageUrl>UpdateProductBom</@pageUrl>#topform" method="post">
    <input type="hidden" name="UPDATE_MODE" value=""/>

    <@row>
        <@cell columns=6>
            <a name="topform"></a>
            <@field type="select" label=uiLabelMap.ManufacturingBomType name="productAssocTypeId" size="1">
                <#if productAssocTypeId?has_content>
                    <#assign curAssocType = delegator.findOne("ProductAssocType", {"productAssocTypeId":productAssocTypeId}, false)>
                    <#if curAssocType??>
                        <option selected="selected" value="${(curAssocType.productAssocTypeId)!}">${(curAssocType.get("description",locale))!}</option>
                        <option value="${(curAssocType.productAssocTypeId)!}"></option>
                    </#if>
                </#if>
                <#list assocTypes as assocType>
                    <option value="${(assocType.productAssocTypeId)!}">${(assocType.get("description",locale))!}</option>
                </#list>
            </@field>
        </@cell>
        <@cell columns=6>
            <@field type="lookup" label=uiLabelMap.ProductProductId value=(productId!) formName="searchform" name="productId" id="productId" fieldFormName="LookupProduct"/>
            <@field type="submit" submitType="link" href="javascript:document.searchform.submit();" class="+${styles.link_run_sys!} ${styles.action_find!}" text=uiLabelMap.ManufacturingShowBOMAssocs />
        </@cell>
    </@row>
    <@row>
        <@cell columns=6 offset=6>
            <@field type="lookup" label=uiLabelMap.ManufacturingCopyToProductId formName="searchform" name="copyToProductId" id="copyToProductId" fieldFormName="LookupProduct"/>
            <@field type="submit" submitType="link" href="javascript:document.searchform.UPDATE_MODE.value='COPY';document.searchform.submit();" class="+${styles.link_run_sys!} ${styles.action_copy!}" text=uiLabelMap.ManufacturingCopyBOMAssocs />
        </@cell>
    </@row>
  </form>
  
    <hr />
    
  <@row>
    <@cell>
    <form action="<@pageUrl>UpdateProductBom</@pageUrl>" method="post" name="editProductAssocForm">
    <#if !(productAssoc??)>
        <input type="hidden" name="UPDATE_MODE" value="CREATE"/>
    <#else>
        <#assign curProductAssocType = productAssoc.getRelatedOne("ProductAssocType", true)>
        <input type="hidden" name="UPDATE_MODE" value="UPDATE"/>
        <input type="hidden" name="productId" value="${productId!}"/>
        <input type="hidden" name="productIdTo" value="${productIdTo!}"/>
        <input type="hidden" name="productAssocTypeId" value="${productAssocTypeId!}"/>
        <input type="hidden" name="fromDate" value="${fromDate!}"/>
    </#if>
    

    <#if !(productAssoc??)>
          <@field type="select" label=uiLabelMap.ManufacturingBomType name="productAssocTypeId" size="1">
                <#if productAssocTypeId?has_content>
                    <#assign curAssocType = delegator.findOne("ProductAssocType", {"productAssocTypeId":productAssocTypeId}, false)>
                    <#if curAssocType??>
                        <option selected="selected" value="${(curAssocType.productAssocTypeId)!}">${(curAssocType.get("description",locale))!}</option>
                        <option value="${(curAssocType.productAssocTypeId)!}"></option>
                    </#if>
                </#if>
                <#list assocTypes as assocType>
                    <option value="${(assocType.productAssocTypeId)!}">${(assocType.get("description",locale))!}</option>
                </#list>
          </@field>
          <@field type="lookup" required=true label=uiLabelMap.ProductProductId value=(productId!) formName="editProductAssocForm" name="productId" id="productId2" fieldFormName="LookupProduct"/>
          <@field type="lookup" required=true label=uiLabelMap.ManufacturingProductIdTo value=(productIdTo!) formName="editProductAssocForm" name="productIdTo" id="productIdTo" fieldFormName="LookupProduct"/>
          <@field type="datetime" label=uiLabelMap.CommonFromDate tooltip="(${rawLabel('ManufacturingWillBeSetToNow')})" name="fromDate" value="" size="25" maxlength="50" id="fromDate_1"/>
    <#else>
          <@field type="display" label=uiLabelMap.ProductProductId>${productId!}</@field>
          <@field type="display" label=uiLabelMap.ManufacturingProductIdTo>${productIdTo!}</@field>
          <@field type="display" label=uiLabelMap.ManufacturingBomType><#if curProductAssocType??>${(curProductAssocType.get("description",locale))!}<#else> ${productAssocTypeId!}</#if></@field>
          <@field type="display" label=uiLabelMap.CommonFromDate>${fromDate?date?string.short!}</@field>
    </#if>
    
    <#if useValues> 
      <#assign value = productAssoc.thruDate!>
    <#else>
      <#assign value = request.getParameter("thruDate")!>
    </#if>
    <@field type="datetime" label=uiLabelMap.CommonThruDate value=value!'' name="thruDate" size="30" maxlength="30" id="fromDate_2"/>
    <@field type="input" label=uiLabelMap.CommonSequenceNum name="sequenceNum" value=useValues?string("${(productAssoc.sequenceNum)!}", "${(request.getParameter('sequenceNum'))!}") size="5" maxlength="10"/>
    <@field type="input" label=uiLabelMap.ManufacturingReason name="reason" value=useValues?string("${(productAssoc.reason)!}", "${(request.getParameter('reason'))!}") size="60" maxlength="255"/>
    <@field type="input" label=uiLabelMap.ManufacturingInstruction name="instruction" value=useValues?string("${(productAssoc.instruction)!}", "${(request.getParameter('instruction'))!}") size="60" maxlength="255"/>
    <@field type="input" label=uiLabelMap.ManufacturingQuantity name="quantity" value=useValues?string("${(productAssoc.quantity)!}", "${(request.getParameter('quantity'))!}") size="10" maxlength="15"/>
    <@field type="input" label=uiLabelMap.ManufacturingScrapFactor name="scrapFactor" value=useValues?string("${(productAssoc.scrapFactor)!}", "${(request.getParameter('scrapFactor'))!}") size="10" maxlength="15"/>
    <@field type="select" label=uiLabelMap.ManufacturingFormula name="estimateCalcMethod">
        <option value=""></option>
        <#assign selectedFormula = "">
        <#if useValues>
            <#assign selectedFormula = (productAssoc.estimateCalcMethod)!>
        <#else>
            <#assign selectedFormula = (request.getParameter("estimateCalcMethod"))!>
        </#if>
        <#list formulae as formula>
            <option value="${formula.customMethodId}"<#if selectedFormula == formula.customMethodId> selected="selected"</#if>>${formula.get("description",locale)!}</option>
        </#list>
    </@field>
    <#if useValues>
      <#assign value = productAssoc.routingWorkEffortId!>
    <#else>
      <#assign value = request.getParameter("routingWorkEffortId")!>
    </#if>
  <#if value?has_content>
    <@field type="lookup" label=uiLabelMap.ManufacturingRoutingTask value=value formName="editProductAssocForm" name="routingWorkEffortId" id="routingWorkEffortId" fieldFormName="LookupRoutingTask"/>
  <#else>
    <@field type="lookup" label=uiLabelMap.ManufacturingRoutingTask formName="editProductAssocForm" name="routingWorkEffortId" id="routingWorkEffortId" fieldFormName="LookupRoutingTask"/>
  </#if>
    
    <#if !(productAssoc??)>
      <@field type="submit" text=uiLabelMap.CommonAdd class="+${styles.link_run_sys!} ${styles.action_add!}" />
    <#else>
      <@field type="submit" text=uiLabelMap.CommonEdit class="+${styles.link_run_sys!} ${styles.action_update!}" />
    </#if>
    </form>
    </@cell>
  </@row>
</@section>

<#if productId?? && product??>
  <@section title=uiLabelMap.ManufacturingProductComponents>
    <a name="components"></a>
    <@table type="data-list" autoAltRows=true>
     <@thead>
      <@tr class="header-row">
        <@th>${uiLabelMap.ProductProductId}</@th>
        <@th>${uiLabelMap.ProductProductName}</@th>
        <@th>${uiLabelMap.CommonFromDate}</@th>
        <@th>${uiLabelMap.CommonThruDate}</@th>
        <@th>${uiLabelMap.CommonSequenceNum}</@th>
        <@th>${uiLabelMap.CommonQuantity}</@th>
        <@th>${uiLabelMap.ManufacturingScrapFactor}</@th>
        <@th>${uiLabelMap.ManufacturingFormula}</@th>
        <@th>${uiLabelMap.ManufacturingRoutingTask}</@th>
        <@th></@th>
        <@th></@th>
      </@tr>
    </@thead>
    <#list assocFromProducts! as assocFromProduct>
    <#assign listToProduct = assocFromProduct.getRelatedOne("AssocProduct", true)>
    <#assign curProductAssocType = assocFromProduct.getRelatedOne("ProductAssocType", true)>
      <@tr>
        <@td><a href="<@pageUrl>EditProductBom?productId=${(assocFromProduct.productIdTo)!}&amp;productAssocTypeId=${(assocFromProduct.productAssocTypeId)!}#components</@pageUrl>" class="${styles.link_nav_info_id!}">${(assocFromProduct.productIdTo)!}</a></@td>
        <@td><#if listToProduct??><a href="<@pageUrl>EditProductBom?productId=${(assocFromProduct.productIdTo)!}&amp;productAssocTypeId=${(assocFromProduct.productAssocTypeId)!}#components</@pageUrl>" class="${styles.link_nav_info_name!}">${(listToProduct.internalName)!}</a></#if></@td>
        <#if (assocFromProduct.getTimestamp("fromDate"))?? && nowDate.before(assocFromProduct.getTimestamp("fromDate"))>
          <#assign class = "alert-elem">
        <#else>
          <#assign class = "">
        </#if>
        <@td class=class>${(assocFromProduct.fromDate)!}</@td>
        <#if (assocFromProduct.getTimestamp("thruDate"))?? && nowDate.after(assocFromProduct.getTimestamp("thruDate"))>
          <#assign class = "alert-elem">
        <#else>
          <#assign class = "">
        </#if>
        <@td class=class>${(assocFromProduct.thruDate)!}</@td>
        <@td>${(assocFromProduct.sequenceNum)!}</@td>
        <@td>${(assocFromProduct.quantity)!}</@td>
        <@td>${(assocFromProduct.scrapFactor)!}</@td>
        <@td>${(assocFromProduct.estimateCalcMethod)!}</@td>
        <@td>${(assocFromProduct.routingWorkEffortId)!}</@td>
        <@td>
        <a href="<@pageUrl>UpdateProductBom?UPDATE_MODE=DELETE&amp;productId=${productId}&amp;productIdTo=${(assocFromProduct.productIdTo)!}&amp;productAssocTypeId=${(assocFromProduct.productAssocTypeId)!}&amp;fromDate=${(assocFromProduct.fromDate)!}&amp;useValues=true</@pageUrl>" class="${styles.link_run_sys!} ${styles.action_remove!}">${uiLabelMap.CommonDelete}</a>
        </@td>
        <@td>
        <a href="<@pageUrl>EditProductBom?productId=${productId}&amp;productIdTo=${(assocFromProduct.productIdTo)!}&amp;productAssocTypeId=${(assocFromProduct.productAssocTypeId)!}&amp;fromDate=${(assocFromProduct.fromDate)!}&amp;useValues=true</@pageUrl>" class="${styles.link_nav!} ${styles.action_update!}">${uiLabelMap.CommonEdit}</a>
        </@td>
      </@tr>
    </#list>
    </@table>
  </@section>
  <@section title=uiLabelMap.ManufacturingProductComponentOf>
    <#if assocToProducts?has_content>
    <@table type="data-list" autoAltRows=true>
      <@thead>
        <@tr class="header-row">
            <@th>${uiLabelMap.ProductProductId}</@th>
            <@th>${uiLabelMap.ProductProductName}</@th>
            <@th>${uiLabelMap.CommonFromDate}</@th>
            <@th>${uiLabelMap.CommonThruDate}</@th>
            <@th>${uiLabelMap.CommonQuantity}</@th>
            <@th></@th>
        </@tr>
      </@thead>
      <@tbody>
        <#list assocToProducts! as assocToProduct>
        <#assign listToProduct = assocToProduct.getRelatedOne("MainProduct", true)>
        <#assign curProductAssocType = assocToProduct.getRelatedOne("ProductAssocType", true)>
        <@tr>
            <@td><a href="<@pageUrl>EditProductBom?productId=${(assocToProduct.productId)!}&amp;productAssocTypeId=${(assocToProduct.productAssocTypeId)!}#components</@pageUrl>" class="${styles.link_nav_info_id!}">${(assocToProduct.productId)!}</a></@td>
<#--                <@td><#if listToProduct??><a href="<@pageUrl>ViewProduct?productId=${(assocToProduct.productId)!}</@pageUrl>" class="${styles.link_nav_info_name!}">${(listToProduct.internalName)!}</a></#if></@td> -->
            <@td><#if listToProduct??><a href="<@pageUrl>EditProductBom?productId=${(assocToProduct.productId)!}&amp;productAssocTypeId=${(assocToProduct.productAssocTypeId)!}#components</@pageUrl>" class="${styles.link_nav_info_name!}">${(listToProduct.internalName)!}</a></#if></@td>
            <@td>${(assocToProduct.getTimestamp("fromDate"))!}</@td>
            <@td>${(assocToProduct.getTimestamp("thruDate"))!}</@td>
            <@td>${(assocToProduct.quantity)!}</@td>
            <@td>
                <a href="<@pageUrl>UpdateProductBom?UPDATE_MODE=DELETE&amp;productId=${(assocToProduct.productId)!}&amp;productIdTo=${(assocToProduct.productIdTo)!}&amp;productAssocTypeId=${(assocToProduct.productAssocTypeId)!}&amp;fromDate=${UtilFormatOut.encodeQueryValue(assocToProduct.getTimestamp("fromDate").toString())}&amp;useValues=true</@pageUrl>" class="${styles.link_run_sys!} ${styles.action_remove!}">
                ${uiLabelMap.CommonDelete}</a>
            </@td>
        </@tr>
        </#list>
      </@tbody>
    </@table>
      <p>${uiLabelMap.CommonNote}: <b class="alert-elem">${uiLabelMap.CommonRed}</b> ${uiLabelMap.ManufacturingNote1} <b class="${styles.text_color_alert!}">${uiLabelMap.CommonRed}</b>${uiLabelMap.ManufacturingNote2} <b class="${styles.text_color_alert!}">${uiLabelMap.CommonRed}</b>${uiLabelMap.ManufacturingNote3}<p>
    <#else>
      <@commonMsg type="result-norecord"/>
    </#if>
  </@section>
</#if>

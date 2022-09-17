<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->

<@section>

    <#if facilityId?? && !(facilityLocation??)>
      <form action="<@pageUrl>CreateFacilityLocation</@pageUrl>" method="post">
        <input type="hidden" name="facilityId" value="${facilityId}" />
    <#elseif facilityLocation??>
      <form action="<@pageUrl>UpdateFacilityLocation</@pageUrl>" method="post">
        <input type="hidden" name="facilityId" value="${facilityId!}" />
        <input type="hidden" name="locationSeqId" value="${locationSeqId}" />
    <#else>
        <@commonMsg type="error">${uiLabelMap.ProductNotCreateLocationFacilityId}</@commonMsg>
    </#if>
    
    <#if facilityId?? || facilityLocation??>
    
        <#if facilityLocation??>
            <@field type="display" label=uiLabelMap.ProductLocationSeqId>
                ${locationSeqId}
            </@field>
        </#if>
    
        <@field type="select" label=uiLabelMap.ProductType name="locationTypeEnumId">
            <#if (facilityLocation.locationTypeEnumId)?has_content>
                <#assign locationTypeEnum = facilityLocation.getRelatedOne("TypeEnumeration", true)!>
                <option value="${facilityLocation.locationTypeEnumId}">${(locationTypeEnum.get("description",locale))?default(facilityLocation.locationTypeEnumId)}</option>
                <option value="${facilityLocation.locationTypeEnumId}">----</option>
            </#if>
            <#list locationTypeEnums as locationTypeEnum>
                <option value="${locationTypeEnum.enumId}">${locationTypeEnum.get("description",locale)}</option>
            </#list>
        </@field>
        <@field type="input" label=uiLabelMap.CommonArea name="areaId" value=((facilityLocation.areaId)!) size="19" maxlength="20" />
        <@field type="input" label=uiLabelMap.ProductAisle name="aisleId" value=((facilityLocation.aisleId)!) size="19" maxlength="20" />
        <@field type="input" label=uiLabelMap.ProductSection name="sectionId" value=((facilityLocation.sectionId)!) size="19" maxlength="20" />
        <@field type="input" label=uiLabelMap.ProductLevel name="levelId" value=((facilityLocation.levelId)!) size="19" maxlength="20" />
        <@field type="input" label=uiLabelMap.ProductPosition name="positionId" value=((facilityLocation.positionId)!) size="19" maxlength="20" />
        <#if locationSeqId??>
          <@field type="submit" text=uiLabelMap.CommonUpdate class="+${styles.link_run_sys!} ${styles.action_update!}" />
        <#else>
          <@field type="submit" text=uiLabelMap.CommonSave class="+${styles.link_run_sys!} ${styles.action_add!}" />
        </#if>
      </form>
      
      <#if locationSeqId??>    
          <#assign sectionTitle = uiLabelMap.ProductLocationProduct/>
          <@section title=sectionTitle>
                <#-- ProductFacilityLocation stuff -->
                <@table type="data-list">
                <@thead>
                <@tr class="header-row">
                    <@th>${uiLabelMap.ProductProduct}</@th>
                    <@th>${uiLabelMap.ProductMinimumStockAndMoveQuantity}</@th>
                </@tr>
                </@thead>
                <#list productFacilityLocations! as productFacilityLocation>
                    <#assign product = productFacilityLocation.getRelatedOne("Product", false)!>
                    <@tr>
                        <@td><#if product??>${(product.internalName)!}</#if>[${productFacilityLocation.productId}]</@td>
                        <@td>
                            <form method="post" action="<@pageUrl>updateProductFacilityLocation</@pageUrl>" id="lineForm${productFacilityLocation_index}">
                                <input type="hidden" name="productId" value="${(productFacilityLocation.productId)!}"/>
                                <input type="hidden" name="facilityId" value="${(productFacilityLocation.facilityId)!}"/>
                                <input type="hidden" name="locationSeqId" value="${(productFacilityLocation.locationSeqId)!}"/>
                                <input type="text" size="10" name="minimumStock" value="${(productFacilityLocation.minimumStock)!}"/>
                                <input type="text" size="10" name="moveQuantity" value="${(productFacilityLocation.moveQuantity)!}"/>
                                <input type="submit" value="${uiLabelMap.CommonUpdate}" class="${styles.link_run_sys!} ${styles.action_update!}"/>
                                <a href="javascript:document.getElementById('lineForm${productFacilityLocation_index}').action='<@pageUrl>deleteProductFacilityLocation</@pageUrl>';document.getElementById('lineForm${productFacilityLocation_index}').submit();" class="${styles.link_run_sys!} ${styles.action_remove!}">${uiLabelMap.CommonDelete}</a>
                            </form>
                        </@td>
                    </@tr>
                </#list>
                </@table>
          </@section>
        
          <#assign sectionTitle = uiLabelMap.ProductAddProduct/>
          <@section title=sectionTitle>
                <form method="post" action="<@pageUrl>createProductFacilityLocation</@pageUrl>" name="createProductFacilityLocationForm">
                    <input type="hidden" name="facilityId" value="${facilityId!}" />
                    <input type="hidden" name="locationSeqId" value="${locationSeqId!}" />
                    <input type="hidden" name="useValues" value="true" />
                    <@field type="input" label=uiLabelMap.ProductProductId size="10" name="productId" />
                    <@field type="input" label=uiLabelMap.ProductMinimumStock size="10" name="minimumStock" />
                    <@field type="input" label=uiLabelMap.ProductMoveQuantity size="10" name="moveQuantity" />
                    <@field type="submit" text=uiLabelMap.CommonAdd class="+${styles.link_run_sys!} ${styles.action_add!}" />
                </form>
          </@section>
        </#if>  
    </#if>
</@section>
<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->
<#assign externalKeyParam = "&amp;externalLoginKey=" + requestAttributes.externalLoginKey!>
<#assign sectionTitle>${rawLabel('ProductInventoryItems')} ${rawLabel('CommonFor')} <#if product??>${raw((product.internalName)!)} </#if> [${raw(productId!)}]</#assign>
<#macro menuContent menuArgs={}>
  <@menu args=menuArgs>
  <#if productId?has_content>
    <@menuitem type="link" href=makeServerUrl("/facility/control/EditInventoryItem?productId=${productId}${raw(externalKeyParam)}") text=uiLabelMap.ProductCreateNewInventoryItemProduct class="+${styles.action_nav!} ${styles.action_add!}" />
    <#if showEmpty>
      <@menuitem type="link" href=makePageUrl("EditProductInventoryItems?productId=${productId}") text=uiLabelMap.ProductHideEmptyItems class="+${styles.action_run_sys!} ${styles.action_hide!}" />
    <#else>
      <@menuitem type="link" href=makePageUrl("EditProductInventoryItems?productId=${productId}&showEmpty=true") text=uiLabelMap.ProductShowEmptyItems class="+${styles.action_run_sys!} ${styles.action_show!}" />
    </#if>
  </#if>
  </@menu>
</#macro>
<@section title=sectionTitle menuContent=menuContent>
  <#if product??>
        <#if productId??>
          <@table type="data-list" autoAltRows=true>
            <@thead>
              <@tr class="header-row">
                <@th>${uiLabelMap.ProductItemId}</@th>
                <@th>${uiLabelMap.ProductItemType}</@th>
                <@th>${uiLabelMap.CommonStatus}</@th>
                <@th>${uiLabelMap.CommonReceived}</@th>
                <@th>${uiLabelMap.CommonExpire}</@th>
                <@th>${uiLabelMap.ProductFacilityContainerId}</@th>
                <@th>${uiLabelMap.ProductLocation}</@th>
                <@th>${uiLabelMap.ProductLotId}</@th>
                <@th>${uiLabelMap.ProductBinNum}</@th>
                <@th align="right">${uiLabelMap.ProductPerUnitPrice}</@th>
                <@th>&nbsp;</@th>
                <@th align="right">${uiLabelMap.ProductInventoryItemInitialQuantity}</@th>
                <@th align="right">${uiLabelMap.ProductAtpQohSerial}</@th>
              </@tr>
            </@thead>
            <#list productInventoryItems as inventoryItem>
               <#-- NOTE: Delivered for serialized inventory means shipped to customer so they should not be displayed here any more -->
               <#if showEmpty || ((inventoryItem.inventoryItemTypeId!) == "SERIALIZED_INV_ITEM" && (inventoryItem.statusId!) != "INV_DELIVERED")
                              || ((inventoryItem.inventoryItemTypeId!) == "NON_SERIAL_INV_ITEM" && ((inventoryItem.availableToPromiseTotal?? && inventoryItem.availableToPromiseTotal != 0) || (inventoryItem.quantityOnHandTotal?? && inventoryItem.quantityOnHandTotal != 0)))>
                    <#assign curInventoryItemType = inventoryItem.getRelatedOne("InventoryItemType", false)>
                    <#assign curStatusItem = inventoryItem.getRelatedOne("StatusItem", true)!>
                    <#assign facilityLocation = inventoryItem.getRelatedOne("FacilityLocation", false)!>
                    <#assign facilityLocationTypeEnum = (facilityLocation.getRelatedOne("TypeEnumeration", true))!>
                    <#assign inventoryItemDetailFirst = Static["org.ofbiz.entity.util.EntityUtil"].getFirst(inventoryItem.getRelated("InventoryItemDetail", null, UtilMisc.toList("effectiveDate"), false))!>
                    <#if curInventoryItemType??>
                        <@tr valign="middle">
                            <@td><a href="<@serverUrl>/facility/control/EditInventoryItem?inventoryItemId=${(inventoryItem.inventoryItemId)!}${raw(externalKeyParam)}</@serverUrl>" class="${styles.link_nav_info_id!}">${(inventoryItem.inventoryItemId)!}</a></@td>
                            <@td>&nbsp;${(curInventoryItemType.get("description",locale))!}</@td>
                            <@td>
                                    <#if curStatusItem?has_content>
                                        ${(curStatusItem.get("description",locale))!}
                                    <#elseif inventoryItem.statusId?has_content>
                                        [${inventoryItem.statusId}]
                                    <#else>
                                        ${uiLabelMap.CommonNotSet}&nbsp;
                                    </#if>
                            </@td>
                            <@td>&nbsp;${(inventoryItem.datetimeReceived)!}</@td>
                            <@td>&nbsp;${(inventoryItem.expireDate)!}</@td>
                            <#if inventoryItem.facilityId?? && inventoryItem.containerId??>
                                <@td class="+${styles.text_color_alert!}">${uiLabelMap.ProductErrorFacility} (${inventoryItem.facilityId})
                                    ${uiLabelMap.ProductAndContainer} (${inventoryItem.containerId}) ${uiLabelMap.CommonSpecified}</@td>
                            <#elseif inventoryItem.facilityId??>
                                <@td>${uiLabelMap.ProductFacilityLetter}:&nbsp;<a href="<@serverUrl>/facility/control/EditFacility?facilityId=${inventoryItem.facilityId}${raw(externalKeyParam)}</@serverUrl>" class="${styles.link_nav_info_id!}">${inventoryItem.facilityId}</a></@td>
                            <#elseif (inventoryItem.containerId)??>
                                <@td>${uiLabelMap.ProductContainerLetter}:&nbsp;<a href="<@pageUrl>EditContainer?containerId=${inventoryItem.containerId }</@pageUrl>" class="${styles.link_nav_info_id!}">${inventoryItem.containerId}</a></@td>
                            <#else>
                                <@td>&nbsp;</@td>
                            </#if>
                            <@td><a href="<@serverUrl>/facility/control/EditFacilityLocation?facilityId=${(inventoryItem.facilityId)!}&amp;locationSeqId=${(inventoryItem.locationSeqId)!}${raw(externalKeyParam)}</@serverUrl>"><#if facilityLocation??>${facilityLocation.areaId!}:${facilityLocation.aisleId!}:${facilityLocation.sectionId!}:${facilityLocation.levelId!}:${facilityLocation.positionId!}</#if><#if facilityLocationTypeEnum?has_content> (${facilityLocationTypeEnum.get("description",locale)})</#if> [${(inventoryItem.locationSeqId)!}]</a></@td>
                            <@td>&nbsp;${(inventoryItem.lotId)!}</@td>
                            <@td>&nbsp;${(inventoryItem.binNumber)!}</@td>
                            <@td align="right"><@ofbizCurrency amount=inventoryItem.unitCost isoCode=inventoryItem.currencyUomId/></@td>
                            <@td>
                                <#if inventoryItemDetailFirst?? && inventoryItemDetailFirst.workEffortId??>
                                    <b>${uiLabelMap.ProductionRunId}</b> ${inventoryItemDetailFirst.workEffortId}
                                <#elseif inventoryItemDetailFirst?? && inventoryItemDetailFirst.orderId??>
                                    <b>${uiLabelMap.OrderId}</b> ${inventoryItemDetailFirst.orderId}
                                </#if>
                            </@td>
                            <@td align="right">${(inventoryItemDetailFirst.quantityOnHandDiff)!}</@td>
                            <#if (inventoryItem.inventoryItemTypeId!) == "NON_SERIAL_INV_ITEM">
                                <@td align="right">${(inventoryItem.availableToPromiseTotal)!"NA"}
                                    / ${(inventoryItem.quantityOnHandTotal)!"NA"}
                                </@td>
                            <#elseif (inventoryItem.inventoryItemTypeId!) == "SERIALIZED_INV_ITEM">
                                <@td align="right">&nbsp;${(inventoryItem.serialNumber)!}</@td>
                            <#else>
                                <@td align="right" class="+${styles.text_color_alert!}">${uiLabelMap.ProductErrorType} ${(inventoryItem.inventoryItemTypeId)!} ${uiLabelMap.ProductUnknownSerialNumber} (${(inventoryItem.serialNumber)!})
                                    ${uiLabelMap.ProductAndQuantityOnHand} (${(inventoryItem.quantityOnHandTotal)!} ${uiLabelMap.CommonSpecified}</@td>
                            </#if>
                        </@tr>
                    </#if>
                </#if>
            </#list>
          </@table>
        </#if>
  <#else>
    <@commonMsg type="error">${uiLabelMap.ProductProductNotFound} ${productId!}!</@commonMsg>
  </#if>
</@section>

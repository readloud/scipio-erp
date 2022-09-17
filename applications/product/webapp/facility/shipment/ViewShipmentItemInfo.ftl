<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->
<#if shipmentItemDatas?has_content>
<@section>
      <@table type="data-complex" class="+${styles.table_spacing_tiny_hint!}" autoAltRows=true>
       <@thead>
        <@tr class="header-row">
          <@th>${uiLabelMap.ProductItem}</@th>
          <@th>&nbsp;</@th>
          <@th>&nbsp;</@th>
          <@th>${uiLabelMap.ProductQuantity}</@th>
          <@th>&nbsp;</@th>
          <@th>&nbsp;</@th>
        </@tr>
       </@thead>
        <#list shipmentItemDatas as shipmentItemData>
            <#assign shipmentItem = shipmentItemData.shipmentItem>
            <#assign itemIssuances = shipmentItemData.itemIssuances>
            <#assign orderShipments = shipmentItemData.orderShipments>
            <#assign shipmentPackageContents = shipmentItemData.shipmentPackageContents>
            <#assign product = shipmentItemData.product!>
            <@tr valign="middle">
                <@td>${shipmentItem.shipmentItemSeqId}</@td>
                <@td colspan="2"><a href="<@serverUrl>/catalog/control/ViewProduct?productId=${shipmentItem.productId!}</@serverUrl>" class="${styles.link_nav_info_idname!}" target="_blank">${shipmentItem.productId!} - ${(product.internalName)!}</a></@td>
                <@td>${shipmentItem.quantity?default("&nbsp;")}</@td>
                <@td colspan="2">${shipmentItem.shipmentContentDescription?default("&nbsp;")}</@td>
            </@tr>
            <#list orderShipments as orderShipment>
                <@tr valign="middle" groupLast=true>
                    <@td>&nbsp;</@td>
                    <@td><span>${uiLabelMap.ProductOrderItem}</span> <a href="<@serverUrl>/ordermgr/control/orderview?orderId=${orderShipment.orderId!}&amp;externalLoginKey=${requestAttributes.externalLoginKey}</@serverUrl>" target="_blank" class="${styles.link_nav_info_id_long!}">${orderShipment.orderId!} - ${orderShipment.orderItemSeqId!}</a></@td>
                    <@td>&nbsp;</@td>
                    <@td>${orderShipment.quantity!}</@td>
                    <@td>&nbsp;</@td>
                    <@td>&nbsp;</@td>
                </@tr>
            </#list>
            <#list itemIssuances as itemIssuance>
                <@tr valign="middle" groupLast=true>
                    <@td>&nbsp;</@td>
                    <@td><span>${uiLabelMap.ProductOrderItem}</span> <a href="<@serverUrl>/ordermgr/control/orderview?orderId=${itemIssuance.orderId!}&amp;externalLoginKey=${requestAttributes.externalLoginKey}</@serverUrl>" target="_blank" class="${styles.link_nav_info_id_long!}">${itemIssuance.orderId!} - ${itemIssuance.orderItemSeqId!}</a></@td>
                    <@td><span>${uiLabelMap.ProductInventory}</span> <a href="<@pageUrl>EditInventoryItem?inventoryItemId=${itemIssuance.inventoryItemId!}</@pageUrl>" target="_blank" class="${styles.link_nav_info_id!}">${itemIssuance.inventoryItemId!}</a></@td>
                    <@td>${itemIssuance.quantity!}</@td>
                    <@td>${itemIssuance.issuedDateTime!}</@td>
                    <@td>${uiLabelMap.ProductFuturePartyRoleList}</@td>
                </@tr>
            </#list>
            <#list shipmentPackageContents as shipmentPackageContent>
                <@tr valign="middle" groupLast=true>
                    <@td>&nbsp;</@td>
                    <@td colspan="2"><span>${uiLabelMap.ProductPackage}</span> ${shipmentPackageContent.shipmentPackageSeqId}</@td>
                    <@td>${shipmentPackageContent.quantity!}</@td>
                    <@td colspan="2">&nbsp;</@td>
                </@tr>
            </#list>
        </#list>
      </@table>
</@section>
</#if>
<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->

<@section title="${rawLabel('ProductIssueInventoryItemsToShipment')}: [${raw(shipmentId!)}]">
  <@fields type="default-manual">
    <@table type="data-list" class="+${styles.table_spacing_tiny_hint!}">
     <@thead>
      <@tr class="header-row">
        <@th>${uiLabelMap.CommonReturn} ${uiLabelMap.CommonDescription}</@th>
        <@th>${uiLabelMap.ProductProduct}</@th>
        <@th>${uiLabelMap.OrderReturnQty}</@th>
        <@th>${uiLabelMap.ProductShipmentQty}</@th>
        <@th>${uiLabelMap.ProductTotIssuedQuantity}</@th>
        <@th></@th>
        <@th>${uiLabelMap.CommonQty} ${uiLabelMap.CommonNot} ${uiLabelMap.ManufacturingIssuedQuantity}</@th>
        <@th>${uiLabelMap.ProductInventoryItemId} ${uiLabelMap.CommonQty} ${uiLabelMap.CommonSubmit}</@th>
      </@tr>
      </@thead>
      <#list items as item>
        <@tr>
          <@td><a href="<@serverUrl>/ordermgr/control/returnMain?returnId=${item.returnId}</@serverUrl>" class="${styles.link_nav_info_id!}">${item.returnId}</a> [${item.returnItemSeqId}]</@td>
          <@td><a href="<@serverUrl>/catalog/control/EditProductInventoryItems?productId=${item.productId}</@serverUrl>" class="${styles.link_nav_info_id!}">${item.productId}</a> ${item.internalName!}</@td>
          <@td>${item.returnQuantity}</@td>
          <@td>${item.shipmentItemQty}</@td>
          <@td>${item.totalQtyIssued}</@td>
          <@td>
            <#if item.issuedItems?has_content>
              <#list item.issuedItems as issuedItem>
                <div><a href="<@serverUrl>/facility/control/EditInventoryItem?inventoryItemId=${issuedItem.inventoryItemId}</@serverUrl>" class="${styles.link_nav_info_id!}">${issuedItem.inventoryItemId}</a> ${issuedItem.quantity}</div>
              </#list>
            </#if>
          </@td>
          <@td>${item.qtyStillNeedToBeIssued}</@td>
          <#if (item.shipmentItemQty > item.totalQtyIssued)>
            <@td>
                <form name="issueInventoryItemToShipment_${item_index}" action="<@pageUrl>issueInventoryItemToShipment</@pageUrl>" method="post">
                  <input type="hidden" name="shipmentId" value="${shipmentId}"/>
                  <input type="hidden" name="shipmentItemSeqId" value="${item.shipmentItemSeqId}"/>
                  <input type="hidden" name="totalIssuedQty" value="${item.totalQtyIssued}"/>
                  <@field type="lookup" formName="issueInventoryItemToShipment_${item_index}" name="inventoryItemId" id="inventoryItemId" fieldFormName="LookupInventoryItem?orderId=${item.orderId}&amp;partyId=${item.partyId}&amp;productId=${item.productId}"/>
                  <@field type="input" size="5" name="quantity"/>
                  <@field type="submit" value=uiLabelMap.CommonSubmit class="${styles.link_run_sys!} ${styles.action_update!}"/>
                </form>
            </@td>
          </#if>
        </@tr>
      </#list>
    </@table>
  </@fields>
</@section>

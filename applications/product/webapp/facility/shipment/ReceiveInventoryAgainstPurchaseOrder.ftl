<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->

    <#-- JS to populate the quantity_o_# field required by the chained issueOrderItemToShipment service -->
    <@script>
      function populateQuantities(rowCount) {
        for (var x = 0; x <= rowCount; x++) {
          var quantityAcceptedInput = document.getElementById('quantityAccepted_o_' + x);
          var quantityInput = document.getElementById('quantity_o_' + x);
          if (quantityAcceptedInput != null && quantityInput != null) {
            quantityInput.value = quantityAcceptedInput.value;
          }
        }
      }
    </@script>

    <#assign productId = parameters.productId!/>

<@section title=uiLabelMap.ProductReceiveInventoryAgainstPurchaseOrder>

<#if shipment??>
    <#if !isPurchaseShipment>
        <@commonMsg type="error">${getLabel('ProductErrorShipmentNotPurchaseShipment', '', {'shipmentId':shipmentId!''})}</@commonMsg>
    <#elseif orderId?has_content && !orderHeader??>
        <@commonMsg type="error">${getLabel('ProductErrorOrderIdNotFound', '', {'orderId':orderId!''})}</@commonMsg>
    <#elseif orderHeader?? && orderHeader.orderTypeId != "PURCHASE_ORDER">
        <@commonMsg type="error">${getLabel('ProductErrorOrderNotPurchaseOrder', '', {'orderId':orderId!''})}</@commonMsg>
    <#elseif ProductReceiveInventoryAgainstPurchaseOrderProductNotFound??>
        <@commonMsg type="error">${getLabel('ProductReceiveInventoryAgainstPurchaseOrderProductNotFound', '', {'productId':productId!'', 'orderId':orderId!''})}</@commonMsg>
    <#elseif ProductReceiveInventoryAgainstPurchaseOrderQuantityExceedsAvailableToReceive??>
        <@commonMsg type="error">${getLabel('ProductReceiveInventoryAgainstPurchaseOrderQuantityExceedsAvailableToReceive', '', {'newQuantity':newQuantity!'', 'productId':productId!''})}</@commonMsg>
    </#if>
    <#if ProductReceiveInventoryAgainstPurchaseOrderQuantityGoesToBackOrder??>
        <@commonMsg type="info">${getLabel('ProductReceiveInventoryAgainstPurchaseOrderQuantityGoesToBackOrder', '', {'quantityToBackOrder':quantityToBackOrder!'', 'quantityToReceive':quantityToReceive!'', 'productId':productId!''})}</@commonMsg>
    </#if>
</#if>

<form name="ReceiveInventoryAgainstPurchaseOrder" action="<@pageUrl>ReceiveInventoryAgainstPurchaseOrder</@pageUrl>">
    <input type="hidden" name="clearAll" value="Y"/>
    <div>
        <@field type="input" size="20" name="shipmentId" value=(shipmentId!) label=uiLabelMap.ProductShipmentId />
        <@field type="lookup" value=(orderId!) label=uiLabelMap.ProductOrderId formName="ReceiveInventoryAgainstPurchaseOrder" name="purchaseOrderId" id="purchaseOrderId" fieldFormName="LookupOrderHeaderAndShipInfo"/>
        <@field type="input" size="20" label=uiLabelMap.ProductOrderShipGroupId name="shipGroupSeqId" value=(shipGroupSeqId!)/>
        <@field type="submit" text=uiLabelMap.CommonSelect class="${styles.link_run_sys!} ${styles.action_select!}"/>
    </div>
</form>

<#if shipment??>
    <#if isPurchaseShipment>

        <#assign itemsAvailableToReceive = ((totalAvailableToReceive!0) > 0)/>
        <#if orderItemDatas??>
            <br />
            <#assign rowCount = 0>
            <#assign totalReadyToReceive = 0/>
            <form action="<@pageUrl>issueOrderItemToShipmentAndReceiveAgainstPO?clearAll=Y</@pageUrl>" method="post" name="selectAllForm">
              <@fields type="default-manual-widgetonly">
                <input type="hidden" name="facilityId" value="${facilityId}"/>
                <input type="hidden" name="purchaseOrderId" value="${orderId}"/>
                <input type="hidden" name="shipmentId" value="${shipmentId}" />
                <input type="hidden" name="_useRowSubmit" value="Y"/>
                <@table type="data-list" autoAltRows=true>
                    <@thead>
                    <@tr class="header-row">
                        <@th>${uiLabelMap.ProductProduct}</@th>
                        <#-- Must use the uiLabelMap[""] notation since the label key has . in it -->
                        <@th>${uiLabelMap["GoodIdentificationType.description.UPCA"]}</@th>
                        <@th>${uiLabelMap.OrderOrder}</@th>
                        <@th>${uiLabelMap.OrderCancelled}</@th>
                        <@th>${uiLabelMap.OrderBackOrdered}</@th>
                        <@th>${uiLabelMap.CommonReceived}</@th>
                        <@th>${uiLabelMap.ProductOpenQuantity}</@th>
                        <@th>${uiLabelMap.ProductBackOrders}</@th>
                        <#if itemsAvailableToReceive>
                            <@th>${uiLabelMap.CommonReceive}</@th>
                            <@th>${uiLabelMap.ProductInventoryItemType}</@th>
                            <@th colspan="2" align="right">${uiLabelMap.CommonAll}<input type="checkbox" name="selectAll" value="${uiLabelMap.CommonY}" onclick="javascript:toggleAll(this, 'selectAllForm');highlightAllRows(this, 'orderItemData_tableRow_', 'selectAllForm');" />
                            </@th>
                        </#if>
                    </@tr>
                    </@thead>
                    <#list orderItemDatas! as orderItemData>
                        <#assign orderItem = orderItemData.orderItem>
                        <#assign product = orderItemData.product!>
                        <#assign itemShipGroupSeqId = orderItemData.shipGroupSeqId!>
                        <#assign totalQuantityReceived = orderItemData.totalQuantityReceived?default(0)>
                        <#assign availableToReceive = orderItemData.availableToReceive?default(0)>
                        <#assign backOrderedQuantity = orderItemData.backOrderedQuantity?default(0)>
                        <#assign fulfilledReservations = orderItemData.fulfilledReservations>
                        <@tr id="orderItemData_tableRow_${rowCount}" valign="middle">
                            <@td>${(product.internalName)!} [${orderItem.productId!(uiLabelMap.CommonNA)}]</@td>
                            <@td>
                                <#assign upcaLookup = {"productId":product.productId, "goodIdentificationTypeId":"UPCA"}/>
                                <#assign upca = delegator.findOne("GoodIdentification", upcaLookup, true)!/>
                                <#if upca?has_content>
                                    ${upca.idValue!}
                                </#if>
                            </@td>
                            <@td>${orderItem.quantity}</@td>
                            <@td>${orderItem.cancelQuantity!0}</@td>
                            <@td>
                                <#if (backOrderedQuantity > 0)>
                                    <@alert type="info">${backOrderedQuantity}</@alert>
                                <#else>
                                    ${backOrderedQuantity}
                                </#if>
                            </@td>
                            <@td>${totalQuantityReceived}</@td>
                            <@td>${orderItem.quantity - (orderItem.cancelQuantity!0) - totalQuantityReceived}</@td>
                            <@td>
                                <#if fulfilledReservations?has_content>
                                    <#list fulfilledReservations?sort_by("orderId") as fulfilledReservation>
                                        ${fulfilledReservation.orderId}<br />
                                    </#list>
                                </#if>
                            </@td>
                            <#if (availableToReceive > 0)>
                                <@td>
                                    <input type="hidden" name="productId_o_${rowCount}" value="${(product.productId)!}"/>
                                    <input type="hidden" name="facilityId_o_${rowCount}" value="${facilityId}"/>
                                    <input type="hidden" name="shipmentId_o_${rowCount}" value="${shipmentId}"/>
                                    <input type="hidden" name="orderId_o_${rowCount}" value="${orderItem.orderId}"/>
                                    <input type="hidden" name="shipGroupSeqId_o_${rowCount}" value="${itemShipGroupSeqId!}"/>
                                    <input type="hidden" name="orderItemSeqId_o_${rowCount}" value="${orderItem.orderItemSeqId}"/>
                                    <input type="hidden" name="unitCost_o_${rowCount}" value="${orderItem.unitPrice!0}"/>
                                    <input type="hidden" name="currencyUomId_o_${rowCount}" value="${currencyUomId!""}"/>
                                    <input type="hidden" name="ownerPartyId_o_${rowCount}" value="${(facility.ownerPartyId)!}"/>
                                    <input type="hidden" name="datetimeReceived_o_${rowCount}" value="${now}"/>
                                    <input type="hidden" name="quantityRejected_o_${rowCount}" value="0"/>
                                    <#-- quantity field required by the chained issueOrderItemToShipment service -->
                                    <input type="hidden" name="quantity_o_${rowCount}" id="quantity_o_${rowCount}" value=""/>
                                    <#if itemQuantitiesToReceive?? && itemQuantitiesToReceive.get(orderItem.orderItemSeqId)??>
                                        <#assign quantityToReceive = itemQuantitiesToReceive.get(orderItem.orderItemSeqId)>
                                    <#else>
                                        <#assign quantityToReceive = 0>
                                    </#if>
                                    <#assign totalReadyToReceive = totalReadyToReceive + quantityToReceive/>
                                    <@field type="input" size="5" name="quantityAccepted_o_${rowCount}" id="quantityAccepted_o_${rowCount}" value=quantityToReceive/>
                                </@td>
                                <@td>
                                    <@field type="select" name="inventoryItemTypeId_o_${rowCount}">
                                      <#list inventoryItemTypes as inventoryItemType>
                                      <option value="${inventoryItemType.inventoryItemTypeId}"<#rt>
                                          <#if (facility.defaultInventoryItemTypeId?has_content) && (inventoryItemType.inventoryItemTypeId == facility.defaultInventoryItemTypeId)> selected="selected"</#if>><#t>
                                        ${inventoryItemType.get("description",locale)?default(inventoryItemType.inventoryItemTypeId)}</option><#lt>
                                      </#list>
                                    </@field>
                                </@td>
                                <@td align="right">
                                    <@field type="submit" submitType="link" href=makePageUrl("ReceiveInventoryAgainstPurchaseOrder?shipmentId=${shipmentId}&purchaseOrderId=${orderId}&productId=${product.productId}") class="${styles.link_run_local!} ${styles.action_clear!}" text=uiLabelMap.CommonClear />
                                </@td>
                                <@td align="right">
                                  <@field type="checkbox" name="_rowSubmit_o_${rowCount}" value="Y" onClick="javascript:checkToggle(this, 'selectAllForm');highlightRow(this,'orderItemData_tableRow_${rowCount}');" />
                                </@td>
                                <#assign rowCount = rowCount + 1>
                            </#if>
                        </@tr>
                    </#list>
                    <#if itemsAvailableToReceive>
                      <@tfoot>
                        <@tr>
                            <@td colspan="11" align="right">
                                <@field type="submit" submitType="link" href=makePageUrl("ReceiveInventoryAgainstPurchaseOrder?shipmentId=${shipmentId}&purchaseOrderId=${orderId}&clearAll=Y") class="${styles.link_run_local!} ${styles.action_clear!}" text=uiLabelMap.CommonClearAll />
                            </@td>
                            <@td align="right">
                                <@field type="submit" submitType="link" class="${styles.link_run_sys!} ${styles.action_receive!}" href="javascript:populateQuantities(${rowCount - 1});document.selectAllForm.submit();" text=uiLabelMap.ProductReceiveItem />
                            </@td>
                        </@tr>
                        <@tr>
                            <@td colspan="12" align="right">
                                <@field type="submit" submitType="link" class="${styles.link_run_sys!} ${styles.action_update!}" href=makePageUrl("completePurchaseOrder?orderId=${orderId}&facilityId=${facilityId}&shipmentId=${shipmentId}") text=uiLabelMap.OrderForceCompletePurchaseOrder />
                            </@td>
                        </@tr>
                      </@tfoot>
                    </#if>
                </@table>
                <input type="hidden" name="_rowCount" value="${rowCount}" />
              </@fields>
            </form>
            <@script>selectAll('selectAllForm');</@script>
        </#if>
        <#if itemsAvailableToReceive && (totalReadyToReceive < totalAvailableToReceive)>
            <@section title=uiLabelMap.ProductReceiveInventoryAddProductToReceive>
            <form name="addProductToReceive" method="post" action="<@pageUrl>ReceiveInventoryAgainstPurchaseOrder</@pageUrl>">
                <input type="hidden" name="shipmentId" value="${shipmentId}"/>
                <input type="hidden" name="purchaseOrderId" value="${orderId}"/>
                <@field type="generic" label="${rawLabel('ProductProductId')}/${rawLabel('ProductGoodIdentification')}">
                    <@field type="input" inline=true size="20" id="productId" name="productId" value=""/>
                        @
                    <@field type="input" inline=true  name="quantity" size="6" maxlength="6" value="1" tabindex="0"/>
                </@field>
                <@field type="submit" text=uiLabelMap.CommonAdd class="${styles.link_run_sys!} ${styles.action_add!}"/>

            </form>
            <@script>
                document.getElementById('productId').focus();
            </@script>
            </@section>
        </#if>
    </#if>
<#elseif parameters.shipmentId?has_content>
  <@commonMsg type="error">${uiLabelMap.ProductShipmentNotFoundId}: [${shipmentId!}]</@commonMsg>
</#if>

</@section>

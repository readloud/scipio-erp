<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->
<#include "component://order/webapp/ordermgr/common/common.ftl">
<#import "component://product/webapp/catalog/common/cataloglib.ftl" as cataloglib>

<#if orderHeader?has_content>

  <#-- price change rules -->
  <#assign allowPriceChange = false/>
  <#if (orderHeader.orderTypeId == 'PURCHASE_ORDER' || security.hasEntityPermission("ORDERMGR", "_SALES_PRICEMOD", request))>
      <#assign allowPriceChange = true/>
  </#if>

<@script>
    <#-- SCIPIO: without this, may have issues with b -->
    function submitUpdateItemInfoForm(action, orderItemSeqId, shipGroupSeqId) {
        document.updateItemInfo.action = action;
        document.updateItemInfo.orderItemSeqId.value = orderItemSeqId ? orderItemSeqId : '';
        document.updateItemInfo.shipGroupSeqId.value = shipGroupSeqId ? shipGroupSeqId : '';
        document.updateItemInfo.submit();
        return;
    }
</@script>

  <#macro menuContent menuArgs={}>
    <@menu args=menuArgs>
    <#if security.hasEntityPermission("ORDERMGR", "_UPDATE", request)>
      <#if orderHeader?has_content && orderHeader.statusId != "ORDER_CANCELLED" && orderHeader.statusId != "ORDER_COMPLETED">
        <@menuitem type="link" href="javascript:submitUpdateItemInfoForm('${escapeFullUrl(makePageUrl('updateOrderItems'), 'js')}');" text=uiLabelMap.OrderUpdateItems class="+${styles.action_run_sys!} ${styles.action_update!}"/>
        <@menuitem type="link" href="javascript:submitUpdateItemInfoForm('${escapeFullUrl(makePageUrl('cancelSelectedOrderItems'), 'js')}');" text=uiLabelMap.OrderCancelSelectedItems class="+${styles.action_run_sys!} ${styles.action_terminate!}" />
        <@menuitem type="link" href="javascript:submitUpdateItemInfoForm('${escapeFullUrl(makePageUrl('cancelOrderItem'), 'js')}');" text=uiLabelMap.OrderCancelAllItems class="+${styles.action_run_sys!} ${styles.action_terminate!}" />
        <@menuitem type="link" href=makePageUrl("orderview?${raw(paramString)}") text=uiLabelMap.OrderViewOrder class="+${styles.action_nav!} ${styles.action_view!}" />
      </#if>
    </#if>
    </@menu>
  </#macro>
  <@section title=uiLabelMap.OrderOrderItems menuContent=menuContent>

    <@alert type="warning">${uiLabelMap.CommonFunctionalityIssuesNotWorkCorrectlyInfo}</@alert>

    <#assign nestedFormMarkup></#assign><#-- SCIPIO: fix for bad nested html forms -->

        <#if !orderItemList?has_content>
            <@commonMsg type="error">${uiLabelMap.checkhelper_sales_order_lines_lookup_failed}</@commonMsg>
        </#if>
        
        <#-- SCIPIO: FIXME: this whole template is full of forms inside table elems = invalid HTML -->
        
        <@table type="data-complex" class="+order-items">

        <#if orderItemList?has_content>
            <form name="updateItemInfo" id="updateItemInfo" method="post" action="<@pageUrl>updateOrderItems</@pageUrl>">
            <input type="hidden" name="orderId" value="${orderId}"/>
            <input type="hidden" name="orderItemSeqId" value="" id="updateItemInfo_orderItemSeqId"/>
            <input type="hidden" name="shipGroupSeqId" value="" id="updateItemInfo_shipGroupSeqId"/>
            <#if (orderHeader.orderTypeId == 'PURCHASE_ORDER')>
              <#-- SCIPIO: FIXME? why is supplierPartyId the partyId? should have a supplierPartyId explicitly in groovy script to clarify this -->
              <input type="hidden" name="supplierPartyId" value="${partyId!}"/>
              <input type="hidden" name="orderTypeId" value="PURCHASE_ORDER"/>
            </#if>
              <@thead>
                <@tr class="header-row">
                    <@th width="30%">${uiLabelMap.ProductProduct}</@th>
                    <@th width="10%" class="${styles.text_right!}">${uiLabelMap.CommonStatus}</@th>
                    <@th width="10%">${uiLabelMap.OrderQuantity}</@th>
                    <@th width="10%" class="${styles.text_right!}">${uiLabelMap.OrderUnitList}</@th>
                    <@th width="10%" class="${styles.text_right!}">${uiLabelMap.OrderAdjustments}</@th>
                    <@th width="10%" class="${styles.text_right!}">${uiLabelMap.OrderSubTotal}</@th>
                    <@th width="20%">&nbsp;</@th>
                </@tr>
              </@thead>
                <#list orderItemList as orderItem>
                    <#if orderItem.productId??> <#-- a null product may come from a quote -->
                      <#assign orderItemContentWrapper = Static["org.ofbiz.order.order.OrderContentWrapper"].makeOrderContentWrapper(orderItem, request)>
                      
                      <@tr>
                          <#assign orderItemType = orderItem.getRelatedOne("OrderItemType", false)!>
                          <#assign productId = orderItem.productId!>
                          <#if productId?? && productId == "shoppingcart.CommentLine">
                              <@td colspan="8" valign="top">
                                  &gt;&gt; ${orderItem.itemDescription}
                              </@td>
                          <#else>
                              <@td valign="top">
                                  <div>
                                    <#if orderHeader.statusId == "ORDER_CANCELLED" || orderHeader.statusId == "ORDER_COMPLETED">
                                      <strong>
                                      <#if productId??>
                                        ${orderItem.productId!(uiLabelMap.CommonNA)} - ${orderItem.itemDescription!}
                                      <#elseif orderItemType??>
                                        ${orderItemType.description} - ${orderItem.itemDescription!}
                                      <#else>
                                        ${orderItem.itemDescription!}
                                      </#if>
                                      </strong>
                                    <#else>
                                      <#if productId??>
                                        <#assign orderItemName = orderItem.productId!(uiLabelMap.CommonNA)/>
                                      <#elseif orderItemType??>
                                        <#assign orderItemName = orderItemType.description/>
                                      </#if>
                                      ${uiLabelMap.ProductProduct}&nbsp;${orderItemName}
                                  
                                      <#if productId??>
                                          <#assign product = orderItem.getRelatedOne("Product", true)>
                                          <#if product.salesDiscontinuationDate?? && UtilDateTime.nowTimestamp().after(product.salesDiscontinuationDate)>
                                              <span class="alert">${uiLabelMap.OrderItemDiscontinued}: ${product.salesDiscontinuationDate}</span>
                                          </#if>
                                      </#if>
                                      <input type="text" size="20" name="idm_${orderItem.orderItemSeqId}" value="${orderItem.itemDescription!}"/>
                                      </#if>
                                  </div>
                                  
                                  <#-- SCIPIO: these are duplicates from far right column
                                      WARN: if uncomment this, please unhardcode the links - see getPropertyValue below
                                  <#if productId??>
                                  <div>
                                      <a href="<@serverUrl>/catalog/control/ViewProduct?productId=${productId}${raw(externalKeyParam)}</@serverUrl>" class="${styles.link_nav!}" target="_blank">${uiLabelMap.ProductCatalog}</a>
                                      <#- SCIPIO: Now points to shop ->
                                      <a href="<@serverUrl>/shop/control/product?product_id=${productId}</@serverUrl>" class="${styles.link_nav!}" target="_blank">${getLabel("Shop", "ShopUiLabels")}</a>
                                      <#if orderItemContentWrapper.get("IMAGE_URL", "url")?has_content>
                                      <a href="<@pageUrl>viewimage?orderId=${orderId}&amp;orderItemSeqId=${orderItem.orderItemSeqId}&amp;orderContentTypeId=IMAGE_URL</@pageUrl>" target="_orderImage" class="${styles.action_run_sys!} ${styles.action_view!}">${uiLabelMap.OrderViewImage}</a>
                                      </#if>
                                  </div>
                                  </#if>
                                  -->
                              </@td>

                              <#-- now show status details per line item -->
                              <#assign currentItemStatus = orderItem.getRelatedOne("StatusItem", false)>
                              <@td class="${styles.text_right!}">
                                  <@modal id="${productId}_st" label=currentItemStatus.get('description',locale)?default(currentItemStatus.statusId)>
                                   
                                            <#if ("ITEM_CREATED" == (currentItemStatus.statusId) && "ORDER_APPROVED" == (orderHeader.statusId)) && security.hasEntityPermission("ORDERMGR", "_UPDATE", request)>
                                                
                                                    <a href="javascript:document.OrderApproveOrderItem_${orderItem.orderItemSeqId!""}.submit()" class="${styles.link_run_sys!} ${styles.action_update!}">${uiLabelMap.OrderApproveItem}</a>
                                                  <#assign nestedFormMarkup>${nestedFormMarkup}
                                                    <form name="OrderApproveOrderItem_${orderItem.orderItemSeqId!""}" method="post" action="<@pageUrl>changeOrderItemStatus</@pageUrl>">
                                                        <input type="hidden" name="statusId" value="ITEM_APPROVED"/>
                                                        <input type="hidden" name="orderId" value="${orderId!}"/>
                                                        <input type="hidden" name="orderItemSeqId" value="${orderItem.orderItemSeqId!}"/>
                                                    </form>
                                                  </#assign>
                                                <br/>
                                            </#if>
                                  <#assign orderItemStatuses = orderReadHelper.getOrderItemStatuses(orderItem)>
                                  <#list orderItemStatuses as orderItemStatus>
                                                
                                    <#assign loopStatusItem = orderItemStatus.getRelatedOne("StatusItem", false)>
                                                <#if orderItemStatus.statusDatetime?has_content><@formattedDateTime date=orderItemStatus.statusDatetime />&nbsp;&nbsp;</#if>${loopStatusItem.get("description",locale)!(orderItemStatus.statusId)}
                                                <br/>
                                  </#list>
                                        
                                  <#assign returns = orderItem.getRelated("ReturnItem", null, null, false)!>
                                <#if returns?has_content>
                                  <#list returns as returnItem>
                                    <#assign returnHeader = returnItem.getRelatedOne("ReturnHeader", false)>
                                    <#if returnHeader.statusId != "RETURN_CANCELLED">
                                                <font color="red">${uiLabelMap.OrderReturned}</font>
                                                ${uiLabelMap.CommonNbr}<a href="<@pageUrl>returnMain?returnId=${returnItem.returnId}</@pageUrl>" class="${styles.link_nav_info_id!}">${returnItem.returnId}</a>
                                    </#if>
                                  </#list>
                                </#if>
                                   </@modal>
                              </@td>
                              <@td valign="top" class="${styles.text_right!}">
                                <#assign shippedQuantity = orderReadHelper.getItemShippedQuantity(orderItem)>
                                <#assign shipmentReceipts = delegator.findByAnd("ShipmentReceipt", {"orderId" : orderHeader.getString("orderId"), "orderItemSeqId" : orderItem.orderItemSeqId}, null, false)/>
                                <#assign totalReceived = 0.0>
                                <#if shipmentReceipts?? && shipmentReceipts?has_content>
                                  <#list shipmentReceipts as shipmentReceipt>
                                    <#if shipmentReceipt.quantityAccepted?? && shipmentReceipt.quantityAccepted?has_content>
                                      <#assign  quantityAccepted = shipmentReceipt.quantityAccepted>
                                      <#assign totalReceived = quantityAccepted + totalReceived>
                                    </#if>
                                    <#if shipmentReceipt.quantityRejected?? && shipmentReceipt.quantityRejected?has_content>
                                      <#assign  quantityRejected = shipmentReceipt.quantityRejected>
                                      <#assign totalReceived = quantityRejected + totalReceived>
                                    </#if>
                                  </#list>
                                </#if>
                                <#if orderHeader.orderTypeId == "PURCHASE_ORDER">
                                  <#assign remainingQuantity = (((orderItem.quantity!0) - (orderItem.cancelQuantity!0)) - totalReceived?double)>
                                <#else>
                                  <#assign remainingQuantity = (((orderItem.quantity!0) - (orderItem.cancelQuantity!0)) - shippedQuantity?double)>
                                </#if>
                                <#assign effTotalQuantity = (((orderItem.quantity!0) - (orderItem.cancelQuantity!0)))>
                                <@modal id="${productId}_q" label=effTotalQuantity?string.number><#-- SCIPIO: inappropriate, includes cancelled: (orderItem.quantity!0)?string.number -->
                                            <@table type="fields" class="+${styles.table_spacing_tiny_hint!}">
                                                <@tr valign="top">
                                                    
                                                    <@td><b>${uiLabelMap.OrderOrdered}</b></@td>
                                                    <@td>${(orderItem.quantity!0)?string.number}</@td>
                                                    <@td><b>${uiLabelMap.OrderShipRequest}</b></@td>
                                                    <@td>${orderReadHelper.getItemReservedQuantity(orderItem)}</@td>
                                                </@tr>
                                                <@tr valign="top">
                                                    <@td><b>${uiLabelMap.OrderCancelled}</b></@td>
                                                    <@td>${(orderItem.cancelQuantity!0)?string.number}</@td>
                                                </@tr>
                                                <@tr valign="top">
                                                    <@td><b>${uiLabelMap.OrderRemaining}</b></@td>
                                                    <@td>${remainingQuantity}</@td>
                                                    <#if orderHeader.orderTypeId == "PURCHASE_ORDER">
                                                        <@td><b>${uiLabelMap.OrderPlannedInReceive}</b></@td>
                                                        <@td>${totalReceived}</@td>
                                                    <#else>
                                                        <@td><b>${uiLabelMap.OrderQtyShipped}</b></@td>
                                                        <@td>${shippedQuantity}</@td>
                                                    </#if>
                                                </@tr>
                                                <@tr valign="top">
                                                    <@td><b>${uiLabelMap.OrderOutstanding}</b></@td>
                                                    <@td>
                                                        <#-- Make sure digital goods without shipments don't always remainn "outstanding": if item is completed, it must have no outstanding quantity.  -->
                                                        <#if (orderItem.statusId?has_content) && (orderItem.statusId == "ITEM_COMPLETED")>
                                                            0
                                                        <#elseif orderHeader.orderTypeId == "PURCHASE_ORDER">
                                                            ${((orderItem.quantity!0) - (orderItem.cancelQuantity!0)) - totalReceived?double}
                                                        <#elseif orderHeader.orderTypeId == "SALES_ORDER">
                                                            ${((orderItem.quantity!0) - (orderItem.cancelQuantity!0)) - shippedQuantity?double}
                                                        </#if>
                                                    </@td>
                                                </@tr>
                                                <@tr valign="top">
                                                    <@td><b>${uiLabelMap.OrderInvoiced}</b></@td>
                                                    <@td>${orderReadHelper.getOrderItemInvoicedQuantity(orderItem)}</@td>
                                                    <@td><b>${uiLabelMap.OrderReturned}</b></@td>
                                                    <@td>${returnQuantityMap.get(orderItem.orderItemSeqId)!0}</@td>
                                                </@tr>
                                            </@table>
                                        </@modal>
                                  
                              </@td>
                              <@td valign="top" class="${styles.text_right!}">
                                  <#-- check for permission to modify price -->
                                  <#if (allowPriceChange) && !(orderItem.statusId == "ITEM_CANCELLED" || orderItem.statusId == "ITEM_COMPLETED")>
                                      <input type="text" size="8" name="ipm_${orderItem.orderItemSeqId}" value="<@ofbizAmount amount=orderItem.unitPrice/>" class="${styles.text_right!}"/>
                                      <input type="hidden" name="opm_${orderItem.orderItemSeqId}" value="Y"/>
                                  <#else>
                                      <div><@ofbizCurrency amount=orderItem.unitPrice isoCode=currencyUomId/> / <@ofbizCurrency amount=orderItem.unitListPrice isoCode=currencyUomId/></div>
                                  </#if>
                              </@td>
                              <@td valign="top" class="${styles.text_right!}">
                                  <@ofbizCurrency amount=Static["org.ofbiz.order.order.OrderReadHelper"].getOrderItemAdjustmentsTotal(orderItem, orderAdjustments, true, false, false) isoCode=currencyUomId/>
                              </@td>
                              <@td valign="top" class="${styles.text_right!}">
                                <#if orderItem.statusId != "ITEM_CANCELLED">
                                  <@ofbizCurrency amount=Static["org.ofbiz.order.order.OrderReadHelper"].getOrderItemSubTotal(orderItem, orderAdjustments) isoCode=currencyUomId/>
                                <#else>
                                  <@ofbizCurrency amount=0.00 isoCode=currencyUomId/>
                                </#if>
                              </@td>
                              <@td>
                                    <@menu type="button">
                                        <#-- SCIPIO: order by ProductContent.sequenceNum -->
                                        <#assign downloadContents = delegator.findByAnd("OrderItemAndProductContentInfo", {"orderId" : orderId, "orderItemSeqId" : orderItem.orderItemSeqId, "productContentTypeId" : "DIGITAL_DOWNLOAD", "statusId" : "ITEM_COMPLETED"}, ["sequenceNum ASC"], true)/>
                                       
                                        <#if downloadContents?has_content>
                                          <#--
                                          <#list downloadContents as downloadContent>
                                            <@menuitem type="link" href=makeServerUrl("/content/control/ViewSimpleContent?contentId=${escapeVal(downloadContent.contentId, 'url')}") text=uiLabelMap.ContentDownload target="_blank" class="+${styles.action_run_sys!} ${styles.action_export!}" />
                                          </#list>
                                          -->
                                          <@modal id="${raw(orderId)}_${raw(orderItem.orderItemSeqId)}_downloads" label=uiLabelMap.ContentDownload linkClass="${styles.link_nav!} ${styles.action_export!}">
                                              <@heading relLevel=+1>${getLabel("EcommerceDownloadsAvailableTitle", "EcommerceUiLabels")}</@heading>
                                              <ol>
                                              <#list downloadContents as downloadContent>
                                                    <li><a href="<@serverUrl>/content/control/ViewSimpleContent?contentId=${downloadContent.contentId}${raw(externalKeyParam)}</@serverUrl>"<#rt/>
                                                        <#lt/> target="_blank" class="${styles.link_run_sys_inline!} ${styles.action_export!}">${downloadContent.contentName!downloadContent.contentId!}</a>
                                              </#list>
                                              </ol>
                                          </@modal>
                                        </#if>
                                        <@menuitem type="link" href=makeServerUrl("/catalog/control/ViewProduct?productId=${escapeVal(productId, 'url')}${raw(externalKeyParam)}") text=uiLabelMap.ProductCatalog target="_blank" class="+${styles.action_nav!} ${styles.action_update!}" />
                                        <@cataloglib.productShopPageUrlMenuItem productId=productId!/>
                                        <#if orderItemContentWrapper.get("IMAGE_URL", "url")?has_content>
                                            <@menuitem type="link" href=makePageUrl("viewimage?orderId=${escapeVal(orderId, 'url')}&orderItemSeqId=${escapeVal(orderItem.orderItemSeqId, 'url')}&orderContentTypeId=IMAGE_URL") text=uiLabelMap.OrderViewImage target="_orderImage" class="+${styles.action_run_sys!} ${styles.action_view!}" />
                                        </#if>
                                    </@menu>
                              </@td>
                          </#if>
                        </@tr>

                      <#-- now show adjustment details per line item -->
                      <#assign orderItemAdjustments = Static["org.ofbiz.order.order.OrderReadHelper"].getOrderItemAdjustmentList(orderItem, orderAdjustments)>
                      <#if orderItemAdjustments?? && orderItemAdjustments?has_content>
                          <#list orderItemAdjustments as orderItemAdjustment>
                              <#assign adjustmentType = orderItemAdjustment.getRelatedOne("OrderAdjustmentType", true)>
                              <@tr>
                                  <@td>
                                      ${uiLabelMap.OrderAdjustment}&nbsp;${adjustmentType.get("description",locale)}&nbsp;
                                      ${orderItemAdjustment.get("description",locale)!} (${orderItemAdjustment.comments!""})

                                  <#if orderItemAdjustment.orderAdjustmentTypeId == "SALES_TAX">
                                    <#if orderItemAdjustment.primaryGeoId?has_content>
                                      <#assign primaryGeo = orderItemAdjustment.getRelatedOne("PrimaryGeo", true)/>
                                      ${uiLabelMap.OrderJurisdiction}&nbsp;${primaryGeo.geoName} [${primaryGeo.abbreviation!}]
                                      <#if orderItemAdjustment.secondaryGeoId?has_content>
                                        <#assign secondaryGeo = orderItemAdjustment.getRelatedOne("SecondaryGeo", true)/>
                                        (${uiLabelMap.CommonIn}&nbsp;${secondaryGeo.geoName} [${secondaryGeo.abbreviation!}])
                                      </#if>
                                    </#if>
                                      <#if orderItemAdjustment.sourcePercentage??>Rate&nbsp;${orderItemAdjustment.sourcePercentage}</#if>
                                      <#if orderItemAdjustment.customerReferenceId?has_content>Customer Tax ID&nbsp;${orderItemAdjustment.customerReferenceId}</#if>
                                      <#if orderItemAdjustment.exemptAmount??>Exempt Amount&nbsp;${orderItemAdjustment.exemptAmount}</#if>
                                  </#if>
                                  </@td>
                                  <@td>&nbsp;</@td>
                                  <@td>&nbsp;</@td>
                                  <@td>&nbsp;</@td>
                                  <@td class="${styles.text_right!}">
                                      <@ofbizCurrency amount=Static["org.ofbiz.order.order.OrderReadHelper"].calcItemAdjustment(orderItemAdjustment, orderItem) isoCode=currencyUomId/>
                                  </@td>
                                  <@td colspan="2">&nbsp;</@td>
                              </@tr>
                          </#list>
                      </#if>

                      <#-- now show ship group info per line item -->
                      <#assign orderItemShipGroupAssocs = orderItem.getRelated("OrderItemShipGroupAssoc", null, null, false)!>
                      <#if orderItemShipGroupAssocs?has_content>
                          <#list orderItemShipGroupAssocs as shipGroupAssoc>
                                <#assign shipGroupQty = shipGroupAssoc.quantity - shipGroupAssoc.cancelQuantity?default(0)>
                              <#assign shipGroup = shipGroupAssoc.getRelatedOne("OrderItemShipGroup", false)>
                              <#assign shipGroupAddress = shipGroup.getRelatedOne("PostalAddress", false)!>
                              <#assign shippedQuantity = orderReadHelper.getItemShipGroupAssocShippedQuantity(orderItem, shipGroup.shipGroupSeqId)>
                              <#if shipGroupAssoc.quantity != shippedQuantity>
                                <#assign itemStatusOkay = (orderItem.statusId != "ITEM_CANCELLED" && orderItem.statusId != "ITEM_COMPLETED" && (shipGroupAssoc.cancelQuantity?default(0) < shipGroupAssoc.quantity?default(0)) && ("Y" != orderItem.isPromo!))>
                                <#assign itemSelectable = (security.hasEntityPermission("ORDERMGR", "_ADMIN", request) && itemStatusOkay) || (security.hasEntityPermission("ORDERMGR", "_UPDATE", request) && itemStatusOkay && orderHeader.statusId != "ORDER_SENT")>
                                <@tr>
                                    <@td class="align-text">
                                        ${uiLabelMap.OrderShipGroup}&nbsp;[${shipGroup.shipGroupSeqId}] ${shipGroupAddress.address1!(uiLabelMap.OrderNotShipped)}
                                    </@td>
                                    <@td></@td>
                                    <@td align="center">
                                      <#if itemStatusOkay>
                                        <input type="text" name="iqm_${shipGroupAssoc.orderItemSeqId}:${shipGroupAssoc.shipGroupSeqId}" size="6" value="${shipGroupQty?string.number}" class="${styles.text_right!}"/>
                                        <#if itemSelectable>
                                            <input type="hidden" name="selectedItem" value="${orderItem.orderItemSeqId}:${shipGroupAssoc.shipGroupSeqId}" />
                                        </#if>
                                      </#if>
                                    </@td>
                                    <@td colspan="3"></@td>
                                    <@td>
                                      <@menu type="button">
                                        <#if itemSelectable>
                                          <@menuitem type="link" href="javascript:submitUpdateItemInfoForm('${escapeFullUrl(makePageUrl('cancelOrderItem'), 'js')}', '${escapeVal(orderItem.orderItemSeqId!, 'js')}', '${escapeVal(shipGroup.shipGroupSeqId!, 'js')}');"
                                            text=(rawLabel('CommonCancel')+" "+rawLabel('CommonItem')) class="+${styles.action_run_sys!} ${styles.action_terminate!} ${styles.action_importance_high!}" /><#-- SCIPIO: removed: target="_orderImage" -->
                                        </#if>
                                      </@menu>
                                    </@td>
                                </@tr>
                              <#else>
                                <@tr>
                                    <@td class="align-text">
                                        ${uiLabelMap.OrderQtyShipped}&nbsp;[${shipGroup.shipGroupSeqId}] ${shipGroupAddress.address1!(uiLabelMap.OrderNotShipped)}
                                    </@td>
                                    <@td align="center">
                                        ${shippedQuantity!0}<input type="hidden" name="iqm_${shipGroupAssoc.orderItemSeqId}:${shipGroupAssoc.shipGroupSeqId}" size="6" value="${shippedQuantity?string.number}"/>
                                    </@td>
                                    <@td colspan="5">&nbsp;</@td>
                                </@tr>
                              </#if>
                          </#list>
                      </#if>
                      <#-- now update/cancel reason and comment field
                      <#if orderItem.statusId != "ITEM_CANCELLED" && orderItem.statusId != "ITEM_COMPLETED" && ("Y" != orderItem.isPromo!)>
                        <@tr>
                        <@td>${uiLabelMap.Return}</@td>
                        <@td colspan="2">${uiLabelMap.OrderReturnReason}
                                <select name="irm_${orderItem.orderItemSeqId}">
                                  <option value="">&nbsp;</option>
                                  <#list orderItemChangeReasons as reason>
                                    <option value="${reason.enumId}">${reason.get("description",locale)?default(reason.enumId)}</option>
                                  </#list>
                                </select>
                        </@td>
                        <@td colspan="2">
                            ${uiLabelMap.CommonComments}
                            <input type="text" name="icm_${orderItem.orderItemSeqId}" value="${orderItem.comments!}" size="30" maxlength="60"/>
                            <#if (orderHeader.orderTypeId == 'PURCHASE_ORDER')>
                              ${uiLabelMap.OrderEstimatedShipDate}
                              <@field type="datetime" name="isdm_${orderItem.orderItemSeqId}" value=(orderItem.estimatedShipDate!) size="25" maxlength="30" id="isdm_${orderItem.orderItemSeqId}" />
                              ${uiLabelMap.OrderOrderQuoteEstimatedDeliveryDate}
                              <@field type="datetime" name="iddm_${orderItem.orderItemSeqId}" value=(orderItem.estimatedDeliveryDate!) size="25" maxlength="30" id="iddm_${orderItem.orderItemSeqId}" />
                    </#if>
                        </@td>
                        <@td colspan="2"></@td>
                        </@tr>
                      </#if> -->
                      
                      
                    </#if>
                    
                </#list>
                <@tr>
                    <@td></@td>
                    <@td colspan="6"><hr/></@td>
                </@tr>
             
            </form>
        </#if>
        <#list orderHeaderAdjustments as orderHeaderAdjustment>
            <#assign adjustmentType = orderHeaderAdjustment.getRelatedOne("OrderAdjustmentType", false)>
            <#assign adjustmentAmount = Static["org.ofbiz.order.order.OrderReadHelper"].calcOrderAdjustment(orderHeaderAdjustment, orderSubTotal)>
            <#assign orderAdjustmentId = orderHeaderAdjustment.get("orderAdjustmentId")>
            <#assign productPromoCodeId = ''>
            <#if adjustmentType.get("orderAdjustmentTypeId") == "PROMOTION_ADJUSTMENT" && orderHeaderAdjustment.get("productPromoId")?has_content>
                <#assign productPromo = orderHeaderAdjustment.getRelatedOne("ProductPromo", false)>
                <#assign productPromoCodes = delegator.findByAnd("ProductPromoCode", {"productPromoId":productPromo.productPromoId}, null, false)>
                <#assign orderProductPromoCode = ''>
                <#list productPromoCodes as productPromoCode>
                    <#if !(orderProductPromoCode?has_content)>
                        <#assign orderProductPromoCode = delegator.findOne("OrderProductPromoCode", {"productPromoCodeId":productPromoCode.productPromoCodeId, "orderId":orderHeaderAdjustment.orderId}, false)!>
                    </#if>
                </#list>
                <#if orderProductPromoCode?has_content>
                    <#assign productPromoCodeId = orderProductPromoCode.get("productPromoCodeId")>
                </#if>
            </#if>
            <#if adjustmentAmount != 0>
                <form name="updateOrderAdjustmentForm${orderAdjustmentId}" method="post" action="<@pageUrl>updateOrderAdjustment</@pageUrl>">
                    <input type="hidden" name="orderAdjustmentId" value="${orderAdjustmentId!}"/>
                    <input type="hidden" name="orderId" value="${orderId!}"/>
                        <@tr>
                            <@td>
                                ${adjustmentType.get("description",locale)}&nbsp;${orderHeaderAdjustment.comments!}
                            </@td>
                            <@td colspan="4">
                                <#if (allowPriceChange)>
                                    <input type="text" name="description" value="${orderHeaderAdjustment.get("description")!}" size="30" maxlength="60"/>
                                <#else>
                                    ${orderHeaderAdjustment.get("description")!}
                                </#if>
                            </@td>
                            <@td><input type="text" name="amount" size="6" value="<@ofbizAmount amount=adjustmentAmount/>" class="${styles.text_right!}"/></@td>
                            <@td nowrap="nowrap">
                                <#if (allowPriceChange)>
                                    <input class="${styles.link_run_session!} ${styles.action_update!}" type="submit" value="${uiLabelMap.CommonUpdate}"/>
                                    <a href="javascript:document.deleteOrderAdjustment${orderAdjustmentId}.submit();" class="${styles.link_run_sys!} ${styles.action_remove!}">${uiLabelMap.CommonDelete}</a>
                                <#else>
                                    <@ofbizAmount amount=adjustmentAmount/>
                                </#if>
                            </@td>
                        </@tr>
                </form>
                <form name="deleteOrderAdjustment${orderAdjustmentId}" method="post" action="<@pageUrl>deleteOrderAdjustment</@pageUrl>">
                    <input type="hidden" name="orderAdjustmentId" value="${orderAdjustmentId!}"/>
                    <input type="hidden" name="orderId" value="${orderId!}"/>
                    <#if adjustmentType.get("orderAdjustmentTypeId") == "PROMOTION_ADJUSTMENT">
                        <input type="hidden" name="productPromoCodeId" value="${productPromoCodeId!}"/>
                    </#if>
                </form>
            </#if>
        </#list>

        <#-- add new adjustment -->
        <#if security.hasEntityPermission("ORDERMGR", "_UPDATE", request) && orderHeader.statusId != "ORDER_COMPLETED" && orderHeader.statusId != "ORDER_CANCELLED" && orderHeader.statusId != "ORDER_REJECTED">
            <form name="addAdjustmentForm" method="post" action="<@pageUrl>createOrderAdjustment</@pageUrl>">
                <input type="hidden" name="comments" value="Added manually by [${userLogin.userLoginId}]"/>
                <input type="hidden" name="isManual" value="Y"/>
                <input type="hidden" name="orderId" value="${orderId!}"/>
                    <@tr>
                        <@td>${uiLabelMap.OrderAdjustment}</@td>
                        <@td colspan="2" class="align-text">
                            <select name="orderAdjustmentTypeId">
                                <#list orderAdjustmentTypes as type>
                                <option value="${type.orderAdjustmentTypeId}">${type.get("description",locale)?default(type.orderAdjustmentTypeId)}</option>
                                </#list>
                            </select>
                        </@td>
                        <@td>
                            <select name="shipGroupSeqId">
                                <option value="_NA_"></option>
                                <#list shipGroups as shipGroup>
                                <option value="${shipGroup.shipGroupSeqId}">${uiLabelMap.OrderShipGroup} ${shipGroup.shipGroupSeqId}</option>
                                </#list>
                            </select>
                        </@td>
                        <@td><input type="text" name="description" value="" size="30" maxlength="60" class="${styles.text_right!}"/></@td>
                        <@td><input type="text" name="amount" size="6" value="<@ofbizAmount amount=0.00/>" class="${styles.text_right!}"/></@td>
                        <@td>
                            <input class="${styles.link_run_sys!} ${styles.action_add!}" type="submit" value="${uiLabelMap.CommonAdd}"/>
                        </@td>
                    </@tr>
            </form>
        </#if>

        <#-- subtotal -->
                <@tr>
                    <@td colspan="5"></@td>
                    <@td colspan="1"><hr /></@td>
                    <@td colspan="1"></@td>
                </@tr>
                <@tr>
                    <@td colspan="5" class="${styles.text_right!}">
                        ${uiLabelMap.OrderItemsSubTotal}
                    </@td>
                    <@td nowrap="nowrap" class="${styles.text_right!}">
                        <@ofbizCurrency amount=orderSubTotal isoCode=currencyUomId/>
                    </@td>
                    <@td>&nbsp;</@td>
                </@tr>
            <#-- other adjustments -->
                <@tr>
                    <@td colspan="5" class="${styles.text_right!}">
                        ${uiLabelMap.OrderTotalOtherOrderAdjustments}
                    </@td>
                    <@td nowrap="nowrap" class="${styles.text_right!}">
                        <@ofbizCurrency amount=otherAdjAmount isoCode=currencyUomId/>
                    </@td>
                    <@td>&nbsp;</@td>
                </@tr>
            <#-- shipping adjustments -->
                <@tr>
                    <@td colspan="5" class="${styles.text_right!}">
                            ${uiLabelMap.OrderTotalShippingAndHandling}
                    </@td>
                    <@td nowrap="nowrap" class="${styles.text_right!}">
                            <@ofbizCurrency amount=shippingAmount isoCode=currencyUomId/>
                    </@td>
                    <@td>&nbsp;</@td>
                </@tr>
            <#-- tax adjustments -->
                <@tr>
                    <@td colspan="5" class="${styles.text_right!}">
                             ${uiLabelMap.OrderTotalSalesTax}
                    </@td>
                    <@td nowrap="nowrap" class="${styles.text_right!}">
                            <@ofbizCurrency amount=taxAmount isoCode=currencyUomId/>
                    </@td>
                    <@td>&nbsp;</@td>
                </@tr>
            <#-- grand total -->
                <@tr>
                    <@td colspan="5"></@td>
                    <@td colspan="1"><hr /></@td>
                    <@td colspan="1"></@td>
                </@tr>
                <@tr>
                    <@td colspan="5" class="${styles.text_right!}">
                            <strong>${uiLabelMap.OrderTotalDue}</strong>
                    </@td>
                    <@td nowrap="nowrap" class="${styles.text_right!}">
                            <@ofbizCurrency amount=grandTotal isoCode=currencyUomId/>
                            </strong>
                    </@td>
                    <@td>&nbsp;</@td>
                </@tr>
        </@table>
        
        ${nestedFormMarkup}<#-- SCIPIO -->
        
  </@section>
    
</#if>

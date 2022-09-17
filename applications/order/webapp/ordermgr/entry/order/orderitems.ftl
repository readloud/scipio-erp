<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->

<#include "component://order/webapp/ordermgr/common/common.ftl">

<@section title=uiLabelMap.OrderOrderItems>
        <@menu type="button"> <#-- class="boxlink" -->
          <#if (maySelectItems!false) == true>
            <@menuitem type="link" href='javascript:document.addOrderToCartForm.add_all.value="true";document.addOrderToCartForm.submit()' text=uiLabelMap.OrderAddAllToCart class="+${styles.action_run_session!} ${styles.action_add!}" />
            <@menuitem type="link" href='javascript:document.addOrderToCartForm.add_all.value="false";document.addOrderToCartForm.submit()' text=uiLabelMap.OrderAddCheckedToCart class="+${styles.action_run_session!} ${styles.action_add!}"/>
          </#if>
        </@menu>
        
        <@table type="data-complex">
          <@thead>
          <@tr>
            <#assign prodColWidth = "65%">
            <#if (maySelectItems!false)>
              <#assign prodColWidth = "60%">
            </#if>
            <@th width=prodColWidth>${uiLabelMap.ProductProduct}</@th>
            <@th width="5%" class="${styles.text_right!}">${uiLabelMap.OrderQuantity}</@th>
            <@th width="10%" class="${styles.text_right!}">${uiLabelMap.CommonUnitPrice}</@th>
            <@th width="10%" class="${styles.text_right!}">${uiLabelMap.OrderAdjustments}</@th>
            <@th width="10%" class="${styles.text_right!}">${uiLabelMap.OrderSubTotal}</@th>
            <#if (maySelectItems!false)>
              <@th width="5%" class="${styles.text_right!}">&nbsp;</@th>
            </#if>
          </@tr>
          </@thead>

        <#-- SCIPIO: OrderItemAttributes and ProductConfigWrappers -->
        <#macro orderItemAttrInfo orderItem>
            <#local orderItemSeqId = raw(orderItem.orderItemSeqId!)>
            <#if orderItemProdCfgMap??>
              <#local cfgWrp = (orderItemProdCfgMap[orderItemSeqId])!false>
            <#else>
              <#local cfgWrp = false><#-- TODO -->
            </#if>
            <#if !cfgWrp?is_boolean>
              <#local selectedOptions = cfgWrp.getSelectedOptions()! />
              <#if selectedOptions?has_content>
                <ul class="order-item-attrib-list">
                  <#list selectedOptions as option>
                    <li>${option.getDescription()}</li>
                  </#list>
                </ul>
              </#if>
            </#if>
            <#if orderItemAttrMap??>
              <#local orderItemAttributes = orderItemAttrMap[orderItemSeqId]!/>
            <#else>
              <#local orderItemAttributes = orderItem.getRelated("OrderItemAttribute", null, null, false)!/>
            </#if>
            <#if orderItemAttributes?has_content>
                <ul class="order-item-attrib-list">
                  <#list orderItemAttributes as orderItemAttribute>
                    <li>${orderItemAttribute.attrName} : ${orderItemAttribute.attrValue}</li>
                  </#list>
                </ul>
            </#if>
        </#macro>

          <#list (orderItems!) as orderItem>
            <#assign itemType = orderItem.getRelatedOne("OrderItemType", false)!>
            <@tr>
              <#if orderItem.productId?? && orderItem.productId == "_?_">
                <@td colspan=(maySelectItems!false)?string("6", "5") valign="top">
                  <b><div> &gt;&gt; ${orderItem.itemDescription}</div></b>
                    <#-- SCIPIO: OrderItemAttributes and ProductConfigWrappers -->
                    <@orderItemAttrInfo orderItem=orderItem/>
                    <#-- SCIPIO: show application survey response QA list for this item -->
                    <@orderlib.orderItemSurvResList survResList=(orderlib.getOrderItemSurvResList(orderItem)!)/><#-- NOTE: could do this, but limits for nothing: interactive=false -->
                </@td>
              <#else>
                <@td valign="top">
                    <#if orderItem.productId??>
                      <a href="<@pageUrl>product?product_id=${orderItem.productId}</@pageUrl>">${orderItem.productId} - ${orderItem.itemDescription}</a>
                    <#else>
                      <b>${(itemType.description)!}</b> : ${orderItem.itemDescription!}
                    </#if>
                    <@orderItemAttrInfo orderItem=orderItem/>
                    <#-- SCIPIO: show application survey response QA list for this item -->
                    <@orderlib.orderItemSurvResList survResList=(orderlib.getOrderItemSurvResList(orderItem)!)/><#-- NOTE: could do this, but limits for nothing: interactive=false -->
                </@td>
                <#assign effTotalQuantity = (((orderItem.quantity!0) - (orderItem.cancelQuantity!0)))><#-- SCIPIO -->
                <@td class="${styles.text_right!}" valign="top">${effTotalQuantity?string.number}</@td><#-- SCIPIO: inappropriate, includes cancelled: orderItem.quantity?string.number -->
                <@td class="${styles.text_right!}" valign="top"><@ofbizCurrency amount=orderItem.unitPrice isoCode=currencyUomId/></@td>
                <@td class="${styles.text_right!}" valign="top"><@ofbizCurrency amount=localOrderReadHelper.getOrderItemAdjustmentsTotal(orderItem) isoCode=currencyUomId/></@td>
                <@td class="${styles.text_right!}" valign="top"><@ofbizCurrency amount=localOrderReadHelper.getOrderItemSubTotal(orderItem) isoCode=currencyUomId/></@td>
                <#if (maySelectItems!false)>
                  <@td>
                    <input name="item_id" value="${orderItem.orderItemSeqId}" type="checkbox" />
                  </@td>
                </#if>
              </#if>
            </@tr>
            <#-- show info from workeffort if it was a rental item -->
            <#if orderItem.orderItemTypeId?? && orderItem.orderItemTypeId == "RENTAL_ORDER_ITEM">
                <#assign WorkOrderItemFulfillments = orderItem.getRelated("WorkOrderItemFulfillment", null, null, false)!>
                <#if WorkOrderItemFulfillments?has_content>
                    <#list WorkOrderItemFulfillments as WorkOrderItemFulfillment>
                        <#assign workEffort = WorkOrderItemFulfillment.getRelatedOne("WorkEffort", true)!>
                          <@tr><@td>&nbsp;</@td><@td>&nbsp;</@td><@td colspan=(maySelectItems!false)?string("4", "3")>${uiLabelMap.CommonFrom}: ${workEffort.estimatedStartDate?string("yyyy-MM-dd")} ${uiLabelMap.CommonTo}: ${workEffort.estimatedCompletionDate?string("yyyy-MM-dd")} ${uiLabelMap.OrderNbrPersons}: ${workEffort.reservPersons}</@td></@tr>
                        <#break><#-- need only the first one -->
                    </#list>
                </#if>
            </#if>

            <#-- now show adjustment details per line item -->
            <#assign itemAdjustments = localOrderReadHelper.getOrderItemAdjustments(orderItem)>
            <#list itemAdjustments as orderItemAdjustment>
              <@tr>
                <@td>
                    <b><i>${uiLabelMap.OrderAdjustment}</i>:</b> <b>${localOrderReadHelper.getAdjustmentType(orderItemAdjustment)}</b>&nbsp;
                    <#if orderItemAdjustment.description?has_content>: ${orderItemAdjustment.get("description",locale)}</#if>

                    <#if orderItemAdjustment.orderAdjustmentTypeId == "SALES_TAX">
                      <#if orderItemAdjustment.primaryGeoId?has_content>
                        <#assign primaryGeo = orderItemAdjustment.getRelatedOne("PrimaryGeo", true)/>
                        <#if primaryGeo.geoName?has_content>
                            <b>${uiLabelMap.OrderJurisdiction}:</b> ${primaryGeo.geoName} [${primaryGeo.abbreviation!}]
                        </#if>
                        <#if orderItemAdjustment.secondaryGeoId?has_content>
                          <#assign secondaryGeo = orderItemAdjustment.getRelatedOne("SecondaryGeo", true)/>
                          (<b>in:</b> ${secondaryGeo.geoName} [${secondaryGeo.abbreviation!}])
                        </#if>
                      </#if>
                      <#if orderItemAdjustment.sourcePercentage??><b>${uiLabelMap.OrderRate}:</b> ${orderItemAdjustment.sourcePercentage}%</#if>
                      <#if orderItemAdjustment.customerReferenceId?has_content><b>${uiLabelMap.OrderCustomerTaxId}:</b> ${orderItemAdjustment.customerReferenceId}</#if>
                      <#if orderItemAdjustment.exemptAmount??><b>${uiLabelMap.OrderExemptAmount}:</b> ${orderItemAdjustment.exemptAmount}</#if>
                    </#if>
                  </@td>
                <@td>&nbsp;</@td>
                <@td>&nbsp;</@td>
                <@td class="${styles.text_right!}"><@ofbizCurrency amount=localOrderReadHelper.getOrderItemAdjustmentTotal(orderItem, orderItemAdjustment) isoCode=currencyUomId/></@td>
                <@td>&nbsp;</@td>
                <#if (maySelectItems!false)>
                  <@td>&nbsp;</@td>
                </#if>
              </@tr>
            </#list>
           </#list>
           <#if !orderItems?has_content>
             <@tr><@td colspan=(maySelectItems!false)?string("6", "5")><span class="${styles.text_color_alert!}">${uiLabelMap.checkhelpertotalsdonotmatchordertotal}</span></@td></@tr>
           </#if>

          <@tr>
            <@td colspan="4"><b>${uiLabelMap.OrderSubTotal}</b></@td>
            <@td class="${styles.text_right!}">&nbsp;<#if orderSubTotal??><@ofbizCurrency amount=orderSubTotal isoCode=currencyUomId/></#if></@td>
            <#if (maySelectItems!false)>
               <@td>&nbsp;</@td>
            </#if>
          </@tr>
          <#list headerAdjustmentsToShow! as orderHeaderAdjustment>
            <@tr>
              <@td colspan="4"><b>${localOrderReadHelper.getAdjustmentType(orderHeaderAdjustment)}</b></@td>
              <@td class="${styles.text_right!}"><@ofbizCurrency amount=localOrderReadHelper.getOrderAdjustmentTotal(orderHeaderAdjustment) isoCode=currencyUomId/></@td>
              <#if (maySelectItems!false)>
                <@td>&nbsp;</@td>
              </#if>
            </@tr>
          </#list>
          <@tr><@td colspan=2></@td><@td colspan=(maySelectItems!false)?string("4", "3")><hr /></@td></@tr>
          
          <@tr>
            <@td colspan="4"><b>${uiLabelMap.FacilityShippingAndHandling}</b></@td>
            <@td class="${styles.text_right!}"><#if orderShippingTotal??><@ofbizCurrency amount=orderShippingTotal isoCode=currencyUomId/></#if></@td>
            <#if (maySelectItems!false)>
              <@td>&nbsp;</@td>
            </#if>
          </@tr>
          <#if orderTaxTotal?has_content && (orderTaxTotal > 0)>
              <@tr>
                <@td colspan="4"><b>${uiLabelMap.OrderSalesTax}</b></@td>
                <@td class="${styles.text_right!}"><#if orderTaxTotal??><@ofbizCurrency amount=orderTaxTotal isoCode=currencyUomId/></#if></@td>
                <#if (maySelectItems!false)>
                  <@td>&nbsp;</@td>
                </#if>
              </@tr>
          </#if>
          <#if orderVATTaxTotal?has_content && (orderVATTaxTotal > 0)>
            <@tr><@td colspan=2></@td><@td colspan="8"><hr /></@td></@tr>
            <@tr>
                <@td colspan="4"><b>${uiLabelMap.AccountingSalesTaxIncluded}</b></@td>
                <@td class="${styles.text_right!}"><#if orderVATTaxTotal??><@ofbizCurrency amount=orderVATTaxTotal isoCode=currencyUomId/></#if></@td>
                <#if (maySelectItems!false)>
                  <@td>&nbsp;</@td>
                </#if>
              </@tr>
          </#if>

          <@tr><@td colspan=2></@td><@td colspan="8"><hr /></@td></@tr>
          <@tr>
            <@td colspan="4"><b>${uiLabelMap.OrderGrandTotal}</b></@td>
            <@td class="${styles.text_right!}"><#if orderGrandTotal??><@ofbizCurrency amount=orderGrandTotal isoCode=currencyUomId/></#if></@td>
            <#if (maySelectItems!false)>
              <@td>&nbsp;</@td>
            </#if>
          </@tr>
        </@table>
</@section>

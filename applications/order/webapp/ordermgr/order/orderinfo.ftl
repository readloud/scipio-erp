<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->

<@section title=uiLabelMap.CommonOverview>
  <#if orderHeader.externalId?has_content>
    <#assign externalOrder = "(" + orderHeader.externalId + ")"/>
  </#if>
  <#assign orderType = orderHeader.getRelatedOne("OrderType", false)/>
    
  <@table type="fields">
  
  <#if orderHeader.orderName?has_content>
    <@tr>
      <@td class="${styles.grid_large!}2">${uiLabelMap.OrderOrderName}
      </@td>
      <@td colspan="3">${orderHeader.orderName}</@td>
    </@tr>
  </#if>
  
    <#-- SCIPIO: Order type -->
    <@tr>
      <@td class="${styles.grid_large!}2">${uiLabelMap.OrderOrderType}
      </@td>
      <@td colspan="3">${(orderHeader.getRelatedOne("OrderType").get("description", locale))!}</@td>
    </@tr>

    <@tr>
      <@td scope="row" class="${styles.grid_large!}3">${uiLabelMap.CommonStatus}</@td>
      <@td colspan="3">
         
        <@modal id="${orderId}_info" label=currentStatus.get('description',locale)>
        <#if orderHeaderStatuses?has_content>
          <ul class="no-bullet">
            <#list orderHeaderStatuses as orderHeaderStatus>
              <#assign loopStatusItem = orderHeaderStatus.getRelatedOne("StatusItem", false)>
              <#--<#assign userlogin = orderHeaderStatus.getRelatedOne("UserLogin", false)>-->
            
              <li>${loopStatusItem.get("description",locale)} <#if orderHeaderStatus.statusDatetime?has_content>- <@formattedDateTime date=orderHeaderStatus.statusDatetime defaultVal="0000-00-00 00:00:00"/></#if>
                      &nbsp;
              ${uiLabelMap.CommonBy} - <#--${Static["org.ofbiz.party.party.PartyHelper"].getPartyName(delegator, userlogin.getString("partyId"), true)}--> [${orderHeaderStatus.statusUserLogin}]</li>
            </#list>
          </ul>
        </#if>
        </@modal>
      </@td>
    </@tr>

    <@tr>
      <@td scope="row" class="${styles.grid_large!}3">${uiLabelMap.OrderDateOrdered}</@td>
      <@td colspan="3">
          <#if orderHeader.orderDate?has_content><@formattedDateTime date=orderHeader.orderDate /></#if>
      </@td>
    </@tr>

    <#-- This is probably not required anymore - the currency is apparent when looking at the order
    <@tr>
      <@td scope="row" class="${styles.grid_large!}3">${uiLabelMap.CommonCurrency}</@td>
      <@td colspan="3">${orderHeader.currencyUom?default("???")}</@td>
    </@tr>
    -->
  <#if orderHeader.internalCode?has_content>
    <@tr>
      <@td scope="row" class="${styles.grid_large!}3">${uiLabelMap.OrderInternalCode}</@td>
      <@td colspan="3">
      ${orderHeader.internalCode}</@td>
    </@tr>
  </#if>

  <#if productStore?has_content>
    <@tr>
      <@td scope="row" class="${styles.grid_large!}3">${uiLabelMap.OrderProductStore}</@td>
      <@td colspan="3">
        <#-- SCIPIO: FIXME: no ViewProductStore available
        <a href="<@serverUrl>/catalog/control/ViewProductStore?productStoreId=${productStore.productStoreId}${raw(externalKeyParam)}</@serverUrl>" target="catalogmgr">${productStore.storeName!(productStore.productStoreId!)}</a>--> 
        ${productStore.storeName!(productStore.productStoreId!)}
        <#if orderHeader.salesChannelEnumId?has_content>
          <#assign channel = orderHeader.getRelatedOne("SalesChannelEnumeration", false)>
          <#if channel.get("description",locale)?has_content && channel.get("enumId")!= "UNKNWN_SALES_CHANNEL">
            (${(channel.get("description",locale))!}<#if orderHeader.webSiteId?has_content> - ${orderHeader.webSiteId}</#if>)
          </#if>
        </#if>
      </@td>
    </@tr>
  </#if>

    <#if orderHeader.originFacilityId?has_content>
        <@tr>
          <@td scope="row" class="${styles.grid_large!}3">${uiLabelMap.OrderOriginFacility}</@td>
          <@td colspan="3">
            <a href="<@serverUrl>/facility/control/EditFacility?facilityId=${orderHeader.originFacilityId}${raw(externalKeyParam)}</@serverUrl>" target="facilitymgr">${orderHeader.originFacilityId}</a>
          </@td>
        </@tr>
    </#if>
  
  <#--
    <@tr>
      <@td scope="row" class="${styles.grid_large!}3">${uiLabelMap.CommonCreatedBy}</@td>
      <@td colspan="3">
      <#if orderHeader.createdBy?has_content>
        <a href="<@serverUrl>/partymgr/control/viewprofile?userlogin_id=${orderHeader.createdBy}${raw(externalKeyParam)}</@serverUrl>" target="partymgr" class="">${orderHeader.createdBy}</a>
      <#else>
        ${uiLabelMap.CommonNotSet}
      </#if>
      </@td>
    </@tr>
    -->
  <#if (orderItem.cancelBackOrderDate)??>
    <@tr>
      <@td scope="row" class="${styles.grid_large!}3">${uiLabelMap.FormFieldTitle_cancelBackOrderDate}</@td>
      <@td colspan="3">
        <#if orderItem.cancelBackOrderDate?has_content><@formattedDateTime date=orderItem.cancelBackOrderDate /></#if>
      </@td>
    </@tr>
  </#if>

  <#if distributorId?? && userLogin??><#-- SCIPIO: 2019-02-27: Don't run getPartyNameForDate if userLogin missing (see OrderServices.sendOrderNotificationScreen warning) -->
    <@tr>
      <@td scope="row" class="${styles.grid_large!}3">${uiLabelMap.OrderDistributor}</@td>
      <@td colspan="3">
         <#assign distPartyNameResult = runService("getPartyNameForDate", {"partyId":distributorId, "compareDate":orderHeader.orderDate, "userLogin":userLogin})/>
         ${distPartyNameResult.fullName?default("[${uiLabelMap.OrderPartyNameNotFound}]")}
      </@td>
    </@tr>
  </#if>

  <#if affiliateId?? && userLogin??><#-- SCIPIO: 2019-02-27: Don't run getPartyNameForDate if userLogin missing (see OrderServices.sendOrderNotificationScreen warning) -->
    <@tr>
      <@td>${uiLabelMap.OrderAffiliate}</@td>
      <@td colspan="3">
        <#assign affPartyNameResult = runService("getPartyNameForDate", {"partyId":affiliateId, "compareDate":orderHeader.orderDate, "userLogin":userLogin})/>
        ${affPartyNameResult.fullName?default("[${uiLabelMap.OrderPartyNameNotFound}]")}
      </@td>
    </@tr>
  </#if>
 
  <#if orderContentWrapper.get("IMAGE_URL", "url")!?trim?has_content>
    <@tr>
      <@td>${uiLabelMap.OrderImage}</@td>
      <@td colspan="3">
        <a href="<@pageUrl>viewimage?orderId=${orderId}&amp;orderContentTypeId=IMAGE_URL</@pageUrl>" target="_orderImage" class="${styles.link_run_sys!} ${styles.action_view!}">${uiLabelMap.OrderViewImage}</a>
      </@td>
    </@tr>
  </#if>

  <#if "SALES_ORDER" == orderHeader.orderTypeId>
    <@tr>
      <@td>${uiLabelMap.FormFieldTitle_priority}</@td>
      <@td colspan="3">
         
         
         <#switch orderHeader.priority!>
            <#case "1"><#assign priorityLabel = uiLabelMap.CommonHigh><#break>
            <#case "2"><#assign priorityLabel = uiLabelMap.CommonNormal><#break>
            <#case "3"><#assign priorityLabel = uiLabelMap.CommonLow><#break>
            <#default><#assign priorityLabel = uiLabelMap.CommonNormal>
         </#switch>
         <@modal id="${orderId}_priority" label=priorityLabel>
             <form name="setOrderReservationPriority" method="post" action="<@pageUrl>setOrderReservationPriority</@pageUrl>">
             <input type="hidden" name="orderId" value="${orderId}"/>
            <@row>
                <@cell columns=6>
                    <select name="priority">
                      <option value="1"<#if ((orderHeader.priority)!) == "1"> selected="selected"</#if>>${uiLabelMap.CommonHigh}</option>
                      <option value="2"<#if ((orderHeader.priority)!) == "2"> selected="selected"<#elseif !(orderHeader.priority)?has_content> selected="selected"</#if>>${uiLabelMap.CommonNormal}</option>
                      <option value="3"<#if ((orderHeader.priority)!) == "3"> selected="selected"</#if>>${uiLabelMap.CommonLow}</option>
                    </select>
                </@cell>
                <@cell columns=6>
                    <input type="submit" class="${styles.link_run_sys!} ${styles.action_update!}" value="${uiLabelMap.FormFieldTitle_reserveInventory}"/>
                </@cell>
            </@row>
            </form>
        </@modal>
      </@td>
    </@tr>
  </#if>
    <@tr>
      <@td>${uiLabelMap.AccountingInvoicePerShipment}</@td>
      <@td colspan="3">
         
         <#switch orderHeader.invoicePerShipment!>
            <#case "Y"><#assign invoicePerShipmentLabel = uiLabelMap.CommonYes><#break>
            <#case "N"><#assign invoicePerShipmentLabel = uiLabelMap.CommonNo><#break>
            <#default><#assign invoicePerShipmentLabel = uiLabelMap.CommonYes>
         </#switch>
         <@modal id="${orderId}_invoicePerShipment" label=invoicePerShipmentLabel>
             <form name="setInvoicePerShipment" method="post" action="<@pageUrl>setInvoicePerShipment</@pageUrl>">
                <input type="hidden" name="orderId" value="${orderId}"/>
                <@row>
                    <@cell columns=6>
                        <select name="invoicePerShipment">
                          <option value="Y" <#if ((orderHeader.invoicePerShipment)!) == "Y">selected="selected" </#if>>${uiLabelMap.CommonYes}</option>
                          <option value="N" <#if ((orderHeader.invoicePerShipment)!) == "N">selected="selected" </#if>>${uiLabelMap.CommonNo}</option>
                        </select>
                    </@cell>
                    <@cell columns=6>
                        <input type="submit" class="${styles.link_run_sys!} ${styles.action_update!}" value="${uiLabelMap.CommonUpdate}"/>
                    </@cell>
                </@row>
            </form>
         </@modal>
      </@td>
    </@tr>
  <#-- The usefulness of this seems a bit limited atm
  <#if orderHeader.isViewed?has_content && orderHeader.isViewed == "Y">
    <@tr>
      <@td>${uiLabelMap.OrderViewed}</@td>
      <@td colspan="3">
        ${uiLabelMap.CommonYes}
      </@td>
    </@tr>
  <#else>
    <@tr id="isViewed">
      <@td>${uiLabelMap.OrderMarkViewed}</@td>
      <@td colspan="3">
        <form id="orderViewed" action="">
          <input type="checkbox" name="checkViewed" onclick="javascript:markOrderViewed();"/>
          <input type="hidden" name="orderId" value="${orderId!}"/>
          <input type="hidden" name="isViewed" value="Y"/>
        </form>
      </@td>
    </@tr>
    <@tr id="viewed" style="display: none;">
      <@td>${uiLabelMap.OrderViewed}</@td>
      <@td colspan="3">
        ${uiLabelMap.CommonYes}
      </@td>
    </@tr>
  </#if>
  -->
  </@table>
</@section>


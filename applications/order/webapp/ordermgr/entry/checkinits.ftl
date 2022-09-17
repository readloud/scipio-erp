<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->

<#assign shoppingCartOrderType = "">
<#assign shoppingCartProductStore = "NA">
<#assign shoppingCartChannelType = "">
<#if shoppingCart??>
  <#assign shoppingCartOrderType = shoppingCart.getOrderType()>
  <#assign shoppingCartProductStore = shoppingCart.getProductStoreId()!"NA">
  <#assign shoppingCartChannelType = shoppingCart.getChannelType()!"">
<#else>
<#-- allow the order type to be set in parameter, so only the appropriate section (Sales or Purchase Order) shows up -->
  <#if parameters.orderTypeId?has_content>
    <#assign shoppingCartOrderType = parameters.orderTypeId>
  </#if>
</#if>
<#-- Sales Order Entry -->
<#if security.hasEntityPermission("ORDERMGR", "_CREATE", request)>
  <#if shoppingCartOrderType != "PURCHASE_ORDER">
    <#assign sectionTitle>${rawLabel('OrderSalesOrder')}<#if shoppingCart??> ${rawLabel('OrderInProgress')}</#if></#assign>
    <#macro menuContent menuArgs={}>
      <@menu args=menuArgs>
        <#-- SCIPIO: 2018-11: now redundant, just click magnifying glass on fields
        <@menuitem type="link" href=makeServerUrl("/partymgr/control/findparty?${raw(externalKeyParam)}") text=uiLabelMap.PartyFindParty class="+${styles.action_nav!} ${styles.action_find!}" />-->
        <@menuitem type="link" href="javascript:document.salesentryform.submit();" text=uiLabelMap.CommonContinue class="+${styles.action_run_session!} ${styles.action_continue!}"/>
      </@menu>
    </#macro>
    <@section class="${styles.grid_large!}9" title=sectionTitle menuContent=menuContent>
      <form method="post" name="salesentryform" action="<@pageUrl>initorderentry</@pageUrl>">
      <input type="hidden" name="originOrderId" value="${parameters.originOrderId!}"/>
      <input type="hidden" name="finalizeMode" value="type"/>
      <input type="hidden" name="orderMode" value="SALES_ORDER"/>
        <@field type="select" label=uiLabelMap.ProductProductStore name="productStoreId" >
            <#assign currentStore = shoppingCartProductStore>
            <#if defaultProductStore?has_content>
               <option value="${defaultProductStore.productStoreId}">${defaultProductStore.storeName!(defaultProductStore.productStoreId!)}</option>
               <option value="${defaultProductStore.productStoreId}">----</option>
            </#if>
            <#list productStores as productStore>
              <option value="${productStore.productStoreId}"<#if productStore.productStoreId == currentStore> selected="selected"</#if>>${productStore.storeName!(productStore.productStoreId!)}</option>
            </#list>
            <#--<#if sessionAttributes.orderMode??>${uiLabelMap.OrderCannotBeChanged}</#if>-->
        </@field>
        <@field type="select" label=uiLabelMap.OrderSalesChannel name="salesChannelEnumId">
            <#assign currentChannel = shoppingCartChannelType>
            <#if defaultSalesChannel?has_content>
               <option value="${defaultSalesChannel.enumId}">${defaultSalesChannel.description!}</option>
               <option value="${defaultSalesChannel.enumId}"> ---- </option>
            </#if>
            <option value="">${uiLabelMap.OrderNoChannel}</option>
            <#list salesChannels as salesChannel>
              <option value="${salesChannel.enumId}"<#if (salesChannel.enumId == currentChannel)> selected="selected"</#if>>${salesChannel.get("description",locale)}</option>
            </#list>
        </@field>
        <#if partyId??>
          <#assign thisPartyId = partyId>
        <#else>
          <#assign thisPartyId = requestParameters.partyId!>
        </#if>
        <@field type="lookup" label=uiLabelMap.CommonUserLoginId id="userLoginId_sales" value=parameters.userLogin.userLoginId formName="salesentryform" name="userLoginId" fieldFormName="LookupUserLoginAndPartyDetails"/>
        <@field type="lookup" label=uiLabelMap.OrderCustomer id="partyId" value=(thisPartyId!) formName="salesentryform" name="partyId" fieldFormName="LookupCustomerName"/>
      </form>
    </@section>
  </#if>
</#if>

<#-- Purchase Order Entry -->
<#if security.hasEntityPermission("ORDERMGR", "_PURCHASE_CREATE", request)>
  <#if shoppingCartOrderType != "SALES_ORDER">
    <#assign sectionTitle>${rawLabel('OrderPurchaseOrder')}<#if shoppingCart??> ${rawLabel('OrderInProgress')}</#if></#assign>
    <#macro menuContent menuArgs={}>
      <@menu args=menuArgs>
        <#-- SCIPIO: 2018-11: now redundant, just click magnifying glass on fields
        <@menuitem type="link" href=makeServerUrl("/partymgr/control/findparty?${raw(externalKeyParam)}") text=uiLabelMap.PartyFindParty class="+${styles.action_nav!} ${styles.action_find!}" />-->
        <@menuitem type="link" href="javascript:document.poentryform.submit();" text=uiLabelMap.CommonContinue class="+${styles.action_run_session!} ${styles.action_continue!}" />
      </@menu>
    </#macro>
    <@section title=sectionTitle class="${styles.grid_large!}9" menuContent=menuContent>
      <form method="post" name="poentryform" action="<@pageUrl>initorderentry</@pageUrl>">
      <input type="hidden" name="finalizeMode" value="type"/>
      <input type="hidden" name="orderMode" value="PURCHASE_ORDER"/>
        <#if partyId??>
          <#assign thisPartyId = partyId>
        <#else>
          <#assign thisPartyId = requestParameters.partyId!>
        </#if>
        <@field type="select" label=uiLabelMap.OrderOrderEntryInternalOrganization name="billToCustomerPartyId">
            <#list organizations as organization>
              <#assign organizationName = Static["org.ofbiz.party.party.PartyHelper"].getPartyName(organization, true)/>
                <#if (organizationName.length() != 0)>
                  <option value="${organization.partyId}">${organization.partyId} - ${organizationName}</option>
                </#if>
            </#list>
        </@field>
        <@field type="lookup" label=uiLabelMap.CommonUserLoginId id="userLoginId_purchase" value=parameters.userLogin.userLoginId formName="poentryform" name="userLoginId" fieldFormName="LookupUserLoginAndPartyDetails"/>
        <@field type="select" label=uiLabelMap.PartySupplier name="supplierPartyId">
            <option value=""></option><#-- SCIPIO: confusing/redundant: ${uiLabelMap.OrderSelectSupplier} -->
            <#list suppliers as supplier>
              <option value="${supplier.partyId}"<#if supplier.partyId == thisPartyId> selected="selected"</#if>>[${supplier.partyId}] - ${Static["org.ofbiz.party.party.PartyHelper"].getPartyName(supplier, true)}</option>
            </#list>
        </@field>
      </form>
    </@section>
  </#if>
</#if>

<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->

<#if commissionReportList?has_content>
  <#macro menuContent menuArgs={}>
    <@menu args=menuArgs>
      <@menuitem type="link" href=makePageUrl("CommissionReport.pdf?isSearch=Y&productId=${parameters.productId!}&partyId=${parameters.partyId!}&fromDate=${parameters.fromDate!}&thruDate=${parameters.thruDate!}") text=uiLabelMap.AccountingInvoicePDF target="_BLANK" class="+${styles.action_run_sys!} ${styles.action_export!}" />
    </@menu>
  </#macro>
  <@section menuContent=menuContent>
  <@table type="data-list" autoAltRows=true>
    <@thead>
    <@tr class="header-row-2">
      <@th>${uiLabelMap.AccountingLicensedProduct}</@th>
      <@th>${uiLabelMap.AccountingQuantity}</@th>
      <@th>${uiLabelMap.AccountingNumberOfOrders} / ${uiLabelMap.AccountingSalesInvoices}</@th>
      <@th>${uiLabelMap.AccountingCommissionAmount}</@th>
      <@th>${uiLabelMap.AccountingNetSale}</@th>
      <@th>${uiLabelMap.AccountingSalesAgents} / ${uiLabelMap.AccountingTermAmount}</@th>
    </@tr>
    </@thead>
    <#list commissionReportList as commissionReport>
      <@tr valign="middle">
        <@td><a href="<@serverUrl>/catalog/control/ViewProduct?productId=${commissionReport.productId!}</@serverUrl>">${commissionReport.productName!}</a></@td>
        <@td>${commissionReport.quantity!}</@td>
        <@td>
          ${commissionReport.numberOfOrders!} /
          <#if commissionReport.salesInvoiceIds?has_content>
            <#list commissionReport.salesInvoiceIds as salesInvoiceId>
              [<a href="<@serverUrl>/ap/control/invoiceOverview?invoiceId=${salesInvoiceId!}</@serverUrl>">${salesInvoiceId!}</a>]
            </#list>
          </#if>
        </@td>
        <@td><@ofbizCurrency amount=(commissionReport.commissionAmount!)/></@td>
        <@td><@ofbizCurrency amount=(commissionReport.netSale!)/></@td>
        <@td>
          <#if commissionReport.salesAgentAndTermAmtMap?has_content>
            <#list commissionReport.salesAgentAndTermAmtMap.values() as partyIdAndTermAmountMap>
              <#assign partyName = (delegator.findOne("PartyNameView", {"partyId" : partyIdAndTermAmountMap.partyId}, true))!>
              <p>[${(partyName.firstName)!} ${(partyName.lastName)!} ${(partyName.groupName)!}(<a href="<@serverUrl>/partymgr/control/viewprofile?partyId=${partyIdAndTermAmountMap.partyId!}</@serverUrl>">${partyIdAndTermAmountMap.partyId!}</a>)]
                / <@ofbizCurrency amount = ((partyIdAndTermAmountMap.termAmount)!)/>
              </p>
            </#list>
          </#if>
        </@td>
      </@tr>
    </#list>
  </@table>
  
    <ul>
      <li></li>
      <li><@heading>${uiLabelMap.CommonSummary} :</@heading></li>
      <li></li>
      <li>${uiLabelMap.ManufacturingTotalQuantity} : ${totalQuantity!}</li>
      <li>${uiLabelMap.AccountingTotalCommissionAmount} : <@ofbizCurrency amount=(totalCommissionAmount!)/></li>
      <li>${uiLabelMap.AccountingTotalNetSales} : <@ofbizCurrency amount=(totalNetSales!)/></li>
      <li>${uiLabelMap.AccountingTotalNumberOfOrders} : ${totalNumberOfOrders!}</li>
    </ul>
  </@section>
<#else>
  <@section>
    <@commonMsg type="result-norecord"/>
  </@section>
</#if>

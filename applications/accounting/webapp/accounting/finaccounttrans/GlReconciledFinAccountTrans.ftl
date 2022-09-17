<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->

<form id="glReconciledFinAccountTrans" name="glReconciledFinAccountTransForm" method="post" action="<@pageUrl>callReconcileFinAccountTrans?clearAll=Y</@pageUrl>">
    <input name="_useRowSubmit" type="hidden" value="Y"/>
    <input name="finAccountId" type="hidden" value="${finAccountId}"/>
    <input name="glReconciliationId" type="hidden" value="${glReconciliationId}"/>
  
    <#macro menuContent menuArgs={}>
        <@menu args=menuArgs>
            <@menuitem type="link" href=makePageUrl("EditFinAccountReconciliations?finAccountId=${finAccountId}&glReconciliationId=${glReconciliationId}") text=uiLabelMap.CommonEdit class="+${styles.action_nav!} ${styles.action_update!}"/>
            <#assign finAcctTransCondList = delegator.findByAnd("FinAccountTrans", {"glReconciliationId" : glReconciliationId, "statusId" : "FINACT_TRNS_CREATED"}, null, false)>
            <#if finAcctTransCondList?has_content>
                <@menuitem type="link" href="javascript:document.CancelBankReconciliationForm.submit();" text=uiLabelMap.AccountingCancelBankReconciliation class="+${styles.action_run_sys!} ${styles.action_terminate!}" />
            </#if>
        </@menu>
    </#macro>
    
    <@section title=uiLabelMap.AccountingCurrentBankReconciliation menuContent=menuContent>
    </@section>
    
    <@section title=uiLabelMap.AccountingPreviousBankReconciliation>
        <#if previousGlReconciliation?has_content>
            <@table type="fields" class="+${styles.table_spacing_tiny_hint!}">
                <@tr>
                    <@td>${uiLabelMap.FormFieldTitle_glReconciliationName}</@td>
                    <@td>${previousGlReconciliation.glReconciliationName!}</@td>
                </@tr>
                <#if previousGlReconciliation.statusId??>
                    <@tr>
                        <@td>${uiLabelMap.CommonStatus}</@td>
                        <#assign previousStatus = previousGlReconciliation.getRelatedOne("StatusItem", true)>
                        <@td>${previousStatus.description!}</@td>
                    </@tr>
                </#if>
                <@tr>
                    <@td>${uiLabelMap.FormFieldTitle_reconciledDate}</@td>
                    <@td>${previousGlReconciliation.reconciledDate!}</@td>
                </@tr>
                <@tr>
                    <@td>${uiLabelMap.AccountingOpeningBalance}</@td>
                    <@td><@ofbizCurrency amount=(previousGlReconciliation.openingBalance!'0')/></@td>
                </@tr>
                <#if previousGlReconciliation.reconciledBalance??>
                    <@tr>
                        <@td>${uiLabelMap.FormFieldTitle_reconciledBalance}</@td>
                        <@td><@ofbizCurrency amount=(previousGlReconciliation.reconciledBalance!'0')/></@td>
                    </@tr>
                </#if>
                <#if previousClosingBalance??>
                    <@tr>
                        <@td>${uiLabelMap.FormFieldTitle_closingBalance}</@td>
                        <@td><@ofbizCurrency amount=previousClosingBalance/></@td>
                    </@tr>
                </#if>
            </@table>
        </#if>
    </@section>

    <@section title=uiLabelMap.AccountingFinAcctTransAssociatedToGlReconciliation>
        <#if finAccountTransList?has_content>
            <@table type="data-list" autoAltRows=true>
                <@thead>   
                    <@tr class="header-row-2">
                        <@th>${uiLabelMap.FormFieldTitle_finAccountTransId}</@th>
                        <@th>${uiLabelMap.FormFieldTitle_finAccountTransType}</@th>
                        <@th>${uiLabelMap.PartyParty}</@th>
                        <@th>${uiLabelMap.FormFieldTitle_transactionDate}</@th>
                        <@th>${uiLabelMap.FormFieldTitle_entryDate}</@th>
                        <@th>${uiLabelMap.CommonAmount}</@th>
                        <@th>${uiLabelMap.FormFieldTitle_paymentId}</@th>
                        <@th>${uiLabelMap.OrderPaymentType}</@th>
                        <@th>${uiLabelMap.FormFieldTitle_paymentMethodTypeId}</@th>
                        <@th>${uiLabelMap.CommonStatus}</@th>
                        <@th>${uiLabelMap.CommonComments}</@th>
                        <#if finAccountTransactions?has_content>
                            <@th>${uiLabelMap.AccountingRemoveFromGlReconciliation}</@th>
                            <@th>${uiLabelMap.FormFieldTitle_glTransactions}</@th>
                        </#if>
                    </@tr>
                </@thead>
                <#list finAccountTransList as finAccountTrans>
                        <#assign payment = "">
                    <#assign payments = "">
                    <#assign status = "">
                    <#assign paymentType = "">
                    <#assign paymentMethodType = "">
                    <#assign partyName = "">
                    <#if finAccountTrans.paymentId?has_content>
                        <#assign payment = delegator.findOne("Payment", {"paymentId" : finAccountTrans.paymentId}, true)>
                    </#if>
                    <#assign finAccountTransType = delegator.findOne("FinAccountTransType", {"finAccountTransTypeId" : finAccountTrans.finAccountTransTypeId}, true)>
                    <#if finAccountTrans.statusId?has_content>
                        <#assign status = delegator.findOne("StatusItem", {"statusId" : finAccountTrans.statusId}, true)>
                    </#if>
                    <#if payment?has_content && payment.paymentTypeId?has_content>
                        <#assign paymentType = delegator.findOne("PaymentType", {"paymentTypeId" : payment.paymentTypeId}, true)>
                    </#if>
                    <#if payment?has_content && payment.paymentMethodTypeId?has_content>
                        <#assign paymentMethodType = delegator.findOne("PaymentMethodType", {"paymentMethodTypeId" : payment.paymentMethodTypeId}, true)>
                    </#if>
                    <#if finAccountTrans.partyId?has_content>
                        <#assign partyName = (delegator.findOne("PartyNameView", {"partyId" : finAccountTrans.partyId}, true))>
                    </#if>
                    <@tr valign="middle">
                        <@td>
                            <input name="finAccountTransId_o_${finAccountTrans_index}" type="hidden" value="${finAccountTrans.finAccountTransId}"/>
                            <input name="organizationPartyId_o_${finAccountTrans_index}" type="hidden" value="${defaultOrganizationPartyId}"/>
                            <input id="finAccountTransId_${finAccountTrans_index}" name="_rowSubmit_o_${finAccountTrans_index}" type="hidden" value="Y"/>
                            ${finAccountTrans.finAccountTransId!}
                        </@td>
                        <@td>${finAccountTransType.description!}</@td>
                        <@td><#if partyName?has_content>${(partyName.firstName)!} ${(partyName.lastName)!} ${(partyName.groupName)!}<a href="<@serverUrl>/partymgr/control/viewprofile?partyId=${partyName.partyId}</@serverUrl>">[${(partyName.partyId)!}]</a></#if></@td>
                        <@td>${finAccountTrans.transactionDate!}</@td>
                        <@td>${finAccountTrans.entryDate!}</@td>
                        <@td><@ofbizCurrency amount=finAccountTrans.amount isoCode=defaultOrganizationPartyCurrencyUomId/></@td>
                        <@td>
                            <#if finAccountTrans.paymentId?has_content>
                                <a href="<@pageUrl>paymentOverview?paymentId=${finAccountTrans.paymentId}</@pageUrl>">${finAccountTrans.paymentId}</a>
                            </#if>
                        </@td>
                        <@td><#if paymentType?has_content>${paymentType.description!}</#if></@td>
                        <@td><#if paymentMethodType?has_content>${paymentMethodType.description!}</#if></@td>
                        <@td><#if status?has_content>${status.description!}</#if></@td>
                        <@td>${finAccountTrans.comments!}</@td>
                        <#if finAccountTrans.statusId == "FINACT_TRNS_CREATED">
                            <@td align="center"><a href="javascript:document.removeFinAccountTransAssociation_${finAccountTrans.finAccountTransId}.submit();" class="${styles.link_run_sys!} ${styles.action_remove!}">${uiLabelMap.CommonRemove}</a></@td>
                        <#else>
                            <@td/>
                        </#if>
                        <#if finAccountTrans.paymentId?has_content>
                            <@td align="center">
                                <#-- FIXME: the content doesn't fit in the modal window. Perhaps it should be wider depending on the content -->
                                <@modal id="toggleGlTransactions_${finAccountTrans.finAccountTransId}" label=uiLabelMap.FormFieldTitle_glTransactions>
                                    <#include "ShowGlTransactions.ftl"/>
                                </@modal>
                            </@td>
                        </#if>
                    </@tr>
                </#list>
            </@table>
        </#if>
    
        <span>${uiLabelMap.AccountingTotalCapital} </span><@ofbizCurrency amount=transactionTotalAmount.grandTotal isoCode=defaultOrganizationPartyCurrencyUomId/>
        <#if isReconciled == false>
            <@field type="submit" text=uiLabelMap.AccountingReconcile class="+${styles.link_run_sys!} ${styles.action_update!}"/>
        </#if>
    </@section>
</form>

<form name="CancelBankReconciliationForm" method="post" action="<@pageUrl>cancelBankReconciliation</@pageUrl>">
    <input name="finAccountId" type="hidden" value="${finAccountId}"/>
    <input name="glReconciliationId" type="hidden" value="${glReconciliationId}"/>
</form>
<#list finAccountTransList as finAccountTrans>
    <form name="removeFinAccountTransAssociation_${finAccountTrans.finAccountTransId}" method="post" action="<@pageUrl>removeFinAccountTransAssociation</@pageUrl>">
        <input name="finAccountTransId" type="hidden" value="${finAccountTrans.finAccountTransId}"/>
        <input name="finAccountId" type="hidden" value="${finAccountTrans.finAccountId}"/>
        <input name="glReconciliationId" type="hidden" value="${glReconciliationId}"/>
    </form>
</#list>

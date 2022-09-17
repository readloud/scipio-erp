<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->

<@script>
function togglePaymentId(master) {
    var payments = jQuery("#paymentBatchForm :checkbox[name='paymentIds']");

    jQuery.each(payments, function() {
        this.checked = master.checked;
    });
    getPaymentRunningTotal();
}
function getPaymentRunningTotal() {
    var payments = jQuery("#paymentBatchForm :checkbox[name='paymentIds']");

    //test if all checkboxes are checked
    var allChecked = true;
    jQuery.each(payments, function() {
        if (!jQuery(this).is(':checked')) {
            allChecked = false;
            return false;
        }
    });

    if(allChecked) {
        jQuery('#checkAllPayments').attr('checked', true);
    } else {
        jQuery('#checkAllPayments').attr('checked', false);
    }

    // check if any checkbox is checked
    var anyChecked = false;
    jQuery.each(payments, function() {
        if (jQuery(this).is(':checked')) {
            anyChecked = true;
            return false;
        }
    });

    if(anyChecked) {
        jQuery({
            url: 'getPaymentRunningTotal',
            async: true,
            data: jQuery('#paymentBatchForm').serialize(),
            success: function(data) {
                jQuery('#showPaymentRunningTotal').html(data.paymentRunningTotal);
            }
        });

        if(jQuery('#serviceName').val() != "") {
            jQuery('#submitButton').removeAttr('disabled');
        } else {
            jQuery('#submitButton').attr('disabled', true);
        }

    } else {
        jQuery('#submitButton').attr('disabled', true);
        jQuery('#showPaymentRunningTotal').html("");
    }
}
function setServiceName(selection) {
    if (selection.value == 'massPaymentsToNotPaid' || selection.value == 'massPaymentsToReceived' || selection.value == 'massPaymentsToConfirmed' || selection.value == 'massPaymentsToCancelled' || selection.value == 'massPaymentsToVoid') {
        jQuery('#paymentBatchForm').attr('action', jQuery('#paymentStatusChange').val());
    }
    else {
        jQuery('#paymentBatchForm').attr('action', selection.value);
    }
    if (selection.value == 'massPaymentsToNotPaid') {
        jQuery('#statusId').val("PMNT_NOT_PAID");
    } else if (selection.value == 'massPaymentsToReceived') {
        jQuery('#statusId').val("PMNT_RECEIVED");
    }else if (selection.value == 'massPaymentsToConfirmed') {
        jQuery('#statusId').val("PMNT_CONFIRMED");
    }else if (selection.value == 'massPaymentsToCancelled') {
        jQuery('#statusId').val("PMNT_CANCELLED");
    }else if (selection.value == 'massPaymentsToVoid') {
        jQuery('#statusId').val("PMNT_VOID");
    }
    if (jQuery('#processBatchPayment').is(':selected')) {
        jQuery('#createPaymentBatch').fadeOut('slow');
    } else {
        jQuery('#createPaymentBatch').fadeIn('slow');
    }

    var payments = jQuery("#paymentBatchForm :checkbox[name='paymentIds']");
    // check if any checkbox is checked
    var anyChecked = false;
    jQuery.each(payments, function() {
        if (jQuery(this).is(':checked')) {
            anyChecked = true;
            return false;
        }
    });

    if(anyChecked && (jQuery('#serviceName').val() != "")) {
        jQuery('#submitButton').removeAttr('disabled');
    } else {
       jQuery('#submitButton').attr('disabled' , true);
    }

}
</@script>
<@section>
        <form id="paymentBatchForm" method="post" action="">
            <#if paymentList?has_content>
                <div class="clearfix">
                <div class="float-left">
                    <span>${uiLabelMap.AccountingRunningTotal} :</span>
                    <span id="showPaymentRunningTotal"></span>
                </div>
                <div class="align-float">
                    <select name="serviceName" id="serviceName" onchange="javascript:setServiceName(this);">
                        <option value="">${uiLabelMap.AccountingSelectAction}</options>
                        <option value="<@pageUrl>createPaymentBatch</@pageUrl>" id="processBatchPayment">${uiLabelMap.AccountingCreateBatch}</option>
                        <option value="massPaymentsToNotPaid">${uiLabelMap.AccountingPaymentStatusToNotPaid}</option>
                        <option value="massPaymentsToReceived">${uiLabelMap.AccountingInvoiceStatusToReceived}</option>
                        <option value="massPaymentsToConfirmed">${uiLabelMap.AccountingPaymentTabStatusToConfirmed}</option>
                        <option value="massPaymentsToCancelled">${uiLabelMap.AccountingPaymentTabStatusToCancelled}</option>
                        <option value="massPaymentsToVoid">${uiLabelMap.AccountingPaymentTabStatusToVoid}</option>
                    </select>
                    <input id="submitButton" type="button" onclick="javascript:jQuery('#paymentBatchForm').submit();" value="${uiLabelMap.CommonRun}" disabled="disabled" />
                    <input type="hidden" name="organizationPartyId" value="${organizationPartyId!}" />
                    <input type="hidden" name="paymentGroupTypeId" value="BATCH_PAYMENT" />
                    <input type="hidden" name="groupInOneTransaction" value="Y" />
                    <input type="hidden" name="paymentStatusChange" id="paymentStatusChange" value="<@pageUrl>massChangePaymentStatus</@pageUrl>" />
                    <input type="hidden" name="statusId" id="statusId" value="${parameters.statusId!}" />
                    <#if finAccountId?has_content>
                        <input type="hidden" name="finAccountId" value="${finAccountId!}" />
                    </#if>
                    <input type="hidden" name="paymentMethodTypeId" value="${paymentMethodTypeId!}" />
                    <input type="hidden" name="cardType" value="${cardType!}" />
                    <input type="hidden" name="partyIdFrom" value="${partyIdFrom!}" />
                    <input type="hidden" name="fromDate" value="${fromDate!}" />
                    <input type="hidden" name="thruDate" value="${thruDate!}" />
                </div>
                </div>
                <div id="createPaymentBatch" style="display: none;" class="align-float">
                    <label for="paymentGroupName">${uiLabelMap.AccountingPaymentGroupName}</label>
                    <input type="text" size="25" id="paymentGroupName" name="paymentGroupName" />
                    <#if finAccounts?has_content>
                        <label for="finAccountId">${uiLabelMap.AccountingBankAccount}</label>
                        <select name="finAccountId" id="finAccountId">
                            <#list finAccounts as finAccount>
                              <#if ("FNACT_MANFROZEN" != finAccount.statusId) && ("FNACT_CANCELLED" != finAccount.statusId)>
                                <option value="${finAccount.get("finAccountId")}">${finAccount.get("finAccountName")} [${finAccount.get("finAccountId")}]</option>
                              </#if>
                            </#list>
                        </select>
                    </#if>
                </div>
                <@table type="data-list" autoAltRows=true>
                  <@thead>
                    <@tr class="header-row-2">
                      <@th>${uiLabelMap.FormFieldTitle_paymentId}</@th>
                      <@th>${uiLabelMap.AccountingPaymentType}</@th>
                      <@th>${uiLabelMap.CommonStatus}</@th>
                      <@th>${uiLabelMap.CommonComments}</@th>
                      <@th>${uiLabelMap.AccountingFromParty}</@th>
                      <@th>${uiLabelMap.AccountingToParty}</@th>
                      <@th>${uiLabelMap.AccountingEffectiveDate}</@th>
                      <@th>${uiLabelMap.AccountingAmount}</@th>
                      <@th>${uiLabelMap.FormFieldTitle_amountToApply}</@th>
                      <@th>${uiLabelMap.CommonPaymentMethodType}</@th>
                      <@th>
                        ${uiLabelMap.CommonSelectAll}
                        <input type="checkbox" id="checkAllPayments" name="checkAllPayments" onchange="javascript:togglePaymentId(this);"/>
                      </@th>
                    </@tr>
                  </@thead>
                  <@tbody>
                    <#list paymentList as payment>
                      <@tr>
                        <@td><a href="<@pageUrl>paymentOverview?paymentId=${payment.paymentId}</@pageUrl>" class="${styles.link_nav_info_id!}">${payment.paymentId}</a></@td>
                        <@td>
                          ${payment.paymentTypeDesc!payment.paymentTypeId}
                        </@td>
                        <@td>
                          ${payment.statusDesc!payment.statusId}
                        </@td>
                        <@td>${(payment.comments)!}</@td>
                        <@td>
                          <a href="<@serverUrl>/partymgr/control/viewprofile?partyId=${payment.partyIdFrom}</@serverUrl>">${(payment.partyFromFirstName)!} ${(payment.partyFromLastName)!} ${(payment.partyFromGroupName)!}[${(payment.partyIdFrom)!}]</a>
                        </@td>
                        <@td>
                          <a href="<@serverUrl>/partymgr/control/viewprofile?partyId=${payment.partyIdTo}</@serverUrl>">${(payment.partyToFirstName)!} ${(payment.partyToLastName)!} ${(payment.partyToGroupName)!}[${(payment.partyIdTo)!}]</a>
                        </@td>
                        <@td>${payment.effectiveDate!}</@td>
                        <@td><@ofbizCurrency amount = payment.amount isoCode = payment.currencyUomId /></@td>
                        <@td>
                          <#assign amountToApply = Static["org.ofbiz.accounting.payment.PaymentWorker"].getPaymentNotApplied(payment) />
                          <@ofbizCurrency amount = amountToApply isoCode = amountToApply.currencyUomId />
                        </@td>
                        <@td>
                          <#assign creditCard = (delegator.findOne("CreditCard", {"paymentMethodId" : payment.paymentMethodId}, false))! />
                          ${payment.paymentMethodTypeDesc!payment.paymentMethodTypeId}
                          <#if creditCard?has_content>/${(creditCard.cardType)!}</#if>
                        </@td>
                        <@td>
                          <input type="checkbox" id="paymentId_${payment_index}" name="paymentIds" value="${payment.paymentId}" onclick="javascript:getPaymentRunningTotal('paymentId_${payment_index}');" />
                        </@td>
                      </@tr>
                    </#list>
                  </@tbody>
                </@table>
            <#else>
                <@commonMsg type="result-norecord"/>
            </#if>
        </form>
</@section>

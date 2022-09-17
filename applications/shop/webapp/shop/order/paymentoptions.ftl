<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->
<#include "component://shop/webapp/shop/order/ordercommon.ftl">

<#-- SCIPIO: DEPRECATED TEMPLATE -->

<@section><#-- title=uiLabelMap.AccountingPaymentInformation-->
  <#-- initial screen show a list of options -->
  <form id="editPaymentOptions" method="post" action="<@pageUrl>setPaymentInformation</@pageUrl>" name="${parameters.formNameValue}">

      <@fields type="default-compact" fieldArgs={"checkboxType":"simple-standard"}>
        <#if productStorePaymentMethodTypeIdMap.GIFT_CARD??>
          <@field type="checkbox" name="addGiftCard" value="Y" checked=(addGiftCard?? && addGiftCard == "Y") label=uiLabelMap.AccountingCheckGiftCard/>
        </#if>
      </@fields>
    
       
    <#--<@field type="generic" label=uiLabelMap.AccountingPaymentMethod>-->
    <fieldset>
      <@fields type="default-compact" fieldArgs={"inlineItems":false}>
        <#if productStorePaymentMethodTypeIdMap.EXT_OFFLINE??>
          <@field type="radio" id="paymentMethodTypeId_EXT_OFFLINE" name="paymentMethodTypeId" value="EXT_OFFLINE" checked=(paymentMethodTypeId?? && paymentMethodTypeId == "EXT_OFFLINE") label=uiLabelMap.OrderPaymentOfflineCheckMoney/>
        </#if>
        <#if productStorePaymentMethodTypeIdMap.CREDIT_CARD??>
          <@field type="radio" id="paymentMethodTypeId_CREDIT_CARD" name="paymentMethodTypeId" value="CREDIT_CARD" checked=(paymentMethodTypeId?? && paymentMethodTypeId == "CREDIT_CARD") label=uiLabelMap.AccountingVisaMastercardAmexDiscover/>
        </#if>
        <#if productStorePaymentMethodTypeIdMap.EFT_ACCOUNT??>
          <@field type="radio" id="paymentMethodTypeId_EFT_ACCOUNT" name="paymentMethodTypeId" value="EFT_ACCOUNT" checked=(paymentMethodTypeId?? && paymentMethodTypeId == "EFT_ACCOUNT") label=uiLabelMap.AccountingAHCElectronicCheck/>
        </#if>
        <#if productStorePaymentMethodTypeIdMap.EXT_PAYPAL??>
          <@field type="radio" id="paymentMethodTypeId_EXT_PAYPAL" name="paymentMethodTypeId" value="EXT_PAYPAL" checked=(paymentMethodTypeId?? && paymentMethodTypeId == "EXT_PAYPAL") label=uiLabelMap.AccountingPayWithPayPal/>
        </#if>
        <#if productStorePaymentMethodTypeIdMap.EXT_LIGHTNING??>
          <@field type="radio" id="paymentMethodTypeId_EXT_LIGHTNING" name="paymentMethodTypeId" value="EXT_LIGHTNING" checked=(paymentMethodTypeId?? && paymentMethodTypeId == "EXT_LIGHTNING") label=uiLabelMap.AccountingPayBitcoin/>
        </#if>
      </@fields>
    </fieldset>
    <#--</@field>-->

    <#--
    <@field type="submit" class="${styles.link_run_session!} ${styles.action_update!}" text=uiLabelMap.CommonContinue/>
    -->
    
  </form>
</@section>

<@checkoutActionsMenu directLinks=true />

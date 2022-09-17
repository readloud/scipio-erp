<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->
<#include "component://shop/webapp/shop/order/ordercommon.ftl">

<@script>
function submitForm(form, mode, value) {
    if (mode == "DN") {
        // done action; checkout
        form.action="<@pageUrl>checkoutoptions</@pageUrl>";
        form.submit();
    } else if (mode == "CS") {
        // continue shopping
        form.action="<@pageUrl>updateCheckoutOptions/showcart</@pageUrl>";
        form.submit();
    } else if (mode == "NA") {
        // new address
        form.action="<@pageUrl>updateCheckoutOptions/editcontactmech?preContactMechTypeId=POSTAL_ADDRESS&contactMechPurposeTypeId=SHIPPING_LOCATION&DONE_PAGE=checkoutoptions</@pageUrl>";
        form.submit();
    } else if (mode == "EA") {
        // edit address
        form.action="<@pageUrl>updateCheckoutOptions/editcontactmech?DONE_PAGE=checkoutshippingaddress&contactMechId="+value+"</@pageUrl>";
        form.submit();
    } else if (mode == "NC") {
        // new credit card
        form.action="<@pageUrl>updateCheckoutOptions/editcreditcard?DONE_PAGE=checkoutoptions</@pageUrl>";
        form.submit();
    } else if (mode == "EC") {
        // edit credit card
        form.action="<@pageUrl>updateCheckoutOptions/editcreditcard?DONE_PAGE=checkoutoptions&paymentMethodId="+value+"</@pageUrl>";
        form.submit();
    } else if (mode == "NE") {
        // new eft account
        form.action="<@pageUrl>updateCheckoutOptions/editeftaccount?DONE_PAGE=checkoutoptions</@pageUrl>";
        form.submit();
    } else if (mode == "EE") {
        // edit eft account
        form.action="<@pageUrl>updateCheckoutOptions/editeftaccount?DONE_PAGE=checkoutoptions&paymentMethodId="+value+"</@pageUrl>";
        form.submit();
    }
}

</@script>

<@section title="${rawLabel('OrderHowShallWeShipIt')}?"><#-- SCIPIO: No numbers for multi-page checkouts, make checkout too rigid: 2) ${uiLabelMap.OrderHowShallWeShipIt}? -->
    <form method="post" name="checkoutInfoForm" id="checkoutInfoForm">
        <#--<fieldset>-->
            <input type="hidden" name="checkoutpage" value="shippingoptions"/>

            <#-- SCIPIO: switched from top-level inverted fields to generic with label because otherwise too inconsistent with
                everything else on this form and with some other pages -->
            <#assign selectedShippingMethod = raw(parameters.shipping_method!chosenShippingMethod!"N@A")>
            <@field type="generic" label=wrapAsRaw("<strong>${uiLabelMap.OrderShippingMethod}</strong>", 'htmlmarkup') required=true>
            <@fields inlineItems=false>
              <#list carrierShipmentMethodList as carrierShipmentMethod>
                <#-- SCIPIO: For shop, will not show ship methods whose shipping estimates returned an error.
                    Selecting them here causes the next events to fail (offline calc for these was not supported in ecommerce). 
                    Some stores may want to let customers place
                    orders with offline calculation, but we know the failure is very likely to be misconfiguration
                    or connectivity failure, and by default we can't assume the store is equipped to handle offlines in these cases.
                    NOTE: Failure is subtly noted by the absence of ship estimate (null). -->
                <#assign shippingEst = "">
                <#if shoppingCart.getShippingContactMechId()??>
                  <#assign shippingEst = shippingEstWpr.getShippingEstimate(carrierShipmentMethod)?default(-1)>
                </#if>
                <#assign shippingMethod = raw(carrierShipmentMethod.shipmentMethodTypeId) + "@" + raw(carrierShipmentMethod.partyId)>
                <#assign labelContent>
                  <#if carrierShipmentMethod.partyId != "_NA_">${carrierShipmentMethod.partyId!}&nbsp;</#if>${carrierShipmentMethod.description!}
                  <#if shippingEst?has_content><#if (shippingEst > -1)> - <@ofbizCurrency amount=shippingEst isoCode=shoppingCart.getCurrency()/><#elseif raw(carrierShipmentMethod.shipmentMethodTypeId!) != "NO_SHIPPING"> - ${uiLabelMap.OrderCalculatedOffline}</#if></#if><#-- SCIPIO: NO_SHIPPING check -->
                </#assign>
                <#--<@commonInvField type="generic" labelContent=labelContent>-->
                <@field type="radio" name="shipping_method" value=(shippingMethod!"") checked=(shippingMethod == selectedShippingMethod) label=wrapAsRaw(labelContent, 'htmlmarkup') /><#--inline=true -->
                <#--</@commonInvField>-->
              </#list>
              <#if !carrierShipmentMethodList?? || carrierShipmentMethodList?size == 0>
                <#assign labelContent>${uiLabelMap.OrderUseDefault}.</#assign>
                <#--<@commonInvField type="generic" labelContent=labelContent>-->
                <@field type="radio" name="shipping_method" value="Default" checked=true label=wrapAsRaw(labelContent, 'htmlmarkup')/><#--inline=true -->
                <#--</@commonInvField>-->
              </#if>
            </@fields>
            </@field>

            <br/>
            <#--<hr />-->
              
            <@field type="generic" label="${rawLabel('OrderShipAllAtOnce')}?">
              <@fields inlineItems=false>
              <@field type="radio" checked=("Y" != (parameters.may_split!shoppingCart.getMaySplit()!"N")) name="may_split" value="false" label="${rawLabel('OrderPleaseWaitUntilBeforeShipping')}."/>
              <@field type="radio" checked=("Y" == (parameters.may_split!shoppingCart.getMaySplit()!"N")) name="may_split" value="true" label="${rawLabel('OrderPleaseShipItemsBecomeAvailable')}."/>
              </@fields>
            </@field>
              <#--<hr />-->
    <#-- limit len -->
    <@fields type="inherit-all" fieldArgs={"gridArgs":{"totalLarge":8}}>
              <@field type="textarea" title=uiLabelMap.OrderSpecialInstructions cols="30" rows="3" wrap="hard" name="shipping_instructions" label=uiLabelMap.OrderSpecialInstructions>${parameters.shipping_instructions!shoppingCart.getShippingInstructions()!}</@field>
       
              <#--<hr />-->

              <#if shoppingCart.getPoNumber()?? && shoppingCart.getPoNumber() != "(none)">
                <#assign currentPoNumber = shoppingCart.getPoNumber()>
              </#if>
              <@field type="input" label=uiLabelMap.OrderPoNumber name="correspondingPoId" size="15" value=(parameters.correspondingPoId!currentPoNumber!)/>
     
            <#if (productStore.showCheckoutGiftOptions!) != "N">
              <#--<hr />-->
              <@field type="generic" label=uiLabelMap.OrderIsThisGift>
                    <@field type="radio" checked=("Y" == (parameters.is_gift!shoppingCart.getIsGift()!"N")) name="is_gift" value="true" label=uiLabelMap.CommonYes />
                    <@field type="radio" checked=("Y" != (parameters.is_gift!shoppingCart.getIsGift()!"N")) name="is_gift" value="false" label=uiLabelMap.CommonNo />
              </@field>
              <#--<hr />-->

              <@field type="textarea" label=uiLabelMap.OrderGiftMessage cols="30" rows="3" wrap="hard" name="gift_message">${parameters.gift_message!shoppingCart.getGiftMessage()!}</@field>
     
            <#else>
              <input type="hidden" name="is_gift" value="false"/>
            </#if>
              <#--<hr />-->
              <@field type="generic" label=uiLabelMap.PartyEmailAddresses>
                  <div>${uiLabelMap.OrderEmailSentToFollowingAddresses}:</div>
                  <div>
                    <b>
                      <#list emailList as email>
                        ${email.infoString!}<#if email_has_next>,</#if>
                      </#list>
                    </b>
                  </div>
                  <div>${uiLabelMap.OrderUpdateEmailAddress} <a href="<@pageUrl>viewprofile?DONE_PAGE=checkoutoptions</@pageUrl>" class="${styles.link_nav_info!} ${styles.action_view!}" target="_BLANK">${uiLabelMap.PartyProfile}</a>.</div>
                  <br />
                  <div>${uiLabelMap.OrderCommaSeperatedEmailAddresses}:</div>
                  <@field type="input" widgetOnly=true size="30" name="order_additional_emails" value=(parameters.order_additional_emails!shoppingCart.getOrderAdditionalEmails()!)/>
              </@field>
        <#--</fieldset>-->
    </@fields>

    </form>
</@section>

<@checkoutActionsMenu directLinks=false formName="checkoutInfoForm" />



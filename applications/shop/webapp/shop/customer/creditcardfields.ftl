<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->
<#include "component://shop/webapp/shop/customer/customercommon.ftl">

<#-- SCIPIO: DUPLICATED FROM:
<#include "component://accounting/webapp/accounting/common/creditcardfields.ftl">
-->

<#if !ccfParams??>
  <#assign ccfParams = parameters>
</#if>

<#-- SCIPIO: Do this on individual field-by-field basis instead
<#if !creditCard?has_content>
    <#assign creditCard = requestParameters>
</#if>

<#if !paymentMethod?has_content>
    <#assign paymentMethod = requestParameters>
</#if>
-->

<#-- SCIPIO: include fieldset in parent if desired
<@fieldset>
-->
    <@field type="input" size="30" maxlength="60" name="${fieldNamePrefix}companyNameOnCard" value=(ccfParams["${fieldNamePrefix}companyNameOnCard"]!(creditCard.companyNameOnCard)!(ccfFallbacks.companyNameOnCard)!) label=uiLabelMap.AccountingCompanyNameCard/>     

    <@personalTitleField params=ccfParams name="${fieldNamePrefix}titleOnCard" personalTitle=((creditCard.titleOnCard)!(ccfFallbacks.titleOnCard)!"") label=uiLabelMap.AccountingPrefixCard /> 

    <@field type="input" size="20" maxlength="60" name="${fieldNamePrefix}firstNameOnCard" value=(ccfParams["${fieldNamePrefix}firstNameOnCard"]!(creditCard.firstNameOnCard)!(ccfFallbacks.firstNameOnCard)!) label=uiLabelMap.AccountingFirstNameCard required=true/>     
    <@field type="input" size="15" maxlength="60" name="${fieldNamePrefix}middleNameOnCard" value=(ccfParams["${fieldNamePrefix}middleNameOnCard"]!(creditCard.middleNameOnCard)!(ccfFallbacks.middleNameOnCard)!) label=uiLabelMap.AccountingMiddleNameCard />    
    <@field type="input" size="20" maxlength="60" name="${fieldNamePrefix}lastNameOnCard" value=(ccfParams["${fieldNamePrefix}lastNameOnCard"]!(creditCard.lastNameOnCard)!(ccfFallbacks.lastNameOnCard)!) label=uiLabelMap.AccountingLastNameCard required=true />  
    <@field type="select" name="${fieldNamePrefix}suffixOnCard" label=uiLabelMap.AccountingSuffixCard>
        <option value="">${uiLabelMap.CommonSelectOne}</option>
        <#assign ccfSuffixOnCard = ccfParams["${fieldNamePrefix}suffixOnCard"]!(creditCard.suffixOnCard)!(ccfFallbacks.suffixOnCard)!"">
        <option<#if ccfSuffixOnCard == "Jr."> selected="selected"</#if>>Jr.</option>
        <option<#if ccfSuffixOnCard == "Sr."> selected="selected"</#if>>Sr.</option>
        <option<#if ccfSuffixOnCard == "I"> selected="selected"</#if>>I</option>
        <option<#if ccfSuffixOnCard == "II"> selected="selected"</#if>>II</option>
        <option<#if ccfSuffixOnCard == "III"> selected="selected"</#if>>III</option>
        <option<#if ccfSuffixOnCard == "IV"> selected="selected"</#if>>IV</option>
        <option<#if ccfSuffixOnCard == "V"> selected="selected"</#if>>V</option>
    </@field>
    <@field type="select" name="${fieldNamePrefix}cardType" label=uiLabelMap.AccountingCardType required=true>
        <#if ccfParams["${fieldNamePrefix}cardType"]??>
          <option value="${ccfParams["${fieldNamePrefix}cardType"]}">${ccfParams["${fieldNamePrefix}cardType"]}</option>
          <option>---</option>
        <#elseif (creditCard.cardType)??>
          <option value="${creditCard.cardType}">${creditCard.cardType}</option>
          <option>---</option>
        </#if>
        <@render resource="component://common/widget/CommonScreens.xml#cctypes" />
    </@field>
   
    <#assign cardNumber = raw(ccfParams["${fieldNamePrefix}cardNumber"]!(creditCard.cardNumber)!(ccfFallbacks.cardNumber)!)>
    <#if cardNumber?has_content>
        <#if cardNumberMinDisplay?has_content>
            <#-- create a display version of the card where all but the last four digits are * -->
            <#assign cardNumberDisplay = getCreditCardDisplayNumber(cardNumber!)><#-- SCIPIO: Refactored -->
            <@field type="input" size="20" maxlength="30" name="${fieldNamePrefix}cardNumber" value=(cardNumberDisplay!) label=uiLabelMap.AccountingCardNumber required=true />
        <#else>
            <@field type="input" size="20" maxlength="30" name="${fieldNamePrefix}cardNumber" value=(cardNumber!) label=uiLabelMap.AccountingCardNumber required=true/>
        </#if>
    <#else>
        <@field type="input" size="20" maxlength="30" name="${fieldNamePrefix}cardNumber" value="" label=uiLabelMap.AccountingCardNumber required=true/>
    </#if>
    
  <#-- SCIPIO: This was commented by someone else, for reasons unclear... use a bool instead. but don't display any current value: ${creditCard.cardSecurityCode!} -->
  <#if showSecurityCodeField>
    <@field type="input" size="5" maxlength="10" name="${fieldNamePrefix}cardSecurityCode" value="" label=uiLabelMap.AccountingCardSecurityCode />
  </#if>
  
    <#assign expMonth = "">
    <#assign expYear = "">
    <#if creditCard?? && creditCard.expireDate??>
        <#assign expDate = creditCard.expireDate>
        <#if (expDate?? && expDate.indexOf("/") > 0)>
            <#assign expMonth = expDate.substring(0,expDate.indexOf("/"))>
            <#assign expYear = expDate.substring(expDate.indexOf("/")+1)>
        </#if>
    </#if>
      
    <@field type="generic" label=uiLabelMap.AccountingExpirationDate required=true>
      <@fields type="default">
        <@field type="select" inline=true name="${fieldNamePrefix}expMonth" required=true>
          <#if ccfParams["${fieldNamePrefix}expMonth"]??>
            <#assign ccExprMonth = ccfParams["${fieldNamePrefix}expMonth"]>
          <#elseif creditCard?has_content && expMonth?has_content>
            <#assign ccExprMonth = expMonth>
          <#elseif (ccfFallbacks.expMonth)??>
            <#assign ccExprMonth = ccfFallbacks.expMonth>
          <#else>
            <#assign ccExprMonth = "">
          </#if>
          <#if ccExprMonth?has_content>
            <option value="${ccExprMonth!}">${ccExprMonth!}</option>
          </#if>
          <@render resource="component://common/widget/CommonScreens.xml#ccmonths" />
        </@field>
        <@field type="select" inline=true name="${fieldNamePrefix}expYear" required=true>
          <#if ccfParams["${fieldNamePrefix}expYear"]??>
            <#assign ccExprYear = ccfParams["${fieldNamePrefix}expYear"]>
          <#elseif creditCard?has_content && expYear?has_content>
            <#assign ccExprYear = expYear>
          <#elseif (ccfFallbacks.expYear)??>
            <#assign ccExprMonth = ccfFallbacks.expYear>
          <#else>
            <#assign ccExprYear = "">
          </#if>
          <#if ccExprYear?has_content>
            <option value="${ccExprYear!}">${ccExprYear!}</option>
          </#if>
          <@render resource="component://common/widget/CommonScreens.xml#ccyears" />
        </@field>
      </@fields>
    </@field>

    <@field type="input" size="20" maxlength="30" name="${fieldNamePrefix}description" value=(ccfParams["${fieldNamePrefix}description"]!(paymentMethod.description)!(ccfFallbacks.description)!) label=uiLabelMap.CommonDescription containerClass="+${styles.field_extra!}"/>

<#--
</@fieldset>
-->





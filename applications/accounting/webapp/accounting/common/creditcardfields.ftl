<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->
<#import "component://accounting/webapp/accounting/common/acctlib.ftl" as acctlib>

<#-- SCIPIO: NOTE: do NOT wrap in @fields, so that caller may specify his own -->

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
    <@field type="input" size="30" maxlength="60" name="${fieldNamePrefix}companyNameOnCard" value=(parameters["${fieldNamePrefix}companyNameOnCard"]!(creditCard.companyNameOnCard)!(ccfFallbacks.companyNameOnCard)!) label=uiLabelMap.AccountingCompanyNameCard/>     
    <@field type="select" name="${fieldNamePrefix}titleOnCard" label=uiLabelMap.AccountingPrefixCard>
        <option value="">${uiLabelMap.CommonSelectOne}</option>
        <#assign ccfTitleOnCard = parameters["${fieldNamePrefix}titleOnCard"]!(creditCard.titleOnCard)!(ccfFallbacks.titleOnCard)!"">
        <option<#if ccfTitleOnCard == rawLabel('CommonTitleMr') || ccfTitleOnCard == "Mr."> selected="selected"</#if>>${uiLabelMap.CommonTitleMr}</option>
        <option<#if ccfTitleOnCard == rawLabel('CommonTitleMrs') || ccfTitleOnCard == "Mrs."> selected="selected"</#if>>${uiLabelMap.CommonTitleMrs}</option>
        <option<#if ccfTitleOnCard == rawLabel('CommonTitleMs') || ccfTitleOnCard == "Ms."> selected="selected"</#if>>${uiLabelMap.CommonTitleMs}</option>
        <option<#if ccfTitleOnCard == rawLabel('CommonTitleDr') || ccfTitleOnCard == "Dr."> selected="selected"</#if>>${uiLabelMap.CommonTitleDr}</option>
    </@field>    
    <@field type="input" size="20" maxlength="60" name="${fieldNamePrefix}firstNameOnCard" value=(parameters["${fieldNamePrefix}firstNameOnCard"]!(creditCard.firstNameOnCard)!(ccfFallbacks.firstNameOnCard)!) label=uiLabelMap.AccountingFirstNameCard required=true/>     
    <@field type="input" size="15" maxlength="60" name="${fieldNamePrefix}middleNameOnCard" value=(parameters["${fieldNamePrefix}middleNameOnCard"]!(creditCard.middleNameOnCard)!(ccfFallbacks.middleNameOnCard)!) label=uiLabelMap.AccountingMiddleNameCard />    
    <@field type="input" size="20" maxlength="60" name="${fieldNamePrefix}lastNameOnCard" value=(parameters["${fieldNamePrefix}lastNameOnCard"]!(creditCard.lastNameOnCard)!(ccfFallbacks.lastNameOnCard)!) label=uiLabelMap.AccountingLastNameCard required=true />  
    <@field type="select" name="${fieldNamePrefix}suffixOnCard" label=uiLabelMap.AccountingSuffixCard>
        <option value="">${uiLabelMap.CommonSelectOne}</option>
        <#assign ccfSuffixOnCard = parameters["${fieldNamePrefix}suffixOnCard"]!(creditCard.suffixOnCard)!(ccfFallbacks.suffixOnCard)!"">
        <option<#if ccfSuffixOnCard == "Jr."> selected="selected"</#if>>Jr.</option>
        <option<#if ccfSuffixOnCard == "Sr."> selected="selected"</#if>>Sr.</option>
        <option<#if ccfSuffixOnCard == "I"> selected="selected"</#if>>I</option>
        <option<#if ccfSuffixOnCard == "II"> selected="selected"</#if>>II</option>
        <option<#if ccfSuffixOnCard == "III"> selected="selected"</#if>>III</option>
        <option<#if ccfSuffixOnCard == "IV"> selected="selected"</#if>>IV</option>
        <option<#if ccfSuffixOnCard == "V"> selected="selected"</#if>>V</option>
    </@field>
    <@field type="select" name="${fieldNamePrefix}cardType" label=uiLabelMap.AccountingCardType required=true>
        <#if parameters["${fieldNamePrefix}cardType"]??>
          <option value="${parameters["${fieldNamePrefix}cardType"]}">${parameters["${fieldNamePrefix}cardType"]}</option>
          <option>---</option>
        <#elseif (creditCard.cardType)??>
          <option value="${creditCard.cardType}">${creditCard.cardType}</option>
          <option>---</option>
        </#if>
        <@render resource="component://common/widget/CommonScreens.xml#cctypes" />
    </@field>
   
    <#assign cardNumber = raw(parameters["${fieldNamePrefix}cardNumber"]!(creditCard.cardNumber)!(ccfFallbacks.cardNumber)!)>
    <#if cardNumber?has_content>
        <#if cardNumberMinDisplay?has_content>
            <#-- create a display version of the card where all but the last four digits are * -->
            <#assign cardNumberDisplay = acctlib.getCreditCardDisplayNumber(cardNumber!)><#-- SCIPIO: Refactored -->
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
          <#if parameters["${fieldNamePrefix}expMonth"]??>
            <#assign ccExprMonth = parameters["${fieldNamePrefix}expMonth"]>
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
          <#if parameters["${fieldNamePrefix}expYear"]??>
            <#assign ccExprYear = parameters["${fieldNamePrefix}expYear"]>
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

    <@field type="input" size="20" maxlength="30" name="${fieldNamePrefix}description" value=(parameters["${fieldNamePrefix}description"]!(paymentMethod.description)!(ccfFallbacks.description)!) label=uiLabelMap.CommonDescription/>

<#--
</@fieldset>
-->
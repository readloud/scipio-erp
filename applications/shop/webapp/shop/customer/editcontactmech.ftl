<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->
<#include "component://shop/webapp/shop/customer/customercommon.ftl">

<#assign requireCreate = false>
<#if canNotView>
  <@commonMsg type="error-perm">${uiLabelMap.PartyContactInfoNotBelongToYou}.</@commonMsg>
  <@menu type="button">
    <@menuitem type="link" href=makePageUrl(donePage) class="+${styles.action_nav!} ${styles.action_cancel!}" text=uiLabelMap.CommonGoBack />
  </@menu>
<#else>
  <#if !contactMech??>
    <#-- When creating a new contact mech, first select the type, then actually create -->
    <#if !requestParameters.preContactMechTypeId?? && !preContactMechTypeId??>
      <#assign requireCreate = true>
      <@section title=uiLabelMap.PartyCreateNewContactInfo>
        <form method="post" action="<@pageUrl>editcontactmechnosave?DONE_PAGE=${donePage}</@pageUrl>" name="createcontactmechform">
            <@field type="select" label=uiLabelMap.PartySelectContactType name="preContactMechTypeId">
              <#list contactMechTypes as contactMechType>
                <option value="${contactMechType.contactMechTypeId}">${contactMechType.get("description",locale)}</option>
              </#list>
            </@field>
          <#-- SCIPIO: make it part of menu further below, otherwise looks strange
            <@field type="submit" submitType="link" href="javascript:document.createcontactmechform.submit()" class="${styles.link_run_sys!} ${styles.action_add!}" text=uiLabelMap.CommonCreate />
          -->
        </form>
      <#--<@commonMsg type="error">ERROR: Contact information with ID "${contactMechId}" not found!</@commonMsg>-->
      </@section>
    </#if>
  </#if>

<#-- SCIPIO: If we are editing an address currently selected for shipping in cart, show this warning.
    This is mostly needed for checkout payment, but we'll just show for all cases to be safe. -->
<#if contactMech??>
  <#-- SCIPIO: Must use context or accessor
  <#if !cart?? && sessionAttributes.shoppingCart??>
    <#assign cart = sessionAttributes.shoppingCart>
  </#if>-->
  <#assign cart = cart!getShoppingCart()!false>
  <#if !cart?is_boolean && cart.getAllShippingContactMechId()?seq_contains(contactMech.contactMechId)>
    <@commonMsg type="warning">${uiLabelMap.CommonWarning}: ${uiLabelMap.ShopEditingShipAddressShipCostChange}</@commonMsg>
  </#if>
</#if>

<#-- SCIPIO: This was a message to explain to "Go Back" kludge; however I have now recoded controller and screen
    to redirect automatically.
<@commonMsg type="info-important">${uiLabelMap.ShopSaveGoBackExplanation}</@commonMsg>-->

  <#if contactMechTypeId??>
  
<#macro menuContent menuArgs={}>
  <@menu args=menuArgs>
    <@menuitem type="link" href=makePageUrl(donePage) class="+${styles.action_nav!} ${styles.action_cancel!}" text=uiLabelMap.CommonGoBack />
    <@menuitem type="link" href="javascript:document.editcontactmechform.submit()" class="+${styles.action_run_sys!} ${styles.action_update!}" text=uiLabelMap.CommonSave />
  </@menu>
</#macro>
<#if !contactMech??>
  <#assign sectionTitle = uiLabelMap.PartyCreateNewContactInfo>
<#else>
  <#-- SCIPIO: duplicate: <#assign sectionTitle = uiLabelMap.PartyEditContactInfo>-->
  <#assign sectionTitle = "">
</#if>
<@section title=sectionTitle menuContent=menuContent menuLayoutGeneral="bottom">
    
    <#if contactMech??>
        <@field type="generic" label=uiLabelMap.PartyContactPurposes>
          <@fields type="default-manual-widgetonly" ignoreParentField=true>
            <@table type="data-complex">
              <#list (partyContactMechPurposes!) as partyContactMechPurpose>
                <#assign contactMechPurposeType = partyContactMechPurpose.getRelatedOne("ContactMechPurposeType", true) />
                <@tr>
                  <@td>
                    <#if contactMechPurposeType??>
                      ${contactMechPurposeType.get("description",locale)}
                    <#else>
                      ${uiLabelMap.PartyPurposeTypeNotFound}: "${partyContactMechPurpose.contactMechPurposeTypeId}"
                    </#if>
                     (${uiLabelMap.CommonSince}: ${partyContactMechPurpose.fromDate.toString()})
                    <#if partyContactMechPurpose.thruDate??>(${uiLabelMap.CommonExpires}:${partyContactMechPurpose.thruDate.toString()})</#if>
                  </@td>
                  <@td>
                      <#-- SCIPIO: 2017-10-10: formerly this was: name="deletePartyContactMechPurpose_${partyContactMechPurpose.contactMechPurposeTypeId}"
                          but some states may cause duplicate purpose records, and then can't delete them, so must use index instead -->
                      <form name="deletePartyContactMechPurpose_${partyContactMechPurpose_index}" method="post" action="<@pageUrl>deletePartyContactMechPurpose?DONE_PAGE=${donePage}</@pageUrl>">
                          <input type="hidden" name="contactMechId" value="${contactMechId}"/>
                          <input type="hidden" name="contactMechPurposeTypeId" value="${partyContactMechPurpose.contactMechPurposeTypeId}"/>
                          <input type="hidden" name="fromDate" value="${partyContactMechPurpose.fromDate}"/>
                          <input type="hidden" name="useValues" value="true"/>
                          <@field type="submit" submitType="link" href="javascript:document.deletePartyContactMechPurpose_${partyContactMechPurpose_index}.submit()" class="${styles.link_run_sys!} ${styles.action_remove!}" text=uiLabelMap.CommonDelete /></a>
                      </form> 
                  </@td>
                </@tr>
              </#list>
              <#if purposeTypes?has_content>
                <@tr>
                  <@td>
                    <#-- for this, always forward back to current page -->
                    <form method="post" action="<@pageUrl>createPartyContactMechPurpose?DONE_PAGE=${donePage}&amp;TARGET_PAGE=editcontactmech&amp;targetPageResponse=forward-target</@pageUrl>" name="newpurposeform">
                      <input type="hidden" name="contactMechId" value="${contactMechId}"/>
                      <input type="hidden" name="useValues" value="true"/>
                        <@field type="select" name="contactMechPurposeTypeId">
                          <option></option>
                          <#list purposeTypes as contactMechPurposeType>
                            <option value="${contactMechPurposeType.contactMechPurposeTypeId}">${contactMechPurposeType.get("description",locale)}</option>
                          </#list>
                        </@field>
                    </form>
                  </@td>
                  <@td><@field type="submit" submitType="link" href="javascript:document.newpurposeform.submit()" class="${styles.link_run_sys!} ${styles.action_add!}" text=uiLabelMap.PartyAddPurpose /></@td>
                </@tr>
              </#if>
            </@table>
          </@fields>
        </@field>
    </#if>
    
  <#-- SCIPIO: NOTE: The target depends on whether creating or updating. When creating, want to return, whereas updating want to get redirected to donepage. -->  
  <#if contactMech??>
    <#assign targetParamStr>&amp;targetPageResponse=redirect-done</#assign>
  <#else>
    <#assign targetParamStr>&amp;TARGET_PAGE=editcontactmech&amp;targetPageResponse=forward-target</#assign>
  </#if>
  <form method="post" action="<@pageUrl>${reqName}?DONE_PAGE=${donePage}${targetParamStr}</@pageUrl>" name="editcontactmechform" id="editcontactmechform">
    
    <#if !contactMech??>
      <input type="hidden" name="contactMechTypeId" value="${contactMechTypeId}" />
      <#if contactMechPurposeType??>
        <p>(${uiLabelMap.PartyNewContactHavePurpose} "${contactMechPurposeType.get("description",locale)!}")</p>
      </#if>
      <#if cmNewPurposeTypeId?has_content><input type="hidden" name="contactMechPurposeTypeId" value="${cmNewPurposeTypeId}" /></#if>
      <#if preContactMechTypeId?has_content><input type="hidden" name="preContactMechTypeId" value="${preContactMechTypeId}" /></#if>
      <#if paymentMethodId?has_content><input type="hidden" name="paymentMethodId" value="${paymentMethodId}" /></#if>
    <#else>
      <input type="hidden" name="contactMechId" value="${contactMechId}" />
      <input type="hidden" name="contactMechTypeId" value="${contactMechTypeId}" />
    </#if>

    <#if contactMechTypeId == "POSTAL_ADDRESS">
      <#-- SCIPIO: Delegated -->
        <@render resource="component://shop/widget/CustomerScreens.xml#postalAddressFields" 
            ctxVars={
              "pafFieldNamePrefix":"",
              "pafUseScripts":false, <#-- NOTE: false because another script takes care of it here for us --> 
              "pafFieldIdPrefix":"editcontactmechform_"
              }/>
    <#elseif contactMechTypeId == "TELECOM_NUMBER">
      <@telecomNumberField label=uiLabelMap.PartyPhoneNumber countryCode=((telecomNumberData.countryCode)!) areaCode=((telecomNumberData.areaCode)!) 
        contactNumber=((telecomNumberData.contactNumber)!) extension=((partyContactMechData.extension)!) />
      <#-- SCIPIO: use tooltips
      <@field type="display">
          [${uiLabelMap.CommonCountryCode}] [${uiLabelMap.PartyAreaCode}] [${uiLabelMap.PartyContactNumber}] [${uiLabelMap.PartyExtension}]
      </@field>
      -->
    <#elseif contactMechTypeId == "EMAIL_ADDRESS">
      <#if tryEntity>
        <#assign fieldValue = contactMech.infoString!>
      <#else>
        <#assign fieldValue = requestParameters.emailAddress!>
      </#if>
      <@field type="input" label=uiLabelMap.PartyEmailAddress required=true size="60" maxlength="255" name="emailAddress" value=fieldValue />
    <#else>
      <@field type="input" label=(contactMechType.get("description",locale)!) required=true size="60" maxlength="255" name="infoString" value=(contactMechData.infoString!) />
    </#if>
      <@allowSolicitationField name="allowSolicitation" allowSolicitation=((partyContactMechData.allowSolicitation)!"") />
  </form>
</@section>

  <#else>    
    <@menu type="button">
      <@menuitem type="link" href=makePageUrl(donePage) class="+${styles.action_nav!} ${styles.action_cancel!}" text=uiLabelMap.CommonGoBack />
    <#if requireCreate>
      <@menuitem type="link" href="javascript:document.createcontactmechform.submit()" class="+${styles.action_run_sys!} ${styles.action_add!}" text=uiLabelMap.CommonCreate />
    </#if>
    </@menu>
  </#if>
</#if>

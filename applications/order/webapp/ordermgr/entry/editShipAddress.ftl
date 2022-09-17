<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->

<#if security.hasEntityPermission("ORDERMGR", "_CREATE", request) || security.hasEntityPermission("ORDERMGR", "_PURCHASE_CREATE", request)>

  <@section>
    <#if postalAddress?has_content>
      <form method="post" action="<@pageUrl>updatePostalAddressOrderEntry</@pageUrl>" name="checkoutsetupform" id="checkoutsetupform">
        <input type="hidden" name="contactMechId" value="${shipContactMechId!}"/>
    <#else>
      <form method="post" action="<@pageUrl>createPostalAddress</@pageUrl>" name="checkoutsetupform" id="checkoutsetupform">
        <input type="hidden" name="contactMechTypeId" value="POSTAL_ADDRESS"/>
        <input type="hidden" name="contactMechPurposeTypeId" value="SHIPPING_LOCATION"/>
    </#if>
        <input type="hidden" name="partyId" value="${cart.getPartyId()?default("_NA_")}"/>
        <input type="hidden" name="finalizeMode" value="ship"/>
        <#if orderPerson?? && orderPerson?has_content>
          <#assign toName = "">
          <#if orderPerson.personalTitle?has_content><#assign toName = orderPerson.personalTitle + " "></#if>
          <#assign toName = toName + orderPerson.firstName + " ">
          <#if orderPerson.middleName?has_content><#assign toName = toName + orderPerson.middleName + " "></#if>
          <#assign toName = toName + orderPerson.lastName>
          <#if orderPerson.suffix?has_content><#assign toName = toName + " " + orderPerson.suffix></#if>
        <#elseif parameters.toName??>
          <#assign toName = parameters.toName>
        <#else>
          <#assign toName = "">
        </#if>
        <@field type="input" label=uiLabelMap.CommonToName size="30" maxlength="60" name="toName" value=toName/>
        <@field type="input" label=uiLabelMap.CommonAttentionName size="30" maxlength="60" name="attnName" value=(parameters.attnName!)/>
        <@field type="input" label="${rawLabel('CommonAddressLine')} 1" required=true size="30" maxlength="30" name="address1" value=(parameters.address1!)/>
        <@field type="input" label="${rawLabel('CommonAddressLine')} 2" size="30" maxlength="30" name="address2" value=(parameters.address2!)/>
        <@field type="input" label=uiLabelMap.CommonCity required=true size="30" maxlength="30" name="city" value=(parameters.city!)/>
        <@field type="select" label=uiLabelMap.CommonStateProvince name="stateProvinceGeoId" id="checkoutsetupform_stateProvinceGeoId">
              <#if parameters.stateProvinceGeoId?has_content>
                <option value="${parameters.stateProvinceGeoId}" selected="selected">${parameters.stateProvinceGeoId}</option>
              </#if>
        </@field>
        <@field type="input" label=uiLabelMap.CommonZipPostalCode required=true size="12" maxlength="10" name="postalCode" value=(parameters.postalCode!)/>
        <@field type="select" label=uiLabelMap.CommonCountry required=true name="countryGeoId" id="checkoutsetupform_countryGeoId">
              <@render resource="component://common/widget/CommonScreens.xml#countries" ctxVars={"currentCountryGeoId":(parameters.countryGeoId!)}/>
        </@field>
        <@field type="select" label=uiLabelMap.OrderAllowSolicitation name="allowSolicitation">
              <#assign selectedValue = parameters.allowSolicitation?default("")/>
              <option></option><option${(selectedValue=="Y")?string(" selected=\"selected\"","")}>Y</option><option${(selectedValue=="N")?string(" selected=\"selected\"","")}>N</option>
        </@field>
      </form>
  </@section>
<#else>
  <@commonMsg type="error">${uiLabelMap.OrderViewPermissionError}</@commonMsg>
</#if>

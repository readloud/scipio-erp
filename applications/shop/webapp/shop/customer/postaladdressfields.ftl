<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->
<#include "component://shop/webapp/shop/customer/customercommon.ftl">

<#-- SCIPIO: TODO?: Review if can merge with order/genericaddress.ftl -->

<#-- SCIPIO: migrated from editcontactmech.ftl -->

<#if !pafParams??>
  <#assign pafParams = parameters>
</#if>


<#if pafParams["${fieldNamePrefix}stateProvinceGeoId"]??>    
  <#assign defaultStateProvinceGeoId = pafParams["${fieldNamePrefix}stateProvinceGeoId"]>
<#elseif (postalAddress??) && (postalAddress.stateProvinceGeoId??)>
  <#assign defaultStateProvinceGeoId = postalAddress.stateProvinceGeoId>
<#elseif (pafFallbacks.stateProvinceGeoId)??>
  <#assign defaultStateProvinceGeoId = pafFallbacks.stateProvinceGeoId>
<#else>
  <#assign defaultStateProvinceGeoId = "">
</#if>

<#if useScripts>

<@script>

jQuery(document).ready(function() {

    <#assign fieldIdPrefixJs = escapeVal(fieldIdPrefix, 'js')>

    <#-- SCIPIO: NOTE: the container IDs can be omitted because the js doesn't make proper use of them anyhow -->
    <#-- TODO?: getAssociatedStateList may be out of date compared to getDependentDropdownValues?  -->
    var errorMsgContainerId = null;
    var containerId = null;
    jQuery("#${fieldIdPrefixJs}countryGeoId").change(function() {
        getAssociatedStateList('${fieldIdPrefixJs}countryGeoId', '${fieldIdPrefixJs}stateProvinceGeoId', errorMsgContainerId, containerId);
    });
    getAssociatedStateList('${fieldIdPrefixJs}countryGeoId', '${fieldIdPrefixJs}stateProvinceGeoId', errorMsgContainerId, containerId);
    
});

</@script>

</#if>

  <@field type="input" label=uiLabelMap.PartyToName size="30" maxlength="60" name="${fieldNamePrefix}toName" value=(pafParams["${fieldNamePrefix}toName"]!(postalAddressData.toName)!(pafFallbacks.toName)!) />
  <@field type="input" label=uiLabelMap.PartyAttentionName size="30" maxlength="60" name="${fieldNamePrefix}attnName" value=(pafParams["${fieldNamePrefix}attnName"]!(postalAddressData.attnName)!(pafFallbacks.attnName)!) containerClass="+${styles.field_extra!}"/>
  <@field type="input" label=uiLabelMap.PartyAddressLine1 required=true size="30" maxlength="30" name="${fieldNamePrefix}address1" value=(pafParams["${fieldNamePrefix}address1"]!(postalAddressData.address1)!(pafFallbacks.address1)!) />
  <@field type="input" label=uiLabelMap.PartyAddressLine2 size="30" maxlength="30" name="${fieldNamePrefix}address2" value=(pafParams["${fieldNamePrefix}address2"]!(postalAddressData.address2)!(pafFallbacks.address2)!) />
  <@field type="input" label=uiLabelMap.PartyCity required=true size="30" maxlength="30" name="${fieldNamePrefix}city" value=(pafParams["${fieldNamePrefix}city"]!(postalAddressData.city)!(pafFallbacks.city)!) />    
  <@field type="input" label=uiLabelMap.PartyZipCode required=true size="12" maxlength="10" name="${fieldNamePrefix}postalCode" value=(pafParams["${fieldNamePrefix}postalCode"]!(postalAddressData.postalCode)!(pafFallbacks.postalCode)!) />
  <@field type="select" label=uiLabelMap.CommonCountry name="${fieldNamePrefix}countryGeoId" id="${fieldIdPrefix}countryGeoId">
      <#if pafParams["${fieldNamePrefix}countryGeoId"]??>    
        <#assign currentCountryGeoId = pafParams["${fieldNamePrefix}countryGeoId"]>
      <#elseif (postalAddress??) && (postalAddress.countryGeoId??)>
        <#assign currentCountryGeoId = postalAddress.countryGeoId>
      <#elseif (pafFallbacks.countryGeoId)??>
        <#assign currentCountryGeoId = pafFallbacks.countryGeoId>
      <#else>
        <#-- redundant done
        <#assign currentCountryGeoId = getPropertyValue("general", "country.geo.id.default")!"">-->
        <#assign currentCountryGeoId = "">
      </#if>
    <#-- SCIPIO: there's no reason for this; allow countries ftl to select the right one
      <option selected="selected" value="${currentCountryGeoId}">
      <#assign countryGeo = delegator.findOne("Geo",{"geoId":currentCountryGeoId}, false)>
        ${countryGeo.get("geoName",locale)}
      </option>
      <option></option>
    -->
      <@render resource="component://common/widget/CommonScreens.xml#countries" ctxVars={"currentCountryGeoId":currentCountryGeoId}/>   
  </@field>
  <@field type="select" label=uiLabelMap.PartyState name="${fieldNamePrefix}stateProvinceGeoId" id="${fieldIdPrefix}stateProvinceGeoId">
    <#-- SCIPIO: NOTE: This was empty in stock; supposed to load via JS; for now, put the current if this is empty -->
    <#if defaultStateProvinceGeoId?has_content>
      <option value="${defaultStateProvinceGeoId}">${defaultStateProvinceGeoId}</option>
    </#if>
  </@field>


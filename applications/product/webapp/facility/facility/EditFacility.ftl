<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->

<#if facility?? && facilityId?has_content>
  <form action="<@pageUrl>UpdateFacility</@pageUrl>" name="EditFacilityForm" method="post" class="basic-form">
  <input type="hidden" name="facilityId" value="${facilityId!}" />
<#else>
  <form action="<@pageUrl>CreateFacility</@pageUrl>" name="EditFacilityForm" method="post" class="basic-form">
  <#if facilityId??>
    <@commonMsg type="error">${uiLabelMap.ProductCouldNotFindFacilityWithId} "${facilityId!}".</@commonMsg>
  </#if>
</#if>

<#if facility?? && facilityId?has_content>
  <@field type="display" label=uiLabelMap.ProductFacilityId tooltip=uiLabelMap.ProductNotModificationRecrationFacility>
      ${facilityId!}
  </@field>
</#if>

  <@field type="select" label=uiLabelMap.ProductFacilityTypeId name="facilityTypeId">
        <option selected="selected" value="${facilityType.facilityTypeId!}">${facilityType.get("description",locale)!}</option>
        <option value="${facilityType.facilityTypeId!}">----</option>
        <#list facilityTypes as nextFacilityType>
          <option value="${nextFacilityType.facilityTypeId!}">${nextFacilityType.get("description",locale)!}</option>
        </#list>

  </@field>
  <@field type="lookup" label=uiLabelMap.FormFieldTitle_parentFacilityId value=(facility.parentFacilityId!) formName="EditFacilityForm" name="parentFacilityId" id="parentFacilityId" fieldFormName="LookupFacility"/>
  <@field type="lookup" label=uiLabelMap.ProductFacilityOwner required=true value=(facility.ownerPartyId!) formName="EditFacilityForm" name="ownerPartyId" id="ownerPartyId" fieldFormName="LookupPartyName"/>
  <@field type="select" label=uiLabelMap.ProductFacilityDefaultWeightUnit name="defaultWeightUomId">
          <option value="">${uiLabelMap.CommonNone}</option>
          <#list weightUomList as uom>
            <option value="${uom.uomId}"
               <#if (facility.defaultWeightUomId?has_content) && (uom.uomId == facility.defaultWeightUomId)>
               selected="selected"
               </#if>
             >${uom.get("description",locale)?default(uom.uomId)} (${uom.abbreviation!})</option>
          </#list>
  </@field>
  <@field type="select" label=uiLabelMap.ProductFacilityDefaultInventoryItemType name="defaultInventoryItemTypeId">
          <#list inventoryItemTypes as nextInventoryItemType>
            <option value="${nextInventoryItemType.inventoryItemTypeId}"
               <#if (facility.defaultInventoryItemTypeId?has_content) && (nextInventoryItemType.inventoryItemTypeId == facility.defaultInventoryItemTypeId)>
               selected="selected"
               </#if>
             >${nextInventoryItemType.get("description",locale)?default(nextInventoryItemType.inventoryItemTypeId)}</option>
          </#list>
  </@field>
  <@field type="input" label=uiLabelMap.ProductName required=true name="facilityName" value=(facility.facilityName!) size="30" maxlength="60" />
  <@field type="input" label=uiLabelMap.ProductFacilitySize name="facilitySize" value=(facility.facilitySize!) size="10" maxlength="20" />
  <@field type="select" label=uiLabelMap.ProductFacilityDefaultAreaUnit name="facilitySizeUomId">
          <option value="">${uiLabelMap.CommonNone}</option>
          <#list areaUomList as uom>
            <option value="${uom.uomId}"
               <#if (facility.facilitySizeUomId?has_content) && (uom.uomId == facility.facilitySizeUomId)>
               selected="selected"
               </#if>
             >${uom.get("description",locale)?default(uom.uomId)} (${uom.abbreviation!})</option>
          </#list>
  </@field>  
  <@field type="input" label=uiLabelMap.ProductProductDescription name="description" value=(facility.description!) size="60" maxlength="250" />
  <@field type="input" label=uiLabelMap.ProductDefaultDaysToShip name="defaultDaysToShip" value=(facility.defaultDaysToShip!) size="10" maxlength="20" />

  <#if facilityId?has_content>
    <@field type="submit" name="Update" text=uiLabelMap.CommonUpdate class="+${styles.link_run_sys!} ${styles.action_update!}" />
  <#else>
    <@field type="submit" name="Update" text=uiLabelMap.CommonSave class="+${styles.link_run_sys!} ${styles.action_add!}" />
  </#if>

</form>

<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->

<@section title="${rawLabel('SfaMergingFollowing')} ${rawLabel('SfaMergeLeads')}">
  <form method="post" action="<@pageUrl>mergeContacts</@pageUrl>" class="basic-form">
  <@fields type="default-manual">
    <#if contactInfoList?has_content >
      <#assign contactInfo1 = contactInfoList[0]/>
      <#assign contactInfo2 = contactInfoList[1]/>
      <input type="hidden" name="partyIdTo" value="${contactInfo1.partyId!}" />
      <input type="hidden" name="partyId" value="${contactInfo2.partyId!}" />

      <input type="hidden" name="addrContactMechIdTo" value="${contactInfo1.addrContactMechId!}" />
      <input type="hidden" name="phoneContactMechIdTo" value="${contactInfo1.phoneContactMechId!}" />
      <input type="hidden" name="emailContactMechIdTo" value="${contactInfo1.emailContactMechId!}" />

      <input type="hidden" name="addrContactMechId" value="${contactInfo2.addrContactMechId!}" />
      <input type="hidden" name="phoneContactMechId" value="${contactInfo2.phoneContactMechId!}" />
      <input type="hidden" name="emailContactMechId" value="${contactInfo2.emailContactMechId!}" />

      <@table type="fields" class="+${styles.table_spacing_tiny_hint!}">
        <@tr>
          <@td width="20%"></@td>
          <@td width="30%"><@heading>${uiLabelMap.SfaFirstContact}</@heading></@td>
          <@td width="30%"><@heading>${uiLabelMap.SfaSecondContact}</@heading></@td>
          <@td width="20%"><@heading>${uiLabelMap.CommonSelect}</@heading></@td>
        </@tr>
        <@tr>
          <@td width="20%">${uiLabelMap.PartyFirstName}</@td>
          <@td width="30%"><@heading relLevel=1>${contactInfo1.firstName!}</@heading></@td>
          <@td width="30%"><@heading relLevel=1>${contactInfo2.firstName!}</@heading></@td>
          <@td width="20%"></@td>
        </@tr>
        <@tr>
          <@td width="20%">${uiLabelMap.PartyLastName}</@td>
          <@td width="30%"><@heading relLevel=1>${contactInfo1.lastName!}</@heading></@td>
          <@td width="30%"><@heading relLevel=1>${contactInfo2.lastName!}</@heading></@td>
          <@td width="20%"></@td>
        </@tr>
        <@tr>
          <@td width="30"><@heading relLevel=1>${uiLabelMap.PartyGeneralCorrespondenceAddress}</@heading></@td>
          <@td width="30"></@td>
        </@tr>
        <@tr>
          <@td width="20%">${uiLabelMap.PartyAddressLine1}</@td>
          <@td width="30%">${contactInfo1.address1!}</@td>
          <@td width="30%">${contactInfo2.address1!}</@td>
          <@td width="20%"><@field type="checkbox" name="useAddress2" value="Y"/></@td>
        </@tr>

        <@tr>
          <@td width="20%">${uiLabelMap.PartyAddressLine2}</@td>
          <@td width="30%">${contactInfo1.address2!}</@td>
          <@td width="30%">${contactInfo2.address2!}</@td>
          <@td width="20%"></@td>
        </@tr>

        <@tr>
          <@td width="20%">${uiLabelMap.PartyCity}</@td>
          <@td width="30%">${contactInfo1.city!}</@td>
          <@td width="30%">${contactInfo2.city!}</@td>
          <@td width="20%"></@td>
        </@tr>
        <@tr>
          <@td width="20%">${uiLabelMap.PartyState}</@td>
          <@td width="30%">${contactInfo1.state!}</@td>
          <@td width="30%">${contactInfo2.state!}</@td>
          <@td width="20%"></@td>
        </@tr>
        <@tr>
          <@td width="20%">${uiLabelMap.PartyZipCode}</@td>
          <@td width="30%">${contactInfo1.postalCode!}</@td>
          <@td width="30%">${contactInfo2.postalCode!}</@td>
          <@td width="20%"></@td>
        </@tr>
        <@tr>
          <@td width="20%">${uiLabelMap.CommonCountry}</@td>
          <@td width="30%">${contactInfo1.country!}</@td>
          <@td width="30%">${contactInfo2.country!}</@td>
          <@td width="20%"></@td>
        </@tr>
        <@tr><@td><@heading relLevel=1>${uiLabelMap.PartyPrimaryPhone}</@heading></@td></@tr>
        <@tr>
          <@td width="20%">${uiLabelMap.CommonCountryCode}</@td>
          <@td width="30%">${contactInfo1.countryCode!}</@td>
          <@td width="30%">${contactInfo2.countryCode!}</@td>
          <@td width="10%"><@field type="checkbox" name="useContactNum2" value="Y"/></@td>
        </@tr>
        <@tr>
          <@td width="20%">${uiLabelMap.PartyAreaCode}</@td>
          <@td width="30%">${contactInfo1.areaCode!}</@td>
          <@td width="30%">${contactInfo2.areaCode!}</@td>
          <@td width="20%"></@td>
        </@tr>
        <@tr>
          <@td width="20%">${uiLabelMap.PartyPhoneNumber}</@td>
          <@td width="30%">${contactInfo1.contactNumber!}</@td>
          <@td width="30%">${contactInfo2.contactNumber!}</@td>
          <@td width="20%"></@td>
        </@tr>
        <@tr>
          <@td width="20%">${uiLabelMap.PartyEmailAddress}</@td>
          <@td width="30%">${contactInfo1.primaryEmail!}</@td>
          <@td width="30%">${contactInfo2.primaryEmail!}</@td>
          <@td width="10%"><@field type="checkbox" name="useEmail2" value="Y"/></@td>
        </@tr>
      </@table>
      <@field type="submit" text=uiLabelMap.CommonSubmit class="+${styles.link_run_sys!} ${styles.action_update!}"/>
    <#else>
      <@commonMsg type="warning">${uiLabelMap.SfaNoLeadsSelectedToMerged}</@commonMsg>
    </#if>
  </@fields>
  </form>
</@section>
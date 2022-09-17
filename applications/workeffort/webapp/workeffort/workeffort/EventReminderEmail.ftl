<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->
<#assign docLangAttr = locale.toString()?replace("_", "-")>
<#assign langDir = "ltr">
<#if "ar.iw"?contains(docLangAttr?substring(0, 2))>
    <#assign langDir = "rtl">
</#if>
<html lang="${docLangAttr}" dir="${langDir}" xmlns="http://www.w3.org/1999/xhtml">
  <head/>
  <body>
    <style type="text/css">
    .label {
      font-weight: bold;
    <#if langDir == "ltr">
      padding-right: 10px;
      text-align: right;
    <#else>
      padding-left: 10px;
      text-align: left;
    </#if>
    }
    div {
      padding: 10px 0 10px 0;
    }
    </style>
    <@table type="fields">
      <#-- Work Effort Info -->
      <@tr><@td>${uiLabelMap.CommonDate}</@td><@td>${parameters.eventDateTime?default("&nbsp;")}</@td></@tr>
      <@tr><@td>${uiLabelMap.CommonName}</@td><@td>${workEffort.workEffortName?default("&nbsp;")}</@td></@tr>
      <@tr><@td>${uiLabelMap.CommonDescription}</@td><@td>${workEffort.description?default("&nbsp;")}</@td></@tr>
      <@tr><@td>${uiLabelMap.CommonType}</@td><@td>${(workEffortType.description)?default("&nbsp;")}</@td></@tr>
      <@tr><@td>${uiLabelMap.CommonPurpose}</@td><@td>${(workEffortPurposeType.description)?default("&nbsp;")}</@td></@tr>
      <@tr><@td>${uiLabelMap.CommonStatus}</@td><@td>${(currentStatusItem.description)?default("&nbsp;")}</@td></@tr>
      <@tr type="util"><@td colspan="2"><hr /></@td></@tr>
    </@table>
    <#if partyAssignments?has_content>
      <div><b>${uiLabelMap.PageTitleListWorkEffortPartyAssigns}</b></div>
      <@table type="data-list" class="+${styles.table_spacing_small_hint!}">
        <@thead><@tr>
          <@th>${uiLabelMap.PartyParty}</@th>
          <@th>${uiLabelMap.PartyRole}</@th>
          <@th>${uiLabelMap.CommonFromDate}</@th>
          <@th>${uiLabelMap.CommonThruDate}</@th>
          <@th>${uiLabelMap.CommonStatus}</@th>
          <@th>${uiLabelMap.WorkEffortDelegateReason}</@th>
        </@tr></@thead>
        <@tbody>
          <#list partyAssignments as wepa>
            <@tr>
              <@td>${wepa.groupName!}${wepa.firstName!} ${wepa.lastName!}</@td>
              <@td>${(wepa.getRelatedOne("RoleType", false).description)?default("&nbsp;")}</@td>
              <@td>${wepa.fromDate?default("&nbsp;")}</@td>
              <@td>${wepa.thruDate?default("&nbsp;")}</@td>
              <@td>${(wepa.getRelatedOne("AssignmentStatusItem", false).description)?default("&nbsp;")}</@td>
              <@td>${(wepa.getRelatedOne("DelegateReasonEnumeration", false).description)?default("&nbsp;")}</@td>
            </@tr>
          </#list>
        </@tbody>
      </@table>
    </#if>
    <#if fixedAssetAssignments?has_content>
      <div><b>${uiLabelMap.PageTitleListWorkEffortFixedAssetAssigns}</b></div>
      <@table type="data-list" class="+${styles.table_spacing_tiny_hint!}">
        <@thead><@tr>
          <@th>${uiLabelMap.AccountingFixedAsset}</@th>
          <@th>${uiLabelMap.CommonFromDate}</@th>
          <@th>${uiLabelMap.CommonThruDate}</@th>
          <@th>${uiLabelMap.CommonStatus}</@th>
          <@th>${uiLabelMap.FormFieldTitle_availabilityStatusId}</@th>
          <@th>${uiLabelMap.FormFieldTitle_allocatedCost}</@th>
          <@th>${uiLabelMap.CommonComments}</@th>
        </@tr></@thead>
        <@tbody>
          <#list fixedAssetAssignments as wefa>
            <@tr>
              <@td>${wefa.fixedAssetName?default("&nbsp;")}</@td>
              <@td>${wefa.fromDate?default("&nbsp;")}</@td>
              <@td>${wefa.thruDate?default("&nbsp;")}</@td>
              <@td>${(wefa.getRelatedOne("StatusItem", false).description)?default("&nbsp;")}</@td>
              <@td>${(wefa.getRelatedOne("AvailabilityStatusItem", false).description)?default("&nbsp;")}</@td>
              <@td>${wefa.allocatedCost?default("&nbsp;")}</@td>
              <@td>${wefa.comments?default("&nbsp;")}</@td>
            </@tr>
          </#list>
        </@tbody>
      </@table>
    </#if>
  </body>
</html>

<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->
<#include "component://workeffort/webapp/workeffort/common/common.ftl">

<#macro menuContent menuArgs={}>
    <@workefflib.calendarDateSwitcher period="week"/>
</#macro>
<@section title="${rawLabel('CommonWeek')} ${raw(UtilDateTime.timeStampToString(start, 'w', timeZone, locale))}"
    menuContent=menuContent menuLayoutTitle="inline-title"><#--${uiLabelMap.WorkEffortWeekView}: -->

<#if periods?has_content>
  <#-- Allow containing screens to specify the URL for creating a new event -->
  <#if !newCalEventUrl??>
    <#assign newCalEventUrl = parameters._LAST_VIEW_NAME_>
  </#if>
  <#if (maxConcurrentEntries < 2)>
    <#assign entryWidth = 85>
  <#else>
    <#assign entryWidth = (85 / (maxConcurrentEntries))>
  </#if>
<div class="week-calendar-full">
<@table type="data-complex" class="+calendar" autoAltRows=true responsive=false>
 <@thead>
  <@tr class="header-row">
    <@th width="15%">${uiLabelMap.CommonDay}</@th>
    <@th colspan=maxConcurrentEntries>${uiLabelMap.WorkEffortCalendarEntries}</@th>
  </@tr>
  </@thead>
  <#list periods as period>
    <#assign currentPeriod = false/>
    <#if (nowTimestamp >= period.start) && (nowTimestamp <= period.end)><#assign currentPeriod = true/></#if>
  <#assign class><#if currentPeriod>current-period<#else><#if (period.calendarEntries?size > 0)>active-period</#if></#if></#assign>
  <@tr class=class>
    <@td width="15%">
      <#-- SCIPIO: FIXME: hardcoded to yyyy-MM-dd to be consistent with datepicker for now: period.start?date?string.short 
        however datepicker itself should not be hardcoded either -->
      <a href="<@pageUrl>${parameters._LAST_VIEW_NAME_}?period=day&amp;startTime=${period.start.time?string("#")}${urlParam!}${addlParam!}</@pageUrl>">${period.start?date?string("EEEE")?cap_first} ${period.start?date?string("yyyy-MM-dd")}</a><br />
      <a href="<@pageUrl>${newCalEventUrl}?period=week&amp;form=edit&amp;startTime=${parameters.start!}&amp;parentTypeId=${parentTypeId!}&amp;currentStatusId=CAL_TENTATIVE&amp;estimatedStartDate=${period.start?string("yyyy-MM-dd HH:mm:ss")}&amp;estimatedCompletionDate=${period.end?string("yyyy-MM-dd HH:mm:ss")}${addlParam!}${urlParam!}</@pageUrl>" class="${styles.link_nav_inline!} ${styles.action_add!}">[+]</a><#--${uiLabelMap.CommonAddNew}-->
    </@td>
    <#list period.calendarEntries as calEntry>
        <#if calEntry.workEffort.actualStartDate??>
            <#assign startDate = calEntry.workEffort.actualStartDate>
          <#else>
            <#assign startDate = calEntry.workEffort.estimatedStartDate!>
        </#if>

        <#if calEntry.workEffort.actualCompletionDate??>
            <#assign completionDate = calEntry.workEffort.actualCompletionDate>
          <#else>
            <#assign completionDate = calEntry.workEffort.estimatedCompletionDate!>
        </#if>

        <#if !completionDate?has_content && calEntry.workEffort.actualMilliSeconds?has_content>
            <#assign completionDate =  calEntry.workEffort.actualStartDate + calEntry.workEffort.actualMilliSeconds>
        </#if>    
        <#if !completionDate?has_content && calEntry.workEffort.estimatedMilliSeconds?has_content>
            <#assign completionDate =  calEntry.workEffort.estimatedStartDate + calEntry.workEffort.estimatedMilliSeconds>
        </#if>    
    
    <#if calEntry.startOfPeriod>
    <#assign rowSpan><#if (calEntry.periodSpan > 1)>${calEntry.periodSpan}</#if></#assign>
    <#assign width>${entryWidth?string("#")}%</#assign>
    <@td rowspan=rowSpan width=width class="+week-entry-event">
    <#if (startDate.compareTo(period.start) <= 0 && completionDate?has_content && completionDate.compareTo(period.end) >= 0)>
      ${uiLabelMap.CommonAllWeek}
    <#elseif (startDate.compareTo(period.start) == 0 && completionDate?has_content && completionDate.compareTo(period.end) == 0)>
      ${uiLabelMap.CommonAllDay}
    <#elseif startDate.before(start) && completionDate?has_content>
      ${uiLabelMap.CommonUntil} ${completionDate?datetime?string.short}
    <#elseif !completionDate?has_content>
      ${uiLabelMap.CommonFrom} ${startDate?time?string.short} - ?
    <#elseif completionDate.after(period.end)>
      ${uiLabelMap.CommonFrom} ${startDate?time?string.short}
    <#else>
      ${startDate?time?string.short}-${completionDate?time?string.short}
    </#if>
    <br />
    <@render resource="component://workeffort/widget/CalendarScreens.xml#calendarEventContent" 
        reqAttribs={"periodType":"week", "workEffortId":calEntry.workEffort.workEffortId}
        restoreValues=true asString=true/>
    </@td>  
    </#if>
    </#list>
    <#if (period.calendarEntries?size < maxConcurrentEntries)>
      <#assign emptySlots = (maxConcurrentEntries - period.calendarEntries?size)>
        <#assign colspan><#if (emptySlots > 1)>${emptySlots}</#if></#assign>
        <@td colspan=colspan>&nbsp;</@td>
    </#if>
    <#if maxConcurrentEntries == 0>
      <#assign width>${entryWidth?string("#")}%</#assign>
      <@td width=width>&nbsp;</@td>
    </#if>
  </@tr>
  </#list>
</@table>
</div>
<#else>
  <@commonMsg type="error">${uiLabelMap.WorkEffortFailedCalendarEntries}</@commonMsg>
</#if>

</@section>


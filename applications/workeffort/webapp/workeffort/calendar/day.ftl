<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->

<#include "component://workeffort/webapp/workeffort/common/common.ftl">

<#-- SCIPIO: FTL now includes the title -->
<#macro menuContent menuArgs={}>
    <@workefflib.calendarDateSwitcher period="day"/>
</#macro>
<@section title=UtilDateTime.timeStampToString(start, 'EEEE MMMM d, yyyy', timeZone, locale)
    menuContent=menuContent menuLayoutTitle="inline-title"><#--${uiLabelMap.WorkEffortDayView}: -->

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
<div class="day-calendar-full">
<@table type="data-complex" class="+calendar" 
    autoAltRows=true responsive=false>
 <@thead>
  <@tr class="header-row">
    <@th width="15%">${uiLabelMap.CommonTime}</@th>
    <@th colspan=maxConcurrentEntries>${uiLabelMap.WorkEffortCalendarEntries}</@th>
  </@tr>
  </@thead>
  <#list periods as period>
    <#assign currentPeriod = false/>
    <#if (nowTimestamp >= period.start) && (nowTimestamp <= period.end)><#assign currentPeriod = true/></#if>
  <#assign class><#if currentPeriod>current-period<#else><#if (period.calendarEntries?size > 0)>active-period</#if></#if></#assign>
  <@tr class=class>
    <@td width="15%">
      ${period.start?time?string.short}<br />
      <a href="<@pageUrl>${newCalEventUrl}?period=day&amp;form=edit&amp;parentTypeId=${parentTypeId!}&amp;startTime=${parameters.start!}&amp;currentStatusId=CAL_TENTATIVE&amp;estimatedStartDate=${period.start?string("yyyy-MM-dd HH:mm:ss")}&amp;estimatedCompletionDate=${period.end?string("yyyy-MM-dd HH:mm:ss")}${urlParam!}${addlParam!}</@pageUrl>" class="${styles.link_nav_inline!} ${styles.action_add!}">[+]</a><#--${uiLabelMap.CommonAddNew}-->
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
    <@td rowspan=rowSpan width=width class="+day-entry-event">
    <#if ((startDate.compareTo(start) <= 0) && completionDate?has_content && completionDate.compareTo(next) >= 0)>
      ${uiLabelMap.CommonAllDay}
    <#elseif startDate.before(start) && completionDate?has_content>
      ${uiLabelMap.CommonUntil} ${completionDate?time?string.short}
    <#elseif !completionDate?has_content>
      ${uiLabelMap.CommonFrom} ${startDate?time?string.short} - ?
    <#elseif completionDate.after(period.end)>
      ${uiLabelMap.CommonFrom} ${startDate?time?string.short}
    <#else>
      ${startDate?time?string.short}-${completionDate?time?string.short}
    </#if>
    <br />
    <@render resource="component://workeffort/widget/CalendarScreens.xml#calendarEventContent" 
        reqAttribs={"periodType":"day", "workEffortId":calEntry.workEffort.workEffortId} 
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

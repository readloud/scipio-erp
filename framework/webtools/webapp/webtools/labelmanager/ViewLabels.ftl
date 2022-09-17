<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->
<@section>
    <#if parameters.searchLabels??>
      <#assign rowNumber = 1>
      <#assign totalLabels = 0>
      <#assign missingLabels = 0>
      <#assign existingLabels = 0>
      <#assign previousKey = "">
      <#if localesFound??>
        <#assign totalLabels = localesFound?size>
      <#else>
        <#assign totalLabels = 0>
      </#if>
    <@row>
        <@cell>
        ${uiLabelMap.WebtoolsLabelStatsMissing}: ${existingLabels!}<br/>
        ${uiLabelMap.WebtoolsLabelStatsExist}: ${missingLabels!}<br/>
        ${uiLabelMap.WebtoolsLabelStatsTotal}: ${totalLabels}
        </@cell>
    </@row>
    </#if>
  <@table type="data-list" autoAltRows=true responsive=true>
   <@thead>
    <@tr class="header-row">
      <#--<@th>${uiLabelMap.WebtoolsLabelManagerRow}</@th>-->
      <@th>${uiLabelMap.WebtoolsLabelManagerKey}</@th>
      <@th>${uiLabelMap.WebtoolsLabelManagerFileName}</@th>
      <@th>${uiLabelMap.WebtoolsLabelManagerReferences}</@th>
      <#list localesFound as localeFound>
        <#assign showLocale = true>
        <#if parameters.labelLocaleName?? && parameters.labelLocaleName != "" && parameters.labelLocaleName != localeFound>
          <#assign showLocale = false>
        </#if>
        <#if showLocale == true>
          <#assign locale = UtilMisc.parseLocale(localeFound)!/>
          <#if locale?? && locale?has_content>
            <#assign langAttr = localeFound.toString()?replace("_", "-")>
            <#assign langDir = "ltr">
            <#if "ar.iw"?contains(langAttr?substring(0, 2))>
              <#assign langDir = "rtl">
            </#if>
            <@th lang=langAttr dir=langDir>
              ${locale.getDisplayName(locale)}
            </@th>
          <#else>
            <@th>${localeFound}</@th>
          </#if>
        </#if>
      </#list>
    </@tr>
    </@thead>
    <#--
    <@tfoot>
          <@tr class="header-row">
            <@th></@th>
            <@th colspan=(localesFound?length+2)>
              ${uiLabelMap.WebtoolsLabelStatsMissing}: ${existingLabels!}<br/>
              ${uiLabelMap.WebtoolsLabelStatsExist}: ${missingLabels!}<br/>
              ${uiLabelMap.WebtoolsLabelStatsTotal}: ${totalLabels}
            </@th>
          </@tr>
      </@tfoot>-->
      
      <#-- SCIPIO: support simple wildcard regexp -->
      <#assign labelKeyRegex = raw(parameters.labelKeyRegex!)>
      
      <#list labelsList as labelList>
        <#assign label = labels.get(labelList)>
        <#assign labelKey = label.labelKey>
        <#assign labelKeyRaw = raw(labelKey)><#-- SCIPIO -->
        <#assign totalLabels = totalLabels + 1>
        <#if references??>
          <#assign referenceNum = 0>
          <#assign reference = references.get(labelKey)!>
          <#if reference?? && reference?has_content>
            <#assign referenceNum = reference.size()>
          </#if>
        </#if>
        <#assign showLabel = true>
        <#if parameters.onlyMissingTranslations?? && parameters.onlyMissingTranslations == "Y" 
            && parameters.labelLocaleName?? && parameters.labelLocaleName != "">
          <#assign labelValue = label.getLabelValue(parameters.labelLocaleName)!>
          <#if labelValue?? && labelValue?has_content>
            <#assign value = labelValue.getLabelValue()!>
            <#if value?? && value?has_content>
              <#assign showLabel = false>
            </#if>
          </#if>
        </#if>
        <#if showLabel && parameters.onlyNotUsedLabels?? && parameters.onlyNotUsedLabels == "Y" && (referenceNum > 0)>
            <#assign showLabel = false>
        </#if>
        <#if showLabel && parameters.labelKey?? && parameters.labelKey != "" && parameters.labelKey != label.labelKey>
          <#assign showLabel = false>
        </#if>
        <#if showLabel && labelKeyRegex?has_content && !labelKeyRaw?matches(labelKeyRegex)><#-- SCIPIO -->
          <#assign showLabel = false>
        </#if>
        <#if showLabel && parameters.labelFileName?? && parameters.labelFileName != "" && parameters.labelFileName != label.fileName>
          <#assign showLabel = false>
        </#if>
        <#if showLabel == true>
          <@tr>
            <#--<@td>${rowNumber}</@td>-->
            <@td><a href="<@pageUrl>UpdateLabel?sourceKey=${labelKey}&amp;sourceFileName=${label.fileName}&amp;sourceKeyComment=${label.labelKeyComment!}</@pageUrl>" <#if previousKey == labelKey>class="submenutext"</#if>>${label.labelKey}</a></@td>
            <@td>${label.fileName}</@td>
            <@td><a href="<@pageUrl>ViewReferences?sourceKey=${labelKey}&amp;labelFileName=${label.fileName}</@pageUrl>">${uiLabelMap.WebtoolsLabelManagerReferences}</a></@td>
            <#list localesFound as localeFound>
              <#assign labelVal = label.getLabelValue(localeFound)!>
              <#assign showLocale = true>
              <#if parameters.labelLocaleName?? && parameters.labelLocaleName != "" && parameters.labelLocaleName != localeFound>
                <#assign showLocale = false>
              </#if>
              <#if showLocale>
                <#if labelVal?has_content>
                  <@td>${labelVal.getLabelValue()}</@td>
                  <#assign existingLabels = existingLabels + 1>
                <#else>
                  <@td>&nbsp;</@td>
                  <#assign missingLabels = missingLabels + 1>
                </#if>
              </#if>
            </#list>
          </@tr>
          <#assign previousKey = labelKey>
          <#assign rowNumber = rowNumber + 1>
        </#if>
      </#list>
  </@table>
</@section>  
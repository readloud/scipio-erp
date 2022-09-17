<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->
<#-- SCIPIO: THIS LIBRARY IS DEPRECATED AND OBSOLETE. DO NOT USE IN SCIPIO-BASED TEMPLATES. -->

<#-- SCIPIO: use same ones loaded by renderer 
     WARN: ability to use same ones as renderer currently depends on rendering context;
        currently may only work for web requests specifically using macro renderer, and not other contexts such as emails,
        but in general in stock code this file was used in regular screens, so not fatal. 
        This file should be avoided in favor of scipio macros.
<#include "component://widget/templates/htmlFormMacroLibrary.ftl"/>
<#include "component://widget/templates/htmlScreenMacroLibrary.ftl"> 
<#include "component://widget/templates/htmlMenuMacroLibrary.ftl">
... and use ones with caching of the directives so only interpreted once per request
<@('<#include "' + (raw(formMacroLibraryPath!'')!'component://widget/templates/htmlFormMacroLibrary.ftl') + '">')?interpret />
<@('<#include "' + (raw(screenMacroLibraryPath!'')!'component://widget/templates/htmlScreenMacroLibrary.ftl') + '">')?interpret />
<@('<#include "' + (raw(menuMacroLibraryPath!'')!'component://widget/templates/htmlMenuMacroLibrary.ftl') + '">')?interpret />-->

<#assign formMacroLibIncludeDirective = getRequestVar("formMacroLibIncludeDirective")!"">

<#if formMacroLibIncludeDirective?is_directive>
    <#assign screenMacroLibIncludeDirective = getRequestVar("screenMacroLibIncludeDirective")!"">
    <#assign menuMacroLibIncludeDirective = getRequestVar("menuMacroLibIncludeDirective")!"">
<#else>
    <#-- note: getMacroLibraryPath only available since scipio renderer mod -->
    <#assign formMacroLibraryPath = raw((formStringRenderer.getMacroLibraryPath())!'component://widget/templates/htmlFormMacroLibrary.ftl')>
    <#assign screenMacroLibraryPath = raw((screens.getScreenStringRenderer().getMacroLibraryPath())!'component://widget/templates/htmlScreenMacroLibrary.ftl')>
    <#assign menuMacroLibraryPath = raw((menuStringRenderer.getMacroLibraryPath())!'component://widget/templates/htmlMenuMacroLibrary.ftl')>

    <#assign formMacroLibIncludeDirective = ('<#include "' + formMacroLibraryPath + '">')?interpret>
    <#assign screenMacroLibIncludeDirective = ('<#include "' + screenMacroLibraryPath + '">')?interpret>
    <#assign menuMacroLibIncludeDirective = ('<#include "' + menuMacroLibraryPath + '">')?interpret>

    <#assign dummy = setRequestVar("formMacroLibIncludeDirective", formMacroLibIncludeDirective)>
    <#assign dummy = setRequestVar("screenMacroLibIncludeDirective", screenMacroLibIncludeDirective)>
    <#assign dummy = setRequestVar("menuMacroLibIncludeDirective", menuMacroLibIncludeDirective)>
</#if>

<@formMacroLibIncludeDirective />
<@screenMacroLibIncludeDirective />
<@menuMacroLibIncludeDirective />


<#macro lookupField className="" alert="" name="" value="" size="20" maxlength="20" id="" event="" action="" readonly="" autocomplete="" descriptionFieldName="" formName="" fieldFormName="" targetParameterIter="" imgSrc="" ajaxUrl="" ajaxEnabled="" presentation="layer" width="" height="" position="topleft" fadeBackground="true" clearText="" showDescription="" initiallyCollapsed="">
    <#if (!ajaxEnabled?has_content)>
        <#assign javascriptEnabled = UtilHttp.isJavaScriptEnabled(request) />
        <#if (javascriptEnabled)>
            <#local ajaxEnabled = true>
        </#if>
    </#if>
    <#if (!id?has_content)>
        <#local id = UtilHttp.getNextUniqueId(request) />
    </#if>
    <#if "true" == readonly>
        <#local readonly = true/>
    <#else>
        <#local readonly = false />
    </#if>
    <#if userPreferences.VISUAL_THEME == "BIZZNESS_TIME">
        <#local position = "center" />
    </#if>
    <@renderLookupField name formName fieldFormName className alert value size maxlength id event action readonly autocomplete descriptionFieldName targetParameterIter imgSrc ajaxUrl ajaxEnabled presentation width height position fadeBackground clearText showDescription initiallyCollapsed/>
</#macro>

<#-- SCIPIO: new params: showCount 
    WARN: commonDisplaying may be ignored (by renderNextPrev) -->
<#macro nextPrev commonUrl="" ajaxEnabled=false javaScriptEnabled=false paginateStyle="nav-pager" paginateFirstStyle="nav-first" viewIndex=0 highIndex=0 listSize=0 viewSize=1 ajaxFirstUrl="" firstUrl="" paginateFirstLabel="" paginatePreviousStyle="nav-previous" ajaxPreviousUrl="" previousUrl="" paginatePreviousLabel="" pageLabel="" ajaxSelectUrl="" selectUrl="" ajaxSelectSizeUrl="" selectSizeUrl="" commonDisplaying="" paginateNextStyle="nav-next" ajaxNextUrl="" nextUrl="" paginateNextLabel="" paginateLastStyle="nav-last" ajaxLastUrl="" lastUrl="" paginateLastLabel="" paginateViewSizeLabel="" showCount=true position="">
    <#local javaScriptEnabled = javaScriptEnabled />
    <#if (!javaScriptEnabled)>
        <#local javaScriptEnabled = UtilHttp.isJavaScriptEnabled(request) />
    </#if>
    <#if (commonUrl?has_content)>
        <#if (!firstUrl?has_content)>
            <#local firstUrl=commonUrl+"VIEW_SIZE=${viewSize}&amp;VIEW_INDEX=${viewIndexFirst}"/>
        </#if>
        <#if (!previousUrl?has_content)>
             <#local previousUrl=commonUrl+"VIEW_SIZE=${viewSize}&amp;VIEW_INDEX=${viewIndexPrevious}"/>
        </#if>
        <#if (!nextUrl?has_content)>
            <#local nextUrl=commonUrl+"VIEW_SIZE=${viewSize}&amp;VIEW_INDEX=${viewIndexNext}"/>
        </#if>
        <#if (!lastUrl?has_content)>
            <#local lastUrl=commonUrl+"VIEW_SIZE=${viewSize}&amp;VIEW_INDEX=${viewIndexLast}"/>
        </#if>
        <#if (!selectUrl?has_content)>
            <#local selectUrl=commonUrl+"VIEW_SIZE=${viewSize}&amp;VIEW_INDEX="/>
        </#if>
        <#if (!selectSizeUrl?has_content)>
            <#local selectSizeUrl=commonUrl+"VIEW_SIZE='+this.value+'&amp;VIEW_INDEX=0"/>
        </#if>
    </#if>
    <#if !showCount>
        <#local commonDisplaying = "">
    </#if>
    <@renderNextPrev paginateStyle=paginateStyle paginateFirstStyle=paginateFirstStyle viewIndex=viewIndex highIndex=highIndex 
      listSize=listSize viewSize=viewSize ajaxEnabled=ajaxEnabled javaScriptEnabled=javaScriptEnabled ajaxFirstUrl=ajaxFirstUrl 
      firstUrl=firstUrl paginateFirstLabel=uiLabelMap.CommonFirst paginatePreviousStyle=paginatePreviousStyle ajaxPreviousUrl=ajaxPreviousUrl 
      previousUrl=previousUrl paginatePreviousLabel=uiLabelMap.CommonPrevious pageLabel=uiLabelMap.CommonPage 
      ajaxSelectUrl=ajaxSelectUrl selectUrl=selectUrl ajaxSelectSizeUrl=ajaxSelectSizeUrl selectSizeUrl=selectSizeUrl 
      commonDisplaying=commonDisplaying paginateNextStyle=paginateNextStyle ajaxNextUrl=ajaxNextUrl nextUrl=nextUrl 
      paginateNextLabel=uiLabelMap.CommonNext paginateLastStyle=paginateLastStyle ajaxLastUrl=ajaxLastUrl lastUrl=lastUrl 
      paginateLastLabel=uiLabelMap.CommonLast paginateViewSizeLabel=uiLabelMap.CommonItemsPerPage position=position />                          
</#macro>

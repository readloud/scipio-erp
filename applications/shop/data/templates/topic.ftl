<?xml version="1.0" encoding="UTF-8"?>
<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->

<entity-engine-xml>
<#recurse doc>
</entity-engine-xml>

<#macro topics>
<#recurse .node>
</#macro>

<#macro topic>
    <#assign contentId="ECMT" + .node.@id[0]/>
    <Content contentId="${contentId}" contentTypeId="WEB_SITE_PUB_PT" contentName="${.node.topic_heading}" description="${.node.topic_desc?html}" ownerContentId=""/>
    <#assign internalName=.node.@name[0]/>
    <#assign internalNameParts=internalName?split(".")/>
    <#assign firstPart=internalNameParts[0] />
    <#assign nowStamp=UtilDateTime.nowTimestamp()/>
    <#if firstPart == "ELTRN">
        <ContentAssoc contentId="CNTELTRN" contentIdTo="${contentId}" contentAssocTypeId="SUB_CONTENT" fromDate="${nowStamp?string("yyyy-MM-dd HH:mm:ss")}"/>
    </#if>
</#macro>

<#macro @element>
</#macro>

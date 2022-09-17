<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->


<#--
<#include "publishlib.ftl" />
-->
<#--
<#import "publishlib.ftl" as publish/>
-->
<#--
${menuWrapper.renderMenuString()}
-->

<#-- SCIPIO: TODO? this should probably be converted but it may be a special case due to formatting content special way? -->
<#-- Main Heading -->
<@table type="generic" width="100%" cellpadding="0" cellspacing="0" border="0">
  <@tr>
    <@td>
      <h1>${contentId!}</h1>
    </@td>
    <@td align="right">
    </@td>
  </@tr>
</@table>

<#if currentValue?has_content>
    <@renderTextData content=currentValue textData=(textData!) />
</#if>
<#--
<#if textList?has_content>
  <#list textList as map>
    <@renderTextData content=map.entity textData=map.text />
  </#list>
</#if>
-->
<#-- ============================================================= -->

<@table type="generic" border="0" width="100%" cellspacing="0" cellpadding="0" class="+boxoutside">
  <@tr>
    <@td width='100%'>
      <@table type="generic" width="100%" border="0" cellspacing="0" cellpadding="0" class="+boxtop">
        <@tr>
          <@td valign="middle">
            <div class="boxhead">&nbsp; Links </div>
          </@td>
        </@tr>
      </@table>
    </@td>
  </@tr>
  <@tr>
    <@td width='100%'>
      <@table type="fields" width="100%" class="+boxbottom">
        <@tr>
          <@td>
            <form mode="post" name="publishsite" action="<@pageUrl>linkContentToPubPt</@pageUrl>">
              <input type="hidden" name="contentId" value="${contentId}"/>
              <@table type="fields" class="+${styles.table_spacing_tiny_hint!}" width="100%">
                    <#assign rowCount = 0 />
                    <#assign rootForumId=rootForumId />
                    <@publishContent forumId=rootForumId contentId=contentId />
                    <#assign rootForumId2=rootForumId2 />
                    <@publishContent forumId=rootForumId2 contentId=contentId />
                    <@tr>
                      <@td colspan="1">
                          <input type="submit" name="submitBtn" value="Publish" class="${styles.link_run_sys!} ${styles.action_updatestatus!}"/>
                      </@td>
                    </@tr>
              </@table>
              <input type="hidden" name="_rowCount" value="${rowCount}"/>
            </form>
          </@td>
        </@tr>

      </@table>
    </@td>
  </@tr>
</@table>

<@table type="generic" border="0" width="100%" cellspacing="0" cellpadding="0" class="+boxoutside">
  <@tr>
    <@td width='100%'>
      <@table type="generic" width="100%" border="0" cellspacing="0" cellpadding="0" class="+boxtop">
        <@tr>
          <@td valign="middle">
            <div class="boxhead">&nbsp; Features </div>
          </@td>
        </@tr>
      </@table>
    </@td>
  </@tr>
  <@tr>
    <@td width='100%'>
      <@table type="fields" width="100%" class="+boxbottom">
        <@tr>
          <@td>
            <form mode="post" name="updatefeatures" action="<@pageUrl>updateFeatures</@pageUrl>">
              <input type="hidden" name="contentId" value="${contentId}"/>
              <@fields type="default-manual">
              <@table type="fields" class="+${styles.table_spacing_tiny_hint!}" width="100%">
                    <@thead>
                       <@tr>
                          <@th>Product Feature</@th>
                          <@th>Has Feature</@th>
                       </@tr>
                    </@thead>
                    <#assign rowCount = 0 />
                    <#list featureList as feature>
                       <#assign checked=""/>
                       <#if feature.action?has_content && feature.action == "Y">
                           <#assign checked="checked=\"checked\""/>
                       </#if>
                       <@tr>
                          <@td>[${feature.productFeatureId}] - ${feature.description}</@td>
                          <@td><input type="checkbox" name="action_o_${rowCount}" value="Y" ${checked}/></@td>
                          <input type="hidden" name="fieldName0_o_${rowCount}" value="productFeatureId"/>
                          <input type="hidden" name="fieldValue0_o_${rowCount}" value="${feature.productFeatureId}"/>
                          <input type="hidden" name="fieldName1_o_${rowCount}" value="dataResourceId"/>
                          <input type="hidden" name="fieldValue1_o_${rowCount}" value="${feature.dataResourceId}"/>
                          <input type="hidden" name="entityName_o_${rowCount}" value="ProductFeatureDataResource"/>
                          <input type="hidden" name="pkFieldCount_o_${rowCount}" value="2"/>
                       </@tr>
                       <#assign rowCount=rowCount + 1/>
                    </#list>
                    <@tr>
                      <@td valign="middle">
                        <div class="boxhead">
                          <@field type="lookup" formName="updatefeatures" name="fieldValue0_o_${rowCount}" id="fieldValue0_o_${rowCount}" fieldFormName="LookupProductFeature"/>
                        </div>
                      </@td>
                          <input type="hidden" name="fieldName0_o_${rowCount}" value="productFeatureId"/>
                          <input type="hidden" name="fieldValue0_o_${rowCount}" value=""/>
                          <input type="hidden" name="fieldName1_o_${rowCount}" value="dataResourceId"/>
                          <input type="hidden" name="fieldValue1_o_${rowCount}" value="${dataResourceId}"/>
                          <input type="hidden" name="entityName_o_${rowCount}" value="ProductFeatureDataResource"/>
                          <input type="hidden" name="pkFieldCount_o_${rowCount}" value="2"/>
                          <#assign rowCount=rowCount + 1/>
                    </@tr>
                    <@tr>
                      <@td colspan="1">
                          <input type="submit" name="submitBtn" value="${uiLabelMap.CommonUpdate}" class="${styles.link_run_sys!} ${styles.action_update!}"/>
                      </@td>
                    </@tr>
              </@table>
              </@fields>
              <input type="hidden" name="_rowCount" value="${rowCount}"/>
            </form>
          </@td>
        </@tr>

      </@table>
    </@td>
  </@tr>
</@table>

<#--
<@table type="generic" border="0" width="100%" cellspacing="0" cellpadding="0" class="+boxoutside">
  <@tr>
    <@td width='100%'>
      <@table type="fields" width="100%" class="+boxtop">
        <@tr>
          <@td valign="middle">
            <div class="boxhead">&nbsp;Image Information</div>
          </@td>
          <@td valign="middle" align="right">
            <a href="<@pageUrl>EditAddImage?contentId=${imgContentId!}dataResourceId=${imgDataResourceId!}</@pageUrl>" class="submenutextright">Update</a>
          </@td>
        </@tr>
      </@table>
    </@td>
  </@tr>
  <@tr>
    <@td width='100%'>
      <@table type="generic" width="100%" border="0" cellspacing="0" cellpadding="0" class="+boxbottom">
        <@tr>
          <@td>
              <@table type="fields" width="100%">
                <@tr><@td align="right" nowrap="nowrap"><div class="tabletext"><b>Image</b></div></@td><@td>&nbsp;</@td><@td><div class="tabletext">
                    <img src="<@pageUrl>img?imgId=${imgDataResourceId!}</@pageUrl>" alt="" />
                    <div></@td></@tr>
              </@table>
          </@td>
        </@tr>
      </@table>
    </@td>
  </@tr>
</@table>
-->


<#--
<#macro contentTree currentValue >

    <#assign contentId = currentValue.contentId/>
    <#assign dataResourceId = currentValue.dataResourceId/>
    <#assign currentTextData = "" />
    <#if dataResourceId?has_content>
        <#assign currentTextData=Static["org.ofbiz.content.data.DataResourceWorker"].renderDataResourceAsText(delegator, dataResourceId, null, null, null, true) />
        <#if currentTextData?has_content>
            <@renderTextData contentId=contentId textData=currentTextData />
        </#if>
    </#if>
    <#assign contentAssocViewList =Static["org.ofbiz.content.content.ContentWorker"].getContentAssocViewList(delegator, contentId, null, "SUB_CONTENT", null, null)! />
    <#list contentAssocViewList as contentAssocDataResourceView>
        <#assign contentId2 = contentAssocDataResourceView.contentId/>
        <#assign mapKey = contentAssocDataResourceView.mapKey/>
        <#assign dataResourceId2 = contentAssocDataResourceView.dataResourceId/>
        <#assign currentTextData=Static["org.ofbiz.content.data.DataResourceWorker"].renderDataResourceAsText(delegator, dataResourceId2, null, null, null, true) />
        <#if currentTextData?has_content>
            <@renderTextData contentId=contentId2 mapKey=mapKey textData=currentTextData />
        </#if>
    </#list>
</#macro>
-->

<#macro renderTextData content textData >
    <#assign contentId=content.contentId!/>
<@table type="generic" border="0" width="100%" cellspacing="0" cellpadding="0" class="+boxoutside">
  <@tr>
    <@td width='100%'>
      <@table type="fields" width="100%" class="+boxtop">
        <@tr>
          <@td valign="middle">
            <div class="boxhead">&nbsp;</div>
          </@td>
          <@td valign="middle" align="right">
            <a href="<@pageUrl>EditAddContent?contentId=${content.contentId!}&amp;contentIdTo=${content.caContentIdTo!}&amp;contentAssocTypeId=${content.caContentAssocTypeId!}&amp;fromDate=${content.caFromDate!}&amp;mapKey=${content.caMapKey!}</@pageUrl>" class="submenutextright">Update</a>
          </@td>
        </@tr>
      </@table>
    </@td>
  </@tr>
  <@tr>
    <@td width='100%'>
      <@table type="generic" width="100%" border="0" cellspacing="0" cellpadding="0" class="+boxbottom">
        <@tr>
          <@td>
              <@table type="fields" width="100%">
                <@tr><@td align="right" nowrap="nowrap"><div class="tabletext"><b>Content Name</b></div></@td><@td><div class="tabletext">${content.contentName!}</div></@td></@tr>
                <@tr><@td align="right" nowrap="nowrap"><div class="tabletext"><b>Description</b></div></@td><@td><div class="tabletext">${content.description!}<div></@td></@tr>
              </@table>
          </@td>
        </@tr>
      </@table>
    </@td>
  </@tr>
  <@tr>
    <@td type="generic" width='100%'>
      <@table type="generic" width="100%" border="0" cellspacing="0" cellpadding="0" class="+boxbottom">
        <@tr>
          <@td>
              <@table type="fields" width="100%">
                <@tr><@td align="right" nowrap="nowrap"><div class="tabletext"><b></b></div></@td><@td><div class="tabletext">
                    <#-- ${textData!} -->
                    <@renderContentAsText subContentId=content.contentId  editRequestName="/EditAddContent"/>
                    </div></@td></@tr>
              </@table>
          </@td>
        </@tr>
      </@table>
    </@td>
  </@tr>
</@table>
</#macro>

<#macro publishContent forumId contentId formAction="/updatePublishLinksMulti"  indentIndex=0 catTrail=[]>

<#local thisContentId=catTrail[indentIndex]!/>

<#assign viewIdx = "" />
<#if requestParameters.viewIndex?has_content>
<#assign viewIdx = requestParameters.viewIndex!?number />
</#if>
<#assign viewSz = "" />
<#if requestParameters.viewSize?has_content>
<#assign viewSz = requestParameters.viewSize!?number />
</#if>

<#local indent = "">
<#local thisCatTrailCsv = "" />
<#local listUpper = (indentIndex - 1) />
<#if catTrail?size < listUpper >
    <#local listUpper = (catTrail?size - 1)>
</#if>
<#if 0 < listUpper >
  <#list 0..listUpper as idx>
      <#if thisCatTrailCsv?has_content>
          <#local thisCatTrailCsv = thisCatTrailCsv + ","/>
      </#if>
      <#local thisCatTrailCsv = thisCatTrailCsv + catTrail[idx]>
  </#list>
</#if>
<#if 0 < indentIndex >
  <#list 0..(indentIndex - 1) as idx>
      <#local indent = indent + "&nbsp;&nbsp;&nbsp;&nbsp;">
  </#list>
</#if>


<@loopSubContent contentId=forumId viewIndex=viewIdx viewSize=viewSz contentAssocTypeId="SUBSITE" returnAfterPickWhen="1==1";>
    <#local isPublished = "" />
    <#assign contentAssocViewFrom=Static["org.ofbiz.content.content.ContentWorker"].getContentAssocViewFrom(delegator, subContentId, contentId, "PUBLISH_LINK", null, null)! />
    <#if contentAssocViewFrom?has_content>
        <#local isPublished = "checked=\"checked\"" />
    </#if>
       <@tr>
         <@td>
            ${indent}
            <#local plusMinus="-"/>
            ${plusMinus} ${content.contentName!}
         </@td >
         <@td>
            <input type="checkbox" name="publish_o_${rowCount}" value="Y" ${isPublished}/>
         </@td >
            <input type="hidden" name="contentIdTo_o_${rowCount}" value="${subContentId}" />
            <input type="hidden" name="contentId_o_${rowCount}" value="${contentId}" />
            <input type="hidden" name="contentAssocTypeId_o_${rowCount}" value="PUBLISH_LINK" />
            <input type="hidden" name="statusId_o_${rowCount}" value="CTNT_IN_PROGRESS" />
       </@tr>
       <#assign rowCount = rowCount + 1 />
       <@publishContent forumId=subContentId contentId=contentId indentIndex=(indentIndex + 1)/>
</@loopSubContent>

</#macro>

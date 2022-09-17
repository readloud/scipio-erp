<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->
<@script>
function insertNowTimestamp(field) {
  eval('document.productForm.' + field + '.value="${nowTimestamp?string}";');
}
function insertImageName(size,nameValue) {
  eval('document.productForm.' + size + 'ImageUrl.value=nameValue;');
}
</@script>

<#if fileType?has_content>
  <@section title=uiLabelMap.ProductResultOfImageUpload>
    <#if !(clientFileName?has_content)>
        <div>${uiLabelMap.ProductNoFileSpecifiedForUpload}.</div>
    <#else>
        <div>${uiLabelMap.ProductTheFileOnYourComputer}: <b>${clientFileName!}</b></div>
        <div>${uiLabelMap.ProductServerFileName}: <b>${fileNameToUse!}</b></div>
        <div>${uiLabelMap.ProductServerDirectory}: <b>${imageServerPath!}</b></div>
        <div>${uiLabelMap.ProductTheUrlOfYourUploadedFile}: <b><a href="<@contentUrl>${imageUrl!}</@contentUrl>" class="${styles.link_nav_info_uri!}">${imageUrl!}</a></b></div>
    </#if>
  </@section>
</#if>

<#if !(configItem??)>
    <@commonMsg type="error">${uiLabelMap.ProductCouldNotFindProductConfigItem} "${configItemId}".</@commonMsg>
<#else>
    <@table type="data-list" autoAltRows=true>
      <@thead>
        <@tr class="header-row">
            <@th>${uiLabelMap.ProductContent}</@th>
            <@th>${uiLabelMap.ProductType}</@th>
            <@th>${uiLabelMap.CommonFrom}</@th>
            <@th>${uiLabelMap.CommonThru}</@th>
            <@th>&nbsp;</@th>
            <@th>&nbsp;</@th>
        </@tr>
      </@thead>
      <@tbody>
        <#list productContentList as entry>
        <#assign productContent=entry.productContent/>
        <#assign productContentType=productContent.getRelatedOne("ProdConfItemContentType", true)/>
        <@tr valign="middle">
            <@td><a href="<@pageUrl>EditProductConfigItemContentContent?configItemId=${productContent.configItemId}&amp;contentId=${productContent.contentId}&amp;confItemContentTypeId=${productContent.confItemContentTypeId}&amp;fromDate=${productContent.fromDate}</@pageUrl>" class="${styles.link_nav_info_desc!}">${entry.content.description?default("[${uiLabelMap.ProductNoDescription}]")} [${entry.content.contentId}]</@td>
            <@td>${productContentType.description!productContent.confItemContentTypeId}</@td>
            <@td>${productContent.fromDate!(uiLabelMap.CommonNA)}</@td>
            <@td>${productContent.thruDate!(uiLabelMap.CommonNA)}</@td>
            <@td>
              <form name="removeContentFromProductConfigItem_${productContent.contentId}_${entry_index}" method="post" action="<@pageUrl>removeContentFromProductConfigItem</@pageUrl>">
                <input name="configItemId" type="hidden" value="${productContent.configItemId}"/>
                <input name="contentId" type="hidden" value="${productContent.contentId}"/>
                <input name="confItemContentTypeId" type="hidden" value="${productContent.confItemContentTypeId}"/>
                <input name="fromDate" type="hidden" value="${productContent.fromDate}"/>
                <input type="submit" value="${uiLabelMap.CommonDelete}"/>
              </form>
            </@td>
            <@td><a href="<@serverUrl>/content/control/EditContent?contentId=${productContent.contentId}&amp;externalLoginKey=${requestAttributes.externalLoginKey!}</@serverUrl>" class="${styles.link_nav!} ${styles.action_update!}">${uiLabelMap.ProductEditContent} ${entry.content.contentId}</a></@td>
         </@tr>
         </#list>
       </@tbody>
    </@table>

    <#if configItemId?has_content && configItem?has_content>
        <@section title=uiLabelMap.ProductCreateNewProductConfigItemContent>
            ${prepareAddProductContentWrapper.renderFormString(context)}
        </@section>
        
        <@section title=uiLabelMap.ProductAddContentProductConfigItem>
            ${addProductContentWrapper.renderFormString(context)}
        </@section>
    </#if>
    <@section title=uiLabelMap.ProductOverrideSimpleFields>
            <form action="<@pageUrl>updateProductConfigItemContent</@pageUrl>" method="post" name="productForm">
                <input type="hidden" name="configItemId" value="${configItemId!}" />
                <@field type="textarea" label=uiLabelMap.CommonDescription name="description" cols="60" rows="2">${(configItem.description)!}</@field>
                <@field type="textarea" label=uiLabelMap.ProductLongDescription name="longDescription" cols="60" rows="7">${(configItem.longDescription)!}</@field>
                <#assign labelDetail>
                    <#if (configItem.imageUrl)??>
                        <a href="<@contentUrl>${configItem.imageUrl}</@contentUrl>" target="_blank"><img alt="Image" src="<@contentUrl>${configItem.imageUrl}</@contentUrl>" class="cssImgSmall" /></a>
                    </#if>
                </#assign>
                <@field type="generic" label=uiLabelMap.ProductSmallImage labelDetail=labelDetail>
                    <@field type="input" name="imageUrl" value=(configItem.imageUrl)?default(imageNameSmall + '.jpg') size="60" maxlength="255" />
                    <#if configItemId?has_content>
                        <div>
                        <span>${uiLabelMap.ProductInsertDefaultImageUrl}: </span>
                        <a href="javascript:insertImageName('small','${imageNameSmall}.jpg');" class="${styles.link_run_local!} ${styles.action_add!}">.jpg</a>
                        <a href="javascript:insertImageName('small','${imageNameSmall}.gif');" class="${styles.link_run_local!} ${styles.action_add!}">.gif</a>
                        <a href="javascript:insertImageName('small','');" class="${styles.link_run_local!} ${styles.action_clear!}">${uiLabelMap.CommonClear}</a>
                        </div>
                    </#if>
                </@field>
                <@field type="submit" name="Update" text=uiLabelMap.CommonUpdate class="+${styles.link_run_sys!} ${styles.action_update!}" />
            </form>
    </@section>
    
    <@section title=uiLabelMap.ProductUploadImage>
            <form method="post" enctype="multipart/form-data" action="<@pageUrl>UploadProductConfigItemImage?configItemId=${configItemId}&amp;upload_file_type=small</@pageUrl>" name="imageUploadForm">
                <@field type="file" size="50" name="fname" />
                <@field type="submit" class="+${styles.link_run_sys!} ${styles.action_import!}" text=uiLabelMap.ProductUploadImage />
            </form>
    </@section>
</#if>

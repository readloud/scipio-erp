<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->
<#if product??>
<@script>
    function insertNowTimestamp(field) {
        eval('document.productForm.' + field + '.value="${nowTimestampString}";');
    };
    function insertImageName(type,nameValue) {
        eval('document.productForm.' + type + 'ImageUrl.value=nameValue;');
    };
</@script>

    <#if fileType?has_content>
        <@heading>${uiLabelMap.ProductResultOfImageUpload}</@heading>
        <#if !(clientFileName?has_content)>
          <div>${uiLabelMap.ProductNoFileSpecifiedForUpload}.</div>
        <#else>
          <div>${uiLabelMap.ProductTheFileOnYourComputer}: <b>${clientFileName!}</b></div>
          <div>${uiLabelMap.ProductServerFileName}: <b>${fileNameToUse!}</b></div>
          <div>${uiLabelMap.ProductServerDirectory}: <b>${imageServerPath!}</b></div>
          <div>${uiLabelMap.ProductTheUrlOfYourUploadedFile}: <b><a href="<@contentUrl>${imageUrl!}</@contentUrl>">${imageUrl!}</a></b></div>
        </#if>
        <br />
    </#if>
    
    <@section>
        <form action="<@pageUrl>updateProductContent</@pageUrl>" method="post" name="productForm">
            <input type="hidden" name="productId" value="${productId!}"/>
            <@field type="input" label=uiLabelMap.ProductProductName name="productName" value=((product.productName)!) size="30" maxlength="60"/>
            <@field type="textarea" label=uiLabelMap.ProductProductDescription name="description" cols="60" rows="2">${(product.description)!}</@field>
            <@field type="textarea" label=uiLabelMap.ProductLongDescription class="+dojo-ResizableTextArea" name="longDescription" cols="60" rows="7">${(product.longDescription)!}</@field>
            <#-- SCIPIO: Now points to shop -->
            <#assign fieldTooltip>${rawLabel('ProductIfNotSpecifiedDefaultsIsProductdetail')} "productdetail", ${rawLabel('ProductDetailScreenMessage')}: "component://shop/widget/CatalogScreens.xml#productdetail"</#assign>
            <@field type="input" label=uiLabelMap.ProductDetailScreen name="detailScreen" value=((product.detailScreen)!) size="60" maxlength="250" tooltip=fieldTooltip/>
            <#-- SCIPIO: FIXME: same pattern copy-pasted... -->
            <#assign labelDetail>
              <#if (product.smallImageUrl)??>
                <a href="<@contentUrl>${(product.smallImageUrl)!}</@contentUrl>" target="_blank"><img alt="Small Image" src="<@contentUrl>${(product.smallImageUrl)!}</@contentUrl>" class="cssImgSmall"/></a>
              </#if>
            </#assign>
            <@field type="generic" label=uiLabelMap.ProductSmallImage labelDetail=labelDetail>
                <@field type="input" name="smallImageUrl" value=(product.smallImageUrl)!'' size="60" maxlength="255"/>
                  <#if productId?has_content>
                    <div>
                        <span>${uiLabelMap.ProductInsertDefaultImageUrl}: </span>
                        <a href="javascript:insertImageName('small','${imageNameSmall}.jpg');" class="${styles.link_run_local!} ${styles.action_add!}">.jpg</a>
                        <a href="javascript:insertImageName('small','${imageNameSmall}.gif');" class="${styles.link_run_local!} ${styles.action_add!}">.gif</a>
                        <a href="javascript:insertImageName('small','');" class="${styles.link_run_local!} ${styles.action_clear!}">${uiLabelMap.CommonClear}</a>
                    </div>
                  </#if>
            </@field>
            <#assign labelDetail>
              <#if (product.mediumImageUrl)??>
                <a href="<@contentUrl>${product.mediumImageUrl}</@contentUrl>" target="_blank"><img alt="Medium Image" src="<@contentUrl>${product.mediumImageUrl}</@contentUrl>" class="cssImgSmall"/></a>
              </#if>
            </#assign>
            <@field type="generic" label=uiLabelMap.ProductMediumImage labelDetail=labelDetail>
                <@field type="input" name="mediumImageUrl" value=(product.mediumImageUrl)!'' size="60" maxlength="255"/>
                  <#if productId?has_content>
                    <div>
                        <span>${uiLabelMap.ProductInsertDefaultImageUrl}: </span>
                        <a href="javascript:insertImageName('medium','${imageNameMedium}.jpg');" class="${styles.link_run_local!} ${styles.action_add!}">.jpg</a>
                        <a href="javascript:insertImageName('medium','${imageNameMedium}.gif');" class="${styles.link_run_local!} ${styles.action_add!}">.gif</a>
                        <a href="javascript:insertImageName('medium','');" class="${styles.link_run_local!} ${styles.action_clear!}">${uiLabelMap.CommonClear}</a>
                    </div>
                  </#if>
            </@field>
            <#assign labelDetail>
              <#if (product.largeImageUrl)??>
                <a href="<@contentUrl>${product.largeImageUrl}</@contentUrl>" target="_blank"><img alt="Large Image" src="<@contentUrl>${product.largeImageUrl}</@contentUrl>" class="cssImgSmall"/></a>
              </#if>
            </#assign>
            <@field type="generic" label=uiLabelMap.ProductLargeImage labelDetail=labelDetail>
                <@field type="input" name="largeImageUrl" value=(product.largeImageUrl)!'' size="60" maxlength="255"/>
                  <#if productId?has_content>
                    <div>
                        <span>${uiLabelMap.ProductInsertDefaultImageUrl}: </span>
                        <a href="javascript:insertImageName('large','${imageNameLarge}.jpg');" class="${styles.link_run_local!} ${styles.action_add!}">.jpg</a>
                        <a href="javascript:insertImageName('large','${imageNameLarge}.gif');" class="${styles.link_run_local!} ${styles.action_add!}">.gif</a>
                        <a href="javascript:insertImageName('large','');" class="${styles.link_run_local!} ${styles.action_clear!}">${uiLabelMap.CommonClear}</a>
                    </div>
                  </#if>
            </@field>
            <#assign labelDetail>
              <#if (product.detailImageUrl)??>
                <a href="<@contentUrl>${product.detailImageUrl}</@contentUrl>" target="_blank"><img alt="Detail Image" src="<@contentUrl>${product.detailImageUrl}</@contentUrl>" class="cssImgSmall"/></a>
              </#if>
            </#assign>
            <@field type="generic" label=uiLabelMap.ProductDetailImage labelDetail=labelDetail>
                <@field type="input" name="detailImageUrl" value=(product.detailImageUrl)!'' size="60" maxlength="255"/>
                  <#if productId?has_content>
                    <div>
                        <span>${uiLabelMap.ProductInsertDefaultImageUrl}: </span>
                        <a href="javascript:insertImageName('detail','${imageNameDetail}.jpg');" class="${styles.link_run_local!} ${styles.action_add!}">.jpg</a>
                        <a href="javascript:insertImageName('detail','${imageNameDetail}.gif');" class="${styles.link_run_local!} ${styles.action_add!}">.gif</a>
                        <a href="javascript:insertImageName('detail','');" class="${styles.link_run_local!} ${styles.action_clear!}">${uiLabelMap.CommonClear}</a>
                    </div>
                  </#if>
            </@field>
            <#assign labelDetail>
                  <#if (product.originalImageUrl)??>
                    <a href="<@contentUrl>${product.originalImageUrl}</@contentUrl>" target="_blank"><img alt="Original Image" src="<@contentUrl>${product.originalImageUrl}</@contentUrl>" class="cssImgSmall"/></a>
                  </#if>
            </#assign>
            <@field type="generic" label=uiLabelMap.ProductOriginalImage labelDetail=labelDetail>
                <@field type="input" name="originalImageUrl" value=(product.originalImageUrl)!'' size="60" maxlength="255"/>
                  <#if productId?has_content>
                    <div>
                        <span>${uiLabelMap.ProductInsertDefaultImageUrl}: </span>
                        <a href="javascript:insertImageName('original','${imageNameOriginal}.jpg');" class="${styles.link_run_local!} ${styles.action_add!}">.jpg</a>
                        <a href="javascript:insertImageName('original','${imageNameOriginal}.gif');" class="${styles.link_run_local!} ${styles.action_add!}">.gif</a>
                        <a href="javascript:insertImageName('original','');" class="${styles.link_run_local!} ${styles.action_clear!}">${uiLabelMap.CommonClear}</a>
                    </div>
                  </#if>
            </@field>
            <@field type="submit" name="Update" text=uiLabelMap.CommonUpdate class="+${styles.link_run_sys!} ${styles.action_update!}"/>
        </form>
        <@script>
            function setUploadUrl(newUrl) {
                var toExec = 'document.imageUploadForm.action="' + newUrl + '";';
                eval(toExec);
            };
        </@script>
    </@section>
    
    <@section title=uiLabelMap.ProductUploadImage>
        <form method="post" enctype="multipart/form-data" action="<@pageUrl>UploadProductImage?productId=${productId}&amp;upload_file_type=original</@pageUrl>" name="imageUploadForm">
          <@fields type="default-nolabelarea">
            <p><em>${uiLabelMap.ProductOriginalImageMessage} : &dollar;{ofbiz.home}/applications/product/config/ImageProperties.xml</em></p>
            <@field type="file" size="50" name="fname"/>
            <@field type="generic">
                <@field type="radio" name="upload_file_type_bogus" value="small" onClick="setUploadUrl('${escapeVal(makePageUrl('UploadProductImage?productId=${productId}&upload_file_type=small'), 'js')}');" label=uiLabelMap.CommonSmall/>
                <@field type="radio" name="upload_file_type_bogus" value="medium" onClick="setUploadUrl('${escapeVal(makePageUrl('UploadProductImage?productId=${productId}&upload_file_type=medium'), 'js')}');" label=uiLabelMap.CommonMedium/>
                <@field type="radio" name="upload_file_type_bogus" value="large" onClick="setUploadUrl('${escapeVal(makePageUrl('UploadProductImage?productId=${productId}&upload_file_type=large'), 'js')}');" label=uiLabelMap.CommonLarge/>
                <@field type="radio" name="upload_file_type_bogus" value="detail" onClick="setUploadUrl('${escapeVal(makePageUrl('UploadProductImage?productId=${productId}&upload_file_type=detail'), 'js')}');" label=uiLabelMap.CommonDetail/>
                <@field type="radio" name="upload_file_type_bogus" value="original" checked=true onClick="setUploadUrl('${escapeVal(makePageUrl('UploadProductImage?productId=${productId}&upload_file_type=original'), 'js')}');" label=uiLabelMap.ProductOriginal/>
            </@field>
            <@field type="submit" class="+${styles.link_run_sys!} ${styles.action_import!}" text=uiLabelMap.ProductUploadImage/>
          </@fields>
        </form>
    </@section>
</#if>

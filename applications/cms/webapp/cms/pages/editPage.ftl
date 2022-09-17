<#include "component://cms/webapp/cms/common/common.ftl">

<#assign debugMode = false>

<#if !content??>
  <#assign content = {}>
</#if>
<#assign pathPlaceholder = "/myUrl">
<#-- NOTE: don't use container=false anymore, use @fields type="default-compact" -->
<#macro pageAttrField fieldObj value="" id="" namePrefix="" expandLangVisible=true>
    <#if fieldObj.type?has_content>
        <#local useAltTooltip = (fieldObj.type == "LONG_TEXT")>
        <#local fieldIdNum = getNextFieldIdNum()>
        <#if !id?has_content>
          <#local id = getNextFieldId(fieldIdNum)>
        </#if>
        <#if fieldObj.maxLength?has_content && (fieldObj.maxLength?number > 0)>
          <#local maxLength = fieldObj.maxLength?number/>
        <#else>
          <#local maxLength = ""/>
        </#if>
        <#local labelDetail = ""/>
        <#local tooltip = raw(fieldObj.help!"")/>
        <#local defaultExpandLang = Static["com.ilscipio.scipio.cms.template.AttributeExpander$ExpandLang"].getDefaultExpandLang()!>
        <#local expandLang = fieldObj.expandLang!defaultExpandLang>
        <#if expandLang?has_content && expandLang != "NONE">
          <#local expandLang = Static["com.ilscipio.scipio.cms.template.AttributeExpander$ExpandLang"].fromStringSafe(expandLang?string)!><#-- make sure we have the enum not just the string -->
          <#if expandLang?has_content>
            <#local supportedLangStr = "${raw(uiLabelMap.CmsSupportedLang)}: ${raw(expandLang.getDescriptionWithExample(locale)!)}"/>
            <#if expandLangVisible>
              <#local labelDetail = supportedLangStr>
            <#else>
              <#local tooltip = appendToSentence(tooltip, supportedLangStr + ".")>
            </#if>
          <#else>
            <#-- ERROR: invalid db value; always report -->
            <#local dummy = Debug.logError("Cms: @pageAttrField: Error: Attribute '" + raw(fieldObj.id!"") + "' has invalid expandLang (" + expandLang + ")", "editPage.ftl")!>
          </#if>
        </#if>
        <#if fieldObj.targetType?has_content>
          <#local tooltip = appendToSentence(tooltip, "targetType: " + fieldObj.targetType + ".")>
        </#if>
        <#local rawLabelDetail = labelDetail><#-- save it -->
        <#local fieldLabel = fieldObj.displayName!"Field">
        <#if labelDetail?has_content>
          <#local labelDetail><span class="label-detail">${escapeVal(labelDetail, 'html')}</span></#local>
        </#if>
        <#if useAltTooltip>
          <#-- NOTE: 2017-04-06: MUST DISABLE REGULAR TOOLTIP entirely for LONG_TEXT, it interferes with the editor in some modes.
            instead just put a tooltip on the label, using hackish method -->
          <#if tooltip?has_content>
            <#local fieldLabelWithTooltip><a href="javascript:void(0);" class="${styles.tooltip!}" title="${tooltip}">${fieldLabel}</a></#local>
            <#local fieldLabel = wrapAsRaw({'raw':raw(fieldLabel), 'htmlmarkup':fieldLabelWithTooltip})>
          </#if>
          <#local tooltip = false>
        </#if>
        <#local required = toBooleanCms(fieldObj.required!)!false>
        <#local containerClass = "+page-attr-field">
        <#local name = namePrefix + (fieldObj.name!"")>
        <#switch fieldObj.type>
            <#case "LONG_TEXT">
              <#local containerClass = addClassArg(containerClass, "page-attr-type-longtext")>
              <#-- DEV NOTE: the rich editor should only display FTL-related functions 
                if fieldObj.expandLang == "FTL" -->
              <@field label=fieldLabel labelDetail=labelDetail type="textarea" class="+editor" name=name placeholder=(fieldObj.defaultValue!"") tooltip=tooltip required=required maxlength=maxLength id=id value=value containerClass=containerClass/>
            <#break>
            <#case "BOOLEAN">
              <#local containerClass = addClassArg(containerClass, "page-attr-type-boolean")>
              <#-- NOTE: BOOLEAN supports expansion languages - if they are enabled, cannot use the simple checkbox -->
              <#if !fieldObj.expandLang?has_content || fieldObj.expandLang == "NONE">
                <#-- TODO?: review this, it's because default-compact looks ugly in this case... maybe default-compact needs a review... -->
                <@fields type="default">
                  <#-- 2018-04-16: DO NOT USE CHECKBOX - it prevents templates from using default-value logic and operators - ternary needed
                  <@field label=fieldLabel labelDetail=labelDetail type="checkbox" name=name tooltip=tooltip required=required maxlength=maxLength id=id 
                      value="true" altValue="false" 
                      checked=(toBooleanCms(value)!toBooleanCms(fieldObj.defaultValue!)!false) containerClass=containerClass/>-->
                  <#local boolVal = toBooleanCms(value)!"">
                  <#local defBoolVal = toBooleanCms(fieldObj.defaultValue!"")!"">
                  <@field label=fieldLabel labelDetail=labelDetail type="select" name=name tooltip=tooltip required=required id=id containerClass=containerClass>
                    <option value=""<#if !boolVal?is_boolean> selected="selected"</#if>><#if defBoolVal?is_boolean>(${defBoolVal?string})</#if></option><#-- not needed? ${uiLabelMap.CommonDefault}: -->
                    <option value="true"<#if boolVal?is_boolean && boolVal> selected="selected"</#if>>true</option>
                    <option value="false"<#if boolVal?is_boolean && !boolVal> selected="selected"</#if>>false</option>
                  </@field>
                </@fields>
              <#else>
                <@field label=fieldLabel labelDetail=labelDetail type="input" name=name placeholder=(fieldObj.defaultValue!"") tooltip=tooltip required=required maxlength=maxLength id=id value=value containerClass=containerClass/>
              </#if>
            <#break>
            <#case "INTEGER">
                <#-- DEV NOTE: cannot enforce [0-9-] here because expandLang supports expressions (TODO: restrict if no expandLang or expandLang=NONE) -->
                <#local containerClass = addClassArg(containerClass, "page-attr-type-integer")>
                <@field label=fieldLabel labelDetail=labelDetail type="input" name=name placeholder=(fieldObj.defaultValue!"") tooltip=tooltip required=required maxlength=maxLength id=id value=value/>
            <#break>
            <#case "SHORT_TEXT">
                <#local containerClass = addClassArg(containerClass, "page-attr-type-shorttext")>
            <#default>
                <@field label=fieldLabel labelDetail=labelDetail type="input" name=name placeholder=(fieldObj.defaultValue!"") tooltip=tooltip required=required maxlength=maxLength id=id value=value containerClass=containerClass/>
        </#switch>
    </#if>
</#macro>

<#assign primaryTargetPathOptions = [
    { "key" : "/cmsPagePlainNoAuth", "description" : "http, no-auth" },
    { "key" : "/cmsPagePlainAuth", "description" : "http, auth" },
    { "key" : "/cmsPageSecureNoAuth", "description" : "https, no-auth" },
    { "key" : "/cmsPageSecureAuth", "description" : "https, auth" }
]>

<#-- The Content -->
<#-- EDIT PAGE -->
<#if template?has_content || meta?has_content>
  <#if webSiteId?has_content><#-- NOTE: webSiteId must be both -->
  
    <#-- DEV NOTE: The path must be escaped using escapeVal(path, 'url') when passed as parameter -->
  
    <#assign editPageByIdUrl = makePageUrl("editPage?pageId="+escapeVal(pageId!, 'url'))>
    <#-- 2019-01-23: The ?webSiteId= causes conflicts and nothing but problems
    <#assign editPageUrl = makePageUrl("editPage?webSiteId=${webSiteId!}&path=${escapeVal(pagePrimaryPath, 'url')}")>-->
    <#assign editPageUrl = editPageByIdUrl>

    <#-- NOTES: Preview & Live links:
        * the preview links must use the *Expanded variable, otherwise will break in future
        * as of 2018-05-07, preview mode "Y" requires cmsAccessToken to also be set
          * cmsAccessToken should also be passed to live link - whether works without depends on website
        * see CmsGetPage.groovy for the secure and extLoginKey logic
        * preview and live support explicit access token param:
            raw(webSiteConfig.accessTokenParamName)+"="+raw(accessToken!)
          but for preview mode can be inlined into the preview mode toggle param (done like that below, to lower URL length)
      -->
    <#assign previewUrl = makeServerUrl({"controller":false, "secure":useSecurePreviewLink, "webSiteId":webSiteId, "extLoginKey": useLinkExtLoginKey, 
        "uri":(raw(pagePrimaryPathExpanded!)+"?"+raw(webSiteConfig.previewModeParamName)+"="+raw(accessToken!)+"&cmsPageVersionId="+raw(versionId!""))})/>
    <#assign liveUrl = makeServerUrl({"controller":false, "secure":useSecureLiveLink, "webSiteId":webSiteId, "extLoginKey": useLinkExtLoginKey,
        "uri":(raw(pagePrimaryPathExpanded!)+(requireLiveAccessToken?then("?"+raw(webSiteConfig.accessTokenParamName)+"="+raw(accessToken!),"")))})/>
    
    <#-- Javascript functions -->
    <@script>
        <@commonCmsScripts />
    
        var pageVersId = '';
        <#-- Activate Page version -->
        function activateVersion(versionId){
            var pageId = $("#pageId").val();
            $.ajax({
                  type: "POST",
                  url: "<@pageUrl escapeAs='js'>activatePageVersion</@pageUrl>",
                  data: {pageId:pageId,versionId:versionId},
                  cache:false,
                  async:true,
                  success: function(data) { 
                      handleCmsEventResponse(data, function(eventMsgs) {
                          doCmsSuccessRedirect("${escapeFullUrl(editPageUrl, 'js')}", eventMsgs);
                      });
                  }
            });
        }
            
        <#-- Save current page -->
        <#-- NOTE: javascript: calls to this must end in void(0) otherwise breaks browsers (firefox) -->
        function addPageVersion(form, activate) {
            if ($("form#editorForm").validate().checkForm() === false) { 
               $('form#editorForm [required]').each(function(){
                  $(this).trigger('blur');
                });
                var def = new $.Deferred();
                def.resolve(false);
                return def;
            } else {
                return $.ajax({
                  type: "POST",
                  url: "<@pageUrl escapeAs='js'>addPageVersion</@pageUrl>",
                  data: getFormData(),
                  cache:false,
                  async:true,
                  success: function(data) { 
                      handleCmsEventResponse(data, function(eventMsgs) {
                          doCmsSuccessRedirect("${escapeFullUrl(editPageUrl, 'js')}", eventMsgs);
                      });
                  }
                });
            }
        }
        
        
        <#-- Save and activate current page -->
        function addAndActivateVersion() {
            pageVersId = '';
            addPageVersion($('form#editorForm'),true).done(function(data){
                if(data.versionId){
                    activateVersion(data.versionId);
                }
            });
        }
                
        <#-- Serialize the content -->
        function getFormData(){
            var tempData = {};
            var tempContent = {};
            
            //serialize form info
            var formInfo = $(".editorPage :input").serializeArray();
            $.each(formInfo, function(i, fd) {
                tempData[fd.name] = fd.value;
            }); 
            
            //serialize the meta information
            var meta = $(".editorMeta :input").serializeArray();
            $.each(meta, function(i, fd) {
                tempContent[fd.name] = fd.value;
            });
            
            //serialize the long page information
            var meta = $(".editorMetaLong :input").serializeArray();
            $.each(meta, function(i, fd) {
                tempContent[fd.name] = fd.value;
            });
                                    
            //serialize the content
            $(".editorAsset").each(function (index, value) { 
                var elId = $(this).attr('scipio-id');
                
                var tempSubAsset = {};
                var content = $(this).find(':input').serializeArray();
                $.each(content, function(i, fd) {
                    var name = fd.name;
                    if (elId) {
                        if (name.indexOf(elId + "_") == 0) { // sanity check
                            name = name.substring(elId.length + 1); // elId + "_"
                            tempSubAsset[name] = fd.value;
                        } else {
                            ; // error, do not commit, something went wrong, storing invalid data
                        }
                    } else {
                        tempSubAsset[name] = fd.value;
                    }
                });
                tempContent[elId] = tempSubAsset;
            });
            tempData['content'] = JSON.stringify(tempContent);
            return tempData;
        }
        
        <#-- Populate from JSON -->
        function populate(frm, data) {   
            $.each(data, function(key, value){  
            var $ctrl = $('[name='+key+']', frm);  
            switch($ctrl.attr("type"))  
            {  
                case "text" :   
                case "hidden":  
                $ctrl.val(value);   
                break;   
                case "radio" : case "checkbox":   
                $ctrl.each(function(){
                   if($(this).attr('value') == value) {  $(this).attr("checked",value); } });   
                break;  
                default:
                $ctrl.val(value); 
            }  
            });  
        }
        
        
        <#-- To be loaded on pageload -->
        $( document ).ready(function() {
            $('.editor').trumbowyg({
                autogrow: true,
                semantic: false,
                btnsDef: {
                    // Customizables dropdowns
                    image: {
                        dropdown: ['insertImage','scipio_media_image','upload','scipio_media_video','scipio_media_audio','scipio_media_file', 'base64', 'noEmbed'], 
                        ico: 'insertImage'
                    },
                    link: {
                        dropdown: [
                            'createLink',
                            'unlink',
                            <#-- DEV NOTE: autourl tries to identify the macro in the given link and
                                open the right dialog; it falls back on createLink. but the ones below are 
                                always needed also, in order to create new links.
                                also, autourl doesn't completely replace the stock 'createLink',
                                because some users may need/want to edit a cmsPageUrl or ofbizUrl using
                                the stock form instead of the helpers. -->
                            'scipio_links_autourl',
                            'scipio_links_cmspageurl', 
                            'scipio_links_ofbizurl', 
                            'scipio_links_ofbizcontenturl'
                        ]
                    }
                },
                btns: [
                    ['viewHTML'],
                    ['formatting'],
                    'btnGrp-semantic',
                    ['superscript', 'subscript'],
                    'btnGrp-justify',
                    'btnGrp-lists',
                    ['link'],
                    ['image'],
                    ['scipio_assets'],
                    ['table'],
                    ['horizontalRule'],
                    ['removeformat'],
                    ['fullscreen']
                ],
                plugins: {
                    // Add imagur parameters to upload plugin
                    scipio_media: {
                        serverPath: '<@pageUrl escapeAs='js'>getMediaFiles</@pageUrl>',
                        mediaUrl: '<@contentUrl escapeAs='js'>/cms/media</@contentUrl>'
                    },
                    scipio_links: {
                        getPagesServerPath: '<@pageUrl escapeAs='js'>getPages</@pageUrl>',
                        getWebSitesServerPath: '<@pageUrl escapeAs='js'>getCmsWebSites</@pageUrl>',
                        <#-- WARN: currentWebSiteId may become problem in future; see js source -->
                        currentWebSiteId: '${escapeVal(webSiteId!, 'js')}'
                    },
                    scipio_assets: {
                        getAssetTypesServerPath: '<@pageUrl escapeAs='js'>getAssetTypes</@pageUrl>',
                        getAssetsServerPath: '<@pageUrl escapeAs='js'>getAssets</@pageUrl>',
                        getAssetAttributesServerPath: '<@pageUrl escapeAs='js'>getAssetAttributes</@pageUrl>',
                        getWebSitesServerPath: '<@pageUrl escapeAs='js'>getCmsWebSites</@pageUrl>',
                        <#-- WARN: currentWebSiteId may become problem in future; see js source -->
                        currentWebSiteId: '${escapeVal(webSiteId!, 'js')}'
                    }
                }
            });
            
            setupCmsDeleteActionHooks();
            
            $( "form#editorForm" ).on( "submit", function( event ) {
              event.preventDefault();
              addPageVersion(this, false);
            });
        });
        
        function updatePageInfo() {
            cmsCheckSubmitFieldOnlyIfChanged('settingsForm', 'description');
            updateCmsElement("<@pageUrl escapeAs='js'>updatePageInfo</@pageUrl>", 'settingsForm', 
                function(eventMsgs) {
                    <#-- use the pageId URL to prevent issues with path change.
                        still need to pass a website. if primary website was changed, use the new one,
                        otherwise use the orig from this page. -->
                    var newUrl = "${escapeFullUrl(editPageByIdUrl, 'js')}";
    
                    var nextWebSiteId = jQuery('#settingsForm select[name=webSiteId]').val();
                    newUrl = updateQueryStringParameter(newUrl, "webSiteId", nextWebSiteId);

                    doCmsSuccessRedirect(newUrl, eventMsgs);
                }
            );
        }
        
        function deletePage() {
            deleteCmsElement("<@pageUrl escapeAs='js'>deletePage</@pageUrl>", 
                { pageId : "${pageId!}" }, 
                function(eventMsgs) {
                    doCmsSuccessRedirect("<@pageUrl escapeAs='js'>pages</@pageUrl>", eventMsgs);
                }
            );
        }
    </@script>
    
    <@modal id="delete-dialog">
        <@heading>${uiLabelMap.CommonWarning}</@heading>
        ${uiLabelMap.CmsConfirmDeleteAction}
        <div class="modal-footer ${styles.text_right}">
           <a id="delete-button" class="${styles.button} btn-ok">${uiLabelMap.CommonContinue}</a>
        </div>
    </@modal>
    <#-- Content -->
    <#macro menuContent menuArgs={}>
        <@menu args=menuArgs>
            <@menuitem type="link" href=makePageUrl("editPage") class="+${styles.action_nav!} ${styles.action_add!}" text=uiLabelMap.CmsNewPage/>
            <@cmsCopyMenuItem target="copyPage" title=uiLabelMap.CmsCopyPage>
                 <@field type="hidden" name="pageId" value=(pageId!)/><#-- for browsing, on error -->
                 <@field type="hidden" name="versionId" value=(versionId!)/><#-- for browsing, on error -->
                 <@field type="hidden" name="webSiteId" value=((meta.webSiteId)!)/>
                 <@field type="hidden" name="srcPageId" value=(pageId!)/>
                 <@cmsCopyVersionSelect versionId=(versionId!)/>
                 <@field type="input" name="primaryPath" value="" label=uiLabelMap.CommonPath placeholder=((meta.primaryPath!pathPlaceholder)) required=true/>
                 <#-- NOTE: the select below is currently for debugging only - not sure if want to expose
                    to users here yet (though should be working) -->
                 <#if debugMode>
                   <@field type="select" name="primaryPathFromContextRoot" label="primaryPathFromContextRoot" required=true>
                      <option value="" selected="selected"></option>
                      <option value="Y">Y</option>
                      <option value="N">N</option>
                   </@field>
                 <#else>
                   <#-- NOTE: do not use primaryPathFromContextRootDefault here; leave this empty (the default setting 
                       is for each webapp, target service must look it up) -->
                   <input type="hidden" name="primaryPathFromContextRoot" value=""/>
                 </#if>
                 <@field type="input" name="pageName" value="" label=uiLabelMap.CommonName required=false/>
                 <@field type="textarea" name="description" value=(meta.description!"") label=uiLabelMap.CommonDescription required=false/>
            </@cmsCopyMenuItem>
            <@menuitem type="generic">
                <@modal id="modal_new_script" label=uiLabelMap.CmsAddScript linkClass="+${styles.menu_button_item_link!} ${styles.action_nav!} ${styles.action_add!}">
                    <@heading>${uiLabelMap.CmsAddScript}</@heading>
                    <@fields type="default-compact">
                        <@cmsScriptTemplateSelectForm formAction=makePageUrl("addScriptToPage") webSiteId=((meta.webSiteId)!)>
                            <input type="hidden" name="pageId" value="${pageId!}" />
                        </@cmsScriptTemplateSelectForm>
                    </@fields>
                </@modal>
            </@menuitem>
            
            <#-- NOTE: void(0) MUST be at the end to prevent browser failure -->
            <@menuitem type="link" href="javascript:addPageVersion($('form#editorForm'),false); void(0);" class="+${styles.action_run_sys!} ${styles.action_update!}" text=uiLabelMap.CommonSave/>
            <@menuitem type="link" href="javascript:activateVersion($('#versionId').val()); void(0);" class="+${styles.action_run_sys!} ${styles.action_update!}" text=uiLabelMap.CmsPublish/>
            <@menuitem type="link" href="javascript:addAndActivateVersion(); void(0);" class="+${styles.action_run_sys!} ${styles.action_add!}" text=uiLabelMap.CmsSaveAndPublish/>
            <#if previewUrl?has_content && webSiteAllowPreview>
              <@menuitem type="link" href=previewUrl class="+${styles.action_run_sys!} ${styles.action_show!}" target="_blank" text=uiLabelMap.CmsPreview/>
            <#else>
              <#-- FIXME?: ideally should make a way to make preview mode secure even on live... -->
              <#if !webSiteAllowPreview>
                <#assign previewLabel = rawLabel('CmsPreview') + " (!)">
                <#-- DEV NOTE: I did a modal due to tooltip issues and because it's clearer in the end -->
                <@menuitem type="generic">
                    <@modal id="modal_preview_disabled" label=previewLabel linkClass="+${styles.menu_button_item_link!} ${styles.action_nav!} ${styles.action_show!} ${styles.disabled}">
                        <p>${uiLabelMap.CmsWebSitePreviewDisabledDesc}</p>
                    </@modal>
                </@menuitem>
              <#else>
                <#assign previewLabel = rawLabel('CmsPreview')>
                <@menuitem type="link" disabled=true class="+${styles.action_run_sys!} ${styles.action_show!}" text=previewLabel/>
              </#if>
            </#if>
            <#if liveUrl?has_content>
              <@menuitem type="link" href=liveUrl class="+${styles.action_run_sys!} ${styles.action_show!}" target="_blank" text=uiLabelMap.CmsViewLive/>
            <#else>
              <@menuitem type="link" disabled=true class="+${styles.action_run_sys!} ${styles.action_show!}" text=uiLabelMap.CmsViewLive/>
            </#if>
            <@menuitem type="link" href="javascript:deletePage(); void(0);" class="+${styles.action_run_sys!} ${styles.action_remove!} action_delete" text=uiLabelMap.CommonDelete/>
            <@cmsObjectExportFormMenuItem presetConfigName="CmsPages" pkField="CmsPage" pkValue=(pageId!)><#-- this could be used to prevent output the mappings, but it's finicky: enterPresetConfigName="CmsPagesStrict" -->
                <@field type="checkbox" label=uiLabelMap.CmsIncludePageControlMappingsOnly size="20" required=false name="enterMajorPresetConfigName" value="CmsPagesMappings" checked=false
                    onChange="if (jQuery(this).is(':checked')) jQuery(this).closest('form').find('input[name=includeMajorDeps]').prop('checked', true);"/>
            </@cmsObjectExportFormMenuItem>
        </@menu>  
    </#macro>
    <@section menuContent=menuContent class="+cms-edit-elem cms-edit-page"><#-- FIXME: get these classes on <body> instead... -->
            <@row>
                <@cell columns=12>
                    <a href="${escapeFullUrl(previewUrl, 'html')}" target="_blank"<#if (meta.description)?has_content> class="${styles.tooltip!}" title="${meta.description}"</#if>><@heading level=1><@serverUrl uri=raw(pagePrimaryPathExpanded!'') controller=false webSiteId=webSiteId escapeAs='html'/></@heading></a>
                </@cell>
            </@row>
            <@row>
                <@cell columns=9>
                  <@form method="post" id="editorForm">
                    <#-- Page Info -->
                    <@field type="hidden" name="versionId" id="versionId" value=versionId!""/>    
                    
                    <#-- Page Content -->
                    <#if template?has_content && template.assets?has_content>
                        <@section title=uiLabelMap.CmsContent class="+editorContent">
                          <@fields type="default-compact">
                            <#assign isFirstAsset = true>
                            <#list template.assets as asset>
                                <#if asset.attributes?has_content>
                                    <#if !isFirstAsset><hr/></#if>
                                    <@section title=((asset.assoc.displayName)!asset.name!"") containerClass="+editorAttribGroup editorAsset" containerAttribs={"scipio-id":asset.importName}>
                                        <#list asset.attributes as attribute>
                                            <#-- NOTE: because of the way the ofbiz renderer implements auto escaping, must use rawString when indexing here -->
                                            <@pageAttrField namePrefix=(asset.importName+"_") fieldObj=attribute value=(content[raw(asset.importName!)][raw(attribute.name!)])!/>
                                        </#list>
                                    </@section>
                                    <#assign isFirstAsset = false>
                                </#if>
                            </#list>
                            
                            <#-- 2017-03-17: render LONG_TEXT template attributes here, because there's no space in the right column... -->
                            <#if template.attributes?has_content>
                              <#if !isFirstAsset><hr/></#if>
                              <@section title=uiLabelMap.CommonPage containerClass="+editorAttribGroup editorMetaLong">
                                <#list template.attributes as attribute>
                                  <#if (attribute.type!) == "LONG_TEXT">
                                    <#-- NOTE: because of the way the ofbiz renderer implements auto escaping, must use rawString when indexing here -->
                                    <@pageAttrField fieldObj=attribute value=content[raw(attribute.name!)]!/>
                                  </#if>                          
                                </#list>
                              </@section>
                            </#if>
                          </@fields>
                        </@section>
                    </#if>              
                    <#--<@field type="submit" text=uiLabelMap.CommonSave class="+${styles.link_run_sys!} ${styles.action_update!}"/>-->
                    
                    <#-- Scripts -->
                    <#-- NOTE: because this page has huge form, the forms have to go further down... -->
                    <@cmsScriptTemplateAssocTable includeForms=false scriptTemplates=(scriptTemplates![])
                        updateAction="updatePageScript" updateFields={"pageId":pageId!}
                        deleteAction="deleteScriptAndPageAssoc"/>
                    
                    <#-- Active Version -->
                    <@section title=uiLabelMap.CommonRevisions>
                            <@table type="data-complex" autoAltRows=true responsive=true scrollable=true fixedColumnsRight=1>
                                <@thead>
                                    <@tr>
                                        <@th width="32px"></@th>
                                        <@th width="200px"></@th>
                                        <@th width="200px"></@th>
                                        <@th></@th>
                                        <@th width="32px"></@th>
                                        <@th width="32px"></@th>
                                    </@tr>
                                </@thead>
                                <@tbody>
                                    <#list meta.versions as version>
                                        <#assign rowSelected = (versionId == (version.id!""))>
                                        <#-- FIXME?: remove the alt and style using selected only? -->
                                        <@tr alt=rowSelected selected=rowSelected>
                                           <@td><i class="${styles.text_color_info} ${styles.icon!} ${styles.icon_user!}" style="font-size:16px;margin:4px;"></i></@td>
                                           <@td> ${version.createdBy!"Anonymous"}</@td>
                                           <#assign verLinkMkrp><@pageUrl escapeAs="html">editPage?pageId=${escapeVal(pageId!, 'url')}&versionId=${escapeVal(version.id!, 'url')}</@pageUrl></#assign>
                                           <@td><#if version.date?has_content><a href="${verLinkMkrp}">${raw(version.date)?datetime}</a></#if></@td>
                                           <@td><#if version.comment?has_content>${version.comment!""}</#if></@td>
                                           <@td><a href="${verLinkMkrp}"><i class="${styles.text_color_info} ${styles.icon!} ${styles.icon_edit!}" style="font-size:16px;margin:4px;"></i></a></@td>
                                           <@td><#if version.active==true><i class="${styles.text_color_success} ${styles.icon!} ${styles.icon_check!}" style="font-size:16px;margin:4px;"></i></#if></@td>
                                        </@tr>
                                    </#list>
                                </@tbody>
                            </@table>
                    </@section>
                    
                    <#-- Page Information -->
                    <div class="editorPage" style="display:none;">
                        <@field type="hidden" name="pageId" value=(pageId!) id="pageId" />
                        <#if meta?has_content>
                            <@field type="hidden" name="pageTemplateId" value=(meta.pageTemplateId!) id="pageTemplateId" />
                            <@field type="hidden" name="webSiteId" value=(meta.webSiteId!) id="webSiteId" />
                        </#if>
                    </div>
                  </@form>
                  
                  <#-- Script forms -->
                  <@cmsScriptTemplateAssocTableForms scriptTemplates=(scriptTemplates![])
                        updateAction="updatePageScript" updateFields={"pageId":pageId!}
                        deleteAction="deleteScriptAndPageAssoc"/>
                </@cell>
                <@cell columns=3>
                    <#if template?has_content>
                      <@form method="post" id="settingsForm" action=makePageUrl('updatePageInfo')>
                        <@field type="hidden" name="pageId" value=(pageId!)/>
                        <@section title=uiLabelMap.CommonInformation>
                            <#-- Page Settings -->
                            <@section title=uiLabelMap.CommonSettings>
                                 <@field label=uiLabelMap.CommonId type="display" value=meta.id!""/>
                                 
                                 <#-- NOTE: TODO?: the schema actually supports multiple primary mappings for multiple website support.
                                    this control can't handle that case, e.g. we assume that meta.defaultWebSiteId==meta.webSiteId. -->
                                 <@webSiteSelectField label="${rawLabel('CmsWebSite')} (${rawLabel('CmsPrimary')})" name="webSiteId" required=true value=((meta.webSiteId)!)
                                    tooltip="${rawLabel('CmsOnlyHookedWebSitesListed')}}"/>
                          
                               <#if meta.candidateWebSiteIds?has_content && ((meta.candidateWebSiteIds?size > 1) || (meta.candidateWebSiteIds?first != ((meta.webSiteId)!)))>
                                 <@field label="${rawLabel('ContentWebSites')}" type="display" tooltip=uiLabelMap.CmsPageMultipleWebsitesInfo><@compress_single_line>
                                    <#-- 2016: if page is associated to more websites, show them, even if UI may not yet allow creating these assocs at this time 
                                        (help debug and correct bad data if nothing else) -->
                                      <#list meta.candidateWebSiteIds as canWebSiteId>
                                          ${canWebSiteId}<#if canWebSiteId_has_next>, </#if><#t>
                                      </#list>
                                 </@compress_single_line></@field>
                               </#if>
                               
                                 <@field label=uiLabelMap.CommonPath type="input" name="primaryPath" value=(meta.primaryPath!"") required=true/>
                                 <#-- TODO: unhardcode this -->
                                 <#assign primaryTargetPath = raw(meta.primaryTargetPath!"")>

                                 <@field type="select" name="primaryTargetPath" id="primaryTargetPath" items=primaryTargetPathOptions currentValue=primaryTargetPath label="${rawLabel('CommonPath')} ${rawLabel('CommonSettings')}" required=false />

                                 <#if meta.pageTemplateId?has_content>
                                   <#assign pgTmplLabel>${uiLabelMap.CmsTemplate} <#t>
                                     <#-- DEV NOTE: I left out the target="_blank" because template changes require reloading this page afterward anyway, so fewer tab-related errors? --><#t/>
                                     <a href="<@pageUrl uri='editTemplate?pageTemplateId='+raw(meta.pageTemplateId)/>"><#t><#-- target="_blank" -->
                                     <i class="${styles.text_color_info} ${styles.icon!} ${styles.icon_edit!}" style="font-size:16px;margin:4px;"></i></a></#assign>
                                 <#else>
                                   <#assign pgTmplLabel>${uiLabelMap.CmsTemplate}</#assign>
                                 </#if>
                                 <@field type="select" name="pageTemplateId" labelContent=pgTmplLabel required=true><#-- duplicate: id="pageTemplateId" -->
                                   <#assign pageTmplFoundInSelect = false>
                                   <#assign pageTmplSelOpts>
                                     <#if availableTemplates?has_content>
                                       <#list availableTemplates as tmpl>
                                         <#assign pageTmplSelected = (raw(tmpl.pageTemplateId!"") == raw(meta.pageTemplateId!""))>
                                         <#if pageTmplSelected>
                                           <#assign pageTmplFoundInSelect = true>
                                         </#if>
                                         <option value="${tmpl.pageTemplateId!""}"<#if pageTmplSelected> selected="selected"</#if>>${tmpl.templateName!tmpl.pageTemplateId!""}</option>
                                       </#list>
                                     </#if>
                                   </#assign>
                                   <#if !pageTmplFoundInSelect>
                                     <option value="${meta.pageTemplateId!""}" selected="selected">${(delegator.findOne("CmsPageTemplate", {"pageTemplateId": meta.pageTemplateId!""}, false).templateName)!meta.pageTemplateId!""}</option>
                                   </#if>
                                   ${pageTmplSelOpts}
                                 </@field>

                                 <@field label=uiLabelMap.CommonName type="input" name="pageName" value=(meta.name!"") required=false/>
                                 <@field label=uiLabelMap.CommonDescription type="textarea" name="description_visible" value=(meta.description!"") required=false/>

                                 <@field label=uiLabelMap.CommonIndexable type="select" name="primaryPathIndexable" value=(meta.primaryPathIndexable!"") required=false tooltip=uiLabelMap.CmsMappingIndexableDesc>
                                    <#assign indexable = meta.primaryPathIndexable!"">
                                    <#assign indexableDefault = (webSiteConfig.getMappingsIndexableDefault())!true>
                                    <#-- NOTE: this indicator is ternary, when empty it defaults to cmsDefaultIsIndexable in web.xml.
                                        it must not be forced to Y or N (e.g., do NOT make this a checkbox). -->
                                    <@field type="option" value="" selected=(!indexable?is_boolean)>(${indexableDefault?string("Y", "N")})</@field>
                                    <@field type="option" value="Y" selected=(indexable?is_boolean && indexable)>Y</@field>
                                    <@field type="option" value="N" selected=(indexable?is_boolean && !indexable)>N</@field>
                                 </@field>

                                 <@field label=uiLabelMap.FormFieldTitle_txTimeout type="input" name="txTimeout" value=(meta.txTimeout!"") required=false tooltip=uiLabelMap.CmsPageTxTimeoutInfo/>

                                 <@menu type="button">
                                    <@menuitem type="link" href="javascript:updatePageInfo(); void(0);" class="+${styles.action_run_sys!} ${styles.action_update!}" text="${rawLabel('CmsSaveSettings')}" />
                                 </@menu> 
                            </@section>
                            
                            <@section title=uiLabelMap.CommonStatus>
                                 <@field label=uiLabelMap.CommonStatus type="display" value=((meta.status!false)?string(uiLabelMap.CmsStatusPublished,uiLabelMap.CmsStatusUnpublished))/>

                                 <#assign pageRevisions><@compress_single_line>
                                     <#if meta?has_content && meta.versions?has_content>
                                        ${meta.versions?size}
                                        <#else>
                                        0
                                    </#if></@compress_single_line>    
                                 </#assign>
                                 <@field label=uiLabelMap.CmsRevisions type="display" value=pageRevisions/>
                            </@section>
                            
                          <#if template.attributes?has_content>
                            <#-- Page Meta Information -->
                            <@section title=uiLabelMap.CmsPageMeta class="+editorMeta">   
                              <@fields type="default"> 
                                <#-- 2017-02-10: now redundant due to being under settings as select                
                                <@field type="display" name="templateName" value=(template.name!) disabled=true label=uiLabelMap.CmsTemplate/>-->
                                <#list template.attributes as attribute>
                                  <#-- 2017-03-17: DO NOT render the LONG_TEXT attributes here - there's no space! -->
                                  <#if (attribute.type!) != "LONG_TEXT">
                                    <#-- NOTE: because of the way the ofbiz renderer implements auto escaping, must use rawString when indexing here -->
                                    <@pageAttrField fieldObj=attribute expandLangVisible=false value=(content[raw(attribute.name!)])!/>
                                  </#if>                          
                                </#list>
                              </@fields>
                            </@section>
                          </#if>
                        </@section>
                      </@form>
                      
                      <#if meta.id?has_content>
                        <#assign sectionTitleHtmlMarkup>${getLabel('CmsViewMappings')} <a href="javascript:void(0);" class="${styles.tooltip!}" title="${uiLabelMap.CmsSimpleViewMappingsExplanation}">(?)</a></#assign>
                        <@section title=wrapAsRaw({'raw':rawLabel('CmsViewMappings'), 'htmlmarkup':sectionTitleHtmlMarkup})>
                            <@render resource="component://cms/widget/CommonScreens.xml#CmsPageViewMappingsSelect" 
                                ctxVars={"pageId":meta.id, "webSiteId":webSiteId, "editPageUrl":editPageUrl!""} asString=true />
                        </@section>
                      </#if>
                          
                    </#if>
                </@cell>  
            </@row>
        
    </@section>
  <#else>
    <#-- at least prevents crash -->
    <@alert type="error">${uiLabelMap.CommonNo} ${uiLabelMap.CmsWebSite}!</@alert>
  </#if>
<#else>
    <@section>
        <#-- NEW PAGE -->
        <@row>
            <@cell columns=6 last=true>
                <@form method="post" id="editorForm" action=makePageUrl("createPage")>
                    <@section title=uiLabelMap.CmsNewPage>
                      <@fields type="default-compact">
                         <input type="hidden" name="isCreate" value="Y"/>
                         <@webSiteSelectField name="webSiteId" value=(parameters.webSiteId!) valueUnsafe=true required=true 
                            tooltip="${rawLabel('CmsOnlyHookedWebSitesListed')}"/>
                         
                         <@field type="input" name="path" value=(parameters.path!) id="path" label=uiLabelMap.CommonPath placeholder=pathPlaceholder required=true/><#-- TODO: rename to primaryPath -->

                         <@field type="select" name="primaryTargetPath" id="primaryTargetPath" items=primaryTargetPathOptions defaultValue="/cmsPageSecureNoAuth" label="${rawLabel('CommonPath')} ${rawLabel('CommonSettings')}" required=false />

                         <@field type="select" name="pageTemplateId" id="pageTemplateId" label=uiLabelMap.CmsTemplate required=true>
                            <option value="">${uiLabelMap.CmsSelectTemplate}</option>
                            <#if availableTemplates?has_content>
                                <#list availableTemplates as tmpl><option value="${tmpl.pageTemplateId!""}">${tmpl.templateName!tmpl.pageTemplateId!""}</option></#list>
                            </#if>
                         </@field>
                         
                         <#-- NOTE: the select below is currently for debugging only - not sure if want to expose
                            to users here yet (though should be working) -->
                         <#if debugMode>
                           <@field type="select" name="primaryPathFromContextRoot" id="primaryPathFromContextRoot" label="primaryPathFromContextRoot" required=true>
                              <option value="" selected="selected"></option>
                              <option value="Y">Y</option>
                              <option value="N">N</option>
                           </@field>
                         <#else>
                           <#-- NOTE: do not use primaryPathFromContextRootDefault here; leave this empty (the default setting 
                               is for each webapp, target service must look it up) -->
                           <input type="hidden" name="primaryPathFromContextRoot" value=""/>
                         </#if>
                         
                         <@field type="input" name="pageName" value=(parameters.pageName!) id="pageName" label=uiLabelMap.CommonName required=false/>
                         <@field type="textarea" name="description" value=(parameters.description!) label=uiLabelMap.CommonDescription required=false/>

                         <@field type="submit" text=uiLabelMap.CommonCreate class="+${styles.link_run_sys!} ${styles.action_add!}"/>
                       </@fields>
                    </@section>
                </@form>
            </@cell>
        </@row>
    </@section>
</#if>


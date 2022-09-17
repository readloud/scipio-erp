<#-- TODO: License -->

<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->

<#if !productCategory?has_content>
    <#if productCategoryId?has_content>        
        <#assign formAction><@pageUrl>createProductCategory</@pageUrl></#assign>
    <#else>
        <#assign formAction><@pageUrl>createProductCategory</@pageUrl></#assign>
    </#if>
<#else>
    <#assign formAction><@pageUrl>updateProductCategory</@pageUrl></#assign>
</#if>

<@section id="EditProductCategory">
    <form action="${formAction}" method="post" name="productCategoryForm">
    
      <#if !productCategory?has_content>
        <input type="hidden" name="isCreate" value="true" />
      </#if>

      <#if productCategory?has_content>
        <input type="hidden" name="productCategoryId" value="${productCategoryId}"/>
      </#if>
      <@fields type="default">
        <#--
         <@row>
            <@cell columns=12>
              <#if !productCategory?has_content>
                <#if productCategoryId?has_content>
                  <@field type="input" label=uiLabelMap.CommonId name="productCategoryId" size="20" maxlength="40" value=productCategoryId/>
                <#else>
                  <@field type="input" label=uiLabelMap.CommonId name="productCategoryId" size="20" maxlength="40" value=""/>
                </#if>
              <#else>
                <@field type="display" label=uiLabelMap.CommonId>
                  <b>${productCategoryId}</b>
                </@field>
              </#if>
            </@cell>
        </@row>-->
        <@row>       
            <@cell columns=12>
                <@field type="select" label=uiLabelMap.CommonType name="productCategoryTypeId" size="1">
                    <#assign selectedKey = "">
                    <#list productCategoryTypes as productCategoryTypeData>
                        <#if requestParameters.productCategoryTypeId?has_content>
                            <#assign selectedKey = requestParameters.productCategoryTypeId>
                        <#elseif (productCategory?has_content && (productCategory.productCategoryTypeId!) == productCategoryTypeData.productCategoryTypeId)>
                            <#assign selectedKey = productCategory.productCategoryTypeId>
                        </#if>
                        <option<#if selectedKey == (productCategoryTypeData.productCategoryTypeId!)> selected="selected"</#if> value="${productCategoryTypeData.productCategoryTypeId}">${productCategoryTypeData.get("description",locale)}</option>
                    </#list>
                </@field>
            </@cell>
        </@row>
        <@row>
            <@cell columns=12>
              <@field type="input" label=uiLabelMap.CommonName value=((productCategory.categoryName)!) name="categoryName" size="60" maxlength="60" tooltip=uiLabelMap.ProductFieldNonLocalizedSeeContentPageForLocalizedFields/>
            </@cell>
        </@row>
        <@row>
            <@cell columns=12>
              <@field type="lookup" label=uiLabelMap.CommonParent value=(productCategory.primaryParentCategoryId)?default('') formName="productCategoryForm" name="primaryParentCategoryId" id="primaryParentCategoryId" fieldFormName="LookupProductCategory"/>
            </@cell>
        </@row>
        <@row>
            <@cell columns=12>
              <#assign fieldValue = "">
              <#if productCategory?has_content>
                <#assign fieldValue = productCategory.detailScreen!>
              </#if>
              <#-- SCIPIO: Now points to shop -->
              <@field type="input" label=uiLabelMap.ProductDetailScreen name="detailScreen" size="60" maxlength="250" value=fieldValue 
                tooltip="${rawLabel('ProductDefaultsTo')} \"categorydetail\", ${rawLabel('ProductDetailScreenMessage')}: \"component://shop/widget/CatalogScreens.xml#categorydetail\""/>
            </@cell>
        </@row>
        <@row>
            <@cell columns=12>
              <@field type="textarea" label=uiLabelMap.CommonDescription name="description" cols="60" rows="2" tooltip=uiLabelMap.ProductFieldNonLocalizedSeeContentPageForLocalizedFields><#if productCategory?has_content>${(productCategory.description)!}</#if></@field>
            </@cell>
        </@row>

    <div id="ImageFields">
        <@row>
            <@cell columns=12>
              <@field type="generic" label=uiLabelMap.ProductCategoryImageUrl>
                <@field type="input" name="categoryImageUrl" value=((productCategory.categoryImageUrl)!) size="60" maxlength="255"/>               
              </@field>
            </@cell>
        </@row>
        <#if (productCategory.categoryImageUrl)??>
            <@row>
                <@cell columns=2>
                </@cell>
                <@cell columns=10>
                    <@img src=makeContentUrl((productCategory.categoryImageUrl)!) target="_blank" width="400px"/>
                </@cell>
            </@row>
        </#if>

        <#-- SCIPIO: deprecated -->
        <#if (productCategory.linkOneImageUrl)??>
            <@row>
                <@cell columns=12>
                  <@field type="generic" label=uiLabelMap.ProductLinkOneImageUrl>
                    <@field type="input" name="linkOneImageUrl" value=((productCategory.linkOneImageUrl)!) size="60" maxlength="255"/>
                  </@field>
                </@cell>
            </@row>

            <#if (productCategory.linkOneImageUrl)??>
                <@row>
                    <@cell columns=2>
                    </@cell>
                    <@cell columns=10>
                        <@img src=makeContentUrl((productCategory.linkOneImageUrl)!) target="_blank" width="400px"/>
                    </@cell>
                </@row>
            </#if>
        </#if>
        <#-- SCIPIO: deprecated -->
        <#if (productCategory.linkTwoImageUrl)??>
            <@row>
                <@cell columns=12>
                  <@field type="generic" label=uiLabelMap.ProductLinkTwoImageUrl>
                    <@field type="input" name="linkTwoImageUrl" value=((productCategory.linkTwoImageUrl)!) size="60" maxlength="255"/>                
                  </@field>
                </@cell>
            </@row>

            <#if (productCategory.linkTwoImageUrl)??>
                <@row>
                    <@cell columns=2>
                    </@cell>
                    <@cell columns=10>
                        <@img src=makeContentUrl((productCategory.linkTwoImageUrl)!) target="_blank" width="400px"/>
                    </@cell>
                </@row>
            </#if>
        </#if>
    </div>
        <@row>
            <@cell>
                <#if productCategory?has_content>
                    <@field type="submit" name="Update" text=uiLabelMap.CommonUpdate class="+${styles.link_run_sys!} ${styles.action_update!}"/>
                <#else>
                    <@field type="submit" name="Create" text=uiLabelMap.CommonCreate class="+${styles.link_run_sys!} ${styles.action_add!}"/>
                </#if>
            </@cell>
        </@row>
      </@fields>
    </form>
</@section>
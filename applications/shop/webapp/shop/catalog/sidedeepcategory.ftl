<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->
<#include "component://shop/webapp/shop/catalog/catalogcommon.ftl">

<#-- variable setup and worker calls -->
<#macro categoryList productCategoryId level isMultiLevel path count class="" previousCategoryId="" catInfo=true>
    <#if catInfo?is_boolean>
      <#local catInfo = catHelper.makeCategoryInfo(productCategoryId)>
    </#if>
    <#-- SCIPIO: sometimes this happens for reasons I'm not sure... just prevent it here -->
    <#if previousCategoryId == productCategoryId>
      <#local previousCategoryId = "">
    </#if>
    <#-- these were already looked up in catInfo
    <#assign productCategory = delegator.findOne("ProductCategory", {"productCategoryId" : productCategoryId}, true)/>
    <#assign contentCategoryName = Static["org.ofbiz.product.category.CategoryContentWrapper"].getProductCategoryContentAsText(productCategory, "CATEGORY_NAME", locale, dispatcher, "raw")!>
    <#assign contentCategoryDesc = Static["org.ofbiz.product.category.CategoryContentWrapper"].getProductCategoryContentAsText(productCategory, "DESCRIPTION", locale, dispatcher, "raw")!>    
    -->
    <#assign productCategory = catInfo.productCategory!"">
    <#assign contentCategoryName = catInfo.categoryName!"">
    <#assign contentCategoryDesc = catInfo.description!"">
    <#assign isOnCurrentCatPath = urlContainsPathPart(raw(currentCategoryPath!""), productCategoryId)>
    <#assign active = false>
    <#if (curCategoryId?has_content && curCategoryId == productCategoryId) || isOnCurrentCatPath>
      <#assign active = true>
    </#if>
    <#assign activeTarget = false>
    <#if curCategoryId?has_content && curCategoryId == productCategoryId>
      <#assign activeTarget = true>
    </#if>
    <#assign categoryUrl><@catalogUrl rawParams=true currentCategoryId=productCategoryId previousCategoryId=previousCategoryId/></#assign>
    <#assign linkText>${raw(catInfo.displayName!"")} <#if (count?number > 0)>(${count})</#if></#assign>
    <#local class = addClassArg(class, "menu-${level}")>
    <@menuitem type="link" href=categoryUrl text=linkText class=class active=active activeTarget=activeTarget>
      <#if isMultiLevel>
        <#-- SCIPIO: NOTE: this code does not work properly, use urlContainsPathPart
        <#if currentCategoryPath.contains("/"+productCategoryId)>-->
        <#if isOnCurrentCatPath>
            <#assign nextLevel=level+1/>
            <#if catList.get("menu-"+nextLevel)?has_content>
                <#assign nextList = catList.get("menu-"+nextLevel) />
                <@iterateList currentList=nextList currentLevel=nextLevel isMultiLevel=true previousCategoryId=productCategoryId/>
            </#if>
        </#if>
      </#if>
    </@menuitem>        
</#macro>

<#macro iterateList currentList currentLevel isMultiLevel previousCategoryId="">
        <#-- SCIPIO: NOTE: this will automatically figure out it's a nested menu and it will inherit the type of the parent -->
        <@menu id="menu-${currentLevel!0}">
          <#list catHelper.makeCategoryInfos(currentList)?sort_by("displayName") as catInfo>
              <#local item = catInfo.item>
              <@categoryList catInfo=catInfo productCategoryId=item.catId level=currentLevel!0 isMultiLevel=isMultiLevel path=item.path!"" count=item.count previousCategoryId=previousCategoryId/>
          </#list>
        </@menu>
</#macro>

<#if catList?has_content || topLevelList?has_content>
    <@menu id="menu-0" type="sidebar">
        <#-- NOTE: don't use has_content on this for now, empty list means no sub-cats, missing list means no top cat selected -->
        <#if catList?? && catList.get("menu-0")??><#-- SCIPIO: Display each categoryItem -->
          <#-- current categories -->
          <@categoryList productCategoryId=baseCategoryId level=0 isMultiLevel=false path="" count=0 class=styles.menu_sidebar_itemdashboard! />
          <#list catHelper.makeCategoryInfos(catList.get("menu-0"))?sort_by("displayName") as catInfo>
              <#-- SCIPIO: sanity check - each item should have as parent the top category - should be able to remove this check later for speed, 
                  but helps in case something went wrong with query -->
              <#assign item = catInfo.item>
              <#if Static["org.ofbiz.product.category.CategoryWorker"].isCategoryChildOf(delegator, dispatcher, baseCategoryId, catInfo.productCategoryId)>
                <@categoryList catInfo=catInfo productCategoryId=item.catId level=0 isMultiLevel=true path=item.path!"" count=item.count previousCategoryId=baseCategoryId!""/>
              <#else>
                <#assign dummy = Debug.logWarning("Scipio: WARN: Side deep category " + raw(item.catId!) + 
                    " not child of base category " + raw(baseCategoryId!"") + "; discarding", "sidedeepcategory.ftl")!>
              </#if>
          </#list>
        <#elseif topLevelList?has_content><#-- SCIPIO: Fallback for empty categories / catalogs -->
          <#-- top categories -->
          <#list catHelper.makeCategoryInfos(topLevelList) as catInfo>
              <@categoryList catInfo=catInfo productCategoryId=catInfo.productCategoryId level=0 isMultiLevel=false path="" count=0 previousCategoryId=""/>
          </#list>
        </#if>
    </@menu>
</#if>

<#-- currentCategoryPath: ${currentCategoryPath!"(none)"} -->
<#-- baseCategoryId: ${baseCategoryId!"(none)"} -->


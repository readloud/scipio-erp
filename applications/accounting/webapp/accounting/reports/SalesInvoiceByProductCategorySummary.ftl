<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->

<#macro resultSummary resultMap>
    <#if resultMap?has_content>
        ${resultMap.quantityTotal!0}:${resultMap.amountTotal!0}:<#if (resultMap.quantityTotal?? && resultMap.quantityTotal > 0)>${resultMap.amountTotal/resultMap.quantityTotal}<#else>0</#if>
    <#else>
        0:0:0
    </#if>
</#macro>



<ul>
    <li>Month: ${month}/${year}</li>
    <#if rootProductCategory?has_content>    
        <li>Root Category: ${Static["org.ofbiz.product.category.CategoryContentWrapper"].getProductCategoryContentAsText(rootProductCategory, "CATEGORY_NAME", locale, dispatcher, "raw")!} [${rootProductCategoryId}]</li>
    </#if>
    <li>Organization: ${(organizationPartyName.groupName)!} [${organizationPartyId?default("No Organization Specified")}]</li>
    <li>Currency: ${(currencyUom.description)!} [${currencyUomId?default("No Currency Specified")}]</li>
</ul>
<div>NOTE: each set of numbers is: &lt;quantity&gt;:&lt;total amount&gt;:&lt;average amount&gt;</div>
<@table type="data-complex">
    <@thead>
        <@tr class="header-row">
            <@th>Day</@th>
            <@th>[No Product]</@th>
            <#list productList as product>
                <@th>${product.internalName!(Static["org.ofbiz.product.product.ProductContentWrapper"].getProductContentAsText(product, "PRODUCT_NAME", locale, dispatcher, "raw")!)}<br />P:[${product.productId}]</@th>
            </#list>
            <#list productCategoryList as productCategory>
                <@th>${Static["org.ofbiz.product.category.CategoryContentWrapper"].getProductCategoryContentAsText(productCategory, "CATEGORY_NAME", locale, dispatcher, "raw")!}<br />C:[${productCategory.productCategoryId}]</@th>
            </#list>
        </@tr>
    </@thead>
    <#-- Days of the month -->
    <#list productNullResultByDayList as productNullResult>
        <#assign productResultMap = productResultMapByDayList.get(productNullResult_index)/>
        <#assign categoryResultMap = categoryResultMapByDayList.get(productNullResult_index)/>

        <#-- now do the null product, then iterate through the products, then categories -->
        <@tr>
            <@td>${(productNullResult_index + 1)}</@td>
            <@td><@resultSummary resultMap=productNullResult/></@td>
            <#list productList as product>
                <#assign productResult = productResultMap[product.productId]!/>
                <@td><@resultSummary resultMap=productResult/></@td>
            </#list>
            <#list productCategoryList as productCategory>
                <#assign categoryResult = categoryResultMap[productCategory.productCategoryId]!/>
                <@td><@resultSummary resultMap=categoryResult/></@td>
            </#list>
        </@tr>
    </#list>

    <#-- Totals for the month -->
    <@tr>
        <@td>Month Total</@td>
        <@td><@resultSummary resultMap=monthProductNullResult/></@td>
        <#list productList as product>
            <#assign productResult = monthProductResultMap[product.productId]!/>
            <@td><@resultSummary resultMap=productResult/></@td>
        </#list>
        <#list productCategoryList as productCategory>
            <#assign categoryResult = monthCategoryResultMap[productCategory.productCategoryId]!/>
            <@td><@resultSummary resultMap=categoryResult/></@td>
        </#list>
    </@tr>
</@table>

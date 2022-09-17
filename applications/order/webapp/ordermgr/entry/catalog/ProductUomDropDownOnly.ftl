<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->
<#if product?? && mainProducts??>
    <select name="parentProductId" onchange="javascript:displayProductVirtualVariantId(this.value);">
        <option value="">Select Unit Of Measure</option>
        <#list mainProducts as mainProduct>
            <option value="${mainProduct.productId}">${mainProduct.uomDesc} : ${mainProduct.piecesIncluded}</option>
        </#list>
    </select>
</#if>
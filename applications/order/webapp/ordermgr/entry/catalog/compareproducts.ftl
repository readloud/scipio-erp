<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->
<@table type="fields" class="+${styles.table_spacing_tiny_hint!}">
<#-- Header row, contains product small image, product name, price -->
    <@tr>
        <@td>&nbsp;</@td>
<#list compareList as product>
    <#assign tdWidth = 100/compareList?size />
    <#assign productData = productDataMap[product.productId]/>
    <#assign productContentWrapper = productData.productContentWrapper/>
    <#assign price = productData.priceMap/>
    <#-- SCIPIO: NOTE: productUrl manually (js-)html-escaped below -->
    <#assign productUrl><@pageUrl uri="product?product_id="+escapeVal(product.productId, 'url')/></#assign>
    <#assign smallImageUrl = productContentWrapper.get("SMALL_IMAGE_URL", "url")!/>
    <#if !smallImageUrl?has_content>
        <#assign smallImageUrl = "/images/defaultImage.jpg"/>
    </#if>
        <@td style="width:${tdWidth?c}%;">
            <img src="<@contentUrl ctxPrefix=true>${smallImageUrl}</@contentUrl>" alt="Small Image"/><br />
            ${productContentWrapper.get("PRODUCT_NAME")!}<br />
    <#if totalPrice??>
            <div>${uiLabelMap.ProductAggregatedPrice}: <span class="basePrice"><@ofbizCurrency amount=totalPrice isoCode=totalPrice.currencyUsed/></span></div>
    <#else>
        <#if price.isSale?? && price.isSale>
            <#assign priceStyle = "salePrice">
        <#else>
            <#assign priceStyle = "regularPrice">
        </#if>

        <#if ((price.price!0) > 0) && ((product.requireAmount!"N") == "N")>
                <#if "Y" == (product.isVirtual!)> ${uiLabelMap.CommonFrom} </#if><span class="${priceStyle}"><@ofbizCurrency amount=price.price isoCode=price.currencyUsed/></span>
        </#if>
    </#if>
            <div class="productbuy">
    <#-- check to see if introductionDate hasn't passed yet -->
    <#if product.introductionDate?? && nowTimestamp.before(product.introductionDate)>
                <div class="${styles.text_color_alert!}">${uiLabelMap.ProductNotYetAvailable}</div>
    <#-- check to see if salesDiscontinuationDate has passed -->
    <#elseif product.salesDiscontinuationDate?? && nowTimestamp.after(product.salesDiscontinuationDate)/>
                <div class="${styles.text_color_alert!}">${uiLabelMap.ProductNoLongerAvailable}</div>
    <#-- check to see if it is a rental item; will enter parameters on the detail screen-->
    <#elseif (product.productTypeId!) == "ASSET_USAGE"/>
                <a href="javascript:doGetViaParent('${escapeVal(productUrl, 'js-html')}');" class="${styles.link_nav!} ${styles.action_add!}">${uiLabelMap.OrderMakeBooking}...</a>
    <#elseif (product.productTypeId!) == "ASSET_USAGE_OUT_IN"/>
                <a href="javascript:doGetViaParent('${escapeVal(productUrl, 'js-html')}}');" class="${styles.link_nav!} ${styles.action_add!}">${uiLabelMap.OrderRent}...</a>
    <#-- check to see if it is an aggregated or configurable product; will enter parameters on the detail screen-->
    <#elseif (product.productTypeId!) == "AGGREGATED" || (product.productTypeId!) == "AGGREGATED_SERVICE"/>
                <a href="javascript:doGetViaParent('${escapeVal(productUrl, 'js-html')}}');" class="${styles.link_nav!} ${styles.action_configure!}">${uiLabelMap.OrderConfigure}...</a>
    <#-- check to see if the product is a virtual product -->
    <#elseif product.isVirtual?? && product.isVirtual == "Y"/>
                <a href="javascript:doGetViaParent('${escapeVal(productUrl, 'js-html')}}');" class="${styles.link_nav!} ${styles.action_select!}">${uiLabelMap.OrderChooseVariations}...</a>
    <#-- check to see if the product requires an amount -->
    <#elseif product.requireAmount?? && product.requireAmount == "Y"/>
                <a href="javascript:doGetViaParent('${escapeVal(productUrl, 'js-html')}}');" class="${styles.link_nav!} ${styles.action_select!}">${uiLabelMap.OrderChooseAmount}...</a>
    <#else>
                <form method="post" action="<@pageUrl>additem</@pageUrl>" name="compareFormAdd${product_index}">
                    <input type="hidden" name="add_product_id" value="${product.productId}"/>
                    <input type="text" size="5" name="quantity" value="1"/>
                    <input type="hidden" name="clearSearch" value="N"/>
                </form>
                <a href="javascript:doPostViaParent('compareFormAdd${product_index}');" class="${styles.link_run_session!} ${styles.action_add!}">${uiLabelMap.OrderAddToCart}</a>

        <#if prodCatMem?? && prodCatMem.quantity?? && 0.00 < prodCatMem.quantity?double>
                <form method="post" action="<@pageUrl>additem</@pageUrl>" name="compareFormAddDefault${product_index}">
                    <input type="hidden" name="add_product_id" value="${prodCatMem.productId!}"/>
                    <input type="hidden" name="quantity" value="${prodCatMem.quantity!}"/>
                    <input type="hidden" name="clearSearch" value="N"/>
                </form>
                <a href="javascript:doPostViaParent('compareFormAddDefault${product_index}');" class="${styles.link_run_session!} ${styles.action_add!}">${uiLabelMap.CommonAddDefault} (${prodCatMem.quantity?string.number}) ${uiLabelMap.OrderToCart}</a>
        </#if>
    </#if>
            </div>
        </@td>
</#list>
    </@tr>
    <#-- Brand name -->
    <@tr>
        <@td>${uiLabelMap.ProductBrandName}</@td>
<#list compareList as product>
        <@td>${product.brandName!}</@td>
</#list>
    </@tr>
    <#-- Description -->
    <@tr>
        <@td>${uiLabelMap.ProductProductDescription}</@td>
<#list compareList as product>
    <#assign productData = productDataMap[product.productId]/>
    <#assign productContentWrapper = productData.productContentWrapper/>
        <@td>${productContentWrapper.get("DESCRIPTION")!}</@td>
</#list>
    </@tr>
    <#-- Long Description -->
    <@tr>
        <@td>${uiLabelMap.ProductLongDescription}</@td>
<#list compareList as product>
    <#assign productData = productDataMap[product.productId]/>
    <#assign productContentWrapper = productData.productContentWrapper/>
        <@td>${escapeVal(productContentWrapper.get("LONG_DESCRIPTION")!, 'htmlmarkup', {"allow":"internal"})}</@td>
</#list>
    </@tr>
<#list productFeatureTypeIds as productFeatureTypeId>
    <#assign productFeatureType = productFeatureTypeMap[productFeatureTypeId]/>
    <@tr>
        <@td>${productFeatureType.get("description", locale)}</@td>
    <#list compareList as product>
        <#assign productData = productDataMap[product.productId]/>
        <#assign applMap = productData[productFeatureTypeId]!/>
        <@td>
        <#if applMap.STANDARD_FEATURE?has_content>
            <#assign features = applMap.STANDARD_FEATURE/>
            <#list features as feature>
            <div>${feature.get("description", locale)}</div>
            </#list>
        </#if>
        <#if applMap.DISTINGUISHING_FEAT?has_content>
            <#assign features = applMap.DISTINGUISHING_FEAT/>
            <#list features as feature>
            <div>${feature.get("description", locale)}</div>
            </#list>
        </#if>
        <#if applMap.SELECTABLE_FEATURE?has_content>
            <#assign features = applMap.SELECTABLE_FEATURE/>
            <div>Available Options:</div>
            <ul>
            <#list features as feature>
                <li>${feature.get("description", locale)}</li>
            </#list>
            </ul>
        </#if>
        </@td>
    </#list>
    </@tr>
</#list>
    <@tr>
        <@td>&nbsp;</@td>
<#list compareList as product>
        <@td>
            <div class="productbuy">
    <#-- check to see if introductionDate hasn't passed yet -->
    <#if product.introductionDate?? && nowTimestamp.before(product.introductionDate)>
                <div class="${styles.text_color_alert!}">${uiLabelMap.ProductNotYetAvailable}</div>
    <#-- check to see if salesDiscontinuationDate has passed -->
    <#elseif product.salesDiscontinuationDate?? && nowTimestamp.after(product.salesDiscontinuationDate)/>
                <div class="${styles.text_color_alert!}">${uiLabelMap.ProductNoLongerAvailable}</div>
    <#-- check to see if it is a rental item; will enter parameters on the detail screen-->
    <#elseif (product.productTypeId!) == "ASSET_USAGE"/>
                <a href="javascript:doGetViaParent('${escapeVal(productUrl, 'js-html')}}');" class="${styles.link_nav!} ${styles.action_add!}">${uiLabelMap.OrderMakeBooking}...</a>
    <#elseif (product.productTypeId!) == "ASSET_USAGE_OUT_IN"/>
                <a href="javascript:doGetViaParent('${escapeVal(productUrl, 'js-html')}}');" class="${styles.link_nav!} ${styles.action_add!}">${uiLabelMap.OrderRent}...</a>
    <#-- check to see if it is an aggregated or configurable product; will enter parameters on the detail screen-->
    <#elseif (product.productTypeId!) == "AGGREGATED" || (product.productTypeId!) == "AGGREGATED_SERVICE"/>
                <a href="javascript:doGetViaParent('${escapeVal(productUrl, 'js-html')}}');" class="${styles.link_nav!} ${styles.action_configure!}">${uiLabelMap.OrderConfigure}...</a>
    <#-- check to see if the product is a virtual product -->
    <#elseif product.isVirtual?? && product.isVirtual == "Y"/>
                <a href="javascript:doGetViaParent('${escapeVal(productUrl, 'js-html')}}');" class="${styles.link_nav!} ${styles.action_select!}">${uiLabelMap.OrderChooseVariations}...</a>
    <#-- check to see if the product requires an amount -->
    <#elseif product.requireAmount?? && product.requireAmount == "Y"/>
                <a href="javascript:doGetViaParent('${escapeVal(productUrl, 'js-html')}}');" class="${styles.link_nav!} ${styles.action_select!}">${uiLabelMap.OrderChooseAmount}...</a>
    <#else>
                <form method="post" action="<@pageUrl>additem</@pageUrl>" name="compare2FormAdd${product_index}">
                    <input type="hidden" name="add_product_id" value="${product.productId}"/>
                    <input type="text" size="5" name="quantity" value="1"/>
                    <input type="hidden" name="clearSearch" value="N"/>
                </form>
                <a href="javascript:doPostViaParent('compare2FormAdd${product_index}');" class="${styles.link_run_session!} ${styles.action_add!}">${uiLabelMap.OrderAddToCart}</a>
        <#if prodCatMem?? && prodCatMem.quantity?? && (0.00 < prodCatMem.quantity?double)>
                <a href="javascript:doPostViaParent('compareFormAddDefault${product_index}');" class="${styles.link_run_session!} ${styles.action_add!}">${uiLabelMap.CommonAddDefault} (${prodCatMem.quantity?string.number}) ${uiLabelMap.OrderToCart}</a>
        </#if>
    </#if>
            </div>
        </@td>
</#list>
    </@tr>
</@table>

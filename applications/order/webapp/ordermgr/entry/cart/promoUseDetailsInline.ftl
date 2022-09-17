<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->

<div>
    <@heading>${uiLabelMap.OrderPromotionInformation}:</@heading>
    <@heading relLevel=1>${uiLabelMap.OrderPromotionsApplied}:</@heading>
        <ul>    
            <#list shoppingCart.getProductPromoUseInfoIter() as productPromoUseInfo>
                <li>
                    <#-- TODO: when promo pretty print is done show promo short description here -->
                    <@heading>${productPromoUseInfo.promoName!}</@heading>
                    <#if productPromoUseInfo.productPromoCodeId?has_content> - ${uiLabelMap.OrderWithPromoCode} [${productPromoUseInfo.productPromoCodeId}]</#if>
                    <#if (productPromoUseInfo.totalDiscountAmount != 0)> - ${uiLabelMap.CommonTotalValue} <@ofbizCurrency amount=(-1*productPromoUseInfo.totalDiscountAmount) isoCode=shoppingCart.getCurrency()/></#if>
                    <br/><a href="<@pageUrl>showPromotionDetails?productPromoId=${productPromoUseInfo.productPromoId!}</@pageUrl>" class="${styles.action_view!}">${uiLabelMap.CommonDetails}</a>
                    <#if productPromoUseInfo.productPromoCodeId?has_content>
                        <br/><a href="<@pageUrl>removePromotion?promoCode=${productPromoUseInfo.productPromoCodeId!}</@pageUrl>" class="${styles.link_run_sys!} ${styles.action_remove!}">${uiLabelMap.OrderRemovePromotion}</a>
                    </#if>
                </li>
                <#if (productPromoUseInfo.quantityLeftInActions > 0)>
                    <li>- Could be used for ${productPromoUseInfo.quantityLeftInActions} more discounted item<#if (productPromoUseInfo.quantityLeftInActions > 1)>s</#if> if added to your cart.</li>
                </#if>
            </#list>
        </ul>
    <@heading relLevel=1>${uiLabelMap.OrderCartItemUseinPromotions}:</@heading>
    <ul>
        <#list shoppingCart.items() as cartLine>
            <#assign cartLineIndex = shoppingCart.getItemIndex(cartLine)>
            <#if cartLine.getIsPromo()>
                <li>${uiLabelMap.OrderItemN} ${cartLineIndex+1} [${cartLine.getProductId()!}] - ${uiLabelMap.OrderIsAPromotionalItem}</li>
            <#else>
                <li>${uiLabelMap.OrderItemN} ${cartLineIndex+1} [${cartLine.getProductId()!}] - ${cartLine.getPromoQuantityUsed()?string.number}/${cartLine.getQuantity()?string.number} ${uiLabelMap.CommonUsed} - ${cartLine.getPromoQuantityAvailable()?string.number} ${uiLabelMap.CommonAvailable}
                    <ul>
                        <#list cartLine.getQuantityUsedPerPromoActualIter() as quantityUsedPerPromoActualEntry>
                            <#assign productPromoActualPK = quantityUsedPerPromoActualEntry.getKey()>
                            <#assign actualQuantityUsed = quantityUsedPerPromoActualEntry.getValue()>
                            <#assign isQualifier = "ProductPromoCond" == productPromoActualPK.getEntityName()>
                            <li>&nbsp;&nbsp;-&nbsp;${actualQuantityUsed} ${uiLabelMap.CommonUsedAs} <#if isQualifier>${uiLabelMap.CommonQualifier}<#else>${uiLabelMap.CommonBenefit}</#if> ${uiLabelMap.OrderOfPromotion} <a href="<@pageUrl>showPromotionDetails?productPromoId=${productPromoActualPK.productPromoId}</@pageUrl>" class="${styles.link_run_sys!} ${styles.action_view!}">${uiLabelMap.CommonDetails}</a></li>
                            <!-- productPromoActualPK ${productPromoActualPK.toString()} -->
                        </#list>
                    </ul>
                    <ul>
                        <#list cartLine.getQuantityUsedPerPromoFailedIter() as quantityUsedPerPromoFailedEntry>
                            <#assign productPromoFailedPK = quantityUsedPerPromoFailedEntry.getKey()>
                            <#assign failedQuantityUsed = quantityUsedPerPromoFailedEntry.getValue()>
                            <#assign isQualifier = "ProductPromoCond" == productPromoFailedPK.getEntityName()>
                            <li>&nbsp;&nbsp;-&nbsp;${uiLabelMap.CommonCouldBeUsedAs} <#if isQualifier>${uiLabelMap.CommonQualifier}<#else>${uiLabelMap.CommonBenefit}</#if> ${uiLabelMap.OrderOfPromotion} <a href="<@pageUrl>showPromotionDetails?productPromoId=${productPromoFailedPK.productPromoId}</@pageUrl>" class="${styles.link_run_sys!} ${styles.action_view!}">${uiLabelMap.CommonDetails}</a></li>
                            <!-- Total times checked but failed: ${failedQuantityUsed}, productPromoFailedPK ${productPromoFailedPK.toString()} -->
                        </#list>
                    </ul>
                    <#list cartLine.getQuantityUsedPerPromoCandidateIter() as quantityUsedPerPromoCandidateEntry>
                        <#assign productPromoCandidatePK = quantityUsedPerPromoCandidateEntry.getKey()>
                        <#assign candidateQuantityUsed = quantityUsedPerPromoCandidateEntry.getValue()>
                        <#assign isQualifier = "ProductPromoCond" == productPromoCandidatePK.getEntityName()>
                        <!-- Left over not reset or confirmed, shouldn't happen: ${candidateQuantityUsed} Might be Used (Candidate) as <#if isQualifier>${uiLabelMap.CommonQualifier}<#else>${uiLabelMap.CommonBenefit}</#if> ${uiLabelMap.OrderOfPromotion} [${productPromoCandidatePK.productPromoId}] -->
                        <!-- productPromoCandidatePK ${productPromoCandidatePK.toString()} -->
                    </#list>
                </li>
            </#if>
        </#list>
    </ul>
</div>


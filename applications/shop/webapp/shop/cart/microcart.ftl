<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->
<#include "component://shop/webapp/shop/cart/cartcommon.ftl">

<#-- SCIPIO: Must use context or accessor
<#assign shoppingCart = sessionAttributes.shoppingCart!>-->
<#assign shoppingCart = getShoppingCart()!>
<#if shoppingCart?has_content>
    <#assign shoppingCartSize = shoppingCart.size()>
<#else>
    <#assign shoppingCartSize = 0>
</#if>
<#assign microCartMenuItem = microCartMenuItem!false>

<#-- 2016-04-21: Quick access to checkout; set to true for testing 
    THIS NOW SUPPORTS A DEBUGGING OPTION, use ?microCartQuickMenu=true on any request to turn it on,
    e.g.,
      /shop/control/main?microCartQuickMenu=true
      /shop/control/main?microCartQuickMenu=false
    NOTE: context setting overrides it if present. -->
<#if (requestParameters.microCartQuickMenu!) == "true" || (requestParameters.microCartQuickMenu!) == "Y">
  <#assign dummy = (session.setAttribute("microCartQuickMenu", true))!>
<#elseif (requestParameters.microCartQuickMenu!) == "false" || (requestParameters.microCartQuickMenu!) == "N">
  <#assign dummy = (session.setAttribute("microCartQuickMenu", false))!>
</#if>
<#assign microCartQuickMenu = microCartQuickMenu!(session.getAttribute("microCartQuickMenu"))!false>

<#if microCartMenuItem>
  <li<#if microCartQuickMenu && (shoppingCartSize > 0)> class="has-dropdown not-click"</#if>>
</#if>
    <a href="<@pageUrl>showcart</@pageUrl>">
        <div id="microcart">
            <div id="microCartIcon"><i class="${styles.icon} ${styles.icon_shopping_cart}"></i></div>
            <#--<div id="microCartQuantity">${(shoppingCart.getTotalQuantity())!0}</div>-->
            <div id="microCartTotal">   
                <#assign currencyUomId = (shoppingCart.getCurrency())!false>
                <#if currencyUomId?is_boolean>
                  <#assign currencyUomId = raw(Static["org.ofbiz.product.store.ProductStoreWorker"].getStoreCurrencyUomId(request)!"")>
                </#if>
                <@ofbizCurrency amount=((shoppingCart.getDisplayGrandTotal())!0) isoCode=currencyUomId/>
            </div>
        </div>
      <#if microCartQuickMenu && (shoppingCartSize > 0)>
        <ul class="dropdown">      
            <li><a href="<@pageUrl>showcart</@pageUrl>">${uiLabelMap.PageTitleOrderShowCart}</a></li>
            <li><a href="<@pageUrl>checkoutoptionslogin</@pageUrl>">${uiLabelMap.OrderCheckout}</a></li>
            <li><a href="<@pageUrl>onePageCheckout</@pageUrl>">${uiLabelMap.EcommerceOnePageCheckout}</a></li>
            <li><a href="<@pageUrl>emptycart</@pageUrl>">${uiLabelMap.EcommerceEmptyCart}</a></li>

        </ul>
      </#if>
    </a>
            
<#-- The following contains a list of various checkout options.
    <ul>
      <li><a href="<@pageUrl>view/showcart</@pageUrl>">[${uiLabelMap.OrderViewCart}]</a></li>
      <#if (shoppingCartSize > 0)>
            <#if !initialLocaleComplete?? || initialLocaleComplete?length == 2 >
                <#if initialLocaleComplete?? && initialLocaleComplete?length == 2  && initialLocaleComplete == "fr">
                    <#assign initialLocaleComplete = "fr_FR">
                <#else>
                    <#assign initialLocaleComplete = "en_US">
                </#if>                              
            </#if>          
          <li id="quickCheckoutEnabled"><a href="<@pageUrl>quickcheckout</@pageUrl>">[${uiLabelMap.OrderCheckoutQuick}]</a></li>
          <li id="quickCheckoutDisabled" style="display:none" class="disabled">[${uiLabelMap.OrderCheckoutQuick}]</li>
          <li id="onePageCheckoutEnabled"><a href="<@pageUrl>onePageCheckout</@pageUrl>">[${uiLabelMap.EcommerceOnePageCheckout}]</a></li>
          <li id="onePageCheckoutDisabled" style="display:none" class="disabled">[${uiLabelMap.EcommerceOnePageCheckout}]</li>
          <li id="googleCheckoutEnabled"><a href="<@pageUrl>googleCheckout</@pageUrl>"><img src="https://checkout.google.com/buttons/checkout.gif?merchant_id=634321449957567&amp;w=160&amp;h=43&amp;style=white&amp;variant=text&amp;loc=${initialLocaleComplete}" alt="[${uiLabelMap.EcommerceCartToGoogleCheckout}]" /></a></li>
          <li id="googleCheckoutDisabled" style="display:none" class="disabled"><img src="https://checkout.google.com/buttons/checkout.gif?merchant_id=634321449957567&amp;w=160&amp;h=43&amp;style=white&amp;variant=text&amp;loc=${initialLocaleComplete}" alt="[${uiLabelMap.EcommerceCartToGoogleCheckout}]" /></li>
          <#if shoppingCart?has_content && (shoppingCart.getGrandTotal() > 0)>
            <li id="microCartPayPalCheckout"><a href="<@pageUrl>setPayPalCheckout</@pageUrl>"><img src="https://www.paypal.com/${initialLocaleComplete}/i/btn/btn_xpressCheckout.gif" alt="[PayPal Express Checkout]" onError="this.onerror=null;this.src='https://www.paypal.com/en_US/i/btn/btn_xpressCheckout.gif'"/></a></li>
          </#if>
      <#else>
          <li class="disabled">[${uiLabelMap.OrderCheckoutQuick}]</li>
          <li class="disabled">[${uiLabelMap.EcommerceOnePageCheckout}]</li>
      </#if>
    </ul>
-->
<#if microCartMenuItem>
  </li>
</#if>
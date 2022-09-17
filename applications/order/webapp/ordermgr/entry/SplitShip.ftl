<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->

<@script>
function submitForm(form, mode, value) {
    if (mode == "DN") {
        // done action; payment info
        form.action="<@pageUrl>updateShippingOptions/checkoutpayment</@pageUrl>";
        form.submit();
    } else if (mode == "CS") {
        // continue shopping
        form.action="<@pageUrl>updateShippingOptions/showcart</@pageUrl>";
        form.submit();
    } else if (mode == "NA") {
        // new address
        form.action="<@pageUrl>updateCheckoutOptions/editcontactmech?DONE_PAGE=splitship&partyId=${cart.getPartyId()}&preContactMechTypeId=POSTAL_ADDRESS&contactMechPurposeTypeId=SHIPPING_LOCATION</@pageUrl>";
        form.submit();
    } else if (mode == "SV") {
        // save option; return to current screen
        form.action="<@pageUrl>updateShippingOptions/splitship</@pageUrl>";
        form.submit();
    } else if (mode == "SA") {
        // selected shipping address
        form.action="<@pageUrl>updateShippingAddress/splitship</@pageUrl>";
        form.submit();
    }
}
</@script>

<@section title=uiLabelMap.OrderItemGroups>
    <@fields type="default-manual">
        <@table type="data-complex" class="+${styles.table_spacing_tiny_hint!}" width="100%">
          <#assign shipGroups = cart.getShipGroups()>
          <#if (shipGroups.size() > 0)>
            <#assign groupIdx = 0>
            <#list shipGroups as group>
              <#assign shipEstimateWrapper = Static["org.ofbiz.order.shoppingcart.shipping.ShippingEstimateWrapper"].getWrapper(dispatcher, cart, groupIdx)>
              <#assign carrierShipmentMethods = shipEstimateWrapper.getShippingMethods()>
              <#assign groupNumber = groupIdx + 1>
              <form method="post" action="#" name="editgroupform${groupIdx}">
                <input type="hidden" name="groupIndex" value="${groupIdx}"/>
                <@tr>
                  <@td>
                    <div class="tabletext"><b>${uiLabelMap.CommonGroup} ${groupNumber}:</b></div>
                    <#list group.getShipItems() as item>
                      <#assign groupItem = group.getShipItemInfo(item)>
                      <div class="tabletext">&nbsp;&nbsp;&nbsp;${item.getName()} - (${groupItem.getItemQuantity()})</div>
                    </#list>
                  </@td>
                  <@td>
                    <div>
                      <span class="tabletext">${uiLabelMap.CommonAdd}:</span>
                      <a href="javascript:submitForm(document.editgroupform${groupIdx}, 'NA', '');" class="${styles.link_nav!} ${styles.action_add!}">${uiLabelMap.PartyAddNewAddress}</a>
                    </div>
                    <div>
                      <#assign selectedContactMechId = cart.getShippingContactMechId(groupIdx)!"">
                      <@field type="select" name="shippingContactMechId" onChange="javascript:submitForm(document.editgroupform${groupIdx}, 'SA', null);">
                        <option value="">${uiLabelMap.OrderSelectShippingAddress}</option>
                        <#list shippingContactMechList as shippingContactMech>
                          <#assign shippingAddress = shippingContactMech.getRelatedOne("PostalAddress", false)>
                          <option value="${shippingAddress.contactMechId}"<#if (shippingAddress.contactMechId == selectedContactMechId)> selected="selected"</#if>>${shippingAddress.address1}</option>
                        </#list>
                      </@field>
                    </div>
                    <#if cart.getShipmentMethodTypeId(groupIdx)??>
                      <#assign selectedShippingMethod = cart.getShipmentMethodTypeId(groupIdx) + "@" + cart.getCarrierPartyId(groupIdx)>
                    <#else>
                      <#assign selectedShippingMethod = "">
                    </#if>
                    <@field type="select" name="shipmentMethodString">
                      <option value="">${uiLabelMap.OrderSelectShippingMethod}</option>
                      <#list carrierShipmentMethods as carrierShipmentMethod>
                        <#assign shippingEst = shipEstimateWrapper.getShippingEstimate(carrierShipmentMethod)?default(-1)>
                        <#assign shippingMethod = carrierShipmentMethod.shipmentMethodTypeId + "@" + carrierShipmentMethod.partyId>
                        <option value="${shippingMethod}"<#if (shippingMethod == selectedShippingMethod)> selected="selected"</#if>>
                          <#if carrierShipmentMethod.partyId != "_NA_">
                            ${carrierShipmentMethod.partyId!}&nbsp;
                          </#if>
                          ${carrierShipmentMethod.description!}
                          <#if shippingEst?has_content>
                            <#if (shippingEst > -1)>
                              &nbsp;-&nbsp;<@ofbizCurrency amount=shippingEst isoCode=cart.getCurrency()/>
                            <#elseif raw(carrierShipmentMethod.shipmentMethodTypeId!) != "NO_SHIPPING"><#-- SCIPIO: NO_SHIPPING check -->
                              &nbsp;-&nbsp;${uiLabelMap.OrderCalculatedOffline}
                            </#if>
                          </#if>
                        </option>
                      </#list>
                    </@field>

                    <@heading>${uiLabelMap.OrderSpecialInstructions}</@heading>
                    <@field type="textarea" cols="35" rows="3" wrap="hard" name="shippingInstructions">${cart.getShippingInstructions(groupIdx)!}</@field>
                  </@td>
                  <@td>
                    <div>
                      <@field type="select" name="maySplit">
                        <#assign maySplitStr = cart.getMaySplit(groupIdx)?default("")>
                        <option value="">${uiLabelMap.OrderSplittingPreference}</option>
                        <option value="false"<#if maySplitStr == "N"> selected="selected"</#if>>${uiLabelMap.OrderShipAllItemsTogether}</option>
                        <option value="true"<#if maySplitStr == "Y"> selected="selected"</#if>>${uiLabelMap.OrderShipItemsWhenAvailable}</option>
                      </@field>
                    </div>
                    <div>
                      <@field type="select" name="isGift">
                        <#assign isGiftStr = cart.getIsGift(groupIdx)?default("")>
                        <option value="">${uiLabelMap.OrderIsGift} ?</option>
                        <option value="false"<#if isGiftStr == "N"> selected="selected"</#if>>${uiLabelMap.OrderNotAGift}</option>
                        <option value="true"<#if isGiftStr == "Y"> selected="selected"</#if>>${uiLabelMap.OrderYesIsAGift}</option>
                      </@field>
                    </div>

                    <@heading>${uiLabelMap.OrderGiftMessage}</@heading>
                    <@field type="textarea" cols="30" rows="3" wrap="hard" name="giftMessage">${cart.getGiftMessage(groupIdx)!}</@field>
                  </@td>
                  <@td><@field type="submit" submitType="button" class="+${styles.link_run_session!} ${styles.action_update!}" text=uiLabelMap.CommonSave onClick="javascript:submitForm(document.editgroupform${groupIdx}, 'SV', null);"/></@td>
                </@tr>
                <#assign groupIdx = groupIdx + 1>
                <#if group_has_next>
                  <@tr type="util">
                    <@td colspan="6"><hr /></@td>
                  </@tr>
                </#if>
              </form>
            </#list>
          <#else>
            <@commonMsg type="result-norecord">${uiLabelMap.OrderNoShipGroupsDefined}.</@commonMsg>
          </#if>
        </@table>
    </@fields>
</@section>

<@section title=uiLabelMap.OrderAssignItems>
    <@fields type="default-manual">
        <@table type="data-complex" class="+${styles.table_spacing_tiny_hint!}" width="100%">
          <@tr>
            <@td><div class="tabletext"><b>${uiLabelMap.OrderProduct}</b></div></@td>
            <@td align="center"><div class="tabletext"><b>${uiLabelMap.OrderTotalQty}</b></div></@td>
            <@td>&nbsp;</@td>
            <@td align="center"><div class="tabletext"><b>${uiLabelMap.OrderMoveQty}</b></div></@td>
            <@td>&nbsp;</@td>
            <@td>&nbsp;</@td>
          </@tr>

          <#list cart.items() as cartLine>
            <#assign cartLineIndex = cart.getItemIndex(cartLine)>
            <@tr>
              <form method="post" action="<@pageUrl>updatesplit</@pageUrl>" name="editgroupform">
                <input type="hidden" name="itemIndex" value="${cartLineIndex}"/>
                <@td>
                  <div class="tabletext">
                    <#if cartLine.getProductId()??>
                      <#-- product item -->
                      <#-- start code to display a small image of the product -->
                      <#assign smallImageUrl = Static["org.ofbiz.product.product.ProductContentWrapper"].getProductContentAsText(cartLine.getProduct(), "SMALL_IMAGE_URL", locale, dispatcher, "url")!>
                      <#if !smallImageUrl?string?has_content><#assign smallImageUrl = "/images/defaultImage.jpg"></#if>
                      <#if smallImageUrl?string?has_content>
                        <a href="<@pageUrl>product?product_id=${cartLine.getProductId()}</@pageUrl>">
                          <img src="<@contentUrl ctxPrefix=true>${smallImageUrl}</@contentUrl>" class="cssImgSmall" alt="" />
                        </a>
                      </#if>
                      <#-- end code to display a small image of the product -->
                      <a href="<@pageUrl>product?product_id=${cartLine.getProductId()}</@pageUrl>" class="${styles.link_nav_info_id!}">${cartLine.getProductId()} -
                      ${cartLine.getName()!}</a> : ${cartLine.getDescription()!}

                      <#-- display the registered ship groups and quantity -->
                      <#assign itemShipGroups = cart.getShipGroups(cartLine)>
                      <#list itemShipGroups.entrySet() as group>
                        <div class="tabletext">
                          <#assign groupNumber = group.getKey() + 1>
                          <b>Group - </b>${groupNumber} / <b>${uiLabelMap.CommonQuantity} - </b>${group.getValue()}
                        </div>
                      </#list>

                      <#-- if inventory is not required check to see if it is out of stock and needs to have a message shown about that... -->
                      <#assign itemProduct = cartLine.getProduct()>
                      <#assign isStoreInventoryNotRequiredAndNotAvailable = Static["org.ofbiz.product.store.ProductStoreWorker"].isStoreInventoryRequiredAndAvailable(request, itemProduct, cartLine.getQuantity(), false, false)>
                      <#if isStoreInventoryNotRequiredAndNotAvailable && itemProduct.inventoryMessage?has_content>
                        <b>(${itemProduct.inventoryMessage})</b>
                      </#if>

                    <#else>
                      <#-- this is a non-product item -->
                      <b>${cartLine.getItemTypeDescription()!}</b> : ${cartLine.getName()!}
                    </#if>
                  </div>

                </@td>
                <@td align="right">
                  <div class="tabletext">${cartLine.getQuantity()?string.number}&nbsp;&nbsp;&nbsp;</div>
                </@td>
                <@td>&nbsp;</@td>
                <@td align="center">
                  <@field type="input" size="6" name="quantity" value=cartLine.getQuantity()?string.number/>
                </@td>
                <@td>&nbsp;</@td>
                <@td>
                  <div class="tabletext">${uiLabelMap.CommonFrom}:
                    <@field type="select" name="fromGroupIndex">
                      <#list itemShipGroups.entrySet() as group>
                        <#assign groupNumber = group.getKey() + 1>
                        <option value="${group.getKey()}">${uiLabelMap.CommonGroup} ${groupNumber}</option>
                      </#list>
                    </@field>
                  </div>
                </@td>
                <@td>
                  <div class="tabletext">${uiLabelMap.CommonTo}:
                    <@field type="select" name="toGroupIndex">
                      <#list 0..(cart.getShipGroupSize() - 1) as groupIdx>
                        <#assign groupNumber = groupIdx + 1>
                        <option value="${groupIdx}">${uiLabelMap.CommonGroup} ${groupNumber}</option>
                      </#list>
                      <option value="-1">${uiLabelMap.CommonNew} ${uiLabelMap.CommonGroup}</option>
                    </@field>
                  </div>
                </@td>
                <@td><@field type="submit" class="+${styles.link_run_session!} ${styles.action_update!}" text=uiLabelMap.CommonSubmit/></@td>
              </form>
            </@tr>
          </#list>
        </@table>
    </@fields>
</@section>

<@menu type="button">
  <@menuitem type="link" href=makePageUrl("updateCheckoutOptions/showcart") text=uiLabelMap.OrderBacktoShoppingCart class="+${styles.action_nav!} ${styles.action_cancel!}" />
  <@menuitem type="link" href=makePageUrl("setBilling") text=uiLabelMap.CommonContinue class="+${styles.action_nav!} ${styles.action_continue!}" />
</@menu>


<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->

<#assign productStoreId = Static["org.ofbiz.product.store.ProductStoreWorker"].getProductStoreId(request) />
<@section title=uiLabelMap.EcommerceAddressBook>
  <form id="refreshRequestForm" method="post" action="<@pageUrl>manageAddress</@pageUrl>">
  </form>
    <#-- Add address -->
    <a class="${styles.link_run_sys!} ${styles.action_add!}" id="addAddress" href="javascript:void(0)">${uiLabelMap.EcommerceAddNewAddress}</a>
    <div id="displayCreateAddressForm" style="display: none;">
      <div id="serverError" class="errorMessage"></div>
      <form id="createPostalAddressForm" method="post" action="">
        <fieldset>
          <input type="hidden" name="roleTypeId" value="CUSTOMER" />
          <input type="hidden" name="productStoreId" value="${productStoreId!}" />
          <div>
            <label for="address1">${uiLabelMap.PartyAddressLine1}*
               <span id="advice-required-address1" style="display: none" class="errorMessage">(${uiLabelMap.CommonRequired})</span>
            </label>
            <input type="text" class="required" name="address1" id="address1" value="" maxlength="30" />
          </div>
          <div>
            <label for="address2">${uiLabelMap.PartyAddressLine2}</label>
            <input type="text" name="address2" id="address2" value="" maxlength="30" />
          </div>
          <div>
            <label for="city">${uiLabelMap.PartyCity}*
              <span id="advice-required-city" style="display: none" class="errorMessage">(${uiLabelMap.CommonRequired})</span>
            </label>
            <input type="text" class="required" name="city" id="city" value="" maxlength="30" />
          </div>
          <div>
            <label for="postalCode">${uiLabelMap.PartyZipCode}*
              <span id="advice-required-postalCode" style="display: none" class="errorMessage">(${uiLabelMap.CommonRequired})</span>
            </label>
            <input type="text" class="required" name="postalCode" id="postalCode" value="" maxlength="10" />
          </div>
          <div>
            <label for="countryGeoId">${uiLabelMap.CommonCountry}*
              <span id="advice-required-countryGeoId" style="display: none" class="errorMessage">(${uiLabelMap.CommonRequired})</span>
            </label>
             <select name="countryGeoId" id="countryGeoId" class="required">
               <#if countryGeoId??>
                 <option value="${countryGeoId}">${countryGeoId}</option>
               </#if>
               <@render resource="component://common/widget/CommonScreens.xml#countries" ctxVars={"countriesPreselect":!countryGeoId??}/>
             </select>
          </div>
          <div id="states">
            <label for="stateProvinceGeoId">${uiLabelMap.PartyState}*
              <span id="advice-required-stateProvinceGeoId" style="display: none" class="errorMessage">(${uiLabelMap.CommonRequired})</span>
            </label>
              <select name="stateProvinceGeoId" id="stateProvinceGeoId">
              <#if stateProvinceGeoId?has_content>
                <option value="${stateProvinceGeoId}">${stateProvinceGeoId}</option>
              <#else>
                <option value="_NA_">${uiLabelMap.PartyNoState}</option>
              </#if>
              </select>
          </div>
          <div class="inline">
            <label for="setBillingPurpose">${uiLabelMap.EcommerceMyDefaultBillingAddress}</label>
            <input type="checkbox" name="setBillingPurpose" id="setBillingPurpose" value="Y" <#if setBillingPurpose??>checked="checked"</#if> />
          </div>
          <div class="inline">
            <label for="setShippingPurpose">${uiLabelMap.EcommerceMyDefaultShippingAddress}</label>
            <input type="checkbox" name="setShippingPurpose" id="setShippingPurpose" value="Y" <#if setShippingPurpose??>checked="checked"</#if> />
          </div>
        </fieldset>
      </form>
    </div>
    <@script>
        jQuery("#displayCreateAddressForm").dialog({autoOpen: false, modal: true,
                buttons: {
                '${uiLabelMap.CommonSubmit}': function() {
                    var createAddressForm = jQuery("#displayCreateAddressForm");
                    if (jQuery("#createPostalAddressForm").valid()) {
                        jQuery("<p>${uiLabelMap.CommonUpdatingData}</p>").insertBefore(createAddressForm);
                        createPartyPostalAddress();
                    }
                },
                '${uiLabelMap.CommonClose}': function() {
                    jQuery(this).dialog('close');
                    }
                }
        });
        jQuery("#addAddress").click(function(){jQuery("#displayCreateAddressForm").dialog("open")});
      </@script>
  </div>

  <#-- Default Addresses -->
  <@section title=uiLabelMap.EcommerceDefaultAddresses>
      <#--===================================== Billing Address and Telecom number ===========================================-->
      <@section title=uiLabelMap.EcommercePrimaryBillingAddress>
      <ul>
      <#if billToContactMechId??>
        <li>${billToAddress1!}</li>
        <#if billToAddress2?has_content><li>${billToAddress2!}</li></#if>
        <li>
          <#if billToStateProvinceGeoId?has_content && billToStateProvinceGeoId != "_NA_">
            ${billToStateProvinceGeoId}
          </#if>
          ${billToCity!},
          ${billToPostalCode!}
        </li>
        <li>${billToCountryGeoId!}</li>
        <#if billToTelecomNumber?has_content>
        <li>
          ${billToTelecomNumber.countryCode!}-
          ${billToTelecomNumber.areaCode!}-
          ${billToTelecomNumber.contactNumber!}
          <#if billToExtension??>-${billToExtension!}</#if>
        </li>
        </#if>
        <li><a id="updateBillToPostalAddress" href="javascript:void(0)" class="${styles.link_nav!} ${styles.action_update!} popup_link">${uiLabelMap.CommonEdit}</a></li>
      <#else>
        <li>${uiLabelMap.PartyPostalInformationNotFound}</li>
      </#if>
      </ul>
      <div id="displayEditBillToPostalAddress" style="display: none;">
        <#include "component://shop/webapp/shop/customer/profile/EditBillToAddress.ftl" />
      </div>
      <@script>
        jQuery("#displayEditBillToPostalAddress").dialog({autoOpen: false, modal: true,
            buttons: {
            '${uiLabelMap.CommonSubmit}': function() {
                var createAddressForm = jQuery("#displayEditBillToPostalAddress");
                if (jQuery("#editBillToPostalAddress").valid()) {
                    jQuery("<p>${uiLabelMap.CommonUpdatingData}</p>").insertBefore(createAddressForm);
                    updatePartyBillToPostalAddress();
                }
                
            },
            '${uiLabelMap.CommonClose}': function() {
                jQuery(this).dialog('close');
                }
            }
        });
        jQuery("#updateBillToPostalAddress").click(function(){jQuery("#displayEditBillToPostalAddress").dialog("open")});
        </@script>
      </@section>
    <#--===================================== Shipping Address and Telecom number ===========================================-->
    <@section title=uiLabelMap.EcommercePrimaryShippingAddress>
      <ul>
      <#if shipToContactMechId??>
        <li>${shipToAddress1!}</li>
        <#if shipToAddress2?has_content><li>${shipToAddress2!}</li></#if>
        <li>
        <#if shipToStateProvinceGeoId?has_content && shipToStateProvinceGeoId != "_NA_">
          ${shipToStateProvinceGeoId}
        </#if>
          ${shipToCity!},
          ${shipToPostalCode!}
        </li>
        <li>${shipToCountryGeoId!}</li>
        <#if shipToTelecomNumber?has_content>
        <li>
          ${shipToTelecomNumber.countryCode!}-
          ${shipToTelecomNumber.areaCode!}-
          ${shipToTelecomNumber.contactNumber!}
          <#if shipToExtension??>-${shipToExtension!}</#if>
        </li>
        </#if>
        <li><a id="updateShipToPostalAddress" href="javascript:void(0)" class="${styles.link_nav!} ${styles.action_update!} popup_link">${uiLabelMap.CommonEdit}</a></li>
      <#else>
        <li>${uiLabelMap.PartyPostalInformationNotFound}</li>
      </#if>
      </ul>
      <div id="displayEditShipToPostalAddress" style="display: none;">
        <#include "component://shop/webapp/shop/customer/profile/EditShipToAddress.ftl" />
      </div>
      <@script>
          jQuery("#displayEditShipToPostalAddress").dialog({autoOpen: false, modal: true,
            buttons: {
            '${uiLabelMap.CommonSubmit}': function() {
                var createAddressForm = jQuery("#displayEditShipToPostalAddress");
                if (jQuery("#editShipToPostalAddress").valid()) {
                    jQuery("<p>${uiLabelMap.CommonUpdatingData}</p>").insertBefore(createAddressForm);
                    updatePartyShipToPostalAddress('submitEditShipToPostalAddress');
                }
            },
            '${uiLabelMap.CommonClose}': function() {
                jQuery(this).dialog('close');
                }
            }
          });
          jQuery("#updateShipToPostalAddress").click(function(){jQuery("#displayEditShipToPostalAddress").dialog("open")});
          </@script>
    </@section>

  </@section>

  <#-- Additional Addresses -->
  <@section title=uiLabelMap.EcommerceAdditionalAddresses>
      <#assign postalAddressFlag = "N" />
      <#list partyContactMechValueMaps as partyContactMechValueMap>
        <#assign contactMech = partyContactMechValueMap.contactMech! />
        <#if (contactMech.contactMechTypeId!) == "POSTAL_ADDRESS">
          <#assign partyContactMech = partyContactMechValueMap.partyContactMech! />
          <#if !(partyContactMechValueMap.partyContactMechPurposes?has_content)>
            <#assign postalAddressFlag = "Y" />
            <#assign postalAddress = partyContactMechValueMap.postalAddress! />
            <div id="displayEditAddressForm_${contactMech.contactMechId}" style="display: none;">
              <#include "component://shop/webapp/shop/customer/profile/EditPostalAddress.ftl" />
            </div>
            <#if postalAddress??>
                <div class="form-field">
                    <ul>
                      <li>${postalAddress.address1}</li>
                      <#if postalAddress.address2?has_content><li>${postalAddress.address2}</li></#if>
                      <li>${postalAddress.city}</li>
                      <li>
                      <#if postalAddress.stateProvinceGeoId?has_content && postalAddress.stateProvinceGeoId != "_NA_">
                        ${postalAddress.stateProvinceGeoId}
                      </#if>
                        ${postalAddress.postalCode!}
                      </li>
                    <#if postalAddress.countryGeoId?has_content><li>${postalAddress.countryGeoId}</li></#if>
                    </ul>
                    <#--
                    <#if (!postalAddress.countryGeoId?has_content || (postalAddress.countryGeoId!) == "USA")>
                      <#assign addr1 = postalAddress.address1!?string />
                      <#if (addr1?index_of(" ") > 0)>
                        <#assign addressNum = addr1?substring(0, addr1?index_of(" ")) />
                        <#assign addressOther = addr1?substring(addr1?index_of(" ")+1) />
                        <a target="_blank" href="#" class="${styles.link_nav!} ${styles.action_find!} ${styles.action_external!}">(${uiLabelMap.CommonLookupWhitepages})</a>
                      </#if>
                    </#if>-->
                </div>
              <div>
                <span>
                  <a id="update_${contactMech.contactMechId}" href="javascript:void(0)" class="${styles.link_nav!} ${styles.action_update!} popup_link" onclick="showState('${contactMech.contactMechId}')">${uiLabelMap.CommonEdit}</a></span>
                  <form id="deletePostalAddress_${contactMech.contactMechId}" method="post" action="<@pageUrl>deletePostalAddress</@pageUrl>">
                    <fieldset>
                      <input type="hidden" name="contactMechId" value="${contactMech.contactMechId}" />
                    </fieldset>
                  </form>
              </div>
              <@script>
                jQuery("#displayEditAddressForm_${contactMech.contactMechId}").dialog({autoOpen: false, modal: true,
                    buttons: {
                    '${uiLabelMap.CommonSubmit}': function() {
                        var createAddressForm = jQuery("#displayEditAddressForm_${contactMech.contactMechId}");
                        if (jQuery("#editPostalAddress_${contactMech.contactMechId}").valid()) {
                            jQuery("<p>${uiLabelMap.CommonUpdatingData}</p>").insertBefore(createAddressForm);
                            updatePartyPostalAddress('submitEditPostalAddress_${contactMech.contactMechId}');
                        }
                    },
                    '${uiLabelMap.CommonClose}': function() {
                        jQuery(this).dialog('close');
                        }
                    }
                });
                jQuery("#update_${contactMech.contactMechId}").click(function(){jQuery("#displayEditAddressForm_${contactMech.contactMechId}").dialog("open")});
                </@script>
            <#else>
              <@commonMsg type="result-norecord">${uiLabelMap.PartyPostalInformationNotFound}.</@commonMsg>
            </#if>
          </#if>
        </#if>
      </#list>
      <#if postalAddressFlag == "N">
        <@commonMsg type="result-norecord">${uiLabelMap.PartyPostalInformationNotFound}.</@commonMsg>
      </#if>
  </@section>
</@section>
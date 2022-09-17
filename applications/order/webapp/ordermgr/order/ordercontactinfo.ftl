<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->

<#macro updateOrderContactMech orderHeader contactMechTypeId contactMechList contactMechPurposeTypeId contactMechAddress>
  <#if (!orderHeader.statusId.equals("ORDER_COMPLETED")) && !(orderHeader.statusId.equals("ORDER_REJECTED")) && !(orderHeader.statusId.equals("ORDER_CANCELLED"))>
    <@modal label="(${rawLabel('CommonEdit')})" id="modal_updateOrderContactMech_${contactMechTypeId}">
        <form name="updateOrderContactMech" method="post" action="<@pageUrl>updateOrderContactMech</@pageUrl>">
          <input type="hidden" name="orderId" value="${orderId!}" />
          <input type="hidden" name="contactMechPurposeTypeId" value="${contactMechPurpose.contactMechPurposeTypeId!}" />
          <input type="hidden" name="oldContactMechId" value="${contactMech.contactMechId!}" />
          <@row>
                <@cell columns=6>
                      <select name="contactMechId">
                        <#if contactMech.contactMechTypeId == "POSTAL_ADDRESS">
                          <option value="${contactMechAddress.contactMechId}">${(contactMechAddress.address1)!""} - ${contactMechAddress.city!""}</option>
                          <option value="${contactMechAddress.contactMechId}"></option>
                          <#list contactMechList as contactMech>
                            <#assign postalAddress = contactMech.getRelatedOne("PostalAddress", false)! />
                            <#assign partyContactPurposes = postalAddress.getRelated("PartyContactMechPurpose", null, null, false)! />
                            <#list partyContactPurposes as partyContactPurpose>
                              <#if contactMech.contactMechId?has_content && partyContactPurpose.contactMechPurposeTypeId == contactMechPurposeTypeId>
                                <option value="${contactMech.contactMechId!}">${(postalAddress.address1)!""} - ${postalAddress.city!""}</option>
                              </#if>
                            </#list>
                          </#list>
                        <#elseif contactMech.contactMechTypeId == "TELECOM_NUMBER">
                          <option value="${contactMechAddress.contactMechId}">${contactMechAddress.countryCode!} <#if contactMechAddress.areaCode??>${contactMechAddress.areaCode}-</#if>${contactMechAddress.contactNumber}</option>
                          <option value="${contactMechAddress.contactMechId}"></option>
                          <#list contactMechList as contactMech>
                             <#assign telecomNumber = contactMech.getRelatedOne("TelecomNumber", false)! />
                             <#assign partyContactPurposes = telecomNumber.getRelated("PartyContactMechPurpose", null, null, false)! />
                             <#list partyContactPurposes as partyContactPurpose>
                               <#if contactMech.contactMechId?has_content && partyContactPurpose.contactMechPurposeTypeId == contactMechPurposeTypeId>
                                  <option value="${contactMech.contactMechId!}">${telecomNumber.countryCode!} <#if telecomNumber.areaCode??>${telecomNumber.areaCode}-</#if>${telecomNumber.contactNumber}</option>
                               </#if>
                             </#list>
                          </#list>
                        <#elseif contactMech.contactMechTypeId == "EMAIL_ADDRESS">
                          <option value="${contactMechAddress.contactMechId}">${(contactMechAddress.infoString)!""}</option>
                          <option value="${contactMechAddress.contactMechId}"></option>
                          <#list contactMechList as contactMech>
                             <#assign partyContactPurposes = contactMech.getRelated("PartyContactMechPurpose", null, null, false)! />
                             <#list partyContactPurposes as partyContactPurpose>
                               <#if contactMech.contactMechId?has_content && partyContactPurpose.contactMechPurposeTypeId == contactMechPurposeTypeId>
                                  <option value="${contactMech.contactMechId!}">${contactMech.infoString!}</option>
                               </#if>
                             </#list>
                          </#list>
                        </#if>
                      </select>
                </@cell>
                <@cell columns=6>
                    <input type="submit" value="${uiLabelMap.CommonUpdate}" class="${styles.link_run_sys!} ${styles.action_update!}" /> 
                </@cell>
            </@row>  
        </form>
    </@modal>
  </#if>
</#macro>

<#if displayParty?has_content || orderContactMechValueMaps?has_content || partyId?has_content><#-- SCIPIO: Allow partyId display -->
 <#-- The usefulness of this information is limited. Uncomment and add as menuContent to section in order to add these functions back in
  <#macro menuContent menuArgs={}>
    <@menu args=menuArgs>
        <@menuitem type="link" href=makePageUrl("orderentry?partyId=${partyId}&orderTypeId=${orderHeader.orderTypeId}") text=uiLabelMap.OrderNewOrder class="+${styles.action_nav!} ${styles.action_view!}" />
        <@menuitem type="link" href="javascript:document.searchOtherOrders.submit()" text=uiLabelMap.OrderOtherOrders class="+${styles.action_nav!} ${styles.action_find!}" />
    </@menu>
  </#macro>
  -->  
  <@section title=uiLabelMap.OrderContactInformation>
      <@table type="fields">
        <#-- the setting of shipping method is only supported for sales orders at this time -->
        <@tr>
          <@td class="${styles.grid_large!}3">${uiLabelMap.CommonName}</@td>
          <@td colspan="3">
                <#if displayParty?has_content && userLogin??><#-- SCIPIO: 2019-02-27: Don't run getPartyNameForDate if userLogin missing (see OrderServices.sendOrderNotificationScreen warning) -->
                    <#assign displayPartyNameResult = runService("getPartyNameForDate", {"partyId":displayParty.partyId, "compareDate":orderHeader.orderDate, "userLogin":userLogin})/>
                    <a href="${customerDetailLink}${partyId}${raw(externalKeyParam)}" target="partymgr" class="">${displayPartyNameResult.fullName!partyId!}</a><#-- SCIPIO: show ID as fallback: ("[${uiLabelMap.OrderPartyNameNotFound}]") -->
                <#elseif partyId?has_content>
                    <a href="${customerDetailLink}${partyId}${raw(externalKeyParam)}" target="partymgr" class="">${partyId}</a>
                </#if>
                <#--
                <#if (orderHeader.salesChannelEnumId)?? && orderHeader.salesChannelEnumId != "POS_SALES_CHANNEL">
                  <form name="searchOtherOrders" method="post" action="<@pageUrl>searchorders</@pageUrl>">
                    <input type="hidden" name="lookupFlag" value="Y"/>
                    <input type="hidden" name="hideFields" value="Y"/>
                    <input type="hidden" name="partyId" value="${partyId}" />
                    <input type="hidden" name="viewIndex" value="1"/>
                    <input type="hidden" name="viewSize" value="20"/>
                  </form>
                </#if>-->
          </@td>
        </@tr>
        <#list shipGroups as shipGroup>
        <#assign shipGroupShipments = shipGroup.getRelated("PrimaryShipment", null, null, false)>
           <#if shipGroupShipments?has_content>
              <@tr>
                <@td scope="row" class="${styles.grid_large!}3">
                  ${uiLabelMap.FacilityShipments}
                </@td>
                <@td>
                    <#list shipGroupShipments as shipment>
                          <@row>
                            <@cell columns=6>
                          ${uiLabelMap.CommonNbr} <a href="<@serverUrl>/facility/control/EditShipment?shipmentId=${shipment.shipmentId}${raw(externalKeyParam)}</@serverUrl>" class="${styles.link_nav_info_id!}">${shipment.shipmentId}</a>
                                                          (<a target="_BLANK" href="<@serverUrl>/facility/control/PackingSlip.pdf?shipmentId=${shipment.shipmentId}${raw(externalKeyParam)}</@serverUrl>" class="${styles.link_nav_info_id!} ${styles.action_export!}">${uiLabelMap.ProductPackingSlip}</a>)
                          </@cell>
                        </@row>
                        <#if "SALES_ORDER" == orderHeader.orderTypeId && "ORDER_COMPLETED" == orderHeader.statusId>
                            <#assign shipmentRouteSegments = delegator.findByAnd("ShipmentRouteSegment", {"shipmentId" : shipment.shipmentId}, null, false)>
                            <#if shipmentRouteSegments?has_content>
                            <@row>
                            <@cell columns=6>
                              <hr/>
                              <#assign shipmentRouteSegment = Static["org.ofbiz.entity.util.EntityUtil"].getFirst(shipmentRouteSegments)>
                              <#if "UPS" == ((shipmentRouteSegment.carrierPartyId)!)>
                                <a href="javascript:document.upsEmailReturnLabel${shipment_index}.submit();" class="${styles.link_nav_info_id!} ${styles.action_send!}">${uiLabelMap.ProductEmailReturnShippingLabelUPS}</a>
                              </#if>
                              <form name="upsEmailReturnLabel${shipment_index}" method="post" action="<@pageUrl>upsEmailReturnLabelOrder</@pageUrl>">
                                <input type="hidden" name="orderId" value="${orderId}"/>
                                <input type="hidden" name="shipmentId" value="${shipment.shipmentId}"/>
                                <input type="hidden" name="shipmentRouteSegmentId" value="${shipmentRouteSegment.shipmentRouteSegmentId}" />
                              </form>
                              </@cell>
                            </@row>
                            </#if>
                          </#if>
                    </#list>
                </@td>
              </@tr>
           </#if>
        </#list>
      <#if orderContactMechValueMaps?has_content>
        <#list orderContactMechValueMaps as orderContactMechValueMap>
          <#assign contactMech = orderContactMechValueMap.contactMech>
          <#assign contactMechPurpose = orderContactMechValueMap.contactMechPurposeType>
          <@tr>
            <@td class="${styles.grid_large!}3">
              ${contactMechPurpose.get("description",locale)}
            </@td>
            <@td colspan="3">
              <#if contactMech.contactMechTypeId == "POSTAL_ADDRESS">
                <#assign postalAddress = orderContactMechValueMap.postalAddress>
                <#if postalAddress?has_content>
                     <#assign dummy = setContextField("postalAddress", postalAddress)>
                     <@render resource="component://party/widget/partymgr/PartyScreens.xml#postalAddressHtmlFormatter" />
                  <@updateOrderContactMech orderHeader=(orderHeader!) contactMechTypeId=contactMech.contactMechTypeId contactMechList=(postalContactMechList!) contactMechPurposeTypeId=(contactMechPurpose.contactMechPurposeTypeId!) contactMechAddress=(postalAddress!) />
                </#if>
              <#elseif contactMech.contactMechTypeId == "TELECOM_NUMBER">
                <#assign telecomNumber = orderContactMechValueMap.telecomNumber>
                  ${telecomNumber.countryCode!}
                  <#if telecomNumber.areaCode??>${telecomNumber.areaCode}-</#if>${telecomNumber.contactNumber}
                  <#--<#if partyContactMech.extension??>ext&nbsp;${partyContactMech.extension}</#if>-->
                  <#-- 
                  <#if !telecomNumber.countryCode?? || telecomNumber.countryCode == "011" || telecomNumber.countryCode == "1">
                    <a target="_blank" href="${uiLabelMap.CommonLookupAnywhoLink}" class="${styles.link_nav!} ${styles.action_find!} ${styles.action_external!}">${uiLabelMap.CommonLookupAnywho}</a>
                   <a target="_blank" href="${uiLabelMap.CommonLookupWhitepagesTelNumberLink}" class="${styles.link_nav!} ${styles.action_find!} ${styles.action_external!}">${uiLabelMap.CommonLookupWhitepages}</a>
                  </#if>-->
                <@updateOrderContactMech orderHeader=(orderHeader!) contactMechTypeId=contactMech.contactMechTypeId contactMechList=(telecomContactMechList!) contactMechPurposeTypeId=(contactMechPurpose.contactMechPurposeTypeId!) contactMechAddress=(telecomNumber!) />
              <#elseif contactMech.contactMechTypeId == "EMAIL_ADDRESS">
                ${contactMech.infoString} <@updateOrderContactMech orderHeader=(orderHeader!) contactMechTypeId=contactMech.contactMechTypeId contactMechList=(emailContactMechList!) contactMechPurposeTypeId=(contactMechPurpose.contactMechPurposeTypeId!) contactMechAddress=(contactMech!) />
                  
                  <#-- ToDo: Validate usefulness
                      <#if security.hasEntityPermission("ORDERMGR", "_SEND_CONFIRMATION", request)>
                         <a href="<@pageUrl>confirmationmailedit?orderId=${orderId}&amp;partyId=${partyId}&amp;sendTo=${contactMech.infoString}</@pageUrl>" class="${styles.link_run_sys!} ${styles.action_update!}" >${uiLabelMap.OrderSendConfirmationEmail}</a>
                      <#else>
                         <a href="mailto:${contactMech.infoString}" class="${styles.link_run_sys!} ${styles.action_send!} ${styles.action_external!}">(${uiLabelMap.OrderSendEmail})</a>
                      </#if>
                  -->
              <#elseif contactMech.contactMechTypeId == "WEB_ADDRESS">
                  ${contactMech.infoString}
                  <#assign openString = contactMech.infoString>
                  <#if !openString?starts_with("http") && !openString?starts_with("HTTP")>
                    <#assign openString = "http://" + openString>
                  </#if>
                  <a target="_blank" href="${openString}" class="${styles.link_nav!} ${styles.action_view!} ${styles.action_external}">(open&nbsp;page&nbsp;in&nbsp;new&nbsp;window)</a>
              <#else>
                  ${contactMech.infoString!}
              </#if>
            </@td>
          </@tr>
        </#list>
      </#if>
      </@table>
  </@section>
</#if>

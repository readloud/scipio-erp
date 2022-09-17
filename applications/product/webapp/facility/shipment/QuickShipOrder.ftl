<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->
<@script>
// function called from ShipmentScaleApplet when a weight is read
function setWeight(weight) {
  document.weightForm.weight.value = weight;
}
</@script>

<#if security.hasEntityPermission("FACILITY", "_VIEW", request)>
<#macro menuContent menuArgs={}>
  <@menu args=menuArgs>
    <@menuitem type="link" href=makePageUrl("quickShipOrder?facilityId=${facilityId}") text=uiLabelMap.ProductNextShipment class="+${styles.action_run_sys!} ${styles.action_continue!}"/>
    <#if shipment?has_content>
      <@menuitem type="link" href=makePageUrl("EditShipment?shipmentId=${shipmentId}") text=uiLabelMap.ProductEditShipment class="+${styles.action_nav!} ${styles.action_update!}"/>
    </#if>
  </@menu>
</#macro>
<@section title="${rawLabel('ProductQuickShipOrderFrom')} ${raw(facility.facilityName!)} [${rawLabel('CommonId')}:${raw(facilityId!)}]">

  <#if shipment??>
    <#if 1 < shipmentPackages.size()>
      <#-- multiple packages -->
      <@alert type="info">${uiLabelMap.ProductMorePackageFoundShipment}.</@alert>
    <#else>
      <#-- single package -->
      <#assign shipmentPackage = (Static["org.ofbiz.entity.util.EntityUtil"].getFirst(shipmentPackages))!>
      <#if shipmentPackage?has_content>
        <#assign weight = (shipmentPackage.weight)?default(0.00)>
        <#if (0 < weight?double) && !requestParameters.reweigh??>
          <@section>
          <#if (1 < shipmentRoutes.size())>
            <#-- multiple routes -->
            <@alert type="info">${uiLabelMap.ProductMoreRouteSegmentFound}.</@alert>
          <#elseif !requestParameters.shipmentRouteSegmentId?? || requestAttributes._ERROR_MESSAGE_??>
            <form name="routeForm" method="post" action="<@pageUrl>setQuickRouteInfo</@pageUrl>">
            <@fields type="default-manual">
              <#assign shipmentRoute = (Static["org.ofbiz.entity.util.EntityUtil"].getFirst(shipmentRoutes))!>
              <#assign carrierPerson = (shipmentRoute.getRelatedOne("CarrierPerson", false))!>
              <#assign carrierPartyGroup = (shipmentRoute.getRelatedOne("CarrierPartyGroup", false))!>
              <#assign shipmentMethodType = (shipmentRoute.getRelatedOne("ShipmentMethodType", false))!>
              <input type="hidden" name="facilityId" value="${facilityId!}"/>
              <input type="hidden" name="shipmentId" value="${shipmentRoute.shipmentId}"/>
              <input type="hidden" name="shipmentRouteSegmentId" value="${shipmentRoute.shipmentRouteSegmentId}"/>
            <@row>
              <@cell columns=9>
                <@field type="select" label=uiLabelMap.ProductCarrier name="carrierPartyId">
                      <#if shipmentRoute.carrierPartyId?has_content>
                        <option value="${shipmentRoute.carrierPartyId}">${(carrierPerson.firstName)!} ${(carrierPerson.middleName)!} ${(carrierPerson.lastName)!} ${(carrierPartyGroup.groupName)!} [${shipmentRoute.carrierPartyId}]</option>
                        <option value="${shipmentRoute.carrierPartyId}">---</option>
                      <#else>
                        <option value="">&nbsp;</option>
                      </#if>
                      <#list carrierPartyDatas as carrierPartyData>
                        <option value="${carrierPartyData.party.partyId}">${(carrierPartyData.person.firstName)!} ${(carrierPartyData.person.middleName)!} ${(carrierPartyData.person.lastName)!} ${(carrierPartyData.partyGroup.groupName)!} [${carrierPartyData.party.partyId}]</option>
                      </#list>
                </@field>
              </@cell>
              <@cell columns=3>
                <@field type="submit" submitType="link" href="javascript:document.routeForm.submit();" class="+${styles.link_run_sys!} ${styles.action_updatestatus!}" text=uiLabelMap.ProductConfirmShipmentUps />
              </@cell>
            </@row>
            <@row>
              <@cell columns=9>
                <@field type="select" label=uiLabelMap.ProductShipMethod name="shipmentMethodTypeId">
                      <#if shipmentMethodType?has_content>
                        <option value="${shipmentMethodType.shipmentMethodTypeId}">${shipmentMethodType.get("description",locale)}</option>
                        <option value="${shipmentMethodType.shipmentMethodTypeId}">---</option>
                      <#else>
                        <option value="">&nbsp;</option>
                      </#if>
                      <#list shipmentMethodTypes as shipmentMethodTypeOption>
                        <option value="${shipmentMethodTypeOption.shipmentMethodTypeId}">${shipmentMethodTypeOption.get("description",locale)}</option>
                      </#list>
                </@field>
              </@cell>
              <@cell columns=3>
                <@field type="submit" submitType="link" href=makePageUrl("quickShipOrder?facilityId=${facilityId}&shipmentId=${shipmentId}&reweigh=Y") class="+${styles.link_run_sys!} ${styles.action_update!}" text=uiLabelMap.ProductReWeighPackage />
              </@cell>
            </@row>
            <@row>
              <@cell columns=9>
                &nbsp;
              </@cell>
              <@cell columns=3>
                <@field type="submit" submitType="image" src=makeContentUrl("/images/spacer.gif") onClick="javascript:document.routeForm.submit();" />
              </@cell>
            </@row>
            </@fields>
            </form>
            <@script>
              document.routeForm.carrierPartyId.focus();
            </@script>
          <#else>
            <#-- display the links for label/packing slip -->
            <#assign allDone = "yes">
            <center>
              <a href="<@pageUrl>viewShipmentPackageRouteSegLabelImage?shipmentId=${requestParameters.shipmentId}&amp;shipmentRouteSegmentId=${requestParameters.shipmentRouteSegmentId}&amp;shipmentPackageSeqId=00001</@pageUrl>" target="_blank" class="${styles.link_run_sys!} ${styles.action_export!}">${uiLabelMap.ProductShippingLabel}</a><br />
              <a href="<@pageUrl>ShipmentManifest.pdf?shipmentId=${requestParameters.shipmentId}&amp;shipmentRouteSegmentId=${requestParameters.shipmentRouteSegmentId}</@pageUrl>" target="_blank" class="${styles.link_run_sys!} ${styles.action_export!}">${uiLabelMap.ProductPackingSlip}</a>
            </center>
          </#if>
          </@section>
        <#else>
          <@section>
          <form name="weightForm" method="post" action="<@pageUrl>setQuickPackageWeight</@pageUrl>">
            <#assign weightUom = shipmentPackage.getRelatedOne("WeightUom", false)!>
            <input type="hidden" name="facilityId" value="${facilityId!}"/>
            <input type="hidden" name="shipmentId" value="${shipmentPackage.shipmentId}"/>
            <input type="hidden" name="shipmentPackageSeqId" value="${shipmentPackage.shipmentPackageSeqId}"/>
              <@field type="generic" label="${rawLabel('ProductPackage')} ${raw(shipmentPackage.shipmentPackageSeqId)} ${rawLabel('ProductWeight')}">
                  <@field type="input" name="weight" />
                  <@field type="select" name="weightUomId">
                    <#if weightUom?has_content>
                      <option value="${weightUom.uomId}">${weightUom.get("description",locale)}</option>
                      <option value="${weightUom.uomId}">---</option>
                    </#if>
                    <#list weightUomList as weightUomOption>
                      <option value="${weightUomOption.uomId}">${weightUomOption.get("description",locale)} [${weightUomOption.abbreviation}]</option>
                    </#list>
                  </@field>
              </@field>
              <@field type="submitarea">
                  <@field type="submit" submitType="image" src=makeContentUrl("/images/spacer.gif") onClick="javascript:document.weightForm.submit();"/>
                  <@field type="submit" submitType="link" href="javascript:document.weightForm.submit();" class="+${styles.link_run_sys!} ${styles.action_update!}" text=uiLabelMap.ProductSetWeight />
              </@field>
          </form>
          <@script>
            document.weightForm.weight.focus();
          </@script>
          <#-- todo embed the applet
          <applet code="ShipmentScaleApplet.class" codebase="/images/" name="Package Weight Reader" width="0" height="0" MAYSCRIPT>
            <param name="serialPort" value="com1">
            <param name="fakeWeight" value="22">
          </applet>
          -->
          </@section>
        </#if>
      <#else>
        <div class="alert">${uiLabelMap.ProductErrorNoPackagesFoundForShipment} !</div>
      </#if>
      <hr />
      ${pages.get("/shipment/ViewShipmentInfo.ftl")}
      <br />${pages.get("/shipment/ViewShipmentItemInfo.ftl")}
      <br />${pages.get("/shipment/ViewShipmentPackageInfo.ftl")}
      <#if allDone?default("no") == "yes">
        <br />${pages.get("/shipment/ViewShipmentRouteInfo.ftl")}
      </#if>
    </#if>
  <#else>
    <@section>
    <form name="selectOrderForm" method="post" action="<@pageUrl>createQuickShipment</@pageUrl>">
      <input type="hidden" name="facilityId" value="${facilityId!}" />
      <input type="hidden" name="originFacilityId" value="${facilityId!}" />
      <input type="hidden" name="setPackedOnly" value="Y" />
        <@field type="input" label=uiLabelMap.ProductOrderNumber name="orderId" size="20" maxlength="20" value=(requestParameters.orderId!) />
        <@field type="submitarea">
            <@field type="submit" submitType="image" src=makeContentUrl("/images/spacer.gif") onClick="javascript:document.selectOrderForm.submit();" />
            <@field type="submit" submitType="link" href="javascript:document.selectOrderForm.submit();" class="+${styles.link_run_sys!} ${styles.action_update!}" text=uiLabelMap.ProductShipOrder />
        </@field>
    </form>
    <@script>
        document.selectOrderForm.orderId.focus();
    </@script>
    </@section>
  </#if>
</@section>
</#if>

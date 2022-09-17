<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->

<#if security.hasEntityPermission("FACILITY", "_VIEW", request)>
  <#if !(showWarningForm)>
    <@section title="${rawLabel('ProductWeighPackageOnly')} ${rawLabel('CommonIn')} ${facility.facilityName!} [${raw((facility.facilityId)!)}]">
        <#if invoiceIds?has_content>
          <div>
            ${uiLabelMap.CommonView} <a href="<@pageUrl>PackingSlip.pdf?shipmentId=${shipmentId}</@pageUrl>" target="_blank" class="${styles.link_run_sys!} ${styles.action_export!}">${uiLabelMap.ProductPackingSlip}</a> ${uiLabelMap.CommonOr}
            ${uiLabelMap.CommonView} <a href="<@pageUrl>ShipmentBarCode.pdf?shipmentId=${shipmentId}</@pageUrl>" target="_blank" class="${styles.link_run_sys!} ${styles.action_export!}">${uiLabelMap.ProductBarcode}</a> ${uiLabelMap.CommonFor} ${uiLabelMap.ProductShipmentId} <a href="<@pageUrl>EditShipment?shipmentId=${shipmentId}</@pageUrl>" class="${styles.link_nav_info_id!}">${shipmentId}</a>
          </div>
          <#if invoiceIds?? && invoiceIds?has_content>
            <div>
              <p>${uiLabelMap.AccountingInvoices}:</p>
              <@menu type="button">
                <#list invoiceIds as invoiceId>
                  <@menuitem type="generic">
                    ${uiLabelMap.CommonNbr} <a href="<@serverUrl>/accounting/control/invoiceOverview?invoiceId=${invoiceId}${raw(externalKeyParam)}</@serverUrl>" target="_blank" class="${styles.menu_button_item_link!} ${styles.action_nav!} ${styles.action_view!}">${invoiceId}</a>
                    (<a href="<@serverUrl>/accounting/control/invoice.pdf?invoiceId=${invoiceId}${raw(externalKeyParam)}</@serverUrl>" target="_blank" class="${styles.menu_button_item_link!} ${styles.action_run_sys!} ${styles.action_export!}">PDF</a>)
                  </@menuitem>
                </#list>
              </@menu>
            </div>
          </#if>
        </#if>
        <#if shipmentPackageRouteSegList?? && shipmentPackageRouteSegList?has_content>
          <#list shipmentPackageRouteSegList as shipmentPackageRouteSeg>
            <form name="viewShipmentPackageRouteSegLabelImageForm_${shipmentPackageRouteSeg_index}" method="post" action="<@pageUrl>viewShipmentPackageRouteSegLabelImage</@pageUrl>">
              <input type="hidden" name="shipmentId" value="${(shipmentPackageRouteSeg.shipmentId)!}"/>
              <input type="hidden" name="shipmentPackageSeqId" value="${(shipmentPackageRouteSeg.shipmentPackageSeqId)!}"/>
              <input type="hidden" name="shipmentRouteSegmentId" value="${(shipmentPackageRouteSeg.shipmentRouteSegmentId)!}"/>
              <div>
                <span>${uiLabelMap.ProductPackage}</span> ${(shipmentPackageRouteSeg.shipmentPackageSeqId)!}
                <#if shipmentPackageRouteSeg.labelImage??>
                  <a href="javascript:document.viewShipmentPackageRouteSegLabelImageForm_${shipmentPackageRouteSeg_index}.submit();" class="${styles.link_run_sys!} ${styles.action_view!}">${uiLabelMap.ProductViewLabelImage}</a>
                </#if>
              </div>
            </form>
          </#list>
        </#if>
     
        <#if !(orderId?has_content)>
          <@section>
          <form name="selectOrderForm" method="post" action="<@pageUrl>WeightPackageOnly</@pageUrl>">
            <input type="hidden" name="facilityId" value="${(facility.facilityId)!}" />
              <@field type="generic" label=uiLabelMap.ProductOrderId>
                  <@field type="input" inline=true name="orderId" size="20" maxlength="20" value=primaryOrderId!""/>
                  /
                  <@field type="input" inline=true name="shipGroupSeqId" size="6" maxlength="6" value=shipGroupSeqId!"00001"/>
              </@field>
              <@field type="submitarea">
                  <@field type="submit" submitType="link" href="javascript:document.selectOrderForm.action='${makePageUrl('PackOrder')}';document.selectOrderForm.submit();" class="+${styles.link_run_sys!} ${styles.action_update!}" text=uiLabelMap.ProductPackOrder />
                  <@field type="submit" submitType="link" href="javascript:document.selectOrderForm.submit();" class="+${styles.link_run_sys!} ${styles.action_verify!}" text=uiLabelMap.ProductWeighPackageOnly />
              </@field>
          </form>
          </@section>
         
          <#-- select picklist bin form -->
          <@section>
          <form name="selectPicklistBinForm" method="post" action="<@pageUrl>WeightPackageOnly</@pageUrl>">
            <input type="hidden" name="facilityId" value="${(facility.facilityId)!}" />
              <@field type="input" label=uiLabelMap.FormFieldTitle_picklistBinId name="picklistBinId" size="29" maxlength="60" value=(picklistBinId!)/>
              <@field type="submitarea">
                  <@field type="submit" submitType="link" href="javascript:document.selectPicklistBinForm.action='${makePageUrl('PackOrder')}';document.selectPicklistBinForm.submit();" class="+${styles.link_run_sys!} ${styles.action_update!}" text=uiLabelMap.ProductPackOrder />
                  <@field type="submit" submitType="link" href="javascript:document.selectPicklistBinForm.submit();" class="+${styles.link_run_sys!} ${styles.action_verify!}" text=uiLabelMap.ProductWeighPackageOnly />
              </@field>
          </form>
          </@section>
        <#else>
          <#assign packedLines = weightPackageSession.getPackedLines(orderId)/>
          <#if !(shipmentPackages?has_content)>
            <@section>
            <#if packedLines?has_content>
              <@table type="data-complex" class="+${styles.table_spacing_tiny_hint!}">
                <@thead>
                <@tr>
                  <@th>
                    ${uiLabelMap.ProductPackedWeight} (${("uiLabelMap.ProductShipmentUomAbbreviation_" + defaultWeightUomId)?eval}):
                  </@th>
                  <@th>
                    ${uiLabelMap.CommonDimension} (${("uiLabelMap.ProductShipmentUomAbbreviation_" + defaultDimensionUomId)?eval}):
                  </@th>
                  <@th>
                    ${uiLabelMap.ProductPackageInputBox}:
                  </@th>
                  <@th>&nbsp;</@th>
                  <@th>&nbsp;</@th>
                </@tr>
                </@thead>
                <@tbody>
                <#list packedLines as packedLine>
                  <#-- SCIPIO: FIXME: form in table (invalid html) -->
                  <form name="updateWeightPackageForm_${packedLine.getWeightPackageSeqId()}" method="post" action="<@pageUrl>updatePackedLine</@pageUrl>">
                    <input type="hidden" name="orderId" value="${orderId!}"/>
                    <input type="hidden" name="facilityId" value="${(facility.facilityId)!}"/>
                    <input type="hidden" name="weightPackageSeqId" value="${packedLine.getWeightPackageSeqId()}"/>
                    <@tr>
                      <@td>
                          ${uiLabelMap.ProductPackage} ${packedLine.getWeightPackageSeqId()}
                          <input type="text" size="7" name="packageWeight" value="${(packedLine.getPackageWeight())!}" />
                      </@td>
                      <@td>
                        <span>${uiLabelMap.CommonLength}<input type="text" name="packageLength" value="${(packedLine.getPackageLength())!}" size="5"/></span>
                        <span>${uiLabelMap.ProductWidth}<input type="text" name="packageWidth" value="${(packedLine.getPackageWidth())!}" size="5"/></span>
                        <span>${uiLabelMap.PartyHeight}<input type="text" name="packageHeight" value="${(packedLine.getPackageHeight())!}" size="5"/></span>
                      </@td>
                      <@td>
                        <select name="shipmentBoxTypeId">
                          <#if shipmentBoxTypes?has_content>
                            <#assign shipmentBoxTypeId = "${(packedLine.getShipmentBoxTypeId())!}"/>
                            <#list shipmentBoxTypes as shipmentBoxType>
                              <#if shipmentBoxTypeId == "${shipmentBoxType.shipmentBoxTypeId}">
                                <option value="${shipmentBoxType.shipmentBoxTypeId}">${shipmentBoxType.description}</option>
                              </#if>
                            </#list>
                            <option value=""></option>
                            <#list shipmentBoxTypes as shipmentBoxType>
                              <option value="${shipmentBoxType.shipmentBoxTypeId}">${shipmentBoxType.description}</option>
                            </#list>
                          </#if>
                        </select>
                      </@td>
                      <@td align="right"><a href="javascript:document.updateWeightPackageForm_${packedLine.getWeightPackageSeqId()}.submit()" class="${styles.link_run_sys!} ${styles.action_update!}">${uiLabelMap.CommonUpdate}</a></@td>
                      <@td align="right"><a href="javascript:document.updateWeightPackageForm_${packedLine.getWeightPackageSeqId()}.action='<@pageUrl>deletePackedLine</@pageUrl>';document.updateWeightPackageForm_${packedLine.getWeightPackageSeqId()}.submit();" class="${styles.link_run_sys!} ${styles.action_remove!}">${uiLabelMap.CommonDelete}</a></@td>
                    </@tr>
                  </form>
                </#list>
                </@tbody>
              </@table>
              <@field type="submit" submitType="link" href="javascript:document.completePackageForm.submit()" class="+${styles.link_run_sys!} ${styles.action_complete!}" text=uiLabelMap.ProductComplete />
              <form name="completePackageForm" method="post" action="<@pageUrl>completePackage</@pageUrl>">
                <input type="hidden" name="orderId" value="${orderId!}"/>
                <input type="hidden" name="shipGroupSeqId" value="${shipGroupSeqId!}"/>
                <input type="hidden" name="facilityId" value="${(facility.facilityId)!}"/>
                <input type="hidden" name="weightUomId" value="${defaultWeightUomId}"/>
                <input type="hidden" name="dimensionUomId" value="${defaultDimensionUomId}"/>
                <input type="hidden" name="shipmentId" value="${(shipment.shipmentId)!}"/>
                <input type="hidden" name="invoiceId" value="${(invoice.invoiceId)!}"/>
                <input type="hidden" name="estimatedShippingCost" value="${estimatedShippingCost!}"/>
                <input type="hidden" name="newEstimatedShippingCost" value="${newEstimatedShippingCost!}"/>
              </form>
            </#if>
            <#if (orderedQuantity > packedLines.size())>
              <form name="weightPackageForm" method="post" action="<@pageUrl>setPackageInfo</@pageUrl>">
                <input type="hidden" name="shipGroupSeqId" value="${shipGroupSeqId!}"/>
                <input type="hidden" name="facilityId" value="${(facility.facilityId)!}"/>
                <input type="hidden" name="orderId" value="${orderId!}"/>
                <#assign packedLines = weightPackageSession.getPackedLines(orderId)/>
                <#if packedLines?has_content>
                  <hr/>
                </#if>
            <#-- SCIPIO: NOTE/FIXME?: this table is meant to align with the previous table... maybe should be part of it... -->
            <@table type="data-complex" class="+${styles.table_spacing_tiny_hint!}">
                <@tr>
                  <@td>${uiLabelMap.ProductPackedWeight} (${("uiLabelMap.ProductShipmentUomAbbreviation_" + defaultWeightUomId)?eval}):
                      <br />
                      ${uiLabelMap.ProductPackage}
                      <input type="text" size="7" name="packageWeight" value=""/>
                  </@td>
                  <@td>
                    <span>${uiLabelMap.CommonDimension} (${("uiLabelMap.ProductShipmentUomAbbreviation_" + defaultDimensionUomId)?eval}):</span>
                    <br />
                    <span>${uiLabelMap.CommonLength}<input type="text" name="packageLength" value="" size="5"/></span>
                    <span>${uiLabelMap.ProductWidth}<input type="text" name="packageWidth" value="" size="5"/></span>
                    <span>${uiLabelMap.PartyHeight}<input type="text" name="packageHeight" value="" size="5"/></span>
                  </@td>
                  <@td>
                    <span>${uiLabelMap.ProductPackageInputBox}:</span>
                    <br />
                    <select name="shipmentBoxTypeId">
                      <#if shipmentBoxTypes?has_content>
                        <option value=""></option>
                        <#list shipmentBoxTypes as shipmentBoxType>
                          <option value="${shipmentBoxType.shipmentBoxTypeId}">${shipmentBoxType.description}</option>
                        </#list>
                      </#if>
                    </select>
                  </@td>
                  <@td align="right"><a href="javascript:document.weightPackageForm.submit()" class="${styles.link_run_sys!} ${styles.action_update!}">${uiLabelMap.ProductNextPackage}</a></@td>
                  <@td align="right">&nbsp;</@td>
                </@tr>
            </@table>
              </form>
            </#if>
            </@section>
          <#else>
            <@section>
            <@table type="data-complex" class="+${styles.table_spacing_tiny_hint!}"> 
             <@thead>
             <@tr>
                <@th>
                 ${uiLabelMap.ProductPackedWeight} (${("uiLabelMap.ProductShipmentUomAbbreviation_" + defaultWeightUomId)?eval}):
                </@th>
                 <@th>
                  ${uiLabelMap.CommonDimension} (${("uiLabelMap.ProductShipmentUomAbbreviation_" + defaultDimensionUomId)?eval}):
                </@th>
                <@th>
                  ${uiLabelMap.ProductPackageInputBox}:
               </@th>
              </@tr>
             </@thead>
             <@tbody>
              <form name="completePackForm" method="post" action="<@pageUrl>shipNow</@pageUrl>">
                <input type="hidden" name="orderId" value="${orderId!}"/>
                <input type="hidden" name="shipGroupSeqId" value="${shipGroupSeqId!}"/>
                <input type="hidden" name="facilityId" value="${(facility.facilityId)!}"/>
                <input type="hidden" name="shipmentId" value="${(shipment.shipmentId)!}"/>
                <input type="hidden" name="invoiceId" value="${(invoice.invoiceId)!}"/> 
                <#list shipmentPackages?sort_by("shipmentPackageSeqId") as shipmentPackage>
                  <@tr>
                    <@td>
                        ${uiLabelMap.ProductPackage} ${(shipmentPackage_index + 1)}
                        <input type="text" size="7" readonly="readonly" name="packageWeight" value="${(shipmentPackage.weight)!}" />
                    </@td>
                    <@td>
                      <span>${uiLabelMap.CommonLength}<input type="text" readonly="readonly" name="packageLength" value="${(shipmentPackage.boxLength)!}" size="5"/></span>
                      <span>${uiLabelMap.ProductWidth}<input type="text" readonly="readonly" name="packageWidth" value="${(shipmentPackage.boxWidth)!}" size="5"/></span>
                      <span>${uiLabelMap.PartyHeight}<input type="text" readonly="readonly" name="packageHeight" value="${(shipmentPackage.boxHeight)!}" size="5"/></span>
                    </@td>
                    <@td>
                      <#if (shipmentPackage.shipmentBoxTypeId)?has_content>
                        <#assign shipmentBoxType = delegator.findOne("ShipmentBoxType", {"shipmentBoxTypeId":shipmentPackage.shipmentBoxTypeId}, true)>
                      </#if>
                      <input type="text" readonly="readonly" name="shipmentBoxTypeId" value="${(shipmentBoxType.description)!}" size="50"/>
                    </@td>
                  </@tr>
                </#list>
              </form>
              </@tbody>
            </@table>
            <@field type="submit" submitType="link" href="javascript:document.completePackForm.submit()" class="+${styles.link_run_sys!} ${styles.action_complete!}" text=uiLabelMap.ProductComplete />
            </@section>
          </#if>
        </#if>
    </@section>
  <#else>
    <@section title=uiLabelMap.WebtoolsWarningLogLevel>
        <@alert type="warning">${uiLabelMap.FacilityWarningMessageThereIsMuchDifferenceInShippingCharges}&nbsp;[${uiLabelMap.FacilityEstimatedShippingCharges} = <@ofbizCurrency amount=(estimatedShippingCost!) isoCode=(shipment.currencyUomId!)/>, ${uiLabelMap.FacilityActualShippingCharges} = <@ofbizCurrency amount=(newEstimatedShippingCost!) isoCode=(shipment.currencyUomId!)/>]</@alert>
        <form name="shipNowForm" method="post" action="<@pageUrl>shipNow</@pageUrl>">
          <input type="hidden" name="orderId" value="${orderId!}"/>
          <input type="hidden" name="shipGroupSeqId" value="${shipGroupSeqId!}"/>
          <input type="hidden" name="facilityId" value="${(facility.facilityId)!}"/>
          <input type="hidden" name="shipmentId" value="${(shipment.shipmentId)!}"/>
        </form>
        <form name="holdShipmentForm" method="post" action="<@pageUrl>HoldShipment</@pageUrl>">
          <input type="hidden" name="orderId" value="${orderId!}"/>
          <input type="hidden" name="shipGroupSeqId" value="${shipGroupSeqId!}"/>
          <input type="hidden" name="facilityId" value="${(facility.facilityId)!}"/>
        </form>
        <div>
          <a href="javascript:document.shipNowForm.submit()" class="${styles.link_run_sys!} ${styles.action_send!}">${uiLabelMap.FacilityShip} ${uiLabelMap.CommonNow}</a>
          &nbsp;
          <a href="javascript:document.holdShipmentForm.submit()" class="${styles.link_run_sys!} ${styles.action_hold!}">${uiLabelMap.FacilityHoldShipment}</a>
        </div>
    </@section>
  </#if>
</#if>

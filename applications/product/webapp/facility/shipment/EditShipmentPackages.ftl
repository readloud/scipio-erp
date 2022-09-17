<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->

<#if shipment??>
    

    <@section>
        <@table type="data-complex" autoAltRows=false>
            <@thead>
                <@tr>
                    <@th>${uiLabelMap.ProductPackage}</@th>
                    <@th>${uiLabelMap.ProductWeight}</@th>
                    <@th>${uiLabelMap.ProductWeightUnit}</@th>
                    <@th>${uiLabelMap.ProductShipmentBoxType}</@th>
                    <@th>${uiLabelMap.ProductShipmentInsuredValuePackage}</@th>
                    <@th>${uiLabelMap.CommonCreated}</@th>
                    <@th>${uiLabelMap.ProductPackage} ${uiLabelMap.CommonContent}</@th>
                    <@th>${uiLabelMap.CommonUpdate}</@th>
                    <@th>${uiLabelMap.CommonDelete}</@th>
                </@tr>
            </@thead>
            
            <#list shipmentPackageDatas as shipmentPackageData>
                <#assign shipmentPackage = shipmentPackageData.shipmentPackage>
                <#assign shipmentPackageContents = shipmentPackageData.shipmentPackageContents!>
                <#assign shipmentPackageRouteSegs = shipmentPackageData.shipmentPackageRouteSegs!>
                <#assign weightUom = shipmentPackageData.weightUom!>
                <form method="post" action="<@pageUrl>updateShipmentPackage</@pageUrl>" name="updateShipmentPackageForm${shipmentPackageData_index}">
                    <input type="hidden" name="shipmentId" value="${shipmentId}"/>
                    <input type="hidden" name="shipmentPackageSeqId" value="${shipmentPackage.shipmentPackageSeqId}"/>
                    <@tr>
                        <@td>${shipmentPackage.shipmentPackageSeqId}</@td>                        
                        <@td>
                            <@field type="input" size="5" name="weight" value=(shipmentPackage.weight!)/>
                        </@td>
                        <@td>                            
                            <@field type="select" name="weightUomId">
                                <#if weightUom?has_content>
                                    <option value="${weightUom.uomId}">${weightUom.get("description",locale)}</option>
                                    <option value="${weightUom.uomId}">---</option>
                                <#else>
                                    <option value="">&nbsp;</option>
                                </#if>
                                <#list weightUoms as weightUomOption>
                                    <option value="${weightUomOption.uomId}">${weightUomOption.get("description",locale)} [${weightUomOption.abbreviation}]</option>
                                </#list>
                            </@field>
                        </@td>
                        <@td>
                            <@field type="select" name="shipmentBoxTypeId">
                                <option value="">&nbsp;</option>
                                <#list boxTypes as boxType>
                                    <option value="${boxType.shipmentBoxTypeId}"<#if shipmentPackage.shipmentBoxTypeId?? && shipmentPackage.shipmentBoxTypeId == boxType.shipmentBoxTypeId> selected="selected"</#if>>${boxType.get("description",locale)}</option>
                                </#list>
                            </@field>
                        </@td>
                        <@td>
                            <@field type="input" size="5" name="insuredValue" value=(shipmentPackage.insuredValue!)/>
                        </@td>
                        <@td>${(shipmentPackage.dateCreated?string('yyyy-MM-dd HH:mm')!)}</@td>
                        <@td><a href="#" id="shipmentPackageContent_${shipmentPackageData_index}" class="${styles.link_run_sys!} ${styles.action_update!}">${uiLabelMap.CommonContent}</a></@td>
                        <@td><a href="javascript:document.updateShipmentPackageForm${shipmentPackageData_index}.submit();" class="${styles.link_run_sys!} ${styles.action_update!}">${uiLabelMap.CommonUpdate}</a></@td>
                        <@td><a href="javascript:document.deleteShipmentPackage_${shipmentPackageData_index}.submit();" class="${styles.link_run_sys!} ${styles.action_remove!}">${uiLabelMap.CommonDelete}</a></@td>
                    </@tr>
                </form>
                <form name="deleteShipmentPackage_${shipmentPackageData_index}" method="post" action="<@pageUrl>deleteShipmentPackage</@pageUrl>">
                    <input type="hidden" name="shipmentId" value="${shipmentId}"/>
                    <input type="hidden" name="shipmentPackageSeqId" value="${shipmentPackage.shipmentPackageSeqId}"/>
                </form>
                <#-- 
               
                <#list shipmentPackageRouteSegs as shipmentPackageRouteSeg>
                    <form action="<@pageUrl>updateShipmentPackageRouteSeg</@pageUrl>" method="post" name="updateShipmentPackageRouteSegForm${shipmentPackageData_index}${shipmentPackageRouteSeg_index}">
                    <input type="hidden" name="shipmentId" value="${shipmentId}"/>
                    <input type="hidden" name="shipmentRouteSegmentId" value="${shipmentPackageRouteSeg.shipmentRouteSegmentId}"/>
                    <input type="hidden" name="shipmentPackageSeqId" value="${shipmentPackageRouteSeg.shipmentPackageSeqId}"/>
                    <@tr alt=alt_row>
                        <@td>&nbsp;</@td>
                        <@td><span>${uiLabelMap.ProductRouteSegment}</span> ${shipmentPackageRouteSeg.shipmentRouteSegmentId}</@td>
                        <@td><span>${uiLabelMap.ProductTrack}</span> <input type="text" size="22" name="trackingCode" value="${shipmentPackageRouteSeg.trackingCode!}"/></@td>
                        <@td>
                                <span>${uiLabelMap.ProductBox}</span>
                                <input type="text" size="5" name="boxNumber" value="${shipmentPackageRouteSeg.boxNumber!}"/>
                                <a href="javascript:document.updateShipmentPackageRouteSegForm${shipmentPackageData_index}${shipmentPackageRouteSeg_index}.submit();" class="${styles.link_run_sys!} ${styles.action_update!}">${uiLabelMap.CommonUpdate}</a>
                                <a href="javascript:document.deleteShipmentPackageRouteSeg${shipmentPackageData_index}${shipmentPackageRouteSeg_index}.submit();" class="${styles.link_run_sys!} ${styles.action_remove!}">${uiLabelMap.CommonDelete}</a>
                        </@td>
                        <@td>&nbsp;</@td>
                    </@tr>
                    </form>
                    <form name="deleteShipmentPackageRouteSeg${shipmentPackageData_index}${shipmentPackageRouteSeg_index}" method="post" action="<@pageUrl>deleteShipmentPackageRouteSeg</@pageUrl>">
                        <input type="hidden" name="shipmentId" value="${shipmentId}"/>
                        <input type="hidden" name="shipmentPackageSeqId" value="${shipmentPackageRouteSeg.shipmentPackageSeqId}"/>
                        <input type="hidden" name="shipmentRouteSegmentId" value="${shipmentPackageRouteSeg.shipmentRouteSegmentId}"/>
                    </form>
                </#list>
                -->  
            </#list>
        </@table>
    </@section>
    
    <#list shipmentPackageDatas as shipmentPackageData>        
        <#assign shipmentPackageContents = shipmentPackageData.shipmentPackageContents!>            
        <@modal id="shipmentPackageContent_${shipmentPackageData_index}">
            <#assign sectionTitle="${rawLabel('ProductPackage')} ${rawLabel('ContentContents')}"/>
            <@section title=sectionTitle>
                <#-- Scipio FIXME: Does makes sense to show this if all items have been packed already? -->
                <@section>
                    <form name="createShipmentPackageContentForm${shipmentPackageData_index}" method="post" action="<@pageUrl>createShipmentPackageContent</@pageUrl>">
                        <input type="hidden" name="shipmentId" value="${shipmentId}"/>
                        <input type="hidden" name="shipmentPackageSeqId" value="${shipmentPackage.shipmentPackageSeqId}"/>            
                        <@field type="select" name="shipmentItemSeqId" label=uiLabelMap.ProductAddFromItem>
                            <#list shipmentItems as shipmentItem>
                                <option>${shipmentItem.shipmentItemSeqId}</option>
                            </#list>
                        </@field>        
                        <@field type="input" name="quantity" size="5" value="0" label=uiLabelMap.ProductQuantity/>
                        <a href="javascript:document.createShipmentPackageContentForm${shipmentPackageData_index}.submit()" class="${styles.link_run_sys!} ${styles.action_add!}">${uiLabelMap.CommonAdd}</a>            
                    </form>
                </@section>
                <#-- -->

                <@table type="data-complex" autoAltRows=true>
                    <@thead>
                        <@tr>
                            <@th>${uiLabelMap.ProductItem}</@th>
                            <@th>${uiLabelMap.ProductQuantity}</@th>                    
                            <@th>${uiLabelMap.CommonDelete}</@th>
                        </@tr>
                    </@thead>
                    <#list shipmentPackageContents as shipmentPackageContent>
                        <@tr>
                            <@td>${shipmentPackageContent.shipmentItemSeqId}</@td>
                            <@td>${shipmentPackageContent.quantity!}</@td>
                            <@td><a href="javascript:document.deleteShipmentPackageContent${shipmentPackageData_index}${shipmentPackageContent_index}.submit();" class="${styles.link_run_sys!} ${styles.action_remove!}">${uiLabelMap.CommonDelete}</a></@td>                        
                        </@tr>
                        <form name="deleteShipmentPackageContent${shipmentPackageData_index}${shipmentPackageContent_index}" method="post" action="<@pageUrl>deleteShipmentPackageContent</@pageUrl>">
                            <input type="hidden" name="shipmentId" value="${shipmentId}"/>
                            <input type="hidden" name="shipmentPackageSeqId" value="${shipmentPackageContent.shipmentPackageSeqId}"/>
                            <input type="hidden" name="shipmentItemSeqId" value="${shipmentPackageContent.shipmentItemSeqId}"/>
                        </form>
                    </#list>
                </@table>
            </@section>
        </@modal>
        <@script>
            $(document).ready(function() {
                $('#shipmentPackageContent_${shipmentPackageData_index}').click(function() {
                    try {
                        $('#modal_shipmentPackageContent_${shipmentPackageData_index}').foundation('reveal','open');
                    } catch(err) {
                        try {
                            $('#modal_shipmentPackageContent_${shipmentPackageData_index}').modal('show');
                        } catch(err) {
                            //t.dispatchEvent(event);
                        }
                    }
                });
            });
        </@script>
    </#list>
<#else>
    <@section>
        <@commonMsg type="error">${uiLabelMap.ProductShipmentNotFoundId} : [${shipmentId!}]</@commonMsg>
    </@section>
</#if>


  
<#--
<@tr valign="middle" alt=alt_row>
    <form action="<@pageUrl>createShipmentPackageRouteSeg</@pageUrl>" name="createShipmentPackageRouteSegForm${shipmentPackageData_index}">
    <input type="hidden" name="shipmentId" value="${shipmentId}"/>
    <input type="hidden" name="shipmentPackageSeqId" value="${shipmentPackage.shipmentPackageSeqId}"/>
    <@td>&nbsp;</@td>
    <@td><span>${uiLabelMap.ProductAddRouteSegmentInfo}</span>
        <select name="shipmentRouteSegmentId">
            <#list shipmentRouteSegments as shipmentRouteSegment>
                <option>${shipmentRouteSegment.shipmentRouteSegmentId}</option>
            </#list>
        </select>
    </@td>
    <@td><span>Track ${uiLabelMap.CommonNbr}</span><input type="text" size="22" name="trackingCode"/></@td>
    <@td><span>Box ${uiLabelMap.CommonNbr}</span><input type="text" size="5" name="boxNumber"/></@td>
    <@td><a href="javascript:document.createShipmentPackageRouteSegForm${shipmentPackageData_index}.submit()" class="${styles.link_run_sys!} ${styles.action_add!}">${uiLabelMap.CommonAdd}</a></@td>
    <@td>&nbsp;</@td>
    </form>
</@tr>
-->
  <#--
        <form action="<@pageUrl>createShipmentPackage</@pageUrl>" name="createShipmentPackageForm">
            <input type="hidden" name="shipmentId" value="${shipmentId}"/>
            <@tr>
                <@td>${uiLabelMap.ProductNewPackage}</@td>
                <@td>&nbsp;</@td>
                <@td><span>${uiLabelMap.ProductWeight}</span> <input type="text" size="5" name="weight"/></@td>
                <@td><span>${uiLabelMap.ProductWeightUnit}</span>
                    <select name="weightUomId">
                        <#list weightUoms as weightUomOption>
                            <option value="${weightUomOption.uomId}">${weightUomOption.get("description",locale)} [${weightUomOption.abbreviation}]</option>
                        </#list>
                    </select>
                </@td>
                <@td><a href="javascript:document.createShipmentPackageForm.submit();" class="${styles.link_run_sys!} ${styles.action_add!}">${uiLabelMap.CommonCreate}</a></@td>
                <@td>&nbsp;</@td>
            </@tr>
        </form>
        -->
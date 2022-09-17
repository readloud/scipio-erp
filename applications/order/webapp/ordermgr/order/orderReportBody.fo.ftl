<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->
<#escape x as x?xml>
<#include "component://order/webapp/ordermgr/common/common.ftl">
<#import "component://content/webapp/content/common/contentlib.ftl" as contentlib>
<#import "component://accounting/webapp/accounting/common/acctlib.ftl" as acctlib>

<#if orderHeader?has_content><fo:block font-size="16pt" font-weight="bold" margin-bottom="5mm">${orderHeader.getRelatedOne("OrderType", false).get("description",locale)}</fo:block></#if>


    <#-- list of terms -->
    <#if terms?has_content>
    <fo:table table-layout="fixed" width="100%" inline-progression-dimension="auto">
        <fo:table-column/>

        <fo:table-header height="10mm">
          <fo:table-row>
            <fo:table-cell>
              <fo:block font-weight="bold">${uiLabelMap.AccountingAgreementItemTerms}</fo:block>
            </fo:table-cell>
          </fo:table-row>
        </fo:table-header>

        <fo:table-body>
          <#list terms as term>
          <#assign termType = term.getRelatedOne("TermType", false)/>
          <fo:table-row>
            <fo:table-cell>
              <fo:block font-size="10pt">${termType.description!} ${term.description!} ${term.termDays!} ${term.textValue!}</fo:block>
            </fo:table-cell>
          </fo:table-row>
          </#list>
        </fo:table-body>
    </fo:table>
    </#if>
    
    

    <fo:table table-layout="fixed" width="100%" space-before="20mm">
    <fo:table-column column-width="50mm"/>
    <fo:table-column column-width="55mm"/>
    <fo:table-column column-width="15mm"/>
    <fo:table-column column-width="25mm"/>
    <fo:table-column column-width="25mm"/>

    <fo:table-header height="10mm" font-size="12pt">
      <fo:table-row border-bottom-style="solid" border-bottom-width="thin" border-bottom-color="black">
        <fo:table-cell>
          <fo:block font-weight="bold">${uiLabelMap.OrderProduct}</fo:block>
        </fo:table-cell>
        <fo:table-cell>
          <fo:block font-weight="bold">${uiLabelMap.CommonDescription}</fo:block>
        </fo:table-cell>
        <fo:table-cell>
          <fo:block font-weight="bold" text-align="right">${uiLabelMap.CommonQty}</fo:block>
        </fo:table-cell>
        <fo:table-cell>
          <fo:block font-weight="bold" text-align="right">${uiLabelMap.OrderUnitList}</fo:block>
        </fo:table-cell>
        <fo:table-cell>
          <fo:block font-weight="bold" text-align="right"></fo:block>
        </fo:table-cell>
      </fo:table-row>
    </fo:table-header>

    <#-- SCIPIO: Factored out table row markup -->
    <#macro invoiceRow>
      <fo:table-row height="8mm" line-height="8mm">
        <fo:table-cell number-columns-spanned="5">
          <fo:block text-align="left" font-size="8pt">
            <#nested>
          </fo:block>
        </fo:table-cell>
      </fo:table-row>
    </#macro>


    <#-- SCIPIO: OrderItemAttributes and ProductConfigWrappers -->
    <#macro orderItemAttrInfo orderItem showCfgOpt=true showItemAttr=true>
      <#local orderItemSeqId = raw(orderItem.orderItemSeqId!)>
      <#if showCfgOpt>
        <#if orderItemProdCfgMap??>
          <#local cfgWrp = (orderItemProdCfgMap[orderItemSeqId])!false>
        <#else>
          <#local cfgWrp = false><#-- TODO -->
        </#if>
        <#if !cfgWrp?is_boolean>
          <#local selectedOptions = cfgWrp.getSelectedOptions()! />
          <#if selectedOptions?has_content>
            <@invoiceRow>
              <fo:list-block line-height="10pt" start-indent="2mm" provisional-distance-between-starts="3mm" provisional-label-separation="1mm">
                <#list selectedOptions as option>
                  <fo:list-item>
                    <fo:list-item-label end-indent="label-end()"><fo:block><fo:inline font-family="Symbol">&#x2022;</fo:inline></fo:block></fo:list-item-label>
                    <fo:list-item-body start-indent="body-start()"><fo:block>${option.getDescription()}</fo:block></fo:list-item-body>
                  </fo:list-item>
                </#list>
              </fo:list-block>
            </@invoiceRow>
          </#if>
        </#if>
      </#if>
      <#if showItemAttr>
        <#if orderItemAttrMap??>
          <#local orderItemAttributes = orderItemAttrMap[orderItemSeqId]!/>
        <#else>
          <#local orderItemAttributes = orderItem.getRelated("OrderItemAttribute", null, null, false)!/>
        </#if>
        <#if orderItemAttributes?has_content>
           <@invoiceRow>
              <fo:list-block line-height="10pt" start-indent="2mm" provisional-distance-between-starts="3mm" provisional-label-separation="1mm">
                <#list orderItemAttributes as orderItemAttribute>
                  <fo:list-item>
                    <fo:list-item-label end-indent="label-end()"><fo:block><fo:inline font-family="Symbol">&#x2022;</fo:inline></fo:block></fo:list-item-label>
                    <fo:list-item-body start-indent="body-start()"><fo:block>${orderItemAttribute.attrName} : ${orderItemAttribute.attrValue}</fo:block></fo:list-item-body>
                  </fo:list-item>
                </#list>
              </fo:list-block>
            </@invoiceRow>
        </#if>
      </#if>
    </#macro>

    <#macro orderItemGiftCardActInfo gcInfoList>
      <fo:list-block line-height="10pt" start-indent="2mm" provisional-distance-between-starts="3mm" provisional-label-separation="1mm">
        <#list gcInfoList as gcInfo>
          <fo:list-item>
            <fo:list-item-label end-indent="label-end()"><fo:block><fo:inline font-family="Symbol">&#x2022;</fo:inline></fo:block></fo:list-item-label>
            <fo:list-item-body start-indent="body-start()"><fo:block>${uiLabelMap.AccountingCardNumber} : ${acctlib.getGiftCardDisplayNumber(gcInfo.cardNumber!)}</fo:block></fo:list-item-body>
          </fo:list-item>
        </#list>
      </fo:list-block>
    </#macro>
    
    <#-- SCIPIO: Based on orderlib macro -->
    <#macro orderItemSurvResList survResList srqaArgs={} useTitleLine=false interactive=false maxInline=-1 class="" listClass="">
      <#local class = addClassArgDefault(class, "order-item-survres-list")>
        <#list survResList as surveyResponse>
            <#local survey = surveyResponse.getRelatedOne("Survey")!>
            <#if useTitleLine>
              <#local surveyDesc = survey.get("description", locale)!>
              <#if surveyDesc?has_content>${surveyDesc}</#if>
            </#if>
            <#if (maxInline != 0) && ("Y" == survey.showOnInvoice!)>
              <@contentlib.renderSurveyResponse surveyResponse=surveyResponse tmplLoc="component://content/template/survey/qalistresult.fo.ftl"
                srqaArgs=({"listClass":listClass, "max":maxInline} + srqaArgs)/>
            </#if>
        </#list>
    </#macro>

    <fo:table-body font-size="10pt" table-layout="fixed" width="100%">
        <#list orderItemList as orderItem>
            <#assign orderItemType = orderItem.getRelatedOne("OrderItemType", false)!>
            <#assign productId = orderItem.productId!>
            <#assign remainingQuantity = (orderItem.quantity?default(0) - orderItem.cancelQuantity?default(0))>
            <#assign itemAdjustment = Static["org.ofbiz.order.order.OrderReadHelper"].getOrderItemAdjustmentsTotal(orderItem, orderAdjustments, true, false, false)>
            <#assign internalImageUrl = Static["org.ofbiz.product.imagemanagement.ImageManagementHelper"].getInternalImageUrl(request, productId!)!>
            
            <fo:table-row height="8mm" line-height="8mm">
                <fo:table-cell>
                    <fo:block text-align="left">
                        <#if orderItem.supplierProductId?has_content>
                            <#assign origProductId = Static["org.ofbiz.product.product.ProductWorker"].getMainProductId(delegator, orderItem.supplierProductId, false)!"">
                            ${orderItem.supplierProductId}<#if origProductId?has_content> (${origProductId})</#if>
                        <#elseif productId?has_content>
                            <#assign origProductId = Static["org.ofbiz.product.product.ProductWorker"].getMainProductId(delegator, productId, false)!"">
                            ${productId}<#if origProductId?has_content> (${origProductId})</#if>
                        <#elseif orderItemType??>
                            ${orderItemType.get("description",locale)}
                        <#else>
                        </#if>
                    </fo:block>
                </fo:table-cell>
                <fo:table-cell>
                    <fo:block text-align="left">${orderItem.itemDescription!}</fo:block>
                </fo:table-cell>
                <fo:table-cell>
                    <fo:block text-align="right"><#if remainingQuantity??>${remainingQuantity?string.number}</#if> </fo:block>
                </fo:table-cell>
                <fo:table-cell text-align="right">
                    <fo:block> <#if orderItem.unitPrice??><@ofbizCurrency amount=(orderItem.unitPrice!) isoCode=(currencyUomId!)/></#if> </fo:block>
                </fo:table-cell>
                <fo:table-cell text-align="right">
                    <fo:block> <#if orderItem.statusId != "ITEM_CANCELLED">
                                <@ofbizCurrency amount=Static["org.ofbiz.order.order.OrderReadHelper"].getOrderItemSubTotal(orderItem, orderAdjustments) isoCode=currencyUomId/>
                            <#else>
                                <@ofbizCurrency amount=0.00 isoCode=currencyUomId/>
                            </#if>
                    </fo:block>
                </fo:table-cell>
            </fo:table-row>

            <#-- SCIPIO: NOTE: You may (un)comment or modify these calls to control the verbosity -->
            <@orderItemAttrInfo orderItem=orderItem showCfgOpt=true showItemAttr=true/>
            <#-- SCIPIO: Show purchased account brief/masked info -->
            <#assign gcInfoList = acctlib.getOrderItemGiftCardInfoList(orderItem, "")!>
            <#if gcInfoList?has_content>
              <@invoiceRow>
                <@orderItemGiftCardActInfo gcInfoList=gcInfoList/>
                <#assign survResList = orderlib.getOrderItemSurvResList(orderItem)!>
                <#if survResList?has_content>
                  <@orderItemSurvResList survResList=survResList/>
                </#if>
              </@invoiceRow>
            <#else>
              <#-- SCIPIO: show application survey response QA list for this item -->
              <@invoiceRow>
                <#assign survResList = orderlib.getOrderItemSurvResList(orderItem)!>
                <#if survResList?has_content>
                  <@orderItemSurvResList survResList=survResList/>
                </#if>
              </@invoiceRow>
            </#if>
        </#list>
                
        <#-- blank line -->
        <fo:table-row height="7px">
            <fo:table-cell number-columns-spanned="5"><fo:block><#-- blank line --></fo:block></fo:table-cell>
        </fo:table-row>

        <fo:table-row height="8mm" line-height="8mm">
           <fo:table-cell number-columns-spanned="2">
              <fo:block/>
           </fo:table-cell>
           <fo:table-cell number-columns-spanned="2" text-align="right" padding-before="3pt" padding-after="3pt">
              <fo:block>${uiLabelMap.OrderSubTotal}</fo:block>
           </fo:table-cell>
           <fo:table-cell text-align="right" border-top-style="solid" border-top-width="thin" border-top-color="black" border-bottom-style="solid" border-bottom-width="thin" border-bottom-color="black" padding-before="3pt" padding-after="3pt">
              <fo:block>
                 <@ofbizCurrency amount=orderSubTotal isoCode=currencyUomId/>
              </fo:block>
           </fo:table-cell>
        </fo:table-row>
        
        
        <#list orderHeaderAdjustments as orderHeaderAdjustment>
            <#assign adjustmentType = orderHeaderAdjustment.getRelatedOne("OrderAdjustmentType", false)>
            <#assign adjustmentAmount = Static["org.ofbiz.order.order.OrderReadHelper"].calcOrderAdjustment(orderHeaderAdjustment, orderSubTotal)>
            <#if adjustmentAmount != 0>
                <fo:table-row height="8mm" line-height="8mm" font-size="8pt" >
                    <fo:table-cell number-columns-spanned="4">
                        <fo:block text-align="right" font-weight="bold">${adjustmentType.get("description",locale)} :
                            <#if orderHeaderAdjustment.get("description")?has_content>
                                (${orderHeaderAdjustment.get("description")!})
                            </#if></fo:block>
                    </fo:table-cell>
                    <fo:table-cell text-align="right">
                        <fo:block><@ofbizCurrency amount=adjustmentAmount isoCode=currencyUomId/></fo:block>
                    </fo:table-cell>
                </fo:table-row>
            </#if>
        </#list>

        <#if otherAdjAmount != 0>
            <#-- blank line -->
            <fo:table-row height="7px">
                <fo:table-cell number-columns-spanned="5"><fo:block><#-- blank line --></fo:block></fo:table-cell>
            </fo:table-row>
            <fo:table-row height="8mm" line-height="8mm">
                <fo:table-cell number-columns-spanned="4">
                    <fo:block text-align="right" font-weight="bold">${uiLabelMap.OrderTotalOtherOrderAdjustments}:</fo:block>
                </fo:table-cell>
                <fo:table-cell text-align="right">
                    <fo:block><@ofbizCurrency amount=otherAdjAmount isoCode=currencyUomId/></fo:block>
                </fo:table-cell>
            </fo:table-row>
        </#if>
        
        <#if shippingAmount != 0>
            <fo:table-row height="8mm" line-height="8mm">
                <fo:table-cell number-columns-spanned="4">
                    <fo:block text-align="right" font-weight="bold">${uiLabelMap.OrderTotalShippingAndHandling}:</fo:block>
                </fo:table-cell>
                <fo:table-cell text-align="right" border-top-style="solid" border-top-width="thin" border-top-color="black" padding-before="3pt" padding-after="3pt">
                    <fo:block><@ofbizCurrency amount=shippingAmount isoCode=currencyUomId/></fo:block>
                </fo:table-cell>
            </fo:table-row>
        </#if>
        
        <#if taxAmount != 0>
            <fo:table-row height="8mm" line-height="8mm">
                <fo:table-cell number-columns-spanned="4">
                    <fo:block text-align="right" font-weight="bold">${uiLabelMap.OrderTotalSalesTax}:</fo:block>
                </fo:table-cell>
                <fo:table-cell text-align="right" border-top-style="solid" border-top-width="thin" border-top-color="black" padding-before="3pt" padding-after="3pt">
                    <fo:block><@ofbizCurrency amount=taxAmount isoCode=currencyUomId/></fo:block>
                </fo:table-cell>
            </fo:table-row>
        </#if>
        <#if orderVATTaxTotal != 0>
            <fo:table-row height="8mm" line-height="8mm">
                <fo:table-cell number-columns-spanned="4">
                    <fo:block text-align="right" font-weight="bold">${uiLabelMap.OrderSalesTaxIncluded}:</fo:block>
                </fo:table-cell>
                <fo:table-cell text-align="right" border-top-style="solid" border-top-width="thin" border-top-color="black">
                    <fo:block><@ofbizCurrency amount=orderVATTaxTotal isoCode=currencyUomId/></fo:block>
                </fo:table-cell>
            </fo:table-row>
        </#if>

        <#-- the grand total -->
        <#if grandTotal != 0>
            <fo:table-row>
               <fo:table-cell number-columns-spanned="2">
                  <fo:block/>
               </fo:table-cell>
               <fo:table-cell number-columns-spanned="2" padding-before="5pt" padding-after="5pt" font-size="13pt">
                  <fo:block text-align="right" font-weight="bold">${uiLabelMap.OrderTotalDue}:</fo:block>
               </fo:table-cell>
               <fo:table-cell text-align="right" border-top-style="double" border-top-width="thick" border-top-color="black" padding-before="5pt" padding-after="5pt" font-size="13pt">
                  <fo:block><@ofbizCurrency amount=grandTotal isoCode=(currencyUomId!)/></fo:block>
               </fo:table-cell>
            </fo:table-row>
            <fo:table-row height="7px">
               <fo:table-cell number-columns-spanned="5">
                  <fo:block/>
               </fo:table-cell>
            </fo:table-row>
        </#if>
        
    </fo:table-body>
 </fo:table>
</#escape>
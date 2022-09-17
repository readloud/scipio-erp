<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->
<#escape x as x?xml>

<#if shipGroups?? && shipGroups.size() gt 1>
    <fo:table table-layout="fixed" border-spacing="3pt" space-before="0.3in" font-size="9pt">
        <fo:table-column column-width="1in"/>
        <fo:table-column column-width="1in"/>
        <fo:table-column column-width="0.5in"/>
        <fo:table-header>
            <fo:table-row font-weight="bold">
                <fo:table-cell><fo:block>${uiLabelMap.OrderShipGroup}</fo:block></fo:table-cell>
                <fo:table-cell><fo:block>${uiLabelMap.OrderProduct}</fo:block></fo:table-cell>
                <fo:table-cell text-align="right"><fo:block>${uiLabelMap.OrderQuantity}</fo:block></fo:table-cell>
            </fo:table-row>
        </fo:table-header>
        <fo:table-body>
            <#list shipGroups as shipGroup>
                <#assign orderItemShipGroupAssocs = shipGroup.getRelated("OrderItemShipGroupAssoc", null, null, false)!>
                <#if orderItemShipGroupAssocs?has_content>
                    <#list orderItemShipGroupAssocs as shipGroupAssoc>
                        <#assign orderItem = shipGroupAssoc.getRelatedOne("OrderItem", false)!>
                        <fo:table-row>
                            <fo:table-cell><fo:block>${shipGroup.shipGroupSeqId}</fo:block></fo:table-cell>
                            <fo:table-cell><fo:block>${orderItem.productId!}</fo:block></fo:table-cell>
                            <fo:table-cell text-align="right"><fo:block>${shipGroupAssoc.quantity?string.number}</fo:block></fo:table-cell>
                        </fo:table-row>
                    </#list>
                </#if>
            </#list>
        </fo:table-body>
    </fo:table>
</#if>


<#if orderTerms?has_content && orderTerms.size() gt 0>
     <#-- a block with the invoice message-->
    <fo:block font-weight="bold">${uiLabelMap.OrderOrderTerms}:</fo:block>
    <#list orderTerms as orderTerm>
        <fo:block font-size="7pt">
            ${orderTerm.getRelatedOne("TermType", false).get("description",locale)} ${orderTerm.termValue!""} ${orderTerm.termDays!""} ${orderTerm.textValue!""}
        </fo:block>
    </#list>
</#if>


<#if orderHeader.getString("orderTypeId") == "SALES_ORDER">
  <fo:block font-size="7pt">
    <#--    Here is a good place to put policies and return information. -->
  </fo:block>
<#elseif orderHeader.getString("orderTypeId") == "PURCHASE_ORDER">
  <fo:block font-size="7pt">
    <#-- Here is a good place to put boilerplate terms and conditions for a purchase order. -->
  </fo:block>
</#if>
</#escape>
<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->
<#escape x as x?xml>

<fo:root xmlns:fo="http://www.w3.org/1999/XSL/Format">
    <fo:layout-master-set>
        <fo:simple-page-master master-name="main" page-height="11.694in" page-width="8.264in"
                margin-top="0.278in" margin-bottom="0.278in" margin-left="0.278in" margin-right="0.278in">
            <fo:region-body margin-top="1in"/>
            <fo:region-before extent="1in"/>
            <fo:region-after extent="1in"/>
        </fo:simple-page-master>
    </fo:layout-master-set>
    <#if hasPermission>
        <#if records?has_content>
            <fo:page-sequence master-reference="main">
                <fo:flow flow-name="xsl-region-body" font-family="Helvetica">
                    <#assign index = 1>
                    <#list records as record>
                        <#if index == 1>
                               <fo:table border="0.5pt solid black">
                                <fo:table-column column-width="252pt"/>
                                <fo:table-body>
                                    <fo:table-row>
                                        <fo:table-cell padding="2pt">
                                            <fo:block>${uiLabelMap.ManufacturingShipTo}:</fo:block>
                                        </fo:table-cell>
                                    </fo:table-row>
                                    <fo:table-row>
                                        <fo:table-cell padding="2pt">
                                            <fo:block font-size="18pt">${record.get("shippingAddressName")!}</fo:block>
                                        </fo:table-cell>
                                    </fo:table-row>
                                    <fo:table-row>
                                        <fo:table-cell padding="2pt">
                                            <fo:block>${record.get("shippingAddressAddress")!}</fo:block>
                                        </fo:table-cell>
                                    </fo:table-row>
                                    <fo:table-row>
                                        <fo:table-cell padding="2pt">
                                            <fo:block>${record.get("shippingAddressCity")!}</fo:block>
                                        </fo:table-cell>
                                    </fo:table-row>
                                </fo:table-body>
                            </fo:table>
                            <fo:block space-after.optimum="10pt" font-size="10pt"/>
                            <fo:table>
                                <fo:table-column column-width="63pt"/>
                                <fo:table-column column-width="63pt"/>
                                <fo:table-column column-width="93pt"/>
                                <fo:table-column column-width="33pt"/>
                                <fo:table-body border="0.5pt solid black">
                                    <fo:table-row>
                                        <fo:table-cell padding="2pt">
                                            <fo:block>${uiLabelMap.OrderOrderId}</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell padding="2pt">
                                            <fo:block>${uiLabelMap.ProductProductId}</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell padding="2pt">
                                            <fo:block>${uiLabelMap.CommonDescription}</fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell padding="2pt">
                                            <fo:block>${uiLabelMap.CommonQuantity}</fo:block>
                                        </fo:table-cell>
                                    </fo:table-row>
                                </fo:table-body>
                            </fo:table>
                        </#if>
                        <fo:table>
                            <fo:table-column column-width="63pt"/>
                            <fo:table-column column-width="63pt"/>
                            <fo:table-column column-width="93pt"/>
                            <fo:table-column column-width="33pt"/>
                            <fo:table-body>
                                <fo:table-row>
                                    <fo:table-cell padding="2pt">
                                        <fo:block>
                                            ${record.get("orderId")!} ${record.get("orderItemSeqId")!}
                                        </fo:block>
                                    </fo:table-cell>
                                    <fo:table-cell padding="2pt">
                                        <fo:block>
                                            ${record.get("productId")!}
                                        </fo:block>
                                    </fo:table-cell>
                                    <fo:table-cell padding="2pt">
                                        <fo:block>
                                            ${record.get("productName")!}
                                        </fo:block>
                                    </fo:table-cell>
                                    <fo:table-cell padding="2pt">
                                        <fo:block text-align="right">
                                            ${record.get("quantity")!}
                                        </fo:block>
                                    </fo:table-cell>
                                </fo:table-row>
                            </fo:table-body>
                        </fo:table>
                        <#assign shipmentPackageSeqId = record.get("shipmentPackageSeqId")>
                           <#if estimatedReadyDatePar?has_content>
                               <#assign shipDate = record.get("shipDate")>
                           </#if>
                           <#assign index = index + 1>
                    </#list>
                    <fo:table border="0.5pt solid black">
                        <fo:table-column column-width="84pt"/>
                        <fo:table-column column-width="84pt"/>
                        <fo:table-column column-width="84pt"/>
                        <fo:table-body>
                            <fo:table-row>
                                <fo:table-cell padding="2pt">
                                    <fo:block>${uiLabelMap.ManufacturingPackage}</fo:block>
                                </fo:table-cell>
                                <fo:table-cell padding="2pt">
                                    <fo:block>${uiLabelMap.ProductShipmentPlan}</fo:block>
                                </fo:table-cell>
                                <fo:table-cell padding="2pt">
                                    <fo:block>${uiLabelMap.ManufacturingEstimatedShipDate}</fo:block>
                                </fo:table-cell>
                            </fo:table-row>
                            <fo:table-row>
                                <fo:table-cell padding="2pt">
                                    <fo:block>
                                        ${shipmentIdPar!}/${shipmentPackageSeqId!}
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell padding="2pt">
                                    <fo:block>
                                        ${shipmentIdPar!}
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell padding="2pt">
                                    <fo:block>
                                        <#if shipDate?has_content>${shipDate!}</#if>
                                    </fo:block>
                                </fo:table-cell>
                            </fo:table-row>
                        </fo:table-body>
                    </fo:table>
                </fo:flow>
            </fo:page-sequence>
        <#else>
            <fo:page-sequence master-reference="main">
                <fo:flow flow-name="xsl-region-body" font-family="Helvetica">
                    <fo:block font-size="14pt">
                        ${uiLabelMap.ManufacturingNoDataAvailable}
                    </fo:block>
                </fo:flow>
            </fo:page-sequence>
        </#if>
    <#else>
        <fo:block font-size="14pt">
            ${uiLabelMap.ManufacturingViewPermissionError}
        </fo:block>
    </#if>
</fo:root>
</#escape>

<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->
<#escape x as x?xml>
    <fo:root xmlns:fo="http://www.w3.org/1999/XSL/Format">
        <fo:layout-master-set>
            <fo:simple-page-master master-name="main" page-height="8in" page-width="11in"
                    margin-top="0.1in" margin-bottom="1in" margin-left="0.2in" margin-right="0.5in">
                <fo:region-body margin-top="1in"/>
            </fo:simple-page-master>
        </fo:layout-master-set>
        <fo:page-sequence master-reference="main">
            <fo:flow flow-name="xsl-region-body" font-family="Helvetica">
                <fo:block text-align="center"><@render resource="component://order/widget/ordermgr/OrderPrintScreens.xml#CompanyLogo" /></fo:block>
                <#if glAccountOrgAndClassList?has_content>
                    <fo:block>${uiLabelMap.AccountingChartOfAcctsFor}
                        <#if (organizationPartyId)??>
                            <#assign partyName = (delegator.findOne("PartyNameView", {"partyId" : organizationPartyId}, false))!>
                            <#if partyName.partyTypeId == "PERSON">
                                ${(partyName.firstName)!} ${(partyName.lastName)!}
                            <#elseif ((partyName.partyTypeId)!) == "PARTY_GROUP">
                                ${(partyName.groupName)!}
                            </#if>
                        </#if>
                    </fo:block>
                    <fo:block>
                        <fo:table>
                            <fo:table-column column-width="15mm"/>
                            <fo:table-column column-width="20mm"/>
                            <fo:table-column column-width="30mm"/>
                            <fo:table-column column-width="30mm"/>
                            <fo:table-column column-width="20mm"/>
                            <fo:table-column column-width="15mm"/>
                            <fo:table-column column-width="25mm"/>
                            <fo:table-column column-width="35mm"/>
                            <fo:table-column column-width="30mm"/>
                            <fo:table-column column-width="30mm"/>
                            <fo:table-header>
                                <fo:table-cell border="1pt solid" border-width=".1mm">
                                    <fo:block text-align="center" font-size="6pt">${uiLabelMap.FormFieldTitle_glAccountId}</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="2pt solid" border-width=".1mm">
                                    <fo:block text-align="center" font-size="6pt">${uiLabelMap.FormFieldTitle_glAccountTypeId}</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="1pt solid" border-width=".1mm">
                                    <fo:block text-align="center" font-size="6pt">${uiLabelMap.FormFieldTitle_glAccountClassId}</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="1pt solid" border-width=".1mm">
                                    <fo:block text-align="center" font-size="6pt">${uiLabelMap.FormFieldTitle_glResourceTypeId}</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="1pt solid" border-width=".1mm">
                                    <fo:block text-align="center" font-size="6pt">${uiLabelMap.FormFieldTitle_glXbrlClassId}</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="1pt solid" border-width=".1mm">
                                    <fo:block text-align="center" font-size="6pt">${uiLabelMap.FormFieldTitle_parentGlAccountId}</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="1pt solid" border-width=".1mm">
                                    <fo:block text-align="center" font-size="6pt">${uiLabelMap.FormFieldTitle_accountCode}</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="1pt solid" border-width=".1mm">
                                    <fo:block text-align="center" font-size="6pt">${uiLabelMap.FormFieldTitle_accountName}</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="1pt solid" border-width=".1mm">
                                    <fo:block text-align="center" font-size="6pt">${uiLabelMap.CommonDescription}</fo:block>
                                </fo:table-cell>
                                <fo:table-cell border="1pt solid" border-width=".1mm">
                                    <fo:block text-align="center" font-size="6pt">${uiLabelMap.AccountingProductId}</fo:block>
                                </fo:table-cell>
                            </fo:table-header>
                            <fo:table-body>
                                <#list glAccountOrgAndClassList as glAccountOrgAndClass>
                                    <fo:table-row>
                                        <fo:table-cell border="1pt solid" border-width=".1mm">
                                            <fo:block text-align="center" font-size="5pt">${(glAccountOrgAndClass.glAccountId)!}</fo:block>        
                                        </fo:table-cell>
                                        <fo:table-cell border="1pt solid" border-width=".1mm">
                                            <fo:block text-align="center" font-size="5pt">
                                                <#if (glAccountOrgAndClass.glAccountTypeId)??>
                                                    <#assign glAccountType = (delegator.findOne("GlAccountType", {"glAccountTypeId" : (glAccountOrgAndClass.glAccountTypeId)!}, false))!>
                                                    ${(glAccountType.description)!}
                                                </#if>
                                            </fo:block>        
                                        </fo:table-cell>
                                        <fo:table-cell border="1pt solid" border-width=".1mm">
                                            <fo:block text-align="center" font-size="5pt">
                                                <#if (glAccountOrgAndClass.glAccountClassId)??>
                                                    <#assign glAccountClass = (delegator.findOne("GlAccountClass", {"glAccountClassId" : (glAccountOrgAndClass.glAccountClassId)!}, false))!>
                                                    ${(glAccountClass.description)!}
                                                </#if>
                                            </fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="1pt solid" border-width=".1mm">
                                            <fo:block text-align="center" font-size="5pt">
                                                <#if (glAccountOrgAndClass.glResourceTypeId)??>
                                                    <#assign glResourceType = (delegator.findOne("GlResourceType", {"glResourceTypeId" : (glAccountOrgAndClass.glResourceTypeId)!}, false))!>
                                                    ${(glResourceType.description)!}
                                                </#if>
                                            </fo:block>
                                        </fo:table-cell>
                                        <fo:table-cell border="1pt solid" border-width=".1mm">
                                            <fo:block text-align="center" font-size="5pt">
                                                <#if (glAccountOrgAndClass.glXbrlClassId)??>
                                                    <#assign glXbrlClass = (delegator.findOne("GlXbrlClass", {"glXbrlClassId" : (glAccountOrgAndClass.glXbrlClassId)!}, false))!>
                                                    ${(glXbrlClass.description)!}
                                                </#if>
                                            </fo:block>        
                                        </fo:table-cell>
                                        <fo:table-cell border="1pt solid" border-width=".1mm">
                                            <fo:block text-align="center" font-size="5pt">${(glAccountOrgAndClass.parentGlAccountId)!}</fo:block>        
                                        </fo:table-cell>
                                        <fo:table-cell border="1pt solid" border-width=".1mm">
                                            <fo:block text-align="center" font-size="5pt">${(glAccountOrgAndClass.accountCode)!}</fo:block>        
                                        </fo:table-cell>
                                        <fo:table-cell border="1pt solid" border-width=".1mm">
                                            <fo:block text-align="center" font-size="5pt">${(glAccountOrgAndClass.accountName)!}</fo:block>        
                                        </fo:table-cell>
                                        <fo:table-cell border="1pt solid" border-width=".1mm">
                                            <fo:block text-align="center" font-size="5pt">${(glAccountOrgAndClass.description)!}</fo:block>        
                                        </fo:table-cell>
                                        <fo:table-cell border="1pt solid" border-width=".1mm">
                                            <fo:block text-align="center" font-size="5pt">${(glAccountOrgAndClass.productId)!}</fo:block>        
                                        </fo:table-cell>
                                    </fo:table-row>
                                </#list>
                            </fo:table-body>
                        </fo:table>
                    </fo:block>
                <#else>
                    <fo:block text-align="center">${uiLabelMap.CommonNoRecordFound}</fo:block>
                </#if>
            </fo:flow>
        </fo:page-sequence>
    </fo:root>
</#escape>
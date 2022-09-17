<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->
<#escape x as x?xml>
<fo:root xmlns:fo="http://www.w3.org/1999/XSL/Format"
    <#-- inheritance -->
    <#if defaultFontFamily?has_content>font-family="${defaultFontFamily}"</#if>
>
    <fo:layout-master-set>
        <fo:simple-page-master master-name="simple-portrait"
              page-width="8.5in" page-height="11in"
              margin-top="0.3in" margin-bottom="0.3in"
              margin-left="0.4in" margin-right="0.3in">
            <fo:region-body margin-top="1in" margin-bottom="0.5in"/>
            <fo:region-before extent="1in"/>
            <fo:region-after extent="0.5in" />
        </fo:simple-page-master>
        <fo:simple-page-master master-name="simple-landscape"
              page-width="11in" page-height="8.5in"
              margin-top="0.3in" margin-bottom="0.3in"
              margin-left="0.4in" margin-right="0.3in">
            <fo:region-body margin-top="1in" margin-bottom="0.5in"/>
            <fo:region-before extent="1in"/>
            <fo:region-after extent="0.5in" />
        </fo:simple-page-master>
    </fo:layout-master-set>

    <fo:page-sequence master-reference="${pageLayoutName!"simple-portrait"}" font-size="8pt">
        <#-- Header -->
        <#-- The elements it it are positioned using a table composed by one row
             composed by two cells (each 50% of the total table that is 100% of the page):
             in the left side cell we put the logo
             in the right side cell we put the title, username and date
        -->
        <fo:static-content flow-name="xsl-region-before" font-size="${headerFontSize!"8pt"}">
            <fo:table>
                <fo:table-column column-number="1" column-width="proportional-column-width(50)"/>
                <fo:table-column column-number="2" column-width="proportional-column-width(50)"/>
                <fo:table-body>
                    <fo:table-row>
                        <fo:table-cell>
                        <#if logoImageUrl??>
                            <fo:block>
                                <fo:external-graphic src="${logoImageUrl}" overflow="hidden" height="40px" content-height="scale-to-fit"/>
                            </fo:block>
                        </#if>
                        </fo:table-cell>
                        <fo:table-cell>
                            <#-- The title of the report -->
                            <fo:block font-weight="bold" text-decoration="underline" space-after="0.03in">
                            </fo:block>
                            <#-- Username and date -->
                            <fo:list-block provisional-distance-between-starts="1in">
                                <fo:list-item>
                                    <fo:list-item-label>
                                        <fo:block font-weight="bold">${uiLabelMap.CommonUsername}</fo:block>
                                    </fo:list-item-label>
                                    <fo:list-item-body start-indent="body-start()">
                                        <fo:block><#if userLogin??>${userLogin.userLoginId!}</#if></fo:block>
                                    </fo:list-item-body>
                                </fo:list-item>
                                <fo:list-item>
                                    <fo:list-item-label>
                                        <fo:block font-weight="bold">${uiLabelMap.CommonDate}</fo:block>
                                    </fo:list-item-label>
                                    <fo:list-item-body start-indent="body-start()">
                                        <fo:block>${nowTimestamp!}</fo:block>
                                    </fo:list-item-body>
                                </fo:list-item>
                            </fo:list-block>
                        </fo:table-cell>
                    </fo:table-row>
                </fo:table-body>
            </fo:table>
        </fo:static-content>

        <#-- Footer -->
        <fo:static-content flow-name="xsl-region-after" font-size="${footerFontSize!"8pt"}">
            <fo:block text-align="center" border-top="thin solid black" padding="3pt">${uiLabelMap.CommonPage} <fo:page-number/> ${uiLabelMap.CommonOf} <fo:page-number-citation ref-id="theEnd"/></fo:block>
        </fo:static-content>

        <#-- Body -->
        <fo:flow flow-name="xsl-region-body" font-size="${bodyFontSize!"8pt"}">
            <fo:block font-weight="bold" text-decoration="underline" space-after="0.2in">
                ${uiLabelMap.CommonFollowingErrorsOccurred}:
            </fo:block>
            <fo:block space-after="0.2in" color="red">
                <#-- SCIPIO: FIXME: In principle should should be something like:
                      escapeEventMsg(errorMessage!, 'fomarkup')
                    but fomarkup doesn't exist yet and needs further validation. 
                    In meantime we will let the screen auto-escaping handle this...
                    NOTE: there is no special line-break handling. -->
                ${errorMessage!}
            </fo:block>
            <fo:block id="theEnd"/>
        </fo:flow>
    </fo:page-sequence>
</fo:root>
</#escape>
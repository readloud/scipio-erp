<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->
<#escape x as x?xml>
<fo:root xmlns:fo="http://www.w3.org/1999/XSL/Format"
    <#-- inheritance -->
    <#if defaultFontFamily?has_content>font-family="${defaultFontFamily}"</#if>>
    
    <#-- *************** -->
    <#-- Page Definition -->
    <#-- *************** -->
    <fo:layout-master-set>
        <#-- Regular -->
        <fo:simple-page-master master-name="main-page-first"
         page-width="210mm" page-height="297mm"
         margin-top="0.35cm"   margin-bottom="0.35cm"
         margin-left="20mm"  margin-right="10mm">
              <fo:region-body   margin-top="105mm" margin-left="0mm" margin-right="10mm" space-after="2.5cm"/>
              <fo:region-before extent="3cm" margin="48.5mm"/>
              <fo:region-after  extent="2cm" />
              <fo:region-start  extent="0cm"/>
              <fo:region-end    extent="0cm"/>
        </fo:simple-page-master>

        <fo:simple-page-master master-name="main-page-subsequent"
         page-width="210mm" page-height="297mm"
         margin-top="0.35cm"   margin-bottom="0.35cm"
         margin-left="20mm"  margin-right="20mm">
              <fo:region-body   margin-top="35mm" margin-left="5mm" margin-right="10mm"/>
              <!--<fo:region-before extent="3cm" margin="45mm" space-after="2.5cm"/>-->
              <fo:region-after  extent="2cm" />
              <fo:region-start  extent="0cm"/>
              <fo:region-end    extent="0cm"/>
        </fo:simple-page-master>

        <#-- Landscape view -->        
        <fo:simple-page-master master-name="main-page-landscape-first"
              page-width="203mm" page-height="297mm"
              margin-top="0.35cm" margin-bottom="135mm"
              margin-left="35mm" margin-right="35mm">
            <fo:region-body margin-top="0cm" margin-bottom="0cm" space-after="2.5cm"/>
            <fo:region-before extent="3cm"/>
            <fo:region-after extent="2cm"/>
            <fo:region-end    extent="0cm"/>
        </fo:simple-page-master>
        
        <fo:simple-page-master master-name="main-page-landscape-subsequent"
              page-width="203mm" page-height="297mm"
              margin-top="0.35cm" margin-bottom="35mm"
              margin-left="35mm" margin-right="35mm">
            <fo:region-body margin-top="35mm" margin-bottom="0cm" space-after="2.5cm"/>
            <!--<fo:region-before extent="3cm"/>-->
            <fo:region-after extent="2cm"/>
            <fo:region-end    extent="0cm"/>
        </fo:simple-page-master>

        <#-- Sequence -->
        <fo:page-sequence-master master-name="page-sequence">
            <fo:repeatable-page-master-alternatives>
                <fo:conditional-page-master-reference page-position="first" master-reference="${pageLayoutName!"main-page"}-first"/>
                <fo:conditional-page-master-reference page-position="rest" master-reference="${pageLayoutName!"main-page"}-subsequent"/>
            </fo:repeatable-page-master-alternatives>
        </fo:page-sequence-master>
    </fo:layout-master-set>


    <#-- ************* -->
    <#-- Page Content -->
    <#-- ************* -->
    <fo:page-sequence master-reference="page-sequence">

        <#-- Header -->
        <#-- The elements it it are positioned using a table composed by one row
             composed by two cells (each 50% of the total table that is 100% of the page):
             in the left side cell the "topLeft" template is included
             in the right side cell the "topRight" template is included
        -->
        <fo:static-content flow-name="xsl-region-before">
            <fo:table table-layout="fixed" width="100%" >
                <fo:table-column column-number="1" padding-right="10mm"/><#-- invalid attr: padding-left="5mm" padding-right="5mm" -->
                <fo:table-column column-number="2" column-width="80mm"/><#-- invalid attr: padding-left="5mm" padding-right="5mm" -->
                <fo:table-body>
                    <fo:table-row>
                        <fo:table-cell><fo:block>${sections.render("topLeft")}</fo:block></fo:table-cell>
                        <fo:table-cell><fo:block>${sections.render("topRight")}</fo:block></fo:table-cell>
                    </fo:table-row>
                </fo:table-body>
            </fo:table>
        </fo:static-content>

        <#-- the footer -->
        <fo:static-content flow-name="xsl-region-after">
            <fo:block>
                ${sections.render("footer")}
            </fo:block>    
        </fo:static-content>

        <#-- page number -->
        <fo:static-content flow-name="xsl-region-end">
            <fo:block font-size="8pt" text-align="center">
                <#-- ${uiLabelMap.CommonPage} <fo:page-number/> ${uiLabelMap.CommonOf}  --><fo:page-number/>/<fo:page-number-citation ref-id="theEnd"/>
            </fo:block>
        </fo:static-content>

        <#-- the body -->
        <fo:flow flow-name="xsl-region-body">
            ${sections.render("body")}  
            <fo:block id="theEnd"/>  <#-- marks the end of the pages and used to identify page-number at the end -->
        </fo:flow>
    </fo:page-sequence>
</fo:root>
</#escape>

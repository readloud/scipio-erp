<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->

<#-- Render the category page -->
<#if requestAttributes.productCategoryId?has_content>
  <@render resource="component://shop/widget/CatalogScreens.xml#bestSellingCategory" />
  <@render resource="component://shop/widget/CatalogScreens.xml#category-include" />
<#else>
  <center><@heading>${uiLabelMap.EcommerceNoPROMOTIONCategory}</@heading></center>
</#if>

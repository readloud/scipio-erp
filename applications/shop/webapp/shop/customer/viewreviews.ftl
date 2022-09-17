<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->
<#include "component://shop/webapp/shop/customer/customercommon.ftl">

<#if reviews?has_content>
  <@section title=uiLabelMap.ProductReviews>
    <@table type="data-list">
      <@tr>
        <@th>${uiLabelMap.EcommerceSentDate}</@th>
        <@th>${uiLabelMap.ProductProductId}</@th>
        <@th>${uiLabelMap.ProductReviews}</@th>
        <@th>${uiLabelMap.ProductRating}</@th>
        <@th>${uiLabelMap.CommonIsAnonymous}</@th>
        <@th>${uiLabelMap.CommonStatus}</@th>
      </@tr>
      <#list reviews as review>
        <@tr>
          <@td>${review.postedDateTime!}</@td>
          <@td><a href="<@catalogAltUrl productId=review.productId/>" style="${styles.link_nav_info_id!}>${review.productId}</a></@td>
          <@td>${review.productReview!}</@td>
          <@td>${review.productRating}</@td>
          <@td>${review.postedAnonymous!}</@td>
          <@td>${review.getRelatedOne("StatusItem", false).get("description", locale)}</@td>
        </@tr>
      </#list>
    </@table>
  </@section>
</#if>

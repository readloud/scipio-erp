<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->

<#-- SCIPIO: DEPRECATED TEMPLATE -->

<#-- SCIPIO: NOTE: this omits the top @menu; the including template must provide its own -->
<#if shipAddr??>
  <#if anontrailMenuArgs?has_content>
    <#--<p><@objectAsScript lang="raw" object=anontrailMenuArgs /></p>-->
    <#-- SCIPIO: WARN: Although @menu will in theory support args maps from groovy context, at current
        time it is not well tested and could be a source of errors here... -->
    <@menu args=anontrailMenuArgs>
      <@menuitem type="link" href=makePageUrl("setShipping") class="+${styles.action_nav!} ${trailClass.shipAddr}" text=uiLabelMap.EcommerceChangeShippingAddress />
      <#if shipOptions??>
        <@menuitem type="link" href=makePageUrl("setShipOptions") class="+${styles.action_nav!} ${trailClass.shipOptions}" text=uiLabelMap.EcommerceChangeShippingOptions />
        <#if billing??>
          <@menuitem type="link" href=makePageUrl("setBilling?resetType=Y") class="+${styles.action_nav!} ${trailClass.paymentType}" text=uiLabelMap.EcommerceChangePaymentInfo />
        </#if>
      </#if>
    </@menu>
  <#else>
      <@menuitem type="link" href=makePageUrl("setShipping") class="+${styles.action_nav!} ${trailClass.shipAddr}" text=uiLabelMap.EcommerceChangeShippingAddress />
      <#if shipOptions??>
        <@menuitem type="link" href=makePageUrl("setShipOptions") class="+${styles.action_nav!} ${trailClass.shipOptions}" text=uiLabelMap.EcommerceChangeShippingOptions />
        <#if billing??>
          <@menuitem type="link" href=makePageUrl("setBilling?resetType=Y") class="+${styles.action_nav!} ${trailClass.paymentType}" text=uiLabelMap.EcommerceChangePaymentInfo />
        </#if>
      </#if>
  </#if>
</#if>

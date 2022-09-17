<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->
<span>${uiLabelMap.OrderOrderQuoteCoefficients}</span>
<br />
<#list quoteCoefficients as quoteCoefficient>
    <div>${quoteCoefficient.coeffName}&nbsp;${quoteCoefficient.coeffValue}</div>
</#list>
<br />
<div><span>${uiLabelMap.CommonTotalCostMult}</span>&nbsp;${costMult}</div>
<div><span>${uiLabelMap.CommonTotalCostToPriceMult}</span>&nbsp;${costToPriceMult}</div>
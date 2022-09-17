<#assign chartType=chartType!"bar"/>    <#-- (line|bar|pie) default: line -->
<#assign library=chartLibrary!"chart"/>
<#assign datasets=(chartDatasets!1)?number />
<#if sales??>
  <#assign sales=rewrapMap(sales, "raw-simple")>
</#if>

<#if sales?has_content> 
    <#if chartType == "line" || chartType == "bar">        
        <@chart type=chartType title=chartTitle library=library xlabel=(xlabel!"") ylabel=(ylabel!"") label1=(label1!"") label2=(label2!"")>
            <#list mapKeys(sales) as key>
                <#assign currData = sales[key] />
                <#if currData?has_content>                          
                   <@chartdata value=((currData.total)!0) value2=((currData.count)!0) title=key/>
                </#if>
            </#list>
        </@chart>
    <#elseif chartType == "pie">
        <@commonMsg type="error">${uiLabelMap.CommonUnsupported}</@commonMsg>
    <#else>
        <@commonMsg type="error">${uiLabelMap.CommonUnsupported}</@commonMsg>
    </#if>
<#elseif sales??>
    <@commonMsg type="result-norecord"/>            
</#if>
<@section>
    <form name="TrackingCodeReportOptions" action="<@pageUrl>AnalyticsTracking</@pageUrl>" method="post">
        <#--<@field type="select" label=uiLabelMap.ProductProductStore name="productStoreId">
            <@field type="option" value="" 
              selected=(!productStoreId?has_content)>${uiLabelMap.CommonAny}</@field>
            <#list productStores as store>
                <@field type="option" value=store.productStoreId 
                  selected=(store.productStoreId == (productStoreId!""))>${store.storeName!(store.productStoreId!)}</@field>
            </#list>
        </@field>-->
    
        <@field name="fromDate" type="datetime" value=(parameters.fromDate!) label=uiLabelMap.CommonFrom />
        <@field name="thruDate" type="datetime" value=(parameters.thruDate!) label=uiLabelMap.CommonThru tooltip=uiLabelMap.CommonLeaveEmptyForNowDate/>
        <@field type="select" name="intervalScope" label=uiLabelMap.CommonTimeInterval required=true><#-- uiLabelMap.CommonIntervalScope -->
            <#assign intervals = UtilDateTime.getTimeIntervals() />
            <#assign currInterval = chartIntervalScope!parameters.intervalScope!"">
            <#-- This contradicted the groovy which required an interval scope
            <option value=""></option>-->
            <#list intervals as interval>
                <option value="${interval}"<#if currInterval == interval> selected="selected"</#if>>${getLabel("Common" + interval?capitalize)}</option>
            </#list>
        </@field>
        
        <@field name="marketingCampaignId" id="marketingCampaignId" type="select" label=uiLabelMap.MarketingCampaignId>
            <option value="">--</option>
            <#list marketingCampaignList as marketingCampaign>
                <option value="${marketingCampaign.marketingCampaignId}"<#if parameters.marketingCampaignId?has_content && parameters.marketingCampaignId == marketingCampaign.marketingCampaignId> selected="selected"</#if>>
                    ${marketingCampaign.campaignName}
                </option>
            </#list>
            <@script>
                $(document).ready(function() {
                    $("#marketingCampaignId").change(function() {
                        $.ajax({
                            url: '<@pageUrl>AnalyticsFindTrackingCodes</@pageUrl>',
                            type: 'POST',
                            data: {
                                "marketingCampaignId" : $(this).val()                         
                            },
                            success: function(data) {
                                trackingCodes = data.trackingCodeList;
                                $("#trackingCodeId").html("");
                                var options = "<option value=''>--</option>";                
                                for (i = 0; i < trackingCodes.length; i++) {
                                    options = options + " <option value='" + trackingCodes[i].trackingCodeId + "'>" + trackingCodes[i].description + "</option>";                                    
                                }
                                $("#trackingCodeId").html(options);
                            }
                        });
                    });
                });
            </@script>
        </@field>

        <@field id="trackingCodeId" name="trackingCodeId" type="select" label=uiLabelMap.MarketingTrackingCode>
            <option value="">--</option>
            <#if trackingCodeList?has_content>
                <#list trackingCodeList as trackingCode>                    
                    <option value="${trackingCode.trackingCodeId}"<#if parameters.trackingCodeId?has_content && parameters.trackingCodeId == trackingCode.trackingCodeId> selected="selected"</#if>>${trackingCode.description}</option>
                </#list>
            </#if>
        </@field>
        
        <#--<@field type="select" label=uiLabelMap.CommonCurrency name="currencyUomId" tooltip="${rawLabel('SfaOnlyConvertibleCurrenciesListed')} ${rawLabel('SfaCurrencyConvertedUsingLastKnownRates')}">
            <option value=""<#if !parameters.currencyUomId?has_content> selected="selected"</#if>>${uiLabelMap.CommonDefault}</option>
            <#list currencies as currency>
                <option value="${currency.uomId}"<#if (parameters.currencyUomId!'') == currency.uomId> selected="selected"</#if>>${currency.uomId}</option>
            </#list>
        </@field>-->
        
        <@field name="run" type="submit" value="" text=uiLabelMap.CommonSubmit />
    </form>
</@section>

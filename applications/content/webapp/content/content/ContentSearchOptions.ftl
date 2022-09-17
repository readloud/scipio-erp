<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->
<@section title=uiLabelMap.CommonAdvancedSearch>

    <form name="advToKeyWordSearchForm" method="post" action="<@pageUrl>ContentSearchResults</@pageUrl>">
      <#-- SCIPIO: don't hardcode this... use sys default -->
      <#--<input type="hidden" name="VIEW_SIZE" value="25"/>-->
        <@field type="generic" label=uiLabelMap.ContentKeywords>
            <@field type="input" label="hey" name="SEARCH_STRING" size="40" value=(requestParameters.SEARCH_STRING!)/>
            <@field type="radio" name="SEARCH_OPERATOR" value="OR" checked=(searchOperator == "OR") label=uiLabelMap.CommonAny />
            <@field type="radio" name="SEARCH_OPERATOR" value="AND" checked=(searchOperator == "AND") label=uiLabelMap.CommonAll />
        </@field>
        <@field type="lookup" label=uiLabelMap.FormFieldTitle_contentId value=(requestParameters.SEARCH_CONTENT_ID!) formName="advToKeyWordSearchForm" name="SEARCH_CONTENT_ID" id="SEARCH_CONTENT_ID" fieldFormName="LookupContent"/>
        <@field type="generic" label=uiLabelMap.FormFieldTitle_contentAssocTypeId>
            <@field type="select" name="contentAssocTypeId">
                <option value="">- ${uiLabelMap.ContentAnyAssocType} -</option>
              <#list contentAssocTypes as contentAssocType>
                <option value="${contentAssocType.contentAssocTypeId}">${contentAssocType.description}</option>
              </#list>
            </@field>
            <span>${uiLabelMap.ContentIncludeAllSubContents}?</span>
            <@field type="radio" name="SEARCH_SUB_CONTENTS" value="Y" checked=true label=uiLabelMap.CommonYes />
            <@field type="radio" name="SEARCH_SUB_CONTENTS" value="N" label=uiLabelMap.CommonNo />
        </@field>
        <@field type="lookup" label=uiLabelMap.PartyPartyId value=(requestParameters.partyId!) formName="advToKeyWordSearchForm" name="partyId" id="partyId" fieldFormName="LookupPartyName"/>
        <@field type="select" label=uiLabelMap.PartyRoleTypeId name="partyRoleTypeId">
            <option value="">- ${uiLabelMap.CommonAnyRoleType} -</option>
          <#list roleTypes as roleType>
            <option value="${roleType.roleTypeId}">${roleType.description}</option>
          </#list>
        </@field>
        <@field type="generic" label=uiLabelMap.ContentLastUpdatedDateFilter>
            <@field type="datetime" label=uiLabelMap.CommonFrom name="fromDate" value=(requestParameters.fromDate!) size="25" maxlength="30" id="fromDate1"/>
            <@field type="datetime" label=uiLabelMap.CommonThru name="thruDate" value=(requestParameters.thruDate!) size="25" maxlength="30" id="thruDate1"/>
        </@field>
        <@field type="generic" label=uiLabelMap.CommonSortedBy>
            <@field type="select" name="sortOrder">
              <option value="SortKeywordRelevancy">${uiLabelMap.ProductKeywordRelevancy}</option>
              <option value="SortContentField:contentName">${uiLabelMap.FormFieldTitle_contentName}</option>
            </@field>
            <@field type="radio" name="sortAscending" value="Y" checked=true label=uiLabelMap.ProductLowToHigh/>
            <@field type="radio" name="sortAscending" value="N" label=uiLabelMap.ProductHighToLow/>
        </@field>
        <#if searchConstraintStrings?has_content>
          <@field type="generic" label=uiLabelMap.ProductLastSearch>
              <#list searchConstraintStrings as searchConstraintString>
                <div>&nbsp;-&nbsp;${searchConstraintString}</div>
              </#list>
              <div>${uiLabelMap.CommonSortedBy} ${searchSortOrderString}</div>
              <div>
                <@field type="radio" name="clearSearch" value="Y" checked=true label=uiLabelMap.ProductNewSearch/>
                <@field type="radio" name="clearSearch" value="N" label=uiLabelMap.CommonRefineSearch/>
              </div>
          </@field>
        </#if>
        <@field type="submitarea">
            <@field type="submit" submitType="link" href="javascript:document.advToKeyWordSearchForm.submit()" class="+${styles.link_run_sys!} ${styles.action_find!}" text=uiLabelMap.CommonFind />
            <@field type="submit" submitType="image" src=makeContentUrl("/images/spacer.gif") onClick="javascript:document.advToKeyWordSearchForm.submit();"/>
        </@field>
    </form>
</@section>

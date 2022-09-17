<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->

<#if security.hasEntityPermission("ORDERMGR", "_CREATE", request)>
  <@section title=uiLabelMap.PartyParty> <#-- class="boxoutside" -->
      <@table type="fields" width="100%" class="+boxbottom">
        <@tr>
          <@td align="center">
            <#if person?has_content>
              <div><a href="${customerDetailLink}${partyId}${raw(externalKeyParam!)}" class="${styles.link_nav_info_name!}">${person.firstName!}&nbsp;${person.lastName!}</a></div>
            <#elseif partyGroup?has_content>
              <div class="tabletext"><a href="${customerDetailLink}${partyId}${raw(externalKeyParam!)}" class="${styles.link_nav_info_name!}">${partyGroup.groupName!}</a></div>
            </#if>
            <form method="post" action="<@pageUrl>orderentry</@pageUrl>" name="setpartyform">
              <div><input type="text" name="partyId" size="10" value="${partyId!}" /></div>
              <div>
                <a href="javascript:document.setpartyform.submit();" class="${styles.link_run_session!} ${styles.action_update!}">${uiLabelMap.CommonSet}</a>&nbsp;|&nbsp;<a href="<@serverUrl>/partymgr/control/findparty</@serverUrl>" class="${styles.link_nav!} ${styles.action_find!}">${uiLabelMap.CommonFind}</a><#if partyId?default("_NA_") != "_NA_" && (partyId!"_NA_") != "">&nbsp;|&nbsp;<a href="${customerDetailLink}${partyId}${raw(externalKeyParam!)}" class="${styles.link_nav!} ${styles.action_view!}">${uiLabelMap.CommonView}</a></#if>
              </div>
            </form>
          </@td>
        </@tr>
      </@table>
  </@section>
<#else>
  <@commonMsg type="error">${uiLabelMap.OrderViewPermissionError}</@commonMsg>
</#if>

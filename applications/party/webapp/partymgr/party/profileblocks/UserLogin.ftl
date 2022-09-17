<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->

  <@section id="partyUserLogins" title=uiLabelMap.PartyUserName>
      <#if userLogins?has_content>
        <@table type="data-list">
          <@tbody>
          <#list userLogins as userUserLogin>
            <@tr>
              <@td>${uiLabelMap.PartyUserLogin}</@td>
              <@td>
                  <#if security.hasEntityPermission("PARTYMGR", "_CREATE", request)>
                    <a href="<@pageUrl>ProfileEditUserLogin?partyId=${party.partyId}&amp;userLoginId=${userUserLogin.userLoginId}</@pageUrl>" class="${styles.action_update!}">${userUserLogin.userLoginId}</a>
                <#else>
                    ${userUserLogin.userLoginId}
                </#if>
              </@td>
              <@td>
                <#assign enabled = uiLabelMap.PartyEnabled>
                <#if (userUserLogin.enabled)?default("Y") == "N">
                  <#if userUserLogin.disabledDateTime??>
                    <#assign disabledTime = userUserLogin.disabledDateTime.toString()>
                  <#else>
                    <#assign disabledTime = "??">
                  </#if>
                  <#assign enabled = uiLabelMap.PartyDisabled + " - " + disabledTime>
                </#if>
                ${enabled}
              </@td>
              <@td class="button-col">
                <#if security.hasEntityPermission("SECURITY", "_VIEW", request)>
                  <a href="<@pageUrl>ProfileEditUserLoginSecurityGroups?partyId=${party.partyId}&amp;userLoginId=${userUserLogin.userLoginId}</@pageUrl>" class="${styles.action_view!}">${uiLabelMap.SecurityGroups}</a>
                </#if>
              </@td>
            </@tr>
          </#list>
          </@tbody>
        </@table>
      <#else>
        <@commonMsg type="result-norecord">${uiLabelMap.PartyNoUserLogin}</@commonMsg>
      </#if>
  </@section>
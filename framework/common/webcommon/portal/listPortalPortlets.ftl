<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->

<div class="screenlet">
  <div class="screenlet-title-bar">
    <ul>
      <li class="h3">${uiLabelMap.CommonAvailablePortlets}</li>
      <li><a href="<@pageUrl>ManagePortalPages?portalPageId=${parameters.portalPageId}&amp;parentPortalPageId=${parameters.parentPortalPageId}</@pageUrl>">${uiLabelMap.CommonCancel}</a></li>
    </ul>
    <br class="clear"/>
  </div>
  <div class="screenlet-body">
  <#if portalPortlets?has_content>
    <#assign orderByList = UtilMisc.toList("portalPortletId")/>

    <table cellspacing="20" class="basic-table">
      <#assign leftColumn = true/>
      <#list portalPortlets as portalPortlet>
        <#if leftColumn==true>
        <tr>
        </#if>
          <td>
            <div class="h3">
              <form method="post" action="<@pageUrl>createPortalPagePortlet</@pageUrl>" onsubmit="javascript:submitFormDisableSubmits(this)" name="createPortalPortlet_${portalPortlet.portalPortletId}"><input name="portalPortletId" value="${portalPortlet.portalPortletId}" type="hidden"/><input name="portalPageId" value="${parameters.portalPageId}" type="hidden"/><input name="columnSeqId" value="${parameters.columnSeqId}" type="hidden"/></form><a class="${styles.link_run_sys!} ${styles.action_add!}" href="javascript:document.createPortalPortlet_${portalPortlet.portalPortletId}.submit()">${uiLabelMap.CommonAdd}: ${portalPortlet.portletName}</a>
            </div>
            <div>
              ${portalPortlet.description!}
            </div>
            <div>
              ${parameters.portletCategoryId}
            </div>
          </td>
          <td>
            <#if portalPortlet.screenshot?has_content>
              <div class="screenshot">
                <a href="<@contentUrl>${portalPortlet.screenshot}</@contentUrl>"><img src="<@contentUrl>${portalPortlet.screenshot}</@contentUrl>" width="250" alt=""/></a>
              </div>
            </#if>
          </td>
        <#if leftColumn==true>
          <td width="10%">
          </td>
        </#if>
        <#if leftColumn==false>
        </tr>
        </#if>
        <#assign leftColumn = !leftColumn/>
      </#list>
    </table>
    <#else>
    <h2>${uiLabelMap.CommonNoPortletsInCategory}</h2>
  </#if>
  </div>
</div>

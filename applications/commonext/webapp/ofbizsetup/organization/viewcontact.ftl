<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->

  <#macro menuContent menuArgs={}>
    <@menu args=menuArgs>
        <#--if security.hasEntityPermission("PARTYMGR", "_CREATE", request) || userLogin.partyId == partyId>
          <@menuitem type="link" href=makePageUrl("editcontactmech?partyId=${partyId}") text=uiLabelMap.CommonCreateNew class="+${styles.action_nav!} ${styles.action_add!}" />
        </#if-->
    </@menu>
  </#macro>
  <@section id="partyContactInfo" title=uiLabelMap.PartyContactInformation menuContent=menuContent>
      <#if contactMeches?has_content>
        <@table type="data-list">
          <@tr>
            <@th>${uiLabelMap.PartyContactType}</@th>
            <@th>${uiLabelMap.PartyContactInformation}</@th>
            <@th>${uiLabelMap.PartyContactSolicitingOk}</@th>
            <@th>&nbsp;</@th>
          </@tr>
          <#list contactMeches as contactMechMap>
            <#assign contactMech = contactMechMap.contactMech>
            <#assign partyContactMech = contactMechMap.partyContactMech>
            <@tr type="util"><@td colspan="4"><hr /></@td></@tr>
            <@tr>
              <@td class="label align-top">${contactMechMap.contactMechType.get("description",locale)}</@td>
              <@td>
                <#list contactMechMap.partyContactMechPurposes as partyContactMechPurpose>
                  <#assign contactMechPurposeType = partyContactMechPurpose.getRelatedOne("ContactMechPurposeType", true)>
                  <div>
                    <#if contactMechPurposeType?has_content>
                      <strong>${contactMechPurposeType.get("description",locale)}</strong>
                    <#else>
                      <strong>${uiLabelMap.PartyMechPurposeTypeNotFound}: "${partyContactMechPurpose.contactMechPurposeTypeId}"</strong>
                    </#if>
                    <#if partyContactMechPurpose.thruDate?has_content>
                      (${uiLabelMap.CommonExpire}: ${partyContactMechPurpose.thruDate})
                    </#if>
                  </div>
                </#list>
                <#if "POSTAL_ADDRESS" == contactMech.contactMechTypeId>
                  <#assign postalAddress = contactMechMap.postalAddress>
                  <#if postalAddress?has_content>
                  <div>
                    <#if postalAddress.toName?has_content><b>${uiLabelMap.PartyAddrToName}:</b> ${postalAddress.toName}<br /></#if>
                    <#if postalAddress.attnName?has_content><b>${uiLabelMap.PartyAddrAttnName}:</b> ${postalAddress.attnName}<br /></#if>
                    ${postalAddress.address1!}<br />
                    <#if postalAddress.address2?has_content>${postalAddress.address2}<br /></#if>
                    ${postalAddress.city!},
                    <#if postalAddress.stateProvinceGeoId?has_content>
                      <#assign stateProvince = postalAddress.getRelatedOne("StateProvinceGeo", true)>
                      ${stateProvince.abbreviation!stateProvince.geoId}
                    </#if>
                    ${postalAddress.postalCode!}
                    <#if postalAddress.countryGeoId?has_content><br />
                      <#assign country = postalAddress.getRelatedOne("CountryGeo", true)>
                      ${country.geoName!country.geoId}
                    </#if>
                  </div>
                  </#if>
                  <#-- 
                  <#if (postalAddress?has_content && !postalAddress.countryGeoId?has_content) || postalAddress.countryGeoId == "USA">
                    <#assign addr1 = postalAddress.address1!>
                    <#if addr1?has_content && (addr1.indexOf(" ") > 0)>
                      <#assign addressNum = addr1.substring(0, addr1.indexOf(" "))>
                      <#assign addressOther = addr1.substring(addr1.indexOf(" ")+1)>
                      <a target="_blank" href="${uiLabelMap.CommonLookupWhitepagesAddressLink}" class="${styles.link_nav!} ${styles.action_find!}">${uiLabelMap.CommonLookupWhitepages}</a>
                    </#if>
                  </#if> -->
                  <#if postalAddress.geoPointId?has_content>
                    <#if contactMechPurposeType?has_content>
                      <#assign popUptitle = contactMechPurposeType.get("description",locale) + uiLabelMap.CommonGeoLocation>
                    </#if>
                    <a href="javascript:popUp('<@pageUrl>geoLocation?geoPointId=${postalAddress.geoPointId}</@pageUrl>', '${popUptitle!}', '450', '550')" class="${styles.link_nav!} ${styles.action_find!}">${uiLabelMap.CommonGeoLocation}</a>
                  </#if>
                <#elseif "TELECOM_NUMBER" == contactMech.contactMechTypeId>
                  <#assign telecomNumber = contactMechMap.telecomNumber>
                  <div>
                    ${telecomNumber.countryCode!}
                    <#if telecomNumber.areaCode?has_content>${telecomNumber.areaCode!"000"}-</#if>${telecomNumber.contactNumber?default("000-0000")}
                    <#if partyContactMech.extension?has_content>${uiLabelMap.PartyContactExt}&nbsp;${partyContactMech.extension}</#if>
                    <#--
                    <#if (telecomNumber?has_content && !telecomNumber.countryCode?has_content) || telecomNumber.countryCode == "011">
                      <a target="_blank" href="${uiLabelMap.CommonLookupAnywhoLink}" class="${styles.link_nav!} ${styles.action_find!} ${styles.action_external!}">${uiLabelMap.CommonLookupAnywho}</a>
                      <a target="_blank" href="${uiLabelMap.CommonLookupWhitepagesTelNumberLink}" class="${styles.link_nav!} ${styles.action_find!} ${styles.action_external!}">${uiLabelMap.CommonLookupWhitepages}</a>
                    </#if>-->
                  </div>
                <#elseif "EMAIL_ADDRESS" == contactMech.contactMechTypeId>
                  <div>
                    ${contactMech.infoString!}
                    <#--a href="<@pageUrl>EditCommunicationEvent?partyIdFrom=${userLogin.partyId}&amp;partyIdTo=${partyId}&amp;communicationEventTypeId=EMAIL_COMMUNICATION&amp;contactMechIdTo=${contactMech.contactMechId}&amp;contactMechTypeId=EMAIL_ADDRESS<#if thisUserPrimaryEmail?has_content>&amp;contactMechIdFrom=${thisUserPrimaryEmail.contactMechId}</#if></@pageUrl>" class="${styles.link_nav!} ${styles.action_send!}">${uiLabelMap.CommonSendEmail}</a-->
                  </div>
                <#elseif "WEB_ADDRESS" == contactMech.contactMechTypeId>
                  <div>
                    ${contactMech.infoString!}
                    <#assign openAddress = contactMech.infoString?default("")>
                    <#if !openAddress?starts_with("http") && !openAddress?starts_with("HTTP")><#assign openAddress = "http://" + openAddress></#if>
                    <a target="_blank" href="${openAddress}" class="${styles.link_nav!} ${styles.action_find!} ${styles.action_external!}">${uiLabelMap.CommonOpenPageNewWindow}</a>
                  </div>
                <#else>
                  <div>${contactMech.infoString!}</div>
                </#if>
                <div>(${uiLabelMap.CommonUpdated}:&nbsp;${partyContactMech.fromDate})</div>
                <#if partyContactMech.thruDate?has_content><div><b>${uiLabelMap.PartyContactEffectiveThru}:&nbsp;${partyContactMech.thruDate}</b></div></#if>
                <#-- create cust request -->
                <#if custRequestTypes??>
                  <form name="createCustRequestForm" action="<@pageUrl>createCustRequest</@pageUrl>" method="post" onsubmit="javascript:submitFormDisableSubmits(this)">
                    <input type="hidden" name="partyId" value="${partyId}"/>
                    <input type="hidden" name="fromPartyId" value="${partyId}"/>
                    <input type="hidden" name="fulfillContactMechId" value="${contactMech.contactMechId}"/>
                    <select name="custRequestTypeId">
                      <#list custRequestTypes as type>
                        <option value="${type.custRequestTypeId}">${type.get("description", locale)}</option>
                      </#list>
                    </select>
                    <input type="submit" class="${styles.link_run_sys!} ${styles.action_add!}" value="${uiLabelMap.PartyCreateNewCustRequest}"/>
                  </form>
                </#if>
              </@td>
              <@td valign="top"><b>(${partyContactMech.allowSolicitation!})</b></@td>
              <@td class="button-col">
                <#--if security.hasEntityPermission("PARTYMGR", "_UPDATE", request) || userLogin.partyId == partyId>
                  <a href="<@pageUrl>editcontactmech?partyId=${partyId}&amp;contactMechId=${contactMech.contactMechId}</@pageUrl>" class="${styles.link_nav!} ${styles.action_update!}">${uiLabelMap.CommonUpdate}</a>
                </#if>
                <#if security.hasEntityPermission("PARTYMGR", "_DELETE", request) || userLogin.partyId == partyId>
                  <form name="partyDeleteContact" method="post" action="<@pageUrl>deleteContactMech</@pageUrl>" onsubmit="javascript:submitFormDisableSubmits(this)">
                    <input name="partyId" value="${partyId}" type="hidden"/>
                    <input name="contactMechId" value="${contactMech.contactMechId}" type="hidden"/>
                    <input type="submit" class="${styles.link_run_sys!} ${styles.action_terminate!}" value="${uiLabelMap.CommonExpire}"/>
                  </form>
                </#if-->
              </@td>
            </@tr>
          </#list>
        </@table>
      <#else>
        <@commonMsg type="result-norecord">${uiLabelMap.PartyNoContactInformation}</@commonMsg>
      </#if>
  </@section>
  
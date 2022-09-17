<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->
<#include "component://shop/webapp/shop/customer/customercommon.ftl">

<#-- A simple macro that builds the contact list -->
<#macro contactList publicEmailContactLists>
  <select name="contactListId" style="width:134px">
    <#list publicEmailContactLists as publicEmailContactList>
      <#assign publicContactMechType = publicEmailContactList.contactList.getRelatedOne("ContactMechType", true)!>
        <option value="${publicEmailContactList.contactList.contactListId}">${publicEmailContactList.contactListType.description!} - ${publicEmailContactList.contactList.contactListName!}</option>
    </#list>
  </select>
</#macro>

<@script>
    function unsubscribe() {
        var form = document.getElementById("signUpForContactListForm");
        form.action = "<@pageUrl>unsubscribeContactListParty</@pageUrl>"
        document.getElementById("statusId").value = "CLPT_UNSUBS_PENDING";
        form.submit();
    }
    function unsubscribeByContactMech() {
        var form = document.getElementById("signUpForContactListForm");
        form.action = "<@pageUrl>unsubscribeContactListPartyContachMech</@pageUrl>"
        document.getElementById("statusId").value = "CLPT_UNSUBS_PENDING";
        form.submit();
    }
</@script>

<@section title=uiLabelMap.EcommerceSignUpForContactList id="miniSignUpForContactList">
  <#assign autoName = sessionAttributes.autoName!><#-- SCIPIO: Access session only once -->
  <#if autoName?has_content>
  <#-- The visitor potentially has an account and party id -->
    <#if userHasAccount>
    <#-- They are logged in so lets present the form to sign up with their email address -->
      <form method="post" action="<@pageUrl>createContactListParty</@pageUrl>" name="signUpForContactListForm" id="signUpForContactListForm">
        <fieldset>
          <#assign contextPath = request.getContextPath()>
          <input type="hidden" name="baseLocation" value="${contextPath}"/>
          <input type="hidden" name="partyId" value="${partyId}"/>
          <input type="hidden" id="statusId" name="statusId" value="CLPT_PENDING"/>
          <p>${uiLabelMap.EcommerceSignUpForContactListComments}</p>
          <div>
            <@contactList publicEmailContactLists=publicEmailContactLists/>
          </div>
          <div>
            <label for="preferredContactMechId">${uiLabelMap.CommonEmail} *</label>
            <select id="preferredContactMechId" name="preferredContactMechId">
              <#list partyAndContactMechList as partyAndContactMech>
                <option value="${partyAndContactMech.contactMechId}"><#if partyAndContactMech.infoString?has_content>${partyAndContactMech.infoString}<#elseif partyAndContactMech.tnContactNumber?has_content>${partyAndContactMech.tnCountryCode!}-${partyAndContactMech.tnAreaCode!}-${partyAndContactMech.tnContactNumber}<#elseif partyAndContactMech.paAddress1?has_content>${partyAndContactMech.paAddress1}, ${partyAndContactMech.paAddress2!}, ${partyAndContactMech.paCity!}, ${partyAndContactMech.paStateProvinceGeoId!}, ${partyAndContactMech.paPostalCode!}, ${partyAndContactMech.paPostalCodeExt!} ${partyAndContactMech.paCountryGeoId!}</#if></option>
              </#list>
            </select>
          </div>
          <div>
            <input type="submit" value="${uiLabelMap.EcommerceSubscribe}" class="${styles.link_run_sys!} ${styles.action_add!}"/>
            <input type="button" value="${uiLabelMap.EcommerceUnsubscribe}" class="${styles.link_run_sys!} ${styles.action_remove!}" onclick="javascript:unsubscribeByContactMech();"/>
          </div>
        </fieldset>
      </form>
    <#else>
    <#-- Not logged in so ask them to log in and then sign up or clear the user association -->
      <p>${uiLabelMap.EcommerceSignUpForContactListLogIn}</p>
      <p><a href="<@pageUrl>${checkLoginUrl}</@pageUrl>">${uiLabelMap.CommonLogin}</a> ${autoName}</p>
      <p>(${uiLabelMap.CommonNotYou}? <a href="<@pageUrl>autoLogout</@pageUrl>">${uiLabelMap.CommonClickHere}</a>)</p>
    </#if>
  <#else>
  <#-- There is no party info so just offer an anonymous (non-partyId) related newsletter sign up -->
    <form method="post" action="<@pageUrl>signUpForContactList</@pageUrl>" name="signUpForContactListForm" id="signUpForContactListForm">
      <fieldset>
        <#assign contextPath = request.getContextPath()>
        <input type="hidden" name="baseLocation" value="${contextPath}"/>
        <input type="hidden" id="statusId" name="statusId"/>
        <div>
          <label>${uiLabelMap.EcommerceSignUpForContactListComments}</label>
          <@contactList publicEmailContactLists=publicEmailContactLists/>
        </div>
        <div>
          <label for="email">${uiLabelMap.CommonEmail} *</label>
          <input id="email" name="email" class="required" type="text"/>
        </div>
        <div>
          <input type="submit" value="${uiLabelMap.EcommerceSubscribe}" class="${styles.link_run_sys!} ${styles.action_add!}"/>
          <input type="button" value="${uiLabelMap.EcommerceUnsubscribe}" class="${styles.link_run_sys!} ${styles.action_remove!}" onclick="javascript:unsubscribe();"/>
        </div>
      </fieldset>
    </form>
  </#if>
</@section>

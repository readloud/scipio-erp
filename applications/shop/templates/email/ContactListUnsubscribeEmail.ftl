<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->
<p>Hello ${partyName.firstName!} ${partyName.lastName!} ${partyName.groupName!}!</p>
<p>Successfully unsubscribed from ${contactList.contactListName} contact list.</p>

<#--assign verifyUrl = baseEcommerceSecureUrl+'/'+'updateContactListPartyNoUserLogin?contactListId='+contactListParty.contactListId+'&amp;partyId='+contactListParty.partyId+'&amp;fromDate='+contactListParty.fromDate+'&amp;statusId=CLPT_SUBS_PENDING&amp;optInVerifyCode='+contactListPartyStatus.optInVerifyCode+'&amp;baseLocation='+baseLocation!>
<#if (contactListParty.preferredContactMechId)??>
    <#assign verifyUrl= verifyUrl+"&amp;preferredContactMechId="+contactListParty.preferredContactMechId>
</#if>
<a href="${verifyUrl}">If this was by mistake, click here subscribe again.</a-->

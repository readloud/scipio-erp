import org.apache.commons.lang3.StringUtils;
import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.UtilProperties;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.condition.EntityCondition;
import org.ofbiz.entity.condition.EntityOperator;
import org.ofbiz.entity.util.EntityQuery;
import org.ofbiz.entity.util.EntityUtil;
import org.ofbiz.product.store.ProductStoreWorker;

import com.ilscipio.scipio.setup.*;

final module = "SetupUser.groovy";

SetupWorker setupWorker = context.setupWorker;
setupStep = context.setupStep;

userData = context.userData ?: [:];
storeData = context.storeData ?: [:];

userInfo = null;

productStoreId = context.productStoreId;

if (productStoreId) {
    productStore = ProductStoreWorker.getProductStore(productStoreId, delegator);

    context.createAllowPassword = "Y".equals(productStore.allowPassword);
    context.getUsername = !"Y".equals(productStore.usePrimaryEmailUsername);
}

userParty = userData.userParty;
context.userParty = userParty;
userPartyId = userData.userPartyId;
context.userPartyId = userPartyId;
orgPartyId = context.orgPartyId;
userPartyRelationship = null;

relRoleTypeId = null;

if (userParty) {
    userInfo = [:];
    userInfo.putAll(context.userParty);    
    if (userData.userUserLogin) {
        userInfo.putAll(userData.userUserLogin);
        context.userUserLogin = userData.userUserLogin;
    }
    if (userData.userPerson) {
        userInfo.putAll(userData.userPerson);
    }

    // This can't possibly work properly
    //partyRole = EntityUtil.getFirst(delegator.findByAnd("PartyRole", ["partyId" : userParty.partyId], null, false));
    //context.userPartyRole = partyRole;
    //if (partyRole) {
    //    context.userPartyRelationship = EntityUtil.getFirst(EntityUtil.filterByDate(delegator.findByAnd("PartyRelationship", ["partyIdTo" : userParty.partyId, "roleTypeIdTo" : partyRole.roleTypeId], null, false)));
    //}
    if (orgPartyId) {
        userPartyRelationship = EntityUtil.getFirst(EntityUtil.filterByDate(delegator.findByAnd("PartyRelationship", 
            ["partyIdFrom" : orgPartyId, "partyIdTo" : userParty.partyId], null, false)));
        if (userPartyRelationship) {
            context.userPartyRelationship = userPartyRelationship;
            context.userPartyRole = userPartyRelationship.getRelatedOne("ToPartyRole", false);
            relRoleTypeId = userPartyRelationship.roleTypeIdTo;
            userInfo.roleTypeId = userPartyRelationship.roleTypeIdTo;
            userInfo.partyRelationshipTypeId = userPartyRelationship.partyRelationshipTypeId;
        } 
    }
}

generalAddressContactMech = userData.generalAddressContactMech;
context.generalAddressContactMech = generalAddressContactMech;
generalAddressContactMechPurposes = userData.generalAddressContactMechPurposes;
context.generalAddressContactMechPurposes = generalAddressContactMechPurposes;
context.generalAddressStandaloneCompleted = userData.generalAddressStandaloneCompleted;
context.locationAddressesCompleted = userData.locationAddressesCompleted;
context.locationPurposes = userData.locationPurposes;
generalPostalAddress = null;
if (generalAddressContactMech) {
    postalAddress = delegator.findOne("PostalAddress", [contactMechId:generalAddressContactMech.contactMechId], false);
    if (postalAddress) {
        generalPostalAddress = [
            "USER_ADDRESS_CONTACTMECHID": generalAddressContactMech.contactMechId,
            "USER_STATE": postalAddress.stateProvinceGeoId,
            "USER_COUNTRY": postalAddress.countryGeoId,
            "USER_ADDRESS1": postalAddress.address1,
            "USER_ADDRESS2": postalAddress.address2,
            "USER_CITY": postalAddress.city,
            "USER_POSTAL_CODE": postalAddress.postalCode,
            "USER_ADDRESS_ALLOW_SOL": generalAddressContactMech.allowSolicitation
        ];
        if (userInfo != null) {
            userInfo.putAll(generalPostalAddress);
            userInfo.USER_ADDR_PURPOSE = generalAddressContactMechPurposes;
        }
    } else {
        Debug.logError("Setup: Configuration error: Mail/ship address contact mech '"
            + generalAddressContactMech.contactMechId + " has no PostalAddress record! Invalid data configuration!", module)
    }
    
}

workPhoneContactMech = userData.workPhoneContactMech;
context.workPhoneContactMech = workPhoneContactMech;
workPhoneNumber = null;
if (workPhoneContactMech) {
    telecomNumber = delegator.findOne("TelecomNumber", [contactMechId:workPhoneContactMech.contactMechId], false);
    if (telecomNumber) {
        workPhoneNumber = [
            "USER_WORK_CONTACTMECHID": workPhoneContactMech.contactMechId,
            "USER_WORK_COUNTRY": telecomNumber.countryCode,
            "USER_WORK_AREA": telecomNumber.areaCode,
            "USER_WORK_CONTACT": telecomNumber.contactNumber,
            "USER_WORK_EXT": workPhoneContactMech.extension,
            "USER_WORK_ALLOW_SOL": workPhoneContactMech.allowSolicitation
        ];
        if (userInfo != null) {
            userInfo.putAll(workPhoneNumber);
        }
    } else {
        Debug.logError("Setup: Configuration error: Work phone contact mech '"
            + workPhoneContactMech.contactMechId + " has no TelecomNumber record! Invalid data configuration!", module)
    }
}

mobilePhoneContactMech = userData.mobilePhoneContactMech;
context.mobilePhoneContactMech = mobilePhoneContactMech;
mobilePhoneNumber = null;
if (mobilePhoneContactMech) {
    telecomNumber = delegator.findOne("TelecomNumber", [contactMechId:mobilePhoneContactMech.contactMechId], false);
    if (telecomNumber) {
        mobilePhoneNumber = [
            "USER_MOBILE_CONTACTMECHID": mobilePhoneContactMech.contactMechId,
            "USER_MOBILE_COUNTRY": telecomNumber.countryCode,
            "USER_MOBILE_AREA": telecomNumber.areaCode,
            "USER_MOBILE_CONTACT": telecomNumber.contactNumber,
            "USER_MOBILE_EXT": mobilePhoneContactMech.extension,
            "USER_MOBILE_ALLOW_SOL": mobilePhoneContactMech.allowSolicitation
        ];
        if (userInfo != null) {
            userInfo.putAll(mobilePhoneNumber);
        }
    } else {
        Debug.logError("Setup: Configuration error: Mobile phone contact mech '"
            + mobilePhoneContactMech.contactMechId + " has no TelecomNumber record! Invalid data configuration!", module)
    }
}

faxPhoneContactMech = userData.faxPhoneContactMech;
context.faxPhoneContactMech = faxPhoneContactMech;
faxPhoneNumber = null;
if (faxPhoneContactMech) {
    telecomNumber = delegator.findOne("TelecomNumber", [contactMechId:faxPhoneContactMech.contactMechId], false);
    if (telecomNumber) {
        faxPhoneNumber = [
            "USER_FAX_CONTACTMECHID": faxPhoneContactMech.contactMechId,
            "USER_FAX_COUNTRY": telecomNumber.countryCode,
            "USER_FAX_AREA": telecomNumber.areaCode,
            "USER_FAX_CONTACT": telecomNumber.contactNumber,
            "USER_FAX_EXT": faxPhoneContactMech.extension,
            "USER_FAX_ALLOW_SOL": faxPhoneContactMech.allowSolicitation
        ];
        if (userInfo != null) {
            userInfo.putAll(faxPhoneNumber);
        }
    } else {
        Debug.logError("Setup: Configuration error: Fax phone contact mech '"
            + faxPhoneContactMech.contactMechId + " has no TelecomNumber record! Invalid data configuration!", module)
    }
}


primaryEmailContactMech = userData.primaryEmailContactMech;
context.primaryEmailContactMech = primaryEmailContactMech;
primaryEmailAddress = null;
if (primaryEmailContactMech) {
    primaryEmailAddress = [
        "USER_EMAIL_CONTACTMECHID": primaryEmailContactMech.contactMechId,
        "USER_EMAIL": primaryEmailContactMech.getRelatedOne("ContactMech", false)?.infoString,
        "USER_EMAIL_ALLOW_SOL": primaryEmailContactMech.allowSolicitation
    ];
    if (userInfo != null) {
        userInfo.putAll(primaryEmailAddress);
    }
}


// true if explicit userPartyId OR explicit newUser=Y flag OR failed create
userSelected = userPartyId || setupWorker?.isEffectiveNewRecordRequest(StringUtils.capitalize(setupStep));
context.userSelected = userSelected;

context.contactMechsCompleted = userData.contactMechsCompleted;

if (UtilProperties.getPropertyAsBoolean("scipiosetup", "user.roles.show.all", false)) {
    userPartyRoles = from("RoleType").orderBy(["description"]).cache().query();
} else {
    // NOTE: The roles to include are defined in party.properties
    userPartyRoles = org.ofbiz.party.party.PartyWorker.getRoleTypesForGroup(delegator, "ORGANIZATION_MEMBER", ["description"]);
}

if (userPartyRelationship) {
    // Ensure our current roleTypeId is in the list
    found = false;
    for(roleType in userPartyRoles) {
        if (roleType.roleTypeId == relRoleTypeId) {
            found = true;
            break;
        }
    }
    if (!found) {
        userPartyRoles = new ArrayList(userPartyRoles);
        userPartyRoles.add(from("RoleType").where("roleTypeId", relRoleTypeId).cache().queryOne());
    }
}
context.userPartyRoles = userPartyRoles;

List<GenericValue> userPartyRelationshipTypes = from("PartyRelationshipType").orderBy(["partyRelationshipName"]).cache().query();
if (userPartyRelationship?.partyRelationshipTypeId) {
    // Ensure our partyRelationshipTypeId is in the list
    found = false;
    for(relType in userPartyRelationshipTypes) {
        if (relType.partyRelationshipTypeId == userPartyRelationship.partyRelationshipTypeId) {
            found = true;
            break;
        }
    }
    if (!found) {
        userPartyRelationshipTypes = new ArrayList(userPartyRelationshipTypes);
        userPartyRelationshipTypes.add(from("PartyRelationshipType").where("partyRelationshipTypeId", userPartyRelationship.partyRelationshipTypeId).cache().queryOne());
    }
}
context.userPartyRelationshipTypes = userPartyRelationshipTypes;

// only allowing this if it was ALREADY empty; need to avoid creating new ones as empty
context.allowEmptyPartyRelType = (userPartyRelationship && !userPartyRelationship.partyRelationshipTypeId);

context.userInfo = userInfo;

// Get all the stores, because it's possible client created a store, then replaced it,
// and then the option will make no sense
productStoreList = storeData.productStoreList;
context.productStoreList = productStoreList;
productStoreIdSet = storeData.productStoreIdSet;
context.productStoreIdSet = productStoreIdSet;

if (userParty) {
    // Lookup ProductStoreRole(s)
    productStoreIdSet = storeData.productStoreIdSet;
    // NOTE: this list is not pre-filtered by organization stores; we must do this now
    allProductStoreRoleList = userData.allProductStoreRoleList;
    productStoreRoleList = productStoreIdSet ? allProductStoreRoleList?.findAll{ productStoreIdSet.contains(it.productStoreId); } : null;
    context.productStoreRoleList = productStoreRoleList;
    Debug.logInfo("Setup: productStoreRoleList for party '" + userPartyId + "': " + productStoreRoleList, module);

    productStoreRole = SetupDataUtil.getBestProductStoreRole(productStoreRoleList, productStoreId, productStoreIdSet, 
        relRoleTypeId, true);
    /* do a separate lookup for this
    psrDiffRole = (productStoreRole && productStoreRole.roleTypeId != relRoleTypeId);
    if (psrDiffRole) {
        Debug.logWarning("Setup: Party '" + userPartyId + "' has relation to store '" + productStoreRole.productStoreId +
            "', but is in different role (" + productStoreRole.roleTypeId + ") than its company relationship (" +
            relRoleTypeId + "); it cannot be managed by setup (use catalog application)", module);
        productStoreRole = null;
        context.psrIsOtherRole;
    }
    */
    Debug.logInfo("Setup: Primary ProductStoreRole for party '" + userPartyId + "': " + productStoreRole?.toString(), module);

    if (productStoreIdSet && relRoleTypeId) {
        // Check if the user has a complex setup, so can warn against this, otherwise is not clear what is happening
        EntityCondition cond = EntityCondition.makeCondition(
            EntityCondition.makeCondition("partyId", userPartyId), EntityOperator.AND,
            EntityCondition.makeCondition(EntityCondition.makeCondition("productStoreId", EntityOperator.IN, productStoreIdSet), EntityOperator.AND,
                EntityCondition.makeCondition("roleTypeId", EntityOperator.NOT_EQUAL, relRoleTypeId)));
        extraProductStoreRoleList = from("ProductStoreRole").where(cond).filterByDate().queryList();
        context.extraProductStoreRoleList = extraProductStoreRoleList;
        if (extraProductStoreRoleList) {
            // This is only for the info box, link the most relevant...
            // NOTE: could pick a better one, but it's really just to have a working link to catalog
            context.extraProductStoreRole = productStoreRole ?: extraProductStoreRoleList[0];
        }
    }
    
    userInfo.PRODUCT_STORE_ID = productStoreRole?.productStoreId;
    context.userProductStoreRole = productStoreRole;
}

// TODO: factor out/optimize
postalTypePurposeList = from("ContactMechTypePurpose").where("contactMechTypeId", "POSTAL_ADDRESS").cache().queryList();
postalPurposeTypeList = postalTypePurposeList.collect { it.getRelatedOne("ContactMechPurposeType", true) };
postalPurposeTypeList = EntityUtil.localizedOrderBy(postalPurposeTypeList, ["description"], context.locale)
context.postalPurposeTypeList = postalPurposeTypeList;

package com.ilscipio.scipio.setup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.GeneralException;
import org.ofbiz.base.util.UtilDateTime;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilProperties;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.entity.Delegator;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.condition.EntityCondition;
import org.ofbiz.entity.condition.EntityOperator;
import org.ofbiz.entity.model.DynamicViewEntity;
import org.ofbiz.entity.model.ModelKeyMap;
import org.ofbiz.entity.util.EntityQuery;
import org.ofbiz.entity.util.EntityUtil;
import org.ofbiz.service.LocalDispatcher;
import org.ofbiz.service.ServiceUtil;

import com.ilscipio.scipio.accounting.external.BaseOperationStats;
import com.ilscipio.scipio.accounting.external.BaseOperationStats.Stat;
import com.ilscipio.scipio.setup.ContactMechPurposeInfo.FacilityContactMechPurposeInfo;
import com.ilscipio.scipio.setup.ContactMechPurposeInfo.PartyContactMechPurposeInfo;

/**
 * Raw setup step data check logic.
 * USE {@link SetupWorker} TO INVOKE THESE DURING REAL SETUP.
 * This is for general reuse and to keep the core logic clear/separate.
 */
public final class SetupDataUtil {

    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());

    public static final Set<String> ORGANIZATION_MAINADDR_PURPOSES = UtilMisc.unmodifiableLinkedHashSet(
            "GENERAL_LOCATION", "PAYMENT_LOCATION", "BILLING_LOCATION"
    );

    public static final Set<String> FACILITY_MAINADDR_PURPOSES = UtilMisc.unmodifiableLinkedHashSet(
            "SHIP_ORIG_LOCATION", "SHIPPING_LOCATION"
    );

    public static final Set<String> USER_MAINADDR_PURPOSES = UtilMisc.unmodifiableLinkedHashSet(
            "GENERAL_LOCATION" // 2018-10-30: this doesn't make sense for all types of users: "SHIPPING_LOCATION"
    );

    private SetupDataUtil() {
    }

    /*
     * *******************************************
     * Setup step elemental data state queries
     * *******************************************
     */

    /*
     * These serve as partial substitutes for the screen groovy scripts.
     *
     * These methods return 2-3 different things at the same time (TODO: maybe separate in future; combined for performance reasons):
     * * the "completed" state boolean (always)
     * * generic queries needed for all screens
     * * the requested object (facility/store/etc) if explicit ID requested OR a default if none requested
     *   -> if the ID requested is invalid or inapplicable (new/create), the method must NOT return
     *      any main record (facility/store/etc), but it must still evaluate the "completed" boolean.
     *      it may return extra records if needed (e.g. defaultFacility)
     *
     * WARN: params map may contain unvalidated user input - others in the map may be already validated.
     * The caller (SetupWorker.CommonStepState subclasses) handles the implicit deps and decides which params must be pre-validated.
     * DO NOT call these methods from screen - all must go through SetupWorker.
     *
     * TODO: these could return error message via ServiceUtil.returnError + let caller log, but not much point yet.
     */

    public static Map<String, Object> getOrganizationStepData(Delegator delegator, LocalDispatcher dispatcher, Map<String, Object> params, boolean useCache)
            throws GeneralException {
        // NOTE: coreCompleted is required for cases where need to recognized half-completed; it means just the core entities
        Map<String, Object> result = UtilMisc.toMap("completed", false, "coreCompleted", false);

        String orgPartyId = (String) params.get("orgPartyId");

        boolean isNewOrFailedCreate = isUnspecificRecordRequest(params, "Organization");

        if (UtilValidate.isNotEmpty(orgPartyId) && !isNewOrFailedCreate) {
            GenericValue party = delegator.findOne("Party", UtilMisc.toMap("partyId", orgPartyId), useCache);
            if (party != null) {
                GenericValue partyRole = delegator.findOne("PartyRole",
                        UtilMisc.toMap("partyId", orgPartyId, "roleTypeId", "INTERNAL_ORGANIZATIO"), useCache);
                if (partyRole != null) {
                    GenericValue partyGroup = delegator.findOne("PartyGroup", UtilMisc.toMap("partyId", orgPartyId), useCache);
                    if (partyGroup != null) {
                        result.put("orgPartyId", orgPartyId);
                        result.put("party", party);
                        result.put("partyGroup", partyGroup);
                        result.put("coreCompleted", true);

                        // TODO?: check for CARRIER roleTypeId??

                        PartyContactMechPurposeInfo contactMechInfo = PartyContactMechPurposeInfo.forParty(delegator, dispatcher, orgPartyId, useCache, "Setup: Organization: ");
                        contactMechInfo.resultsToMap(result);

                        Set<String> generalAddressContactMechPurposes = null;
                        GenericValue generalAddressContactMech = contactMechInfo.getPartyContactMechForPurpose(delegator, "GENERAL_LOCATION", useCache);
                        if (generalAddressContactMech == null) {
                            if (UtilMisc.booleanValueVersatile(params.get("useClosestAddress"), true)) {
                                Debug.logInfo("Setup: Organization: party '" + orgPartyId + "' has no GENERAL_LOCATION address; trying closest-matching for address purposes " + ORGANIZATION_MAINADDR_PURPOSES, module);
                                generalAddressContactMech = contactMechInfo.getClosestPartyContactMechForPurposes(delegator, ORGANIZATION_MAINADDR_PURPOSES, useCache);
                            }
                        }
                        if (generalAddressContactMech != null) {
                            generalAddressContactMechPurposes = contactMechInfo.getContactMechPurposes(generalAddressContactMech.getString("contactMechId"));
                        }
                        result.put("generalAddressContactMech", generalAddressContactMech);
                        result.put("generalAddressContactMechPurposes", generalAddressContactMechPurposes);
                        boolean generalAddressStandaloneCompleted = (generalAddressContactMech != null) && UtilMisc.containsAll(generalAddressContactMechPurposes, ORGANIZATION_MAINADDR_PURPOSES);
                        result.put("generalAddressStandaloneCompleted", generalAddressStandaloneCompleted);

                        result.put("locationPurposes", ORGANIZATION_MAINADDR_PURPOSES);
                        Map<String, GenericValue> locationContactMechs = contactMechInfo.getPartyContactMechForPurposeMap(delegator, ORGANIZATION_MAINADDR_PURPOSES, useCache);
                        boolean locationAddressesCompleted = (locationContactMechs.size() >= ORGANIZATION_MAINADDR_PURPOSES.size());
                        result.put("locationAddressesCompleted", locationAddressesCompleted);

                        GenericValue workPhoneContactMech = contactMechInfo.getPartyContactMechForPurpose(delegator, "PHONE_WORK", useCache);
                        GenericValue faxPhoneContactMech = contactMechInfo.getPartyContactMechForPurpose(delegator, "FAX_NUMBER", useCache);
                        GenericValue primaryEmailContactMech = contactMechInfo.getPartyContactMechForPurpose(delegator, "PRIMARY_EMAIL", useCache);

                        result.put("workPhoneContactMech", workPhoneContactMech);
                        result.put("faxPhoneContactMech", faxPhoneContactMech);
                        result.put("primaryEmailContactMech", primaryEmailContactMech);

                        boolean contactMechsCompleted = locationAddressesCompleted; // && simpleContactMechsCompleted
                        result.put("contactMechsCompleted", contactMechsCompleted);

                        if (contactMechsCompleted) {
                            result.put("completed", true);
                        }
                    } else {
                        Debug.logError("Setup: Organization: Party '" + orgPartyId + "' does not have a PartyGroup record (invalid organization)", module);
                    }
                } else {
                    Debug.logError("Setup: Organization: Party '" + orgPartyId + "' does not have INTERNAL_ORGANIZATIO role (invalid organization)", module);
                }
            } else {
                Debug.logError("Setup: Organization: Party '" + orgPartyId + "' does not exist (invalid organization)", module);
            }
        }
        return result;
    }

    public static Map<String, Object> getUserStepData(Delegator delegator, LocalDispatcher dispatcher, Map<String, Object> params, boolean useCache)
            throws GeneralException {
        Map<String, Object> result = UtilMisc.toMap("completed", false, "coreCompleted", false);

        String orgPartyId = (String) params.get("orgPartyId");
        String userPartyId = (String) params.get("userPartyId");

        boolean isNewOrFailedCreate = isUnspecificRecordRequest(params, "User");

        GenericValue party = null;
        if (UtilValidate.isNotEmpty(orgPartyId) && !isNewOrFailedCreate) {
            if (UtilValidate.isNotEmpty(userPartyId)) {
                party = delegator.findOne("Party", UtilMisc.toMap("partyId", userPartyId), useCache);
                if (party != null) {
                    List<GenericValue> partyRelationshipList = party.getRelated("ToPartyRelationship",
                            UtilMisc.toMap("partyIdFrom", orgPartyId, "roleTypeIdFrom", "INTERNAL_ORGANIZATIO"), UtilMisc.toList("fromDate DESC"), false);
                    if (UtilValidate.isNotEmpty(partyRelationshipList)) {
                        if (partyRelationshipList.size() > 1) {
                            Debug.logWarning("Setup: User: party " + userPartyId + "' got multiple owner relationships for organization '" + orgPartyId + "'",
                                    module);
                        }
                    } else {
                        Debug.logError("Setup: User: party '" + userPartyId + "'" + " is not an owner of organization '" + orgPartyId + "'; ignoring", module);
                        party = null;
                    }
                } else {
                    Debug.logError("Setup: User: party '" + userPartyId + "' not found; ignoring", module);
                }
            } else {
                GenericValue orgParty = delegator.findOne("Party", UtilMisc.toMap("partyId", orgPartyId), useCache);
                if (orgParty != null) {
                    List<GenericValue> partyRelationshipList = orgParty.getRelated("FromPartyRelationship",
                            UtilMisc.toMap("partyIdFrom", orgPartyId, "roleTypeIdFrom", "INTERNAL_ORGANIZATIO"), UtilMisc.toList("fromDate DESC"), false);
                    if (UtilValidate.isNotEmpty(partyRelationshipList)) {
                        // makes no sense here
                        //if (partyRelationshipList.size() > 1) {
                        //    Debug.logWarning("Setup: User '" + userPartyId + "' has multiple relationships with organization '" + orgPartyId + "'", module);
                        //}
                        GenericValue partyRelationshipOwner = EntityUtil.getFirst(partyRelationshipList);
                        party = partyRelationshipOwner.getRelatedOne("ToParty", false);
                    }
                }
            }
        }
        if (party != null) {
            userPartyId = party.getString("partyId");
            GenericValue userUserLogin = EntityUtil.getFirst(party.getRelated("UserLogin", UtilMisc.toMap("partyId", userPartyId), null, false));
            GenericValue userPerson = party.getRelatedOne("Person", false);

            result.put("userUserLogin", userUserLogin);
            result.put("userPerson", userPerson);
            result.put("userPartyId", userPartyId);
            result.put("userParty", party);
            result.put("coreCompleted", true);

            PartyContactMechPurposeInfo contactMechInfo = PartyContactMechPurposeInfo.forParty(delegator, dispatcher, userPartyId, useCache, "Setup: User: ");
            contactMechInfo.resultsToMap(result);
            Set<String> generalAddressContactMechPurposes = null;
            GenericValue generalAddressContactMech = contactMechInfo.getPartyContactMechForPurpose(delegator, "GENERAL_LOCATION", useCache);
            if (generalAddressContactMech == null) {
                if (UtilMisc.booleanValueVersatile(params.get("useClosestAddress"), true)) {
                    Debug.logInfo("Setup: User: party '" + userPartyId + "' has no GENERAL_LOCATION address; trying closest-matching for address purposes "
                            + USER_MAINADDR_PURPOSES, module);
                    generalAddressContactMech = contactMechInfo.getClosestPartyContactMechForPurposes(delegator, USER_MAINADDR_PURPOSES, useCache);
                }
            }
            // 2018-10-31: special case: for user, if there is an address with no purpose, grab it
            if (generalAddressContactMech == null) {
                List<GenericValue> extraAddressList = EntityQuery.use(delegator).from("PartyContactMechAndContactMech")
                        .where("partyId", userPartyId, "contactMechTypeId", "POSTAL_ADDRESS").filterByDate().orderBy("-fromDate").cache(useCache).queryList();
                if (extraAddressList.size() > 0) {
                    generalAddressContactMech = extraAddressList.get(0).extractViewMember("PartyContactMech");
                    Debug.logInfo("Setup: User: party '" + userPartyId + "' has no address having purposes " + USER_MAINADDR_PURPOSES 
                            + "; using first found address (contactMechId: " + generalAddressContactMech.getString("contactMechId") + ")", module);
                }
            }
            if (generalAddressContactMech != null) {
                generalAddressContactMechPurposes = contactMechInfo.getContactMechPurposes(generalAddressContactMech.getString("contactMechId"));
            }
            result.put("generalAddressContactMech", generalAddressContactMech);
            result.put("generalAddressContactMechPurposes", generalAddressContactMechPurposes);
            boolean generalAddressStandaloneCompleted = (generalAddressContactMech != null)
                    && UtilMisc.containsAll(generalAddressContactMechPurposes, USER_MAINADDR_PURPOSES);
            result.put("generalAddressStandaloneCompleted", generalAddressStandaloneCompleted);

            result.put("locationPurposes", USER_MAINADDR_PURPOSES);
            Map<String, GenericValue> locationContactMechs = contactMechInfo.getPartyContactMechForPurposeMap(delegator, USER_MAINADDR_PURPOSES, useCache);
            boolean locationAddressesCompleted = (locationContactMechs.size() >= USER_MAINADDR_PURPOSES.size());
            result.put("locationAddressesCompleted", locationAddressesCompleted);

            GenericValue workPhoneContactMech = contactMechInfo.getPartyContactMechForPurpose(delegator, "PHONE_WORK", useCache);
            GenericValue faxPhoneContactMech = contactMechInfo.getPartyContactMechForPurpose(delegator, "FAX_NUMBER", useCache);
            GenericValue mobilePhoneContactMech = contactMechInfo.getPartyContactMechForPurpose(delegator, "PHONE_MOBILE", useCache);
            GenericValue primaryEmailContactMech = contactMechInfo.getPartyContactMechForPurpose(delegator, "PRIMARY_EMAIL", useCache);

            result.put("workPhoneContactMech", workPhoneContactMech);
            result.put("faxPhoneContactMech", faxPhoneContactMech);
            result.put("mobilePhoneContactMech", mobilePhoneContactMech);
            result.put("primaryEmailContactMech", primaryEmailContactMech);

            boolean contactMechsCompleted = locationAddressesCompleted;
            result.put("contactMechsCompleted", contactMechsCompleted);

            // NOTE: this list is NOT filtered by the organization product stores; screen do it for now
            List<GenericValue> allProductStoreRoleList = EntityQuery.use(delegator).from("ProductStoreRole")
                    .where("partyId", userPartyId).filterByDate().cache(useCache).queryList();
            result.put("allProductStoreRoleList", allProductStoreRoleList);

            if (contactMechsCompleted) {
                result.put("completed", true);
            }
        }
        return result;
    }

    public static Map<String, Object> getAccountingStepData(Delegator delegator, LocalDispatcher dispatcher, Map<String, Object> params, boolean useCache) throws GeneralException {
        Map<String, Object> result = UtilMisc.toMap("completed", false, "coreCompleted", false);

        boolean isNewOrFailedCreate = isUnspecificRecordRequest(params, "GlAccount");
        boolean isImportPredefinedGL = params.containsKey("importPredefinedGL");

        String orgPartyId = (String) params.get("orgPartyId");
        String topGlAccountId = (String) params.get("topGlAccountId");

        DynamicViewEntity dve = new DynamicViewEntity();
        dve.addMemberEntity("GAO", "GlAccountOrganization");
        dve.addMemberEntity("GA", "GlAccount");
        dve.addViewLink("GA", "GAO", false, ModelKeyMap.makeKeyMapList("glAccountId"));
        dve.addAlias("GA", "glAccountId");
        dve.addAlias("GA", "parentGlAccountId");
        dve.addAlias("GAO", "organizationPartyId");
        dve.addAlias("GAO", "roleTypeId");
        dve.addAlias("GAO", "fromDate");
        dve.addAlias("GAO", "thruDate");
        dve.addRelation("one", null, "GlAccount", ModelKeyMap.makeKeyMapList("glAccountId"));
        dve.addRelation("one", null, "GlAccountOrganization", ModelKeyMap.makeKeyMapList("glAccountId", "glAccountId", "organizationPartyId", "organizationPartyId"));
        List<EntityCondition> dveConditions = new ArrayList<>();
        dveConditions.add(EntityCondition.makeCondition("roleTypeId", EntityOperator.EQUALS, "INTERNAL_ORGANIZATIO"));
        dveConditions.add(EntityCondition.makeCondition("parentGlAccountId", EntityOperator.EQUALS, null));

        List<GenericValue> glAccountAndOrganizations = EntityQuery.use(delegator).from(dve).where(EntityCondition.makeCondition(dveConditions, EntityOperator.AND))
                .orderBy(UtilMisc.toList("fromDate")).queryList();
        GenericValue glAccountOrganization = null;

        if (UtilValidate.isNotEmpty(orgPartyId) && !isNewOrFailedCreate) {
            if (isImportPredefinedGL) {
                String importPredefinedGL = (String) params.get("importPredefinedGL");
                String defaultGLUrl = UtilProperties.getPropertyValue("general", "scipio.accounting.defaultGL." + importPredefinedGL);
                Map<String, Object>  entityImportCtx = UtilMisc.newMap();
                GenericValue systemUserLogin = delegator.findOne("UserLogin", true, UtilMisc.toMap("userLoginId", "system"));
                entityImportCtx.put("isUrl", "Y");
                entityImportCtx.put("filename", defaultGLUrl);
                entityImportCtx.put("userLogin", systemUserLogin);
                Map<String, Object> defaultGLImportResult = dispatcher.runSync("entityImport", entityImportCtx, 0, true);
                if (ServiceUtil.isSuccess(defaultGLImportResult)) {
                    // FIXME: Let's assume everything went OK, although
                    // it's hard to tell since entityImport doesn't return an
                    // explicit field stating so.
                    topGlAccountId = importPredefinedGL;
                } else {
                    Debug.logError("Error importing default GL [" + importPredefinedGL + "]", module);
                }
            }

            GenericValue topGlAccount = delegator.findOne("GlAccount", true, UtilMisc.toMap("glAccountId", topGlAccountId));
            if (UtilValidate.isNotEmpty(topGlAccountId)) {
                if (topGlAccount == null) {
                    Debug.logError("Setup: GL account '" + topGlAccountId + "' not found; ignoring", module);
                }
            } else {
                GenericValue glAccountAndOrganizationFiltered = EntityUtil.getFirst(EntityUtil.filterByDate(glAccountAndOrganizations));
                if (UtilValidate.isNotEmpty(glAccountAndOrganizationFiltered)) {
                    topGlAccount = glAccountAndOrganizationFiltered.getRelatedOne("GlAccount", false);
                    topGlAccountId = glAccountAndOrganizationFiltered.getString("glAccountId");
                }
            }

            if (UtilValidate.isNotEmpty(glAccountAndOrganizations)) {
                if (glAccountAndOrganizations.size() > 1) {
                    Debug.logWarning("Setup: Multiple GL for organization '" + orgPartyId + "' and role type 'INTERNAL_ORGANIZATIO'", module);
                }
                for (GenericValue glAccountAndOrganization : glAccountAndOrganizations) {
                    GenericValue gao = glAccountAndOrganization.getRelatedOne("GlAccountOrganization", false);
                    if (!glAccountAndOrganization.get("glAccountId").equals(topGlAccountId)) {
                        Debug.logWarning("Setup: GL account '" + glAccountAndOrganization.getString("glAccountId") + "' not used; expiring", module);

                        gao.put("thruDate", UtilDateTime.nowTimestamp());
                        gao.store();
                    } else {
                        glAccountOrganization = gao;
                    }
                }
            }

            
            GenericValue partyAcctgPreference = delegator.findOne("PartyAcctgPreference", false, UtilMisc.toMap("partyId", orgPartyId));
            if (UtilValidate.isNotEmpty(partyAcctgPreference)) {
                result.put("acctgPreferences", partyAcctgPreference);
            }

            if (topGlAccount != null) {
                result.put("coreCompleted", true);
                boolean isFiscalPeriodSet = false;
                if (UtilValidate.isEmpty(glAccountOrganization)) {
                    glAccountOrganization = delegator.makeValue("GlAccountOrganization", UtilMisc.toMap("glAccountId", topGlAccountId, "organizationPartyId", orgPartyId,
                            "roleTypeId", "INTERNAL_ORGANIZATIO", "fromDate", UtilDateTime.nowTimestamp()));
                    delegator.create(glAccountOrganization);
                }
                //Timestamp now = UtilDateTime.nowTimestamp();
                List<EntityCondition> openedCurrentFiscalPeriodsCond = UtilMisc.toList(EntityCondition.makeCondition("isClosed", EntityOperator.NOT_EQUAL, "Y"));

                GenericValue openedCurrentFiscalPeriods = EntityQuery.use(delegator).from("CustomTimePeriod").where(openedCurrentFiscalPeriodsCond, EntityOperator.AND).filterByDate().queryFirst();
                if (UtilValidate.isNotEmpty(openedCurrentFiscalPeriods)) {
                    isFiscalPeriodSet = true;
                }

                if (isFiscalPeriodSet) {
                    result.put("completed", true);
                }

                if (params.containsKey("datevImportDataCategory")) {
                    String datevImportDataCategory = (String) params.get("datevImportDataCategory");
                    if (UtilValidate.isNotEmpty(datevImportDataCategory)) {
                        if (params.containsKey("operationStats")) {
                            BaseOperationStats operationStats = (BaseOperationStats) params.get("operationStats");
                            for (Stat stat : operationStats.getStats()) {
                                Debug.log("[" + stat.getScope().toString() + "][" + stat.getLevel().toString() + "]: " + stat.getMessage());
                            }
                        }
                    }
                }

                result.put("topGlAccountId", topGlAccountId);
                result.put("topGlAccount", topGlAccount);
            }
        }
        return result;
    }

    public static Map<String, Object> getFacilityStepData(Delegator delegator, LocalDispatcher dispatcher, Map<String, Object> params, boolean useCache)
            throws GeneralException {
        Map<String, Object> result = UtilMisc.toMap("completed", false, "coreCompleted", false);

        String facilityId = (String) params.get("facilityId");
        String orgPartyId = (String) params.get("orgPartyId");
        String productStoreId = (String) params.get("productStoreId");

        // if new or failed create, we must still determine if overall "completed", but we cannot return
        // a specific facility ID - see the result map assignments below
        boolean isNewOrFailedCreate = isUnspecificRecordRequest(params, "Facility");

        GenericValue facility = null;
        if (UtilValidate.isNotEmpty(orgPartyId)) {
            if (UtilValidate.isNotEmpty(facilityId) && !isNewOrFailedCreate) { // ignore ID if new or failed create
                // filter by owner to prevent editing other companies's facilities
                facility = delegator.findOne("Facility", UtilMisc.toMap("facilityId", facilityId), useCache);
                if (facility != null) {
                    if (!orgPartyId.equals(facility.getString("ownerPartyId"))) {
                        Debug.logError("Setup: Facility '" + facilityId + "' does not belong to organization '" + orgPartyId + "'; ignoring", module);
                        facility = null;
                    }
                } else {
                    Debug.logError("Setup: Facility '" + facilityId + "' not found; ignoring", module);
                }
            } else {
                if (UtilValidate.isNotEmpty(productStoreId)) {
                    // this case selects the best facility for the passed store
                    // TODO: REVIEW: this is not reusing the getStoreStepStateData for now because
                    // facility step now comes first and will create endless loop
                    GenericValue productStore = delegator.findOne("ProductStore", UtilMisc.toMap("productStoreId", productStoreId), useCache);
                    if (productStore != null) {
                        if (orgPartyId.equals(productStore.getString("payToPartyId"))) {
                            facilityId = productStore.getString("inventoryFacilityId");
                            if (UtilValidate.isNotEmpty(facilityId)) {
                                facility = delegator.findOne("Facility", UtilMisc.toMap("facilityId", facilityId), useCache);
                                if (facility != null) {
                                    if (orgPartyId.equals(facility.getString("ownerPartyId"))) {
                                        ; // ok
                                    } else {
                                        Debug.logError("Setup: Warehouse '" + facilityId + "'"
                                                + " does not belong to organization '"
                                                + orgPartyId + "'; ignoring", module);
                                        facility = null;
                                    }
                                } else {
                                    Debug.logError("Setup: Warehouse '" + facilityId + "' not found; ignoring", module);
                                }
                            } else {
                                // TODO: REVIEW: there are multiple reasons for this;
                                // * does not support ProductStoreFacility-only or multi-facility for now;
                                // * product store was created without a facility
                                Debug.logWarning("Setup: Cannot get warehouse for store '"
                                        + productStoreId + "'" + " because ProductStore.inventoryFacilityId is not set", module);
                            }
                        } else {
                            Debug.logError("Setup: ProductStore '" + productStoreId + "' does not appear to belong to"
                                    + " organization '" + orgPartyId + "'; ignoring", module);
                            productStore = null;
                        }
                    } else {
                        Debug.logError("Setup: ProductStore '" + productStoreId + "' not found; ignoring", module);
                    }
                } else {
                    List<GenericValue> facilities = delegator.findByAnd("Facility", UtilMisc.toMap("ownerPartyId", orgPartyId), null, useCache);
                    facility = EntityUtil.getFirst(facilities);
                    if (facilities.size() >= 2) {
                        Debug.logInfo("Setup: Multiple warehouses found for organization '" + orgPartyId
                                + "'; selecting first ('" + facility.getString("facilityId") + "')", module);
                    }
                }
            }
        }
        if (facility != null) {
            facilityId = facility.getString("facilityId");
            if (!isNewOrFailedCreate) { // if new or failed create, do not return specific info
                result.put("facilityId", facilityId);
                result.put("facility", facility);
            }
            result.put("coreCompleted", true);

            // supporting one address only for now
//            Map<String, Object> fields = UtilMisc.toMap("facilityId", facilityId);
//            List<GenericValue> contactMechPurposes = EntityUtil.filterByDate(delegator.findByAnd("FacilityContactMechPurpose",
//                    fields, UtilMisc.toList("fromDate DESC"), useCache));
//            if (!isNewOrFailedCreate) { // if new or failed create, do not return specific info
//                result.put("facilityContactMechPurposeList", contactMechPurposes);
//            }

            FacilityContactMechPurposeInfo contactMechInfo = FacilityContactMechPurposeInfo.forFacility(delegator, dispatcher, facilityId, useCache, "Setup: Facility: ");
            if (!isNewOrFailedCreate) {
                contactMechInfo.resultsToMap(result);
            }

            Set<String> shipAddressContactMechPurposes = null;
            GenericValue shipAddressContactMech = contactMechInfo.getFacilityContactMechForPurpose(delegator, "SHIP_ORIG_LOCATION", useCache);
            if (shipAddressContactMech == null) {
                if (UtilMisc.booleanValueVersatile(params.get("useClosestAddress"), true)) {
                    Debug.logInfo("Setup: Facility: facility '" + facilityId + "' has no SHIP_ORIG_LOCATION address; trying closest-matching for address purposes " + FACILITY_MAINADDR_PURPOSES, module);
                    shipAddressContactMech = contactMechInfo.getClosestFacilityContactMechForPurposes(delegator, FACILITY_MAINADDR_PURPOSES, useCache);
                }
            }
            if (shipAddressContactMech != null) {
                shipAddressContactMechPurposes = contactMechInfo.getContactMechPurposes(shipAddressContactMech.getString("contactMechId"));
            }
            if (!isNewOrFailedCreate) {
                result.put("shipAddressContactMech", shipAddressContactMech);
                result.put("shipAddressContactMechPurposes", shipAddressContactMechPurposes);
            }
            boolean shipAddressStandaloneCompleted = (shipAddressContactMech != null) && UtilMisc.containsAll(shipAddressContactMechPurposes, FACILITY_MAINADDR_PURPOSES);
            result.put("shipAddressStandaloneCompleted", shipAddressStandaloneCompleted);

            if (!isNewOrFailedCreate) {
                result.put("locationPurposes", FACILITY_MAINADDR_PURPOSES);
            }
            Map<String, GenericValue> locationContactMechs = contactMechInfo.getFacilityContactMechForPurposeMap(delegator, FACILITY_MAINADDR_PURPOSES, useCache);
            boolean locationAddressesCompleted = (locationContactMechs.size() >= FACILITY_MAINADDR_PURPOSES.size());
            result.put("locationAddressesCompleted", locationAddressesCompleted);

            boolean completed = locationAddressesCompleted;
            result.put("completed", completed);
        }
        return result;
    }

    public static Map<String, Object> getCatalogStepStateData(Delegator delegator, LocalDispatcher dispatcher, Map<String, Object> params, boolean useCache)
            throws GeneralException {
        Map<String, Object> result = UtilMisc.toMap("completed", false);

        String productStoreId = (String) params.get("productStoreId");
        String prodCatalogId = (String) params.get("prodCatalogId");

        boolean isNewOrFailedCreate = isUnspecificRecordRequest(params, "Catalog");

        List<GenericValue> productStoreCatalogList = EntityQuery.use(delegator).from("ProductStoreCatalog")
                .where("productStoreId", productStoreId).orderBy("sequenceNum ASC").filterByDate().cache(useCache).queryList();
        result.put("productStoreCatalogList", productStoreCatalogList);

        GenericValue productStoreCatalog = null;
        if (UtilValidate.isNotEmpty(prodCatalogId) && !isNewOrFailedCreate) { // ignore ID if new or failed create
            List<GenericValue> filteredList = EntityUtil.filterByAnd(productStoreCatalogList, UtilMisc.toMap("prodCatalogId", prodCatalogId));
            productStoreCatalog = EntityUtil.getFirst(filteredList);
            if (productStoreCatalog == null) {
                Debug.logError("Setup: Could not find catalog '" + prodCatalogId + "' for store '" + productStoreId + "'", module);
            }
        } else {
            productStoreCatalog = EntityUtil.getFirst(productStoreCatalogList);
            if (productStoreCatalogList.size() >= 2) {
                Debug.logInfo("Setup: Store '" + productStoreId + "' has multiple active catalogs, selecting first ('"
                        + productStoreCatalog.getString("prodCatalogId") + "') as default for setup"
                        + " (catalogs: " + getEntityStringFieldValues(productStoreCatalogList, "prodCatalogId", new ArrayList<>(productStoreCatalogList.size())) + ")",
                        productStoreCatalog.getString("prodCatalogId"));
            } else if (productStoreCatalogList.size() == 0 && UtilValidate.isNotEmpty(prodCatalogId)) {
                Debug.logInfo("Setup: Store '" + productStoreId + "' has no active catalog", module);
            }
        }

        if (productStoreCatalog != null) {
            GenericValue prodCatalog = productStoreCatalog.getRelatedOne("ProdCatalog", useCache);
            if (!isNewOrFailedCreate) { // if new or failed create, do not return specific info
                result.put("productStoreCatalog", productStoreCatalog);
                result.put("prodCatalog", prodCatalog);
            }
            result.put("completed", true);
        }

        return result;
    }

    public static Map<String, Object> getStoreStepStateData(Delegator delegator, LocalDispatcher dispatcher, Map<String, Object> params, boolean includeWebsite, boolean useCache)
            throws GeneralException {
        Map<String, Object> result = UtilMisc.toMap("completed", false, "coreCompleted", false);

        String productStoreId = (String) params.get("productStoreId");
        String orgPartyId = (String) params.get("orgPartyId");
        boolean hasOrgPartyId = UtilValidate.isNotEmpty(orgPartyId);

        boolean isNewOrFailedCreate = isUnspecificRecordRequest(params, "Store");

        GenericValue productStore = null;
        List<GenericValue> productStoreList = hasOrgPartyId ? 
                EntityQuery.use(delegator).from("ProductStore").where("payToPartyId", orgPartyId).cache(useCache).queryList() :
                Collections.emptyList();
        if (UtilValidate.isNotEmpty(productStoreId) && !isNewOrFailedCreate) { // ignore ID if new or failed create
            if (hasOrgPartyId) {
                List<GenericValue> targetProductStoreList = EntityUtil.filterByAnd(productStoreList, UtilMisc.toMap("productStoreId", productStoreId));
                if (UtilValidate.isNotEmpty(targetProductStoreList)) {
                    productStore = productStoreList.get(0);
                }
            } else {
                // we'll require a non-null orgPartyId here to simplify, so both parameters should be passed around
            }
        } else {
            // Unless asked to create a new store, read the first store by default;
            // in majority cases clients will create one store per company, so this saves some reloading.
            if (hasOrgPartyId) {
                if (UtilValidate.isNotEmpty(productStoreList)) {
                    // FIXME: there should be a flag somewhere to indicate the default store...
                    productStore = productStoreList.get(0);
                    if (productStoreList.size() >= 2) {
                        Debug.logInfo("Setup: Organization '" + orgPartyId
                            + "' has multiple ProductStores (" + productStoreList.size()
                            + "); assuming first as default for the setup process (productStoreId: "
                            + productStore.getString("productStoreId") + ")", module);
                    }
                }
            }
        }

        result.put("productStoreList", productStoreList);
        Set<String> productStoreIdSet = productStoreList.stream().map(value -> value.getString("productStoreId")).collect(Collectors.toSet());
        result.put("productStoreIdSet", productStoreIdSet);

        boolean productStoreCompleted = false;
        if (productStore != null) {
            result.put("coreCompleted", true);
            productStoreId = productStore.getString("productStoreId");
            if (!isNewOrFailedCreate) { // if new or failed create, do not return specific info
                result.put("productStoreId", productStoreId);
                result.put("productStore", productStore);
            }

            String facilityId = productStore.getString("inventoryFacilityId");
            if (UtilValidate.isNotEmpty(facilityId)) {
                Map<String, Object> fields = UtilMisc.toMap("productStoreId", productStoreId, "facilityId", facilityId);
                List<GenericValue> productFacilityList = EntityUtil.filterByDate(delegator.findByAnd("ProductStoreFacility",
                        fields, UtilMisc.toList("sequenceNum ASC"), useCache));
                if (UtilValidate.isNotEmpty(productFacilityList)) {
                    productStoreCompleted = true;
                } else {
                    Debug.logWarning("Setup: ProductStore '" + productStoreId + "' has no ProductStoreFacility relation for warehouse '" + facilityId
                            + "'; treating store as incomplete" + " (NOTE: may require manually fixing the schema)", module);
                }
            } else {
                Debug.logWarning("Setup: ProductStore '" + productStoreId + "' has no inventoryFacilityId field; treating store as incomplete", module);
            }
            if (!isNewOrFailedCreate) { // if new or failed create, do not return specific info
                result.put("facilityId", facilityId);
            }
            result.put("productStoreCompleted", productStoreCompleted);
        }
        if (includeWebsite) {
            Map<String, Object> websiteParams = new HashMap<>(params);
            websiteParams.put("productStoreId", productStoreId);
            // TODO: REVIEW: not sure if will be needed
            //websiteParams.put("unspecReqWebsite", isNewOrFailedCreate);
            Map<String, Object> websiteResult = getWebsiteStepStateData(delegator, dispatcher, websiteParams, useCache);
            result.putAll(websiteResult); // this magically works for now

            boolean websiteCompleted = Boolean.TRUE.equals(websiteResult.get("completed"));
            result.put("websiteCompleted", websiteCompleted);

            result.put("completed", productStoreCompleted && websiteCompleted);
        } else {
            result.put("completed", productStoreCompleted);
        }
        return result;
    }
    public static Map<String, Object> getStoreStepStateData(Delegator delegator, LocalDispatcher dispatcher, Map<String, Object> params, boolean useCache) throws GeneralException {
        return getStoreStepStateData(delegator, dispatcher, params, true, useCache);
    }

    public static Map<String, Object> getWebsiteStepStateData(Delegator delegator, LocalDispatcher dispatcher, Map<String, Object> params, boolean useCache)
            throws GeneralException {
        Map<String, Object> result = UtilMisc.toMap("completed", false);

        String productStoreId = (String) params.get("productStoreId");

        boolean isNewOrFailedCreate = isUnspecificRecordRequest(params, "Website");

        GenericValue webSite = null;
        Map<String, Object> fields = UtilMisc.toMap("productStoreId", productStoreId);
        List<GenericValue> webSiteList = delegator.findByAnd("WebSite", fields, null, useCache);
        if (!isNewOrFailedCreate) {
            String webSiteId = (String) params.get("webSiteId");
            if (UtilValidate.isNotEmpty(webSiteId)) {
                for(GenericValue ws : webSiteList) {
                    if (webSiteId.equals(ws.getString("webSiteId"))) {
                        webSite = ws;
                        break;
                    }
                }
                if (webSite == null) {
                    Debug.logError("Setup: Received webSiteId '" + webSiteId
                            + "' does not match any WebSite for productStoreId '" + productStoreId
                            + "' in system; ignoring and using default (if any)", module);
                }
            }
        }

        // NOTE: The following logic must match: org.ofbiz.product.store.ProductStoreWorker.getStoreDefaultWebSite

        List<GenericValue> defaultWebSiteList = EntityUtil.filterByAnd(webSiteList, UtilMisc.toMap("isStoreDefault", "Y"));
        GenericValue defaultWebSite = EntityUtil.getFirst(defaultWebSiteList);
        if (defaultWebSiteList.size() > 1) {
            Debug.logError("Multiple (" + defaultWebSiteList.size() + ") default WebSite records found for ProductStore '" + productStoreId
                    + "'; you must only assign one WebSite the isStoreDefault Y flag per ProductStore;"
                    + " (using first found as default: " + defaultWebSite.getString("webSiteId") + ")", module);
        }

        // NOTE: this isn't fully accurate (for the bad webSiteId param case), but won't matter for now
        if (webSite == null) {
            if (defaultWebSite != null) {
                webSite = defaultWebSite;
            } else {
                webSite = EntityUtil.getFirst(webSiteList);
                if (webSiteList != null && webSiteList.size() >= 2) {
                    Debug.logWarning("Setup: Found multiple (" + webSiteList.size() + ") WebSite records"
                            + " for ProductStore '" + productStoreId + "', but none of them is set as default (isStoreDefault Y)"
                            + "; some code may have issues with this configuration; try setting isStoreDefault Y for one of them"
                            + " (using first found as default: " + webSite.getString("webSiteId") + ")", module);
                }
            }
        }

        result.put("webSiteList", webSiteList);
        result.put("webSiteCount", webSiteList.size());
        result.put("defaultWebSite", defaultWebSite);

        if (webSite != null) {
            if (!isNewOrFailedCreate) { // if new or failed create, do not return specific info
                result.put("webSiteId", webSite.getString("webSiteId"));
                result.put("webSite", webSite);
            }
            result.put("completed", true);
        }
        return result;
    }

    /*
     * *******************************************
     * Generic helpers
     * *******************************************
     */

    // TODO: REVIEW: unclear which code should order fromDate by ASC or DESC, so in the meantime,
    // use this to centralize any fix needed (for setup code only!)
    private static final List<String> defaultContactOrderBy = UtilMisc.unmodifiableArrayList("-fromDate");
    public static List<String> getDefaultContactOrderBy() {
        return defaultContactOrderBy;
    }
    public static List<String> getDefaultContactOrderBy(String fromDateField) {
        return UtilMisc.toList("-" + fromDateField);
    }

    private static <T extends Collection<String>> T getEntityStringFieldValues(List<GenericValue> values, String fieldName, T out) {
        for (GenericValue value : values) {
            String str = value.getString(fieldName);
            if (str != null)
                out.add(str);
        }
        return out;
    }

    private static boolean isEventError(Map<String, Object> params) {
        return SetupEvents.isPreviousEventSavedError(params);
    }

    // Exact request states

    static boolean isNewRecordRequest(Map<String, Object> params, String recordTypeCamel) {
        return UtilMisc.booleanValueVersatile(params.get("new" + recordTypeCamel), false);
    }

    /**
     * Generalized record action check, naming pattern: "isXxxYyy" where Xxx = action, Yyy = record type (step name).
     */
    static boolean isActionRecordRequest(Map<String, Object> params, String actionNameCamel, String recordTypeCamel) {
        if ("new".equalsIgnoreCase(actionNameCamel)) {
            return isNewRecordRequest(params, recordTypeCamel);
        } else {
            return UtilMisc.booleanValueVersatile(params.get("is" + actionNameCamel + recordTypeCamel), false);
        }
    }

    static boolean isActionRecordSuccessRequest(Map<String, Object> params, String actionNameCamel, String recordTypeCamel) {
        return isActionRecordRequest(params, actionNameCamel, recordTypeCamel) && !isEventError(params);
    }

    static boolean isActionRecordFailedRequest(Map<String, Object> params, String actionNameCamel, String recordTypeCamel) {
        return isActionRecordRequest(params, actionNameCamel, recordTypeCamel) && isEventError(params);
    }

    static boolean isCreateRecordRequest(Map<String, Object> params, String recordTypeCamel) {
        return UtilMisc.booleanValueVersatile(params.get("isCreate" + recordTypeCamel), false);
    }

    static boolean isCreateRecordSuccessRequest(Map<String, Object> params, String recordTypeCamel) {
        return isCreateRecordRequest(params, recordTypeCamel) && !isEventError(params);
    }

    static boolean isCreateRecordFailedRequest(Map<String, Object> params, String recordTypeCamel) {
        return isCreateRecordRequest(params, recordTypeCamel) && isEventError(params);
    }

    static boolean isDeleteRecordRequest(Map<String, Object> params, String recordTypeCamel) {
        return UtilMisc.booleanValueVersatile(params.get("isDelete" + recordTypeCamel), false);
    }

    static boolean isDeleteRecordSuccessRequest(Map<String, Object> params, String recordTypeCamel) {
        return isDeleteRecordRequest(params, recordTypeCamel) && !isEventError(params);
    }

    static boolean isDeleteRecordFailedRequest(Map<String, Object> params, String recordTypeCamel) {
        return isDeleteRecordRequest(params, recordTypeCamel) && isEventError(params);
    }

    static boolean isAddRecordRequest(Map<String, Object> params, String recordTypeCamel) {
        return UtilMisc.booleanValueVersatile(params.get("isAdd" + recordTypeCamel), false);
    }

    static boolean isAddRecordSuccessRequest(Map<String, Object> params, String recordTypeCamel) {
        return isAddRecordRequest(params, recordTypeCamel) && !isEventError(params);
    }

    static boolean isAddRecordFailedRequest(Map<String, Object> params, String recordTypeCamel) {
        return isAddRecordRequest(params, recordTypeCamel) && isEventError(params);
    }

    // Aggregate/high-level states

    /**
     * Returns true if "new" form was requested
     * OR if a form was submitted as create and creation failed
     * OR it's a delete request.
     * <p>
     * For second case, if create form was submitted with a specific ID requested but error because the ID already exists,
     * we don't want the page to look up existing, because it discards the user input.
     * <p>
     * newXxx: passed when loading the page/form
     * isCreateXxx: passed with form submit
     * isDeleteXxx: passed with delete form submit
     */
    static boolean isUnspecificRecordRequest(Map<String, Object> params, String recordTypeCamel) {
        //stepName = stepName.substring(0, 1).toUpperCase() + stepName.substring(1);

        // SPECIAL: this can be used internally to override
        Boolean unspecific = UtilMisc.booleanValue(params.get("unspecReq" + recordTypeCamel));
        if (unspecific != null) return unspecific;

        return isNewRecordRequest(params, recordTypeCamel) ||
                isCreateRecordFailedRequest(params, recordTypeCamel) ||
                isDeleteRecordSuccessRequest(params, recordTypeCamel) ||
                isAddRecordFailedRequest(params, recordTypeCamel);
    }

    static boolean isEffectiveNewRecordRequest(Map<String, Object> params, String recordTypeCamel) {
        return isNewRecordRequest(params, recordTypeCamel) ||
                isCreateRecordFailedRequest(params, recordTypeCamel);
    }
    
    /**
     * Tries to find an organization related to the given party, mainly for display purposes,
     * and returns the PartyRelationship, with the organization party ID as partyIdFrom.
     * <p>
     * For now, does NOT strictly require roleTypeIdFrom "INTERNAL_ORGANIZATIO", though it is prioritized;
     * however the organization does need to have "INTERNAL_ORGANIZATIO" as PartyRole.
     * <p>
     * Priority: INTERNAL_ORGANIZATIO > _NA_ > *
     * <p>
     * This logic works around demo data variations.
     * <p>
     * TODO?: could delegate, or move this impl, but for now
     * this may be somewhat setup-specific heuristic (see SetupPreparePartyEdit.groovy).
     */
    public static GenericValue findBestDisplayOrganizationRelationForParty(Delegator delegator, String partyId, boolean useCache) throws GenericEntityException {
        List<GenericValue> relList = EntityQuery.use(delegator).from("PartyRelationship")
                .where("partyIdTo", partyId).filterByDate().orderBy("-fromDate").cache(useCache).queryList();
        GenericValue bestRel = null;
        for(GenericValue rel : relList) {
            String roleTypeIdFrom = rel.getString("roleTypeIdFrom");
            if ("INTERNAL_ORGANIZATIO".equals(roleTypeIdFrom)) {
                bestRel = rel;
                break;
            } else if (EntityQuery.use(delegator).from("PartyRole")
                    .where("partyId", rel.getString("partyIdFrom"), "roleTypeId", "INTERNAL_ORGANIZATIO").cache(useCache)
                    .queryOne().size() > 0) {
                if ("_NA_".equals(roleTypeIdFrom)) {
                    // _NA_ has priority over 
                    if (bestRel == null || !"_NA_".equals(bestRel.getString("roleTypeIdFrom"))) {
                        bestRel = rel;
                    }
                } else {
                    if (bestRel == null) {
                        bestRel = rel;
                    }
                }
            }
        }
        return bestRel;
    }
    
    /**
     * Returns the ProductStoreRole in given list based on priority of productStoreId and roleTypeId.
     * <p>
     * DEV NOTE: 2018-10-24: The strict==false case is currently only used to help give warnings to users,
     * the screen/events does not actually support handling roleTypeId other than the target.
     */
    public static GenericValue getBestProductStoreRole(Collection<GenericValue> productStoreRoleList, String mainProductStoreId, 
            Collection<String> productStoreIds, String targetRoleTypeId, boolean strictRoleType) {
        if (mainProductStoreId == null || productStoreIds == null) {
            return null;
        }
        // Best-effort: Get the most precise role possible
        GenericValue bestPsr = null;
        boolean bestPsrRoleMatches = false;
        for(GenericValue psr : productStoreRoleList) {
            String psrId = psr.getString("productStoreId");
            if (psrId.equals(mainProductStoreId)) {
                if (psr.getString("roleTypeId").equals(targetRoleTypeId)) {
                    // perfect match
                    bestPsr = psr;
                    bestPsrRoleMatches = true;
                    break;
                } else if (!strictRoleType) {
                    if (bestPsr == null || (!bestPsrRoleMatches && bestPsr.getString("productStoreId").equals(mainProductStoreId))) {
                        // 3rd best match: matches the main store, but in a different role
                        bestPsr = psr;
                        bestPsrRoleMatches = false;
                    }
                }
            }
            if (productStoreIds.contains(psrId)) {
                if (psr.getString("roleTypeId").equals(targetRoleTypeId)) {
                    // 2nd best match: matches non-default store, in target role
                    bestPsr = psr;
                    bestPsrRoleMatches = true;
                } else if (!strictRoleType) {
                    if (bestPsr == null) {
                        // 4th best match: matches non-default store, but in a different role
                        bestPsr = psr;
                        bestPsrRoleMatches = false;
                    }
                }
            }
        }
        return bestPsr;
    }

    // REMOVED: this was code intended for handling roleTypeId as part of PRODUCT_STORE_ID param for 
    // ProductStoreRoles to company stores that do not match the company relationship roleTypeId, 
    // but it is too complicated to implement without causing additional issues.
//    /**
//     * Split "productStoreId[::roleTypeId]" string to map containing the values.
//     * Returns null if param was empty or "::", map with valid flag in every other case.
//     * <p>
//     * Used by EditUser.ftl and SetupEvents.xml.
//     */
//    public static Map<String, Object> splitProductStoreRoleParamToValues(Delegator delegator, String productStoreRoleParam, Collection<String> allowedProductStoreIds) {
//        if (UtilValidate.isEmpty(productStoreRoleParam)) {
//            return null;
//        }
//        String[] parts = productStoreRoleParam.split("::", 2);
//        String productStoreId = parts[0];
//        String roleTypeId = (parts.length >= 2) ? parts[1] : "";
//        if (UtilValidate.isEmpty(productStoreId)) {
//            if (roleTypeId.isEmpty()) {
//                return null;
//            } else {
//                return UtilMisc.toMap("valid", false);
//            }
//        }
//
//        if (allowedProductStoreIds != null && !allowedProductStoreIds.contains(productStoreId)) {
//            Debug.logWarning("Setup: Unrecognized productStoreId '" + productStoreId  + " from store role parameter '" 
//                    + productStoreRoleParam + "'; allowed IDs: " + allowedProductStoreIds, module);
//            return UtilMisc.toMap("valid", false);
//        }
//
//        GenericValue productStore = null;
//        GenericValue roleType = null;
//        boolean valid = true;
//        try {
//            productStore = EntityQuery.use(delegator).from("ProductStore").where("productStoreId", productStoreId).queryOne();
//            if (productStore == null) {
//                Debug.logWarning("Setup: Unrecognized productStoreId '" + productStoreId + "' from store role parameter '" + productStoreRoleParam + "'", module);
//                valid = false;
//            } else {
//                if (!roleTypeId.isEmpty()) {
//                    roleType = EntityQuery.use(delegator).from("RoleType").where("roleTypeId", roleTypeId).cache().queryOne();
//                    if (roleType == null) {
//                        Debug.logWarning("Setup: Unrecognized roleTypeId '" + roleTypeId + "' from store role parameter '" + productStoreRoleParam + "'", module);
//                        valid = false;
//                    }
//                }
//            }
//        } catch (GenericEntityException e) {
//            Debug.logError(e, module);
//            valid = false;
//        }
//
//        return UtilMisc.toMap("valid", valid, "productStore", productStore, "roleType", roleType);
//    }
}

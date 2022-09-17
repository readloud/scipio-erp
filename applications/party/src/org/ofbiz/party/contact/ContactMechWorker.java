/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/

package org.ofbiz.party.contact;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.servlet.ServletRequest;

import org.ofbiz.base.util.Assert;
import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.UtilDateTime;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.entity.Delegator;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.condition.EntityCondition;
import org.ofbiz.entity.util.EntityQuery;
import org.ofbiz.entity.util.EntityUtil;
import org.ofbiz.entity.util.EntityUtilProperties;

/**
 * Worker methods for Contact Mechanisms
 */
public class ContactMechWorker {

    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());

    private ContactMechWorker() {}

    public static List<Map<String, Object>> getPartyContactMechValueMaps(Delegator delegator, String partyId, boolean showOld) {
       return getPartyContactMechValueMaps(delegator, partyId, showOld, null);
    }
    public static List<Map<String, Object>> getPartyContactMechValueMaps(Delegator delegator, String partyId, boolean showOld, String contactMechTypeId) {
        List<Map<String, Object>> partyContactMechValueMaps = new ArrayList<>();

        List<GenericValue> allPartyContactMechs = null;

        try {
            // SCIPIO: 2018-10-19: Added filterByDate to initial pre-query instead of manual post-query filter, and handle contactMechTypeId in smarter way
            // DEV NOTE: WARN: DO NOT ADD A cache() CALL HERE!
            if (contactMechTypeId != null) {
                allPartyContactMechs = EntityQuery.use(delegator)
                        .from("PartyContactMechAndContactMech")
                        .where("partyId", partyId, "contactMechTypeId", contactMechTypeId)
                        .filterByDate(!showOld)
                        .queryList();
            } else {
                allPartyContactMechs = EntityQuery.use(delegator)
                        .from("PartyContactMechAndContactMech")
                        .where("partyId", partyId)
                        .filterByDate(!showOld)
                        .queryList();
            }
            /*
            List<GenericValue> tempCol = EntityQuery.use(delegator).from("PartyContactMech").where("partyId", partyId).filterByDate(!showOld).queryList();
            if (contactMechTypeId != null) {
                List<GenericValue> tempColTemp = new ArrayList<>();
                for (GenericValue partyContactMech: tempCol) {
                    GenericValue contactMech = delegator.getRelatedOne("ContactMech", partyContactMech, false);
                    if (contactMech != null && contactMechTypeId.equals(contactMech.getString("contactMechTypeId"))) {
                        tempColTemp.add(partyContactMech);
                    }

                }
                tempCol = tempColTemp;
            }
            if (!showOld) tempCol = EntityUtil.filterByDate(tempCol, true);
            
            allPartyContactMechs = tempCol;
            */
        } catch (GenericEntityException e) {
            Debug.logWarning(e, module);
        }

        if (allPartyContactMechs == null) return partyContactMechValueMaps;

        for (GenericValue partyContactMechView : allPartyContactMechs) {
            GenericValue partyContactMech = null;
            try { // SCIPIO: 2018-10-19
                partyContactMech = partyContactMechView.extractViewMember("PartyContactMech");
            } catch (GenericEntityException e) {
                Debug.logWarning(e, module);
            }

            GenericValue contactMech = null;

            try {
                // SCIPIO: 2018-10-22: avoid double-lookup
                //contactMech = partyContactMech.getRelatedOne("ContactMech", false);
                contactMech = partyContactMechView.extractViewMember("ContactMech");
            } catch (GenericEntityException e) {
                Debug.logWarning(e, module);
            }
            if (contactMech != null) {
                Map<String, Object> partyContactMechValueMap = new HashMap<>();

                partyContactMechValueMaps.add(partyContactMechValueMap);
                partyContactMechValueMap.put("contactMech", contactMech);
                partyContactMechValueMap.put("partyContactMech", partyContactMech);

                try {
                    partyContactMechValueMap.put("contactMechType", contactMech.getRelatedOne("ContactMechType", true));
                } catch (GenericEntityException e) {
                    Debug.logWarning(e, module);
                }

                try {
                    List<GenericValue> partyContactMechPurposes = partyContactMech.getRelated("PartyContactMechPurpose", null, null, false);

                    if (!showOld) partyContactMechPurposes = EntityUtil.filterByDate(partyContactMechPurposes, true);
                    partyContactMechValueMap.put("partyContactMechPurposes", partyContactMechPurposes);
                } catch (GenericEntityException e) {
                    Debug.logWarning(e, module);
                }

                try {
                    if ("POSTAL_ADDRESS".equals(contactMech.getString("contactMechTypeId"))) {
                        partyContactMechValueMap.put("postalAddress", contactMech.getRelatedOne("PostalAddress", false));
                    } else if ("TELECOM_NUMBER".equals(contactMech.getString("contactMechTypeId"))) {
                        partyContactMechValueMap.put("telecomNumber", contactMech.getRelatedOne("TelecomNumber", false));
                    }
                } catch (GenericEntityException e) {
                    Debug.logWarning(e, module);
                }
            }
        }

        return partyContactMechValueMaps;
    }

    public static List<Map<String, Object>> getFacilityContactMechValueMaps(Delegator delegator, String facilityId, boolean showOld, String contactMechTypeId) {
        List<Map<String, Object>> facilityContactMechValueMaps = new ArrayList<>();

        List<GenericValue> allFacilityContactMechs = null;

        try {
            // SCIPIO: 2018-10-19: Added filterByDate to initial pre-query instead of manual post-query filter, and handle contactMechTypeId in smarter way
            // DEV NOTE: WARN: DO NOT ADD A cache() CALL HERE!
            if (contactMechTypeId != null) {
                allFacilityContactMechs = EntityQuery.use(delegator)
                        .from("FacilityContactMechAndContactMech")
                        .where("facilityId", facilityId, "contactMechTypeId", contactMechTypeId)
                        .filterByDate(!showOld)
                        .queryList();
            } else {
                allFacilityContactMechs = EntityQuery.use(delegator)
                        .from("FacilityContactMechAndContactMech")
                        .where("facilityId", facilityId)
                        .filterByDate(!showOld)
                        .queryList();
            }
            /*
            List<GenericValue> tempCol = EntityQuery.use(delegator).from("FacilityContactMech").where("facilityId", facilityId).filterByDate(!showOld).queryList();
            if (contactMechTypeId != null) {
                List<GenericValue> tempColTemp = new ArrayList<>();
                for (GenericValue partyContactMech: tempCol) {
                    GenericValue contactMech = delegator.getRelatedOne("ContactMech", partyContactMech, false);
                    if (contactMech != null && contactMechTypeId.equals(contactMech.getString("contactMechTypeId"))) {
                        tempColTemp.add(partyContactMech);
                    }

                }
                tempCol = tempColTemp;
            }
            if (!showOld) tempCol = EntityUtil.filterByDate(tempCol, true); // SCIPIO: 2018-10-19: Added filterByDate to initial pre-query instead of manual post-query filter
            allFacilityContactMechs = tempCol;
            */
        } catch (GenericEntityException e) {
            Debug.logWarning(e, module);
        }

        if (allFacilityContactMechs == null) return facilityContactMechValueMaps;

        for (GenericValue facilityContactMechView : allFacilityContactMechs) {
            GenericValue facilityContactMech = null;
            try { // SCIPIO: 2018-10-19
                facilityContactMech = facilityContactMechView.extractViewMember("FacilityContactMech");
            } catch (GenericEntityException e) {
                Debug.logWarning(e, module);
            }

            GenericValue contactMech = null;

            try {
                // SCIPIO: 2018-10-22: avoid double-lookup
                //contactMech = facilityContactMech.getRelatedOne("ContactMech", false);
                contactMech = facilityContactMechView.extractViewMember("ContactMech");
            } catch (GenericEntityException e) {
                Debug.logWarning(e, module);
            }
            if (contactMech != null) {
                Map<String, Object> facilityContactMechValueMap = new HashMap<>();

                facilityContactMechValueMaps.add(facilityContactMechValueMap);
                facilityContactMechValueMap.put("contactMech", contactMech);
                facilityContactMechValueMap.put("facilityContactMech", facilityContactMech);

                try {
                    facilityContactMechValueMap.put("contactMechType", contactMech.getRelatedOne("ContactMechType", true));
                } catch (GenericEntityException e) {
                    Debug.logWarning(e, module);
                }

                try {
                    List<GenericValue> facilityContactMechPurposes = facilityContactMech.getRelated("FacilityContactMechPurpose", null, null, false);

                    if (!showOld) facilityContactMechPurposes = EntityUtil.filterByDate(facilityContactMechPurposes, true);
                    facilityContactMechValueMap.put("facilityContactMechPurposes", facilityContactMechPurposes);
                } catch (GenericEntityException e) {
                    Debug.logWarning(e, module);
                }

                try {
                    if ("POSTAL_ADDRESS".equals(contactMech.getString("contactMechTypeId"))) {
                        facilityContactMechValueMap.put("postalAddress", contactMech.getRelatedOne("PostalAddress", false));
                    } else if ("TELECOM_NUMBER".equals(contactMech.getString("contactMechTypeId"))) {
                        facilityContactMechValueMap.put("telecomNumber", contactMech.getRelatedOne("TelecomNumber", false));
                    }
                } catch (GenericEntityException e) {
                    Debug.logWarning(e, module);
                }
            }
        }

        return facilityContactMechValueMaps;
    }


    public static List<Map<String, GenericValue>> getOrderContactMechValueMaps(Delegator delegator, String orderId) {
        List<Map<String, GenericValue>> orderContactMechValueMaps = new ArrayList<>();

        List<GenericValue> allOrderContactMechs = null;

        try {
            allOrderContactMechs = EntityQuery.use(delegator).from("OrderContactMech")
                    .where("orderId", orderId)
                    .orderBy("contactMechPurposeTypeId")
                    .queryList();
        } catch (GenericEntityException e) {
            Debug.logWarning(e, module);
        }

        if (allOrderContactMechs == null) return orderContactMechValueMaps;

        for (GenericValue orderContactMech: allOrderContactMechs) {
            GenericValue contactMech = null;

            try {
                contactMech = orderContactMech.getRelatedOne("ContactMech", false);
            } catch (GenericEntityException e) {
                Debug.logWarning(e, module);
            }
            if (contactMech != null) {
                Map<String, GenericValue> orderContactMechValueMap = new HashMap<>();

                orderContactMechValueMaps.add(orderContactMechValueMap);
                orderContactMechValueMap.put("contactMech", contactMech);
                orderContactMechValueMap.put("orderContactMech", orderContactMech);

                try {
                    orderContactMechValueMap.put("contactMechType", contactMech.getRelatedOne("ContactMechType", true));
                } catch (GenericEntityException e) {
                    Debug.logWarning(e, module);
                }

                try {
                    GenericValue contactMechPurposeType = orderContactMech.getRelatedOne("ContactMechPurposeType", false);

                    orderContactMechValueMap.put("contactMechPurposeType", contactMechPurposeType);
                } catch (GenericEntityException e) {
                    Debug.logWarning(e, module);
                }

                try {
                    if ("POSTAL_ADDRESS".equals(contactMech.getString("contactMechTypeId"))) {
                        orderContactMechValueMap.put("postalAddress", contactMech.getRelatedOne("PostalAddress", false));
                    } else if ("TELECOM_NUMBER".equals(contactMech.getString("contactMechTypeId"))) {
                        orderContactMechValueMap.put("telecomNumber", contactMech.getRelatedOne("TelecomNumber", false));
                    }
                } catch (GenericEntityException e) {
                    Debug.logWarning(e, module);
                }
            }
        }

        return orderContactMechValueMaps;
    }

    public static Collection<Map<String, GenericValue>> getWorkEffortContactMechValueMaps(Delegator delegator, String workEffortId) {
        Collection<Map<String, GenericValue>> workEffortContactMechValueMaps = new ArrayList<>();

        List<GenericValue> allWorkEffortContactMechs = null;

        try {
            allWorkEffortContactMechs = EntityQuery.use(delegator).from("WorkEffortContactMech")
                    .where("workEffortId", workEffortId)
                    .filterByDate()
                    .queryList();
        } catch (GenericEntityException e) {
            Debug.logWarning(e, module);
        }

        if (allWorkEffortContactMechs == null) return null;

        for (GenericValue workEffortContactMech: allWorkEffortContactMechs) {
            GenericValue contactMech = null;

            try {
                contactMech = workEffortContactMech.getRelatedOne("ContactMech", false);
            } catch (GenericEntityException e) {
                Debug.logWarning(e, module);
            }
            if (contactMech != null) {
                Map<String, GenericValue> workEffortContactMechValueMap = new HashMap<>();

                workEffortContactMechValueMaps.add(workEffortContactMechValueMap);
                workEffortContactMechValueMap.put("contactMech", contactMech);
                workEffortContactMechValueMap.put("workEffortContactMech", workEffortContactMech);

                try {
                    workEffortContactMechValueMap.put("contactMechType", contactMech.getRelatedOne("ContactMechType", true));
                } catch (GenericEntityException e) {
                    Debug.logWarning(e, module);
                }

                try {
                    if ("POSTAL_ADDRESS".equals(contactMech.getString("contactMechTypeId"))) {
                        workEffortContactMechValueMap.put("postalAddress", contactMech.getRelatedOne("PostalAddress", false));
                    } else if ("TELECOM_NUMBER".equals(contactMech.getString("contactMechTypeId"))) {
                        workEffortContactMechValueMap.put("telecomNumber", contactMech.getRelatedOne("TelecomNumber", false));
                    }
                } catch (GenericEntityException e) {
                    Debug.logWarning(e, module);
                }
            }
        }

        return workEffortContactMechValueMaps.size() > 0 ? workEffortContactMechValueMaps : null;
    }

    public static void getContactMechAndRelated(ServletRequest request, String partyId, Map<String, Object> target) {
        Delegator delegator = (Delegator) request.getAttribute("delegator");

        boolean tryEntity = true;
        if (request.getAttribute("_ERROR_MESSAGE_") != null) {
            tryEntity = false;
        }
        if ("true".equals(request.getParameter("tryEntity"))) {
            tryEntity = true;
        }

        String donePage = request.getParameter("DONE_PAGE");
        if (donePage == null) {
            donePage = (String) request.getAttribute("DONE_PAGE");
        }
        if (UtilValidate.isEmpty(donePage)) {
            donePage = "viewprofile";
        }
        target.put("donePage", donePage);

        String contactMechTypeId = request.getParameter("preContactMechTypeId");

        if (contactMechTypeId == null) {
            contactMechTypeId = (String) request.getAttribute("preContactMechTypeId");
        }
        if (contactMechTypeId != null) {
            tryEntity = false;
        }

        String contactMechId = request.getParameter("contactMechId");

        if (request.getAttribute("contactMechId") != null) {
            contactMechId = (String) request.getAttribute("contactMechId");
        }

        GenericValue contactMech = null;

        if (contactMechId != null) {
            target.put("contactMechId", contactMechId);

            // try to find a PartyContactMech with a valid date range
            List<GenericValue> partyContactMechs = null;

            try {
                partyContactMechs = EntityQuery.use(delegator).from("PartyContactMech")
                        .where("partyId", partyId, "contactMechId", contactMechId)
                        .filterByDate()
                        .queryList();
            } catch (GenericEntityException e) {
                Debug.logWarning(e, module);
            }

            GenericValue partyContactMech = EntityUtil.getFirst(partyContactMechs);

            if (partyContactMech != null) {
                target.put("partyContactMech", partyContactMech);

                Collection<GenericValue> partyContactMechPurposes = null;

                try {
                    partyContactMechPurposes = EntityUtil.filterByDate(partyContactMech.getRelated("PartyContactMechPurpose", null, null, false), true);
                } catch (GenericEntityException e) {
                    Debug.logWarning(e, module);
                }
                if (UtilValidate.isNotEmpty(partyContactMechPurposes)) {
                    target.put("partyContactMechPurposes", partyContactMechPurposes);
                }
            }

            try {
                contactMech = EntityQuery.use(delegator).from("ContactMech").where("contactMechId", contactMechId).queryOne();
            } catch (GenericEntityException e) {
                Debug.logWarning(e, module);
            }

            if (contactMech != null) {
                target.put("contactMech", contactMech);
                contactMechTypeId = contactMech.getString("contactMechTypeId");
            }
        }

        if (contactMechTypeId != null) {
            target.put("contactMechTypeId", contactMechTypeId);

            try {
                GenericValue contactMechType = EntityQuery.use(delegator).from("ContactMechType").where("contactMechTypeId", contactMechTypeId).queryOne();

                if (contactMechType != null) {
                    target.put("contactMechType", contactMechType);
                }
            } catch (GenericEntityException e) {
                Debug.logWarning(e, module);
            }

            Collection<GenericValue> purposeTypes = new LinkedList<>();
            Iterator<GenericValue> typePurposes = null;

            try {
                typePurposes = UtilMisc.toIterator(EntityQuery.use(delegator).from("ContactMechTypePurpose")
                        .where("contactMechTypeId", contactMechTypeId)
                        .queryList());
            } catch (GenericEntityException e) {
                Debug.logWarning(e, module);
            }
            while (typePurposes != null && typePurposes.hasNext()) {
                GenericValue contactMechTypePurpose = typePurposes.next();
                GenericValue contactMechPurposeType = null;

                try {
                    contactMechPurposeType = contactMechTypePurpose.getRelatedOne("ContactMechPurposeType", false);
                } catch (GenericEntityException e) {
                    Debug.logWarning(e, module);
                }
                if (contactMechPurposeType != null) {
                    purposeTypes.add(contactMechPurposeType);
                }
            }
            if (purposeTypes.size() > 0) {
                target.put("purposeTypes", purposeTypes);
            }
        }

        String requestName;

        if (contactMech == null) {
            // create
            if ("POSTAL_ADDRESS".equals(contactMechTypeId)) {
                if (request.getParameter("contactMechPurposeTypeId") != null || request.getAttribute("contactMechPurposeTypeId") != null) {
                    requestName = "createPostalAddressAndPurpose";
                } else {
                    requestName = "createPostalAddress";
                }
            } else if ("TELECOM_NUMBER".equals(contactMechTypeId)) {
                requestName = "createTelecomNumber";
            } else if ("EMAIL_ADDRESS".equals(contactMechTypeId)) {
                requestName = "createEmailAddress";
            // SCIPIO: 2018-10-09: TODO: REVIEW: this request may not exist yet
            //} else if ("FTP_ADDRESS".equals(contactMechTypeId)) {
            //    requestName = "createFtpAddress";
            } else {
                requestName = "createContactMech";
            }
        } else {
            // update
            if ("POSTAL_ADDRESS".equals(contactMechTypeId)) {
                requestName = "updatePostalAddress";
            } else if ("TELECOM_NUMBER".equals(contactMechTypeId)) {
                requestName = "updateTelecomNumber";
            } else if ("EMAIL_ADDRESS".equals(contactMechTypeId)) {
                requestName = "updateEmailAddress";
            // SCIPIO: 2018-10-09: TODO: REVIEW: this request may not exist yet
            //} else if ("FTP_ADDRESS".equals(contactMechTypeId)) {
            //    requestName = "updateFtpAddress";
            } else {
                requestName = "updateContactMech";
            }
        }
        target.put("requestName", requestName);

        if ("POSTAL_ADDRESS".equals(contactMechTypeId)) {
            GenericValue postalAddress = null;

            try {
                if (contactMech != null) {
                    postalAddress = contactMech.getRelatedOne("PostalAddress", false);
                }
            } catch (GenericEntityException e) {
                Debug.logWarning(e, module);
            }
            if (postalAddress != null) {
                target.put("postalAddress", postalAddress);
            }
        } else if ("TELECOM_NUMBER".equals(contactMechTypeId)) {
            GenericValue telecomNumber = null;

            try {
                if (contactMech != null) {
                    telecomNumber = contactMech.getRelatedOne("TelecomNumber", false);
                }
            } catch (GenericEntityException e) {
                Debug.logWarning(e, module);
            }
            if (telecomNumber != null) {
                target.put("telecomNumber", telecomNumber);
            }
        } else if ("FTP_ADDRESS".equals(contactMechTypeId)) {
            GenericValue ftpAddress = null;

            try {
                if (contactMech != null) {
                    ftpAddress = contactMech.getRelatedOne("FtpAddress", false);
                }
            } catch (GenericEntityException e) {
                Debug.logWarning(e, module);
            }
            if (ftpAddress != null) {
                target.put("ftpAddress", ftpAddress);
            }
        }

        if ("true".equals(request.getParameter("useValues"))) {
            tryEntity = true;
        }
        target.put("tryEntity", tryEntity);

        try {
            Collection<GenericValue> contactMechTypes = EntityQuery.use(delegator).from("ContactMechType").cache(true).queryList();

            if (contactMechTypes != null) {
                target.put("contactMechTypes", contactMechTypes);
            }
        } catch (GenericEntityException e) {
            Debug.logWarning(e, module);
        }
    }

    /** Returns the first valid FacilityContactMech found based on the given facilityId and a prioritized list of purposes
     * SCIPIO: 2017-12-19: added useCache flag and overload.
     * @param delegator the delegator
     * @param facilityId the facility id
     * @param purposeTypes a List of ContactMechPurposeType ids which will be checked one at a time until a valid contact mech is found
     * @param useCache whether to use entity cache (SCIPIO)
     * @return returns the first valid FacilityContactMech found based on the given facilityId and a prioritized list of purposes
     */
    public static GenericValue getFacilityContactMechByPurpose(Delegator delegator, String facilityId, List<String> purposeTypes, boolean useCache) {
        if (UtilValidate.isEmpty(facilityId)) {
            return null;
        }
        if (UtilValidate.isEmpty(purposeTypes)) {
            return null;
        }

        for (String purposeType: purposeTypes) {
            List<GenericValue> facilityContactMechPurposes = null;
            List<EntityCondition> conditionList = new LinkedList<>();
            conditionList.add(EntityCondition.makeCondition("facilityId", facilityId));
            conditionList.add(EntityCondition.makeCondition("contactMechPurposeTypeId", purposeType));
            EntityCondition entityCondition = EntityCondition.makeCondition(conditionList);
            try {
                facilityContactMechPurposes = EntityQuery.use(delegator).from("FacilityContactMechPurpose")
                        .where(entityCondition)
                        .orderBy("-fromDate")
                        .cache(useCache)
                        .filterByDate()
                        .queryList();
            } catch (GenericEntityException e) {
                Debug.logWarning(e, module);
                continue;
            }
            for (GenericValue facilityContactMechPurpose: facilityContactMechPurposes) {
                String contactMechId = facilityContactMechPurpose.getString("contactMechId");
                List<GenericValue> facilityContactMechs = null;
                conditionList = new LinkedList<>();
                conditionList.add(EntityCondition.makeCondition("facilityId", facilityId));
                conditionList.add(EntityCondition.makeCondition("contactMechId", contactMechId));
                entityCondition = EntityCondition.makeCondition(conditionList);
                try {
                    facilityContactMechs = EntityQuery.use(delegator).from("FacilityContactMech")
                            .where(entityCondition)
                            .orderBy("-fromDate")
                            .cache(useCache)
                            .filterByDate()
                            .queryList();
                } catch (GenericEntityException e) {
                    Debug.logWarning(e, module);
                }
                if (UtilValidate.isNotEmpty(facilityContactMechs)) {
                    return EntityUtil.getFirst(facilityContactMechs);
                }
            }

        }
        return null;
    }

    /** Returns the first valid FacilityContactMech found based on the given facilityId and a prioritized list of purposes, using entity cache.
     * SCIPIO: 2017-12-19: now delegating with useCache=true.
     * @param delegator the delegator
     * @param facilityId the facility id
     * @param purposeTypes a List of ContactMechPurposeType ids which will be checked one at a time until a valid contact mech is found
     * @return returns the first valid FacilityContactMech found based on the given facilityId and a prioritized list of purposes
     */
    public static GenericValue getFacilityContactMechByPurpose(Delegator delegator, String facilityId, List<String> purposeTypes) {
        return getFacilityContactMechByPurpose(delegator, facilityId, purposeTypes, true);
    }

    /**
     * SCIPIO: Finds the latest FacilityContactMech for each given purpose or for all purposes if not specified.
     * Added 2018-02-01.
     * @throws GenericEntityException
     */
    public static Map<String, GenericValue> getFacilityContactMechsByPurpose(Delegator delegator, String facilityId, Collection<String> purposeTypes, boolean useCache) throws GenericEntityException {
        if (UtilValidate.isEmpty(facilityId)) return null;

        java.sql.Timestamp moment = UtilDateTime.nowTimestamp();

        // Simply lookup all active FacilityContactMechPurpose and filter after, will tend to be faster this way
        List<GenericValue> facilityContactMechPurposes = EntityQuery.use(delegator).from("FacilityContactMechPurpose")
                .where("facilityId", facilityId)
                .orderBy("-fromDate")
                .cache(useCache)
                .filterByDate(moment)
                .queryList();

        Map<String, GenericValue> results = new HashMap<>();
        for(GenericValue facilityContactMechPurpose: facilityContactMechPurposes) {
            String contactMechPurposeTypeId = facilityContactMechPurpose.getString("contactMechPurposeTypeId");
            if (UtilValidate.isNotEmpty(purposeTypes) && !purposeTypes.contains(contactMechPurposeTypeId)) {
                continue;
            }
            if (results.containsKey(contactMechPurposeTypeId)) {
                //Debug.logWarning("Facility '" + facilityId + "' has two or more active contact mechs with same purpose: " + contactMechPurposeTypeId, module);
                continue;
            }

            String contactMechId = facilityContactMechPurpose.getString("contactMechId");
            // NOTE: this appears redundant, but must validate fromDate/thruDate on this too
            GenericValue facilityContactMech = EntityQuery.use(delegator).from("FacilityContactMech")
                    .where("facilityId", facilityId, "contactMechId", contactMechId)
                    .orderBy("-fromDate")
                    .cache(useCache)
                    .filterByDate(moment)
                    .queryFirst();
            if (facilityContactMech != null) {
                results.put(contactMechPurposeTypeId, facilityContactMech);
            }
        }
        return results;
    }

    /**
     * SCIPIO: Finds the latest Facility PostalAddress for each given purpose or for all purposes if not specified.
     * NOTE: This uses some optimizations, so not guaranteed to check the full validity of each record (common operations).
     * Added 2018-02-01.
     * @throws GenericEntityException
     */
    public static Map<String, GenericValue> getFacilityPostalAddressesByPurpose(Delegator delegator, String facilityId, Collection<String> purposeTypes, boolean useCache) throws GenericEntityException {
        if (UtilValidate.isEmpty(facilityId)) return null;
        Map<String, GenericValue> results = getFacilityContactMechsByPurpose(delegator, facilityId, purposeTypes, useCache);
        Collection<String> keysToRemove = null;
        for(Map.Entry<String, GenericValue> entry : results.entrySet()) {
            String contactMechId = entry.getValue().getString("contactMechId");
            GenericValue postalAddress = delegator.findOne("PostalAddress", UtilMisc.toMap("contactMechId", contactMechId), useCache);
            // NOTE: we skip POSTAL_ADDRESS contactMechTypeId check; usually redundant here
            if (postalAddress != null) {
                entry.setValue(postalAddress);
            } else {
                if (keysToRemove == null) keysToRemove = new ArrayList<>();
                keysToRemove.add(entry.getKey());
            }
        }
        if (keysToRemove != null) {
            for(String purposeType : keysToRemove) {
                results.remove(purposeType);
            }
        }
        return results;
    }

    public static void getFacilityContactMechAndRelated(ServletRequest request, String facilityId, Map<String, Object> target) {
        Delegator delegator = (Delegator) request.getAttribute("delegator");

        boolean tryEntity = true;
        if (request.getAttribute("_ERROR_MESSAGE") != null) {
            tryEntity = false;
        }
        if ("true".equals(request.getParameter("tryEntity"))) {
            tryEntity = true;
        }

        String donePage = request.getParameter("DONE_PAGE");
        if (donePage == null) {
            donePage = (String) request.getAttribute("DONE_PAGE");
        }
        if (UtilValidate.isEmpty(donePage)) {
            donePage = "viewprofile";
        }
        target.put("donePage", donePage);

        String contactMechTypeId = request.getParameter("preContactMechTypeId");

        if (contactMechTypeId == null) {
            contactMechTypeId = (String) request.getAttribute("preContactMechTypeId");
        }
        if (contactMechTypeId != null) {
            tryEntity = false;
        }

        String contactMechId = request.getParameter("contactMechId");

        if (request.getAttribute("contactMechId") != null) {
            contactMechId = (String) request.getAttribute("contactMechId");
        }

        GenericValue contactMech = null;

        if (contactMechId != null) {
            target.put("contactMechId", contactMechId);

            // try to find a PartyContactMech with a valid date range
            List<GenericValue> facilityContactMechs = null;

            try {
                facilityContactMechs = EntityQuery.use(delegator).from("FacilityContactMech")
                        .where("facilityId", facilityId, "contactMechId", contactMechId)
                        .filterByDate()
                        .queryList();
            } catch (GenericEntityException e) {
                Debug.logWarning(e, module);
            }

            GenericValue facilityContactMech = EntityUtil.getFirst(facilityContactMechs);

            if (facilityContactMech != null) {
                target.put("facilityContactMech", facilityContactMech);

                Collection<GenericValue> facilityContactMechPurposes = null;

                try {
                    facilityContactMechPurposes = EntityUtil.filterByDate(facilityContactMech.getRelated("FacilityContactMechPurpose", null, null, false), true);
                } catch (GenericEntityException e) {
                    Debug.logWarning(e, module);
                }
                if (UtilValidate.isNotEmpty(facilityContactMechPurposes)) {
                    target.put("facilityContactMechPurposes", facilityContactMechPurposes);
                }
            }

            try {
                contactMech = EntityQuery.use(delegator).from("ContactMech").where("contactMechId", contactMechId).queryOne();
            } catch (GenericEntityException e) {
                Debug.logWarning(e, module);
            }

            if (contactMech != null) {
                target.put("contactMech", contactMech);
                contactMechTypeId = contactMech.getString("contactMechTypeId");
            }
        }

        if (contactMechTypeId != null) {
            target.put("contactMechTypeId", contactMechTypeId);

            try {
                GenericValue contactMechType = EntityQuery.use(delegator).from("ContactMechType").where("contactMechTypeId", contactMechTypeId).queryOne();

                if (contactMechType != null) {
                    target.put("contactMechType", contactMechType);
                }
            } catch (GenericEntityException e) {
                Debug.logWarning(e, module);
            }

            Collection<GenericValue> purposeTypes = new LinkedList<>();
            Iterator<GenericValue> typePurposes = null;

            try {
                typePurposes = UtilMisc.toIterator(EntityQuery.use(delegator).from("ContactMechTypePurpose")
                        .where("contactMechTypeId", contactMechTypeId)
                        .queryList());
            } catch (GenericEntityException e) {
                Debug.logWarning(e, module);
            }
            while (typePurposes != null && typePurposes.hasNext()) {
                GenericValue contactMechTypePurpose = typePurposes.next();
                GenericValue contactMechPurposeType = null;

                try {
                    contactMechPurposeType = contactMechTypePurpose.getRelatedOne("ContactMechPurposeType", false);
                } catch (GenericEntityException e) {
                    Debug.logWarning(e, module);
                }
                if (contactMechPurposeType != null) {
                    purposeTypes.add(contactMechPurposeType);
                }
            }
            if (purposeTypes.size() > 0) {
                target.put("purposeTypes", purposeTypes);
            }
        }

        String requestName;

        if (contactMech == null) {
            // create
            if ("POSTAL_ADDRESS".equals(contactMechTypeId)) {
                if (request.getParameter("contactMechPurposeTypeId") != null || request.getAttribute("contactMechPurposeTypeId") != null) {
                    requestName = "createPostalAddressAndPurpose";
                } else {
                    requestName = "createPostalAddress";
                }
            } else if ("TELECOM_NUMBER".equals(contactMechTypeId)) {
                requestName = "createTelecomNumber";
            } else if ("EMAIL_ADDRESS".equals(contactMechTypeId)) {
                requestName = "createEmailAddress";
            } else {
                requestName = "createContactMech";
            }
        } else {
            // update
            if ("POSTAL_ADDRESS".equals(contactMechTypeId)) {
                requestName = "updatePostalAddress";
            } else if ("TELECOM_NUMBER".equals(contactMechTypeId)) {
                requestName = "updateTelecomNumber";
            } else if ("EMAIL_ADDRESS".equals(contactMechTypeId)) {
                requestName = "updateEmailAddress";
            } else {
                requestName = "updateContactMech";
            }
        }
        target.put("requestName", requestName);

        if ("POSTAL_ADDRESS".equals(contactMechTypeId)) {
            GenericValue postalAddress = null;

            try {
                if (contactMech != null) {
                    postalAddress = contactMech.getRelatedOne("PostalAddress", false);
                }
            } catch (GenericEntityException e) {
                Debug.logWarning(e, module);
            }
            if (postalAddress != null) {
                target.put("postalAddress", postalAddress);
            }
        } else if ("TELECOM_NUMBER".equals(contactMechTypeId)) {
            GenericValue telecomNumber = null;

            try {
                if (contactMech != null) {
                    telecomNumber = contactMech.getRelatedOne("TelecomNumber", false);
                }
            } catch (GenericEntityException e) {
                Debug.logWarning(e, module);
            }
            if (telecomNumber != null) {
                target.put("telecomNumber", telecomNumber);
            }
        }

        if ("true".equals(request.getParameter("useValues"))) {
            tryEntity = true;
        }
        target.put("tryEntity", tryEntity);

        try {
            Collection<GenericValue> contactMechTypes = EntityQuery.use(delegator).from("ContactMechType").cache(true).queryList();

            if (contactMechTypes != null) {
                target.put("contactMechTypes", contactMechTypes);
            }
        } catch (GenericEntityException e) {
            Debug.logWarning(e, module);
        }
    }

    public static List<Map<String, Object>> getPartyPostalAddresses(ServletRequest request, String partyId, String curContactMechId) {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        List<Map<String, Object>> postalAddressInfos = new LinkedList<>();

        List<GenericValue> allPartyContactMechs = null;

        try {
            allPartyContactMechs = EntityQuery.use(delegator).from("PartyContactMech").where("partyId", partyId).filterByDate().queryList();
        } catch (GenericEntityException e) {
            Debug.logWarning(e, module);
        }

        if (allPartyContactMechs == null) {
            return postalAddressInfos;
        }

        for (GenericValue partyContactMech: allPartyContactMechs) {
            GenericValue contactMech = null;

            try {
                contactMech = partyContactMech.getRelatedOne("ContactMech", false);
            } catch (GenericEntityException e) {
                Debug.logWarning(e, module);
            }
            if (contactMech != null && "POSTAL_ADDRESS".equals(contactMech.getString("contactMechTypeId")) && !contactMech.getString("contactMechId").equals(curContactMechId)) {
                Map<String, Object> postalAddressInfo = new HashMap<>();

                postalAddressInfos.add(postalAddressInfo);
                postalAddressInfo.put("contactMech", contactMech);
                postalAddressInfo.put("partyContactMech", partyContactMech);

                try {
                    GenericValue postalAddress = contactMech.getRelatedOne("PostalAddress", false);
                    postalAddressInfo.put("postalAddress", postalAddress);
                } catch (GenericEntityException e) {
                    Debug.logWarning(e, module);
                }

                try {
                    List<GenericValue> partyContactMechPurposes = EntityUtil.filterByDate(partyContactMech.getRelated("PartyContactMechPurpose", null, null, false), true);
                    postalAddressInfo.put("partyContactMechPurposes", partyContactMechPurposes);
                } catch (GenericEntityException e) {
                    Debug.logWarning(e, module);
                }
            }
        }

        return postalAddressInfos;
    }

    public static Map<String, Object> getCurrentPostalAddress(ServletRequest request, String partyId, String curContactMechId) {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        Map<String, Object> results = new HashMap<>();

        if (curContactMechId != null) {
            List<GenericValue> partyContactMechs = null;

            try {
                partyContactMechs = EntityQuery.use(delegator).from("PartyContactMech")
                        .where("partyId", partyId, "contactMechId", curContactMechId)
                        .filterByDate()
                        .queryList();
            } catch (GenericEntityException e) {
                Debug.logWarning(e, module);
            }
            GenericValue curPartyContactMech = EntityUtil.getFirst(partyContactMechs);
            results.put("curPartyContactMech", curPartyContactMech);

            GenericValue curContactMech = null;
            if (curPartyContactMech != null) {
                try {
                    curContactMech = curPartyContactMech.getRelatedOne("ContactMech", false);
                } catch (GenericEntityException e) {
                    Debug.logWarning(e, module);
                }

                Collection<GenericValue> curPartyContactMechPurposes = null;
                try {
                    curPartyContactMechPurposes = EntityUtil.filterByDate(curPartyContactMech.getRelated("PartyContactMechPurpose", null, null, false), true);
                } catch (GenericEntityException e) {
                    Debug.logWarning(e, module);
                }
                results.put("curPartyContactMechPurposes", curPartyContactMechPurposes);
            }
            results.put("curContactMech", curContactMech);

            GenericValue curPostalAddress = null;
            if (curContactMech != null) {
                try {
                    curPostalAddress = curContactMech.getRelatedOne("PostalAddress", false);
                } catch (GenericEntityException e) {
                    Debug.logWarning(e, module);
                }
            }

            results.put("curPostalAddress", curPostalAddress);
        }
        return results;
    }

    public static boolean isUspsAddress(GenericValue postalAddress) {
        if (postalAddress == null) {
            // null postal address is not a USPS address
            return false;
        }
        if (!"PostalAddress".equals(postalAddress.getEntityName())) {
            // not a postal address not a USPS address
            return false;
        }

        // get and clean the address strings
        String addr1 = postalAddress.getString("address1");
        String addr2 = postalAddress.getString("address2");

        // get the matching string from general.properties
        String matcher = EntityUtilProperties.getPropertyValue("general", "usps.address.match", postalAddress.getDelegator());
        if (UtilValidate.isNotEmpty(matcher)) {
            if (addr1 != null && addr1.toLowerCase(Locale.getDefault()).matches(matcher)) {
                return true;
            }
            if (addr2 != null && addr2.toLowerCase(Locale.getDefault()).matches(matcher)) {
                return true;
            }
        }

        return false;
    }

    public static boolean isCompanyAddress(GenericValue postalAddress, String companyPartyId) {
        if (postalAddress == null) {
            // null postal address is not an internal address
            return false;
        }
        if (!"PostalAddress".equals(postalAddress.getEntityName())) {
            // not a postal address not an internal address
            return false;
        }
        if (companyPartyId == null) {
            // no partyId not an internal address
            return false;
        }

        String state = postalAddress.getString("stateProvinceGeoId");
        String addr1 = postalAddress.getString("address1");
        String addr2 = postalAddress.getString("address2");
        if (state != null) {
            state = state.replaceAll("\\W", "").toLowerCase(Locale.getDefault());
        } else {
            state = "";
        }
        if (addr1 != null) {
            addr1 = addr1.replaceAll("\\W", "").toLowerCase(Locale.getDefault());
        } else {
            addr1 = "";
        }
        if (addr2 != null) {
            addr2 = addr2.replaceAll("\\W", "").toLowerCase(Locale.getDefault());
        } else {
            addr2 = "";
        }

        // get all company addresses
        Delegator delegator = postalAddress.getDelegator();
        List<GenericValue> postalAddresses = new LinkedList<>();
        try {
            List<GenericValue> partyContactMechs = EntityQuery.use(delegator).from("PartyContactMech")
                    .where("partyId", companyPartyId)
                    .filterByDate()
                    .queryList();
            if (partyContactMechs != null) {
                for (GenericValue pcm: partyContactMechs) {
                    GenericValue addr = pcm.getRelatedOne("PostalAddress", false);
                    if (addr != null) {
                        postalAddresses.add(addr);
                    }
                }
            }
        } catch (GenericEntityException e) {
            Debug.logError(e, "Unable to get party postal addresses", module);
        }

        for (GenericValue addr: postalAddresses) {
            String thisAddr1 = addr.getString("address1");
            String thisAddr2 = addr.getString("address2");
            String thisState = addr.getString("stateProvinceGeoId");
            if (thisState != null) {
                thisState = thisState.replaceAll("\\W", "").toLowerCase(Locale.getDefault());
            } else {
                thisState = "";
            }
            if (thisAddr1 != null) {
                thisAddr1 = thisAddr1.replaceAll("\\W", "").toLowerCase(Locale.getDefault());
            } else {
                thisAddr1 = "";
            }
            if (thisAddr2 != null) {
                thisAddr2 = thisAddr2.replaceAll("\\W", "").toLowerCase(Locale.getDefault());
            } else {
                thisAddr2 = "";
            }
            if (thisAddr1.equals(addr1) && thisAddr2.equals(addr2) && thisState.equals(state)) {
                return true;
            }
        }

        return false;
    }

    public static String getContactMechAttribute(Delegator delegator, String contactMechId, String attrName) {
        GenericValue attr = null;
        try {
            attr = EntityQuery.use(delegator).from("ContactMechAttribute").where("contactMechId", contactMechId, "attrName", attrName).queryOne();
        } catch (GenericEntityException e) {
            Debug.logError(e, module);
        }
        if (attr == null) {
            return null;
        } else {
            return attr.getString("attrValue");
        }
    }

    public static String getPostalAddressPostalCodeGeoId(GenericValue postalAddress, Delegator delegator) throws GenericEntityException {
        // if postalCodeGeoId not empty use that
        if (UtilValidate.isNotEmpty(postalAddress.getString("postalCodeGeoId"))) {
            return postalAddress.getString("postalCodeGeoId");
        }

        // no postalCodeGeoId, see if there is a Geo record matching the countryGeoId and postalCode fields
        if (UtilValidate.isNotEmpty(postalAddress.getString("countryGeoId")) && UtilValidate.isNotEmpty(postalAddress.getString("postalCode"))) {
            // first try the shortcut with the geoId convention for "{countryGeoId}-{postalCode}"
            GenericValue geo = EntityQuery.use(delegator).from("Geo").where("geoId", postalAddress.getString("countryGeoId") + "-" + postalAddress.getString("postalCode")).cache().queryOne();
            if (geo != null) {
                // save the value to the database for quicker future reference
                if (postalAddress.isMutable()) {
                    postalAddress.set("postalCodeGeoId", geo.getString("geoId"));
                    postalAddress.store();
                } else {
                    GenericValue mutablePostalAddress = EntityQuery.use(delegator).from("PostalAddress").where("contactMechId", postalAddress.getString("contactMechId")).queryOne();
                    mutablePostalAddress.set("postalCodeGeoId", geo.getString("geoId"));
                    mutablePostalAddress.store();
                }

                return geo.getString("geoId");
            }

            // no shortcut, try the longcut to see if there is something with a geoCode associated to the countryGeoId
            GenericValue geoAssocAndGeoTo = EntityQuery.use(delegator).from("GeoAssocAndGeoTo")
                    .where("geoIdFrom", postalAddress.getString("countryGeoId"), "geoCode", postalAddress.getString("postalCode"), "geoAssocTypeId", "REGIONS")
                    .cache(true)
                    .queryFirst();
            if (geoAssocAndGeoTo != null) {
                // save the value to the database for quicker future reference
                if (postalAddress.isMutable()) {
                    postalAddress.set("postalCodeGeoId", geoAssocAndGeoTo.getString("geoId"));
                    postalAddress.store();
                } else {
                    GenericValue mutablePostalAddress = EntityQuery.use(delegator).from("PostalAddress").where("contactMechId", postalAddress.getString("contactMechId")).queryOne();
                    mutablePostalAddress.set("postalCodeGeoId", geoAssocAndGeoTo.getString("geoId"));
                    mutablePostalAddress.store();
                }

                return geoAssocAndGeoTo.getString("geoId");
            }
        }

        // nothing found, return null
        return null;
    }

    /**
     * Returns a <b>PostalAddress</b> <code>GenericValue</code> as a URL encoded <code>String</code>.
     *
     * @param postalAddress A <b>PostalAddress</b> <code>GenericValue</code>.
     * @return A URL encoded <code>String</code>.
     * @throws GenericEntityException
     * @throws UnsupportedEncodingException
     */
    public static String urlEncodePostalAddress(GenericValue postalAddress) throws GenericEntityException, UnsupportedEncodingException {
        Assert.notNull("postalAddress", postalAddress);
        if (!"PostalAddress".equals(postalAddress.getEntityName())) {
            throw new IllegalArgumentException("postalAddress argument is not a PostalAddress entity");
        }
        StringBuilder sb = new StringBuilder();
        if (postalAddress.get("address1") != null) {
            sb.append(postalAddress.get("address1"));
        }
        if (postalAddress.get("address2") != null) {
            sb.append(", ").append(postalAddress.get("address2"));
        }
        if (postalAddress.get("city") != null) {
            sb.append(", ").append(postalAddress.get("city"));
        }
        if (postalAddress.get("stateProvinceGeoId") != null) {
            GenericValue geoValue = postalAddress.getRelatedOne("StateProvinceGeo", false);
            if (geoValue != null) {
                sb.append(", ").append(geoValue.get("geoName"));
            }
        } else if (postalAddress.get("countyGeoId") != null) {
            GenericValue geoValue = postalAddress.getRelatedOne("CountyGeo", false);
            if (geoValue != null) {
                sb.append(", ").append(geoValue.get("geoName"));
            }
        }
        if (postalAddress.get("postalCode") != null) {
            sb.append(", ").append(postalAddress.get("postalCode"));
        }
        if (postalAddress.get("countryGeoId") != null) {
            GenericValue geoValue = postalAddress.getRelatedOne("CountryGeo", false);
            if (geoValue != null) {
                sb.append(", ").append(geoValue.get("geoName"));
            }
        }
        String postalAddressString = sb.toString().trim();
        while (postalAddressString.contains("  ")) {
            postalAddressString = postalAddressString.replace("  ", " ");
        }
        return URLEncoder.encode(postalAddressString, "UTF-8");
    }
}

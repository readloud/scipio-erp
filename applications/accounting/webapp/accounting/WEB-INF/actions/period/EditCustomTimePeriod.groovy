/*
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
 */

import org.ofbiz.base.util.*;
import org.ofbiz.entity.*;
import org.ofbiz.entity.condition.EntityCondition
import org.ofbiz.entity.condition.EntityJoinOperator
import org.ofbiz.entity.condition.EntityOperator
import org.ofbiz.security.*;

findOrganizationPartyId = parameters.findOrganizationPartyId;
if (findOrganizationPartyId) {
    context.findOrganizationPartyId = findOrganizationPartyId;
}

currentCustomTimePeriodId = parameters.currentCustomTimePeriodId;
if (currentCustomTimePeriodId) {
    context.currentCustomTimePeriodId = currentCustomTimePeriodId;
}

currentCustomTimePeriod = currentCustomTimePeriodId ? from("CustomTimePeriod").where("customTimePeriodId", currentCustomTimePeriodId).queryOne() : null;
if (currentCustomTimePeriod) {
    context.currentCustomTimePeriod = currentCustomTimePeriod;
}

currentPeriodType = currentCustomTimePeriod ? currentCustomTimePeriod.getRelatedOne("PeriodType", true) : null;
if (currentPeriodType) {
    context.currentPeriodType = currentPeriodType;
}

// SCIPIO (11/13/2018): Filtering by company and isClosed = N
List findMapNotClosed = [];
if (findOrganizationPartyId)
    findMapNotClosed.add(EntityCondition.makeCondition("organizationPartyId", EntityOperator.EQUALS, findOrganizationPartyId));
if  (currentCustomTimePeriodId)
    findMapNotClosed.add(EntityCondition.makeCondition("parentPeriodId", EntityOperator.EQUALS, currentCustomTimePeriodId));
findMapNotClosed.add(EntityCondition.makeCondition(UtilMisc.toList(
        EntityCondition.makeCondition("isClosed", EntityOperator.EQUALS, "N"), 
        EntityCondition.makeCondition("isClosed", EntityOperator.EQUALS, null)), 
        EntityJoinOperator.OR));
customTimePeriods = from("CustomTimePeriod").where(findMapNotClosed).orderBy(["periodTypeId", "periodNum", "fromDate"]).queryList();
context.customTimePeriods = customTimePeriods;

findMap = [ : ];
if (findOrganizationPartyId) findMap.organizationPartyId = findOrganizationPartyId;
if (currentCustomTimePeriodId) findMap.parentPeriodId = currentCustomTimePeriodId;
allCustomTimePeriods = from("CustomTimePeriod").where(findMap).orderBy(["organizationPartyId", "parentPeriodId", "periodTypeId", "periodNum", "fromDate"]).queryList();
context.allCustomTimePeriods = allCustomTimePeriods;

// SCIPIO (11/13/2018): Filtering by company and closed=Y
findMap.isClosed = "Y";
allClosedCustomTimePeriods = from("CustomTimePeriod").where(findMap).orderBy(["organizationPartyId", "parentPeriodId", "periodTypeId", "periodNum", "fromDate"]).queryList();
context.allClosedCustomTimePeriods = allClosedCustomTimePeriods;

periodTypes = from("PeriodType").orderBy("description").cache(true).queryList();
context.periodTypes = periodTypes;

newPeriodTypeId = "FISCAL_YEAR";
if ("FISCAL_YEAR".equals(currentCustomTimePeriod?.periodTypeId)) {
    newPeriodTypeId = "FISCAL_QUARTER";
}
if ("FISCAL_QUARTER".equals(currentCustomTimePeriod?.periodTypeId)) {
    newPeriodTypeId = "FISCAL_MONTH";
}
if ("FISCAL_MONTH".equals(currentCustomTimePeriod?.periodTypeId)) {
    newPeriodTypeId = "FISCAL_WEEK";
}
if ("FISCAL_BIWEEK".equals(currentCustomTimePeriod?.periodTypeId)) {
    newPeriodTypeId = "FISCAL_WEEK";
}
if ("FISCAL_WEEK".equals(currentCustomTimePeriod?.periodTypeId)) {
    newPeriodTypeId = "";
}

context.newPeriodTypeId = newPeriodTypeId;

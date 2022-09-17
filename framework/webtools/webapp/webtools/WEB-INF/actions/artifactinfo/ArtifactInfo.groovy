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

import org.ofbiz.entity.Delegator;
import org.ofbiz.webtools.artifactinfo.*;
import org.ofbiz.base.util.*;

name = parameters.name;
location = parameters.location;
type = parameters.type;
uniqueId = parameters.uniqueId;
delegatorName = delegator.getDelegatorName()
if (delegatorName.contains("default#")) {
    delegatorName = "default";
}
useReloadCache = !UtilMisc.booleanValueVersatile(parameters.reloadArtifacts, false); // SCIPIO: reload flag
aif = ArtifactInfoFactory.getArtifactInfoFactory(delegatorName, useReloadCache, useReloadCache ? true : false);
context.aif = aif;
artifactInfo = null;
if ("search".equals(parameters.findType)) {
    artifactInfoSet = aif.getAllArtifactInfosByNamePartial(name, type);
    if (artifactInfoSet.size() == 1) {
        artifactInfo = artifactInfoSet.iterator().next();
        context.artifactInfo = artifactInfo;
    } else {
        context.artifactInfoSet = new TreeSet(artifactInfoSet);
    }
} else {
    if (name) {
        artifactInfo = aif.getArtifactInfoByNameAndType(name, location, type);
        context.artifactInfo = artifactInfo;
    } else if (uniqueId) {
        artifactInfo = aif.getArtifactInfoByUniqueIdAndType(uniqueId, type);
        context.artifactInfo = artifactInfo;
    }
}

recentArtifactInfoList = session.getAttribute("recentArtifactInfoList");
if (recentArtifactInfoList == null) {
    synchronized (UtilHttp.getSessionSyncObject(request)) { // SCIPIO: Atomicity
        recentArtifactInfoList = session.getAttribute("recentArtifactInfoList");
        if (recentArtifactInfoList == null) {
            recentArtifactInfoList = [];
            session.setAttribute("recentArtifactInfoList", recentArtifactInfoList);
        }
    }
}

synchronized (recentArtifactInfoList) { // SCIPIO: Thread safety
    if (artifactInfo) {
        artifactInfoMap = [type : artifactInfo.getType(), uniqueId : artifactInfo.getUniqueId(), displayName : artifactInfo.getDisplayName()];
        // add to the recently viewed list
        if (recentArtifactInfoList && recentArtifactInfoList.get(0).equals(artifactInfoMap)) {
            // hmmm, I guess do nothing if it's already there
        } else {
            // SCIPIO: Remove any existing matching before adding, to keep list from filling with useless duplicates
            for (def i = recentArtifactInfoList.iterator(); i.hasNext();) {
                if (Objects.equals(artifactInfoMap, i.next())) {
                    i.remove();
                }
            }
            recentArtifactInfoList.add(0, artifactInfoMap);
        }
    }
    // SCIPIO: Duplicate the list and limit length (previously done in screen)
    context.recentArtifactInfoList = (recentArtifactInfoList.size() > 20) ? recentArtifactInfoList.subList(0, 20) : new ArrayList(recentArtifactInfoList);
}

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
package org.ofbiz.common;

import static org.ofbiz.base.util.UtilGenerics.checkList;
import static org.ofbiz.base.util.UtilGenerics.checkMap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ThreadLocalRandom;

import javax.mail.internet.MimeMessage;

import org.ofbiz.base.metrics.Metrics;
import org.ofbiz.base.metrics.MetricsFactory;
import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.UtilCodec;
import org.ofbiz.base.util.UtilDateTime;
import org.ofbiz.base.util.UtilIO;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilProperties;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.entity.Delegator;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.condition.EntityCondition;
import org.ofbiz.entity.condition.EntityOperator;
import org.ofbiz.entity.model.ModelEntity;
import org.ofbiz.entity.transaction.TransactionUtil;
import org.ofbiz.entity.util.EntityFindOptions;
import org.ofbiz.entity.util.EntityQuery;
import org.ofbiz.service.DispatchContext;
import org.ofbiz.service.GenericServiceException;
import org.ofbiz.service.LocalDispatcher;
import org.ofbiz.service.ModelService;
import org.ofbiz.service.ServiceSynchronization;
import org.ofbiz.service.ServiceUtil;
import org.ofbiz.service.mail.MimeMessageWrapper;

/**
 * Common Services
 */
public class CommonServices {

    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());
    public static final String resource = "CommonUiLabels";

    /**
     * Generic Test Service
     *@param dctx The DispatchContext that this service is operating in
     *@param context Map containing the input parameters
     *@return Map with the result of the service, the output parameters
     */
    public static Map<String, Object> testService(DispatchContext dctx, Map<String, ?> context) {
        Map<String, Object> response = ServiceUtil.returnSuccess();

        if (context.size() > 0) {
            for (Map.Entry<String, ?> entry: context.entrySet()) {
                Object cKey = entry.getKey();
                Object value = entry.getValue();

                Debug.logInfo("---- SVC-CONTEXT: " + cKey + " => " + value, module);
            }
        }
        if (!context.containsKey("message")) {
            response.put("resp", "no message found");
        } else {
            Debug.logInfo("-----SERVICE TEST----- : " + (String) context.get("message"), module);
            response.put("resp", "service done");
        }

        Debug.logInfo("----- SVC: " + dctx.getName() + " -----", module);
        return response;
    }

    /**
     * Generic Test SOAP Service
     *@param dctx The DispatchContext that this service is operating in
     *@param context Map containing the input parameters
     *@return Map with the result of the service, the output parameters
     */
    public static Map<String, Object> testSOAPService(DispatchContext dctx, Map<String, ?> context) {
        Delegator delegator = dctx.getDelegator();
        Map<String, Object> response = ServiceUtil.returnSuccess();

        List<GenericValue> testingNodes = new LinkedList<>();
        for (int i = 0; i < 3; i ++) {
            GenericValue testingNode = delegator.makeValue("TestingNode");
            testingNode.put("testingNodeId", "TESTING_NODE" + i);
            testingNode.put("description", "Testing Node " + i);
            testingNode.put("createdStamp", UtilDateTime.nowTimestamp());
            testingNodes.add(testingNode);
        }
        response.put("testingNodes", testingNodes);
        return response;
    }

    public static Map<String, Object> blockingTestService(DispatchContext dctx, Map<String, ?> context) {
        Long duration = (Long) context.get("duration");
        if (duration == null) {
            duration = 30000l;
        }
        Debug.logInfo("-----SERVICE BLOCKING----- : " + duration/1000d +" seconds", module);
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
        }
        return CommonServices.testService(dctx, context);
    }

    public static Map<String, Object> testRollbackListener(DispatchContext dctx, Map<String, ?> context) {
        try {
            ServiceSynchronization.registerRollbackService(dctx, "testScv", null, context, false, false);
        } catch (GenericServiceException e) {
            Debug.logError(e, module);
        }
        Locale locale = (Locale) context.get("locale");
        return ServiceUtil.returnError(UtilProperties.getMessage(resource, "CommonTestRollingBack", locale));
    }

    public static Map<String, Object> testCommitListener(DispatchContext dctx, Map<String, ?> context) {
        try {
            ServiceSynchronization.registerCommitService(dctx, "testScv", null, context, false, false);
        } catch (GenericServiceException e) {
            Debug.logError(e, module);
        }
        return ServiceUtil.returnSuccess();
    }

    /**
     * Create Note Record
     *@param ctx The DispatchContext that this service is operating in
     *@param context Map containing the input parameters
     *@return Map with the result of the service, the output parameters
     */
    public static Map<String, Object> createNote(DispatchContext ctx, Map<String, ?> context) {
        Delegator delegator = ctx.getDelegator();
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        Timestamp noteDate = (Timestamp) context.get("noteDate");
        String partyId = (String) context.get("partyId");
        String noteName = (String) context.get("noteName");
        String note = (String) context.get("note");
        String noteId = delegator.getNextSeqId("NoteData");
        Locale locale = (Locale) context.get("locale");
        if (noteDate == null) {
            noteDate = UtilDateTime.nowTimestamp();
        }


        // check for a party id
        if (partyId == null) {
            if (userLogin != null && userLogin.get("partyId") != null) {
                partyId = userLogin.getString("partyId");
            }
        }

        Map<String, Object> fields = UtilMisc.toMap("noteId", noteId, "noteName", noteName, "noteInfo", note,
                "noteParty", partyId, "noteDateTime", noteDate);

        try {
            GenericValue newValue = delegator.makeValue("NoteData", fields);

            delegator.create(newValue);
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError(UtilProperties.getMessage(resource, "CommonNoteCannotBeUpdated", UtilMisc.toMap("errorString", e.getMessage()), locale));
        }
        Map<String, Object> result = ServiceUtil.returnSuccess();

        result.put("noteId", noteId);
        result.put("partyId", partyId);
        return result;
    }

    /**
     * Service for setting debugging levels.
     *@param dctc The DispatchContext that this service is operating in
     *@param context Map containing the input parameters
     *@return Map with the result of the service, the output parameters
     */
    public static Map<String, Object> adjustDebugLevels(DispatchContext dctc, Map<String, ?> context) {
        Debug.set(Debug.FATAL, "Y".equalsIgnoreCase((String) context.get("fatal")));
        Debug.set(Debug.ERROR, "Y".equalsIgnoreCase((String) context.get("error")));
        Debug.set(Debug.WARNING, "Y".equalsIgnoreCase((String) context.get("warning")));
        Debug.set(Debug.IMPORTANT, "Y".equalsIgnoreCase((String) context.get("important")));
        Debug.set(Debug.INFO, "Y".equalsIgnoreCase((String) context.get("info")));
        Debug.set(Debug.TIMING, "Y".equalsIgnoreCase((String) context.get("timing")));
        Debug.set(Debug.VERBOSE, "Y".equalsIgnoreCase((String) context.get("verbose")));
        
        // SCIPIO (2019-03-22): This serves as a way to test active logging levels after adjusting
        Debug.logFatal("Logging a fatal log", module);
        Debug.logError("Logging an error log", module);
        Debug.logWarning("Logging a warning log", module);
        Debug.logImportant("Logging an important log", module);
        Debug.logInfo("Logging an info log", module);
        Debug.logTiming("Logging an timming log", module);
        Debug.logVerbose("Logging an verbose log", module);
        Debug.log("Logging a debug log", module);

        return ServiceUtil.returnSuccess();
    }

    public static Map<String, Object> forceGc(DispatchContext dctx, Map<String, ?> context) {
        System.gc();
        return ServiceUtil.returnSuccess();
    }

    /**
     * Echo service; returns exactly what was sent.
     * This service does not have required parameters and does not validate
     */
     public static Map<String, Object> echoService(DispatchContext dctx, Map<String, ?> context) {
         Map<String, Object> result =  new LinkedHashMap<>();
         result.putAll(context);
         result.put(ModelService.RESPONSE_MESSAGE, ModelService.RESPOND_SUCCESS);
         return result;
     }

    /**
     * Return Error Service; Used for testing error handling
     */
    public static Map<String, Object> returnErrorService(DispatchContext dctx, Map<String, ?> context) {
        Locale locale = (Locale) context.get("locale");
        return ServiceUtil.returnError(UtilProperties.getMessage(resource, "CommonServiceReturnError", locale));
    }

    /**
     * Return TRUE Service; ECA Condition Service
     */
    public static Map<String, Object> conditionTrueService(DispatchContext dctx, Map<String, ?> context) {
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("conditionReply", Boolean.TRUE);
        return result;
    }

    /**
     * Return FALSE Service; ECA Condition Service
     */
    public static Map<String, Object> conditionFalseService(DispatchContext dctx, Map<String, ?> context) {
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("conditionReply", Boolean.FALSE);
        return result;
    }

    /** Cause a Referential Integrity Error */
    public static Map<String, Object> entityFailTest(DispatchContext dctx, Map<String, ?> context) {
        Delegator delegator = dctx.getDelegator();
        Locale locale = (Locale) context.get("locale");

        // attempt to create a DataSource entity w/ an invalid dataSourceTypeId
        GenericValue newEntity = delegator.makeValue("DataSource");
        newEntity.set("dataSourceId", "ENTITY_FAIL_TEST");
        newEntity.set("dataSourceTypeId", "ENTITY_FAIL_TEST");
        newEntity.set("description", "Entity Fail Test - Delete me if I am here");
        try {
            delegator.create(newEntity);
        } catch (GenericEntityException e) {
            Debug.logError(e, module);
            return ServiceUtil.returnError(UtilProperties.getMessage(resource, "CommonEntityTestFailure", locale));
        }

        return ServiceUtil.returnSuccess();
    }

    /** Test entity sorting */
    public static Map<String, Object> entitySortTest(DispatchContext dctx, Map<String, ?> context) {
        Delegator delegator = dctx.getDelegator();
        Set<ModelEntity> set = new TreeSet<>();

        set.add(delegator.getModelEntity("Person"));
        set.add(delegator.getModelEntity("PartyRole"));
        set.add(delegator.getModelEntity("Party"));
        set.add(delegator.getModelEntity("ContactMech"));
        set.add(delegator.getModelEntity("PartyContactMech"));
        set.add(delegator.getModelEntity("OrderHeader"));
        set.add(delegator.getModelEntity("OrderItem"));
        set.add(delegator.getModelEntity("OrderContactMech"));
        set.add(delegator.getModelEntity("OrderRole"));
        set.add(delegator.getModelEntity("Product"));
        set.add(delegator.getModelEntity("RoleType"));

        for (ModelEntity modelEntity: set) {
            Debug.logInfo(modelEntity.getEntityName(), module);
        }
        return ServiceUtil.returnSuccess();
    }

    public static Map<String, Object> makeALotOfVisits(DispatchContext dctx, Map<String, ?> context) {
        Delegator delegator = dctx.getDelegator();
        int count = (Integer) context.get("count");

        // SCIPIO: 2018-02-15: patch to add random userLoginId/partyId combo
        Integer randomUserCount = (Integer) context.get("randomUserCount");
        List<GenericValue> userLoginList = Collections.emptyList();
        if (randomUserCount != null && randomUserCount > 0) {
            EntityFindOptions efo = new EntityFindOptions();
            efo.setMaxRows(randomUserCount);
            EntityCondition cond = EntityCondition.makeCondition("partyId", EntityOperator.NOT_EQUAL, null);
            try {
                userLoginList = delegator.findList("UserLogin", cond, UtilMisc.toSet("userLoginId", "partyId"),
                        null, efo, false);
                userLoginList = new ArrayList<>(userLoginList);
            } catch (GenericEntityException e) {
                Debug.logError(e, "Could not get UserLogins for dummy visit creation", module);
            }
        }

        for (int i = 0; i < count; i++) {
            GenericValue v = delegator.makeValue("Visit");
            String seqId = delegator.getNextSeqId("Visit");

            v.set("visitId", seqId);
            v.set("userCreated", "N");
            v.set("sessionId", "NA-" + seqId);
            v.set("serverIpAddress", "127.0.0.1");
            v.set("serverHostName", "localhost");
            v.set("webappName", "webtools");
            v.set("initialLocale", "en_US");
            v.set("initialRequest", "https://localhost:8443/admin/control/main");
            v.set("initialReferrer", "https://localhost:8443/admin/control/main");
            v.set("initialUserAgent", "Mozilla/5.0 (Macintosh; U; PPC Mac OS X; en-us) AppleWebKit/124 (KHTML, like Gecko) Safari/125.1");
            v.set("clientIpAddress", "127.0.0.1");
            v.set("clientHostName", "localhost");
            v.set("fromDate", UtilDateTime.nowTimestamp());

            if (userLoginList.size() > 0) { // SCIPIO
                int ui = ThreadLocalRandom.current().nextInt(0, userLoginList.size());
                GenericValue randUserLogin = userLoginList.get(ui);
                v.set("userLoginId", randUserLogin.get("userLoginId"));
                v.set("partyId", randUserLogin.get("partyId"));
            }

            try {
                delegator.create(v);
            } catch (GenericEntityException e) {
                Debug.logError(e, module);
            }
        }

        return ServiceUtil.returnSuccess();
    }

    public static Map<String, Object> displayXaDebugInfo(DispatchContext dctx, Map<String, ?> context) {
        if (TransactionUtil.debugResources()) {
            if (UtilValidate.isNotEmpty(TransactionUtil.debugResMap)) {
                TransactionUtil.logRunningTx();
            } else {
                Debug.logInfo("No running transaction to display.", module);
            }
        } else {
            Debug.logInfo("Debug resources is disabled.", module);
        }

        return ServiceUtil.returnSuccess();
    }

    public static Map<String, Object> byteBufferTest(DispatchContext dctx, Map<String, ?> context) {
        ByteBuffer buffer1 = (ByteBuffer) context.get("byteBuffer1");
        ByteBuffer buffer2 = (ByteBuffer) context.get("byteBuffer2");
        String fileName1 = (String) context.get("saveAsFileName1");
        String fileName2 = (String) context.get("saveAsFileName2");
        String ofbizHome = System.getProperty("ofbiz.home");
        String outputPath1 = ofbizHome + (fileName1.startsWith("/") ? fileName1 : "/" + fileName1);
        String outputPath2 = ofbizHome + (fileName2.startsWith("/") ? fileName2 : "/" + fileName2);
        RandomAccessFile file1 = null, file2 = null;

        try {
            file1 = new RandomAccessFile(outputPath1, "rw");
            file2 = new RandomAccessFile(outputPath2, "rw");
            file1.write(buffer1.array());
            file2.write(buffer2.array());
        } catch (IOException e) {
            Debug.logError(e, module);
        } finally {
            try {
                file1.close();
                file2.close();
            } catch (Exception e) {
                Debug.logError(e, module);
            }
        }

        return ServiceUtil.returnSuccess();
    }

    public static Map<String, Object> uploadTest(DispatchContext dctx, Map<String, ?> context) {
        LocalDispatcher dispatcher = dctx.getDispatcher();
        GenericValue userLogin = (GenericValue) context.get("userLogin");

        byte[] array = (byte[]) context.get("uploadFile");
        String fileName = (String) context.get("_uploadFile_fileName");
        String contentType = (String) context.get("_uploadFile_contentType");

        Map<String, Object> createCtx =  new LinkedHashMap<>();
        createCtx.put("binData", array);
        createCtx.put("dataResourceTypeId", "OFBIZ_FILE");
        createCtx.put("dataResourceName", fileName);
        createCtx.put("dataCategoryId", "PERSONAL");
        createCtx.put("statusId", "CTNT_PUBLISHED");
        createCtx.put("mimeTypeId", contentType);
        createCtx.put("userLogin", userLogin);

        Map<String, Object> createResp = null;
        try {
            createResp = dispatcher.runSync("createFile", createCtx);
        } catch (GenericServiceException e) {
            Debug.logError(e, module);
            return ServiceUtil.returnError(e.getMessage());
        }
        if (ServiceUtil.isError(createResp)) {
            return ServiceUtil.returnError(ServiceUtil.getErrorMessage(createResp));
        }

        GenericValue dataResource = (GenericValue) createResp.get("dataResource");
        if (dataResource != null) {
            Map<String, Object> contentCtx =  new LinkedHashMap<>();
            contentCtx.put("dataResourceId", dataResource.getString("dataResourceId"));
            contentCtx.put("localeString", ((Locale) context.get("locale")).toString());
            contentCtx.put("contentTypeId", "DOCUMENT");
            contentCtx.put("mimeTypeId", contentType);
            contentCtx.put("contentName", fileName);
            contentCtx.put("statusId", "CTNT_PUBLISHED");
            contentCtx.put("userLogin", userLogin);

            Map<String, Object> contentResp = null;
            try {
                contentResp = dispatcher.runSync("createContent", contentCtx);
            } catch (GenericServiceException e) {
                Debug.logError(e, module);
                return ServiceUtil.returnError(e.getMessage());
            }
            if (ServiceUtil.isError(contentResp)) {
                return ServiceUtil.returnError(ServiceUtil.getErrorMessage(contentResp));
            }
        }

        return ServiceUtil.returnSuccess();
    }

    public static Map<String, Object> simpleMapListTest(DispatchContext dctx, Map<String, ?> context) {
        List<String> listOfStrings = checkList(context.get("listOfStrings"), String.class);
        Map<String, String> mapOfStrings = checkMap(context.get("mapOfStrings"), String.class, String.class);

        for (String str: listOfStrings) {
            String v = mapOfStrings.get(str);
            Debug.logInfo("SimpleMapListTest: " + str + " -> " + v, module);
        }

        return ServiceUtil.returnSuccess();
    }

    public static Map<String, Object> mcaTest(DispatchContext dctx, Map<String, ?> context) {
        MimeMessageWrapper wrapper = (MimeMessageWrapper) context.get("messageWrapper");
        MimeMessage message = wrapper.getMessage();
        try {
            if (message.getAllRecipients() != null) {
               Debug.logInfo("To: " + UtilMisc.toListArray(message.getAllRecipients()), module);
            }
            if (message.getFrom() != null) {
               Debug.logInfo("From: " + UtilMisc.toListArray(message.getFrom()), module);
            }
            Debug.logInfo("Subject: " + message.getSubject(), module);
            if (message.getSentDate() != null) {
                Debug.logInfo("Sent: " + message.getSentDate().toString(), module);
            }
            if (message.getReceivedDate() != null) {
                Debug.logInfo("Received: " + message.getReceivedDate().toString(), module);
            }
        } catch (Exception e) {
            Debug.logError(e, module);
        }
        return ServiceUtil.returnSuccess();
    }

    public static Map<String, Object> streamTest(DispatchContext dctx, Map<String, ?> context) {
        InputStream in = (InputStream) context.get("inputStream");
        OutputStream out = (OutputStream) context.get("outputStream");

        String line;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, UtilIO.getUtf8()));
                Writer writer = new OutputStreamWriter(out, UtilIO.getUtf8())) {
            while ((line = reader.readLine()) != null) {
                Debug.logInfo("Read line: " + line, module);
                writer.write(line);
            }
        } catch (IOException e) {
            Debug.logError(e, module);
            return ServiceUtil.returnError(e.getMessage());
        }

        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("contentType", "text/plain");
        return result;
    }

    public static Map<String, Object> ping(DispatchContext dctx, Map<String, ?> context) {
        Delegator delegator = dctx.getDelegator();
        String message = (String) context.get("message");
        Locale locale = (Locale) context.get("locale");
        if (message == null) {
            message = "PONG";
        }

        long count;
        try {
            count = EntityQuery.use(delegator).from("SequenceValueItem").queryCount();
        } catch (GenericEntityException e) {
            Debug.logError(e.getMessage(), module);
            return ServiceUtil.returnError(UtilProperties.getMessage(resource, "CommonPingDatasourceCannotConnect", locale));
        }

        if (count != 0L) {
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("message", message);
            return result;
        }
        return ServiceUtil.returnError(UtilProperties.getMessage(resource, "CommonPingDatasourceInvalidCount", locale));
    }

    public static Map<String, Object> getAllMetrics(DispatchContext dctx, Map<String, ?> context) {
        List<Map<String, Object>> metricsMapList = new LinkedList<>();
        Collection<Metrics> metricsList = MetricsFactory.getMetrics();
        for (Metrics metrics : metricsList) {
            Map<String, Object> metricsMap =  new LinkedHashMap<>();
            metricsMap.put("name", metrics.getName());
            metricsMap.put("serviceRate", metrics.getServiceRate());
            metricsMap.put("threshold", metrics.getThreshold());
            metricsMap.put("totalEvents", metrics.getTotalEvents());
            metricsMapList.add(metricsMap);
        }
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("metricsList", metricsMapList);
        return result;
    }

    public static Map<String, Object> resetMetric(DispatchContext dctx, Map<String, ?> context) {
        String originalName = (String) context.get("name");
        Locale locale = (Locale)context.get("locale");
        String name = UtilCodec.getDecoder("url").decode(originalName);
        if (name == null) {
            return ServiceUtil.returnError(UtilProperties.getMessage(resource, "CommonExceptionThrownWhileDecodingMetric", UtilMisc.toMap("originalName", originalName), locale));
        }
        Metrics metric = MetricsFactory.getMetric(name);
        if (metric != null) {
            metric.reset();
            return ServiceUtil.returnSuccess();
        }
        return ServiceUtil.returnError(UtilProperties.getMessage(resource, "CommonMetricNotFound", UtilMisc.toMap("name", name), locale));
    }
}

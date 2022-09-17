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
package org.ofbiz.service;

import java.util.Map;

import org.ofbiz.entity.Delegator;
import org.ofbiz.security.Security;
import org.ofbiz.service.jms.JmsListenerFactory;
import org.ofbiz.service.job.JobManager;

/**
 * A local service dispatcher. This is the main API for the service engine.
 * <p>Instances of <code>LocalDispatcher</code> are based on a {@link org.ofbiz.entity.Delegator}
 * instance and an entity model reader name. You can get a <code>LocalDispatcher</code> instance
 * by calling the {@link org.ofbiz.service.ServiceDispatcher#getLocalDispatcher(String, Delegator)}
 * factory method.</p>
 */
public interface LocalDispatcher {

    /**
     * Disables running of Service Engine Condition Actions (SECAs).  Intended to be turned off temporarily.
     */
    void disableEcas();

    /**
     * Reenables running of Service Engine Condition Actions (SECAs).
     */
    void enableEcas();

    /**
     * Returns whether Service Engine Condition Actions (SECAs) are disabled or not.
     * @return returns whether Service Engine Condition Actions (SECAs) are disabled or not.
     */
    boolean isEcasDisabled();

    /**
     * Run the service synchronously and return the result.
     * @param serviceName Name of the service to run.
     * @param context Map of name, value pairs composing the context.
     * @return Map of name, value pairs composing the result.
     * @throws ServiceAuthException
     * @throws ServiceValidationException
     * @throws GenericServiceException
     */
    Map<String, Object> runSync(String serviceName, Map<String, ? extends Object> context) throws GenericServiceException;

    /**
     * Run the service synchronously with a specified timeout and return the result.
     * @param serviceName Name of the service to run.
     * @param context Map of name, value pairs composing the context.
     * @param transactionTimeout the overriding timeout for the transaction (if we started it).
     * @param requireNewTransaction if true we will suspend and create a new transaction so we are sure to start.
     * @return Map of name, value pairs composing the result.
     * @throws ServiceAuthException
     * @throws ServiceValidationException
     * @throws GenericServiceException
     */
    Map<String, Object> runSync(String serviceName, Map<String, ? extends Object> context, int transactionTimeout, boolean requireNewTransaction) throws ServiceAuthException, ServiceValidationException, GenericServiceException;
    Map<String, Object> runSync(String serviceName, int transactionTimeout, boolean requireNewTransaction, Object... context) throws ServiceAuthException, ServiceValidationException, GenericServiceException;

    /**
     * SCIPIO: Run the service synchronously, with optional separate transaction.
     * Added 2019-01-30.
     * @param serviceName Name of the service to run.
     * @param context Map of name, value pairs composing the context.
     * @param transactionTimeout the overriding timeout for the transaction (if we started it).
     * @param requireNewTransaction if true we will suspend and create a new transaction so we are sure to start.
     * @return Map of name, value pairs composing the result.
     * @throws ServiceAuthException
     * @throws ServiceValidationException
     * @throws GenericServiceException
     */
    default Map<String, Object> runSync(String serviceName, Map<String, ? extends Object> context, boolean requireNewTransaction) throws ServiceAuthException, ServiceValidationException, GenericServiceException {
        return runSync(serviceName, context, -1, requireNewTransaction);
    }

    /**
     * SCIPIO: Run the service synchronously in a separate transaction.
     * Added 2019-01-30.
     * @param serviceName Name of the service to run.
     * @param context Map of name, value pairs composing the context.
     * @param transactionTimeout the overriding timeout for the transaction (if we started it).
     * @param requireNewTransaction if true we will suspend and create a new transaction so we are sure to start.
     * @return Map of name, value pairs composing the result.
     * @throws ServiceAuthException
     * @throws ServiceValidationException
     * @throws GenericServiceException
     */
    default Map<String, Object> runSyncNewTrans(String serviceName, Map<String, ? extends Object> context) throws ServiceAuthException, ServiceValidationException, GenericServiceException {
        return runSync(serviceName, context, -1, true);
    }

    /**
     * Run the service synchronously and IGNORE the result.
     * @param serviceName Name of the service to run.
     * @param context Map of name, value pairs composing the context.
     * @throws ServiceAuthException
     * @throws ServiceValidationException
     * @throws GenericServiceException
     */
    void runSyncIgnore(String serviceName, Map<String, ? extends Object> context) throws GenericServiceException;

    /**
     * Run the service synchronously with a specified timeout and IGNORE the result.
     * @param serviceName Name of the service to run.
     * @param context Map of name, value pairs composing the context.
     * @param transactionTimeout the overriding timeout for the transaction (if we started it).
     * @param requireNewTransaction if true we will suspend and create a new transaction so we are sure to start.
     * @throws ServiceAuthException
     * @throws ServiceValidationException
     * @throws GenericServiceException
     */
    void runSyncIgnore(String serviceName, Map<String, ? extends Object> context, int transactionTimeout, boolean requireNewTransaction) throws ServiceAuthException, ServiceValidationException, GenericServiceException;
    void runSyncIgnore(String serviceName, int transactionTimeout, boolean requireNewTransaction, Object... context) throws ServiceAuthException, ServiceValidationException, GenericServiceException;

    /**
     * Run the service asynchronously, passing an instance of GenericRequester that will receive the result.
     * @param serviceName Name of the service to run.
     * @param context Map of name, value pairs composing the context.
     * @param requester Object implementing GenericRequester interface which will receive the result.
     * @param persist True for store/run; False for run.
     * @param transactionTimeout the overriding timeout for the transaction (if we started it).
     * @param requireNewTransaction if true we will suspend and create a new transaction so we are sure to start.
     * @throws ServiceAuthException
     * @throws ServiceValidationException
     * @throws GenericServiceException
     */
    void runAsync(String serviceName, Map<String, ? extends Object> context, GenericRequester requester, boolean persist, int transactionTimeout, boolean requireNewTransaction) throws ServiceAuthException, ServiceValidationException, GenericServiceException;
    void runAsync(String serviceName, GenericRequester requester, boolean persist, int transactionTimeout, boolean requireNewTransaction, Object... context) throws ServiceAuthException, ServiceValidationException, GenericServiceException;

    /**
     * Run the service asynchronously, passing an instance of GenericRequester that will receive the result.
     * @param serviceName Name of the service to run.
     * @param context Map of name, value pairs composing the context.
     * @param requester Object implementing GenericRequester interface which will receive the result.
     * @param persist True for store/run; False for run.
     * @throws ServiceAuthException
     * @throws ServiceValidationException
     * @throws GenericServiceException
     */
    void runAsync(String serviceName, Map<String, ? extends Object> context, GenericRequester requester, boolean persist) throws ServiceAuthException, ServiceValidationException, GenericServiceException;
    void runAsync(String serviceName, GenericRequester requester, boolean persist, Object... context) throws ServiceAuthException, ServiceValidationException, GenericServiceException;

    /**
     * Run the service asynchronously, passing an instance of GenericRequester that will receive the result.
     * This method WILL persist the job.
     * @param serviceName Name of the service to run.
     * @param context Map of name, value pairs composing the context.
     * @param requester Object implementing GenericRequester interface which will receive the result.
     * @throws ServiceAuthException
     * @throws ServiceValidationException
     * @throws GenericServiceException
     */
    void runAsync(String serviceName, Map<String, ? extends Object> context, GenericRequester requester) throws ServiceAuthException, ServiceValidationException, GenericServiceException;
    void runAsync(String serviceName, GenericRequester requester, Object... context) throws ServiceAuthException, ServiceValidationException, GenericServiceException;

    /**
     * Run the service asynchronously and IGNORE the result.
     * @param serviceName Name of the service to run.
     * @param context Map of name, value pairs composing the context.
     * @param persist True for store/run; False for run.
     * @throws ServiceAuthException
     * @throws ServiceValidationException
     * @throws GenericServiceException
     */
    void runAsync(String serviceName, Map<String, ? extends Object> context, boolean persist) throws ServiceAuthException, ServiceValidationException, GenericServiceException;
    void runAsync(String serviceName, boolean persist, Object... context) throws ServiceAuthException, ServiceValidationException, GenericServiceException;

    /**
     * Run the service asynchronously and IGNORE the result. This method WILL persist the job.
     * @param serviceName Name of the service to run.
     * @param context Map of name, value pairs composing the context.
     * @throws ServiceAuthException
     * @throws ServiceValidationException
     * @throws GenericServiceException
     */
    void runAsync(String serviceName, Map<String, ? extends Object> context) throws ServiceAuthException, ServiceValidationException, GenericServiceException;

    /**
     * Run the service asynchronously.
     * @param serviceName Name of the service to run.
     * @param context Map of name, value pairs composing the context.
     * @param persist True for store/run; False for run.
     * @return A new GenericRequester object.
     * @throws ServiceAuthException
     * @throws ServiceValidationException
     * @throws GenericServiceException
     */
    GenericResultWaiter runAsyncWait(String serviceName, Map<String, ? extends Object> context, boolean persist) throws ServiceAuthException, ServiceValidationException, GenericServiceException;
    GenericResultWaiter runAsyncWait(String serviceName, boolean persist, Object... context) throws ServiceAuthException, ServiceValidationException, GenericServiceException;

    /**
     * Run the service asynchronously. This method WILL persist the job.
     * @param serviceName Name of the service to run.
     * @param context Map of name, value pairs composing the context.
     * @return A new GenericRequester object.
     * @throws ServiceAuthException
     * @throws ServiceValidationException
     * @throws GenericServiceException
     */
    GenericResultWaiter runAsyncWait(String serviceName, Map<String, ? extends Object> context) throws ServiceAuthException, ServiceValidationException, GenericServiceException;

    /**
     * Register a callback listener on a specific service.
     * @param serviceName Name of the service to link callback to.
     * @param cb The callback implementation.
     */
    void registerCallback(String serviceName, GenericServiceCallback cb);

    /**
     * Schedule a service to run asynchronously at a specific start time.
     * @param poolName Name of the service pool to send to.
     * @param serviceName Name of the service to invoke.
     * @param context The name/value pairs composing the context.
     * @param startTime The time to run this service.
     * @param frequency The frequency of the recurrence (RecurrenceRule.DAILY, etc).
     * @param interval The interval of the frequency recurrence.
     * @param count The number of times to repeat.
     * @param endTime The time in milliseconds the service should expire
     * @param maxRetry The number of times we should retry on failure
     * @throws ServiceAuthException
     * @throws ServiceValidationException
     * @throws GenericServiceException
     */
    void schedule(String poolName, String serviceName, Map<String, ? extends Object> context, long startTime, int frequency, int interval, int count, long endTime, int maxRetry) throws GenericServiceException;
    void schedule(String poolName, String serviceName, long startTime, int frequency, int interval, int count, long endTime, int maxRetry, Object... context) throws GenericServiceException;

    /**
     * Schedule a service to run asynchronously at a specific start time.
     * @param jobName Name of the job
     * @param poolName Name of the service pool to send to.
     * @param serviceName Name of the service to invoke.
     * @param context The name/value pairs composing the context.
     * @param startTime The time to run this service.
     * @param frequency The frequency of the recurrence (RecurrenceRule.DAILY, etc).
     * @param interval The interval of the frequency recurrence.
     * @param count The number of times to repeat.
     * @param endTime The time in milliseconds the service should expire
     * @param maxRetry The number of times we should retry on failure
     * @throws ServiceAuthException
     * @throws ServiceValidationException
     * @throws GenericServiceException
     */
    void schedule(String jobName, String poolName, String serviceName, Map<String, ? extends Object> context, long startTime, int frequency, int interval, int count, long endTime, int maxRetry) throws GenericServiceException;
    void schedule(String jobName, String poolName, String serviceName, long startTime, int frequency, int interval, int count, long endTime, int maxRetry, Object... context) throws GenericServiceException;

    /**
     * SCIPIO: Schedule a service to run asynchronously at specified event
     * @param jobName Name of the job
     * @param poolName Name of the service pool to send to.
     * @param serviceName Name of the service to invoke.
     * @param context The name/value pairs composing the context.
     * @param startTime The time to run this service.
     * @param frequency The frequency of the recurrence (RecurrenceRule.DAILY, etc).
     * @param interval The interval of the frequency recurrence.
     * @param count The number of times to repeat.
     * @param endTime The time in milliseconds the service should expire
     * @param maxRetry The number of times we should retry on failure
     * @param eventId The ID of the triggering event
     * @throws ServiceAuthException
     * @throws ServiceValidationException
     * @throws GenericServiceException
     */
    void schedule(String jobName, String poolName, String serviceName, Map<String, ? extends Object> context, long startTime, int frequency, int interval, int count, long endTime, int maxRetry, String eventId) throws GenericServiceException;

    /**
     * Schedule a service to run asynchronously at a specific start time.
     * @param serviceName Name of the service to invoke.
     * @param context The name/value pairs composing the context.
     * @param startTime The time to run this service.
     * @param frequency The frequency of the recurrence (RecurrenceRule.DAILY, etc).
     * @param interval The interval of the frequency recurrence.
     * @param count The number of times to repeat.
     * @param endTime The time in milliseconds the service should expire
     * @throws GenericServiceException
     */
    void schedule(String serviceName, Map<String, ? extends Object> context, long startTime, int frequency, int interval, int count, long endTime) throws GenericServiceException;
    void schedule(String serviceName, long startTime, int frequency, int interval, int count, long endTime, Object... context) throws GenericServiceException;

    /**
     * Schedule a service to run asynchronously at a specific start time.
     * @param serviceName Name of the service to invoke.
     * @param context The name/value pairs composing the context.
     * @param startTime The time to run this service.
     * @param frequency The frequency of the recurrence (RecurrenceRule.DAILY, etc).
     * @param interval The interval of the frequency recurrence.
     * @param count The number of times to repeat.
     * @throws GenericServiceException
     */
    void schedule(String serviceName, Map<String, ? extends Object> context, long startTime, int frequency, int interval, int count) throws GenericServiceException;
    void schedule(String serviceName, long startTime, int frequency, int interval, int count, Object... context) throws GenericServiceException;

    /**
     * Schedule a service to run asynchronously at a specific start time.
     * @param serviceName Name of the service to invoke.
     * @param context The name/value pairs composing the context.
     * @param startTime The time to run this service.
     * @param frequency The frequency of the recurrence (RecurrenceRule.DAILY, etc).
     * @param interval The interval of the frequency recurrence.
     * @param endTime The time in milliseconds the service should expire
     * @throws GenericServiceException
     */
    void schedule(String serviceName, Map<String, ? extends Object> context, long startTime, int frequency, int interval, long endTime) throws GenericServiceException;
    void schedule(String serviceName, long startTime, int frequency, int interval, long endTime, Object... context) throws GenericServiceException;

    /**
     * Schedule a service to run asynchronously at a specific start time.
     * @param serviceName Name of the service to invoke.
     * @param context The name/value pairs composing the context.
     * @param startTime The time to run this service.
     * @throws GenericServiceException
     */
    void schedule(String serviceName, Map<String, ? extends Object> context, long startTime) throws GenericServiceException;
    void schedule(String serviceName, long startTime, Object... context) throws GenericServiceException;


    /**
     * Adds a rollback service to the current TX using ServiceSynchronization
     * @param serviceName
     * @param context
     * @param persist
     * @throws GenericServiceException
     */
    void addRollbackService(String serviceName, Map<String, ? extends Object> context, boolean persist) throws GenericServiceException;
    void addRollbackService(String serviceName, boolean persist, Object... context) throws GenericServiceException;

    /**
     * Adds a commit service to the current TX using ServiceSynchronization
     * @param serviceName
     * @param context
     * @param persist
     * @throws GenericServiceException
     */
    void addCommitService(String serviceName, Map<String, ? extends Object> context, boolean persist) throws GenericServiceException;
    void addCommitService(String serviceName, boolean persist, Object... context) throws GenericServiceException;

    /**
     * SCIPIO: Returns a {@link ServiceSyncRegistrations} object that can be queried to get info about
     * the services currently registered through {@link #addCommitService} and {@link #addRollbackService}.
     * Added 2017-12-20.
     * @throws GenericServiceException
     */
    ServiceSyncRegistrations getServiceSyncRegistrations() throws GenericServiceException;

    /**
     * Gets the JobManager associated with this dispatcher
     * @return JobManager that is associated with this dispatcher
     */
    JobManager getJobManager();

    /**
     * Gets the JmsListenerFactory which holds the message listeners.
     * @return JmsListenerFactory
     */
    JmsListenerFactory getJMSListeneFactory();

    /**
     * Gets the GenericEntityDelegator associated with this dispatcher
     * @return GenericEntityDelegator associated with this dispatcher
     */
    Delegator getDelegator();


    /**
     * Gets the Security object associated with this dispatcher
     * @return Security object associated with this dispatcher
     */
    Security getSecurity();

    /**
     * Returns the Name of this local dispatcher
     * @return String representing the name of this local dispatcher
     */
    String getName();

    /**
     * Returns the DispatchContext created by this dispatcher
     * @return DispatchContext created by this dispatcher
     */
    DispatchContext getDispatchContext();

    /**
     * De-Registers this LocalDispatcher
     */
    void deregister();

    /**
     * Gets the ModelService instance that corresponds to given the name
     * SCIPIO: New method: This is the same as <code>getDispatchContext().getModelService(...)</code>. Added 2019-02-05.
     * @param serviceName Name of the service
     * @return GenericServiceModel that corresponds to the serviceName
     */
    public default ModelService getModelService(String serviceName) throws GenericServiceException {
        return getDispatchContext().getModelService(serviceName);
    }

    /**
     * Uses an existing map of name value pairs and extracts the keys which are used in serviceName
     * Note: This goes not guarantee the context will be 100% valid, there may be missing fields
     * SCIPIO: New method: This is the same as <code>getDispatchContext().makeValidContext(...)</code>. Added 2019-01-31.
     * @param serviceName The name of the service to obtain parameters for
     * @param mode The mode to use for building the new map (i.e. can be IN or OUT)
     * @param context The initial set of values to pull from
     * @return Map contains any valid values
     * @throws GenericServiceException
     */
    public default Map<String, Object> makeValidContext(String serviceName, String mode, Map<String, ? extends Object> context) throws GenericServiceException {
        return getDispatchContext().makeValidContext(serviceName, mode, context);
    }

    /**
     * Uses an existing map of name value pairs and extracts the keys which are used in serviceName
     * Note: This goes not guarantee the context will be 100% valid, there may be missing fields
     * SCIPIO: New method: This is the same as <code>DispatchContext.makeValidContext(...)</code>. Added 2019-01-31.
     * @param model The ModelService object of the service to obtain parameters for
     * @param mode The mode to use for building the new map (i.e. can be IN or OUT)
     * @param context The initial set of values to pull from
     * @return Map contains any valid values
     * @throws GenericServiceException
     */
    public default Map<String, Object> makeValidContext(ModelService model, String mode, Map<String, ? extends Object> context) throws GenericServiceException {
        // SCIPIO: NOTE: For unknown reasons, this method is static on DispatchContext, but this is not suitable
        // for LocalDispatcher and even counterproductive.
        return DispatchContext.makeValidContext(model, mode, context);
    }
}


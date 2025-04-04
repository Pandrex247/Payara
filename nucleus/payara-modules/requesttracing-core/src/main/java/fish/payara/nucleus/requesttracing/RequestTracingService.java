/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) [2016-2024] Payara Foundation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://github.com/payara/Payara/blob/main/LICENSE.txt
 * See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * The Payara Foundation designates this particular file as subject to the "Classpath"
 * exception as provided by the Payara Foundation in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package fish.payara.nucleus.requesttracing;

import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import fish.payara.internal.notification.EventLevel;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import com.sun.enterprise.config.serverbeans.Config;
import com.sun.enterprise.config.serverbeans.Domain;
import com.sun.enterprise.config.serverbeans.Server;

import org.glassfish.api.StartupRunLevel;
import org.glassfish.api.admin.ServerEnvironment;
import org.glassfish.api.event.EventListener;
import org.glassfish.api.event.EventTypes;
import org.glassfish.api.event.Events;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.api.messaging.Topic;
import org.glassfish.hk2.runlevel.RunLevel;
import org.jvnet.hk2.annotations.Optional;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.config.Changed;
import org.jvnet.hk2.config.ConfigBeanProxy;
import org.jvnet.hk2.config.ConfigListener;
import org.jvnet.hk2.config.ConfigSupport;
import org.jvnet.hk2.config.NotProcessed;
import org.jvnet.hk2.config.Transactions;
import org.jvnet.hk2.config.UnprocessedChangeEvents;

import fish.payara.internal.notification.PayaraNotification;
import fish.payara.internal.notification.PayaraNotificationFactory;
import fish.payara.internal.notification.TimeUtil;
import fish.payara.monitoring.collect.MonitoringData;
import fish.payara.monitoring.collect.MonitoringDataCollector;
import fish.payara.monitoring.collect.MonitoringDataSource;
import fish.payara.monitoring.collect.MonitoringWatchCollector;
import fish.payara.monitoring.collect.MonitoringWatchSource;
import fish.payara.notification.requesttracing.EventType;
import fish.payara.notification.requesttracing.RequestTrace;
import fish.payara.notification.requesttracing.RequestTraceSpan;
import fish.payara.notification.requesttracing.RequestTraceSpanLog;
import fish.payara.notification.requesttracing.RequestTracingNotificationData;
import fish.payara.nucleus.config.ClusteredConfig;
import fish.payara.nucleus.eventbus.ClusterMessage;
import fish.payara.nucleus.eventbus.EventBus;
import fish.payara.nucleus.events.HazelcastEvents;
import fish.payara.nucleus.executorservice.PayaraExecutorService;
import fish.payara.nucleus.hazelcast.HazelcastCore;
import fish.payara.nucleus.requesttracing.configuration.RequestTracingServiceConfiguration;
import fish.payara.nucleus.requesttracing.domain.execoptions.RequestTracingExecutionOptions;
import fish.payara.nucleus.requesttracing.events.RequestTracingEvents;
import fish.payara.nucleus.requesttracing.sampling.AdaptiveSampleFilter;
import fish.payara.nucleus.requesttracing.sampling.SampleFilter;
import fish.payara.nucleus.requesttracing.store.RequestTraceStoreFactory;
import fish.payara.nucleus.requesttracing.store.RequestTraceStoreInterface;
import io.opentracing.tag.Tag;

/**
 * Main service class that provides methods used by interceptors for tracing
 * requests.
 *
 * @author mertcaliskan
 * @since 4.1.1.163
 */
@Service(name = "requesttracing-service")
@RunLevel(StartupRunLevel.VAL)
public class RequestTracingService implements EventListener, ConfigListener, MonitoringDataSource, MonitoringWatchSource {


    private static final Logger logger = Logger.getLogger(RequestTracingService.class.getCanonicalName());

    private static final String DURATION = "Duration";
    public static final String EVENT_BUS_LISTENER_NAME = "RequestTracingEvents";

    private static final int SECOND = 1;
    private static final int MINUTE = 60 * SECOND;
    private static final int HOUR = 60 * MINUTE;

    @Inject
    @Named(ServerEnvironment.DEFAULT_INSTANCE_NAME)
    @Optional
    RequestTracingServiceConfiguration configuration;

    @Inject
    private Events events;
    
    @Inject
    private EventBus eventBus;

    @Inject
    private Domain domain;

    @Inject
    private Server server;

    @Inject
    ServerEnvironment env;

    @Inject
    Transactions transactions;

    @Inject
    private ServiceLocator habitat;

    @Inject
    private Topic<PayaraNotification> notificationEventBus;

    @Inject
    private PayaraNotificationFactory notificationFactory;

    @Inject
    RequestTraceSpanStore requestEventStore;

    @Inject
    private HazelcastCore hazelcast;

    @Inject
    private ClusteredConfig clusteredConfig;

    @Inject
    private PayaraExecutorService payaraExecutorService;

    private RequestTracingExecutionOptions executionOptions = new RequestTracingExecutionOptions();

    private RequestTraceStoreInterface historicRequestTraceStore;
    private RequestTraceStoreInterface requestTraceStore;

    /**
     * Hold the last not yet collected traces. The size of the queue is limited by removing oldest element in case it
     * gets larger than a fixed limit before adding a new trace to the queue.
     */
    private final ConcurrentLinkedQueue<RequestTrace> uncollectedTraces = new ConcurrentLinkedQueue<>();
    private final Map<String, Integer> activeCollectionGroups = new ConcurrentHashMap<>();

    /**
     * The filter which determines whether to sample a given request
     */
    private SampleFilter sampleFilter;

    @PostConstruct
    void postConstruct() {
        events.register(this);
        configuration = habitat.getService(RequestTracingServiceConfiguration.class);
        payaraExecutorService = habitat.getService(PayaraExecutorService.class);
    }

    @Override
    public void event(Event event) {
        // If Hazelcast is enabled, wait for it, otherwise just bootstrap when the server is ready
        if (hazelcast.isEnabled()) {
            if (event.is(HazelcastEvents.HAZELCAST_BOOTSTRAP_COMPLETE)) {
                bootstrapRequestTracingService();
            }
        } else {
            if (event.is(EventTypes.SERVER_READY)) {
                bootstrapRequestTracingService();
            }
        }
        
        // If Hazelcast has shutdown, reinitialise request tracing
        if (event.is(HazelcastEvents.HAZELCAST_SHUTDOWN_COMPLETE)) {
            bootstrapRequestTracingService();
        }

        transactions.addListenerForType(RequestTracingServiceConfiguration.class, this);
    }

    /**
     * Starts the request tracing service
     * @since 4.1.1.171
     */
    public void bootstrapRequestTracingService() {
        if (configuration != null) {
            executionOptions.setEnabled(Boolean.parseBoolean(configuration.getEnabled()));
            
            executionOptions.setSampleRate(Double.valueOf(configuration.getSampleRate()));
            executionOptions.setAdaptiveSamplingEnabled(Boolean.parseBoolean(configuration.getAdaptiveSamplingEnabled()));
            executionOptions.setAdaptiveSamplingTargetCount(Integer.valueOf(configuration.getAdaptiveSamplingTargetCount()));
            executionOptions.setAdaptiveSamplingTimeValue(Integer.valueOf(configuration.getAdaptiveSamplingTimeValue()));
            executionOptions.setAdaptiveSamplingTimeUnit(TimeUnit.valueOf(configuration.getAdaptiveSamplingTimeUnit()));
            
            executionOptions.setApplicationsOnlyEnabled(Boolean.parseBoolean(configuration.getApplicationsOnlyEnabled()));
            executionOptions.setThresholdUnit(TimeUnit.valueOf(configuration.getThresholdUnit()));
            executionOptions.setThresholdValue(Long.parseLong(configuration.getThresholdValue()));
            executionOptions.setSampleRateFirstEnabled(Boolean.parseBoolean(configuration.getSampleRateFirstEnabled()));
            
            executionOptions.setTraceStoreSize(Integer.parseInt(configuration.getTraceStoreSize()));
            executionOptions.setTraceStoreTimeout(TimeUtil.setStoreTimeLimit(configuration.getTraceStoreTimeout()));
            executionOptions.setReservoirSamplingEnabled(Boolean.parseBoolean(configuration.getReservoirSamplingEnabled()));
            
            executionOptions.setHistoricTraceStoreEnabled(Boolean.parseBoolean(configuration.getHistoricTraceStoreEnabled()));
            executionOptions.setHistoricTraceStoreSize(Integer.parseInt(configuration.getHistoricTraceStoreSize()));
            executionOptions.setHistoricTraceStoreTimeout(TimeUtil.setStoreTimeLimit(configuration.getHistoricTraceStoreTimeout()));

            bootstrapNotifierList();
        }

        if (executionOptions != null && executionOptions.isEnabled()) {
            if (executionOptions.getAdaptiveSamplingEnabled()) {
                sampleFilter = new AdaptiveSampleFilter(executionOptions.getSampleRate(), executionOptions.getAdaptiveSamplingTargetCount(),
                        executionOptions.getAdaptiveSamplingTimeValue(), executionOptions.getAdaptiveSamplingTimeUnit());
            } else {
                sampleFilter = new SampleFilter(executionOptions.getSampleRate());
            }

            // Set up the historic request trace store if enabled
            if (executionOptions.isHistoricTraceStoreEnabled()) {
                historicRequestTraceStore = RequestTraceStoreFactory.getStore(executionOptions.getReservoirSamplingEnabled(), true);
                initStoreSize(historicRequestTraceStore, executionOptions::getHistoricTraceStoreSize, "historicRequestTraceStoreSize");


                // Disable cleanup task if it's null, less than 0, or reservoir sampling is enabled
                if (executionOptions.getTraceStoreTimeout() != null 
                        && executionOptions.getTraceStoreTimeout() > 0 
                        && !executionOptions.getReservoirSamplingEnabled()) {
                    // if timeout is bigger than 5 minutes execute the cleaner task in 5 minutes periods,
                    // if not use timeout value as period
                    long period = executionOptions.getTraceStoreTimeout() > TimeUtil.CLEANUP_TASK_FIVE_MIN_PERIOD
                            ? TimeUtil.CLEANUP_TASK_FIVE_MIN_PERIOD : executionOptions.getTraceStoreTimeout();
                    payaraExecutorService.scheduleAtFixedRate(new RequestTraceStoreCleanupTask(
                            executionOptions.getTraceStoreTimeout(), historicRequestTraceStore), 
                            0, period, TimeUnit.SECONDS);
                }
            }

            // Set up the general request trace store
            requestTraceStore = RequestTraceStoreFactory.getStore(executionOptions.getReservoirSamplingEnabled(), false);
            initStoreSize(requestTraceStore, executionOptions::getTraceStoreSize, "requestTraceStoreSize");

            // Disable cleanup task if it's null, less than 0, or reservoir sampling is enabled
            if (executionOptions.getTraceStoreTimeout() != null && executionOptions.getTraceStoreTimeout() > 0 
                    && !executionOptions.getReservoirSamplingEnabled()) {
                // if timeout is bigger than 5 minutes execute the cleaner task in 5 minutes periods,
                // if not use timeout value as period
                long period = executionOptions.getTraceStoreTimeout() > TimeUtil.CLEANUP_TASK_FIVE_MIN_PERIOD
                        ? TimeUtil.CLEANUP_TASK_FIVE_MIN_PERIOD : executionOptions.getTraceStoreTimeout();
                payaraExecutorService.scheduleAtFixedRate(new RequestTraceStoreCleanupTask(
                        executionOptions.getTraceStoreTimeout(), requestTraceStore), 
                        0, period, TimeUnit.SECONDS);
            }
            
            logger.log(Level.INFO, "Payara Request Tracing Service Started with configuration: {0}", executionOptions);
        } else {
            clusteredConfig.clearSharedConfiguration("requestTraceStoreSize");
            clusteredConfig.clearSharedConfiguration("historicRequestTraceStore");
        }
    }

    private void initStoreSize(RequestTraceStoreInterface store, IntSupplier size, String clusteredConfigProperty) {
        if (store.isShared()) {
            store.setSize(() -> clusteredConfig.getSharedConfiguration(clusteredConfigProperty, size.getAsInt(), Integer::max));
        } else {
            clusteredConfig.clearSharedConfiguration(clusteredConfigProperty);
            store.setSize(size);
        }
    }

    /**
     * Configures notifiers with request tracing and starts any enabled ones.
     * If no options are set then the log notifier is automatically turned on.
     * @since 4.1.2.173
     */
    public void bootstrapNotifierList() {
        executionOptions.clearNotifiers();
        if (configuration.getNotifierList() != null) {
            configuration.getNotifierList().forEach(executionOptions::enableNotifier);
        }
    }

    /**
     * Retrieves the current Conversation ID
     * @return
     */
    public UUID getConversationID() {
        return requestEventStore.getTraceID();
    }
    
    public UUID getStartingTraceID() {
        return requestEventStore.getTrace().getTraceSpans().getFirst().getId();
    }

    /**
     * Reset the conversation ID This is especially useful for trace propagation
     * across threads when the event tracer can receive the conversation ID
     * propagated to it
     *
     * @param newID
     */
    public void setTraceId(UUID newID) {
        requestEventStore.setTraceId(newID);
    }

    /**
     * Returns true if a trace has started and not yet completed. NOTE: This only applies to traces started using the
     * request tracing service; traces started using OpenTracing *MAY* not be picked up by this (for example, 
     * if you're using the OpenTracing MockTracer instead of the in-built one).
     * @return 
     */
    public boolean isTraceInProgress() {
        return requestEventStore.isTraceInProgress();
    }

    /**
     * Starts a new request trace
     * @return a unique identifier for the request trace
     */
    public RequestTraceSpan startTrace(String traceName) {
        return startTrace(new RequestTraceSpan(EventType.TRACE_START, traceName));
    }

    public RequestTraceSpan startTrace(RequestTraceSpan span) {
        if (shouldStartTrace()) {
            span.addSpanTag("Server", server.getName());
            span.addSpanTag("Domain", domain.getName());
            requestEventStore.storeEvent(span);
            return span;
        } else {
            return null;
        }
    }

    public RequestTraceSpan startTrace(RequestTraceSpan span, long timestampMillis) {
        if (shouldStartTrace()) {
            span.addSpanTag("Server", server.getName());
            span.addSpanTag("Domain", domain.getName());
            requestEventStore.storeEvent(span, timestampMillis);
            return span;
        } else {
            return null;
        }
    }

    public RequestTraceSpan startTrace(UUID propagatedTraceId, UUID propagatedParentId,
            RequestTraceSpan.SpanContextRelationshipType propagatedRelationshipType, String traceName) {
        if (!isRequestTracingEnabled()) {
            return null;
        }
        RequestTraceSpan span = new RequestTraceSpan(EventType.PROPAGATED_TRACE, traceName,
                propagatedTraceId, propagatedParentId, propagatedRelationshipType);
        span.addSpanTag("Server", server.getName());
        span.addSpanTag("Domain", domain.getName());
        requestEventStore.storeEvent(span);
        return span;
    }

    /**
     * Adds a new event to the request trace currently in progress
     * @param requestEvent 
     */
    public void traceSpan(RequestTraceSpan requestEvent) {
        if (isRequestTracingEnabled() && isTraceInProgress()) {
            traceOrEnd(requestEvent);
        }
    }

    public void traceSpan(RequestTraceSpan requestEvent, long timestampMillis) {
        if (isRequestTracingEnabled() && isTraceInProgress()) {
            traceOrEnd(requestEvent, timestampMillis);
        }
    }

    private void traceOrEnd(RequestTraceSpan requestEvent) {
        // If the span is the same one that started the trace, finish it
        if (spanIsRootSpan(requestEvent)) {
            endTrace();
        } else {
            requestEventStore.storeEvent(requestEvent);
        }
    }

    private void traceOrEnd(RequestTraceSpan requestEvent, long timestampMillis) {
        if (spanIsRootSpan(requestEvent)) {
            endTrace(timestampMillis);
        } else {
            requestEventStore.storeEvent(requestEvent, timestampMillis);
        }
    }

    private boolean spanIsRootSpan(RequestTraceSpan requestEvent) {
        return !requestEventStore.getTrace().getTraceSpans().isEmpty()
                && requestEventStore.getTrace().getTraceSpans().getFirst().equals(requestEvent);
    }

    public  boolean shouldStartTrace() {
        if (!isRequestTracingEnabled()) {
            return false;
        }

        // Check if the trace came from an admin listener. If it did, and 'applications only' is enabled, ignore the trace.
        if (executionOptions.getApplicationsOnlyEnabled() == true
                && Thread.currentThread().getName().matches("admin-thread-pool::admin-listener\\([0-9]+\\)")) {
            return false;
        }

        // Determine whether to sample the request, if sampleRateFirstEnabled is true
        if (executionOptions.getSampleRateFirstEnabled() && !sampleFilter.sample()) {
            return false;
        }

        return true;
    }

    /**
     * 
     */
    public void endTrace() {
        if (!isRequestTracingEnabled() || !isTraceInProgress()) {
            return;
        }
        requestEventStore.endTrace();
        processTraceEnd();
    }

    public void endTrace(long timestampMillis) {
        if (!isRequestTracingEnabled() || !isTraceInProgress()) {
            return;
        }
        requestEventStore.endTrace(timestampMillis);
        processTraceEnd();
    }


    public void processSpan(RequestTraceSpan finishedSpan) {
        if (!isRequestTracingEnabled()) {
            return;
        }
        requestEventStore.storeEvent(finishedSpan, 0);
        requestEventStore.endTrace(finishedSpan.getTimeOccured()+finishedSpan.getSpanDuration());
    }

    private void processTraceEnd() {
        Long thresholdValueInNanos = getThresholdValueInNanos();

        long elapsedTime = requestEventStore.getElapsedTime();
        long elapsedTimeInNanos = TimeUnit.NANOSECONDS.convert(elapsedTime, TimeUnit.MILLISECONDS);
        if (elapsedTimeInNanos - thresholdValueInNanos > 0) {
            // Determine whether to sample the request, if sampleRateFirstEnabled is false
            if (!executionOptions.getSampleRateFirstEnabled()) {
                if (!sampleFilter.sample()) {
                    requestEventStore.flushStore();
                    return;
                }
            }
            // collect any trace exceeding the threshold
            if (uncollectedTraces.size() >= 50) {
                uncollectedTraces.poll(); // avoid queue creating a memory leak by accumulating entries in case no consumer polls them
            }
            RequestTrace requestTrace = requestEventStore.getTrace();
            uncollectedTraces.add(requestTrace);

            Runnable addTask = () -> {
                RequestTrace removedTrace = requestTraceStore.addTrace(requestTrace);

                // Store the trace in the historic trace store if it's enabled, avoiding recalculation
                if (executionOptions.isHistoricTraceStoreEnabled()) {
                    historicRequestTraceStore.addTrace(requestTrace, removedTrace);
                }

                if (removedTrace != null) {
                    if (hazelcast.isEnabled()) {
                        eventBus.publish(EVENT_BUS_LISTENER_NAME, new ClusterMessage(
                                RequestTracingEvents.STORE_FULL.toString()));
                    } else {
                        events.send(new EventListener.Event(RequestTracingEvents.STORE_FULL));
                    }
                }
            };

            payaraExecutorService.submit(addTask);

            Collection<String> enabledNotifiers = getExecutionOptions().getEnabledNotifiers();
            PayaraNotification notification = notificationFactory.newBuilder()
                .whitelist(enabledNotifiers.toArray(new String[0]))
                .subject("Request execution time: " + elapsedTime + "(ms) exceeded the acceptable threshold")
                .message(requestTrace.toString())
                .data(new RequestTracingNotificationData(requestTrace))
                .level(EventLevel.WARNING)
                .build();
            notificationEventBus.publish(notification);
        }
        requestEventStore.flushStore();
    }

    public void addSpanLog(RequestTraceSpanLog spanLog) {
        if (!isRequestTracingEnabled() || !isTraceInProgress()) {
            return;
        }
        
        requestEventStore.getTrace().addSpanLog(spanLog);
    }
    
    /**
     *
     * @return 
     * @since 4.1.1.164
     */
    public Long getThresholdValueInNanos() {
        if (executionOptions != null) {
            return TimeUnit.NANOSECONDS.convert(executionOptions.getThresholdValue(),
                    executionOptions.getThresholdUnit());
        }
        return null;
    }

    public boolean isRequestTracingEnabled() {
        return executionOptions != null && executionOptions.isEnabled();
    }
    
    public RequestTracingExecutionOptions getExecutionOptions() {
        return executionOptions;
    }

    @Override
    public UnprocessedChangeEvents changed(PropertyChangeEvent[] events) {
        boolean isCurrentInstanceMatchTarget = false;
        if (env.isInstance()) {
            isCurrentInstanceMatchTarget = true;
        } else {
            for (PropertyChangeEvent pe : events) {
                ConfigBeanProxy proxy = (ConfigBeanProxy) pe.getSource();
                while (proxy != null && !(proxy instanceof Config)) {
                    proxy = proxy.getParent();
                }

                if (proxy != null && ((Config) proxy).isDas()) {
                    isCurrentInstanceMatchTarget = true;
                    break;
                }
            }
        }

        if (isCurrentInstanceMatchTarget) {
            return ConfigSupport.sortAndDispatch(events, new Changed() {

                @Override
                public <T extends ConfigBeanProxy> NotProcessed changed(TYPE type, Class<T> changedType, T changedInstance) {

                    if (changedType.equals(RequestTracingServiceConfiguration.class)) {
                        configuration = (RequestTracingServiceConfiguration) changedInstance;
                    }
                    return null;
                }
            }, logger);
        }
        return null;
    }
    
    /**
     * Returns the RequestTraceStore used for storing historical traces
     * @return 
     */
    public RequestTraceStoreInterface getHistoricRequestTraceStore() {
        return historicRequestTraceStore;
    }
    
    /**
     * Returns the RequestTraceStore used for storing traces
     * @return 
     */
    public RequestTraceStoreInterface getRequestTraceStore() {
        return requestTraceStore;
    }

    @Override
    public void collect(MonitoringWatchCollector collector) {
        if ("true".equals(configuration.getEnabled())) {
            long thresholdMillis = getConfigurationThresholdInMillis();
            collector.watch("ns:trace @:* Duration", "Request Trace Duration", "ms")
                .amber(thresholdMillis, -30, false, null, null, false)
                .red(thresholdMillis, 10, true, null, null, false)
                .green(-(thresholdMillis/2), 1, false, null, null, false);
        }
    }

    private long getConfigurationThresholdInMillis() {
        return TimeUnit.MILLISECONDS.convert(Long.parseLong(configuration.getThresholdValue()), 
                TimeUnit.valueOf(configuration.getThresholdUnit()));
    }

    @Override
    @MonitoringData(ns = "trace")
    public void collect(MonitoringDataCollector collector) {
        for (String group : activeCollectionGroups.keySet()) {
            collector.group(group).collect(DURATION, 0);
            activeCollectionGroups.compute(group, (key, value) -> value <= 1 ? null : value - 1);
        }
        long thresholdInMillis = getConfigurationThresholdInMillis();
        RequestTrace trace = uncollectedTraces.poll();
        while (trace != null) {
            String group = collectTrace(collector, trace, thresholdInMillis);
            if (group != null) {
                activeCollectionGroups.compute(group, (key, value) -> 35);
            }
            trace = uncollectedTraces.poll();
        }
    }


    private static String collectTrace(MonitoringDataCollector tracingCollector, RequestTrace trace, long threshold) {
        try {
            UUID traceId = trace.getTraceId();
            if (traceId != null) {
                String group = metricGroupName(trace);
                long durationMillis = trace.getTraceSpans().getFirst().getSpanDuration() / 1000000;
                RequestTraceSpan annotationSpan = trace.getTraceSpans().getLast();
                List<String> attrs = new ArrayList<>();
                attrs.add("Threshold");
                attrs.add(String.valueOf(threshold));
                attrs.add("Operation");
                attrs.add(annotationSpan.getEventName());
                attrs.add("Start");
                attrs.add(String.valueOf(annotationSpan.getStartInstant().toEpochMilli()));
                attrs.add("End");
                attrs.add(String.valueOf(annotationSpan.getTraceEndTime().toEpochMilli()));
                for (Entry<Object, String> tag : annotationSpan.getSpanTags().entrySet()) {
                    if (tag.getKey() instanceof Tag) {
                        attrs.add(((Tag) tag.getKey()).getKey());
                    } else {
                        attrs.add(tag.getKey().toString());
                    }
                    attrs.add(tag.getValue());
                }
                tracingCollector.group(group)
                    .collect(DURATION, durationMillis)
                    .annotate(DURATION, durationMillis, attrs.toArray(new String[0]));
                return group;
            }
        } catch (Exception ex) {
            logger.log(Level.FINE, "Failed to collect trace", ex);
        }
        return null;
    }

    public static String metricGroupName(RequestTrace trace) {
        return stripPackageName(trace.getTraceSpans().getLast().getEventName());
    }

    public static String stripPackageName(String eventName) {
        int javaMethodDot = eventName.lastIndexOf('.');
        int httpMethodDivider = eventName.indexOf(':');
        return javaMethodDot < 0 || httpMethodDivider < 0
                ? eventName
                : eventName.substring(0, httpMethodDivider) + "_"
                        + eventName.substring(eventName.lastIndexOf('.', javaMethodDot - 1) + 1);
    }
}

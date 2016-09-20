/**
 * Graylog Internal Logs Plugin - Internal Logs plugin for Graylog
 * Copyright © 2016 Graylog, Inc. (hello@graylog.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.graylog.plugins.internallogs.codec;

import com.google.inject.assistedinject.Assisted;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.ThrowableProxy;
import org.apache.logging.log4j.core.net.Severity;
import org.graylog2.cluster.Node;
import org.graylog2.cluster.NodeNotFoundException;
import org.graylog2.cluster.NodeService;
import org.graylog2.plugin.Message;
import org.graylog2.plugin.cluster.ClusterConfigService;
import org.graylog2.plugin.cluster.ClusterId;
import org.graylog2.plugin.configuration.Configuration;
import org.graylog2.plugin.configuration.ConfigurationRequest;
import org.graylog2.plugin.configuration.fields.BooleanField;
import org.graylog2.plugin.inputs.annotations.Codec;
import org.graylog2.plugin.inputs.annotations.ConfigClass;
import org.graylog2.plugin.inputs.annotations.FactoryClass;
import org.graylog2.plugin.inputs.codecs.AbstractCodec;
import org.graylog2.plugin.journal.RawMessage;
import org.graylog2.plugin.system.NodeId;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.util.List;
import java.util.Map;

@Codec(name = "serialized-logevent", displayName = "Serialized LogEvent")
public class SerializedLogEventCodec extends AbstractCodec {
    private static final Logger LOG = LoggerFactory.getLogger(SerializedLogEventCodec.class);

    private static final String CK_INCLUDE_SOURCE = "include_source";
    private static final String CK_INCLUDE_THREAD_CONTEXT = "include_thread_context";
    private static final String CK_INCLUDE_STACK_TRACE = "include_stack_trace";
    private static final String CK_INCLUDE_EXCEPTION_CAUSE = "include_exception_cause";

    private final boolean includeSource;
    private final boolean includeThreadContext;
    private final boolean includeStackTrace;
    private final boolean includeExceptionCause;

    private final String clusterId;
    private final String nodeId;
    private final String hostname;

    @Inject
    public SerializedLogEventCodec(@Assisted Configuration configuration,
                                   NodeId nodeId,
                                   NodeService nodeService,
                                   ClusterConfigService clusterConfigService) {
        super(configuration);
        this.includeSource = configuration.getBoolean(CK_INCLUDE_SOURCE, true);
        this.includeThreadContext = configuration.getBoolean(CK_INCLUDE_THREAD_CONTEXT, true);
        this.includeStackTrace = configuration.getBoolean(CK_INCLUDE_STACK_TRACE, true);
        this.includeExceptionCause = configuration.getBoolean(CK_INCLUDE_EXCEPTION_CAUSE, true);

        final ClusterId clusterIdBean = clusterConfigService.get(ClusterId.class);
        this.clusterId = clusterIdBean == null ? null : clusterIdBean.clusterId();

        this.nodeId = nodeId.toString();
        String nodeHostname;
        try {
            final Node node = nodeService.byNodeId(nodeId);
            nodeHostname = node.getHostname();
        } catch (NodeNotFoundException e) {
            nodeHostname = null;
        }
        this.hostname = nodeHostname;
    }

    @Nullable
    @Override
    public Message decode(@Nonnull RawMessage rawMessage) {
        try (final ByteArrayInputStream inputStream = new ByteArrayInputStream(rawMessage.getPayload());
             final ObjectInputStream objectInputStream = new ObjectInputStream(inputStream)) {
            final LogEvent logEvent = (LogEvent) objectInputStream.readObject();
            return processLogEvent(logEvent);
        } catch (Exception e) {
            LOG.error("Couldn't deserialize log event", e);
            return null;
        }
    }

    private Message processLogEvent(LogEvent logEvent) {
        final String formattedMessage = logEvent.getMessage().getFormattedMessage();
        final DateTime timestamp = new DateTime(logEvent.getTimeMillis(), DateTimeZone.UTC);
        final Message message = new Message(formattedMessage, hostname, timestamp);

        final Level level = logEvent.getLevel();
        message.addField(Message.FIELD_LEVEL, Severity.getSeverity(level).getCode());
        message.addField("log4j_level", level.name());
        message.addField("log4j_level_int", level.intLevel());

        message.addField("node_id", nodeId);
        if (clusterId != null) {
            message.addField("cluster_id", clusterId);
        }
        message.addField("logger_name", logEvent.getLoggerName());
        message.addField("thread_id", logEvent.getThreadId());
        message.addField("thread_name", logEvent.getThreadName());
        message.addField("thread_priority", logEvent.getThreadPriority());
        message.addField("timestamp_nanos", logEvent.getNanoTime());

        final Marker marker = logEvent.getMarker();
        if (marker != null) {
            message.addField("marker", marker.getName());
        }

        if (includeThreadContext) {
            for (Map.Entry<String, String> entry : logEvent.getContextMap().entrySet()) {
                message.addField("context_" + entry.getKey(), entry.getValue());
            }

            // Guard against https://issues.apache.org/jira/browse/LOG4J2-1530
            final ThreadContext.ContextStack contextStack = logEvent.getContextStack();
            if (contextStack != null) {
                final List<String> contextStackItems = contextStack.asList();
                if (contextStackItems != null && !contextStackItems.isEmpty()) {
                    message.addField("context_stack", contextStackItems);
                }
            }
        }

        if (includeSource) {
            final StackTraceElement source = logEvent.getSource();
            if (source != null) {
                message.addField("source_file_name", source.getFileName());
                message.addField("source_method_name", source.getMethodName());
                message.addField("source_class_name", source.getClassName());
                message.addField("source_line_number", source.getLineNumber());
            }
        }

        final ThrowableProxy throwableProxy = logEvent.getThrownProxy();
        if (includeStackTrace && throwableProxy != null) {
            final String stackTrace;
            if (includeExceptionCause) {
                stackTrace = throwableProxy.getExtendedStackTraceAsString();
            } else {
                stackTrace = throwableProxy.getCauseStackTraceAsString();
            }

            message.addField("exception_class", throwableProxy.getName());
            message.addField("exception_message", throwableProxy.getMessage());
            message.addField("exception_stack_trace", stackTrace);
        }

        return message;
    }

    @FactoryClass
    public interface Factory extends AbstractCodec.Factory<SerializedLogEventCodec> {
        @Override
        SerializedLogEventCodec create(Configuration configuration);

        @Override
        SerializedLogEventCodec.Config getConfig();

        @Override
        SerializedLogEventCodec.Descriptor getDescriptor();
    }

    @ConfigClass
    public static class Config extends AbstractCodec.Config {
        @Override
        public ConfigurationRequest getRequestedConfiguration() {
            final ConfigurationRequest requestedConfiguration = super.getRequestedConfiguration();
            requestedConfiguration.addField(new BooleanField(
                    CK_INCLUDE_SOURCE,
                    "Include source information",
                    true,
                    "Whether to include source information (package, class, line number)."));
            requestedConfiguration.addField(new BooleanField(
                    CK_INCLUDE_THREAD_CONTEXT,
                    "Include thread context",
                    true,
                    "Whether to include the contents of the thread context."));
            requestedConfiguration.addField(new BooleanField(
                    CK_INCLUDE_STACK_TRACE,
                    "Include stack traces",
                    true,
                    "Whether to include stack traces."));
            requestedConfiguration.addField(new BooleanField(
                    CK_INCLUDE_EXCEPTION_CAUSE,
                    "Include exception causes",
                    true,
                    "Whether to include information about the exception cause."));

            return requestedConfiguration;
        }
    }

    public static class Descriptor extends AbstractCodec.Descriptor {
        @Inject
        public Descriptor() {
            super(SerializedLogEventCodec.class.getAnnotation(Codec.class).displayName());
        }
    }
}

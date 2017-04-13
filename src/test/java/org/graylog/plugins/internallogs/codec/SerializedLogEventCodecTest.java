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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.MutableLogEvent;
import org.apache.logging.log4j.core.net.Severity;
import org.apache.logging.log4j.message.SimpleMessage;
import org.apache.logging.log4j.spi.MutableThreadContextStack;
import org.apache.logging.log4j.util.SortedArrayStringMap;
import org.apache.logging.log4j.util.StringMap;
import org.graylog2.cluster.Node;
import org.graylog2.cluster.NodeService;
import org.graylog2.plugin.Message;
import org.graylog2.plugin.cluster.ClusterConfigService;
import org.graylog2.plugin.cluster.ClusterId;
import org.graylog2.plugin.configuration.Configuration;
import org.graylog2.plugin.journal.RawMessage;
import org.graylog2.plugin.system.NodeId;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class SerializedLogEventCodecTest {
    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private NodeId nodeId;
    @Mock
    private Node node;
    @Mock
    private NodeService nodeService;
    @Mock
    private ClusterConfigService clusterConfigService;
    private SerializedLogEventCodec codec;

    @Before
    public void setUp() throws Exception {
        final ClusterId clusterId = ClusterId.create("cluster-id");

        when(clusterConfigService.get(ClusterId.class)).thenReturn(clusterId);
        when(nodeId.toString()).thenReturn("node-id");
        when(node.getHostname()).thenReturn("example.org");
        when(nodeService.byNodeId(nodeId)).thenReturn(node);

        codec = new SerializedLogEventCodec(Configuration.EMPTY_CONFIGURATION, nodeId, nodeService, clusterConfigService);
    }

    @Test
    public void decodeWithEmptyRawMessagePayloadReturnsNull() throws Exception {
        final RawMessage rawMessage = new RawMessage(new byte[0]);
        assertThat(codec.decode(rawMessage)).isNull();
    }

    @Test
    public void decodeWithInvalidRawMessagePayloadReturnsNull() throws Exception {
        final RawMessage rawMessage = new RawMessage("foobar".getBytes(StandardCharsets.UTF_8));
        assertThat(codec.decode(rawMessage)).isNull();
    }

    @Test
    public void decodeWithValidRawMessagePayloadReturnsValidMessage() throws Exception {
        final DateTime timestamp = new DateTime(2016, 9, 20, 0, 0, DateTimeZone.UTC);
        final LogEvent logEvent = createLogEvent(timestamp);
        final byte[] payload;
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream();
             final ObjectOutputStream outputStream = new ObjectOutputStream(baos)) {
            outputStream.writeObject(logEvent);
            outputStream.flush();
            payload = baos.toByteArray();
        }

        assertThat(payload).isNotNull();

        final RawMessage rawMessage = new RawMessage(payload);
        final Message message = codec.decode(rawMessage);
        assertThat(message).isNotNull();
        assertThat(message.getMessage()).isEqualTo("Test");
        assertThat(message.getSource()).isEqualTo("example.org");
        assertThat(message.getTimestamp()).isEqualTo(timestamp);
        assertThat(message.getFields())
                .containsKey("_id")
                .containsEntry("cluster_id", "cluster-id")
                .containsEntry("node_id", "node-id")
                .containsEntry("level", Severity.DEBUG.getCode())
                .containsEntry("log4j_level", "TRACE")
                .containsEntry("log4j_level_int", Level.TRACE.intLevel())
                .containsEntry("marker", "TestMarker")
                .containsEntry("logger_name", "org.example.Test")
                .containsEntry("context_foobar", "quux")
                .containsEntry("context_stack", ImmutableList.of("one", "two"))
                .containsEntry("exception_class", "java.lang.Throwable")
                .containsEntry("exception_message", "Test");
        assertThat((String) message.getField("exception_stack_trace")).startsWith("java.lang.Throwable: Test");
    }

    @Test
    public void decodedMessageDoesNotContainExtraInformation() throws Exception {
        final Configuration configuration = new Configuration(ImmutableMap.of(
                "include_source", false,
                "include_thread_context", false,
                "include_stack_trace", false,
                "include_exception_cause", false
        ));
        final SerializedLogEventCodec codec = new SerializedLogEventCodec(configuration, nodeId, nodeService, clusterConfigService);
        final LogEvent logEvent = createLogEvent(new DateTime(2016, 9, 20, 0, 0, DateTimeZone.UTC));
        final byte[] payload;
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream();
             final ObjectOutputStream outputStream = new ObjectOutputStream(baos)) {
            outputStream.writeObject(logEvent);
            outputStream.flush();
            payload = baos.toByteArray();
        }

        assertThat(payload).isNotNull();

        final RawMessage rawMessage = new RawMessage(payload);
        final Message message = codec.decode(rawMessage);
        assertThat(message).isNotNull();
        assertThat(message.getFields())
                .doesNotContainKeys("context_stack")
                .doesNotContainKeys("context_foobar")
                .doesNotContainKeys("source_file_name", "source_method_name", "source_class_name", "source_line_number")
                .doesNotContainKeys("exception_class", "exception_message", "exception_stack_trace");
    }

    private LogEvent createLogEvent(DateTime timestamp) {
        final MutableLogEvent logEvent = new MutableLogEvent();

        logEvent.setMessage(new SimpleMessage("Test"));
        logEvent.setLevel(Level.TRACE);
        logEvent.setLoggerName("org.example.Test");
        logEvent.setMarker(MarkerManager.getMarker("TestMarker"));
        logEvent.setTimeMillis(timestamp.getMillis());
        logEvent.setNanoTime(42L);

        logEvent.setContextStack(new MutableThreadContextStack(ImmutableList.of("one", "two")));
        final SortedArrayStringMap contextData = new SortedArrayStringMap(1);
        contextData.putValue("foobar", "quux");
        logEvent.setContextData(contextData);

        logEvent.setThreadId(23L);
        logEvent.setThreadName("thread-name");
        logEvent.setThreadPriority(42);

        logEvent.setThrown(new Throwable("Test", new Throwable("cause")));

        logEvent.setIncludeLocation(true);

        return logEvent.createMemento();
    }
}
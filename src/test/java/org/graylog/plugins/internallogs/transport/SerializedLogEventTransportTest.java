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
package org.graylog.plugins.internallogs.transport;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.impl.MutableLogEvent;
import org.apache.logging.log4j.core.layout.SerializedLayout;
import org.apache.logging.log4j.message.SimpleMessage;
import org.graylog.plugins.internallogs.log4j.DirectConsumingAppender;
import org.graylog2.plugin.LocalMetricRegistry;
import org.graylog2.plugin.configuration.Configuration;
import org.graylog2.plugin.inputs.MessageInput;
import org.graylog2.plugin.inputs.codecs.CodecAggregator;
import org.graylog2.plugin.journal.RawMessage;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class SerializedLogEventTransportTest {
    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private final LocalMetricRegistry metricRegistry = new LocalMetricRegistry();
    private SerializedLogEventTransport transport;

    @Before
    public void setUp() throws Exception {
        transport = new SerializedLogEventTransport(Configuration.EMPTY_CONFIGURATION, metricRegistry);
    }

    @Test
    public void launchCreatesAndStartsAppenderAndProcessesMessages() throws Exception {
        final MessageInput messageInput = mock(MessageInput.class);
        transport.launch(messageInput);

        final DirectConsumingAppender appender = transport.getAppender();
        assertThat(appender.getName()).isEqualTo("graylog-plugin-internal-logs");
        assertThat(appender.getLayout()).isInstanceOf(SerializedLayout.class);
        assertThat(appender.isStarted()).isTrue();

        final MutableLogEvent logEvent = new MutableLogEvent();
        logEvent.setMessage(new SimpleMessage("Processed"));
        logEvent.setLevel(Level.ERROR);
        appender.append(logEvent);
        verify(messageInput, times(1)).processRawMessage(any(RawMessage.class));

        final MutableLogEvent ignoredLogEvent = new MutableLogEvent();
        ignoredLogEvent.setMessage(new SimpleMessage("Ignored"));
        ignoredLogEvent.setLevel(Level.TRACE);
        appender.append(ignoredLogEvent);
        verifyNoMoreInteractions(messageInput);
    }

    @Test
    public void stopShutsDownAppender() throws Exception {
        final DirectConsumingAppender appender = mock(DirectConsumingAppender.class);
        transport.setAppender(appender);
        transport.stop();
        verify(appender, times(1)).stop();
    }

    @Test
    public void stopSucceedsIfAppenderIsNull() throws Exception {
        transport.setAppender(null);
        transport.stop();
    }

    @Test
    public void setMessageAggregatorDoesNothing() throws Exception {
        final CodecAggregator codecAggregator = mock(CodecAggregator.class);
        transport.setMessageAggregator(codecAggregator);
        verifyNoMoreInteractions(codecAggregator);
    }

    @Test
    public void getMetricSetReturnsMetricRegistry() throws Exception {
        assertThat(transport.getMetricSet()).isSameAs(metricRegistry);
    }
}
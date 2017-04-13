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
package org.graylog.plugins.internallogs.log4j;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.filter.ThresholdFilter;
import org.apache.logging.log4j.core.impl.MutableLogEvent;
import org.apache.logging.log4j.core.layout.SerializedLayout;
import org.apache.logging.log4j.message.SimpleMessage;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

public class DirectConsumingAppenderTest {
    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private DirectConsumingAppender appender;
    private TestConsumer consumer;

    @Before
    public void setUp() throws Exception {
        consumer = new TestConsumer();
        appender = new DirectConsumingAppender("test", consumer, Level.INFO);
    }

    @Test
    public void appenderSendsLogEventToConsumerIfThresholdMatches() throws Exception {
        final MutableLogEvent logEvent = new MutableLogEvent();
        logEvent.setMessage(new SimpleMessage("Test"));
        logEvent.setLevel(Level.ERROR);
        appender.append(logEvent);

        assertThat(consumer.getProcessedLogEvents()).hasSize(1);
    }

    @Test
    public void appenderIgnoresLogEventIfThresholdMismatches() throws Exception {
        final MutableLogEvent logEvent = new MutableLogEvent();
        logEvent.setMessage(new SimpleMessage("Test"));
        logEvent.setLevel(Level.TRACE);
        appender.append(logEvent);

        assertThat(consumer.getProcessedLogEvents()).isEmpty();
    }

    @Test
    public void appenderUsesName() throws Exception {
        assertThat(appender.getName()).isEqualTo("test");
    }

    @Test
    public void appenderUsesSerializedLayout() throws Exception {
        assertThat(appender.getLayout()).isInstanceOf(SerializedLayout.class);
    }

    @Test
    public void appenderUsesThresholdFilter() throws Exception {
        assertThat(appender.getFilter()).isInstanceOf(ThresholdFilter.class);
    }

    private static final class TestConsumer implements Consumer<byte[]> {
        private final List<byte[]> processedLogEvents = new ArrayList<>();

        @Override
        public void accept(byte[] logEvent) {
            processedLogEvents.add(logEvent);
        }

        public List<byte[]> getProcessedLogEvents() {
            return processedLogEvents;
        }
    }

}
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
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.filter.ThresholdFilter;
import org.apache.logging.log4j.core.layout.SerializedLayout;

import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

/**
 * A Log4J appender that calls a given {@link Consumer} for each appended {@link LogEvent}.
 */
public class DirectConsumingAppender extends AbstractAppender {
    private static final SerializedLayout LAYOUT = SerializedLayout.createLayout();
    private static final byte[] HEADER = LAYOUT.getHeader();

    private final Consumer<byte[]> logEventConsumer;

    public DirectConsumingAppender(String name, Consumer<byte[]> logEventConsumer, Level threshold) {
        super(name, ThresholdFilter.createFilter(threshold, Filter.Result.ACCEPT, Filter.Result.DENY), LAYOUT, false);
        this.logEventConsumer = requireNonNull(logEventConsumer);
    }

    @Override
    public synchronized void append(LogEvent event) {
        if (!isFiltered(event)) {
            final byte[] content = LAYOUT.toByteArray(event);
            final byte[] record = new byte[HEADER.length + content.length];

            System.arraycopy(HEADER, 0, record, 0, HEADER.length);
            System.arraycopy(content, 0, record, HEADER.length, content.length);

            logEventConsumer.accept(record);
        }
    }
}
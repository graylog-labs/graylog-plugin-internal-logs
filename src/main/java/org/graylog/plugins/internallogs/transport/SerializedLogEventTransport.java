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

import com.codahale.metrics.MetricSet;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSortedMap;
import com.google.inject.assistedinject.Assisted;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.graylog.plugins.internallogs.log4j.DirectConsumingAppender;
import org.graylog2.plugin.LocalMetricRegistry;
import org.graylog2.plugin.configuration.Configuration;
import org.graylog2.plugin.configuration.ConfigurationRequest;
import org.graylog2.plugin.configuration.fields.ConfigurationField;
import org.graylog2.plugin.configuration.fields.DropdownField;
import org.graylog2.plugin.inputs.MessageInput;
import org.graylog2.plugin.inputs.MisfireException;
import org.graylog2.plugin.inputs.annotations.ConfigClass;
import org.graylog2.plugin.inputs.annotations.FactoryClass;
import org.graylog2.plugin.inputs.codecs.CodecAggregator;
import org.graylog2.plugin.inputs.transports.Transport;
import org.graylog2.plugin.journal.RawMessage;

import javax.inject.Inject;
import java.util.SortedMap;

import static java.util.Objects.requireNonNull;

public class SerializedLogEventTransport implements Transport {
    private static final String CK_LEVEL_THRESHOLD = "level_threshold";
    private static final Level DEFAULT_LEVEL = Level.INFO;

    private final Configuration configuration;
    private final LocalMetricRegistry metricRegistry;
    private DirectConsumingAppender appender;
    private Level threshold;

    @Inject
    public SerializedLogEventTransport(@Assisted Configuration configuration,
                                       LocalMetricRegistry metricRegistry) {
        this.configuration = configuration;
        this.metricRegistry = requireNonNull(metricRegistry);
        this.threshold = Level.toLevel(configuration.getString(CK_LEVEL_THRESHOLD), DEFAULT_LEVEL);
    }

    @VisibleForTesting
    protected DirectConsumingAppender getAppender() {
        return appender;
    }

    @VisibleForTesting
    protected void setAppender(DirectConsumingAppender appender) {
        this.appender = appender;
    }

    @Override
    public void launch(MessageInput input) throws MisfireException {
        appender = new DirectConsumingAppender(
                "graylog-plugin-internal-logs",
                logEvent -> input.processRawMessage(new RawMessage(logEvent)),
                threshold);
        addAppender(appender);
    }

    private void addAppender(Appender appender) {
        final LoggerContext context = LoggerContext.getContext(false);
        final org.apache.logging.log4j.core.config.Configuration config = context.getConfiguration();
        appender.start();
        config.addAppender(appender);

        for (final LoggerConfig loggerConfig : config.getLoggers().values()) {
            loggerConfig.addAppender(appender, null, null);
        }
        config.getRootLogger().addAppender(appender, null, null);
        context.updateLoggers();
    }

    @Override
    public void stop() {
        if (appender != null) {
            removeAppender(appender.getName());
            appender.stop();
        }
    }

    private void removeAppender(String name) {
        final LoggerContext context = LoggerContext.getContext(false);
        final org.apache.logging.log4j.core.config.Configuration config = context.getConfiguration();

        for (final LoggerConfig loggerConfig : config.getLoggers().values()) {
            loggerConfig.removeAppender(name);
        }
        config.getRootLogger().removeAppender(name);
        context.updateLoggers();
    }

    @Override
    public void setMessageAggregator(CodecAggregator aggregator) {
    }

    @Override
    public MetricSet getMetricSet() {
        return metricRegistry;
    }

    @FactoryClass
    public interface Factory extends Transport.Factory<SerializedLogEventTransport> {
        @Override
        SerializedLogEventTransport create(Configuration configuration);

        @Override
        Config getConfig();
    }

    @ConfigClass
    public static class Config implements Transport.Config {
        @Override
        public ConfigurationRequest getRequestedConfiguration() {
            final ConfigurationRequest configurationRequest = new ConfigurationRequest();
            final SortedMap<String, String> levels = ImmutableSortedMap
                    .<String, String>orderedBy((o1, o2) -> Level.getLevel(o1).compareTo(Level.getLevel(o2)))
                    .put(Level.OFF.name(), Level.OFF.name())
                    .put(Level.FATAL.name(), Level.FATAL.name())
                    .put(Level.ERROR.name(), Level.ERROR.name())
                    .put(Level.WARN.name(), Level.WARN.name())
                    .put(Level.INFO.name(), Level.INFO.name())
                    .put(Level.DEBUG.name(), Level.DEBUG.name())
                    .put(Level.TRACE.name(), Level.TRACE.name())
                    .put(Level.ALL.name(), Level.ALL.name())
                    .build();

            configurationRequest.addField(
                    new DropdownField(
                            CK_LEVEL_THRESHOLD,
                            "Level threshold",
                            Level.INFO.name(),
                            levels,
                            "Defines the minimum log level for internal log messages which should be logged.",
                            ConfigurationField.Optional.NOT_OPTIONAL)
            );

            return configurationRequest;
        }
    }
}

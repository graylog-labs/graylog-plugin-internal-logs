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
package org.graylog.plugins.internallogs;

import org.graylog.plugins.internallogs.codec.SerializedLogEventCodec;
import org.graylog.plugins.internallogs.input.InternalLogsInput;
import org.graylog.plugins.internallogs.transport.SerializedLogEventTransport;
import org.graylog2.plugin.PluginModule;

public class InternalLogsInputPluginModule extends PluginModule {
    @Override
    protected void configure() {
        addTransport("serialized-logevent", SerializedLogEventTransport.class);
        addCodec("serialized-logevent", SerializedLogEventCodec.class);
        addMessageInput(InternalLogsInput.class);
    }
}

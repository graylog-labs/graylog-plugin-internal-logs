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

import org.graylog2.plugin.PluginMetaData;
import org.graylog2.plugin.ServerStatus;
import org.graylog2.plugin.Version;

import java.net.URI;
import java.util.Collections;
import java.util.Set;

public class InternalLogsInputPluginMetaData implements PluginMetaData {
    @Override
    public String getUniqueId() {
        return InternalLogsInputPlugin.class.getCanonicalName();
    }

    @Override
    public String getName() {
        return "Internal Logs plugin";
    }

    @Override
    public String getAuthor() {
        return "Graylog, Inc.";
    }

    @Override
    public URI getURL() {
        return URI.create("https://www.graylog.org/");
    }

    @Override
    public Version getVersion() {
        return Version.from(1, 0, 0);
    }

    @Override
    public String getDescription() {
        return "Input plugin for Graylog's internal logs.";
    }

    @Override
    public Version getRequiredVersion() {
        return Version.from(2, 0, 0);
    }

    @Override
    public Set<ServerStatus.Capability> getRequiredCapabilities() {
        return Collections.emptySet();
    }
}

/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.monitor.os;

import org.apache.lucene.util.Constants;
import org.elasticsearch.common.component.AbstractComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.util.SingleObjectCache;

/**
 *
 */
public class OsService extends AbstractComponent {

    private final OsProbe probe;

    private final OsInfo info;

    private SingleObjectCache<OsStats> osStatsCache;

    @Inject
    public OsService(Settings settings, OsProbe probe) {
        super(settings);
        this.probe = probe;

        TimeValue refreshInterval = settings.getAsTime("monitor.os.refresh_interval", TimeValue.timeValueSeconds(1));

        this.info = probe.osInfo();
        this.info.refreshInterval = refreshInterval.millis();
        this.info.availableProcessors = Runtime.getRuntime().availableProcessors();
        this.info.name = Constants.OS_NAME;
        osStatsCache = new OsStatsCache(refreshInterval, probe.osStats());
        logger.debug("Using probe [{}] with refresh_interval [{}]", probe, refreshInterval);
    }

    public OsInfo info() {
        return this.info;
    }

    public synchronized OsStats stats() {
        return osStatsCache.getOrRefresh();
    }

    private class OsStatsCache extends SingleObjectCache<OsStats> {
        public OsStatsCache(TimeValue interval, OsStats initValue) {
            super(interval, initValue);
        }

        @Override
        protected OsStats refresh() {
            return probe.osStats();
        }
    }
}

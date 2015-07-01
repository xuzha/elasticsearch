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

package org.elasticsearch.search.scriptfilter;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.cache.query.QueryCacheModule;
import org.elasticsearch.index.cache.query.QueryCacheModule.QueryCacheSettings;
import org.elasticsearch.index.cache.query.index.IndexQueryCache;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptService.ScriptType;
import org.elasticsearch.search.sort.SortOrder;
import org.elasticsearch.test.ElasticsearchIntegrationTest;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.index.query.QueryBuilders.filteredQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.scriptQuery;
import static org.hamcrest.Matchers.equalTo;

/**
 *
 */
@ElasticsearchIntegrationTest.ClusterScope(scope=ElasticsearchIntegrationTest.Scope.SUITE)
public class ScriptQuerySearchTests extends ElasticsearchIntegrationTest {

    @Override
    protected Settings nodeSettings(int nodeOrdinal) {
        return Settings.settingsBuilder().put(super.nodeSettings(nodeOrdinal))
                // aggressive filter caching so that we can assert on the number of iterations of the script filters
                .put(QueryCacheModule.QueryCacheSettings.QUERY_CACHE_TYPE, IndexQueryCache.class)
                .put(QueryCacheSettings.QUERY_CACHE_EVERYTHING, true)
                .build();
    }

    @Test
    public void testCustomScriptBoost() throws Exception {
        createIndex("test");
        client().prepareIndex("test", "type1", "1")
                .setSource(jsonBuilder().startObject().field("test", "value beck").field("num1", 1.0f).endObject())
                .execute().actionGet();
        flush();
        client().prepareIndex("test", "type1", "2")
                .setSource(jsonBuilder().startObject().field("test", "value beck").field("num1", 2.0f).endObject())
                .execute().actionGet();
        flush();
        client().prepareIndex("test", "type1", "3")
                .setSource(jsonBuilder().startObject().field("test", "value beck").field("num1", 3.0f).endObject())
                .execute().actionGet();
        refresh();

        logger.info("running doc['num1'].value > 1");
        SearchResponse response = client().prepareSearch()
                .setQuery(filteredQuery(matchAllQuery(), scriptQuery(new Script("doc['num1'].value > 1")))).addSort("num1", SortOrder.ASC)
                .addScriptField("sNum1", new Script("doc['num1'].value")).execute().actionGet();

        assertThat(response.getHits().totalHits(), equalTo(2l));
        assertThat(response.getHits().getAt(0).id(), equalTo("2"));
        assertThat((Double) response.getHits().getAt(0).fields().get("sNum1").values().get(0), equalTo(2.0));
        assertThat(response.getHits().getAt(1).id(), equalTo("3"));
        assertThat((Double) response.getHits().getAt(1).fields().get("sNum1").values().get(0), equalTo(3.0));

        Map<String, Object> params = new HashMap<>();
        params.put("param1", 2);

        logger.info("running doc['num1'].value > param1");
        response = client()
                .prepareSearch()
                .setQuery(scriptQuery(new Script("doc['num1'].value > param1", ScriptType.INLINE, null, params)))
                .addSort("num1", SortOrder.ASC).addScriptField("sNum1", new Script("doc['num1'].value")).execute().actionGet();

        assertThat(response.getHits().totalHits(), equalTo(1l));
        assertThat(response.getHits().getAt(0).id(), equalTo("3"));
        assertThat((Double) response.getHits().getAt(0).fields().get("sNum1").values().get(0), equalTo(3.0));

        params = new HashMap<>();
        params.put("param1", -1);
        logger.info("running doc['num1'].value > param1");
        response = client()
                .prepareSearch()
                .setQuery(
                        filteredQuery(matchAllQuery(),
                                scriptQuery(new Script("doc['num1'].value > param1", ScriptType.INLINE, null, params))))
                .addSort("num1", SortOrder.ASC).addScriptField("sNum1", new Script("doc['num1'].value")).execute().actionGet();

        assertThat(response.getHits().totalHits(), equalTo(3l));
        assertThat(response.getHits().getAt(0).id(), equalTo("1"));
        assertThat((Double) response.getHits().getAt(0).fields().get("sNum1").values().get(0), equalTo(1.0));
        assertThat(response.getHits().getAt(1).id(), equalTo("2"));
        assertThat((Double) response.getHits().getAt(1).fields().get("sNum1").values().get(0), equalTo(2.0));
        assertThat(response.getHits().getAt(2).id(), equalTo("3"));
        assertThat((Double) response.getHits().getAt(2).fields().get("sNum1").values().get(0), equalTo(3.0));
    }

    private static AtomicInteger scriptCounter = new AtomicInteger(0);

    public static int incrementScriptCounter() {
        return scriptCounter.incrementAndGet();
    }
}

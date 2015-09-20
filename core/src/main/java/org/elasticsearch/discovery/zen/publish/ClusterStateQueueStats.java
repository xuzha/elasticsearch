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

package org.elasticsearch.discovery.zen.publish;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Streamable;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentBuilderString;

import java.io.IOException;
import java.util.Locale;

/**
 * Class encapsulating stats about the PendingClusterStatsQueue
 */
public class ClusterStateQueueStats implements Streamable, ToXContent {

    private int total;
    private int pending;
    private int committed;

    public ClusterStateQueueStats() {

    }

    public ClusterStateQueueStats(int total, int pending, int committed) {
        this.total = total;
        this.pending = pending;
        this.committed = committed;
    }

    public int getCommitted() {
        return committed;
    }

    public int getPending() {
        return pending;
    }

    public int getTotal() {
        return total;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(Fields.PENDINGQUEUE);
        builder.field(Fields.TOTAL, total);
        builder.field(Fields.PENDING, pending);
        builder.field(Fields.COMMITTED, committed);
        builder.endObject();
        return builder;
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        total = in.readInt();
        pending = in.readInt();
        committed = in.readInt();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeInt(total);
        out.writeInt(pending);
        out.writeInt(committed);
    }

    static final class Fields {

        static final XContentBuilderString PENDINGQUEUE = new XContentBuilderString("clusterstate_queue");
        static final XContentBuilderString TOTAL = new XContentBuilderString("total");
        static final XContentBuilderString PENDING = new XContentBuilderString("pending");
        static final XContentBuilderString COMMITTED = new XContentBuilderString("committed");
    }
}

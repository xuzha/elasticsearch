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

package org.elasticsearch.search.aggregations.pipeline;

import org.elasticsearch.search.aggregations.pipeline.bucketmetrics.avg.AvgBucketBuilder;
import org.elasticsearch.search.aggregations.pipeline.bucketmetrics.max.MaxBucketBuilder;
import org.elasticsearch.search.aggregations.pipeline.bucketmetrics.min.MinBucketBuilder;
import org.elasticsearch.search.aggregations.pipeline.bucketmetrics.sum.SumBucketBuilder;
import org.elasticsearch.search.aggregations.pipeline.bucketscript.BucketScriptBuilder;
import org.elasticsearch.search.aggregations.pipeline.derivative.DerivativeBuilder;
import org.elasticsearch.search.aggregations.pipeline.movavg.MovAvgBuilder;

public final class PipelineAggregatorBuilders {

    private PipelineAggregatorBuilders() {
    }

    public static final DerivativeBuilder derivative(String name) {
        return new DerivativeBuilder(name);
    }

    public static final MaxBucketBuilder maxBucket(String name) {
        return new MaxBucketBuilder(name);
    }

    public static final MinBucketBuilder minBucket(String name) {
        return new MinBucketBuilder(name);
    }

    public static final AvgBucketBuilder avgBucket(String name) {
        return new AvgBucketBuilder(name);
    }

    public static final SumBucketBuilder sumBucket(String name) {
        return new SumBucketBuilder(name);
    }

    public static final MovAvgBuilder movingAvg(String name) {
        return new MovAvgBuilder(name);
    }

    public static final BucketScriptBuilder seriesArithmetic(String name) {
        return new BucketScriptBuilder(name);
    }
}

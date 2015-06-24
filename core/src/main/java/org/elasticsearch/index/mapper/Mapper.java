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

package org.elasticsearch.index.mapper;

import com.google.common.collect.ImmutableMap;
import org.elasticsearch.Version;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.index.analysis.AnalysisService;
import org.elasticsearch.index.similarity.SimilarityLookupService;

import java.util.Map;

public interface Mapper extends ToXContent, Iterable<Mapper> {

    Mapper[] EMPTY_ARRAY = new Mapper[0];

    class BuilderContext {
        private final Settings indexSettings;
        private final ContentPath contentPath;

        public BuilderContext(Settings indexSettings, ContentPath contentPath) {
            this.contentPath = contentPath;
            this.indexSettings = indexSettings;
        }

        public ContentPath path() {
            return this.contentPath;
        }

        @Nullable
        public Settings indexSettings() {
            return this.indexSettings;
        }

        @Nullable
        public Version indexCreatedVersion() {
            if (indexSettings == null) {
                return null;
            }
            return Version.indexCreated(indexSettings);
        }
    }

    abstract class Builder<T extends Builder, Y extends Mapper> {

        public String name;

        protected T builder;

        protected Builder(String name) {
            this.name = name;
        }

        public String name() {
            return this.name;
        }

        public abstract Y build(BuilderContext context);
    }

    interface TypeParser {

        class ParserContext {

            private final AnalysisService analysisService;

            private final SimilarityLookupService similarityLookupService;

            private final MapperService mapperService;

            private final ImmutableMap<String, TypeParser> typeParsers;

            private final Version indexVersionCreated;

            public ParserContext(AnalysisService analysisService, SimilarityLookupService similarityLookupService,
                                 MapperService mapperService,
                                 ImmutableMap<String, TypeParser> typeParsers, Version indexVersionCreated) {
                this.analysisService = analysisService;
                this.similarityLookupService = similarityLookupService;
                this.mapperService = mapperService;
                this.typeParsers = typeParsers;
                this.indexVersionCreated = indexVersionCreated;
            }

            public AnalysisService analysisService() {
                return analysisService;
            }

            public SimilarityLookupService similarityLookupService() {
                return similarityLookupService;
            }

            public MapperService mapperService() {
                return mapperService;
            }

            public TypeParser typeParser(String type) {
                return typeParsers.get(Strings.toUnderscoreCase(type));
            }

            public Version indexVersionCreated() {
                return indexVersionCreated;
            }
        }

        Mapper.Builder<?,?> parse(String name, Map<String, Object> node, ParserContext parserContext) throws MapperParsingException;
    }

    String name();

    void merge(Mapper mergeWith, MergeResult mergeResult) throws MergeMappingException;

    void close();
}

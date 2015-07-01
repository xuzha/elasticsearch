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

package org.elasticsearch.index.mapper.core;

import com.carrotsearch.hppc.cursors.ObjectCursor;
import com.carrotsearch.hppc.cursors.ObjectObjectCursor;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.IndexOptions;
import org.elasticsearch.Version;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.collect.ImmutableOpenMap;
import org.elasticsearch.common.lucene.Lucene;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.analysis.NamedAnalyzer;
import org.elasticsearch.index.fielddata.FieldDataType;
import org.elasticsearch.index.mapper.ContentPath;
import org.elasticsearch.index.mapper.FieldMapper;
import org.elasticsearch.index.mapper.MappedFieldType;
import org.elasticsearch.index.mapper.MappedFieldTypeReference;
import org.elasticsearch.index.mapper.Mapper;
import org.elasticsearch.index.mapper.MapperParsingException;
import org.elasticsearch.index.mapper.MergeMappingException;
import org.elasticsearch.index.mapper.MergeResult;
import org.elasticsearch.index.mapper.ParseContext;
import org.elasticsearch.index.mapper.internal.AllFieldMapper;
import org.elasticsearch.index.similarity.SimilarityLookupService;
import org.elasticsearch.index.similarity.SimilarityProvider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

import static org.elasticsearch.index.mapper.core.TypeParsers.DOC_VALUES;

public abstract class AbstractFieldMapper implements FieldMapper {

    public static class Defaults {
        public static final float BOOST = 1.0f;
        public static final ContentPath.Type PATH_TYPE = ContentPath.Type.FULL;
    }

    public abstract static class Builder<T extends Builder, Y extends AbstractFieldMapper> extends Mapper.Builder<T, Y> {

        protected final MappedFieldType fieldType;
        private final IndexOptions defaultOptions;
        protected Boolean docValues;
        protected boolean omitNormsSet = false;
        protected String indexName;
        protected Boolean includeInAll;
        protected boolean indexOptionsSet = false;
        @Nullable
        protected Settings fieldDataSettings;
        protected final MultiFields.Builder multiFieldsBuilder;
        protected CopyTo copyTo;

        protected Builder(String name, MappedFieldType fieldType) {
            super(name);
            this.fieldType = fieldType.clone();
            this.defaultOptions = fieldType.indexOptions(); // we have to store it the fieldType is mutable
            multiFieldsBuilder = new MultiFields.Builder();
        }

        public T index(boolean index) {
            if (index) {
                if (fieldType.indexOptions() == IndexOptions.NONE) {
                    /*
                     * the logic here is to reset to the default options only if we are not indexed ie. options are null
                     * if the fieldType has a non-null option we are all good it might have been set through a different
                     * call.
                     */
                    final IndexOptions options = getDefaultIndexOption();
                    assert options != IndexOptions.NONE : "default IndexOptions is NONE can't enable indexing";
                    fieldType.setIndexOptions(options);
                }
            } else {
                fieldType.setIndexOptions(IndexOptions.NONE);
            }
            return builder;
        }

        protected IndexOptions getDefaultIndexOption() {
            return defaultOptions;
        }

        public T store(boolean store) {
            this.fieldType.setStored(store);
            return builder;
        }

        public T docValues(boolean docValues) {
            this.docValues = docValues;
            return builder;
        }

        public T storeTermVectors(boolean termVectors) {
            if (termVectors != this.fieldType.storeTermVectors()) {
                this.fieldType.setStoreTermVectors(termVectors);
            } // don't set it to false, it is default and might be flipped by a more specific option
            return builder;
        }

        public T storeTermVectorOffsets(boolean termVectorOffsets) {
            if (termVectorOffsets) {
                this.fieldType.setStoreTermVectors(termVectorOffsets);
            }
            this.fieldType.setStoreTermVectorOffsets(termVectorOffsets);
            return builder;
        }

        public T storeTermVectorPositions(boolean termVectorPositions) {
            if (termVectorPositions) {
                this.fieldType.setStoreTermVectors(termVectorPositions);
            }
            this.fieldType.setStoreTermVectorPositions(termVectorPositions);
            return builder;
        }

        public T storeTermVectorPayloads(boolean termVectorPayloads) {
            if (termVectorPayloads) {
                this.fieldType.setStoreTermVectors(termVectorPayloads);
            }
            this.fieldType.setStoreTermVectorPayloads(termVectorPayloads);
            return builder;
        }

        public T tokenized(boolean tokenized) {
            this.fieldType.setTokenized(tokenized);
            return builder;
        }

        public T boost(float boost) {
            this.fieldType.setBoost(boost);
            return builder;
        }

        public T omitNorms(boolean omitNorms) {
            this.fieldType.setOmitNorms(omitNorms);
            this.omitNormsSet = true;
            return builder;
        }

        public T indexOptions(IndexOptions indexOptions) {
            this.fieldType.setIndexOptions(indexOptions);
            this.indexOptionsSet = true;
            return builder;
        }

        public T indexName(String indexName) {
            this.indexName = indexName;
            return builder;
        }

        public T indexAnalyzer(NamedAnalyzer indexAnalyzer) {
            this.fieldType.setIndexAnalyzer(indexAnalyzer);
            return builder;
        }

        public T searchAnalyzer(NamedAnalyzer searchAnalyzer) {
            this.fieldType.setSearchAnalyzer(searchAnalyzer);
            return builder;
        }

        public T includeInAll(Boolean includeInAll) {
            this.includeInAll = includeInAll;
            return builder;
        }

        public T similarity(SimilarityProvider similarity) {
            this.fieldType.setSimilarity(similarity);
            return builder;
        }

        public T normsLoading(MappedFieldType.Loading normsLoading) {
            this.fieldType.setNormsLoading(normsLoading);
            return builder;
        }

        public T fieldDataSettings(Settings settings) {
            this.fieldDataSettings = settings;
            return builder;
        }

        public Builder nullValue(Object nullValue) {
            this.fieldType.setNullValue(nullValue);
            return this;
        }

        public T multiFieldPathType(ContentPath.Type pathType) {
            multiFieldsBuilder.pathType(pathType);
            return builder;
        }

        public T addMultiField(Mapper.Builder mapperBuilder) {
            multiFieldsBuilder.add(mapperBuilder);
            return builder;
        }

        public T copyTo(CopyTo copyTo) {
            this.copyTo = copyTo;
            return builder;
        }

        protected MappedFieldType.Names buildNames(BuilderContext context) {
            return new MappedFieldType.Names(name, buildIndexName(context), buildIndexNameClean(context), buildFullName(context));
        }

        protected String buildIndexName(BuilderContext context) {
            if (context.indexCreatedVersion().onOrAfter(Version.V_2_0_0)) {
                return buildFullName(context);
            }
            String actualIndexName = indexName == null ? name : indexName;
            return context.path().pathAsText(actualIndexName);
        }
        
        protected String buildIndexNameClean(BuilderContext context) {
            if (context.indexCreatedVersion().onOrAfter(Version.V_2_0_0)) {
                return buildFullName(context);
            }
            return indexName == null ? name : indexName;
        }

        protected String buildFullName(BuilderContext context) {
            return context.path().fullPathAsText(name);
        }

        protected void setupFieldType(BuilderContext context) {
            fieldType.setNames(buildNames(context));
        }
    }

    protected MappedFieldTypeReference fieldTypeRef;
    protected final boolean hasDefaultDocValues;
    protected Settings customFieldDataSettings;
    protected final MultiFields multiFields;
    protected CopyTo copyTo;
    protected final boolean indexCreatedBefore2x;

    protected AbstractFieldMapper(MappedFieldType fieldType, Boolean docValues, @Nullable Settings fieldDataSettings, Settings indexSettings) {
        this(fieldType, docValues, fieldDataSettings, indexSettings, MultiFields.empty(), null);
    }

    protected AbstractFieldMapper(MappedFieldType fieldType, Boolean docValues, @Nullable Settings fieldDataSettings, Settings indexSettings, MultiFields multiFields, CopyTo copyTo) {
        assert indexSettings != null;
        this.indexCreatedBefore2x = Version.indexCreated(indexSettings).before(Version.V_2_0_0);
        this.customFieldDataSettings = fieldDataSettings;
        FieldDataType fieldDataType;
        if (fieldDataSettings == null) {
            fieldDataType = defaultFieldDataType();
        } else {
            // create a new field data type, with the default settings as well as the "new ones"
            fieldDataType = new FieldDataType(defaultFieldDataType().getType(),
                Settings.builder().put(defaultFieldDataType().getSettings()).put(fieldDataSettings)
            );
        }

        // TODO: hasDocValues should just be set directly on the field type by callers of this ctor, but
        // then we need to eliminate defaultDocValues() (only needed by geo, which needs to be fixed with passing
        // doc values setting down to lat/lon) and get rid of specifying doc values in fielddata (which
        // complicates whether we can just compare to the default value to know whether to write the setting)
        if (docValues == null && fieldDataType != null && FieldDataType.DOC_VALUES_FORMAT_VALUE.equals(fieldDataType.getFormat(indexSettings))) {
            docValues = true;
        }
        hasDefaultDocValues = docValues == null;

        this.fieldTypeRef = new MappedFieldTypeReference(fieldType); // must init first so defaultDocValues() can be called
        fieldType = fieldType.clone();
        if (fieldType.indexAnalyzer() == null && fieldType.tokenized() == false && fieldType.indexOptions() != IndexOptions.NONE) {
            fieldType.setIndexAnalyzer(Lucene.KEYWORD_ANALYZER);
            fieldType.setSearchAnalyzer(Lucene.KEYWORD_ANALYZER);
        }
        fieldType.setHasDocValues(docValues == null ? defaultDocValues() : docValues);
        fieldType.setFieldDataType(fieldDataType);
        fieldType.freeze();
        this.fieldTypeRef.set(fieldType); // now reset ref once extra settings have been initialized

        this.multiFields = multiFields;
        this.copyTo = copyTo;
    }
    
    protected boolean defaultDocValues() {
        if (indexCreatedBefore2x) {
            return false;
        } else {
            return fieldType().tokenized() == false && fieldType().indexOptions() != IndexOptions.NONE;
        }
    }

    @Override
    public String name() {
        // TODO: cleanup names so Mapper knows about paths, so that it is always clear whether we are using short or full name
        return fieldType().names().shortName();
    }

    public abstract MappedFieldType defaultFieldType();

    public abstract FieldDataType defaultFieldDataType();

    @Override
    public MappedFieldType fieldType() {
        return fieldTypeRef.get();
    }

    @Override
    public MappedFieldTypeReference fieldTypeReference() {
        return fieldTypeRef;
    }

    @Override
    public void setFieldTypeReference(MappedFieldTypeReference ref) {
        if (ref.get().equals(fieldType()) == false) {
            throw new IllegalStateException("Cannot overwrite field type reference to unequal reference");
        }
        ref.incrementAssociatedMappers();
        this.fieldTypeRef = ref;
    }

    @Override
    public CopyTo copyTo() {
        return copyTo;
    }

    @Override
    public Mapper parse(ParseContext context) throws IOException {
        final List<Field> fields = new ArrayList<>(2);
        try {
            parseCreateField(context, fields);
            for (Field field : fields) {
                if (!customBoost()) {
                    field.setBoost(fieldType().boost());
                }
                context.doc().add(field);
            }
        } catch (Exception e) {
            throw new MapperParsingException("failed to parse [" + fieldType().names().fullName() + "]", e);
        }
        multiFields.parse(this, context);
        return null;
    }

    /**
     * Parse the field value and populate <code>fields</code>.
     */
    protected abstract void parseCreateField(ParseContext context, List<Field> fields) throws IOException;

    /**
     * Derived classes can override it to specify that boost value is set by derived classes.
     */
    protected boolean customBoost() {
        return false;
    }

    public Iterator<Mapper> iterator() {
        if (multiFields == null) {
            return Collections.emptyIterator();
        }
        return multiFields.iterator();
    }

    @Override
    public void merge(Mapper mergeWith, MergeResult mergeResult) throws MergeMappingException {
        if (!this.getClass().equals(mergeWith.getClass())) {
            String mergedType = mergeWith.getClass().getSimpleName();
            if (mergeWith instanceof AbstractFieldMapper) {
                mergedType = ((AbstractFieldMapper) mergeWith).contentType();
            }
            mergeResult.addConflict("mapper [" + fieldType().names().fullName() + "] of different type, current_type [" + contentType() + "], merged_type [" + mergedType + "]");
            // different types, return
            return;
        }
        AbstractFieldMapper fieldMergeWith = (AbstractFieldMapper) mergeWith;
        List<String> subConflicts = new ArrayList<>(); // TODO: just expose list from MergeResult?
        fieldType().checkTypeName(fieldMergeWith.fieldType(), subConflicts);
        if (subConflicts.isEmpty() == false) {
            // return early if field types don't match
            assert subConflicts.size() == 1;
            mergeResult.addConflict(subConflicts.get(0));
            return;
        }

        boolean strict = this.fieldTypeRef.getNumAssociatedMappers() > 1 && mergeResult.updateAllTypes() == false;
        fieldType().checkCompatibility(fieldMergeWith.fieldType(), subConflicts, strict);
        for (String conflict : subConflicts) {
            mergeResult.addConflict(conflict);
        }
        multiFields.merge(mergeWith, mergeResult);

        if (mergeResult.simulate() == false && mergeResult.hasConflicts() == false) {
            // apply changeable values
            MappedFieldType fieldType = fieldMergeWith.fieldType().clone();
            fieldType.freeze();
            fieldTypeRef.set(fieldType);
            this.customFieldDataSettings = fieldMergeWith.customFieldDataSettings;
            this.copyTo = fieldMergeWith.copyTo;
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(fieldType().names().shortName());
        boolean includeDefaults = params.paramAsBoolean("include_defaults", false);
        doXContentBody(builder, includeDefaults, params);
        return builder.endObject();
    }

    protected void doXContentBody(XContentBuilder builder, boolean includeDefaults, Params params) throws IOException {

        builder.field("type", contentType());
        if (indexCreatedBefore2x && (includeDefaults || !fieldType().names().shortName().equals(fieldType().names().originalIndexName()))) {
            builder.field("index_name", fieldType().names().originalIndexName());
        }

        if (includeDefaults || fieldType().boost() != 1.0f) {
            builder.field("boost", fieldType().boost());
        }

        FieldType defaultFieldType = defaultFieldType();
        boolean indexed =  fieldType().indexOptions() != IndexOptions.NONE;
        boolean defaultIndexed = defaultFieldType.indexOptions() != IndexOptions.NONE;
        if (includeDefaults || indexed != defaultIndexed ||
                fieldType().tokenized() != defaultFieldType.tokenized()) {
            builder.field("index", indexTokenizeOptionToString(indexed, fieldType().tokenized()));
        }
        if (includeDefaults || fieldType().stored() != defaultFieldType.stored()) {
            builder.field("store", fieldType().stored());
        }
        doXContentDocValues(builder, includeDefaults);
        if (includeDefaults || fieldType().storeTermVectors() != defaultFieldType.storeTermVectors()) {
            builder.field("term_vector", termVectorOptionsToString(fieldType()));
        }
        if (includeDefaults || fieldType().omitNorms() != defaultFieldType.omitNorms() || fieldType().normsLoading() != null) {
            builder.startObject("norms");
            if (includeDefaults || fieldType().omitNorms() != defaultFieldType.omitNorms()) {
                builder.field("enabled", !fieldType().omitNorms());
            }
            if (fieldType().normsLoading() != null) {
                builder.field(MappedFieldType.Loading.KEY, fieldType().normsLoading());
            }
            builder.endObject();
        }
        if (indexed && (includeDefaults || fieldType().indexOptions() != defaultFieldType.indexOptions())) {
            builder.field("index_options", indexOptionToString(fieldType().indexOptions()));
        }

        doXContentAnalyzers(builder, includeDefaults);

        if (fieldType().similarity() != null) {
            builder.field("similarity", fieldType().similarity().name());
        } else if (includeDefaults) {
            builder.field("similarity", SimilarityLookupService.DEFAULT_SIMILARITY);
        }

        TreeMap<String, Object> orderedFielddataSettings = new TreeMap<>();
        if (hasCustomFieldDataSettings()) {
            orderedFielddataSettings.putAll(customFieldDataSettings.getAsMap());
            builder.field("fielddata", orderedFielddataSettings);
        } else if (includeDefaults) {
            orderedFielddataSettings.putAll(fieldType().fieldDataType().getSettings().getAsMap());
            builder.field("fielddata", orderedFielddataSettings);
        }
        multiFields.toXContent(builder, params);

        if (copyTo != null) {
            copyTo.toXContent(builder, params);
        }
    }
    
    protected void doXContentAnalyzers(XContentBuilder builder, boolean includeDefaults) throws IOException {
        if (fieldType().indexAnalyzer() == null) {
            if (includeDefaults) {
                builder.field("analyzer", "default");
            }
        } else if (includeDefaults || fieldType().indexAnalyzer().name().startsWith("_") == false && fieldType().indexAnalyzer().name().equals("default") == false) {
            builder.field("analyzer", fieldType().indexAnalyzer().name());
            if (fieldType().searchAnalyzer().name().equals(fieldType().indexAnalyzer().name()) == false) {
                builder.field("search_analyzer", fieldType().searchAnalyzer().name());
            }
        }
    }
    
    protected void doXContentDocValues(XContentBuilder builder, boolean includeDefaults) throws IOException {
        if (includeDefaults || hasDefaultDocValues == false) {
            builder.field(DOC_VALUES, fieldType().hasDocValues());
        }
    }

    protected static String indexOptionToString(IndexOptions indexOption) {
        switch (indexOption) {
            case DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS:
                return TypeParsers.INDEX_OPTIONS_OFFSETS;
            case DOCS_AND_FREQS:
                return TypeParsers.INDEX_OPTIONS_FREQS;
            case DOCS_AND_FREQS_AND_POSITIONS:
                return TypeParsers.INDEX_OPTIONS_POSITIONS;
            case DOCS:
                return TypeParsers.INDEX_OPTIONS_DOCS;
            default:
                throw new IllegalArgumentException("Unknown IndexOptions [" + indexOption + "]");
        }
    }

    public static String termVectorOptionsToString(FieldType fieldType) {
        if (!fieldType.storeTermVectors()) {
            return "no";
        } else if (!fieldType.storeTermVectorOffsets() && !fieldType.storeTermVectorPositions()) {
            return "yes";
        } else if (fieldType.storeTermVectorOffsets() && !fieldType.storeTermVectorPositions()) {
            return "with_offsets";
        } else {
            StringBuilder builder = new StringBuilder("with");
            if (fieldType.storeTermVectorPositions()) {
                builder.append("_positions");
            }
            if (fieldType.storeTermVectorOffsets()) {
                builder.append("_offsets");
            }
            if (fieldType.storeTermVectorPayloads()) {
                builder.append("_payloads");
            }
            return builder.toString();
        }
    }

    protected static String indexTokenizeOptionToString(boolean indexed, boolean tokenized) {
        if (!indexed) {
            return "no";
        } else if (tokenized) {
            return "analyzed";
        } else {
            return "not_analyzed";
        }
    }

    protected boolean hasCustomFieldDataSettings() {
        return customFieldDataSettings != null && customFieldDataSettings.equals(Settings.EMPTY) == false;
    }

    protected abstract String contentType();

    public static class MultiFields {

        public static MultiFields empty() {
            return new MultiFields(Defaults.PATH_TYPE, ImmutableOpenMap.<String, FieldMapper>of());
        }

        public static class Builder {

            private final ImmutableOpenMap.Builder<String, Mapper.Builder> mapperBuilders = ImmutableOpenMap.builder();
            private ContentPath.Type pathType = Defaults.PATH_TYPE;

            public Builder pathType(ContentPath.Type pathType) {
                this.pathType = pathType;
                return this;
            }

            public Builder add(Mapper.Builder builder) {
                mapperBuilders.put(builder.name(), builder);
                return this;
            }

            @SuppressWarnings("unchecked")
            public MultiFields build(AbstractFieldMapper.Builder mainFieldBuilder, BuilderContext context) {
                if (pathType == Defaults.PATH_TYPE && mapperBuilders.isEmpty()) {
                    return empty();
                } else if (mapperBuilders.isEmpty()) {
                    return new MultiFields(pathType, ImmutableOpenMap.<String, FieldMapper>of());
                } else {
                    ContentPath.Type origPathType = context.path().pathType();
                    context.path().pathType(pathType);
                    context.path().add(mainFieldBuilder.name());
                    ImmutableOpenMap.Builder mapperBuilders = this.mapperBuilders;
                    for (ObjectObjectCursor<String, Mapper.Builder> cursor : this.mapperBuilders) {
                        String key = cursor.key;
                        Mapper.Builder value = cursor.value;
                        Mapper mapper = value.build(context);
                        assert mapper instanceof FieldMapper;
                        mapperBuilders.put(key, mapper);
                    }
                    context.path().remove();
                    context.path().pathType(origPathType);
                    ImmutableOpenMap.Builder<String, FieldMapper> mappers = mapperBuilders.cast();
                    return new MultiFields(pathType, mappers.build());
                }
            }
        }

        private final ContentPath.Type pathType;
        private volatile ImmutableOpenMap<String, FieldMapper> mappers;

        public MultiFields(ContentPath.Type pathType, ImmutableOpenMap<String, FieldMapper> mappers) {
            this.pathType = pathType;
            this.mappers = mappers;
            // we disable the all in multi-field mappers
            for (ObjectCursor<FieldMapper> cursor : mappers.values()) {
                FieldMapper mapper = cursor.value;
                if (mapper instanceof AllFieldMapper.IncludeInAll) {
                    ((AllFieldMapper.IncludeInAll) mapper).unsetIncludeInAll();
                }
            }
        }

        public void parse(AbstractFieldMapper mainField, ParseContext context) throws IOException {
            // TODO: multi fields are really just copy fields, we just need to expose "sub fields" or something that can be part of the mappings
            if (mappers.isEmpty()) {
                return;
            }

            context = context.createMultiFieldContext();

            ContentPath.Type origPathType = context.path().pathType();
            context.path().pathType(pathType);

            context.path().add(mainField.fieldType().names().shortName());
            for (ObjectCursor<FieldMapper> cursor : mappers.values()) {
                cursor.value.parse(context);
            }
            context.path().remove();
            context.path().pathType(origPathType);
        }

        // No need for locking, because locking is taken care of in ObjectMapper#merge and DocumentMapper#merge
        public void merge(Mapper mergeWith, MergeResult mergeResult) throws MergeMappingException {
            AbstractFieldMapper mergeWithMultiField = (AbstractFieldMapper) mergeWith;

            List<FieldMapper> newFieldMappers = null;
            ImmutableOpenMap.Builder<String, FieldMapper> newMappersBuilder = null;

            for (ObjectCursor<FieldMapper> cursor : mergeWithMultiField.multiFields.mappers.values()) {
                FieldMapper mergeWithMapper = cursor.value;
                Mapper mergeIntoMapper = mappers.get(mergeWithMapper.fieldType().names().shortName());
                if (mergeIntoMapper == null) {
                    // no mapping, simply add it if not simulating
                    if (!mergeResult.simulate()) {
                        // we disable the all in multi-field mappers
                        if (mergeWithMapper instanceof AllFieldMapper.IncludeInAll) {
                            ((AllFieldMapper.IncludeInAll) mergeWithMapper).unsetIncludeInAll();
                        }
                        if (newMappersBuilder == null) {
                            newMappersBuilder = ImmutableOpenMap.builder(mappers);
                        }
                        newMappersBuilder.put(mergeWithMapper.fieldType().names().shortName(), mergeWithMapper);
                        if (mergeWithMapper instanceof AbstractFieldMapper) {
                            if (newFieldMappers == null) {
                                newFieldMappers = new ArrayList<>(2);
                            }
                            newFieldMappers.add(mergeWithMapper);
                        }
                    }
                } else {
                    mergeIntoMapper.merge(mergeWithMapper, mergeResult);
                }
            }

            // first add all field mappers
            if (newFieldMappers != null) {
                mergeResult.addFieldMappers(newFieldMappers);
            }
            // now publish mappers
            if (newMappersBuilder != null) {
                mappers = newMappersBuilder.build();
            }
        }

        public Iterator<Mapper> iterator() {
            return Iterators.transform(mappers.values().iterator(), new Function<ObjectCursor<FieldMapper>, Mapper>() {
                @Override
                public Mapper apply(@Nullable ObjectCursor<FieldMapper> cursor) {
                    return cursor.value;
                }
            });
        }

        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            if (pathType != Defaults.PATH_TYPE) {
                builder.field("path", pathType.name().toLowerCase(Locale.ROOT));
            }
            if (!mappers.isEmpty()) {
                // sort the mappers so we get consistent serialization format
                Mapper[] sortedMappers = mappers.values().toArray(Mapper.class);
                Arrays.sort(sortedMappers, new Comparator<Mapper>() {
                    @Override
                    public int compare(Mapper o1, Mapper o2) {
                        return o1.name().compareTo(o2.name());
                    }
                });
                builder.startObject("fields");
                for (Mapper mapper : sortedMappers) {
                    mapper.toXContent(builder, params);
                }
                builder.endObject();
            }
            return builder;
        }
    }

    /**
     * Represents a list of fields with optional boost factor where the current field should be copied to
     */
    public static class CopyTo {

        private final ImmutableList<String> copyToFields;

        private CopyTo(ImmutableList<String> copyToFields) {
            this.copyToFields = copyToFields;
        }

        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            if (!copyToFields.isEmpty()) {
                builder.startArray("copy_to");
                for (String field : copyToFields) {
                    builder.value(field);
                }
                builder.endArray();
            }
            return builder;
        }

        public static class Builder {
            private final ImmutableList.Builder<String> copyToBuilders = ImmutableList.builder();

            public Builder add(String field) {
                copyToBuilders.add(field);
                return this;
            }

            public CopyTo build() {
                return new CopyTo(copyToBuilders.build());
            }
        }

        public List<String> copyToFields() {
            return copyToFields;
        }
    }

    /**
     * Returns if this field is only generated when indexing. For example, the field of type token_count
     */
    @Override
    public boolean isGenerated() {
        return false;
    }
}

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

package org.elasticsearch.script.expression;

import org.apache.lucene.queries.function.ValueSource;
import org.elasticsearch.index.fielddata.AtomicNumericFieldData;
import org.elasticsearch.search.MultiValueMode;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

class DateMethodFunctionValues extends FieldDataFunctionValues {
    private final int calendarType;
    private final Calendar calendar;

    DateMethodFunctionValues(ValueSource parent, MultiValueMode multiValueMode,  AtomicNumericFieldData data, int calendarType) {
        super(parent, multiValueMode, data);

        this.calendarType = calendarType;
        calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.ROOT);
    }

    @Override
    public double doubleVal(int docId) {
        long millis = (long)dataAccessor.get(docId);
        calendar.setTimeInMillis(millis);
        return calendar.get(calendarType);
    }
}

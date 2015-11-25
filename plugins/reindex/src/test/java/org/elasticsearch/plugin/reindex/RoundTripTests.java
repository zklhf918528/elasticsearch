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

package org.elasticsearch.plugin.reindex;

import org.elasticsearch.action.WriteConsistencyLevel;
import org.elasticsearch.action.bulk.BulkItemResponse.Failure;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.ShardSearchFailure;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.io.stream.Streamable;
import org.elasticsearch.common.lucene.uid.Versions;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptService.ScriptType;
import org.elasticsearch.search.SearchShardTarget;
import org.elasticsearch.test.ESTestCase;

import java.io.IOException;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.apache.lucene.util.TestUtil.randomSimpleString;

/**
 * Round trip tests for all Streamable things declared in this plugin.
 */
public class RoundTripTests extends ESTestCase {
    public void testReindexRequest() throws IOException {
        ReindexRequest reindex = new ReindexRequest(new SearchRequest(), new IndexRequest());
        randomRequest(reindex);
        reindex.getDestination().version(randomFrom(Versions.MATCH_ANY, Versions.MATCH_DELETED, 12L, 1L, 123124L, 12L));
        reindex.getDestination().index("test");
        ReindexRequest tripped = new ReindexRequest();
        roundTrip(reindex, tripped);
        assertRequestEquals(reindex, tripped);
        assertEquals(reindex.getDestination().version(), tripped.getDestination().version());
        assertEquals(reindex.getDestination().index(), tripped.getDestination().index());
    }

    public void testUpdateByQueryRequest() throws IOException {
        UpdateByQueryRequest update = new UpdateByQueryRequest(new SearchRequest());
        randomRequest(update);
        UpdateByQueryRequest tripped = new UpdateByQueryRequest();
        roundTrip(update, tripped);
        assertRequestEquals(update, tripped);
    }

    public void testReindexResponse() throws IOException {
        ReindexResponse response = new ReindexResponse(randomPositiveLong(), randomPositiveLong(), randomPositiveLong(),
                randomPositiveInt(), randomPositiveLong(), randomPositiveLong(), randomIndexingFailures(), randomSearchFailures());
        ReindexResponse tripped = new ReindexResponse();
        roundTrip(response, tripped);
        assertResponseEquals(response, tripped);
        assertEquals(response.getCreated(), tripped.getCreated());
    }

    public void testBulkIndexByScrollResponse() throws IOException {
        BulkIndexByScrollResponse response = new BulkIndexByScrollResponse(randomPositiveLong(), randomPositiveLong(), randomPositiveInt(),
                randomPositiveLong(), randomPositiveLong(), randomIndexingFailures(), randomSearchFailures());
        BulkIndexByScrollResponse tripped = new BulkIndexByScrollResponse();
        roundTrip(response, tripped);
        assertResponseEquals(response, tripped);
    }

    private void randomRequest(AbstractBulkIndexByScrollRequest<?> request) {
        request.getSource().indices("test");
        request.getSource().source().size(between(1, 1000));
        request.setSize(random().nextBoolean() ? between(1, Integer.MAX_VALUE) : -1);
        request.setAbortOnVersionConflict(random().nextBoolean());
        request.setRefresh(rarely());
        request.setTimeout(TimeValue.parseTimeValue(randomTimeValue(), null, "test"));
        request.setConsistency(randomFrom(WriteConsistencyLevel.values()));
        request.setScript(random().nextBoolean() ? null : randomScript());
    }

    private void assertRequestEquals(AbstractBulkIndexByScrollRequest<?> request,
            AbstractBulkIndexByScrollRequest<?> tripped) {
        assertArrayEquals(request.getSource().indices(), tripped.getSource().indices());
        assertEquals(request.getSource().source().size(), tripped.getSource().source().size());
        assertEquals(request.isAbortOnVersionConflict(), tripped.isAbortOnVersionConflict());
        assertEquals(request.isRefresh(), tripped.isRefresh());
        assertEquals(request.getTimeout(), tripped.getTimeout());
        assertEquals(request.getConsistency(), tripped.getConsistency());
        assertEquals(request.getScript(), tripped.getScript());
    }

    private List<Failure> randomIndexingFailures() {
        return usually() ? emptyList()
                : singletonList(new Failure(randomSimpleString(random()), randomSimpleString(random()),
                        randomSimpleString(random()), new IllegalArgumentException("test")));
    }

    private List<ShardSearchFailure> randomSearchFailures() {
        return usually() ? emptyList()
                : singletonList(new ShardSearchFailure(randomSimpleString(random()), new SearchShardTarget(randomSimpleString(random()),
                        randomSimpleString(random()), randomInt()), randomFrom(RestStatus.values())));
    }


    private void assertResponseEquals(BulkIndexByScrollResponse response, BulkIndexByScrollResponse tripped) {
        assertEquals(response.getTook(), tripped.getTook());
        assertEquals(response.getUpdated(), tripped.getUpdated());
        assertEquals(response.getBatches(), tripped.getBatches());
        assertEquals(response.getVersionConflicts(), tripped.getVersionConflicts());
        assertEquals(response.getNoops(), tripped.getNoops());
        assertEquals(response.getIndexingFailures().size(), tripped.getIndexingFailures().size());
        for (int i = 0; i < response.getIndexingFailures().size(); i++) {
            Failure expected = response.getIndexingFailures().get(i);
            Failure actual = tripped.getIndexingFailures().get(i);
            assertEquals(expected.getIndex(), actual.getIndex());
            assertEquals(expected.getType(), actual.getType());
            assertEquals(expected.getId(), actual.getId());
            assertEquals(expected.getMessage(), actual.getMessage());
            assertEquals(expected.getStatus(), actual.getStatus());
        }
    }

    private void roundTrip(Streamable example, Streamable empty) throws IOException {
        BytesStreamOutput out = new BytesStreamOutput();
        example.writeTo(out);
        empty.readFrom(out.bytes().streamInput());
    }

    private Script randomScript() {
        return new Script(randomSimpleString(random()), // Name
                randomFrom(ScriptType.values()), // Type
                random().nextBoolean() ? null : randomSimpleString(random()), // Language
                emptyMap()); // Params
    }

    private long randomPositiveLong() {
        long l;
        do {
            l = randomLong();
        } while (l < 0);
        return l;
    }

    private int randomPositiveInt() {
        return randomInt(Integer.MAX_VALUE);
    }
}
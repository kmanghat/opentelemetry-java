/*
 * Copyright 2019, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.sdk.contrib.zpages;

import static com.google.common.truth.Truth.assertThat;

import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.TracerSdkProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.MockitoAnnotations;

/** Unit tests for {@link TracezDataAggregator}. */
@RunWith(JUnit4.class)
public final class TracezDataAggregatorTest {
  private final TracerSdkProvider tracerSdkProvider = TracerSdkProvider.builder().build();
  private final Tracer tracer = tracerSdkProvider.get("TracezDataAggregatorTest");
  private static final String SPAN_NAME_ONE = "one";
  private static final String SPAN_NAME_TWO = "two";

  private TracezSpanProcessor spanProcessor;
  private TracezDataAggregator dataAggregator;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    spanProcessor = TracezSpanProcessor.newBuilder().build();
    tracerSdkProvider.addSpanProcessor(spanProcessor);
    dataAggregator = new TracezDataAggregator(spanProcessor);
  }

  @Test
  public void getRunningSpanCounts_noSpans() {
    /* getRunningSpanCounts should return a an empty map */
    Map<String, Integer> counts = dataAggregator.getRunningSpanCounts();
    assertThat(counts.size()).isEqualTo(0);
    assertThat(counts.get(SPAN_NAME_ONE)).isNull();
    assertThat(counts.get(SPAN_NAME_TWO)).isNull();
  }

  @Test
  public void getRunningSpanCounts_twoSpanNames() {
    Span span1 = tracer.spanBuilder(SPAN_NAME_ONE).startSpan();
    Span span2 = tracer.spanBuilder(SPAN_NAME_TWO).startSpan();
    /* getRunningSpanCounts should return a map with 2 different span names */
    Map<String, Integer> counts = dataAggregator.getRunningSpanCounts();
    assertThat(counts.size()).isEqualTo(2);
    assertThat(counts.get(SPAN_NAME_ONE)).isEqualTo(1);
    assertThat(counts.get(SPAN_NAME_TWO)).isEqualTo(1);

    span1.end();
    /* getRunningSpanCounts should return a map with 1 unique span name */
    counts = dataAggregator.getRunningSpanCounts();
    assertThat(counts.size()).isEqualTo(1);
    assertThat(counts.get(SPAN_NAME_ONE)).isNull();
    assertThat(counts.get(SPAN_NAME_TWO)).isEqualTo(1);

    span2.end();
    /* getRunningSpanCounts should return a map with no span names */
    counts = dataAggregator.getRunningSpanCounts();
    assertThat(counts.size()).isEqualTo(0);
    assertThat(counts.get(SPAN_NAME_ONE)).isNull();
    assertThat(counts.get(SPAN_NAME_TWO)).isNull();
  }

  @Test
  public void getRunningSpanCounts_oneSpanName() {
    Span span1 = tracer.spanBuilder(SPAN_NAME_ONE).startSpan();
    Span span2 = tracer.spanBuilder(SPAN_NAME_ONE).startSpan();
    Span span3 = tracer.spanBuilder(SPAN_NAME_ONE).startSpan();
    /* getRunningSpanCounts should return a map with 1 span name */
    Map<String, Integer> counts = dataAggregator.getRunningSpanCounts();
    assertThat(counts.size()).isEqualTo(1);
    assertThat(counts.get(SPAN_NAME_ONE)).isEqualTo(3);
    span1.end();
    span2.end();
    span3.end();
    /* getRunningSpanCounts should return a map with no span names */
    counts = dataAggregator.getRunningSpanCounts();
    assertThat(counts.size()).isEqualTo(0);
    assertThat(counts.get(SPAN_NAME_ONE)).isNull();
  }

  @Test
  public void getRunningSpansByName_noSpans() {
    /* getRunningSpansByName should return an empty List */
    assertThat(dataAggregator.getRunningSpansByName(SPAN_NAME_ONE).size()).isEqualTo(0);
    assertThat(dataAggregator.getRunningSpansByName(SPAN_NAME_TWO).size()).isEqualTo(0);
  }

  @Test
  public void getRunningSpansByName_twoSpanNames() {
    Span span1 = tracer.spanBuilder(SPAN_NAME_ONE).startSpan();
    Span span2 = tracer.spanBuilder(SPAN_NAME_TWO).startSpan();
    /* getRunningSpansByName should return a List with only the corresponding span */
    assertThat(dataAggregator.getRunningSpansByName(SPAN_NAME_ONE))
        .containsExactly(((ReadableSpan) span1).toSpanData());
    assertThat(dataAggregator.getRunningSpansByName(SPAN_NAME_TWO))
        .containsExactly(((ReadableSpan) span2).toSpanData());
    span1.end();
    span2.end();
    /* getRunningSpansByName should return an empty List for each span name */
    assertThat(dataAggregator.getRunningSpansByName(SPAN_NAME_ONE).size()).isEqualTo(0);
    assertThat(dataAggregator.getRunningSpansByName(SPAN_NAME_TWO).size()).isEqualTo(0);
  }

  @Test
  public void getRunningSpansByName_oneSpanName() {
    Span span1 = tracer.spanBuilder(SPAN_NAME_ONE).startSpan();
    Span span2 = tracer.spanBuilder(SPAN_NAME_ONE).startSpan();
    Span span3 = tracer.spanBuilder(SPAN_NAME_ONE).startSpan();
    /* getRunningSpansByName should return a List with all 3 spans */
    List<SpanData> spans = dataAggregator.getRunningSpansByName(SPAN_NAME_ONE);
    assertThat(spans.size()).isEqualTo(3);
    assertThat(spans).contains(((ReadableSpan) span1).toSpanData());
    assertThat(spans).contains(((ReadableSpan) span2).toSpanData());
    assertThat(spans).contains(((ReadableSpan) span3).toSpanData());
    span1.end();
    span2.end();
    span3.end();
    /* getRunningSpansByName should return an empty List */
    assertThat(dataAggregator.getRunningSpansByName(SPAN_NAME_ONE).size()).isEqualTo(0);
  }
}

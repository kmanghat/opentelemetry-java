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
import static org.mockito.Mockito.when;

import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.SpanId;
import io.opentelemetry.trace.TraceFlags;
import io.opentelemetry.trace.TraceId;
import io.opentelemetry.trace.TraceState;
import java.util.Collection;
import java.util.Properties;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Unit tests for {@link TraceZSpanProcessor}. */
@RunWith(JUnit4.class)
public final class TraceZSpanProcessorTest {
  @Mock private ReadableSpan readableSpan;
  private static final SpanContext SAMPLED_SPAN_CONTEXT =
      SpanContext.create(
          TraceId.getInvalid(),
          SpanId.getInvalid(),
          TraceFlags.builder().setIsSampled(true).build(),
          TraceState.builder().build());
  private static final SpanContext NOT_SAMPLED_SPAN_CONTEXT = SpanContext.getInvalid();

  private static void assertSpanCacheSizes(
      TraceZSpanProcessor spanProcessor, int runningSpanCacheSize, int completedSpanCacheSize) {
    Collection<ReadableSpan> runningSpans = spanProcessor.getRunningSpans();
    Collection<ReadableSpan> completedSpans = spanProcessor.getCompletedSpans();
    assertThat(runningSpans.size()).isEqualTo(runningSpanCacheSize);
    assertThat(completedSpans.size()).isEqualTo(completedSpanCacheSize);
  }

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void onStart_onEnd_SampledSpan() {
    TraceZSpanProcessor spanProcessor = TraceZSpanProcessor.newBuilder().build();
    when(readableSpan.getSpanContext()).thenReturn(SAMPLED_SPAN_CONTEXT);
    spanProcessor.onStart(readableSpan);
    assertSpanCacheSizes(spanProcessor, 1, 0);
    spanProcessor.onEnd(readableSpan);
    assertSpanCacheSizes(spanProcessor, 0, 1);
  }

  @Test
  public void onStart_NotSampledSpan() {
    TraceZSpanProcessor spanProcessor = TraceZSpanProcessor.newBuilder().build();
    when(readableSpan.getSpanContext()).thenReturn(NOT_SAMPLED_SPAN_CONTEXT);
    spanProcessor.onStart(readableSpan);
    assertSpanCacheSizes(spanProcessor, 0, 0);
  }

  @Test
  public void buildFromProperties_defaultSampledFlag() {
    Properties properties = new Properties();
    TraceZSpanProcessor spanProcessor =
        TraceZSpanProcessor.newBuilder().readProperties(properties).build();

    when(readableSpan.getSpanContext()).thenReturn(NOT_SAMPLED_SPAN_CONTEXT);
    spanProcessor.onStart(readableSpan);
    assertSpanCacheSizes(spanProcessor, 0, 0);
  }

  @Test
  public void buildFromProperties_onlySampledTrue() {
    Properties properties = new Properties();
    properties.setProperty("otel.ssp.export.sampled", "true");
    TraceZSpanProcessor spanProcessor =
        TraceZSpanProcessor.newBuilder().readProperties(properties).build();

    when(readableSpan.getSpanContext()).thenReturn(NOT_SAMPLED_SPAN_CONTEXT);
    spanProcessor.onStart(readableSpan);
    assertSpanCacheSizes(spanProcessor, 0, 0);
  }

  @Test
  public void buildFromProperties_onlySampledFalse() {
    Properties properties = new Properties();
    properties.setProperty("otel.ssp.export.sampled", "false");
    TraceZSpanProcessor spanProcessor =
        TraceZSpanProcessor.newBuilder().readProperties(properties).build();

    when(readableSpan.getSpanContext()).thenReturn(NOT_SAMPLED_SPAN_CONTEXT);
    spanProcessor.onStart(readableSpan);
    assertSpanCacheSizes(spanProcessor, 1, 0);
    spanProcessor.onEnd(readableSpan);
    assertSpanCacheSizes(spanProcessor, 0, 1);
  }
}

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

import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.annotation.concurrent.ThreadSafe;

/**
 * A {@link SpanProcessor} implementation for the traceZ zPage.
 *
 * <p>Configuration options for {@link io.opentelemetry.sdk.contrib.zpages.TraceZSpanProcessor} can
 * be read from system properties, environment variables, or {@link java.util.Properties} objects.
 *
 * <p>For system properties and {@link java.util.Properties} objects, {@link
 * io.opentelemetry.sdk.contrib.zpages.TraceZSpanProcessor} will look for the following names:
 *
 * <ul>
 *   <li>{@code otel.ssp.export.sampled}: sets whether only sampled spans should be exported.
 * </ul>
 *
 * <p>For environment variables, {@link io.opentelemetry.sdk.contrib.zpages.TraceZSpanProcessor}
 * will look for the following names:
 *
 * <ul>
 *   <li>{@code OTEL_SSP_EXPORT_SAMPLED}: sets whether only sampled spans should be exported.
 * </ul>
 */
@ThreadSafe
public final class TraceZDataAggregator {
  private final TraceZSpanProcessor spanProcessor;

  /**
   * Constructor for {@link io.opentelemetry.sdk.contrib.zpages.TraceZDataAggregator}.
   *
   * @param spanProcessor collects span data.
   */
  public TraceZDataAggregator(TraceZSpanProcessor spanProcessor) {
    this.spanProcessor = spanProcessor;
  }

  /**
   * Returns a Collection of all running spans for {@link
   * io.opentelemetry.sdk.contrib.zpages.TraceZDataAggregator}.
   *
   * @param spanName name to filter returned spans.
   * @return a List of {@link io.opentelemetry.sdk.trace.data.SpanData}.\
   */
  public List<SpanData> getRunningSpansByName(String spanName) {
    Collection<ReadableSpan> allRunningSpans = spanProcessor.getRunningSpans();
    List<SpanData> runningSpanData = new ArrayList<>();
    for (ReadableSpan span : allRunningSpans) {
      if (span.getName().equals(spanName)) {
        runningSpanData.add(span.toSpanData());
      }
    }
    return runningSpanData;
  }
}

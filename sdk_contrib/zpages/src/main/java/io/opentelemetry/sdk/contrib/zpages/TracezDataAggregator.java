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
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.concurrent.ThreadSafe;

/**
 * A data aggregator for the traceZ zPage.
 *
 * <p>The traceZ data aggregator compiles information about the running spans, span latencies, and
 * error spans for the frontend of the zPage.
 */
@ThreadSafe
public final class TracezDataAggregator {
  private final TracezSpanProcessor spanProcessor;

  /**
   * Constructor for {@link io.opentelemetry.sdk.contrib.zpages.TracezDataAggregator}.
   *
   * @param spanProcessor collects span data.
   */
  public TracezDataAggregator(TracezSpanProcessor spanProcessor) {
    this.spanProcessor = spanProcessor;
  }

  /**
   * Returns a Map of the running span counts for {@link
   * io.opentelemetry.sdk.contrib.zpages.TracezDataAggregator}.
   *
   * @return a Map of span counts for each span name.
   */
  public Map<String, Integer> getRunningSpanCounts() {
    Collection<ReadableSpan> allRunningSpans = spanProcessor.getRunningSpans();
    Map<String, Integer> numSpansPerName = new HashMap<>();
    for (ReadableSpan span : allRunningSpans) {
      Integer prevValue = numSpansPerName.get(span.getName());
      numSpansPerName.put(span.getName(), prevValue != null ? prevValue + 1 : 1);
    }
    return numSpansPerName;
  }

  /**
   * Returns a List of all running spans with a given span name for {@link
   * io.opentelemetry.sdk.contrib.zpages.TracezDataAggregator}.
   *
   * @param spanName name to filter returned spans.
   * @return a List of {@link io.opentelemetry.sdk.trace.data.SpanData}.
   */
  public List<SpanData> getRunningSpansByName(String spanName) {
    Collection<ReadableSpan> allRunningSpans = spanProcessor.getRunningSpans();
    List<SpanData> filteredSpans = new ArrayList<>();
    for (ReadableSpan span : allRunningSpans) {
      if (span.getName().equals(spanName)) {
        filteredSpans.add(span.toSpanData());
      }
    }
    return filteredSpans;
  }
}

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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
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

  public enum LatencyBoundaries {
    /** Stores finished successful requests of duration within the interval [0, 10us). */
    ZERO_MICROSx10(0, TimeUnit.MICROSECONDS.toNanos(10)),

    /** Stores finished successful requests of duration within the interval [10us, 100us). */
    MICROSx10_MICROSx100(TimeUnit.MICROSECONDS.toNanos(10), TimeUnit.MICROSECONDS.toNanos(100)),

    /** Stores finished successful requests of duration within the interval [100us, 1ms). */
    MICROSx100_MILLIx1(TimeUnit.MICROSECONDS.toNanos(100), TimeUnit.MILLISECONDS.toNanos(1)),

    /** Stores finished successful requests of duration within the interval [1ms, 10ms). */
    MILLIx1_MILLIx10(TimeUnit.MILLISECONDS.toNanos(1), TimeUnit.MILLISECONDS.toNanos(10)),

    /** Stores finished successful requests of duration within the interval [10ms, 100ms). */
    MILLIx10_MILLIx100(TimeUnit.MILLISECONDS.toNanos(10), TimeUnit.MILLISECONDS.toNanos(100)),

    /** Stores finished successful requests of duration within the interval [100ms, 1sec). */
    MILLIx100_SECONDx1(TimeUnit.MILLISECONDS.toNanos(100), TimeUnit.SECONDS.toNanos(1)),

    /** Stores finished successful requests of duration within the interval [1sec, 10sec). */
    SECONDx1_SECONDx10(TimeUnit.SECONDS.toNanos(1), TimeUnit.SECONDS.toNanos(10)),

    /** Stores finished successful requests of duration within the interval [10sec, 100sec). */
    SECONDx10_SECONDx100(TimeUnit.SECONDS.toNanos(10), TimeUnit.SECONDS.toNanos(100)),

    /** Stores finished successful requests of duration greater than or equal to 100sec. */
    SECONDx100_MAX(TimeUnit.SECONDS.toNanos(100), Long.MAX_VALUE);

    private final long latencyLowerBound;
    private final long latencyUpperBound;

    /**
     * Constructs a {@code LatencyBoundaries} with the given boundaries and label.
     *
     * @param latencyLowerBound the latency lower bound of the bucket.
     * @param latencyUpperBound the latency upper bound of the bucket.
     */
    LatencyBoundaries(long latencyLowerBound, long latencyUpperBound) {
      this.latencyLowerBound = latencyLowerBound;
      this.latencyUpperBound = latencyUpperBound;
    }

    /**
     * Returns the latency lower bound of the bucket.
     *
     * @return the latency lower bound of the bucket.
     */
    private long getLatencyLowerBound() {
      return latencyLowerBound;
    }

    /**
     * Returns the latency upper bound of the bucket.
     *
     * @return the latency upper bound of the bucket.
     */
    private long getLatencyUpperBound() {
      return latencyUpperBound;
    }
  }

  /**
   * Returns a Map of counts for the completed spans within [lowerBound, upperBound) {@link
   * io.opentelemetry.sdk.contrib.zpages.TracezDataAggregator}.
   *
   * @param lowerBound latency lower bound (inclusive)
   * @param upperBound latency upper bound (exclusive)
   * @return a Map of span counts for each span name within the bounds.
   */
  public Map<String, Integer> getSpanLatencyCounts(long lowerBound, long upperBound) {
    Collection<ReadableSpan> allCompletedSpans = spanProcessor.getCompletedSpans();
    Map<String, Integer> numSpansPerName = new HashMap<>();
    for (ReadableSpan span : allCompletedSpans) {
      if (span.getLatencyNanos() >= lowerBound && span.getLatencyNanos() < upperBound) {
        Integer prevValue = numSpansPerName.get(span.getName());
        numSpansPerName.put(span.getName(), prevValue != null ? prevValue + 1 : 1);
      }
    }
    return numSpansPerName;
  }

  /**
   * Returns a nested Map of counts for all completed spans {@link
   * io.opentelemetry.sdk.contrib.zpages.TracezDataAggregator}.
   *
   * @return a Map of span-count Maps for each latency boundary.
   */
  public Map<LatencyBoundaries, Map<String, Integer>> getSpanLatencyCounts() {
    Map<LatencyBoundaries, Map<String, Integer>> numSpansPerBoundary =
        new EnumMap<>(LatencyBoundaries.class);
    for (LatencyBoundaries bucket : LatencyBoundaries.values()) {
      numSpansPerBoundary.put(
          bucket,
          getSpanLatencyCounts(bucket.getLatencyLowerBound(), bucket.getLatencyUpperBound()));
    }
    return numSpansPerBoundary;
  }

  /**
   * Returns a List of all completed spans with a given span name between [lowerBound, upperBound)
   * for {@link io.opentelemetry.sdk.contrib.zpages.TracezDataAggregator}.
   *
   * @param spanName name to filter returned spans.
   * @param lowerBound latency lower bound (inclusive)
   * @param upperBound latency upper bound (exclusive)
   * @return a List of {@link io.opentelemetry.sdk.trace.data.SpanData}.
   */
  public List<SpanData> getCompletedSpansByLatency(
      String spanName, long lowerBound, long upperBound) {
    Collection<ReadableSpan> allCompletedSpans = spanProcessor.getCompletedSpans();
    List<SpanData> filteredSpans = new ArrayList<>();
    for (ReadableSpan span : allCompletedSpans) {
      if (span.getName().equals(spanName)
          && span.getLatencyNanos() >= lowerBound
          && span.getLatencyNanos() < upperBound) {
        filteredSpans.add(span.toSpanData());
      }
    }
    return filteredSpans;
  }
}

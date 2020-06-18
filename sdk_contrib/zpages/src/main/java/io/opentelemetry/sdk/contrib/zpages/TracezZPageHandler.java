/*
 * Copyright 2020, OpenTelemetry Authors
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

import static com.google.common.html.HtmlEscapers.htmlEscaper;

import com.google.common.base.Charsets;
import io.opentelemetry.common.AttributeValue;
import io.opentelemetry.sdk.contrib.zpages.TracezDataAggregator.LatencyBoundaries;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.SpanData.Event;
import io.opentelemetry.trace.SpanId;
import io.opentelemetry.trace.Status;
import io.opentelemetry.trace.Status.CanonicalCode;
import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

final class TracezZPageHandler extends ZPageHandler {
  private enum SampleType {
    RUNNING(0),
    LATENCY(1),
    ERROR(2),
    UNKNOWN(-1);

    private final int value;

    SampleType(int value) {
      this.value = value;
    }

    static SampleType fromString(String str) {
      int value = Integer.parseInt(str);
      switch (value) {
        case 0:
          return RUNNING;
        case 1:
          return LATENCY;
        case 2:
          return ERROR;
        default:
          return UNKNOWN;
      }
    }

    int getValue() {
      return value;
    }
  }

  private static final String TRACEZ_URL = "/tracez";
  // Background color used for zebra striping rows of summary table
  private static final String ZEBRA_STRIPE_COLOR = "#f0f0f0";
  // Color for sampled traceIds
  private static final String SAMPLED_TRACE_ID_COLOR = "#C1272D";
  // Color for not sampled traceIds
  private static final String NOT_SAMPLED_TRACE_ID_COLOR = "black";
  // Query string parameter name for span name
  private static final String PARAM_SPAN_NAME = "zspanname";
  // Query string parameter name for type to display
  // * 0 = running, 1 = latency, 2 = error
  private static final String PARAM_SAMPLE_TYPE = "ztype";
  // Query string parameter name for sub-type:
  // * for latency based sampled spans [0, 8] corresponds to each latency boundaries
  //   where 0 corresponds to the first boundary
  // * for error based sampled spans [0, 15], 0 means all, otherwise the error code
  private static final String PARAM_SAMPLE_SUB_TYPE = "zsubtype";
  @javax.annotation.Nullable private final TracezDataAggregator dataAggregator;
  // Map from LatencyBoundaries to human readable string on the UI
  private static final Map<LatencyBoundaries, String> LATENCY_BOUNDARIES_STRING_MAP =
      buildLatencyBoundariesStringMap();

  private TracezZPageHandler(TracezDataAggregator dataAggregator) {
    this.dataAggregator = dataAggregator;
  }

  /**
   * Constructs a new {@code TracezZPageHandler}.
   *
   * @return a new {@code TracezZPageHandler}.
   */
  static TracezZPageHandler create(@javax.annotation.Nullable TracezDataAggregator dataAggregator) {
    return new TracezZPageHandler(dataAggregator);
  }

  @Override
  public String getUrlPath() {
    return TRACEZ_URL;
  }

  /**
   * Emits CSS Styles to the {@link PrintWriter} {@code out}. Content emited by this function should
   * be enclosed by <head></head> tag.
   *
   * @param out The {@link PrintWriter} {@code out}.
   */
  private static void emitHtmlStyle(PrintWriter out) {
    out.write("<style>");
    out.write(ZPageStyle.style);
    out.write("</style");
  }

  /**
   * Emits the header of the summary table to the {@link PrintWriter} {@code out}.
   *
   * @param out The {@link PrintWriter} {@code out}.
   * @param formatter A {@link Formatter} for formatting HTML expressions.
   */
  private static void emitSummaryTableHeader(PrintWriter out, Formatter formatter) {
    // First row
    out.write("<tr class=\"bg-color\">");
    out.write("<th colspan=1 class=\"header-text\"><b>Span Name</b></th>");
    out.write("<th colspan=1 class=\"header-text border-left-white\"><b>Running</b></th>");
    out.write("<th colspan=9 class=\"header-text border-left-white\"><b>Latency Samples</b></th>");
    out.write("<th colspan=1 class=\"header-text border-left-white\"><b>Error Samples</b></th>");
    out.write("</tr>");

    // Second row
    out.write("<tr class=\"bg-color\">");
    out.write("<th colspan=1></th>");
    out.write("<th colspan=1 class=\"border-left-white\"></th>");
    for (LatencyBoundaries latencyBoundaries : LatencyBoundaries.values()) {
      formatter.format(
          "<th colspan=1 class=\"border-left-white align-center\""
              + "style=\"color: #fff;\"><b>[%s]</b></th>",
          LATENCY_BOUNDARIES_STRING_MAP.get(latencyBoundaries));
    }
    out.write("<th colspan=1 class=\"border-left-white\"></th>");
    out.write("</tr>");
  }

  /**
   * Emits a single cell of the summary table depends on the paramters passed in, to the {@link
   * PrintWriter} {@code out}.
   *
   * @param out The {@link PrintWriter} {@code out}.
   * @param formatter {@link Formatter} for formatting HTML expressions.
   * @param spanName The name of the corresponding span.
   * @param numOfSamples The number of samples of the corresponding span.
   * @param type The type of the corresponding span (running, latency, error).
   * @param subtype The sub-type of the corresponding span (latency [0, 8], error [0, 15]).
   */
  private static void emitSummaryTableCell(
      PrintWriter out,
      Formatter formatter,
      String spanName,
      int numOfSamples,
      SampleType type,
      int subtype)
      throws UnsupportedEncodingException {
    // If numOfSamples is greater than 0, emit a link to see detailed span information
    // If numOfSamples is smaller than 0, print the text "N/A", otherwise print the text "0"
    if (numOfSamples > 0) {
      formatter.format(
          "<td class=\"align-center border-left-dark\"><a href=\"?%s=%s&%s=%d&%s=%d\">%d</a></td>",
          PARAM_SPAN_NAME,
          URLEncoder.encode(spanName, "UTF-8"),
          PARAM_SAMPLE_TYPE,
          type.getValue(),
          PARAM_SAMPLE_SUB_TYPE,
          subtype,
          numOfSamples);
    } else if (numOfSamples < 0) {
      out.write("<td class=\"align-center border-left-dark\">N/A</td>");
    } else {
      out.write("<td class=\"align-center border-left-dark\">0</td>");
    }
  }

  /**
   * Emits the summary table of running spans and sampled spans to the {@link PrintWriter} {@code
   * out}.
   *
   * @param out The {@link PrintWriter} {@code out}.
   * @param formatter A {@link Formatter} for formatting HTML expressions.
   */
  private void emitSummaryTable(PrintWriter out, Formatter formatter)
      throws UnsupportedEncodingException {
    if (dataAggregator == null) {
      return;
    }
    out.write("<table style=\"border-spacing: 0; border: 1px solid #363636;\">");
    emitSummaryTableHeader(out, formatter);

    Set<String> spanNames = dataAggregator.getSpanNames();
    boolean zebraStripe = false;

    Map<String, Integer> runningSpanCounts = dataAggregator.getRunningSpanCounts();
    Map<String, Map<LatencyBoundaries, Integer>> latencySpanCounts =
        dataAggregator.getSpanLatencyCounts();
    Map<String, Integer> errorSpanCounts = dataAggregator.getErrorSpanCounts();
    for (String spanName : spanNames) {
      if (zebraStripe) {
        formatter.format("<tr style=\"background-color: %s\">", ZEBRA_STRIPE_COLOR);
      } else {
        out.write("<tr>");
      }
      zebraStripe = !zebraStripe;
      formatter.format("<td>%s</td>", htmlEscaper().escape(spanName));

      // Running spans column
      int numOfRunningSpans =
          runningSpanCounts.containsKey(spanName) ? runningSpanCounts.get(spanName) : 0;
      // subtype is ignored for running spans
      emitSummaryTableCell(out, formatter, spanName, numOfRunningSpans, SampleType.RUNNING, 0);

      // Latency based sampled spans column
      int subtype = 0;
      for (LatencyBoundaries latencyBoundaries : LatencyBoundaries.values()) {
        int numOfLatencySamples =
            latencySpanCounts.containsKey(spanName)
                    && latencySpanCounts.get(spanName).containsKey(latencyBoundaries)
                ? latencySpanCounts.get(spanName).get(latencyBoundaries)
                : 0;
        emitSummaryTableCell(
            out, formatter, spanName, numOfLatencySamples, SampleType.LATENCY, subtype);
        subtype += 1;
      }

      // Error based sampled spans column
      int numOfErrorSamples =
          errorSpanCounts.containsKey(spanName) ? errorSpanCounts.get(spanName) : 0;
      // subtype 0 means all errors
      emitSummaryTableCell(out, formatter, spanName, numOfErrorSamples, SampleType.ERROR, 0);
    }
  }

  private static void emitSpanNameAndCount(
      Formatter formatter, String spanName, int count, SampleType type) {
    formatter.format(
        "<p class=\"align-center\"><b> Span Name: %s </b></p>", htmlEscaper().escape(spanName));
    formatter.format(
        "<p class=\"align-center\"><b> Number of %s: %d </b></p>",
        type == SampleType.RUNNING
            ? "running"
            : type == SampleType.LATENCY ? "latency samples" : "error samples",
        count);
  }

  private static void emitSpanDetails(
      PrintWriter out, Formatter formatter, Collection<SpanData> spans) {
    out.write("<pre>\n");
    formatter.format("%-23s %18s%n", "When", "Elapsed(s)");
    out.write("-------------------------------------------\n");
    for (SpanData span : spans) {
      emitSingleSpan(formatter, span);
    }
    out.write("</pre>\n");
  }

  private static void emitSingleSpan(Formatter formatter, SpanData span) {
    Calendar calendar = Calendar.getInstance();
    calendar.setTimeInMillis(TimeUnit.NANOSECONDS.toMillis(span.getStartEpochNanos()));
    long microsField = TimeUnit.NANOSECONDS.toMicros(span.getStartEpochNanos());
    String elapsedSecondsStr =
        span.getHasEnded()
            ? String.format(
                "%13.6f", (span.getEndEpochNanos() - span.getStartEpochNanos()) * 1.0e-9)
            : String.format("%13s", " ");

    formatter.format(
        "<b>%04d/%02d/%02d-%02d:%02d:%02d.%06d %s     TraceId: <b style=\"color:%s;\">%s</b> "
            + "SpanId: %s ParentSpanId: %s</b>%n",
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH) + 1,
        calendar.get(Calendar.DAY_OF_MONTH),
        calendar.get(Calendar.HOUR_OF_DAY),
        calendar.get(Calendar.MINUTE),
        calendar.get(Calendar.SECOND),
        microsField,
        elapsedSecondsStr,
        span.getTraceFlags().isSampled() ? SAMPLED_TRACE_ID_COLOR : NOT_SAMPLED_TRACE_ID_COLOR,
        span.getTraceId().toLowerBase16(),
        span.getSpanId().toLowerBase16(),
        (span.getParentSpanId() == null
            ? SpanId.getInvalid().toLowerBase16()
            : span.getParentSpanId().toLowerBase16()));

    int lastEntryDayOfYear = calendar.get(Calendar.DAY_OF_YEAR);

    long lastEpochNanos = span.getStartEpochNanos();
    List<Event> timedEvents = span.getEvents();
    Collections.sort(timedEvents, new EventComparator());
    for (Event event : timedEvents) {
      // Special printing so that durations smaller than one second
      // are left padded with blanks instead of '0' characters.
      // E.g.,
      //        Number                  Printout
      //        ---------------------------------
      //        0.000534                  .   534
      //        1.000534                 1.000534
      long deltaMicros = TimeUnit.NANOSECONDS.toMicros(event.getEpochNanos() - lastEpochNanos);
      String deltaString;
      if (deltaMicros >= 1000000) {
        deltaString = String.format("%.6f", (deltaMicros / 1000000.0));
      } else {
        deltaString = String.format(".%6d", deltaMicros);
      }

      calendar.setTimeInMillis(TimeUnit.NANOSECONDS.toMillis(event.getEpochNanos()));
      microsField = TimeUnit.NANOSECONDS.toMicros(event.getEpochNanos());

      int dayOfYear = calendar.get(Calendar.DAY_OF_YEAR);
      if (dayOfYear == lastEntryDayOfYear) {
        formatter.format("%11s", "");
      } else {
        formatter.format(
            "%04d/%02d/%02d-",
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH));
        lastEntryDayOfYear = dayOfYear;
      }

      formatter.format(
          "%02d:%02d:%02d.%06d %13s ... %s%n",
          calendar.get(Calendar.HOUR_OF_DAY),
          calendar.get(Calendar.MINUTE),
          calendar.get(Calendar.SECOND),
          microsField,
          deltaString,
          htmlEscaper().escape(renderEvent(event)));

      lastEpochNanos = event.getEpochNanos();
    }
    Status status = span.getStatus();
    if (status != null) {
      formatter.format("%44s %s%n", "", htmlEscaper().escape(renderStatus(status)));
    }
    formatter.format(
        "%44s %s%n",
        "", htmlEscaper().escape(renderAttributes(span.getResource().getAttributes())));
  }

  private static String renderStatus(Status status) {
    return status.toString();
  }

  private static String renderAttributes(Map<String, AttributeValue> attributes) {
    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append("Attributes:{");
    boolean first = true;
    for (Map.Entry<String, AttributeValue> entry : attributes.entrySet()) {
      if (first) {
        first = false;
      } else {
        stringBuilder.append(", ");
      }
      stringBuilder.append(entry.getKey());
      stringBuilder.append("=");
      stringBuilder.append(entry.getValue().toString());
    }
    stringBuilder.append("}");
    return stringBuilder.toString();
  }

  private static String renderEvent(Event event) {
    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append(event.getName());
    if (!event.getAttributes().isEmpty()) {
      stringBuilder.append(" ");
      stringBuilder.append(renderAttributes(event.getAttributes()));
    }
    return stringBuilder.toString();
  }

  /**
   * Emits HTML body content to the {@link PrintWriter} {@code out}. Content emited by this function
   * should be enclosed by <body></body> tag.
   *
   * @param out The {@link PrintWriter} {@code out}.
   */
  private void emitHtmlBody(Map<String, String> queryMap, PrintWriter out)
      throws UnsupportedEncodingException {
    if (dataAggregator == null) {
      out.write("OpenTelemetry implementation not available.");
      return;
    }
    // Link to OpenTelemetry Logo
    out.write(
        "<img style=\"height: 90px;\""
            + "src=\"https://opentelemetry.io/img/logos/opentelemetry-horizontal-color.png\" />");
    out.write("<h1>TraceZ Summary</h1>");
    Formatter formatter = new Formatter(out, Locale.US);
    emitSummaryTable(out, formatter);
    // spanName will be null if the query parameter doesn't exist in the URL
    String spanName = queryMap.get(PARAM_SPAN_NAME);
    if (spanName != null) {
      // Show detailed information for the corresponding span
      String typeStr = queryMap.get(PARAM_SAMPLE_TYPE);
      if (typeStr != null) {
        List<SpanData> spans = null;
        SampleType type = SampleType.fromString(typeStr);
        if (type == SampleType.UNKNOWN) {
          // Type of sample is garbage value
          return;
        } else if (type == SampleType.RUNNING) {
          // Display running span
          spans = dataAggregator.getRunningSpans(spanName);
          Collections.sort(spans, new SpanDataComparator(/* incremental= */ true));
        } else {
          String subtypeStr = queryMap.get(PARAM_SAMPLE_SUB_TYPE);
          if (subtypeStr != null) {
            int subtype = Integer.parseInt(subtypeStr);
            if (type == SampleType.LATENCY) {
              if (subtype < 0 || subtype >= LatencyBoundaries.values().length) {
                // N/A or out-of-bound check for latency based subtype, valid values: [0, 8]
                return;
              }
              // Display latency based span
              LatencyBoundaries latencyBoundary = LatencyBoundaries.values()[subtype];
              spans =
                  dataAggregator.getOkSpans(
                      spanName,
                      latencyBoundary.getLatencyLowerBound(),
                      latencyBoundary.getLatencyUpperBound());
              Collections.sort(spans, new SpanDataComparator(/* incremental= */ false));
            } else {
              if (subtype < 0 || subtype >= CanonicalCode.values().length) {
                // N/A or out-of-bound cueck for error based subtype, valid values: [0, 15]
                return;
              }
              // Display error based span
            }
          }
        }
        out.write("<h2>Span Details</h2>");
        emitSpanNameAndCount(formatter, spanName, spans == null ? 0 : spans.size(), type);

        if (spans != null) {
          emitSpanDetails(out, formatter, spans);
        }
      }
    }
  }

  @Override
  public void emitHtml(Map<String, String> queryMap, OutputStream outputStream) {
    // PrintWriter for emiting HTML contents
    PrintWriter out =
        new PrintWriter(new BufferedWriter(new OutputStreamWriter(outputStream, Charsets.UTF_8)));
    out.write("<!DOCTYPE html>");
    out.write("<html lang=\"en\">");
    out.write("<head>");
    out.write("<meta charset=\"UTF-8\">");
    out.write(
        "<link rel=\"shortcut icon\" href=\"https://opentelemetry.io/favicon.png\""
            + "type=\"image/png\">");
    out.write(
        "<link href=\"https://fonts.googleapis.com/css?family=Open+Sans:300\""
            + "rel=\"stylesheet\">");
    out.write(
        "<link href=\"https://fonts.googleapis.com/css?family=Roboto\"" + "rel=\"stylesheet\">");
    out.write("<title>TraceZ</title>");
    emitHtmlStyle(out);
    out.write("</head>");
    out.write("<body>");
    try {
      emitHtmlBody(queryMap, out);
    } catch (Throwable t) {
      out.write("Error while generating HTML: " + t);
    }
    out.write("</body>");
    out.write("</html>");
    out.close();
  }

  private static String latencyBoundariesToString(LatencyBoundaries latencyBoundaries) {
    switch (latencyBoundaries) {
      case ZERO_MICROSx10:
        return ">0us";
      case MICROSx10_MICROSx100:
        return ">10us";
      case MICROSx100_MILLIx1:
        return ">100us";
      case MILLIx1_MILLIx10:
        return ">1ms";
      case MILLIx10_MILLIx100:
        return ">10ms";
      case MILLIx100_SECONDx1:
        return ">100ms";
      case SECONDx1_SECONDx10:
        return ">1s";
      case SECONDx10_SECONDx100:
        return ">10s";
      case SECONDx100_MAX:
        return ">100s";
    }
    throw new IllegalArgumentException("No value string available for: " + latencyBoundaries);
  }

  private static Map<LatencyBoundaries, String> buildLatencyBoundariesStringMap() {
    Map<LatencyBoundaries, String> latencyBoundariesMap = new HashMap<>();
    for (LatencyBoundaries latencyBoundaries : LatencyBoundaries.values()) {
      latencyBoundariesMap.put(latencyBoundaries, latencyBoundariesToString(latencyBoundaries));
    }
    return Collections.unmodifiableMap(latencyBoundariesMap);
  }

  private static int compareLongs(long x, long y) {
    if (x < y) {
      return -1;
    } else if (x == y) {
      return 0;
    } else {
      return 1;
    }
  }

  private static final class EventComparator implements Comparator<Event>, Serializable {
    private static final long serialVersionUID = 0;

    @Override
    public int compare(Event e1, Event e2) {
      return compareLongs(e1.getEpochNanos(), e2.getEpochNanos());
    }
  }

  private static final class SpanDataComparator implements Comparator<SpanData>, Serializable {
    private static final long serialVersionUID = 0;
    private final boolean incremental;

    /**
     * Returns a new {@code SpanDataComparator}.
     *
     * @param incremental {@code true} if sorting spans incrementally
     */
    private SpanDataComparator(boolean incremental) {
      this.incremental = incremental;
    }

    @Override
    public int compare(SpanData s1, SpanData s2) {
      return incremental
          ? compareLongs(s1.getStartEpochNanos(), s2.getStartEpochNanos())
          : compareLongs(s2.getStartEpochNanos(), s1.getEndEpochNanos());
    }
  }
}

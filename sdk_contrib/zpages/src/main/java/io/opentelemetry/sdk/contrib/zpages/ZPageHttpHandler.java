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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** An {@link HttpHanlder} that will be used to render HTML pages using any {@code ZPageHandler}. */
final class ZPageHttpHandler implements HttpHandler {
  private final ZPageHandler zpageHandler;

  /** Constructs a new {@code ZPageHttpHandler}. */
  ZPageHttpHandler(ZPageHandler zpageHandler) {
    this.zpageHandler = zpageHandler;
  }

  @VisibleForTesting
  static Map<String, String> queryMapBuilder(URI uri) {
    String queryStrings = uri.getQuery();
    if (queryStrings == null) {
      return Collections.emptyMap();
    }
    Map<String, String> queryMap = new HashMap<String, String>();
    for (String param : Splitter.on("&").split(queryStrings)) {
      List<String> splits = Splitter.on("=").splitToList(param);
      if (splits.size() > 1) {
        queryMap.put(splits.get(0), splits.get(1));
      } else {
        queryMap.put(splits.get(0), "");
      }
    }
    return Collections.unmodifiableMap(queryMap);
  }

  @Override
  public final void handle(HttpExchange httpExchange) throws IOException {
    try {
      httpExchange.sendResponseHeaders(200, 0);
      zpageHandler.emitHtml(
          queryMapBuilder(httpExchange.getRequestURI()), httpExchange.getResponseBody());
    } finally {
      httpExchange.close();
    }
  }
}

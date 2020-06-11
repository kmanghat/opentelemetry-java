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

/** This class contains the unified CSS styles for all zPages. */
final class ZPageStyle {
  private ZPageStyle() {}

  /** Style here will be applied to the generated HTML pages for all zPages. */
  static String style =
      "body{font-family: \"Roboto\", sans-serif; font-size: 14px;"
          + "background-color: #F2F4EC;}"
          + "h1{color: #363636; text-align: center; margin-bottom 20px;}"
          + "p{padding: 0 0.5em; color: #4a4a4a;}"
          + "tr.bg-color{background-color: #4b5fab;}"
          + "table{margin: 0 auto;}"
          + "th{padding: 0 1em; line-height: 2.0}"
          + "td{padding: 0 1em; line-height: 2.0}"
          + ".border-right-white{border-right: 1px solid #fff;}"
          + ".border-left-white{border-left: 1px solid #fff;}"
          + ".border-left-dark{border-left: 1px solid #363636;}"
          + "th.header-text{color: #fff; line-height: 3.0;}"
          + "td.align-center{text-align: center;}"
          + "td.bg-white{background-color: #fff;}";
}

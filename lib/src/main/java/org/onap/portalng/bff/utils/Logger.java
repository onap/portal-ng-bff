/*
 *
 * Copyright (c) 2022. Deutsche Telekom AG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 *
 */

package org.onap.portalng.bff.utils;

import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

@Slf4j
public class Logger {

  /**
   * Not meant to be instantiated. To prevent Java from adding an implicit public constructor to
   * every class which does not define at least one explicitly.
   */
  private Logger() {}

  public static void requestLog(String xRequestId, HttpMethod methode, URI path) {
    log.info("bff - request - X-Request-Id {} {} {}", xRequestId, methode, path);
  }

  public static void responseLog(String xRequestId, HttpStatus code) {
    log.info("bff - response - X-Request-Id {} {}", xRequestId, code);
  }

  public static void errorLog(String xRequestId, String msg, String id, String app) {
    log.info("bff - error - X-Request-Id {} {} {} not found in {}", xRequestId, msg, id, app);
  }

  public static void errorLog(
      String xRequestId, String msg, String id, String app, String errorDetails) {
    log.info(
        "bff - error - X-Request-Id {} {} {} not found in {} error message: {}",
        xRequestId,
        msg,
        id,
        app,
        errorDetails);
  }
}

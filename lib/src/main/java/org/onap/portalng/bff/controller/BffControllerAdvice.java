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

package org.onap.portalng.bff.controller;

import org.onap.portalng.bff.exceptions.DownstreamApiProblemException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.result.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global exception handling for the BFF. Renders every error as an {@code application/problem+json}
 * response (RFC 7807 / 9457) using Spring's native {@link org.springframework.http.ProblemDetail}.
 *
 * <p>Extending {@link ResponseEntityExceptionHandler} gives correct problem-detail handling for all
 * standard Spring Web exceptions out of the box; {@link DownstreamApiProblemException} — the BFF's
 * own exception — is handled explicitly so its status and extension fields are surfaced. {@link
 * #createResponseEntity} forces the {@code application/problem+json} content type on every rendered
 * problem body, which is the contract portal-ui relies on.
 */
@RestControllerAdvice
public class BffControllerAdvice extends ResponseEntityExceptionHandler {

  @org.springframework.web.bind.annotation.ExceptionHandler(DownstreamApiProblemException.class)
  public ResponseEntity<Object> handleDownstreamApiProblemException(
      DownstreamApiProblemException ex) {
    return ResponseEntity.status(ex.getStatusCode())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(ex.getBody());
  }

  /**
   * Ensure every problem response carries the {@code application/problem+json} content type,
   * regardless of which handler produced it (mirrors the behaviour of the previous Zalando {@code
   * ProblemHandling} advice).
   */
  @Override
  protected Mono<ResponseEntity<Object>> createResponseEntity(
      Object body, HttpHeaders headers, HttpStatusCode statusCode, ServerWebExchange exchange) {
    final HttpHeaders effectiveHeaders = headers != null ? headers : new HttpHeaders();
    if (effectiveHeaders.getContentType() == null) {
      effectiveHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
    }
    return super.createResponseEntity(body, effectiveHeaders, statusCode, exchange);
  }
}

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

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.Comparator;
import java.util.List;
import org.onap.portalng.bff.exceptions.DownstreamApiProblemException;
import org.onap.portalng.bff.openapi.server.model.ConstraintViolationApiDto;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
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
 * own exception — is handled explicitly so its status and extension fields are surfaced. The {@link
 * #handleThrowable Throwable catch-all} renders any other uncaught exception (e.g. a Spring
 * Security {@code AccessDeniedException} thrown from a controller) as a 500 problem, mirroring the
 * catch-all the previous Zalando {@code ProblemHandling} advice provided. {@link
 * #createResponseEntity} forces the {@code application/problem+json} content type on every rendered
 * problem body, which is the contract portal-ui relies on.
 */
@RestControllerAdvice
public class BffControllerAdvice extends ResponseEntityExceptionHandler {

  @ExceptionHandler(DownstreamApiProblemException.class)
  public ResponseEntity<Object> handleDownstreamApiProblemException(
      DownstreamApiProblemException ex) {
    return ResponseEntity.status(ex.getStatusCode())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(ex.getBody());
  }

  /**
   * Bean-validation failures on controller parameters / request bodies (thrown as a jakarta {@link
   * ConstraintViolationException}) render as a 400 {@code problem+json} titled {@code "Constraint
   * Violation"} carrying a top-level {@code violations} array of {field, message} — the shape
   * portal-ui reads and the behaviour the removed Zalando {@code ConstraintViolationAdviceTrait}
   * provided. The {@code field} is the violation's full property path (e.g. {@code
   * getCellSitesInArea.arg2}), matching the previous output.
   */
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex) {
    final List<ConstraintViolationApiDto> violations =
        ex.getConstraintViolations().stream()
            .map(
                violation ->
                    new ConstraintViolationApiDto(pathOf(violation), violation.getMessage()))
            // Deterministic order (the ConstraintViolation set is unordered).
            .sorted(
                Comparator.comparing(
                    ConstraintViolationApiDto::getField,
                    Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    final ProblemDetail body = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    body.setTitle("Constraint Violation");
    body.setProperty("violations", violations);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(body);
  }

  private static String pathOf(ConstraintViolation<?> violation) {
    return violation.getPropertyPath() == null ? null : violation.getPropertyPath().toString();
  }

  /**
   * Catch-all for exceptions not handled by a more specific handler or by {@link
   * ResponseEntityExceptionHandler}'s built-in handlers. Renders a 500 {@code problem+json}
   * carrying the exception message as {@code detail} — the behaviour the removed Zalando {@code
   * ThrowableAdviceTrait} provided (e.g. a controller-thrown {@code AccessDeniedException}).
   */
  @ExceptionHandler(Throwable.class)
  public ResponseEntity<Object> handleThrowable(Throwable ex) {
    final ProblemDetail body = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
    body.setTitle(HttpStatus.INTERNAL_SERVER_ERROR.toString());
    body.setDetail(ex.getMessage());
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(body);
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

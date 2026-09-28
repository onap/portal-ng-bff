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
import lombok.extern.slf4j.Slf4j;
import org.onap.portalng.bff.exceptions.DownstreamApiProblemException;
import org.onap.portalng.bff.openapi.server.model.ConstraintViolationApiDto;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.reactive.result.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;
import tools.jackson.core.TokenStreamLocation;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

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
@Slf4j
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
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(constraintViolationProblem(violations));
  }

  /**
   * Request-body bean-validation failures render with the same {@code "Constraint Violation"} title
   * and {@code violations} array as {@link #handleConstraintViolation}, so portal-ui sees one
   * validation-error shape. {@code field} is the rejected field's name.
   */
  @Override
  protected Mono<ResponseEntity<Object>> handleWebExchangeBindException(
      WebExchangeBindException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      ServerWebExchange exchange) {
    final List<ConstraintViolationApiDto> violations =
        ex.getFieldErrors().stream()
            // Sort by field for a deterministic order (getFieldErrors() order is not stable).
            .sorted(Comparator.comparing(FieldError::getField))
            .map(
                error -> new ConstraintViolationApiDto(error.getField(), error.getDefaultMessage()))
            .toList();
    return handleExceptionInternal(
        ex, constraintViolationProblem(violations), headers, status, exchange);
  }

  private static ProblemDetail constraintViolationProblem(
      List<ConstraintViolationApiDto> violations) {
    final ProblemDetail body = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    body.setTitle("Constraint Violation");
    body.setProperty("violations", violations);
    return body;
  }

  /**
   * Logs the cause of a rejected request input, which the rendered problem does not carry. For a
   * Jackson decode error the client gets the JSON path or offset only: Jackson's own message names
   * the target DTO class and echoes the offending value.
   */
  @Override
  protected Mono<ResponseEntity<Object>> handleServerWebInputException(
      ServerWebInputException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      ServerWebExchange exchange) {
    final Throwable cause = NestedExceptionUtils.getMostSpecificCause(ex);
    log.warn(
        "Rejected request input for {} {}: {}",
        exchange.getRequest().getMethod(),
        exchange.getRequest().getPath(),
        cause.toString());
    if (cause instanceof JacksonException jacksonException) {
      ex.getBody().setDetail(clientDetail(jacksonException));
    }
    return super.handleServerWebInputException(ex, headers, status, exchange);
  }

  private static String clientDetail(JacksonException ex) {
    final String path = jsonPath(ex);
    if (!path.isEmpty()) {
      final String problem =
          ex instanceof UnrecognizedPropertyException ? "Unrecognized property" : "Invalid value";
      return problem + " at '" + path + "'";
    }
    final TokenStreamLocation location = ex.getLocation();
    if (location != null && location.getByteOffset() >= 0) {
      return "Malformed JSON request body at byte offset " + location.getByteOffset();
    }
    return "Malformed JSON request body";
  }

  private static String jsonPath(JacksonException ex) {
    final StringBuilder path = new StringBuilder();
    for (JacksonException.Reference reference : ex.getPath()) {
      if (reference.getPropertyName() != null) {
        if (!path.isEmpty()) {
          path.append('.');
        }
        path.append(reference.getPropertyName());
      } else if (reference.getIndex() >= 0) {
        path.append('[').append(reference.getIndex()).append(']');
      }
    }
    return path.toString();
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
   * ProblemHandling} advice). The headers are copied because for an {@code ErrorResponse} they are
   * the exception's own read-only headers.
   */
  @Override
  protected Mono<ResponseEntity<Object>> createResponseEntity(
      Object body, HttpHeaders headers, HttpStatusCode statusCode, ServerWebExchange exchange) {
    final HttpHeaders writableHeaders = new HttpHeaders();
    if (headers != null) {
      writableHeaders.putAll(headers);
    }
    if (writableHeaders.getContentType() == null) {
      writableHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
    }
    return super.createResponseEntity(body, writableHeaders, statusCode, exchange);
  }
}

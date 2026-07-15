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

package org.onap.portalng.bff.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.onap.portalng.bff.openapi.server.model.ConstraintViolationApiDto;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

/**
 * Renders exceptions that surface as an {@link ErrorResponse} — most importantly Spring's own
 * {@link org.springframework.web.server.ServerWebInputException} (missing / unconvertible query
 * params, unreadable bodies, …) and {@link org.springframework.web.server.ResponseStatusException}
 * — as an {@code application/problem+json} body (RFC 7807 / 9457).
 *
 * <p>This is a global reactive {@link WebExceptionHandler}, not a {@code @ControllerAdvice}: those
 * exceptions are raised during request/argument resolution and propagate up the {@code WebFilter}
 * chain, where {@code @ControllerAdvice} (and thus {@link BffControllerAdvice}) never sees them. It
 * runs at {@code @Order(-2)} — ahead of Spring Boot's default {@code
 * DefaultErrorWebExceptionHandler} (-1) and, crucially, ahead of the Spring Security exception
 * translation that would otherwise turn an unrendered downstream error into an empty {@code 403}.
 * This restores the behaviour the removed Zalando {@code ProblemExceptionHandler} provided.
 *
 * <p>{@link DownstreamApiProblemException} (the BFF's own {@link ErrorResponse}) is handled here as
 * well, so the same {@code problem+json} rendering applies whether it is thrown from a controller
 * or from further up the filter chain.
 */
@Slf4j
@Component
@Order(-2)
@RequiredArgsConstructor
public class ProblemWebExceptionHandler implements WebExceptionHandler {

  private final ObjectMapper objectMapper;

  @Override
  public Mono<Void> handle(ServerWebExchange exchange, Throwable throwable) {
    if (!(throwable instanceof ErrorResponse errorResponse)) {
      // Not something we render as a problem — let the next handler in the chain deal with it.
      return Mono.error(throwable);
    }

    final ProblemDetail body = problemBody(errorResponse);
    exchange.getResponse().setStatusCode(errorResponse.getStatusCode());
    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);

    final byte[] bytes;
    try {
      bytes = objectMapper.writeValueAsBytes(body);
    } catch (JsonProcessingException e) {
      log.error("Failed to serialize problem detail for {}", throwable.getClass().getName(), e);
      return Mono.error(throwable);
    }

    final DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
    return exchange.getResponse().writeWith(Mono.just(buffer));
  }

  /**
   * Request-body bean-validation failures surface as a {@link WebExchangeBindException}. Render
   * them with the same {@code "Constraint Violation"} title and top-level {@code violations} array
   * (field, message) as controller-parameter {@code ConstraintViolationException}s, so portal-ui
   * sees a single, consistent validation-error shape (the behaviour the removed Zalando {@code
   * MethodArgumentNotValidAdviceTrait} provided). {@code field} is the rejected field's name. Any
   * other {@link ErrorResponse} keeps its own {@link ProblemDetail} body.
   */
  private static ProblemDetail problemBody(ErrorResponse errorResponse) {
    if (errorResponse instanceof WebExchangeBindException bindException) {
      final List<ConstraintViolationApiDto> violations =
          bindException.getFieldErrors().stream()
              // Sort by field for a deterministic order (getFieldErrors() order is not stable).
              .sorted(Comparator.comparing(FieldError::getField))
              .map(
                  (FieldError fieldError) ->
                      new ConstraintViolationApiDto(
                          fieldError.getField(), fieldError.getDefaultMessage()))
              .toList();
      final ProblemDetail body = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
      body.setTitle("Constraint Violation");
      body.setProperty("violations", violations);
      return body;
    }
    return errorResponse.getBody();
  }
}

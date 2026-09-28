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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Renders an {@link ErrorResponse} that no {@code @ExceptionHandler} rendered — one raised in a
 * {@code WebFilter}, or one whose handler failed — as an {@code application/problem+json} body (RFC
 * 7807 / 9457). Exceptions raised while resolving a handler or its arguments ({@link
 * org.springframework.web.server.ServerWebInputException}, {@link
 * org.springframework.web.bind.support.WebExchangeBindException}, a 405, …) are rendered by {@link
 * org.onap.portalng.bff.controller.BffControllerAdvice}.
 *
 * <p>This is a global reactive {@link WebExceptionHandler}, not a {@code @ControllerAdvice}, so it
 * also sees exceptions from the {@code WebFilter} chain. It runs at {@code @Order(-2)} — ahead of
 * Spring Boot's default {@code DefaultErrorWebExceptionHandler} (-1) and, crucially, ahead of the
 * Spring Security exception translation that would otherwise turn an unrendered downstream error
 * into an empty {@code 403}. This restores the behaviour the removed Zalando {@code
 * ProblemExceptionHandler} provided.
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

    final ProblemDetail body = errorResponse.getBody();
    exchange.getResponse().setStatusCode(errorResponse.getStatusCode());
    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);

    final byte[] bytes;
    try {
      bytes = objectMapper.writeValueAsBytes(body);
    } catch (JacksonException e) {
      log.error("Failed to serialize problem detail for {}", throwable.getClass().getName(), e);
      return Mono.error(throwable);
    }

    final DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
    return exchange.getResponse().writeWith(Mono.just(buffer));
  }
}

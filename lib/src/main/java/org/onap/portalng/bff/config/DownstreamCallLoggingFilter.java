/*
 *
 * Copyright (c) 2026. Deutsche Telekom AG
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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Logs the start, the response (status and duration until the response headers arrived) and the
 * failure of every outgoing call, together with the downstream system it targets.
 *
 * <p>The downstream system is read from the {@link #DOWNSTREAM_SYSTEM_ATTRIBUTE} request attribute
 * (see {@link #forSystem(String)}); calls without it are reported with the target host.
 */
@Slf4j
public class DownstreamCallLoggingFilter implements ExchangeFilterFunction {

  public static final String DOWNSTREAM_SYSTEM_ATTRIBUTE =
      DownstreamCallLoggingFilter.class.getName() + ".downstreamSystem";

  /** Tags every request built by a {@link WebClient} with the given downstream system. */
  public static Consumer<WebClient.RequestHeadersSpec<?>> forSystem(String downstreamSystem) {
    return spec -> spec.attribute(DOWNSTREAM_SYSTEM_ATTRIBUTE, downstreamSystem);
  }

  /**
   * Moves this filter behind all others, so that it logs the raw response of the downstream system
   * rather than an error an inner filter made of it, and its duration leaves out the token request
   * of the OAuth2 filter.
   */
  public static void moveInnermost(List<ExchangeFilterFunction> filters) {
    final List<ExchangeFilterFunction> loggingFilters =
        filters.stream().filter(DownstreamCallLoggingFilter.class::isInstance).toList();
    filters.removeAll(loggingFilters);
    filters.addAll(loggingFilters);
  }

  @Override
  public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
    final String downstreamSystem =
        request
            .attribute(DOWNSTREAM_SYSTEM_ATTRIBUTE)
            .map(Object::toString)
            .orElseGet(() -> request.url().getHost());
    final String xRequestId =
        Optional.ofNullable(request.headers().getFirst(BeansConfig.X_REQUEST_ID)).orElse("not set");

    return Mono.defer(
        () -> {
          final long start = System.nanoTime();
          log.info(
              "bff - downstream request - X-Request-Id {} {} {} {}",
              xRequestId,
              downstreamSystem,
              request.method(),
              request.url());
          return next.exchange(request)
              .doOnNext(
                  response ->
                      log.atLevel(
                              response.statusCode().is5xxServerError() ? Level.WARN : Level.INFO)
                          .log(
                              "bff - downstream response - X-Request-Id {} {} {} {} {} in {} ms",
                              xRequestId,
                              downstreamSystem,
                              request.method(),
                              request.url(),
                              response.statusCode().value(),
                              elapsedMillis(start)))
              .doOnError(
                  error ->
                      log.warn(
                          "bff - downstream failure - X-Request-Id {} {} {} {} after {} ms",
                          xRequestId,
                          downstreamSystem,
                          request.method(),
                          request.url(),
                          elapsedMillis(start),
                          error));
        });
  }

  private static long elapsedMillis(long startNanos) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
  }
}

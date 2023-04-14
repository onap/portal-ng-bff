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

package org.onap.portal.bff.config.clients;

import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.onap.portal.bff.exceptions.DownstreamApiProblemException;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractClientConfig<E> {
  private final Class<E> errorResponseTypeClass;

  protected ExchangeFilterFunction errorHandlingExchangeFilterFunction() {
    return ExchangeFilterFunction.ofResponseProcessor(
        clientResponse -> {
          if (clientResponse.statusCode().isError()) {
            return clientResponse
                .bodyToMono(errorResponseTypeClass)
                .doOnNext(s -> log.error("Received error response from downstream: {}", s))
                .flatMap(
                    problemResponse ->
                        Mono.error(mapException(problemResponse, clientResponse.statusCode())));
          }
          return Mono.just(clientResponse);
        });
  }

  protected abstract DownstreamApiProblemException mapException(
      E errorResponse, HttpStatus httpStatus);

  protected ClientHttpConnector getClientHttpConnector() {
    // ConnectionTimeouts introduced due to
    // io.netty.channel.unix.Errors$NativeIoException: readAddress(..) failed: Connection reset by
    // peer issue
    // https://github.com/reactor/reactor-netty/issues/1774#issuecomment-908066283
    ConnectionProvider connectionProvider =
        ConnectionProvider.builder("fixed")
            .maxConnections(500)
            .maxIdleTime(Duration.ofSeconds(20))
            .maxLifeTime(Duration.ofSeconds(60))
            .pendingAcquireTimeout(Duration.ofSeconds(60))
            .evictInBackground(Duration.ofSeconds(120))
            .build();
    return new ReactorClientHttpConnector(HttpClient.create(connectionProvider));
  }

  protected WebClient getWebClient(
      WebClient.Builder webClientBuilder, List<ExchangeFilterFunction> filters) {
    if (filters != null) {
      filters.forEach(webClientBuilder::filter);
    }
    return webClientBuilder
        .filter(errorHandlingExchangeFilterFunction())
        .clientConnector(getClientHttpConnector())
        .build();
  }
}

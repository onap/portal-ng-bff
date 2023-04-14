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

package org.onap.portal.bff.idtoken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.onap.portal.bff.BaseIntegrationTest;
import org.onap.portal.bff.config.IdTokenExchangeFilterFunction;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class IdTokenExchangeFilterFunctionTest extends BaseIntegrationTest {

  @Test
  void idTokenIsCorrectlyPropagated() {
    final IdTokenExchangeFilterFunction filterFunction = new IdTokenExchangeFilterFunction();

    final String idToken = UUID.randomUUID().toString();
    final ServerWebExchange serverWebExchange =
        MockServerWebExchange.builder(
                MockServerHttpRequest.get("http://localhost:8000")
                    .header(IdTokenExchangeFilterFunction.X_AUTH_IDENTITY_HEADER, idToken))
            .build();

    final ClientRequest request =
        ClientRequest.create(HttpMethod.GET, URI.create("http://api-server:9000"))
            .attribute(ServerWebExchange.class.getName(), serverWebExchange)
            .build();
    final ClientResponse response = mock(ClientResponse.class);

    final ExchangeFunction exchange =
        r -> {
          assertThat(r.headers().getOrEmpty(IdTokenExchangeFilterFunction.X_AUTH_IDENTITY_HEADER))
              .containsExactly(idToken);

          return Mono.just(response);
        };

    final ClientResponse result = filterFunction.filter(request, exchange).block();
    assertThat(result).isEqualTo(response);
  }

  @Test
  void exceptionIsThrownWhenIdTokenIsMissingInRequest() {
    final IdTokenExchangeFilterFunction filterFunction = new IdTokenExchangeFilterFunction();

    final ServerWebExchange serverWebExchange =
        MockServerWebExchange.builder(MockServerHttpRequest.get("http://localhost:8000")).build();

    final ClientRequest request =
        ClientRequest.create(HttpMethod.GET, URI.create("http://api-server:9000"))
            .attribute(ServerWebExchange.class.getName(), serverWebExchange)
            .build();
    final ExchangeFunction exchange = r -> Mono.just(mock(ClientResponse.class));

    assertThatThrownBy(() -> filterFunction.filter(request, exchange).block())
        .hasMessage("Forbidden: ID token is missing");
  }
}

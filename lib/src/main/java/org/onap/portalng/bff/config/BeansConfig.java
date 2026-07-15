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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.net.URI;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.onap.portalng.bff.exceptions.DownstreamApiProblemException;
import org.onap.portalng.bff.openapi.server.model.ConstraintViolationApiDto;
import org.onap.portalng.bff.utils.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import reactor.core.publisher.Mono;

@Slf4j
@Configuration
public class BeansConfig {

  public static final String OAUTH2_EXCHANGE_FILTER_FUNCTION = "oauth2ExchangeFilterFunction";
  public static final String ID_TOKEN_EXCHANGE_FILTER_FUNCTION = "idTokenExchangeFilterFunction";
  public static final String ERROR_HANDLING_EXCHANGE_FILTER_FUNCTION =
      "errorHandlingExchangeFilterFunction";
  public static final String LOG_REQUEST_EXCHANGE_FILTER_FUNCTION =
      "logRequestExchangeFilterFunction";
  public static final String LOG_RESPONSE_EXCHANGE_FILTER_FUNCTION =
      "logResponseExchangeFilterFunction";
  public static final String X_REQUEST_ID = "X-Request-Id";

  private static final String CLIENT_REGISTRATION_ID = "keycloak";
  private static final ObjectMapper objectMapper = new ObjectMapper();

  private static final Authentication CLIENT_CREDENTIALS_AUTHENTICATION =
      new AnonymousAuthenticationToken(
          "client-credentials",
          "client-credentials",
          AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

  @Bean(name = OAUTH2_EXCHANGE_FILTER_FUNCTION)
  ExchangeFilterFunction oauth2ExchangeFilterFunction(
      ReactiveOAuth2AuthorizedClientManager authorizedClientManager) {
    return (request, next) -> {
      OAuth2AuthorizeRequest authorizeRequest =
          OAuth2AuthorizeRequest.withClientRegistrationId(CLIENT_REGISTRATION_ID)
              .principal(CLIENT_CREDENTIALS_AUTHENTICATION)
              .build();
      return authorizedClientManager
          .authorize(authorizeRequest)
          .map(
              authorizedClient ->
                  ClientRequest.from(request)
                      .headers(
                          headers ->
                              headers.setBearerAuth(
                                  authorizedClient.getAccessToken().getTokenValue()))
                      .build())
          .defaultIfEmpty(request)
          .flatMap(next::exchange);
    };
  }

  @Bean(name = ERROR_HANDLING_EXCHANGE_FILTER_FUNCTION)
  ExchangeFilterFunction errorHandlingExchangeFilterFunction() {
    return ExchangeFilterFunction.ofResponseProcessor(
        clientResponse -> {
          if (clientResponse.statusCode().isError()) {
            return clientResponse
                .bodyToMono(String.class)
                .doOnNext(s -> log.error("Received error response from downstream: {}", s))
                .flatMap(
                    downstreamExceptionBody ->
                        Mono.error(toException(downstreamExceptionBody, objectMapper)));
          }
          return Mono.just(clientResponse);
        });
  }

  /**
   * Rebuilds a {@link DownstreamApiProblemException} from a downstream error body.
   *
   * <p>The body is parsed as a permissive {@link JsonNode} rather than the generated {@code
   * ProblemApiDto}: that DTO's {@code downstreamSystem} is a closed enum ({@code
   * KEYCLOAK/PREFERENCES/HISTORY}), so binding a consumer's value (e.g. a T-NAP {@code GEOCODING},
   * {@code AAI}, {@code SO_CATALOG}) would throw and the whole error body — including {@code
   * downstreamSystem} — would be lost. Reading {@code downstreamSystem} as free text keeps any
   * value intact, matching the pre-migration behaviour where the exception deserialized straight
   * from JSON.
   */
  private static DownstreamApiProblemException toException(
      String downstreamExceptionBody, ObjectMapper objectMapper) {
    final JsonNode node;
    try {
      node = objectMapper.readTree(downstreamExceptionBody);
    } catch (JsonProcessingException e) {
      return DownstreamApiProblemException.builder().build();
    }

    final DownstreamApiProblemException.Builder builder = DownstreamApiProblemException.builder();
    if (node.hasNonNull("status")) {
      builder.status(HttpStatusCode.valueOf(node.get("status").asInt()));
    }
    if (node.hasNonNull("title")) {
      builder.title(node.get("title").asText());
    }
    if (node.hasNonNull("detail")) {
      builder.detail(node.get("detail").asText());
    }
    if (node.hasNonNull("type")) {
      builder.type(URI.create(node.get("type").asText()));
    }
    if (node.hasNonNull("instance")) {
      builder.instance(URI.create(node.get("instance").asText()));
    }
    if (node.hasNonNull("downstreamSystem")) {
      builder.downstreamSystem(node.get("downstreamSystem").asText());
    }
    if (node.hasNonNull("downstreamStatus")) {
      builder.downstreamStatus(node.get("downstreamStatus").asInt());
    }
    if (node.hasNonNull("downstreamMessageId")) {
      builder.downstreamMessageId(node.get("downstreamMessageId").asText());
    }
    if (node.has("violations") && node.get("violations").isArray()) {
      final List<ConstraintViolationApiDto> violations = new ArrayList<>();
      for (JsonNode violation : node.get("violations")) {
        violations.add(
            new ConstraintViolationApiDto(
                violation.path("field").asText(null), violation.path("message").asText(null)));
      }
      builder.violations(violations);
    }
    return builder.build();
  }

  //
  // Don't use this. Log will is written in the LoggerInterceptor
  //
  @Bean(name = LOG_REQUEST_EXCHANGE_FILTER_FUNCTION)
  ExchangeFilterFunction logRequestExchangeFilterFunction() {
    return ExchangeFilterFunction.ofRequestProcessor(
        clientRequest -> {
          List<String> xRequestIdList = clientRequest.headers().get(X_REQUEST_ID);
          if (xRequestIdList != null && !xRequestIdList.isEmpty()) {
            String xRequestId = xRequestIdList.get(0);
            Logger.requestLog(xRequestId, clientRequest.method(), clientRequest.url());
          }
          return Mono.just(clientRequest);
        });
  }

  @Bean(name = LOG_RESPONSE_EXCHANGE_FILTER_FUNCTION)
  ExchangeFilterFunction logResponseExchangeFilterFunction() {
    return ExchangeFilterFunction.ofResponseProcessor(
        clientResponse -> {
          String xRequestId = "not set";
          List<String> xRequestIdList = clientResponse.headers().header(X_REQUEST_ID);
          if (xRequestIdList != null && !xRequestIdList.isEmpty())
            xRequestId = xRequestIdList.get(0);
          Logger.responseLog(xRequestId, clientResponse.statusCode());
          return Mono.just(clientResponse);
        });
  }

  @Bean
  ExchangeStrategies exchangeStrategies(@Qualifier("objectMapper") ObjectMapper objectMapper) {
    return ExchangeStrategies.builder()
        .codecs(
            configurer -> {
              final ClientCodecConfigurer.ClientDefaultCodecs defaultCodecs =
                  configurer.defaultCodecs();

              defaultCodecs.maxInMemorySize(16 * 1024 * 1024); // 16MB
              defaultCodecs.jackson2JsonEncoder(new Jackson2JsonEncoder(objectMapper));
              defaultCodecs.jackson2JsonDecoder(new Jackson2JsonDecoder(objectMapper));
            })
        .build();
  }

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
    // Jackson2ObjectMapperBuilder.build() auto-registers Spring's ProblemDetailJacksonMixin, so
    // ProblemDetail extension properties (downstreamSystem, downstreamStatus, ...) serialize flat
    // at the top level of the body — the wire format portal-ui depends on. No Zalando ProblemModule
    // is needed anymore.
    return builder
        .modules(new JavaTimeModule())
        .build()
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);
  }

  @Bean
  public XmlMapper xmlMapper() {
    return XmlMapper.builder()
        .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
        .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
        .build();
  }
}

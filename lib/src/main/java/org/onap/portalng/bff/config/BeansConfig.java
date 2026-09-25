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
import java.net.URI;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.onap.portalng.bff.exceptions.DownstreamApiProblemException;
import org.onap.portalng.bff.openapi.server.model.ConstraintViolationApiDto;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.http.codec.json.JacksonJsonDecoder;
import org.springframework.http.codec.json.JacksonJsonEncoder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Configuration
public class BeansConfig {

  public static final String OAUTH2_EXCHANGE_FILTER_FUNCTION = "oauth2ExchangeFilterFunction";
  public static final String ID_TOKEN_EXCHANGE_FILTER_FUNCTION = "idTokenExchangeFilterFunction";
  public static final String ERROR_HANDLING_EXCHANGE_FILTER_FUNCTION =
      "errorHandlingExchangeFilterFunction";
  public static final String LOG_DOWNSTREAM_CALL_EXCHANGE_FILTER_FUNCTION =
      "logDownstreamCallExchangeFilterFunction";
  public static final String X_REQUEST_ID = "X-Request-Id";

  private static final String CLIENT_REGISTRATION_ID = "keycloak";
  private static final ObjectMapper objectMapper = JsonMapper.builder().build();

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
    } catch (JacksonException e) {
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

  @Bean(name = LOG_DOWNSTREAM_CALL_EXCHANGE_FILTER_FUNCTION)
  ExchangeFilterFunction logDownstreamCallExchangeFilterFunction() {
    return new DownstreamCallLoggingFilter();
  }

  @Bean
  ExchangeStrategies exchangeStrategies(JsonMapper jsonMapper) {
    return ExchangeStrategies.builder()
        .codecs(
            configurer -> {
              final ClientCodecConfigurer.ClientDefaultCodecs defaultCodecs =
                  configurer.defaultCodecs();

              defaultCodecs.maxInMemorySize(16 * 1024 * 1024); // 16MB
              defaultCodecs.jacksonJsonEncoder(new JacksonJsonEncoder(jsonMapper));
              defaultCodecs.jacksonJsonDecoder(new JacksonJsonDecoder(jsonMapper));
            })
        .build();
  }

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  /**
   * Contributes to Boot's autoconfigured {@code JsonMapper} instead of replacing it with a mapper
   * of our own. That mapper already carries Spring's {@code ProblemDetailJacksonMixin} (registered
   * by {@code JacksonAutoConfiguration.JsonProblemDetailsConfiguration}), so ProblemDetail
   * extension properties — {@code downstreamSystem}, {@code downstreamStatus}, … — keep serializing
   * flat at the top level of the body, which is the wire format portal-ui depends on. Java-time
   * support is built into Jackson 3, so no module registration is needed either. Declaring a second
   * {@code ObjectMapper} bean here would not work: Boot's {@code JsonMapper} is {@code @Primary},
   * so every unqualified {@code ObjectMapper} injection point would silently get that one instead
   * of ours.
   */
  @Bean
  JsonMapperBuilderCustomizer nonNullInclusionCustomizer() {
    return builder ->
        builder.changeDefaultPropertyInclusion(
            inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_NULL));
  }
}

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

package org.onap.portal.bff.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Clock;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.onap.portal.bff.exceptions.DownstreamApiProblemException;
import org.onap.portal.bff.utils.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.zalando.problem.jackson.ProblemModule;
import reactor.core.publisher.Mono;

@Slf4j
@Configuration
public class BeansConfig {

  public static final String OAUTH2_EXCHANGE_FILTER_FUNCTION = "oauth2ExchangeFilterFunction";
  private static final String ID_TOKEN_EXCHANGE_FILTER_FUNCTION = "idTokenExchangeFilterFunction";
  private static final String ERROR_HANDLING_EXCHANGE_FILTER_FUNCTION =
      "errorHandlingExchangeFilterFunction";
  private static final String LOG_REQUEST_EXCHANGE_FILTER_FUNCTION =
      "logRequestExchangeFilterFunction";
  private static final String LOG_RESPONSE_EXCHANGE_FILTER_FUNCTION =
      "logResponseExchangeFilterFunction";
  private static final String CLIENT_REGISTRATION_ID = "keycloak";
  public static final String X_REQUEST_ID = "X-Request-Id";

  @Bean(name = OAUTH2_EXCHANGE_FILTER_FUNCTION)
  ExchangeFilterFunction oauth2ExchangeFilterFunction(
      ReactiveOAuth2AuthorizedClientManager authorizedClientManager) {
    final ServerOAuth2AuthorizedClientExchangeFilterFunction oauth2Filter =
        new ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);
    oauth2Filter.setDefaultClientRegistrationId(CLIENT_REGISTRATION_ID);

    return oauth2Filter;
  }

  @Bean(name = ID_TOKEN_EXCHANGE_FILTER_FUNCTION)
  ExchangeFilterFunction idTokenExchangeFilterFunction() {
    return new IdTokenExchangeFilterFunction();
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
                    downstreamExceptionBody -> {
                      try {
                        return Mono.error(
                            new ObjectMapper()
                                .readValue(
                                    downstreamExceptionBody, DownstreamApiProblemException.class));
                      } catch (JsonProcessingException e) {
                        return Mono.error(DownstreamApiProblemException.builder().build());
                      }
                    });
          }
          return Mono.just(clientResponse);
        });
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
  ExchangeStrategies exchangeStrategies(ObjectMapper objectMapper) {
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

  // we need to use prototype scope to always create new instance of the bean
  // because internally WebClient.Builder is mutable
  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  WebClient.Builder webClientBuilder(
      ExchangeStrategies exchangeStrategies,
      @Qualifier(ID_TOKEN_EXCHANGE_FILTER_FUNCTION)
          ExchangeFilterFunction idTokenExchangeFilterFunction,
      @Qualifier(ERROR_HANDLING_EXCHANGE_FILTER_FUNCTION)
          ExchangeFilterFunction errorHandlingExchangeFilterFunction,
      @Qualifier(LOG_RESPONSE_EXCHANGE_FILTER_FUNCTION)
          ExchangeFilterFunction logResponseExchangeFilterFunction) {
    return WebClient.builder()
        .exchangeStrategies(exchangeStrategies)
        .filter(idTokenExchangeFilterFunction)
        .filter(errorHandlingExchangeFilterFunction)
        .filter(logResponseExchangeFilterFunction);
  }

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
    return builder
        .modules(new ProblemModule(), new JavaTimeModule())
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

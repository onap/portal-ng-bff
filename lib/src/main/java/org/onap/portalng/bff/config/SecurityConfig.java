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

import static org.springframework.security.config.Customizer.withDefaults;

import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import org.onap.portalng.bff.openapi.server.model.ProblemApiDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.client.AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.endpoint.WebClientReactiveClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final KeycloakPermissionFilter keycloakPermissionFilter;

  @Value("${bff.endpoints.unauthenticated}")
  private String[] unauthenticatedEndpoints;

  @Bean
  public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
    return http.httpBasic(
            basic ->
                basic
                    .disable()
                    .formLogin(
                        login -> login.disable().csrf(csrf -> csrf.disable().cors(withDefaults()))))
        .authorizeExchange(
            exchange ->
                exchange
                    .pathMatchers(unauthenticatedEndpoints)
                    .permitAll()
                    .anyExchange()
                    .authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults()))
        .oauth2Client(withDefaults())
        .addFilterAfter(keycloakPermissionFilter, SecurityWebFiltersOrder.AUTHORIZATION)
        .build();
  }

  @Bean
  ReactiveOAuth2AuthorizedClientManager reactiveOAuth2AuthorizedClientManager(
      ReactiveClientRegistrationRepository clientRegistrationRepository,
      ReactiveOAuth2AuthorizedClientService authorizedClientService,
      ObservationRegistry observationRegistry,
      @Qualifier(BeansConfig.LOG_DOWNSTREAM_CALL_EXCHANGE_FILTER_FUNCTION)
          ExchangeFilterFunction logDownstreamCallExchangeFilterFunction) {

    // Spring Security's default token client uses a bare WebClient that is neither observed nor
    // logged. The injectable WebClient.Builder is no substitute: its BFF-wide filters would turn a
    // token endpoint error into a DownstreamApiProblemException instead of an OAuth2 error.
    final WebClientReactiveClientCredentialsTokenResponseClient tokenResponseClient =
        new WebClientReactiveClientCredentialsTokenResponseClient();
    tokenResponseClient.setWebClient(
        WebClient.builder()
            .observationRegistry(observationRegistry)
            .filter(logDownstreamCallExchangeFilterFunction)
            .defaultRequest(
                DownstreamCallLoggingFilter.forSystem(
                    ProblemApiDto.DownstreamSystemEnum.KEYCLOAK.toString()))
            .build());

    final ReactiveOAuth2AuthorizedClientProvider authorizedClientProvider =
        ReactiveOAuth2AuthorizedClientProviderBuilder.builder()
            .clientCredentials(
                clientCredentials ->
                    clientCredentials.accessTokenResponseClient(tokenResponseClient))
            .build();

    final AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager authorizedClientManager =
        new AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(
            clientRegistrationRepository, authorizedClientService);
    authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);

    return authorizedClientManager;
  }
}

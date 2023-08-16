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

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.web.server.SecurityWebFilterChain;

@EnableWebFluxSecurity
@Configuration
public class SecurityConfig {
  @Bean
  public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
    return http.httpBasic()
        .disable()
        .formLogin()
        .disable()
        .csrf()
        .disable()
        .cors()
        .and()
        .authorizeExchange()
        .pathMatchers(HttpMethod.GET, "/api-docs.html", "/api.yaml", "/webjars/**", "/actuator/**")
        .permitAll()
        .anyExchange()
        .authenticated()
        .and()
        .oauth2ResourceServer(ServerHttpSecurity.OAuth2ResourceServerSpec::jwt)
        .oauth2Client()
        .and()
        .build();
  }

  @Bean
  ReactiveOAuth2AuthorizedClientManager reactiveOAuth2AuthorizedClientManager(
      ReactiveClientRegistrationRepository clientRegistrationRepository,
      ServerOAuth2AuthorizedClientRepository authorizedClientRepository) {

    final ReactiveOAuth2AuthorizedClientProvider authorizedClientProvider =
        ReactiveOAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build();

    final DefaultReactiveOAuth2AuthorizedClientManager authorizedClientManager =
        new DefaultReactiveOAuth2AuthorizedClientManager(
            clientRegistrationRepository, authorizedClientRepository);
    authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);

    return authorizedClientManager;
  }
}

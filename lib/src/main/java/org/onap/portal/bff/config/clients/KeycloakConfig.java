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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.onap.portal.bff.config.BeansConfig;
import org.onap.portal.bff.config.PortalBffConfig;
import org.onap.portal.bff.exceptions.DownstreamApiProblemException;
import org.onap.portal.bff.openapi.client_portal_keycloak.ApiClient;
import org.onap.portal.bff.openapi.client_portal_keycloak.api.KeycloakApi;
import org.onap.portal.bff.openapi.client_portal_keycloak.model.ErrorResponseKeycloakDto;
import org.onap.portal.bff.openapi.server.model.ProblemApiDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Configuration
public class KeycloakConfig extends AbstractClientConfig<ErrorResponseKeycloakDto> {
  private final ObjectMapper objectMapper;
  private final PortalBffConfig bffConfig;
  private final ExchangeFilterFunction oauth2ExchangeFilterFunction;

  @Autowired
  public KeycloakConfig(
      @Qualifier(BeansConfig.OAUTH2_EXCHANGE_FILTER_FUNCTION)
          ExchangeFilterFunction oauth2ExchangeFilterFunction,
      ObjectMapper objectMapper,
      PortalBffConfig bffConfig) {
    super(ErrorResponseKeycloakDto.class);
    this.objectMapper = objectMapper;
    this.bffConfig = bffConfig;
    this.oauth2ExchangeFilterFunction = oauth2ExchangeFilterFunction;
  }

  @Bean
  public KeycloakApi keycloakApi(WebClient.Builder webClientBuilder) {
    return constructApiClient(webClientBuilder, KeycloakApi::new);
  }

  private <T> T constructApiClient(
      WebClient.Builder webClientBuilder, Function<ApiClient, T> apiConstructor) {
    final ApiClient apiClient =
        new ApiClient(
            getWebClient(webClientBuilder, List.of(oauth2ExchangeFilterFunction)),
            objectMapper,
            objectMapper.getDateFormat());

    // Extract service name and version from BasePath
    String urlBasePathPrefix =
        String.format("%s/auth/admin/realms/%s", bffConfig.getKeycloakUrl(), bffConfig.getRealm());

    return apiConstructor.apply(apiClient.setBasePath(urlBasePathPrefix));
  }

  @Override
  protected DownstreamApiProblemException mapException(
      ErrorResponseKeycloakDto errorResponse, HttpStatus httpStatus) {
    String errorDetail =
        errorResponse.getErrorMessage() != null
            ? errorResponse.getErrorMessage()
            : errorResponse.getError();

    return DownstreamApiProblemException.builder()
        .title(httpStatus.toString())
        .detail(errorDetail)
        .downstreamSystem(ProblemApiDto.DownstreamSystemEnum.KEYCLOAK.toString())
        .downstreamMessageId("not set by downstream system")
        .downstreamStatus(httpStatus.value())
        .build();
  }

  @Override
  protected ClientHttpConnector getClientHttpConnector() {
    return null;
  }
}

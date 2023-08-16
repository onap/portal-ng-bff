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

package org.onap.portalng.bff.config.clients;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.onap.portalng.bff.config.BeansConfig;
import org.onap.portalng.bff.config.PortalBffConfig;
import org.onap.portalng.bff.exceptions.DownstreamApiProblemException;
import org.onap.portalng.bff.openapi.client_portal_prefs.ApiClient;
import org.onap.portalng.bff.openapi.client_portal_prefs.api.PreferencesApi;
import org.onap.portalng.bff.openapi.client_portal_prefs.model.ProblemPortalPrefsDto;
import org.onap.portalng.bff.openapi.server.model.ProblemApiDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Configuration
public class PortalPrefsConfig extends AbstractClientConfig<ProblemPortalPrefsDto> {
  private final ObjectMapper objectMapper;
  private final PortalBffConfig bffConfig;
  private final ExchangeFilterFunction oauth2ExchangeFilterFunction;

  public PortalPrefsConfig(
      @Qualifier(BeansConfig.OAUTH2_EXCHANGE_FILTER_FUNCTION)
          ExchangeFilterFunction oauth2ExchangeFilterFunction,
      ObjectMapper objectMapper,
      PortalBffConfig bffConfig) {
    super(ProblemPortalPrefsDto.class);
    this.objectMapper = objectMapper;
    this.bffConfig = bffConfig;
    this.oauth2ExchangeFilterFunction = oauth2ExchangeFilterFunction;
  }

  @Bean
  public PreferencesApi portalPrefsApi(WebClient.Builder webClientBuilder) {
    return constructApiClient(webClientBuilder, PreferencesApi::new);
  }

  private <T> T constructApiClient(
      WebClient.Builder webClientBuilder, Function<ApiClient, T> apiConstructor) {
    final ApiClient apiClient =
        new ApiClient(
            getWebClient(webClientBuilder, List.of(oauth2ExchangeFilterFunction)),
            objectMapper,
            objectMapper.getDateFormat());

    final String generatedBasePath = apiClient.getBasePath();
    String basePath = "";
    try {
      basePath = bffConfig.getPortalPrefsUrl() + new URL(generatedBasePath).getPath();
    } catch (MalformedURLException e) {
      log.error(e.getLocalizedMessage());
    }
    return apiConstructor.apply(apiClient.setBasePath(basePath));
  }

  @Override
  protected DownstreamApiProblemException mapException(
      ProblemPortalPrefsDto errorResponse, HttpStatus httpStatus) {
    return DownstreamApiProblemException.builder()
        .title(httpStatus.toString())
        .detail(errorResponse.getDetail())
        .downstreamMessageId(errorResponse.getType())
        .downstreamSystem(ProblemApiDto.DownstreamSystemEnum.PORTAL_PREFS.toString())
        .downstreamStatus(httpStatus.value())
        .build();
  }
}

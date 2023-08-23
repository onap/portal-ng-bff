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

package org.onap.portalng.bff.services;

import lombok.RequiredArgsConstructor;
import org.onap.portalng.bff.exceptions.DownstreamApiProblemException;
import org.onap.portalng.bff.openapi.client_preferences.api.PreferencesApi;
import org.onap.portalng.bff.openapi.client_preferences.model.PreferencesPreferencesDto;
import org.onap.portalng.bff.openapi.server.model.CreatePreferencesRequestApiDto;
import org.onap.portalng.bff.openapi.server.model.PreferencesResponseApiDto;
import org.onap.portalng.bff.utils.Logger;
import org.springframework.core.convert.support.ConfigurableConversionService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
@Service
public class PreferencesService {

  private static final String PREFERENCES_APPLICATION_NAME = "PREFERENCES";

  private final PreferencesApi preferencesApi;
  private final ConfigurableConversionService conversionService;

  public Mono<PreferencesResponseApiDto> createPreferences(
      String xRequestId, CreatePreferencesRequestApiDto request) {
    PreferencesPreferencesDto preferencesPreferencesDto = new PreferencesPreferencesDto();
    preferencesPreferencesDto.setProperties(request.getProperties());
    return preferencesApi
        .savePreferences(xRequestId, preferencesPreferencesDto)
        .map(resp -> conversionService.convert(resp, PreferencesResponseApiDto.class))
        .onErrorResume(
            DownstreamApiProblemException.class,
            ex -> {
              Logger.errorLog(
                  xRequestId, "Preference raise error", xRequestId, PREFERENCES_APPLICATION_NAME);
              return Mono.error(ex);
            });
  }

  public Mono<PreferencesResponseApiDto> updatePreferences(
      String xRequestId, CreatePreferencesRequestApiDto request) {
    PreferencesPreferencesDto preferencesPreferencesDto = new PreferencesPreferencesDto();
    preferencesPreferencesDto.setProperties(request.getProperties());
    return preferencesApi
        .updatePreferences(xRequestId, preferencesPreferencesDto)
        .map(resp -> conversionService.convert(resp, PreferencesResponseApiDto.class))
        .onErrorResume(
            DownstreamApiProblemException.class,
            ex -> {
              Logger.errorLog(
                  xRequestId, "Preference raise error", xRequestId, PREFERENCES_APPLICATION_NAME);
              return Mono.error(ex);
            });
  }

  public Mono<PreferencesResponseApiDto> getPreferences(String xRequestId) {
    return preferencesApi
        .getPreferences(xRequestId)
        .map(preferences -> conversionService.convert(preferences, PreferencesResponseApiDto.class))
        .onErrorResume(
            DownstreamApiProblemException.class,
            ex -> {
              Logger.errorLog(
                  xRequestId,
                  "Get preferences failed for ID",
                  xRequestId,
                  PREFERENCES_APPLICATION_NAME);
              return Mono.error(ex);
            });
  }
}

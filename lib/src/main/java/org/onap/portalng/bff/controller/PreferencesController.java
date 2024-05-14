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

package org.onap.portalng.bff.controller;

import jakarta.validation.Valid;
import org.onap.portalng.bff.config.BffConfig;
import org.onap.portalng.bff.openapi.server.api.PreferencesApi;
import org.onap.portalng.bff.openapi.server.model.CreatePreferencesRequestApiDto;
import org.onap.portalng.bff.openapi.server.model.PreferencesResponseApiDto;
import org.onap.portalng.bff.services.PreferencesService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
public class PreferencesController extends AbstractBffController implements PreferencesApi {
  public static final String CREATE = "PREFERENCES_CREATE";
  public static final String GET = "PREFERENCES_GET";
  public static final String UPDATE = "PREFERENCES_UPDATE";

  private final PreferencesService preferencesService;

  public PreferencesController(BffConfig bffConfig, PreferencesService preferencesService) {
    super(bffConfig);
    this.preferencesService = preferencesService;
  }

  @Override
  public Mono<ResponseEntity<PreferencesResponseApiDto>> getPreferences(
      String xRequestId, ServerWebExchange exchange) {
    return checkRoleAccess(GET, exchange)
        .then(preferencesService.getPreferences(xRequestId))
        .map(ResponseEntity::ok);
  }

  @Override
  public Mono<ResponseEntity<PreferencesResponseApiDto>> savePreferences(
      @Valid Mono<CreatePreferencesRequestApiDto> preferencesApiDto,
      String xRequestId,
      ServerWebExchange exchange) {
    return checkRoleAccess(CREATE, exchange)
        .doOnError(smth -> System.out.println(smth))
        .then(preferencesApiDto)
        .flatMap(request -> preferencesService.createPreferences(xRequestId, request))
        .map(ResponseEntity::ok);
  }

  @Override
  public Mono<ResponseEntity<PreferencesResponseApiDto>> updatePreferences(
      @Valid Mono<CreatePreferencesRequestApiDto> preferencesApiDto,
      String xRequestId,
      ServerWebExchange exchange) {
    return checkRoleAccess(UPDATE, exchange)
        .then(preferencesApiDto)
        .flatMap(request -> preferencesService.updatePreferences(xRequestId, request))
        .map(ResponseEntity::ok);
  }
}

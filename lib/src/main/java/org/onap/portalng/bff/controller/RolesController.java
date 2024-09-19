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

import org.onap.portalng.bff.config.BffConfig;
import org.onap.portalng.bff.openapi.server.api.RolesApi;
import org.onap.portalng.bff.openapi.server.model.RoleListResponseApiDto;
import org.onap.portalng.bff.services.KeycloakService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
public class RolesController extends AbstractBffController implements RolesApi {

  public static final String LIST = "ROLE_LIST";

  private final KeycloakService keycloakService;

  @Autowired
  public RolesController(BffConfig bffConfig, KeycloakService keycloakService) {
    super(bffConfig);
    this.keycloakService = keycloakService;
  }

  @Override
  public Mono<ResponseEntity<RoleListResponseApiDto>> listRoles(
      String xRequestId, ServerWebExchange exchange) {
    return keycloakService
        .listRoles(xRequestId)
        .collectList()
        .map(roles -> new RoleListResponseApiDto().items(roles).totalCount(roles.size()))
        .map(ResponseEntity::ok);
  }
}

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
import org.onap.portalng.bff.openapi.server.api.UsersApi;
import org.onap.portalng.bff.openapi.server.model.CreateUserRequestApiDto;
import org.onap.portalng.bff.openapi.server.model.RoleApiDto;
import org.onap.portalng.bff.openapi.server.model.RoleListResponseApiDto;
import org.onap.portalng.bff.openapi.server.model.UpdateUserPasswordRequestApiDto;
import org.onap.portalng.bff.openapi.server.model.UpdateUserRequestApiDto;
import org.onap.portalng.bff.openapi.server.model.UserListResponseApiDto;
import org.onap.portalng.bff.openapi.server.model.UserResponseApiDto;
import org.onap.portalng.bff.services.KeycloakService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
public class UsersController extends AbstractBffController implements UsersApi {

  private final KeycloakService keycloakService;

  @Autowired
  public UsersController(BffConfig bffConfig, KeycloakService keycloakService) {
    super(bffConfig);
    this.keycloakService = keycloakService;
  }

  @Override
  public Mono<ResponseEntity<UserResponseApiDto>> createUser(
      Mono<CreateUserRequestApiDto> requestMono, String xRequestId, ServerWebExchange exchange) {
    return requestMono
        .flatMap(request -> keycloakService.createUser(request, xRequestId))
        .map(ResponseEntity::ok);
  }

  @Override
  public Mono<ResponseEntity<UserResponseApiDto>> getUser(
      String userId, String xRequestId, ServerWebExchange exchange) {
    return keycloakService.getUser(userId, xRequestId).map(ResponseEntity::ok);
  }

  @Override
  public Mono<ResponseEntity<Void>> updateUser(
      String userId,
      Mono<UpdateUserRequestApiDto> requestMono,
      String xRequestId,
      ServerWebExchange exchange) {
    return requestMono
        .flatMap(request -> keycloakService.updateUser(userId, request, xRequestId))
        .map(ResponseEntity::ok);
  }

  @Override
  public Mono<ResponseEntity<Void>> deleteUser(
      String userId, String xRequestId, ServerWebExchange exchange) {
    return keycloakService
        .deleteUser(userId, xRequestId)
        .thenReturn(ResponseEntity.noContent().build());
  }

  @Override
  public Mono<ResponseEntity<UserListResponseApiDto>> listUsers(
      Integer page, Integer pageSize, String xRequestId, ServerWebExchange exchange) {

    return keycloakService.listUsers(page, pageSize, xRequestId).map(ResponseEntity::ok);
  }

  @Override
  public Mono<ResponseEntity<Void>> updatePassword(
      String userId,
      Mono<UpdateUserPasswordRequestApiDto> requestMono,
      String xRequestId,
      ServerWebExchange exchange) {
    return requestMono
        .flatMap(request -> keycloakService.updateUserPassword(userId, request))
        .thenReturn(ResponseEntity.noContent().build());
  }

  @Override
  public Mono<ResponseEntity<RoleListResponseApiDto>> listAvailableRoles(
      String userId, String xRequestId, ServerWebExchange exchange) {
    return keycloakService.getAvailableRoles(userId, xRequestId).map(ResponseEntity::ok);
  }

  @Override
  public Mono<ResponseEntity<RoleListResponseApiDto>> listAssignedRoles(
      String userId, String xRequestId, ServerWebExchange exchange) {
    return keycloakService.getAssignedRoles(userId, xRequestId).map(ResponseEntity::ok);
  }

  @Override
  public Mono<ResponseEntity<RoleListResponseApiDto>> updateAssignedRoles(
      String userId, String xRequestId, Flux<RoleApiDto> rolesFlux, ServerWebExchange exchange) {
    return rolesFlux
        .collectList()
        .flatMap(roles -> keycloakService.updateAssignedRoles(userId, roles, xRequestId))
        .map(ResponseEntity::ok);
  }
}

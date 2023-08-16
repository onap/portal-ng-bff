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

import org.onap.portalng.bff.config.PortalBffConfig;
import org.onap.portalng.bff.openapi.server.api.ActionsApi;
import org.onap.portalng.bff.openapi.server.model.ActionsListResponseApiDto;
import org.onap.portalng.bff.openapi.server.model.ActionsResponseApiDto;
import org.onap.portalng.bff.openapi.server.model.CreateActionRequestApiDto;
import org.onap.portalng.bff.services.ActionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
public class ActionsController extends AbstractBffController implements ActionsApi {
  public static final String CREATE = "ACTIONS_CREATE";
  public static final String GET = "ACTIONS_GET";
  public static final String LIST = "ACTIONS_LIST";

  private final ActionService actionService;

  public ActionsController(PortalBffConfig bffConfig, ActionService actionService) {
    super(bffConfig);
    this.actionService = actionService;
  }

  @Override
  public Mono<ResponseEntity<ActionsResponseApiDto>> createAction(
      String userId,
      String xRequestId,
      Mono<CreateActionRequestApiDto> createActionRequestApiDto,
      ServerWebExchange exchange) {
    return checkRoleAccess(CREATE, exchange)
        .then(createActionRequestApiDto)
        .flatMap(action -> actionService.createAction(userId, xRequestId, action))
        .map(ResponseEntity::ok);
  }

  @Override
  public Mono<ResponseEntity<ActionsListResponseApiDto>> getActions(
      String userId,
      Integer page,
      Integer pageSize,
      Integer showLastHours,
      String xRequestId,
      ServerWebExchange exchange) {
    return checkRoleAccess(GET, exchange)
        .then(actionService.getActions(userId, xRequestId, page, pageSize, showLastHours))
        .map(ResponseEntity::ok);
  }

  @Override
  public Mono<ResponseEntity<ActionsListResponseApiDto>> listActions(
      Integer page,
      Integer pageSize,
      Integer showLastHours,
      String xRequestId,
      ServerWebExchange exchange) {
    return checkRoleAccess(LIST, exchange)
        .then(actionService.listActions(xRequestId, page, pageSize, showLastHours))
        .map(ResponseEntity::ok);
  }
}

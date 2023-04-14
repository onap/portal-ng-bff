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

package org.onap.portal.bff.services;

import lombok.RequiredArgsConstructor;
import org.onap.portal.bff.exceptions.DownstreamApiProblemException;
import org.onap.portal.bff.openapi.client_portal_history.api.ActionsApi;
import org.onap.portal.bff.openapi.client_portal_history.model.CreateActionRequestPortalHistoryDto;
import org.onap.portal.bff.openapi.server.model.ActionsListResponseApiDto;
import org.onap.portal.bff.openapi.server.model.ActionsResponseApiDto;
import org.onap.portal.bff.openapi.server.model.CreateActionRequestApiDto;
import org.onap.portal.bff.openapi.server.model.ProblemApiDto;
import org.onap.portal.bff.utils.Logger;
import org.springframework.core.convert.support.ConfigurableConversionService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
@Service
public class ActionService {
  private final ActionsApi actionsApi;
  private final ConfigurableConversionService conversionService;

  public Mono<ActionsResponseApiDto> createAction(
      String userId, String xRequestId, CreateActionRequestApiDto createActionRequestApiDto) {
    // First map from server API model to client API model
    CreateActionRequestPortalHistoryDto createActionRequestPortalHistoryDto =
        new CreateActionRequestPortalHistoryDto();
    createActionRequestPortalHistoryDto.setUserId(createActionRequestApiDto.getUserId());
    createActionRequestPortalHistoryDto.setAction(createActionRequestApiDto.getAction());
    createActionRequestPortalHistoryDto.setActionCreatedAt(
        createActionRequestApiDto.getActionCreatedAt());

    return actionsApi
        .createAction(userId, xRequestId, createActionRequestPortalHistoryDto)
        .map(
            action ->
                new ActionsResponseApiDto()
                    .action(action.getAction())
                    .actionCreatedAt(action.getActionCreatedAt()))
        .onErrorResume(
            DownstreamApiProblemException.class,
            ex -> {
              Logger.errorLog(
                  xRequestId,
                  "Create actions failed for userId",
                  userId,
                  ProblemApiDto.DownstreamSystemEnum.PORTAL_HISTORY.toString());
              return Mono.error(ex);
            });
  }

  public Mono<ActionsListResponseApiDto> getActions(
      String userId, String xRequestId, Integer page, Integer pageSize, Integer showLastHours) {

    return actionsApi
        .getActions(userId, xRequestId, page, pageSize, showLastHours)
        .map(actions -> conversionService.convert(actions, ActionsListResponseApiDto.class))
        .onErrorResume(
            DownstreamApiProblemException.class,
            ex -> {
              Logger.errorLog(
                  xRequestId,
                  "Get actions failed for userId",
                  userId,
                  ProblemApiDto.DownstreamSystemEnum.PORTAL_HISTORY.toString());
              return Mono.error(ex);
            });
  }

  public Mono<ActionsListResponseApiDto> listActions(
      String xRequestId, Integer page, Integer pageSize, Integer showLast) {
    return actionsApi
        .listActions(xRequestId, page, pageSize, showLast)
        .map(
            responseEntity ->
                conversionService.convert(responseEntity, ActionsListResponseApiDto.class))
        .onErrorResume(
            DownstreamApiProblemException.class,
            ex -> {
              Logger.errorLog(
                  xRequestId,
                  "List actions failed",
                  null,
                  ProblemApiDto.DownstreamSystemEnum.PORTAL_HISTORY.toString());
              return Mono.error(ex);
            });
  }

  public Mono<Object> deleteActions(String userId, String xRequestId, Integer deleteAfterHours) {
    return actionsApi
        .deleteActions(userId, xRequestId, deleteAfterHours)
        .onErrorResume(
            DownstreamApiProblemException.class,
            ex -> {
              Logger.errorLog(
                  xRequestId,
                  "Get actions failed for userId because actions cannot be deleted after "
                      + deleteAfterHours
                      + " hours",
                  userId,
                  ProblemApiDto.DownstreamSystemEnum.PORTAL_HISTORY.toString());
              return Mono.error(ex);
            });
  }
}

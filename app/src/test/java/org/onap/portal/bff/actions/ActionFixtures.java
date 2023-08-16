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

package org.onap.portalng.bff.actions;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import org.onap.portalng.bff.openapi.client_portal_history.model.ActionResponsePortalHistoryDto;
import org.onap.portalng.bff.openapi.client_portal_history.model.ActionsListResponsePortalHistoryDto;
import org.onap.portalng.bff.openapi.client_portal_history.model.CreateActionRequestPortalHistoryDto;
import org.onap.portalng.bff.openapi.server.model.CreateActionRequestApiDto;

public class ActionFixtures {

  public static ActionsListResponsePortalHistoryDto generateActionsListResponse(
      Integer numberOfActions, Integer totalCount, OffsetDateTime createdAt) {
    ActionsListResponsePortalHistoryDto actionsListResponsePortalHistoryDto =
        new ActionsListResponsePortalHistoryDto();
    for (Integer i = 0; i < numberOfActions; i++) {
      actionsListResponsePortalHistoryDto.addActionsListItem(
          generateActionResponse(
              "Instantiation", "create", null, i.toString(), "SO", i, createdAt));
    }
    actionsListResponsePortalHistoryDto.setTotalCount(totalCount);
    return actionsListResponsePortalHistoryDto;
  }

  public static ActionResponsePortalHistoryDto generateActionResponse(
      String type,
      String action,
      String message,
      String id,
      String downStreamSystem,
      Integer deltaHours,
      OffsetDateTime createdAt) {
    ActionDto actionDto = new ActionDto();
    actionDto.setType(type);
    actionDto.setAction(action);
    actionDto.setMessage(message);
    actionDto.setDownStreamSystem(downStreamSystem);
    actionDto.setDownStreamId(id);

    return new ActionResponsePortalHistoryDto()
        .action(actionDto)
        .actionCreatedAt(createdAt.minus(deltaHours, ChronoUnit.HOURS));
  }

  public static CreateActionRequestPortalHistoryDto generateActionRequestPortalHistoryDto(
      String type,
      String action,
      String message,
      String id,
      String downStreamSystem,
      String userId,
      OffsetDateTime createdAt) {
    ActionDto actionDto = new ActionDto();
    actionDto.setType(type);
    actionDto.setAction(action);
    actionDto.setMessage(message);
    actionDto.setDownStreamSystem(downStreamSystem);
    actionDto.setDownStreamId(id);
    return new CreateActionRequestPortalHistoryDto()
        .action(actionDto)
        .actionCreatedAt(createdAt)
        .userId(userId);
  }

  public static CreateActionRequestApiDto generateCreateActionRequestApiDto(
      String type,
      String action,
      String message,
      String id,
      String downStreamSystem,
      String userId,
      OffsetDateTime createdAt) {
    ActionDto actionDto = new ActionDto();
    actionDto.setType(type);
    actionDto.setAction(action);
    actionDto.setMessage(message);
    actionDto.setDownStreamSystem(downStreamSystem);
    actionDto.setDownStreamId(id);

    return new CreateActionRequestApiDto()
        .action(actionDto)
        .actionCreatedAt(createdAt)
        .userId(userId);
  }
}

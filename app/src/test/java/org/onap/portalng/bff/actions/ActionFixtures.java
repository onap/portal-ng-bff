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
import org.onap.portalng.bff.openapi.client_history.model.ActionResponseHistoryDto;
import org.onap.portalng.bff.openapi.client_history.model.ActionsListResponseHistoryDto;
import org.onap.portalng.bff.openapi.client_history.model.CreateActionRequestHistoryDto;
import org.onap.portalng.bff.openapi.server.model.CreateActionRequestApiDto;

public class ActionFixtures {

  public static ActionsListResponseHistoryDto generateActionsListResponse(
      Integer numberOfActions, Integer totalCount, OffsetDateTime createdAt) {
    ActionsListResponseHistoryDto actionsListResponseHistoryDto =
        new ActionsListResponseHistoryDto();
    for (Integer i = 0; i < numberOfActions; i++) {
      actionsListResponseHistoryDto.addActionsListItem(
          generateActionResponse(
              "Instantiation", "create", null, i.toString(), "SO", i, createdAt));
    }
    actionsListResponseHistoryDto.setTotalCount(totalCount);
    return actionsListResponseHistoryDto;
  }

  public static ActionResponseHistoryDto generateActionResponse(
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

    return new ActionResponseHistoryDto()
        .action(actionDto)
        .actionCreatedAt(createdAt.minus(deltaHours, ChronoUnit.HOURS));
  }

  public static CreateActionRequestHistoryDto generateActionRequestHistoryDto(
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
    return new CreateActionRequestHistoryDto()
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

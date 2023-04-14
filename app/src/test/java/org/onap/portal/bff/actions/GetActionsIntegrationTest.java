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

package org.onap.portal.bff.actions;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.onap.portal.bff.openapi.client_portal_history.model.ActionsListResponsePortalHistoryDto;
import org.onap.portal.bff.openapi.server.model.ActionsListResponseApiDto;

class GetActionsIntegrationTest extends ActionsMocks {

  @Test
  void thatActionCanBeRetrievedWithParameterShowLastHours() throws Exception {
    int numberOfActions = 10;
    Integer showLastHours = 2;
    String userId = "22-33-44-55";
    OffsetDateTime createdAt = OffsetDateTime.now();
    ActionsListResponsePortalHistoryDto actionsListResponsePortalHistoryDto =
        ActionFixtures.generateActionsListResponse(numberOfActions, 30, createdAt);

    mockGetActions(actionsListResponsePortalHistoryDto, userId, showLastHours);

    final ActionsListResponseApiDto response = getActions(userId);

    assertThat(response.getTotalCount()).isEqualTo(30);
    assertThat(response.getItems()).hasSize(numberOfActions);
    assertThat(response.getItems().get(0).getActionCreatedAt())
        .isEqualTo(
            actionsListResponsePortalHistoryDto.getActionsList().get(0).getActionCreatedAt());
    assertThat(objectMapper.writeValueAsString(response.getItems().get(0).getAction()))
        .isEqualTo(
            objectMapper.writeValueAsString(
                actionsListResponsePortalHistoryDto.getActionsList().get(0).getAction()));
  }

  @Test
  void thatActionCanBeRetrievedWithoutParameterShowLastHours() throws Exception {
    int numberOfActions = 10;
    String userId = "22-33-44-55";
    OffsetDateTime createdAt = OffsetDateTime.now();
    ActionsListResponsePortalHistoryDto actionsListResponsePortalHistoryDto =
        ActionFixtures.generateActionsListResponse(numberOfActions, 30, createdAt);

    mockGetActionsWithoutParameterShowLastHours(actionsListResponsePortalHistoryDto, userId);

    final ActionsListResponseApiDto response = getActionsWithoutParameterShowLastHours(userId);

    assertThat(response.getTotalCount()).isEqualTo(30);
    assertThat(response.getItems()).hasSize(numberOfActions);
    assertThat(response.getItems().get(0).getActionCreatedAt())
        .isEqualTo(
            actionsListResponsePortalHistoryDto.getActionsList().get(0).getActionCreatedAt());
    assertThat(objectMapper.writeValueAsString(response.getItems().get(0).getAction()))
        .isEqualTo(
            objectMapper.writeValueAsString(
                actionsListResponsePortalHistoryDto.getActionsList().get(0).getAction()));
  }
}

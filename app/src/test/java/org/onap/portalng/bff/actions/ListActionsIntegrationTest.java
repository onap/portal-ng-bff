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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.onap.portalng.bff.openapi.client_history.model.ActionsListResponseHistoryDto;
import org.onap.portalng.bff.openapi.client_history.model.ProblemHistoryDto;
import org.onap.portalng.bff.openapi.server.model.ActionsListResponseApiDto;
import org.onap.portalng.bff.openapi.server.model.ProblemApiDto;
import org.springframework.http.HttpStatus;

class ListActionsIntegrationTest extends ActionsMocks {

  @Test
  void thatActionsListCanBeRetrieved() throws Exception {
    int numberOfActions = 10;
    OffsetDateTime createdAt = OffsetDateTime.now();
    ActionsListResponseHistoryDto actionsListResponseHistoryDto =
        ActionFixtures.generateActionsListResponse(numberOfActions, 1000, createdAt);

    mockListActions(actionsListResponseHistoryDto);

    final ActionsListResponseApiDto response = listActions();

    assertThat(response.getTotalCount()).isEqualTo(1000);
    assertThat(response.getItems()).hasSize(numberOfActions);
    assertThat(response.getItems().get(0).getActionCreatedAt())
        .isEqualTo(actionsListResponseHistoryDto.getActionsList().get(0).getActionCreatedAt());
    Assertions.assertThat(objectMapper.writeValueAsString(response.getItems().get(0).getAction()))
        .isEqualTo(
            objectMapper.writeValueAsString(
                actionsListResponseHistoryDto.getActionsList().get(0).getAction()));
  }

  @Test
  void thatActionsListCanNotBeRetrieved() throws Exception {

    ProblemHistoryDto problemHistoryDto =
        new ProblemHistoryDto()
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .detail("Internal database error")
            .title("Internal Server Error")
            .instance("history");

    mockListActionsProblem(problemHistoryDto);

    final ProblemApiDto response = listActionsProblem();

    assertThat(response.getDownstreamSystem())
        .isEqualTo(ProblemApiDto.DownstreamSystemEnum.HISTORY);
    assertThat(response.getDownstreamStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
    assertThat(response.getDetail()).isEqualTo(problemHistoryDto.getDetail());
  }
}

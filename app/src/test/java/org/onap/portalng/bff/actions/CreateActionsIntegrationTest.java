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
import org.onap.portalng.bff.openapi.client_history.model.ActionResponseHistoryDto;
import org.onap.portalng.bff.openapi.client_history.model.ProblemHistoryDto;
import org.onap.portalng.bff.openapi.server.model.ActionsResponseApiDto;
import org.onap.portalng.bff.openapi.server.model.CreateActionRequestApiDto;
import org.onap.portalng.bff.openapi.server.model.ProblemApiDto;
import org.springframework.http.HttpStatus;

class CreateActionsIntegrationTest extends ActionsMocks {

  @Test
  void thatActionCanBeCreated() throws Exception {
    String userId = "22-33-44-55";
    OffsetDateTime createdAt = OffsetDateTime.now();
    ActionResponseHistoryDto actionResponseHistoryDto =
        ActionFixtures.generateActionResponse(
            "Instantiation", "create", "no detail message", "223344", "SO", 0, createdAt);
    CreateActionRequestApiDto createActionDto =
        ActionFixtures.generateCreateActionRequestApiDto(
            "Instantiation", "create", "no detail message", "223344", "SO", userId, createdAt);

    mockCreateActions(userId, actionResponseHistoryDto);

    final ActionsResponseApiDto response = createAction(createActionDto, userId);

    assertThat(response.getActionCreatedAt())
        .isEqualTo(actionResponseHistoryDto.getActionCreatedAt());
    Assertions.assertThat(objectMapper.writeValueAsString(response.getAction()))
        .isEqualTo(objectMapper.writeValueAsString(actionResponseHistoryDto.getAction()));
  }

  @Test
  void thatActionCanNotBeCreated() throws Exception {
    String userId = "22-33-44-55";
    OffsetDateTime createdAt = OffsetDateTime.now();

    ProblemHistoryDto problemHistoryDto =
        new ProblemHistoryDto()
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .detail("Internal database error")
            .title("Internal Server Error")
            .instance("history");

    CreateActionRequestApiDto createActionDto =
        ActionFixtures.generateCreateActionRequestApiDto(
            "Instantiation", "create", "no detail message", "223344", "SO", userId, createdAt);

    mockCreateActionsProblem(userId, problemHistoryDto);

    final ProblemApiDto response = createActionProblem(createActionDto, userId);

    assertThat(response.getDownstreamSystem())
        .isEqualTo(ProblemApiDto.DownstreamSystemEnum.HISTORY);

    assertThat(response.getDownstreamStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
    assertThat(response.getDetail()).isEqualTo(problemHistoryDto.getDetail());
  }
}

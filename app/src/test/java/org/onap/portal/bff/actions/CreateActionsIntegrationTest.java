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
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.onap.portal.bff.openapi.client_portal_history.model.ActionResponsePortalHistoryDto;
import org.onap.portal.bff.openapi.client_portal_history.model.ProblemPortalHistoryDto;
import org.onap.portal.bff.openapi.server.model.ActionsResponseApiDto;
import org.onap.portal.bff.openapi.server.model.CreateActionRequestApiDto;
import org.onap.portal.bff.openapi.server.model.ProblemApiDto;
import org.springframework.http.HttpStatus;

class CreateActionsIntegrationTest extends ActionsMocks {

  @Test
  void thatActionCanBeCreated() throws Exception {
    String userId = "22-33-44-55";
    OffsetDateTime createdAt = OffsetDateTime.now();
    ActionResponsePortalHistoryDto actionResponsePortalHistoryDto =
        ActionFixtures.generateActionResponse(
            "Instantiation", "create", "no detail message", "223344", "SO", 0, createdAt);
    CreateActionRequestApiDto createActionDto =
        ActionFixtures.generateCreateActionRequestApiDto(
            "Instantiation", "create", "no detail message", "223344", "SO", userId, createdAt);

    mockCreateActions(userId, actionResponsePortalHistoryDto);

    final ActionsResponseApiDto response = createAction(createActionDto, userId);

    assertThat(response.getActionCreatedAt())
        .isEqualTo(actionResponsePortalHistoryDto.getActionCreatedAt());
    Assertions.assertThat(objectMapper.writeValueAsString(response.getAction()))
        .isEqualTo(objectMapper.writeValueAsString(actionResponsePortalHistoryDto.getAction()));
  }

  @Test
  void thatActionCanNotBeCreated() throws Exception {
    String userId = "22-33-44-55";
    OffsetDateTime createdAt = OffsetDateTime.now();

    ProblemPortalHistoryDto problemPortalHistoryDto =
        new ProblemPortalHistoryDto()
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .detail("Internal database error")
            .title("Internal Server Error")
            .instance("portal-history");

    CreateActionRequestApiDto createActionDto =
        ActionFixtures.generateCreateActionRequestApiDto(
            "Instantiation", "create", "no detail message", "223344", "SO", userId, createdAt);

    mockCreateActionsProblem(userId, problemPortalHistoryDto);

    final ProblemApiDto response = createActionProblem(createActionDto, userId);

    assertThat(response.getDownstreamSystem())
        .isEqualTo(ProblemApiDto.DownstreamSystemEnum.PORTAL_HISTORY);

    assertThat(response.getDownstreamStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
    assertThat(response.getDetail()).isEqualTo("justtomakethisfail");
  }
}

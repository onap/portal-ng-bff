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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.EqualToPattern;
import io.restassured.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.onap.portalng.bff.BaseIntegrationTest;
import org.onap.portalng.bff.openapi.client_history.model.ActionResponseHistoryDto;
import org.onap.portalng.bff.openapi.client_history.model.ActionsListResponseHistoryDto;
import org.onap.portalng.bff.openapi.client_history.model.ProblemHistoryDto;
import org.onap.portalng.bff.openapi.server.model.ActionsListResponseApiDto;
import org.onap.portalng.bff.openapi.server.model.ActionsResponseApiDto;
import org.onap.portalng.bff.openapi.server.model.CreateActionRequestApiDto;
import org.onap.portalng.bff.openapi.server.model.ProblemApiDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

public class ActionsMocks extends BaseIntegrationTest {
  protected static final String X_REQUEST_ID = "addf6005-3075-4c80-b7bc-2c70b7d42b00";

  // used for test thatActionsListCanBeRetrieved
  protected ActionsListResponseApiDto listActions() {
    return requestSpecification()
        .given()
        .accept(MediaType.APPLICATION_JSON_VALUE)
        .header(new Header("X-Request-Id", X_REQUEST_ID))
        .when()
        .get("/actions")
        .then()
        .statusCode(HttpStatus.OK.value())
        .extract()
        .body()
        .as(ActionsListResponseApiDto.class);
  }

  // used for test thatActionsListCanBeRetrieved
  protected void mockListActions(ActionsListResponseHistoryDto actionsListResponseHistoryDto)
      throws Exception {
    WireMock.stubFor(
        WireMock.get(WireMock.urlEqualTo("/v1/actions?page=1&pageSize=10"))
            .withHeader("X-Request-Id", new EqualToPattern(X_REQUEST_ID))
            .willReturn(
                WireMock.aResponse()
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withBody(objectMapper.writeValueAsString(actionsListResponseHistoryDto))));
  }

  // used for test thatActionsListCanNotBeRetrieved
  protected ProblemApiDto listActionsProblem() {
    return requestSpecification()
        .given()
        .accept(MediaType.APPLICATION_JSON_VALUE)
        .header(new Header("X-Request-Id", X_REQUEST_ID))
        .when()
        .get("/actions")
        .then()
        .statusCode(HttpStatus.BAD_GATEWAY.value())
        .extract()
        .body()
        .as(ProblemApiDto.class);
  }

  // used for test thatActionsListCanNotBeRetrieved
  protected void mockListActionsProblem(ProblemHistoryDto problemHistoryDto) throws Exception {
    WireMock.stubFor(
        WireMock.get(WireMock.urlEqualTo("/v1/actions?page=1&pageSize=10"))
            .withHeader("X-Request-Id", new EqualToPattern(X_REQUEST_ID))
            .willReturn(
                WireMock.aResponse()
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE)
                    .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
                    .withBody(objectMapper.writeValueAsString(problemHistoryDto))));
  }

  // used for test thatActionCanBeRetrieved
  protected void mockGetActions(
      ActionsListResponseHistoryDto actionsListResponseHistoryDto,
      String userId,
      Integer showLastHours)
      throws Exception {
    WireMock.stubFor(
        WireMock.get(
                WireMock.urlEqualTo(
                    "/v1/actions/"
                        + userId
                        + "?page=1&pageSize=10"
                        + "&showLastHours="
                        + showLastHours))
            .withHeader("X-Request-Id", new EqualToPattern(X_REQUEST_ID))
            .willReturn(
                WireMock.aResponse()
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withBody(objectMapper.writeValueAsString(actionsListResponseHistoryDto))));
  }

  // used for test thatActionCanBeRetrieved
  protected ActionsListResponseApiDto getActions(String userId) {
    return requestSpecification()
        .given()
        .accept(MediaType.APPLICATION_JSON_VALUE)
        .header(new Header("X-Request-Id", X_REQUEST_ID))
        .when()
        .get("/actions/" + userId + "?page=1&pageSize=10&showLastHours=2")
        .then()
        .statusCode(HttpStatus.OK.value())
        .extract()
        .body()
        .as(ActionsListResponseApiDto.class);
  }

  // used for test thatActionCanBeRetrievedWithoutParameterShowLastHours
  protected void mockGetActionsWithoutParameterShowLastHours(
      ActionsListResponseHistoryDto actionsListResponseHistoryDto, String userId) throws Exception {
    WireMock.stubFor(
        WireMock.get(WireMock.urlEqualTo("/v1/actions/" + userId + "?page=1&pageSize=10"))
            .withHeader("X-Request-Id", new EqualToPattern(X_REQUEST_ID))
            .willReturn(
                WireMock.aResponse()
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withBody(objectMapper.writeValueAsString(actionsListResponseHistoryDto))));
  }

  // used for test thatActionCanBeRetrievedWithoutParameterShowLastHours
  protected ActionsListResponseApiDto getActionsWithoutParameterShowLastHours(String userId) {
    return requestSpecification()
        .given()
        .accept(MediaType.APPLICATION_JSON_VALUE)
        .header(new Header("X-Request-Id", X_REQUEST_ID))
        .when()
        .get("/actions/" + userId + "?page=1&pageSize=10")
        .then()
        .statusCode(HttpStatus.OK.value())
        .extract()
        .body()
        .as(ActionsListResponseApiDto.class);
  }

  // Used for thatActionCanBeCreated
  protected void mockCreateActions(String userId, ActionResponseHistoryDto actionResponseHistoryDto)
      throws Exception {
    WireMock.stubFor(
        WireMock.post(WireMock.urlEqualTo("/v1/actions/" + userId))
            .withHeader("X-Request-Id", new EqualToPattern(X_REQUEST_ID))
            .withRequestBody(WireMock.matchingJsonPath("$.action"))
            .willReturn(
                WireMock.aResponse()
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withStatus(200)
                    .withBody(objectMapper.writeValueAsString(actionResponseHistoryDto))));
  }

  // Used for thatActionCanBeCreated
  protected ActionsResponseApiDto createAction(
      CreateActionRequestApiDto createActionRequestApiDto, String userId) throws Exception {
    return requestSpecification()
        .given()
        .accept(MediaType.APPLICATION_JSON_VALUE)
        .contentType(MediaType.APPLICATION_JSON_VALUE)
        .header(new Header("X-Request-Id", X_REQUEST_ID))
        .body(objectMapper.writeValueAsString(createActionRequestApiDto))
        .when()
        .post("/actions/" + userId)
        .then()
        .statusCode(HttpStatus.OK.value())
        .extract()
        .body()
        .as(ActionsResponseApiDto.class);
  }

  // Used for thatActionCanNotBeCreated
  protected void mockCreateActionsProblem(String userId, ProblemHistoryDto problemHistoryDto)
      throws JsonProcessingException {
    WireMock.stubFor(
        WireMock.post(WireMock.urlEqualTo("/v1/actions/" + userId))
            .withHeader("X-Request-Id", new EqualToPattern(X_REQUEST_ID))
            .withRequestBody(WireMock.matchingJsonPath("$.action"))
            .willReturn(
                WireMock.aResponse()
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE)
                    .withStatus(500)
                    .withBody(objectMapper.writeValueAsString(problemHistoryDto))));
  }

  // Used for thatActionCanNotBeCreated
  protected ProblemApiDto createActionProblem(
      CreateActionRequestApiDto createActionRequestApiDto, String userId)
      throws JsonProcessingException {
    return requestSpecification()
        .given()
        .accept(MediaType.APPLICATION_JSON_VALUE)
        .contentType(MediaType.APPLICATION_JSON_VALUE)
        .header(new Header("X-Request-Id", X_REQUEST_ID))
        .body(objectMapper.writeValueAsString(createActionRequestApiDto))
        .when()
        .post("/actions/" + userId)
        .then()
        .statusCode(HttpStatus.BAD_GATEWAY.value())
        .extract()
        .body()
        .as(ProblemApiDto.class);
  }
}

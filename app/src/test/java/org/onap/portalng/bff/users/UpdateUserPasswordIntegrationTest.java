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

package org.onap.portalng.bff.users;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.restassured.http.Header;
import org.junit.jupiter.api.Test;
import org.onap.portalng.bff.BaseIntegrationTest;
import org.onap.portalng.bff.openapi.client_keycloak.model.CredentialKeycloakDto;
import org.onap.portalng.bff.openapi.client_keycloak.model.ErrorResponseKeycloakDto;
import org.onap.portalng.bff.openapi.server.model.ProblemApiDto;
import org.onap.portalng.bff.openapi.server.model.UpdateUserPasswordRequestApiDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

class UpdateUserPasswordIntegrationTest extends BaseIntegrationTest {

  @Test
  void userPasswordCanBeUpdated() throws Exception {
    final CredentialKeycloakDto keycloakRequest =
        new CredentialKeycloakDto().temporary(true).value("pswd");

    WireMock.stubFor(
        WireMock.put(
                WireMock.urlMatching(
                    String.format("/auth/admin/realms/%s/users/1/reset-password", realm)))
            .withRequestBody(WireMock.equalTo(objectMapper.writeValueAsString(keycloakRequest)))
            .willReturn(WireMock.aResponse().withStatus(204)));

    final UpdateUserPasswordRequestApiDto request =
        new UpdateUserPasswordRequestApiDto().temporary(true).value("pswd");

    requestSpecification()
        .given()
        .contentType(MediaType.APPLICATION_JSON_VALUE)
        .header(new Header("X-Request-Id", "addf6005-3075-4c80-b7bc-2c70b7d42b57"))
        .body(request)
        .when()
        .put("/users/1/password")
        .then()
        .statusCode(HttpStatus.NO_CONTENT.value());
  }

  @Test
  void userPasswordCanNotBeUpdated() throws Exception {
    final ErrorResponseKeycloakDto keycloakErrorResponse =
        new ErrorResponseKeycloakDto().errorMessage("Some error message");

    final CredentialKeycloakDto keycloakRequest =
        new CredentialKeycloakDto().temporary(true).value("pswd");

    WireMock.stubFor(
        WireMock.put(
                WireMock.urlMatching(
                    String.format("/auth/admin/realms/%s/users/1/reset-password", realm)))
            .withRequestBody(WireMock.equalTo(objectMapper.writeValueAsString(keycloakRequest)))
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withStatus(400)
                    .withBody(objectMapper.writeValueAsString(keycloakErrorResponse))));

    final UpdateUserPasswordRequestApiDto request =
        new UpdateUserPasswordRequestApiDto().temporary(true).value("pswd");

    final ProblemApiDto response =
        requestSpecification()
            .given()
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .header(new Header("X-Request-Id", "addf6005-3075-4c80-b7bc-2c70b7d42b57"))
            .body(request)
            .when()
            .put("/users/1/password")
            .then()
            .statusCode(HttpStatus.BAD_GATEWAY.value())
            .extract()
            .body()
            .as(ProblemApiDto.class);

    assertThat(response).isNotNull();
    assertThat(response.getTitle()).isEqualTo(HttpStatus.BAD_REQUEST.toString());
    assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY.value());
    assertThat(response.getDownstreamSystem())
        .isEqualTo(ProblemApiDto.DownstreamSystemEnum.KEYCLOAK);
  }
}

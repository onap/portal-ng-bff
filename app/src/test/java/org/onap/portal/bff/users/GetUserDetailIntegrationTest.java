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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.restassured.http.Header;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.onap.portalng.bff.BaseIntegrationTest;
import org.onap.portalng.bff.openapi.client_portal_keycloak.model.ErrorResponseKeycloakDto;
import org.onap.portalng.bff.openapi.client_portal_keycloak.model.RoleKeycloakDto;
import org.onap.portalng.bff.openapi.client_portal_keycloak.model.UserKeycloakDto;
import org.onap.portalng.bff.openapi.server.model.ProblemApiDto;
import org.onap.portalng.bff.openapi.server.model.UserResponseApiDto;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

class GetUserDetailIntegrationTest extends BaseIntegrationTest {

  @Test
  void detailOfUserCanBeProvided() throws Exception {
    final UserKeycloakDto keycloakUser =
        new UserKeycloakDto().id("1").username("user1").email("user1@localhost").enabled(true);

    WireMock.stubFor(
        WireMock.get(WireMock.urlMatching(String.format("/auth/admin/realms/%s/users/1", realm)))
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withBody(objectMapper.writeValueAsString(keycloakUser))));

    final RoleKeycloakDto keycloackRole = new RoleKeycloakDto().id(randomUUID()).name("onap_admin");
    mockAssignedRoles(keycloakUser.getId(), List.of(keycloackRole));

    final UserResponseApiDto response =
        requestSpecification()
            .given()
            .accept(MediaType.APPLICATION_JSON_VALUE)
            .header(new Header("X-Request-Id", "addf6005-3075-4c80-b7bc-2c70b7d42b57"))
            .when()
            .get("/users/1")
            .then()
            .statusCode(HttpStatus.OK.value())
            .extract()
            .body()
            .as(UserResponseApiDto.class);

    assertThat(response).isNotNull();
    assertThat(response.getId()).isEqualTo("1");
    assertThat(response.getUsername()).isEqualTo("user1");
    assertThat(response.getEmail()).isEqualTo("user1@localhost");
    assertThat(response.getFirstName()).isNull();
    assertThat(response.getLastName()).isNull();
    assertThat(response.getRealmRoles()).containsExactly(keycloackRole.getName());
  }

  @Test
  void detailOfNonExistentUserCanNotBeProvided() throws Exception {

    ErrorResponseKeycloakDto keycloakErrorResponse =
        new ErrorResponseKeycloakDto().errorMessage("Some error message");

    WireMock.stubFor(
        WireMock.get(WireMock.urlMatching(String.format("/auth/admin/realms/%s/users/1", realm)))
            .willReturn(
                WireMock.aResponse()
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withStatus(400)
                    .withBody(objectMapper.writeValueAsString(keycloakErrorResponse))));

    final ProblemApiDto response =
        requestSpecification()
            .given()
            .accept(MediaType.APPLICATION_JSON_VALUE)
            .header(new Header("X-Request-Id", "addf6005-3075-4c80-b7bc-2c70b7d42b57"))
            .when()
            .get("/users/1")
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

  protected void mockAssignedRoles(String userID, List<RoleKeycloakDto> keycloakRoles)
      throws JsonProcessingException {
    WireMock.stubFor(
        WireMock.get(
                WireMock.urlMatching(
                    String.format(
                        "/auth/admin/realms/%s/users/%s/role-mappings/realm", realm, userID)))
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withBody(objectMapper.writeValueAsString(keycloakRoles))));
  }
}

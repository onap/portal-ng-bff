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
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.onap.portalng.bff.BaseIntegrationTest;
import org.onap.portalng.bff.openapi.client_portal_keycloak.model.ErrorResponseKeycloakDto;
import org.onap.portalng.bff.openapi.client_portal_keycloak.model.RequiredActionsKeycloakDto;
import org.onap.portalng.bff.openapi.client_portal_keycloak.model.RoleKeycloakDto;
import org.onap.portalng.bff.openapi.client_portal_keycloak.model.UserKeycloakDto;
import org.onap.portalng.bff.openapi.server.model.CreateUserRequestApiDto;
import org.onap.portalng.bff.openapi.server.model.ProblemApiDto;
import org.onap.portalng.bff.openapi.server.model.RoleApiDto;
import org.onap.portalng.bff.openapi.server.model.RoleListResponseApiDto;
import org.onap.portalng.bff.openapi.server.model.UserResponseApiDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

class CreateUserIntegrationTest extends BaseIntegrationTest {

  @Test
  void userCanBeCreated() throws Exception {
    String xRequestID = "addf6005-3075-4c80-b7bc-2c70b7d42b57";

    final UserKeycloakDto keycloakRequest =
        new UserKeycloakDto()
            .username("user1")
            .email("user1@localhost.com")
            .enabled(true)
            .requiredActions(List.of(RequiredActionsKeycloakDto.UPDATE_PASSWORD));
    final String userId = randomUUID();
    mockCreateUser(keycloakRequest, userId);

    final UserKeycloakDto keycloakResponse =
        new UserKeycloakDto()
            .id(userId)
            .username(keycloakRequest.getUsername())
            .email(keycloakRequest.getEmail())
            .firstName(keycloakRequest.getFirstName())
            .lastName(keycloakRequest.getLastName())
            .enabled(keycloakRequest.getEnabled());
    mockGetUser(userId, keycloakResponse);

    final RoleKeycloakDto onapAdmin = new RoleKeycloakDto().id(randomUUID()).name("onap_admin");
    mockAddRoles(userId, List.of(onapAdmin));
    mockAssignedRoles(userId, List.of(onapAdmin));
    mockListRealmRoles(List.of(onapAdmin));

    requestSpecification()
        .given()
        .accept(MediaType.APPLICATION_JSON_VALUE)
        .header(new Header("X-Request-Id", xRequestID))
        .when()
        .get("/users/{id}/roles", userId)
        .then()
        .statusCode(HttpStatus.OK.value())
        .extract()
        .body()
        .as(RoleListResponseApiDto.class);
    mockSendUpdateEmail(userId, List.of(RequiredActionsKeycloakDto.UPDATE_PASSWORD));

    final CreateUserRequestApiDto request =
        new CreateUserRequestApiDto()
            .username("user1")
            .email("user1@localhost.com")
            .firstName(null)
            .lastName(null)
            .enabled(true)
            .addRolesItem(new RoleApiDto().id(onapAdmin.getId()).name("onap_admin"));

    final UserResponseApiDto response =
        requestSpecification()
            .given()
            .accept(MediaType.APPLICATION_JSON_VALUE)
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .header(new Header("X-Request-Id", xRequestID))
            .body(request)
            .when()
            .post("/users")
            .then()
            .statusCode(HttpStatus.OK.value())
            .extract()
            .body()
            .as(UserResponseApiDto.class);

    assertThat(response).isNotNull();
    assertThat(response.getId()).isEqualTo(userId);
    assertThat(response.getUsername()).isEqualTo(request.getUsername());
    assertThat(response.getEmail()).isEqualTo(request.getEmail());
    assertThat(response.getFirstName()).isEqualTo(request.getFirstName());
    assertThat(response.getLastName()).isEqualTo(request.getLastName());
    assertThat(response.getEnabled()).isEqualTo(request.getEnabled());
    assertThat(response.getRealmRoles()).containsExactly("onap_admin");
  }

  @Test
  void userCanNotBeCreated() throws Exception {
    final UserKeycloakDto keycloakRequest =
        new UserKeycloakDto()
            .username("user1")
            .email("user1@localhost.com")
            .enabled(true)
            .requiredActions(List.of(RequiredActionsKeycloakDto.UPDATE_PASSWORD));
    final String userId = randomUUID();
    mockCreateUser(keycloakRequest, userId);

    final UserKeycloakDto keycloakResponse =
        new UserKeycloakDto()
            .id(userId)
            .username(keycloakRequest.getUsername())
            .email(keycloakRequest.getEmail())
            .firstName(keycloakRequest.getFirstName())
            .lastName(keycloakRequest.getLastName())
            .enabled(keycloakRequest.getEnabled());
    mockGetUser(userId, keycloakResponse);

    final RoleKeycloakDto onapAdmin = new RoleKeycloakDto().id(randomUUID()).name("onap_admin");
    mockAddRoles(userId, List.of(onapAdmin));
    mockListRealmRoles(List.of(onapAdmin));

    final ErrorResponseKeycloakDto keycloakErrorResponse =
        new ErrorResponseKeycloakDto().errorMessage("Some error message");

    mockSendUpdateEmailWithProblem(
        userId, List.of(RequiredActionsKeycloakDto.UPDATE_PASSWORD), keycloakErrorResponse);

    final CreateUserRequestApiDto request =
        new CreateUserRequestApiDto()
            .username("user1")
            .email("user1@localhost.com")
            .firstName(null)
            .lastName(null)
            .enabled(true)
            .addRolesItem(new RoleApiDto().id(onapAdmin.getId()).name("onap_admin"));

    final ProblemApiDto response =
        requestSpecification()
            .given()
            .accept(MediaType.APPLICATION_JSON_VALUE)
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .header(new Header("X-Request-Id", "addf6005-3075-4c80-b7bc-2c70b7d42b57"))
            .body(request)
            .when()
            .post("/users")
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

  @Test
  void userCanNotBeCreatedWithNonexistentRoles() throws Exception {
    String xRequestID = "addf6005-3075-4c80-b7bc-2c70b7d42b57";

    mockListRealmRoles(Collections.emptyList());

    final CreateUserRequestApiDto request =
        new CreateUserRequestApiDto()
            .username("user1")
            .email("user1@localhost.com")
            .firstName(null)
            .lastName(null)
            .enabled(true)
            .addRolesItem(new RoleApiDto().id("nonexistent_id").name("nonexistent_role"));

    final ProblemApiDto response =
        requestSpecification()
            .given()
            .accept(MediaType.APPLICATION_JSON_VALUE)
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .header(new Header("X-Request-Id", xRequestID))
            .body(request)
            .when()
            .post("/users")
            .then()
            .statusCode(HttpStatus.NOT_FOUND.value())
            .extract()
            .body()
            .as(ProblemApiDto.class);

    assertThat(response).isNotNull();
    assertThat(response.getTitle()).isEqualTo(HttpStatus.NOT_FOUND.toString());
    assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    assertThat(response.getDownstreamSystem())
        .isEqualTo(ProblemApiDto.DownstreamSystemEnum.KEYCLOAK);
  }

  protected void mockCreateUser(UserKeycloakDto request, String userId) throws Exception {
    WireMock.stubFor(
        WireMock.post(WireMock.urlMatching(String.format("/auth/admin/realms/%s/users", realm)))
            .withRequestBody(WireMock.equalToJson(objectMapper.writeValueAsString(request)))
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withHeader(
                        "location",
                        String.format("/auth/admin/realms/%s/users/%s", realm, userId))));
  }

  protected void mockGetUser(String userId, UserKeycloakDto response) throws Exception {
    WireMock.stubFor(
        WireMock.get(
                WireMock.urlMatching(
                    String.format("/auth/admin/realms/%s/users/%s", realm, userId)))
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withBody(objectMapper.writeValueAsString(response))));
  }

  protected void mockAddRoles(String userId, List<RoleKeycloakDto> response) throws Exception {
    WireMock.stubFor(
        WireMock.post(
                WireMock.urlMatching(
                    String.format(
                        "/auth/admin/realms/%s/users/%s/role-mappings/realm", realm, userId)))
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withBody(objectMapper.writeValueAsString(response))));
  }

  protected void mockSendUpdateEmailWithProblem(
      String userId,
      List<RequiredActionsKeycloakDto> request,
      ErrorResponseKeycloakDto keycloakErrorResponse)
      throws Exception {
    WireMock.stubFor(
        WireMock.put(
                WireMock.urlMatching(
                    String.format(
                        "/auth/admin/realms/%s/users/%s/execute-actions-email", realm, userId)))
            .withRequestBody(WireMock.equalTo(objectMapper.writeValueAsString(request)))
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withStatus(400)
                    .withBody(objectMapper.writeValueAsString(keycloakErrorResponse))));
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

  protected void mockSendUpdateEmail(String userId, List<RequiredActionsKeycloakDto> request)
      throws Exception {
    WireMock.stubFor(
        WireMock.put(
                WireMock.urlMatching(
                    String.format(
                        "/auth/admin/realms/%s/users/%s/execute-actions-email", realm, userId)))
            .withRequestBody(WireMock.equalTo(objectMapper.writeValueAsString(request)))
            .willReturn(
                WireMock.aResponse().withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)));
  }

  protected void mockListRealmRoles(List<RoleKeycloakDto> roles) throws Exception {
    WireMock.stubFor(
        WireMock.get(WireMock.urlMatching(String.format("/auth/admin/realms/%s/roles", realm)))
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withBody(objectMapper.writeValueAsString(roles))));
  }
}

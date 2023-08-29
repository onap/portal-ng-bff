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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.onap.portalng.bff.BaseIntegrationTest;
import org.onap.portalng.bff.openapi.client_keycloak.model.ErrorResponseKeycloakDto;
import org.onap.portalng.bff.openapi.client_keycloak.model.RoleKeycloakDto;
import org.onap.portalng.bff.openapi.client_keycloak.model.UserKeycloakDto;
import org.onap.portalng.bff.openapi.server.model.ProblemApiDto;
import org.onap.portalng.bff.openapi.server.model.UserListResponseApiDto;
import org.onap.portalng.bff.openapi.server.model.UserResponseApiDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

class ListUsersIntegrationTest extends BaseIntegrationTest {
  private final RoleKeycloakDto ONAP_ADMIN =
      new RoleKeycloakDto().id(randomUUID()).name("portal_admin");
  private final RoleKeycloakDto OFFLINE_ACCESS =
      new RoleKeycloakDto().id(randomUUID()).name("offline_access");

  @Test
  void listOfUsersWithDefaultPaginationCanBeProvided() throws Exception {
    final UserKeycloakDto tAdmin =
        new UserKeycloakDto()
            .id("8f05caaf-0e36-4bcd-b9b3-0ae3d531acc2")
            .username("t-admin")
            .email("t-admin@example.xyz")
            .firstName("FirstName4t-admin")
            .lastName("LastName4t-admin")
            .enabled(true);

    final UserKeycloakDto tDesigner =
        new UserKeycloakDto()
            .id("04ed5525-740d-42da-bc4c-2d3fcf955ee9")
            .username("t-designer")
            .email("t-designer@example.xyz")
            .firstName("FirstName4t-designer")
            .lastName("LastName4t-designer")
            .enabled(true);

    mockGetUserCount(2);
    mockListUsers(List.of(tAdmin, tDesigner), 0, 10);
    mockListRealmRoles(List.of(ONAP_ADMIN, OFFLINE_ACCESS));
    mockListRoleUsers(OFFLINE_ACCESS.getName(), List.of(tAdmin, tDesigner));
    mockListRoleUsers(ONAP_ADMIN.getName(), List.of(tAdmin));

    final UserResponseApiDto expectedTAdmin =
        new UserResponseApiDto()
            .id("8f05caaf-0e36-4bcd-b9b3-0ae3d531acc2")
            .username("t-admin")
            .email("t-admin@example.xyz")
            .firstName("FirstName4t-admin")
            .lastName("LastName4t-admin")
            .enabled(true)
            .addRealmRolesItem("portal_admin")
            .addRealmRolesItem("offline_access");
    final UserResponseApiDto expectedTDesigner =
        new UserResponseApiDto()
            .id("04ed5525-740d-42da-bc4c-2d3fcf955ee9")
            .username("t-designer")
            .email("t-designer@example.xyz")
            .firstName("FirstName4t-designer")
            .lastName("LastName4t-designer")
            .enabled(true)
            .addRealmRolesItem("offline_access");

    final UserListResponseApiDto response = listUsers();
    assertThat(response).isNotNull();
    assertThat(response.getTotalCount()).isEqualTo(2);
    assertThat(response.getItems().get(0).getRealmRoles())
        .containsExactlyInAnyOrder(
            expectedTAdmin.getRealmRoles().get(0), expectedTAdmin.getRealmRoles().get(1));
    assertThat(response.getItems().get(1).getRealmRoles())
        .containsExactly(expectedTDesigner.getRealmRoles().get(0));
  }

  @Test
  void listOfUsersWithSpecifiedPaginationCanBeProvided() throws Exception {
    final UserKeycloakDto keycloakUser =
        new UserKeycloakDto()
            .id("1")
            .username("user1")
            .email("user1@localhost")
            .firstName("User1")
            .lastName("Test")
            .enabled(true);

    mockGetUserCount(1);
    mockListUsers(List.of(keycloakUser), 60, 30);
    mockListRealmRoles(Collections.emptyList());

    final UserListResponseApiDto response = listUsers(Optional.of(3), Optional.of(30));
    assertThat(response).isNotNull();
    assertThat(response.getTotalCount()).isEqualTo(1);
    assertThat(response.getItems())
        .containsExactly(
            new UserResponseApiDto()
                .id("1")
                .username("user1")
                .enabled(true)
                .email("user1@localhost")
                .firstName("User1")
                .lastName("Test")
                .realmRoles(java.util.List.of()));
  }

  @Test
  void listOfUsersWithSpecifiedPaginationCanNotBeProvided() throws Exception {
    final ErrorResponseKeycloakDto keycloakErrorResponse =
        new ErrorResponseKeycloakDto().errorMessage("Some error message");

    mockGetUserCount(55);
    mockListUsersWithProblems(keycloakErrorResponse, 60, 30);
    mockListRealmRoles(Collections.emptyList());

    ProblemApiDto response =
        requestSpecification()
            .given()
            .accept(MediaType.APPLICATION_JSON_VALUE)
            .header(new Header("X-Request-Id", "addf6005-3075-4c80-b7bc-2c70b7d42b57"))
            .when()
            .get(adjustPath("/users", Optional.of(3), Optional.of(30)))
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

  protected void mockGetUserCount(Integer userCount) {
    WireMock.stubFor(
        WireMock.get(
                WireMock.urlMatching(String.format("/auth/admin/realms/%s/users/count", realm)))
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withBody(userCount.toString())));
  }

  protected void mockListUsers(List<UserKeycloakDto> keycloakUsers, Integer first, Integer max)
      throws Exception {
    WireMock.stubFor(
        WireMock.get(
                WireMock.urlMatching(
                    String.format(
                        "/auth/admin/realms/%s/users\\?first=%s&max=%s", realm, first, max)))
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withBody(objectMapper.writeValueAsString(keycloakUsers))));
  }

  protected void mockListRealmRoles(List<RoleKeycloakDto> roles) throws Exception {
    WireMock.stubFor(
        WireMock.get(WireMock.urlMatching(String.format("/auth/admin/realms/%s/roles", realm)))
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withBody(objectMapper.writeValueAsString(roles))));
  }

  protected void mockListRoleUsers(String roleName, List<UserKeycloakDto> response)
      throws Exception {
    WireMock.stubFor(
        WireMock.get(
                WireMock.urlMatching(
                    String.format("/auth/admin/realms/%s/roles/%s/users", realm, roleName)))
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withBody(objectMapper.writeValueAsString(response))));
  }

  protected UserListResponseApiDto listUsers() {
    return listUsers(Optional.empty(), Optional.empty());
  }

  protected UserListResponseApiDto listUsers(Optional<Integer> page, Optional<Integer> pageSize) {
    return requestSpecification()
        .given()
        .accept(MediaType.APPLICATION_JSON_VALUE)
        .header(new Header("X-Request-Id", "addf6005-3075-4c80-b7bc-2c70b7d42b57"))
        .when()
        .get(adjustPath("/users", page, pageSize))
        .then()
        .statusCode(HttpStatus.OK.value())
        .extract()
        .body()
        .as(UserListResponseApiDto.class);
  }

  protected void mockListUsersWithProblems(
      ErrorResponseKeycloakDto keycloakErrorResponse, Integer first, Integer max) throws Exception {
    WireMock.stubFor(
        WireMock.get(
                WireMock.urlMatching(
                    String.format(
                        "/auth/admin/realms/%s/users\\?first=%s&max=%s", realm, first, max)))
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withStatus(400)
                    .withBody(objectMapper.writeValueAsString(keycloakErrorResponse))));
  }
}

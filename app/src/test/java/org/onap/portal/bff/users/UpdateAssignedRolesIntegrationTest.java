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
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.restassured.http.Header;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.onap.portalng.bff.BaseIntegrationTest;
import org.onap.portalng.bff.openapi.client_portal_keycloak.model.ErrorResponseKeycloakDto;
import org.onap.portalng.bff.openapi.client_portal_keycloak.model.RoleKeycloakDto;
import org.onap.portalng.bff.openapi.server.model.ProblemApiDto;
import org.onap.portalng.bff.openapi.server.model.RoleApiDto;
import org.onap.portalng.bff.openapi.server.model.RoleListResponseApiDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

class UpdateAssignedRolesIntegrationTest extends BaseIntegrationTest {

  @Test
  void listOfAssignedRolesCanBeUpdatedWhenPreviousAssignedRolesAreEmpty() throws Exception {
    final RoleKeycloakDto keycloakRole1 = new RoleKeycloakDto().id("1").name("role1");
    final RoleKeycloakDto keycloakRole2 = new RoleKeycloakDto().id("2").name("role2");

    final List<RoleKeycloakDto> keycloakAvailableRoles = List.of(keycloakRole1, keycloakRole2);
    final List<RoleKeycloakDto> keycloakInitialAssignedRoles = List.of();
    final List<RoleKeycloakDto> keycloakUpdatedAssignedRoles = List.of(keycloakRole1);

    final List<RoleKeycloakDto> keycloakRolesToAdd = List.of(keycloakRole1);

    WireMock.stubFor(
        WireMock.get(
                WireMock.urlMatching(
                    String.format(
                        "/auth/admin/realms/%s/users/1/role-mappings/realm/available", realm)))
            .inScenario("test")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withBody(objectMapper.writeValueAsString(keycloakAvailableRoles))));

    WireMock.stubFor(
        WireMock.get(
                WireMock.urlMatching(
                    String.format("/auth/admin/realms/%s/users/1/role-mappings/realm", realm)))
            .inScenario("test")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withBody(objectMapper.writeValueAsString(keycloakInitialAssignedRoles))));

    WireMock.stubFor(
        WireMock.post(
                WireMock.urlMatching(
                    String.format("/auth/admin/realms/%s/users/1/role-mappings/realm", realm)))
            .inScenario("test")
            .whenScenarioStateIs(Scenario.STARTED)
            .withRequestBody(WireMock.equalTo(objectMapper.writeValueAsString(keycloakRolesToAdd)))
            .willReturn(WireMock.aResponse().withStatus(HttpStatus.NO_CONTENT.value()))
            .willSetStateTo("rolesUpdated"));

    WireMock.stubFor(
        WireMock.get(
                WireMock.urlMatching(
                    String.format("/auth/admin/realms/%s/users/1/role-mappings/realm", realm)))
            .inScenario("test")
            .whenScenarioStateIs("rolesUpdated")
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withBody(objectMapper.writeValueAsString(keycloakUpdatedAssignedRoles))));

    final RoleApiDto roleToAssign = new RoleApiDto().id("1").name("role1");
    final List<RoleApiDto> rolesToAssign = List.of(roleToAssign);

    final RoleListResponseApiDto response =
        requestSpecification()
            .given()
            .accept(MediaType.APPLICATION_JSON_VALUE)
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .header(new Header("X-Request-Id", "addf6005-3075-4c80-b7bc-2c70b7d42b57"))
            .body(rolesToAssign)
            .when()
            .put("/users/1/roles")
            .then()
            .statusCode(HttpStatus.OK.value())
            .extract()
            .body()
            .as(RoleListResponseApiDto.class);

    final RoleApiDto role = new RoleApiDto().id("1").name("role1");

    assertThat(response).isNotNull();
    assertThat(response.getTotalCount()).isEqualTo(response.getItems().size());
    assertThat(response.getItems()).containsExactly(role);
  }

  @Test
  void listOfAssignedRolesCanBeUpdatedWhenPreviousAssignedRolesAreNotEmpty() throws Exception {
    final RoleKeycloakDto keycloakRole1 = new RoleKeycloakDto().id("1").name("role1");
    final RoleKeycloakDto keycloakRole2 = new RoleKeycloakDto().id("2").name("role2");
    final RoleKeycloakDto keycloakRole3 = new RoleKeycloakDto().id("3").name("role3");

    final List<RoleKeycloakDto> keycloakAvailableRoles =
        List.of(keycloakRole1, keycloakRole2, keycloakRole3);
    final List<RoleKeycloakDto> keycloakInitialAssignedRoles =
        List.of(keycloakRole1, keycloakRole2);
    final List<RoleKeycloakDto> keycloakUpdatedAssignedRoles = List.of(keycloakRole1);

    final List<RoleKeycloakDto> keycloakRolesToRemove = List.of(keycloakRole1, keycloakRole2);
    final List<RoleKeycloakDto> keycloakRolesToAdd = List.of(keycloakRole1);

    WireMock.stubFor(
        WireMock.get(
                WireMock.urlMatching(
                    String.format(
                        "/auth/admin/realms/%s/users/1/role-mappings/realm/available", realm)))
            .inScenario("test")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withBody(objectMapper.writeValueAsString(keycloakAvailableRoles))));

    WireMock.stubFor(
        WireMock.get(
                WireMock.urlMatching(
                    String.format("/auth/admin/realms/%s/users/1/role-mappings/realm", realm)))
            .inScenario("test")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withBody(objectMapper.writeValueAsString(keycloakInitialAssignedRoles))));

    WireMock.stubFor(
        WireMock.delete(
                WireMock.urlMatching(
                    String.format("/auth/admin/realms/%s/users/1/role-mappings/realm", realm)))
            .inScenario("test")
            .whenScenarioStateIs(Scenario.STARTED)
            .withRequestBody(
                WireMock.equalTo(objectMapper.writeValueAsString(keycloakRolesToRemove)))
            .willReturn(WireMock.aResponse().withStatus(HttpStatus.NO_CONTENT.value())));

    WireMock.stubFor(
        WireMock.post(
                WireMock.urlMatching(
                    String.format("/auth/admin/realms/%s/users/1/role-mappings/realm", realm)))
            .inScenario("test")
            .whenScenarioStateIs(Scenario.STARTED)
            .withRequestBody(WireMock.equalTo(objectMapper.writeValueAsString(keycloakRolesToAdd)))
            .willReturn(WireMock.aResponse().withStatus(HttpStatus.NO_CONTENT.value()))
            .willSetStateTo("rolesUpdated"));

    WireMock.stubFor(
        WireMock.get(
                WireMock.urlMatching(
                    String.format("/auth/admin/realms/%s/users/1/role-mappings/realm", realm)))
            .inScenario("test")
            .whenScenarioStateIs("rolesUpdated")
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withBody(objectMapper.writeValueAsString(keycloakUpdatedAssignedRoles))));

    final RoleApiDto roleToAssign = new RoleApiDto().id("1").name("role1");
    final List<RoleApiDto> rolesToAssign = List.of(roleToAssign);

    final RoleListResponseApiDto response =
        requestSpecification()
            .given()
            .accept(MediaType.APPLICATION_JSON_VALUE)
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .header(new Header("X-Request-Id", "addf6005-3075-4c80-b7bc-2c70b7d42b57"))
            .body(rolesToAssign)
            .when()
            .put("/users/1/roles")
            .then()
            .statusCode(HttpStatus.OK.value())
            .extract()
            .body()
            .as(RoleListResponseApiDto.class);

    final RoleApiDto role = new RoleApiDto().id("1").name("role1");

    assertThat(response).isNotNull();
    assertThat(response.getTotalCount()).isEqualTo(response.getItems().size());
    assertThat(response.getItems()).containsExactly(role);
  }

  @Test
  void listOfAssignedRolesCanBeCleared() throws Exception {
    final RoleKeycloakDto keycloakRole1 = new RoleKeycloakDto().id("1").name("role1");
    final RoleKeycloakDto keycloakRole2 = new RoleKeycloakDto().id("2").name("role2");

    final List<RoleKeycloakDto> keycloakAvailableRoles = List.of(keycloakRole1, keycloakRole2);
    final List<RoleKeycloakDto> keycloakAssignedRoles = Collections.emptyList();
    final List<RoleKeycloakDto> keycloakRolesToRemove = List.of(keycloakRole1, keycloakRole2);

    WireMock.stubFor(
        WireMock.get(
                WireMock.urlMatching(
                    String.format(
                        "/auth/admin/realms/%s/users/1/role-mappings/realm/available", realm)))
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withBody(objectMapper.writeValueAsString(keycloakAvailableRoles))));

    WireMock.stubFor(
        WireMock.delete(
                WireMock.urlMatching(
                    String.format("/auth/admin/realms/%s/users/1/role-mappings/realm", realm)))
            .withRequestBody(
                WireMock.equalTo(objectMapper.writeValueAsString(keycloakRolesToRemove)))
            .willReturn(WireMock.aResponse().withStatus(HttpStatus.NO_CONTENT.value())));

    WireMock.stubFor(
        WireMock.get(
                WireMock.urlMatching(
                    String.format("/auth/admin/realms/%s/users/1/role-mappings/realm", realm)))
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withBody(objectMapper.writeValueAsString(keycloakAssignedRoles))));

    final List<RoleApiDto> rolesToAssign = Collections.emptyList();

    final RoleListResponseApiDto response =
        requestSpecification()
            .given()
            .accept(MediaType.APPLICATION_JSON_VALUE)
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .header(new Header("X-Request-Id", "addf6005-3075-4c80-b7bc-2c70b7d42b57"))
            .body(rolesToAssign)
            .when()
            .put("/users/1/roles")
            .then()
            .statusCode(HttpStatus.OK.value())
            .extract()
            .body()
            .as(RoleListResponseApiDto.class);

    assertThat(response).isNotNull();
    assertThat(response.getTotalCount()).isEqualTo(response.getItems().size());
    assertThat(response.getItems()).isEmpty();
  }

  @Test
  void listOfAssignedRolesCanNotBeUpdatedWhenPreviousAssignedRolesAreEmpty() throws Exception {
    final ErrorResponseKeycloakDto keycloakErrorResponse =
        new ErrorResponseKeycloakDto().errorMessage("Some error message");

    final RoleKeycloakDto keycloakRole1 = new RoleKeycloakDto().id("1").name("role1");
    final RoleKeycloakDto keycloakRole2 = new RoleKeycloakDto().id("2").name("role2");

    final List<RoleKeycloakDto> keycloakAvailableRoles = List.of(keycloakRole1, keycloakRole2);
    final List<RoleKeycloakDto> keycloakInitialAssignedRoles = List.of();

    final List<RoleKeycloakDto> keycloakRolesToAdd = List.of(keycloakRole1);

    WireMock.stubFor(
        WireMock.get(
                WireMock.urlMatching(
                    String.format(
                        "/auth/admin/realms/%s/users/1/role-mappings/realm/available", realm)))
            .inScenario("test")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withBody(objectMapper.writeValueAsString(keycloakAvailableRoles))));

    WireMock.stubFor(
        WireMock.get(
                WireMock.urlMatching(
                    String.format("/auth/admin/realms/%s/users/1/role-mappings/realm", realm)))
            .inScenario("test")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withBody(objectMapper.writeValueAsString(keycloakInitialAssignedRoles))));

    WireMock.stubFor(
        WireMock.post(
                WireMock.urlMatching(
                    String.format("/auth/admin/realms/%s/users/1/role-mappings/realm", realm)))
            .inScenario("test")
            .whenScenarioStateIs(Scenario.STARTED)
            .withRequestBody(WireMock.equalTo(objectMapper.writeValueAsString(keycloakRolesToAdd)))
            .willReturn(WireMock.aResponse().withStatus(HttpStatus.NO_CONTENT.value()))
            .willSetStateTo("rolesUpdated"));

    WireMock.stubFor(
        WireMock.get(
                WireMock.urlMatching(
                    String.format("/auth/admin/realms/%s/users/1/role-mappings/realm", realm)))
            .inScenario("test")
            .whenScenarioStateIs("rolesUpdated")
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withStatus(400)
                    .withBody(objectMapper.writeValueAsString(keycloakErrorResponse))));

    final RoleApiDto roleToAssign = new RoleApiDto().id("1").name("role1");
    final List<RoleApiDto> rolesToAssign = List.of(roleToAssign);

    ProblemApiDto response =
        requestSpecification()
            .given()
            .accept(MediaType.APPLICATION_JSON_VALUE)
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .header(new Header("X-Request-Id", "addf6005-3075-4c80-b7bc-2c70b7d42b57"))
            .body(rolesToAssign)
            .when()
            .put("/users/1/roles")
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
  void listOfAssignedRolesCanNotBeUpdatedWhenPreviousAssignedRolesAreNotEmpty() throws Exception {
    final ErrorResponseKeycloakDto keycloakErrorResponse =
        new ErrorResponseKeycloakDto().errorMessage("Some error message");

    final RoleKeycloakDto keycloakRole1 = new RoleKeycloakDto().id("1").name("role1");
    final RoleKeycloakDto keycloakRole2 = new RoleKeycloakDto().id("2").name("role2");
    final RoleKeycloakDto keycloakRole3 = new RoleKeycloakDto().id("3").name("role3");

    final List<RoleKeycloakDto> keycloakAvailableRoles =
        List.of(keycloakRole1, keycloakRole2, keycloakRole3);
    final List<RoleKeycloakDto> keycloakUpdatedAssignedRoles = List.of(keycloakRole1);

    final List<RoleKeycloakDto> keycloakRolesToRemove = List.of(keycloakRole1, keycloakRole2);
    final List<RoleKeycloakDto> keycloakRolesToAdd = List.of(keycloakRole1);

    WireMock.stubFor(
        WireMock.get(
                WireMock.urlMatching(
                    String.format(
                        "/auth/admin/realms/%s/users/1/role-mappings/realm/available", realm)))
            .inScenario("test")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withBody(objectMapper.writeValueAsString(keycloakAvailableRoles))));

    WireMock.stubFor(
        WireMock.get(
                WireMock.urlMatching(
                    String.format("/auth/admin/realms/%s/users/1/role-mappings/realm", realm)))
            .inScenario("test")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withStatus(400)
                    .withBody(objectMapper.writeValueAsString(keycloakErrorResponse))));

    WireMock.stubFor(
        WireMock.delete(
                WireMock.urlMatching(
                    String.format("/auth/admin/realms/%s/users/1/role-mappings/realm", realm)))
            .inScenario("test")
            .whenScenarioStateIs(Scenario.STARTED)
            .withRequestBody(
                WireMock.equalTo(objectMapper.writeValueAsString(keycloakRolesToRemove)))
            .willReturn(WireMock.aResponse().withStatus(HttpStatus.NO_CONTENT.value())));

    WireMock.stubFor(
        WireMock.post(
                WireMock.urlMatching(
                    String.format("/auth/admin/realms/%s/users/1/role-mappings/realm", realm)))
            .inScenario("test")
            .whenScenarioStateIs(Scenario.STARTED)
            .withRequestBody(WireMock.equalTo(objectMapper.writeValueAsString(keycloakRolesToAdd)))
            .willReturn(WireMock.aResponse().withStatus(HttpStatus.NO_CONTENT.value()))
            .willSetStateTo("rolesUpdated"));

    WireMock.stubFor(
        WireMock.get(
                WireMock.urlMatching(
                    String.format("/auth/admin/realms/%s/users/1/role-mappings/realm", realm)))
            .inScenario("test")
            .whenScenarioStateIs("rolesUpdated")
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withBody(objectMapper.writeValueAsString(keycloakUpdatedAssignedRoles))));

    final RoleApiDto roleToAssign = new RoleApiDto().id("1").name("role1");
    final List<RoleApiDto> rolesToAssign = List.of(roleToAssign);

    final ProblemApiDto response =
        requestSpecification()
            .given()
            .accept(MediaType.APPLICATION_JSON_VALUE)
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .header(new Header("X-Request-Id", "addf6005-3075-4c80-b7bc-2c70b7d42b57"))
            .body(rolesToAssign)
            .when()
            .put("/users/1/roles")
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

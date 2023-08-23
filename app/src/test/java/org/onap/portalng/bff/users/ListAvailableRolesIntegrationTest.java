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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.onap.portalng.bff.BaseIntegrationTest;
import org.onap.portalng.bff.openapi.client_keycloak.model.ErrorResponseKeycloakDto;
import org.onap.portalng.bff.openapi.client_keycloak.model.RoleKeycloakDto;
import org.onap.portalng.bff.openapi.server.model.ProblemApiDto;
import org.onap.portalng.bff.openapi.server.model.RoleApiDto;
import org.onap.portalng.bff.openapi.server.model.RoleListResponseApiDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

class ListAvailableRolesIntegrationTest extends BaseIntegrationTest {

  @Test
  void listOfAvailableRolesCanBeProvided() throws Exception {
    final RoleKeycloakDto keycloakRole1 = new RoleKeycloakDto().id("1").name("role1");
    final RoleKeycloakDto keycloakRole2 = new RoleKeycloakDto().id("2").name("role2");
    final List<RoleKeycloakDto> keycloakRoles = List.of(keycloakRole1, keycloakRole2);

    WireMock.stubFor(
        WireMock.get(
                WireMock.urlMatching(
                    String.format(
                        "/auth/admin/realms/%s/users/1/role-mappings/realm/available", realm)))
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withBody(objectMapper.writeValueAsString(keycloakRoles))));

    final RoleListResponseApiDto response =
        requestSpecification()
            .given()
            .accept(MediaType.APPLICATION_JSON_VALUE)
            .header(new Header("X-Request-Id", "addf6005-3075-4c80-b7bc-2c70b7d42b57"))
            .when()
            .get("/users/1/roles/available")
            .then()
            .statusCode(HttpStatus.OK.value())
            .extract()
            .body()
            .as(RoleListResponseApiDto.class);

    final RoleApiDto role1 = new RoleApiDto().id("1").name("role1");
    final RoleApiDto role2 = new RoleApiDto().id("2").name("role2");

    assertThat(response).isNotNull();
    assertThat(response.getTotalCount()).isEqualTo(response.getItems().size());
    assertThat(response.getItems()).containsExactly(role1, role2);
  }

  @Test
  void listOfAvailableRolesCanNotBeProvided() throws Exception {
    final ErrorResponseKeycloakDto keycloakErrorResponse =
        new ErrorResponseKeycloakDto().errorMessage("Some error message");

    WireMock.stubFor(
        WireMock.get(
                WireMock.urlMatching(
                    String.format(
                        "/auth/admin/realms/%s/users/1/role-mappings/realm/available", realm)))
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withStatus(400)
                    .withBody(objectMapper.writeValueAsString(keycloakErrorResponse))));

    ProblemApiDto response =
        requestSpecification()
            .given()
            .accept(MediaType.APPLICATION_JSON_VALUE)
            .header(new Header("X-Request-Id", "addf6005-3075-4c80-b7bc-2c70b7d42b57"))
            .when()
            .get("/users/1/roles/available")
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

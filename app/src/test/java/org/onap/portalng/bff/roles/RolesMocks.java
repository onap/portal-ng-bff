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

package org.onap.portalng.bff.roles;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.restassured.http.Header;
import java.util.List;
import org.onap.portalng.bff.BaseIntegrationTest;
import org.onap.portalng.bff.openapi.client_keycloak.model.RoleKeycloakDto;
import org.onap.portalng.bff.openapi.server.model.RoleListResponseApiDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

public class RolesMocks extends BaseIntegrationTest {

  protected RoleListResponseApiDto listRoles() {
    return requestSpecification()
        .given()
        .accept(MediaType.APPLICATION_JSON_VALUE)
        .header(new Header("X-Request-Id", "addf6005-3075-4c80-b7bc-2c70b7d42b57"))
        .when()
        .get("/roles")
        .then()
        .statusCode(HttpStatus.OK.value())
        .extract()
        .body()
        .as(RoleListResponseApiDto.class);
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

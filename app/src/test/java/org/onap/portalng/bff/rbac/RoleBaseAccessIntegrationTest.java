/*
 *
 * Copyright (c) 2024. Deutsche Telekom AG
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

package org.onap.portalng.bff.rbac;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.restassured.http.Header;
import org.junit.jupiter.api.Test;
import org.onap.portalng.bff.BaseIntegrationTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

public class RoleBaseAccessIntegrationTest extends BaseIntegrationTest {

  @Test
  void thatRoleIsNotSufficient() {

    WireMock.stubFor(
        WireMock.post(
                WireMock.urlMatching(
                    String.format("/realms/%s/protocol/openid-connect/token", realm)))
            .withRequestBody(
                WireMock.containing("grant_type=urn:ietf:params:oauth:grant-type:uma-ticket"))
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withStatus(HttpStatus.FORBIDDEN.value())));

    requestSpecification()
        .given()
        .accept(MediaType.APPLICATION_JSON_VALUE)
        .header(new Header("X-Request-Id", "addf6005-3075-4c80-b7bc-2c70b7d42b57"))
        .when()
        .get("/roles")
        .then()
        .statusCode(HttpStatus.FORBIDDEN.value());
  }

  @Test
  void thatResourceIsNotAvailable() {

    WireMock.stubFor(
        WireMock.post(
                WireMock.urlMatching(
                    String.format("/realms/%s/protocol/openid-connect/token", realm)))
            .withRequestBody(
                WireMock.containing("grant_type=urn:ietf:params:oauth:grant-type:uma-ticket"))
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withStatus(HttpStatus.BAD_REQUEST.value())));

    requestSpecification()
        .given()
        .accept(MediaType.APPLICATION_JSON_VALUE)
        .header(new Header("X-Request-Id", "addf6005-3075-4c80-b7bc-2c70b7d42b57"))
        .when()
        .get("/roles")
        .then()
        .statusCode(HttpStatus.FORBIDDEN.value());
  }

  @Test
  void thatRoleBaseCheckIsMalformed() {

    WireMock.stubFor(
        WireMock.post(
                WireMock.urlMatching(
                    String.format("/realms/%s/protocol/openid-connect/token", realm)))
            .withRequestBody(
                WireMock.containing("grant_type=urn:ietf:params:oauth:grant-type:uma-ticket"))
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withBody(objectMapper.createObjectNode().put("result", "false").toString())));

    requestSpecification()
        .given()
        .accept(MediaType.APPLICATION_JSON_VALUE)
        .header(new Header("X-Request-Id", "addf6005-3075-4c80-b7bc-2c70b7d42b57"))
        .when()
        .get("/roles")
        .then()
        .statusCode(HttpStatus.FORBIDDEN.value());
  }
}

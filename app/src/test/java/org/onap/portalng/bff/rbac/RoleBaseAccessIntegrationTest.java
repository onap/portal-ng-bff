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
import org.onap.portalng.bff.TokenGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

public class RoleBaseAccessIntegrationTest extends BaseIntegrationTest {

  @Autowired private TokenGenerator tokenGenerator;

  @Test
  void thatEmptyPermissionsResultInForbidden() {
    WireMock.stubFor(
        WireMock.post(
                WireMock.urlMatching("/realms/%s/protocol/openid-connect/token".formatted(realm)))
            .withRequestBody(
                WireMock.containing("grant_type=urn:ietf:params:oauth:grant-type:uma-ticket"))
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withBody("[]")));

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
  void thatMissingScopeResultsInForbidden() {
    WireMock.stubFor(
        WireMock.post(
                WireMock.urlMatching("/realms/%s/protocol/openid-connect/token".formatted(realm)))
            .withRequestBody(
                WireMock.containing("grant_type=urn:ietf:params:oauth:grant-type:uma-ticket"))
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withBody("[{\"rsname\":\"roles\",\"scopes\":[\"POST\"]}]")));

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
  void thatKeycloakErrorResultsInForbidden() {
    WireMock.stubFor(
        WireMock.post(
                WireMock.urlMatching("/realms/%s/protocol/openid-connect/token".formatted(realm)))
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
  void thatMalformedResponseResultsInForbidden() {
    WireMock.stubFor(
        WireMock.post(
                WireMock.urlMatching("/realms/%s/protocol/openid-connect/token".formatted(realm)))
            .withRequestBody(
                WireMock.containing("grant_type=urn:ietf:params:oauth:grant-type:uma-ticket"))
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withBody("{\"invalid\":\"response\"}")));

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
  void thatPermissionCheckWorksWithoutIdTokenHeader() {
    WireMock.stubFor(
        WireMock.get(WireMock.urlMatching("/admin/realms/%s/roles".formatted(realm)))
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withBody("[{\"id\":\"1\",\"name\":\"role1\"}]")));

    final String accessToken =
        tokenGenerator.generateToken(getTokenGeneratorConfig("portal_admin"));

    unauthenticatedRequestSpecification()
        .auth()
        .preemptive()
        .oauth2(accessToken)
        .given()
        .accept(MediaType.APPLICATION_JSON_VALUE)
        .header(new Header("X-Request-Id", "addf6005-3075-4c80-b7bc-2c70b7d42b57"))
        .when()
        .get("/roles")
        .then()
        .statusCode(HttpStatus.OK.value());
  }
}

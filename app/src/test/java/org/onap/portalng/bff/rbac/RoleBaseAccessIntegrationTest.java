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

/**
 * Verifies the RBAC behavior of the {@code KeycloakPermissionFilter}. Since the filter delegates
 * URI-to-resource matching back to Keycloak via {@code response_mode=decision}, these tests stub
 * the UMA-ticket token call with the decision Keycloak itself would return ({@code {"result":true}}
 * to grant, HTTP 403 to deny) rather than a pre-matched permission array.
 */
public class RoleBaseAccessIntegrationTest extends BaseIntegrationTest {

  @Autowired private TokenGenerator tokenGenerator;

  private String tokenUri() {
    return "/realms/%s/protocol/openid-connect/token".formatted(realm);
  }

  /** Stub the downstream Keycloak admin call backing {@code GET /roles}. */
  private void stubRolesBackend() {
    WireMock.stubFor(
        WireMock.get(WireMock.urlMatching("/admin/realms/%s/roles".formatted(realm)))
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withBody("[{\"id\":\"1\",\"name\":\"role1\"}]")));
  }

  @Test
  void thatGrantedDecisionAllowsAccess() {
    stubRolesBackend();
    WireMock.stubFor(
        WireMock.post(WireMock.urlMatching(tokenUri()))
            .withRequestBody(
                WireMock.containing("grant_type=urn:ietf:params:oauth:grant-type:uma-ticket"))
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withBody("{\"result\":true}")));

    requestSpecification()
        .given()
        .accept(MediaType.APPLICATION_JSON_VALUE)
        .header(new Header("X-Request-Id", "addf6005-3075-4c80-b7bc-2c70b7d42b57"))
        .when()
        .get("/roles")
        .then()
        .statusCode(HttpStatus.OK.value());

    // The filter must ask Keycloak to match the URI (decision mode), not match names locally.
    WireMock.verify(
        WireMock.postRequestedFor(WireMock.urlMatching(tokenUri()))
            .withRequestBody(WireMock.containing("response_mode=decision"))
            .withRequestBody(WireMock.containing("permission=/roles#GET")));
  }

  @Test
  void thatDeniedDecisionResultsInForbidden() {
    WireMock.stubFor(
        WireMock.post(WireMock.urlMatching(tokenUri()))
            .withRequestBody(
                WireMock.containing("grant_type=urn:ietf:params:oauth:grant-type:uma-ticket"))
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withStatus(HttpStatus.FORBIDDEN.value())
                    .withBody("{\"error\":\"access_denied\"}")));

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
        WireMock.post(WireMock.urlMatching(tokenUri()))
            .withRequestBody(
                WireMock.containing("grant_type=urn:ietf:params:oauth:grant-type:uma-ticket"))
            .willReturn(WireMock.aResponse().withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())));

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
        WireMock.post(WireMock.urlMatching(tokenUri()))
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
  void thatDecisionIsCachedPerToken() {
    stubRolesBackend();
    WireMock.stubFor(
        WireMock.post(WireMock.urlMatching(tokenUri()))
            .withRequestBody(
                WireMock.containing("grant_type=urn:ietf:params:oauth:grant-type:uma-ticket"))
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withBody("{\"result\":true}")));

    // Same token for both requests, so the cached decision (keyed by token id, method and uri)
    // must be reused on the second call instead of issuing another UMA-ticket request.
    final String accessToken =
        tokenGenerator.generateToken(getTokenGeneratorConfig("portal_admin"));

    for (int i = 0; i < 2; i++) {
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

    WireMock.verify(
        1,
        WireMock.postRequestedFor(WireMock.urlMatching(tokenUri()))
            .withRequestBody(
                WireMock.containing("grant_type=urn:ietf:params:oauth:grant-type:uma-ticket")));
  }

  @Test
  void thatPermissionCheckWorksWithoutIdTokenHeader() {
    stubRolesBackend();
    WireMock.stubFor(
        WireMock.post(WireMock.urlMatching(tokenUri()))
            .withRequestBody(
                WireMock.containing("grant_type=urn:ietf:params:oauth:grant-type:uma-ticket"))
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withBody("{\"result\":true}")));

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

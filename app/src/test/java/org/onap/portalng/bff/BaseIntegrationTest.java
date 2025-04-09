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

package org.onap.portalng.bff;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer;
import com.nimbusds.jose.jwk.JWKSet;
import io.restassured.RestAssured;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.specification.RequestSpecification;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.hc.core5.net.URIBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.onap.portalng.bff.config.BffConfig;
import org.onap.portalng.bff.config.IdTokenExchangeFilterFunction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.cloud.contract.wiremock.WireMockConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;

/** Base class for all tests that has the common config including port, realm, logging and auth. */
@AutoConfigureWireMock(port = 0)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class BaseIntegrationTest {

  @TestConfiguration
  public static class Config {
    @Bean
    WireMockConfigurationCustomizer optionsCustomizer() {
      return options -> options.extensions(new ResponseTemplateTransformer(true));
    }
  }

  @LocalServerPort protected int port;

  @Value("${bff.realm}")
  protected String realm;

  @Autowired protected ObjectMapper objectMapper;
  @Autowired private TokenGenerator tokenGenerator;

  @Autowired protected BffConfig bffConfig;

  @BeforeAll
  public static void setup() {
    RestAssured.filters(new RequestLoggingFilter(), new ResponseLoggingFilter());
  }

  /** Mocks the OIDC auth flow. */
  @BeforeEach
  public void mockAuth() {
    WireMock.reset();

    WireMock.stubFor(
        WireMock.get(
                WireMock.urlMatching("/realms/%s/protocol/openid-connect/certs".formatted(realm)))
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", JWKSet.MIME_TYPE)
                    .withBody(tokenGenerator.getJwkSet().toString())));

    final TokenGenerator.TokenGeneratorConfig config =
        TokenGenerator.TokenGeneratorConfig.builder().port(port).realm(realm).build();

    WireMock.stubFor(
        WireMock.post(
                WireMock.urlMatching("/realms/%s/protocol/openid-connect/token".formatted(realm)))
            .withBasicAuth("test", "test")
            .withRequestBody(WireMock.containing("grant_type=client_credentials"))
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withBody(
                        objectMapper
                            .createObjectNode()
                            .put("token_type", "bearer")
                            .put("access_token", tokenGenerator.generateToken(config))
                            .put("expires_in", config.getExpireIn().getSeconds())
                            .put("refresh_token", tokenGenerator.generateToken(config))
                            .put("refresh_expires_in", config.getExpireIn().getSeconds())
                            .put("not-before-policy", 0)
                            .put("session_state", UUID.randomUUID().toString())
                            .put("scope", "email profile")
                            .toString())));

    /*
     * MockAuth for new RBAC permission via keycloak
     */
    WireMock.stubFor(
        WireMock.post(
                WireMock.urlMatching(
                    "/realms/%s/protocol/openid-connect/token".formatted(realm)))
            .withRequestBody(
                WireMock.containing("grant_type=urn:ietf:params:oauth:grant-type:uma-ticket"))
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withBody(objectMapper.createObjectNode().put("result", "true").toString())));
  }

  /**
   * Object to store common attributes of requests that are going to be made. Adds an Identity
   * header for the <code>portal_admin</code> role to the request.
   */
  protected RequestSpecification requestSpecification() {
    final String idToken = tokenGenerator.generateToken(getTokenGeneratorConfig("portal_admin"));

    return unauthenticatedRequestSpecification()
        .auth()
        .preemptive()
        .oauth2(idToken)
        .header(IdTokenExchangeFilterFunction.X_AUTH_IDENTITY_HEADER, "Bearer " + idToken);
  }

  /**
   * Object to store common attributes of requests that are going to be made. Adds an Identity
   * header for the given role to the request.
   *
   * @param role the role used for RBAC
   * @return the templated request
   */
  protected RequestSpecification requestSpecification(String role) {
    final String idToken = tokenGenerator.generateToken(getTokenGeneratorConfig(role));

    return unauthenticatedRequestSpecification()
        .auth()
        .preemptive()
        .oauth2(idToken)
        .header(IdTokenExchangeFilterFunction.X_AUTH_IDENTITY_HEADER, "Bearer " + idToken);
  }

  /**
   * Object to store common attributes of requests that are going to be made. Adds an Identity
   * header for the given roles to the request.
   *
   * @param roles the roles used for RBAC
   * @return the templated request
   */
  protected RequestSpecification requestSpecification(List<String> roles) {
    final String idToken = tokenGenerator.generateToken(getTokenGeneratorConfig(roles));

    return unauthenticatedRequestSpecification()
        .auth()
        .preemptive()
        .oauth2(idToken)
        .header(IdTokenExchangeFilterFunction.X_AUTH_IDENTITY_HEADER, "Bearer " + idToken);
  }

  /** Get a RequestSpecification that does not have an Identity header. */
  protected RequestSpecification unauthenticatedRequestSpecification() {
    return RestAssured.given().port(port);
  }

  /**
   * Builds an OAuth2 configuration including the role, port and realm. This config can be used to
   * generate OAuth2 access tokens.
   *
   * @param role the role used for RBAC
   * @return the OAuth2 configuration
   */
  protected TokenGenerator.TokenGeneratorConfig getTokenGeneratorConfig(String role) {
    return TokenGenerator.TokenGeneratorConfig.builder()
        .port(port)
        .realm(realm)
        .roles(Collections.singletonList(role))
        .build();
  }

  /**
   * Builds an OAuth2 configuration including the roles, port and realm. This config can be used to
   * generate OAuth2 access tokens.
   *
   * @param roles the roles used for RBAC
   * @return the OAuth2 configuration
   */
  protected TokenGenerator.TokenGeneratorConfig getTokenGeneratorConfig(List<String> roles) {
    return TokenGenerator.TokenGeneratorConfig.builder()
        .port(port)
        .realm(realm)
        .roles(roles)
        .build();
  }

  public static OffsetDateTime offsetNow() {
    return OffsetDateTime.now(Clock.systemUTC());
  }

  public static String randomUUID() {
    return UUID.randomUUID().toString();
  }

  public static String adjustPath(
      String basePath, Optional<Integer> page, Optional<Integer> pageSize) {
    return adjustPath(basePath, page, pageSize, Optional.empty());
  }

  public static String adjustPath(
      String basePath,
      Optional<Integer> page,
      Optional<Integer> pageSize,
      Optional<String> filter) {
    URIBuilder builder;
    try {
      builder = new URIBuilder(basePath);
      if (page.isPresent()) {
        builder.addParameter("page", String.valueOf(page.get()));
      }
      if (pageSize.isPresent()) {
        builder.addParameter("pageSize", String.valueOf(pageSize.get()));
      }
      if (filter.isPresent()) {
        builder.addParameter("filter", filter.get());
      }
      return builder.build().toString();
    } catch (URISyntaxException e) {
      e.printStackTrace();
    }
    return basePath;
  }
}

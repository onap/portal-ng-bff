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

package org.onap.portalng.bff.preferences;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.EqualToPattern;
import io.restassured.http.Header;
import java.io.File;
import java.io.IOException;
import org.apache.hc.core5.http.HttpHeaders;
import org.onap.portalng.bff.BaseIntegrationTest;
import org.onap.portalng.bff.openapi.client_preferences.model.PreferencesPreferencesDto;
import org.onap.portalng.bff.openapi.client_preferences.model.ProblemPreferencesDto;
import org.onap.portalng.bff.openapi.server.model.CreatePreferencesRequestApiDto;
import org.onap.portalng.bff.openapi.server.model.PreferencesResponseApiDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

public class PreferencesMocks extends BaseIntegrationTest {
  protected static final String X_REQUEST_ID = "addf6005-3075-4c80-b7bc-2c70b7d42b57";

  protected static final String PREF_PROPERTIES_FILE =
      "src/test/resources/preferences/preferencesProperties.json";

  protected static final ObjectMapper objectMapper =
      new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

  protected static <T> T getFixture(final String fileName, Class<T> type) throws IOException {
    return objectMapper.readValue(new File(fileName), type);
  }

  protected PreferencesResponseApiDto getPreferences() {

    return requestSpecification()
        .given()
        .accept(MediaType.APPLICATION_JSON_VALUE)
        .header(new Header("X-Request-Id", X_REQUEST_ID))
        .when()
        .get("/preferences")
        .then()
        .statusCode(HttpStatus.OK.value())
        .extract()
        .body()
        .as(PreferencesResponseApiDto.class);
  }

  protected void mockGetPreferences(PreferencesPreferencesDto preferencesPreferencesDto)
      throws Exception {
    WireMock.stubFor(
        WireMock.get(WireMock.urlEqualTo("/v1/preferences"))
            .withHeader("X-Request-Id", new EqualToPattern(X_REQUEST_ID))
            .willReturn(
                WireMock.aResponse()
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withBody(objectMapper.writeValueAsString(preferencesPreferencesDto))));
  }

  protected void mockGetPreferencesError(ProblemPreferencesDto problem) throws Exception {
    WireMock.stubFor(
        WireMock.get(WireMock.urlEqualTo("/v1/preferences"))
            .withHeader("X-Request-Id", new EqualToPattern(X_REQUEST_ID))
            .willReturn(
                WireMock.aResponse()
                    .withHeader(
                        org.springframework.http.HttpHeaders.CONTENT_TYPE,
                        MediaType.APPLICATION_PROBLEM_JSON_VALUE)
                    .withBody(objectMapper.writeValueAsString(problem))
                    .withStatus(HttpStatus.UNAUTHORIZED.value())));
  }

  protected PreferencesResponseApiDto createPreferences(CreatePreferencesRequestApiDto request) {
    return requestSpecification()
        .given()
        .contentType(MediaType.APPLICATION_JSON_VALUE)
        .header(new Header("X-Request-Id", X_REQUEST_ID))
        .body(request)
        .when()
        .post("/preferences")
        .then()
        .statusCode(HttpStatus.OK.value())
        .extract()
        .body()
        .as(PreferencesResponseApiDto.class);
  }

  protected void mockCreatePreferences(PreferencesPreferencesDto preferencesPreferencesDto)
      throws Exception {
    WireMock.stubFor(
        WireMock.post(WireMock.urlEqualTo("/v1/preferences"))
            .withHeader("X-Request-Id", new EqualToPattern(X_REQUEST_ID))
            .withRequestBody(
                WireMock.equalToJson(
                    objectMapper.writeValueAsString(preferencesPreferencesDto), true, false))
            .willReturn(
                WireMock.aResponse()
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withBody(objectMapper.writeValueAsString(preferencesPreferencesDto))));
  }

  protected void mockCreatePreferencesError(
      PreferencesPreferencesDto preferencesPreferencesDto,
      ProblemPreferencesDto problemPreferencesDto)
      throws Exception {
    WireMock.stubFor(
        WireMock.post(WireMock.urlEqualTo("/v1/preferences"))
            .withHeader("X-Request-Id", new EqualToPattern(X_REQUEST_ID))
            .withRequestBody(
                WireMock.equalToJson(
                    objectMapper.writeValueAsString(preferencesPreferencesDto), true, false))
            .willReturn(
                WireMock.aResponse()
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withStatus(HttpStatus.BAD_REQUEST.value())
                    .withBody(objectMapper.writeValueAsString(problemPreferencesDto))));
  }

  protected PreferencesResponseApiDto updatePreferences(CreatePreferencesRequestApiDto request) {
    return requestSpecification()
        .given()
        .contentType(MediaType.APPLICATION_JSON_VALUE)
        .header(new Header("X-Request-Id", X_REQUEST_ID))
        .body(request)
        .when()
        .put("/preferences")
        .then()
        .statusCode(HttpStatus.OK.value())
        .extract()
        .body()
        .as(PreferencesResponseApiDto.class);
  }

  protected void mockUpdatePreferences(PreferencesPreferencesDto preferencesPreferencesDto)
      throws Exception {
    WireMock.stubFor(
        WireMock.put(WireMock.urlEqualTo("/v1/preferences"))
            .withHeader("X-Request-Id", new EqualToPattern(X_REQUEST_ID))
            .withRequestBody(
                WireMock.equalToJson(
                    objectMapper.writeValueAsString(preferencesPreferencesDto), true, false))
            .willReturn(
                WireMock.aResponse()
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withBody(objectMapper.writeValueAsString(preferencesPreferencesDto))));
  }

  protected void mockUpdatePreferencesError(
      PreferencesPreferencesDto preferencesPreferencesDto,
      ProblemPreferencesDto problemPreferencesDto)
      throws Exception {
    WireMock.stubFor(
        WireMock.put(WireMock.urlEqualTo("/v1/preferences"))
            .withRequestBody(
                WireMock.equalToJson(
                    objectMapper.writeValueAsString(preferencesPreferencesDto), true, false))
            .willReturn(
                WireMock.aResponse()
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withStatus(HttpStatus.BAD_REQUEST.value())
                    .withBody(objectMapper.writeValueAsString(problemPreferencesDto))));
  }
}

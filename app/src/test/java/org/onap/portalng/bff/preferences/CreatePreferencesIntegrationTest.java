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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.restassured.http.Header;
import org.junit.jupiter.api.Test;
import org.onap.portalng.bff.openapi.client_preferences.model.PreferencesPreferencesDto;
import org.onap.portalng.bff.openapi.client_preferences.model.ProblemPreferencesDto;
import org.onap.portalng.bff.openapi.server.model.CreatePreferencesRequestApiDto;
import org.onap.portalng.bff.openapi.server.model.PreferencesResponseApiDto;
import org.onap.portalng.bff.openapi.server.model.ProblemApiDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

class CreatePreferencesIntegrationTest extends PreferencesMocks {
  private static final String PREFERENCE_PROPERTIES_VALUE =
      """
        {
        "properties": {
        "appStarter": "value1",
        "dashboard": {"key1:" : "value2"}
        }

        }\
        """;

  @Test
  void thatPreferencesCanBeCreated() throws Exception {
    PreferencesPreferencesDto preferencesPreferencesDto = new PreferencesPreferencesDto();
    preferencesPreferencesDto.setProperties(PREFERENCE_PROPERTIES_VALUE);
    mockCreatePreferences(preferencesPreferencesDto);

    final CreatePreferencesRequestApiDto request =
        new CreatePreferencesRequestApiDto().properties(PREFERENCE_PROPERTIES_VALUE);
    final PreferencesResponseApiDto response = createPreferences(request);
    assertThat(response).isNotNull();
    assertThat(response.getProperties()).isEqualTo(preferencesPreferencesDto.getProperties());
  }

  @Test
  void thatPreferencesCanNotBeCreated() throws Exception {
    final var problemPreferencesDto = new ProblemPreferencesDto();
    problemPreferencesDto.setStatus(HttpStatus.BAD_REQUEST.value());
    problemPreferencesDto.setTitle(HttpStatus.BAD_REQUEST.toString());
    problemPreferencesDto.setDetail("Some details");

    final PreferencesPreferencesDto preferencesPreferencesDto =
        new PreferencesPreferencesDto().properties(PREFERENCE_PROPERTIES_VALUE);
    mockCreatePreferencesError(preferencesPreferencesDto, problemPreferencesDto);

    CreatePreferencesRequestApiDto responseBody =
        new CreatePreferencesRequestApiDto().properties(PREFERENCE_PROPERTIES_VALUE);
    final ProblemApiDto response =
        requestSpecification()
            .given()
            .accept(MediaType.APPLICATION_PROBLEM_JSON_VALUE)
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .header(new Header("X-Request-Id", X_REQUEST_ID))
            .body(responseBody)
            .when()
            .post("/preferences")
            .then()
            .statusCode(HttpStatus.BAD_GATEWAY.value())
            .extract()
            .body()
            .as(ProblemApiDto.class);

    assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY.value());
    assertThat(response.getDetail()).isEqualTo(problemPreferencesDto.getDetail());
    assertThat(response.getDownstreamSystem())
        .isEqualTo(ProblemApiDto.DownstreamSystemEnum.PREFERENCES);
    assertThat(response.getDownstreamStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
  }

  /**
   * Characterization test pinning the exact `application/problem+json` wire format that portal-ui
   * depends on. This guards the migration from the Zalando Problem library to Spring's native
   * {@link org.springframework.http.ProblemDetail}: the standard RFC-7807/9457 fields and the
   * BFF-specific extension fields ({@code downstreamSystem}, {@code downstreamStatus}) must all
   * stay serialized flat at the top level of the JSON body — not nested under a {@code
   * "properties"} wrapper, which is the failure mode of a naive ProblemDetail migration.
   */
  @Test
  void thatErrorResponseKeepsProblemJsonWireFormat() throws Exception {
    final var problemPreferencesDto = new ProblemPreferencesDto();
    problemPreferencesDto.setStatus(HttpStatus.BAD_REQUEST.value());
    problemPreferencesDto.setTitle(HttpStatus.BAD_REQUEST.toString());
    problemPreferencesDto.setDetail("Some details");

    final PreferencesPreferencesDto preferencesPreferencesDto =
        new PreferencesPreferencesDto().properties(PREFERENCE_PROPERTIES_VALUE);
    mockCreatePreferencesError(preferencesPreferencesDto, problemPreferencesDto);

    final CreatePreferencesRequestApiDto responseBody =
        new CreatePreferencesRequestApiDto().properties(PREFERENCE_PROPERTIES_VALUE);
    final String rawBody =
        requestSpecification()
            .given()
            .accept(MediaType.APPLICATION_PROBLEM_JSON_VALUE)
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .header(new Header("X-Request-Id", X_REQUEST_ID))
            .body(responseBody)
            .when()
            .post("/preferences")
            .then()
            .statusCode(HttpStatus.BAD_GATEWAY.value())
            .contentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE)
            .extract()
            .body()
            .asString();

    final JsonNode json = objectMapper.readTree(rawBody);

    // Standard problem fields, all top-level.
    assertThat(json.path("title").asText()).isEqualTo(HttpStatus.BAD_REQUEST.toString());
    assertThat(json.path("status").isInt()).isTrue();
    assertThat(json.path("status").asInt()).isEqualTo(HttpStatus.BAD_GATEWAY.value());
    assertThat(json.path("detail").asText()).isEqualTo("Some details");

    // BFF extension fields must be top-level too (portal-ui reads error.downstreamStatus).
    assertThat(json.path("downstreamSystem").asText())
        .isEqualTo(ProblemApiDto.DownstreamSystemEnum.PREFERENCES.getValue());
    assertThat(json.path("downstreamStatus").asInt()).isEqualTo(HttpStatus.BAD_REQUEST.value());

    // The ProblemDetail migration trap: extension fields must NOT be nested under "properties".
    assertThat(json.has("properties")).isFalse();
  }

  @Test
  void thatPreferencesExceptionsAreHandledForResponseWithoutBody() throws Exception {
    final PreferencesPreferencesDto preferencesPreferencesDto =
        new PreferencesPreferencesDto().properties(PREFERENCE_PROPERTIES_VALUE);
    mockCreatePreferencesErrorWithoutBody(preferencesPreferencesDto);

    CreatePreferencesRequestApiDto responseBody =
        new CreatePreferencesRequestApiDto().properties(PREFERENCE_PROPERTIES_VALUE);
    final ProblemApiDto response =
        requestSpecification()
            .given()
            .accept(MediaType.APPLICATION_PROBLEM_JSON_VALUE)
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .header(new Header("X-Request-Id", X_REQUEST_ID))
            .body(responseBody)
            .when()
            .post("/preferences")
            .then()
            .statusCode(HttpStatus.BAD_GATEWAY.value())
            .extract()
            .body()
            .as(ProblemApiDto.class);

    assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY.value());
    assertThat(response.getDetail()).isNull();
    assertThat(response.getDownstreamSystem())
        .isEqualTo(ProblemApiDto.DownstreamSystemEnum.PREFERENCES);
    assertThat(response.getDownstreamStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
  }
}

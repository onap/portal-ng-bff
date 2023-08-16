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

import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.http.Header;
import org.junit.jupiter.api.Test;
import org.onap.portalng.bff.openapi.client_portal_prefs.model.PreferencesPortalPrefsDto;
import org.onap.portalng.bff.openapi.client_portal_prefs.model.ProblemPortalPrefsDto;
import org.onap.portalng.bff.openapi.server.model.PreferencesResponseApiDto;
import org.onap.portalng.bff.openapi.server.model.ProblemApiDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

class GetPreferencesIntegrationTest extends PreferencesMocks {

  @Test
  void thatPreferencesCanBeRetrieved() throws Exception {
    PreferencesPortalPrefsDto preferencesPortalPrefsDto = new PreferencesPortalPrefsDto();
    preferencesPortalPrefsDto.setProperties(getFixture(PREF_PROPERTIES_FILE, Object.class));
    mockGetPreferences(preferencesPortalPrefsDto);

    final PreferencesResponseApiDto response = getPreferences();
    assertThat(response).isNotNull();
  }

  @Test
  void thatPreferencesCanNotBeRetrieved() throws Exception {
    final ProblemPortalPrefsDto problemResponse =
        new ProblemPortalPrefsDto()
            .title("Unauthorized")
            .status(HttpStatus.UNAUTHORIZED.value())
            .detail("Unauthorized error detail")
            .instance("instance")
            .type("type");

    mockGetPreferencesError(problemResponse);

    final ProblemApiDto errorResponse =
        requestSpecification()
            .given()
            .accept(MediaType.APPLICATION_JSON_VALUE)
            .header(new Header("X-Request-Id", "addf6005-3075-4c80-b7bc-2c70b7d42b57"))
            .when()
            .get("/preferences")
            .then()
            .statusCode(HttpStatus.BAD_GATEWAY.value())
            .contentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE)
            .extract()
            .body()
            .as(ProblemApiDto.class);

    assertThat(errorResponse.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY.value());
    assertThat(errorResponse.getTitle()).isEqualTo(HttpStatus.UNAUTHORIZED.toString());
    assertThat(errorResponse.getDetail()).isEqualTo("Unauthorized error detail");
  }
}

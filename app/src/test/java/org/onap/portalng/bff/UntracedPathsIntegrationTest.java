/*
 *
 * Copyright (c) 2026. Deutsche Telekom AG
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

import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.onap.portalng.bff.openapi.client_preferences.model.PreferencesPreferencesDto;
import org.onap.portalng.bff.preferences.PreferencesMocks;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "management.endpoints.web.exposure.include=health,prometheus")
class UntracedPathsIntegrationTest extends PreferencesMocks {

  @Test
  void thatPolledActuatorPathsAreNotObservedWhileApiRequestsAre() throws Exception {
    mockGetPreferences(new PreferencesPreferencesDto());
    getPreferences();

    for (String probe :
        new String[] {
          "/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"
        }) {
      unauthenticatedRequestSpecification()
          .when()
          .get(probe)
          .then()
          .statusCode(HttpStatus.OK.value());
    }
    scrape();

    final String metrics = scrape();

    assertThat(metrics)
        .doesNotContain("uri=\"/actuator/health")
        .doesNotContain("uri=\"/actuator/prometheus\"")
        .containsPattern("http_server_requests_seconds_count\\{[^}]*uri=\"/preferences\"");
  }

  private String scrape() {
    return unauthenticatedRequestSpecification()
        .accept(ContentType.TEXT)
        .when()
        .get("/actuator/prometheus")
        .then()
        .statusCode(HttpStatus.OK.value())
        .extract()
        .asString();
  }
}

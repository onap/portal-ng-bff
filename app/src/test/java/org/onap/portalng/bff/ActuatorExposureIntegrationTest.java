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

import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

class ActuatorExposureIntegrationTest extends BaseIntegrationTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/actuator/health",
        "/actuator/health/liveness",
        "/actuator/health/readiness",
        "/actuator/info"
      })
  void thatProbeAndInfoEndpointsAreReachableWithoutToken(String path) {
    unauthenticatedRequestSpecification().when().get(path).then().statusCode(HttpStatus.OK.value());
  }

  @Test
  void thatPrometheusScrapeIsReachableWithoutToken() {
    unauthenticatedRequestSpecification()
        .accept(ContentType.TEXT)
        .when()
        .get("/actuator/prometheus")
        .then()
        .statusCode(HttpStatus.OK.value());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/actuator",
        "/actuator/env",
        "/actuator/configprops",
        "/actuator/heapdump",
        "/actuator/loggers",
        "/actuator/beans",
        "/actuator/threaddump"
      })
  void thatOtherActuatorEndpointsRequireToken(String path) {
    unauthenticatedRequestSpecification()
        .when()
        .get(path)
        .then()
        .statusCode(HttpStatus.UNAUTHORIZED.value());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/actuator/env",
        "/actuator/configprops",
        "/actuator/heapdump",
        "/actuator/loggers",
        "/actuator/beans",
        "/actuator/threaddump"
      })
  void thatSensitiveActuatorEndpointsAreNotExposedEvenWithToken(String path) {
    requestSpecification().when().get(path).then().statusCode(HttpStatus.NOT_FOUND.value());
  }
}

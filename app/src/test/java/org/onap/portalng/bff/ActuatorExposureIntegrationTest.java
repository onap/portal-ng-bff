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
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The test {@code application.yml} replaces the main one on the test classpath, so the actuator
 * exposure and the unauthenticated and RBAC-excluded path lists are read here from the main {@code
 * application.yml}: that way this test fails when the shipped defaults open up again.
 */
class ActuatorExposureIntegrationTest extends BaseIntegrationTest {

  private static final List<String> PRODUCTION_KEYS =
      List.of(
          "management.endpoints.web.exposure.include",
          "bff.endpoints.unauthenticated",
          "bff.rbac.endpoints-excluded");

  @DynamicPropertySource
  static void productionActuatorProperties(DynamicPropertyRegistry registry) throws IOException {
    final List<PropertySource<?>> sources =
        new YamlPropertySourceLoader()
            .load(
                "main-application-yml",
                new FileSystemResource("src/main/resources/application.yml"));
    for (String key : PRODUCTION_KEYS) {
      final Object value =
          sources.stream()
              .map(source -> source.getProperty(key))
              .filter(Objects::nonNull)
              .findFirst()
              .orElseThrow(() -> new IllegalStateException(key + " is not set in application.yml"));
      registry.add(key, value::toString);
    }
  }

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

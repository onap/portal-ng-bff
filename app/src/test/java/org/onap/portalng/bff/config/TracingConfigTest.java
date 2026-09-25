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

package org.onap.portalng.bff.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;

class TracingConfigTest {

  private final ObservationPredicate enabledPredicate =
      new TracingConfig().untracedPathsPredicate(true);

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/actuator/health",
        "/actuator/health/liveness",
        "/actuator/health/readiness",
        "/actuator/prometheus"
      })
  void thatPolledActuatorPathsAreNotObserved(String path) {
    assertThat(enabledPredicate.test("http.server.requests", serverContext(path, ""))).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"/preferences", "/actuator", "/actuator/info", "/actuator/metrics"})
  void thatOtherPathsAreObserved(String path) {
    assertThat(enabledPredicate.test("http.server.requests", serverContext(path, ""))).isTrue();
  }

  @Test
  void thatPathsAreMatchedWithinTheBasePath() {
    assertThat(
            enabledPredicate.test(
                "http.server.requests", serverContext("/bff/actuator/prometheus", "/bff")))
        .isFalse();
    assertThat(
            enabledPredicate.test(
                "http.server.requests", serverContext("/bff/preferences", "/bff")))
        .isTrue();
  }

  @Test
  void thatNothingIsFilteredWhenDisabled() {
    final ObservationPredicate disabledPredicate =
        new TracingConfig().untracedPathsPredicate(false);

    assertThat(
            disabledPredicate.test(
                "http.server.requests", serverContext("/actuator/health/liveness", "")))
        .isTrue();
    assertThat(
            disabledPredicate.test(
                "http.server.requests", serverContext("/actuator/prometheus", "")))
        .isTrue();
  }

  @Test
  void thatNonServerRequestObservationsAreObserved() {
    assertThat(enabledPredicate.test("http.client.requests", new Observation.Context())).isTrue();
  }

  private static ServerRequestObservationContext serverContext(String path, String contextPath) {
    return new ServerRequestObservationContext(
        MockServerHttpRequest.get(path).contextPath(contextPath).build(),
        new MockServerHttpResponse(),
        Map.of());
  }
}

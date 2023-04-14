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

package org.onap.portal.bff;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;

class HealthCheckIntegrationTest extends BaseIntegrationTest {

  @Autowired private ApplicationAvailability applicationAvailability;

  @Test
  void livenessProbeIsAvailable() {

    assertThat(applicationAvailability.getLivenessState()).isEqualTo(LivenessState.CORRECT);
  }

  @Test
  void readinessProbeIsAvailable() {

    assertThat(applicationAvailability.getReadinessState())
        .isEqualTo(ReadinessState.ACCEPTING_TRAFFIC);
  }
}

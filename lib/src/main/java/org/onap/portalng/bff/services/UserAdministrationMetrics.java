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

package org.onap.portalng.bff.services;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Counts the user administration operations the BFF performs against Keycloak, tagged by operation
 * and outcome.
 */
@Component
@RequiredArgsConstructor
public class UserAdministrationMetrics {

  public static final String METRIC_NAME = "portalng.bff.user.administration";

  private final MeterRegistry meterRegistry;

  public enum Operation {
    CREATE_USER,
    UPDATE_USER,
    DELETE_USER,
    UPDATE_PASSWORD,
    UPDATE_ROLES;

    String tagValue() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  public <T> Function<Mono<T>, Mono<T>> record(Operation operation) {
    return mono ->
        mono.doOnSuccess(ignored -> increment(operation, "success"))
            .doOnError(ignored -> increment(operation, "failure"));
  }

  private void increment(Operation operation, String outcome) {
    meterRegistry
        .counter(METRIC_NAME, "operation", operation.tagValue(), "outcome", outcome)
        .increment();
  }
}

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

package org.onap.portalng.bff.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.zalando.problem.Problem;
import org.zalando.problem.Status;
import reactor.core.publisher.Mono;

/**
 * Class that contains configuration of the downstream apis. This could be username and password or
 * urls.
 */
@Valid
@ConfigurationProperties("bff")
@Data
public class BffConfig {

  @NotBlank private final String realm;
  @NotBlank private final String preferencesUrl;
  @NotBlank private final String historyUrl;
  @NotBlank private final String keycloakUrl;

  @NotNull private final Map<String, List<String>> accessControl;

  public Mono<List<String>> getRoles(String method) {
    return Mono.just(accessControl)
        .map(control -> control.get(method))
        .onErrorResume(
            e ->
                Mono.error(
                    Problem.valueOf(
                        Status.FORBIDDEN, "The user does not have the necessary access rights")));
  }
}

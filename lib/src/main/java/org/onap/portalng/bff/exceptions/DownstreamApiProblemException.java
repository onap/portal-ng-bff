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

package org.onap.portalng.bff.exceptions;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.net.URI;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.onap.portalng.bff.openapi.server.model.ConstraintViolationApiDto;
import org.zalando.problem.AbstractThrowableProblem;
import org.zalando.problem.Problem;
import org.zalando.problem.Status;
import org.zalando.problem.StatusType;

/** The default bff exception */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString
@JsonIgnoreProperties
public class DownstreamApiProblemException extends AbstractThrowableProblem {

  @Builder.Default private final URI type = Problem.DEFAULT_TYPE;
  @Builder.Default private final String title = "Bad gateway error";

  @JsonIgnore @Builder.Default private final transient StatusType status = Status.BAD_GATEWAY;

  @Builder.Default
  private final String detail = "Please find more detail under correlationId: 'TODO'";

  @Builder.Default private final String downstreamSystem = null;
  @Builder.Default private final URI instance = null;
  @Builder.Default private final Integer downstreamStatus = null;
  @Builder.Default private final String downstreamMessageId = null;

  @JsonIgnore @Builder.Default
  private final transient List<ConstraintViolationApiDto> violations = null;
}

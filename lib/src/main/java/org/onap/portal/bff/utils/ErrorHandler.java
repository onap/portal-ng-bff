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

package org.onap.portal.bff.utils;

import java.util.List;
import java.util.Objects;
import org.onap.portal.bff.exceptions.DownstreamApiProblemException;
import org.onap.portal.bff.openapi.server.model.ProblemApiDto;
import org.springframework.http.HttpStatus;

public class ErrorHandler {
  /**
   * Not meant to be instantiated. To prevent Java from adding an implicit public constructor to
   * every class which does not define at least one explicitly.
   */
  private ErrorHandler() {}

  public static String mapVariablesToDetails(List<String> variables, String details) {
    int i = 0;
    for (String variable : variables) {
      i++;
      details = details.replace("%" + i, variable);
    }
    return details;
  }

  public static DownstreamApiProblemException getDownstreamApiProblemException(
      HttpStatus httpStatus,
      List<String> variables,
      String text,
      String messageId,
      ProblemApiDto.DownstreamSystemEnum downStreamSystem) {
    String errorDetail =
        variables != null && text != null
            ? ErrorHandler.mapVariablesToDetails(variables, text)
            : null;

    return DownstreamApiProblemException.builder()
        .title(httpStatus.toString())
        .detail(errorDetail)
        .downstreamMessageId(Objects.requireNonNullElse(messageId, "not set by downstream system"))
        .downstreamSystem(downStreamSystem.toString())
        .downstreamStatus(httpStatus.value())
        .build();
  }
}

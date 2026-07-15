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

import java.net.URI;
import java.util.List;
import org.onap.portalng.bff.openapi.server.model.ConstraintViolationApiDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * The default bff exception.
 *
 * <p>Rendered as an {@code application/problem+json} response (RFC 7807 / 9457) via Spring's native
 * {@link ProblemDetail}. The BFF-specific fields ({@code downstreamSystem}, {@code
 * downstreamStatus}, {@code downstreamMessageId}, {@code violations}) are carried as ProblemDetail
 * extension properties, which Spring's {@code ProblemDetailJacksonMixin} serializes flat at the top
 * level of the body — matching the wire format that portal-ui depends on.
 */
public class DownstreamApiProblemException extends ErrorResponseException {

  private static final HttpStatus DEFAULT_STATUS = HttpStatus.BAD_GATEWAY;
  private static final String DEFAULT_TITLE = "Bad gateway error";
  private static final String DEFAULT_DETAIL =
      "Please find more detail under correlationId: 'TODO'";

  private DownstreamApiProblemException(HttpStatusCode status, ProblemDetail body) {
    super(status, body, null);
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * Fluent builder preserving the original API. Defaults mirror the previous Zalando-based
   * implementation: status defaults to {@code BAD_GATEWAY}. A field explicitly set to {@code null}
   * (e.g. {@code detail(null)}) stays absent from the rendered body — distinct from leaving it
   * unset.
   */
  public static final class Builder {
    private HttpStatusCode status = DEFAULT_STATUS;
    private String title = DEFAULT_TITLE;
    private String detail = DEFAULT_DETAIL;
    private String downstreamSystem;
    private Integer downstreamStatus;
    private String downstreamMessageId;
    private List<ConstraintViolationApiDto> violations;
    private URI type;
    private URI instance;

    public Builder status(HttpStatusCode status) {
      this.status = status;
      return this;
    }

    public Builder title(String title) {
      this.title = title;
      return this;
    }

    public Builder detail(String detail) {
      this.detail = detail;
      return this;
    }

    public Builder downstreamSystem(String downstreamSystem) {
      this.downstreamSystem = downstreamSystem;
      return this;
    }

    public Builder downstreamStatus(Integer downstreamStatus) {
      this.downstreamStatus = downstreamStatus;
      return this;
    }

    public Builder downstreamMessageId(String downstreamMessageId) {
      this.downstreamMessageId = downstreamMessageId;
      return this;
    }

    public Builder violations(List<ConstraintViolationApiDto> violations) {
      this.violations = violations;
      return this;
    }

    public Builder type(URI type) {
      this.type = type;
      return this;
    }

    public Builder instance(URI instance) {
      this.instance = instance;
      return this;
    }

    public DownstreamApiProblemException build() {
      final HttpStatusCode effectiveStatus = status != null ? status : DEFAULT_STATUS;
      final ProblemDetail body = ProblemDetail.forStatus(effectiveStatus);
      if (title != null) {
        body.setTitle(title);
      }
      if (detail != null) {
        body.setDetail(detail);
      }
      if (type != null) {
        body.setType(type);
      }
      if (instance != null) {
        body.setInstance(instance);
      }
      if (downstreamSystem != null) {
        body.setProperty("downstreamSystem", downstreamSystem);
      }
      if (downstreamStatus != null) {
        body.setProperty("downstreamStatus", downstreamStatus);
      }
      if (downstreamMessageId != null) {
        body.setProperty("downstreamMessageId", downstreamMessageId);
      }
      if (violations != null) {
        body.setProperty("violations", violations);
      }
      return new DownstreamApiProblemException(effectiveStatus, body);
    }
  }
}

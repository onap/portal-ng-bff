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

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.onap.portalng.bff.config.ProblemWebExceptionHandler;
import org.onap.portalng.bff.controller.BffControllerAdvice;
import org.onap.portalng.bff.openapi.server.model.ConstraintViolationApiDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ServerWebInputException;
import reactor.test.StepVerifier;

/**
 * Regression tests locking in the error-rendering contract that the Zalando Problem migration must
 * preserve for consumers (notably T-NAP portal-bff): consistent {@code application/problem+json}
 * bodies with the right status and, for validation failures, a top-level {@code violations} array.
 * These paths are not exercised by the other integration tests, which is how the original migration
 * regressed them.
 */
class BffErrorRenderingTest {

  private final BffControllerAdvice advice = new BffControllerAdvice();
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void thatDownstreamApiProblemExceptionKeepsStatusAndExtensionFields() {
    final DownstreamApiProblemException ex =
        DownstreamApiProblemException.builder()
            .status(HttpStatus.NOT_FOUND)
            .title("Title")
            .detail("Detail")
            .downstreamSystem("AAI")
            .downstreamStatus(404)
            .build();

    final ResponseEntity<Object> response = advice.handleDownstreamApiProblemException(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    final ProblemDetail body = (ProblemDetail) response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getTitle()).isEqualTo("Title");
    assertThat(body.getProperties()).containsEntry("downstreamSystem", "AAI");
    assertThat(body.getProperties()).containsEntry("downstreamStatus", 404);
  }

  @Test
  void thatConstraintViolationRendersAs400WithViolations() {
    final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    final Set<ConstraintViolation<Bean>> raw = validator.validate(new Bean(null));
    final ConstraintViolationException ex = new ConstraintViolationException(raw);

    final ResponseEntity<Object> response = advice.handleConstraintViolation(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    final ProblemDetail body = (ProblemDetail) response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getTitle()).isEqualTo("Constraint Violation");
    @SuppressWarnings("unchecked")
    final List<ConstraintViolationApiDto> violations =
        (List<ConstraintViolationApiDto>) body.getProperties().get("violations");
    assertThat(violations).isNotEmpty();
    assertThat(violations.get(0).getField()).contains("name");
  }

  @Test
  void thatUnexpectedThrowableRendersAs500WithMessageAsDetail() {
    final ResponseEntity<Object> response =
        advice.handleThrowable(
            new AccessDeniedException("User X doesn't have access to draft main."));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    final ProblemDetail body = (ProblemDetail) response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getTitle()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.toString());
    assertThat(body.getDetail()).isEqualTo("User X doesn't have access to draft main.");
  }

  @Test
  void thatServerWebInputExceptionRendersAsProblemJsonAndDoesNotEscapeToSecurity() {
    final ProblemWebExceptionHandler handler = new ProblemWebExceptionHandler(objectMapper);
    final MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/whatever"));

    StepVerifier.create(
            handler.handle(
                exchange, new ServerWebInputException("Required parameter 'x' is not present.")))
        .verifyComplete();

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(exchange.getResponse().getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
  }

  private record Bean(@NotBlank String name) {}
}

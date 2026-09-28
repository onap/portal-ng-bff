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

package org.onap.portalng.bff.exceptions;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import io.restassured.path.json.JsonPath;
import io.restassured.response.ValidatableResponse;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.onap.portalng.bff.BaseIntegrationTest;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

class RequestInputErrorIntegrationTest extends BaseIntegrationTest {

  private final CapturingAppender logs = new CapturingAppender();

  @BeforeEach
  void captureLogs() {
    logs.start();
    rootLogger().addAppender(logs);
  }

  @AfterEach
  void stopCapturingLogs() {
    rootLogger().detachAppender(logs);
  }

  @Test
  void thatMalformedJsonIsRejectedWithItsByteOffset() {
    final JsonPath problem = postUser("{\"username\": ").statusCode(400).extract().jsonPath();

    assertThat(problem.getInt("status")).isEqualTo(400);
    assertThat(problem.getString("title")).isEqualTo("Bad Request");
    assertThat(problem.getString("detail"))
        .isEqualTo("Malformed JSON request body at byte offset 13");
    assertThat(logs.messages()).noneMatch(m -> m.contains("Failure in @ExceptionHandler"));
    assertThat(logs.warnings("Rejected request input for POST /users"))
        .singleElement()
        .asString()
        .contains("UnexpectedEndOfInputException");
  }

  @Test
  void thatMistypedPropertyIsRejectedWithItsPathOnly() {
    final JsonPath problem =
        postUser(
                "{\"username\": \"user1\", \"email\": \"user1@localhost.com\","
                    + " \"enabled\": \"maybe\", \"roles\": []}")
            .statusCode(400)
            .extract()
            .jsonPath();

    assertThat(problem.getString("detail")).isEqualTo("Invalid value at 'enabled'");
    assertThat(logs.messages()).noneMatch(m -> m.contains("Failure in @ExceptionHandler"));
    assertThat(logs.warnings("Rejected request input for POST /users"))
        .singleElement()
        .asString()
        .contains("InvalidFormatException");
  }

  @Test
  void thatUnconvertibleQueryParameterIsRejectedWith400() {
    final JsonPath problem =
        requestSpecification()
            .accept(MediaType.APPLICATION_PROBLEM_JSON_VALUE)
            .when()
            .get("/users?page=abc")
            .then()
            .statusCode(400)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE)
            .extract()
            .jsonPath();

    assertThat(problem.getInt("status")).isEqualTo(400);
    assertThat(logs.messages()).noneMatch(m -> m.contains("Failure in @ExceptionHandler"));
  }

  @Test
  void thatUnsupportedMethodIsRejectedWith405AndKeepsTheAllowHeader() {
    final ValidatableResponse response =
        requestSpecification()
            .accept(MediaType.APPLICATION_PROBLEM_JSON_VALUE)
            .when()
            .patch("/roles")
            .then()
            .statusCode(HttpStatus.METHOD_NOT_ALLOWED.value())
            .contentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

    assertThat(response.extract().jsonPath().getInt("status")).isEqualTo(405);
    assertThat(response.extract().header(HttpHeaders.ALLOW)).contains("GET");
    assertThat(logs.messages()).noneMatch(m -> m.contains("Failure in @ExceptionHandler"));
  }

  @Test
  void thatBodyValidationFailureKeepsTheViolationsShape() {
    final JsonPath problem =
        postUser(
                "{\"username\": \"<user1>\", \"email\": \"user1@localhost.com\","
                    + " \"enabled\": true, \"roles\": []}")
            .statusCode(400)
            .extract()
            .jsonPath();

    assertThat(problem.getMap("")).containsOnlyKeys("instance", "status", "title", "violations");
    assertThat(problem.getInt("status")).isEqualTo(400);
    assertThat(problem.getString("title")).isEqualTo("Constraint Violation");
    assertThat(problem.getList("violations.field")).containsExactly("username");
    assertThat(problem.getString("violations[0].message")).startsWith("must match ");
    assertThat(logs.messages()).noneMatch(m -> m.contains("Failure in @ExceptionHandler"));
  }

  @Nested
  @TestPropertySource(properties = "spring.jackson.deserialization.fail-on-unknown-properties=true")
  class WhenUnknownPropertiesAreRejected {

    @Test
    void thatUnknownNestedPropertyIsRejectedWithItsPath() {
      final JsonPath problem =
          postUser(
                  "{\"username\": \"user1\", \"email\": \"user1@localhost.com\","
                      + " \"enabled\": true, \"roles\": [{\"id\": \"1\", \"name\": \"a\","
                      + " \"isAdmin\": true}]}")
              .statusCode(400)
              .extract()
              .jsonPath();

      assertThat(problem.getString("detail"))
          .isEqualTo("Unrecognized property at 'roles[0].isAdmin'");
    }
  }

  private ValidatableResponse postUser(String body) {
    return requestSpecification()
        .accept(MediaType.APPLICATION_PROBLEM_JSON_VALUE)
        .contentType(MediaType.APPLICATION_JSON_VALUE)
        .body(body)
        .when()
        .post("/users")
        .then()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
  }

  private static Logger rootLogger() {
    return (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
  }

  private static final class CapturingAppender extends AppenderBase<ILoggingEvent> {
    private final List<ILoggingEvent> events = new CopyOnWriteArrayList<>();

    @Override
    protected void append(ILoggingEvent event) {
      events.add(event);
    }

    List<String> messages() {
      return events.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    List<String> warnings(String prefix) {
      return events.stream()
          .filter(event -> event.getLevel() == Level.WARN)
          .map(ILoggingEvent::getFormattedMessage)
          .filter(message -> message.startsWith(prefix))
          .toList();
    }
  }
}

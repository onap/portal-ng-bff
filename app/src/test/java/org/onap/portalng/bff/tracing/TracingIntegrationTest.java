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

package org.onap.portalng.bff.tracing;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import io.micrometer.observation.ObservationHandler;
import io.restassured.http.Header;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.logstash.logback.encoder.LogstashEncoder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.onap.portalng.bff.MainApplicationProperties;
import org.onap.portalng.bff.config.DownstreamCallLoggingFilter;
import org.onap.portalng.bff.openapi.client_preferences.model.PreferencesPreferencesDto;
import org.onap.portalng.bff.openapi.client_preferences.model.ProblemPreferencesDto;
import org.onap.portalng.bff.preferences.PreferencesMocks;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.reactive.function.client.ClientRequestObservationContext;
import tools.jackson.databind.JsonNode;

@TestPropertySource(properties = "management.opentelemetry.tracing.export.schedule-delay=100ms")
@Import(TracingIntegrationTest.ClientObservationRecorder.class)
class TracingIntegrationTest extends PreferencesMocks {

  @DynamicPropertySource
  static void productionTracingProperties(DynamicPropertyRegistry registry) throws IOException {
    MainApplicationProperties.register(
        registry,
        "management.tracing.",
        "management.opentelemetry.",
        "management.otlp.",
        "spring.reactor.");
    registry.add("COLLECTOR_HOST", () -> "http://localhost");
    registry.add("COLLECTOR_PORT", () -> "${wiremock.server.port}");
  }

  @Autowired private ClientObservationRecorder clientObservations;
  @Autowired private ReactiveOAuth2AuthorizedClientService authorizedClientService;

  private final JsonCapturingAppender downstreamLogs = new JsonCapturingAppender();

  @BeforeEach
  void captureDownstreamCallLogs() {
    downstreamLogs.start();
    downstreamCallLogger().addAppender(downstreamLogs);
  }

  @AfterEach
  void stopCapturingDownstreamCallLogs() {
    downstreamCallLogger().detachAppender(downstreamLogs);
  }

  @Test
  void thatTheSpansOfARequestAreExportedToTheCollector() throws Exception {
    WireMock.stubFor(WireMock.post("/v1/traces").willReturn(WireMock.ok()));
    mockGetPreferences(new PreferencesPreferencesDto());

    getPreferences();

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(
                        WireMock.findAll(
                            WireMock.postRequestedFor(WireMock.urlEqualTo("/v1/traces"))
                                .withHeader(
                                    HttpHeaders.CONTENT_TYPE,
                                    WireMock.equalTo("application/x-protobuf"))))
                    .isNotEmpty());
  }

  @Test
  void thatDownstreamCallLogsCarryTheTraceAndSpanIdsOfTheRequest() throws Exception {
    mockGetPreferences(new PreferencesPreferencesDto());

    getPreferences();

    final List<JsonNode> preferencesCalls = downstreamLogsOf("PREFERENCES", 2);
    assertThat(preferencesCalls)
        .extracting(line -> line.path("message").asText())
        .anySatisfy(
            message ->
                assertThat(message)
                    .matches(
                        "bff - downstream request - X-Request-Id %s PREFERENCES GET \\S+/v1/preferences"
                            .formatted(X_REQUEST_ID)))
        .anySatisfy(
            message ->
                assertThat(message)
                    .matches(
                        "bff - downstream response - X-Request-Id %s PREFERENCES GET \\S+/v1/preferences 200 in \\d+ ms"
                            .formatted(X_REQUEST_ID)));
    assertThat(preferencesCalls)
        .allSatisfy(
            line -> {
              assertThat(line.path("traceId").asText()).matches("[0-9a-f]{32}");
              assertThat(line.path("spanId").asText()).matches("[0-9a-f]{16}");
            });
    assertThat(preferencesCalls.stream().map(line -> line.path("traceId").asText()).distinct())
        .hasSize(1);
  }

  @Test
  void thatAnErrorResponseIsLoggedWithTheDownstreamStatus() throws Exception {
    mockGetPreferencesError(new ProblemPreferencesDto());

    requestSpecification()
        .given()
        .accept(MediaType.APPLICATION_JSON_VALUE)
        .header(new Header("X-Request-Id", X_REQUEST_ID))
        .when()
        .get("/preferences")
        .then()
        .statusCode(HttpStatus.BAD_GATEWAY.value());

    assertThat(downstreamLogsOf("PREFERENCES", 2))
        .extracting(line -> line.path("message").asText())
        .anySatisfy(
            message ->
                assertThat(message)
                    .matches(
                        "bff - downstream response - X-Request-Id %s PREFERENCES GET \\S+/v1/preferences 401 in \\d+ ms"
                            .formatted(X_REQUEST_ID)));
  }

  @Test
  void thatAFailedDownstreamCallIsLoggedWithItsCause() {
    WireMock.stubFor(
        WireMock.get(WireMock.urlEqualTo("/v1/preferences"))
            .willReturn(WireMock.aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

    requestSpecification()
        .given()
        .accept(MediaType.APPLICATION_JSON_VALUE)
        .header(new Header("X-Request-Id", X_REQUEST_ID))
        .when()
        .get("/preferences")
        .then()
        .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());

    final List<JsonNode> preferencesCalls = downstreamLogsOf("PREFERENCES", 2);
    assertThat(preferencesCalls)
        .anySatisfy(
            line -> {
              assertThat(line.path("level").asText()).isEqualTo("WARN");
              assertThat(line.path("message").asText())
                  .matches(
                      "bff - downstream failure - X-Request-Id %s PREFERENCES GET \\S+/v1/preferences after \\d+ ms"
                          .formatted(X_REQUEST_ID));
              assertThat(line.path("stack_trace").asText()).isNotBlank();
            });
  }

  @Test
  void thatTheClientCredentialsTokenRequestIsPartOfTheRequestTrace() throws Exception {
    authorizedClientService.removeAuthorizedClient("keycloak", "client-credentials").block();
    clientObservations.contexts.clear();
    mockGetPreferences(new PreferencesPreferencesDto());

    getPreferences();

    assertThat(clientObservations.contexts)
        .filteredOn(
            context ->
                context.getRequest().url().getPath().endsWith("/protocol/openid-connect/token")
                    && context
                        .getRequest()
                        .headers()
                        .getFirst(HttpHeaders.AUTHORIZATION)
                        .startsWith("Basic "))
        .singleElement()
        .satisfies(context -> assertThat(context.getParentObservation()).isNotNull());
  }

  private List<JsonNode> downstreamLogsOf(String downstreamSystem, int expected) {
    return await()
        .atMost(Duration.ofSeconds(5))
        .until(
            () ->
                downstreamLogs.lines.stream()
                    .map(line -> objectMapper.readTree(line))
                    .filter(
                        line ->
                            line.path("message").asText().contains(" " + downstreamSystem + " "))
                    .toList(),
            lines -> lines.size() >= expected);
  }

  private static Logger downstreamCallLogger() {
    return (Logger) LoggerFactory.getLogger(DownstreamCallLoggingFilter.class);
  }

  /**
   * Encodes each event when it is logged: the MDC of a logback event is read lazily, so an event
   * inspected later on the test thread would report that thread's MDC instead.
   */
  private static final class JsonCapturingAppender extends AppenderBase<ILoggingEvent> {
    private final LogstashEncoder encoder = new LogstashEncoder();
    private final List<String> lines = new CopyOnWriteArrayList<>();

    @Override
    public void start() {
      encoder.start();
      super.start();
    }

    @Override
    protected void append(ILoggingEvent event) {
      lines.add(new String(encoder.encode(event), UTF_8));
    }
  }

  @TestConfiguration
  static class ClientObservationRecorder {
    private final List<ClientRequestObservationContext> contexts = new CopyOnWriteArrayList<>();

    @Bean
    ObservationHandler<ClientRequestObservationContext> recordingClientObservationHandler() {
      return new ObservationHandler<>() {
        @Override
        public void onStart(ClientRequestObservationContext context) {
          contexts.add(context);
        }

        @Override
        public boolean supportsContext(io.micrometer.observation.Observation.Context context) {
          return context instanceof ClientRequestObservationContext;
        }
      };
    }
  }
}

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

package org.onap.portalng.bff.users;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.restassured.http.ContentType;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.onap.portalng.bff.BaseIntegrationTest;
import org.springframework.http.HttpStatus;

class UserAdministrationMetricsIntegrationTest extends BaseIntegrationTest {

  private static final String DELETE_SUCCESS =
      "portalng_bff_user_administration_total{operation=\"delete_user\",outcome=\"success\"}";
  private static final String DELETE_FAILURE =
      "portalng_bff_user_administration_total{operation=\"delete_user\",outcome=\"failure\"}";

  @Test
  void thatSuccessfulAndFailedUserDeletionsAreCounted() {
    final String before = scrape();

    mockDeleteUser("1", 204);
    requestSpecification().when().delete("/users/1").then().statusCode(204);
    mockDeleteUser("2", 400);
    requestSpecification()
        .when()
        .delete("/users/2")
        .then()
        .statusCode(HttpStatus.BAD_GATEWAY.value());

    final String after = scrape();
    assertThat(value(after, DELETE_SUCCESS) - value(before, DELETE_SUCCESS)).isEqualTo(1.0);
    assertThat(value(after, DELETE_FAILURE) - value(before, DELETE_FAILURE)).isEqualTo(1.0);
  }

  private void mockDeleteUser(String userId, int status) {
    WireMock.stubFor(
        WireMock.delete(WireMock.urlMatching("/admin/realms/%s/users/%s".formatted(realm, userId)))
            .willReturn(WireMock.aResponse().withStatus(status)));
  }

  private String scrape() {
    return unauthenticatedRequestSpecification()
        .accept(ContentType.TEXT)
        .when()
        .get("/actuator/prometheus")
        .then()
        .statusCode(HttpStatus.OK.value())
        .extract()
        .asString();
  }

  private static double value(String scrape, String series) {
    final Matcher matcher =
        Pattern.compile("^" + Pattern.quote(series) + " (\\S+)$", Pattern.MULTILINE)
            .matcher(scrape);
    return matcher.find() ? Double.parseDouble(matcher.group(1)) : 0.0;
  }
}

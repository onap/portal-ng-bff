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

package org.onap.portalng.bff.headers;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.EqualToPattern;
import io.restassured.http.Header;
import org.junit.jupiter.api.Test;
import org.onap.portalng.bff.BaseIntegrationTest;
import org.onap.portalng.bff.openapi.client_portal_prefs.model.PreferencesPortalPrefsDto;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

class XRequestIdHeaderTest extends BaseIntegrationTest {
  protected static final String X_REQUEST_ID = "addf6005-3075-4c80-b7bc-2c70b7d42b57";

  @Test
  void xRequestIdHeaderIsCorrectlySetInResponse() throws Exception {
    // use preferences endpoint for testing the header
    final PreferencesPortalPrefsDto preferencesPortalPrefsDto = new PreferencesPortalPrefsDto();

    // mockGetTile(tileDetailResponsePortalServiceDto, X_REQUEST_ID);
    mockGetPreferences(preferencesPortalPrefsDto, X_REQUEST_ID);

    final String response = getPreferencesExtractHeader(X_REQUEST_ID);
    assertThat(response).isEqualTo(X_REQUEST_ID);
  }

  protected void mockGetPreferences(
      PreferencesPortalPrefsDto preferencesPortalPrefsDto, String xRequestId) throws Exception {
    WireMock.stubFor(
        WireMock.get(WireMock.urlEqualTo("/v1/preferences"))
            .withHeader("X-Request-Id", new EqualToPattern(X_REQUEST_ID))
            .willReturn(
                WireMock.aResponse()
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withHeader("X-Request-Id", xRequestId)
                    .withBody(objectMapper.writeValueAsString(preferencesPortalPrefsDto))));
  }

  protected String getPreferencesExtractHeader(String xRequestId) {
    return requestSpecification()
        .given()
        .accept(MediaType.APPLICATION_JSON_VALUE)
        .header(new Header("X-Request-Id", xRequestId))
        .when()
        .get("/preferences")
        .then()
        .statusCode(HttpStatus.OK.value())
        .extract()
        .header("X-Request-Id");
  }
}

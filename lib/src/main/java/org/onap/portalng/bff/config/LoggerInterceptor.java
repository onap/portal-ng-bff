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

import java.util.List;
import org.onap.portalng.bff.utils.Logger;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.reactive.ServerWebExchangeContextFilter;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class LoggerInterceptor extends ServerWebExchangeContextFilter {
  public static final String EXCHANGE_CONTEXT_ATTRIBUTE =
      ServerWebExchangeContextFilter.class.getName() + ".EXCHANGE_CONTEXT";

  public static final String X_REQUEST_ID = "X-Request-Id";

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    List<String> xRequestIdList = exchange.getRequest().getHeaders().get(X_REQUEST_ID);
    if (xRequestIdList != null && !xRequestIdList.isEmpty()) {
      String xRequestId = xRequestIdList.get(0);
      Logger.requestLog(
          xRequestId, exchange.getRequest().getMethod(), exchange.getRequest().getURI());
      exchange.getResponse().getHeaders().add(X_REQUEST_ID, xRequestId);
    }
    return chain
        .filter(exchange)
        .contextWrite(cxt -> cxt.put(EXCHANGE_CONTEXT_ATTRIBUTE, exchange));
  }
}

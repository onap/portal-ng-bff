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

package org.onap.portal.bff.config;

import com.nimbusds.jwt.JWTParser;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.util.List;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.server.ServerWebExchange;
import org.zalando.problem.Problem;
import org.zalando.problem.Status;
import reactor.core.publisher.Mono;

public class IdTokenExchangeFilterFunction implements ExchangeFilterFunction {

  public static final String X_AUTH_IDENTITY_HEADER = "X-Auth-Identity";
  public static final String CLAIM_NAME_ROLES = "roles";

  private static final List<String> EXCLUDED_PATHS_PATTERNS =
      List.of(
          "/actuator/**", "**/actuator/**", "*/actuator/**", "/**/actuator/**", "/*/actuator/**");

  private static final Mono<ServerWebExchange> serverWebExchangeFromContext =
      Mono.deferContextual(Mono::just)
          .filter(context -> context.hasKey(ServerWebExchange.class))
          .map(context -> context.get(ServerWebExchange.class));

  @Override
  public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
    boolean shouldNotFilter =
        EXCLUDED_PATHS_PATTERNS.stream()
            .anyMatch(
                excludedPath ->
                    new AntPathMatcher().match(excludedPath, request.url().getRawPath()));
    if (shouldNotFilter) {
      return next.exchange(request).switchIfEmpty(Mono.defer(() -> next.exchange(request)));
    }
    return extractServerWebExchange(request)
        .flatMap(IdTokenExchangeFilterFunction::extractIdentityHeader)
        .flatMap(
            idToken -> {
              final ClientRequest requestWithIdToken =
                  ClientRequest.from(request).header(X_AUTH_IDENTITY_HEADER, idToken).build();

              return next.exchange(requestWithIdToken);
            })
        .switchIfEmpty(Mono.defer(() -> next.exchange(request)));
  }

  private Mono<ServerWebExchange> extractServerWebExchange(ClientRequest request) {
    return Mono.justOrEmpty(request.attribute(ServerWebExchange.class.getName()))
        .cast(ServerWebExchange.class)
        .switchIfEmpty(serverWebExchangeFromContext);
  }

  private static Mono<String> extractIdentityHeader(ServerWebExchange exchange) {
    return io.vavr.collection.List.ofAll(
            exchange.getRequest().getHeaders().getOrEmpty(X_AUTH_IDENTITY_HEADER))
        .headOption()
        .map(Mono::just)
        .getOrElse(Mono.error(Problem.valueOf(Status.FORBIDDEN, "ID token is missing")));
  }

  private static Mono<String> extractIdToken(ServerWebExchange exchange) {
    return extractIdentityHeader(exchange)
        .map(identityHeader -> identityHeader.replace("Bearer ", ""));
  }

  public static Mono<Void> validateAccess(
      ServerWebExchange exchange, List<String> rolesListForMethod) {

    return extractRoles(exchange)
        .map(roles -> roles.stream().anyMatch(rolesListForMethod::contains))
        .flatMap(
            match -> {
              if (Boolean.TRUE.equals(match)) {
                return Mono.empty();
              } else {
                return Mono.error(Problem.valueOf(Status.FORBIDDEN));
              }
            });
  }

  private static Mono<List<String>> extractRoles(ServerWebExchange exchange) {
    return extractIdToken(exchange)
        .flatMap(
            token ->
                Try.of(() -> JWTParser.parse(token))
                    .mapTry(jwt -> Option.of(jwt.getJWTClaimsSet()))
                    .map(
                        optionJwtClaimSet ->
                            optionJwtClaimSet
                                .flatMap(
                                    jwtClaimSet ->
                                        Option.of(jwtClaimSet.getClaim(CLAIM_NAME_ROLES)))
                                .map(obj -> (List<String>) obj))
                    .map(Mono::just)
                    .getOrElseGet(Mono::error))
        .map(optionRoles -> optionRoles.getOrElse(List.of()));
  }
}

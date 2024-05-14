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

import com.nimbusds.jwt.JWTParser;
import java.text.ParseException;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.server.ServerWebExchange;
import org.zalando.problem.Problem;
import org.zalando.problem.Status;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;

@Component
public class IdTokenExchangeFilterFunction implements ExchangeFilterFunction {

  public static final String X_AUTH_IDENTITY_HEADER = "X-Auth-Identity";
  public static final String CLAIM_NAME_ROLES = "roles";

  private final List<String> rbacExcludedPatterns;

  private static final Mono<ServerWebExchange> serverWebExchangeFromContext =
      Mono.deferContextual(Mono::just)
          .filter(context -> context.hasKey(ServerWebExchange.class))
          .map(context -> context.get(ServerWebExchange.class));

  private final AntPathMatcher antPathMatcher = new AntPathMatcher();

  IdTokenExchangeFilterFunction(BffConfig bffConfig) {
    this.rbacExcludedPatterns = bffConfig.getRbac().endpointsExcluded();
  }

  @Override
  public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
    boolean shouldNotFilter =
        rbacExcludedPatterns.stream()
            .anyMatch(
                excludedPath -> antPathMatcher.match(excludedPath, request.url().getRawPath()));
    if (shouldNotFilter) {
      return next.exchange(request).switchIfEmpty(Mono.defer(() -> next.exchange(request)));
    }
    return extractServerWebExchange(request)
        .flatMap(IdTokenExchangeFilterFunction::extractIdentityHeader)
        .map(idToken -> ClientRequest.from(request).header(X_AUTH_IDENTITY_HEADER, idToken).build())
        .flatMap(requestWithIdToken -> next.exchange(requestWithIdToken))
        .switchIfEmpty(Mono.defer(() -> next.exchange(request)));
  }

  private Mono<ServerWebExchange> extractServerWebExchange(ClientRequest request) {
    return Mono.justOrEmpty(request.attribute(ServerWebExchange.class.getName()))
        .cast(ServerWebExchange.class)
        .switchIfEmpty(serverWebExchangeFromContext);
  }

  private static Mono<String> extractIdentityHeader(ServerWebExchange exchange) {
    return Mono.just(exchange)
        .map(exch -> exch.getRequest().getHeaders().getOrEmpty(X_AUTH_IDENTITY_HEADER).get(0))
        .onErrorResume(ex -> Mono.error(Problem.valueOf(Status.FORBIDDEN, "ID token is missing")));
  }

  private static Mono<String> extractIdToken(ServerWebExchange exchange) {
    return extractIdentityHeader(exchange)
        .map(identityHeader -> identityHeader.replace("Bearer ", ""));
  }

  public static Mono<Void> validateAccess(
      ServerWebExchange exchange, Set<String> rolesListForMethod) {

    return extractRoles(exchange)
        .map(roles -> roles.stream().anyMatch(rolesListForMethod::contains))
        .flatMap(
            match ->
                Boolean.TRUE.equals(match)
                    ? Mono.empty()
                    : Mono.error(Problem.valueOf(Status.FORBIDDEN)));
  }

  private static Mono<List<String>> extractRoles(ServerWebExchange exchange) {
    return extractIdToken(exchange)
        .map(
            token -> {
              try {
                return JWTParser.parse(token);
              } catch (ParseException e) {
                throw Exceptions.propagate(e);
              }
            })
        .map(
            jwt -> {
              try {
                return jwt.getJWTClaimsSet().getClaim(CLAIM_NAME_ROLES);
              } catch (ParseException e) {
                throw Exceptions.propagate(e);
              }
            })
        .filter(List.class::isInstance)
        .map(roles -> (List<String>) roles)
        .switchIfEmpty(Mono.just(List.<String>of()));
  }
}

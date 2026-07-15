/*
 *
 * Copyright (c) 2024. Deutsche Telekom AG
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Enforces RBAC by asking Keycloak, for each authenticated request, whether the caller may access
 * the requested URI with the requested HTTP method.
 *
 * <p>The URI-to-resource matching is delegated to Keycloak via an UMA-ticket request with {@code
 * response_mode=decision} and {@code permission_resource_matching_uri=true}: Keycloak matches the
 * request URI against the {@code uris} attribute of each authorization resource and returns a
 * single {@code {"result":true}} decision when access is granted (or HTTP 403 when denied). Doing
 * the matching in Keycloak — rather than reimplementing it here — keeps the filter agnostic of how
 * resources are named and how their URI patterns are written.
 *
 * <p>Each decision is cached per token (keyed by the JWT id together with the HTTP method and URI)
 * until the token expires, so a given endpoint is evaluated by Keycloak only once per token.
 */
@Component
@Slf4j
public class KeycloakPermissionFilter implements WebFilter {

  private final WebClient webClient;
  private final ObjectMapper objectMapper;
  private final BffConfig bffConfig;
  private final List<String> excludedPatterns;
  private final AntPathMatcher antPathMatcher = new AntPathMatcher();
  private final Cache<String, Boolean> decisionCache;

  public KeycloakPermissionFilter(
      WebClient.Builder webClientBuilder,
      ObjectMapper objectMapper,
      BffConfig bffConfig,
      @Value("${bff.rbac.endpoints-excluded}") String[] excludedPaths) {
    this.webClient = webClientBuilder.build();
    this.objectMapper = objectMapper;
    this.bffConfig = bffConfig;
    this.excludedPatterns = List.of(excludedPaths);
    this.decisionCache =
        Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfter(
                new Expiry<String, Boolean>() {
                  @Override
                  public long expireAfterCreate(String key, Boolean value, long currentTime) {
                    // Safe fallback, overridden by the explicit per-token ttl in policy().put().
                    return Duration.ofMinutes(5).toNanos();
                  }

                  @Override
                  public long expireAfterUpdate(
                      String key, Boolean value, long currentTime, long currentDuration) {
                    return currentDuration;
                  }

                  @Override
                  public long expireAfterRead(
                      String key, Boolean value, long currentTime, long currentDuration) {
                    return currentDuration;
                  }
                })
            .build();
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String uri = exchange.getRequest().getURI().getPath();
    String method = exchange.getRequest().getMethod().toString();

    for (String pattern : excludedPatterns) {
      if (antPathMatcher.match(pattern, uri)) {
        return chain.filter(exchange);
      }
    }

    return exchange
        .getPrincipal()
        .switchIfEmpty(
            Mono.defer(
                () -> {
                  exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                  return exchange.getResponse().setComplete().then(Mono.empty());
                }))
        .cast(JwtAuthenticationToken.class)
        .flatMap(
            auth -> {
              Jwt jwt = auth.getToken();
              String accessToken = "Bearer " + jwt.getTokenValue();
              String jti = jwt.getId();
              Instant exp = jwt.getExpiresAt();
              Duration ttl =
                  (exp != null) ? Duration.between(Instant.now(), exp) : Duration.ofMinutes(5);
              if (ttl.isNegative() || ttl.isZero()) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete().then(Mono.empty());
              }

              if (jti != null) {
                Boolean cached = decisionCache.getIfPresent(cacheKey(jti, method, uri));
                if (cached != null) {
                  return Mono.just(cached);
                }
              }

              return fetchDecision(accessToken, uri, method)
                  .doOnNext(granted -> cacheDecision(jti, method, uri, granted, ttl));
            })
        // The 403 fallback must cover ONLY the permission decision (Keycloak call, token parsing),
        // NOT the downstream request handling: errors from chain.filter() below (e.g. a
        // ServerWebInputException that must render as 400) would otherwise be swallowed into a 403.
        .onErrorResume(
            ex -> {
              log.error("Permission check failed", ex);
              exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
              return exchange.getResponse().setComplete().then(Mono.empty());
            })
        .flatMap(granted -> applyDecision(granted, exchange, chain));
  }

  /**
   * Asks Keycloak whether the given URI and method are allowed for the caller.
   *
   * @return a {@link Mono} emitting {@code true} (granted) or {@code false} (denied) for a
   *     definitive decision; the {@link Mono} errors for any non-decision response (e.g. a Keycloak
   *     outage or an unparseable body) so that the outcome is not cached and the request is
   *     rejected with 403.
   */
  private Mono<Boolean> fetchDecision(String accessToken, String uri, String method) {
    // permission=<uri>#<method> is sent unencoded on purpose: Keycloak splits the resource from the
    // scope on the '#' separator and matches <uri> against each resource's configured uris.
    String body =
        "grant_type=urn:ietf:params:oauth:grant-type:uma-ticket"
            + "&audience="
            + URLEncoder.encode(bffConfig.getKeycloakClientId(), StandardCharsets.UTF_8)
            + "&permission_resource_format=uri"
            + "&permission_resource_matching_uri=true"
            + "&permission="
            + uri
            + "#"
            + method
            + "&response_mode=decision";

    return webClient
        .post()
        .uri(
            bffConfig.getKeycloakUrl()
                + "/realms/"
                + bffConfig.getRealm()
                + "/protocol/openid-connect/token")
        .header(HttpHeaders.AUTHORIZATION, accessToken)
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .bodyValue(body)
        .exchangeToMono(
            response -> {
              if (response.statusCode().is2xxSuccessful()) {
                return response.bodyToMono(String.class).map(this::isGranted);
              }
              // Keycloak answers a denied UMA-ticket request with 403; treat that as a definitive
              // "deny" decision (cacheable) rather than an error.
              if (response.statusCode().value() == HttpStatus.FORBIDDEN.value()) {
                return response.releaseBody().thenReturn(Boolean.FALSE);
              }
              return response.createException().flatMap(Mono::error);
            });
  }

  private boolean isGranted(String response) {
    try {
      JsonNode jsonNode = objectMapper.readTree(response);
      return jsonNode.has("result") && jsonNode.get("result").asBoolean();
    } catch (Exception e) {
      // An unparseable body is not a decision; surface it as an error so it is not cached.
      throw new IllegalStateException("Could not parse Keycloak decision response", e);
    }
  }

  private void cacheDecision(String jti, String method, String uri, boolean granted, Duration ttl) {
    if (jti == null) {
      return;
    }
    decisionCache
        .policy()
        .expireVariably()
        .ifPresent(policy -> policy.put(cacheKey(jti, method, uri), granted, ttl));
  }

  private Mono<Void> applyDecision(
      boolean granted, ServerWebExchange exchange, WebFilterChain chain) {
    if (granted) {
      return chain.filter(exchange);
    }
    exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
    return exchange.getResponse().setComplete();
  }

  private static String cacheKey(String jti, String method, String uri) {
    return jti + "|" + method + "|" + uri;
  }
}

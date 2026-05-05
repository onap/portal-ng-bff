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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
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

@Component
@Slf4j
public class KeycloakPermissionFilter implements WebFilter {

  private final WebClient webClient;
  private final ObjectMapper objectMapper;
  private final BffConfig bffConfig;
  private final List<String> excludedPatterns;
  private final AntPathMatcher antPathMatcher = new AntPathMatcher();
  private final Cache<String, List<CachedPermission>> permissionCache;

  public KeycloakPermissionFilter(
      WebClient.Builder webClientBuilder,
      ObjectMapper objectMapper,
      BffConfig bffConfig,
      @Value("${bff.rbac.endpoints-excluded}") String[] excludedPaths) {
    this.webClient = webClientBuilder.build();
    this.objectMapper = objectMapper;
    this.bffConfig = bffConfig;
    this.excludedPatterns = List.of(excludedPaths);
    this.permissionCache =
        Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfter(
                new Expiry<String, List<CachedPermission>>() {
                  @Override
                  public long expireAfterCreate(
                      String key, List<CachedPermission> value, long currentTime) {
                    return value.isEmpty()
                        ? Duration.ofMinutes(1).toNanos()
                        : Duration.ofMinutes(5)
                            .toNanos(); // safe fallback, overridden by policy.put()
                  }

                  @Override
                  public long expireAfterUpdate(
                      String key,
                      List<CachedPermission> value,
                      long currentTime,
                      long currentDuration) {
                    return currentDuration;
                  }

                  @Override
                  public long expireAfterRead(
                      String key,
                      List<CachedPermission> value,
                      long currentTime,
                      long currentDuration) {
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
                return exchange.getResponse().setComplete();
              }

              if (jti != null) {
                List<CachedPermission> cached = permissionCache.getIfPresent(jti);
                if (cached != null) {
                  return evaluatePermission(cached, uri, method, exchange, chain);
                }
              }

              return fetchPermissions(accessToken, jti, ttl)
                  .flatMap(
                      permissions -> evaluatePermission(permissions, uri, method, exchange, chain));
            })
        .onErrorResume(
            ex -> {
              log.error("Permission check failed", ex);
              exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
              return exchange.getResponse().setComplete();
            });
  }

  private Mono<List<CachedPermission>> fetchPermissions(
      String accessToken, String jti, Duration ttl) {
    String body =
        "grant_type=urn:ietf:params:oauth:grant-type:uma-ticket"
            + "&audience="
            + URLEncoder.encode(bffConfig.getKeycloakClientId(), StandardCharsets.UTF_8)
            + "&response_mode=permissions";

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
        .retrieve()
        .bodyToMono(String.class)
        .flatMap(this::parsePermissions)
        .doOnNext(
            permissions -> {
              if (jti != null) {
                permissionCache
                    .policy()
                    .expireVariably()
                    .ifPresent(
                        policy -> {
                          policy.put(jti, permissions, ttl);
                        });
              }
            });
  }

  private Mono<List<CachedPermission>> parsePermissions(String response) {
    try {
      return Mono.just(
          objectMapper.readValue(response, new TypeReference<List<CachedPermission>>() {}));
    } catch (Exception e) {
      log.error("Error parsing permissions response", e);
      return Mono.error(e);
    }
  }

  private Mono<Void> evaluatePermission(
      List<CachedPermission> permissions,
      String uri,
      String method,
      ServerWebExchange exchange,
      WebFilterChain chain) {
    boolean granted =
        permissions.stream()
            .anyMatch(
                p -> matchesResource(p, uri) && p.scopes() != null && p.scopes().contains(method));
    if (granted) {
      return chain.filter(exchange);
    }
    exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
    return exchange.getResponse().setComplete();
  }

  private boolean matchesResource(CachedPermission permission, String uri) {
    String rsname = permission.rsname();
    if (rsname == null) {
      return false;
    }
    String prefix = "/" + rsname;
    return uri.equals(prefix) || uri.startsWith(prefix + "/");
  }

  record CachedPermission(String rsname, Set<String> scopes) {}
}

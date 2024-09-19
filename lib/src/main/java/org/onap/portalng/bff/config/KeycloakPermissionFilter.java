package org.onap.portalng.bff.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
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

  @Value("${bff.endpoints.unauthenticated}")
  private String[] EXCLUDED_PATHS;

  public KeycloakPermissionFilter(
      WebClient.Builder webClientBuilder, ObjectMapper objectMapper, BffConfig bffConfig) {
    this.webClient = webClientBuilder.build();
    this.objectMapper = objectMapper;
    this.bffConfig = bffConfig;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String accessToken = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    String uri = exchange.getRequest().getURI().getPath();
    String method = exchange.getRequest().getMethod().toString();

    for (String excludedPath : EXCLUDED_PATHS) {
      if (uri.matches(excludedPath.replace("**", ".*"))) {
        return chain.filter(exchange);
      }
    }

    String body =
        new StringBuilder()
            .append("grant_type=urn:ietf:params:oauth:grant-type:uma-ticket")
            .append("&audience=")
            .append(bffConfig.getKeycloakClientId())
            .append("&permission_resource_format=uri")
            .append("&permission_resource_matching_uri=true")
            .append("&permission=")
            .append(uri)
            .append("#")
            .append(method)
            .append("&response_mode=decision")
            .toString();

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
        .flatMap(
            response -> {
              if (isPermissionGranted(response)) {
                return chain.filter(exchange);
              } else {
                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                return exchange.getResponse().setComplete();
              }
            })
        .onErrorResume(
            ex -> {
              log.error("RBAC request body: {}", body);
              log.error("RBAC response error: {}", ex.getMessage());
              exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
              return exchange.getResponse().setComplete();
            });
  }

  private boolean isPermissionGranted(String response) {
    log.info("isPermissionGranted: {}", response);
    try {
      JsonNode jsonNode = objectMapper.readTree(response);
      if (jsonNode.has("result") && jsonNode.get("result").asBoolean()) {
        return true;
      }
    } catch (Exception e) {
      log.error("Error parsing JSON response", e);
    }
    return false;
  }
}

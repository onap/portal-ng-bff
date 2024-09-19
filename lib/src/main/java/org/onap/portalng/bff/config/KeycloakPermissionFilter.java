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
  private final String keycloakUrl;
  private final String realm;
  private final String clientId;
  private final ObjectMapper objectMapper;

  private static final String[] EXCLUDED_PATHS = {
    "/api-docs.html", "/api.yaml", "/webjars/**", "/actuator/**"
  };

  public KeycloakPermissionFilter(
      WebClient.Builder webClientBuilder,
      @Value("${KEYCLOAK_URL}") String keycloakUrl,
      @Value("${KEYCLOAK_REALM}") String realm,
      @Value("${KEYCLOAK_CLIENT_ID}") String clientId,
      ObjectMapper objectMapper) {
    this.webClient = webClientBuilder.build();
    this.keycloakUrl = keycloakUrl;
    this.realm = realm;
    this.clientId = clientId;
    this.objectMapper = objectMapper;
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
            .append(clientId)
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
        .uri(keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/token")
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

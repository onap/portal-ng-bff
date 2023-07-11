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

package org.onap.portal.bff.services;

import io.vavr.API;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.onap.portal.bff.exceptions.DownstreamApiProblemException;
import org.onap.portal.bff.mappers.CredentialMapper;
import org.onap.portal.bff.mappers.RolesMapper;
import org.onap.portal.bff.mappers.UsersMapper;
import org.onap.portal.bff.openapi.client_portal_keycloak.api.KeycloakApi;
import org.onap.portal.bff.openapi.client_portal_keycloak.model.RequiredActionsKeycloakDto;
import org.onap.portal.bff.openapi.server.model.*;
import org.onap.portal.bff.utils.Logger;
import org.springframework.core.convert.support.ConfigurableConversionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.zalando.problem.Status;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@RequiredArgsConstructor
@Service
public class KeycloakService {
  private final KeycloakApi keycloakApi;
  private final ConfigurableConversionService conversionService;
  private final RolesMapper rolesMapper;
  private final UsersMapper usersMapper;
  private final CredentialMapper credentialMapper;

  public Mono<UserResponseApiDto> createUser(CreateUserRequestApiDto request, String xRequestId) {
    log.debug("Create user in keycloak. request=`{}`", request);

    final List<RoleApiDto> rolesToBeAssigned =
        request.getRoles().isEmpty() ? Collections.emptyList() : request.getRoles();
    return listRoles(xRequestId)
        .collectList()
        .flatMap(
            realmRoles -> {
              final List<RoleApiDto> absentRoles =
                  rolesToBeAssigned.stream().filter(role -> !realmRoles.contains(role)).toList();
              if (!absentRoles.isEmpty()) {
                return Mono.error(
                    DownstreamApiProblemException.builder()
                        .status(Status.NOT_FOUND)
                        .detail(
                            String.format(
                                "Roles not found in the realm: %s",
                                absentRoles.stream().map(RoleApiDto::getName).toList()))
                        .downstreamSystem(ProblemApiDto.DownstreamSystemEnum.KEYCLOAK.toString())
                        .title(HttpStatus.NOT_FOUND.toString())
                        .build());
              }
              return Mono.just(rolesToBeAssigned);
            })
        .flatMap(roles -> createUserWithRoles(request, xRequestId, roles));
  }

  private Mono<UserResponseApiDto> createUserWithRoles(
      CreateUserRequestApiDto request, String xRequestId, List<RoleApiDto> roles) {
    return keycloakApi
        .createUserWithHttpInfo(
            usersMapper.convert(request, List.of(RequiredActionsKeycloakDto.UPDATE_PASSWORD)))
        .map(responseEntit -> responseEntit.getHeaders().getLocation())
        .map(URI::toString)
        .map(location -> location.substring(location.lastIndexOf("/") + 1))
        .flatMap(userId -> !roles.isEmpty() ? assignRoles(userId, roles) : Mono.just(userId))
        .flatMap(
            userId -> sendActionEmail(userId, List.of(RequiredActionsKeycloakDto.UPDATE_PASSWORD)))
        .onErrorResume(
            DownstreamApiProblemException.class,
            ex -> {
              Logger.errorLog(
                  xRequestId,
                  "Create user failed at sending update-password email for userName",
                  request.getUsername(),
                  ProblemApiDto.DownstreamSystemEnum.KEYCLOAK.toString());
              return Mono.error(ex);
            })
        .flatMap((String userId1) -> getUser(userId1, xRequestId));
  }

  public Mono<UserResponseApiDto> getUser(String userId, String xRequestId) {
    log.debug("Get user from keycloak. userId=`{}`", userId);
    return Mono.zip(
            keycloakApi
                .getUser(userId)
                .map(user -> conversionService.convert(user, UserResponseApiDto.class)),
            getAssignedRoles(userId, xRequestId))
        .map(
            tuple ->
                new UserResponseApiDto()
                    .username(tuple.getT1().getUsername())
                    .email(tuple.getT1().getEmail())
                    .enabled(tuple.getT1().getEnabled())
                    .id(tuple.getT1().getId())
                    .firstName(tuple.getT1().getFirstName())
                    .lastName(tuple.getT1().getLastName())
                    .realmRoles(
                        tuple.getT2().getItems().stream().map(RoleApiDto::getName).toList()))
        .onErrorResume(
            DownstreamApiProblemException.class,
            ex -> {
              Logger.errorLog(
                  xRequestId,
                  "Failed to get user",
                  userId,
                  ProblemApiDto.DownstreamSystemEnum.KEYCLOAK.toString());
              return Mono.error(ex);
            });
  }

  public Mono<UserListResponseApiDto> listUsers(int page, int pageSize, String xRequestId) {
    log.debug("Get users from keycloak. page=`{}`, pageSize=`{}`", page, pageSize);
    final int first = (page - 1) * pageSize;

    return Mono.zip(
            keycloakApi.getUsersCount(null, null, null, null, null, null, null),
            keycloakApi
                .getUsers(
                    null, null, null, null, null, null, null, null, first, pageSize, null, null,
                    null, null)
                .collectList(),
            listRoles(xRequestId)
                .flatMap(
                    role ->
                        listUsersByRole(role.getName(), xRequestId)
                            .map(user -> Tuple.of(user.getId(), role.getName())))
                .collectList()
                .map(io.vavr.collection.List::ofAll)
                .map(list -> list.groupBy(t -> t._1).map((k, v) -> Tuple.of(k, v.map(Tuple2::_2)))))
        .map(
            tuple -> {
              final UserListResponseApiDto result = new UserListResponseApiDto();
              result.setTotalCount(tuple.getT1());
              result.setItems(
                  io.vavr.collection.List.ofAll(tuple.getT2())
                      .map(
                          user ->
                              usersMapper.convert(
                                  user,
                                  tuple.getT3().getOrElse(user.getId(), API.List()).toJavaList()))
                      .toJavaList());
              // result.setItems(
              //     tuple.getT2().stream()
              //         .map(user -> usersMapper.convert(user,tuple.getT3()))
              //         .toList());

              return result;
            })
        .onErrorResume(
            DownstreamApiProblemException.class,
            ex -> {
              Logger.errorLog(
                  xRequestId,
                  "List users failed",
                  null,
                  ProblemApiDto.DownstreamSystemEnum.KEYCLOAK.toString());
              return Mono.error(ex);
            });
  }

  public Mono<Void> updateUser(String userId, UpdateUserRequestApiDto request, String xRequestId) {
    log.debug("Update user in keycloak. userId=`{}`, request=`{}`", userId, request);
    return keycloakApi
        .updateUser(userId, usersMapper.convert(request))
        .onErrorResume(
            DownstreamApiProblemException.class,
            ex -> {
              Logger.errorLog(
                  xRequestId,
                  "Failed to update user",
                  userId,
                  ProblemApiDto.DownstreamSystemEnum.KEYCLOAK.toString());
              return Mono.error(ex);
            });
  }

  public Mono<Void> updateUserPassword(String userId, UpdateUserPasswordRequestApiDto request) {
    log.debug(
        "Update password for user in keycloak. userId=`{}`, temporary=`{}`",
        userId,
        request.getTemporary());

    return keycloakApi.resetUserPassword(userId, credentialMapper.convert(request));
  }

  public Mono<Void> deleteUser(String userId, String xRequestId) {
    log.debug("Delete user from keycloak. userId=`{}`", userId);

    return keycloakApi
        .deleteUser(userId)
        .onErrorResume(
            DownstreamApiProblemException.class,
            ex -> {
              Logger.errorLog(
                  xRequestId,
                  "Failed to delete user",
                  userId,
                  ProblemApiDto.DownstreamSystemEnum.KEYCLOAK.toString());
              return Mono.error(ex);
            });
  }

  public Mono<String> assignRoles(String userId, List<RoleApiDto> roles) {
    log.debug(
        "Assign roles to user in keycloak. userId=`{}`, roleIds=`{}`",
        userId,
        roles.stream().map(RoleApiDto::getId).collect(Collectors.joining(", ")));

    return keycloakApi
        .addRealmRoleMappingsToUser(userId, roles.stream().map(rolesMapper::convert).toList())
        .thenReturn(userId);
  }

  public Mono<RoleListResponseApiDto> updateAssignedRoles(
      String userId, List<RoleApiDto> roles, String xRequestId) {
    log.debug(
        "Update assigned roles for user in keycloak. userId=`{}`, roleIds=`{}`",
        userId,
        roles.stream().map(RoleApiDto::getId).collect(Collectors.joining(", ")));

    return getAssignedRoles(userId, xRequestId)
        .map(response -> response.getItems())
        .flatMap(
            assignedRoles -> {
              if (assignedRoles.isEmpty()) {
                return Mono.empty();
              }
              return unassignRoles(userId, assignedRoles);
            })
        .onErrorResume(
            DownstreamApiProblemException.class,
            ex -> {
              Logger.errorLog(
                  xRequestId,
                  "Update assigned roles failed for userId",
                  userId,
                  ProblemApiDto.DownstreamSystemEnum.KEYCLOAK.toString());
              return Mono.error(ex);
            })
        .then(
            Mono.defer(
                () -> {
                  if (roles.isEmpty()) {
                    return Mono.empty();
                  }
                  return assignRoles(userId, roles);
                }))
        .then(Mono.defer(() -> getAssignedRoles(userId, xRequestId)));
  }

  public Mono<Void> unassignRoles(String userId, List<RoleApiDto> roles) {
    log.debug(
        "Unassign roles from user in keycloak. userId=`{}`, roleIds=`{}`",
        userId,
        roles.stream().map(RoleApiDto::getId).collect(Collectors.joining(", ")));

    return keycloakApi.deleteRealmRoleMappingsByUserId(
        userId, roles.stream().map(rolesMapper::convert).toList());
  }

  public Mono<String> sendActionEmail(
      String userId, java.util.List<RequiredActionsKeycloakDto> requiredActions) {
    log.debug(
        "Sending update actions email to user in keycloak. userId=`{}`, actions=`{}`",
        userId,
        requiredActions);
    return keycloakApi
        .executeActionsEmail(userId, null, null, null, requiredActions)
        .thenReturn(userId);
  }

  public Flux<RoleApiDto> listRoles(String xRequestId) {
    return keycloakApi
        .getRoles(null, null, null, null)
        .map(role -> conversionService.convert(role, RoleApiDto.class))
        .onErrorResume(
            DownstreamApiProblemException.class,
            ex -> {
              Logger.errorLog(xRequestId, "Get realm roles failed for ID", xRequestId, "KEYCLOAK");
              return Mono.error(ex);
            });
  }

  public Mono<RoleListResponseApiDto> getAssignedRoles(String userId, String xRequestId) {
    log.debug("Get assigned roles from keycloak. userId=`{}`", userId);

    return keycloakApi
        .getRealmRoleMappingsByUserId(userId)
        .map(role -> conversionService.convert(role, RoleApiDto.class))
        .collectList()
        .map(
            items -> {
              final RoleListResponseApiDto result = new RoleListResponseApiDto();
              result.setTotalCount(items.size()); // keycloak does not support pagination for roles
              result.setItems(items);
              return result;
            })
        .onErrorResume(
            DownstreamApiProblemException.class,
            ex -> {
              Logger.errorLog(
                  xRequestId,
                  "Get assigned roles failed for userId",
                  userId,
                  ProblemApiDto.DownstreamSystemEnum.KEYCLOAK.toString());
              return Mono.error(ex);
            });
  }

  public Mono<RoleListResponseApiDto> getAvailableRoles(String userId, String xRequestId) {
    log.debug("Get available roles from keycloak. userId=`{}`", userId);

    return keycloakApi
        .getAvailableRealmRoleMappingsByUserId(userId)
        .map(role -> conversionService.convert(role, RoleApiDto.class))
        .collectList()
        .map(
            items -> {
              final RoleListResponseApiDto result = new RoleListResponseApiDto();
              result.setTotalCount(items.size()); // keycloak does not support pagination for roles
              result.setItems(items);

              return result;
            })
        .onErrorResume(
            DownstreamApiProblemException.class,
            ex -> {
              Logger.errorLog(
                  xRequestId,
                  "Get available roles failed for userId",
                  userId,
                  ProblemApiDto.DownstreamSystemEnum.KEYCLOAK.toString());
              return Mono.error(ex);
            });
  }

  public Flux<UserResponseApiDto> listUsersByRole(String roleName, String xRequestId) {
    return keycloakApi
        .getUsersByRole(roleName, null, null)
        .log()
        .map(user -> conversionService.convert(user, UserResponseApiDto.class))
        .onErrorResume(
            DownstreamApiProblemException.class,
            ex -> {
              Logger.errorLog(
                  xRequestId, "Get users by realm role failed for ID", xRequestId, "KEYCLOAK");
              return Mono.error(ex);
            });
  }
}

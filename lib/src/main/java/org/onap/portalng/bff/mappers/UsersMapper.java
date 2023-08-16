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

package org.onap.portalng.bff.mappers;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.onap.portalng.bff.config.MapperSpringConfig;
import org.onap.portalng.bff.openapi.client_portal_keycloak.model.RequiredActionsKeycloakDto;
import org.onap.portalng.bff.openapi.client_portal_keycloak.model.UserKeycloakDto;
import org.onap.portalng.bff.openapi.server.model.CreateUserRequestApiDto;
import org.onap.portalng.bff.openapi.server.model.UpdateUserRequestApiDto;
import org.onap.portalng.bff.openapi.server.model.UserResponseApiDto;
import org.springframework.core.convert.converter.Converter;

@Mapper(config = MapperSpringConfig.class, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UsersMapper extends Converter<UserKeycloakDto, UserResponseApiDto> {

  UserResponseApiDto convert(UserKeycloakDto source);

  @Mapping(source = "roles", target = "realmRoles")
  UserResponseApiDto convert(UserKeycloakDto source, List<String> roles);

  @Mapping(source = "actions", target = "requiredActions")
  UserKeycloakDto convert(CreateUserRequestApiDto source, List<RequiredActionsKeycloakDto> actions);

  UserKeycloakDto convert(UpdateUserRequestApiDto source);
}

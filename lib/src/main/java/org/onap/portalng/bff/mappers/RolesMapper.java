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

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.onap.portalng.bff.config.MapperSpringConfig;
import org.onap.portalng.bff.openapi.client_keycloak.model.RoleKeycloakDto;
import org.onap.portalng.bff.openapi.server.model.RoleApiDto;
import org.springframework.core.convert.converter.Converter;

@Mapper(config = MapperSpringConfig.class, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RolesMapper extends Converter<RoleKeycloakDto, RoleApiDto> {
  RoleApiDto convert(RoleKeycloakDto source);

  RoleKeycloakDto convert(RoleApiDto source);
}

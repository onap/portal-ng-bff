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
import org.onap.portalng.bff.mappers.ActionsMapper;
import org.onap.portalng.bff.mappers.PreferencesMapper;
import org.onap.portalng.bff.mappers.RolesMapper;
import org.onap.portalng.bff.mappers.UsersMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.support.ConfigurableConversionService;
import org.springframework.core.convert.support.DefaultConversionService;

@SuppressWarnings("rawtypes")
@Configuration
public class ConversionServiceConfig {

  @Bean
  public ConfigurableConversionService conversionService(
      ActionsMapper actionsMapper,
      PreferencesMapper preferencesMapper,
      RolesMapper rolesMapper,
      UsersMapper usersMapper) {
    final List<Converter> converters =
        List.of(
            actionsMapper,
            preferencesMapper,
            preferencesMapper,
            actionsMapper,
            rolesMapper,
            usersMapper);

    final ConfigurableConversionService conversionService = new DefaultConversionService();
    converters.forEach(conversionService::addConverter);

    return conversionService;
  }
}

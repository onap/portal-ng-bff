/*
 *
 * Copyright (c) 2026. Deutsche Telekom AG
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

package org.onap.portalng.bff;

import java.io.IOException;
import java.util.Arrays;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * The test {@code application.yml} replaces the main one on the test classpath. Tests that must
 * exercise the shipped configuration register its keys from the main {@code application.yml}
 * through this class, so a regression there fails them.
 */
public final class MainApplicationProperties {

  private MainApplicationProperties() {}

  /**
   * Registers every key of the main {@code application.yml} that starts with one of the prefixes.
   */
  public static void register(DynamicPropertyRegistry registry, String... prefixes)
      throws IOException {
    for (PropertySource<?> source :
        new YamlPropertySourceLoader()
            .load(
                "main-application-yml",
                new FileSystemResource("src/main/resources/application.yml"))) {
      final EnumerablePropertySource<?> enumerable = (EnumerablePropertySource<?>) source;
      for (String name : enumerable.getPropertyNames()) {
        if (Arrays.stream(prefixes).anyMatch(name::startsWith)) {
          final String value = String.valueOf(enumerable.getProperty(name));
          registry.add(name, () -> value);
        }
      }
    }
  }
}

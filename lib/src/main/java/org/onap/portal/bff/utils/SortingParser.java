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

package org.onap.portal.bff.utils;

import io.vavr.collection.List;
import io.vavr.collection.Seq;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

public class SortingParser {
  private static final String DESC_PREFIX = "-";
  private static final String SEPARATOR = ",";

  private SortingParser() {}

  public static Seq<SortingParam> parse(String sort) {
    return List.of(sort.split(SEPARATOR))
        .filter(name -> !name.isEmpty() && !name.equals(DESC_PREFIX))
        .map(
            name -> {
              if (name.startsWith(DESC_PREFIX)) {
                return SortingParam.builder()
                    .name(name.substring(DESC_PREFIX.length()))
                    .isDescending(true)
                    .build();
              }
              return SortingParam.builder().name(name).isDescending(false).build();
            });
  }

  @Builder
  @Value
  public static class SortingParam {
    @NonNull String name;
    boolean isDescending;
  }
}

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

import java.util.Comparator;
import java.util.regex.Pattern;

public class VersionComparator implements Comparator<String> {
  private static final Pattern SEPARATOR_PATTERN = Pattern.compile("\\.");

  @Override
  public int compare(String version1, String version2) {
    final String[] parsedVersion1 = SEPARATOR_PATTERN.split(version1);
    final String[] parsedVersion2 = SEPARATOR_PATTERN.split(version2);
    final int maxLength = Math.max(parsedVersion1.length, parsedVersion2.length);

    for (int i = 0; i < maxLength; i++) {
      final Integer v1 = i < parsedVersion1.length ? Integer.parseInt(parsedVersion1[i]) : 0;
      final Integer v2 = i < parsedVersion2.length ? Integer.parseInt(parsedVersion2[i]) : 0;
      final int compare = v1.compareTo(v2);

      if (compare != 0) {
        return compare;
      }
    }

    return 0;
  }
}

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

import io.vavr.collection.Map;
import io.vavr.collection.Seq;
import io.vavr.control.Option;
import java.util.Comparator;

public class SortingChainResolver<T> {
  final Map<String, Comparator<T>> comparators;

  public SortingChainResolver(Map<String, Comparator<T>> comparators) {
    this.comparators = comparators;
  }

  public Option<Comparator<T>> resolve(Seq<SortingParser.SortingParam> sortingParams) {
    final Seq<Comparator<T>> resolvedComparators =
        sortingParams.flatMap(
            sortingParam ->
                comparators
                    .get(sortingParam.getName())
                    .map(
                        comparator -> {
                          if (sortingParam.isDescending()) {
                            return comparator.reversed();
                          }
                          return comparator;
                        }));

    if (resolvedComparators.isEmpty()) {
      return Option.none();
    }
    return Option.some(resolvedComparators.reduceLeft(Comparator::thenComparing));
  }
}

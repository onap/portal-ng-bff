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

import static org.assertj.core.api.Assertions.assertThat;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.Comparator;
import lombok.Data;
import lombok.NonNull;
import org.junit.jupiter.api.Test;

class SortingChainResolverTest {

  @Test
  void emptySortIsCorrectlyResolved() {
    final SortingChainResolver<DummyPerson> resolver =
        new SortingChainResolver<>(HashMap.of("age", Comparator.comparing(DummyPerson::getAge)));

    final Option<Comparator<DummyPerson>> comparatorOption =
        resolver.resolve(SortingParser.parse(""));
    assertThat(comparatorOption.isEmpty()).isTrue();
  }

  @Test
  void sortWithUnknownPropertyIsCorrectlyResolved() {
    final SortingChainResolver<DummyPerson> resolver =
        new SortingChainResolver<>(HashMap.of("age", Comparator.comparing(DummyPerson::getAge)));

    final Option<Comparator<DummyPerson>> comparatorOption =
        resolver.resolve(SortingParser.parse("unknown"));
    assertThat(comparatorOption.isEmpty()).isTrue();
  }

  @Test
  void sortWithSingleAscendingPropertyIsCorrectlyResolved() {
    final SortingChainResolver<DummyPerson> resolver =
        new SortingChainResolver<>(HashMap.of("age", Comparator.comparing(DummyPerson::getAge)));

    final Option<Comparator<DummyPerson>> comparatorOption =
        resolver.resolve(SortingParser.parse("age"));
    assertThat(comparatorOption.isDefined()).isTrue();

    final List<DummyPerson> list =
        List.of(new DummyPerson("Albert", 10), new DummyPerson("Bernard", 7));
    final List<DummyPerson> expectedList =
        List.of(new DummyPerson("Bernard", 7), new DummyPerson("Albert", 10));
    assertThat(list.sorted(comparatorOption.get())).containsExactlyElementsOf(expectedList);
  }

  @Test
  void sortWithSingleDescendingPropertyIsCorrectlyResolved() {
    final SortingChainResolver<DummyPerson> resolver =
        new SortingChainResolver<>(HashMap.of("age", Comparator.comparing(DummyPerson::getAge)));

    final Option<Comparator<DummyPerson>> comparatorOption =
        resolver.resolve(SortingParser.parse("-age"));
    assertThat(comparatorOption.isDefined()).isTrue();

    final List<DummyPerson> list =
        List.of(new DummyPerson("Charles", 23), new DummyPerson("Dominick", 31));
    final List<DummyPerson> expectedList =
        List.of(new DummyPerson("Dominick", 31), new DummyPerson("Charles", 23));
    assertThat(list.sorted(comparatorOption.get())).containsExactlyElementsOf(expectedList);
  }

  @Test
  void sortWithMultiplePropertiesIsCorrectlyResolved() {
    final SortingChainResolver<DummyPerson> resolver =
        new SortingChainResolver<>(
            HashMap.of("age", Comparator.comparing(DummyPerson::getAge))
                .put("name", Comparator.comparing(DummyPerson::getName)));

    final Option<Comparator<DummyPerson>> comparatorOption =
        resolver.resolve(SortingParser.parse("age,name"));
    assertThat(comparatorOption.isDefined()).isTrue();

    final List<DummyPerson> list =
        List.of(
            new DummyPerson("Harold", 27),
            new DummyPerson("Diego", 70),
            new DummyPerson("David", 27));
    final List<DummyPerson> expectedList =
        List.of(
            new DummyPerson("David", 27),
            new DummyPerson("Harold", 27),
            new DummyPerson("Diego", 70));
    assertThat(list.sorted(comparatorOption.get())).containsExactlyElementsOf(expectedList);
  }

  @Test
  void sortWithMultiplePropertiesInDifferentOrderIsCorrectlyResolved() {
    final SortingChainResolver<DummyPerson> resolver =
        new SortingChainResolver<>(
            HashMap.of("age", Comparator.comparing(DummyPerson::getAge))
                .put("name", Comparator.comparing(DummyPerson::getName)));

    final Option<Comparator<DummyPerson>> comparatorOption =
        resolver.resolve(SortingParser.parse("name,age"));
    assertThat(comparatorOption.isDefined()).isTrue();

    final List<DummyPerson> list =
        List.of(
            new DummyPerson("Harold", 27),
            new DummyPerson("Diego", 70),
            new DummyPerson("David", 27));
    final List<DummyPerson> expectedList =
        List.of(
            new DummyPerson("David", 27),
            new DummyPerson("Diego", 70),
            new DummyPerson("Harold", 27));
    assertThat(list.sorted(comparatorOption.get())).containsExactlyElementsOf(expectedList);
  }

  @Data
  private static class DummyPerson {
    @NonNull private final String name;
    private final int age;
  }
}

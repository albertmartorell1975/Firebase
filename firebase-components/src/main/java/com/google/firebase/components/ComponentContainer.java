// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.components;

import com.google.firebase.inject.Deferred;
import com.google.firebase.inject.Provider;
import java.util.Set;

/** Provides a means to retrieve instances of requested classes/interfaces. */
public interface ComponentContainer {
  default <T> T get(Class<T> anInterface) {
    return get(Qualified.unqualified(anInterface));
  }

  default <T> Provider<T> getProvider(Class<T> anInterface) {
    return getProvider(Qualified.unqualified(anInterface));
  }

  default <T> Deferred<T> getDeferred(Class<T> anInterface) {
    return getDeferred(Qualified.unqualified(anInterface));
  }

  default <T> Set<T> setOf(Class<T> anInterface) {
    return setOf(Qualified.unqualified(anInterface));
  }

  default <T> Provider<Set<T>> setOfProvider(Class<T> anInterface) {
    return setOfProvider(Qualified.unqualified(anInterface));
  }

  default <T> T get(Qualified<T> anInterface) {
    Provider<T> provider = getProvider(anInterface);
    if (provider == null) {
      return null;
    }
    return provider.get();
  }

  <T> Provider<T> getProvider(Qualified<T> anInterface);

  <T> Deferred<T> getDeferred(Qualified<T> anInterface);

  default <T> Set<T> setOf(Qualified<T> anInterface) {
    return setOfProvider(anInterface).get();
  }

  <T> Provider<Set<T>> setOfProvider(Qualified<T> anInterface);
}

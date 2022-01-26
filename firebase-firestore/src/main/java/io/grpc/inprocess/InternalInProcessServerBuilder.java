/*
 * Copyright 2020 The gRPC Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.grpc.inprocess;

import io.grpc.Internal;

/**
 * Internal {@link io.grpc.inprocess.InProcessServerBuilder} accessor. This is intended for usage
 * internal to the gRPC team. If you *really* think you need to use this, contact the gRPC team
 * first.
 */
@Internal
public class InternalInProcessServerBuilder {
  public static void setStatsEnabled(InProcessServerBuilder builder, boolean value) {
    builder.setStatsEnabled(value);
  }

  private InternalInProcessServerBuilder() {}
}

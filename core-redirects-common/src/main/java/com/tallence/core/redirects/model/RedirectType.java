/*
 * Copyright 2019 Tallence AG
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
package com.tallence.core.redirects.model;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * This enum represents the type of a redirect.
 * Redirects can be applied after a 404 error or always.
 */
public enum RedirectType {

  ALWAYS, AFTER_NOT_FOUND;

  private static final Map<String, RedirectType> LOOKUP = new HashMap<>(2);

  static {
    Arrays.stream(RedirectType.values()).forEach(redirectType -> LOOKUP.put(redirectType.name(), redirectType));
  }

  public static RedirectType asRedirectType(String type) {
    return LOOKUP.get(type);
  }

}

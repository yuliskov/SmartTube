/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package androidx.leanback.util;

import androidx.annotation.RestrictTo;

/**
 * Math Utilities for leanback library.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public final class MathUtil {

    private MathUtil() {
        // Prevent construction of this util class
    }

    /**
     * Convert long to int safely. Similar with Math.toIntExact() in Java 8.
     * @param numLong Number of type long to convert.
     * @return int version of input.
     * @throws ArithmeticException If input overflows int.
     */
    public static int safeLongToInt(long numLong) {
        if ((int) numLong != numLong) {
            throw new ArithmeticException("Input overflows int.\n");
        }
        return (int) numLong;
    }
}

/*
 * Copyright (C) 2014 The Android Open Source Project
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
package androidx.leanback.widget;

/**
 * Interface for highlighting the item that has focus.
 *
 */
public interface FocusHighlight {
    /**
     * No zoom factor.
     */
    public static final int ZOOM_FACTOR_NONE = 0;

    /**
     * A small zoom factor, recommended for large item views.
     */
    public static final int ZOOM_FACTOR_SMALL = 1;

    /**
     * A medium zoom factor, recommended for medium sized item views.
     */
    public static final int ZOOM_FACTOR_MEDIUM = 2;

    /**
     * A large zoom factor, recommended for small item views.
     */
    public static final int ZOOM_FACTOR_LARGE = 3;

    /**
     * An extra small zoom factor.
     */
    public static final int ZOOM_FACTOR_XSMALL = 4;
}

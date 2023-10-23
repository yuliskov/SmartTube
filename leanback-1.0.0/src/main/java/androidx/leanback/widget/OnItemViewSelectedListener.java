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
 * Interface for receiving notification when a row or item becomes selected. The concept of
 * current selection is different than focus.  A row or item can be selected without having focus;
 * for example, when a row header view gains focus then the corresponding row view becomes selected.
 * This interface expects row object to be sub class of {@link Row}.
 */
public interface OnItemViewSelectedListener extends BaseOnItemViewSelectedListener<Row> {
}

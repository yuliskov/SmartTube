/**
 * Copyright (C) 2015 The Android Open Source Project
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

/**
 * <p>Support classes for building Leanback user experiences.</p>
 * <p>
 * Many apps intended for a 10-foot, or 'Leanback', experience are centered around media and games.
 * Games tend to have custom user interfaces, but media applications may benefit from a common set of
 * user interface components that work well in a Leanback environment.  Following is an overview of
 * the Leanback Support Library.
 * </p>
 * <p>
 * Leanback provides a model-view-presenter approach to building applications:
 * <ul>
 * <li>The model is primarily provided by the application developer. Leanback imposes very few
 * restrictions on how this model is implemented: anything extending Object in Java is
 * supported.
 * </li>
 * <li>The view is handled by the existing {@link android.view} package. Developers
 * may continue to use their existing knowledge and experience to create visually compelling
 * applications with Leanback.
 * </li>
 * <li>The presenter is based on the existing Adapter concept in the Android framework, but has
 * been updated to add more flexibility and composability. In particular, the interface for
 * binding data to views has been separated from the adapter that traverses the data, allowing
 * presenters to be used in more places.  See {@link androidx.leanback.widget.Presenter}
 * for more details.
 * </li>
 * </ul>
 * <p>
 * Leanback contains a mixture of higher level building blocks such as Fragments in the
 * {@link androidx.leanback.app} package. Notable examples are the
 * {@link androidx.leanback.app.BrowseSupportFragment},
 * {@link androidx.leanback.app.DetailsSupportFragment},
 * {@link androidx.leanback.app.PlaybackSupportFragment} and the
 * {@link androidx.leanback.app.GuidedStepSupportFragment}.  Helper classes are also
 * provided that work with the leanback fragments, for example the
 * {@link androidx.leanback.media.PlaybackTransportControlGlue} and
 * {@link androidx.leanback.app.PlaybackSupportFragmentGlueHost}.
 * </p>
 * <p>
 * Many lower level building blocks are also provided in the {@link androidx.leanback.widget} package.
 * These allow applications to easily incorporate Leanback look and feel while allowing for a
 * high degree of customization.  Primary examples include the UI widget
 * {@link androidx.leanback.widget.HorizontalGridView} and
 * {@link androidx.leanback.widget.VerticalGridView}.
 */

package androidx.leanback;
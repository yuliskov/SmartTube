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
 * <p>Support classes providing high level Leanback user interface building blocks.</p>
 * <p>
 * Leanback fragments are available both as support fragments (subclassed from
 * {@link androidx.fragment.app.Fragment androidx.fragment.app.Fragment}) and as platform
 * fragments (subclassed from {@link android.app.Fragment android.app.Fragment}). A few of the most
 * commonly used leanback fragments are described here.
 * </p>
 * <p>
 * A {@link androidx.leanback.app.BrowseSupportFragment} by default operates in the "row"
 * mode. It includes an optional “fastlane”
 * navigation side panel and a list of rows, with one-to-one correspondance between each header
 * in the fastlane and a row.  The application supplies the
 * {@link androidx.leanback.widget.ObjectAdapter} containing the list of
 * rows and a {@link androidx.leanback.widget.PresenterSelector} of row presenters.
 * </p>
 * <p>
 * A {@link androidx.leanback.app.BrowseSupportFragment} also works in a "page" mode when
 * each row of fastlane is mapped to a fragment that the app registers in
 * {@link androidx.leanback.app.BrowseSupportFragment#getMainFragmentRegistry()}.
 * </p>
 * <p>
 * A {@link androidx.leanback.app.DetailsSupportFragment} will typically consist of a
 * large overview of an item at the top,
 * some actions that a user can perform, and possibly rows of additional or related items.
 * The content for this fragment is specified in the same way as for the BrowseSupportFragment, with
 * the convention that the first element in the ObjectAdapter corresponds to the overview row.
 * The {@link androidx.leanback.widget.DetailsOverviewRow} and
 * {@link androidx.leanback.widget.FullWidthDetailsOverviewRowPresenter} provide a
 * default template for this row.
 * </p>
 * <p>
 * A {@link androidx.leanback.app.PlaybackSupportFragment} or its subclass
 * {@link androidx.leanback.app.VideoSupportFragment} hosts
 * {@link androidx.leanback.media.PlaybackTransportControlGlue}
 * or {@link androidx.leanback.media.PlaybackBannerControlGlue} with a Leanback
 * look and feel.  It is recommended to use an instance of
 * {@link androidx.leanback.media.PlaybackTransportControlGlue}.
 * This helper implements a standard behavior for user interaction with
 * the most commonly used controls as well as video scrubbing.
 * </p>
 * <p>
 * A {@link androidx.leanback.app.SearchSupportFragment} allows the developer to accept a
 * query from a user and display the results
 * using the familiar list rows.
 * </p>
 * <p>
 * A {@link androidx.leanback.app.GuidedStepSupportFragment} is used to guide the user
 * through a decision or series of decisions.
 * </p>
 **/

package androidx.leanback.app;

/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.leanback.widget;

import android.view.View;

/**
 * Interface for a custom EditText subclass to support autofill in
 * {@link androidx.leanback.app.GuidedStepSupportFragment}.
 * <p>
 *
 * Apps who needs to supply custom layouts for {@link GuidedActionsStylist} with their own EditText
 * classes should implement this interface in order to support autofill in
 * {@link androidx.leanback.app.GuidedStepSupportFragment}. This ensures autofill event happened
 * within custom EditText is propagated to GuidedStepSupportFragment.
 * e.g.
 * <pre><code>
 * public class MyEditText extends EditText implements GuidedActionAutofillSupport {
 *     OnAutofillListener mAutofillViewListener;
 *     &#064;Override
 *     public void setOnAutofillListener(OnAutofillListener autofillViewListener) {
 *         mAutofillViewListener = autofillViewListener;
 *     }
 *
 *     &#064;Override
 *     public void autofill(AutofillValue values) {
 *         super.autofill(values);
 *         if (mAutofillViewListener != null) {
 *             mAutofillViewListener.onAutofill(this);
 *         }
 *     }
 *     // ...
 * }
 * </code></pre>
 *
 */
public interface GuidedActionAutofillSupport {

    /**
     * Listener for autofill event. Leanback will set the Listener on the custom view.
     */
    interface OnAutofillListener {

        /**
         * Custom view should call this method when autofill event happened.
         *
         * @param view The view where autofill happened.
         */
        void onAutofill(View view);

    }

    /**
     * Sets AutofillListener on the custom view.
     */
    void setOnAutofillListener(OnAutofillListener listener);
}

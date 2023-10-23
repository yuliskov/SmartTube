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

import android.view.KeyEvent;
import android.widget.EditText;

/**
 * Interface for an EditText subclass that can delegate calls to onKeyPreIme up to a registered
 * listener.
 * <p>
 * Used in editable actions within {@link androidx.leanback.app.GuidedStepFragment} to
 * allow for custom back key handling. Specifically, this is used to implement the behavior that
 * dismissing the IME also clears edit text focus. Clients who need to supply custom layouts for
 * {@link GuidedActionsStylist} with their own EditText classes should satisfy this interface in
 * order to inherit this behavior.
 */
public interface ImeKeyMonitor {

    /**
     * Listener interface for key events intercepted pre-IME by edit text objects.
     */
    public interface ImeKeyListener {
        /**
         * Callback invoked from EditText's onKeyPreIme method override. Returning true tells the
         * caller that the key event is handled and should not be propagated.
         */
        public abstract boolean onKeyPreIme(EditText editText, int keyCode, KeyEvent event);
    }

    /**
     * Set the listener for this edit text object. The listener's onKeyPreIme method will be
     * invoked from the host edit text's onKeyPreIme method.
     */
    public void setImeKeyListener(ImeKeyListener listener);
}

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
package androidx.leanback.widget;

import android.graphics.drawable.Drawable;

/**
 * An interface implemented by the user if they wish to provide actions for a media item row to
 * be displayed by an {@link AbstractMediaItemPresenter}.
 *
 * A media row consists of media item details together with a number of custom actions,
 * following the media item details. Classes implementing {@link MultiActionsProvider} can define
 * their own media data model within their derived classes.
 * <p>
 *     The actions are provided by overriding {@link MultiActionsProvider#getActions()}
 *     Provided actions should be instances of {@link MultiAction}.
 * </p>
 */
public interface MultiActionsProvider {

    /**
     * MultiAction represents an action that can have multiple states. {@link #getIndex()} returns
     * the current index within the drawables. Both list of drawables and index can be updated
     * dynamically in the program, and the UI could be updated by notifying the listeners
     * provided in {@link AbstractMediaItemPresenter.ViewHolder}.
     */
    public static class MultiAction {
        private long mId;
        private int mIndex;
        private Drawable[] mDrawables;

        public MultiAction(long id) {
            mId = id;
            mIndex = 0;
        }

        /**
         * Sets the drawables used for displaying different states within this {@link MultiAction}.
         * The size of drawables determines the set of states this action represents.
         * @param drawables Array of drawables for different MultiAction states.
         */
        public void setDrawables(Drawable[] drawables) {
            mDrawables = drawables;
            if (mIndex > drawables.length - 1) {
                mIndex = drawables.length - 1;
            }
        }

        /**
         * Returns the drawables used for displaying different states within this
         * {@link MultiAction}.
         * @return The drawables used for displaying different states within this
         *         {@link MultiAction}.
         */
        public Drawable[] getDrawables() {
            return mDrawables;
        }

        /**
         * Increments the index which this MultiAction currently represents. The index is wrapped
         * around to zero when the end is reached.
         */
        public void incrementIndex() {
            setIndex(mIndex < (mDrawables.length - 1) ? (mIndex + 1) : 0);
        }

        /**
         * Sets the index which this MultiAction currently represents.
         * @param index The current action index.
         */
        public void setIndex(int index) {
            mIndex = index;
        }

        /**
         * Returns the currently selected index in this MultiAction.
         * @return The currently selected index in this MultiAction.
         */
        public int getIndex() {
            return mIndex;
        }

        /**
         * @return The icon drawable for the current state of this MultiAction.
         */
        public Drawable getCurrentDrawable() {
            return mDrawables[mIndex];
        }

        /**
         * @return The id for this MultiAction.
         */
        public long getId() {
            return mId;
        }
    }

    /**
     * Should override this method in order to provide a custom set of actions for a media item row
     * @return Array of MultiAction items to be displayed for this media item row.
     */
    public MultiAction[] getActions();
}

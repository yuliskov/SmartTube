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

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.leanback.R;

/**
 * The presenter displaying a custom action in {@link AbstractMediaItemPresenter}.
 * This is the default presenter for actions in media rows if no action presenter is provided by the
 * user.
 *
 * Binds to items of type {@link MultiActionsProvider.MultiAction}.
 */
class MediaItemActionPresenter extends Presenter {

    MediaItemActionPresenter() {
    }

    static class ViewHolder extends Presenter.ViewHolder {
        final ImageView mIcon;

        public ViewHolder(View view) {
            super(view);
            mIcon = (ImageView) view.findViewById(R.id.actionIcon);
        }

        public ImageView getIcon() {
            return mIcon;
        }
    }

    @Override
    public Presenter.ViewHolder onCreateViewHolder(ViewGroup parent) {
        Context context = parent.getContext();
        View actionView = LayoutInflater.from(context)
                .inflate(R.layout.lb_row_media_item_action, parent, false);
        return new ViewHolder(actionView);
    }

    @Override
    public void onBindViewHolder(Presenter.ViewHolder viewHolder, Object item) {
        ViewHolder actionViewHolder = (ViewHolder) viewHolder;
        MultiActionsProvider.MultiAction action = (MultiActionsProvider.MultiAction) item;
        actionViewHolder.getIcon().setImageDrawable(action.getCurrentDrawable());
    }

    @Override
    public void onUnbindViewHolder(Presenter.ViewHolder viewHolder) {
    }
}

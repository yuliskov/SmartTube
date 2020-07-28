/*
 * Copyright (c) 2014 The Android Open Source Project
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

package com.example.android.tvleanback.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.leanback.app.GuidedStepSupportFragment;
import androidx.leanback.widget.GuidanceStylist;
import androidx.leanback.widget.GuidanceStylist.Guidance;
import androidx.leanback.widget.GuidedAction;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.example.android.tvleanback.R;

import java.util.List;

/**
 * Activity that showcases different aspects of GuidedStepFragments.
 */
public class GuidedStepActivity extends FragmentActivity {

    private static final int CONTINUE = 0;
    private static final int BACK = 1;
    private static final int OPTION_CHECK_SET_ID = 10;
    private static final String[] OPTION_NAMES = {
            "Option A",
            "Option B",
            "Option C"
    };
    private static final String[] OPTION_DESCRIPTIONS = {
            "Here's one thing you can do",
            "Here's another thing you can do",
            "Here's one more thing you can do"
    };
    private static final int[] OPTION_DRAWABLES = {R.drawable.ic_guidedstep_option_a,
            R.drawable.ic_guidedstep_option_b, R.drawable.ic_guidedstep_option_c};
    private static final boolean[] OPTION_CHECKED = {true, false, false};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (null == savedInstanceState) {
            GuidedStepSupportFragment.addAsRoot(this, new FirstStepFragment(), android.R.id.content);
        }
    }

    private static void addAction(
            Context context,
            List<GuidedAction> actions,
            long id,
            String title,
            String desc) {
        actions.add(new GuidedAction.Builder(context)
                .id(id)
                .title(title)
                .description(desc)
                .build());
    }

    private static void addCheckedAction(
            Context context,
            List<GuidedAction> actions,
            int iconResId,
            String title,
            String desc,
            boolean checked) {
        GuidedAction guidedAction = new GuidedAction.Builder(context)
                .title(title)
                .description(desc)
                .checkSetId(OPTION_CHECK_SET_ID)
                .icon(iconResId)
                .build();
        guidedAction.setChecked(checked);
        actions.add(guidedAction);
    }

    public static class FirstStepFragment extends GuidedStepSupportFragment {
        @Override
        public int onProvideTheme() {
            return R.style.Theme_Example_Leanback_GuidedStep_First;
        }

        @Override
        @NonNull
        public Guidance onCreateGuidance(@NonNull Bundle savedInstanceState) {
            String title = getString(R.string.guidedstep_first_title);
            String breadcrumb = getString(R.string.guidedstep_first_breadcrumb);
            String description = getString(R.string.guidedstep_first_description);
            Drawable icon = getActivity().getDrawable(R.drawable.ic_main_icon);
            return new Guidance(title, description, breadcrumb, icon);
        }

        @Override
        public void onCreateActions(@NonNull List<GuidedAction> actions,
                Bundle savedInstanceState) {
            addAction(getContext(),
                    actions,
                    CONTINUE,
                    getResources().getString(R.string.guidedstep_continue),
                    getResources().getString(R.string.guidedstep_letsdoit));
            addAction(getContext(),
                    actions,
                    BACK,
                    getResources().getString(R.string.guidedstep_cancel),
                    getResources().getString(R.string.guidedstep_nevermind));
        }

        @Override
        public void onGuidedActionClicked(GuidedAction action) {
            FragmentManager fm = getFragmentManager();
            if (action.getId() == CONTINUE) {
                GuidedStepSupportFragment.add(fm, new SecondStepFragment());
            } else {
                getActivity().finishAfterTransition();
            }
        }
    }

    public static class SecondStepFragment extends GuidedStepSupportFragment {

        @Override
        @NonNull
        public Guidance onCreateGuidance(Bundle savedInstanceState) {
            String title = getString(R.string.guidedstep_second_title);
            String breadcrumb = getString(R.string.guidedstep_second_breadcrumb);
            String description = getString(R.string.guidedstep_second_description);
            Drawable icon = getActivity().getDrawable(R.drawable.ic_main_icon);
            return new Guidance(title, description, breadcrumb, icon);
        }

        @Override
        public GuidanceStylist onCreateGuidanceStylist() {
            return new GuidanceStylist() {
                @Override
                public int onProvideLayoutId() {
                    return R.layout.guidedstep_second_guidance;
                }
            };
        }

        @Override
        public void onCreateActions(@NonNull List<GuidedAction> actions,
                Bundle savedInstanceState) {
            String desc = getResources().getString(R.string.guidedstep_action_description);
            actions.add(new GuidedAction.Builder()
                    .title(getResources().getString(R.string.guidedstep_action_title))
                    .description(desc)
                    .multilineDescription(true)
                    .infoOnly(true)
                    .enabled(false)
                    .build());
            for (int i = 0; i < OPTION_NAMES.length; i++) {
                addCheckedAction(getContext(),
                        actions,
                        OPTION_DRAWABLES[i],
                        OPTION_NAMES[i],
                        OPTION_DESCRIPTIONS[i],
                        OPTION_CHECKED[i]);
            }
        }

        @Override
        public void onGuidedActionClicked(GuidedAction action) {
            FragmentManager fm = getFragmentManager();
            ThirdStepFragment next = ThirdStepFragment.newInstance(getSelectedActionPosition() - 1);
            GuidedStepSupportFragment.add(fm, next);
        }

    }

    public static class ThirdStepFragment extends GuidedStepSupportFragment {
        private final static String ARG_OPTION_IDX = "arg.option.idx";

        public static ThirdStepFragment newInstance(final int option) {
            final ThirdStepFragment f = new ThirdStepFragment();
            final Bundle args = new Bundle();
            args.putInt(ARG_OPTION_IDX, option);
            f.setArguments(args);
            return f;
        }

        @Override
        @NonNull
        public Guidance onCreateGuidance(Bundle savedInstanceState) {
            String title = getString(R.string.guidedstep_third_title);
            String breadcrumb = getString(R.string.guidedstep_third_breadcrumb);
            String description = getString(R.string.guidedstep_third_command)
                    + OPTION_NAMES[getArguments().getInt(ARG_OPTION_IDX)];
            Drawable icon = getActivity().getDrawable(R.drawable.ic_main_icon);
            return new Guidance(title, description, breadcrumb, icon);
        }

        @Override
        public void onCreateActions(@NonNull List<GuidedAction> actions,
                Bundle savedInstanceState) {
            addAction(getContext(), actions, CONTINUE, "Done", "All finished");
            addAction(getContext(), actions, BACK, "Back", "Forgot something...");
        }

        @Override
        public void onGuidedActionClicked(GuidedAction action) {
            if (action.getId() == CONTINUE) {
                getActivity().finishAfterTransition();
            } else {
                getFragmentManager().popBackStack();
            }
        }
    }
}

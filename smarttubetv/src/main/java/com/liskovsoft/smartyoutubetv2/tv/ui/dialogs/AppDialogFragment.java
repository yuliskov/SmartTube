package com.liskovsoft.smartyoutubetv2.tv.ui.dialogs;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.preference.LeanbackPreferenceFragment;
import androidx.leanback.preference.LeanbackSettingsFragment;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragment;
import androidx.preference.PreferenceScreen;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.views.AppDialogView;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.base.LeanbackListPreferenceDialogFragmentBase;
import com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.other.ChatPreference;
import com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.other.ChatPreferenceDialogFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.other.CommentsPreference;
import com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.other.CommentsPreferenceDialogFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.other.RadioListPreferenceDialogFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.other.StringListPreference;
import com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.other.StringListPreferenceDialogFragment;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

import java.util.List;

public class AppDialogFragment extends LeanbackSettingsFragment implements AppDialogView {
    private static final String TAG = AppDialogFragment.class.getSimpleName();
    private AppDialogPresenter mPresenter;
    private AppPreferenceManager mManager;
    private boolean mIsTransparent;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPresenter = AppDialogPresenter.instance(getActivity());
        mPresenter.setView(this);
        mIsTransparent = mPresenter.isTransparent();
        mManager = new AppPreferenceManager(getActivity());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        mPresenter.onViewDestroyed();
    }

    @Override
    public void onPreferenceStartInitialScreen() {
        // FIX: Can not perform this action after onSaveInstanceState
        // Possible fix: Unable to add window -- token android.os.BinderProxy is not valid; is your activity running?
        if (!Utils.checkActivity(getActivity())) {
            return;
        }

        try {
            // Fix mPresenter in null after init stage.
            // Seems concurrency between dialogs.
            mPresenter.setView(this);

            mPresenter.onViewInitialized();
        } catch (IllegalStateException e) {
            // NOP
        }
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragment preferenceFragment, Preference preference) {
        // Contains only child fragments.
        return false;
    }

    @Override
    public boolean onPreferenceStartScreen(PreferenceFragment preferenceFragment, PreferenceScreen preferenceScreen) {
        // Contains only child fragments.
        return false;
    }

    private AppPreferenceFragment buildPreferenceFragment(List<OptionCategory> categories, String title) {
        AppPreferenceFragment fragment = new AppPreferenceFragment();
        fragment.setCategories(categories);
        fragment.setTitle(title);
        fragment.enableTransparent(mIsTransparent);
        return fragment;
    }

    @Override
    public void show(List<OptionCategory> categories, String title) {
        AppPreferenceFragment fragment = buildPreferenceFragment(categories, title);

        if (fragment.isSkipBackStack()) {
            onPreferenceDisplayDialog(fragment, mManager.createPreference(categories.get(0)));
        } else {
            startPreferenceFragment(fragment);
        }
    }

    @Override
    public boolean onPreferenceDisplayDialog(@NonNull PreferenceFragment caller, @NonNull Preference pref) {
        // Fix: IllegalStateException: Activity has been destroyed
        // Possible fix: Unable to add window -- token android.os.BinderProxy is not valid; is your activity running?
        if (!Utils.checkActivity(getActivity())) {
            return false;
        }

        if (pref instanceof StringListPreference) {
            StringListPreference listPreference = (StringListPreference) pref;
            StringListPreferenceDialogFragment f = StringListPreferenceDialogFragment.newInstanceStringList(listPreference.getKey());
            f.enableTransparent(mIsTransparent);
            f.setTargetFragment(caller, 0);
            f.setPreference(pref);
            startPreferenceFragment(f);

            return true;
        } else if (pref instanceof ListPreference) {
            ListPreference listPreference = (ListPreference) pref;
            RadioListPreferenceDialogFragment f = RadioListPreferenceDialogFragment.newInstanceSingle(listPreference.getKey());
            f.enableTransparent(mIsTransparent);
            f.setTargetFragment(caller, 0);
            f.setPreference(pref);
            startPreferenceFragment(f);

            return true;
        } else if (pref instanceof ChatPreference) {
            ChatPreference chatPreference = (ChatPreference) pref;
            ChatPreferenceDialogFragment f = ChatPreferenceDialogFragment.newInstance(chatPreference.getChatReceiver(), chatPreference.getKey());
            f.enableTransparent(mIsTransparent);
            f.setTargetFragment(caller, 0);
            f.setPreference(pref);
            startPreferenceFragment(f);

            return true;
        } else if (pref instanceof CommentsPreference) {
            CommentsPreference commentsPreference = (CommentsPreference) pref;
            CommentsPreferenceDialogFragment f = CommentsPreferenceDialogFragment.newInstance(commentsPreference.getCommentsReceiver(), commentsPreference.getKey());
            f.enableTransparent(mIsTransparent);
            f.setTargetFragment(caller, 0);
            f.setPreference(pref);
            startPreferenceFragment(f);

            return true;
        } else if (pref instanceof MultiSelectListPreference) {
            MultiSelectListPreference listPreference = (MultiSelectListPreference) pref;
            LeanbackListPreferenceDialogFragmentBase f = LeanbackListPreferenceDialogFragmentBase.newInstanceMulti(listPreference.getKey());
            f.setTargetFragment(caller, 0);
            f.setPreference(pref);
            startPreferenceFragment(f);
        }
        // TODO
        // else if (pref instanceof EditTextPreference) {
        //
        //        }
        else {
            return false;
        }

        // NOTE: Transparent CheckedList should be placed here (just in case you'll need it).

        //return super.onPreferenceDisplayDialog(caller, pref);
        return true;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);

        if (mIsTransparent && view != null) {
            // Enable transparent background (this is the only place to do it)
            ViewUtil.enableTransparentDialog(getActivity(), view);
        }

        return view;
    }

    @Override
    public void finish() {
        if (getActivity() != null) {
            getActivity().finish();
        }
    }

    @Override
    public void goBack() {
        if (getChildFragmentManager() != null) {
            getChildFragmentManager().popBackStack();
        }
    }

    @Override
    public boolean isShown() {
        return isVisible() && getUserVisibleHint();
    }

    public void onFinish() {
        mPresenter.onFinish();
    }
    
    public static class AppPreferenceFragment extends LeanbackPreferenceFragment {
        private static final String TAG = AppPreferenceFragment.class.getSimpleName();
        private List<OptionCategory> mCategories;
        private Context mExtractedContext;
        private AppPreferenceManager mManager;
        private String mTitle;
        private boolean mIsTransparent;
        private boolean mSkipBackStack;

        @Override
        public void onCreatePreferences(Bundle bundle, String s) {
            // Note, place in field with different name to avoid field overlapping
            mExtractedContext = (Context) Helpers.getField(this, "mStyledContext");
            // Note, don't use external manager (probable focus lost and visual bugs)
            mManager = new AppPreferenceManager(mExtractedContext);

            initPrefs();

            Log.d(TAG, "onCreatePreferences");
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = super.onCreateView(inflater, container, savedInstanceState);

            if (mIsTransparent && view != null) {
                ViewUtil.enableTransparentDialog(getActivity(), view);
            }

            return view;
        }

        private void initPrefs() {
            PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(mExtractedContext);
            setPreferenceScreen(screen);

            screen.setTitle(mTitle);

            addPreferences(screen);
        }

        private void addPreferences(PreferenceScreen screen) {
            if (mCategories != null) {
                for (OptionCategory category : mCategories) {
                    if (category.items != null) {
                        screen.addPreference(mManager.createPreference(category));
                    }
                }
            }
        }

        @Override
        public void onDisplayPreferenceDialog(Preference preference) {
            super.onDisplayPreferenceDialog(preference);
        }

        @Override
        public boolean onPreferenceTreeClick(Preference preference) {
            return super.onPreferenceTreeClick(preference);
        }

        public void setCategories(List<OptionCategory> categories) {
            mCategories = categories;
            mSkipBackStack = categories != null && categories.size() == 1;
        }

        public void setTitle(String title) {
            mTitle = title;
        }

        public void enableTransparent(boolean enable) {
            mIsTransparent = enable;
        }

        public boolean isSkipBackStack() {
            return mSkipBackStack;
        }
    }
}
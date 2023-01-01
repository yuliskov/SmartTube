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
import androidx.preference.DialogPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragment;
import androidx.preference.PreferenceScreen;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.views.AppDialogView;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.other.ChatPreference;
import com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.other.ChatPreferenceDialogFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.other.CommentsPreference;
import com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.other.CommentsPreferenceDialogFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.other.RadioListPreferenceDialogFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.other.StringListPreference;
import com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.other.StringListPreferenceDialogFragment;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

import java.util.List;

public class AppDialogFragment extends LeanbackSettingsFragment
        implements DialogPreference.TargetFragment, AppDialogView {
    private static final String TAG = AppDialogFragment.class.getSimpleName();
    private AppPreferenceFragment mPreferenceFragment;
    private AppDialogPresenter mSettingsPresenter;
    private boolean mIsTransparent;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSettingsPresenter = AppDialogPresenter.instance(getActivity());
        mSettingsPresenter.setView(this);
        mIsTransparent = mSettingsPresenter.isTransparent();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        mSettingsPresenter.onViewDestroyed();
    }

    @Override
    public void onPreferenceStartInitialScreen() {
        // FIX: Can not perform this action after onSaveInstanceState
        // Possible fix: Unable to add window -- token android.os.BinderProxy is not valid; is your activity running?
        if (!Utils.checkActivity(getActivity())) {
            return;
        }

        try {
            // Fix mSettingsPresenter in null after init stage.
            // Seems concurrency between dialogs.
            mSettingsPresenter.setView(this);

            mPreferenceFragment = buildPreferenceFragment();
            mPreferenceFragment.enableTransparent(mIsTransparent);
            startPreferenceFragment(mPreferenceFragment);

            mSettingsPresenter.onViewInitialized();
        } catch (IllegalStateException e) {
            // NOP
        }
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragment preferenceFragment, Preference preference) {
        return false;
    }

    @Override
    public boolean onPreferenceStartScreen(PreferenceFragment preferenceFragment, PreferenceScreen preferenceScreen) {
        PreferenceFragment frag = buildPreferenceFragment();
        startPreferenceFragment(frag);
        return true;
    }

    @Override
    public Preference findPreference(CharSequence charSequence) {
        return mPreferenceFragment.findPreference(charSequence);
    }

    private AppPreferenceFragment buildPreferenceFragment() {
        return new AppPreferenceFragment();
    }

    @Override
    public void setTitle(String title) {
        if (mPreferenceFragment != null) {
            mPreferenceFragment.setTitle(title);
        }
    }

    @Override
    public void addCategories(List<OptionCategory> categories) {
        if (mPreferenceFragment != null) {
            mPreferenceFragment.addCategories(categories);
        }
    }

    @Override
    public void clear() {
        if (getActivity() != null) {
            getActivity().runOnUiThread(this::onPreferenceStartInitialScreen);
        }
    }

    @Override
    public boolean onPreferenceDisplayDialog(@NonNull PreferenceFragment caller, Preference pref) {
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
            startPreferenceFragment(f);

            return true;
        } else if (pref instanceof ListPreference) {
            ListPreference listPreference = (ListPreference) pref;
            RadioListPreferenceDialogFragment f = RadioListPreferenceDialogFragment.newInstanceSingle(listPreference.getKey());
            f.enableTransparent(mIsTransparent);
            f.setTargetFragment(caller, 0);
            startPreferenceFragment(f);

            return true;
        } else if (pref instanceof ChatPreference) {
            ChatPreference chatPreference = (ChatPreference) pref;
            ChatPreferenceDialogFragment f = ChatPreferenceDialogFragment.newInstance(chatPreference.getChatReceiver(), chatPreference.getKey());
            f.enableTransparent(mIsTransparent);
            f.setTargetFragment(caller, 0);
            startPreferenceFragment(f);

            return true;
        } else if (pref instanceof CommentsPreference) {
            CommentsPreference commentsPreference = (CommentsPreference) pref;
            CommentsPreferenceDialogFragment f = CommentsPreferenceDialogFragment.newInstance(commentsPreference.getCommentsReceiver(), commentsPreference.getKey());
            f.enableTransparent(mIsTransparent);
            f.setTargetFragment(caller, 0);
            startPreferenceFragment(f);

            return true;
        }

        // NOTE: Transparent CheckedList should be placed here (just in case you'll need it).

        return super.onPreferenceDisplayDialog(caller, pref);
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
        if (mPreferenceFragment != null) {
            mPreferenceFragment.goBack();
        }
    }

    @Override
    public boolean isShown() {
        return isVisible() && getUserVisibleHint();
    }

    public void onFinish() {
        mSettingsPresenter.onFinish();
    }
    
    public static class AppPreferenceFragment extends LeanbackPreferenceFragment {
        private static final String TAG = AppPreferenceFragment.class.getSimpleName();
        private List<OptionCategory> mCategories;
        private Context mExtractedContext;
        private AppDialogFragmentManager mManager;
        private String mTitle;
        private int mBackStackCount;
        private boolean mIsTransparent;

        @Override
        public void onCreatePreferences(Bundle bundle, String s) {
            // Note, place in field with different name to avoid field overlapping
            mExtractedContext = (Context) Helpers.getField(this, "mStyledContext");
            mManager = new AppDialogFragmentManager(mExtractedContext);

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

            addCategories(screen);

            setSingleCategoryAsRoot(screen);
        }

        private void addCategories(PreferenceScreen screen) {
            if (mCategories != null) {
                for (OptionCategory category : mCategories) {
                    if (category.items != null) {
                        screen.addPreference(mManager.createPreference(category));
                    }
                }
            }
        }
        
        private void setSingleCategoryAsRoot(PreferenceScreen screen) {
            // Possible fix: java.lang.IllegalStateException Activity has been destroyed
            if (!Utils.checkActivity(getActivity())) {
                return;
            }

            // auto expand single list preference
            if (mCategories != null && mCategories.size() == 1 && screen.getPreferenceCount() > 0) {
                Preference preference = screen.getPreference(0);

                if (preference instanceof DialogPreference) {
                    onDisplayPreferenceDialog(preference);

                    if (getFragmentManager() != null) {
                        mBackStackCount = 0;

                        getFragmentManager().addOnBackStackChangedListener(this::onBackPressed);
                    }
                }

                // NOTE: we should avoid open simple buttons because we don't know what is hidden behind them: new dialog on action
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

        public void addCategories(List<OptionCategory> categories) {
            mCategories = categories;
        }

        public void setTitle(String title) {
            mTitle = title;
        }

        public void enableTransparent(boolean enable) {
            mIsTransparent = enable;
        }

        public void goBack() {
            if (getFragmentManager() != null) {
                getFragmentManager().popBackStack();
            }
        }

        private void onBackPressed() {
            if (getFragmentManager() != null) {
                int currentBackStackCount = getFragmentManager().getBackStackEntryCount();

                if (currentBackStackCount < mBackStackCount) {
                    if (currentBackStackCount == 0) {
                        // single dialog
                        getActivity().finish();
                    } else {
                        // multiple stacked dialogs
                        getFragmentManager().popBackStack();
                    }
                }

                mBackStackCount = currentBackStackCount;
            }
        }
    }
}
package com.liskovsoft.smartyoutubetv2.tv.ui.channel;

import android.os.Bundle;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.VideoGroupPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelView;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.presenter.ChannelHeaderPresenter.ChannelHeaderCallback;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.video.MultipleRowsFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.misc.ProgressBarManager;
import com.liskovsoft.googlecommon.common.helpers.YouTubeHelper;

public class ChannelFragment extends MultipleRowsFragment implements ChannelView {
    private static final String TAG = ChannelFragment.class.getSimpleName();
    private static final String SELECTED_ITEM_INDEX = "SelectedItemIndex";
    private ChannelPresenter mChannelPresenter;
    private ProgressBarManager mProgressBarManager;
    private boolean mIsFragmentCreated;
    private int mRestoredItemIndex = -1;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(null); // Real restore takes place in the presenter
        
        mRestoredItemIndex = savedInstanceState != null ? savedInstanceState.getInt(SELECTED_ITEM_INDEX, -1) : -1;
        mIsFragmentCreated = true;
        mChannelPresenter = ChannelPresenter.instance(getContext());
        mChannelPresenter.setView(this);

        mProgressBarManager = new ProgressBarManager();
        if (MainUIData.instance(getContext()).isChannelSearchBarEnabled()) {
            addHeader(new ChannelHeaderCallback() {
                @Override
                public void onSearchSettingsClicked() {
                    mChannelPresenter.onSearchSettingsClicked();
                }

                @Override
                public boolean onSearchSubmit(String query) {
                    return mChannelPresenter.onSearchSubmit(query);
                }

                @Override
                public String getChannelTitle() {
                    if (mChannelPresenter.getChannel() == null) {
                        return Helpers.startsWith(mChannelPresenter.getChannelId(), "@") ? mChannelPresenter.getChannelId() : null;
                    }

                    String author = mChannelPresenter.getChannel().getAuthor();
                    String title = mChannelPresenter.getChannel().getTitle();
                    String subs = mChannelPresenter.getChannel().subscriberCount;

                    return Helpers.toString(YouTubeHelper.createInfo(Helpers.firstNonNull(author, title), subs));
                }
            });
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        // Not robust. Because tab content often changed after reloading.
        outState.putInt(SELECTED_ITEM_INDEX, getPosition());
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Don't move to onCreateView
        mProgressBarManager.setRootView((ViewGroup) getActivity().findViewById(android.R.id.content).getRootView());

        mChannelPresenter.onViewInitialized();

        // Restore state after crash
        setPosition(mRestoredItemIndex);
        mRestoredItemIndex = -1;
    }

    @Override
    protected VideoGroupPresenter getMainPresenter() {
        return ChannelPresenter.instance(getContext());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mChannelPresenter.onViewDestroyed();
    }

    public void onFinish() {
        mChannelPresenter.onFinish();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (!mIsFragmentCreated) {
            mChannelPresenter.onViewResumed();
        }

        mIsFragmentCreated = false;
    }

    @Override
    public void showProgressBar(boolean show) {
        if (show) {
            mProgressBarManager.show();
        } else {
            mProgressBarManager.hide();
        }
    }
}

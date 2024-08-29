package com.liskovsoft.smartyoutubetv2.common.app.presenters.browse;

import android.annotation.SuppressLint;
import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.yt.data.Account;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.ErrorFragmentData;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.browse.BaseBrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.browse.PocketTubePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.browse.SectionsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.SectionPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.VideoGroupPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager.AccountChangeListener;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class BrowsePresenter extends BasePresenter<BrowseView> implements SectionPresenter, VideoGroupPresenter, AccountChangeListener {
    private static final String TAG = BrowsePresenter.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    private static BrowsePresenter sInstance;
    private final List<BaseBrowsePresenter> mListeners = new CopyOnWriteArrayList<>();
    private Video mCurrentVideo;

    private BrowsePresenter(Context context) {
        super(context);

        mListeners.add(new SectionsPresenter(this));
        mListeners.add(new PocketTubePresenter(this));
    }

    public static BrowsePresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new BrowsePresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    public static void unhold() {
        sInstance = null;
    }

    @Override
    public void onViewInitialized() {
        super.onViewInitialized();

        Utils.process(mListeners, BaseBrowsePresenter::onViewInitialized);
    }

    @Override
    public void onViewPaused() {
        super.onViewPaused();

        Utils.process(mListeners, BaseBrowsePresenter::onViewPaused);
    }

    public void updateSections() {
        if (getView() == null) {
            return;
        }

        Utils.process(mListeners, BaseBrowsePresenter::updateSections);
    }

    public void updateChannelSorting() {
        Utils.process(mListeners, BaseBrowsePresenter::updateChannelSorting);
    }

    public void updatePlaylistsStyle() {
        Utils.process(mListeners, BaseBrowsePresenter::updatePlaylistsStyle);
    }

    @Override
    public void onViewDestroyed() {
        super.onViewDestroyed();

        Utils.process(mListeners, BaseBrowsePresenter::onViewDestroyed);
    }

    @Override
    public void onVideoItemSelected(Video item) {
        if (getView() == null) {
            return;
        }

        mCurrentVideo = item;

        Utils.process(mListeners, listener -> listener.onVideoItemSelected(item));
    }

    @Override
    public void onVideoItemClicked(Video item) {
        if (getContext() == null) {
            return;
        }

        Utils.process(mListeners, listener -> listener.onVideoItemClicked(item));
    }

    @Override
    public void onVideoItemLongClicked(Video item) {
        if (getContext() == null) {
            return;
        }

        Utils.process(mListeners, listener -> listener.onVideoItemLongClicked(item));
    }

    @Override
    public void onScrollEnd(Video item) {
        if (item == null) {
            Log.e(TAG, "Can't scroll. Video is null.");
            return;
        }

        Utils.process(mListeners, listener -> listener.onScrollEnd(item));
    }

    @Override
    public void onSectionFocused(int sectionId) {
        mCurrentVideo = null; // fast scroll through the sections (fix empty selected item)

        Utils.process(mListeners, listener -> listener.onSectionFocused(sectionId));
    }

    @Override
    public void onSectionLongPressed(int sectionId) {
        Utils.process(mListeners, listener -> listener.onSectionLongPressed(sectionId));
    }

    @Override
    public boolean hasPendingActions() {
        return Utils.chainProcess(mListeners, BaseBrowsePresenter::hasPendingActions);
    }

    public boolean isItemPinned(Video item) {
        return Utils.chainProcess(mListeners, listener -> listener.isItemPinned(item));
    }

    public void moveSectionUp(BrowseSection section) {
        Utils.process(mListeners, listener -> listener.moveSectionUp(section));
    }

    public void moveSectionDown(BrowseSection section) {
        Utils.process(mListeners, listener -> listener.moveSectionDown(section));
    }

    public void renameSection(BrowseSection section) {
        Utils.process(mListeners, listener -> listener.renameSection(section));
    }

    public void enableAllSections(boolean enable) {
        Utils.process(mListeners, listener -> listener.enableAllSections(enable));
    }

    public void enableSection(int sectionId, boolean enable) {
        Utils.process(mListeners, listener -> listener.enableSection(sectionId, enable));
    }

    public void pinItem(Video item) {
        if (getView() == null) {
            return;
        }

        Utils.process(mListeners, listener -> listener.pinItem(item));
    }

    public void pinItem(String title, int resId, ErrorFragmentData data) {
        if (getView() == null) {
            return;
        }

        Utils.process(mListeners, listener -> listener.pinItem(title, resId, data));
    }

    public void unpinItem(Video item) {
        Utils.process(mListeners, listener -> listener.unpinItem(item));
    }

    public void refresh() {
        Utils.process(mListeners, BaseBrowsePresenter::refresh);
    }

    public void refresh(boolean focusOnContent) {
        Utils.process(mListeners, listener -> listener.refresh(focusOnContent));
    }

    /**
     * Is Channels new look enabled?
     */
    public boolean isMultiGridChannelUploadsSection() {
        return Utils.chainProcess(mListeners, BaseBrowsePresenter::isMultiGridChannelUploadsSection);
    }

    public boolean isSettingsSection() {
        return Utils.chainProcess(mListeners, BaseBrowsePresenter::isSettingsSection);
    }

    public boolean isPlaylistsSection() {
        return Utils.chainProcess(mListeners, BaseBrowsePresenter::isPlaylistsSection);
    }

    public boolean isHomeSection() {
        return Utils.chainProcess(mListeners, BaseBrowsePresenter::isHomeSection);
    }

    public boolean isHistorySection() {
        return Utils.chainProcess(mListeners, BaseBrowsePresenter::isHistorySection);
    }

    public boolean isSubscriptionsSection() {
        return Utils.chainProcess(mListeners, BaseBrowsePresenter::isSubscriptionsSection);
    }

    public boolean isPinnedSection() {
        return Utils.chainProcess(mListeners, BaseBrowsePresenter::isPinnedSection);
    }

    public void selectSection(int sectionId) {
        Utils.process(mListeners, listener -> listener.selectSection(sectionId));
    }

    public boolean inForeground() {
        return Utils.chainProcess(mListeners, BaseBrowsePresenter::inForeground);
    }

    //private boolean isGridSection() {
    //    return mCurrentSection != null && mCurrentSection.getType() != BrowseSection.TYPE_ROW;
    //}

    @Override
    public void onAccountChanged(Account account) {
        Utils.process(mListeners, listener -> listener.onAccountChanged(account));
    }

    public Video getCurrentVideo() {
        return mCurrentVideo;
    }
}

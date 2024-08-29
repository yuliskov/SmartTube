package com.liskovsoft.smartyoutubetv2.common.app.presenters.browse;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.yt.data.Account;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.ErrorFragmentData;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.browse.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.SectionPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.VideoGroupPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager.AccountChangeListener;

public abstract class BaseBrowsePresenter implements SectionPresenter, VideoGroupPresenter, AccountChangeListener {
    private final BrowsePresenter mBrowsePresenter;

    public BaseBrowsePresenter(BrowsePresenter root) {
        mBrowsePresenter = root;
    }

    public void updateSections() {}
    public void updateChannelSorting() {}
    public void updatePlaylistsStyle() {}
    public boolean isItemPinned(Video item) { return false; }
    public void moveSectionUp(BrowseSection section) {}
    public void moveSectionDown(BrowseSection section) {}
    public void renameSection(BrowseSection section) {}
    public void enableAllSections(boolean enable) {}
    public void enableSection(int sectionId, boolean enable) {}
    public void pinItem(Video item) {}
    public void pinItem(String title, int resId, ErrorFragmentData data) {}
    public void unpinItem(Video item) {}
    public void refresh() {}
    public void refresh(boolean focusOnContent) {}
    public boolean isMultiGridChannelUploadsSection() { return false; }
    public boolean isSettingsSection() { return false; }
    public boolean isPlaylistsSection() { return false; }
    public boolean isHomeSection() { return false; }
    public boolean isHistorySection() { return false; }
    public boolean isSubscriptionsSection() { return false; }
    public boolean isPinnedSection() { return false; }
    public void selectSection(int sectionId) {}
    public boolean inForeground() { return false; }

    public void onSectionFocused(int sectionId) {}
    public void onSectionLongPressed(int sectionId) {}

    public void onVideoItemSelected(Video item) {}
    public void onVideoItemClicked(Video item) {}
    public void onVideoItemLongClicked(Video item) {}
    public void onScrollEnd(Video item) {}
    public boolean hasPendingActions() { return false; }

    public void onAccountChanged(Account account) {}

    // BasePresenter

    final public Video getCurrentVideo() { return getRoot().getCurrentVideo(); }

    final public BrowsePresenter getRoot() {
        return mBrowsePresenter;
    }

    public void onViewInitialized() {}

    public void onViewPaused() {}

    public void onViewDestroyed() {}

    final public Context getContext() {
        return getRoot().getContext();
    }

    final public void syncItem(Video video) {
        getRoot().syncItem(video);
    }

    final public BrowseView getView() {
        return getRoot().getView();
    }

    final public void removeItem(Video item) {
        getRoot().removeItem(item);
    }

    final public void removeItemAuthor(Video item) {
        getRoot().removeItemAuthor(item);
    }
}

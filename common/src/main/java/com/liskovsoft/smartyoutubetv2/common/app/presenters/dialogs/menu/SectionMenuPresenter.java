package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu;

import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.MediaItemManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AccountSelectionPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SplashView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;

public class SectionMenuPresenter extends BasePresenter<Void> {
    private static final String TAG = SectionMenuPresenter.class.getSimpleName();
    private final MediaItemManager mItemManager;
    private final AppDialogPresenter mSettingsPresenter;
    private final MediaServiceManager mServiceManager;
    private Video mVideo;
    private BrowseSection mSection;
    private boolean mIsUnpinFromSidebarEnabled;
    private boolean mIsUnpinSectionFromSidebarEnabled;
    private boolean mIsReturnToBackgroundVideoEnabled;
    private boolean mIsAccountSelectionEnabled;
    private boolean mIsRefreshEnabled;

    private SectionMenuPresenter(Context context) {
        super(context);
        MediaService service = YouTubeMediaService.instance();
        mItemManager = service.getMediaItemManager();
        mServiceManager = MediaServiceManager.instance();
        mSettingsPresenter = AppDialogPresenter.instance(context);
    }

    public static SectionMenuPresenter instance(Context context) {
        return new SectionMenuPresenter(context);
    }

    public void showMenu(BrowseSection section) {
        mIsReturnToBackgroundVideoEnabled = true;
        mIsUnpinFromSidebarEnabled = true;
        mIsUnpinSectionFromSidebarEnabled = true;
        mIsAccountSelectionEnabled = true;
        mIsRefreshEnabled = true;

        showMenuInt(section);
    }

    private void showMenuInt(BrowseSection section) {
        if (section == null) {
            return;
        }

        updateEnabledMenuItems();

        disposeActions();

        mSection = section;
        mVideo = section.getData();

        MediaServiceManager.instance().authCheck(this::obtainPlaylistsAndShowDialogSigned, this::prepareAndShowDialogUnsigned);
    }

    private void obtainPlaylistsAndShowDialogSigned() {
        prepareAndShowDialogSigned();
    }

    private void prepareAndShowDialogSigned() {
        if (getContext() == null) {
            return;
        }

        mSettingsPresenter.clear();

        appendReturnToBackgroundVideoButton();
        appendRefreshButton();
        appendUnpinFromSidebarButton();
        appendUnpinSectionFromSidebarButton();
        appendAccountSelectionButton();

        if (!mSettingsPresenter.isEmpty()) {
            String title = mSection != null ? mSection.getTitle() : null;
            mSettingsPresenter.showDialog(title, this::disposeActions);
        }
    }

    private void prepareAndShowDialogUnsigned() {
        if (getContext() == null) {
            return;
        }

        mSettingsPresenter.clear();

        appendReturnToBackgroundVideoButton();
        appendRefreshButton();
        appendUnpinFromSidebarButton();
        appendUnpinSectionFromSidebarButton();
        appendAccountSelectionButton();

        if (mSettingsPresenter.isEmpty()) {
            MessageHelpers.showMessage(getContext(), R.string.msg_signed_users_only);
        } else {
            String title = mSection != null ? mSection.getTitle() : null;
            mSettingsPresenter.showDialog(title, this::disposeActions);
        }
    }

    private void appendUnpinFromSidebarButton() {
        if (!mIsUnpinFromSidebarEnabled) {
            return;
        }

        // Prepare to new channel playlists
        if (mVideo == null || (!mVideo.hasPlaylist() && !mVideo.hasUploads() && !mVideo.hasChannel())) {
            return;
        }

        mSettingsPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.unpin_from_sidebar),
                        optionItem -> {
                            if (mVideo.hasPlaylist()) {
                                togglePinToSidebar(createPinnedSection(mVideo));
                                mSettingsPresenter.closeDialog();
                            } else {
                                mServiceManager.loadChannelUploads(mVideo, group -> {
                                    if (group.getMediaItems() != null) {
                                        MediaItem firstItem = group.getMediaItems().get(0);

                                        Video section = createPinnedSection(Video.from(firstItem));
                                        section.title = mVideo.title;
                                        togglePinToSidebar(section);
                                        mSettingsPresenter.closeDialog();
                                    }
                                });
                            }
                        }));
    }

    private void appendUnpinSectionFromSidebarButton() {
        if (!mIsUnpinSectionFromSidebarEnabled) {
            return;
        }

        if (mSection == null || mSection.getId() == MediaGroup.TYPE_SETTINGS || mVideo != null) {
            return;
        }

        mSettingsPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.unpin_from_sidebar),
                        optionItem -> {
                            GeneralData.instance(getContext()).enableSection(mSection.getId(), false);
                            BrowsePresenter.instance(getContext()).updateSections();
                            mSettingsPresenter.closeDialog();
                        }));
    }

    private void appendAccountSelectionButton() {
        if (!mIsAccountSelectionEnabled) {
            return;
        }

        mSettingsPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.dialog_account_list), optionItem -> {
                    AccountSelectionPresenter.instance(getContext()).show(true);
                }));
    }

    private void appendRefreshButton() {
        if (!mIsRefreshEnabled) {
            return;
        }

        if (mSection == null || mSection.getId() == MediaGroup.TYPE_SETTINGS) {
            return;
        }

        mSettingsPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.refresh_section), optionItem -> {
                    BrowsePresenter.instance(getContext()).getView().focusOnContent();
                    BrowsePresenter.instance(getContext()).refresh();
                    mSettingsPresenter.closeDialog();
                }));
    }

    private void togglePinToSidebar(Video section) {
        BrowsePresenter presenter = BrowsePresenter.instance(getContext());

        // Toggle between pin/unpin while dialog is opened
        boolean isItemPinned = presenter.isItemPinned(section);

        if (isItemPinned) {
            presenter.unpinItem(section);
        } else {
            presenter.pinItem(section);
        }
        //MessageHelpers.showMessage(getContext(), isItemPinned ? R.string.unpinned_from_sidebar : R.string.pinned_to_sidebar);
    }

    private Video createPinnedSection(Video video) {
        if (video == null || (!video.hasPlaylist() && !video.hasUploads())) {
            return null;
        }

        Video section = new Video();
        section.playlistId = video.playlistId;
        section.title = String.format("%s - %s",
                video.group != null && video.group.getTitle() != null ? video.group.getTitle() : video.title,
                video.author != null ? video.author : video.description
        );
        section.cardImageUrl = video.cardImageUrl;

        return section;
    }

    private void appendReturnToBackgroundVideoButton() {
        if (!mIsReturnToBackgroundVideoEnabled || !PlaybackPresenter.instance(getContext()).isRunningInBackground()) {
            return;
        }

        mSettingsPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.return_to_background_video),
                        // Assume that the Playback view already blocked and remembered.
                        optionItem -> ViewManager.instance(getContext()).startView(SplashView.class)
                )
        );
    }

    private void disposeActions() {
        //RxUtils.disposeActions(mPlaylistAction);
    }

    private void updateEnabledMenuItems() {
        MainUIData mainUIData = MainUIData.instance(getContext());

        if (!mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_PIN_TO_SIDEBAR)) {
            mIsUnpinFromSidebarEnabled = false;
            mIsUnpinSectionFromSidebarEnabled = false;
        }

        if (!mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_SELECT_ACCOUNT)) {
            mIsAccountSelectionEnabled = false;
        }
    }
}

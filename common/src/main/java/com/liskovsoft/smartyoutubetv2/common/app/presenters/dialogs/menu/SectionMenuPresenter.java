package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu;

import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.yt.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.yt.data.MediaItem;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.VideoMenuPresenter.VideoMenuCallback;
import com.liskovsoft.smartyoutubetv2.common.app.views.SplashView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;

import java.util.Iterator;

public class SectionMenuPresenter extends BaseMenuPresenter {
    private static final String TAG = SectionMenuPresenter.class.getSimpleName();
    private final AppDialogPresenter mDialogPresenter;
    private Video mVideo;
    private BrowseSection mSection;
    private boolean mIsReturnToBackgroundVideoEnabled;
    private boolean mIsMarkAllChannelsWatchedEnabled;
    private boolean mIsRefreshEnabled;
    private boolean mIsMoveSectionEnabled;
    private boolean mIsRenameSectionEnabled;

    private SectionMenuPresenter(Context context) {
        super(context);
        mDialogPresenter = AppDialogPresenter.instance(context);
    }

    public static SectionMenuPresenter instance(Context context) {
        return new SectionMenuPresenter(context);
    }

    @Override
    protected Video getVideo() {
        return mVideo;
    }

    @Override
    protected BrowseSection getSection() {
        return mSection;
    }

    @Override
    protected AppDialogPresenter getDialogPresenter() {
        return mDialogPresenter;
    }

    @Override
    protected VideoMenuCallback getCallback() {
        return null;
    }

    public void showMenu(BrowseSection section) {
        if (section == null) {
            return;
        }

        disposeActions();

        mSection = section;
        mVideo = section.getData() instanceof Video ? (Video) section.getData() : null;

        MediaServiceManager.instance().authCheck(this::obtainPlaylistsAndShowDialogSigned, this::prepareAndShowDialogUnsigned);
    }

    private void obtainPlaylistsAndShowDialogSigned() {
        prepareAndShowDialogSigned();
    }

    private void prepareAndShowDialogSigned() {
        if (getContext() == null) {
            return;
        }

        appendReturnToBackgroundVideoButton();
        appendRefreshButton();
        appendUnpinVideoFromSidebarButton();
        appendUnpinSectionFromSidebarButton();
        appendMarkAllChannelsWatchedButton();
        appendAccountSelectionButton();
        appendMoveSectionButton();
        appendRenameSectionButton();
        appendCreatePlaylistButton();
        appendToggleHistoryButton();
        appendClearHistoryButton();
        appendUpdateCheckButton();

        if (!mDialogPresenter.isEmpty()) {
            String title = mSection != null ? mSection.getTitle() : null;
            mDialogPresenter.showDialog(title, this::disposeActions);
        }
    }

    private void prepareAndShowDialogUnsigned() {
        if (getContext() == null) {
            return;
        }

        appendReturnToBackgroundVideoButton();
        appendRefreshButton();
        appendUnpinVideoFromSidebarButton();
        appendUnpinSectionFromSidebarButton();
        appendAccountSelectionButton();
        appendMoveSectionButton();
        appendRenameSectionButton();

        if (mDialogPresenter.isEmpty()) {
            MessageHelpers.showMessage(getContext(), R.string.msg_signed_users_only);
        } else {
            String title = mSection != null ? mSection.getTitle() : null;
            mDialogPresenter.showDialog(title, this::disposeActions);
        }
    }

    private void appendRefreshButton() {
        if (!mIsRefreshEnabled) {
            return;
        }

        if (mSection == null || mSection.getId() == MediaGroup.TYPE_SETTINGS) {
            return;
        }

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.refresh_section), optionItem -> {
                    if (BrowsePresenter.instance(getContext()).getView() != null) {
                        BrowsePresenter.instance(getContext()).getView().focusOnContent();
                        BrowsePresenter.instance(getContext()).refresh();
                    }
                    mDialogPresenter.closeDialog();
                }));
    }

    private void appendMoveSectionButton() {
        if (!mIsMoveSectionEnabled) {
            return;
        }

        if (mSection == null) {
            return;
        }

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.move_section_up), optionItem -> {
                    //mDialogPresenter.closeDialog();
                    BrowsePresenter.instance(getContext()).moveSectionUp(mSection);
                }));

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.move_section_down), optionItem -> {
                    //mDialogPresenter.closeDialog();
                    BrowsePresenter.instance(getContext()).moveSectionDown(mSection);
                }));
    }

    private void appendRenameSectionButton() {
        if (!mIsRenameSectionEnabled) {
            return;
        }

        if (mSection == null || mSection.isDefault()) {
            return;
        }

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.rename_section), optionItem -> {
                    mDialogPresenter.closeDialog();
                    SimpleEditDialog.show(
                            getContext(),
                            mSection.getTitle(),
                            newValue -> {
                                mSection.setTitle(newValue);
                                BrowsePresenter.instance(getContext()).renameSection(mSection);
                                return true;
                            },
                            getContext().getString(R.string.rename_section)
                    );
                }));
    }

    private void appendReturnToBackgroundVideoButton() {
        if (!mIsReturnToBackgroundVideoEnabled || !PlaybackPresenter.instance(getContext()).isRunningInBackground()) {
            return;
        }

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.return_to_background_video),
                        // Assume that the Playback view already blocked and remembered.
                        optionItem -> ViewManager.instance(getContext()).startView(SplashView.class)
                )
        );
    }

    private void appendMarkAllChannelsWatchedButton() {
        if (!mIsMarkAllChannelsWatchedEnabled) {
            return;
        }

        if (mSection == null || mSection.getId() != MediaGroup.TYPE_CHANNEL_UPLOADS) {
            return;
        }

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.mark_all_channels_watched), optionItem -> {
                    mDialogPresenter.closeDialog();

                    MediaServiceManager serviceManager = MediaServiceManager.instance();

                    MessageHelpers.showMessage(getContext(), R.string.wait_data_loading);

                    serviceManager.loadSubscribedChannels(group -> {
                        Iterator<MediaItem> iterator = group.getMediaItems().iterator();

                        processNextChannel(serviceManager, iterator);
                    });
                }));
    }

    private void processNextChannel(MediaServiceManager serviceManager, Iterator<MediaItem> iterator) {
        if (iterator.hasNext()) {
            MediaItem next = iterator.next();

            if (!next.hasNewContent()) {
                processNextChannel(serviceManager, iterator);
                return;
            }

            MessageHelpers.showMessage(getContext(), next.getTitle());
            serviceManager.loadChannelUploads(next, (groupTmp) -> processNextChannel(serviceManager, iterator));
        } else {
            MessageHelpers.showMessage(getContext(), R.string.msg_done);
        }
    }

    private void disposeActions() {
        //RxUtils.disposeActions(mPlaylistAction);
    }

    @Override
    protected void updateEnabledMenuItems() {
        super.updateEnabledMenuItems();

        mIsReturnToBackgroundVideoEnabled = true;
        mIsRefreshEnabled = true;
        mIsMarkAllChannelsWatchedEnabled = true;
        mIsMoveSectionEnabled = true;
        mIsRenameSectionEnabled = true;

        MainUIData mainUIData = MainUIData.instance(getContext());

        mIsMoveSectionEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_MOVE_SECTION_UP);
        mIsMoveSectionEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_MOVE_SECTION_DOWN);
        mIsRenameSectionEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_RENAME_SECTION);
    }
}

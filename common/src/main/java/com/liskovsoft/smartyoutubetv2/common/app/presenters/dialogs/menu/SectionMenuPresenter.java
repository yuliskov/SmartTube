package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu;

import android.content.Context;

import androidx.annotation.Nullable;

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
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.VideoMenuPresenter.MenuAction;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.VideoMenuPresenter.VideoMenuCallback;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.ContextMenuManager;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.ContextMenuProvider;
import com.liskovsoft.smartyoutubetv2.common.app.views.SplashView;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

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
    private final Map<Long, MenuAction> mMenuMapping = new HashMap<>();

    private SectionMenuPresenter(Context context) {
        super(context);
        mDialogPresenter = AppDialogPresenter.instance(context);

        initMenuMapping();
    }

    public static SectionMenuPresenter instance(Context context) {
        return new SectionMenuPresenter(context);
    }

    @Override
    protected @Nullable Video getVideo() {
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

        for (Long menuItem : MainUIData.instance(getContext()).getMenuItemsOrdered()) {
            MenuAction menuAction = mMenuMapping.get(menuItem);
            if (menuAction != null) {
                menuAction.run();
            }
        }

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
        appendClearHistoryButton();

        for (Long menuItem : MainUIData.instance(getContext()).getMenuItemsOrdered()) {
            MenuAction menuAction = mMenuMapping.get(menuItem);
            if (menuAction != null && !menuAction.isAuth()) {
                menuAction.run();
            }
        }

        if (!mDialogPresenter.isEmpty()) {
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

        if (mSection == null || mSection.isDefault() || getVideo() == null ||
                (!getVideo().hasPlaylist() && !getVideo().hasReloadPageKey() && !getVideo().hasChannel())) {
            return;
        }

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.rename_section), optionItem -> {
                    mDialogPresenter.closeDialog();
                    SimpleEditDialog.show(
                            getContext(),
                            getContext().getString(R.string.rename_section),
                            mSection.getTitle(),
                            newValue -> {
                                mSection.setTitle(newValue);
                                BrowsePresenter.instance(getContext()).renameSection(mSection);
                                return true;
                            });
                }));
    }

    private void appendReturnToBackgroundVideoButton() {
        if (!mIsReturnToBackgroundVideoEnabled || !PlaybackPresenter.instance(getContext()).isRunningInBackground()) {
            return;
        }

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.return_to_background_video),
                        // Assume that the Playback view already blocked and remembered.
                        optionItem -> getViewManager().startView(SplashView.class)
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

    private void initMenuMapping() {
        mMenuMapping.clear();

        for (ContextMenuProvider provider : new ContextMenuManager(getContext()).getProviders()) {
            if (provider.getMenuType() != ContextMenuProvider.MENU_TYPE_SECTION) {
                continue;
            }
            mMenuMapping.put(provider.getId(), new MenuAction(() -> appendContextMenuItem(provider), false));
        }
    }

    private void appendContextMenuItem(ContextMenuProvider provider) {
        MainUIData mainUIData = MainUIData.instance(getContext());
        if (mainUIData.isMenuItemEnabled(provider.getId()) && provider.isEnabled(getVideo())) {
            mDialogPresenter.appendSingleButton(
                    UiOptionItem.from(getContext().getString(provider.getTitleResId()), optionItem -> provider.onClicked(getVideo(), getCallback()))
            );
        }
    }
}

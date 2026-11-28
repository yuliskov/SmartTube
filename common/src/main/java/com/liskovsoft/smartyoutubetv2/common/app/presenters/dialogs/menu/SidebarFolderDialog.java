package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.service.SidebarService;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;

import java.util.ArrayList;
import java.util.List;

public class SidebarFolderDialog {
    /**
     * Choose the folder for the single item
     */
    public static void showMoveDialog(Context context, Video item) {
        SidebarService service = SidebarService.instance(context);
        AppDialogPresenter dialogPresenter = AppDialogPresenter.instance(context);
        int currentId = service.getFolderId(item);

        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(context.getString(R.string.new_folder), optionItem -> {
            dialogPresenter.closeDialog();
            SimpleEditDialog.show(context, context.getString(R.string.new_folder), null, newValue -> {
                if (service.findFolderByTitle(newValue) != null) {
                    return false;
                }

                BrowsePresenter.instance(context).moveToFolder(item, service.createFolder(newValue).sectionId);
                return true;
            });
        }, false));

        options.add(UiOptionItem.from(context.getString(R.string.no_folder), optionItem -> {
            dialogPresenter.closeDialog();
            BrowsePresenter.instance(context).moveToFolder(item, -1);
        }, currentId == -1));

        for (Video folder : service.getFolders()) {
            options.add(UiOptionItem.from(folder.getTitle(), optionItem -> {
                dialogPresenter.closeDialog();
                BrowsePresenter.instance(context).moveToFolder(item, folder.sectionId);
            }, currentId == folder.sectionId));
        }

        String title = context.getString(R.string.move_to_folder);
        dialogPresenter.appendRadioCategory(title, options);
        dialogPresenter.showDialog(title);
    }

    /**
     * Choose all items of the folder at once
     */
    public static void showItemsDialog(Context context, int folderId) {
        SidebarService service = SidebarService.instance(context);
        Video folder = service.findFolder(folderId);

        if (folder == null) {
            return;
        }

        AppDialogPresenter dialogPresenter = AppDialogPresenter.instance(context);
        List<OptionItem> options = new ArrayList<>();
        boolean[] isChanged = {false};

        for (Video item : service.getFolderCandidates()) {
            Video otherFolder = service.findFolder(service.getFolderId(item));
            String title = otherFolder != null && otherFolder.sectionId != folderId ?
                    String.format("%s (%s)", item.getTitle(), otherFolder.getTitle()) : item.getTitle();

            options.add(UiOptionItem.from(title, optionItem -> {
                service.moveToFolder(item, optionItem.isSelected() ? folderId : -1);
                isChanged[0] = true;
            }, service.getFolderId(item) == folderId));
        }

        dialogPresenter.appendCheckedCategory(folder.getTitle(), options);
        dialogPresenter.showDialog(folder.getTitle(), () -> {
            if (isChanged[0]) {
                BrowsePresenter.instance(context).updateSections();
            }
        });
    }
}

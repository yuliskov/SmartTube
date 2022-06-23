package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.prefs.ContentBlockData;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * An action for enable/disable sponsored content.
 */
public class ContentBlockAction extends TwoStateAction {
    public ContentBlockAction(Context context) {
        super(context, R.id.action_content_block, R.drawable.action_content_block);

        String[] labels = new String[2];
        // Note, labels denote the action taken when clicked
        labels[INDEX_OFF] = ContentBlockData.SPONSOR_BLOCK_NAME;
        labels[INDEX_ON] = ContentBlockData.SPONSOR_BLOCK_NAME;
        setLabels(labels);
    }
}

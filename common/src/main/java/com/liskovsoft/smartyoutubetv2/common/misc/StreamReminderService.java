package com.liskovsoft.smartyoutubetv2.common.misc;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

public class StreamReminderService {
    private static StreamReminderService sInstance;

    private StreamReminderService() {
    }

    public static StreamReminderService instance() {
        if (sInstance == null) {
            sInstance = new StreamReminderService();
        }

        return sInstance;
    }

    public boolean isReminderSet(Video video) {
        return false;
    }

    public void toggleReminder(Video video) {
        
    }
}

package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs;

/**
 * YouTube color scheme: an update found by the background check waits behind the title bar update button.<br/>
 * Separate from {@link AppUpdatePresenter} so the tv module doesn't need the update checker library.
 */
public class PendingAppUpdate {
    private static Runnable sShowUpdate;
    private static Runnable sOnUpdateFoundListener;

    static void set(Runnable showUpdate) {
        sShowUpdate = showUpdate;

        if (sOnUpdateFoundListener != null) {
            sOnUpdateFoundListener.run();
        }
    }

    public static boolean exists() {
        return sShowUpdate != null;
    }

    /**
     * Shows the install dialog of the found update
     */
    public static void show() {
        if (sShowUpdate != null) {
            sShowUpdate.run();
        }
    }

    public static void setOnUpdateFoundListener(Runnable listener) {
        sOnUpdateFoundListener = listener;
    }
}

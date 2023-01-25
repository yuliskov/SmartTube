package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.sharedutils.helpers.Helpers;

public class PlayerTweaksData {
    private static final String VIDEO_PLAYER_TWEAKS_DATA = "video_player_tweaks_data";
    public static final int PLAYER_BUTTON_VIDEO_ZOOM = 0b1;
    public static final int PLAYER_BUTTON_SEARCH = 0b10;
    public static final int PLAYER_BUTTON_PIP = 0b100;
    public static final int PLAYER_BUTTON_SCREEN_OFF = 0b1000;
    public static final int PLAYER_BUTTON_PLAYBACK_QUEUE = 0b10000;
    public static final int PLAYER_BUTTON_VIDEO_SPEED = 0b100000;
    public static final int PLAYER_BUTTON_VIDEO_STATS = 0b1000000;
    public static final int PLAYER_BUTTON_OPEN_CHANNEL = 0b10000000;
    public static final int PLAYER_BUTTON_SUBTITLES = 0b100000000;
    public static final int PLAYER_BUTTON_SUBSCRIBE = 0b1000000000;
    public static final int PLAYER_BUTTON_LIKE = 0b10000000000;
    public static final int PLAYER_BUTTON_DISLIKE = 0b100000000000;
    public static final int PLAYER_BUTTON_ADD_TO_PLAYLIST = 0b1000000000000;
    public static final int PLAYER_BUTTON_PLAY_PAUSE = 0b10000000000000;
    public static final int PLAYER_BUTTON_REPEAT_MODE = 0b100000000000000;
    public static final int PLAYER_BUTTON_NEXT = 0b1000000000000000;
    public static final int PLAYER_BUTTON_PREVIOUS = 0b10000000000000000;
    public static final int PLAYER_BUTTON_HIGH_QUALITY = 0b100000000000000000;
    public static final int PLAYER_BUTTON_VIDEO_INFO = 0b1000000000000000000;
    public static final int PLAYER_BUTTON_SHARE = 0b10000000000000000000;
    public static final int PLAYER_BUTTON_SEEK_INTERVAL = 0b100000000000000000000;
    public static final int PLAYER_BUTTON_CONTENT_BLOCK = 0b1000000000000000000000;
    public static final int PLAYER_BUTTON_CHAT = 0b10000000000000000000000;
    public static final int PLAYER_BUTTON_VIDEO_ROTATE = 0b100000000000000000000000;
    public static final int PLAYER_BUTTON_DEFAULT = Integer.MAX_VALUE & ~(PLAYER_BUTTON_SEEK_INTERVAL | PLAYER_BUTTON_CONTENT_BLOCK | PLAYER_BUTTON_VIDEO_ROTATE); // all buttons, except these
    @SuppressLint("StaticFieldLeak")
    private static PlayerTweaksData sInstance;
    private final AppPrefs mPrefs;
    private boolean mIsAmlogicFixEnabled;
    private boolean mIsFrameDropFixEnabled;
    private boolean mIsSnapToVsyncDisabled;
    private boolean mIsProfileLevelCheckSkipped;
    private boolean mIsSWDecoderForced;
    private boolean mIsTextureViewEnabled;
    private boolean mIsSetOutputSurfaceWorkaroundEnabled;
    private boolean mIsAudioSyncFixEnabled;
    private boolean mIsKeepFinishedActivityEnabled;
    private boolean mIsLiveStreamFixEnabled;
    private boolean mIsPlaybackNotificationsDisabled;
    private boolean mIsTunneledPlaybackEnabled;
    private int mPlayerButtons;
    private boolean mIsBufferingFixEnabled;
    private boolean mIsNoFpsPresetsEnabled;
    private boolean mIsRememberPositionOfShortVideosEnabled;
    private boolean mIsSuggestionsDisabled;
    private boolean mIsAvcOverVp9Preferred;
    private boolean mIsChatPlacedLeft;
    private boolean mIsRealChannelIconEnabled;
    private float mPixelRatio;
    private boolean mIsQualityInfoBitrateEnabled;
    private boolean mIsSpeedButtonOldBehaviorEnabled;
    private boolean mIsButtonLongClickEnabled;

    private PlayerTweaksData(Context context) {
        mPrefs = AppPrefs.instance(context);
        restoreData();
    }

    public static PlayerTweaksData instance(Context context) {
        if (sInstance == null) {
            sInstance = new PlayerTweaksData(context.getApplicationContext());
        }

        return sInstance;
    }

    public void enableAmlogicFix(boolean enable) {
        mIsAmlogicFixEnabled = enable;
        persistData();
    }

    public boolean isAmlogicFixEnabled() {
        return mIsAmlogicFixEnabled;
    }

    public void enableFrameDropFix(boolean enable) {
        mIsFrameDropFixEnabled = enable;
        persistData();
    }

    public boolean isFrameDropFixEnabled() {
        return mIsFrameDropFixEnabled;
    }

    public void disableSnapToVsync(boolean disable) {
        mIsSnapToVsyncDisabled = disable;
        persistData();
    }

    public boolean isSnappingToVsyncDisabled() {
        return mIsSnapToVsyncDisabled;
    }

    public void skipProfileLevelCheck(boolean enable) {
        mIsProfileLevelCheckSkipped = enable;
        persistData();
    }

    public boolean isProfileLevelCheckSkipped() {
        return mIsProfileLevelCheckSkipped;
    }

    public void forceSWDecoder(boolean force) {
        mIsSWDecoderForced = force;
        persistData();
    }

    public boolean isSWDecoderForced() {
        return mIsSWDecoderForced;
    }

    public boolean isTextureViewEnabled() {
        return mIsTextureViewEnabled;
    }

    public void enableTextureView(boolean enable) {
        mIsTextureViewEnabled = enable;
        persistData();
    }

    public boolean isSetOutputSurfaceWorkaroundEnabled() {
        return mIsSetOutputSurfaceWorkaroundEnabled;
    }

    /**
     * Need to be enabled on older version of ExoPlayer (e.g. 2.10.6).<br/>
     * It's because there's no tweaks for modern devices.
     */
    public void enableSetOutputSurfaceWorkaround(boolean enable) {
        mIsSetOutputSurfaceWorkaroundEnabled = enable;
        persistData();
    }

    public void enableAudioSyncFix(boolean enable) {
        mIsAudioSyncFixEnabled = enable;
        persistData();
    }

    public boolean isAudioSyncFixEnabled() {
        return mIsAudioSyncFixEnabled;
    }

    /**
     * Fix crashes on chinese projectors
     */
    public boolean isKeepFinishedActivityEnabled() {
        return mIsKeepFinishedActivityEnabled;
    }

    /**
     * Fix crashes on chinese projectors
     */
    public void enableKeepFinishedActivity(boolean enable) {
        mIsKeepFinishedActivityEnabled = enable;
        persistData();
    }

    public void enableLiveStreamFix(boolean enable) {
        mIsLiveStreamFixEnabled = enable;
        persistData();
    }

    public boolean isLiveStreamFixEnabled() {
        return mIsLiveStreamFixEnabled;
    }

    public void disablePlaybackNotifications(boolean disable) {
        mIsPlaybackNotificationsDisabled = disable;
        persistData();
    }

    public boolean isPlaybackNotificationsDisabled() {
        return mIsPlaybackNotificationsDisabled;
    }

    public boolean isTunneledPlaybackEnabled() {
        return mIsTunneledPlaybackEnabled;
    }

    public void enableTunneledPlayback(boolean enable) {
        mIsTunneledPlaybackEnabled = enable;
        persistData();
    }

    public void enablePlayerButton(int playerButtons) {
        mPlayerButtons |= playerButtons;
        persistData();
    }

    public void disablePlayerButton(int playerButtons) {
        mPlayerButtons &= ~playerButtons;
        persistData();
    }

    public boolean isPlayerButtonEnabled(int menuItems) {
        return (mPlayerButtons & menuItems) == menuItems;
    }

    public void enableBufferingFix(boolean enable) {
        mIsBufferingFixEnabled = enable;
        persistData();
    }

    public boolean isBufferingFixEnabled() {
        return mIsBufferingFixEnabled;
    }

    public void enableNoFpsPresets(boolean enable) {
        mIsNoFpsPresetsEnabled = enable;
        persistData();
    }

    public boolean isNoFpsPresetsEnabled() {
        return mIsNoFpsPresetsEnabled;
    }

    public void preferAvcOverVp9(boolean prefer) {
        mIsAvcOverVp9Preferred = prefer;
        persistData();
    }

    public boolean isAvcOverVp9Preferred() {
        return mIsAvcOverVp9Preferred;
    }

    public void enableRememberPositionOfShortVideos(boolean enable) {
        mIsRememberPositionOfShortVideosEnabled = enable;
        persistData();
    }

    public boolean isRememberPositionOfShortVideosEnabled() {
        return mIsRememberPositionOfShortVideosEnabled;
    }

    public boolean isSuggestionsDisabled() {
        return mIsSuggestionsDisabled;
    }

    public void disableSuggestions(boolean disable) {
        mIsSuggestionsDisabled = disable;
        persistData();
    }

    public boolean isChatPlacedLeft() {
        return mIsChatPlacedLeft;
    }

    public void placeChatLeft(boolean left) {
        mIsChatPlacedLeft = left;
        persistData();
    }

    public boolean isRealChannelIconEnabled() {
        return mIsRealChannelIconEnabled;
    }

    public void enableRealChannelIcon(boolean enable) {
        mIsRealChannelIconEnabled = enable;
        persistData();
    }

    public void setPixelRatio(float pixelRatio) {
        mPixelRatio = pixelRatio;
        persistData();
    }

    public float getPixelRatio() {
        return mPixelRatio;
    }

    public boolean isQualityInfoBitrateEnabled() {
        return mIsQualityInfoBitrateEnabled;
    }

    public void enableQualityInfoBitrate(boolean enable) {
        mIsQualityInfoBitrateEnabled = enable;
        persistData();
    }

    public boolean isSpeedButtonOldBehaviorEnabled() {
        return mIsSpeedButtonOldBehaviorEnabled;
    }

    public void enableSpeedButtonOldBehavior(boolean enable) {
        mIsSpeedButtonOldBehaviorEnabled = enable;
        persistData();
    }

    public boolean isButtonLongClickEnabled() {
        return mIsButtonLongClickEnabled;
    }

    public void enableButtonLongClick(boolean enable) {
        mIsButtonLongClickEnabled = enable;
        persistData();
    }

    private void restoreData() {
        String data = mPrefs.getData(VIDEO_PLAYER_TWEAKS_DATA);

        String[] split = Helpers.splitObjectLegacy(data);

        mIsAmlogicFixEnabled = Helpers.parseBoolean(split, 0, false);
        mIsFrameDropFixEnabled = Helpers.parseBoolean(split, 1, false);
        mIsSnapToVsyncDisabled = Helpers.parseBoolean(split, 2, false);
        mIsProfileLevelCheckSkipped = Helpers.parseBoolean(split, 3, false);
        mIsSWDecoderForced = Helpers.parseBoolean(split, 4, false);
        mIsTextureViewEnabled = Helpers.parseBoolean(split, 5, false);
        // Need to be enabled (?) on older version of ExoPlayer (e.g. 2.10.6).
        // It's because there's no tweaks for modern devices.
        mIsSetOutputSurfaceWorkaroundEnabled = Helpers.parseBoolean(split, 7, true);
        mIsAudioSyncFixEnabled = Helpers.parseBoolean(split, 8, false);
        mIsKeepFinishedActivityEnabled = Helpers.parseBoolean(split, 9, false);
        mIsLiveStreamFixEnabled = Helpers.parseBoolean(split, 10, false);
        mIsPlaybackNotificationsDisabled = Helpers.parseBoolean(split, 11, !Helpers.isAndroidTVLauncher(mPrefs.getContext()));
        mIsTunneledPlaybackEnabled = Helpers.parseBoolean(split, 12, false);
        mPlayerButtons = Helpers.parseInt(split, 13, PLAYER_BUTTON_DEFAULT);
        mIsBufferingFixEnabled = Helpers.parseBoolean(split, 14, false);
        mIsNoFpsPresetsEnabled = Helpers.parseBoolean(split, 15, false);
        mIsRememberPositionOfShortVideosEnabled = Helpers.parseBoolean(split, 16, false);
        mIsSuggestionsDisabled = Helpers.parseBoolean(split, 17, false);
        mIsAvcOverVp9Preferred = Helpers.parseBoolean(split, 18, false);
        mIsChatPlacedLeft = Helpers.parseBoolean(split, 19, false);
        mIsRealChannelIconEnabled = Helpers.parseBoolean(split, 20, true);
        mPixelRatio = Helpers.parseFloat(split, 21, 1.0f);
        mIsQualityInfoBitrateEnabled = Helpers.parseBoolean(split, 22, false);
        mIsSpeedButtonOldBehaviorEnabled = Helpers.parseBoolean(split, 23, false);
        mIsButtonLongClickEnabled = Helpers.parseBoolean(split, 24, true);
    }

    private void persistData() {
        mPrefs.setData(VIDEO_PLAYER_TWEAKS_DATA, Helpers.mergeObject(
                mIsAmlogicFixEnabled, mIsFrameDropFixEnabled, mIsSnapToVsyncDisabled,
                mIsProfileLevelCheckSkipped, mIsSWDecoderForced, mIsTextureViewEnabled,
                null, mIsSetOutputSurfaceWorkaroundEnabled, mIsAudioSyncFixEnabled, mIsKeepFinishedActivityEnabled,
                mIsLiveStreamFixEnabled, mIsPlaybackNotificationsDisabled, mIsTunneledPlaybackEnabled, mPlayerButtons,
                mIsBufferingFixEnabled, mIsNoFpsPresetsEnabled, mIsRememberPositionOfShortVideosEnabled, mIsSuggestionsDisabled,
                mIsAvcOverVp9Preferred, mIsChatPlacedLeft, mIsRealChannelIconEnabled, mPixelRatio, mIsQualityInfoBitrateEnabled,
                mIsSpeedButtonOldBehaviorEnabled, mIsButtonLongClickEnabled
        ));
    }
}

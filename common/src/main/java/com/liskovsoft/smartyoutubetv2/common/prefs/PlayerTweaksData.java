package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs.ProfileChangeListener;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.internal.MediaServiceData;

public class PlayerTweaksData implements ProfileChangeListener {
    private static final String VIDEO_PLAYER_TWEAKS_DATA = "video_player_tweaks_data";
    public static final int PLAYER_DATA_SOURCE_DEFAULT = 0;
    public static final int PLAYER_DATA_SOURCE_OKHTTP = 1;
    public static final int PLAYER_DATA_SOURCE_CRONET = 2;
    public static final int PLAYER_BUTTON_VIDEO_ZOOM = 1;
    public static final int PLAYER_BUTTON_SEARCH = 1 << 1;
    public static final int PLAYER_BUTTON_PIP = 1 << 2;
    //public static final int PLAYER_BUTTON_SCREEN_OFF = 1 << 3;
    public static final int PLAYER_BUTTON_PLAYBACK_QUEUE = 1 << 4;
    public static final int PLAYER_BUTTON_VIDEO_SPEED = 1 << 5;
    public static final int PLAYER_BUTTON_VIDEO_STATS = 1 << 6;
    public static final int PLAYER_BUTTON_OPEN_CHANNEL = 1 << 7;
    public static final int PLAYER_BUTTON_SUBTITLES = 1 << 8;
    public static final int PLAYER_BUTTON_SUBSCRIBE = 1 << 9;
    public static final int PLAYER_BUTTON_LIKE = 1 << 10;
    public static final int PLAYER_BUTTON_DISLIKE = 1 << 11;
    public static final int PLAYER_BUTTON_ADD_TO_PLAYLIST = 1 << 12;
    public static final int PLAYER_BUTTON_PLAY_PAUSE = 1 << 13;
    public static final int PLAYER_BUTTON_REPEAT_MODE = 1 << 14;
    public static final int PLAYER_BUTTON_NEXT = 1 << 15;
    public static final int PLAYER_BUTTON_PREVIOUS = 1 << 16;
    public static final int PLAYER_BUTTON_HIGH_QUALITY = 1 << 17;
    public static final int PLAYER_BUTTON_VIDEO_INFO = 1 << 18;
    public static final int PLAYER_BUTTON_SHARE = 1 << 19;
    public static final int PLAYER_BUTTON_SEEK_INTERVAL = 1 << 20;
    public static final int PLAYER_BUTTON_CONTENT_BLOCK = 1 << 21;
    public static final int PLAYER_BUTTON_CHAT = 1 << 22;
    public static final int PLAYER_BUTTON_VIDEO_ROTATE = 1 << 23;
    public static final int PLAYER_BUTTON_SCREEN_DIMMING = 1 << 24;
    public static final int PLAYER_BUTTON_SOUND_OFF = 1 << 25;
    public static final int PLAYER_BUTTON_AFR = 1 << 26;
    public static final int PLAYER_BUTTON_VIDEO_FLIP = 1 << 27;
    public static final int PLAYER_BUTTON_DEFAULT = PLAYER_BUTTON_SEARCH | PLAYER_BUTTON_PIP | PLAYER_BUTTON_SCREEN_DIMMING | PLAYER_BUTTON_VIDEO_SPEED |
            PLAYER_BUTTON_VIDEO_STATS | PLAYER_BUTTON_OPEN_CHANNEL | PLAYER_BUTTON_SUBTITLES | PLAYER_BUTTON_SUBSCRIBE |
            PLAYER_BUTTON_LIKE | PLAYER_BUTTON_DISLIKE | PLAYER_BUTTON_ADD_TO_PLAYLIST | PLAYER_BUTTON_PLAY_PAUSE |
            PLAYER_BUTTON_REPEAT_MODE | PLAYER_BUTTON_NEXT | PLAYER_BUTTON_PREVIOUS | PLAYER_BUTTON_HIGH_QUALITY |
            PLAYER_BUTTON_VIDEO_INFO | PLAYER_BUTTON_CHAT;
    @SuppressLint("StaticFieldLeak")
    private static PlayerTweaksData sInstance;
    private final AppPrefs mPrefs;
    private boolean mIsAmlogicFixEnabled;
    private boolean mIsAmazonFrameDropFixEnabled;
    private boolean mIsSonyFrameDropFixEnabled;
    private boolean mIsSnapToVsyncDisabled;
    private boolean mIsProfileLevelCheckSkipped;
    private boolean mIsSWDecoderForced;
    private boolean mIsTextureViewEnabled;
    private boolean mIsSetOutputSurfaceWorkaroundEnabled;
    private boolean mIsAudioSyncFixEnabled;
    private boolean mIsKeepFinishedActivityEnabled;
    private boolean mIsHlsStreamsForced;
    private boolean mIsDashUrlStreamsForced;
    private boolean mIsPlaybackNotificationsDisabled;
    private boolean mIsTunneledPlaybackEnabled;
    private int mPlayerButtons;
    private boolean mIsNoFpsPresetsEnabled;
    private boolean mIsRememberPositionOfShortVideosEnabled;
    private boolean mIsRememberPositionOfLiveVideosEnabled;
    private boolean mIsSuggestionsDisabled;
    private boolean mIsAvcOverVp9Preferred;
    private boolean mIsChatPlacedLeft;
    private boolean mIsCommentsPlacedLeft;
    private boolean mIsRealChannelIconEnabled;
    private float mPixelRatio;
    private boolean mIsQualityInfoBitrateEnabled;
    private boolean mIsSpeedButtonOldBehaviorEnabled;
    private boolean mIsButtonLongClickEnabled;
    private boolean mIsLongSpeedListEnabled;
    private boolean mIsExtraLongSpeedListEnabled;
    private int mPlayerDataSource;
    private boolean mUnlockAllFormats;
    private boolean mIsBufferOnStreamsDisabled;
    private boolean mIsSectionPlaylistEnabled;
    private boolean mIsScreenOffTimeoutEnabled;
    private boolean mIsBootScreenOffEnabled;
    private int mScreenOffTimeoutSec;
    private int mScreenOffDimmingPercents;
    private boolean mIsUIAnimationsEnabled;
    private boolean mIsLikesCounterEnabled;
    private boolean mIsChapterNotificationEnabled;
    private boolean mIsPlayerUiOnNextEnabled;
    private boolean mIsPlayerAutoVolumeEnabled;
    private boolean mIsSimplePlayerNavigationEnabled;
    private boolean mIsUnsafeAudioFormatsEnabled;
    private boolean mIsLoopShortsEnabled;
    private boolean mIsQuickSkipShortsEnabled;
    private boolean mIsQuickSkipVideosEnabled;
    private boolean mIsOculusQuestFixEnabled;
    private boolean mIsAudioFocusEnabled;
    private boolean mIsNetworkErrorFixingDisabled;
    private boolean mIsDontResizeVideoToFitDialogEnabled;
    private boolean mIsSuggestionsHorizontallyScrolled;
    private boolean mIsPauseAfterEachVideoInQueueEnabled;
    private final Runnable mPersistDataInt = this::persistDataInt;

    private PlayerTweaksData(Context context) {
        mPrefs = AppPrefs.instance(context);
        mPrefs.addListener(this);
        restoreData();
    }

    public static PlayerTweaksData instance(Context context) {
        if (sInstance == null) {
            sInstance = new PlayerTweaksData(context.getApplicationContext());
        }

        return sInstance;
    }

    public boolean isAmlogicFixEnabled() {
        return mIsAmlogicFixEnabled;
    }

    public void setAmlogicFixEnabled(boolean enable) {
        mIsAmlogicFixEnabled = enable;
        persistData();
    }

    public boolean isAmazonFrameDropFixEnabled() {
        return mIsAmazonFrameDropFixEnabled;
    }

    public void setAmazonFrameDropFixEnabled(boolean enable) {
        mIsAmazonFrameDropFixEnabled = enable;
        persistData();
    }

    public boolean isSonyFrameDropFixEnabled() {
        return mIsSonyFrameDropFixEnabled;
    }

    public void setSonyFrameDropFixEnabled(boolean enable) {
        mIsSonyFrameDropFixEnabled = enable;
        persistData();
    }

    public boolean isSnappingToVsyncDisabled() {
        return mIsSnapToVsyncDisabled;
    }

    public void setSnappingToVsyncDisabled(boolean disable) {
        mIsSnapToVsyncDisabled = disable;
        persistData();
    }

    public boolean isProfileLevelCheckSkipped() {
        return mIsProfileLevelCheckSkipped;
    }

    public void setProfileLevelCheckSkipped(boolean enable) {
        mIsProfileLevelCheckSkipped = enable;
        persistData();
    }

    public boolean isSWDecoderForced() {
        return mIsSWDecoderForced;
    }

    public void setSWDecoderForced(boolean force) {
        mIsSWDecoderForced = force;
        persistData();
    }

    public boolean isTextureViewEnabled() {
        return mIsTextureViewEnabled;
    }

    public void setTextureViewEnabled(boolean enable) {
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
    public void setSetOutputSurfaceWorkaroundEnabled(boolean enable) {
        mIsSetOutputSurfaceWorkaroundEnabled = enable;
        persistData();
    }

    public boolean isAudioSyncFixEnabled() {
        return mIsAudioSyncFixEnabled;
    }

    public void setAudioSyncFixEnabled(boolean enable) {
        mIsAudioSyncFixEnabled = enable;
        persistData();
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
    public void setKeepFinishedActivityEnabled(boolean enable) {
        mIsKeepFinishedActivityEnabled = enable;
        persistData();
    }

    public boolean isHlsStreamsForced() {
        return mIsHlsStreamsForced;
    }

    public void setHlsStreamsForced(boolean enable) {
        mIsHlsStreamsForced = enable;
        mIsDashUrlStreamsForced = false;
        persistData();
    }

    public boolean isDashUrlStreamsForced() {
        return mIsDashUrlStreamsForced;
    }

    public void setDashUrlStreamsForced(boolean enable) {
        mIsDashUrlStreamsForced = enable;
        mIsHlsStreamsForced = false;
        persistData();
    }

    public boolean isPlaybackNotificationsDisabled() {
        return mIsPlaybackNotificationsDisabled;
    }

    public void setPlaybackNotificationsDisabled(boolean disable) {
        mIsPlaybackNotificationsDisabled = disable;
        persistData();
    }

    public boolean isTunneledPlaybackEnabled() {
        return mIsTunneledPlaybackEnabled;
    }

    public void setTunneledPlaybackEnabled(boolean enable) {
        mIsTunneledPlaybackEnabled = enable;
        persistData();
    }

    public boolean isUnsafeAudioFormatsEnabled() {
        return mIsUnsafeAudioFormatsEnabled;
    }

    public void setUnsafeAudioFormatsEnabled(boolean enable) {
        mIsUnsafeAudioFormatsEnabled = enable;
        persistData();
    }

    public boolean isPlayerButtonEnabled(int menuItems) {
        return (mPlayerButtons & menuItems) == menuItems;
    }

    public void setPlayerButtonEnabled(int playerButtons) {
        mPlayerButtons |= playerButtons;
        persistData();
    }

    public void setPlayerButtonDisabled(int playerButtons) {
        mPlayerButtons &= ~playerButtons;
        persistData();
    }

    public int getPlayerDataSource() {
        return mPlayerDataSource;
    }

    public void setPlayerDataSource(int dataSource) {
        mPlayerDataSource = dataSource;
        persistData();
    }

    public boolean isNoFpsPresetsEnabled() {
        return mIsNoFpsPresetsEnabled;
    }

    public void setNoFpsPresetsEnabled(boolean enable) {
        mIsNoFpsPresetsEnabled = enable;
        persistData();
    }

    public boolean isAvcOverVp9Preferred() {
        return mIsAvcOverVp9Preferred;
    }

    public void setAvcOverVp9Preferred(boolean prefer) {
        mIsAvcOverVp9Preferred = prefer;
        persistData();
    }

    public boolean isRememberPositionOfShortVideosEnabled() {
        return mIsRememberPositionOfShortVideosEnabled;
    }

    public void setRememberPositionOfShortVideosEnabled(boolean enable) {
        mIsRememberPositionOfShortVideosEnabled = enable;
        persistData();
    }

    public boolean isRememberPositionOfLiveVideosEnabled() {
        return mIsRememberPositionOfLiveVideosEnabled;
    }

    public void setRememberPositionOfLiveVideosEnabled(boolean enable) {
        mIsRememberPositionOfLiveVideosEnabled = enable;
        persistData();
    }

    public boolean isSuggestionsDisabled() {
        return mIsSuggestionsDisabled;
    }

    public void setSuggestionsDisabled(boolean disable) {
        mIsSuggestionsDisabled = disable;
        persistData();
    }

    public boolean isChatPlacedLeft() {
        return mIsChatPlacedLeft;
    }

    public void setChatPlacedLeft(boolean left) {
        mIsChatPlacedLeft = left;
        persistData();
    }

    public boolean isCommentsPlacedLeft() {
        return mIsCommentsPlacedLeft;
    }

    public void setCommentsPlacedLeft(boolean left) {
        mIsCommentsPlacedLeft = left;
        persistData();
    }

    public boolean isRealChannelIconEnabled() {
        return mIsRealChannelIconEnabled;
    }

    public void setRealChannelIconEnabled(boolean enable) {
        mIsRealChannelIconEnabled = enable;
        persistData();
    }

    public float getPixelRatio() {
        return mPixelRatio;
    }

    public void setPixelRatio(float pixelRatio) {
        mPixelRatio = pixelRatio;
        persistData();
    }

    public boolean isQualityInfoBitrateEnabled() {
        return mIsQualityInfoBitrateEnabled;
    }

    public void setQualityInfoBitrateEnabled(boolean enable) {
        mIsQualityInfoBitrateEnabled = enable;
        persistData();
    }

    public boolean isSpeedButtonOldBehaviorEnabled() {
        return mIsSpeedButtonOldBehaviorEnabled;
    }

    public void setSpeedButtonOldBehaviorEnabled(boolean enable) {
        mIsSpeedButtonOldBehaviorEnabled = enable;
        persistData();
    }

    public boolean isOculusQuestFixEnabled() {
        return mIsOculusQuestFixEnabled;
    }

    public void setOculusQuestFixEnabled(boolean enable) {
        mIsOculusQuestFixEnabled = enable;
        persistData();
    }

    public boolean isButtonLongClickEnabled() {
        return mIsButtonLongClickEnabled;
    }

    public void setButtonLongClickEnabled(boolean enable) {
        mIsButtonLongClickEnabled = enable;
        persistData();
    }

    public boolean isLongSpeedListEnabled() {
        return mIsLongSpeedListEnabled;
    }

    public void setLongSpeedListEnabled(boolean enable) {
        mIsExtraLongSpeedListEnabled = false;
        mIsLongSpeedListEnabled = enable;
        persistData();
    }

    public boolean isExtraLongSpeedListEnabled() {
        return mIsExtraLongSpeedListEnabled;
    }

    public void setExtraLongSpeedListEnabled(boolean enable) {
        mIsLongSpeedListEnabled = false;
        mIsExtraLongSpeedListEnabled = enable;
        persistData();
    }

    public boolean isAllFormatsUnlocked() {
        return mUnlockAllFormats;
    }

    public void setAllFormatsUnlocked(boolean unlock) {
        mUnlockAllFormats = unlock;
        persistData();
    }

    public boolean isBufferOnStreamsDisabled() {
        return mIsBufferOnStreamsDisabled;
    }

    public void setBufferOnStreamsDisabled(boolean disable) {
        mIsBufferOnStreamsDisabled = disable;
        persistData();
    }

    public boolean isSectionPlaylistEnabled() {
        return mIsSectionPlaylistEnabled;
    }

    public void setSectionPlaylistEnabled(boolean enable) {
        mIsSectionPlaylistEnabled = enable;
        persistData();
    }

    public boolean isScreenOffTimeoutEnabled() {
        return mIsScreenOffTimeoutEnabled;
    }

    public void setScreenOffTimeoutEnabled(boolean enable) {
        mIsScreenOffTimeoutEnabled = enable;
        persistData();
    }

    public int getScreenOffTimeoutSec() {
        return mScreenOffTimeoutSec;
    }

    public void setScreenOffTimeoutSec(int timeoutSec) {
        mScreenOffTimeoutSec = timeoutSec;
        mIsScreenOffTimeoutEnabled = mIsScreenOffTimeoutEnabled && timeoutSec > 0;
        persistData();
    }

    public int getScreenOffDimmingPercents() {
        return mScreenOffDimmingPercents;
    }

    public void setScreenOffDimmingPercents(int percents) {
        mScreenOffDimmingPercents = percents;
        persistData();
    }

    public boolean isBootScreenOffEnabled() {
        return mIsBootScreenOffEnabled && !isScreenOffTimeoutEnabled();
    }

    public void setBootScreenOffEnabled(boolean enable) {
        mIsBootScreenOffEnabled = enable;
        persistData();
    }

    public boolean isUIAnimationsEnabled() {
        return mIsUIAnimationsEnabled;
    }

    public void setUIAnimationsEnabled(boolean enable) {
        mIsUIAnimationsEnabled = enable;
        persistData();
    }

    public boolean isLikesCounterEnabled() {
        return mIsLikesCounterEnabled;
    }

    public void setLikesCounterEnabled(boolean enable) {
        mIsLikesCounterEnabled = enable;
        persistData();
    }

    public boolean isChapterNotificationEnabled() {
        return mIsChapterNotificationEnabled;
    }

    public void setChapterNotificationEnabled(boolean enable) {
        mIsChapterNotificationEnabled = enable;
        persistData();
    }

    public boolean isPlayerUiOnNextEnabled() {
        return mIsPlayerUiOnNextEnabled;
    }

    public void setPlayerUiOnNextEnabled(boolean enable) {
        mIsPlayerUiOnNextEnabled = enable;
        persistData();
    }

    public boolean isPlayerAutoVolumeEnabled() {
        return mIsPlayerAutoVolumeEnabled;
    }

    public void setPlayerAutoVolumeEnabled(boolean enable) {
        mIsPlayerAutoVolumeEnabled = enable;
        persistData();
    }

    public boolean isAudioFocusEnabled() {
        return mIsAudioFocusEnabled;
    }

    public void setAudioFocusEnabled(boolean enable) {
        mIsAudioFocusEnabled = enable;
        persistData();
    }

    public boolean isSimplePlayerNavigationEnabled() {
        return mIsSimplePlayerNavigationEnabled;
    }

    public void setSimplePlayerNavigationEnabled(boolean enable) {
        mIsSimplePlayerNavigationEnabled = enable;
        persistData();
    }

    public boolean isLoopShortsEnabled() {
        return mIsLoopShortsEnabled;
    }

    public void setLoopShortsEnabled(boolean enable) {
        mIsLoopShortsEnabled = enable;
        persistData();
    }

    public boolean isQuickSkipShortsEnabled() {
        return mIsQuickSkipShortsEnabled;
    }

    public void setQuickSkipShortsEnabled(boolean enable) {
        mIsQuickSkipShortsEnabled = enable;
        persistData();
    }

    public boolean isQuickSkipVideosEnabled() {
        return mIsQuickSkipVideosEnabled;
    }

    public void setQuickSkipVideosEnabled(boolean enable) {
        mIsQuickSkipVideosEnabled = enable;
        persistData();
    }

    public boolean isHighBitrateFormatsEnabled() {
        return MediaServiceData.instance().isFormatEnabled(MediaServiceData.FORMATS_EXTENDED_HLS);
    }

    public void setHighBitrateFormatsEnabled(boolean enable) {
        MediaServiceData.instance().setFormatEnabled(MediaServiceData.FORMATS_EXTENDED_HLS, enable);
    }

    public boolean isIPv4DnsPreferred() {
        return GlobalPreferences.instance(mPrefs.getContext()).isIPv4DnsPreferred();
    }

    public void setIPv4DnsPreferred(boolean prefer) {
        GlobalPreferences.instance(mPrefs.getContext()).setIPv4DnsPreferred(prefer);
    }

    public boolean isNetworkErrorFixingDisabled() {
        return mIsNetworkErrorFixingDisabled;
    }

    public void setNetworkErrorFixingDisabled(boolean disabled) {
        mIsNetworkErrorFixingDisabled = disabled;
        persistData();
    }

    public boolean isDontResizeVideoToFitDialogEnabled() {
        return mIsDontResizeVideoToFitDialogEnabled;
    }

    public void setDontResizeVideoToFitDialogEnabled(boolean enable) {
        mIsDontResizeVideoToFitDialogEnabled = enable;
        persistData();
    }

    public boolean isSuggestionsHorizontallyScrolled() {
        return mIsSuggestionsHorizontallyScrolled;
    }

    public void setSuggestionsHorizontallyScrolled(boolean enable) {
        mIsSuggestionsHorizontallyScrolled = enable;
        persistData();
    }

    public boolean isPauseAfterEachVideoInQueueEnabled() {
        return mIsPauseAfterEachVideoInQueueEnabled;
    }

    public void setPauseAfterEachVideoInQueueEnabled(boolean enable) {
        mIsPauseAfterEachVideoInQueueEnabled = enable;
        persistData();
    }

    private void restoreData() {
        String data = mPrefs.getProfileData(VIDEO_PLAYER_TWEAKS_DATA);

        String[] split = Helpers.splitData(data);

        mIsAmlogicFixEnabled = Helpers.parseBoolean(split, 0, false);
        mIsAmazonFrameDropFixEnabled = Helpers.parseBoolean(split, 1, false);
        mIsSnapToVsyncDisabled = Helpers.parseBoolean(split, 2, false);
        mIsProfileLevelCheckSkipped = Helpers.parseBoolean(split, 3, false);
        mIsSWDecoderForced = Helpers.parseBoolean(split, 4, false);
        mIsTextureViewEnabled = Helpers.parseBoolean(split, 5, false);
        // Need to be enabled (?) on older version of ExoPlayer (e.g. 2.10.6).
        // It's because there's no tweaks for modern devices.
        mIsSetOutputSurfaceWorkaroundEnabled = Helpers.parseBoolean(split, 7, true);
        mIsAudioSyncFixEnabled = Helpers.parseBoolean(split, 8, false);
        mIsKeepFinishedActivityEnabled = Helpers.parseBoolean(split, 9, false);
        mIsHlsStreamsForced = Helpers.parseBoolean(split, 10, false);
        mIsPlaybackNotificationsDisabled = Helpers.parseBoolean(split, 11, !Helpers.isAndroidTVLauncher(mPrefs.getContext()));
        mIsTunneledPlaybackEnabled = Helpers.parseBoolean(split, 12, false);
        mPlayerButtons = Helpers.parseInt(split, 13, PLAYER_BUTTON_DEFAULT);
        // Buffering fix was there.
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
        mIsLongSpeedListEnabled = Helpers.parseBoolean(split, 25, true);
        //mPlayerDataSource = Helpers.parseInt(split, 26, Utils.skipCronet() ? PLAYER_DATA_SOURCE_DEFAULT : PLAYER_DATA_SOURCE_CRONET);
        mPlayerDataSource = Helpers.parseInt(split, 26, PLAYER_DATA_SOURCE_DEFAULT);
        mUnlockAllFormats = Helpers.parseBoolean(split, 27, false);
        mIsDashUrlStreamsForced = Helpers.parseBoolean(split, 28, false);
        mIsSonyFrameDropFixEnabled = Helpers.parseBoolean(split, 29, false);
        mIsBufferOnStreamsDisabled = Helpers.parseBoolean(split, 30, false);
        // Cause severe garbage collector stuttering
        mIsSectionPlaylistEnabled = Helpers.parseBoolean(split, 31, Utils.isEnoughRam());
        mIsScreenOffTimeoutEnabled = Helpers.parseBoolean(split, 32, false);
        mScreenOffTimeoutSec = Helpers.parseInt(split, 33, 0);
        mIsUIAnimationsEnabled = Helpers.parseBoolean(split, 34, false);
        mIsLikesCounterEnabled = Helpers.parseBoolean(split, 35, true);
        mIsChapterNotificationEnabled = Helpers.parseBoolean(split, 36, false);
        mScreenOffDimmingPercents = Helpers.parseInt(split, 37, 100);
        mIsBootScreenOffEnabled = Helpers.parseBoolean(split, 38, false);
        mIsPlayerUiOnNextEnabled = Helpers.parseBoolean(split, 39, false);
        mIsPlayerAutoVolumeEnabled = Helpers.parseBoolean(split, 40, true);
        mIsSimplePlayerNavigationEnabled = Helpers.parseBoolean(split, 41, true);
        mIsUnsafeAudioFormatsEnabled = Helpers.parseBoolean(split, 42, true);
        //mIsHighBitrateFormatsEnabled = Helpers.parseBoolean(split, 43, false);
        mIsLoopShortsEnabled = Helpers.parseBoolean(split, 44, true);
        mIsQuickSkipShortsEnabled = Helpers.parseBoolean(split, 45, true);
        mIsRememberPositionOfLiveVideosEnabled = Helpers.parseBoolean(split, 46, true);
        mIsOculusQuestFixEnabled = Helpers.parseBoolean(split, 47, Utils.isOculusQuest());
        // mPlayerDataSource was here
        // Cronet is buffering too, unfortunately, so leave the default as a safest method (e.g. for "strtarmenia")
        // mPlayerDataSource = Helpers.parseInt(split, 48, PLAYER_DATA_SOURCE_DEFAULT);
        mIsExtraLongSpeedListEnabled = Helpers.parseBoolean(split, 49, false);
        mIsQuickSkipVideosEnabled = Helpers.parseBoolean(split, 50, false);
        mIsNetworkErrorFixingDisabled = Helpers.parseBoolean(split, 51, false);
        mIsCommentsPlacedLeft = Helpers.parseBoolean(split, 52, false);
        //mIsPersistentAntiBotFixEnabled = Helpers.parseBoolean(split, 53, false);
        mIsAudioFocusEnabled = Helpers.parseBoolean(split, 54, true);
        mIsDontResizeVideoToFitDialogEnabled = Helpers.parseBoolean(split, 55, false);
        mIsSuggestionsHorizontallyScrolled = Helpers.parseBoolean(split, 56, false);
        mIsPauseAfterEachVideoInQueueEnabled = Helpers.parseBoolean(split, 57, false);

        updateDefaultValues();
    }

    public void persistNow() {
        Utils.post(mPersistDataInt);
    }

    private void persistData() {
        Utils.postDelayed(mPersistDataInt, 10_000);
    }

    private void persistDataInt() {
        mPrefs.setProfileData(VIDEO_PLAYER_TWEAKS_DATA, Helpers.mergeData(
                mIsAmlogicFixEnabled, mIsAmazonFrameDropFixEnabled, mIsSnapToVsyncDisabled,
                mIsProfileLevelCheckSkipped, mIsSWDecoderForced, mIsTextureViewEnabled,
                null, mIsSetOutputSurfaceWorkaroundEnabled, mIsAudioSyncFixEnabled, mIsKeepFinishedActivityEnabled, mIsHlsStreamsForced,
                mIsPlaybackNotificationsDisabled, mIsTunneledPlaybackEnabled, mPlayerButtons,
                null, mIsNoFpsPresetsEnabled, mIsRememberPositionOfShortVideosEnabled, mIsSuggestionsDisabled,
                mIsAvcOverVp9Preferred, mIsChatPlacedLeft, mIsRealChannelIconEnabled, mPixelRatio, mIsQualityInfoBitrateEnabled,
                mIsSpeedButtonOldBehaviorEnabled, mIsButtonLongClickEnabled, mIsLongSpeedListEnabled, mPlayerDataSource, mUnlockAllFormats,
                mIsDashUrlStreamsForced, mIsSonyFrameDropFixEnabled, mIsBufferOnStreamsDisabled, mIsSectionPlaylistEnabled,
                mIsScreenOffTimeoutEnabled, mScreenOffTimeoutSec, mIsUIAnimationsEnabled, mIsLikesCounterEnabled, mIsChapterNotificationEnabled,
                mScreenOffDimmingPercents, mIsBootScreenOffEnabled, mIsPlayerUiOnNextEnabled, mIsPlayerAutoVolumeEnabled, mIsSimplePlayerNavigationEnabled,
                mIsUnsafeAudioFormatsEnabled, null, mIsLoopShortsEnabled, mIsQuickSkipShortsEnabled, mIsRememberPositionOfLiveVideosEnabled,
                mIsOculusQuestFixEnabled, null, mIsExtraLongSpeedListEnabled, mIsQuickSkipVideosEnabled, mIsNetworkErrorFixingDisabled, mIsCommentsPlacedLeft,
                null, mIsAudioFocusEnabled, mIsDontResizeVideoToFitDialogEnabled, mIsSuggestionsHorizontallyScrolled, mIsPauseAfterEachVideoInQueueEnabled
                ));
    }

    private void updateDefaultValues() {
        // Enable only certain buttons (not all, like it was)
        if (mPlayerButtons >>> 30 == 0b1) { // check leftmost bit (old format)
            int bits = 32 - 24;
            mPlayerButtons = mPlayerButtons << bits >>> bits; // remove auto enabled bits
        }
    }

    @Override
    public void onProfileChanged() {
        Utils.removeCallbacks(mPersistDataInt);
        restoreData();
    }
}

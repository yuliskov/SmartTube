package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.Build.VERSION;
import com.google.android.exoplayer2.text.CaptionStyleCompat;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.locale.LocaleUtility;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.PlaybackEngineController;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleManager.SubtitleStyle;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.ExoFormatItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayerData {
    private static final String VIDEO_PLAYER_DATA = "video_player_data";
    public static final int ONLY_UI = 0;
    public static final int UI_AND_PAUSE = 1;
    public static final int ONLY_PAUSE = 2;
    public static final int AUTO_HIDE_NEVER = 0;
    public static final int SEEK_PREVIEW_NONE = 0;
    public static final int SEEK_PREVIEW_SINGLE = 1;
    public static final int SEEK_PREVIEW_CAROUSEL_SLOW = 2;
    public static final int SEEK_PREVIEW_CAROUSEL_FAST = 3;
    @SuppressLint("StaticFieldLeak")
    private static PlayerData sInstance;
    private final AppPrefs mPrefs;
    private int mOKButtonBehavior;
    private int mUIHideTimeoutSec;
    private boolean mIsAbsoluteDateEnabled;
    private boolean mIsSeekConfirmPauseEnabled;
    private boolean mIsClockEnabled;
    private boolean mIsGlobalClockEnabled;
    private boolean mIsRemainingTimeEnabled;
    private int mBackgroundMode;
    private FormatItem mVideoFormat;
    private FormatItem mAudioFormat;
    private FormatItem mSubtitleFormat;
    private int mVideoBufferType;
    private final List<SubtitleStyle> mSubtitleStyles = new ArrayList<>();
    private final Map<String, FormatItem> mDefaultVideoFormats = new HashMap<>();
    private int mSubtitleStyleIndex;
    private int mVideoZoomMode;
    private float mVideoAspectRatio;
    private int mSeekPreviewMode;
    private float mSpeed;
    private boolean mIsAfrEnabled;
    private boolean mIsAfrFpsCorrectionEnabled;
    private boolean mIsAfrResSwitchEnabled;
    private int mAfrPauseMs;
    private int mAudioDelayMs;
    private boolean mIsRememberSpeedEnabled;
    private boolean mIsLegacyCodecsForced;
    private int mPlaybackMode;
    private boolean mIsSonyTimerFixEnabled;
    private boolean mIsQualityInfoEnabled;
    private boolean mIsRememberSpeedEachEnabled;
    private boolean mIsTimeCorrectionEnabled;
    private boolean mIsGlobalEndingTimeEnabled;
    private boolean mIsEndingTimeEnabled;
    private boolean mIsDoubleRefreshRateEnabled;
    private boolean mIsSeekConfirmPlayEnabled;
    private int mStartSeekIncrementMs;
    private float mSubtitleScale;
    private float mPlayerVolume;
    private boolean mIsTooltipsEnabled;
    private float mSubtitlePosition;
    private boolean mIsNumberKeySeekEnabled;
    private boolean mIsSkip24RateEnabled;

    private PlayerData(Context context) {
        mPrefs = AppPrefs.instance(context);
        initSubtitleStyles();
        initDefaultFormats();
        restoreData();
    }

    public static PlayerData instance(Context context) {
        if (sInstance == null) {
            sInstance = new PlayerData(context.getApplicationContext());
        }

        return sInstance;
    }

    public void setOKButtonBehavior(int option) {
        mOKButtonBehavior = option;
        persistData();
    }

    public int getOKButtonBehavior() {
        return mOKButtonBehavior;
    }

    public void setUIHideTimoutSec(int timoutSec) {
        mUIHideTimeoutSec = timoutSec;
        persistData();
    }

    public int getUIHideTimoutSec() {
        return mUIHideTimeoutSec;
    }

    public void enableAbsoluteDate(boolean show) {
        mIsAbsoluteDateEnabled = show;
        persistData();
    }

    public boolean isAbsoluteDateEnabled() {
        return mIsAbsoluteDateEnabled;
    }

    public void setSeekPreviewMode(int mode) {
        mSeekPreviewMode = mode;
        persistData();
    }

    public int getSeekPreviewMode() {
        return mSeekPreviewMode;
    }

    public void enableSeekConfirmPause(boolean enable) {
        mIsSeekConfirmPauseEnabled = enable;
        persistData();
    }

    public boolean isSeekConfirmPauseEnabled() {
        return mIsSeekConfirmPauseEnabled;
    }

    public void enableSeekConfirmPlay(boolean enable) {
        mIsSeekConfirmPlayEnabled = enable;
        persistData();
    }

    public boolean isSeekConfirmPlayEnabled() {
        return mIsSeekConfirmPlayEnabled;
    }

    public boolean isClockEnabled() {
        return mIsClockEnabled;
    }

    public void enableClock(boolean enable) {
        mIsClockEnabled = enable;
        persistData();
    }

    public boolean isGlobalClockEnabled() {
        return mIsGlobalClockEnabled;
    }

    public void enableGlobalClock(boolean enable) {
        mIsGlobalClockEnabled = enable;
        persistData();
    }

    public boolean isGlobalEndingTimeEnabled() {
        return mIsGlobalEndingTimeEnabled;
    }

    public void enableGlobalEndingTime(boolean enable) {
        mIsGlobalEndingTimeEnabled = enable;
        persistData();
    }

    public boolean isRemainingTimeEnabled() {
        return mIsRemainingTimeEnabled;
    }

    public void enableRemainingTime(boolean enable) {
        mIsRemainingTimeEnabled = enable;
        persistData();
    }

    public boolean isEndingTimeEnabled() {
        return mIsEndingTimeEnabled;
    }

    public void enableEndingTime(boolean enable) {
        mIsEndingTimeEnabled = enable;
        persistData();
    }

    public boolean isQualityInfoEnabled() {
        return mIsQualityInfoEnabled;
    }

    public void enableQualityInfo(boolean enable) {
        mIsQualityInfoEnabled = enable;
        persistData();
    }

    public void setBackgroundMode(int type) {
        mBackgroundMode = type;
        persistData();
    }

    public int getBackgroundMode() {
        return mBackgroundMode;
    }

    public void setPlaybackMode(int type) {
        mPlaybackMode = type;
        persistData();
    }

    public int getPlaybackMode() {
        return mPlaybackMode;
    }

    public boolean isRememberSpeedEnabled() {
        return mIsRememberSpeedEnabled;
    }

    public void enableRememberSpeed(boolean enable) {
        mIsRememberSpeedEnabled = enable;
        mIsRememberSpeedEachEnabled = false;
        persistData();
    }

    public boolean isRememberSpeedEachEnabled() {
        return mIsRememberSpeedEachEnabled;
    }

    public void enableRememberSpeedEach(boolean enable) {
        mIsRememberSpeedEachEnabled = enable;
        mIsRememberSpeedEnabled = false;
        persistData();
    }

    public boolean isLegacyCodecsForced() {
        return mIsLegacyCodecsForced;
    }

    public void forceLegacyCodecs(boolean enable) {
        mIsLegacyCodecsForced = enable;
        persistData();
    }

    public boolean isAfrEnabled() {
        return mIsAfrEnabled;
    }

    public void setAfrEnabled(boolean enabled) {
        mIsAfrEnabled = enabled;
        persistData();
    }

    public boolean isAfrFpsCorrectionEnabled() {
        return mIsAfrFpsCorrectionEnabled;
    }

    public void setAfrFpsCorrectionEnabled(boolean enabled) {
        mIsAfrFpsCorrectionEnabled = enabled;
        persistData();
    }

    public boolean isAfrResSwitchEnabled() {
        return mIsAfrResSwitchEnabled;
    }

    public void setAfrResSwitchEnabled(boolean enabled) {
        mIsAfrResSwitchEnabled = enabled;
        persistData();
    }

    public int getAfrPauseMs() {
        return mAfrPauseMs;
    }

    public void setAfrPauseMs(int pauseSec) {
        mAfrPauseMs = pauseSec;
        persistData();
    }

    public boolean isDoubleRefreshRateEnabled() {
        return mIsDoubleRefreshRateEnabled;
    }

    public void setDoubleRefreshRateEnabled(boolean enabled) {
        mIsDoubleRefreshRateEnabled = enabled;
        persistData();
    }

    public boolean isTooltipsEnabled() {
        return mIsTooltipsEnabled;
    }

    public void enableTooltips(boolean enable) {
        mIsTooltipsEnabled = enable;
        persistData();
    }

    public boolean isNumberKeySeekEnabled() {
        return mIsNumberKeySeekEnabled;
    }

    public void enableNumberKeySeek(boolean enable) {
        mIsNumberKeySeekEnabled = enable;
        persistData();
    }

    public FormatItem getFormat(int type) {
        FormatItem format = null;

        switch (type) {
            case FormatItem.TYPE_VIDEO:
                format = mVideoFormat;
                break;
            case FormatItem.TYPE_AUDIO:
                format = mAudioFormat;
                break;
            case FormatItem.TYPE_SUBTITLE:
                format = mSubtitleFormat;
                break;
        }

        return FormatItem.checkFormat(format, type);
    }

    public void setFormat(FormatItem format) {
        if (format == null) {
            return;
        }

        switch (format.getType()) {
            case FormatItem.TYPE_VIDEO:
                mVideoFormat = format;
                break;
            case FormatItem.TYPE_AUDIO:
                mAudioFormat = format;
                break;
            case FormatItem.TYPE_SUBTITLE:
                mSubtitleFormat = format;
                break;
        }
        
        persistData();
    }

    public void setVideoBufferType(int type) {
        mVideoBufferType = type;
        persistData();
    }

    public int getVideoBufferType() {
        return mVideoBufferType;
    }

    public List<SubtitleStyle> getSubtitleStyles() {
        return mSubtitleStyles;
    }

    public SubtitleStyle getSubtitleStyle() {
        return mSubtitleStyles.get(mSubtitleStyleIndex);
    }

    public void setSubtitleStyle(SubtitleStyle subtitleStyle) {
        mSubtitleStyleIndex = mSubtitleStyles.indexOf(subtitleStyle);
        persistData();
    }

    public float getSubtitleScale() {
        return mSubtitleScale;
    }

    public void setSubtitleScale(float scale) {
        mSubtitleScale = scale;
        persistData();
    }

    public float getSubtitlePosition() {
        return mSubtitlePosition;
    }

    public void setSubtitlePosition(float position) {
        mSubtitlePosition = position;
        persistData();
    }

    public float getPlayerVolume() {
        return mPlayerVolume;
    }

    public void setPlayerVolume(float scale) {
        mPlayerVolume = scale;
        persistData();
    }

    public void setVideoZoomMode(int mode) {
        mVideoZoomMode = mode;
        persistData();
    }

    public int getVideoZoomMode() {
        return mVideoZoomMode;
    }

    public void setVideoAspectRatio(float ratio) {
        mVideoAspectRatio = ratio;
        persistData();
    }

    public float getVideoAspectRatio() {
        return mVideoAspectRatio;
    }

    public void setSpeed(float speed) {
        if (mSpeed == speed) {
            return;
        }

        mSpeed = speed;
        persistData();
    }

    public float getSpeed() {
        return mSpeed;
    }

    public int getAudioDelayMs() {
        return mAudioDelayMs;
    }

    public void setAudioDelayMs(int delayMs) {
        mAudioDelayMs = delayMs;
        persistData();
    }

    public void enableSonyTimerFix(boolean enable) {
        mIsSonyTimerFixEnabled = enable;
        persistData();
    }

    public boolean isSonyTimerFixEnabled() {
        return mIsSonyTimerFixEnabled;
    }

    public void enableTimeCorrection(boolean enable) {
        mIsTimeCorrectionEnabled = enable;
        persistData();
    }

    public boolean isTimeCorrectionEnabled() {
        return mIsTimeCorrectionEnabled;
    }

    public boolean isSkip24RateEnabled() {
        return mIsSkip24RateEnabled;
    }

    public void enableSkip24Rate(boolean enable) {
        mIsSkip24RateEnabled = enable;
        persistData();
    }

    public FormatItem getDefaultAudioFormat() {
        String language = LocaleUtility.getCurrentLanguage(mPrefs.getContext());

        return ExoFormatItem.fromAudioSpecs(String.format("%s,%s", "mp4a", language));
    }

    public FormatItem getDefaultVideoFormat() {
        FormatItem formatItem = mDefaultVideoFormats.get(Build.MODEL);

        if (formatItem == null) {
            if (VERSION.SDK_INT <= 19) { // Android 4 playback crash fix (memory leak?)
                formatItem = FormatItem.VIDEO_SD_AVC_30;
            } else if (Helpers.isVP9Supported()) {
                formatItem = FormatItem.VIDEO_FHD_VP9_60;
            }
        }

        return formatItem != null ? formatItem : FormatItem.VIDEO_HD_AVC_30;
    }

    public int getStartSeekIncrementMs() {
        return mStartSeekIncrementMs;
    }

    public void setStartSeekIncrementMs(int startSeekIncrementMs) {
        mStartSeekIncrementMs = startSeekIncrementMs;
        persistData();
    }

    private void initSubtitleStyles() {
        mSubtitleStyles.add(new SubtitleStyle(R.string.subtitle_white_transparent, R.color.light_grey, R.color.transparent, CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW));
        mSubtitleStyles.add(new SubtitleStyle(R.string.subtitle_white_semi_transparent, R.color.light_grey, R.color.semi_transparent, CaptionStyleCompat.EDGE_TYPE_OUTLINE));
        mSubtitleStyles.add(new SubtitleStyle(R.string.subtitle_white_black, R.color.light_grey, R.color.black, CaptionStyleCompat.EDGE_TYPE_OUTLINE));
        mSubtitleStyles.add(new SubtitleStyle(R.string.subtitle_yellow_transparent, R.color.yellow, R.color.transparent, CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW));

        if (Build.VERSION.SDK_INT >= 19) {
            mSubtitleStyles.add(new SubtitleStyle(R.string.subtitle_system));
        }
    }

    /**
     * Overrides for auto detected values
     */
    private void initDefaultFormats() {
        mDefaultVideoFormats.put("SHIELD Android TV", FormatItem.VIDEO_4K_VP9_60);
        mDefaultVideoFormats.put("AFTMM", FormatItem.VIDEO_4K_VP9_60); // Stick 4K 2018
        mDefaultVideoFormats.put("AFTKA", FormatItem.VIDEO_4K_VP9_60); // Stick 4K Max 2021
        mDefaultVideoFormats.put("P1", FormatItem.VIDEO_FHD_AVC_60); // Chinese projector (see annoying emails)
    }

    private void restoreData() {
        String data = mPrefs.getData(VIDEO_PLAYER_DATA);

        String[] split = Helpers.splitObjectLegacy(data);

        mOKButtonBehavior = Helpers.parseInt(split, 0, ONLY_UI);
        mUIHideTimeoutSec = Helpers.parseInt(split, 1, 3);
        mIsAbsoluteDateEnabled = Helpers.parseBoolean(split, 2, false);
        mSeekPreviewMode = Helpers.parseInt(split, 3, SEEK_PREVIEW_SINGLE);
        mIsSeekConfirmPauseEnabled = Helpers.parseBoolean(split, 4, false);
        mIsClockEnabled = Helpers.parseBoolean(split, 5, true);
        mIsRemainingTimeEnabled = Helpers.parseBoolean(split, 6, true);
        mBackgroundMode = Helpers.parseInt(split, 7, PlaybackEngineController.BACKGROUND_MODE_DEFAULT);
        // afrData was there
        mVideoFormat = Helpers.firstNonNull(ExoFormatItem.from(Helpers.parseStr(split, 9)), getDefaultVideoFormat());
        mAudioFormat = Helpers.firstNonNull(ExoFormatItem.from(Helpers.parseStr(split, 10)), getDefaultAudioFormat());
        mSubtitleFormat = ExoFormatItem.from(Helpers.parseStr(split, 11));
        mVideoBufferType = Helpers.parseInt(split, 12, PlaybackEngineController.BUFFER_LOW);
        mSubtitleStyleIndex = Helpers.parseInt(split, 13, 1);
        mVideoZoomMode = Helpers.parseInt(split, 14, PlaybackEngineController.ZOOM_MODE_DEFAULT);
        mSpeed = Helpers.parseFloat(split, 15, 1.0f);
        mIsAfrEnabled = Helpers.parseBoolean(split, 16, false);
        mIsAfrFpsCorrectionEnabled = Helpers.parseBoolean(split, 17, true);
        mIsAfrResSwitchEnabled = Helpers.parseBoolean(split, 18, false);
        // old afr delay sec was there
        mAudioDelayMs = Helpers.parseInt(split, 20, 0);
        mIsRememberSpeedEnabled = Helpers.parseBoolean(split, 21, false);
        mPlaybackMode = Helpers.parseInt(split, 22, PlaybackEngineController.PLAYBACK_MODE_PLAY_ALL);
        // didn't remember what was there
        mIsLegacyCodecsForced = Helpers.parseBoolean(split, 24, false);
        mIsSonyTimerFixEnabled = Helpers.parseBoolean(split, 25, false);
        // old player tweaks
        mIsQualityInfoEnabled = Helpers.parseBoolean(split, 28, true);
        mIsRememberSpeedEachEnabled = Helpers.parseBoolean(split, 29, false);
        mVideoAspectRatio = Helpers.parseFloat(split, 30, PlaybackEngineController.ASPECT_RATIO_DEFAULT);
        mIsGlobalClockEnabled = Helpers.parseBoolean(split, 31, false);
        mIsTimeCorrectionEnabled = Helpers.parseBoolean(split, 32, true);
        mIsGlobalEndingTimeEnabled = Helpers.parseBoolean(split, 33, false);
        mIsEndingTimeEnabled = Helpers.parseBoolean(split, 34, false);
        mIsDoubleRefreshRateEnabled = Helpers.parseBoolean(split, 35, true);
        mIsSeekConfirmPlayEnabled = Helpers.parseBoolean(split, 36, false);
        mStartSeekIncrementMs = Helpers.parseInt(split, 37, 10_000);
        // old subs size px
        mSubtitleScale = Helpers.parseFloat(split, 39, 1.0f);
        mPlayerVolume = Helpers.parseFloat(split, 40, 1.0f);
        mIsTooltipsEnabled = Helpers.parseBoolean(split, 41, true);
        mSubtitlePosition = Helpers.parseFloat(split, 42, 0.1f);
        mIsNumberKeySeekEnabled = Helpers.parseBoolean(split, 43, true);
        mIsSkip24RateEnabled = Helpers.parseBoolean(split, 44, false);
        mAfrPauseMs = Helpers.parseInt(split, 45, 0);

        if (!mIsRememberSpeedEnabled) {
            mSpeed = 1.0f;
        }
    }

    private void persistData() {
        mPrefs.setData(VIDEO_PLAYER_DATA, Helpers.mergeObject(mOKButtonBehavior, mUIHideTimeoutSec, mIsAbsoluteDateEnabled, mSeekPreviewMode, mIsSeekConfirmPauseEnabled,
                mIsClockEnabled, mIsRemainingTimeEnabled, mBackgroundMode, null, // afrData was there
                Helpers.toString(mVideoFormat), Helpers.toString(mAudioFormat), Helpers.toString(mSubtitleFormat),
                mVideoBufferType, mSubtitleStyleIndex, mVideoZoomMode, mSpeed,
                mIsAfrEnabled, mIsAfrFpsCorrectionEnabled, mIsAfrResSwitchEnabled, null, mAudioDelayMs,
                mIsRememberSpeedEnabled, mPlaybackMode, null, // didn't remember what was there
                mIsLegacyCodecsForced, mIsSonyTimerFixEnabled, null, null, // old player tweaks
                mIsQualityInfoEnabled, mIsRememberSpeedEachEnabled, mVideoAspectRatio, mIsGlobalClockEnabled, mIsTimeCorrectionEnabled,
                mIsGlobalEndingTimeEnabled, mIsEndingTimeEnabled, mIsDoubleRefreshRateEnabled, mIsSeekConfirmPlayEnabled,
                mStartSeekIncrementMs, null, mSubtitleScale, mPlayerVolume, mIsTooltipsEnabled, mSubtitlePosition, mIsNumberKeySeekEnabled,
                mIsSkip24RateEnabled, mAfrPauseMs));
    }
}

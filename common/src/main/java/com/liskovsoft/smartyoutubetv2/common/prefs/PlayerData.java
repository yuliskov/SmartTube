package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.Build.VERSION;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.text.CaptionStyleCompat;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.locale.LocaleUtility;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerEngine;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerEngineConstants;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleManager.SubtitleStyle;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.ExoFormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track.MediaTrack;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs.ProfileChangeListener;
import com.liskovsoft.smartyoutubetv2.common.prefs.common.DataChangeBase;
import com.liskovsoft.youtubeapi.service.internal.MediaServiceData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayerData extends DataChangeBase implements PlayerEngineConstants, ProfileChangeListener {
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
    private int mUiHideTimeoutSec;
    private boolean mIsSeekConfirmPauseEnabled;
    private boolean mIsClockEnabled;
    private boolean mIsGlobalClockEnabled;
    private boolean mIsRemainingTimeEnabled;
    private int mBackgroundMode;
    private FormatItem mVideoFormat;
    private FormatItem mTempVideoFormat;
    private FormatItem mAudioFormat;
    private FormatItem mSubtitleFormat;
    private int mVideoBufferType;
    private final List<SubtitleStyle> mSubtitleStyles = new ArrayList<>();
    private final Map<String, FormatItem> mDefaultVideoFormats = new HashMap<>();
    private int mSubtitleStyleIndex;
    private int mVideoZoomMode;
    private int mVideoZoom;
    private float mVideoAspectRatio;
    private int mVideoRotation;
    private boolean mIsVideoFlipEnabled;
    private int mSeekPreviewMode;
    private float mSpeed;
    private float mLastSpeed;
    private boolean mIsAfrEnabled;
    private boolean mIsAfrFpsCorrectionEnabled;
    private boolean mIsAfrResSwitchEnabled;
    private int mAfrPauseMs;
    private int mAudioDelayMs;
    private String mAudioLanguage;
    private String mSubtitleLanguage;
    private boolean mIsAllSpeedEnabled;
    private int mRepeatMode;
    private boolean mIsSonyTimerFixEnabled;
    private boolean mIsQualityInfoEnabled;
    private boolean mIsSpeedPerVideoEnabled;
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
    private boolean mIsSkipShortsEnabled;
    private boolean mIsLiveChatEnabled;
    private List<FormatItem> mLastSubtitleFormats;
    private List<String> mEnabledSubtitlesPerChannel;
    private boolean mIsSubtitlesPerChannelEnabled;
    private boolean mIsSpeedPerChannelEnabled;
    private final Map<String, SpeedItem> mSpeeds = new HashMap<>();
    private float mPitch;
    private long mAfrSwitchTimeMs;
    private List<String> mLastAudioLanguages;

    private static class SpeedItem {
        public String channelId;
        public float speed;

        public SpeedItem(String channelId, float speed) {
            this.channelId = channelId;
            this.speed = speed;
        }

        public static SpeedItem fromString(String specs) {
            String[] split = Helpers.splitObj(specs);

            if (split == null || split.length != 2) {
                return new SpeedItem(null, 1);
            }

            return new SpeedItem(Helpers.parseStr(split[0]), Helpers.parseFloat(split[1]));
        }

        @NonNull
        @Override
        public String toString() {
            return Helpers.mergeObj(channelId, speed);
        }
    }

    private PlayerData(Context context) {
        mPrefs = AppPrefs.instance(context);
        mPrefs.addListener(this);
        initSubtitleStyles();
        initDefaultFormats();
        restoreState();
    }

    public static PlayerData instance(Context context) {
        if (sInstance == null) {
            sInstance = new PlayerData(context.getApplicationContext());
        }

        return sInstance;
    }

    public void setOKButtonBehavior(int option) {
        mOKButtonBehavior = option;
        persistState();
    }

    public int getOKButtonBehavior() {
        return mOKButtonBehavior;
    }

    public void setUiHideTimeoutSec(int timeoutSec) {
        mUiHideTimeoutSec = timeoutSec;
        persistState();
    }

    public int getUiHideTimeoutSec() {
        return mUiHideTimeoutSec;
    }

    public void setSeekPreviewMode(int mode) {
        mSeekPreviewMode = mode;
        persistState();
    }

    public int getSeekPreviewMode() {
        return mSeekPreviewMode;
    }

    public void enableSeekConfirmPause(boolean enable) {
        mIsSeekConfirmPauseEnabled = enable;
        persistState();
    }

    public boolean isSeekConfirmPauseEnabled() {
        return mIsSeekConfirmPauseEnabled;
    }

    public void enableSeekConfirmPlay(boolean enable) {
        mIsSeekConfirmPlayEnabled = enable;
        persistState();
    }

    public boolean isSeekConfirmPlayEnabled() {
        return mIsSeekConfirmPlayEnabled;
    }

    public boolean isClockEnabled() {
        return mIsClockEnabled;
    }

    public void enableClock(boolean enable) {
        mIsClockEnabled = enable;
        persistState();
    }

    public boolean isGlobalClockEnabled() {
        return mIsGlobalClockEnabled;
    }

    public void enableGlobalClock(boolean enable) {
        mIsGlobalClockEnabled = enable;
        persistState();
    }

    public boolean isGlobalEndingTimeEnabled() {
        return mIsGlobalEndingTimeEnabled;
    }

    public void enableGlobalEndingTime(boolean enable) {
        mIsGlobalEndingTimeEnabled = enable;
        persistState();
    }

    public boolean isRemainingTimeEnabled() {
        return mIsRemainingTimeEnabled;
    }

    public void enableRemainingTime(boolean enable) {
        mIsRemainingTimeEnabled = enable;
        persistState();
    }

    public boolean isEndingTimeEnabled() {
        return mIsEndingTimeEnabled;
    }

    public void enableEndingTime(boolean enable) {
        mIsEndingTimeEnabled = enable;
        persistState();
    }

    public boolean isQualityInfoEnabled() {
        return mIsQualityInfoEnabled;
    }

    public void enableQualityInfo(boolean enable) {
        mIsQualityInfoEnabled = enable;
        persistState();
    }

    public void setBackgroundMode(int type) {
        mBackgroundMode = type;
        persistState();
    }

    public int getBackgroundMode() {
        return mBackgroundMode;
    }

    public void setRepeatMode(int mode) {
        mRepeatMode = mode;
        persistState();
    }

    public int getRepeatMode() {
        return mRepeatMode;
    }

    public boolean isAllSpeedEnabled() {
        return mIsAllSpeedEnabled;
    }

    public void enableAllSpeed(boolean enable) {
        mIsAllSpeedEnabled = enable;
        mIsSpeedPerVideoEnabled = false;
        mIsSpeedPerChannelEnabled = false;
        persistState();
    }

    public boolean isSpeedPerVideoEnabled() {
        return mIsSpeedPerVideoEnabled;
    }

    public void enableSpeedPerVideo(boolean enable) {
        mIsSpeedPerVideoEnabled = enable;
        mIsAllSpeedEnabled = false;
        mIsSpeedPerChannelEnabled = false;
        persistState();
    }

    public boolean isLegacyCodecsForced() {
        return MediaServiceData.instance().isFormatEnabled(MediaServiceData.FORMATS_URL) && !MediaServiceData.instance().isFormatEnabled(MediaServiceData.FORMATS_DASH);
    }

    public void forceLegacyCodecs(boolean enable) {
        MediaServiceData.instance().enableFormat(MediaServiceData.FORMATS_URL, enable);
        MediaServiceData.instance().enableFormat(MediaServiceData.FORMATS_DASH, !enable);
    }

    public boolean isAfrEnabled() {
        return mIsAfrEnabled;
    }

    public void setAfrEnabled(boolean enabled) {
        mIsAfrEnabled = enabled;
        persistState();
    }

    public boolean isAfrFpsCorrectionEnabled() {
        return mIsAfrFpsCorrectionEnabled;
    }

    public void setAfrFpsCorrectionEnabled(boolean enabled) {
        mIsAfrFpsCorrectionEnabled = enabled;
        persistState();
    }

    public boolean isAfrResSwitchEnabled() {
        return mIsAfrResSwitchEnabled;
    }

    public void setAfrResSwitchEnabled(boolean enabled) {
        mIsAfrResSwitchEnabled = enabled;
        persistState();
    }

    public int getAfrPauseMs() {
        return mAfrPauseMs;
    }

    public void setAfrPauseMs(int pauseSec) {
        mAfrPauseMs = pauseSec;
        persistState();
    }

    public boolean isDoubleRefreshRateEnabled() {
        return mIsDoubleRefreshRateEnabled;
    }

    public void setDoubleRefreshRateEnabled(boolean enabled) {
        mIsDoubleRefreshRateEnabled = enabled;
        persistState();
    }

    public boolean isTooltipsEnabled() {
        return mIsTooltipsEnabled;
    }

    public void enableTooltips(boolean enable) {
        mIsTooltipsEnabled = enable;
        persistState();
    }

    public boolean isNumberKeySeekEnabled() {
        return mIsNumberKeySeekEnabled;
    }

    public void enableNumberKeySeek(boolean enable) {
        mIsNumberKeySeekEnabled = enable;
        persistState();
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

        MediaTrack track = FormatItem.toMediaTrack(format);
        if (track != null) {
            track.isSaved = true;
        }

        return FormatItem.checkFormat(format, type);
    }

    public void setFormat(FormatItem format) {
        //if (format == null || Helpers.equalsAny(format, mVideoFormat, mAudioFormat, mSubtitleFormat)) {
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
                setLastSubtitleFormat(format);
                mSubtitleFormat = format;
                break;
        }
        
        persistState();
    }

    public void setTempVideoFormat(FormatItem format) {
        mTempVideoFormat = format;
    }

    public FormatItem getTempVideoFormat() {
        return mTempVideoFormat;
    }

    public FormatItem getLastSubtitleFormat() {
        return !mLastSubtitleFormats.isEmpty() ? mLastSubtitleFormats.get(0) : FormatItem.SUBTITLE_NONE;
    }

    public List<FormatItem> getLastSubtitleFormats() {
        return mLastSubtitleFormats;
    }

    private void setLastSubtitleFormat(FormatItem format) {
        if (format != null && !format.isDefault()) {
            mLastSubtitleFormats.remove(format);
            mLastSubtitleFormats.add(0, format);
        } else if (mSubtitleFormat != null && !mSubtitleFormat.isDefault()) {
            mLastSubtitleFormats.remove(mSubtitleFormat);
            mLastSubtitleFormats.add(0, mSubtitleFormat);
        }

        // Limit max size
        //if (mLastSubtitleFormats.size() > 3) {
        //    mLastSubtitleFormats.subList(3, mLastSubtitleFormats.size()).clear();
        //}
    }

    public void enableSubtitlesPerChannel(String channelId) {
        mEnabledSubtitlesPerChannel.add(channelId);
        persistState();
    }

    public void disableSubtitlesPerChannel(String channelId) {
        mEnabledSubtitlesPerChannel.remove(channelId);
        persistState();
    }

    public boolean isSubtitlesPerChannelEnabled(String channelId) {
        if (channelId == null) {
            return false;
        }

        return mEnabledSubtitlesPerChannel.contains(channelId);
    }

    public void enableSubtitlesPerChannel(boolean enable) {
        mIsSubtitlesPerChannelEnabled = enable;
        persistState();
    }

    public boolean isSubtitlesPerChannelEnabled() {
        return mIsSubtitlesPerChannelEnabled;
    }

    public void setVideoBufferType(int type) {
        mVideoBufferType = type;
        persistState();
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
        persistState();
    }

    public float getSubtitleScale() {
        return mSubtitleScale;
    }

    public void setSubtitleScale(float scale) {
        mSubtitleScale = scale;
        persistState();
    }

    public float getSubtitlePosition() {
        return mSubtitlePosition;
    }

    public void setSubtitlePosition(float position) {
        mSubtitlePosition = position;
        persistState();
    }

    public float getPlayerVolume() {
        return mPlayerVolume;
    }

    public void setPlayerVolume(float scale) {
        mPlayerVolume = scale;
        persistState();
    }

    public void setVideoZoomMode(int mode) {
        mVideoZoomMode = mode;
        persistState();
    }

    public int getVideoZoomMode() {
        return mVideoZoomMode;
    }

    public void setVideoZoom(int percents) {
        mVideoZoom = percents;
        persistState();
    }

    public int getVideoZoom() {
        return mVideoZoom;
    }

    public void setVideoAspectRatio(float ratio) {
        mVideoAspectRatio = ratio;
        persistState();
    }

    public float getVideoAspectRatio() {
        return mVideoAspectRatio;
    }

    public void setVideoRotation(int angle) {
        mVideoRotation = angle;
        persistState();
    }

    public int getVideoRotation() {
        return mVideoRotation;
    }

    public void setVideoFlipEnabled(boolean enabled) {
        mIsVideoFlipEnabled = enabled;
        persistState();
    }

    public boolean isVideoFlipEnabled() {
        return mIsVideoFlipEnabled;
    }

    public void setSpeed(float speed) {
        setSpeed(null, speed);
    }

    public void setSpeed(String channelId, float speed) {
        if (mSpeed == speed && channelId == null) {
            return;
        }

        if (isSpeedPerChannelEnabled() && channelId != null) {
            if (Helpers.floatEquals(speed, 1.0f)) {
                mSpeeds.remove(channelId);
            } else {
                mSpeeds.put(channelId, new SpeedItem(channelId, speed));
            }
        }
        setLastSpeed(speed);
        mSpeed = speed;
        persistState();
    }

    public float getSpeed() {
        return getSpeed(null);
    }

    public float getSpeed(String channelId) {
        SpeedItem speed = null;

        if (isSpeedPerChannelEnabled() && channelId != null) {
            speed = mSpeeds.get(channelId);
            mSpeed = 1.0f; // reset speed if the channel not found
        }

        if (speed != null) {
            mSpeed = speed.speed;
        }

        return mSpeed;
    }

    public void setLastSpeed(float speed) {
        if (speed > 0 && !Helpers.floatEquals(speed, 1.0f)) {
            mLastSpeed = speed;
        } else if (mSpeed > 0 && !Helpers.floatEquals(mSpeed, 1.0f)) {
            mLastSpeed = mSpeed;
        }
    }

    public float getLastSpeed() {
        return mLastSpeed;
    }

    public void enableSpeedPerChannel(boolean enable) {
        mIsSpeedPerChannelEnabled = enable;
        mIsSpeedPerVideoEnabled = false;
        mIsAllSpeedEnabled = false;
        persistState();
    }

    public boolean isSpeedPerChannelEnabled() {
        return mIsSpeedPerChannelEnabled;
    }

    public int getAudioDelayMs() {
        return mAudioDelayMs;
    }

    public void setAudioDelayMs(int delayMs) {
        mAudioDelayMs = delayMs;
        persistState();
    }

    public float getPitch() {
        return mPitch;
    }

    public void setPitch(float pitch) {
        mPitch = pitch;
        persistState();
    }

    public String getAudioLanguage() {
        return mAudioLanguage;
    }

    public void setAudioLanguage(String language) {
        mAudioLanguage = language;
        setLastAudioLanguage(language);
        persistState();
    }

    public List<String> getLastAudioLanguages() {
        return mLastAudioLanguages;
    }

    private void setLastAudioLanguage(String language) {
        mLastAudioLanguages.remove(language);
        mLastAudioLanguages.add(0, language);
    }

    public String getSubtitleLanguage() {
        return mSubtitleLanguage;
    }

    public void setSubtitleLanguage(String language) {
        mSubtitleLanguage = language;
        persistState();
    }

    public void enableSonyTimerFix(boolean enable) {
        mIsSonyTimerFixEnabled = enable;
        persistState();
    }

    public boolean isSonyTimerFixEnabled() {
        return mIsSonyTimerFixEnabled;
    }

    public void enableTimeCorrection(boolean enable) {
        mIsTimeCorrectionEnabled = enable;
        persistState();
    }

    public boolean isTimeCorrectionEnabled() {
        return mIsTimeCorrectionEnabled;
    }

    public boolean isSkip24RateEnabled() {
        return mIsSkip24RateEnabled;
    }

    public void enableSkip24Rate(boolean enable) {
        mIsSkip24RateEnabled = enable;
        persistState();
    }

    public boolean isSkipShortsEnabled() {
        return mIsSkipShortsEnabled;
    }

    public void enableSkipShorts(boolean enable) {
        mIsSkipShortsEnabled = enable;
        persistState();
    }

    public boolean isLiveChatEnabled() {
        return mIsLiveChatEnabled;
    }

    public void enableLiveChat(boolean enable) {
        mIsLiveChatEnabled = enable;
        persistState();
    }

    public FormatItem getDefaultAudioFormat() {
        // Android 4 (probably some others) doesn't support opus (ac3 will be reverted to opus)
        // Note, 5.1 mp4a doesn't work in 5.1 mode
        // Use opus (ac3 fallback) on modern devices. vp9 and opus should be supported at the same time?
        return Helpers.isVP9ResolutionSupported(2160) ? FormatItem.AUDIO_51_AC3 : FormatItem.AUDIO_HQ_MP4A;
    }

    public FormatItem getDefaultVideoFormat() {
        FormatItem formatItem = mDefaultVideoFormats.get(Build.MODEL);

        if (formatItem == null) {
            if (VERSION.SDK_INT <= 19) { // Android 4 playback crash fix (memory leak?)
                formatItem = FormatItem.VIDEO_SD_AVC_30;
            } else if (VERSION.SDK_INT <= 23 && Helpers.isVP9ResolutionSupported(1080)) {
                formatItem = FormatItem.VIDEO_FHD_VP9_60;
            } else if (Helpers.isVP9ResolutionSupported(2160)) {
                formatItem = FormatItem.VIDEO_4K_VP9_60;
            } else if (Helpers.isVP9ResolutionSupported(1080)) {
                formatItem = FormatItem.VIDEO_FHD_VP9_60;
            }
        }

        return formatItem != null ? formatItem : FormatItem.VIDEO_HD_AVC_30;
    }

    public FormatItem getDefaultSubtitleFormat() {
        return FormatItem.SUBTITLE_NONE;
    }

    public int getStartSeekIncrementMs() {
        return mStartSeekIncrementMs;
    }

    public void setStartSeekIncrementMs(int startSeekIncrementMs) {
        mStartSeekIncrementMs = startSeekIncrementMs;
        persistState();
    }

    public void setAfrSwitchTimeMs(long timeMillis) {
        mAfrSwitchTimeMs = timeMillis;
    }

    public long getAfrSwitchTimeMs() {
        return mAfrSwitchTimeMs;
    }

    @TargetApi(19)
    private void initSubtitleStyles() {
        mSubtitleStyles.add(new SubtitleStyle(R.string.subtitle_white_transparent, R.color.light_grey, R.color.transparent, CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW));
        mSubtitleStyles.add(new SubtitleStyle(R.string.subtitle_white_semi_transparent, R.color.light_grey, R.color.semi_transparent, CaptionStyleCompat.EDGE_TYPE_OUTLINE));
        mSubtitleStyles.add(new SubtitleStyle(R.string.subtitle_white_black, R.color.light_grey, R.color.black, CaptionStyleCompat.EDGE_TYPE_OUTLINE));
        mSubtitleStyles.add(new SubtitleStyle(R.string.subtitle_yellow_transparent, R.color.yellow, R.color.transparent, CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW));
        mSubtitleStyles.add(new SubtitleStyle(R.string.subtitle_yellow_semi_transparent, R.color.yellow, R.color.semi_transparent, CaptionStyleCompat.EDGE_TYPE_OUTLINE));
        mSubtitleStyles.add(new SubtitleStyle(R.string.subtitle_yellow_black, R.color.yellow, R.color.black, CaptionStyleCompat.EDGE_TYPE_OUTLINE));

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

    private void restoreState() {
        String data = mPrefs.getProfileData(VIDEO_PLAYER_DATA);

        String[] split = Helpers.splitData(data);

        mOKButtonBehavior = Helpers.parseInt(split, 0, ONLY_UI);
        mUiHideTimeoutSec = Helpers.parseInt(split, 1, 3);
        // mIsAbsoluteDateEnabled
        mSeekPreviewMode = Helpers.parseInt(split, 3, SEEK_PREVIEW_SINGLE);
        mIsSeekConfirmPauseEnabled = Helpers.parseBoolean(split, 4, false);
        mIsClockEnabled = Helpers.parseBoolean(split, 5, true);
        mIsRemainingTimeEnabled = Helpers.parseBoolean(split, 6, true);
        mBackgroundMode = Helpers.parseInt(split, 7, PlayerEngine.BACKGROUND_MODE_DEFAULT);
        // afrData was there
        mVideoFormat = Helpers.firstNonNull(ExoFormatItem.from(Helpers.parseStr(split, 9)), getDefaultVideoFormat());
        mAudioFormat = Helpers.firstNonNull(ExoFormatItem.from(Helpers.parseStr(split, 10)), getDefaultAudioFormat());
        mSubtitleFormat = Helpers.firstNonNull(ExoFormatItem.from(Helpers.parseStr(split, 11)), getDefaultSubtitleFormat());
        mVideoBufferType = Helpers.parseInt(split, 12, PlayerEngine.BUFFER_MEDIUM);
        mSubtitleStyleIndex = Helpers.parseInt(split, 13, 4); // yellow on semi bg
        mVideoZoomMode = Helpers.parseInt(split, 14, PlayerEngine.ZOOM_MODE_DEFAULT);
        mSpeed = Helpers.parseFloat(split, 15, 1.0f);
        mIsAfrEnabled = Helpers.parseBoolean(split, 16, false);
        mIsAfrFpsCorrectionEnabled = Helpers.parseBoolean(split, 17, true);
        mIsAfrResSwitchEnabled = Helpers.parseBoolean(split, 18, false);
        // old afr delay sec was there
        mAudioDelayMs = Helpers.parseInt(split, 20, 0);
        mIsAllSpeedEnabled = Helpers.parseBoolean(split, 21, false);
        // repeat mode was here
        // didn't remember what was there
        // mIsLegacyCodecsForced
        mIsSonyTimerFixEnabled = Helpers.parseBoolean(split, 25, false);
        // old player tweaks
        mIsQualityInfoEnabled = Helpers.parseBoolean(split, 28, true);
        mIsSpeedPerVideoEnabled = Helpers.parseBoolean(split, 29, false);
        mVideoAspectRatio = Helpers.parseFloat(split, 30, PlayerEngine.ASPECT_RATIO_DEFAULT);
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
        mIsLiveChatEnabled = Helpers.parseBoolean(split, 46, false);
        mLastSubtitleFormats = Helpers.parseList(split, 47, ExoFormatItem::from);
        //mLastSubtitleFormat = Helpers.firstNonNull(ExoFormatItem.from(Helpers.parseStr(split, 47)), FormatItem.SUBTITLE_NONE);
        mLastSpeed = Helpers.parseFloat(split, 48, 1.0f);
        mVideoRotation = Helpers.parseInt(split, 49, 0);
        mVideoZoom = Helpers.parseInt(split, 50, -1);
        mRepeatMode = Helpers.parseInt(split, 51, PlayerEngineConstants.PLAYBACK_MODE_ALL);
        mAudioLanguage = Helpers.parseStr(split, 52, LocaleUtility.getCurrentLanguage(mPrefs.getContext()));
        mSubtitleLanguage = Helpers.parseStr(split, 53, LocaleUtility.getCurrentLanguage(mPrefs.getContext()));
        //String enabledSubtitles = Helpers.parseStr(split, 54);
        mEnabledSubtitlesPerChannel = Helpers.parseStrList(split, 54);
        mIsSubtitlesPerChannelEnabled = Helpers.parseBoolean(split, 55, true);
        mIsSpeedPerChannelEnabled = Helpers.parseBoolean(split, 56, true);
        String[] speeds = Helpers.parseArray(split, 57);
        mPitch = Helpers.parseFloat(split, 58, 1.0f);
        mIsSkipShortsEnabled = Helpers.parseBoolean(split, 59, false);
        mLastAudioLanguages = Helpers.parseStrList(split, 60);
        mIsVideoFlipEnabled = Helpers.parseBoolean(split, 61, false);

        if (speeds != null) {
            for (String speedSpec : speeds) {
                SpeedItem item = SpeedItem.fromString(speedSpec);
                mSpeeds.put(item.channelId, item);
            }
        }

        if (!mIsAllSpeedEnabled) {
            mSpeed = 1.0f;
        }
    }

    private void persistState() {
        mPrefs.setProfileData(VIDEO_PLAYER_DATA, Helpers.mergeData(mOKButtonBehavior, mUiHideTimeoutSec, null,
                mSeekPreviewMode, mIsSeekConfirmPauseEnabled,
                mIsClockEnabled, mIsRemainingTimeEnabled, mBackgroundMode, null, // afrData was there
                mVideoFormat, mAudioFormat, mSubtitleFormat,
                mVideoBufferType, mSubtitleStyleIndex, mVideoZoomMode, mSpeed,
                mIsAfrEnabled, mIsAfrFpsCorrectionEnabled, mIsAfrResSwitchEnabled, null, mAudioDelayMs, mIsAllSpeedEnabled, null, null,
                null, mIsSonyTimerFixEnabled, null, null, // old player tweaks
                mIsQualityInfoEnabled, mIsSpeedPerVideoEnabled, mVideoAspectRatio, mIsGlobalClockEnabled, mIsTimeCorrectionEnabled,
                mIsGlobalEndingTimeEnabled, mIsEndingTimeEnabled, mIsDoubleRefreshRateEnabled, mIsSeekConfirmPlayEnabled,
                mStartSeekIncrementMs, null, mSubtitleScale, mPlayerVolume, mIsTooltipsEnabled, mSubtitlePosition, mIsNumberKeySeekEnabled,
                mIsSkip24RateEnabled, mAfrPauseMs, mIsLiveChatEnabled, mLastSubtitleFormats, mLastSpeed, mVideoRotation,
                mVideoZoom, mRepeatMode, mAudioLanguage, mSubtitleLanguage, mEnabledSubtitlesPerChannel, mIsSubtitlesPerChannelEnabled,
                mIsSpeedPerChannelEnabled, Helpers.mergeArray(mSpeeds.values().toArray()), mPitch, mIsSkipShortsEnabled, mLastAudioLanguages, mIsVideoFlipEnabled
        ));

        onDataChange();
    }

    @Override
    public void onProfileChanged() {
        // reset on profile change
        mSpeeds.clear();

        restoreState();
    }
}

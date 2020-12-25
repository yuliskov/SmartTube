package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import com.google.android.exoplayer2.text.CaptionStyleCompat;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.PlaybackEngineController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers.AutoFrameRateManager.AfrData;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleManager.SubtitleStyle;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.ExoFormatItem;

import java.util.ArrayList;
import java.util.List;

public class PlayerData {
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
    private boolean mIsShowFullDateEnabled;
    private boolean mIsPauseOnSeekEnabled;
    private boolean mIsClockEnabled;
    private boolean mIsRemainingTimeEnabled;
    private int mPlaybackMode;
    private AfrData mAfrData;
    private FormatItem mVideoFormat;
    private FormatItem mAudioFormat;
    private FormatItem mSubtitleFormat;
    private int mVideoBufferType;
    private final List<SubtitleStyle> mSubtitleStyles = new ArrayList<>();
    private int mSubtitleStyleIndex;
    private int mVideoZoomMode;
    private int mSeekPreviewMode;
    private float mSpeed;

    public PlayerData(Context context) {
        mPrefs = AppPrefs.instance(context);
        initSubtitleStyles();
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

    public void showFullDate(boolean show) {
        mIsShowFullDateEnabled = show;
        persistData();
    }

    public boolean isShowFullDateEnabled() {
        return mIsShowFullDateEnabled;
    }

    public void setSeekPreviewMode(int mode) {
        mSeekPreviewMode = mode;
        persistData();
    }

    public int getSeekPreviewMode() {
        return mSeekPreviewMode;
    }

    public void enablePauseOnSeek(boolean enable) {
        mIsPauseOnSeekEnabled = enable;
        persistData();
    }

    public boolean isClockEnabled() {
        return mIsClockEnabled;
    }

    public void enableClock(boolean enable) {
        mIsClockEnabled = enable;
        persistData();
    }

    public boolean isRemainingTimeEnabled() {
        return mIsRemainingTimeEnabled;
    }

    public void enableRemainingTime(boolean enable) {
        mIsRemainingTimeEnabled = enable;
        persistData();
    }

    public boolean isPauseOnSeekEnabled() {
        return mIsPauseOnSeekEnabled;
    }

    public void setPlaybackMode(int type) {
        mPlaybackMode = type;
        persistData();
    }

    public int getPlaybackMode() {
        return mPlaybackMode;
    }

    public AfrData getAfrData() {
        return mAfrData;
    }

    public void setAfrData(AfrData afrData) {
        mAfrData = afrData;
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

    public void setVideoZoomMode(int mode) {
        mVideoZoomMode = mode;
        persistData();
    }

    public int getVideoZoomMode() {
        return mVideoZoomMode;
    }

    public void setSpeed(float speed) {
        mSpeed = speed;
        persistData();
    }

    public float getSpeed() {
        return mSpeed;
    }

    private void initSubtitleStyles() {
        mSubtitleStyles.add(new SubtitleStyle(R.string.subtitle_default, R.color.light_grey, R.color.transparent, CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW));
        mSubtitleStyles.add(new SubtitleStyle(R.string.subtitle_semi_transparent_bg, R.color.light_grey, R.color.semi_grey, CaptionStyleCompat.EDGE_TYPE_OUTLINE));
        mSubtitleStyles.add(new SubtitleStyle(R.string.subtitle_black_bg, R.color.light_grey, R.color.black, CaptionStyleCompat.EDGE_TYPE_OUTLINE));
        mSubtitleStyles.add(new SubtitleStyle(R.string.subtitle_yellow, R.color.yellow, R.color.transparent, CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW));
    }

    private void restoreData() {
        String data = mPrefs.getPlayerData();

        String[] split = Helpers.splitObject(data);

        mOKButtonBehavior = Helpers.parseInt(split, 0, ONLY_UI);
        mUIHideTimeoutSec = Helpers.parseInt(split, 1, 3);
        mIsShowFullDateEnabled = Helpers.parseBoolean(split, 2, false);
        mSeekPreviewMode = Helpers.parseInt(split, 3, SEEK_PREVIEW_SINGLE);
        mIsPauseOnSeekEnabled = Helpers.parseBoolean(split, 4, false);
        mIsClockEnabled = Helpers.parseBoolean(split, 5, true);
        mIsRemainingTimeEnabled = Helpers.parseBoolean(split, 6, true);
        mPlaybackMode = Helpers.parseInt(split, 7, PlaybackEngineController.PLAYBACK_MODE_DEFAULT);
        mAfrData = AfrData.from(Helpers.parseStr(split, 8));
        mVideoFormat = ExoFormatItem.from(Helpers.parseStr(split, 9));
        mAudioFormat = ExoFormatItem.from(Helpers.parseStr(split, 10));
        mSubtitleFormat = ExoFormatItem.from(Helpers.parseStr(split, 11));
        mVideoBufferType = Helpers.parseInt(split, 12, PlaybackEngineController.BUFFER_LOW);
        mSubtitleStyleIndex = Helpers.parseInt(split, 13, 1);
        mVideoZoomMode = Helpers.parseInt(split, 14, PlaybackEngineController.ZOOM_MODE_DEFAULT);
        mSpeed = Helpers.parseFloat(split, 15, 1.0f);
    }

    private void persistData() {
        mPrefs.setPlayerData(Helpers.mergeObject(mOKButtonBehavior, mUIHideTimeoutSec,
                mIsShowFullDateEnabled, mSeekPreviewMode, mIsPauseOnSeekEnabled,
                mIsClockEnabled, mIsRemainingTimeEnabled, mPlaybackMode, Helpers.toString(mAfrData),
                Helpers.toString(mVideoFormat), Helpers.toString(mAudioFormat), Helpers.toString(mSubtitleFormat),
                mVideoBufferType, mSubtitleStyleIndex, mVideoZoomMode, mSpeed));
    }
}

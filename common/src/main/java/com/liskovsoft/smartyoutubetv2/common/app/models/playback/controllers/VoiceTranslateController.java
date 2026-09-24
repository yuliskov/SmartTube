package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import android.os.SystemClock;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.BasePlayerController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerUI;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.VotSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.common.vot.TranslationAudioPlayer;
import com.liskovsoft.smartyoutubetv2.common.vot.VotAudioSource;
import com.liskovsoft.smartyoutubetv2.common.vot.VotClient;
import com.liskovsoft.smartyoutubetv2.common.vot.VotSettings;
import com.liskovsoft.smartyoutubetv2.common.vot.VotYouTubeAudio;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/** Voice-over follows the main player's clock or selects a ready YouTube audio track. */
public final class VoiceTranslateController extends BasePlayerController {
    private static final String TAG = VoiceTranslateController.class.getSimpleName();
    private VotSettings mSettings;
    private TranslationAudioPlayer mAudio;
    private VotClient mClient;
    private Disposable mRequest;
    private boolean mEnabled;
    private boolean mStartedAutomatically;
    private boolean mAutoTried;
    private boolean mWaitingForAudio;
    private boolean mResumeAfterLoad;
    private String mSkippedVideoId;
    private boolean mDucked;
    private float mPreviousVolume;
    private float mPreferenceAtDuck;
    private float mCurrentVolume;
    private long mSpeechUntil;
    private int mGeneration;
    private MediaItemFormatInfo mFormatInfo;
    private String mFormatInfoVideoId;
    private String mLoadedVideoId;
    private FormatItem mNativeOriginal;
    private FormatItem mNativeDub;

    public void onFormatInfo(MediaItemFormatInfo info) {
        mFormatInfo = info;
        Video video = getVideo();
        mFormatInfoVideoId = video != null ? video.videoId : null;
        maybeAutoStart();
    }

    private final Runnable mSync = new Runnable() {
        @Override
        public void run() {
            if (mEnabled && mAudio != null) {
                sync();
                Utils.postDelayed(this, 1000);
            }
        }
    };

    private final Runnable mVolumeTick = new Runnable() {
        @Override public void run() {
            if (!mDucked || mAudio == null || getPlayer() == null) return;
            updateVolume();
            Utils.postDelayed(this, 50);
        }
    };

    @Override
    public void onButtonClicked(int buttonId, int buttonState) {
        if (buttonId != R.id.action_voice_translate || !settings().isEnabled()) return;
        if (mEnabled) {
            Video video = getVideo();
            mSkippedVideoId = video != null ? video.videoId : null;
            stop();
        } else {
            mSkippedVideoId = null;
            mAutoTried = true;
            start(false);
        }
    }

    @Override
    public void onButtonLongClicked(int buttonId, int buttonState) {
        if (buttonId == R.id.action_voice_translate) VotSettingsPresenter.show(getContext());
    }

    private VotSettings settings() {
        if (mSettings == null) mSettings = new VotSettings(getContext());
        return mSettings;
    }

    public void onEnabledSettingChanged() {
        if (!settings().isEnabled()) stop();
        else {
            mAutoTried = false;
            maybeAutoStart();
        }
    }

    public void onRequestSettingsChanged() {
        if (!mEnabled) {
            mAutoTried = false;
            maybeAutoStart();
            return;
        }
        boolean automatic = mStartedAutomatically;
        stop();
        start(automatic);
    }

    private String sourceLanguage() {
        if (settings().hasSelectedSourceLanguage()) return settings().getSourceLanguage();
        String detected = VotAudioSource.originalLanguage(mFormatInfo);
        return detected != null ? detected : settings().getSourceLanguage();
    }

    private void maybeAutoStart() {
        Video video = getVideo();
        PlaybackView player = getPlayer();
        if (mEnabled || mAutoTried || !settings().isEnabled()
                || video == null || video.videoId == null || video.isLive
                || video.videoId.equals(mSkippedVideoId) || mFormatInfo == null
                || !video.videoId.equals(mFormatInfoVideoId)
                || !video.videoId.equals(mLoadedVideoId) || player == null || !player.supportsVoiceOver()
                || player.getDurationMs() <= 0) return;
        mAutoTried = true;
        start(true);
    }

    private void start(boolean automatic) {
        PlaybackView player = getPlayer();
        Video video = getVideo();
        if (!settings().isEnabled() || player == null || !player.supportsVoiceOver()
                || video == null || video.videoId == null || video.isLive
                || mFormatInfo == null || !video.videoId.equals(mFormatInfoVideoId)
                || !video.videoId.equals(mLoadedVideoId)
                || player.getDurationMs() <= 0) {
            if (!automatic) MessageHelpers.showMessage(getContext(), R.string.vot_unavailable);
            return;
        }
        String sourceLanguage = sourceLanguage();
        String targetLanguage = settings().getTargetLanguage();
        if (sourceLanguage == null || settings().shouldSkipSameLanguage(
                sourceLanguage, targetLanguage, automatic)) {
            if (!automatic) MessageHelpers.showMessage(getContext(), R.string.vot_unavailable);
            return;
        }
        String provider = settings().getVoiceSource();
        if (!VotSettings.SOURCE_YANDEX.equals(provider)) {
            FormatItem dub = VotYouTubeAudio.dub(player.getAudioFormats(), targetLanguage);
            FormatItem original = VotYouTubeAudio.original(player.getAudioFormats(),
                    player.getAudioFormat(), targetLanguage);
            if (dub != null && original != null) {
                mNativeOriginal = original;
                mNativeDub = dub;
                mEnabled = true;
                mStartedAutomatically = automatic;
                setButton(PlayerUI.BUTTON_ON);
                player.setFormat(dub);
                if (!automatic) MessageHelpers.showMessage(getContext(), R.string.vot_playing);
                return;
            }
            if (VotSettings.SOURCE_YOUTUBE.equals(provider)) {
                if (!automatic) MessageHelpers.showMessage(getContext(), R.string.vot_youtube_unavailable);
                return;
            }
        }
        mEnabled = true;
        mStartedAutomatically = automatic;
        mWaitingForAudio = true;
        mResumeAfterLoad = player.getPlayWhenReady();
        player.setPlayWhenReady(false);
        if (getScreensaverManager() != null) getScreensaverManager().disableChecked();
        player.showVoiceOverProgress(true);
        MessageHelpers.showMessage(getContext(), getContext().getString(R.string.vot_loading,
                getContext().getString(targetName(targetLanguage))));
        setButton(PlayerUI.BUTTON_ON);
        int generation = ++mGeneration;
        String videoId = video.videoId;
        long durationMs = player.getDurationMs();
        boolean lively = settings().useLivelyVoice() && "ru".equals(targetLanguage);
        VotAudioSource source = VotAudioSource.best(mFormatInfo, sourceLanguage);
        VotClient client = new VotClient();
        mClient = client;
        mRequest = Single.fromCallable(() -> client.translate(
                        videoId, durationMs, source, sourceLanguage, targetLanguage, lively,
                        () -> Utils.post(() -> {
                            if (mEnabled && generation == mGeneration && getPlayer() != null) {
                                MessageHelpers.showMessage(getContext(), getContext().getString(
                                        R.string.vot_uploading,
                                        getContext().getString(targetName(targetLanguage))));
                            }
                        })))
                .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe(url -> {
                    if (mEnabled && generation == mGeneration && getPlayer() != null) play(url);
                }, error -> {
                    if (mEnabled && generation == mGeneration) {
                        Log.e(TAG, "Translation request failed: %s", error.getMessage());
                        boolean showError = !mStartedAutomatically;
                        stop();
                        if (showError) MessageHelpers.showMessage(getContext(),
                                error instanceof VotClient.AuthRequiredException
                                        ? R.string.vot_account_required : R.string.vot_unavailable);
                    }
                });
    }

    private void play(String url) {
        PlaybackView player = getPlayer();
        if (player == null) return;
        mAudio = new TranslationAudioPlayer(getContext());
        mAudio.setOnReadyListener(new TranslationAudioPlayer.OnReadyListener() {
            @Override
            public void onReady() {
                if (!mEnabled || getPlayer() == null) return;
                mPreviousVolume = getPlayer().getVolume();
                mPreferenceAtDuck = getPlayerData().getPlayerVolume();
                mDucked = true;
                mCurrentVolume = mPreviousVolume;
                mSpeechUntil = 0;
                Utils.postDelayed(mVolumeTick, 50);
                finishWaiting(true);
                sync();
                Utils.postDelayed(mSync, 1000);
                if (!mStartedAutomatically) MessageHelpers.showMessage(getContext(), R.string.vot_playing);
            }

            @Override
            public void onError(String message) {
                boolean showError = !mStartedAutomatically;
                stop();
                if (showError) MessageHelpers.showMessage(getContext(), R.string.vot_unavailable);
            }
        });
        try {
            mAudio.play(url, player.getPositionMs(), settings().getTranslationVolume() / 100f,
                    player.getSpeed());
        } catch (RuntimeException error) {
            Log.e(TAG, "Could not start translation audio: %s", error.getMessage());
            boolean showError = !mStartedAutomatically;
            stop();
            if (showError) MessageHelpers.showMessage(getContext(), R.string.vot_unavailable);
        }
    }

    private void applyDuck() {
        if (mDucked && getPlayer() != null) {
            if (getPlayerData().getPlayerVolume() != mPreferenceAtDuck) {
                mPreviousVolume = preferredVolume();
                mPreferenceAtDuck = getPlayerData().getPlayerVolume();
            }
            updateVolume();
        }
    }

    private void updateVolume() {
        if (!mDucked || mAudio == null || getPlayer() == null) return;
        mAudio.setVolume(settings().getTranslationVolume() / 100f);
        if (getPlayerData().getPlayerVolume() != mPreferenceAtDuck) {
            mPreviousVolume = preferredVolume();
            mPreferenceAtDuck = getPlayerData().getPlayerVolume();
        }
        long now = SystemClock.elapsedRealtime();
        if (mAudio.isReady() && getPlayer().isPlaying() && mAudio.getSpeechRms() >= 0.009) {
            mSpeechUntil = now + 850;
        }
        boolean shouldDuck = !settings().isAdaptiveVolumeEnabled() || now < mSpeechUntil;
        float cap = settings().getOriginalVolume() / 100f;
        if (settings().isStrongDuckingEnabled()) cap = Math.min(cap, 0.05f);
        float target = shouldDuck ? Math.min(mPreviousVolume, cap) : mPreviousVolume;
        float step = target < mCurrentVolume ? 0.45f : 0.083f;
        mCurrentVolume += (target - mCurrentVolume) * step;
        if (Math.abs(target - mCurrentVolume) < 0.002f) mCurrentVolume = target;
        if (Math.abs(getPlayer().getVolume() - mCurrentVolume) >= 0.002f)
            getPlayer().setVolume(mCurrentVolume);
    }

    private float preferredVolume() {
        float volume = getPlayerData().getPlayerVolume();
        Video video = getVideo();
        if (video != null) {
            if (getPlayerTweaksData().isPlayerAutoVolumeEnabled()) {
                volume = volume < 1f ? volume * video.volume : video.volume;
            }
            if (video.isShorts) volume /= 2f;
        }
        return volume;
    }

    public float volumeForControls(float currentVolume) {
        return mDucked ? mPreviousVolume : currentVolume;
    }

    public boolean setVolumeFromControls(float volume) {
        if (!mDucked) return false;
        mPreviousVolume = volume;
        updateVolume();
        return true;
    }

    private void sync() {
        PlaybackView player = getPlayer();
        if (player == null || mAudio == null || !mAudio.canSync()) return;
        if (player.isPlaying()) mAudio.resume(); else mAudio.pause();
        long position = player.getPositionMs();
        if (Math.abs(mAudio.getPositionMs() - position) > 750) mAudio.seekTo(position);
    }

    private void stop() {
        stop(true);
    }

    private void stop(boolean resumeVideo) {
        FormatItem original = mNativeOriginal;
        mNativeOriginal = null;
        mNativeDub = null;
        mEnabled = false;
        mStartedAutomatically = false;
        ++mGeneration;
        finishWaiting(resumeVideo);
        Utils.removeCallbacks(mSync);
        Utils.removeCallbacks(mVolumeTick);
        if (mClient != null) { mClient.cancel(); mClient = null; }
        if (mRequest != null) { mRequest.dispose(); mRequest = null; }
        if (mAudio != null) { mAudio.release(); mAudio = null; }
        if (original != null && getPlayer() != null) getPlayer().setFormat(original);
        if (mDucked && getPlayer() != null) {
            getPlayer().setVolume(getPlayerData().getPlayerVolume() == mPreferenceAtDuck
                    ? mPreviousVolume : preferredVolume());
        }
        mDucked = false;
        mSpeechUntil = 0;
        setButton(PlayerUI.BUTTON_OFF);
    }

    private void finishWaiting(boolean resumeVideo) {
        if (!mWaitingForAudio) return;
        PlaybackView player = getPlayer();
        boolean shouldResume = resumeVideo && mResumeAfterLoad && player != null;
        mWaitingForAudio = false;
        mResumeAfterLoad = false;
        if (player != null) {
            player.showVoiceOverProgress(false);
            if (shouldResume) player.setPlayWhenReady(true);
            else if (getScreensaverManager() != null) getScreensaverManager().enableChecked();
        }
    }

    private int targetName(String language) {
        if ("en".equals(language)) return R.string.vot_target_english;
        if ("kk".equals(language)) return R.string.vot_target_kazakh;
        return R.string.vot_target_russian;
    }

    private void setButton(int state) {
        if (getPlayer() != null) getPlayer().setButtonState(R.id.action_voice_translate, state);
    }

    @Override public void onNewVideo(Video item) {
        stop();
        mFormatInfo = null;
        mFormatInfoVideoId = null;
        mLoadedVideoId = null;
        mAutoTried = false;
        if (item == null || item.videoId == null || !item.videoId.equals(mSkippedVideoId)) {
            mSkippedVideoId = null;
        }
    }
    @Override public void onEngineReleased() {
        boolean resume = mWaitingForAudio && mResumeAfterLoad;
        stop(false);
        if (resume) getController(VideoStateController.class).setPlayEnabled(true);
        mFormatInfo = null;
        mFormatInfoVideoId = null;
        mLoadedVideoId = null;
        mAutoTried = false;
    }
    @Override public void onPlayEnd() { stop(false); mAutoTried = true; }
    @Override public void onFinish() { stop(false); }
    @Override public void onViewDestroyed() { stop(false); mAutoTried = false; mLoadedVideoId = null; }
    @Override public void onViewResumed() { sync(); maybeAutoStart(); }
    @Override public void onVideoLoaded(Video item) {
        mLoadedVideoId = item != null ? item.videoId : null;
        applyDuck();
        maybeAutoStart();
    }
    @Override public void onTrackChanged(FormatItem track) { applyDuck(); }
    @Override public void onTrackSelected(FormatItem track) {
        if (mNativeDub != null && track != null && track.getType() == FormatItem.TYPE_AUDIO
                && !mNativeDub.equals(track)) {
            Video video = getVideo();
            mSkippedVideoId = video != null ? video.videoId : null;
            mNativeOriginal = null; // Keep the audio track explicitly selected by the user.
            stop();
        }
    }
    public void onPlayRequested() {
        if (mWaitingForAudio) mResumeAfterLoad = true;
    }
    public void onPauseRequested() {
        if (mWaitingForAudio) mResumeAfterLoad = false;
    }
    @Override public void onPlayClicked() { onPlayRequested(); }
    @Override public void onPauseClicked() { onPauseRequested(); }
    @Override public void onPlay() {
        if (mWaitingForAudio && getPlayer() != null) {
            mResumeAfterLoad = getPlayer().getPlayWhenReady();
            getPlayer().setPlayWhenReady(false);
            if (getScreensaverManager() != null) getScreensaverManager().disableChecked();
            return;
        }
        sync();
        maybeAutoStart();
    }
    @Override public void onTickle() { maybeAutoStart(); }
    @Override public void onPause() {
        if (mWaitingForAudio && getScreensaverManager() != null)
            getScreensaverManager().disableChecked();
        if (mAudio != null) mAudio.pause();
    }
    @Override public void onBuffering() { if (mAudio != null) mAudio.pause(); }
    @Override public void onSeekEnd() { sync(); }
    @Override public void onSpeedChanged(float speed) { if (mAudio != null) mAudio.setPlaybackSpeed(speed); }
}

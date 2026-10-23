package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Annotation;
import android.text.SpannableStringBuilder;
import android.text.Spanned;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.ui.DualSubtitleCueMarkers;
import com.liskovsoft.sharedutils.mylogger.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Listens to processed cues from the primary {@link SubtitleManager}, translates
 * the text via {@link GoogleTranslateService}, and merges the translation into
 * the same cue so both original and translated text render as a single block.
 */
public final class SubtitleTranslator implements SubtitleManager.OnCuesProcessedListener {
    private static final String TAG = SubtitleTranslator.class.getSimpleName();

    /** Supplies current playback time for aligning timedtext translation rows with on-screen cues. */
    public interface PlaybackPositionSupplier {
        long getPositionMs();
    }

    /**
     * After the original line appears, wait at most this long before showing the translation
     * when the network response arrived earlier — gives a short beat to read the original first.
     */
    private static final long MIN_ORIGINAL_VISIBLE_BEFORE_MERGE_MS = 380;

    /**
     * If we estimate the next subtitle is due sooner than this, and the translation only just
     * became available late in this line's life, skip merging (unreadable flash).
     */
    private static final long SKIP_MERGE_IF_NEXT_CUE_WITHIN_MS = 650;

    /** Only apply {@link #SKIP_MERGE_IF_NEXT_CUE_WITHIN_MS} when the line was on screen this long. */
    private static final long LATE_TRANSLATION_MIN_LINE_AGE_MS = 750;

    /** Ignore intervals outside this range when learning average gap between subtitle lines. */
    private static final long MIN_CUE_GAP_MS = 250;
    private static final long MAX_CUE_GAP_MS = 30000;

    private final SubtitleManager mPrimarySubtitleManager;
    private final GoogleTranslateService mTranslateService;
    @Nullable private final YoutubeTimedTextDualSubtitleSource mTimedSource;
    @Nullable private final PlaybackPositionSupplier mPositionSupplier;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private String mLastOriginalText;
    private String mLastTranslatedText;
    private long mCueSequence;

    private long mLastOriginalChangeElapsed;
    private long mAvgMsBetweenOriginalChanges;

    private Runnable mPendingMerge;

    /** Log once per playback session when tlang path wins (avoids log spam). */
    private boolean mLoggedTlangUse;

    /**
     * When non-null, we wait for {@link #onYoutubeTimedTextPrefetchComplete()} before starting web
     * translate, so the first lines are not a mix of slow Google text and later official tlang.
     */
    private boolean mTimedPrefetchDone;
    public SubtitleTranslator(String targetLanguage, SubtitleManager primarySubtitleManager) {
        this(targetLanguage, primarySubtitleManager, null, null);
    }

    public SubtitleTranslator(
            String targetLanguage,
            SubtitleManager primarySubtitleManager,
            @Nullable YoutubeTimedTextDualSubtitleSource timedSource,
            @Nullable PlaybackPositionSupplier positionSupplier) {
        mPrimarySubtitleManager = primarySubtitleManager;
        mTranslateService = new GoogleTranslateService(targetLanguage);
        mTimedSource = timedSource;
        mPositionSupplier = positionSupplier;
        mTimedPrefetchDone = timedSource == null;
    }

    /**
     * Call from the main thread when {@link YoutubeTimedTextDualSubtitleSource#loadAsync(Runnable)}
     * finishes (success or failure). Applies a tlang merge for the current line or starts web
     * translate if tracks are unavailable.
     */
    public void onYoutubeTimedTextPrefetchComplete() {
        mTimedPrefetchDone = true;
        if (mLastOriginalText == null) {
            return;
        }
        if (mTimedSource != null && mTimedSource.isReady()) {
            List<Cue> merged = tryBuildTimedYoutubeTranslation(mLastOriginalText);
            if (merged != null) {
                mCueSequence++;
                mPrimarySubtitleManager.setCuesDirectly(merged);
                return;
            }
            // Timedtext loaded successfully but this specific line missed — don't fall back
            // to Google Translate; the next cue will likely hit via onCuesProcessed().
            return;
        }
        // Timedtext load failed entirely (isReady=false) — fall back to Google Translate.
        cancelPendingMerge();
        final long seq = ++mCueSequence;
        final String line = mLastOriginalText;
        mTranslateService.translate(line, translatedText -> {
            if (seq != mCueSequence) {
                return;
            }
            if (translatedText == null || translatedText.trim().isEmpty()) {
                return;
            }
            mLastTranslatedText = translatedText;
            scheduleMergedCue(line, translatedText, seq);
        });
    }

    @Override
    @Nullable
    public List<Cue> onCuesProcessed(List<Cue> processedCues) {
        if (processedCues == null || processedCues.isEmpty()) {
            cancelPendingMerge();
            mLastOriginalText = null;
            mLastTranslatedText = null;
            mLastOriginalChangeElapsed = 0;
            return null;
        }

        StringBuilder combined = new StringBuilder();
        for (Cue cue : processedCues) {
            if (cue.text != null) {
                if (combined.length() > 0) {
                    combined.append("\n");
                }
                combined.append(cue.text);
            }
        }

        String originalText = combined.toString().trim();
        if (originalText.isEmpty()) {
            cancelPendingMerge();
            mLastOriginalText = null;
            mLastTranslatedText = null;
            mLastOriginalChangeElapsed = 0;
            return null;
        }

        if (originalText.equals(mLastOriginalText)) {
            if (mLastTranslatedText != null) {
                return buildMergedCues(originalText, mLastTranslatedText);
            }
            return null;
        }

        cancelPendingMerge();
        long now = SystemClock.elapsedRealtime();
        if (mLastOriginalChangeElapsed != 0 && mLastOriginalText != null) {
            long gap = now - mLastOriginalChangeElapsed;
            if (gap >= MIN_CUE_GAP_MS && gap <= MAX_CUE_GAP_MS) {
                mAvgMsBetweenOriginalChanges =
                        mAvgMsBetweenOriginalChanges == 0
                                ? gap
                                : Math.round(0.35 * gap + 0.65 * mAvgMsBetweenOriginalChanges);
            }
        }
        mLastOriginalChangeElapsed = now;
        mLastOriginalText = originalText;

        List<Cue> timedMerged = tryBuildTimedYoutubeTranslation(originalText);
        if (timedMerged != null) {
            mCueSequence++;
            return timedMerged;
        }

        // Timedtext prefetch is still in progress — wait for it before starting web translate
        // so the first lines are not a mix of slow Google text and later official tlang.
        if (mTimedSource != null && !mTimedPrefetchDone) {
            mCueSequence++;
            return null;
        }

        // Timedtext is ready but lookup missed (timing gap, text mismatch, etc.).
        // Don't fall back to Google Translate on a per-line miss: the miss is likely transient
        // (e.g. a segment boundary) and a slow HTTP response would arrive too late to be readable
        // anyway, triggering SKIP_MERGE_IF_NEXT_CUE_WITHIN_MS and being silently dropped.
        if (mTimedSource != null && mTimedSource.isReady()) {
            mCueSequence++;
            return null;
        }

        final long seq = ++mCueSequence;

        mTranslateService.translate(originalText, translatedText -> {
            if (seq != mCueSequence) {
                return;
            }
            if (translatedText == null || translatedText.trim().isEmpty()) {
                return;
            }

            mLastTranslatedText = translatedText;
            scheduleMergedCue(originalText, translatedText, seq);
        });
        return null;
    }

    /**
     * Uses YouTube's {@code tlang} timedtext when loaded; avoids Google Translate latency and keeps
     * lines aligned with the official translation track.
     */
    @Nullable
    private List<Cue> tryBuildTimedYoutubeTranslation(String originalText) {
        if (mTimedSource == null || !mTimedSource.isReady()) {
            return null;
        }
        long pos = mPositionSupplier != null ? mPositionSupplier.getPositionMs() : -1L;
        String translated = mTimedSource.lookup(originalText, pos);
        if (translated == null || translated.trim().isEmpty()) {
            return null;
        }
        if (!mLoggedTlangUse) {
            mLoggedTlangUse = true;
            Log.d(TAG, "Dual subtitles: using YouTube tlang timedtext for this session (not web translate)");
        }
        mLastTranslatedText = translated;
        return buildMergedCues(originalText, translated);
    }

    private void scheduleMergedCue(String originalText, String translatedText, long seq) {
        cancelPendingMerge();
        long now = SystemClock.elapsedRealtime();
        if (shouldSkipMergeAsTooCloseToNextCue(now)) {
            return;
        }
        long elapsedSinceLine = now - mLastOriginalChangeElapsed;
        long wait = Math.max(0, MIN_ORIGINAL_VISIBLE_BEFORE_MERGE_MS - elapsedSinceLine);
        Runnable run =
                () -> {
                    if (seq != mCueSequence || !originalText.equals(mLastOriginalText)) {
                        return;
                    }
                    pushMergedCue(originalText, translatedText);
                };
        mPendingMerge = run;
        if (wait == 0) {
            mMainHandler.post(run);
        } else {
            mMainHandler.postDelayed(run, wait);
        }
    }

    /**
     * When translation latency is high, the response often lands right before the next subtitle.
     * If our running average says the next line is almost due, skip drawing the translation once
     * so the viewer is not shown a line they cannot read.
     */
    private boolean shouldSkipMergeAsTooCloseToNextCue(long now) {
        if (mAvgMsBetweenOriginalChanges < MIN_CUE_GAP_MS) {
            return false;
        }
        long expectedNextChange = mLastOriginalChangeElapsed + mAvgMsBetweenOriginalChanges;
        long untilNext = expectedNextChange - now;
        long elapsedOnLine = now - mLastOriginalChangeElapsed;
        return untilNext < SKIP_MERGE_IF_NEXT_CUE_WITHIN_MS
                && elapsedOnLine >= LATE_TRANSLATION_MIN_LINE_AGE_MS;
    }

    private void cancelPendingMerge() {
        if (mPendingMerge != null) {
            mMainHandler.removeCallbacks(mPendingMerge);
            mPendingMerge = null;
        }
    }

    private void pushMergedCue(String originalText, String translatedText) {
        mPrimarySubtitleManager.setCuesDirectly(buildMergedCues(originalText, translatedText));
    }

    private List<Cue> buildMergedCues(String originalText, String translatedText) {
        String plainTranslation = translatedText == null ? "" : translatedText.toString();
        SpannableStringBuilder ssb = new SpannableStringBuilder();
        ssb.append(originalText);
        ssb.append("\n");
        int translationStart = ssb.length();
        ssb.append(plainTranslation);
        ssb.setSpan(
                new Annotation(DualSubtitleCueMarkers.ANNOTATION_KEY, DualSubtitleCueMarkers.ANNOTATION_VALUE),
                translationStart,
                ssb.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        List<Cue> merged = new ArrayList<>();
        merged.add(new Cue(ssb));
        return merged;
    }

    public void setTargetLanguage(String language) {
        mTranslateService.setTargetLanguage(language);
        cancelPendingMerge();
        mLastOriginalText = null;
        mLastTranslatedText = null;
        mLastOriginalChangeElapsed = 0;
        mAvgMsBetweenOriginalChanges = 0;
    }

    public String getTargetLanguage() {
        return mTranslateService.getTargetLanguage();
    }

    public void release() {
        cancelPendingMerge();
        mTranslateService.shutdown();
        mLoggedTlangUse = false;
        if (mTimedSource != null) {
            mTimedSource.release();
        }
        mLastOriginalText = null;
        mLastTranslatedText = null;
        mLastOriginalChangeElapsed = 0;
        mAvgMsBetweenOriginalChanges = 0;
        mCueSequence++;
    }
}

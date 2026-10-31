package com.liskovsoft.smartyoutubetv2.common.exoplayer.errors;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer.DecoderInitializationException;
import com.google.android.exoplayer2.source.DefaultMediaSourceEventListener;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.source.chunk.Chunk;
import com.google.android.exoplayer2.source.chunk.ContainerMediaChunk;
import com.google.android.exoplayer2.upstream.HttpDataSource.InvalidResponseCodeException;
import com.google.android.exoplayer2.util.MimeTypes;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorManager;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track.MediaTrack;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class TrackErrorFixer extends DefaultMediaSourceEventListener {
    private static final int BLACKLIST_CHECK_MS = 1_000;
    private static final int BLACKLIST_CLEAR_MS = 10_000;
    private static final String TAG = TrackErrorFixer.class.getSimpleName();
    private final TrackSelectorManager mTrackSelectorManager;
    private long mSelectionTimeMs;
    private final Map<MediaTrack, Long> mBlacklistedTracks = new HashMap<>();
    private InvalidResponseCodeException mLastEx;

    public TrackErrorFixer(TrackSelectorManager trackSelectorManager) {
        mTrackSelectorManager = trackSelectorManager;
    }

    /**
     * 1) Blacklist non-playable audio tracks for live streams.<br/>
     * Last segment of such streams produce 404 error.<br/>
     * See DrLupo streams, for example.<br/.
     * <br/>
     * 2) Blacklist non-playable tracks for regular videos (error 503).<br/>
     */
    public boolean fixError(Exception e) {
        if (!(e instanceof InvalidResponseCodeException)) {
            return false;
        }

        InvalidResponseCodeException ex = (InvalidResponseCodeException) e;

        if (ex.responseCode != 404 && ex.responseCode != 503 && ex.responseCode != 500) {
            return false;
        }

        if (System.currentTimeMillis() - mSelectionTimeMs < BLACKLIST_CHECK_MS) {
            return false;
        }

        mLastEx = ex;

        return selectDifferentCodec(isAudio(mLastEx));
    }

    //public boolean fixError(ExoPlaybackException error) {
    //    if (error != null && error.getCause() instanceof DecoderInitializationException) {
    //        return selectDifferentCodec(MimeTypes.isAudio(((DecoderInitializationException) error.getCause()).mimeType));
    //    }
    //
    //    return false;
    //}

    private boolean selectDifferentCodec(boolean isAudio) {
        if (System.currentTimeMillis() - mSelectionTimeMs < BLACKLIST_CLEAR_MS) {
            return false;
        }

        Set<MediaTrack> tracks = isAudio ? mTrackSelectorManager.getAudioTracks() : mTrackSelectorManager.getVideoTracks();

        if (tracks == null) {
            return false;
        }

        MediaTrack currentTrack = null;

        for (MediaTrack track : tracks) {
            if (track.isSelected) {
                currentTrack = track;
                break;
            }
        }

        if (currentTrack == null || currentTrack.format == null || currentTrack.format.codecs == null) {
            return false;
        }

        String currentCodec = currentTrack.format.codecs;
        int width = currentTrack.format.width;

        MediaTrack nextTrack = null;

        for (MediaTrack track : tracks) {
            if (track.format == null) {
                continue;
            }

            if (!currentCodec.equals(track.format.codecs) && track.format.width <= width) {
                nextTrack = track;
                break;
            }
        }

        if (nextTrack != null) {
            mTrackSelectorManager.selectTrack(nextTrack);
            mSelectionTimeMs = System.currentTimeMillis();
            return true;
        }

        return false;
    }

    //private boolean selectNextTrack() {
    //    if (mLastEx == null) {
    //        return false;
    //    }
    //
    //    if (System.currentTimeMillis() - mSelectionTimeMs < BLACKLIST_CLEAR_MS) {
    //        return false;
    //    }
    //
    //    Set<MediaTrack> tracks = isAudio(mLastEx) ? mTrackSelectorManager.getAudioTracks() : mTrackSelectorManager.getVideoTracks();
    //
    //    if (tracks == null) {
    //        return false;
    //    }
    //
    //    MediaTrack nextTrack = null;
    //    boolean afterSelected = false;
    //
    //    for (MediaTrack track : tracks) {
    //        if (track.isSelected) {
    //            afterSelected = true;
    //            continue;
    //        }
    //
    //        if (afterSelected) {
    //            nextTrack = track;
    //            break;
    //        }
    //    }
    //
    //    if (nextTrack != null) {
    //        mTrackSelectorManager.selectTrack(nextTrack);
    //        return true;
    //    }
    //
    //    mSelectionTimeMs = System.currentTimeMillis();
    //    return false;
    //}
    //
    //private boolean selectNextTrackOld() {
    //    if (mLastEx == null) {
    //        return false;
    //    }
    //
    //    if (System.currentTimeMillis() - mSelectionTimeMs > BLACKLIST_CLEAR_MS) {
    //        mBlacklistedTracks.clear();
    //    }
    //
    //    Set<MediaTrack> tracks = isAudio(mLastEx) ? mTrackSelectorManager.getAudioTracks() : mTrackSelectorManager.getVideoTracks();
    //    MediaTrack tmpTrack = null;
    //
    //    if (tracks == null) {
    //        return false;
    //    }
    //
    //    for (MediaTrack track : tracks) {
    //        if (track.isSelected) {
    //            addToBlacklist(track);
    //        } else {
    //            tmpTrack = track;
    //            if (!isBlacklisted(track)) {
    //                addToBlacklist(track);
    //                break;
    //            }
    //        }
    //    }
    //
    //    if (tmpTrack != null) {
    //        mTrackSelectorManager.selectTrack(tmpTrack);
    //        mSelectionTimeMs = System.currentTimeMillis();
    //        //Utils.postDelayed(mHandler, mSelectFirstTrack, SELECT_FIRST_TRACK_MS);
    //        return true;
    //    }
    //
    //    return false;
    //}
    //
    //private void selectFirstTrack() {
    //    if (mLastEx == null) {
    //        return;
    //    }
    //
    //    mBlacklistedTracks.clear();
    //
    //    Set<MediaTrack> tracks = isAudio(mLastEx) ? mTrackSelectorManager.getAudioTracks() : mTrackSelectorManager.getVideoTracks();
    //    MediaTrack tmpTrack = null;
    //
    //    for (MediaTrack track : tracks) {
    //        addToBlacklist(track);
    //        tmpTrack = track;
    //        break;
    //    }
    //
    //    if (tmpTrack != null) {
    //        mTrackSelectorManager.selectTrack(tmpTrack);
    //        mSelectionTimeMs = System.currentTimeMillis();
    //    }
    //}

    private boolean isAudio(InvalidResponseCodeException ex) {
        String url = ex.dataSpec.uri.toString();

        return url.contains("mime/audio");
    }

    private boolean isBlacklisted(MediaTrack track) {
        for (Entry<MediaTrack, Long> entry : mBlacklistedTracks.entrySet()) {
            if (track == entry.getKey() && System.currentTimeMillis() - entry.getValue() < BLACKLIST_CLEAR_MS) {
                return true;
            }
        }

        return false;
    }

    private void addToBlacklist(MediaTrack track) {
        mBlacklistedTracks.put(track, System.currentTimeMillis());
    }

    public void fixEmptyChunk(Chunk chunk) {
        // Fix when just started new type live stream ahead of the position
        if (chunk instanceof ContainerMediaChunk) {
            long nextLoadPosition = (Long) Helpers.getField(chunk, "nextLoadPosition");
            if (nextLoadPosition == 0) {
                Log.e(TAG, "Stream position behind the timeline. Waiting for new data...");
                try {
                    Thread.sleep(10_000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void onLoadError(int windowIndex, @Nullable MediaPeriodId mediaPeriodId, LoadEventInfo loadEventInfo,
                            MediaLoadData mediaLoadData, IOException error, boolean wasCanceled) {
        fixError(error);
    }
}

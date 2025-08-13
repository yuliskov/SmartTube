package com.google.android.exoplayer2.source.sabr;

import android.os.Handler;
import android.os.Message;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.source.SampleQueue;
import com.google.android.exoplayer2.source.sabr.manifest.SabrManifest;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.util.ParsableByteArray;

import java.io.IOException;

public final class PlayerEmsgHandler implements Handler.Callback {
    /** Callbacks for player emsg events encountered during DASH live stream. */
    public interface PlayerEmsgCallback {

        /** Called when the current manifest should be refreshed. */
        void onDashManifestRefreshRequested();

        /**
         * Called when the manifest with the publish time has been expired.
         *
         * @param expiredManifestPublishTimeUs The manifest publish time that has been expired.
         */
        void onDashManifestPublishTimeExpired(long expiredManifestPublishTimeUs);
    }

    private final Allocator allocator;
    private final PlayerEmsgCallback playerEmsgCallback;
    private SabrManifest manifest;

    /**
     * @param manifest The initial manifest.
     * @param playerEmsgCallback The callback that this event handler can invoke when handling emsg
     *     messages that generate DASH media source events.
     * @param allocator An {@link Allocator} from which allocations can be obtained.
     */
    public PlayerEmsgHandler(
            SabrManifest manifest, PlayerEmsgCallback playerEmsgCallback, Allocator allocator) {
        this.manifest = manifest;
        this.playerEmsgCallback = playerEmsgCallback;
        this.allocator = allocator;
    }


    @Override
    public boolean handleMessage(@NonNull Message msg) {
        return false;
    }

    /** Returns a {@link TrackOutput} that emsg messages could be written to. */
    public PlayerTrackEmsgHandler newPlayerTrackEmsgHandler() {
        return new PlayerTrackEmsgHandler(new SampleQueue(allocator));
    }

    /** Handles emsg messages for a specific track for the player. */
    public static final class PlayerTrackEmsgHandler implements TrackOutput {

        public PlayerTrackEmsgHandler(SampleQueue sampleQueue) {
            
        }

        @Override
        public void format(Format format) {
            
        }

        @Override
        public int sampleData(ExtractorInput input, int length, boolean allowEndOfInput) throws IOException, InterruptedException {
            return 0;
        }

        @Override
        public void sampleData(ParsableByteArray data, int length) {

        }

        @Override
        public void sampleMetadata(long timeUs, int flags, int size, int offset, @Nullable CryptoData encryptionData) {

        }
    }
}

package com.google.android.exoplayer2.source.sabr.parser;

import android.util.SparseArray;

import androidx.annotation.IntDef;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.source.sabr.parser.parts.FormatInitializedSabrPart;
import com.google.android.exoplayer2.source.sabr.parser.parts.MediaSegmentDataSabrPart;
import com.google.android.exoplayer2.source.sabr.parser.parts.MediaSegmentEndSabrPart;
import com.google.android.exoplayer2.source.sabr.parser.parts.MediaSegmentInitSabrPart;
import com.google.android.exoplayer2.source.sabr.parser.parts.SabrPart;
import com.google.android.exoplayer2.util.LongArray;
import com.google.android.exoplayer2.util.NalUnitUtil;
import com.google.android.exoplayer2.util.ParsableByteArray;

import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;

public class SabrExtractor implements Extractor {
    /**
     * Flags controlling the behavior of the extractor. Possible flag value is {@link
     * #FLAG_DISABLE_SEEK_FOR_CUES}.
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            flag = true,
            value = {FLAG_DISABLE_SEEK_FOR_CUES})
    public @interface Flags {}
    /**
     * Flag to disable seeking for cues.
     * <p>
     * Normally (i.e. when this flag is not set) the extractor will seek to the cues element if its
     * position is specified in the seek head and if it's after the first cluster. Setting this flag
     * disables seeking to the cues element. If the cues element is after the first cluster then the
     * media is treated as being unseekable.
     */
    public static final int FLAG_DISABLE_SEEK_FOR_CUES = 1;

    private static final int VORBIS_MAX_INPUT_SIZE = 8192;
    private static final int OPUS_MAX_INPUT_SIZE = 5760;
    private static final int ENCRYPTION_IV_SIZE = 8;
    private static final int TRACK_TYPE_AUDIO = 2;

    private static final int BLOCK_STATE_START = 0;
    private static final int BLOCK_STATE_HEADER = 1;
    private static final int BLOCK_STATE_DATA = 2;

    //private final EbmlReader reader;
    //private final VarintReader varintReader;
    private final SabrStream sabrStream;
    private final SparseArray<Track> tracks;
    private final boolean seekForCuesEnabled;

    // Temporary arrays.
    private final ParsableByteArray nalStartCode;
    private final ParsableByteArray nalLength;
    private final ParsableByteArray scratch;
    private final ParsableByteArray vorbisNumPageSamples;
    private final ParsableByteArray seekEntryIdBytes;
    private final ParsableByteArray sampleStrippedBytes;
    private final ParsableByteArray subtitleSample;
    private final ParsableByteArray encryptionInitializationVector;
    private final ParsableByteArray encryptionSubsampleData;
    private ByteBuffer encryptionSubsampleDataBuffer;

    private long segmentContentSize;
    private long segmentContentPosition = C.POSITION_UNSET;
    private long timecodeScale = C.TIME_UNSET;
    private long durationTimecode = C.TIME_UNSET;
    private long durationUs = C.TIME_UNSET;

    // The track corresponding to the current TrackEntry element, or null.
    private Track currentTrack;

    // Whether a seek map has been sent to the output.
    private boolean sentSeekMap;

    // Master seek entry related elements.
    private int seekEntryId;
    private long seekEntryPosition;

    // Cue related elements.
    private boolean seekForCues;
    private long cuesContentPosition = C.POSITION_UNSET;
    private long seekPositionAfterBuildingCues = C.POSITION_UNSET;
    private long clusterTimecodeUs = C.TIME_UNSET;
    private LongArray cueTimesUs;
    private LongArray cueClusterPositions;
    private boolean seenClusterPositionForCurrentCuePoint;

    // Block reading state.
    private int blockState;
    private long blockTimeUs;
    private long blockDurationUs;
    private int blockLacingSampleIndex;
    private int blockLacingSampleCount;
    private int[] blockLacingSampleSizes;
    private int blockTrackNumber;
    private int blockTrackNumberLength;
    @C.BufferFlags
    private int blockFlags;

    // Sample reading state.
    private int sampleBytesRead;
    private boolean sampleEncodingHandled;
    private boolean sampleSignalByteRead;
    private boolean sampleInitializationVectorRead;
    private boolean samplePartitionCountRead;
    private byte sampleSignalByte;
    private int samplePartitionCount;
    private int sampleCurrentNalBytesRemaining;
    private int sampleBytesWritten;
    private boolean sampleRead;
    private boolean sampleSeenReferenceBlock;

    // Extractor outputs.
    private ExtractorOutput extractorOutput;

    public SabrExtractor() {
        this(0);
    }

    private SabrExtractor(@Flags int flags) {
        // TODO: replace nulls with the actual values
        sabrStream = new SabrStream(
                null,
                null,
                null,
                null,
                null,
                null,
                -1,
                -1,
                -1,
                null,
                false,
                null
        );
        seekForCuesEnabled = (flags & FLAG_DISABLE_SEEK_FOR_CUES) == 0;
        tracks = new SparseArray<>();
        scratch = new ParsableByteArray(4);
        vorbisNumPageSamples = new ParsableByteArray(ByteBuffer.allocate(4).putInt(-1).array());
        seekEntryIdBytes = new ParsableByteArray(4);
        nalStartCode = new ParsableByteArray(NalUnitUtil.NAL_START_CODE);
        nalLength = new ParsableByteArray(4);
        sampleStrippedBytes = new ParsableByteArray();
        subtitleSample = new ParsableByteArray();
        encryptionInitializationVector = new ParsableByteArray(ENCRYPTION_IV_SIZE);
        encryptionSubsampleData = new ParsableByteArray();
    }

    @Override
    public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
        // TODO: not implemented
        return true;
    }

    @Override
    public void init(ExtractorOutput output) {
        extractorOutput = output;
    }

    @Override
    public int read(ExtractorInput input, PositionHolder seekPosition)
            throws IOException, InterruptedException {
        sampleRead = false;
        boolean continueReading = true;
        while (continueReading && !sampleRead) {
            SabrPart sabrPart = sabrStream.parse(input);
            continueReading = sabrPart != null;

            if (sabrPart instanceof FormatInitializedSabrPart) {
                // TODO: not initialized
            } else if (sabrPart instanceof MediaSegmentInitSabrPart) {
                // TODO: not initialized
            } else if (sabrPart instanceof MediaSegmentDataSabrPart) {
                // TODO: not initialized
            } else if (sabrPart instanceof MediaSegmentEndSabrPart) {
                // TODO: not initialized
            }

            if (continueReading && maybeSeekForCues(seekPosition, input.getPosition())) {
                return Extractor.RESULT_SEEK;
            }
        }
        if (!continueReading) {
            return Extractor.RESULT_END_OF_INPUT;
        }
        return Extractor.RESULT_CONTINUE;
    }

    @Override
    public void seek(long position, long timeUs) {
        // TODO: not implemented
        //clusterTimecodeUs = C.TIME_UNSET;
        //blockState = BLOCK_STATE_START;
        //reader.reset();
        //varintReader.reset();
        //resetSample();
        //for (int i = 0; i < tracks.size(); i++) {
        //    tracks.valueAt(i).reset();
        //}
    }

    @Override
    public void release() {
        // TODO: not implemented
    }

    private void resetSample() {
        sampleBytesRead = 0;
        sampleBytesWritten = 0;
        sampleCurrentNalBytesRemaining = 0;
        sampleEncodingHandled = false;
        sampleSignalByteRead = false;
        samplePartitionCountRead = false;
        samplePartitionCount = 0;
        sampleSignalByte = (byte) 0;
        sampleInitializationVectorRead = false;
        sampleStrippedBytes.reset();
    }

    /**
     * Updates the position of the holder to Cues element's position if the extractor configuration
     * permits use of master seek entry. After building Cues sets the holder's position back to where
     * it was before.
     *
     * @param seekPosition The holder whose position will be updated.
     * @param currentPosition Current position of the input.
     * @return Whether the seek position was updated.
     */
    private boolean maybeSeekForCues(PositionHolder seekPosition, long currentPosition) {
        if (seekForCues) {
            seekPositionAfterBuildingCues = currentPosition;
            seekPosition.position = cuesContentPosition;
            seekForCues = false;
            return true;
        }
        // After parsing Cues, seek back to original position if available. We will not do this unless
        // we seeked to get to the Cues in the first place.
        if (sentSeekMap && seekPositionAfterBuildingCues != C.POSITION_UNSET) {
            seekPosition.position = seekPositionAfterBuildingCues;
            seekPositionAfterBuildingCues = C.POSITION_UNSET;
            return true;
        }
        return false;
    }

    private void commitSampleToOutput(Track track, long timeUs) {
        track.output.sampleMetadata(timeUs, blockFlags, sampleBytesWritten, 0, track.cryptoData);
        sampleRead = true;
        resetSample();
    }

    private static final class Track {
        private static final int DISPLAY_UNIT_PIXELS = 0;
        private static final int MAX_CHROMATICITY = 50000;  // Defined in CTA-861.3.
        /**
         * Default max content light level (CLL) that should be encoded into hdrStaticInfo.
         */
        private static final int DEFAULT_MAX_CLL = 1000;  // nits.

        /**
         * Default frame-average light level (FALL) that should be encoded into hdrStaticInfo.
         */
        private static final int DEFAULT_MAX_FALL = 200;  // nits.

        // Common elements.
        public String name;
        public String codecId;
        public int number;
        public int type;
        public int defaultSampleDurationNs;
        public boolean hasContentEncryption;
        public byte[] sampleStrippedBytes;
        public TrackOutput.CryptoData cryptoData;
        public byte[] codecPrivate;
        public DrmInitData drmInitData;

        // Video elements.
        public int width = Format.NO_VALUE;
        public int height = Format.NO_VALUE;
        public int displayWidth = Format.NO_VALUE;
        public int displayHeight = Format.NO_VALUE;
        public int displayUnit = DISPLAY_UNIT_PIXELS;
        @C.Projection public int projectionType = Format.NO_VALUE;
        public float projectionPoseYaw = 0f;
        public float projectionPosePitch = 0f;
        public float projectionPoseRoll = 0f;
        public byte[] projectionData = null;
        @C.StereoMode
        public int stereoMode = Format.NO_VALUE;
        public boolean hasColorInfo = false;
        @C.ColorSpace
        public int colorSpace = Format.NO_VALUE;
        @C.ColorTransfer
        public int colorTransfer = Format.NO_VALUE;
        @C.ColorRange
        public int colorRange = Format.NO_VALUE;
        public int maxContentLuminance = DEFAULT_MAX_CLL;
        public int maxFrameAverageLuminance = DEFAULT_MAX_FALL;
        public float primaryRChromaticityX = Format.NO_VALUE;
        public float primaryRChromaticityY = Format.NO_VALUE;
        public float primaryGChromaticityX = Format.NO_VALUE;
        public float primaryGChromaticityY = Format.NO_VALUE;
        public float primaryBChromaticityX = Format.NO_VALUE;
        public float primaryBChromaticityY = Format.NO_VALUE;
        public float whitePointChromaticityX = Format.NO_VALUE;
        public float whitePointChromaticityY = Format.NO_VALUE;
        public float maxMasteringLuminance = Format.NO_VALUE;
        public float minMasteringLuminance = Format.NO_VALUE;

        // Audio elements. Initially set to their default values.
        public int channelCount = 1;
        public int audioBitDepth = Format.NO_VALUE;
        public int sampleRate = 8000;
        public long codecDelayNs = 0;
        public long seekPreRollNs = 0;

        // Set when the output is initialized. nalUnitLengthFieldLength is only set for H264/H265.
        public TrackOutput output;
        public int nalUnitLengthFieldLength;

        // TODO: not implemented
    }
}

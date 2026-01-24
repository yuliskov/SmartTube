package com.google.android.exoplayer2.source.sabr.parser.core;

import android.util.Pair;
import android.util.SparseArray;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.extractor.ChunkIndex;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.MpegAudioHeader;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.source.sabr.parser.parts.FormatInitializedSabrPart;
import com.google.android.exoplayer2.source.sabr.parser.parts.MediaSegmentDataSabrPart;
import com.google.android.exoplayer2.source.sabr.parser.parts.MediaSegmentEndSabrPart;
import com.google.android.exoplayer2.source.sabr.parser.parts.MediaSegmentInitSabrPart;
import com.google.android.exoplayer2.source.sabr.parser.parts.SabrPart;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.LongArray;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.NalUnitUtil;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.ColorInfo;

import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

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

    private static final String TAG = SabrExtractor.class.getSimpleName();

    private static final int VORBIS_MAX_INPUT_SIZE = 8192;
    private static final int OPUS_MAX_INPUT_SIZE = 5760;
    private static final int ENCRYPTION_IV_SIZE = 8;
    private static final int TRACK_TYPE_AUDIO = 2;

    private static final int BLOCK_STATE_START = 0;
    private static final int BLOCK_STATE_HEADER = 1;
    private static final int BLOCK_STATE_DATA = 2;

    private static final String CODEC_ID_VP8 = "V_VP8";
    private static final String CODEC_ID_VP9 = "V_VP9";
    private static final String CODEC_ID_AV1 = "V_AV1";
    private static final String CODEC_ID_MPEG2 = "V_MPEG2";
    private static final String CODEC_ID_MPEG4_SP = "V_MPEG4/ISO/SP";
    private static final String CODEC_ID_MPEG4_ASP = "V_MPEG4/ISO/ASP";
    private static final String CODEC_ID_MPEG4_AP = "V_MPEG4/ISO/AP";
    private static final String CODEC_ID_H264 = "V_MPEG4/ISO/AVC";
    private static final String CODEC_ID_H265 = "V_MPEGH/ISO/HEVC";
    private static final String CODEC_ID_FOURCC = "V_MS/VFW/FOURCC";
    private static final String CODEC_ID_THEORA = "V_THEORA";
    private static final String CODEC_ID_VORBIS = "A_VORBIS";
    private static final String CODEC_ID_OPUS = "A_OPUS";
    private static final String CODEC_ID_AAC = "A_AAC";
    private static final String CODEC_ID_MP2 = "A_MPEG/L2";
    private static final String CODEC_ID_MP3 = "A_MPEG/L3";
    private static final String CODEC_ID_AC3 = "A_AC3";
    private static final String CODEC_ID_E_AC3 = "A_EAC3";
    private static final String CODEC_ID_TRUEHD = "A_TRUEHD";
    private static final String CODEC_ID_DTS = "A_DTS";
    private static final String CODEC_ID_DTS_EXPRESS = "A_DTS/EXPRESS";
    private static final String CODEC_ID_DTS_LOSSLESS = "A_DTS/LOSSLESS";
    private static final String CODEC_ID_FLAC = "A_FLAC";
    private static final String CODEC_ID_ACM = "A_MS/ACM";
    private static final String CODEC_ID_PCM_INT_LIT = "A_PCM/INT/LIT";
    private static final String CODEC_ID_SUBRIP = "S_TEXT/UTF8";
    private static final String CODEC_ID_ASS = "S_TEXT/ASS";
    private static final String CODEC_ID_VOBSUB = "S_VOBSUB";
    private static final String CODEC_ID_PGS = "S_HDMV/PGS";
    private static final String CODEC_ID_DVBSUB = "S_DVBSUB";

    private static final int FOURCC_COMPRESSION_DIVX = 0x58564944;
    private static final int FOURCC_COMPRESSION_H263 = 0x33363248;
    private static final int FOURCC_COMPRESSION_VC1 = 0x31435657;

    /**
     * A template for the prefix that must be added to each subrip sample. The 12 byte end timecode
     * starting at {@link #SUBRIP_PREFIX_END_TIMECODE_OFFSET} is set to a dummy value, and must be
     * replaced with the duration of the subtitle.
     * <p>
     * Equivalent to the UTF-8 string: "1\n00:00:00,000 --> 00:00:00,000\n".
     */
    private static final byte[] SUBRIP_PREFIX = new byte[] {49, 10, 48, 48, 58, 48, 48, 58, 48, 48,
            44, 48, 48, 48, 32, 45, 45, 62, 32, 48, 48, 58, 48, 48, 58, 48, 48, 44, 48, 48, 48, 10};
    /**
     * The byte offset of the end timecode in {@link #SUBRIP_PREFIX}.
     */
    private static final int SUBRIP_PREFIX_END_TIMECODE_OFFSET = 19;
    /**
     * A special end timecode indicating that a subrip subtitle should be displayed until the next
     * subtitle, or until the end of the media in the case of the last subtitle.
     * <p>
     * Equivalent to the UTF-8 string: "            ".
     */
    private static final byte[] SUBRIP_TIMECODE_EMPTY =
            new byte[] {32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32};
    /**
     * The value by which to divide a time in microseconds to convert it to the unit of the last value
     * in a subrip timecode (milliseconds).
     */
    private static final long SUBRIP_TIMECODE_LAST_VALUE_SCALING_FACTOR = 1000;
    /**
     * The format of a subrip timecode.
     */
    private static final String SUBRIP_TIMECODE_FORMAT = "%02d:%02d:%02d,%03d";

    /**
     * Matroska specific format line for SSA subtitles.
     */
    private static final byte[] SSA_DIALOGUE_FORMAT = Util.getUtf8Bytes("Format: Start, End, "
            + "ReadOrder, Layer, Style, Name, MarginL, MarginR, MarginV, Effect, Text");
    /**
     * A template for the prefix that must be added to each SSA sample. The 10 byte end timecode
     * starting at {@link #SSA_PREFIX_END_TIMECODE_OFFSET} is set to a dummy value, and must be
     * replaced with the duration of the subtitle.
     * <p>
     * Equivalent to the UTF-8 string: "Dialogue: 0:00:00:00,0:00:00:00,".
     */
    private static final byte[] SSA_PREFIX = new byte[] {68, 105, 97, 108, 111, 103, 117, 101, 58, 32,
            48, 58, 48, 48, 58, 48, 48, 58, 48, 48, 44, 48, 58, 48, 48, 58, 48, 48, 58, 48, 48, 44};
    /**
     * The byte offset of the end timecode in {@link #SSA_PREFIX}.
     */
    private static final int SSA_PREFIX_END_TIMECODE_OFFSET = 21;
    /**
     * The value by which to divide a time in microseconds to convert it to the unit of the last value
     * in an SSA timecode (1/100ths of a second).
     */
    private static final long SSA_TIMECODE_LAST_VALUE_SCALING_FACTOR = 10000;
    /**
     * A special end timecode indicating that an SSA subtitle should be displayed until the next
     * subtitle, or until the end of the media in the case of the last subtitle.
     * <p>
     * Equivalent to the UTF-8 string: "          ".
     */
    private static final byte[] SSA_TIMECODE_EMPTY =
            new byte[] {32, 32, 32, 32, 32, 32, 32, 32, 32, 32};
    /**
     * The format of an SSA timecode.
     */
    private static final String SSA_TIMECODE_FORMAT = "%01d:%02d:%02d:%02d";

    /**
     * The length in bytes of a WAVEFORMATEX structure.
     */
    private static final int WAVE_FORMAT_SIZE = 18;
    /**
     * Format tag indicating a WAVEFORMATEXTENSIBLE structure.
     */
    private static final int WAVE_FORMAT_EXTENSIBLE = 0xFFFE;
    /**
     * Format tag for PCM.
     */
    private static final int WAVE_FORMAT_PCM = 1;
    /**
     * Sub format for PCM.
     */
    private static final UUID WAVE_SUBFORMAT_PCM = new UUID(0x0100000000001000L, 0x800000AA00389B71L);

    //private final EbmlReader reader;
    //private final VarintReader varintReader;
    private final SabrStream sabrStream;
    private final SparseArray<Track> tracks;
    private final boolean seekForCuesEnabled;
    private Format format;
    private final int trackType;
    private int readNum;
    private int partNum;

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

    /**
     * @param trackType The type of the track. Typically one of the {@link C}
     *    {@code TRACK_TYPE_*} constants.
     */
    public SabrExtractor(int trackType, @NonNull Format format, @NonNull SabrStream sabrStream) {
        this(0, trackType, format, sabrStream);
    }

    private SabrExtractor(@Flags int flags, int trackType, @NonNull Format format, @NonNull SabrStream sabrStream) {
        this.sabrStream = sabrStream;
        this.format = format;
        this.trackType = trackType;
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
        // NOTE: checks whether the input contains SABR stream
        return true; // always ok
    }

    @Override
    public void init(ExtractorOutput output) {
        extractorOutput = output;
    }

    @Override
    public int read(ExtractorInput input, PositionHolder seekPosition)
            throws IOException, InterruptedException {

        Log.e(TAG, "Start SABR read: track=" + trackType + ", readNum=" + readNum++);

        sampleRead = false;
        boolean continueReading = true;
        while (continueReading && !sampleRead) {
            SabrPart sabrPart = sabrStream.parse(input);
            continueReading = sabrPart != null;

            // Debug
            //if (sabrPart instanceof MediaSegmentDataSabrPart) {
            //    MediaSegmentDataSabrPart data = (MediaSegmentDataSabrPart) sabrPart;
            //    Log.e(TAG, "Consumed contentLength: " + data.contentLength);
            //    data.data.skipFully(data.contentLength);
            //}

            if (sabrPart instanceof FormatInitializedSabrPart) {
                initializeFormat((FormatInitializedSabrPart) sabrPart);
            } else if (sabrPart instanceof MediaSegmentInitSabrPart) {
                initializeSegment((MediaSegmentInitSabrPart) sabrPart);
            } else if (sabrPart instanceof MediaSegmentDataSabrPart) {
                writeSegmentData((MediaSegmentDataSabrPart) sabrPart);
            } else if (sabrPart instanceof MediaSegmentEndSabrPart) {
                endSegment((MediaSegmentEndSabrPart) sabrPart);
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
        clusterTimecodeUs = C.TIME_UNSET;
        blockState = BLOCK_STATE_START;
        sabrStream.reset();
        resetSample();
        for (int i = 0; i < tracks.size(); i++) {
            tracks.valueAt(i).reset();
        }
    }

    @Override
    public void release() {
        // Do nothing
    }

    private void initializeFormat(FormatInitializedSabrPart part) throws ParserException {
        //startMasterElement
        //endMasterElement

        format = part.formatSelector.getSelectedFormat();

        initCurrentTrack();

        // TODO: init seekMap
        extractorOutput.seekMap(new SeekMap.Unseekable(part.endTimeMs != -1 ? part.endTimeMs * 1_000L : C.TIME_UNSET));

        initExtractorOutput();
    }

    private void initializeSegment(MediaSegmentInitSabrPart part) {
        // TODO: not implemented
        Log.e(TAG, "Begin segment: track=" + trackType + ", segNum=" + part.sequenceNumber + ", startTimeMs=" + part.startTimeMs + ", durationMs=" + part.durationMs);
    }

    private void writeSegmentData(MediaSegmentDataSabrPart part) throws IOException, InterruptedException {
        // binaryElement

        Log.e(TAG, "Received SABR part: track=" + trackType + ", partNum=" + partNum++ + ", partLength=" + part.contentLength + ", partOffset=" + part.segmentStartBytes);

        // TODO: init seek segment data

        // TODO: hardcoded blockTrackNumber is bad
        //Track track = tracks.get(blockTrackNumber);
        Track track = tracks.get(1);

        // Ignore the block if we don't know about the track to which it belongs.
        if (track == null) {
            part.data.skipFully(part.contentLength);
            return;
        }

        writeSampleData(part.data, track, part.contentLength);

        //long sampleTimeUs = blockTimeUs
        //        + (part.sequenceNumber * track.defaultSampleDurationNs) / 1000;
        //long sampleTimeUs = part.startTimeMs * 1_000L;
        //commitSampleToOutput(track, sampleTimeUs);

        // NOTE: Required to avoid infinite write loop inside 'writeSampleData'! This what 'commitSampleToOutput' actually does.
        sampleRead = true;
        resetSample();
    }

    private void endSegment(MediaSegmentEndSabrPart part) {
        Log.e(TAG, "End segment: track=" + trackType + ", segNum=" + part.sequenceNumber + ", startTimeMs=" + part.startTimeMs + ", durationMs=" + part.durationMs);

        // TODO: not fully implemented?
        //extractorOutput.endTracks(); // commit all written data

        Track track = tracks.get(1);
        long timeUs = part.startTimeMs * 1_000L;
        track.output.sampleMetadata(timeUs, blockFlags, sampleBytesWritten, 0, track.cryptoData);
        sampleBytesWritten = 0;
        //commitSampleToOutput(track, timeUs);
    }

    private void initCurrentTrack() {
        currentTrack = new Track();

        currentTrack.width = format.width;
        currentTrack.height = format.height;
        currentTrack.number = 1;
        currentTrack.type = trackType; // TODO: possibly wrong type
        currentTrack.channelCount = format.channelCount;
        currentTrack.stereoMode = format.stereoMode;
        currentTrack.sampleRate = format.sampleRate;
        currentTrack.name = format.label; // TODO: possibly wrong name
        currentTrack.codecId = translate(format.codecs); // TODO: possibly wrong codec id
        currentTrack.language = format.language;

        if (format.colorInfo != null) {
            currentTrack.hasColorInfo = true;
            currentTrack.colorSpace = format.colorInfo.colorSpace;
            currentTrack.colorTransfer = format.colorInfo.colorTransfer;
            currentTrack.colorRange = format.colorInfo.colorRange;
        }

        // TODO: init values taken from the sabr parts
        currentTrack.defaultSampleDurationNs = -1;
        currentTrack.codecDelayNs = 0;
        currentTrack.seekPreRollNs = 0;
        currentTrack.audioBitDepth = Format.NO_VALUE;

        // TODO: maybe init more fields
    }

    private void initExtractorOutput() throws ParserException {
        String codecId = currentTrack.codecId;
        if (isCodecSupported(codecId)) {
            currentTrack.initializeOutput(extractorOutput, currentTrack.number);
            tracks.put(currentTrack.number, currentTrack);
        }
        currentTrack = null;

        // We have a single track per SABR stream
        if (tracks.size() == 0) {
            throw new ParserException("No valid tracks were found for codec: " + codecId);
        }
        extractorOutput.endTracks();
    }

    private String translate(String codecId) {
        if (codecId == null) {
            return null;
        }

        if (codecId.startsWith("opus")) {
            return CODEC_ID_OPUS;
        } else if (codecId.startsWith("vp9")) {
            return CODEC_ID_VP9;
        } else if (codecId.startsWith("avc")) {
            return CODEC_ID_H264;
        } else if (codecId.startsWith("mp4a")) {
            return CODEC_ID_AAC;
        }

        return codecId;
    }

    private void commitSampleToOutput(Track track, long timeUs) {
        track.output.sampleMetadata(timeUs, blockFlags, sampleBytesWritten, 0, track.cryptoData);
        sampleRead = true;
        resetSample();
    }

    private void resetSample() {
        sampleBytesRead = 0;
        //sampleBytesWritten = 0;
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
     * Ensures {@link #scratch} contains at least {@code requiredLength} bytes of data, reading from
     * the extractor input if necessary.
     */
    private void readScratch(ExtractorInput input, int requiredLength)
            throws IOException, InterruptedException {
        if (scratch.limit() >= requiredLength) {
            return;
        }
        if (scratch.capacity() < requiredLength) {
            scratch.reset(Arrays.copyOf(scratch.data, Math.max(scratch.data.length * 2, requiredLength)),
                    scratch.limit());
        }
        input.readFully(scratch.data, scratch.limit(), requiredLength - scratch.limit());
        scratch.setLimit(requiredLength);
    }

    private void writeSampleData(ExtractorInput input, Track track, int size)
            throws IOException, InterruptedException {
        if (CODEC_ID_SUBRIP.equals(track.codecId)) {
            writeSubtitleSampleData(input, SUBRIP_PREFIX, size);
            return;
        } else if (CODEC_ID_ASS.equals(track.codecId)) {
            writeSubtitleSampleData(input, SSA_PREFIX, size);
            return;
        }

        TrackOutput output = track.output;
        if (!sampleEncodingHandled) {
            if (track.hasContentEncryption) {
                // If the sample is encrypted, read its encryption signal byte and set the IV size.
                // Clear the encrypted flag.
                blockFlags &= ~C.BUFFER_FLAG_ENCRYPTED;
                if (!sampleSignalByteRead) {
                    input.readFully(scratch.data, 0, 1);
                    sampleBytesRead++;
                    if ((scratch.data[0] & 0x80) == 0x80) {
                        throw new ParserException("Extension bit is set in signal byte");
                    }
                    sampleSignalByte = scratch.data[0];
                    sampleSignalByteRead = true;
                }
                // TODO: maybe handle an encryption here
            } else if (track.sampleStrippedBytes != null) {
                // If the sample has header stripping, prepare to read/output the stripped bytes first.
                sampleStrippedBytes.reset(track.sampleStrippedBytes, track.sampleStrippedBytes.length);
            }
            sampleEncodingHandled = true;
        }
        size += sampleStrippedBytes.limit();

        if (CODEC_ID_H264.equals(track.codecId) || CODEC_ID_H265.equals(track.codecId)) {
            // Zero the top three bytes of the array that we'll use to decode nal unit lengths, in case
            // they're only 1 or 2 bytes long.
            byte[] nalLengthData = nalLength.data;
            nalLengthData[0] = 0;
            nalLengthData[1] = 0;
            nalLengthData[2] = 0;
            int nalUnitLengthFieldLength = track.nalUnitLengthFieldLength;
            int nalUnitLengthFieldLengthDiff = 4 - track.nalUnitLengthFieldLength;
            // NAL units are length delimited, but the decoder requires start code delimited units.
            // Loop until we've written the sample to the track output, replacing length delimiters with
            // start codes as we encounter them.
            while (sampleBytesRead < size) {
                if (sampleCurrentNalBytesRemaining == 0) {
                    // Read the NAL length so that we know where we find the next one.
                    readToTarget(input, nalLengthData, nalUnitLengthFieldLengthDiff,
                            nalUnitLengthFieldLength);
                    nalLength.setPosition(0);
                    sampleCurrentNalBytesRemaining = nalLength.readUnsignedIntToInt();
                    // Write a start code for the current NAL unit.
                    nalStartCode.setPosition(0);
                    output.sampleData(nalStartCode, 4);
                    sampleBytesWritten += 4;
                } else {
                    // Write the payload of the NAL unit.
                    sampleCurrentNalBytesRemaining -=
                            readToOutput(input, output, sampleCurrentNalBytesRemaining);
                }
            }
        } else {
            while (sampleBytesRead < size) {
                readToOutput(input, output, size - sampleBytesRead);
            }
        }

        if (CODEC_ID_VORBIS.equals(track.codecId)) {
            // Vorbis decoder in android MediaCodec [1] expects the last 4 bytes of the sample to be the
            // number of samples in the current page. This definition holds good only for Ogg and
            // irrelevant for Matroska. So we always set this to -1 (the decoder will ignore this value if
            // we set it to -1). The android platform media extractor [2] does the same.
            // [1] https://android.googlesource.com/platform/frameworks/av/+/lollipop-release/media/libstagefright/codecs/vorbis/dec/SoftVorbis.cpp#314
            // [2] https://android.googlesource.com/platform/frameworks/av/+/lollipop-release/media/libstagefright/NuMediaExtractor.cpp#474
            vorbisNumPageSamples.setPosition(0);
            output.sampleData(vorbisNumPageSamples, 4);
            sampleBytesWritten += 4;
        }
    }

    private void writeSubtitleSampleData(ExtractorInput input, byte[] samplePrefix, int size)
            throws IOException, InterruptedException {
        int sizeWithPrefix = samplePrefix.length + size;
        if (subtitleSample.capacity() < sizeWithPrefix) {
            // Initialize subripSample to contain the required prefix and have space to hold a subtitle
            // twice as long as this one.
            subtitleSample.data = Arrays.copyOf(samplePrefix, sizeWithPrefix + size);
        } else {
            System.arraycopy(samplePrefix, 0, subtitleSample.data, 0, samplePrefix.length);
        }
        input.readFully(subtitleSample.data, samplePrefix.length, size);
        subtitleSample.reset(sizeWithPrefix);
        // Defer writing the data to the track output. We need to modify the sample data by setting
        // the correct end timecode, which we might not have yet.
    }

    private void commitSubtitleSample(Track track, String timecodeFormat, int endTimecodeOffset,
                                      long lastTimecodeValueScalingFactor, byte[] emptyTimecode) {
        setSampleDuration(subtitleSample.data, blockDurationUs, timecodeFormat, endTimecodeOffset,
                lastTimecodeValueScalingFactor, emptyTimecode);
        // Note: If we ever want to support DRM protected subtitles then we'll need to output the
        // appropriate encryption data here.
        track.output.sampleData(subtitleSample, subtitleSample.limit());
        sampleBytesWritten += subtitleSample.limit();
    }

    private static void setSampleDuration(byte[] subripSampleData, long durationUs,
                                          String timecodeFormat, int endTimecodeOffset, long lastTimecodeValueScalingFactor,
                                          byte[] emptyTimecode) {
        byte[] timeCodeData;
        if (durationUs == C.TIME_UNSET) {
            timeCodeData = emptyTimecode;
        } else {
            int hours = (int) (durationUs / (3600 * C.MICROS_PER_SECOND));
            durationUs -= (hours * 3600 * C.MICROS_PER_SECOND);
            int minutes = (int) (durationUs / (60 * C.MICROS_PER_SECOND));
            durationUs -= (minutes * 60 * C.MICROS_PER_SECOND);
            int seconds = (int) (durationUs / C.MICROS_PER_SECOND);
            durationUs -= (seconds * C.MICROS_PER_SECOND);
            int lastValue = (int) (durationUs / lastTimecodeValueScalingFactor);
            timeCodeData = Util.getUtf8Bytes(String.format(Locale.US, timecodeFormat, hours, minutes,
                    seconds, lastValue));
        }
        System.arraycopy(timeCodeData, 0, subripSampleData, endTimecodeOffset, emptyTimecode.length);
    }

    /**
     * Writes {@code length} bytes of sample data into {@code target} at {@code offset}, consisting of
     * pending {@link #sampleStrippedBytes} and any remaining data read from {@code input}.
     */
    private void readToTarget(ExtractorInput input, byte[] target, int offset, int length)
            throws IOException, InterruptedException {
        int pendingStrippedBytes = Math.min(length, sampleStrippedBytes.bytesLeft());
        input.readFully(target, offset + pendingStrippedBytes, length - pendingStrippedBytes);
        if (pendingStrippedBytes > 0) {
            sampleStrippedBytes.readBytes(target, offset, pendingStrippedBytes);
        }
        sampleBytesRead += length;
    }

    /**
     * Outputs up to {@code length} bytes of sample data to {@code output}, consisting of either
     * {@link #sampleStrippedBytes} or data read from {@code input}.
     */
    private int readToOutput(ExtractorInput input, TrackOutput output, int length)
            throws IOException, InterruptedException {
        int bytesRead;
        int strippedBytesLeft = sampleStrippedBytes.bytesLeft();
        if (strippedBytesLeft > 0) {
            bytesRead = Math.min(length, strippedBytesLeft);
            output.sampleData(sampleStrippedBytes, bytesRead);
        } else {
            bytesRead = output.sampleData(input, length, false);
        }
        sampleBytesRead += bytesRead;
        sampleBytesWritten += bytesRead;
        return bytesRead;
    }

    /**
     * Builds a {@link SeekMap} from the recently gathered Cues information.
     *
     * @return The built {@link SeekMap}. The returned {@link SeekMap} may be unseekable if cues
     *     information was missing or incomplete.
     */
    private SeekMap buildSeekMap() {
        if (segmentContentPosition == C.POSITION_UNSET || durationUs == C.TIME_UNSET
                || cueTimesUs == null || cueTimesUs.size() == 0
                || cueClusterPositions == null || cueClusterPositions.size() != cueTimesUs.size()) {
            // Cues information is missing or incomplete.
            cueTimesUs = null;
            cueClusterPositions = null;
            return new SeekMap.Unseekable(durationUs);
        }
        int cuePointsSize = cueTimesUs.size();
        int[] sizes = new int[cuePointsSize];
        long[] offsets = new long[cuePointsSize];
        long[] durationsUs = new long[cuePointsSize];
        long[] timesUs = new long[cuePointsSize];
        for (int i = 0; i < cuePointsSize; i++) {
            timesUs[i] = cueTimesUs.get(i);
            offsets[i] = segmentContentPosition + cueClusterPositions.get(i);
        }
        for (int i = 0; i < cuePointsSize - 1; i++) {
            sizes[i] = (int) (offsets[i + 1] - offsets[i]);
            durationsUs[i] = timesUs[i + 1] - timesUs[i];
        }
        sizes[cuePointsSize - 1] =
                (int) (segmentContentPosition + segmentContentSize - offsets[cuePointsSize - 1]);
        durationsUs[cuePointsSize - 1] = durationUs - timesUs[cuePointsSize - 1];
        cueTimesUs = null;
        cueClusterPositions = null;
        return new ChunkIndex(sizes, offsets, durationsUs, timesUs);
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

    private long scaleTimecodeToUs(long unscaledTimecode) throws ParserException {
        if (timecodeScale == C.TIME_UNSET) {
            throw new ParserException("Can't scale timecode prior to timecodeScale being set.");
        }
        return Util.scaleLargeTimestamp(unscaledTimecode, timecodeScale, 1000);
    }

    private static boolean isCodecSupported(String codecId) {
        return CODEC_ID_VP8.equals(codecId)
                || CODEC_ID_VP9.equals(codecId)
                || CODEC_ID_AV1.equals(codecId)
                || CODEC_ID_MPEG2.equals(codecId)
                || CODEC_ID_MPEG4_SP.equals(codecId)
                || CODEC_ID_MPEG4_ASP.equals(codecId)
                || CODEC_ID_MPEG4_AP.equals(codecId)
                || CODEC_ID_H264.equals(codecId)
                || CODEC_ID_H265.equals(codecId)
                || CODEC_ID_FOURCC.equals(codecId)
                || CODEC_ID_THEORA.equals(codecId)
                || CODEC_ID_OPUS.equals(codecId)
                || CODEC_ID_VORBIS.equals(codecId)
                || CODEC_ID_AAC.equals(codecId)
                || CODEC_ID_MP2.equals(codecId)
                || CODEC_ID_MP3.equals(codecId)
                || CODEC_ID_AC3.equals(codecId)
                || CODEC_ID_E_AC3.equals(codecId)
                || CODEC_ID_TRUEHD.equals(codecId)
                || CODEC_ID_DTS.equals(codecId)
                || CODEC_ID_DTS_EXPRESS.equals(codecId)
                || CODEC_ID_DTS_LOSSLESS.equals(codecId)
                || CODEC_ID_FLAC.equals(codecId)
                || CODEC_ID_ACM.equals(codecId)
                || CODEC_ID_PCM_INT_LIT.equals(codecId)
                || CODEC_ID_SUBRIP.equals(codecId)
                || CODEC_ID_ASS.equals(codecId)
                || CODEC_ID_VOBSUB.equals(codecId)
                || CODEC_ID_PGS.equals(codecId)
                || CODEC_ID_DVBSUB.equals(codecId);
    }

    /**
     * Returns an array that can store (at least) {@code length} elements, which will be either a new
     * array or {@code array} if it's not null and large enough.
     */
    private static int[] ensureArrayCapacity(int[] array, int length) {
        if (array == null) {
            return new int[length];
        } else if (array.length >= length) {
            return array;
        } else {
            // Double the size to avoid allocating constantly if the required length increases gradually.
            return new int[Math.max(array.length * 2, length)];
        }
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

        // Text elements.
        public boolean flagForced;
        public boolean flagDefault = true;
        private String language = "eng";

        // Set when the output is initialized. nalUnitLengthFieldLength is only set for H264/H265.
        public TrackOutput output;
        public int nalUnitLengthFieldLength;

        /** Initializes the track with an output. */
        public void initializeOutput(ExtractorOutput output, int trackId) throws ParserException {
            String mimeType = null;
            int maxInputSize = Format.NO_VALUE;
            @C.PcmEncoding int pcmEncoding = Format.NO_VALUE;
            List<byte[]> initializationData = null;
            switch (codecId) {
                case CODEC_ID_VP8:
                    mimeType = MimeTypes.VIDEO_VP8;
                    break;
                case CODEC_ID_VP9:
                    mimeType = MimeTypes.VIDEO_VP9;
                    break;
                case CODEC_ID_AV1:
                    mimeType = MimeTypes.VIDEO_AV1;
                    break;
                case CODEC_ID_MPEG2:
                    mimeType = MimeTypes.VIDEO_MPEG2;
                    break;
                case CODEC_ID_MPEG4_SP:
                case CODEC_ID_MPEG4_ASP:
                case CODEC_ID_MPEG4_AP:
                    mimeType = MimeTypes.VIDEO_MP4V;
                    initializationData =
                            codecPrivate == null ? null : Collections.singletonList(codecPrivate);
                    break;
                case CODEC_ID_H264:
                    mimeType = MimeTypes.VIDEO_H264;
                    //AvcConfig avcConfig = AvcConfig.parse(new ParsableByteArray(codecPrivate));
                    //initializationData = avcConfig.initializationData;
                    //nalUnitLengthFieldLength = avcConfig.nalUnitLengthFieldLength;
                    break;
                case CODEC_ID_H265:
                    mimeType = MimeTypes.VIDEO_H265;
                    //HevcConfig hevcConfig = HevcConfig.parse(new ParsableByteArray(codecPrivate));
                    //initializationData = hevcConfig.initializationData;
                    //nalUnitLengthFieldLength = hevcConfig.nalUnitLengthFieldLength;
                    break;
                case CODEC_ID_FOURCC:
                    //Pair<String, List<byte[]>> pair = parseFourCcPrivate(new ParsableByteArray(codecPrivate));
                    //mimeType = pair.first;
                    //initializationData = pair.second;
                    break;
                case CODEC_ID_THEORA:
                    // TODO: This can be set to the real mimeType if/when we work out what initializationData
                    // should be set to for this case.
                    mimeType = MimeTypes.VIDEO_UNKNOWN;
                    break;
                case CODEC_ID_VORBIS:
                    mimeType = MimeTypes.AUDIO_VORBIS;
                    maxInputSize = VORBIS_MAX_INPUT_SIZE;
                    //initializationData = parseVorbisCodecPrivate(codecPrivate);
                    break;
                case CODEC_ID_OPUS:
                    mimeType = MimeTypes.AUDIO_OPUS;
                    maxInputSize = OPUS_MAX_INPUT_SIZE;
                    //initializationData = new ArrayList<>(3);
                    //initializationData.add(codecPrivate);
                    //initializationData.add(
                    //        ByteBuffer.allocate(8).order(ByteOrder.nativeOrder()).putLong(codecDelayNs).array());
                    //initializationData.add(
                    //        ByteBuffer.allocate(8).order(ByteOrder.nativeOrder()).putLong(seekPreRollNs).array());
                    break;
                case CODEC_ID_AAC:
                    mimeType = MimeTypes.AUDIO_AAC;
                    //initializationData = Collections.singletonList(codecPrivate);
                    break;
                case CODEC_ID_MP2:
                    mimeType = MimeTypes.AUDIO_MPEG_L2;
                    maxInputSize = MpegAudioHeader.MAX_FRAME_SIZE_BYTES;
                    break;
                case CODEC_ID_MP3:
                    mimeType = MimeTypes.AUDIO_MPEG;
                    maxInputSize = MpegAudioHeader.MAX_FRAME_SIZE_BYTES;
                    break;
                case CODEC_ID_AC3:
                    mimeType = MimeTypes.AUDIO_AC3;
                    break;
                case CODEC_ID_E_AC3:
                    mimeType = MimeTypes.AUDIO_E_AC3;
                    break;
                case CODEC_ID_DTS:
                case CODEC_ID_DTS_EXPRESS:
                    mimeType = MimeTypes.AUDIO_DTS;
                    break;
                case CODEC_ID_DTS_LOSSLESS:
                    mimeType = MimeTypes.AUDIO_DTS_HD;
                    break;
                case CODEC_ID_FLAC:
                    mimeType = MimeTypes.AUDIO_FLAC;
                    initializationData = Collections.singletonList(codecPrivate);
                    break;
                case CODEC_ID_ACM:
                    mimeType = MimeTypes.AUDIO_RAW;
                    if (parseMsAcmCodecPrivate(new ParsableByteArray(codecPrivate))) {
                        pcmEncoding = Util.getPcmEncoding(audioBitDepth);
                        if (pcmEncoding == C.ENCODING_INVALID) {
                            pcmEncoding = Format.NO_VALUE;
                            mimeType = MimeTypes.AUDIO_UNKNOWN;
                            Log.w(TAG, "Unsupported PCM bit depth: " + audioBitDepth + ". Setting mimeType to "
                                    + mimeType);
                        }
                    } else {
                        mimeType = MimeTypes.AUDIO_UNKNOWN;
                        Log.w(TAG, "Non-PCM MS/ACM is unsupported. Setting mimeType to " + mimeType);
                    }
                    break;
                case CODEC_ID_PCM_INT_LIT:
                    mimeType = MimeTypes.AUDIO_RAW;
                    pcmEncoding = Util.getPcmEncoding(audioBitDepth);
                    if (pcmEncoding == C.ENCODING_INVALID) {
                        pcmEncoding = Format.NO_VALUE;
                        mimeType = MimeTypes.AUDIO_UNKNOWN;
                        Log.w(TAG, "Unsupported PCM bit depth: " + audioBitDepth + ". Setting mimeType to "
                                + mimeType);
                    }
                    break;
                case CODEC_ID_SUBRIP:
                    mimeType = MimeTypes.APPLICATION_SUBRIP;
                    break;
                case CODEC_ID_ASS:
                    mimeType = MimeTypes.TEXT_SSA;
                    break;
                case CODEC_ID_VOBSUB:
                    mimeType = MimeTypes.APPLICATION_VOBSUB;
                    initializationData = Collections.singletonList(codecPrivate);
                    break;
                case CODEC_ID_PGS:
                    mimeType = MimeTypes.APPLICATION_PGS;
                    break;
                case CODEC_ID_DVBSUB:
                    mimeType = MimeTypes.APPLICATION_DVBSUBS;
                    // Init data: composition_page (2), ancillary_page (2)
                    initializationData = Collections.singletonList(new byte[] {codecPrivate[0],
                            codecPrivate[1], codecPrivate[2], codecPrivate[3]});
                    break;
                default:
                    throw new ParserException("Unrecognized codec identifier.");
            }

            int type;
            Format format;
            @C.SelectionFlags int selectionFlags = 0;
            selectionFlags |= flagDefault ? C.SELECTION_FLAG_DEFAULT : 0;
            selectionFlags |= flagForced ? C.SELECTION_FLAG_FORCED : 0;
            // TODO: Consider reading the name elements of the tracks and, if present, incorporating them
            // into the trackId passed when creating the formats.
            if (MimeTypes.isAudio(mimeType)) {
                type = C.TRACK_TYPE_AUDIO;
                format = Format.createAudioSampleFormat(Integer.toString(trackId), mimeType, null,
                        Format.NO_VALUE, maxInputSize, channelCount, sampleRate, pcmEncoding,
                        initializationData, drmInitData, selectionFlags, language);
            } else if (MimeTypes.isVideo(mimeType)) {
                type = C.TRACK_TYPE_VIDEO;
                if (displayUnit == Track.DISPLAY_UNIT_PIXELS) {
                    displayWidth = displayWidth == Format.NO_VALUE ? width : displayWidth;
                    displayHeight = displayHeight == Format.NO_VALUE ? height : displayHeight;
                }
                float pixelWidthHeightRatio = Format.NO_VALUE;
                if (displayWidth != Format.NO_VALUE && displayHeight != Format.NO_VALUE) {
                    pixelWidthHeightRatio = ((float) (height * displayWidth)) / (width * displayHeight);
                }
                ColorInfo colorInfo = null;
                if (hasColorInfo) {
                    byte[] hdrStaticInfo = getHdrStaticInfo();
                    colorInfo = new ColorInfo(colorSpace, colorRange, colorTransfer, hdrStaticInfo);
                }
                int rotationDegrees = Format.NO_VALUE;
                // Some HTC devices signal rotation in track names.
                if ("htc_video_rotA-000".equals(name)) {
                    rotationDegrees = 0;
                } else if ("htc_video_rotA-090".equals(name)) {
                    rotationDegrees = 90;
                } else if ("htc_video_rotA-180".equals(name)) {
                    rotationDegrees = 180;
                } else if ("htc_video_rotA-270".equals(name)) {
                    rotationDegrees = 270;
                }
                if (projectionType == C.PROJECTION_RECTANGULAR
                        && Float.compare(projectionPoseYaw, 0f) == 0
                        && Float.compare(projectionPosePitch, 0f) == 0) {
                    // The range of projectionPoseRoll is [-180, 180].
                    if (Float.compare(projectionPoseRoll, 0f) == 0) {
                        rotationDegrees = 0;
                    } else if (Float.compare(projectionPosePitch, 90f) == 0) {
                        rotationDegrees = 90;
                    } else if (Float.compare(projectionPosePitch, -180f) == 0
                            || Float.compare(projectionPosePitch, 180f) == 0) {
                        rotationDegrees = 180;
                    } else if (Float.compare(projectionPosePitch, -90f) == 0) {
                        rotationDegrees = 270;
                    }
                }
                format =
                        Format.createVideoSampleFormat(
                                Integer.toString(trackId),
                                mimeType,
                                /* codecs= */ null,
                                /* bitrate= */ Format.NO_VALUE,
                                maxInputSize,
                                width,
                                height,
                                /* frameRate= */ Format.NO_VALUE,
                                initializationData,
                                rotationDegrees,
                                pixelWidthHeightRatio,
                                projectionData,
                                stereoMode,
                                colorInfo,
                                drmInitData);
            } else if (MimeTypes.APPLICATION_SUBRIP.equals(mimeType)) {
                type = C.TRACK_TYPE_TEXT;
                format = Format.createTextSampleFormat(Integer.toString(trackId), mimeType, selectionFlags,
                        language, drmInitData);
            } else if (MimeTypes.TEXT_SSA.equals(mimeType)) {
                type = C.TRACK_TYPE_TEXT;
                initializationData = new ArrayList<>(2);
                initializationData.add(SSA_DIALOGUE_FORMAT);
                initializationData.add(codecPrivate);
                format = Format.createTextSampleFormat(Integer.toString(trackId), mimeType, null,
                        Format.NO_VALUE, selectionFlags, language, Format.NO_VALUE, drmInitData,
                        Format.OFFSET_SAMPLE_RELATIVE, initializationData);
            } else if (MimeTypes.APPLICATION_VOBSUB.equals(mimeType)
                    || MimeTypes.APPLICATION_PGS.equals(mimeType)
                    || MimeTypes.APPLICATION_DVBSUBS.equals(mimeType)) {
                type = C.TRACK_TYPE_TEXT;
                format =
                        Format.createImageSampleFormat(
                                Integer.toString(trackId),
                                mimeType,
                                null,
                                Format.NO_VALUE,
                                selectionFlags,
                                initializationData,
                                language,
                                drmInitData);
            } else {
                throw new ParserException("Unexpected MIME type.");
            }

            this.output = output.track(number, type);
            this.output.format(format);
        }

        /** Resets any state stored in the track in response to a seek. */
        public void reset() {
            // Do nothing
        }

        /** Returns the HDR Static Info as defined in CTA-861.3. */
        private byte[] getHdrStaticInfo() {
            // Are all fields present.
            if (primaryRChromaticityX == Format.NO_VALUE || primaryRChromaticityY == Format.NO_VALUE
                    || primaryGChromaticityX == Format.NO_VALUE || primaryGChromaticityY == Format.NO_VALUE
                    || primaryBChromaticityX == Format.NO_VALUE || primaryBChromaticityY == Format.NO_VALUE
                    || whitePointChromaticityX == Format.NO_VALUE
                    || whitePointChromaticityY == Format.NO_VALUE || maxMasteringLuminance == Format.NO_VALUE
                    || minMasteringLuminance == Format.NO_VALUE) {
                return null;
            }

            byte[] hdrStaticInfoData = new byte[25];
            ByteBuffer hdrStaticInfo = ByteBuffer.wrap(hdrStaticInfoData);
            hdrStaticInfo.put((byte) 0);  // Type.
            hdrStaticInfo.putShort((short) ((primaryRChromaticityX * MAX_CHROMATICITY) + 0.5f));
            hdrStaticInfo.putShort((short) ((primaryRChromaticityY * MAX_CHROMATICITY) + 0.5f));
            hdrStaticInfo.putShort((short) ((primaryGChromaticityX * MAX_CHROMATICITY)  + 0.5f));
            hdrStaticInfo.putShort((short) ((primaryGChromaticityY * MAX_CHROMATICITY) + 0.5f));
            hdrStaticInfo.putShort((short) ((primaryBChromaticityX * MAX_CHROMATICITY) + 0.5f));
            hdrStaticInfo.putShort((short) ((primaryBChromaticityY * MAX_CHROMATICITY) + 0.5f));
            hdrStaticInfo.putShort((short) ((whitePointChromaticityX * MAX_CHROMATICITY) + 0.5f));
            hdrStaticInfo.putShort((short) ((whitePointChromaticityY * MAX_CHROMATICITY) + 0.5f));
            hdrStaticInfo.putShort((short) (maxMasteringLuminance + 0.5f));
            hdrStaticInfo.putShort((short) (minMasteringLuminance + 0.5f));
            hdrStaticInfo.putShort((short) maxContentLuminance);
            hdrStaticInfo.putShort((short) maxFrameAverageLuminance);
            return hdrStaticInfoData;
        }

        /**
         * Builds initialization data for a {@link Format} from FourCC codec private data.
         *
         * @return The codec mime type and initialization data. If the compression type is not supported
         *     then the mime type is set to {@link MimeTypes#VIDEO_UNKNOWN} and the initialization data
         *     is {@code null}.
         * @throws ParserException If the initialization data could not be built.
         */
        private static Pair<String, List<byte[]>> parseFourCcPrivate(ParsableByteArray buffer)
                throws ParserException {
            try {
                buffer.skipBytes(16); // size(4), width(4), height(4), planes(2), bitcount(2).
                long compression = buffer.readLittleEndianUnsignedInt();
                if (compression == FOURCC_COMPRESSION_DIVX) {
                    return new Pair<>(MimeTypes.VIDEO_DIVX, null);
                } else if (compression == FOURCC_COMPRESSION_H263) {
                    return new Pair<>(MimeTypes.VIDEO_H263, null);
                } else if (compression == FOURCC_COMPRESSION_VC1) {
                    // Search for the initialization data from the end of the BITMAPINFOHEADER. The last 20
                    // bytes of which are: sizeImage(4), xPel/m (4), yPel/m (4), clrUsed(4), clrImportant(4).
                    int startOffset = buffer.getPosition() + 20;
                    byte[] bufferData = buffer.data;
                    for (int offset = startOffset; offset < bufferData.length - 4; offset++) {
                        if (bufferData[offset] == 0x00
                                && bufferData[offset + 1] == 0x00
                                && bufferData[offset + 2] == 0x01
                                && bufferData[offset + 3] == 0x0F) {
                            // We've found the initialization data.
                            byte[] initializationData = Arrays.copyOfRange(bufferData, offset, bufferData.length);
                            return new Pair<>(MimeTypes.VIDEO_VC1, Collections.singletonList(initializationData));
                        }
                    }
                    throw new ParserException("Failed to find FourCC VC1 initialization data");
                }
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new ParserException("Error parsing FourCC private data");
            }

            Log.w(TAG, "Unknown FourCC. Setting mimeType to " + MimeTypes.VIDEO_UNKNOWN);
            return new Pair<>(MimeTypes.VIDEO_UNKNOWN, null);
        }

        /**
         * Builds initialization data for a {@link Format} from Vorbis codec private data.
         *
         * @return The initialization data for the {@link Format}.
         * @throws ParserException If the initialization data could not be built.
         */
        private static List<byte[]> parseVorbisCodecPrivate(byte[] codecPrivate)
                throws ParserException {
            try {
                if (codecPrivate[0] != 0x02) {
                    throw new ParserException("Error parsing vorbis codec private");
                }
                int offset = 1;
                int vorbisInfoLength = 0;
                while (codecPrivate[offset] == (byte) 0xFF) {
                    vorbisInfoLength += 0xFF;
                    offset++;
                }
                vorbisInfoLength += codecPrivate[offset++];

                int vorbisSkipLength = 0;
                while (codecPrivate[offset] == (byte) 0xFF) {
                    vorbisSkipLength += 0xFF;
                    offset++;
                }
                vorbisSkipLength += codecPrivate[offset++];

                if (codecPrivate[offset] != 0x01) {
                    throw new ParserException("Error parsing vorbis codec private");
                }
                byte[] vorbisInfo = new byte[vorbisInfoLength];
                System.arraycopy(codecPrivate, offset, vorbisInfo, 0, vorbisInfoLength);
                offset += vorbisInfoLength;
                if (codecPrivate[offset] != 0x03) {
                    throw new ParserException("Error parsing vorbis codec private");
                }
                offset += vorbisSkipLength;
                if (codecPrivate[offset] != 0x05) {
                    throw new ParserException("Error parsing vorbis codec private");
                }
                byte[] vorbisBooks = new byte[codecPrivate.length - offset];
                System.arraycopy(codecPrivate, offset, vorbisBooks, 0, codecPrivate.length - offset);
                List<byte[]> initializationData = new ArrayList<>(2);
                initializationData.add(vorbisInfo);
                initializationData.add(vorbisBooks);
                return initializationData;
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new ParserException("Error parsing vorbis codec private");
            }
        }

        /**
         * Parses an MS/ACM codec private, returning whether it indicates PCM audio.
         *
         * @return Whether the codec private indicates PCM audio.
         * @throws ParserException If a parsing error occurs.
         */
        private static boolean parseMsAcmCodecPrivate(ParsableByteArray buffer) throws ParserException {
            try {
                int formatTag = buffer.readLittleEndianUnsignedShort();
                if (formatTag == WAVE_FORMAT_PCM) {
                    return true;
                } else if (formatTag == WAVE_FORMAT_EXTENSIBLE) {
                    buffer.setPosition(WAVE_FORMAT_SIZE + 6); // unionSamples(2), channelMask(4)
                    return buffer.readLong() == WAVE_SUBFORMAT_PCM.getMostSignificantBits()
                            && buffer.readLong() == WAVE_SUBFORMAT_PCM.getLeastSignificantBits();
                } else {
                    return false;
                }
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new ParserException("Error parsing MS/ACM codec private");
            }
        }
    }
}

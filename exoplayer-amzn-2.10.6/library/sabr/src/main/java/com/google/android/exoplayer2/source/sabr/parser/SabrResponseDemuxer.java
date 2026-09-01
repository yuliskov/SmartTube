package com.google.android.exoplayer2.source.sabr.parser;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.source.sabr.parser.exceptions.SabrMediaCorrelationException;
import com.google.android.exoplayer2.source.sabr.parser.ump.UMPDecoder;
import com.google.android.exoplayer2.source.sabr.parser.ump.UMPPartId;
import com.google.android.exoplayer2.source.sabr.protos.misc.FormatId;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.MediaHeader;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bounded, generation-aware correlation of multiplexed SABR media and control frames. */
public final class SabrResponseDemuxer {
    public interface ControlHandler {
        void onControlFrame(long generation, UMPDecoder.Frame frame);
    }

    public static final class CompletedSegment {
        private final MediaHeader header;
        private final byte[] data;

        private CompletedSegment(MediaHeader header, byte[] data) {
            this.header = header;
            this.data = data;
        }

        public MediaHeader getHeader() { return header; }
        public byte[] getData() { return java.util.Arrays.copyOf(data, data.length); }
        public boolean isInitialization() { return header.getIsInitSeg(); }
        public long getHeaderId() { return unsigned(header.getHeaderId()); }
    }

    private static final class PartialSegment {
        final MediaHeader header;
        final ByteArrayOutputStream data = new ByteArrayOutputStream();

        PartialSegment(MediaHeader header) {
            this.header = header;
        }
    }

    private final String videoId;
    private final int maxPartialHeaders;
    private final int maxCompletedSegments;
    private final int maxStoredBytes;
    private final @Nullable ControlHandler controlHandler;
    private final LinkedHashMap<Long, PartialSegment> partial = new LinkedHashMap<>();
    private final ArrayDeque<CompletedSegment> completed = new ArrayDeque<>();
    private final LinkedHashMap<String, CompletedSegment> initializationCache = new LinkedHashMap<>();
    private final LinkedHashSet<Long> endedHeaderIds = new LinkedHashSet<>();
    private long generation;
    private int storedBytes;
    private boolean closed;

    public SabrResponseDemuxer(
            String videoId,
            long generation,
            int maxPartialHeaders,
            int maxCompletedSegments,
            int maxStoredBytes,
            @Nullable ControlHandler controlHandler) {
        if (maxPartialHeaders <= 0 || maxCompletedSegments <= 0 || maxStoredBytes <= 0) {
            throw new IllegalArgumentException("SABR demux limits must be positive");
        }
        this.videoId = videoId;
        this.generation = generation;
        this.maxPartialHeaders = maxPartialHeaders;
        this.maxCompletedSegments = maxCompletedSegments;
        this.maxStoredBytes = maxStoredBytes;
        this.controlHandler = controlHandler;
    }

    public synchronized List<CompletedSegment> consume(long responseGeneration, UMPDecoder.Frame frame) {
        requireOpen();
        if (responseGeneration != generation) {
            return Collections.emptyList();
        }
        long partId = frame.getPartId();
        if (partId == UMPPartId.MEDIA_HEADER) {
            consumeHeader(frame.getPayload());
            return Collections.emptyList();
        }
        if (partId == UMPPartId.MEDIA) {
            consumeMedia(frame.getPayload());
            return Collections.emptyList();
        }
        if (partId == UMPPartId.MEDIA_END) {
            CompletedSegment segment = consumeEnd(frame.getPayload());
            return Collections.singletonList(segment);
        }
        if (controlHandler != null) {
            controlHandler.onControlFrame(responseGeneration, frame);
        }
        return Collections.emptyList();
    }

    public synchronized void setGeneration(long generation) {
        this.generation = generation;
        for (PartialSegment segment : partial.values()) {
            storedBytes -= segment.data.size();
        }
        partial.clear();
        completed.clear();
        storedBytes = initializationBytes();
        endedHeaderIds.clear();
    }

    public synchronized @Nullable CompletedSegment poll(FormatId formatId) {
        Iterator<CompletedSegment> iterator = completed.iterator();
        while (iterator.hasNext()) {
            CompletedSegment segment = iterator.next();
            if (segment.header.hasFormatId() && segment.header.getFormatId().equals(formatId)) {
                iterator.remove();
                storedBytes -= segment.data.length;
                return segment;
            }
        }
        return null;
    }

    public synchronized @Nullable CompletedSegment getInitialization(FormatId formatId) {
        return initializationCache.get(formatId.toString());
    }

    public synchronized int getPartialCount() { return partial.size(); }
    public synchronized int getCompletedCount() { return completed.size(); }
    public synchronized int getStoredByteCount() { return storedBytes; }

    public synchronized void close() {
        closed = true;
        partial.clear();
        completed.clear();
        initializationCache.clear();
        endedHeaderIds.clear();
        storedBytes = 0;
    }

    private void consumeHeader(byte[] payload) {
        MediaHeader header;
        try {
            header = MediaHeader.parseFrom(payload);
        } catch (InvalidProtocolBufferException parseFailure) {
            throw error(SabrMediaCorrelationException.Reason.INVALID_HEADER,
                    "Malformed SABR media header", parseFailure);
        }
        if (!header.hasHeaderId() || !header.hasFormatId()
                || (!header.getIsInitSeg() && !header.hasSequenceNumber())) {
            throw error(SabrMediaCorrelationException.Reason.INVALID_HEADER,
                    "SABR media header is missing correlation fields");
        }
        if (header.hasVideoId() && !videoId.equals(header.getVideoId())) {
            throw error(SabrMediaCorrelationException.Reason.VIDEO_MISMATCH,
                    "SABR media header belongs to a different video");
        }
        long headerId = unsigned(header.getHeaderId());
        if (partial.containsKey(headerId) || endedHeaderIds.contains(headerId)) {
            throw error(SabrMediaCorrelationException.Reason.DUPLICATE_HEADER,
                    "Duplicate SABR media header");
        }
        if (partial.size() >= maxPartialHeaders) {
            throw error(SabrMediaCorrelationException.Reason.STORE_LIMIT_EXCEEDED,
                    "SABR partial-media header limit exceeded");
        }
        partial.put(headerId, new PartialSegment(header));
    }

    private void consumeMedia(byte[] payload) {
        Prefix prefix = decodePrefix(payload);
        PartialSegment segment = partial.get(prefix.value);
        if (segment == null) {
            throw error(SabrMediaCorrelationException.Reason.MISSING_HEADER,
                    "SABR media data has no matching header");
        }
        int mediaLength = payload.length - prefix.length;
        if (storedBytes > maxStoredBytes - mediaLength) {
            throw error(SabrMediaCorrelationException.Reason.STORE_LIMIT_EXCEEDED,
                    "SABR media byte limit exceeded");
        }
        if (segment.header.hasContentLength()
                && segment.data.size() + mediaLength > segment.header.getContentLength()) {
            throw error(SabrMediaCorrelationException.Reason.CONTENT_LENGTH_MISMATCH,
                    "SABR media exceeded its declared content length");
        }
        segment.data.write(payload, prefix.length, mediaLength);
        storedBytes += mediaLength;
    }

    private CompletedSegment consumeEnd(byte[] payload) {
        Prefix prefix = decodePrefix(payload);
        if (endedHeaderIds.contains(prefix.value)) {
            throw error(SabrMediaCorrelationException.Reason.DUPLICATE_END,
                    "Duplicate SABR media end marker");
        }
        PartialSegment segment = partial.remove(prefix.value);
        if (segment == null) {
            throw error(SabrMediaCorrelationException.Reason.MISSING_HEADER,
                    "SABR media end has no matching header");
        }
        if (segment.header.hasContentLength()
                && segment.header.getContentLength() != segment.data.size()) {
            storedBytes -= segment.data.size();
            throw error(SabrMediaCorrelationException.Reason.CONTENT_LENGTH_MISMATCH,
                    "SABR media length differs from its declared content length");
        }

        CompletedSegment result = new CompletedSegment(segment.header, segment.data.toByteArray());
        rememberEnded(prefix.value);
        if (result.isInitialization()) {
            String key = result.header.getFormatId().toString();
            CompletedSegment replaced = initializationCache.put(key, result);
            if (replaced != null) {
                storedBytes -= replaced.data.length;
            }
        } else {
            completed.addLast(result);
            while (completed.size() > maxCompletedSegments) {
                storedBytes -= completed.removeFirst().data.length;
            }
        }
        return result;
    }

    private void rememberEnded(long headerId) {
        endedHeaderIds.add(headerId);
        while (endedHeaderIds.size() > maxPartialHeaders + maxCompletedSegments) {
            Iterator<Long> iterator = endedHeaderIds.iterator();
            iterator.next();
            iterator.remove();
        }
    }

    private int initializationBytes() {
        int total = 0;
        for (CompletedSegment segment : initializationCache.values()) {
            total += segment.data.length;
        }
        return total;
    }

    private void requireOpen() {
        if (closed) {
            throw error(SabrMediaCorrelationException.Reason.CLOSED, "SABR demuxer is closed");
        }
    }

    private static Prefix decodePrefix(byte[] payload) {
        if (payload.length == 0) {
            throw error(SabrMediaCorrelationException.Reason.MALFORMED_MEDIA_PREFIX,
                    "SABR media correlation prefix is missing");
        }
        int first = payload[0] & 0xFF;
        int size = first < 128 ? 1 : first < 192 ? 2 : first < 224 ? 3 : first < 240 ? 4 : 5;
        if (payload.length < size) {
            throw error(SabrMediaCorrelationException.Reason.MALFORMED_MEDIA_PREFIX,
                    "SABR media correlation prefix is truncated");
        }
        long value;
        int shift;
        if (size == 5) {
            value = 0;
            shift = 0;
        } else {
            shift = 8 - size;
            value = first & ((1L << shift) - 1);
        }
        for (int i = 1; i < size; i++) {
            value |= ((long) payload[i] & 0xFF) << shift;
            shift += 8;
        }
        return new Prefix(value & 0xFFFF_FFFFL, size);
    }

    private static long unsigned(int value) {
        return value & 0xFFFF_FFFFL;
    }

    private static SabrMediaCorrelationException error(
            SabrMediaCorrelationException.Reason reason, String message) {
        return new SabrMediaCorrelationException(reason, message);
    }

    private static SabrMediaCorrelationException error(
            SabrMediaCorrelationException.Reason reason, String message, Throwable cause) {
        return new SabrMediaCorrelationException(reason, message, cause);
    }

    private static final class Prefix {
        final long value;
        final int length;

        Prefix(long value, int length) {
            this.value = value;
            this.length = length;
        }
    }
}

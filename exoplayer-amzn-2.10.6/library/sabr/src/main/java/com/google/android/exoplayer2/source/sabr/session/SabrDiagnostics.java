package com.google.android.exoplayer2.source.sabr.session;

import androidx.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Bounded metadata-only event history. Values capable of carrying credentials are not accepted. */
public final class SabrDiagnostics {
    public static final class Event {
        public final long elapsedRealtimeMs;
        public final long generation;
        public final long requestNumber;
        public final String type;
        public final long playerTimeMs;
        public final long bufferedDurationMs;
        public final long mediaHeaderId;
        public final int sequenceNumber;
        public final long byteCount;
        public final long backoffMs;
        public final int contextCount;
        public final boolean cookiePresent;
        public final boolean tokenPresent;
        public final @Nullable String errorCategory;

        public Event(
                long elapsedRealtimeMs,
                long generation,
                long requestNumber,
                String type,
                long playerTimeMs,
                long bufferedDurationMs,
                long mediaHeaderId,
                int sequenceNumber,
                long byteCount,
                long backoffMs,
                int contextCount,
                boolean cookiePresent,
                boolean tokenPresent,
                @Nullable String errorCategory) {
            this.elapsedRealtimeMs = elapsedRealtimeMs;
            this.generation = generation;
            this.requestNumber = requestNumber;
            this.type = type;
            this.playerTimeMs = playerTimeMs;
            this.bufferedDurationMs = bufferedDurationMs;
            this.mediaHeaderId = mediaHeaderId;
            this.sequenceNumber = sequenceNumber;
            this.byteCount = byteCount;
            this.backoffMs = backoffMs;
            this.contextCount = contextCount;
            this.cookiePresent = cookiePresent;
            this.tokenPresent = tokenPresent;
            this.errorCategory = errorCategory;
        }

        @Override
        public String toString() {
            return type + " gen=" + generation + " rn=" + requestNumber
                    + " playerMs=" + playerTimeMs + " bufferedMs=" + bufferedDurationMs
                    + " header=" + mediaHeaderId + " seq=" + sequenceNumber
                    + " bytes=" + byteCount + " backoffMs=" + backoffMs
                    + " contexts=" + contextCount + " cookie=" + cookiePresent
                    + " token=" + tokenPresent
                    + (errorCategory != null ? " error=" + errorCategory : "");
        }
    }

    private final int capacity;
    private final ArrayDeque<Event> events;

    public SabrDiagnostics(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        events = new ArrayDeque<>(capacity);
    }

    public synchronized void record(Event event) {
        if (events.size() == capacity) {
            events.removeFirst();
        }
        events.addLast(event);
    }

    public synchronized List<Event> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(events));
    }

    public synchronized void clear() {
        events.clear();
    }
}

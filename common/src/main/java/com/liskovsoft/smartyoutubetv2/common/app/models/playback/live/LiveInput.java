package com.liskovsoft.smartyoutubetv2.common.app.models.playback.live;

import java.util.Objects;

/** A validated, network-free YouTube live playback input. */
public final class LiveInput {
    public enum Type { VIDEO, CHANNEL }

    private final Type type;
    private final String value;
    private final String normalizedInput;

    LiveInput(Type type, String value, String normalizedInput) {
        this.type = Objects.requireNonNull(type);
        this.value = Objects.requireNonNull(value);
        this.normalizedInput = Objects.requireNonNull(normalizedInput);
    }

    public Type getType() {
        return type;
    }

    /** Video ID for VIDEO, canonical channel ID or @handle for CHANNEL. */
    public String getValue() {
        return value;
    }

    public String getNormalizedInput() {
        return normalizedInput;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof LiveInput)) return false;
        LiveInput that = (LiveInput) other;
        return type == that.type && value.equals(that.value)
                && normalizedInput.equals(that.normalizedInput);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, value, normalizedInput);
    }
}

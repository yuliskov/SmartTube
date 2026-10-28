package com.liskovsoft.smartyoutubetv2.common.vot;

public class VotTranslationResponse {
    public static final int STATUS_FAILED = 0;
    public static final int STATUS_FINISHED = 1;
    public static final int STATUS_WAITING = 2;
    public static final int STATUS_LONG_WAITING = 3;
    public static final int STATUS_PART_CONTENT = 5;
    public static final int STATUS_AUDIO_REQUESTED = 6;
    public static final int STATUS_SESSION_REQUIRED = 7;

    public String url;
    public int status;
    public int remainingTimeSec;
    public String translationId;
    public String message;

    public boolean isReady() {
        return status == STATUS_FINISHED || status == STATUS_PART_CONTENT;
    }

    public boolean isWaiting() {
        return status == STATUS_WAITING || status == STATUS_LONG_WAITING;
    }
}

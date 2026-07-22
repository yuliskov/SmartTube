package com.liskovsoft.smartyoutubetv2.common.vot;

public final class VotProgress {
    public static final int TYPE_WAITING = 0;
    public static final int TYPE_READY = 1;
    public static final int TYPE_FAILED = 2;

    public final int type;
    public final String audioUrl;
    public final int remainingTimeSec;
    public final int status;
    public final String message;

    private VotProgress(int type, String audioUrl, int remainingTimeSec, int status, String message) {
        this.type = type;
        this.audioUrl = audioUrl;
        this.remainingTimeSec = remainingTimeSec;
        this.status = status;
        this.message = message;
    }

    public static VotProgress waiting(int remainingTimeSec, int status) {
        return new VotProgress(TYPE_WAITING, null, remainingTimeSec, status, null);
    }

    public static VotProgress ready(String audioUrl) {
        return new VotProgress(TYPE_READY, audioUrl, 0, VotTranslationResponse.STATUS_FINISHED, null);
    }

    public static VotProgress failed(String message) {
        return new VotProgress(TYPE_FAILED, null, 0, VotTranslationResponse.STATUS_FAILED, message);
    }
}

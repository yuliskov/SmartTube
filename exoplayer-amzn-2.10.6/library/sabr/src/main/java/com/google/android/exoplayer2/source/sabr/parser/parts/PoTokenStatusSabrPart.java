package com.google.android.exoplayer2.source.sabr.parser.parts;

public class PoTokenStatusSabrPart implements SabrPart {
    public final PoTokenStatus status;

    public PoTokenStatusSabrPart(PoTokenStatus status) {
        this.status = status;
    }

    public enum PoTokenStatus {
        OK,                          // PO Token is provided and valid
        MISSING,                     // PO Token is not provided, and is required. A PO Token should be provided ASAP
        INVALID,                     // PO Token is provided, but is invalid. A new one should be generated ASAP
        PENDING,                     // PO Token is provided, but probably only a cold start token. A full PO Token should be provided ASAP
        NOT_REQUIRED,                // PO Token is not provided, and is not required
        PENDING_MISSING              // PO Token is not provided, but is pending. A full PO Token should be (probably) provided ASAP
    }
}

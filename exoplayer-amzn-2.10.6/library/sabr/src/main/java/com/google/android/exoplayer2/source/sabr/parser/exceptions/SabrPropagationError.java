package com.google.android.exoplayer2.source.sabr.parser.exceptions;

/**
 * A family of errors that should be handled outside the sabr module
 */
public abstract class SabrPropagationError extends SabrStreamError {
    protected SabrPropagationError(String msg) {
        super(msg);
    }
}

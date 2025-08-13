package com.google.android.exoplayer2.source.sabr.manifest;

import com.google.android.exoplayer2.offline.FilterableManifest;
import com.google.android.exoplayer2.offline.StreamKey;

import java.util.List;

/**
 * Represents a SABR media presentation
 */
public class SabrManifest implements FilterableManifest<SabrManifest> {
    @Override
    public SabrManifest copy(List<StreamKey> streamKeys) {
        return null;
    }
}

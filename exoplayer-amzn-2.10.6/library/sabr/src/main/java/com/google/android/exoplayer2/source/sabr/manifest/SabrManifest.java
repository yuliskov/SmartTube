package com.google.android.exoplayer2.source.sabr.manifest;

import com.google.android.exoplayer2.offline.FilterableManifest;
import com.google.android.exoplayer2.offline.StreamKey;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;

import java.util.Collections;
import java.util.List;

/**
 * Represents a SABR media presentation
 */
public class SabrManifest implements FilterableManifest<SabrManifest> {
    private final MediaItemFormatInfo mFormatInfo;

    public SabrManifest(MediaItemFormatInfo formatInfo) {
        mFormatInfo = formatInfo;
    }

    @Override
    public SabrManifest copy(List<StreamKey> streamKeys) {
        return null;
    }

    public long getStartMs() {
        return 0;
    }
}

package com.liskovsoft.smartyoutubetv2.common.filter;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

import java.util.ArrayList;
import java.util.List;

public class VideoFilter {

    public static List<Video> filterBlocked(
            Context context,
            List<Video> videos
    ) {

        if (videos == null || videos.isEmpty()) {
            return videos;
        }

        KeywordFilterManager manager =
                KeywordFilterManager.instance(context);

        List<Video> filtered = new ArrayList<>();

        for (Video video : videos) {

            if (video == null) {
                continue;
            }

            String title = video.getTitle();

            if (!manager.isBlocked(title)) {
                filtered.add(video);
            }
        }

        return filtered;
    }
}
package com.liskovsoft.smartyoutubetv2.common.utils;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import com.liskovsoft.smartyoutubetv2.common.R;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class Utils {
    /**
     * Limit the maximum size of a Map by removing oldest entries when limit reached
     */
    public static <K, V> Map<K, V> createLRUMap(final int maxEntries) {
        return new LinkedHashMap<K, V>(maxEntries*10/7, 0.7f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > maxEntries;
            }
        };
    }

    /**
     * Trim playlist if one exceeds max size
     */
    public static <T> List<T> createLRUList(final int maxEntries) {
        return new LinkedList<T>() {
            @Override
            public boolean add(T t) {
                if (size() > maxEntries) {
                    removeFirst();
                }

                return super.add(t);
            }
        };
    }

    @TargetApi(17)
    public static void displayShareVideoDialog(Context context, String videoId) {
        Uri videoUrl = convertToFullUrl(videoId);
        showMultiChooser(context, videoUrl);
    }

    @TargetApi(17)
    private static void showMultiChooser(Context context, Uri url) {
        Intent primaryIntent = new Intent(Intent.ACTION_VIEW);
        Intent secondaryIntent = new Intent(Intent.ACTION_SEND);
        primaryIntent.setData(url);
        secondaryIntent.putExtra(Intent.EXTRA_TEXT, url.toString());
        secondaryIntent.setType("text/plain");
        Intent chooserIntent = Intent.createChooser(primaryIntent, context.getResources().getText(R.string.send_to));
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[] { secondaryIntent });
        chooserIntent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        context.startActivity(chooserIntent);
    }

    private static Uri convertToFullUrl(String videoId) {
        String url = String.format("https://www.youtube.com/watch?v=%s", videoId);
        return Uri.parse(url);
    }
}

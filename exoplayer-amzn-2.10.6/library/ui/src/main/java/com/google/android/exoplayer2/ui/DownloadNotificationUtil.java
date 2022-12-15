/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.ui;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.offline.Download;
import com.google.android.exoplayer2.util.Util;
import java.util.List;

/**
 * @deprecated Using this class can cause notifications to flicker on devices with {@link
 *     Util#SDK_INT} &lt; 21. Use {@link DownloadNotificationHelper} instead.
 */
@Deprecated
public final class DownloadNotificationUtil {

  private DownloadNotificationUtil() {}

  /**
   * Returns a progress notification for the given downloads.
   *
   * @param context A context for accessing resources.
   * @param smallIcon A small icon for the notification.
   * @param channelId The id of the notification channel to use.
   * @param contentIntent An optional content intent to send when the notification is clicked.
   * @param message An optional message to display on the notification.
   * @param downloads The downloads.
   * @return The notification.
   */
  public static Notification buildProgressNotification(
      Context context,
      @DrawableRes int smallIcon,
      String channelId,
      @Nullable PendingIntent contentIntent,
      @Nullable String message,
      List<Download> downloads) {
    return new DownloadNotificationHelper(context, channelId)
        .buildProgressNotification(smallIcon, contentIntent, message, downloads);
  }

  /**
   * Returns a notification for a completed download.
   *
   * @param context A context for accessing resources.
   * @param smallIcon A small icon for the notifications.
   * @param channelId The id of the notification channel to use.
   * @param contentIntent An optional content intent to send when the notification is clicked.
   * @param message An optional message to display on the notification.
   * @return The notification.
   */
  public static Notification buildDownloadCompletedNotification(
      Context context,
      @DrawableRes int smallIcon,
      String channelId,
      @Nullable PendingIntent contentIntent,
      @Nullable String message) {
    return new DownloadNotificationHelper(context, channelId)
        .buildDownloadCompletedNotification(smallIcon, contentIntent, message);
  }

  /**
   * Returns a notification for a failed download.
   *
   * @param context A context for accessing resources.
   * @param smallIcon A small icon for the notifications.
   * @param channelId The id of the notification channel to use.
   * @param contentIntent An optional content intent to send when the notification is clicked.
   * @param message An optional message to display on the notification.
   * @return The notification.
   */
  public static Notification buildDownloadFailedNotification(
      Context context,
      @DrawableRes int smallIcon,
      String channelId,
      @Nullable PendingIntent contentIntent,
      @Nullable String message) {
    return new DownloadNotificationHelper(context, channelId)
        .buildDownloadFailedNotification(smallIcon, contentIntent, message);
  }
}

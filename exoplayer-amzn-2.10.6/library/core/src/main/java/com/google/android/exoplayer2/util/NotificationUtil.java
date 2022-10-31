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
package com.google.android.exoplayer2.util;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Utility methods for displaying {@link Notification Notifications}. */
@SuppressLint("InlinedApi")
public final class NotificationUtil {

  /**
   * Notification channel importance levels. One of {@link #IMPORTANCE_UNSPECIFIED}, {@link
   * #IMPORTANCE_NONE}, {@link #IMPORTANCE_MIN}, {@link #IMPORTANCE_LOW}, {@link
   * #IMPORTANCE_DEFAULT} or {@link #IMPORTANCE_HIGH}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    IMPORTANCE_UNSPECIFIED,
    IMPORTANCE_NONE,
    IMPORTANCE_MIN,
    IMPORTANCE_LOW,
    IMPORTANCE_DEFAULT,
    IMPORTANCE_HIGH
  })
  public @interface Importance {}
  /** @see NotificationManager#IMPORTANCE_UNSPECIFIED */
  public static final int IMPORTANCE_UNSPECIFIED = NotificationManager.IMPORTANCE_UNSPECIFIED;
  /** @see NotificationManager#IMPORTANCE_NONE */
  public static final int IMPORTANCE_NONE = NotificationManager.IMPORTANCE_NONE;
  /** @see NotificationManager#IMPORTANCE_MIN */
  public static final int IMPORTANCE_MIN = NotificationManager.IMPORTANCE_MIN;
  /** @see NotificationManager#IMPORTANCE_LOW */
  public static final int IMPORTANCE_LOW = NotificationManager.IMPORTANCE_LOW;
  /** @see NotificationManager#IMPORTANCE_DEFAULT */
  public static final int IMPORTANCE_DEFAULT = NotificationManager.IMPORTANCE_DEFAULT;
  /** @see NotificationManager#IMPORTANCE_HIGH */
  public static final int IMPORTANCE_HIGH = NotificationManager.IMPORTANCE_HIGH;

  /** @deprecated Use {@link #createNotificationChannel(Context, String, int, int, int)}. */
  @Deprecated
  public static void createNotificationChannel(
      Context context, String id, @StringRes int nameResourceId, @Importance int importance) {
    createNotificationChannel(
        context, id, nameResourceId, /* descriptionResourceId= */ 0, importance);
  }

  /**
   * Creates a notification channel that notifications can be posted to. See {@link
   * NotificationChannel} and {@link
   * NotificationManager#createNotificationChannel(NotificationChannel)} for details.
   *
   * @param context A {@link Context}.
   * @param id The id of the channel. Must be unique per package. The value may be truncated if it's
   *     too long.
   * @param nameResourceId A string resource identifier for the user visible name of the channel.
   *     The recommended maximum length is 40 characters. The string may be truncated if it's too
   *     long. You can rename the channel when the system locale changes by listening for the {@link
   *     Intent#ACTION_LOCALE_CHANGED} broadcast.
   * @param descriptionResourceId A string resource identifier for the user visible description of
   *     the channel, or 0 if no description is provided. The recommended maximum length is 300
   *     characters. The value may be truncated if it is too long. You can change the description of
   *     the channel when the system locale changes by listening for the {@link
   *     Intent#ACTION_LOCALE_CHANGED} broadcast.
   * @param importance The importance of the channel. This controls how interruptive notifications
   *     posted to this channel are. One of {@link #IMPORTANCE_UNSPECIFIED}, {@link
   *     #IMPORTANCE_NONE}, {@link #IMPORTANCE_MIN}, {@link #IMPORTANCE_LOW}, {@link
   *     #IMPORTANCE_DEFAULT} and {@link #IMPORTANCE_HIGH}.
   */
  public static void createNotificationChannel(
      Context context,
      String id,
      @StringRes int nameResourceId,
      @StringRes int descriptionResourceId,
      @Importance int importance) {
    if (Util.SDK_INT >= 26) {
      NotificationManager notificationManager =
          (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
      NotificationChannel channel =
          new NotificationChannel(id, context.getString(nameResourceId), importance);
      if (descriptionResourceId != 0) {
        channel.setDescription(context.getString(descriptionResourceId));
      }
      notificationManager.createNotificationChannel(channel);
    }
  }

  /**
   * Post a notification to be shown in the status bar. If a notification with the same id has
   * already been posted by your application and has not yet been canceled, it will be replaced by
   * the updated information. If {@code notification} is {@code null} then any notification
   * previously shown with the specified id will be cancelled.
   *
   * @param context A {@link Context}.
   * @param id The notification id.
   * @param notification The {@link Notification} to post, or {@code null} to cancel a previously
   *     shown notification.
   */
  public static void setNotification(Context context, int id, @Nullable Notification notification) {
    NotificationManager notificationManager =
        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    if (notification != null) {
      notificationManager.notify(id, notification);
    } else {
      notificationManager.cancel(id);
    }
  }

  private NotificationUtil() {}
}

/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.demo;

import android.app.Notification;
import com.google.android.exoplayer2.offline.Download;
import com.google.android.exoplayer2.offline.DownloadManager;
import com.google.android.exoplayer2.offline.DownloadService;
import com.google.android.exoplayer2.scheduler.PlatformScheduler;
import com.google.android.exoplayer2.ui.DownloadNotificationHelper;
import com.google.android.exoplayer2.util.NotificationUtil;
import com.google.android.exoplayer2.util.Util;
import java.util.List;

/** A service for downloading media. */
public class DemoDownloadService extends DownloadService {

  private static final String CHANNEL_ID = "download_channel";
  private static final int JOB_ID = 1;
  private static final int FOREGROUND_NOTIFICATION_ID = 1;

  private static int nextNotificationId = FOREGROUND_NOTIFICATION_ID + 1;

  private DownloadNotificationHelper notificationHelper;

  public DemoDownloadService() {
    super(
        FOREGROUND_NOTIFICATION_ID,
        DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
        CHANNEL_ID,
        R.string.exo_download_notification_channel_name,
        /* channelDescriptionResourceId= */ 0);
    nextNotificationId = FOREGROUND_NOTIFICATION_ID + 1;
  }

  @Override
  public void onCreate() {
    super.onCreate();
    notificationHelper = new DownloadNotificationHelper(this, CHANNEL_ID);
  }

  @Override
  protected DownloadManager getDownloadManager() {
    return ((DemoApplication) getApplication()).getDownloadManager();
  }

  @Override
  protected PlatformScheduler getScheduler() {
    return Util.SDK_INT >= 21 ? new PlatformScheduler(this, JOB_ID) : null;
  }

  @Override
  protected Notification getForegroundNotification(List<Download> downloads) {
    return notificationHelper.buildProgressNotification(
        R.drawable.ic_download, /* contentIntent= */ null, /* message= */ null, downloads);
  }

  @Override
  protected void onDownloadChanged(Download download) {
    Notification notification;
    if (download.state == Download.STATE_COMPLETED) {
      notification =
          notificationHelper.buildDownloadCompletedNotification(
              R.drawable.ic_download_done,
              /* contentIntent= */ null,
              Util.fromUtf8Bytes(download.request.data));
    } else if (download.state == Download.STATE_FAILED) {
      notification =
          notificationHelper.buildDownloadFailedNotification(
              R.drawable.ic_download_done,
              /* contentIntent= */ null,
              Util.fromUtf8Bytes(download.request.data));
    } else {
      return;
    }
    NotificationUtil.setNotification(this, nextNotificationId++, notification);
  }
}

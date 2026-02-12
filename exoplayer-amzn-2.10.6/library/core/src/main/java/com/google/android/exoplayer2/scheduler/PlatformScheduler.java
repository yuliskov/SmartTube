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
package com.google.android.exoplayer2.scheduler;

import android.annotation.TargetApi;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.PersistableBundle;
import androidx.annotation.RequiresPermission;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;

/**
 * A {@link Scheduler} that uses {@link JobScheduler}. To use this scheduler, you must add {@link
 * PlatformSchedulerService} to your manifest:
 *
 * <pre>{@literal
 * <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>
 * <uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
 *
 * <service android:name="com.google.android.exoplayer2.scheduler.PlatformScheduler$PlatformSchedulerService"
 *     android:permission="android.permission.BIND_JOB_SERVICE"
 *     android:exported="true"/>
 * }</pre>
 */
@TargetApi(21)
public final class PlatformScheduler implements Scheduler {

  private static final boolean DEBUG = false;
  private static final String TAG = "PlatformScheduler";
  private static final String KEY_SERVICE_ACTION = "service_action";
  private static final String KEY_SERVICE_PACKAGE = "service_package";
  private static final String KEY_REQUIREMENTS = "requirements";

  private final int jobId;
  private final ComponentName jobServiceComponentName;
  private final JobScheduler jobScheduler;

  /**
   * @param context Any context.
   * @param jobId An identifier for the jobs scheduled by this instance. If the same identifier was
   *     used by a previous instance, anything scheduled by the previous instance will be canceled
   *     by this instance if {@link #schedule(Requirements, String, String)} or {@link #cancel()}
   *     are called.
   */
  @RequiresPermission(android.Manifest.permission.RECEIVE_BOOT_COMPLETED)
  public PlatformScheduler(Context context, int jobId) {
    this.jobId = jobId;
    jobServiceComponentName = new ComponentName(context, PlatformSchedulerService.class);
    jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
  }

  @Override
  public boolean schedule(Requirements requirements, String servicePackage, String serviceAction) {
    JobInfo jobInfo =
        buildJobInfo(jobId, jobServiceComponentName, requirements, serviceAction, servicePackage);
    int result = jobScheduler.schedule(jobInfo);
    logd("Scheduling job: " + jobId + " result: " + result);
    return result == JobScheduler.RESULT_SUCCESS;
  }

  @Override
  public boolean cancel() {
    logd("Canceling job: " + jobId);
    jobScheduler.cancel(jobId);
    return true;
  }

  // @RequiresPermission constructor annotation should ensure the permission is present.
  @SuppressWarnings("MissingPermission")
  private static JobInfo buildJobInfo(
      int jobId,
      ComponentName jobServiceComponentName,
      Requirements requirements,
      String serviceAction,
      String servicePackage) {
    JobInfo.Builder builder = new JobInfo.Builder(jobId, jobServiceComponentName);

    if (requirements.isUnmeteredNetworkRequired()) {
      builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED);
    } else if (requirements.isNetworkRequired()) {
      builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
    }
    builder.setRequiresDeviceIdle(requirements.isIdleRequired());
    builder.setRequiresCharging(requirements.isChargingRequired());
    builder.setPersisted(true);

    PersistableBundle extras = new PersistableBundle();
    extras.putString(KEY_SERVICE_ACTION, serviceAction);
    extras.putString(KEY_SERVICE_PACKAGE, servicePackage);
    extras.putInt(KEY_REQUIREMENTS, requirements.getRequirements());
    builder.setExtras(extras);

    return builder.build();
  }

  private static void logd(String message) {
    if (DEBUG) {
      Log.d(TAG, message);
    }
  }

  /** A {@link JobService} that starts the target service if the requirements are met. */
  public static final class PlatformSchedulerService extends JobService {
    @Override
    public boolean onStartJob(JobParameters params) {
      logd("PlatformSchedulerService started");
      PersistableBundle extras = params.getExtras();
      Requirements requirements = new Requirements(extras.getInt(KEY_REQUIREMENTS));
      if (requirements.checkRequirements(this)) {
        logd("Requirements are met");
        String serviceAction = extras.getString(KEY_SERVICE_ACTION);
        String servicePackage = extras.getString(KEY_SERVICE_PACKAGE);
        Intent intent =
            new Intent(Assertions.checkNotNull(serviceAction)).setPackage(servicePackage);
        logd("Starting service action: " + serviceAction + " package: " + servicePackage);
        Util.startForegroundService(this, intent);
      } else {
        logd("Requirements are not met");
        jobFinished(params, /* needsReschedule */ true);
      }
      return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
      return false;
    }
  }
}

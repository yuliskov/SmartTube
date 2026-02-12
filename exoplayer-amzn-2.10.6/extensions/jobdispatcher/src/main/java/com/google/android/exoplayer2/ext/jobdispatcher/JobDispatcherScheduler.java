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
package com.google.android.exoplayer2.ext.jobdispatcher;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import com.firebase.jobdispatcher.Constraint;
import com.firebase.jobdispatcher.FirebaseJobDispatcher;
import com.firebase.jobdispatcher.GooglePlayDriver;
import com.firebase.jobdispatcher.Job;
import com.firebase.jobdispatcher.JobParameters;
import com.firebase.jobdispatcher.JobService;
import com.firebase.jobdispatcher.Lifetime;
import com.google.android.exoplayer2.scheduler.Requirements;
import com.google.android.exoplayer2.scheduler.Scheduler;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;

/**
 * A {@link Scheduler} that uses {@link FirebaseJobDispatcher}. To use this scheduler, you must add
 * {@link JobDispatcherSchedulerService} to your manifest:
 *
 * <pre>{@literal
 * <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>
 * <uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
 *
 * <service
 *     android:name="com.google.android.exoplayer2.ext.jobdispatcher.JobDispatcherScheduler$JobDispatcherSchedulerService"
 *     android:exported="false">
 *   <intent-filter>
 *     <action android:name="com.firebase.jobdispatcher.ACTION_EXECUTE"/>
 *   </intent-filter>
 * </service>
 * }</pre>
 *
 * <p>This Scheduler uses Google Play services but does not do any availability checks. Any uses
 * should be guarded with a call to {@code
 * GoogleApiAvailability#isGooglePlayServicesAvailable(android.content.Context)}
 *
 * @see <a
 *     href="https://developers.google.com/android/reference/com/google/android/gms/common/GoogleApiAvailability#isGooglePlayServicesAvailable(android.content.Context)">GoogleApiAvailability</a>
 * @deprecated Use com.google.android.exoplayer2.ext.workmanager.WorkManagerScheduler or {@link
 *     com.google.android.exoplayer2.scheduler.PlatformScheduler}.
 */
@Deprecated
public final class JobDispatcherScheduler implements Scheduler {

  private static final boolean DEBUG = false;
  private static final String TAG = "JobDispatcherScheduler";
  private static final String KEY_SERVICE_ACTION = "service_action";
  private static final String KEY_SERVICE_PACKAGE = "service_package";
  private static final String KEY_REQUIREMENTS = "requirements";

  private final String jobTag;
  private final FirebaseJobDispatcher jobDispatcher;

  /**
   * @param context A context.
   * @param jobTag A tag for jobs scheduled by this instance. If the same tag was used by a previous
   *     instance, anything scheduled by the previous instance will be canceled by this instance if
   *     {@link #schedule(Requirements, String, String)} or {@link #cancel()} are called.
   */
  public JobDispatcherScheduler(Context context, String jobTag) {
    this.jobDispatcher =
        new FirebaseJobDispatcher(new GooglePlayDriver(context.getApplicationContext()));
    this.jobTag = jobTag;
  }

  @Override
  public boolean schedule(Requirements requirements, String servicePackage, String serviceAction) {
    Job job = buildJob(jobDispatcher, requirements, jobTag, servicePackage, serviceAction);
    int result = jobDispatcher.schedule(job);
    logd("Scheduling job: " + jobTag + " result: " + result);
    return result == FirebaseJobDispatcher.SCHEDULE_RESULT_SUCCESS;
  }

  @Override
  public boolean cancel() {
    int result = jobDispatcher.cancel(jobTag);
    logd("Canceling job: " + jobTag + " result: " + result);
    return result == FirebaseJobDispatcher.CANCEL_RESULT_SUCCESS;
  }

  private static Job buildJob(
      FirebaseJobDispatcher dispatcher,
      Requirements requirements,
      String tag,
      String servicePackage,
      String serviceAction) {
    Job.Builder builder =
        dispatcher
            .newJobBuilder()
            .setService(JobDispatcherSchedulerService.class) // the JobService that will be called
            .setTag(tag);

    if (requirements.isUnmeteredNetworkRequired()) {
      builder.addConstraint(Constraint.ON_UNMETERED_NETWORK);
    } else if (requirements.isNetworkRequired()) {
      builder.addConstraint(Constraint.ON_ANY_NETWORK);
    }

    if (requirements.isIdleRequired()) {
      builder.addConstraint(Constraint.DEVICE_IDLE);
    }
    if (requirements.isChargingRequired()) {
      builder.addConstraint(Constraint.DEVICE_CHARGING);
    }
    builder.setLifetime(Lifetime.FOREVER).setReplaceCurrent(true);

    Bundle extras = new Bundle();
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
  public static final class JobDispatcherSchedulerService extends JobService {
    @Override
    public boolean onStartJob(JobParameters params) {
      logd("JobDispatcherSchedulerService is started");
      Bundle extras = params.getExtras();
      Assertions.checkNotNull(extras, "Service started without extras.");
      Requirements requirements = new Requirements(extras.getInt(KEY_REQUIREMENTS));
      if (requirements.checkRequirements(this)) {
        logd("Requirements are met");
        String serviceAction = extras.getString(KEY_SERVICE_ACTION);
        String servicePackage = extras.getString(KEY_SERVICE_PACKAGE);
        Assertions.checkNotNull(serviceAction, "Service action missing.");
        Assertions.checkNotNull(servicePackage, "Service package missing.");
        Intent intent = new Intent(serviceAction).setPackage(servicePackage);
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

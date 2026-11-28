package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;
import android.os.PowerManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Configuration;
import androidx.work.ListenableWorker;
import androidx.work.NetworkType;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerFactory;
import androidx.work.WorkerParameters;

import com.liskovsoft.leanbackassistant.channels.UpdateChannelsWorker;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowPowerManager;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class UpdateChannelsWorkerTest {
    private Context mContext;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.getApplication();
        GlobalPreferences.instance(mContext).setChannelsServiceEnabled(true);
    }

    @After
    public void tearDown() {
        GlobalPreferences.instance(mContext).setChannelsServiceEnabled(true);
    }

    @Test
    public void scheduleAndCancelRespectEnabledPreferenceAndConstraints() throws Exception {
        WorkManager.initialize(
                mContext,
                new Configuration.Builder()
                        .setWorkerFactory(new NoOpWorkerFactory())
                        .build()
        );
        WorkManager workManager = WorkManager.getInstance(mContext);

        try {
            // 1. Scheduling when enabled registers hourly connected-network work
            UpdateChannelsWorker.schedule(mContext);

            List<WorkInfo> scheduled = workManager
                    .getWorkInfosForUniqueWork(UpdateChannelsWorker.WORK_NAME)
                    .get(5, TimeUnit.SECONDS);
            assertEquals(1, scheduled.size());
            assertFalse(scheduled.get(0).getState().isFinished());
            assertEquals(NetworkType.CONNECTED, scheduled.get(0).getConstraints().getRequiredNetworkType());
            assertNotNull(scheduled.get(0).getPeriodicityInfo());
            assertEquals(
                    TimeUnit.MINUTES.toMillis(UpdateChannelsWorker.REPEAT_INTERVAL_MINUTES),
                    scheduled.get(0).getPeriodicityInfo().getRepeatIntervalMillis()
            );

            // 2. Disabling preference and calling cancel() must actually cancel the work
            GlobalPreferences.instance(mContext).setChannelsServiceEnabled(false);
            UpdateChannelsWorker.cancel(mContext);

            List<WorkInfo> afterCancel = workManager
                    .getWorkInfosForUniqueWork(UpdateChannelsWorker.WORK_NAME)
                    .get(5, TimeUnit.SECONDS);
            assertEquals(1, afterCancel.size());
            assertEquals(WorkInfo.State.CANCELLED, afterCancel.get(0).getState());

            // 3. Re-enabling and then calling schedule() after disabling cancels existing work
            GlobalPreferences.instance(mContext).setChannelsServiceEnabled(true);
            UpdateChannelsWorker.schedule(mContext);
            GlobalPreferences.instance(mContext).setChannelsServiceEnabled(false);
            UpdateChannelsWorker.schedule(mContext);

            List<WorkInfo> afterDisabledSchedule = workManager
                    .getWorkInfosForUniqueWork(UpdateChannelsWorker.WORK_NAME)
                    .get(5, TimeUnit.SECONDS);
            assertEquals(1, afterDisabledSchedule.size());
            assertEquals(WorkInfo.State.CANCELLED, afterDisabledSchedule.get(0).getState());
        } finally {
            workManager.cancelAllWork().getResult().get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    public void isDeviceInteractiveReflectsPowerManagerState() {
        PowerManager powerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        ShadowPowerManager shadowPowerManager = Shadows.shadowOf(powerManager);

        shadowPowerManager.setIsInteractive(true);
        assertTrue(UpdateChannelsWorker.isDeviceInteractive(mContext));

        shadowPowerManager.setIsInteractive(false);
        assertFalse(UpdateChannelsWorker.isDeviceInteractive(mContext));
    }

    private static class NoOpWorkerFactory extends WorkerFactory {
        @Nullable
        @Override
        public ListenableWorker createWorker(
                @NonNull Context appContext,
                @NonNull String workerClassName,
                @NonNull WorkerParameters workerParameters) {
            return new Worker(appContext, workerParameters) {
                @NonNull
                @Override
                public Result doWork() {
                    return Result.success();
                }
            };
        }
    }
}

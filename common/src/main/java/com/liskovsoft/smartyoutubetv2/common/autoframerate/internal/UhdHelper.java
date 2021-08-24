package com.liskovsoft.smartyoutubetv2.common.autoframerate.internal;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.Display;
import android.view.Window;
import android.view.WindowManager;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.internal.DisplayHolder.Mode;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

// Source: https://developer.amazon.com/docs/fire-tv/4k-apis-for-hdmi-mode-switch.html#amazonextension

/**
 * UhdHelper is a convenience class to provide interfaces to query<br/>
 * 1) Supported Display modes.<br/>
 * 2) Get current display mode<br/>
 * 3) Set preferred mode.
 */
public class UhdHelper {
    Context mContext;
    private UhdHelperListener mListener;
    final public static String version = "v1.1";
    private String sDisplayClassName = "android.view.Display";
    private String sSupportedModesMethodName = "getSupportedModes";
    private String sPreferredDisplayModeIdFieldName = "preferredDisplayModeId";
    private String sGetModeMethodName = "getMode";
    private String sGetModeIdMethodName = "getModeId";
    private String sGetPhysicalHeightMethodName = "getPhysicalHeight";
    private String sGetPhysicalWidthMethodName = "getPhysicalWidth";
    private String sGetRefreshRateMethodName = "getRefreshRate";
    private AtomicBoolean mIsSetModeInProgress;
    private WorkHandler mWorkHandler;
    private OverlayStateChangeReceiver overlayStateChangeReceiver;
    boolean isReceiversRegistered;
    private DisplayHolder mInternalDisplay;
    private boolean showInterstitial = false;
    private boolean isInterstitialFadeReceived = false;
    private Window mTargetWindow;
    private int currentOverlayStatus;
    public final static String MODESWITCH_OVERLAY_ENABLE = "com.amazon.tv.notification.modeswitch_overlay.action.ENABLE";
    public final static String MODESWITCH_OVERLAY_DISABLE = "com.amazon.tv.notification.modeswitch_overlay.action.DISABLE";
    public final static String MODESWITCH_OVERLAY_EXTRA_STATE = "com.amazon.tv.notification.modeswitch_overlay.extra.STATE";
    public final static String MODESWITCH_OVERLAY_STATE_CHANGED = "com.amazon.tv.notification.modeswitch_overlay.action.STATE_CHANGED";
    public final static int OVERLAY_STATE_DISMISSED = 0;
    DisplayManager mDisplayManager;
    DisplayManager.DisplayListener mDisplayListener;
    /**
     * Physical height of UHD in pixels ( {@value} )
     */
    public static final int HEIGHT_UHD = 2160;
    /**
     * {@value} ms to wait for broadcast before declaring timeout.
     */
    public static final int SET_MODE_TIMEOUT_DELAY_MS = 15 * 1000;
    /**
     * {@value} ms to wait for Interstitial broadcast before declaring timeout.
     */
    public static final int SHOW_INTERSTITIAL_TIMEOUT_DELAY_MS = 2 * 1000;

    private static final String TAG = UhdHelper.class.getSimpleName();

    /**
     * Construct a UhdHelper object.
     *
     * @param context Activity context.
     */
    @SuppressLint("NewApi")
    public UhdHelper(Context context) {
        mContext = context;
        mInternalDisplay = new DisplayHolder();
        mIsSetModeInProgress = new AtomicBoolean(false);
        mWorkHandler = new WorkHandler(Looper.getMainLooper());
        overlayStateChangeReceiver = new OverlayStateChangeReceiver();
        mDisplayManager = (DisplayManager) mContext.getSystemService(Context.DISPLAY_SERVICE);
        isReceiversRegistered = false;

    }

    private static final int MODE_CHANGED_MSG = 1;
    private static final int MODE_CHANGE_TIMEOUT_MSG = 2;
    private static final int SEND_CALLBACK_WITH_SUPPLIED_RESULT = 3;
    private static final int INTERSTITIAL_FADED_BROADCAST_MSG = 4;
    private static final int INTERSTITIAL_TIMEOUT_MSG = 5;

    /**
     * Handler that handles the broadcast or timeout
     * prcoessing and issues callbacks accordingly.
     */
    private class WorkHandler extends Handler {
        private int mRequestedModeId;
        private UhdHelperListener mCallbackListener;

        public WorkHandler(Looper looper) {
            super(looper);
        }

        public void setExpectedMode(int modeId) {
            mRequestedModeId = modeId;
        }

        private void setCallbackListener(UhdHelperListener listener) {
            this.mCallbackListener = listener;
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MODE_CHANGED_MSG:
                    Mode mode = getCurrentMode();
                    if (mode == null) {
                        Log.w(TAG, "Mode query returned null after onDisplayChanged callback");
                        return;
                    }
                    if (mode.getModeId() == mRequestedModeId) {
                        Log.i(TAG, "Callback for expected change Id= " + mRequestedModeId);
                        maybeDoACallback(mode);
                        doPostModeSetCleanup();
                    } else {
                        Log.w(TAG, "Callback received but not expected mode. Mode= " + mode + " expected= " + mRequestedModeId);
                    }

                    break;
                case MODE_CHANGE_TIMEOUT_MSG:
                    Log.i(TAG, "Time out without mode change");
                    maybeDoACallback(null);
                    doPostModeSetCleanup();
                    break;
                case SEND_CALLBACK_WITH_SUPPLIED_RESULT:
                    maybeDoACallback((Mode) msg.obj);
                    if (msg.arg1 == 1) {
                        doPostModeSetCleanup();
                    }
                    break;
                case INTERSTITIAL_FADED_BROADCAST_MSG:
                    if (!isInterstitialFadeReceived) {
                        Log.i(TAG, "Broadcast for text fade received, Initializing the mode change.");
                        isInterstitialFadeReceived = true;
                        initModeChange(mRequestedModeId, null);
                    }
                    break;
                case INTERSTITIAL_TIMEOUT_MSG:
                    if (!isInterstitialFadeReceived) {
                        Log.w(TAG, "Didn't received any broadcast for interstitial text fade till time out, starting the mode change.");
                        isInterstitialFadeReceived = true; //So we don't do another.
                        initModeChange(mRequestedModeId, null);
                    }
                default:
                    break;
            }
        }

        private void maybeDoACallback(Mode mode) {
            if (this.mCallbackListener != null) {
                Log.d(TAG, "Sending callback to listener");
                this.mCallbackListener.onModeChanged(mode);
            } else {
                Log.d(TAG, "Can't issue callback as no listener registered");
            }
        }

        /**
         * Removal of message and unregistering receiver after mode set is done.
         */
        @TargetApi(17)
        private void doPostModeSetCleanup() {
            if (currentOverlayStatus != OVERLAY_STATE_DISMISSED) {
                Log.i(TAG, "Tearing down the overlay Post mode switch attempt.");
                currentOverlayStatus = OVERLAY_STATE_DISMISSED;
                hideOptimizingOverlay();
            }
            synchronized (mIsSetModeInProgress) {
                // need these to be run in order, tell compiler
                // not to reorder the instructions.
                this.removeMessages(MODE_CHANGE_TIMEOUT_MSG);
                if (isReceiversRegistered) {
                    mDisplayManager.unregisterDisplayListener(mDisplayListener);
                    unregisterReceiverSilently(overlayStateChangeReceiver);
                    isReceiversRegistered = false;
                }
                this.removeMessages(MODE_CHANGED_MSG);
                mCallbackListener = null;
                mIsSetModeInProgress.set(false);
            }
        }

        /**
         * Simple unregister. If error occurs, then swallow it silently.
         * @param receiver receiver
         */
        private void unregisterReceiverSilently(BroadcastReceiver receiver) {
            try {
                mContext.unregisterReceiver(receiver);
            } catch (IllegalArgumentException e) { // swallow 'Receiver not registered'
                e.printStackTrace();
            }
        }
    }

    /**
     * Private class for receiving the
     * {@link com.amazon.tv.notification.modeswitch_overlay.extra.STATE STATE} events.
     */
    @SuppressWarnings("JavadocReference")
    private class OverlayStateChangeReceiver extends BroadcastReceiver {
        private final int OVERLAY_FADE_COMPLETE_EXTRA = 3;

        @Override
        public void onReceive(Context context, Intent intent) {
            currentOverlayStatus = intent.getIntExtra((MODESWITCH_OVERLAY_EXTRA_STATE), -1);
            if (currentOverlayStatus == OVERLAY_FADE_COMPLETE_EXTRA && !isInterstitialFadeReceived) {
                mWorkHandler.removeMessages(INTERSTITIAL_TIMEOUT_MSG);
                mWorkHandler.sendMessage(mWorkHandler.obtainMessage(INTERSTITIAL_FADED_BROADCAST_MSG));
                Log.i(TAG, "Got the Interstitial text fade broadcast, Starting the mode change");
            }
        }
    }

    /**
     * Returns the current Display mode.
     *
     * @return {@link Mode Mode}
     * that is currently set on the system or NULL if an error occurred.
     */
    public Mode getCurrentMode() {
        Display currentDisplay = getCurrentDisplay();
        if (currentDisplay == null) {
            return null;
        }
        try {
            Class<?> classToInvestigate = Class.forName(sDisplayClassName);
            Method getModeMethod = classToInvestigate.getDeclaredMethod(sGetModeMethodName);
            Object currentMode = getModeMethod.invoke(currentDisplay);
            return convertReturnedModeToInternalMode(currentMode);
        } catch (Exception e) {
            Log.e(TAG, "error getting mode", e);
        }
        Log.e(TAG, "Current Mode is not present in supported Modes");
        return null;
    }

    /**
     * Utility function to parse android.view.Display,Mode to
     * {@link Mode mode}
     *
     * @param systemMode mode
     * @return {@link Mode Mode} object
     * or NULL if an error occurred.
     */
    private Mode convertReturnedModeToInternalMode(Object systemMode) {
        Mode returnedInstance = null;
        try {
            Class<?> modeClass = systemMode.getClass();
            int modeId = (int) modeClass.getDeclaredMethod(sGetModeIdMethodName).invoke(systemMode);
            int width = (int) modeClass.getDeclaredMethod(sGetPhysicalWidthMethodName).invoke(systemMode);
            int height = (int) modeClass.getDeclaredMethod(sGetPhysicalHeightMethodName).invoke(systemMode);
            float refreshRate = (float) modeClass.getDeclaredMethod(sGetRefreshRateMethodName).invoke(systemMode);
            returnedInstance = mInternalDisplay.getModeInstance(modeId, width, height, refreshRate);
        } catch (Exception e) {
            Log.e(TAG, "error converting", e);
        }
        return returnedInstance;
    }

    /**
     * Returns all the supported modes.
     *
     * @return An array of
     * {@link Mode Mode} objects
     * or NULL if an error occurred.
     */
    public Mode[] getSupportedModes() {
        Mode[] returnedSupportedModes = {};
        try {
            Class<?> classToInvestigate = Class.forName(sDisplayClassName);
            Method getSupportedMethod = classToInvestigate.getDeclaredMethod(sSupportedModesMethodName);
            Object[] SupportedModes = (Object[]) getSupportedMethod.invoke(getCurrentDisplay());
            returnedSupportedModes = new Mode[SupportedModes.length];
            int i = 0;
            for (Object mode : SupportedModes) {
                returnedSupportedModes[i++] = convertReturnedModeToInternalMode(mode);
            }
        } catch (Exception e) {
            Log.e(TAG, "error getting modes", e);
        }
        return returnedSupportedModes;
    }

    /**
     * Returns current {@link Display Display} object.
     * Assumes that the 1st display is the actual display.
     *
     * @return {@link Display Display}
     */
    @TargetApi(17)
    private Display getCurrentDisplay() {
        if (mContext == null) return null;
        Display[] displays = mDisplayManager.getDisplays();
        if (displays == null || displays.length == 0) {
            Log.e(TAG, "ERROR on device to get the display");
            return null;
        }
        //assuming the 1st display is the actual display.
        return displays[0];
    }

    /**
     * Change the display mode to the supplied mode.
     * <p>
     * Note that you must register a {@link UhdHelperListener listener} using
     * {@link UhdHelper#registerModeChangeListener(UhdHelperListener) registerModeChangeListener}
     * to receive the callback for success or failure.
     * Also, note that this method need to be called from Main UI thread.
     * <p>
     * The method will not attempt a mode switch and fail immediately with callback if
     * 1) Device SDK is less than Android L
     * 2) Device is Android L but not Amazon AFT* devices.
     *
     * @param targetWindow        {@link Window Window} to use for setting the display
     *                            and call parameters
     * @param modeId              The desired mode to switch to. Must be a valid mode supported
     *                            by the platform.
     * @param allowOverlayDisplay Flag request to allow display overlay on applicable device.
     */
    @TargetApi(17)
    public void setPreferredDisplayModeId(Window targetWindow, int modeId, boolean allowOverlayDisplay) {
        if (modeId == 0) { // mode is not set
            return;
        }

        /*
         * The Android M preview adds a preferredDisplayModeId to
         * WindowManager.LayoutParams.preferredDisplayModeId API. A PreferredDisplayModeId can be
         * set in the LayoutParams of any Window.
         */
        String deviceName = Build.MODEL;

        // Let the handler know what listener to use, we will
        // send null callback in case of an error.
        mWorkHandler.setCallbackListener(mListener);

        boolean supportedDevice = DisplaySyncHelper.supportsDisplayModeChange();

        //Some basic failure conditions that need handling
        if (!supportedDevice) {
            Log.i(TAG, "Attempt to set preferred Display mode on an unsupported device: " + deviceName);
            //send and cleanup
            mWorkHandler.sendMessage(mWorkHandler.obtainMessage(SEND_CALLBACK_WITH_SUPPLIED_RESULT, 1, 1, null));
            return;
        } else if (!DisplaySyncHelper.isAmazonFireTVDevice()) {
            //We cannot not show interstitial for Non-Amazon Fire TV devices
            allowOverlayDisplay = false;
        }
        if (mIsSetModeInProgress.get()) {
            Log.e(TAG, "setPreferredDisplayModeId is already in progress! " + "Cannot set another while it is in progress");
            //Send but don't cleanup as further processing is expected.
            mWorkHandler.sendMessage(mWorkHandler.obtainMessage(SEND_CALLBACK_WITH_SUPPLIED_RESULT, null));
            return;
        }
        Mode currentMode = getCurrentMode();
        if (currentMode == null || currentMode.getModeId() == modeId) {
            Log.i(TAG, "Current mode id same as mode id requested or is Null. Aborting.");
            //send and cleanup receivers/callback listeners
            mWorkHandler.sendMessage(mWorkHandler.obtainMessage(SEND_CALLBACK_WITH_SUPPLIED_RESULT, 1, 1, currentMode));
            return;
        }
        //Check if the modeId given is even supported by the system.
        Mode[] supportedModes = getSupportedModes();
        boolean isRequestedModeSupported = false;
        boolean isRequestedModeUhd = false;
        for (Mode mode : supportedModes) {
            if (mode.getModeId() == modeId) {
                isRequestedModeUhd = (mode.getPhysicalHeight() >= HEIGHT_UHD ? true : false);
                isRequestedModeSupported = true;
                break;
            }
        }
        if (!isRequestedModeSupported) {
            Log.e(TAG, "Requested mode id not among the supported Mode Id.");
            //send and cleanup receivers/callback listeners
            mWorkHandler.sendMessage(mWorkHandler.obtainMessage(SEND_CALLBACK_WITH_SUPPLIED_RESULT, 1, 1, null));
            return;
        }

        //We are now going to do setMode call and will do callback for it.
        mIsSetModeInProgress.set(true);
        //Let the handler know what modeId onDisplayChanged callback event to look for
        mWorkHandler.setExpectedMode(modeId);
        mContext.registerReceiver(overlayStateChangeReceiver, new IntentFilter(MODESWITCH_OVERLAY_STATE_CHANGED));
        mDisplayListener = new DisplayManager.DisplayListener() {
            @Override
            public void onDisplayAdded(int displayId) {
            }

            @Override
            public void onDisplayRemoved(int displayId) {
            }

            @Override
            public void onDisplayChanged(int displayId) {
                Display display = mDisplayManager.getDisplay(displayId);
                if (display != null) {
                    Log.i(TAG, "onDisplayChanged. id= " + displayId + " " + display.toString());
                }
                mWorkHandler.obtainMessage(MODE_CHANGED_MSG).sendToTarget();
            }
        };
        mDisplayManager.registerDisplayListener(mDisplayListener, mWorkHandler);
        isReceiversRegistered = true;

        mTargetWindow = targetWindow;
        showInterstitial = (allowOverlayDisplay && isRequestedModeUhd);

        //Also check if flag is available, otherwise fail and return
        WindowManager.LayoutParams mWindowAttributes = mTargetWindow.getAttributes();
        //Check if the field is available or not. This is for early failure.
        Class<?> cLayoutParams = mWindowAttributes.getClass();
        Field attributeFlags;
        try {
            attributeFlags = cLayoutParams.getDeclaredField(sPreferredDisplayModeIdFieldName);
        } catch (Exception e) {
            Log.e(TAG, "error getting field", e);
            //send and cleanup receivers/callback listeners
            mWorkHandler.sendMessage(mWorkHandler.obtainMessage(SEND_CALLBACK_WITH_SUPPLIED_RESULT, 1, 1, null));
            return;
        }

        if (showInterstitial) {
            isInterstitialFadeReceived = false;
            showOptimizingOverlay();
            mWorkHandler.sendMessageDelayed(mWorkHandler.obtainMessage(INTERSTITIAL_TIMEOUT_MSG), SHOW_INTERSTITIAL_TIMEOUT_DELAY_MS);
        } else {
            initModeChange(modeId, attributeFlags);
        }
    }

    /**
     * Start the mode change by setting the preferredDisplayModeId field of {@link WindowManager.LayoutParams}
     */
    private void initModeChange(int modeId, Field attributeFlagField) {
        WindowManager.LayoutParams mWindowAttributes = mTargetWindow.getAttributes();
        try {
            if (attributeFlagField == null) {
                Class<?> cLayoutParams = mWindowAttributes.getClass();
                attributeFlagField = cLayoutParams.getDeclaredField(sPreferredDisplayModeIdFieldName);
            }

            // ensure mode is not set
            int currentModeId = attributeFlagField.getInt(mWindowAttributes);
            if (currentModeId != modeId) {
                // attempt mode switch
                attributeFlagField.setInt(mWindowAttributes, modeId);
                mTargetWindow.setAttributes(mWindowAttributes);
            }
        } catch (Exception e) {
            Log.e(TAG, "error getting field", e);
            // send and cleanup receivers/callback listeners
            mWorkHandler.sendMessage(mWorkHandler.obtainMessage(SEND_CALLBACK_WITH_SUPPLIED_RESULT, 1, 1, null));
            return;
        }
        // We assume that the mode change is not instantaneous and will send the onDisplayChanged callback.
        // Start the clock on the mode change timeout
        mWorkHandler.sendMessageDelayed(mWorkHandler.obtainMessage(MODE_CHANGE_TIMEOUT_MSG), SET_MODE_TIMEOUT_DELAY_MS);
    }

    /**
     * Send the broadcast to show overlay display
     */
    private void showOptimizingOverlay() {
        final Intent overlayIntent = new Intent(MODESWITCH_OVERLAY_ENABLE);
        mContext.sendBroadcast(overlayIntent);
        Log.i(TAG, "Sending the broadcast to display overlay");
    }

    /**
     * Send the broadcast to hide overlay display if showing.
     */
    private void hideOptimizingOverlay() {
        final Intent overlayIntent = new Intent(MODESWITCH_OVERLAY_DISABLE);
        mContext.sendBroadcast(overlayIntent);
        Log.i(TAG, "Sending the broadcast to hide display overlay");

    }

    /**
     * Register a {@link UhdHelperListener listener} to be notified of result
     * of the {@link UhdHelper#setPreferredDisplayModeId(Window, int, boolean) setPreferredDisplayModeId}
     * call.
     *
     * @param listener that will receive the result of the callback.
     */
    public void registerModeChangeListener(UhdHelperListener listener) {
        mListener = listener;
    }

    /**
     * Register the {@link UhdHelperListener listener}
     *
     * @param listener
     */
    public void unregisterDisplayModeChangeListener(UhdHelperListener listener) {
        mListener = null;
    }

    public static String toResolution(Mode mode) {
        if (mode == null) {
            return null;
        }

        return String.format("%sx%s@%s", mode.getPhysicalWidth(), mode.getPhysicalHeight(), mode.getRefreshRate());
    }

    public static Mode getCurrentMode(Context context) {
        if (Build.VERSION.SDK_INT < 23) {
            WindowManager wm = (WindowManager) context.getApplicationContext().getSystemService(Context.WINDOW_SERVICE); // the results will be higher than using the activity context object or the getWindowManager() shortcut
            Display display = wm.getDefaultDisplay();

            if (display == null) {
                return null;
            }

            Point size = new Point();
            display.getSize(size);

            return new Mode(0, size.x, size.y, display.getRefreshRate());
        } else {
            Display display = getCurrentDisplay(context);

            if (display == null) {
                return null;
            }

            Display.Mode mode = display.getMode();

            return new Mode(mode.getModeId(), mode.getPhysicalWidth(), mode.getPhysicalHeight(), mode.getRefreshRate());
        }
    }

    @TargetApi(17)
    private static Display getCurrentDisplay(Context context) {
        DisplayManager displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        if (displayManager == null)
            return null;
        Display[] displays = displayManager.getDisplays();
        if (displays == null || displays.length == 0) {
            return null;
        }
        //assuming the 1st display is the actual display.
        return displays[0];
    }
}

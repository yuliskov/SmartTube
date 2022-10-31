/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.os.Build;
import android.util.Log;

public final class AmazonQuirks {

    //ordering of the static initializations is important.
    private static final String TAG = AmazonQuirks.class.getSimpleName();
    private static final String FIRETV_GEN1_DEVICE_MODEL       = "AFTB";
    private static final String FIRETV_GEN2_DEVICE_MODEL       = "AFTS";
    private static final String FIRETV_STICK_DEVICE_MODEL      = "AFTM";
    private static final String FIRETV_STICK_GEN2_DEVICE_MODEL = "AFTT";
    private static final String KINDLE_TABLET_DEVICE_MODEL     = "KF";
    private static final String FIRE_PHONE_DEVICE_MODEL        = "SD";
    private static final String AMAZON                         = "Amazon";

    private static final String DEVICEMODEL  = Build.MODEL;
    private static final String MANUFACTURER = Build.MANUFACTURER;

    private static final int AUDIO_HARDWARE_LATENCY_FOR_TABLETS = 90000;

    //caching
    private static final boolean isAmazonDevice;
    private static final boolean isFireTVGen1;
    private static final boolean isFireTVStick;
    private static final boolean isFireTVGen2;
    private static final boolean isKindleTablet;
    private static final boolean isFirePhone;

    private static boolean isSnappingToVsyncDisabled;
    private static boolean skipProfileLevelCheck;

    // This static block must be the last
    //INIT ORDERING IS IMPORTANT IN THIS BLOCK!
    static {
        isAmazonDevice = MANUFACTURER.equalsIgnoreCase(AMAZON);
        isFireTVGen1   = isAmazonDevice && DEVICEMODEL.equalsIgnoreCase(FIRETV_GEN1_DEVICE_MODEL);
        isFireTVGen2   = isAmazonDevice && DEVICEMODEL.equalsIgnoreCase(FIRETV_GEN2_DEVICE_MODEL);
        isFireTVStick  = isAmazonDevice && DEVICEMODEL.equalsIgnoreCase(FIRETV_STICK_DEVICE_MODEL);
        isKindleTablet = isAmazonDevice && DEVICEMODEL.startsWith(KINDLE_TABLET_DEVICE_MODEL);
        isFirePhone = isAmazonDevice && DEVICEMODEL.startsWith(FIRE_PHONE_DEVICE_MODEL);
        loadForcedLogSettings();
    }

    private AmazonQuirks(){}

    public static boolean isDolbyPassthroughQuirkEnabled() {
        // Sets dolby passthrough quirk for Amazon Fire TV (Gen 1) Family
        return isFireTVGen1Family();
    }

    public static boolean isAmazonDevice(){
        return isAmazonDevice;
    }

    public static boolean isFireTVGen1Family() {
        return isFireTVGen1 || isFireTVStick;
    }

    public static boolean isFireTVGen2() {
        return isFireTVGen2;
    }

    // We assume that this function is called only for supported
    // passthrough mimetypes such as AC3, EAC3 etc
    public static boolean useDefaultPassthroughDecoder() {
        //Use platform decoder only for
        // - FireTV Gen1
        // - FireTV Stick
        if (isFireTVGen1Family()) {
            Log.i(TAG, "Using platform Dolby decoder");
            return false;
        }

        Log.i(TAG, "Using default Dolby pass-through decoder");
        return true;
    }

    public static boolean isLatencyQuirkEnabled() {
        // Sets latency quirk for Amazon KK and JB Tablets and Fire Phone
        return (Util.SDK_INT <= 19) && (isKindleTablet || isFirePhone);
    }

    public static int getAudioHWLatency() {
        // this function is called only when the above function
        // returns true for latency quirk. So no need to check for
        // SDK version and device type again
        return AUDIO_HARDWARE_LATENCY_FOR_TABLETS;
    }
    /**
      * Updates log level based on a local system property of the device. This can be very useful
      * to enable logging in scenarios when a 3P developer has issues we need to assist
      * Example:
      * adb shell setprop com.amazon.exoplayer.forcelog Video:verbose#Audio:debug
      */
     private static void loadForcedLogSettings() {
         String setting = getSystemProperty("com.amazon.exoplayer.forcelog");
         // this happens on release builds, and without disabling setenforce
         if (setting == null || setting.equals("")) {
             return;
         }
         try {
             String[] pairs = setting.split("#");
             for (String onePair : pairs) {
                 String[] elements = onePair.split(":");
                 Logger.Module module = Logger.Module.valueOf(elements[0]);
                 int level = 0;
                 String levelStr = elements[1];
                 switch (levelStr.toLowerCase()) {
                     case "error"  : level = Log.ERROR;   break;
                     case "info"   : level = Log.INFO;    break;
                     case "verbose": level = Log.VERBOSE; break;
                     case "warn"   : level = Log.WARN;    break;
                     default       : level = Log.DEBUG;   break;
                 }
                 Logger.setLogLevel(module, level);
             }
         } catch (Exception ex) {
             Log.e(TAG, "Could not set logging level.", ex);
         }
     }

     // for debugging purposes only, so it is a minimalistic solution
     public static String getSystemProperty(String key) {
         try {
             Class<?> SP = Class.forName("android.os.SystemProperties");
             return (String) SP.getMethod("get", String.class).invoke(null, key);
         } catch (Exception e) {
             return null;
         }
     }
    /*
     * To disable snapping the frame release times to VSYNC call this function with true
     * By default, snapping to VSYNC is enabled if this function is not called.
     */
    public static void disableSnappingToVsync(boolean disable) {
         isSnappingToVsyncDisabled = disable;
    }

    public static boolean isSnappingToVsyncDisabled() {
         return isSnappingToVsyncDisabled;
    }

    /**
     * Called to set quirk which determines if codec profile checks should be
     * skipped.  Call this api with true to make the player skip checking the
     * support for profile levels of the content. Only use this if you are sure
     * that the target device is capable playing the content, but under reports
     * the capabilities.
     *
     * @param  skip If true then skip codec profile check
     */
    public static void skipProfileLevelCheck(boolean skip) {
        skipProfileLevelCheck = skip;
    }

    /**
     * Returns the state of the skip codec profile check quirk.
     *
     * @return the value of the skip codec profile check quirk
     */
    public static boolean shouldSkipProfileLevelCheck() {
        return skipProfileLevelCheck;
    }
}

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

import java.lang.IllegalArgumentException;
import android.util.Log;
import java.util.Arrays;

/**
 * A common logger module that allows any module to log.
 * Supports log level configuration for each module.
 * call {@link Logger#setLogLevel} API to configure the modules and log levels from the app
 */
public class Logger {
    public enum Module {
        Unknown,
        /**
         *  Module that includes MediaCodecTrackRenderer
         */
        AudioVideoCommon,
        /**
         *  Module that includes MediaCodecAudioTrackRenderer and AudioTrack
         */
        Audio,
        /**
         *  Module that includes MediaCodecVideoTrackRenderer
         */
        Video,
        /**
         *  Module that includes MOD_VIDEO, MOD_AUDIO and MOD_AUDIO_VIDEO_COMMON
         */
        AudioVideo,

        Text,
        Source,
        Manifest,
        Player,
        /**
         *  Includes all modules
         */
        All
    }

    private String mTag = "UNKNOWN";
    private int mModule = Module.Unknown.ordinal();
    private static final int[] enabledModules = new int[Module.All.ordinal()];
    /**
     * By default all modules are set to Log level Log.INFO
     */
    static {
        Arrays.fill(enabledModules, Log.INFO);
    }
    /**
     * Constructor for this class
     * @param tag The file TAG to be used in logging
     * @param module The module to which this file belongs to.
     */
    public Logger(Module module, String tag) {
        if (tag == null) {
            throw new IllegalArgumentException("Null Tag");
        }
        mTag = tag;
        mModule = module.ordinal();
    }
    /**
     * Configures the log levels for a specific module
     * @param module The module to which this file belongs to. One of the
     *   MOD_xxx values
     *   Setting All enables logging in all modules.
     *   Setting AudioVideo enables logging in both Audio and Video
     *   Setting Audio or Video enables logging in AudioVideoCommon
     * @param logLevel Log level for this module. One of the constants in
     *    android.util.Log. i.e Log.ERROR, Log.WARNING, Log.INFO, Log.DEBUG and Log.VERBOSE
     *    Info , error and warning logs are always printed.
     *    Setting to Log.INFO, Log.ERROR, Log.WARNING etc disables DEBUG and VERBOSE logs.
     *    Setting to Log.VERBOSE prints Debug and Verbose logs.
     *    Setting to Log.DEBUG prints Debug logs (excludes Verbose)
     */
    public static void setLogLevel(Module module, int logLevel) {
        if (module.compareTo(Module.All) == 0) {
            Arrays.fill(enabledModules, logLevel);
        } else {
            enabledModules[module.ordinal()] = logLevel;
        }
        if (module.compareTo(Module.Audio) >= 0 && module.compareTo(Module.AudioVideo) <= 0) {
            enabledModules[Module.AudioVideoCommon.ordinal()] = logLevel;
        }
        if (module.compareTo(Module.AudioVideo) == 0) {
            enabledModules[Module.Audio.ordinal()] = logLevel;
            enabledModules[Module.Video.ordinal()] = logLevel;
        }
    }

    /**
     * Call this function to override the tag given in constructor
     * @param tag The file TAG to be used in logging.
     */
    public void setTAG(String tag) {
        if (tag == null) {
            throw new IllegalArgumentException("Null Tag");
        }
        mTag = tag;
    }

    /**
     * Call this function to override the module given in constructor
     * @param module The module to which this file belongs to.
     */
    public void setModule(Module module) {
        mModule = module.ordinal();
    }

    public boolean allowVerbose() {
        return (enabledModules[mModule] == Log.VERBOSE);
    }

    public boolean allowDebug() {
        return (enabledModules[mModule] <= Log.DEBUG);
    }

    /**
     * Function to print verbose level logs. Prints only
     * if the log level is Log.VERBOSE
     * @param msg The log message
     */
    public void v(String msg) {
        if (enabledModules[mModule] == Log.VERBOSE) {
            Log.v(mTag, msg);
        }
    }
    /**
     * Function to print debug level logs. Prints only
     * if the log level is Log.DEBUG or Log.VERBOSE
     * @param msg The log message
     */
    public void d(String msg) {
        if (enabledModules[mModule] <= Log.DEBUG) {
            Log.d(mTag, msg);
        }
    }
    /**
     * Function to print info level logs
     * @param msg The log message
     */
    public void i(String msg) {
        Log.i(mTag, msg);
    }
    /**
     * Function to print warning level logs
     * @param msg The log message
     */
    public void w(String msg) {
        Log.w(mTag, msg);
    }
    /**
     * Function to print error level logs
     * @param msg The log message
     */
    public void e(String msg) {
        Log.e(mTag, msg);
    }

    /**
     * Function to print error level logs and throwable
     * @param msg The log message
     * @param tr The throwable
     */
    public void e(String msg, Throwable tr) {
        Log.e(mTag, msg, tr);
    }
}

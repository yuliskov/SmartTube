/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.drm;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import android.text.TextUtils;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.drm.DefaultDrmSession.ProvisioningManager;
import com.google.android.exoplayer2.drm.DrmInitData.SchemeData;
import com.google.android.exoplayer2.drm.DrmSession.DrmSessionException;
import com.google.android.exoplayer2.drm.ExoMediaDrm.OnEventListener;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.EventDispatcher;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * A {@link DrmSessionManager} that supports playbacks using {@link ExoMediaDrm}.
 */
@TargetApi(18)
public class DefaultDrmSessionManager<T extends ExoMediaCrypto> implements DrmSessionManager<T>,
    ProvisioningManager<T> {

  /**
   * Signals that the {@link DrmInitData} passed to {@link #acquireSession} does not contain does
   * not contain scheme data for the required UUID.
   */
  public static final class MissingSchemeDataException extends Exception {

    private MissingSchemeDataException(UUID uuid) {
      super("Media does not support uuid: " + uuid);
    }
  }

  /**
   * The key to use when passing CustomData to a PlayReady instance in an optional parameter map.
   */
  public static final String PLAYREADY_CUSTOM_DATA_KEY = "PRCustomData";

  /**
   * Determines the action to be done after a session acquired. One of {@link #MODE_PLAYBACK},
   * {@link #MODE_QUERY}, {@link #MODE_DOWNLOAD} or {@link #MODE_RELEASE}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({MODE_PLAYBACK, MODE_QUERY, MODE_DOWNLOAD, MODE_RELEASE})
  public @interface Mode {}
  /**
   * Loads and refreshes (if necessary) a license for playback. Supports streaming and offline
   * licenses.
   */
  public static final int MODE_PLAYBACK = 0;
  /**
   * Restores an offline license to allow its status to be queried.
   */
  public static final int MODE_QUERY = 1;
  /** Downloads an offline license or renews an existing one. */
  public static final int MODE_DOWNLOAD = 2;
  /** Releases an existing offline license. */
  public static final int MODE_RELEASE = 3;
  /** Number of times to retry for initial provisioning and key request for reporting error. */
  public static final int INITIAL_DRM_REQUEST_RETRY_COUNT = 3;

  private static final String TAG = "DefaultDrmSessionMgr";

  private final UUID uuid;
  private final ExoMediaDrm<T> mediaDrm;
  private final MediaDrmCallback callback;
  private final @Nullable HashMap<String, String> optionalKeyRequestParameters;
  private final EventDispatcher<DefaultDrmSessionEventListener> eventDispatcher;
  private final boolean multiSession;
  private final int initialDrmRequestRetryCount;

  private final List<DefaultDrmSession<T>> sessions;
  private final List<DefaultDrmSession<T>> provisioningSessions;

  private @Nullable Looper playbackLooper;
  private int mode;
  private @Nullable byte[] offlineLicenseKeySetId;

  /* package */ volatile @Nullable MediaDrmHandler mediaDrmHandler;

  /**
   * Instantiates a new instance using the Widevine scheme.
   *
   * @param callback Performs key and provisioning requests.
   * @param optionalKeyRequestParameters An optional map of parameters to pass as the last argument
   *     to {@link ExoMediaDrm#getKeyRequest(byte[], List, int, HashMap)}. May be null.
   * @throws UnsupportedDrmException If the specified DRM scheme is not supported.
   */
  public static DefaultDrmSessionManager<FrameworkMediaCrypto> newWidevineInstance(
      MediaDrmCallback callback, @Nullable HashMap<String, String> optionalKeyRequestParameters)
      throws UnsupportedDrmException {
    return newFrameworkInstance(C.WIDEVINE_UUID, callback, optionalKeyRequestParameters);
  }

  /**
   * Instantiates a new instance using the PlayReady scheme.
   *
   * <p>Note that PlayReady is unsupported by most Android devices, with the exception of Android TV
   * devices, which do provide support.
   *
   * @param callback Performs key and provisioning requests.
   * @param customData Optional custom data to include in requests generated by the instance.
   * @throws UnsupportedDrmException If the specified DRM scheme is not supported.
   */
  public static DefaultDrmSessionManager<FrameworkMediaCrypto> newPlayReadyInstance(
      MediaDrmCallback callback, @Nullable String customData) throws UnsupportedDrmException {
    HashMap<String, String> optionalKeyRequestParameters;
    if (!TextUtils.isEmpty(customData)) {
      optionalKeyRequestParameters = new HashMap<>();
      optionalKeyRequestParameters.put(PLAYREADY_CUSTOM_DATA_KEY, customData);
    } else {
      optionalKeyRequestParameters = null;
    }
    return newFrameworkInstance(C.PLAYREADY_UUID, callback, optionalKeyRequestParameters);
  }

  /**
   * Instantiates a new instance.
   *
   * @param uuid The UUID of the drm scheme.
   * @param callback Performs key and provisioning requests.
   * @param optionalKeyRequestParameters An optional map of parameters to pass as the last argument
   *     to {@link ExoMediaDrm#getKeyRequest(byte[], List, int, HashMap)}. May be null.
   * @throws UnsupportedDrmException If the specified DRM scheme is not supported.
   */
  public static DefaultDrmSessionManager<FrameworkMediaCrypto> newFrameworkInstance(
      UUID uuid,
      MediaDrmCallback callback,
      @Nullable HashMap<String, String> optionalKeyRequestParameters)
      throws UnsupportedDrmException {
    return new DefaultDrmSessionManager<>(
        uuid,
        FrameworkMediaDrm.newInstance(uuid),
        callback,
        optionalKeyRequestParameters,
        /* multiSession= */ false,
        INITIAL_DRM_REQUEST_RETRY_COUNT);
  }

  /**
   * @param uuid The UUID of the drm scheme.
   * @param mediaDrm An underlying {@link ExoMediaDrm} for use by the manager.
   * @param callback Performs key and provisioning requests.
   * @param optionalKeyRequestParameters An optional map of parameters to pass as the last argument
   *     to {@link ExoMediaDrm#getKeyRequest(byte[], List, int, HashMap)}. May be null.
   */
  public DefaultDrmSessionManager(
      UUID uuid,
      ExoMediaDrm<T> mediaDrm,
      MediaDrmCallback callback,
      @Nullable HashMap<String, String> optionalKeyRequestParameters) {
    this(
        uuid,
        mediaDrm,
        callback,
        optionalKeyRequestParameters,
        /* multiSession= */ false,
        INITIAL_DRM_REQUEST_RETRY_COUNT);
  }

  /**
   * @param uuid The UUID of the drm scheme.
   * @param mediaDrm An underlying {@link ExoMediaDrm} for use by the manager.
   * @param callback Performs key and provisioning requests.
   * @param optionalKeyRequestParameters An optional map of parameters to pass as the last argument
   *     to {@link ExoMediaDrm#getKeyRequest(byte[], List, int, HashMap)}. May be null.
   * @param multiSession A boolean that specify whether multiple key session support is enabled.
   *     Default is false.
   */
  public DefaultDrmSessionManager(
      UUID uuid,
      ExoMediaDrm<T> mediaDrm,
      MediaDrmCallback callback,
      @Nullable HashMap<String, String> optionalKeyRequestParameters,
      boolean multiSession) {
    this(
        uuid,
        mediaDrm,
        callback,
        optionalKeyRequestParameters,
        multiSession,
        INITIAL_DRM_REQUEST_RETRY_COUNT);
  }

  /**
   * @param uuid The UUID of the drm scheme.
   * @param mediaDrm An underlying {@link ExoMediaDrm} for use by the manager.
   * @param callback Performs key and provisioning requests.
   * @param optionalKeyRequestParameters An optional map of parameters to pass as the last argument
   *     to {@link ExoMediaDrm#getKeyRequest(byte[], List, int, HashMap)}. May be null.
   * @param multiSession A boolean that specify whether multiple key session support is enabled.
   *     Default is false.
   * @param initialDrmRequestRetryCount The number of times to retry for initial provisioning and
   *     key request before reporting error.
   */
  public DefaultDrmSessionManager(
      UUID uuid,
      ExoMediaDrm<T> mediaDrm,
      MediaDrmCallback callback,
      @Nullable HashMap<String, String> optionalKeyRequestParameters,
      boolean multiSession,
      int initialDrmRequestRetryCount) {
    Assertions.checkNotNull(uuid);
    Assertions.checkNotNull(mediaDrm);
    Assertions.checkArgument(!C.COMMON_PSSH_UUID.equals(uuid), "Use C.CLEARKEY_UUID instead");
    this.uuid = uuid;
    this.mediaDrm = mediaDrm;
    this.callback = callback;
    this.optionalKeyRequestParameters = optionalKeyRequestParameters;
    this.eventDispatcher = new EventDispatcher<>();
    this.multiSession = multiSession;
    this.initialDrmRequestRetryCount = initialDrmRequestRetryCount;
    mode = MODE_PLAYBACK;
    sessions = new ArrayList<>();
    provisioningSessions = new ArrayList<>();
    if (multiSession && C.WIDEVINE_UUID.equals(uuid) && Util.SDK_INT >= 19) {
      // TODO: Enabling session sharing probably doesn't do anything useful here. It would only be
      // useful if DefaultDrmSession instances were aware of one another's state, which is not
      // implemented. Or if custom renderers are being used that allow playback to proceed before
      // keys, which seems unlikely to be true in practice.
      mediaDrm.setPropertyString("sessionSharing", "enable");
    }
    mediaDrm.setOnEventListener(new MediaDrmEventListener());
  }

  /**
   * Adds a {@link DefaultDrmSessionEventListener} to listen to drm session events.
   *
   * @param handler A handler to use when delivering events to {@code eventListener}.
   * @param eventListener A listener of events.
   */
  public final void addListener(Handler handler, DefaultDrmSessionEventListener eventListener) {
    eventDispatcher.addListener(handler, eventListener);
  }

  /**
   * Removes a {@link DefaultDrmSessionEventListener} from the list of drm session event listeners.
   *
   * @param eventListener The listener to remove.
   */
  public final void removeListener(DefaultDrmSessionEventListener eventListener) {
    eventDispatcher.removeListener(eventListener);
  }

  /**
   * Provides access to {@link ExoMediaDrm#getPropertyString(String)}.
   * <p>
   * This method may be called when the manager is in any state.
   *
   * @param key The key to request.
   * @return The retrieved property.
   */
  public final String getPropertyString(String key) {
    return mediaDrm.getPropertyString(key);
  }

  /**
   * Provides access to {@link ExoMediaDrm#setPropertyString(String, String)}.
   * <p>
   * This method may be called when the manager is in any state.
   *
   * @param key The property to write.
   * @param value The value to write.
   */
  public final void setPropertyString(String key, String value) {
    mediaDrm.setPropertyString(key, value);
  }

  /**
   * Provides access to {@link ExoMediaDrm#getPropertyByteArray(String)}.
   * <p>
   * This method may be called when the manager is in any state.
   *
   * @param key The key to request.
   * @return The retrieved property.
   */
  public final byte[] getPropertyByteArray(String key) {
    return mediaDrm.getPropertyByteArray(key);
  }

  /**
   * Provides access to {@link ExoMediaDrm#setPropertyByteArray(String, byte[])}.
   * <p>
   * This method may be called when the manager is in any state.
   *
   * @param key The property to write.
   * @param value The value to write.
   */
  public final void setPropertyByteArray(String key, byte[] value) {
    mediaDrm.setPropertyByteArray(key, value);
  }

  /**
   * Sets the mode, which determines the role of sessions acquired from the instance. This must be
   * called before {@link #acquireSession(Looper, DrmInitData)} is called.
   *
   * <p>By default, the mode is {@link #MODE_PLAYBACK} and a streaming license is requested when
   * required.
   *
   * <p>{@code mode} must be one of these:
   *
   * <ul>
   *   <li>{@link #MODE_PLAYBACK}: If {@code offlineLicenseKeySetId} is null, a streaming license is
   *       requested otherwise the offline license is restored.
   *   <li>{@link #MODE_QUERY}: {@code offlineLicenseKeySetId} can not be null. The offline license
   *       is restored.
   *   <li>{@link #MODE_DOWNLOAD}: If {@code offlineLicenseKeySetId} is null, an offline license is
   *       requested otherwise the offline license is renewed.
   *   <li>{@link #MODE_RELEASE}: {@code offlineLicenseKeySetId} can not be null. The offline
   *       license is released.
   * </ul>
   *
   * @param mode The mode to be set.
   * @param offlineLicenseKeySetId The key set id of the license to be used with the given mode.
   */
  public void setMode(@Mode int mode, @Nullable byte[] offlineLicenseKeySetId) {
    Assertions.checkState(sessions.isEmpty());
    if (mode == MODE_QUERY || mode == MODE_RELEASE) {
      Assertions.checkNotNull(offlineLicenseKeySetId);
    }
    this.mode = mode;
    this.offlineLicenseKeySetId = offlineLicenseKeySetId;
  }

  // DrmSessionManager implementation.

  @Override
  public boolean canAcquireSession(DrmInitData drmInitData) {
    if (offlineLicenseKeySetId != null) {
      // An offline license can be restored so a session can always be acquired.
      return true;
    }
    List<SchemeData> schemeDatas = getSchemeDatas(drmInitData, uuid, true);
    if (schemeDatas.isEmpty()) {
      if (drmInitData.schemeDataCount == 1 && drmInitData.get(0).matches(C.COMMON_PSSH_UUID)) {
        // Assume scheme specific data will be added before the session is opened.
        Log.w(
            TAG, "DrmInitData only contains common PSSH SchemeData. Assuming support for: " + uuid);
      } else {
        // No data for this manager's scheme.
        return false;
      }
    }
    String schemeType = drmInitData.schemeType;
    if (schemeType == null || C.CENC_TYPE_cenc.equals(schemeType)) {
      // If there is no scheme information, assume patternless AES-CTR.
      return true;
    } else if (C.CENC_TYPE_cbc1.equals(schemeType) || C.CENC_TYPE_cbcs.equals(schemeType)
        || C.CENC_TYPE_cens.equals(schemeType)) {
      // API support for AES-CBC and pattern encryption was added in API 24. However, the
      // implementation was not stable until API 25.
      return Util.SDK_INT >= 25;
    }
    // Unknown schemes, assume one of them is supported.
    return true;
  }

  @Override
  public DrmSession<T> acquireSession(Looper playbackLooper, DrmInitData drmInitData) {
    Assertions.checkState(this.playbackLooper == null || this.playbackLooper == playbackLooper);
    if (sessions.isEmpty()) {
      this.playbackLooper = playbackLooper;
      if (mediaDrmHandler == null) {
        mediaDrmHandler = new MediaDrmHandler(playbackLooper);
      }
    }

    List<SchemeData> schemeDatas = null;
    if (offlineLicenseKeySetId == null) {
      schemeDatas = getSchemeDatas(drmInitData, uuid, false);
      if (schemeDatas.isEmpty()) {
        final MissingSchemeDataException error = new MissingSchemeDataException(uuid);
        eventDispatcher.dispatch(listener -> listener.onDrmSessionManagerError(error));
        return new ErrorStateDrmSession<>(new DrmSessionException(error));
      }
    }

    DefaultDrmSession<T> session;
    if (!multiSession) {
      session = sessions.isEmpty() ? null : sessions.get(0);
    } else {
      // Only use an existing session if it has matching init data.
      session = null;
      for (DefaultDrmSession<T> existingSession : sessions) {
        if (Util.areEqual(existingSession.schemeDatas, schemeDatas)) {
          session = existingSession;
          break;
        }
      }
    }

    if (session == null) {
      // Create a new session.
      session =
          new DefaultDrmSession<>(
              uuid,
              mediaDrm,
              this,
              schemeDatas,
              mode,
              offlineLicenseKeySetId,
              optionalKeyRequestParameters,
              callback,
              playbackLooper,
              eventDispatcher,
              initialDrmRequestRetryCount);
      sessions.add(session);
    }
    session.acquire();
    return session;
  }

  @Override
  public void releaseSession(DrmSession<T> session) {
    if (session instanceof ErrorStateDrmSession) {
      // Do nothing.
      return;
    }

    DefaultDrmSession<T> drmSession = (DefaultDrmSession<T>) session;
    if (drmSession.release()) {
      sessions.remove(drmSession);
      if (provisioningSessions.size() > 1 && provisioningSessions.get(0) == drmSession) {
        // Other sessions were waiting for the released session to complete a provision operation.
        // We need to have one of those sessions perform the provision operation instead.
        provisioningSessions.get(1).provision();
      }
      provisioningSessions.remove(drmSession);
    }
  }

  // ProvisioningManager implementation.

  @Override
  public void provisionRequired(DefaultDrmSession<T> session) {
    if (provisioningSessions.contains(session)) {
      // The session has already requested provisioning.
      return;
    }
    provisioningSessions.add(session);
    if (provisioningSessions.size() == 1) {
      // This is the first session requesting provisioning, so have it perform the operation.
      session.provision();
    }
  }

  @Override
  public void onProvisionCompleted() {
    for (DefaultDrmSession<T> session : provisioningSessions) {
      session.onProvisionCompleted();
    }
    provisioningSessions.clear();
  }

  @Override
  public void onProvisionError(Exception error) {
    for (DefaultDrmSession<T> session : provisioningSessions) {
      session.onProvisionError(error);
    }
    provisioningSessions.clear();
  }

  // Internal methods.

  /**
   * Extracts {@link SchemeData} instances suitable for the given DRM scheme {@link UUID}.
   *
   * @param drmInitData The {@link DrmInitData} from which to extract the {@link SchemeData}.
   * @param uuid The UUID.
   * @param allowMissingData Whether a {@link SchemeData} with null {@link SchemeData#data} may be
   *     returned.
   * @return The extracted {@link SchemeData} instances, or an empty list if no suitable data is
   *     present.
   */
  private static List<SchemeData> getSchemeDatas(
      DrmInitData drmInitData, UUID uuid, boolean allowMissingData) {
    // Look for matching scheme data (matching the Common PSSH box for ClearKey).
    List<SchemeData> matchingSchemeDatas = new ArrayList<>(drmInitData.schemeDataCount);
    for (int i = 0; i < drmInitData.schemeDataCount; i++) {
      SchemeData schemeData = drmInitData.get(i);
      boolean uuidMatches = schemeData.matches(uuid)
          || (C.CLEARKEY_UUID.equals(uuid) && schemeData.matches(C.COMMON_PSSH_UUID));
      if (uuidMatches && (schemeData.data != null || allowMissingData)) {
        matchingSchemeDatas.add(schemeData);
      }
    }
    return matchingSchemeDatas;
  }

  @SuppressLint("HandlerLeak")
  private class MediaDrmHandler extends Handler {

    public MediaDrmHandler(Looper looper) {
      super(looper);
    }

    @Override
    public void handleMessage(Message msg) {
      byte[] sessionId = (byte[]) msg.obj;
      if (sessionId == null) {
        // The event is not associated with any particular session.
        return;
      }
      for (DefaultDrmSession<T> session : sessions) {
        if (session.hasSessionId(sessionId)) {
          session.onMediaDrmEvent(msg.what);
          return;
        }
      }
    }

  }

  private class MediaDrmEventListener implements OnEventListener<T> {

    @Override
    public void onEvent(
        ExoMediaDrm<? extends T> md,
        @Nullable byte[] sessionId,
        int event,
        int extra,
        @Nullable byte[] data) {
      Assertions.checkNotNull(mediaDrmHandler).obtainMessage(event, sessionId).sendToTarget();
    }

  }

}

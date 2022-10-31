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
import android.media.NotProvisionedException;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import androidx.annotation.Nullable;
import android.util.Pair;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.drm.DrmInitData.SchemeData;
import com.google.android.exoplayer2.drm.ExoMediaDrm.KeyRequest;
import com.google.android.exoplayer2.drm.ExoMediaDrm.ProvisionRequest;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.EventDispatcher;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/**
 * A {@link DrmSession} that supports playbacks using {@link ExoMediaDrm}.
 */
@TargetApi(18)
/* package */ class DefaultDrmSession<T extends ExoMediaCrypto> implements DrmSession<T> {

  /**
   * Manages provisioning requests.
   */
  public interface ProvisioningManager<T extends ExoMediaCrypto> {

    /**
     * Called when a session requires provisioning. The manager <em>may</em> call
     * {@link #provision()} to have this session perform the provisioning operation. The manager
     * <em>will</em> call {@link DefaultDrmSession#onProvisionCompleted()} when provisioning has
     * completed, or {@link DefaultDrmSession#onProvisionError} if provisioning fails.
     *
     * @param session The session.
     */
    void provisionRequired(DefaultDrmSession<T> session);

    /**
     * Called by a session when it fails to perform a provisioning operation.
     *
     * @param error The error that occurred.
     */
    void onProvisionError(Exception error);

    /**
     * Called by a session when it successfully completes a provisioning operation.
     */
    void onProvisionCompleted();

  }

  private static final String TAG = "DefaultDrmSession";

  private static final int MSG_PROVISION = 0;
  private static final int MSG_KEYS = 1;
  private static final int MAX_LICENSE_DURATION_TO_RENEW = 60;

  /** The DRM scheme datas, or null if this session uses offline keys. */
  public final @Nullable List<SchemeData> schemeDatas;

  private final ExoMediaDrm<T> mediaDrm;
  private final ProvisioningManager<T> provisioningManager;
  private final @DefaultDrmSessionManager.Mode int mode;
  private final @Nullable HashMap<String, String> optionalKeyRequestParameters;
  private final EventDispatcher<DefaultDrmSessionEventListener> eventDispatcher;
  private final int initialDrmRequestRetryCount;

  /* package */ final MediaDrmCallback callback;
  /* package */ final UUID uuid;
  /* package */ final PostResponseHandler postResponseHandler;

  private @DrmSession.State int state;
  private int openCount;
  private HandlerThread requestHandlerThread;
  private PostRequestHandler postRequestHandler;
  private @Nullable T mediaCrypto;
  private @Nullable DrmSessionException lastException;
  private byte @MonotonicNonNull [] sessionId;
  private byte @MonotonicNonNull [] offlineLicenseKeySetId;

  private @Nullable KeyRequest currentKeyRequest;
  private @Nullable ProvisionRequest currentProvisionRequest;

  /**
   * Instantiates a new DRM session.
   *
   * @param uuid The UUID of the drm scheme.
   * @param mediaDrm The media DRM.
   * @param provisioningManager The manager for provisioning.
   * @param schemeDatas DRM scheme datas for this session, or null if an {@code
   *     offlineLicenseKeySetId} is provided.
   * @param mode The DRM mode.
   * @param offlineLicenseKeySetId The offline license key set identifier, or null when not using
   *     offline keys.
   * @param optionalKeyRequestParameters The optional key request parameters.
   * @param callback The media DRM callback.
   * @param playbackLooper The playback looper.
   * @param eventDispatcher The dispatcher for DRM session manager events.
   * @param initialDrmRequestRetryCount The number of times to retry for initial provisioning and
   *     key request before reporting error.
   */
  public DefaultDrmSession(
      UUID uuid,
      ExoMediaDrm<T> mediaDrm,
      ProvisioningManager<T> provisioningManager,
      @Nullable List<SchemeData> schemeDatas,
      @DefaultDrmSessionManager.Mode int mode,
      @Nullable byte[] offlineLicenseKeySetId,
      @Nullable HashMap<String, String> optionalKeyRequestParameters,
      MediaDrmCallback callback,
      Looper playbackLooper,
      EventDispatcher<DefaultDrmSessionEventListener> eventDispatcher,
      int initialDrmRequestRetryCount) {
    if (mode == DefaultDrmSessionManager.MODE_QUERY
        || mode == DefaultDrmSessionManager.MODE_RELEASE) {
      Assertions.checkNotNull(offlineLicenseKeySetId);
    }
    this.uuid = uuid;
    this.provisioningManager = provisioningManager;
    this.mediaDrm = mediaDrm;
    this.mode = mode;
    if (offlineLicenseKeySetId != null) {
      this.offlineLicenseKeySetId = offlineLicenseKeySetId;
      this.schemeDatas = null;
    } else {
      this.schemeDatas = Collections.unmodifiableList(Assertions.checkNotNull(schemeDatas));
    }
    this.optionalKeyRequestParameters = optionalKeyRequestParameters;
    this.callback = callback;
    this.initialDrmRequestRetryCount = initialDrmRequestRetryCount;
    this.eventDispatcher = eventDispatcher;
    state = STATE_OPENING;

    postResponseHandler = new PostResponseHandler(playbackLooper);
    requestHandlerThread = new HandlerThread("DrmRequestHandler");
    requestHandlerThread.start();
    postRequestHandler = new PostRequestHandler(requestHandlerThread.getLooper());
  }

  // Life cycle.

  public void acquire() {
    if (++openCount == 1) {
      if (state == STATE_ERROR) {
        return;
      }
      if (openInternal(true)) {
        doLicense(true);
      }
    }
  }

  /** @return True if the session is closed and cleaned up, false otherwise. */
  // Assigning null to various non-null variables for clean-up. Class won't be used after release.
  @SuppressWarnings("assignment.type.incompatible")
  public boolean release() {
    if (--openCount == 0) {
      state = STATE_RELEASED;
      postResponseHandler.removeCallbacksAndMessages(null);
      postRequestHandler.removeCallbacksAndMessages(null);
      postRequestHandler = null;
      requestHandlerThread.quit();
      requestHandlerThread = null;
      mediaCrypto = null;
      lastException = null;
      currentKeyRequest = null;
      currentProvisionRequest = null;
      if (sessionId != null) {
        mediaDrm.closeSession(sessionId);
        sessionId = null;
        eventDispatcher.dispatch(DefaultDrmSessionEventListener::onDrmSessionReleased);
      }
      return true;
    }
    return false;
  }

  public boolean hasSessionId(byte[] sessionId) {
    return Arrays.equals(this.sessionId, sessionId);
  }

  public void onMediaDrmEvent(int what) {
    switch (what) {
      case ExoMediaDrm.EVENT_KEY_REQUIRED:
        onKeysRequired();
        break;
      default:
        break;
    }
  }

  // Provisioning implementation.

  public void provision() {
    currentProvisionRequest = mediaDrm.getProvisionRequest();
    postRequestHandler.post(MSG_PROVISION, currentProvisionRequest, /* allowRetry= */ true);
  }

  public void onProvisionCompleted() {
    if (openInternal(false)) {
      doLicense(true);
    }
  }

  public void onProvisionError(Exception error) {
    onError(error);
  }

  // DrmSession implementation.

  @Override
  @DrmSession.State
  public final int getState() {
    return state;
  }

  @Override
  public final @Nullable DrmSessionException getError() {
    return state == STATE_ERROR ? lastException : null;
  }

  @Override
  public final @Nullable T getMediaCrypto() {
    return mediaCrypto;
  }

  @Override
  public @Nullable Map<String, String> queryKeyStatus() {
    return sessionId == null ? null : mediaDrm.queryKeyStatus(sessionId);
  }

  @Override
  public @Nullable byte[] getOfflineLicenseKeySetId() {
    return offlineLicenseKeySetId;
  }

  // Internal methods.

  /**
   * Try to open a session, do provisioning if necessary.
   *
   * @param allowProvisioning if provisioning is allowed, set this to false when calling from
   *     processing provision response.
   * @return true on success, false otherwise.
   */
  @EnsuresNonNullIf(result = true, expression = "sessionId")
  private boolean openInternal(boolean allowProvisioning) {
    if (isOpen()) {
      // Already opened
      return true;
    }

    try {
      sessionId = mediaDrm.openSession();
      eventDispatcher.dispatch(DefaultDrmSessionEventListener::onDrmSessionAcquired);
      mediaCrypto = mediaDrm.createMediaCrypto(sessionId);
      state = STATE_OPENED;
      return true;
    } catch (NotProvisionedException e) {
      if (allowProvisioning) {
        provisioningManager.provisionRequired(this);
      } else {
        onError(e);
      }
    } catch (Exception e) {
      onError(e);
    }

    return false;
  }

  private void onProvisionResponse(Object request, Object response) {
    if (request != currentProvisionRequest || (state != STATE_OPENING && !isOpen())) {
      // This event is stale.
      return;
    }
    currentProvisionRequest = null;

    if (response instanceof Exception) {
      provisioningManager.onProvisionError((Exception) response);
      return;
    }

    try {
      mediaDrm.provideProvisionResponse((byte[]) response);
    } catch (Exception e) {
      provisioningManager.onProvisionError(e);
      return;
    }

    provisioningManager.onProvisionCompleted();
  }

  @RequiresNonNull("sessionId")
  private void doLicense(boolean allowRetry) {
    switch (mode) {
      case DefaultDrmSessionManager.MODE_PLAYBACK:
      case DefaultDrmSessionManager.MODE_QUERY:
        if (offlineLicenseKeySetId == null) {
          postKeyRequest(sessionId, ExoMediaDrm.KEY_TYPE_STREAMING, allowRetry);
        } else if (state == STATE_OPENED_WITH_KEYS || restoreKeys()) {
          long licenseDurationRemainingSec = getLicenseDurationRemainingSec();
          if (mode == DefaultDrmSessionManager.MODE_PLAYBACK
              && licenseDurationRemainingSec <= MAX_LICENSE_DURATION_TO_RENEW) {
            Log.d(TAG, "Offline license has expired or will expire soon. "
                + "Remaining seconds: " + licenseDurationRemainingSec);
            postKeyRequest(sessionId, ExoMediaDrm.KEY_TYPE_OFFLINE, allowRetry);
          } else if (licenseDurationRemainingSec <= 0) {
            onError(new KeysExpiredException());
          } else {
            state = STATE_OPENED_WITH_KEYS;
            eventDispatcher.dispatch(DefaultDrmSessionEventListener::onDrmKeysRestored);
          }
        }
        break;
      case DefaultDrmSessionManager.MODE_DOWNLOAD:
        if (offlineLicenseKeySetId == null) {
          postKeyRequest(sessionId, ExoMediaDrm.KEY_TYPE_OFFLINE, allowRetry);
        } else {
          // Renew
          if (restoreKeys()) {
            postKeyRequest(sessionId, ExoMediaDrm.KEY_TYPE_OFFLINE, allowRetry);
          }
        }
        break;
      case DefaultDrmSessionManager.MODE_RELEASE:
        Assertions.checkNotNull(offlineLicenseKeySetId);
        // It's not necessary to restore the key (and open a session to do that) before releasing it
        // but this serves as a good sanity/fast-failure check.
        if (restoreKeys()) {
          postKeyRequest(offlineLicenseKeySetId, ExoMediaDrm.KEY_TYPE_RELEASE, allowRetry);
        }
        break;
      default:
        break;
    }
  }

  @RequiresNonNull({"sessionId", "offlineLicenseKeySetId"})
  private boolean restoreKeys() {
    try {
      mediaDrm.restoreKeys(sessionId, offlineLicenseKeySetId);
      return true;
    } catch (Exception e) {
      Log.e(TAG, "Error trying to restore Widevine keys.", e);
      onError(e);
    }
    return false;
  }

  private long getLicenseDurationRemainingSec() {
    if (!C.WIDEVINE_UUID.equals(uuid)) {
      return Long.MAX_VALUE;
    }
    Pair<Long, Long> pair =
        Assertions.checkNotNull(WidevineUtil.getLicenseDurationRemainingSec(this));
    return Math.min(pair.first, pair.second);
  }

  private void postKeyRequest(byte[] scope, int type, boolean allowRetry) {
    try {
      currentKeyRequest =
          mediaDrm.getKeyRequest(scope, schemeDatas, type, optionalKeyRequestParameters);
      postRequestHandler.post(MSG_KEYS, currentKeyRequest, allowRetry);
    } catch (Exception e) {
      onKeysError(e);
    }
  }

  private void onKeyResponse(Object request, Object response) {
    if (request != currentKeyRequest || !isOpen()) {
      // This event is stale.
      return;
    }
    currentKeyRequest = null;

    if (response instanceof Exception) {
      onKeysError((Exception) response);
      return;
    }

    try {
      byte[] responseData = (byte[]) response;
      if (mode == DefaultDrmSessionManager.MODE_RELEASE) {
        mediaDrm.provideKeyResponse(Util.castNonNull(offlineLicenseKeySetId), responseData);
        eventDispatcher.dispatch(DefaultDrmSessionEventListener::onDrmKeysRestored);
      } else {
        byte[] keySetId = mediaDrm.provideKeyResponse(sessionId, responseData);
        if ((mode == DefaultDrmSessionManager.MODE_DOWNLOAD
            || (mode == DefaultDrmSessionManager.MODE_PLAYBACK && offlineLicenseKeySetId != null))
            && keySetId != null && keySetId.length != 0) {
          offlineLicenseKeySetId = keySetId;
        }
        state = STATE_OPENED_WITH_KEYS;
        eventDispatcher.dispatch(DefaultDrmSessionEventListener::onDrmKeysLoaded);
      }
    } catch (Exception e) {
      onKeysError(e);
    }
  }

  private void onKeysRequired() {
    if (mode == DefaultDrmSessionManager.MODE_PLAYBACK && state == STATE_OPENED_WITH_KEYS) {
      Util.castNonNull(sessionId);
      doLicense(/* allowRetry= */ false);
    }
  }

  private void onKeysError(Exception e) {
    if (e instanceof NotProvisionedException) {
      provisioningManager.provisionRequired(this);
    } else {
      onError(e);
    }
  }

  private void onError(final Exception e) {
    lastException = new DrmSessionException(e);
    eventDispatcher.dispatch(listener -> listener.onDrmSessionManagerError(e));
    if (state != STATE_OPENED_WITH_KEYS) {
      state = STATE_ERROR;
    }
  }

  @EnsuresNonNullIf(result = true, expression = "sessionId")
  @SuppressWarnings("contracts.conditional.postcondition.not.satisfied")
  private boolean isOpen() {
    return state == STATE_OPENED || state == STATE_OPENED_WITH_KEYS;
  }

  // Internal classes.

  @SuppressLint("HandlerLeak")
  private class PostResponseHandler extends Handler {

    public PostResponseHandler(Looper looper) {
      super(looper);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void handleMessage(Message msg) {
      Pair<Object, Object> requestAndResponse = (Pair<Object, Object>) msg.obj;
      Object request = requestAndResponse.first;
      Object response = requestAndResponse.second;
      switch (msg.what) {
        case MSG_PROVISION:
          onProvisionResponse(request, response);
          break;
        case MSG_KEYS:
          onKeyResponse(request, response);
          break;
        default:
          break;

      }
    }

  }

  @SuppressLint("HandlerLeak")
  private class PostRequestHandler extends Handler {

    public PostRequestHandler(Looper backgroundLooper) {
      super(backgroundLooper);
    }

    void post(int what, Object request, boolean allowRetry) {
      int allowRetryInt = allowRetry ? 1 : 0;
      int errorCount = 0;
      obtainMessage(what, allowRetryInt, errorCount, request).sendToTarget();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void handleMessage(Message msg) {
      Object request = msg.obj;
      Object response;
      try {
        switch (msg.what) {
          case MSG_PROVISION:
            response = callback.executeProvisionRequest(uuid, (ProvisionRequest) request);
            break;
          case MSG_KEYS:
            response = callback.executeKeyRequest(uuid, (KeyRequest) request);
            break;
          default:
            throw new RuntimeException();
        }
      } catch (Exception e) {
        if (maybeRetryRequest(msg)) {
          return;
        }
        response = e;
      }
      postResponseHandler.obtainMessage(msg.what, Pair.create(request, response)).sendToTarget();
    }

    private boolean maybeRetryRequest(Message originalMsg) {
      boolean allowRetry = originalMsg.arg1 == 1;
      if (!allowRetry) {
        return false;
      }
      int errorCount = originalMsg.arg2 + 1;
      if (errorCount > initialDrmRequestRetryCount) {
        return false;
      }
      Message retryMsg = Message.obtain(originalMsg);
      retryMsg.arg2 = errorCount;
      sendMessageDelayed(retryMsg, getRetryDelayMillis(errorCount));
      return true;
    }

    private long getRetryDelayMillis(int errorCount) {
      return Math.min((errorCount - 1) * 1000, 5000);
    }

  }
}

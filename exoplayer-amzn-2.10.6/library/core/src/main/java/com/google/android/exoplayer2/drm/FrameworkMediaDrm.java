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
import android.media.DeniedByServerException;
import android.media.MediaCryptoException;
import android.media.MediaDrm;
import android.media.MediaDrmException;
import android.media.NotProvisionedException;
import android.media.UnsupportedSchemeException;
import androidx.annotation.Nullable;
import android.text.TextUtils;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.drm.DrmInitData.SchemeData;
import com.google.android.exoplayer2.extractor.mp4.PsshAtomUtil;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * An {@link ExoMediaDrm} implementation that wraps the framework {@link MediaDrm}.
 */
@TargetApi(23)
public final class FrameworkMediaDrm implements ExoMediaDrm<FrameworkMediaCrypto> {

  private static final String CENC_SCHEME_MIME_TYPE = "cenc";
  private static final String MOCK_LA_URL_VALUE = "https://x";
  private static final String MOCK_LA_URL = "<LA_URL>" + MOCK_LA_URL_VALUE + "</LA_URL>";
  private static final int UTF_16_BYTES_PER_CHARACTER = 2;
  private static final String TAG = "FrameworkMediaDrm";

  private final UUID uuid;
  private final MediaDrm mediaDrm;

  /**
   * Creates an instance for the specified scheme UUID.
   *
   * @param uuid The scheme uuid.
   * @return The created instance.
   * @throws UnsupportedDrmException If the DRM scheme is unsupported or cannot be instantiated.
   */
  public static FrameworkMediaDrm newInstance(UUID uuid) throws UnsupportedDrmException {
    try {
      return new FrameworkMediaDrm(uuid);
    } catch (UnsupportedSchemeException e) {
      throw new UnsupportedDrmException(UnsupportedDrmException.REASON_UNSUPPORTED_SCHEME, e);
    } catch (Exception e) {
      throw new UnsupportedDrmException(UnsupportedDrmException.REASON_INSTANTIATION_ERROR, e);
    }
  }

  private FrameworkMediaDrm(UUID uuid) throws UnsupportedSchemeException {
    Assertions.checkNotNull(uuid);
    Assertions.checkArgument(!C.COMMON_PSSH_UUID.equals(uuid), "Use C.CLEARKEY_UUID instead");
    this.uuid = uuid;
    this.mediaDrm = new MediaDrm(adjustUuid(uuid));
    if (C.WIDEVINE_UUID.equals(uuid) && needsForceWidevineL3Workaround()) {
      forceWidevineL3(mediaDrm);
    }
  }

  @Override
  public void setOnEventListener(
      final ExoMediaDrm.OnEventListener<? super FrameworkMediaCrypto> listener) {
    mediaDrm.setOnEventListener(
        listener == null
            ? null
            : (mediaDrm, sessionId, event, extra, data) ->
                listener.onEvent(FrameworkMediaDrm.this, sessionId, event, extra, data));
  }

  @Override
  public void setOnKeyStatusChangeListener(
      final ExoMediaDrm.OnKeyStatusChangeListener<? super FrameworkMediaCrypto> listener) {
    if (Util.SDK_INT < 23) {
      throw new UnsupportedOperationException();
    }

    mediaDrm.setOnKeyStatusChangeListener(
        listener == null
            ? null
            : (mediaDrm, sessionId, keyInfo, hasNewUsableKey) -> {
              List<KeyStatus> exoKeyInfo = new ArrayList<>();
              for (MediaDrm.KeyStatus keyStatus : keyInfo) {
                exoKeyInfo.add(new KeyStatus(keyStatus.getStatusCode(), keyStatus.getKeyId()));
              }
              listener.onKeyStatusChange(
                  FrameworkMediaDrm.this, sessionId, exoKeyInfo, hasNewUsableKey);
            },
        null);
  }

  @Override
  public byte[] openSession() throws MediaDrmException {
    return mediaDrm.openSession();
  }

  @Override
  public void closeSession(byte[] sessionId) {
    mediaDrm.closeSession(sessionId);
  }

  @Override
  public KeyRequest getKeyRequest(
      byte[] scope,
      @Nullable List<DrmInitData.SchemeData> schemeDatas,
      int keyType,
      @Nullable HashMap<String, String> optionalParameters)
      throws NotProvisionedException {
    SchemeData schemeData = null;
    byte[] initData = null;
    String mimeType = null;
    if (schemeDatas != null) {
      schemeData = getSchemeData(uuid, schemeDatas);
      initData = adjustRequestInitData(uuid, Assertions.checkNotNull(schemeData.data));
      mimeType = adjustRequestMimeType(uuid, schemeData.mimeType);
    }
    MediaDrm.KeyRequest request =
        mediaDrm.getKeyRequest(scope, initData, mimeType, keyType, optionalParameters);

    byte[] requestData = adjustRequestData(uuid, request.getData());

    String licenseServerUrl = request.getDefaultUrl();
    if (MOCK_LA_URL_VALUE.equals(licenseServerUrl)) {
      licenseServerUrl = "";
    }
    if (TextUtils.isEmpty(licenseServerUrl)
        && schemeData != null
        && !TextUtils.isEmpty(schemeData.licenseServerUrl)) {
      licenseServerUrl = schemeData.licenseServerUrl;
    }

    return new KeyRequest(requestData, licenseServerUrl);
  }

  @Nullable
  @Override
  public byte[] provideKeyResponse(byte[] scope, byte[] response)
      throws NotProvisionedException, DeniedByServerException {
    if (C.CLEARKEY_UUID.equals(uuid)) {
      response = ClearKeyUtil.adjustResponseData(response);
    }

    return mediaDrm.provideKeyResponse(scope, response);
  }

  @Override
  public ProvisionRequest getProvisionRequest() {
    final MediaDrm.ProvisionRequest request = mediaDrm.getProvisionRequest();
    return new ProvisionRequest(request.getData(), request.getDefaultUrl());
  }

  @Override
  public void provideProvisionResponse(byte[] response) throws DeniedByServerException {
    mediaDrm.provideProvisionResponse(response);
  }

  @Override
  public Map<String, String> queryKeyStatus(byte[] sessionId) {
    return mediaDrm.queryKeyStatus(sessionId);
  }

  @Override
  public void release() {
    mediaDrm.release();
  }

  @Override
  public void restoreKeys(byte[] sessionId, byte[] keySetId) {
    mediaDrm.restoreKeys(sessionId, keySetId);
  }

  @Override
  public String getPropertyString(String propertyName) {
    return mediaDrm.getPropertyString(propertyName);
  }

  @Override
  public byte[] getPropertyByteArray(String propertyName) {
    return mediaDrm.getPropertyByteArray(propertyName);
  }

  @Override
  public void setPropertyString(String propertyName, String value) {
    mediaDrm.setPropertyString(propertyName, value);
  }

  @Override
  public void setPropertyByteArray(String propertyName, byte[] value) {
    mediaDrm.setPropertyByteArray(propertyName, value);
  }

  @Override
  public FrameworkMediaCrypto createMediaCrypto(byte[] initData) throws MediaCryptoException {
    // Work around a bug prior to Lollipop where L1 Widevine forced into L3 mode would still
    // indicate that it required secure video decoders [Internal ref: b/11428937].
    boolean forceAllowInsecureDecoderComponents = Util.SDK_INT < 21
        && C.WIDEVINE_UUID.equals(uuid) && "L3".equals(getPropertyString("securityLevel"));
    return new FrameworkMediaCrypto(
        adjustUuid(uuid), initData, forceAllowInsecureDecoderComponents);
  }

  private static SchemeData getSchemeData(UUID uuid, List<SchemeData> schemeDatas) {
    if (!C.WIDEVINE_UUID.equals(uuid)) {
      // For non-Widevine CDMs always use the first scheme data.
      return schemeDatas.get(0);
    }

    if (Util.SDK_INT >= 28 && schemeDatas.size() > 1) {
      // For API level 28 and above, concatenate multiple PSSH scheme datas if possible.
      SchemeData firstSchemeData = schemeDatas.get(0);
      int concatenatedDataLength = 0;
      boolean canConcatenateData = true;
      for (int i = 0; i < schemeDatas.size(); i++) {
        SchemeData schemeData = schemeDatas.get(i);
        byte[] schemeDataData = Util.castNonNull(schemeData.data);
        if (schemeData.requiresSecureDecryption == firstSchemeData.requiresSecureDecryption
            && Util.areEqual(schemeData.mimeType, firstSchemeData.mimeType)
            && Util.areEqual(schemeData.licenseServerUrl, firstSchemeData.licenseServerUrl)
            && PsshAtomUtil.isPsshAtom(schemeDataData)) {
          concatenatedDataLength += schemeDataData.length;
        } else {
          canConcatenateData = false;
          break;
        }
      }
      if (canConcatenateData) {
        byte[] concatenatedData = new byte[concatenatedDataLength];
        int concatenatedDataPosition = 0;
        for (int i = 0; i < schemeDatas.size(); i++) {
          SchemeData schemeData = schemeDatas.get(i);
          byte[] schemeDataData = Util.castNonNull(schemeData.data);
          int schemeDataLength = schemeDataData.length;
          System.arraycopy(
              schemeDataData, 0, concatenatedData, concatenatedDataPosition, schemeDataLength);
          concatenatedDataPosition += schemeDataLength;
        }
        return firstSchemeData.copyWithData(concatenatedData);
      }
    }

    // For API levels 23 - 27, prefer the first V1 PSSH box. For API levels 22 and earlier, prefer
    // the first V0 box.
    for (int i = 0; i < schemeDatas.size(); i++) {
      SchemeData schemeData = schemeDatas.get(i);
      int version = PsshAtomUtil.parseVersion(Util.castNonNull(schemeData.data));
      if (Util.SDK_INT < 23 && version == 0) {
        return schemeData;
      } else if (Util.SDK_INT >= 23 && version == 1) {
        return schemeData;
      }
    }

    // If all else fails, use the first scheme data.
    return schemeDatas.get(0);
  }

  private static UUID adjustUuid(UUID uuid) {
    // ClearKey had to be accessed using the Common PSSH UUID prior to API level 27.
    return Util.SDK_INT < 27 && C.CLEARKEY_UUID.equals(uuid) ? C.COMMON_PSSH_UUID : uuid;
  }

  private static byte[] adjustRequestInitData(UUID uuid, byte[] initData) {
    // TODO: Add API level check once [Internal ref: b/112142048] is fixed.
    if (C.PLAYREADY_UUID.equals(uuid)) {
      byte[] schemeSpecificData = PsshAtomUtil.parseSchemeSpecificData(initData, uuid);
      if (schemeSpecificData == null) {
        // The init data is not contained in a pssh box.
        schemeSpecificData = initData;
      }
      initData =
          PsshAtomUtil.buildPsshAtom(
              C.PLAYREADY_UUID, addLaUrlAttributeIfMissing(schemeSpecificData));
    }

    // Prior to L the Widevine CDM required data to be extracted from the PSSH atom. Some Amazon
    // devices also required data to be extracted from the PSSH atom for PlayReady.
    if ((Util.SDK_INT < 21 && C.WIDEVINE_UUID.equals(uuid))
        || (C.PLAYREADY_UUID.equals(uuid)
            && "Amazon".equals(Util.MANUFACTURER)
            && ("AFTB".equals(Util.MODEL) // Fire TV Gen 1
                || "AFTS".equals(Util.MODEL) // Fire TV Gen 2
                || "AFTM".equals(Util.MODEL)))) { // Fire TV Stick Gen 1
      byte[] psshData = PsshAtomUtil.parseSchemeSpecificData(initData, uuid);
      if (psshData != null) {
        // Extraction succeeded, so return the extracted data.
        return psshData;
      }
    }
    return initData;
  }

  private static String adjustRequestMimeType(UUID uuid, String mimeType) {
    // Prior to API level 26 the ClearKey CDM only accepted "cenc" as the scheme for MP4.
    if (Util.SDK_INT < 26
        && C.CLEARKEY_UUID.equals(uuid)
        && (MimeTypes.VIDEO_MP4.equals(mimeType) || MimeTypes.AUDIO_MP4.equals(mimeType))) {
      return CENC_SCHEME_MIME_TYPE;
    }
    return mimeType;
  }

  private static byte[] adjustRequestData(UUID uuid, byte[] requestData) {
    if (C.CLEARKEY_UUID.equals(uuid)) {
      return ClearKeyUtil.adjustRequestData(requestData);
    }
    return requestData;
  }

  @SuppressLint("WrongConstant") // Suppress spurious lint error [Internal ref: b/32137960]
  private static void forceWidevineL3(MediaDrm mediaDrm) {
    mediaDrm.setPropertyString("securityLevel", "L3");
  }

  /**
   * Returns whether the device codec is known to fail if security level L1 is used.
   *
   * <p>See <a href="https://github.com/google/ExoPlayer/issues/4413">GitHub issue #4413</a>.
   */
  private static boolean needsForceWidevineL3Workaround() {
    return "ASUS_Z00AD".equals(Util.MODEL);
  }

  /**
   * If the LA_URL tag is missing, injects a mock LA_URL value to avoid causing the CDM to throw
   * when creating the key request. The LA_URL attribute is optional but some Android PlayReady
   * implementations are known to require it. Does nothing it the provided {@code data} already
   * contains an LA_URL value.
   */
  private static byte[] addLaUrlAttributeIfMissing(byte[] data) {
    ParsableByteArray byteArray = new ParsableByteArray(data);
    // See https://docs.microsoft.com/en-us/playready/specifications/specifications for more
    // information about the init data format.
    int length = byteArray.readLittleEndianInt();
    int objectRecordCount = byteArray.readLittleEndianShort();
    int recordType = byteArray.readLittleEndianShort();
    if (objectRecordCount != 1 || recordType != 1) {
      Log.i(TAG, "Unexpected record count or type. Skipping LA_URL workaround.");
      return data;
    }
    int recordLength = byteArray.readLittleEndianShort();
    String xml = byteArray.readString(recordLength, Charset.forName(C.UTF16LE_NAME));
    if (xml.contains("<LA_URL>")) {
      // LA_URL already present. Do nothing.
      return data;
    }
    // This PlayReady object record does not include an LA_URL. We add a mock value for it.
    int endOfDataTagIndex = xml.indexOf("</DATA>");
    if (endOfDataTagIndex == -1) {
      Log.w(TAG, "Could not find the </DATA> tag. Skipping LA_URL workaround.");
    }
    String xmlWithMockLaUrl =
        xml.substring(/* beginIndex= */ 0, /* endIndex= */ endOfDataTagIndex)
            + MOCK_LA_URL
            + xml.substring(/* beginIndex= */ endOfDataTagIndex);
    int extraBytes = MOCK_LA_URL.length() * UTF_16_BYTES_PER_CHARACTER;
    ByteBuffer newData = ByteBuffer.allocate(length + extraBytes);
    newData.order(ByteOrder.LITTLE_ENDIAN);
    newData.putInt(length + extraBytes);
    newData.putShort((short) objectRecordCount);
    newData.putShort((short) recordType);
    newData.putShort((short) (xmlWithMockLaUrl.length() * UTF_16_BYTES_PER_CHARACTER));
    newData.put(xmlWithMockLaUrl.getBytes(Charset.forName(C.UTF16LE_NAME)));
    return newData.array();
  }
}

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

#include <jni.h>

#include <android/log.h>

#include <cstdlib>

#include "opus.h"  // NOLINT
#include "opus_multistream.h"  // NOLINT

#define LOG_TAG "opus_jni"
#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, \
                                             __VA_ARGS__))

#define DECODER_FUNC(RETURN_TYPE, NAME, ...) \
  extern "C" { \
  JNIEXPORT RETURN_TYPE \
    Java_com_google_android_exoplayer2_ext_opus_OpusDecoder_ ## NAME \
      (JNIEnv* env, jobject thiz, ##__VA_ARGS__);\
  } \
  JNIEXPORT RETURN_TYPE \
    Java_com_google_android_exoplayer2_ext_opus_OpusDecoder_ ## NAME \
      (JNIEnv* env, jobject thiz, ##__VA_ARGS__)\

#define LIBRARY_FUNC(RETURN_TYPE, NAME, ...) \
  extern "C" { \
  JNIEXPORT RETURN_TYPE \
    Java_com_google_android_exoplayer2_ext_opus_OpusLibrary_ ## NAME \
      (JNIEnv* env, jobject thiz, ##__VA_ARGS__);\
  } \
  JNIEXPORT RETURN_TYPE \
    Java_com_google_android_exoplayer2_ext_opus_OpusLibrary_ ## NAME \
      (JNIEnv* env, jobject thiz, ##__VA_ARGS__)\

// JNI references for SimpleOutputBuffer class.
static jmethodID outputBufferInit;

jint JNI_OnLoad(JavaVM* vm, void* reserved) {
  JNIEnv* env;
  if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
    return -1;
  }
  return JNI_VERSION_1_6;
}

static const int kBytesPerSample = 2;  // opus fixed point uses 16 bit samples.
static const int kMaxOpusOutputPacketSizeSamples = 960 * 6;
static int channelCount;
static int errorCode;

DECODER_FUNC(jlong, opusInit, jint sampleRate, jint channelCount,
     jint numStreams, jint numCoupled, jint gain, jbyteArray jStreamMap) {
  int status = OPUS_INVALID_STATE;
  ::channelCount = channelCount;
  errorCode = 0;
  jbyte* streamMapBytes = env->GetByteArrayElements(jStreamMap, 0);
  uint8_t* streamMap = reinterpret_cast<uint8_t*>(streamMapBytes);
  OpusMSDecoder* decoder = opus_multistream_decoder_create(
      sampleRate, channelCount, numStreams, numCoupled, streamMap, &status);
  env->ReleaseByteArrayElements(jStreamMap, streamMapBytes, 0);
  if (!decoder || status != OPUS_OK) {
    LOGE("Failed to create Opus Decoder; status=%s", opus_strerror(status));
    return 0;
  }
  status = opus_multistream_decoder_ctl(decoder, OPUS_SET_GAIN(gain));
  if (status != OPUS_OK) {
    LOGE("Failed to set Opus header gain; status=%s", opus_strerror(status));
    return 0;
  }

  // Populate JNI References.
  const jclass outputBufferClass = env->FindClass(
      "com/google/android/exoplayer2/decoder/SimpleOutputBuffer");
  outputBufferInit = env->GetMethodID(outputBufferClass, "init",
      "(JI)Ljava/nio/ByteBuffer;");

  return reinterpret_cast<intptr_t>(decoder);
}

DECODER_FUNC(jint, opusDecode, jlong jDecoder, jlong jTimeUs,
     jobject jInputBuffer, jint inputSize, jobject jOutputBuffer) {
  OpusMSDecoder* decoder = reinterpret_cast<OpusMSDecoder*>(jDecoder);
  const uint8_t* inputBuffer =
      reinterpret_cast<const uint8_t*>(
          env->GetDirectBufferAddress(jInputBuffer));

  const jint outputSize =
      kMaxOpusOutputPacketSizeSamples * kBytesPerSample * channelCount;

  env->CallObjectMethod(jOutputBuffer, outputBufferInit, jTimeUs, outputSize);
  if (env->ExceptionCheck()) {
    // Exception is thrown in Java when returning from the native call.
    return -1;
  }
  const jobject jOutputBufferData = env->CallObjectMethod(jOutputBuffer,
      outputBufferInit, jTimeUs, outputSize);
  if (env->ExceptionCheck()) {
    // Exception is thrown in Java when returning from the native call.
    return -1;
  }

  int16_t* outputBufferData = reinterpret_cast<int16_t*>(
      env->GetDirectBufferAddress(jOutputBufferData));
  int sampleCount = opus_multistream_decode(decoder, inputBuffer, inputSize,
      outputBufferData, kMaxOpusOutputPacketSizeSamples, 0);
  // record error code
  errorCode = (sampleCount < 0) ? sampleCount : 0;
  return (sampleCount < 0) ? sampleCount
      : sampleCount * kBytesPerSample * channelCount;
}

DECODER_FUNC(jint, opusSecureDecode, jlong jDecoder, jlong jTimeUs,
     jobject jInputBuffer, jint inputSize, jobject jOutputBuffer,
     jint sampleRate, jobject mediaCrypto, jint inputMode, jbyteArray key,
     jbyteArray javaIv, jint inputNumSubSamples, jintArray numBytesOfClearData,
     jintArray numBytesOfEncryptedData) {
  // Doesn't support
  // Java client should have checked vpxSupportSecureDecode
  // and avoid calling this
  // return -2 (DRM Error)
  return -2;
}

DECODER_FUNC(void, opusClose, jlong jDecoder) {
  OpusMSDecoder* decoder = reinterpret_cast<OpusMSDecoder*>(jDecoder);
  opus_multistream_decoder_destroy(decoder);
}

DECODER_FUNC(void, opusReset, jlong jDecoder) {
  OpusMSDecoder* decoder = reinterpret_cast<OpusMSDecoder*>(jDecoder);
  opus_multistream_decoder_ctl(decoder, OPUS_RESET_STATE);
}

DECODER_FUNC(jstring, opusGetErrorMessage, jlong jContext) {
  return env->NewStringUTF(opus_strerror(errorCode));
}

DECODER_FUNC(jint, opusGetErrorCode, jlong jContext) {
  return errorCode;
}

LIBRARY_FUNC(jstring, opusIsSecureDecodeSupported) {
  // Doesn't support
  return 0;
}

LIBRARY_FUNC(jstring, opusGetVersion) {
  return env->NewStringUTF(opus_get_version_string());
}

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
#include <stdlib.h>
#include <android/log.h>

extern "C" {
#ifdef __cplusplus
#define __STDC_CONSTANT_MACROS
#ifdef _STDINT_H
#undef _STDINT_H
#endif
#include <stdint.h>
#endif
#include <libavcodec/avcodec.h>
#include <libavresample/avresample.h>
#include <libavutil/channel_layout.h>
#include <libavutil/error.h>
#include <libavutil/opt.h>
}

#define LOG_TAG "ffmpeg_jni"
#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, \
                   __VA_ARGS__))

#define DECODER_FUNC(RETURN_TYPE, NAME, ...) \
  extern "C" { \
  JNIEXPORT RETURN_TYPE \
    Java_com_google_android_exoplayer2_ext_ffmpeg_FfmpegDecoder_ ## NAME \
      (JNIEnv* env, jobject thiz, ##__VA_ARGS__);\
  } \
  JNIEXPORT RETURN_TYPE \
    Java_com_google_android_exoplayer2_ext_ffmpeg_FfmpegDecoder_ ## NAME \
      (JNIEnv* env, jobject thiz, ##__VA_ARGS__)\

#define LIBRARY_FUNC(RETURN_TYPE, NAME, ...) \
  extern "C" { \
  JNIEXPORT RETURN_TYPE \
    Java_com_google_android_exoplayer2_ext_ffmpeg_FfmpegLibrary_ ## NAME \
      (JNIEnv* env, jobject thiz, ##__VA_ARGS__);\
  } \
  JNIEXPORT RETURN_TYPE \
    Java_com_google_android_exoplayer2_ext_ffmpeg_FfmpegLibrary_ ## NAME \
      (JNIEnv* env, jobject thiz, ##__VA_ARGS__)\

#define ERROR_STRING_BUFFER_LENGTH 256

// Output format corresponding to AudioFormat.ENCODING_PCM_16BIT.
static const AVSampleFormat OUTPUT_FORMAT_PCM_16BIT = AV_SAMPLE_FMT_S16;
// Output format corresponding to AudioFormat.ENCODING_PCM_FLOAT.
static const AVSampleFormat OUTPUT_FORMAT_PCM_FLOAT = AV_SAMPLE_FMT_FLT;

// Error codes matching FfmpegDecoder.java.
static const int DECODER_ERROR_INVALID_DATA = -1;
static const int DECODER_ERROR_OTHER = -2;

/**
 * Returns the AVCodec with the specified name, or NULL if it is not available.
 */
AVCodec *getCodecByName(JNIEnv* env, jstring codecName);

/**
 * Allocates and opens a new AVCodecContext for the specified codec, passing the
 * provided extraData as initialization data for the decoder if it is non-NULL.
 * Returns the created context.
 */
AVCodecContext *createContext(JNIEnv *env, AVCodec *codec, jbyteArray extraData,
                              jboolean outputFloat, jint rawSampleRate,
                              jint rawChannelCount);

/**
 * Decodes the packet into the output buffer, returning the number of bytes
 * written, or a negative DECODER_ERROR constant value in the case of an error.
 */
int decodePacket(AVCodecContext *context, AVPacket *packet,
                 uint8_t *outputBuffer, int outputSize);

/**
 * Outputs a log message describing the avcodec error number.
 */
void logError(const char *functionName, int errorNumber);

/**
 * Releases the specified context.
 */
void releaseContext(AVCodecContext *context);

jint JNI_OnLoad(JavaVM *vm, void *reserved) {
  JNIEnv *env;
  if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
    return -1;
  }
  avcodec_register_all();
  return JNI_VERSION_1_6;
}

LIBRARY_FUNC(jstring, ffmpegGetVersion) {
  return env->NewStringUTF(LIBAVCODEC_IDENT);
}

LIBRARY_FUNC(jboolean, ffmpegHasDecoder, jstring codecName) {
  return getCodecByName(env, codecName) != NULL;
}

DECODER_FUNC(jlong, ffmpegInitialize, jstring codecName, jbyteArray extraData,
             jboolean outputFloat, jint rawSampleRate, jint rawChannelCount) {
  AVCodec *codec = getCodecByName(env, codecName);
  if (!codec) {
    LOGE("Codec not found.");
    return 0L;
  }
  return (jlong)createContext(env, codec, extraData, outputFloat, rawSampleRate,
                              rawChannelCount);
}

DECODER_FUNC(jint, ffmpegDecode, jlong context, jobject inputData,
    jint inputSize, jobject outputData, jint outputSize) {
  if (!context) {
    LOGE("Context must be non-NULL.");
    return -1;
  }
  if (!inputData || !outputData) {
    LOGE("Input and output buffers must be non-NULL.");
    return -1;
  }
  if (inputSize < 0) {
    LOGE("Invalid input buffer size: %d.", inputSize);
    return -1;
  }
  if (outputSize < 0) {
    LOGE("Invalid output buffer length: %d", outputSize);
    return -1;
  }
  uint8_t *inputBuffer = (uint8_t *) env->GetDirectBufferAddress(inputData);
  uint8_t *outputBuffer = (uint8_t *) env->GetDirectBufferAddress(outputData);
  AVPacket packet;
  av_init_packet(&packet);
  packet.data = inputBuffer;
  packet.size = inputSize;
  return decodePacket((AVCodecContext *) context, &packet, outputBuffer,
                      outputSize);
}

DECODER_FUNC(jint, ffmpegGetChannelCount, jlong context) {
  if (!context) {
    LOGE("Context must be non-NULL.");
    return -1;
  }
  return ((AVCodecContext *) context)->channels;
}

DECODER_FUNC(jint, ffmpegGetSampleRate, jlong context) {
  if (!context) {
    LOGE("Context must be non-NULL.");
    return -1;
  }
  return ((AVCodecContext *) context)->sample_rate;
}

DECODER_FUNC(jlong, ffmpegReset, jlong jContext, jbyteArray extraData) {
  AVCodecContext *context = (AVCodecContext *) jContext;
  if (!context) {
    LOGE("Tried to reset without a context.");
    return 0L;
  }

  AVCodecID codecId = context->codec_id;
  if (codecId == AV_CODEC_ID_TRUEHD) {
    // Release and recreate the context if the codec is TrueHD.
    // TODO: Figure out why flushing doesn't work for this codec.
    releaseContext(context);
    AVCodec *codec = avcodec_find_decoder(codecId);
    if (!codec) {
      LOGE("Unexpected error finding codec %d.", codecId);
      return 0L;
    }
    jboolean outputFloat =
        (jboolean)(context->request_sample_fmt == OUTPUT_FORMAT_PCM_FLOAT);
    return (jlong)createContext(env, codec, extraData, outputFloat,
                                /* rawSampleRate= */ -1,
                                /* rawChannelCount= */ -1);
  }

  avcodec_flush_buffers(context);
  return (jlong) context;
}

DECODER_FUNC(void, ffmpegRelease, jlong context) {
  if (context) {
    releaseContext((AVCodecContext *) context);
  }
}

AVCodec *getCodecByName(JNIEnv* env, jstring codecName) {
  if (!codecName) {
    return NULL;
  }
  const char *codecNameChars = env->GetStringUTFChars(codecName, NULL);
  AVCodec *codec = avcodec_find_decoder_by_name(codecNameChars);
  env->ReleaseStringUTFChars(codecName, codecNameChars);
  return codec;
}

AVCodecContext *createContext(JNIEnv *env, AVCodec *codec, jbyteArray extraData,
                              jboolean outputFloat, jint rawSampleRate,
                              jint rawChannelCount) {
  AVCodecContext *context = avcodec_alloc_context3(codec);
  if (!context) {
    LOGE("Failed to allocate context.");
    return NULL;
  }
  context->request_sample_fmt =
      outputFloat ? OUTPUT_FORMAT_PCM_FLOAT : OUTPUT_FORMAT_PCM_16BIT;
  if (extraData) {
    jsize size = env->GetArrayLength(extraData);
    context->extradata_size = size;
    context->extradata =
        (uint8_t *) av_malloc(size + AV_INPUT_BUFFER_PADDING_SIZE);
    if (!context->extradata) {
      LOGE("Failed to allocate extradata.");
      releaseContext(context);
      return NULL;
    }
    env->GetByteArrayRegion(extraData, 0, size, (jbyte *) context->extradata);
  }
  if (context->codec_id == AV_CODEC_ID_PCM_MULAW ||
      context->codec_id == AV_CODEC_ID_PCM_ALAW) {
    context->sample_rate = rawSampleRate;
    context->channels = rawChannelCount;
    context->channel_layout = av_get_default_channel_layout(rawChannelCount);
  }
  context->err_recognition = AV_EF_IGNORE_ERR;
  int result = avcodec_open2(context, codec, NULL);
  if (result < 0) {
    logError("avcodec_open2", result);
    releaseContext(context);
    return NULL;
  }
  return context;
}

int decodePacket(AVCodecContext *context, AVPacket *packet,
                 uint8_t *outputBuffer, int outputSize) {
  int result = 0;
  // Queue input data.
  result = avcodec_send_packet(context, packet);
  if (result) {
    logError("avcodec_send_packet", result);
    return result == AVERROR_INVALIDDATA ? DECODER_ERROR_INVALID_DATA
                                         : DECODER_ERROR_OTHER;
  }

  // Dequeue output data until it runs out.
  int outSize = 0;
  while (true) {
    AVFrame *frame = av_frame_alloc();
    if (!frame) {
      LOGE("Failed to allocate output frame.");
      return -1;
    }
    result = avcodec_receive_frame(context, frame);
    if (result) {
      av_frame_free(&frame);
      if (result == AVERROR(EAGAIN)) {
        break;
      }
      logError("avcodec_receive_frame", result);
      return result;
    }

    // Resample output.
    AVSampleFormat sampleFormat = context->sample_fmt;
    int channelCount = context->channels;
    int channelLayout = context->channel_layout;
    int sampleRate = context->sample_rate;
    int sampleCount = frame->nb_samples;
    int dataSize = av_samples_get_buffer_size(NULL, channelCount, sampleCount,
                                              sampleFormat, 1);
    AVAudioResampleContext *resampleContext;
    if (context->opaque) {
      resampleContext = (AVAudioResampleContext *) context->opaque;
    } else {
      resampleContext = avresample_alloc_context();
      av_opt_set_int(resampleContext, "in_channel_layout",  channelLayout, 0);
      av_opt_set_int(resampleContext, "out_channel_layout", channelLayout, 0);
      av_opt_set_int(resampleContext, "in_sample_rate", sampleRate, 0);
      av_opt_set_int(resampleContext, "out_sample_rate", sampleRate, 0);
      av_opt_set_int(resampleContext, "in_sample_fmt", sampleFormat, 0);
      // The output format is always the requested format.
      av_opt_set_int(resampleContext, "out_sample_fmt",
          context->request_sample_fmt, 0);
      result = avresample_open(resampleContext);
      if (result < 0) {
        logError("avresample_open", result);
        av_frame_free(&frame);
        return -1;
      }
      context->opaque = resampleContext;
    }
    int inSampleSize = av_get_bytes_per_sample(sampleFormat);
    int outSampleSize = av_get_bytes_per_sample(context->request_sample_fmt);
    int outSamples = avresample_get_out_samples(resampleContext, sampleCount);
    int bufferOutSize = outSampleSize * channelCount * outSamples;
    if (outSize + bufferOutSize > outputSize) {
      LOGE("Output buffer size (%d) too small for output data (%d).",
           outputSize, outSize + bufferOutSize);
      av_frame_free(&frame);
      return -1;
    }
    result = avresample_convert(resampleContext, &outputBuffer, bufferOutSize,
                                outSamples, frame->data, frame->linesize[0],
                                sampleCount);
    av_frame_free(&frame);
    if (result < 0) {
      logError("avresample_convert", result);
      return result;
    }
    int available = avresample_available(resampleContext);
    if (available != 0) {
      LOGE("Expected no samples remaining after resampling, but found %d.",
           available);
      return -1;
    }
    outputBuffer += bufferOutSize;
    outSize += bufferOutSize;
  }
  return outSize;
}

void logError(const char *functionName, int errorNumber) {
  char *buffer = (char *) malloc(ERROR_STRING_BUFFER_LENGTH * sizeof(char));
  av_strerror(errorNumber, buffer, ERROR_STRING_BUFFER_LENGTH);
  LOGE("Error in %s: %s", functionName, buffer);
  free(buffer);
}

void releaseContext(AVCodecContext *context) {
  if (!context) {
    return;
  }
  AVAudioResampleContext *resampleContext;
  if ((resampleContext = (AVAudioResampleContext *) context->opaque)) {
    avresample_free(&resampleContext);
    context->opaque = NULL;
  }
  avcodec_free_context(&context);
}


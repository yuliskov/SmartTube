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

#include <android/log.h>
#include <jni.h>

#include <array>
#include <cstdlib>
#include <cstring>

#include "include/flac_parser.h"

#define LOG_TAG "flac_jni"
#define ALOGE(...) \
  ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__))
#define ALOGV(...) \
  ((void)__android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__))

#define DECODER_FUNC(RETURN_TYPE, NAME, ...)                               \
  extern "C" {                                                             \
  JNIEXPORT RETURN_TYPE                                                    \
      Java_com_google_android_exoplayer2_ext_flac_FlacDecoderJni_##NAME( \
          JNIEnv *env, jobject thiz, ##__VA_ARGS__);                       \
  }                                                                        \
  JNIEXPORT RETURN_TYPE                                                    \
      Java_com_google_android_exoplayer2_ext_flac_FlacDecoderJni_##NAME( \
          JNIEnv *env, jobject thiz, ##__VA_ARGS__)

class JavaDataSource : public DataSource {
 public:
  void setFlacDecoderJni(JNIEnv *env, jobject flacDecoderJni) {
    this->env = env;
    this->flacDecoderJni = flacDecoderJni;
    if (mid == NULL) {
      jclass cls = env->GetObjectClass(flacDecoderJni);
      mid = env->GetMethodID(cls, "read", "(Ljava/nio/ByteBuffer;)I");
    }
  }

  ssize_t readAt(off64_t offset, void *const data, size_t size) {
    jobject byteBuffer = env->NewDirectByteBuffer(data, size);
    int result = env->CallIntMethod(flacDecoderJni, mid, byteBuffer);
    if (env->ExceptionCheck()) {
      // Exception is thrown in Java when returning from the native call.
      result = -1;
    }
    return result;
  }

 private:
  JNIEnv *env;
  jobject flacDecoderJni;
  jmethodID mid;
};

struct Context {
  JavaDataSource *source;
  FLACParser *parser;

  Context() {
    source = new JavaDataSource();
    parser = new FLACParser(source);
  }

  ~Context() {
    delete parser;
    delete source;
  }
};

DECODER_FUNC(jlong, flacInit) {
  Context *context = new Context;
  if (!context->parser->init()) {
    delete context;
    return 0;
  }
  return reinterpret_cast<intptr_t>(context);
}

DECODER_FUNC(jobject, flacDecodeMetadata, jlong jContext) {
  Context *context = reinterpret_cast<Context *>(jContext);
  context->source->setFlacDecoderJni(env, thiz);
  if (!context->parser->decodeMetadata()) {
    return NULL;
  }

  jclass arrayListClass = env->FindClass("java/util/ArrayList");
  jmethodID arrayListConstructor =
      env->GetMethodID(arrayListClass, "<init>", "()V");
  jobject commentList = env->NewObject(arrayListClass, arrayListConstructor);
  jmethodID arrayListAddMethod =
      env->GetMethodID(arrayListClass, "add", "(Ljava/lang/Object;)Z");

  if (context->parser->areVorbisCommentsValid()) {
    std::vector<std::string> vorbisComments =
        context->parser->getVorbisComments();
    for (std::vector<std::string>::const_iterator vorbisComment =
             vorbisComments.begin();
         vorbisComment != vorbisComments.end(); ++vorbisComment) {
      jstring commentString = env->NewStringUTF((*vorbisComment).c_str());
      env->CallBooleanMethod(commentList, arrayListAddMethod, commentString);
      env->DeleteLocalRef(commentString);
    }
  }

  jobject pictureFrames = env->NewObject(arrayListClass, arrayListConstructor);
  bool picturesValid = context->parser->arePicturesValid();
  if (picturesValid) {
    std::vector<FlacPicture> pictures = context->parser->getPictures();
    jclass pictureFrameClass = env->FindClass(
        "com/google/android/exoplayer2/metadata/flac/PictureFrame");
    jmethodID pictureFrameConstructor =
        env->GetMethodID(pictureFrameClass, "<init>",
                         "(ILjava/lang/String;Ljava/lang/String;IIII[B)V");
    for (std::vector<FlacPicture>::const_iterator picture = pictures.begin();
         picture != pictures.end(); ++picture) {
      jstring mimeType = env->NewStringUTF(picture->mimeType.c_str());
      jstring description = env->NewStringUTF(picture->description.c_str());
      jbyteArray pictureData = env->NewByteArray(picture->data.size());
      env->SetByteArrayRegion(pictureData, 0, picture->data.size(),
                              (signed char *)&picture->data[0]);
      jobject pictureFrame = env->NewObject(
          pictureFrameClass, pictureFrameConstructor, picture->type, mimeType,
          description, picture->width, picture->height, picture->depth,
          picture->colors, pictureData);
      env->CallBooleanMethod(pictureFrames, arrayListAddMethod, pictureFrame);
      env->DeleteLocalRef(mimeType);
      env->DeleteLocalRef(description);
      env->DeleteLocalRef(pictureData);
    }
  }

  const FLAC__StreamMetadata_StreamInfo &streamInfo =
      context->parser->getStreamInfo();

  jclass flacStreamMetadataClass = env->FindClass(
      "com/google/android/exoplayer2/util/"
      "FlacStreamMetadata");
  jmethodID flacStreamMetadataConstructor =
      env->GetMethodID(flacStreamMetadataClass, "<init>",
                       "(IIIIIIIJLjava/util/List;Ljava/util/List;)V");

  return env->NewObject(flacStreamMetadataClass, flacStreamMetadataConstructor,
                        streamInfo.min_blocksize, streamInfo.max_blocksize,
                        streamInfo.min_framesize, streamInfo.max_framesize,
                        streamInfo.sample_rate, streamInfo.channels,
                        streamInfo.bits_per_sample, streamInfo.total_samples,
                        commentList, pictureFrames);
}

DECODER_FUNC(jint, flacDecodeToBuffer, jlong jContext, jobject jOutputBuffer) {
  Context *context = reinterpret_cast<Context *>(jContext);
  context->source->setFlacDecoderJni(env, thiz);
  void *outputBuffer = env->GetDirectBufferAddress(jOutputBuffer);
  jint outputSize = env->GetDirectBufferCapacity(jOutputBuffer);
  return context->parser->readBuffer(outputBuffer, outputSize);
}

DECODER_FUNC(jint, flacDecodeToArray, jlong jContext, jbyteArray jOutputArray) {
  Context *context = reinterpret_cast<Context *>(jContext);
  context->source->setFlacDecoderJni(env, thiz);
  jbyte *outputBuffer = env->GetByteArrayElements(jOutputArray, NULL);
  jint outputSize = env->GetArrayLength(jOutputArray);
  int count = context->parser->readBuffer(outputBuffer, outputSize);
  env->ReleaseByteArrayElements(jOutputArray, outputBuffer, 0);
  return count;
}

DECODER_FUNC(jlong, flacGetDecodePosition, jlong jContext) {
  Context *context = reinterpret_cast<Context *>(jContext);
  return context->parser->getDecodePosition();
}

DECODER_FUNC(jlong, flacGetLastFrameTimestamp, jlong jContext) {
  Context *context = reinterpret_cast<Context *>(jContext);
  return context->parser->getLastFrameTimestamp();
}

DECODER_FUNC(jlong, flacGetLastFrameFirstSampleIndex, jlong jContext) {
  Context *context = reinterpret_cast<Context *>(jContext);
  return context->parser->getLastFrameFirstSampleIndex();
}

DECODER_FUNC(jlong, flacGetNextFrameFirstSampleIndex, jlong jContext) {
  Context *context = reinterpret_cast<Context *>(jContext);
  return context->parser->getNextFrameFirstSampleIndex();
}

DECODER_FUNC(jboolean, flacGetSeekPoints, jlong jContext, jlong timeUs,
             jlongArray outSeekPoints) {
  Context *context = reinterpret_cast<Context *>(jContext);
  std::array<int64_t, 4> result;
  bool success = context->parser->getSeekPositions(timeUs, result);
  if (success) {
    env->SetLongArrayRegion(outSeekPoints, 0, result.size(), result.data());
  }
  return success;
}

DECODER_FUNC(jstring, flacGetStateString, jlong jContext) {
  Context *context = reinterpret_cast<Context *>(jContext);
  const char *str = context->parser->getDecoderStateString();
  return env->NewStringUTF(str);
}

DECODER_FUNC(jboolean, flacIsDecoderAtEndOfStream, jlong jContext) {
  Context *context = reinterpret_cast<Context *>(jContext);
  return context->parser->isDecoderAtEndOfStream();
}

DECODER_FUNC(void, flacFlush, jlong jContext) {
  Context *context = reinterpret_cast<Context *>(jContext);
  context->parser->flush();
}

DECODER_FUNC(void, flacReset, jlong jContext, jlong newPosition) {
  Context *context = reinterpret_cast<Context *>(jContext);
  context->parser->reset(newPosition);
}

DECODER_FUNC(void, flacRelease, jlong jContext) {
  Context *context = reinterpret_cast<Context *>(jContext);
  delete context;
}

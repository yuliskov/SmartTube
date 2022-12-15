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

#include <cpu-features.h>
#ifdef __ARM_NEON__
#include <arm_neon.h>
#endif
#include <jni.h>

#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <pthread.h>
#include <algorithm>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <new>

#define VPX_CODEC_DISABLE_COMPAT 1
#include "vpx/vpx_decoder.h"
#include "vpx/vp8dx.h"

#define LOG_TAG "vpx_jni"
#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, \
                                             __VA_ARGS__))

#define DECODER_FUNC(RETURN_TYPE, NAME, ...) \
  extern "C" { \
  JNIEXPORT RETURN_TYPE \
    Java_com_google_android_exoplayer2_ext_vp9_VpxDecoder_ ## NAME \
      (JNIEnv* env, jobject thiz, ##__VA_ARGS__);\
  } \
  JNIEXPORT RETURN_TYPE \
    Java_com_google_android_exoplayer2_ext_vp9_VpxDecoder_ ## NAME \
      (JNIEnv* env, jobject thiz, ##__VA_ARGS__)\

#define LIBRARY_FUNC(RETURN_TYPE, NAME, ...) \
  extern "C" { \
  JNIEXPORT RETURN_TYPE \
    Java_com_google_android_exoplayer2_ext_vp9_VpxLibrary_ ## NAME \
      (JNIEnv* env, jobject thiz, ##__VA_ARGS__);\
  } \
  JNIEXPORT RETURN_TYPE \
    Java_com_google_android_exoplayer2_ext_vp9_VpxLibrary_ ## NAME \
      (JNIEnv* env, jobject thiz, ##__VA_ARGS__)\

// JNI references for VpxOutputBuffer class.
static jmethodID initForYuvFrame;
static jmethodID initForPrivateFrame;
static jfieldID dataField;
static jfieldID outputModeField;
static jfieldID decoderPrivateField;

// android.graphics.ImageFormat.YV12.
static const int kHalPixelFormatYV12 = 0x32315659;
static const int kDecoderPrivateBase = 0x100;
static int errorCode;

jint JNI_OnLoad(JavaVM* vm, void* reserved) {
  JNIEnv* env;
  if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
    return -1;
  }
  return JNI_VERSION_1_6;
}

#ifdef __ARM_NEON__
static int convert_16_to_8_neon(const vpx_image_t* const img, jbyte* const data,
                                const int32_t uvHeight, const int32_t yLength,
                                const int32_t uvLength) {
  if (!(android_getCpuFeatures() & ANDROID_CPU_ARM_FEATURE_NEON)) return 0;
  uint32x2_t lcg_val = vdup_n_u32(random());
  lcg_val = vset_lane_u32(random(), lcg_val, 1);
  // LCG values recommended in good ol' "Numerical Recipes"
  const uint32x2_t LCG_MULT = vdup_n_u32(1664525);
  const uint32x2_t LCG_INCR = vdup_n_u32(1013904223);

  const uint16_t* srcBase =
      reinterpret_cast<uint16_t*>(img->planes[VPX_PLANE_Y]);
  uint8_t* dstBase = reinterpret_cast<uint8_t*>(data);
  // In units of uint16_t, so /2 from raw stride
  const int srcStride = img->stride[VPX_PLANE_Y] / 2;
  const int dstStride = img->stride[VPX_PLANE_Y];

  for (int y = 0; y < img->d_h; y++) {
    const uint16_t* src = srcBase;
    uint8_t* dst = dstBase;

    // Each read consumes 4 2-byte samples, but to reduce branches and
    // random steps we unroll to four rounds, so each loop consumes 16
    // samples.
    const int imax = img->d_w & ~15;
    int i;
    for (i = 0; i < imax; i += 16) {
      // Run a round of the RNG.
      lcg_val = vmla_u32(LCG_INCR, lcg_val, LCG_MULT);

      // The lower two bits of this LCG parameterization are garbage,
      // leaving streaks on the image. We access the upper bits of each
      // 16-bit lane by shifting. (We use this both as an 8- and 16-bit
      // vector, so the choice of which one to keep it as is arbitrary.)
      uint8x8_t randvec =
          vreinterpret_u8_u16(vshr_n_u16(vreinterpret_u16_u32(lcg_val), 8));

      // We retrieve the values and shift them so that the bits we'll
      // shift out (after biasing) are in the upper 8 bits of each 16-bit
      // lane.
      uint16x4_t values = vshl_n_u16(vld1_u16(src), 6);
      src += 4;

      // We add the bias bits in the lower 8 to the shifted values to get
      // the final values in the upper 8 bits.
      uint16x4_t added1 = vqadd_u16(values, vreinterpret_u16_u8(randvec));

      // Shifting the randvec bits left by 2 bits, as an 8-bit vector,
      // should leave us with enough bias to get the needed rounding
      // operation.
      randvec = vshl_n_u8(randvec, 2);

      // Retrieve and sum the next 4 pixels.
      values = vshl_n_u16(vld1_u16(src), 6);
      src += 4;
      uint16x4_t added2 = vqadd_u16(values, vreinterpret_u16_u8(randvec));

      // Reinterpret the two added vectors as 8x8, zip them together, and
      // discard the lower portions.
      uint8x8_t zipped =
          vuzp_u8(vreinterpret_u8_u16(added1), vreinterpret_u8_u16(added2))
              .val[1];
      vst1_u8(dst, zipped);
      dst += 8;

      // Run it again with the next two rounds using the remaining
      // entropy in randvec.
      randvec = vshl_n_u8(randvec, 2);
      values = vshl_n_u16(vld1_u16(src), 6);
      src += 4;
      added1 = vqadd_u16(values, vreinterpret_u16_u8(randvec));
      randvec = vshl_n_u8(randvec, 2);
      values = vshl_n_u16(vld1_u16(src), 6);
      src += 4;
      added2 = vqadd_u16(values, vreinterpret_u16_u8(randvec));
      zipped = vuzp_u8(vreinterpret_u8_u16(added1), vreinterpret_u8_u16(added2))
                   .val[1];
      vst1_u8(dst, zipped);
      dst += 8;
    }

    uint32_t randval = 0;
    // For the remaining pixels in each row - usually none, as most
    // standard sizes are divisible by 32 - convert them "by hand".
    while (i < img->d_w) {
      if (!randval) randval = random();
      dstBase[i] = (srcBase[i] + (randval & 3)) >> 2;
      i++;
      randval >>= 2;
    }

    srcBase += srcStride;
    dstBase += dstStride;
  }

  const uint16_t* srcUBase =
      reinterpret_cast<uint16_t*>(img->planes[VPX_PLANE_U]);
  const uint16_t* srcVBase =
      reinterpret_cast<uint16_t*>(img->planes[VPX_PLANE_V]);
  const int32_t uvWidth = (img->d_w + 1) / 2;
  uint8_t* dstUBase = reinterpret_cast<uint8_t*>(data + yLength);
  uint8_t* dstVBase = reinterpret_cast<uint8_t*>(data + yLength + uvLength);
  const int srcUVStride = img->stride[VPX_PLANE_V] / 2;
  const int dstUVStride = img->stride[VPX_PLANE_V];

  for (int y = 0; y < uvHeight; y++) {
    const uint16_t* srcU = srcUBase;
    const uint16_t* srcV = srcVBase;
    uint8_t* dstU = dstUBase;
    uint8_t* dstV = dstVBase;

    // As before, each i++ consumes 4 samples (8 bytes). For simplicity we
    // don't unroll these loops more than we have to, which is 8 samples.
    const int imax = uvWidth & ~7;
    int i;
    for (i = 0; i < imax; i += 8) {
      lcg_val = vmla_u32(LCG_INCR, lcg_val, LCG_MULT);
      uint8x8_t randvec =
          vreinterpret_u8_u16(vshr_n_u16(vreinterpret_u16_u32(lcg_val), 8));
      uint16x4_t uVal1 = vqadd_u16(vshl_n_u16(vld1_u16(srcU), 6),
                                   vreinterpret_u16_u8(randvec));
      srcU += 4;
      randvec = vshl_n_u8(randvec, 2);
      uint16x4_t vVal1 = vqadd_u16(vshl_n_u16(vld1_u16(srcV), 6),
                                   vreinterpret_u16_u8(randvec));
      srcV += 4;
      randvec = vshl_n_u8(randvec, 2);
      uint16x4_t uVal2 = vqadd_u16(vshl_n_u16(vld1_u16(srcU), 6),
                                   vreinterpret_u16_u8(randvec));
      srcU += 4;
      randvec = vshl_n_u8(randvec, 2);
      uint16x4_t vVal2 = vqadd_u16(vshl_n_u16(vld1_u16(srcV), 6),
                                   vreinterpret_u16_u8(randvec));
      srcV += 4;
      vst1_u8(dstU,
              vuzp_u8(vreinterpret_u8_u16(uVal1), vreinterpret_u8_u16(uVal2))
                  .val[1]);
      dstU += 8;
      vst1_u8(dstV,
              vuzp_u8(vreinterpret_u8_u16(vVal1), vreinterpret_u8_u16(vVal2))
                  .val[1]);
      dstV += 8;
    }

    uint32_t randval = 0;
    while (i < uvWidth) {
      if (!randval) randval = random();
      dstUBase[i] = (srcUBase[i] + (randval & 3)) >> 2;
      randval >>= 2;
      dstVBase[i] = (srcVBase[i] + (randval & 3)) >> 2;
      randval >>= 2;
      i++;
    }

    srcUBase += srcUVStride;
    srcVBase += srcUVStride;
    dstUBase += dstUVStride;
    dstVBase += dstUVStride;
  }

  return 1;
}

#endif  // __ARM_NEON__

static void convert_16_to_8_standard(const vpx_image_t* const img,
                                     jbyte* const data, const int32_t uvHeight,
                                     const int32_t yLength,
                                     const int32_t uvLength) {
  // Y
  int sampleY = 0;
  for (int y = 0; y < img->d_h; y++) {
    const uint16_t* srcBase = reinterpret_cast<uint16_t*>(
        img->planes[VPX_PLANE_Y] + img->stride[VPX_PLANE_Y] * y);
    int8_t* destBase = data + img->stride[VPX_PLANE_Y] * y;
    for (int x = 0; x < img->d_w; x++) {
      // Lightweight dither. Carryover the remainder of each 10->8 bit
      // conversion to the next pixel.
      sampleY += *srcBase++;
      *destBase++ = sampleY >> 2;
      sampleY = sampleY & 3;  // Remainder.
    }
  }
  // UV
  int sampleU = 0;
  int sampleV = 0;
  const int32_t uvWidth = (img->d_w + 1) / 2;
  for (int y = 0; y < uvHeight; y++) {
    const uint16_t* srcUBase = reinterpret_cast<uint16_t*>(
        img->planes[VPX_PLANE_U] + img->stride[VPX_PLANE_U] * y);
    const uint16_t* srcVBase = reinterpret_cast<uint16_t*>(
        img->planes[VPX_PLANE_V] + img->stride[VPX_PLANE_V] * y);
    int8_t* destUBase = data + yLength + img->stride[VPX_PLANE_U] * y;
    int8_t* destVBase =
        data + yLength + uvLength + img->stride[VPX_PLANE_V] * y;
    for (int x = 0; x < uvWidth; x++) {
      // Lightweight dither. Carryover the remainder of each 10->8 bit
      // conversion to the next pixel.
      sampleU += *srcUBase++;
      *destUBase++ = sampleU >> 2;
      sampleU = sampleU & 3;  // Remainder.
      sampleV += *srcVBase++;
      *destVBase++ = sampleV >> 2;
      sampleV = sampleV & 3;  // Remainder.
    }
  }
}

struct JniFrameBuffer {
  friend class JniBufferManager;

  int stride[4];
  uint8_t* planes[4];
  int d_w;
  int d_h;

 private:
  int id;
  int ref_count;
  vpx_codec_frame_buffer_t vpx_fb;
};

class JniBufferManager {
  static const int MAX_FRAMES = 32;

  JniFrameBuffer* all_buffers[MAX_FRAMES];
  int all_buffer_count = 0;

  JniFrameBuffer* free_buffers[MAX_FRAMES];
  int free_buffer_count = 0;

  pthread_mutex_t mutex;

 public:
  JniBufferManager() { pthread_mutex_init(&mutex, NULL); }

  ~JniBufferManager() {
    while (all_buffer_count--) {
      free(all_buffers[all_buffer_count]->vpx_fb.data);
    }
  }

  int get_buffer(size_t min_size, vpx_codec_frame_buffer_t* fb) {
    pthread_mutex_lock(&mutex);
    JniFrameBuffer* out_buffer;
    if (free_buffer_count) {
      out_buffer = free_buffers[--free_buffer_count];
      if (out_buffer->vpx_fb.size < min_size) {
        free(out_buffer->vpx_fb.data);
        out_buffer->vpx_fb.data = (uint8_t*)malloc(min_size);
        out_buffer->vpx_fb.size = min_size;
      }
    } else {
      out_buffer = new JniFrameBuffer();
      out_buffer->id = all_buffer_count;
      all_buffers[all_buffer_count++] = out_buffer;
      out_buffer->vpx_fb.data = (uint8_t*)malloc(min_size);
      out_buffer->vpx_fb.size = min_size;
      out_buffer->vpx_fb.priv = &out_buffer->id;
    }
    *fb = out_buffer->vpx_fb;
    int retVal = 0;
    if (!out_buffer->vpx_fb.data || all_buffer_count >= MAX_FRAMES) {
      LOGE("ERROR: JniBufferManager get_buffer OOM.");
      retVal = -1;
    } else {
      memset(fb->data, 0, fb->size);
    }
    out_buffer->ref_count = 1;
    pthread_mutex_unlock(&mutex);
    return retVal;
  }

  JniFrameBuffer* get_buffer(int id) const {
    if (id < 0 || id >= all_buffer_count) {
      LOGE("ERROR: JniBufferManager get_buffer invalid id %d.", id);
      return NULL;
    }
    return all_buffers[id];
  }

  void add_ref(int id) {
    if (id < 0 || id >= all_buffer_count) {
      LOGE("ERROR: JniBufferManager add_ref invalid id %d.", id);
      return;
    }
    pthread_mutex_lock(&mutex);
    all_buffers[id]->ref_count++;
    pthread_mutex_unlock(&mutex);
  }

  int release(int id) {
    if (id < 0 || id >= all_buffer_count) {
      LOGE("ERROR: JniBufferManager release invalid id %d.", id);
      return -1;
    }
    pthread_mutex_lock(&mutex);
    JniFrameBuffer* buffer = all_buffers[id];
    if (!buffer->ref_count) {
      LOGE("ERROR: JniBufferManager release, buffer already released.");
      pthread_mutex_unlock(&mutex);
      return -1;
    }
    if (!--buffer->ref_count) {
      free_buffers[free_buffer_count++] = buffer;
    }
    pthread_mutex_unlock(&mutex);
    return 0;
  }
};

struct JniCtx {
  JniCtx() { buffer_manager = new JniBufferManager(); }

  ~JniCtx() {
    if (native_window) {
      ANativeWindow_release(native_window);
    }
    if (buffer_manager) {
      delete buffer_manager;
    }
  }

  void acquire_native_window(JNIEnv* env, jobject new_surface) {
    if (surface != new_surface) {
      if (native_window) {
        ANativeWindow_release(native_window);
      }
      native_window = ANativeWindow_fromSurface(env, new_surface);
      surface = new_surface;
      width = 0;
    }
  }

  JniBufferManager* buffer_manager = NULL;
  vpx_codec_ctx_t* decoder = NULL;
  ANativeWindow* native_window = NULL;
  jobject surface = NULL;
  int width = 0;
  int height = 0;
};

int vpx_get_frame_buffer(void* priv, size_t min_size,
                         vpx_codec_frame_buffer_t* fb) {
  JniBufferManager* const buffer_manager =
      reinterpret_cast<JniBufferManager*>(priv);
  return buffer_manager->get_buffer(min_size, fb);
}

int vpx_release_frame_buffer(void* priv, vpx_codec_frame_buffer_t* fb) {
  JniBufferManager* const buffer_manager =
      reinterpret_cast<JniBufferManager*>(priv);
  return buffer_manager->release(*(int*)fb->priv);
}

DECODER_FUNC(jlong, vpxInit, jboolean disableLoopFilter,
             jboolean enableRowMultiThreadMode, jint threads) {
  JniCtx* context = new JniCtx();
  context->decoder = new vpx_codec_ctx_t();
  vpx_codec_dec_cfg_t cfg = {0, 0, 0};
  cfg.threads = threads;
  errorCode = 0;
  vpx_codec_err_t err =
      vpx_codec_dec_init(context->decoder, &vpx_codec_vp9_dx_algo, &cfg, 0);
  if (err) {
    LOGE("ERROR: Failed to initialize libvpx decoder, error = %d.", err);
    errorCode = err;
    return 0;
  }
#ifdef VPX_CTRL_VP9_DECODE_SET_ROW_MT
  err = vpx_codec_control(context->decoder, VP9D_SET_ROW_MT,
                          enableRowMultiThreadMode);
  if (err) {
    LOGE("ERROR: Failed to enable row multi thread mode, error = %d.", err);
  }
#endif
  if (disableLoopFilter) {
    err = vpx_codec_control(context->decoder, VP9_SET_SKIP_LOOP_FILTER, true);
    if (err) {
      LOGE("ERROR: Failed to shut off libvpx loop filter, error = %d.", err);
    }
#ifdef VPX_CTRL_VP9_SET_LOOP_FILTER_OPT
  } else {
    err = vpx_codec_control(context->decoder, VP9D_SET_LOOP_FILTER_OPT, true);
    if (err) {
      LOGE("ERROR: Failed to enable loop filter optimization, error = %d.",
           err);
    }
#endif
  }
  err = vpx_codec_set_frame_buffer_functions(
      context->decoder, vpx_get_frame_buffer, vpx_release_frame_buffer,
      context->buffer_manager);
  if (err) {
    LOGE("ERROR: Failed to set libvpx frame buffer functions, error = %d.",
         err);
  }

  // Populate JNI References.
  const jclass outputBufferClass = env->FindClass(
      "com/google/android/exoplayer2/ext/vp9/VpxOutputBuffer");
  initForYuvFrame = env->GetMethodID(outputBufferClass, "initForYuvFrame",
                                     "(IIIII)Z");
  initForPrivateFrame =
      env->GetMethodID(outputBufferClass, "initForPrivateFrame", "(II)V");
  dataField = env->GetFieldID(outputBufferClass, "data",
                              "Ljava/nio/ByteBuffer;");
  outputModeField = env->GetFieldID(outputBufferClass, "mode", "I");
  decoderPrivateField =
      env->GetFieldID(outputBufferClass, "decoderPrivate", "I");
  return reinterpret_cast<intptr_t>(context);
}

DECODER_FUNC(jlong, vpxDecode, jlong jContext, jobject encoded, jint len) {
  JniCtx* const context = reinterpret_cast<JniCtx*>(jContext);
  const uint8_t* const buffer =
      reinterpret_cast<const uint8_t*>(env->GetDirectBufferAddress(encoded));
  const vpx_codec_err_t status =
      vpx_codec_decode(context->decoder, buffer, len, NULL, 0);
  errorCode = 0;
  if (status != VPX_CODEC_OK) {
    LOGE("ERROR: vpx_codec_decode() failed, status= %d", status);
    errorCode = status;
    return -1;
  }
  return 0;
}

DECODER_FUNC(jlong, vpxSecureDecode, jlong jContext, jobject encoded, jint len,
    jobject mediaCrypto, jint inputMode, jbyteArray&, jbyteArray&,
    jint inputNumSubSamples, jintArray numBytesOfClearData,
    jintArray numBytesOfEncryptedData) {
  // Doesn't support
  // Java client should have checked vpxSupportSecureDecode
  // and avoid calling this
  // return -2 (DRM Error)
  return -2;
}

DECODER_FUNC(jlong, vpxClose, jlong jContext) {
  JniCtx* const context = reinterpret_cast<JniCtx*>(jContext);
  vpx_codec_destroy(context->decoder);
  delete context;
  return 0;
}

DECODER_FUNC(jint, vpxGetFrame, jlong jContext, jobject jOutputBuffer) {
  JniCtx* const context = reinterpret_cast<JniCtx*>(jContext);
  vpx_codec_iter_t iter = NULL;
  const vpx_image_t* const img = vpx_codec_get_frame(context->decoder, &iter);

  if (img == NULL) {
    return 1;
  }

  const int kOutputModeYuv = 0;
  const int kOutputModeSurfaceYuv = 1;

  int outputMode = env->GetIntField(jOutputBuffer, outputModeField);
  if (outputMode == kOutputModeYuv) {
    const int kColorspaceUnknown = 0;
    const int kColorspaceBT601 = 1;
    const int kColorspaceBT709 = 2;
    const int kColorspaceBT2020 = 3;

    int colorspace = kColorspaceUnknown;
    switch (img->cs) {
      case VPX_CS_BT_601:
        colorspace = kColorspaceBT601;
        break;
      case VPX_CS_BT_709:
        colorspace = kColorspaceBT709;
        break;
    case VPX_CS_BT_2020:
        colorspace = kColorspaceBT2020;
        break;
      default:
        break;
    }

    // resize buffer if required.
    jboolean initResult = env->CallBooleanMethod(
        jOutputBuffer, initForYuvFrame, img->d_w, img->d_h,
        img->stride[VPX_PLANE_Y], img->stride[VPX_PLANE_U], colorspace);
    if (env->ExceptionCheck() || !initResult) {
      return -1;
    }

    // get pointer to the data buffer.
    const jobject dataObject = env->GetObjectField(jOutputBuffer, dataField);
    jbyte* const data =
        reinterpret_cast<jbyte*>(env->GetDirectBufferAddress(dataObject));

    const int32_t uvHeight = (img->d_h + 1) / 2;
    const uint64_t yLength = img->stride[VPX_PLANE_Y] * img->d_h;
    const uint64_t uvLength = img->stride[VPX_PLANE_U] * uvHeight;
    if (img->fmt == VPX_IMG_FMT_I42016) {  // HBD planar 420.
      // Note: The stride for BT2020 is twice of what we use so this is wasting
      // memory. The long term goal however is to upload half-float/short so
      // it's not important to optimize the stride at this time.
      int converted = 0;
#ifdef __ARM_NEON__
      converted = convert_16_to_8_neon(img, data, uvHeight, yLength, uvLength);
#endif  // __ARM_NEON__
      if (!converted) {
        convert_16_to_8_standard(img, data, uvHeight, yLength, uvLength);
      }
    } else {
      // TODO: This copy can be eliminated by using external frame
      // buffers. This is insignificant for smaller videos but takes ~1.5ms
      // for 1080p clips. So this should eventually be gotten rid of.
      memcpy(data, img->planes[VPX_PLANE_Y], yLength);
      memcpy(data + yLength, img->planes[VPX_PLANE_U], uvLength);
      memcpy(data + yLength + uvLength, img->planes[VPX_PLANE_V], uvLength);
    }
  } else if (outputMode == kOutputModeSurfaceYuv &&
             img->fmt != VPX_IMG_FMT_I42016) {
    int id = *(int*)img->fb_priv;
    context->buffer_manager->add_ref(id);
    JniFrameBuffer* jfb = context->buffer_manager->get_buffer(id);
    for (int i = 2; i >= 0; i--) {
      jfb->stride[i] = img->stride[i];
      jfb->planes[i] = (uint8_t*)img->planes[i];
    }
    jfb->d_w = img->d_w;
    jfb->d_h = img->d_h;
    env->CallVoidMethod(jOutputBuffer, initForPrivateFrame, img->d_w, img->d_h);
    if (env->ExceptionCheck()) {
      return -1;
    }
    env->SetIntField(jOutputBuffer, decoderPrivateField,
                     id + kDecoderPrivateBase);
  }
  return 0;
}

DECODER_FUNC(jint, vpxRenderFrame, jlong jContext, jobject jSurface,
             jobject jOutputBuffer) {
  JniCtx* const context = reinterpret_cast<JniCtx*>(jContext);
  const int id = env->GetIntField(jOutputBuffer, decoderPrivateField) -
                 kDecoderPrivateBase;
  JniFrameBuffer* srcBuffer = context->buffer_manager->get_buffer(id);
  context->acquire_native_window(env, jSurface);
  if (context->native_window == NULL || !srcBuffer) {
    return 1;
  }
  if (context->width != srcBuffer->d_w || context->height != srcBuffer->d_h) {
    ANativeWindow_setBuffersGeometry(context->native_window, srcBuffer->d_w,
                                     srcBuffer->d_h, kHalPixelFormatYV12);
    context->width = srcBuffer->d_w;
    context->height = srcBuffer->d_h;
  }
  ANativeWindow_Buffer buffer;
  int result = ANativeWindow_lock(context->native_window, &buffer, NULL);
  if (buffer.bits == NULL || result) {
    return -1;
  }
  // Y
  const size_t src_y_stride = srcBuffer->stride[VPX_PLANE_Y];
  int stride = srcBuffer->d_w;
  const uint8_t* src_base =
      reinterpret_cast<uint8_t*>(srcBuffer->planes[VPX_PLANE_Y]);
  uint8_t* dest_base = (uint8_t*)buffer.bits;
  for (int y = 0; y < srcBuffer->d_h; y++) {
    memcpy(dest_base, src_base, stride);
    src_base += src_y_stride;
    dest_base += buffer.stride;
  }
  // UV
  const int src_uv_stride = srcBuffer->stride[VPX_PLANE_U];
  const int dest_uv_stride = (buffer.stride / 2 + 15) & (~15);
  const int32_t buffer_uv_height = (buffer.height + 1) / 2;
  const int32_t height =
      std::min((int32_t)(srcBuffer->d_h + 1) / 2, buffer_uv_height);
  stride = (srcBuffer->d_w + 1) / 2;
  src_base = reinterpret_cast<uint8_t*>(srcBuffer->planes[VPX_PLANE_U]);
  const uint8_t* src_v_base =
      reinterpret_cast<uint8_t*>(srcBuffer->planes[VPX_PLANE_V]);
  uint8_t* dest_v_base =
      ((uint8_t*)buffer.bits) + buffer.stride * buffer.height;
  dest_base = dest_v_base + buffer_uv_height * dest_uv_stride;
  for (int y = 0; y < height; y++) {
    memcpy(dest_base, src_base, stride);
    memcpy(dest_v_base, src_v_base, stride);
    src_base += src_uv_stride;
    src_v_base += src_uv_stride;
    dest_base += dest_uv_stride;
    dest_v_base += dest_uv_stride;
  }
  return ANativeWindow_unlockAndPost(context->native_window);
}

DECODER_FUNC(void, vpxReleaseFrame, jlong jContext, jobject jOutputBuffer) {
  JniCtx* const context = reinterpret_cast<JniCtx*>(jContext);
  const int id = env->GetIntField(jOutputBuffer, decoderPrivateField) -
                 kDecoderPrivateBase;
  env->SetIntField(jOutputBuffer, decoderPrivateField, -1);
  context->buffer_manager->release(id);
}

DECODER_FUNC(jstring, vpxGetErrorMessage, jlong jContext) {
  JniCtx* const context = reinterpret_cast<JniCtx*>(jContext);
  return env->NewStringUTF(vpx_codec_error(context->decoder));
}

DECODER_FUNC(jint, vpxGetErrorCode, jlong jContext) { return errorCode; }

LIBRARY_FUNC(jstring, vpxIsSecureDecodeSupported) {
  // Doesn't support
  return 0;
}

LIBRARY_FUNC(jstring, vpxGetVersion) {
  return env->NewStringUTF(vpx_codec_version_str());
}

LIBRARY_FUNC(jstring, vpxGetBuildConfig) {
  return env->NewStringUTF(vpx_codec_build_config());
}

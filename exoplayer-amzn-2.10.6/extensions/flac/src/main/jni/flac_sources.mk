#
# Copyright (C) 2016 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

FLAC_SOURCES = \
  flac_jni.cc                                    \
  flac_parser.cc                                 \
  flac/src/libFLAC/bitmath.c                     \
  flac/src/libFLAC/bitreader.c                   \
  flac/src/libFLAC/bitwriter.c                   \
  flac/src/libFLAC/cpu.c                         \
  flac/src/libFLAC/crc.c                         \
  flac/src/libFLAC/fixed.c                       \
  flac/src/libFLAC/fixed_intrin_sse2.c           \
  flac/src/libFLAC/fixed_intrin_ssse3.c          \
  flac/src/libFLAC/float.c                       \
  flac/src/libFLAC/format.c                      \
  flac/src/libFLAC/lpc.c                         \
  flac/src/libFLAC/lpc_intrin_avx2.c             \
  flac/src/libFLAC/lpc_intrin_sse2.c             \
  flac/src/libFLAC/lpc_intrin_sse41.c            \
  flac/src/libFLAC/lpc_intrin_sse.c              \
  flac/src/libFLAC/md5.c                         \
  flac/src/libFLAC/memory.c                      \
  flac/src/libFLAC/metadata_iterators.c          \
  flac/src/libFLAC/metadata_object.c             \
  flac/src/libFLAC/stream_decoder.c              \
  flac/src/libFLAC/stream_encoder.c              \
  flac/src/libFLAC/stream_encoder_framing.c      \
  flac/src/libFLAC/stream_encoder_intrin_avx2.c  \
  flac/src/libFLAC/stream_encoder_intrin_sse2.c  \
  flac/src/libFLAC/stream_encoder_intrin_ssse3.c \
  flac/src/libFLAC/window.c

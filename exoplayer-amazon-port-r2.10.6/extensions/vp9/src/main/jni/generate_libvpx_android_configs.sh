#!/bin/bash
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

# a bash script that generates the necessary config files for libvpx android ndk
# builds.

set -e

if [ $# -ne 0 ]; then
  echo "Usage: ${0}"
  exit
fi

# configuration parameters common to all architectures
common_params="--disable-examples --disable-docs --enable-realtime-only"
common_params+=" --disable-vp8 --disable-vp9-encoder --disable-webm-io"
common_params+=" --disable-libyuv --disable-runtime-cpu-detect"
common_params+=" --enable-external-build"

# configuration parameters for various architectures
arch[0]="armeabi-v7a"
config[0]="--target=armv7-android-gcc --enable-neon --enable-neon-asm"

arch[1]="x86"
config[1]="--force-target=x86-android-gcc --disable-sse2"
config[1]+=" --disable-sse3 --disable-ssse3 --disable-sse4_1 --disable-avx"
config[1]+=" --disable-avx2 --enable-pic"

arch[2]="arm64-v8a"
config[2]="--force-target=armv8-android-gcc --enable-neon"

arch[3]="x86_64"
config[3]="--force-target=x86_64-android-gcc --disable-sse2"
config[3]+=" --disable-sse3 --disable-ssse3 --disable-sse4_1 --disable-avx"
config[3]+=" --disable-avx2 --enable-pic --disable-neon --disable-neon-asm"

limit=$((${#arch[@]} - 1))

# list of files allowed after running configure in each arch directory.
# everything else will be removed.
allowed_files="libvpx_srcs.txt vpx_config.c vpx_config.h vpx_scale_rtcd.h"
allowed_files+=" vp8_rtcd.h vp9_rtcd.h vpx_version.h vpx_config.asm"
allowed_files+=" vpx_dsp_rtcd.h libvpx.ver"

remove_trailing_whitespace() {
  perl -pi -e 's/\s\+$//' "$@"
}

convert_asm() {
  for i in $(seq 0 ${limit}); do
    while read file; do
      case "${file}" in
        *.asm.[sS])
          # Some files may already have been processed (there are duplicated
          # .asm.s files for vp8 in the armeabi/armeabi-v7a configurations).
          file="libvpx/${file}"
          if [[ ! -e "${file}" ]]; then
            asm_file="${file%.[sS]}"
            cat "${asm_file}" | libvpx/build/make/ads2gas.pl > "${file}"
            remove_trailing_whitespace "${file}"
            rm "${asm_file}"
          fi
          ;;
      esac
    done < libvpx_android_configs/${arch[${i}]}/libvpx_srcs.txt
  done
}

extglob_status="$(shopt extglob | cut -f2)"
shopt -s extglob
for i in $(seq 0 ${limit}); do
  mkdir -p "libvpx_android_configs/${arch[${i}]}"
  pushd "libvpx_android_configs/${arch[${i}]}"

  # configure and make
  echo "build_android_configs: "
  echo "configure ${config[${i}]} ${common_params}"
  ../../libvpx/configure ${config[${i}]} ${common_params}
  rm -f libvpx_srcs.txt
  for f in ${allowed_files}; do
    # the build system supports multiple different configurations. avoid
    # failing out when, for example, vp8_rtcd.h is not part of a configuration
    make "${f}" || true
  done

  # remove files that aren't needed
  rm -rf !(${allowed_files// /|})
  remove_trailing_whitespace *

  popd
done

# restore extglob status as it was before
if [[ "${extglob_status}" == "off" ]]; then
  shopt -u extglob
fi

convert_asm

echo "Generated android config files."

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

WORKING_DIR := $(call my-dir)

# build libflacJNI.so
include $(CLEAR_VARS)
include $(WORKING_DIR)/flac_sources.mk

LOCAL_PATH := $(WORKING_DIR)
LOCAL_MODULE := libflacJNI
LOCAL_ARM_MODE := arm
LOCAL_CPP_EXTENSION := .cc

LOCAL_C_INCLUDES := \
    $(LOCAL_PATH)/flac/include \
    $(LOCAL_PATH)/flac/src/libFLAC/include
LOCAL_SRC_FILES := $(FLAC_SOURCES)

LOCAL_CFLAGS += '-DPACKAGE_VERSION="1.3.2"' -DFLAC__NO_MD5 -DFLAC__INTEGER_ONLY_LIBRARY
LOCAL_CFLAGS += -D_REENTRANT -DPIC -DU_COMMON_IMPLEMENTATION -fPIC -DHAVE_SYS_PARAM_H
LOCAL_CFLAGS += -O3 -funroll-loops -finline-functions -DFLAC__NO_ASM '-DFLAC__HAS_OGG=0'

LOCAL_LDLIBS := -llog -lz -lm
include $(BUILD_SHARED_LIBRARY)

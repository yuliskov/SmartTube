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

#ifndef INCLUDE_DATA_SOURCE_H_
#define INCLUDE_DATA_SOURCE_H_

#include <jni.h>
#include <sys/types.h>

class DataSource {
 public:
  virtual ~DataSource() {}
  // Returns the number of bytes read, or -1 on failure. It's not an error if
  // this returns zero; it just means the given offset is equal to, or
  // beyond, the end of the source.
  virtual ssize_t readAt(off64_t offset, void* const data, size_t size) = 0;
};

#endif  // INCLUDE_DATA_SOURCE_H_

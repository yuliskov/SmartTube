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

set -e
ASM_CONVERTER="./libopus/celt/arm/arm2gnu.pl"

if [[ ! -x "${ASM_CONVERTER}" ]]; then
  echo "Please make sure you have checked out libopus."
  exit
fi

while read file; do
  # This check is required because the ASM conversion script doesn't seem to be
  # idempotent.
  if [[ ! "${file}" =~ .*_gnu\.s$ ]]; then
    gnu_file="${file%.s}_gnu.s"
    ${ASM_CONVERTER} "${file}" > "${gnu_file}"
    # The ASM conversion script replaces includes with *_gnu.S. So, replace
    # occurences of "*-gnu.S" with "*_gnu.s".
    perl -pi -e "s/-gnu\.S/_gnu\.s/g" "${gnu_file}"
    rm -f "${file}"
  fi
done < <(find . -iname '*.s')

# Generate armopts.s from armopts.s.in
sed \
  -e "s/@OPUS_ARM_MAY_HAVE_EDSP@/1/g" \
  -e "s/@OPUS_ARM_MAY_HAVE_MEDIA@/1/g" \
  -e "s/@OPUS_ARM_MAY_HAVE_NEON@/1/g" \
  libopus/celt/arm/armopts.s.in > libopus/celt/arm/armopts.s.temp
${ASM_CONVERTER} "libopus/celt/arm/armopts.s.temp" > "libopus/celt/arm/armopts_gnu.s"
rm "libopus/celt/arm/armopts.s.temp"
echo "Converted all ASM files and generated armopts.s successfully."

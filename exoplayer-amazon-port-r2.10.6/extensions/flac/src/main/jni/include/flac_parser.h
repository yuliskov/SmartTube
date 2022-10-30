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

#ifndef FLAC_PARSER_H_
#define FLAC_PARSER_H_

#include <stdint.h>

#include <array>
#include <cstdlib>
#include <string>
#include <vector>

// libFLAC parser
#include "FLAC/stream_decoder.h"

#include "include/data_source.h"

typedef int status_t;

struct FlacPicture {
  int type;
  std::string mimeType;
  std::string description;
  FLAC__uint32 width;
  FLAC__uint32 height;
  FLAC__uint32 depth;
  FLAC__uint32 colors;
  std::vector<char> data;
};

class FLACParser {
 public:
  FLACParser(DataSource *source);
  ~FLACParser();

  bool init();

  // stream properties
  unsigned getMaxBlockSize() const { return mStreamInfo.max_blocksize; }
  unsigned getSampleRate() const { return mStreamInfo.sample_rate; }
  unsigned getChannels() const { return mStreamInfo.channels; }
  unsigned getBitsPerSample() const { return mStreamInfo.bits_per_sample; }
  FLAC__uint64 getTotalSamples() const { return mStreamInfo.total_samples; }

  const FLAC__StreamMetadata_StreamInfo& getStreamInfo() const {
    return mStreamInfo;
  }

  bool areVorbisCommentsValid() const { return mVorbisCommentsValid; }

  std::vector<std::string> getVorbisComments() { return mVorbisComments; }

  bool arePicturesValid() const { return mPicturesValid; }

  const std::vector<FlacPicture> &getPictures() const { return mPictures; }

  int64_t getLastFrameTimestamp() const {
    return (1000000LL * mWriteHeader.number.sample_number) / getSampleRate();
  }

  int64_t getLastFrameFirstSampleIndex() const {
    return mWriteHeader.number.sample_number;
  }

  int64_t getNextFrameFirstSampleIndex() const {
    return mWriteHeader.number.sample_number + mWriteHeader.blocksize;
  }

  bool decodeMetadata();
  size_t readBuffer(void *output, size_t output_size);

  bool getSeekPositions(int64_t timeUs, std::array<int64_t, 4> &result);

  void flush() {
    reset(mCurrentPos);
  }

  void reset(int64_t newPosition) {
    if (mDecoder != NULL) {
      mCurrentPos = newPosition;
      mEOF = false;
      if (newPosition == 0) {
        mStreamInfoValid = false;
        mVorbisCommentsValid = false;
        mPicturesValid = false;
        mVorbisComments.clear();
        mPictures.clear();
        FLAC__stream_decoder_reset(mDecoder);
      } else {
        FLAC__stream_decoder_flush(mDecoder);
      }
    }
  }

  int64_t getDecodePosition() {
    uint64_t position;
    if (mDecoder != NULL
        && FLAC__stream_decoder_get_decode_position(mDecoder, &position)) {
      return position;
    }
    return -1;
  }

  const char *getDecoderStateString() {
    return FLAC__stream_decoder_get_resolved_state_string(mDecoder);
  }

  bool isDecoderAtEndOfStream() const {
    return FLAC__stream_decoder_get_state(mDecoder) ==
           FLAC__STREAM_DECODER_END_OF_STREAM;
  }

 private:
  DataSource *mDataSource;

  void (*mCopy)(int8_t *dst, const int *const *src, unsigned bytesPerSample,
                unsigned nSamples, unsigned nChannels);

  // handle to underlying libFLAC parser
  FLAC__StreamDecoder *mDecoder;

  // current position within the data source
  off64_t mCurrentPos;
  bool mEOF;

  // cached when the STREAMINFO metadata is parsed by libFLAC
  FLAC__StreamMetadata_StreamInfo mStreamInfo;
  bool mStreamInfoValid;

  const FLAC__StreamMetadata_SeekTable *mSeekTable;
  uint64_t firstFrameOffset;

  // cached when the VORBIS_COMMENT metadata is parsed by libFLAC
  std::vector<std::string> mVorbisComments;
  bool mVorbisCommentsValid;

  // cached when the PICTURE metadata is parsed by libFLAC
  std::vector<FlacPicture> mPictures;
  bool mPicturesValid;

  // cached when a decoded PCM block is "written" by libFLAC parser
  bool mWriteRequested;
  bool mWriteCompleted;
  FLAC__FrameHeader mWriteHeader;
  const FLAC__int32 *const *mWriteBuffer;

  // most recent error reported by libFLAC parser
  FLAC__StreamDecoderErrorStatus mErrorStatus;

  // no copy constructor or assignment
  FLACParser(const FLACParser &);
  FLACParser &operator=(const FLACParser &);

  // FLAC parser callbacks as C++ instance methods
  FLAC__StreamDecoderReadStatus readCallback(FLAC__byte buffer[],
                                             size_t *bytes);
  FLAC__StreamDecoderSeekStatus seekCallback(FLAC__uint64 absolute_byte_offset);
  FLAC__StreamDecoderTellStatus tellCallback(
      FLAC__uint64 *absolute_byte_offset);
  FLAC__StreamDecoderLengthStatus lengthCallback(FLAC__uint64 *stream_length);
  FLAC__bool eofCallback();
  FLAC__StreamDecoderWriteStatus writeCallback(
      const FLAC__Frame *frame, const FLAC__int32 *const buffer[]);
  void metadataCallback(const FLAC__StreamMetadata *metadata);
  void errorCallback(FLAC__StreamDecoderErrorStatus status);

  // FLAC parser callbacks as C-callable functions
  static FLAC__StreamDecoderReadStatus read_callback(
      const FLAC__StreamDecoder *decoder, FLAC__byte buffer[], size_t *bytes,
      void *client_data);
  static FLAC__StreamDecoderSeekStatus seek_callback(
      const FLAC__StreamDecoder *decoder, FLAC__uint64 absolute_byte_offset,
      void *client_data);
  static FLAC__StreamDecoderTellStatus tell_callback(
      const FLAC__StreamDecoder *decoder, FLAC__uint64 *absolute_byte_offset,
      void *client_data);
  static FLAC__StreamDecoderLengthStatus length_callback(
      const FLAC__StreamDecoder *decoder, FLAC__uint64 *stream_length,
      void *client_data);
  static FLAC__bool eof_callback(const FLAC__StreamDecoder *decoder,
                                 void *client_data);
  static FLAC__StreamDecoderWriteStatus write_callback(
      const FLAC__StreamDecoder *decoder, const FLAC__Frame *frame,
      const FLAC__int32 *const buffer[], void *client_data);
  static void metadata_callback(const FLAC__StreamDecoder *decoder,
                                const FLAC__StreamMetadata *metadata,
                                void *client_data);
  static void error_callback(const FLAC__StreamDecoder *decoder,
                             FLAC__StreamDecoderErrorStatus status,
                             void *client_data);
};

#endif  // FLAC_PARSER_H_

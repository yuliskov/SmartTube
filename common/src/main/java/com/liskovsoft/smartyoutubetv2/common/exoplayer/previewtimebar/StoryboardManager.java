package com.liskovsoft.smartyoutubetv2.common.exoplayer.previewtimebar;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.ArraySet;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;
import com.liskovsoft.appupdatechecker2.other.SettingsManager;
import com.liskovsoft.mediaserviceinterfaces.MediaItemManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemStoryboard;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemStoryboard.Size;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.utils.RxUtils;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import java.util.Set;

public class StoryboardManager {
    private static final String TAG = SettingsManager.class.getSimpleName();
    private static final long FRAME_DURATION_MS = 10_000;
    private final MediaItemManager mMediaItemManager;
    private final Context mContext;
    private Video mVideo;
    private long mLengthMs;
    private MediaItemStoryboard mStoryboard;
    private Disposable mFormatAction;
    private long[] mSeekPositions;
    private static final int DIRECTION_RIGHT = 0;
    private static final int DIRECTION_LEFT = 1;
    private int mCurrentImgNum = -1;
    private Set<Integer> mCachedImageNums;
    private int mSeekDirection = DIRECTION_RIGHT;
    private static final int MAX_PRELOADED_IMAGES = 3;

    public interface Callback {
        void onBitmapLoaded(Bitmap bitmap);
    }

    public StoryboardManager(Context context) {
        mContext = context;
        MediaService mediaService = YouTubeMediaService.instance();
        mMediaItemManager = mediaService.getMediaItemManager();
    }

    public void setVideo(Video video, long lengthMs) {
        mVideo = video;
        mLengthMs = lengthMs;
        mSeekPositions = null;
        mCachedImageNums = new ArraySet<>();

        RxUtils.disposeActions(mFormatAction);

        if (video == null) {
            return;
        }

        Observable<MediaItemStoryboard> storyboardObserve;

        if (video.mediaItem != null) {
            storyboardObserve = mMediaItemManager.getStoryboardObserve(video.mediaItem);
        } else {
            storyboardObserve = mMediaItemManager.getStoryboardObserve(video.videoId);
        }

        mFormatAction = storyboardObserve
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        storyboard -> {
                            mStoryboard = storyboard;
                            initSeekPositions();
                        },
                        error -> Log.e(TAG, "Error obtaining format info: %s", error.getMessage())
                );
    }

    private void initSeekPositions() {
        if (mLengthMs <= 0 || mStoryboard == null) {
            return;
        }

        long frameDurationMS = getFrameDurationMsAlt();

        if (frameDurationMS <= 1_000) {
            return;
        }

        int size = (int) (mLengthMs / frameDurationMS);
        mSeekPositions = new long[size];
        for (int i = 0; i < mSeekPositions.length; i++) {
            mSeekPositions[i] = i * (mLengthMs / mSeekPositions.length);
        }
    }

    private long getFrameDurationMs() {
        return FRAME_DURATION_MS;
    }

    private long getFrameDurationMsAlt() {
        if (mStoryboard == null) {
            return -1;
        }

        Size groupSize = mStoryboard.getGroupSize();

        return mStoryboard.getGroupDurationMS() / (groupSize.getRowCount() * groupSize.getColCount());
    }

    public long[] getSeekPositions() {
        if (mSeekPositions == null || mSeekPositions.length < 10) {
            // Preventing from video being skipped fully
            return null;
        }

        return mSeekPositions;
    }

    public void getBitmap(int index, Callback callback) {
        if (mStoryboard == null || mSeekPositions == null || index >= mSeekPositions.length) {
            return;
        }

        loadPreview(mSeekPositions[index], callback);
    }

    public void loadPreview(long currentPosition, Callback callback) {
        if (mStoryboard == null || mStoryboard.getGroupDurationMS() == 0) {
            return;
        }

        int imgNum = (int) currentPosition / mStoryboard.getGroupDurationMS();
        long realPosMS = currentPosition % mStoryboard.getGroupDurationMS();
        Size size = mStoryboard.getGroupSize();
        GlideThumbnailTransformation transformation =
                new GlideThumbnailTransformation(realPosMS, size.getWidth(), size.getHeight(), size.getRowCount(), size.getColCount(), size.getDurationEachMS());

        Glide.with(mContext)
                .asBitmap()
                .load(mStoryboard.getGroupUrl(imgNum))
                .override(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL)
                .transform(transformation)
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                        callback.onBitmapLoaded(resource);
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                        // NOP
                    }
                });

        if (mCurrentImgNum != imgNum) {
            mSeekDirection = mCurrentImgNum < imgNum ? DIRECTION_RIGHT : DIRECTION_LEFT;
            mCachedImageNums.add(imgNum);
            mCurrentImgNum = imgNum;

            preloadNextImage();
        }
    }

    private void preloadNextImage() {
        if (mStoryboard == null) {
            return;
        }

        for (int i = 1; i <= MAX_PRELOADED_IMAGES; i++) {
            int imgNum = mSeekDirection == DIRECTION_RIGHT ? mCurrentImgNum + i : mCurrentImgNum - i; // get next image
            preloadImage(imgNum);
        }
    }

    private void preloadImage(int imgNum) {
        if (mCachedImageNums.contains(imgNum) || imgNum < 0) {
            return;
        }

        Log.d(TAG, "Oops, image #" + imgNum + " didn't cached yet");

        mCachedImageNums.add(imgNum);

        String link = mStoryboard.getGroupUrl(imgNum);

        Glide.with(mContext)
                .load(link)
                .preload();
    }
}

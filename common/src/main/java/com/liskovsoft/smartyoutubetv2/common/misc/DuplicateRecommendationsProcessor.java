package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.youtubeapi.service.internal.MediaServiceData;
import com.liskovsoft.sharedutils.helpers.Helpers;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class DuplicateRecommendationsProcessor implements BrowseProcessor {
	private static final String TAG = DuplicateRecommendationsProcessor.class.getSimpleName();
	private final OnItemReady mOnItemReady;
	private final MediaServiceData mMediaServiceData;
	private boolean mIsHideDuplicateRecommendationsEnabled;
	private final Set<String> mGlobalSeenVideoIds = new HashSet<>();
	private boolean mIsFirstHomeGroup = true;
	
	// Dynamic threshold calculated based on initial group size
	private Integer mMinVideosThreshold = null;
	private final Set<String> mProcessedGroups = new HashSet<>();
	private final java.util.Map<String, Integer> mGroupSizeHistory = new java.util.HashMap<>();
	
	public interface OnContinuationNeeded {
		void onContinuationNeeded(VideoGroup videoGroup);
	}
	
	private OnContinuationNeeded mContinuationCallback;

    public DuplicateRecommendationsProcessor(Context context, OnItemReady onItemReady) {
        mOnItemReady = onItemReady;
        mMediaServiceData = MediaServiceData.instance();
        initData();
    }
    
    public DuplicateRecommendationsProcessor(Context context, OnItemReady onItemReady, OnContinuationNeeded continuationCallback) {
        mOnItemReady = onItemReady;
        mMediaServiceData = MediaServiceData.instance();
        mContinuationCallback = continuationCallback;
        initData();
    }
    
    /**
     * Clear the global seen video IDs to allow fresh content loading
     */
    public void clearSeenVideos() {
        mGlobalSeenVideoIds.clear();
        mIsFirstHomeGroup = true;
        mMinVideosThreshold = null;
        mProcessedGroups.clear();
        mGroupSizeHistory.clear();}

    private void initData() {
		mIsHideDuplicateRecommendationsEnabled = mMediaServiceData.isContentHidden(MediaServiceData.CONTENT_DUPLICATE_RECOMMENDATIONS_HOME);
		android.util.Log.d(TAG, "DuplicateRecommendationsProcessor initialized - enabled: " + mIsHideDuplicateRecommendationsEnabled);
	}

    @Override
    public void process(VideoGroup videoGroup) {
		// Always check the current setting to ensure real-time updates
		if (mMediaServiceData.isContentHidden(MediaServiceData.CONTENT_DUPLICATE_RECOMMENDATIONS_HOME) != mIsHideDuplicateRecommendationsEnabled) {
			mIsHideDuplicateRecommendationsEnabled = mMediaServiceData.isContentHidden(MediaServiceData.CONTENT_DUPLICATE_RECOMMENDATIONS_HOME);
			android.util.Log.d(TAG, "DuplicateRecommendationsProcessor changed - enabled: " + mIsHideDuplicateRecommendationsEnabled);
		}
	
        if (!mIsHideDuplicateRecommendationsEnabled || videoGroup == null || videoGroup.isEmpty()) {
            return;
        }

        // Only process home section recommendations
        if (!isHomeRecommendationsSection(videoGroup)) {
            // Reset flag when we're not in home section
            mIsFirstHomeGroup = true;
            return;
        }

        // Clear global tracking when processing the first home group of a new section load
        if (mIsFirstHomeGroup) {
            mGlobalSeenVideoIds.clear();
            mIsFirstHomeGroup = false;
        }

        removeDuplicateVideos(videoGroup);
    }

    @Override
    public void dispose() {
        // No cleanup needed since we don't use listeners
    }

    private boolean isHomeRecommendationsSection(VideoGroup videoGroup) {
		// Check if this VideoGroup belongs to the Home section
		if (videoGroup.getSection() == null) {
			return false;
		}
		
		int sectionType = videoGroup.getSection().getId();
		// Target all content within the Home tab (TYPE_HOME covers all dynamic sections)
		return sectionType == com.liskovsoft.mediaserviceinterfaces.data.MediaGroup.TYPE_HOME;
	}

    private void removeDuplicateVideos(VideoGroup videoGroup) {
        if (videoGroup.getVideos() == null) {
            android.util.Log.d(TAG, "removeDuplicateVideos: videoGroup.getVideos() is null");
            return;
        }

        String groupKey = videoGroup.getTitle() + "_" + videoGroup.hashCode();
        boolean isFirstProcessing = !mProcessedGroups.contains(groupKey);
        
        int originalSize = videoGroup.getVideos().size();
        
        // Calculate threshold on first run based on initial group size
        if (mMinVideosThreshold == null && isFirstProcessing && originalSize > 0) {
            mMinVideosThreshold = Math.max(5, Math.min(35, (int) originalSize));
            android.util.Log.d(TAG, "removeDuplicateVideos: Calculated MIN_VIDEOS_THRESHOLD = " + mMinVideosThreshold + " based on initial size " + originalSize);
        }

        Iterator<Video> iterator = videoGroup.getVideos().iterator();
        int removedCount = 0;
        
        while (iterator.hasNext()) {
            Video video = iterator.next();
            
            if (video.videoId != null) {
                if (mGlobalSeenVideoIds.contains(video.videoId)) {
                    // Remove duplicate video
                    iterator.remove();
                    removedCount++;
                } else {
                    // Add to global seen set
                    mGlobalSeenVideoIds.add(video.videoId);
                }
            }
        }
        
        if (removedCount > 0) {
            android.util.Log.d(TAG, "removeDuplicateVideos: " + videoGroup.getTitle() + " - removed " + removedCount + " duplicates (" + videoGroup.getVideos().size() + "/" + (originalSize) + " remaining)");
        }
        
        // Check if we need to load more videos due to too many removals
        int remainingVideos = videoGroup.getVideos().size();
        if (mMinVideosThreshold != null && remainingVideos < mMinVideosThreshold && mContinuationCallback != null) {
            Integer previousSize = mGroupSizeHistory.get(groupKey);
            boolean shouldTriggerContinuation = false;
            
            if (isFirstProcessing) {
                // First time processing this group
                shouldTriggerContinuation = true;
            } else if (previousSize != null && remainingVideos > previousSize) {
                // Group size increased since last processing, continue trying
                shouldTriggerContinuation = true;
            }
            
            if (shouldTriggerContinuation) {
                android.util.Log.d(TAG, "removeDuplicateVideos: Triggering continuation for " + videoGroup.getTitle() + " (" + remainingVideos + " videos, threshold: " + mMinVideosThreshold + ", previous: " + previousSize + ")");
                mContinuationCallback.onContinuationNeeded(videoGroup);
            } else {
                android.util.Log.d(TAG, "removeDuplicateVideos: Skipping continuation for " + videoGroup.getTitle() + " (no size increase: " + remainingVideos + " <= " + previousSize + ")");
            }
        }
        
        // Update group size history
        mGroupSizeHistory.put(groupKey, remainingVideos);
        
        // Mark this group as processed
        if (isFirstProcessing) {
            mProcessedGroups.add(groupKey);
        }
    }
}
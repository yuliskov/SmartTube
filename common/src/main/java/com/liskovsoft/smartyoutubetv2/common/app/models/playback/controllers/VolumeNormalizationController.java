package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.BasePlayerController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerUI;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;

/**
 * Keeps the playback overlay button synchronized with the effective normalization state.
 * A click always toggles what the viewer is currently hearing while preserving the chosen
 * global/per-channel scope for the next click and for future app sessions.
 */
public class VolumeNormalizationController extends BasePlayerController {
    private String mChannelId;

    @Override
    public void onNewVideo(Video item) {
        mChannelId = item != null ? normalizeChannelId(item.channelId) : null;
        updateButtonState();
    }

    @Override
    public void onVideoLoaded(Video item) {
        updateChannelId(item != null ? item.channelId : null);
        updateButtonState();
    }

    @Override
    public void onMetadata(MediaItemMetadata metadata) {
        updateChannelId(metadata != null ? metadata.getChannelId() : null);
        updateButtonState();
    }

    @Override
    public void onButtonClicked(int buttonId, int buttonState) {
        if (buttonId != R.id.action_volume_normalization || getPlayer() == null) {
            return;
        }

        PlayerData playerData = getPlayerData();
        int previousMode = playerData.getVolumeNormalizationMode();
        int messageResId;

        if (previousMode == PlayerData.VOLUME_NORMALIZATION_GLOBAL) {
            playerData.setVolumeNormalizationMode(PlayerData.VOLUME_NORMALIZATION_OFF);
            messageResId = R.string.volume_normalization_global_disabled;
        } else if (previousMode == PlayerData.VOLUME_NORMALIZATION_SELECTED_CHANNELS) {
            String channelId = resolveChannelId();
            if (channelId == null) {
                showChannelUnavailable();
                return;
            }

            boolean enable = !playerData.isVolumeNormalizationEnabledForChannel(channelId);
            playerData.setVolumeNormalizationEnabledForChannel(channelId, enable);
            messageResId = enable ? R.string.volume_normalization_channel_enabled :
                    R.string.volume_normalization_channel_disabled;
        } else if (playerData.getLastVolumeNormalizationMode() == PlayerData.VOLUME_NORMALIZATION_GLOBAL) {
            playerData.setVolumeNormalizationMode(PlayerData.VOLUME_NORMALIZATION_GLOBAL);
            messageResId = R.string.volume_normalization_global_enabled;
        } else {
            String channelId = resolveChannelId();
            if (channelId == null) {
                showChannelUnavailable();
                return;
            }

            playerData.setVolumeNormalizationEnabledForChannel(channelId, true);
            playerData.setVolumeNormalizationMode(PlayerData.VOLUME_NORMALIZATION_SELECTED_CHANNELS);
            messageResId = R.string.volume_normalization_channel_enabled;
        }

        playerData.persistNow();
        updateButtonState();
        MessageHelpers.showMessage(getContext(), messageResId);

        int currentMode = playerData.getVolumeNormalizationMode();
        if ((previousMode == PlayerData.VOLUME_NORMALIZATION_OFF) !=
                (currentMode == PlayerData.VOLUME_NORMALIZATION_OFF)) {
            // The legacy volume booster is selected when the engine is created. Recreate the
            // engine when crossing the OFF boundary so it can never run beside the compressor.
            getPlayer().restartEngine();
        } else {
            getPlayer().refreshVolumeNormalization();
        }
    }

    private void updateChannelId(String channelId) {
        String normalized = normalizeChannelId(channelId);
        if (normalized != null) {
            mChannelId = normalized;
        }
    }

    private String resolveChannelId() {
        if (mChannelId != null) {
            return mChannelId;
        }

        Video video = getVideo();
        return video != null ? normalizeChannelId(video.channelId) : null;
    }

    private void updateButtonState() {
        if (getPlayer() == null) {
            return;
        }

        boolean enabled = getPlayerData().isVolumeNormalizationEnabled(resolveChannelId());
        getPlayer().setButtonState(
                R.id.action_volume_normalization,
                enabled ? PlayerUI.BUTTON_ON : PlayerUI.BUTTON_OFF);
    }

    private void showChannelUnavailable() {
        MessageHelpers.showMessage(getContext(), R.string.volume_normalization_channel_unavailable);
        updateButtonState();
    }

    private static String normalizeChannelId(String channelId) {
        if (channelId == null) {
            return null;
        }

        String normalized = channelId.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}

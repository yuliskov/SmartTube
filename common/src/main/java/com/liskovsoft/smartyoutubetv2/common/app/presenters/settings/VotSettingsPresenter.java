package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.locale.LocaleUtility;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.VoiceTranslateController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.vot.VotSettings;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;

import java.util.ArrayList;
import java.util.List;

/** TV-friendly settings for the options that affect voice-over playback. */
public final class VotSettingsPresenter {
    private static final int[] SOURCE_NAMES = {
            R.string.vot_lang_english, R.string.vot_target_russian, R.string.vot_lang_chinese,
            R.string.vot_lang_korean, R.string.vot_lang_arabic, R.string.vot_lang_french,
            R.string.vot_lang_italian, R.string.vot_lang_spanish, R.string.vot_lang_german,
            R.string.vot_lang_japanese
    };
    private static final int[] TARGET_NAMES = {
            R.string.vot_target_russian, R.string.vot_target_english, R.string.vot_target_kazakh
    };

    public static void show(Context context) {
        VotSettings settings = new VotSettings(context);
        AppDialogPresenter dialog = AppDialogPresenter.instance(context);

        dialog.appendSingleSwitch(UiOptionItem.from(context.getString(R.string.vot_feature_enabled),
                item -> {
                    settings.setEnabled(item.isSelected());
                    PlaybackPresenter playback = PlaybackPresenter.instance(context);
                    if (playback.getPlayer() != null) playback.getPlayer().setButtonVisible(
                            R.id.action_voice_translate, settings.shouldShowButton());
                    VoiceTranslateController controller = playback.getController(VoiceTranslateController.class);
                    if (controller != null) controller.onEnabledSettingChanged();
                }, settings.isEnabled()));

        List<OptionItem> providers = new ArrayList<>();
        String[] providerIds = {VotSettings.SOURCE_YANDEX, VotSettings.SOURCE_AUTO,
                VotSettings.SOURCE_YOUTUBE};
        int[] providerNames = {R.string.vot_provider_yandex, R.string.vot_provider_auto,
                R.string.vot_provider_youtube};
        for (int i = 0; i < providerIds.length; i++) {
            String provider = providerIds[i];
            providers.add(UiOptionItem.from(context.getString(providerNames[i]), item -> {
                if (provider.equals(settings.getVoiceSource())) return;
                settings.setVoiceSource(provider);
                restartVoiceOver(context);
            }, provider.equals(settings.getVoiceSource())));
        }
        dialog.appendRadioCategory(context.getString(R.string.vot_provider), providers);

        List<OptionItem> targets = new ArrayList<>();
        for (int i = 0; i < VotSettings.TARGET_LANGUAGES.length; i++) {
            String language = VotSettings.TARGET_LANGUAGES[i];
            targets.add(UiOptionItem.from(context.getString(TARGET_NAMES[i]), item -> {
                if (language.equals(settings.getTargetLanguage())) return;
                settings.setTargetLanguage(language);
                restartVoiceOver(context);
            }, language.equals(settings.getTargetLanguage())));
        }
        dialog.appendRadioCategory(context.getString(R.string.vot_target_language), targets);

        List<OptionItem> sources = new ArrayList<>();
        sources.add(UiOptionItem.from(context.getString(R.string.vot_source_auto), item -> {
            settings.setSourceLanguage(null);
            restartVoiceOver(context);
        }, !settings.hasSelectedSourceLanguage()));
        for (int i = 0; i < VotSettings.SOURCE_LANGUAGES.length; i++) {
            String language = VotSettings.SOURCE_LANGUAGES[i];
            sources.add(UiOptionItem.from(context.getString(SOURCE_NAMES[i]),
                    item -> {
                        if (settings.hasSelectedSourceLanguage()
                                && language.equals(settings.getSourceLanguage())) return;
                        settings.setSourceLanguage(language);
                        restartVoiceOver(context);
                    },
                    settings.hasSelectedSourceLanguage()
                            && language.equals(settings.getSourceLanguage())));
        }
        dialog.appendRadioCategory(context.getString(R.string.vot_source_language), sources);

        List<OptionItem> voices = new ArrayList<>();
        voices.add(UiOptionItem.from(context.getString(R.string.vot_live_voices),
                context.getString(R.string.vot_live_voices_desc),
                item -> {
                    if (settings.useLivelyVoice()) return;
                    settings.setLivelyVoice(true);
                    restartVoiceOver(context);
                }, settings.useLivelyVoice()));
        voices.add(UiOptionItem.from(context.getString(R.string.vot_standard_voices),
                item -> {
                    if (!settings.useLivelyVoice()) return;
                    settings.setLivelyVoice(false);
                    restartVoiceOver(context);
                }, !settings.useLivelyVoice()));
        dialog.appendRadioCategory(context.getString(R.string.vot_voice_type), voices);

        boolean hasToken = settings.getOAuthToken() != null;
        dialog.appendSingleButton(UiOptionItem.from(context.getString(R.string.vot_yandex_token),
                context.getString(hasToken ? R.string.vot_yandex_token_saved : R.string.vot_yandex_token_missing),
                item -> {
                    dialog.closeDialog();
                    SimpleEditDialog.showPasswordWithMessage(context,
                            context.getString(R.string.vot_yandex_token),
                            context.getString(R.string.vot_yandex_token_steps),
                            context.getString(R.string.vot_yandex_token_help),
                            "https://yandex.ru/dev/id/doc/"
                                    + ("ru".equals(LocaleUtility.getCurrentLanguage(context)) ? "ru" : "en")
                                    + "/tokens/debug-token", input -> {
                                if (VotSettings.oauthToken(input) == null) {
                                    MessageHelpers.showMessage(context, R.string.vot_yandex_token_invalid);
                                    return false;
                                }
                                if (!settings.setOAuthToken(input)) {
                                    MessageHelpers.showMessage(context, R.string.vot_yandex_token_save_failed);
                                    return false;
                                }
                                MessageHelpers.showMessage(context, R.string.vot_yandex_token_saved);
                                restartVoiceOver(context);
                                return true;
                            });
                }));
        if (hasToken) {
            dialog.appendSingleButton(UiOptionItem.from(context.getString(R.string.vot_yandex_token_remove),
                    item -> {
                        if (!settings.setOAuthToken(null)) {
                            MessageHelpers.showMessage(context, R.string.vot_yandex_token_save_failed);
                            return;
                        }
                        restartVoiceOver(context);
                        dialog.closeDialog();
                    }));
        }

        dialog.appendSingleButton(UiOptionItem.from(context.getString(R.string.vot_volume_settings),
                item -> showVolume(context, settings)));
        dialog.showDialog(context.getString(R.string.vot_settings));
    }

    private static void showVolume(Context context, VotSettings settings) {
        AppDialogPresenter dialog = AppDialogPresenter.instance(context);
        dialog.appendRadioCategory(context.getString(R.string.vot_original_volume),
                volumeOptions(context, settings, true));
        dialog.appendRadioCategory(context.getString(R.string.vot_translation_volume),
                volumeOptions(context, settings, false));
        dialog.appendSingleSwitch(UiOptionItem.from(context.getString(R.string.vot_adaptive_volume),
                item -> settings.setAdaptiveVolumeEnabled(item.isSelected()),
                settings.isAdaptiveVolumeEnabled()));
        dialog.appendSingleSwitch(UiOptionItem.from(context.getString(R.string.vot_strong_duck),
                item -> settings.setStrongDuckingEnabled(item.isSelected()),
                settings.isStrongDuckingEnabled()));
        dialog.showDialog(context.getString(R.string.vot_volume_settings));
    }

    private static List<OptionItem> volumeOptions(Context context, VotSettings settings, boolean original) {
        List<OptionItem> options = new ArrayList<>();
        for (int percent = 0; percent <= 100; percent += 5) {
            int value = percent;
            int selected = original ? settings.getOriginalVolume() : settings.getTranslationVolume();
            options.add(UiOptionItem.from(context.getString(R.string.volume, String.valueOf(percent)), item -> {
                if (original) settings.setOriginalVolume(value);
                else settings.setTranslationVolume(value);
            }, selected == percent));
        }
        return options;
    }

    private static void restartVoiceOver(Context context) {
        VoiceTranslateController controller = PlaybackPresenter.instance(context)
                .getController(VoiceTranslateController.class);
        if (controller != null) controller.onRequestSettingsChanged();
    }

    private VotSettingsPresenter() {}
}

package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.SearchData;

import java.util.ArrayList;
import java.util.List;

public class SearchSettingsPresenter {
    private final Context mContext;
    private final SearchData mSearchData;

    public SearchSettingsPresenter(Context context) {
        mContext = context;
        mSearchData = SearchData.instance(context);
    }

    public static SearchSettingsPresenter instance(Context context) {
        return new SearchSettingsPresenter(context.getApplicationContext());
    }

    public void show() {
        AppSettingsPresenter settingsPresenter = AppSettingsPresenter.instance(mContext);
        settingsPresenter.clear();
        
        appendMiscCategory(settingsPresenter);

        settingsPresenter.showDialog(mContext.getString(R.string.dialog_search));
    }

    private void appendMiscCategory(AppSettingsPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(mContext.getString(R.string.instant_voice_search),
                option -> mSearchData.setInstantVoiceSearchEnabled(option.isSelected()),
                mSearchData.isInstantVoiceSearchEnabled()));

        settingsPresenter.appendCheckedCategory(mContext.getString(R.string.player_other), options);
    }
}

package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.search;

import android.content.Context;
import android.util.AttributeSet;
import androidx.leanback.widget.SearchOrbView;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * A subclass of {@link SearchOrbView} that invokes dialog with search query params.
 */
public class SearchSettingsOrbView extends SearchOrbView {
    public SearchSettingsOrbView(Context context) {
        this(context, null);
    }

    public SearchSettingsOrbView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SearchSettingsOrbView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        setOrbIcon(getResources().getDrawable(R.drawable.search_bar_settings_orb));
    }
}

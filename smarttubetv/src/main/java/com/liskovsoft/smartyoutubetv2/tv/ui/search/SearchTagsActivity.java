package com.liskovsoft.smartyoutubetv2.tv.ui.search;

import android.content.Intent;
import android.os.Bundle;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;

public class SearchTagsActivity extends LeanbackActivity {
    private SearchTagsFragment mFragment;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_search_tags);
        mFragment = (SearchTagsFragment) getSupportFragmentManager()
                .findFragmentById(R.id.search_tags_fragment);
    }

    @Override
    public boolean onSearchRequested() {
        if (mFragment.hasResults()) {
            startActivity(new Intent(this, SearchTagsActivity.class));
        } else {
            mFragment.startRecognition();
        }
        return true;
    }
}

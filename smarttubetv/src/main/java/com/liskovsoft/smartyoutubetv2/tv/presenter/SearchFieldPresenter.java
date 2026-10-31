package com.liskovsoft.smartyoutubetv2.tv.presenter;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.leanback.widget.Presenter;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.video.GridFragmentHelper;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

public class SearchFieldPresenter extends Presenter {
    private static final String TAG = SearchFieldPresenter.class.getSimpleName();
    private int mWidth;
    private int mHeight;

    public static class SearchFieldCallback {
        private String mText;

        public void onTextChanged(String text) {
            mText = text;
        }

        public String getText() {
            return mText;
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        updateDimensions(parent.getContext());

        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View contentView = inflater.inflate(R.layout.search_field, parent, false);

        EditText editField = contentView.findViewById(R.id.simple_edit_value);
        ViewUtil.setDimensions(editField, mWidth, -1); // don't do auto height
        contentView.setOnFocusChangeListener((v, hasFocus) -> {
            Log.d(TAG, "On edit field focused");
            if (hasFocus) {
                editField.requestFocus();
            }
        });

        editField.setOnClickListener(v -> {
            Log.d(TAG, "On click");
            Helpers.showKeyboardAlt(v.getContext(), v);
        });

        return new ViewHolder(contentView);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object item) {
        SearchFieldCallback callback = (SearchFieldCallback) item;
        EditText editField = viewHolder.view.findViewById(R.id.simple_edit_value);
        if (editField.getTag() != null) {
            editField.removeTextChangedListener((TextWatcher) editField.getTag());
        }
        editField.setText(callback.getText());
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                callback.onTextChanged(s.toString());
            }
        };
        editField.addTextChangedListener(watcher);
        editField.setTag(watcher);
    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {

    }

    private void updateDimensions(Context context) {
        Pair<Integer, Integer> dimens = getCardDimensPx(context);

        mWidth = dimens.first;
        mHeight = dimens.second;
    }

    private Pair<Integer, Integer> getCardDimensPx(Context context) {
        return GridFragmentHelper.getCardDimensPx(
                context, R.dimen.channel_card_width,
                R.dimen.channel_card_height,
                MainUIData.instance(context).getVideoGridScale(),
                true);
    }
}

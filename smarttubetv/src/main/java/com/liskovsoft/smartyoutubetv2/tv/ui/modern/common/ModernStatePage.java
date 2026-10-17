package com.liskovsoft.smartyoutubetv2.tv.ui.modern.common;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.CategoryEmptyError;
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.ErrorFragmentData;
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.SignInError;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Empty, error and sign in states of a page, built from the error data of the presenters:<br/>
 * - nothing to show (no error, or an empty result): "Nothing found" and "Try again"<br/>
 * - load error: "Can't load content", the tips, the error in one line, "Try again" (and "Sign in" for auth errors)<br/>
 * - sign in needed: the reason and "Sign in"
 */
public final class ModernStatePage {
    /**
     * Error of the services when a result is empty.
     */
    private static final String EMPTY_RESULT = "fromNullable result is null";

    private ModernStatePage() {
    }

    /**
     * @param retry loads the page again, null to hide "Try again"
     */
    public static View create(Context context, ViewGroup parent, ErrorFragmentData data, @Nullable Runnable retry) {
        View page = LayoutInflater.from(context).inflate(R.layout.modern_state, parent, false);
        ImageView icon = page.findViewById(R.id.modern_state_icon);
        TextView title = page.findViewById(R.id.modern_state_title);
        TextView text = page.findViewById(R.id.modern_state_text);
        TextView details = page.findViewById(R.id.modern_state_details);
        ViewGroup actions = page.findViewById(R.id.modern_state_actions);

        if (data instanceof SignInError) {
            icon.setImageResource(R.drawable.modern_ic_account_circle);
            title.setText(data.getMessage());
            addAction(actions, data.getActionText(), data::onAction);
        } else if (data instanceof CategoryEmptyError && isEmpty(((CategoryEmptyError) data).getError())) {
            icon.setImageResource(R.drawable.modern_ic_video_library);
            title.setText(R.string.nothing_found);
            addAction(actions, retry != null ? context.getString(R.string.modern_try_again) : null, retry);
        } else if (data instanceof CategoryEmptyError) {
            Throwable error = ((CategoryEmptyError) data).getError();
            // Info icon upside down: exclamation mark in a circle
            icon.setImageResource(R.drawable.modern_ic_info);
            icon.setRotation(180);
            // First line: the title, the other lines: what to check
            String[] message = context.getString(R.string.msg_cant_load_content).split("\n", 2);
            title.setText(trimEnd(message[0]));
            setText(text, message.length > 1 ? message[1] : null);
            setText(details, String.format("%s: %s", error.getClass().getSimpleName(), error.getMessage()));
            addAction(actions, retry != null ? context.getString(R.string.modern_try_again) : null, retry);
            addAction(actions, data.getActionText(), data::onAction);
        } else {
            icon.setImageResource(R.drawable.modern_ic_info);
            title.setText(data.getMessage());
            addAction(actions, data.getActionText(), data::onAction);
        }

        if (text.getText().length() == 0) {
            text.setVisibility(View.GONE);
        }

        if (details.getText().length() == 0) {
            details.setVisibility(View.GONE);
        }

        if (actions.getChildCount() == 0) {
            actions.setVisibility(View.GONE);
        }

        return page;
    }

    /**
     * @return true if the page has an action and it took the focus
     */
    public static boolean focus(View page) {
        ViewGroup actions = page.findViewById(R.id.modern_state_actions);
        return actions.getChildCount() > 0 && actions.getChildAt(0).requestFocus();
    }

    private static boolean isEmpty(@Nullable Throwable error) {
        return error == null || Helpers.containsAny(error.getMessage(), EMPTY_RESULT);
    }

    private static void addAction(ViewGroup actions, @Nullable String label, @Nullable Runnable action) {
        if (TextUtils.isEmpty(label) || action == null) {
            return;
        }

        TextView button = (TextView) LayoutInflater.from(actions.getContext()).inflate(R.layout.modern_state_action, actions, false);
        button.setText(ModernText.sentenceCase(label));
        button.setOnClickListener(v -> action.run());
        actions.addView(button);
    }

    private static void setText(TextView view, @Nullable String text) {
        if (!TextUtils.isEmpty(text)) {
            view.setText(text);
        }
    }

    private static String trimEnd(String title) {
        return title.endsWith(".") ? title.substring(0, title.length() - 1) : title;
    }
}

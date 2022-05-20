package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import androidx.appcompat.app.AlertDialog;
import com.liskovsoft.sharedutils.helpers.KeyHelpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;

public class SimpleEditDialog {
    public interface OnChange {
        void onChange(String newValue);
    }

    public static void show(Context context, String defaultValue, OnChange onChange, String dialogTitle) {
        show(context, defaultValue, onChange, dialogTitle, false);
    }

    public static void show(Context context, String defaultValue, OnChange onChange, String dialogTitle, boolean emptyValueCheck) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.AppDialog);
        LayoutInflater inflater = LayoutInflater.from(context);
        View contentView = inflater.inflate(R.layout.simple_edit_dialog, null);

        KeyHelpers.fixEnterKey(contentView.findViewById(R.id.simple_edit_value));

        ((EditText) contentView.findViewById(R.id.simple_edit_value)).setText(defaultValue);

        // keep empty, will override below.
        // https://stackoverflow.com/a/15619098/5379584
        AlertDialog configDialog = builder
                .setTitle(dialogTitle)
                .setView(contentView)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> { })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> { })
                .create();

        configDialog.show();

        configDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener((view) -> {
            String newValue = ((EditText) contentView.findViewById(R.id.simple_edit_value)).getText().toString();

            if (emptyValueCheck && newValue.isEmpty()) {
                MessageHelpers.showMessage(context, R.string.enter_value);
                return;
            }

            configDialog.dismiss();
            onChange.onChange(newValue);
        });

        configDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener((view) -> configDialog.dismiss());
    }
}

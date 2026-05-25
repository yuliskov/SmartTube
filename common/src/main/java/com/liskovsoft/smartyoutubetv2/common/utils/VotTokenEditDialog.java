package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.liskovsoft.sharedutils.helpers.KeyHelpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;

public final class VotTokenEditDialog {
    public interface OnSave {
        void onSave(String token);
    }

    private VotTokenEditDialog() {
    }

    public static void show(Context context, String currentToken, OnSave onSave) {
        show(context, currentToken, null, onSave);
    }

    public static void show(Context context, String currentToken, String clipboardPrefill, OnSave onSave) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.AppDialog);
        View contentView = LayoutInflater.from(context).inflate(R.layout.vot_token_edit_dialog, null);

        TextView hint = contentView.findViewById(R.id.vot_token_hint);
        EditText editField = contentView.findViewById(R.id.vot_token_value);
        Button pasteBtn = contentView.findViewById(R.id.vot_token_paste);
        Button clearBtn = contentView.findViewById(R.id.vot_token_clear);

        hint.setText(R.string.vot_token_dialog_hint);
        KeyHelpers.fixShowKeyboard(editField);

        String initial = clipboardPrefill != null ? clipboardPrefill : currentToken;
        if (initial != null && !initial.isEmpty()) {
            editField.setText(initial);
            editField.setSelection(initial.length());
        }

        AlertDialog dialog = builder
                .setTitle(R.string.vot_token_dialog_title)
                .setView(contentView)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                .create();

        pasteBtn.setOnClickListener(v -> {
            String clip = readClipboard(context);
            if (clip == null || clip.isEmpty()) {
                MessageHelpers.showMessage(context, R.string.vot_clipboard_empty);
                return;
            }
            editField.setText(clip.trim());
            editField.setSelection(editField.getText().length());
        });

        clearBtn.setOnClickListener(v -> {
            editField.setText("");
            editField.requestFocus();
        });

        try {
            dialog.show();
        } catch (RuntimeException e) {
            MessageHelpers.showMessage(context, e.getMessage());
            return;
        }

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = editField.getText().toString().trim();
            onSave.onSave(value);
            dialog.dismiss();
        });
    }

    public static void pasteFromClipboard(Context context, OnSave onSave) {
        String clip = readClipboard(context);
        if (clip == null || clip.isEmpty()) {
            MessageHelpers.showMessage(context, R.string.vot_clipboard_empty);
            return;
        }
        show(context, "", clip.trim(), onSave);
    }

    private static String readClipboard(Context context) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) {
            return null;
        }
        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            return null;
        }
        CharSequence text = clip.getItemAt(0).getText();
        return text != null ? text.toString() : null;
    }
}

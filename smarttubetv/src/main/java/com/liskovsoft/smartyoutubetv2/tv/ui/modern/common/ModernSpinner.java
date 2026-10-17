package com.liskovsoft.smartyoutubetv2.tv.ui.modern.common;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Build;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import androidx.core.content.ContextCompat;

import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * The loading spinner of the modern interface: the same look and size on every screen.
 */
public final class ModernSpinner {
    private ModernSpinner() {
    }

    public static ProgressBar create(Context context) {
        ProgressBar spinner = new ProgressBar(context, null, android.R.attr.progressBarStyleLarge);
        apply(spinner);
        return spinner;
    }

    /**
     * Restyles an existing spinner (e.g. the one the player creates).
     */
    public static void apply(ProgressBar spinner) {
        Context context = spinner.getContext();
        // Platform drawable, in case the spinner was given another one
        spinner.setIndeterminateDrawable(new ProgressBar(context, null, android.R.attr.progressBarStyleLarge).getIndeterminateDrawable());

        if (Build.VERSION.SDK_INT >= 21) {
            spinner.setIndeterminateTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.modern_text_primary)));
        }

        int size = context.getResources().getDimensionPixelSize(R.dimen.modern_spinner_size);
        ViewGroup.LayoutParams params = spinner.getLayoutParams();

        if (params != null) {
            params.width = size;
            params.height = size;
            spinner.setLayoutParams(params);
        } else {
            spinner.setMinimumWidth(size);
            spinner.setMinimumHeight(size);
        }
    }
}

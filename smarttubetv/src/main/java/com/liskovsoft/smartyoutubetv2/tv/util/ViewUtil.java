package com.liskovsoft.smartyoutubetv2.tv.util;

import android.content.Context;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.os.Build;
import android.os.Build.VERSION;
import android.text.BidiFormatter;
import android.text.Layout;
import android.text.TextUtils.TruncateAt;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.FocusHighlight;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.RowPresenter;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.adapter.VideoGroupObjectAdapter;
import com.liskovsoft.smartyoutubetv2.tv.ui.widgets.marqueetextview.MarqueeTextView;

public class ViewUtil {
    /**
     * Focused card zoom factor
     */
    public static final int FOCUS_ZOOM_FACTOR = FocusHighlight.ZOOM_FACTOR_SMALL;
    /**
     * Dim focused card?
     */
    public static final boolean USE_FOCUS_DIMMER = false;
    /**
     * Dim other rows in {@link RowPresenter}
     */
    public static final boolean SELECT_EFFECT_ENABLED = false;
    /**
     * Scroll continue threshold
     */
    public static final int GRID_SCROLL_CONTINUE_NUM = 10;
    public static final int ROW_SCROLL_CONTINUE_NUM = 4;

    /**
     * Checks whether text is truncated (e.g. has ... at the end)
     */
    public static boolean isTruncated(TextView textView) {
        Layout layout = textView.getLayout();
        if (layout != null) {
            int lines = layout.getLineCount();
            if (lines > 0) {
                int ellipsisCount = layout.getEllipsisCount(lines - 1);
                if (ellipsisCount > 0) {
                    return true;
                }
            }
        }

        return false;
    }

    public static void disableMarquee(TextView... textViews) {
        if (textViews == null || textViews.length == 0) {
            return;
        }

        for (TextView textView : textViews) {
            textView.setEllipsize(TruncateAt.END);
            textView.setHorizontallyScrolling(false);

            // Fix: text disappear on rtl languages
            if (VERSION.SDK_INT > 17 && BidiFormatter.getInstance().isRtlContext()) {
                textView.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            }
        }
    }

    /**
     * <a href="https://stackoverflow.com/questions/3332924/textview-marquee-not-working">More info</a>
     */
    public static void enableMarquee(TextView... textViews) {
        if (textViews == null || textViews.length == 0) {
            return;
        }

        //if (VERSION.SDK_INT > 17) {
        //    if (BidiFormatter.getInstance().isRtlContext()) {
        //        // TODO: fix marquee on rtl languages
        //        // TODO: text disappear on rtl languages
        //        return;
        //    }
        //}

        for (TextView textView : textViews) {
            if (ViewUtil.isTruncated(textView)) { // multiline scroll fix
                textView.setEllipsize(TruncateAt.MARQUEE);
                textView.setMarqueeRepeatLimit(-1);
                textView.setHorizontallyScrolling(true);

                // App dialog title fix.
                textView.setSelected(true);

                // Fix: right scrolling on rtl languages
                // Fix: text disappear on rtl languages
                if (VERSION.SDK_INT > 17 && BidiFormatter.getInstance().isRtlContext()) {
                    textView.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
                    textView.setTextDirection(TextView.TEXT_DIRECTION_RTL);
                    textView.setGravity(Gravity.START);
                }
            }
        }
    }

    public static void setTextScrollSpeed(TextView textView, float speed) {
        if (textView instanceof MarqueeTextView) {
            ((MarqueeTextView) textView).setMarqueeSpeedFactor(speed);
        }
    }

    public static void enableView(View view, boolean enabled) {
        if (view != null) {
            view.setVisibility(enabled ? View.VISIBLE : View.GONE);
        }
    }

    public static void setDimensions(View view, int width, int height) {
        if (view != null) {
            ViewGroup.LayoutParams lp = view.getLayoutParams();

            if (lp != null) {
                if (width > 0) {
                    lp.width = width;
                }
                if (height > 0) {
                    lp.height = height;
                }
                view.setLayoutParams(lp);
            }
        }
    }

    public static boolean isListRowEmpty(Object obj) {
        if (obj instanceof ListRow) {
            ListRow row = (ListRow) obj;
            VideoGroupObjectAdapter adapter = (VideoGroupObjectAdapter) row.getAdapter();
            return adapter == null || adapter.size() == 0;
        }

        return true;
    }

    public static RequestOptions glideOptions() {
        //return new RequestOptions()
        //        .skipMemoryCache(true);

        return new RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.NONE) // ensure start animation from beginning
                .skipMemoryCache(true); // ensure start animation from beginning
    }

    public static void enableTransparentDialog(Context context, View rootView) {
        if (context == null || rootView == null || VERSION.SDK_INT <= 19) {
            return;
        }

        View container = rootView.findViewById(R.id.settings_preference_fragment_container);
        View mainFrame = rootView.findViewById(R.id.main_frame);
        View title = rootView.findViewById(R.id.decor_title_container);
        int transparent = ContextCompat.getColor(context, R.color.transparent);
        int semiTransparent = ContextCompat.getColor(context, R.color.semi_grey);

        if (container instanceof FrameLayout) {
            // ViewOutlineProvider: NoClassDefFoundError on API 19
            container.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        }
        if (mainFrame instanceof LinearLayout) {
            mainFrame.setBackgroundColor(semiTransparent);
        }
        if (title instanceof FrameLayout) {
            title.setBackgroundColor(transparent);
        }
        // Can't set bg of individual items here because ones isn't added yet.
    }

    public static void makeMonochrome(ImageView iconView) {
        ColorMatrix colorMatrix = new ColorMatrix();
        colorMatrix.setSaturation(0);
        ColorMatrixColorFilter filter = new ColorMatrixColorFilter(colorMatrix);
        iconView.setColorFilter(filter);
    }
}

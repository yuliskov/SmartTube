package com.liskovsoft.smartyoutubetv2.tv.util;

import android.content.Context;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.os.Build.VERSION;
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
import androidx.leanback.widget.VerticalGridView;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.liskovsoft.sharedutils.helpers.Helpers;
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
    public static final boolean FOCUS_DIMMER_ENABLED = false;
    /**
     * Dim other rows in {@link RowPresenter}
     */
    public static final boolean ROW_SELECT_EFFECT_ENABLED = false;
    /**
     * Scroll continue threshold
     */
    public static final int GRID_SCROLL_CONTINUE_NUM = 10;
    public static final int ROW_SCROLL_CONTINUE_NUM = 4;
    public static final boolean ROUNDED_CORNERS_ENABLED = true;

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
        if (VERSION.SDK_INT <= 19 || textViews == null || textViews.length == 0) { // Android 4: Broken grid layout fix
            return;
        }

        for (TextView textView : textViews) {
            textView.setEllipsize(TruncateAt.END);
            // Line below cause broken grid layout on Android 4 and older
            textView.setHorizontallyScrolling(false);

            applyMarqueeRtlParams(textView, false);
        }
    }

    /**
     * <a href="https://stackoverflow.com/questions/3332924/textview-marquee-not-working">More info</a>
     */
    public static void enableMarquee(TextView... textViews) {
        if (VERSION.SDK_INT <= 19 || textViews == null || textViews.length == 0) { // Android 4: Broken grid layout fix
            return;
        }

        for (TextView textView : textViews) {
            if (ViewUtil.isTruncated(textView)) { // multiline scroll fix
                textView.setEllipsize(TruncateAt.MARQUEE);
                textView.setMarqueeRepeatLimit(-1);
                textView.setHorizontallyScrolling(true);

                // App dialog title fix.
                textView.setSelected(true);

                applyMarqueeRtlParams(textView, true);
            }
        }
    }

    public static void applyMarqueeRtlParams(TextView textView, boolean scroll) {
        //if (VERSION.SDK_INT <= 17) {
        //    return;
        //}

        //if (!BidiFormatter.getInstance().isRtlContext()) {
        //    return;
        //}

        if (!Helpers.isTextRTL(textView.getText())) {
            // TextView may be reused from rtl context. Do reset.
            textView.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
            textView.setTextDirection(TextView.TEXT_DIRECTION_FIRST_STRONG);
            textView.setGravity(Gravity.TOP | Gravity.START);
            return;
        }

        if (scroll) {
            // Fix: right scrolling on rtl languages
            // Fix: text disappear on rtl languages
            textView.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
            textView.setTextDirection(TextView.TEXT_DIRECTION_RTL);
            textView.setGravity(Gravity.START);
        } else {
            // Fix: text disappear on rtl languages
            textView.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        }
    }

    public static void setTextScrollSpeed(TextView textView, float speed) {
        if (VERSION.SDK_INT <= 19) { // Android 4: Broken grid layout fix
            return;
        }

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
        return new RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.NONE) // ensure start animation from beginning
                .skipMemoryCache(true); // ensure start animation from beginning
    }

    public static void enableTransparentDialog(Context context, View rootView) {
        if (context == null || rootView == null || VERSION.SDK_INT <= 19) {
            return;
        }

        // Usually null. Present only on parent fragment.
        View mainContainer = rootView.findViewById(R.id.settings_preference_fragment_container);
        View mainFrame = rootView.findViewById(R.id.main_frame);
        View itemsContainer = rootView.findViewById(R.id.list);
        View title = rootView.findViewById(R.id.decor_title_container);
        int transparent = ContextCompat.getColor(context, R.color.transparent);
        int semiTransparent = ContextCompat.getColor(context, R.color.semi_grey);

        // Disable shadow outline on parent fragment
        if (mainContainer instanceof FrameLayout && VERSION.SDK_INT >= 21) {
            // ViewOutlineProvider: NoClassDefFoundError on API 19
            mainContainer.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        }
        if (mainFrame instanceof LinearLayout) {
            mainFrame.setBackgroundColor(transparent);
        }
        if (itemsContainer instanceof VerticalGridView) {
            // Set background for individual buttons in the list.
            // This is the only way to do this because items haven't been added yet to the container.
            ((VerticalGridView) itemsContainer).setOnChildLaidOutListener(
                    (parent, view, position, id) -> view.setBackgroundResource(R.drawable.transparent_dialog_item_bg)
            );
        }
        if (title instanceof FrameLayout) {
            title.setBackgroundColor(transparent);
            title.setVisibility(View.GONE);
        }
    }

    public static void enableLeftDialog(Context context, View rootView) {
        if (context == null || rootView == null || VERSION.SDK_INT <= 19) {
            return;
        }

        // Usually null. Present only on parent fragment.
        View mainContainer = rootView.findViewById(R.id.settings_preference_fragment_container);

        if (mainContainer instanceof FrameLayout) {
            ((FrameLayout.LayoutParams) mainContainer.getLayoutParams()).gravity = Gravity.START;
        }
    }

    public static void makeMonochrome(ImageView iconView) {
        ColorMatrix colorMatrix = new ColorMatrix();
        colorMatrix.setSaturation(0);
        ColorMatrixColorFilter filter = new ColorMatrixColorFilter(colorMatrix);
        iconView.setColorFilter(filter);
    }
}

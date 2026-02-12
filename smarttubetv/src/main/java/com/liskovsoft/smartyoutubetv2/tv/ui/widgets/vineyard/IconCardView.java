package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.vineyard;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.leanback.widget.BaseCardView;
import com.liskovsoft.smartyoutubetv2.tv.R;

public class IconCardView extends BaseCardView {
    RelativeLayout mLayout;
    ImageView mIcon;
    TextView mTitle;
    TextView mValue;

    public IconCardView(Context context, int styleResId) {
        super(new ContextThemeWrapper(context, styleResId), null, 0);
        buildCardView(styleResId);

    }

    public IconCardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(getStyledContext(context, attrs, defStyleAttr), attrs, defStyleAttr);
        buildCardView(getImageCardViewStyle(context, attrs, defStyleAttr));
    }

    private void buildCardView(int styleResId) {
        // Make sure the ImageCardView is focusable.
        setFocusable(true);
        setFocusableInTouchMode(true);
        //setCardType(CARD_TYPE_INFO_UNDER);
        setCardType(CARD_TYPE_MAIN_ONLY);
        setBackgroundResource(R.color.primary_dark);

        LayoutInflater inflater = LayoutInflater.from(getContext());
        View view = inflater.inflate(R.layout.view_options_item, this);

        mLayout = view.findViewById(R.id.layout_option_card);
        mIcon = view.findViewById(R.id.image_option);
        mTitle = view.findViewById(R.id.text_option_title);
        mValue = view.findViewById(R.id.text_option_value);

        TypedArray cardAttrs =
                getContext().obtainStyledAttributes(
                        styleResId, R.styleable.lbImageCardView);
        cardAttrs.recycle();
    }

    public void setMainImageDimensions(int width, int height) {
        ViewGroup.LayoutParams lp = mLayout.getLayoutParams();
        lp.width = width;
        lp.height = height;
        mLayout.setLayoutParams(lp);
    }

    public void setOptionIcon(Drawable drawable) {
        mIcon.setImageDrawable(drawable);
    }

    public void setOptionTitleText(String titleText) {
        mTitle.setText(titleText);
    }

    public void setOptionValueText(String valueText) {
        mValue.setText(valueText);
    }

    private static Context getStyledContext(Context context, AttributeSet attrs, int defStyleAttr) {
        int style = getImageCardViewStyle(context, attrs, defStyleAttr);
        return new ContextThemeWrapper(context, style);
    }

    private static int getImageCardViewStyle(Context context, AttributeSet attrs, int defStyleAttr) {
        // Read style attribute defined in XML layout.
        int style = null == attrs ? 0 : attrs.getStyleAttribute();
        if (0 == style) {
            // Not found? Read global ImageCardView style from Theme attribute.
            TypedArray styledAttrs =
                    context.obtainStyledAttributes(
                            R.styleable.LeanbackTheme);
            style = styledAttrs.getResourceId(
                    R.styleable.LeanbackTheme_imageCardViewStyle, 0);
            styledAttrs.recycle();
        }
        return style;
    }

    public IconCardView(Context context) {
        this(context, null);
    }

    public IconCardView(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.imageCardViewStyle);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

}

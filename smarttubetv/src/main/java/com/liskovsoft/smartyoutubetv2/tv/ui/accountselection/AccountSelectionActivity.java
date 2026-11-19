package com.liskovsoft.smartyoutubetv2.tv.ui.accountselection;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.view.ViewCompat;

import com.bumptech.glide.Glide;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AccountSelectionPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.AccountSelectionView;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;

import java.util.List;

/**
 * Full screen "Who's watching?" account picker.
 */
public class AccountSelectionActivity extends LeanbackActivity implements AccountSelectionView {
    private static final float FOCUSED_SCALE = 1.1f;
    private static final long FOCUS_ANIMATION_MS = 150;
    private AccountSelectionPresenter mPresenter;
    private LinearLayout mAccountList;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_selection);

        ViewCompat.setBackground(findViewById(R.id.account_selection_root), createBackground());

        ImageView logo = findViewById(R.id.account_selection_logo);
        if (logo.getDrawable() == null) {
            logo.setVisibility(View.GONE);
        }

        mAccountList = findViewById(R.id.account_selection_list);
        findViewById(R.id.account_selection_signed_out).setOnClickListener(v -> mPresenter.onAccountClicked(null));

        mPresenter = AccountSelectionPresenter.instance(this);
        mPresenter.setView(this);
        mPresenter.onViewInitialized();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mPresenter.onViewResumed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mPresenter.onViewDestroyed();
    }

    @Override
    public void update(List<Account> accounts) {
        mAccountList.removeAllViews();

        View focusTarget = null;

        if (accounts != null) {
            for (Account account : accounts) {
                View tile = createAccountTile(account);
                mAccountList.addView(tile);

                if (account.isSelected()) {
                    focusTarget = tile;
                }
            }
        }

        mAccountList.addView(createAddAccountTile());

        if (focusTarget == null) {
            focusTarget = mAccountList.getChildAt(0);
        }

        focusTarget.requestFocus();
    }

    @Override
    public void close() {
        finish();
    }

    private View createAccountTile(Account account) {
        String name = account.getName() != null ? account.getName() : account.getEmail();
        String subtitle = account.getName() != null ? account.getEmail() : null;

        View tile = createTile(name, subtitle);
        tile.setOnClickListener(v -> mPresenter.onAccountClicked(account));

        String avatarUrl = account.getAvatarImageUrl();

        if (avatarUrl != null) {
            ImageView avatar = tile.findViewById(R.id.account_avatar);

            try {
                Glide.with(this)
                        .load(avatarUrl)
                        .circleCrop()
                        .into(avatar);
            } catch (ExceptionInInitializerError e) {
                // Glide Kivi error
                e.printStackTrace();
            }
        }

        return tile;
    }

    private View createAddAccountTile() {
        View tile = createTile(getString(R.string.account_selection_add_account), null);
        tile.setOnClickListener(v -> mPresenter.onAddAccountClicked());

        ImageView avatar = tile.findViewById(R.id.account_avatar);
        avatar.setImageDrawable(null);
        avatar.setBackgroundResource(R.drawable.account_selection_add_bg);
        tile.findViewById(R.id.account_add_icon).setVisibility(View.VISIBLE);

        return tile;
    }

    private View createTile(CharSequence title, CharSequence subtitle) {
        View tile = getLayoutInflater().inflate(R.layout.account_selection_item, mAccountList, false);

        ((TextView) tile.findViewById(R.id.account_name)).setText(title);

        TextView subtitleView = tile.findViewById(R.id.account_subtitle);
        subtitleView.setText(subtitle);
        // Keep the space to align names of all tiles
        subtitleView.setVisibility(TextUtils.isEmpty(subtitle) ? View.INVISIBLE : View.VISIBLE);

        tile.setOnFocusChangeListener((v, hasFocus) -> {
            float scale = hasFocus ? FOCUSED_SCALE : 1f;
            v.animate().scaleX(scale).scaleY(scale).setDuration(FOCUS_ANIMATION_MS).start();
        });

        return tile;
    }

    /**
     * Dark background with soft glows tinted by the current color scheme.
     */
    private Drawable createBackground() {
        int brandColor = resolveColor(R.attr.brandColor, Color.BLACK);
        int accentColor = resolveColor(R.attr.brandAccentColor, Color.DKGRAY);
        int screenWidth = getResources().getDisplayMetrics().widthPixels;

        GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {Color.rgb(0x16, 0x16, 0x16), Color.rgb(0x08, 0x08, 0x08)});

        GradientDrawable topGlow = createGlow(brandColor, 0x66, 0.1f, 0f, screenWidth * 0.6f);
        GradientDrawable bottomGlow = createGlow(accentColor, 0x99, 0.95f, 1f, screenWidth * 0.7f);

        return new LayerDrawable(new Drawable[] {base, topGlow, bottomGlow});
    }

    private static GradientDrawable createGlow(int color, int alpha, float centerX, float centerY, float radius) {
        int startColor = (color & 0x00FFFFFF) | (alpha << 24);
        GradientDrawable glow = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {startColor, Color.TRANSPARENT});
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setGradientCenter(centerX, centerY);
        glow.setGradientRadius(radius);
        return glow;
    }

    private int resolveColor(int attr, int defaultColor) {
        TypedValue value = new TypedValue();

        if (getTheme().resolveAttribute(attr, value, true)
                && value.type >= TypedValue.TYPE_FIRST_COLOR_INT && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return value.data;
        }

        return defaultColor;
    }
}

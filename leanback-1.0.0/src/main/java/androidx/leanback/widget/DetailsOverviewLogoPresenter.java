package androidx.leanback.widget;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.leanback.R;

/**
 * Presenter that responsible to create a ImageView and bind to DetailsOverviewRow. The default
 * implementation uses {@link DetailsOverviewRow#getImageDrawable()} and binds to {@link ImageView}.
 * <p>
 * Default implementation assumes no scaleType on ImageView and uses intrinsic width and height of
 * {@link DetailsOverviewRow#getImageDrawable()} to initialize ImageView's layout params.  To
 * specify a fixed size and/or specify a scapeType, subclass should change ImageView's layout params
 * and scaleType in {@link #onCreateView(ViewGroup)}.
 * <p>
 * Subclass may override and has its own image view. Subclass may also download image from URL
 * instead of using {@link DetailsOverviewRow#getImageDrawable()}. It's subclass's responsibility to
 * call {@link FullWidthDetailsOverviewRowPresenter#notifyOnBindLogo(FullWidthDetailsOverviewRowPresenter.ViewHolder)}
 * whenever {@link #isBoundToImage(ViewHolder, DetailsOverviewRow)} turned to true so that activity
 * transition can be started.
 */
public class DetailsOverviewLogoPresenter extends Presenter {

    /**
     * ViewHolder for Logo view of DetailsOverviewRow.
     */
    public static class ViewHolder extends Presenter.ViewHolder {

        protected FullWidthDetailsOverviewRowPresenter mParentPresenter;
        protected FullWidthDetailsOverviewRowPresenter.ViewHolder mParentViewHolder;
        private boolean mSizeFromDrawableIntrinsic;

        public ViewHolder(View view) {
            super(view);
        }

        public FullWidthDetailsOverviewRowPresenter getParentPresenter() {
            return mParentPresenter;
        }

        public FullWidthDetailsOverviewRowPresenter.ViewHolder getParentViewHolder() {
            return mParentViewHolder;
        }

        /**
         * @return True if layout size of ImageView should be changed to intrinsic size of Drawable,
         *         false otherwise. Used by
         *         {@link DetailsOverviewLogoPresenter#onBindViewHolder(Presenter.ViewHolder, Object)}
         *         .
         *
         * @see DetailsOverviewLogoPresenter#onCreateView(ViewGroup)
         * @see DetailsOverviewLogoPresenter#onBindViewHolder(Presenter.ViewHolder, Object)
         */
        public boolean isSizeFromDrawableIntrinsic() {
            return mSizeFromDrawableIntrinsic;
        }

        /**
         * Change if the ImageView layout size should be synchronized to Drawable intrinsic size.
         * Used by
         * {@link DetailsOverviewLogoPresenter#onBindViewHolder(Presenter.ViewHolder, Object)}.
         *
         * @param sizeFromDrawableIntrinsic True if layout size of ImageView should be changed to
         *        intrinsic size of Drawable, false otherwise.
         *
         * @see DetailsOverviewLogoPresenter#onCreateView(ViewGroup)
         * @see DetailsOverviewLogoPresenter#onBindViewHolder(Presenter.ViewHolder, Object)
         */
        public void setSizeFromDrawableIntrinsic(boolean sizeFromDrawableIntrinsic) {
            mSizeFromDrawableIntrinsic = sizeFromDrawableIntrinsic;
        }
    }

    /**
     * Create a View for the Logo, default implementation loads from
     * {@link R.layout#lb_fullwidth_details_overview_logo}. Subclass may override this method to use
     * a fixed layout size and change ImageView scaleType. If the layout params is WRAP_CONTENT for
     * both width and size, the ViewHolder would be using intrinsic size of Drawable in
     * {@link #onBindViewHolder(Presenter.ViewHolder, Object)}.
     *
     * @param parent Parent view.
     * @return View created for the logo.
     */
    public View onCreateView(ViewGroup parent) {
        return LayoutInflater.from(parent.getContext())
                .inflate(R.layout.lb_fullwidth_details_overview_logo, parent, false);
    }

    @Override
    public Presenter.ViewHolder onCreateViewHolder(ViewGroup parent) {
        View view = onCreateView(parent);
        ViewHolder vh = new ViewHolder(view);
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        vh.setSizeFromDrawableIntrinsic(lp.width == ViewGroup.LayoutParams.WRAP_CONTENT
                && lp.height == ViewGroup.LayoutParams.WRAP_CONTENT);
        return vh;
    }

    /**
     * Called from {@link FullWidthDetailsOverviewRowPresenter} to setup FullWidthDetailsOverviewRowPresenter
     * and FullWidthDetailsOverviewRowPresenter.ViewHolder that hosts the logo.
     * @param viewHolder
     * @param parentViewHolder
     * @param parentPresenter
     */
    public void setContext(ViewHolder viewHolder,
            FullWidthDetailsOverviewRowPresenter.ViewHolder parentViewHolder,
            FullWidthDetailsOverviewRowPresenter parentPresenter) {
        viewHolder.mParentViewHolder = parentViewHolder;
        viewHolder.mParentPresenter = parentPresenter;
    }

    /**
     * Returns true if the logo view is bound to image. Subclass may override. The default
     * implementation returns true when {@link DetailsOverviewRow#getImageDrawable()} is not null.
     * If subclass of DetailsOverviewLogoPresenter manages its own image drawable, it should
     * override this function to report status correctly and invoke
     * {@link FullWidthDetailsOverviewRowPresenter#notifyOnBindLogo(FullWidthDetailsOverviewRowPresenter.ViewHolder)}
     * when image view is bound to the drawable.
     */
    public boolean isBoundToImage(ViewHolder viewHolder, DetailsOverviewRow row) {
        return row != null && row.getImageDrawable() != null;
    }

    /**
     * Bind logo View to drawable of DetailsOverviewRow and call notifyOnBindLogo().  The
     * default implementation assumes the Logo View is an ImageView and change layout size to
     * intrinsic size of ImageDrawable if {@link ViewHolder#isSizeFromDrawableIntrinsic()} is true.
     * @param viewHolder ViewHolder to bind.
     * @param item DetailsOverviewRow object to bind.
     */
    @Override
    public void onBindViewHolder(Presenter.ViewHolder viewHolder, Object item) {
        DetailsOverviewRow row = (DetailsOverviewRow) item;
        ImageView imageView = ((ImageView) viewHolder.view);
        imageView.setImageDrawable(row.getImageDrawable());
        if (isBoundToImage((ViewHolder) viewHolder, row)) {
            ViewHolder vh = (ViewHolder) viewHolder;
            if (vh.isSizeFromDrawableIntrinsic()) {
                ViewGroup.LayoutParams lp = imageView.getLayoutParams();
                lp.width = row.getImageDrawable().getIntrinsicWidth();
                lp.height = row.getImageDrawable().getIntrinsicHeight();
                if (imageView.getMaxWidth() > 0 || imageView.getMaxHeight() > 0) {
                    float maxScaleWidth = 1f;
                    if (imageView.getMaxWidth() > 0) {
                        if (lp.width > imageView.getMaxWidth()) {
                            maxScaleWidth = imageView.getMaxWidth() / (float) lp.width;
                        }
                    }
                    float maxScaleHeight = 1f;
                    if (imageView.getMaxHeight() > 0) {
                        if (lp.height > imageView.getMaxHeight()) {
                            maxScaleHeight = imageView.getMaxHeight() / (float) lp.height;
                        }
                    }
                    float scale = Math.min(maxScaleWidth, maxScaleHeight);
                    lp.width = (int) (lp.width * scale);
                    lp.height = (int) (lp.height * scale);
                }
                imageView.setLayoutParams(lp);
            }
            vh.mParentPresenter.notifyOnBindLogo(vh.mParentViewHolder);
        }
    }

    @Override
    public void onUnbindViewHolder(Presenter.ViewHolder viewHolder) {
    }

}

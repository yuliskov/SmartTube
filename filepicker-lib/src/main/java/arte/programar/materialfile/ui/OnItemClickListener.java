package arte.programar.materialfile.ui;

import android.os.SystemClock;
import android.view.View;

interface OnItemClickListener {
    void onItemClick(View view, int position);

    abstract class ThrottleClickListener implements OnItemClickListener {

        private static final long MIN_CLICK_INTERVAL = 600;

        private long mLastClickTime;

        abstract void onItemClickThrottled(View view, int position);

        @Override
        public void onItemClick(View view, int position) {
            final long currentClickTime = SystemClock.uptimeMillis();
            final long elapsedTime = currentClickTime - mLastClickTime;

            mLastClickTime = currentClickTime;

            if (elapsedTime <= MIN_CLICK_INTERVAL) {
                return;
            }

            onItemClickThrottled(view, position);
        }
    }
}

package com.r0adkll.slidr.widget;


import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import androidx.core.view.ViewCompat;
import androidx.core.view.ViewGroupCompat;

import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.r0adkll.slidr.model.SlidrConfig;
import com.r0adkll.slidr.util.ViewDragHelper;
import com.r0adkll.slidr.model.SlidrInterface;


public class SliderPanel extends FrameLayout {

    private static final int MIN_FLING_VELOCITY = 400; // dips per second

    private int screenWidth;
    private int screenHeight;

    private View decorView;
    private ViewDragHelper dragHelper;
    private OnPanelSlideListener listener;
    private Paint scrimPaint;
    private ScrimRenderer scrimRenderer;

    private boolean isLocked = false;
    private boolean isEdgeTouched = false;
    private int edgePosition;

    private SlidrConfig config;


	public SliderPanel(Context context) {
		super(context);
	}


    public SliderPanel(Context context, View decorView, SlidrConfig config){
        super(context);
        this.decorView = decorView;
		this.config = (config == null ? new SlidrConfig.Builder().build() : config);
        init();
    }


    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        boolean interceptForDrag;

        if(isLocked){
            return false;
        }

        if(config.isEdgeOnly()) {
            isEdgeTouched = canDragFromEdge(ev);
        }

        // Fix for pull request #13 and issue #12
        try {
            interceptForDrag = dragHelper.shouldInterceptTouchEvent(ev);
        } catch (Exception e) {
            interceptForDrag = false;
        }

        return interceptForDrag && !isLocked;
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if(isLocked){
            return false;
        }

        try {
            dragHelper.processTouchEvent(event);
        }catch (IllegalArgumentException e){
            return false;
        }

        return true;
    }


    @Override
    public void computeScroll() {
        super.computeScroll();
        if(dragHelper.continueSettling(true)){
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }


    @Override
    protected void onDraw(Canvas canvas) {
        scrimRenderer.render(canvas, config.getPosition(), scrimPaint);
    }


    /**
     * Set the panel slide listener that gets called based on slider changes
     * @param listener callback implementation
     */
    public void setOnPanelSlideListener(OnPanelSlideListener listener){
        this.listener = listener;
    }


    /**
     * Get the default {@link SlidrInterface} from which to control the panel with after attachment
     */
    public SlidrInterface getDefaultInterface() {
        return defaultSlidrInterface;
    }


    private final SlidrInterface defaultSlidrInterface = new SlidrInterface() {


        @Override
        public void lock() {
            SliderPanel.this.lock();
        }


        @Override
        public void unlock() {
            SliderPanel.this.unlock();
        }
    };


    /**
     * The drag helper callback interface for the Left position
     */
    private final ViewDragHelper.Callback leftCallback = new ViewDragHelper.Callback() {

        @Override
        public boolean tryCaptureView(View child, int pointerId) {
            boolean edgeCase = !config.isEdgeOnly() || dragHelper.isEdgeTouched(edgePosition, pointerId);
            return child.getId() == decorView.getId() && edgeCase;
        }

        @Override
        public int clampViewPositionHorizontal(View child, int left, int dx) {
            return clamp(left, 0, screenWidth);
        }

        @Override
        public int getViewHorizontalDragRange(View child) {
            return screenWidth;
        }

        @Override
        public void onViewReleased(View releasedChild, float xvel, float yvel) {
            super.onViewReleased(releasedChild, xvel, yvel);

            int left = releasedChild.getLeft();
            int settleLeft = 0;
            int leftThreshold = (int) (getWidth() * config.getDistanceThreshold());
            boolean isVerticalSwiping = Math.abs(yvel) > config.getVelocityThreshold();

            if(xvel > 0){

                if(Math.abs(xvel) > config.getVelocityThreshold() && !isVerticalSwiping){
                    settleLeft = screenWidth;
                }else if(left > leftThreshold){
                    settleLeft = screenWidth;
                }

            }else if(xvel == 0){
                if(left > leftThreshold){
                    settleLeft = screenWidth;
                }
            }

            dragHelper.settleCapturedViewAt(settleLeft, releasedChild.getTop());
            invalidate();
        }

        @Override
        public void onViewPositionChanged(View changedView, int left, int top, int dx, int dy) {
            super.onViewPositionChanged(changedView, left, top, dx, dy);
            float percent = 1f - ((float)left / (float) screenWidth);

            if(listener != null) listener.onSlideChange(percent);

            // Update the dimmer alpha
            applyScrim(percent);
        }

        @Override
        public void onViewDragStateChanged(int state) {
            super.onViewDragStateChanged(state);
            if(listener != null) listener.onStateChanged(state);
            switch (state){
                case ViewDragHelper.STATE_IDLE:
                    if(decorView.getLeft() == 0){
                        // State Open
                        if(listener != null) listener.onOpened();
                    }else{
                        // State Closed
                        if(listener != null) listener.onClosed();
                    }
                    break;
                case ViewDragHelper.STATE_DRAGGING:

                    break;
                case ViewDragHelper.STATE_SETTLING:

                    break;
            }
        }

    };


    /**
     * The drag helper callbacks for dragging the slidr attachment from the right of the screen
     */
    private final ViewDragHelper.Callback rightCallback = new ViewDragHelper.Callback() {
        @Override
        public boolean tryCaptureView(View child, int pointerId) {
            boolean edgeCase = !config.isEdgeOnly() || dragHelper.isEdgeTouched(edgePosition, pointerId);
            return child.getId() == decorView.getId() && edgeCase;
        }

        @Override
        public int clampViewPositionHorizontal(View child, int left, int dx) {
            return clamp(left, -screenWidth, 0);
        }

        @Override
        public int getViewHorizontalDragRange(View child) {
            return screenWidth;
        }

        @Override
        public void onViewReleased(View releasedChild, float xvel, float yvel) {
            super.onViewReleased(releasedChild, xvel, yvel);

            int left = releasedChild.getLeft();
            int settleLeft = 0;
            int leftThreshold = (int) (getWidth() * config.getDistanceThreshold());
            boolean isVerticalSwiping = Math.abs(yvel) > config.getVelocityThreshold();

            if(xvel < 0){

                if(Math.abs(xvel) > config.getVelocityThreshold() && !isVerticalSwiping){
                    settleLeft = -screenWidth;
                }else if(left < -leftThreshold){
                    settleLeft = -screenWidth;
                }

            }else if(xvel == 0){
                if(left < -leftThreshold){
                    settleLeft = -screenWidth;
                }
            }

            dragHelper.settleCapturedViewAt(settleLeft, releasedChild.getTop());
            invalidate();
        }

        @Override
        public void onViewPositionChanged(View changedView, int left, int top, int dx, int dy) {
            super.onViewPositionChanged(changedView, left, top, dx, dy);
            float percent = 1f - ((float)Math.abs(left) / (float) screenWidth);

            if(listener != null) listener.onSlideChange(percent);

            // Update the dimmer alpha
            applyScrim(percent);
        }

        @Override
        public void onViewDragStateChanged(int state) {
            super.onViewDragStateChanged(state);
            if(listener != null) listener.onStateChanged(state);
            switch (state){
                case ViewDragHelper.STATE_IDLE:
                    if(decorView.getLeft() == 0){
                        // State Open
                        if(listener != null) listener.onOpened();
                    }else{
                        // State Closed
                        if(listener != null) listener.onClosed();
                    }
                    break;
                case ViewDragHelper.STATE_DRAGGING:

                    break;
                case ViewDragHelper.STATE_SETTLING:

                    break;
            }
        }
    };


    /**
     * The drag helper callbacks for dragging the slidr attachment from the top of the screen
     */
    private final ViewDragHelper.Callback topCallback = new ViewDragHelper.Callback() {
        @Override
        public boolean tryCaptureView(View child, int pointerId) {
            return child.getId() == decorView.getId() && (!config.isEdgeOnly() || isEdgeTouched);
        }

        @Override
        public int clampViewPositionVertical(View child, int top, int dy) {
            return clamp(top, 0, screenHeight);
        }

        @Override
        public int getViewVerticalDragRange(View child) {
            return screenHeight;
        }

        @Override
        public void onViewReleased(View releasedChild, float xvel, float yvel) {
            super.onViewReleased(releasedChild, xvel, yvel);

            int top = releasedChild.getTop();
            int settleTop = 0;
            int topThreshold = (int) (getHeight() * config.getDistanceThreshold());
            boolean isSideSwiping = Math.abs(xvel) > config.getVelocityThreshold();

            if(yvel > 0){
                if(Math.abs(yvel) > config.getVelocityThreshold() && !isSideSwiping){
                    settleTop = screenHeight;
                }else if(top > topThreshold){
                    settleTop = screenHeight;
                }
            }else if(yvel == 0){
                if(top > topThreshold){
                    settleTop = screenHeight;
                }
            }

            dragHelper.settleCapturedViewAt(releasedChild.getLeft(), settleTop);
            invalidate();
        }

        @Override
        public void onViewPositionChanged(View changedView, int left, int top, int dx, int dy) {
            super.onViewPositionChanged(changedView, left, top, dx, dy);
            float percent = 1f - ((float)Math.abs(top) / (float) screenHeight);

            if(listener != null) listener.onSlideChange(percent);

            // Update the dimmer alpha
            applyScrim(percent);
        }

        @Override
        public void onViewDragStateChanged(int state) {
            super.onViewDragStateChanged(state);
            if(listener != null) listener.onStateChanged(state);
            switch (state){
                case ViewDragHelper.STATE_IDLE:
                    if(decorView.getTop() == 0){
                        // State Open
                        if(listener != null) listener.onOpened();
                    }else{
                        // State Closed
                        if(listener != null) listener.onClosed();
                    }
                    break;
                case ViewDragHelper.STATE_DRAGGING:

                    break;
                case ViewDragHelper.STATE_SETTLING:

                    break;
            }
        }
    };


    /**
     * The drag helper callbacks for dragging the slidr attachment from the bottom of hte screen
     */
    private final ViewDragHelper.Callback bottomCallback = new ViewDragHelper.Callback() {
        @Override
        public boolean tryCaptureView(View child, int pointerId) {
            return child.getId() == decorView.getId() && (!config.isEdgeOnly() || isEdgeTouched);
        }

        @Override
        public int clampViewPositionVertical(View child, int top, int dy) {
            return clamp(top, -screenHeight, 0);
        }

        @Override
        public int getViewVerticalDragRange(View child) {
            return screenHeight;
        }

        @Override
        public void onViewReleased(View releasedChild, float xvel, float yvel) {
            super.onViewReleased(releasedChild, xvel, yvel);

            int top = releasedChild.getTop();
            int settleTop = 0;
            int topThreshold = (int) (getHeight() * config.getDistanceThreshold());
            boolean isSideSwiping = Math.abs(xvel) > config.getVelocityThreshold();

            if(yvel < 0){
                if(Math.abs(yvel) > config.getVelocityThreshold() && !isSideSwiping){
                    settleTop = -screenHeight;
                }else if(top < -topThreshold){
                    settleTop = -screenHeight;
                }
            }else if(yvel == 0){
                if(top < -topThreshold){
                    settleTop = -screenHeight;
                }
            }

            dragHelper.settleCapturedViewAt(releasedChild.getLeft(), settleTop);
            invalidate();
        }

        @Override
        public void onViewPositionChanged(View changedView, int left, int top, int dx, int dy) {
            super.onViewPositionChanged(changedView, left, top, dx, dy);
            float percent = 1f - ((float)Math.abs(top) / (float) screenHeight);

            if(listener != null) listener.onSlideChange(percent);

            // Update the dimmer alpha
            applyScrim(percent);
        }

        @Override
        public void onViewDragStateChanged(int state) {
            super.onViewDragStateChanged(state);
            if(listener != null) listener.onStateChanged(state);
            switch (state){
                case ViewDragHelper.STATE_IDLE:
                    if(decorView.getTop() == 0){
                        // State Open
                        if(listener != null) listener.onOpened();
                    }else{
                        // State Closed
                        if(listener != null) listener.onClosed();
                    }
                    break;
                case ViewDragHelper.STATE_DRAGGING:

                    break;
                case ViewDragHelper.STATE_SETTLING:

                    break;
            }
        }
    };


    /**
     * The drag helper callbacks for dragging the slidr attachment in both vertical directions
     */
    private final ViewDragHelper.Callback verticalCallback = new ViewDragHelper.Callback() {
        @Override
        public boolean tryCaptureView(View child, int pointerId) {
            return child.getId() == decorView.getId() && (!config.isEdgeOnly() || isEdgeTouched);
        }

        @Override
        public int clampViewPositionVertical(View child, int top, int dy) {
            return clamp(top, -screenHeight, screenHeight);
        }

        @Override
        public int getViewVerticalDragRange(View child) {
            return screenHeight;
        }

        @Override
        public void onViewReleased(View releasedChild, float xvel, float yvel) {
            super.onViewReleased(releasedChild, xvel, yvel);

            int top = releasedChild.getTop();
            int settleTop = 0;
            int topThreshold = (int) (getHeight() * config.getDistanceThreshold());
            boolean isSideSwiping = Math.abs(xvel) > config.getVelocityThreshold();

            if(yvel > 0){

                // Being slinged down
                if(Math.abs(yvel) > config.getVelocityThreshold() && !isSideSwiping){
                    settleTop = screenHeight;
                }else if(top > topThreshold){
                    settleTop = screenHeight;
                }

            }else if(yvel < 0){
                // Being slinged up
                if(Math.abs(yvel) > config.getVelocityThreshold() && !isSideSwiping){
                    settleTop = -screenHeight;
                }else if(top < -topThreshold){
                    settleTop = -screenHeight;
                }

            }else{

                if(top > topThreshold){
                    settleTop = screenHeight;
                }else if(top < -topThreshold){
                    settleTop = -screenHeight;
                }

            }

            dragHelper.settleCapturedViewAt(releasedChild.getLeft(), settleTop);
            invalidate();
        }

        @Override
        public void onViewPositionChanged(View changedView, int left, int top, int dx, int dy) {
            super.onViewPositionChanged(changedView, left, top, dx, dy);
            float percent = 1f - ((float)Math.abs(top) / (float) screenHeight);

            if(listener != null) listener.onSlideChange(percent);

            // Update the dimmer alpha
            applyScrim(percent);
        }

        @Override
        public void onViewDragStateChanged(int state) {
            super.onViewDragStateChanged(state);
            if(listener != null) listener.onStateChanged(state);
            switch (state){
                case ViewDragHelper.STATE_IDLE:
                    if(decorView.getTop() == 0){
                        // State Open
                        if(listener != null) listener.onOpened();
                    }else{
                        // State Closed
                        if(listener != null) listener.onClosed();
                    }
                    break;
                case ViewDragHelper.STATE_DRAGGING:

                    break;
                case ViewDragHelper.STATE_SETTLING:

                    break;
            }
        }
    };


    /**
     * The drag helper callbacks for dragging the slidr attachment in both horizontal directions
     */
    private final ViewDragHelper.Callback horizontalCallback = new ViewDragHelper.Callback() {
        @Override
        public boolean tryCaptureView(View child, int pointerId) {
            boolean edgeCase = !config.isEdgeOnly() || dragHelper.isEdgeTouched(edgePosition, pointerId);
            return child.getId() == decorView.getId() && edgeCase;
        }

        @Override
        public int clampViewPositionHorizontal(View child, int left, int dx) {
            return clamp(left, -screenWidth, screenWidth);
        }

        @Override
        public int getViewHorizontalDragRange(View child) {
            return screenWidth;
        }

        @Override
        public void onViewReleased(View releasedChild, float xvel, float yvel) {
            super.onViewReleased(releasedChild, xvel, yvel);

            int left = releasedChild.getLeft();
            int settleLeft = 0;
            int leftThreshold = (int) (getWidth() * config.getDistanceThreshold());
            boolean isVerticalSwiping = Math.abs(yvel) > config.getVelocityThreshold();

            if(xvel > 0){

                if(Math.abs(xvel) > config.getVelocityThreshold() && !isVerticalSwiping){
                    settleLeft = screenWidth;
                }else if(left > leftThreshold){
                    settleLeft = screenWidth;
                }

            }else if(xvel < 0){

                if(Math.abs(xvel) > config.getVelocityThreshold() && !isVerticalSwiping){
                    settleLeft = -screenWidth;
                }else if(left < -leftThreshold){
                    settleLeft = -screenWidth;
                }

            }else{
                if(left > leftThreshold){
                    settleLeft = screenWidth;
                }else if(left < -leftThreshold){
                    settleLeft = -screenWidth;
                }
            }

            dragHelper.settleCapturedViewAt(settleLeft, releasedChild.getTop());
            invalidate();
        }

        @Override
        public void onViewPositionChanged(View changedView, int left, int top, int dx, int dy) {
            super.onViewPositionChanged(changedView, left, top, dx, dy);
            float percent = 1f - ((float)Math.abs(left) / (float) screenWidth);

            if(listener != null) listener.onSlideChange(percent);

            // Update the dimmer alpha
            applyScrim(percent);
        }

        @Override
        public void onViewDragStateChanged(int state) {
            super.onViewDragStateChanged(state);
            if(listener != null) listener.onStateChanged(state);
            switch (state){
                case ViewDragHelper.STATE_IDLE:
                    if(decorView.getLeft() == 0){
                        // State Open
                        if(listener != null) listener.onOpened();
                    }else{
                        // State Closed
                        if(listener != null) listener.onClosed();
                    }
                    break;
                case ViewDragHelper.STATE_DRAGGING:

                    break;
                case ViewDragHelper.STATE_SETTLING:

                    break;
            }
        }
    };


    private void init(){
        setWillNotDraw(false);
        screenWidth = getResources().getDisplayMetrics().widthPixels;

        final float density = getResources().getDisplayMetrics().density;
        final float minVel = MIN_FLING_VELOCITY * density;

        ViewDragHelper.Callback callback;
        switch (config.getPosition()){
            case LEFT:
                callback = leftCallback;
                edgePosition = ViewDragHelper.EDGE_LEFT;
                break;
            case RIGHT:
                callback = rightCallback;
                edgePosition = ViewDragHelper.EDGE_RIGHT;
                break;
            case TOP:
                callback = topCallback;
                edgePosition = ViewDragHelper.EDGE_TOP;
                break;
            case BOTTOM:
                callback = bottomCallback;
                edgePosition = ViewDragHelper.EDGE_BOTTOM;
                break;
            case VERTICAL:
                callback = verticalCallback;
                edgePosition = ViewDragHelper.EDGE_TOP | ViewDragHelper.EDGE_BOTTOM;
                break;
            case HORIZONTAL:
                callback = horizontalCallback;
                edgePosition = ViewDragHelper.EDGE_LEFT | ViewDragHelper.EDGE_RIGHT;
                break;
            default:
                callback = leftCallback;
                edgePosition = ViewDragHelper.EDGE_LEFT;
        }

        dragHelper = ViewDragHelper.create(this, config.getSensitivity(), callback);
        dragHelper.setMinVelocity(minVel);
        dragHelper.setEdgeTrackingEnabled(edgePosition);

        ViewGroupCompat.setMotionEventSplittingEnabled(this, false);

        // Setup the dimmer view
        scrimPaint = new Paint();
        scrimPaint.setColor(config.getScrimColor());
        scrimPaint.setAlpha(toAlpha(config.getScrimStartAlpha()));
        scrimRenderer = new ScrimRenderer(this, decorView);

        /*
         * This is so we can get the height of the view and
         * ignore the system navigation that would be included if we
         * retrieved this value from the DisplayMetrics
         */
        post(new Runnable() {
            @Override
            public void run() {
                screenHeight = getHeight();
            }
        });

    }


    private void lock(){
        dragHelper.abort();
        isLocked = true;
    }


    private void unlock(){
        dragHelper.abort();
        isLocked = false;
    }


    private boolean canDragFromEdge(MotionEvent ev) {
        float x = ev.getX();
        float y = ev.getY();

        switch (config.getPosition()) {
            case LEFT:
                return x < config.getEdgeSize(getWidth());
            case RIGHT:
                return x > getWidth() - config.getEdgeSize(getWidth());
            case BOTTOM:
                return y > getHeight() - config.getEdgeSize(getHeight());
            case TOP:
                return y < config.getEdgeSize(getHeight());
            case HORIZONTAL:
                return x < config.getEdgeSize(getWidth()) || x > getWidth() - config.getEdgeSize(getWidth());
            case VERTICAL:
                return y < config.getEdgeSize(getHeight()) || y > getHeight() - config.getEdgeSize(getHeight());
        }
        return false;
    }


    private void applyScrim(float percent){
        float alpha = (percent * (config.getScrimStartAlpha() - config.getScrimEndAlpha())) + config.getScrimEndAlpha();
        scrimPaint.setAlpha(toAlpha(alpha));
        invalidate(scrimRenderer.getDirtyRect(config.getPosition()));
    }


    private static int clamp(int value, int min, int max){
        return Math.max(min, Math.min(max, value));
    }


    private static int toAlpha(float percentage) {
        return (int) (percentage * 255);
    }


    /**
     * The panel sliding interface that gets called
     * whenever the panel is closed or opened
     */
    public interface OnPanelSlideListener{
        void onStateChanged(int state);
        void onClosed();
        void onOpened();
        void onSlideChange(float percent);
    }

}

package com.r0adkll.slidr.widget;


import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import androidx.annotation.NonNull;
import android.view.View;

import com.r0adkll.slidr.model.SlidrPosition;


final class ScrimRenderer {

    private final View rootView;
    private final View decorView;
    private final Rect dirtyRect;


    ScrimRenderer(@NonNull View rootView, @NonNull View decorView) {
        this.rootView = rootView;
        this.decorView = decorView;
        dirtyRect = new Rect();
    }


    void render(Canvas canvas, SlidrPosition position, Paint paint) {
        switch (position) {
            case LEFT:
                renderLeft(canvas, paint);
                break;
            case RIGHT:
                renderRight(canvas, paint);
                break;
            case TOP:
                renderTop(canvas, paint);
                break;
            case BOTTOM:
                renderBottom(canvas, paint);
                break;
            case VERTICAL:
                renderVertical(canvas, paint);
                break;
            case HORIZONTAL:
                renderHorizontal(canvas, paint);
                break;
        }
    }


    Rect getDirtyRect(SlidrPosition position) {
        switch (position) {
            case LEFT:
                dirtyRect.set(0, 0, decorView.getLeft(), rootView.getMeasuredHeight());
                break;
            case RIGHT:
                dirtyRect.set(decorView.getRight(), 0, rootView.getMeasuredWidth(), rootView.getMeasuredHeight());
                break;
            case TOP:
                dirtyRect.set(0, 0, rootView.getMeasuredWidth(), decorView.getTop());
                break;
            case BOTTOM:
                dirtyRect.set(0, decorView.getBottom(), rootView.getMeasuredWidth(), rootView.getMeasuredHeight());
                break;
            case VERTICAL:
                if (decorView.getTop() > 0) {
                    dirtyRect.set(0, 0, rootView.getMeasuredWidth(), decorView.getTop());
                }
                else {
                    dirtyRect.set(0, decorView.getBottom(), rootView.getMeasuredWidth(), rootView.getMeasuredHeight());
                }
                break;
            case HORIZONTAL:
                if (decorView.getLeft() > 0) {
                    dirtyRect.set(0, 0, decorView.getLeft(), rootView.getMeasuredHeight());
                }
                else {
                    dirtyRect.set(decorView.getRight(), 0, rootView.getMeasuredWidth(), rootView.getMeasuredHeight());
                }
                break;
        }
        return dirtyRect;
    }


    private void renderLeft(Canvas canvas, Paint paint) {
        canvas.drawRect(0, 0, decorView.getLeft(), rootView.getMeasuredHeight(), paint);
    }


    private void renderRight(Canvas canvas, Paint paint) {
        canvas.drawRect(decorView.getRight(), 0, rootView.getMeasuredWidth(), rootView.getMeasuredHeight(), paint);
    }


    private void renderTop(Canvas canvas, Paint paint) {
        canvas.drawRect(0, 0, rootView.getMeasuredWidth(), decorView.getTop(), paint);
    }


    private void renderBottom(Canvas canvas, Paint paint) {
        canvas.drawRect(0, decorView.getBottom(), rootView.getMeasuredWidth(), rootView.getMeasuredHeight(), paint);
    }


    private void renderVertical(Canvas canvas, Paint paint) {
        if (decorView.getTop() > 0) {
            renderTop(canvas, paint);
        }
        else {
            renderBottom(canvas, paint);
        }
    }


    private void renderHorizontal(Canvas canvas, Paint paint) {
        if (decorView.getLeft() > 0) {
            renderLeft(canvas, paint);
        }
        else {
            renderRight(canvas, paint);
        }
    }
}

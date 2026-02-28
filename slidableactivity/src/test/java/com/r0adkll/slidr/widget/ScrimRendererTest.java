package com.r0adkll.slidr.widget;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import com.r0adkll.slidr.model.SlidrPosition;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@RunWith(MockitoJUnitRunner.class)
public class ScrimRendererTest {

    private static final int LEFT = 10;
    private static final int HORIZONTAL_LEFT = -10;
    private static final int RIGHT = 50;
    private static final int TOP = 20;
    private static final int VERTICAL_TOP = -20;
    private static final int BOTTOM = 100;
    private static final int WIDTH = 720;
    private static final int HEIGHT = 1280;

    @Mock Paint paint;
    @Mock Canvas canvas;
    @Mock View decorView;
    @Mock View rootView;

    private ScrimRenderer renderer;


    @Before
    public void setUp() {
        when(rootView.getMeasuredWidth()).thenReturn(WIDTH);
        when(rootView.getMeasuredHeight()).thenReturn(HEIGHT);
        when(decorView.getLeft()).thenReturn(LEFT);
        when(decorView.getRight()).thenReturn(RIGHT);
        when(decorView.getTop()).thenReturn(TOP);
        when(decorView.getBottom()).thenReturn(BOTTOM);
        renderer = new ScrimRenderer(rootView, decorView);
    }


    @Test
    public void shouldDrawRectForLeftPosition() {
        renderer.render(canvas, SlidrPosition.LEFT, paint);
        verify(canvas).drawRect(0, 0, LEFT, HEIGHT, paint);
    }


    @Test
    public void shouldDrawRectForRightPosition() {
        renderer.render(canvas, SlidrPosition.RIGHT, paint);
        verify(canvas).drawRect(RIGHT, 0, WIDTH, HEIGHT, paint);
    }


    @Test
    public void shouldDrawRectForTopPosition() {
        renderer.render(canvas, SlidrPosition.TOP, paint);
        verify(canvas).drawRect(0, 0, WIDTH, TOP, paint);
    }


    @Test
    public void shouldDrawRectForBottomPosition() {
        renderer.render(canvas, SlidrPosition.BOTTOM, paint);
        verify(canvas).drawRect(0, BOTTOM, WIDTH, HEIGHT, paint);
    }


    @Test
    public void shouldDrawRectForPositiveVerticalPosition() {
        renderer.render(canvas, SlidrPosition.VERTICAL, paint);
        verify(canvas).drawRect(0, 0, WIDTH, TOP, paint);
    }


    @Test
    public void shouldDrawRectForNegativeVerticalPosition() {
        when(decorView.getTop()).thenReturn(VERTICAL_TOP);
        renderer.render(canvas, SlidrPosition.VERTICAL, paint);
        verify(canvas).drawRect(0, BOTTOM, WIDTH, HEIGHT, paint);
    }


    @Test
    public void shouldDrawRectForPositiveHorizontalPosition() {
        renderer.render(canvas, SlidrPosition.HORIZONTAL, paint);
        verify(canvas).drawRect(0, 0, LEFT, HEIGHT, paint);
    }


    @Test
    public void shouldDrawRectForNegativeHorizontalPosition() {
        when(decorView.getLeft()).thenReturn(HORIZONTAL_LEFT);
        renderer.render(canvas, SlidrPosition.HORIZONTAL, paint);
        verify(canvas).drawRect(RIGHT, 0, WIDTH, HEIGHT, paint);
    }
}
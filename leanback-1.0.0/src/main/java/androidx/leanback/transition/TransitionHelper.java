/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package androidx.leanback.transition;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;

import android.animation.TimeInterpolator;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.transition.AutoTransition;
import android.transition.ChangeTransform;
import android.transition.Fade;
import android.transition.Scene;
import android.transition.Transition;
import android.transition.TransitionInflater;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.AnimationUtils;

import androidx.annotation.RestrictTo;

import java.util.ArrayList;

/**
 * Helper for view transitions.
 * @hide
 */
@RestrictTo(LIBRARY_GROUP)
public final class TransitionHelper {

    public static final int FADE_IN = 0x1;
    public static final int FADE_OUT = 0x2;

    /**
     * Returns true if system supports entrance Transition animations.
     */
    public static boolean systemSupportsEntranceTransitions() {
        return Build.VERSION.SDK_INT >= 21;
    }

    private static class TransitionStub {
        ArrayList<TransitionListener> mTransitionListeners;

        TransitionStub() {
        }
    }

    public static Object getSharedElementEnterTransition(Window window) {
        if (Build.VERSION.SDK_INT >= 21) {
            return window.getSharedElementEnterTransition();
        }
        return null;
    }

    public static void setSharedElementEnterTransition(Window window, Object transition) {
        if (Build.VERSION.SDK_INT >= 21) {
            window.setSharedElementEnterTransition((Transition) transition);
        }
    }

    public static Object getSharedElementReturnTransition(Window window) {
        if (Build.VERSION.SDK_INT >= 21) {
            return window.getSharedElementReturnTransition();
        }
        return null;
    }

    public static void setSharedElementReturnTransition(Window window, Object transition) {
        if (Build.VERSION.SDK_INT >= 21) {
            window.setSharedElementReturnTransition((Transition) transition);
        }
    }

    public static Object getSharedElementExitTransition(Window window) {
        if (Build.VERSION.SDK_INT >= 21) {
            return window.getSharedElementExitTransition();
        }
        return null;
    }

    public static Object getSharedElementReenterTransition(Window window) {
        if (Build.VERSION.SDK_INT >= 21) {
            return window.getSharedElementReenterTransition();
        }
        return null;
    }

    public static Object getEnterTransition(Window window) {
        if (Build.VERSION.SDK_INT >= 21) {
            return window.getEnterTransition();
        }
        return null;
    }

    public static void setEnterTransition(Window window, Object transition) {
        if (Build.VERSION.SDK_INT >= 21) {
            window.setEnterTransition((Transition) transition);
        }
    }

    public static Object getReturnTransition(Window window) {
        if (Build.VERSION.SDK_INT >= 21) {
            return window.getReturnTransition();
        }
        return null;
    }

    public static void setReturnTransition(Window window, Object transition) {
        if (Build.VERSION.SDK_INT >= 21) {
            window.setReturnTransition((Transition) transition);
        }
    }

    public static Object getExitTransition(Window window) {
        if (Build.VERSION.SDK_INT >= 21) {
            return window.getExitTransition();
        }
        return null;
    }

    public static Object getReenterTransition(Window window) {
        if (Build.VERSION.SDK_INT >= 21) {
            return window.getReenterTransition();
        }
        return null;
    }

    public static Object createScene(ViewGroup sceneRoot, Runnable r) {
        if (Build.VERSION.SDK_INT >= 19) {
            Scene scene = new Scene(sceneRoot);
            scene.setEnterAction(r);
            return scene;
        }
        return r;
    }

    public static Object createChangeBounds(boolean reparent) {
        if (Build.VERSION.SDK_INT >= 19) {
            CustomChangeBounds changeBounds = new CustomChangeBounds();
            changeBounds.setReparent(reparent);
            return changeBounds;
        }
        return new TransitionStub();
    }

    public static Object createChangeTransform() {
        if (Build.VERSION.SDK_INT >= 21) {
            return new ChangeTransform();
        }
        return new TransitionStub();
    }

    public static void setChangeBoundsStartDelay(Object changeBounds, View view, int startDelay) {
        if (Build.VERSION.SDK_INT >= 19) {
            ((CustomChangeBounds) changeBounds).setStartDelay(view, startDelay);
        }
    }

    public static void setChangeBoundsStartDelay(Object changeBounds, int viewId, int startDelay) {
        if (Build.VERSION.SDK_INT >= 19) {
            ((CustomChangeBounds) changeBounds).setStartDelay(viewId, startDelay);
        }
    }

    public static void setChangeBoundsStartDelay(Object changeBounds, String className,
            int startDelay) {
        if (Build.VERSION.SDK_INT >= 19) {
            ((CustomChangeBounds) changeBounds).setStartDelay(className, startDelay);
        }
    }

    public static void setChangeBoundsDefaultStartDelay(Object changeBounds, int startDelay) {
        if (Build.VERSION.SDK_INT >= 19) {
            ((CustomChangeBounds) changeBounds).setDefaultStartDelay(startDelay);
        }
    }

    public static Object createTransitionSet(boolean sequential) {
        if (Build.VERSION.SDK_INT >= 19) {
            TransitionSet set = new TransitionSet();
            set.setOrdering(sequential ? TransitionSet.ORDERING_SEQUENTIAL
                    : TransitionSet.ORDERING_TOGETHER);
            return set;
        }
        return new TransitionStub();
    }

    public static Object createSlide(int slideEdge) {
        if (Build.VERSION.SDK_INT >= 19) {
            SlideKitkat slide = new SlideKitkat();
            slide.setSlideEdge(slideEdge);
            return slide;
        }
        return new TransitionStub();
    }

    public static Object createScale() {
        if (Build.VERSION.SDK_INT >= 21) {
            return new ChangeTransform();
        }
        if (Build.VERSION.SDK_INT >= 19) {
            return new Scale();
        }
        return new TransitionStub();
    }

    public static void addTransition(Object transitionSet, Object transition) {
        if (Build.VERSION.SDK_INT >= 19) {
            ((TransitionSet) transitionSet).addTransition((Transition) transition);
        }
    }

    public static void exclude(Object transition, int targetId, boolean exclude) {
        if (Build.VERSION.SDK_INT >= 19) {
            ((Transition) transition).excludeTarget(targetId, exclude);
        }
    }

    public static void exclude(Object transition, View targetView, boolean exclude) {
        if (Build.VERSION.SDK_INT >= 19) {
            ((Transition) transition).excludeTarget(targetView, exclude);
        }
    }

    public static void excludeChildren(Object transition, int targetId, boolean exclude) {
        if (Build.VERSION.SDK_INT >= 19) {
            ((Transition) transition).excludeChildren(targetId, exclude);
        }
    }

    public static void excludeChildren(Object transition, View targetView, boolean exclude) {
        if (Build.VERSION.SDK_INT >= 19) {
            ((Transition) transition).excludeChildren(targetView, exclude);
        }
    }

    public static void include(Object transition, int targetId) {
        if (Build.VERSION.SDK_INT >= 19) {
            ((Transition) transition).addTarget(targetId);
        }
    }

    public static void include(Object transition, View targetView) {
        if (Build.VERSION.SDK_INT >= 19) {
            ((Transition) transition).addTarget(targetView);
        }
    }

    public static void setStartDelay(Object transition, long startDelay) {
        if (Build.VERSION.SDK_INT >= 19) {
            ((Transition) transition).setStartDelay(startDelay);
        }
    }

    public static void setDuration(Object transition, long duration) {
        if (Build.VERSION.SDK_INT >= 19) {
            ((Transition) transition).setDuration(duration);
        }
    }

    public static Object createAutoTransition() {
        if (Build.VERSION.SDK_INT >= 19) {
            return new AutoTransition();
        }
        return new TransitionStub();
    }

    public static Object createFadeTransition(int fadeMode) {
        if (Build.VERSION.SDK_INT >= 19) {
            return new Fade(fadeMode);
        }
        return new TransitionStub();
    }

    public static void addTransitionListener(Object transition, final TransitionListener listener) {
        if (listener == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 19) {
            Transition t = (Transition) transition;
            listener.mImpl = new Transition.TransitionListener() {
                @Override
                public void onTransitionStart(Transition transition11) {
                    listener.onTransitionStart(transition11);
                }

                @Override
                public void onTransitionResume(Transition transition11) {
                    listener.onTransitionResume(transition11);
                }

                @Override
                public void onTransitionPause(Transition transition11) {
                    listener.onTransitionPause(transition11);
                }

                @Override
                public void onTransitionEnd(Transition transition11) {
                    listener.onTransitionEnd(transition11);
                }

                @Override
                public void onTransitionCancel(Transition transition11) {
                    listener.onTransitionCancel(transition11);
                }
            };
            t.addListener((Transition.TransitionListener) listener.mImpl);
        } else {
            TransitionStub stub = (TransitionStub) transition;
            if (stub.mTransitionListeners == null) {
                stub.mTransitionListeners = new ArrayList<>();
            }
            stub.mTransitionListeners.add(listener);
        }
    }

    public static void removeTransitionListener(Object transition, TransitionListener listener) {
        if (Build.VERSION.SDK_INT >= 19) {
            if (listener == null || listener.mImpl == null) {
                return;
            }
            Transition t = (Transition) transition;
            t.removeListener((Transition.TransitionListener) listener.mImpl);
            listener.mImpl = null;
        } else {
            TransitionStub stub = (TransitionStub) transition;
            if (stub.mTransitionListeners != null) {
                stub.mTransitionListeners.remove(listener);
            }
        }
    }

    public static void runTransition(Object scene, Object transition) {
        if (Build.VERSION.SDK_INT >= 19) {
            TransitionManager.go((Scene) scene, (Transition) transition);
        } else {
            TransitionStub transitionStub = (TransitionStub) transition;
            if (transitionStub != null && transitionStub.mTransitionListeners != null) {
                for (int i = 0, size = transitionStub.mTransitionListeners.size(); i < size; i++) {
                    transitionStub.mTransitionListeners.get(i).onTransitionStart(transition);
                }
            }
            Runnable r = ((Runnable) scene);
            if (r != null) {
                r.run();
            }
            if (transitionStub != null && transitionStub.mTransitionListeners != null) {
                for (int i = 0, size = transitionStub.mTransitionListeners.size(); i < size; i++) {
                    transitionStub.mTransitionListeners.get(i).onTransitionEnd(transition);
                }
            }
        }
    }

    public static void setInterpolator(Object transition, Object timeInterpolator) {
        if (Build.VERSION.SDK_INT >= 19) {
            ((Transition) transition).setInterpolator((TimeInterpolator) timeInterpolator);
        }
    }

    public static void addTarget(Object transition, View view) {
        if (Build.VERSION.SDK_INT >= 19) {
            ((Transition) transition).addTarget(view);
        }
    }

    public static Object createDefaultInterpolator(Context context) {
        if (Build.VERSION.SDK_INT >= 21) {
            return AnimationUtils.loadInterpolator(context,
                    android.R.interpolator.fast_out_linear_in);
        }
        return null;
    }

    public static Object loadTransition(Context context, int resId) {
        if (Build.VERSION.SDK_INT >= 19) {
            return TransitionInflater.from(context).inflateTransition(resId);
        }
        return new TransitionStub();
    }

    public static void setEnterTransition(android.app.Fragment fragment, Object transition) {
        if (Build.VERSION.SDK_INT >= 21) {
            fragment.setEnterTransition((Transition) transition);
        }
    }

    public static void setExitTransition(android.app.Fragment fragment, Object transition) {
        if (Build.VERSION.SDK_INT >= 21) {
            fragment.setExitTransition((Transition) transition);
        }
    }

    public static void setSharedElementEnterTransition(android.app.Fragment fragment,
            Object transition) {
        if (Build.VERSION.SDK_INT >= 21) {
            fragment.setSharedElementEnterTransition((Transition) transition);
        }
    }

    public static void addSharedElement(android.app.FragmentTransaction ft,
            View view, String transitionName) {
        if (Build.VERSION.SDK_INT >= 21) {
            ft.addSharedElement(view, transitionName);
        }
    }

    public static Object createFadeAndShortSlide(int edge) {
        if (Build.VERSION.SDK_INT >= 21) {
            return new FadeAndShortSlide(edge);
        }
        return new TransitionStub();
    }

    public static Object createFadeAndShortSlide(int edge, float distance) {
        if (Build.VERSION.SDK_INT >= 21) {
            FadeAndShortSlide slide = new FadeAndShortSlide(edge);
            slide.setDistance(distance);
            return slide;
        }
        return new TransitionStub();
    }

    public static void beginDelayedTransition(ViewGroup sceneRoot, Object transitionObject) {
        if (Build.VERSION.SDK_INT >= 21) {
            Transition transition = (Transition) transitionObject;
            TransitionManager.beginDelayedTransition(sceneRoot, transition);
        }
    }

    public static void setTransitionGroup(ViewGroup viewGroup, boolean transitionGroup) {
        if (Build.VERSION.SDK_INT >= 21) {
            viewGroup.setTransitionGroup(transitionGroup);
        }
    }

    public static void setEpicenterCallback(Object transition,
            final TransitionEpicenterCallback callback) {
        if (Build.VERSION.SDK_INT >= 21) {
            if (callback == null) {
                ((Transition) transition).setEpicenterCallback(null);
            } else {
                ((Transition) transition).setEpicenterCallback(new Transition.EpicenterCallback() {
                    @Override
                    public Rect onGetEpicenter(Transition transition11) {
                        return callback.onGetEpicenter(transition11);
                    }
                });
            }
        }
    }

    private TransitionHelper() {
    }
}

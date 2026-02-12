/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.leanback.widget;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.util.Property;
import android.view.animation.LinearInterpolator;

/**
 * ParallaxTarget is responsible for updating the target through the {@link #update(float)} method
 * or the {@link #directUpdate(Number)} method when {@link #isDirectMapping()} is true.
 * When {@link #isDirectMapping()} is false, {@link ParallaxEffect} transforms the values of
 * {@link Parallax}, which represents the current state of UI, into a float value between 0 and 1.
 * That float value is passed into {@link #update(float)} method.
 */
public abstract class ParallaxTarget {

    /**
     * Implementation class is supposed to update target with the provided fraction
     * (between 0 and 1). The fraction represents percentage of completed change (e.g. scroll) on
     * target. Called only when {@link #isDirectMapping()} is false.
     *
     * @param fraction Fraction between 0 to 1.
     * @see #isDirectMapping()
     */
    public void update(float fraction) {
    }

    /**
     * Returns true if the ParallaxTarget is directly mapping from source value,
     * {@link #directUpdate(Number)} will be used to update value, otherwise update(fraction) will
     * be called to update value. Default implementation returns false.
     *
     * @return True if direct mapping, false otherwise.
     * @see #directUpdate(Number)
     * @see #update(float)
     */
    public boolean isDirectMapping() {
        return false;
    }

    /**
     * Directly update the target using a float or int value. Called when {@link #isDirectMapping()}
     * is true.
     *
     * @param value Either int or float value.
     * @see #isDirectMapping()
     */
    public void directUpdate(Number value) {
    }

    /**
     * PropertyValuesHolderTarget is an implementation of {@link ParallaxTarget} that uses
     * {@link PropertyValuesHolder} to update the target object.
     */
    public static final class PropertyValuesHolderTarget extends ParallaxTarget {

        /**
         * We simulate a parallax effect on target object using an ObjectAnimator. PSEUDO_DURATION
         * is used on the ObjectAnimator.
         */
        private static final long PSEUDO_DURATION = 1000000;

        private final ObjectAnimator mAnimator;
        private float mFraction;

        public PropertyValuesHolderTarget(Object targetObject, PropertyValuesHolder values) {
            mAnimator = ObjectAnimator.ofPropertyValuesHolder(targetObject, values);
            mAnimator.setInterpolator(new LinearInterpolator());
            mAnimator.setDuration(PSEUDO_DURATION);
        }

        @Override
        public void update(float fraction) {
            mFraction = fraction;
            mAnimator.setCurrentPlayTime((long) (PSEUDO_DURATION * fraction));
        }

    }

    /**
     * DirectPropertyTarget is to support direct mapping into either Integer Property or Float
     * Property. App uses convenient method {@link ParallaxEffect#target(Object, Property)} to
     * add a direct mapping.
     * @param <T> Type of target object.
     * @param <V> Type of value, either Integer or Float.
     */
    public static final class DirectPropertyTarget<T extends Object, V extends Number>
            extends ParallaxTarget {

        Object mObject;
        Property<T, V> mProperty;

        /**
         * @param targetObject Target object for perform Parallax
         * @param property     Target property, either an Integer Property or a Float Property.
         */
        public DirectPropertyTarget(Object targetObject, Property<T, V> property) {
            mObject = targetObject;
            mProperty = property;
        }

        /**
         * Returns true as DirectPropertyTarget receives a number to update Property in
         * {@link #directUpdate(Number)}.
         */
        @Override
        public boolean isDirectMapping() {
            return true;
        }

        @Override
        public void directUpdate(Number value) {
            mProperty.set((T) mObject, (V) value);
        }
    }
}

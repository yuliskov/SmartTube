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

import android.animation.PropertyValuesHolder;
import android.util.Property;

import androidx.annotation.RestrictTo;
import androidx.leanback.widget.Parallax.FloatProperty;
import androidx.leanback.widget.Parallax.FloatPropertyMarkerValue;
import androidx.leanback.widget.Parallax.IntProperty;
import androidx.leanback.widget.Parallax.PropertyMarkerValue;

import java.util.ArrayList;
import java.util.List;

/**
 * ParallaxEffect class drives changes in {@link ParallaxTarget} in response to changes in
 * variables defined in {@link Parallax}.
 * <p>
 * ParallaxEffect has a list of {@link Parallax.PropertyMarkerValue}s which represents the range of
 * values that source variables can take. The main function is
 * {@link ParallaxEffect#performMapping(Parallax)} which computes a fraction between 0 and 1
 * based on the current values of variables in {@link Parallax}. As the parallax effect goes
 * on, the fraction increases from 0 at beginning to 1 at the end. Then the fraction is passed on
 * to {@link ParallaxTarget#update(float)}.
 * <p>
 * App use {@link Parallax#addEffect(PropertyMarkerValue...)} to create a ParallaxEffect.
 */
public abstract class ParallaxEffect {

    final List<Parallax.PropertyMarkerValue> mMarkerValues = new ArrayList(2);
    final List<Float> mWeights = new ArrayList<Float>(2);
    final List<Float> mTotalWeights = new ArrayList<Float>(2);
    final List<ParallaxTarget> mTargets = new ArrayList<ParallaxTarget>(4);

    /**
     * Only accessible from package
     */
    ParallaxEffect() {
    }

    /**
     * Returns the list of {@link PropertyMarkerValue}s, which represents the range of values that
     * source variables can take.
     *
     * @return A list of {@link Parallax.PropertyMarkerValue}s.
     * @see #performMapping(Parallax)
     */
    public final List<Parallax.PropertyMarkerValue> getPropertyRanges() {
        return mMarkerValues;
    }

    /**
     * Returns a list of Float objects that represents weight associated with each variable range.
     * Weights are used when there are three or more marker values.
     *
     * @return A list of Float objects that represents weight associated with each variable range.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public final List<Float> getWeights() {
        return mWeights;
    }

    /**
     * Sets the list of {@link PropertyMarkerValue}s, which represents the range of values that
     * source variables can take.
     *
     * @param markerValues A list of {@link PropertyMarkerValue}s.
     * @see #performMapping(Parallax)
     */
    public final void setPropertyRanges(Parallax.PropertyMarkerValue... markerValues) {
        mMarkerValues.clear();
        for (Parallax.PropertyMarkerValue markerValue : markerValues) {
            mMarkerValues.add(markerValue);
        }
    }

    /**
     * Sets a list of Float objects that represents weight associated with each variable range.
     * Weights are used when there are three or more marker values.
     *
     * @param weights A list of Float objects that represents weight associated with each variable
     *                range.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public final void setWeights(float... weights) {
        for (float weight : weights) {
            if (weight <= 0) {
                throw new IllegalArgumentException();
            }
        }
        mWeights.clear();
        mTotalWeights.clear();
        float totalWeight = 0f;
        for (float weight : weights) {
            mWeights.add(weight);
            totalWeight += weight;
            mTotalWeights.add(totalWeight);
        }
    }

    /**
     * Sets a list of Float objects that represents weight associated with each variable range.
     * Weights are used when there are three or more marker values.
     *
     * @param weights A list of Float objects that represents weight associated with each variable
     *                range.
     * @return This ParallaxEffect object, allowing calls to methods in this class to be chained.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public final ParallaxEffect weights(float... weights) {
        setWeights(weights);
        return this;
    }

    /**
     * Add a ParallaxTarget to run parallax effect.
     *
     * @param target ParallaxTarget to add.
     */
    public final void addTarget(ParallaxTarget target) {
        mTargets.add(target);
    }

    /**
     * Add a ParallaxTarget to run parallax effect.
     *
     * @param target ParallaxTarget to add.
     * @return This ParallaxEffect object, allowing calls to methods in this class to be chained.
     */
    public final ParallaxEffect target(ParallaxTarget target) {
        mTargets.add(target);
        return this;
    }

    /**
     * Creates a {@link ParallaxTarget} from {@link PropertyValuesHolder} and adds it to the list
     * of targets.
     *
     * @param targetObject Target object for PropertyValuesHolderTarget.
     * @param values       PropertyValuesHolder for PropertyValuesHolderTarget.
     * @return This ParallaxEffect object, allowing calls to methods in this class to be chained.
     */
    public final ParallaxEffect target(Object targetObject, PropertyValuesHolder values) {
        mTargets.add(new ParallaxTarget.PropertyValuesHolderTarget(targetObject, values));
        return this;
    }

    /**
     * Creates a {@link ParallaxTarget} using direct mapping from source property into target
     * property, the new {@link ParallaxTarget} will be added to its list of targets.
     *
     * @param targetObject Target object for property.
     * @param targetProperty The target property that will receive values.
     * @return This ParallaxEffect object, allowing calls to methods in this class to be chained.
     * @param <T> Type of target object.
     * @param <V> Type of target property value, either Integer or Float.
     * @see ParallaxTarget#isDirectMapping()
     */
    public final <T, V extends Number> ParallaxEffect target(T targetObject,
            Property<T, V> targetProperty) {
        mTargets.add(new ParallaxTarget.DirectPropertyTarget(targetObject, targetProperty));
        return this;
    }

    /**
     * Returns the list of {@link ParallaxTarget} objects.
     *
     * @return The list of {@link ParallaxTarget} objects.
     */
    public final List<ParallaxTarget> getTargets() {
        return mTargets;
    }

    /**
     * Remove a {@link ParallaxTarget} object from the list.
     * @param target The {@link ParallaxTarget} object to be removed.
     */
    public final void removeTarget(ParallaxTarget target) {
        mTargets.remove(target);
    }

    /**
     * Perform mapping from {@link Parallax} to list of {@link ParallaxTarget}.
     */
    public final void performMapping(Parallax source) {
        if (mMarkerValues.size() < 2) {
            return;
        }
        if (this instanceof IntEffect) {
            source.verifyIntProperties();
        } else {
            source.verifyFloatProperties();
        }
        boolean fractionCalculated = false;
        float fraction = 0;
        Number directValue = null;
        for (int i = 0; i < mTargets.size(); i++) {
            ParallaxTarget target = mTargets.get(i);
            if (target.isDirectMapping()) {
                if (directValue == null) {
                    directValue = calculateDirectValue(source);
                }
                target.directUpdate(directValue);
            } else {
                if (!fractionCalculated) {
                    fractionCalculated = true;
                    fraction = calculateFraction(source);
                }
                target.update(fraction);
            }
        }
    }

    /**
     * This method is expected to compute a fraction between 0 and 1 based on the current values of
     * variables in {@link Parallax}. As the parallax effect goes on, the fraction increases
     * from 0 at beginning to 1 at the end.
     *
     * @return Float value between 0 and 1.
     */
    abstract float calculateFraction(Parallax source);

    /**
     * This method is expected to get the current value of the single {@link IntProperty} or
     * {@link FloatProperty}.
     *
     * @return Current value of the single {@link IntProperty} or {@link FloatProperty}.
     */
    abstract Number calculateDirectValue(Parallax source);

    /**
     * When there are multiple ranges (aka three or more markerValues),  this method adjust the
     * fraction inside a range to fraction of whole range.
     * e.g. four marker values, three weight values: 6, 2, 2.  totalWeights are 6, 8, 10
     * When markerValueIndex is 3, the fraction is inside last range.
     * adjusted_fraction = 8 / 10 + 2 / 10 * fraction.
     */
    final float getFractionWithWeightAdjusted(float fraction, int markerValueIndex) {
        // when there are three or more markerValues, take weight into consideration.
        if (mMarkerValues.size() >= 3) {
            final boolean hasWeightsDefined = mWeights.size() == mMarkerValues.size() - 1;
            if (hasWeightsDefined) {
                // use weights user defined
                final float allWeights = mTotalWeights.get(mTotalWeights.size() - 1);
                fraction = fraction * mWeights.get(markerValueIndex - 1) / allWeights;
                if (markerValueIndex >= 2) {
                    fraction += mTotalWeights.get(markerValueIndex - 2) / allWeights;
                }
            } else {
                // assume each range has same weight.
                final float allWeights =  mMarkerValues.size() - 1;
                fraction = fraction / allWeights;
                if (markerValueIndex >= 2) {
                    fraction += (float) (markerValueIndex - 1) / allWeights;
                }
            }
        }
        return fraction;
    }

    /**
     * Implementation of {@link ParallaxEffect} for integer type.
     */
    static final class IntEffect extends ParallaxEffect {

        @Override
        Number calculateDirectValue(Parallax source) {
            if (mMarkerValues.size() != 2) {
                throw new RuntimeException("Must use two marker values for direct mapping");
            }
            if (mMarkerValues.get(0).getProperty() != mMarkerValues.get(1).getProperty()) {
                throw new RuntimeException(
                        "Marker value must use same Property for direct mapping");
            }
            int value1 = ((Parallax.IntPropertyMarkerValue) mMarkerValues.get(0))
                    .getMarkerValue(source);
            int value2 = ((Parallax.IntPropertyMarkerValue) mMarkerValues.get(1))
                    .getMarkerValue(source);
            if (value1 > value2) {
                int swapValue = value2;
                value2 = value1;
                value1 = swapValue;
            }

            Number currentValue = ((IntProperty) mMarkerValues.get(0).getProperty()).get(source);
            if (currentValue.intValue() < value1) {
                currentValue = value1;
            } else if (currentValue.intValue() > value2) {
                currentValue = value2;
            }
            return currentValue;
        }

        @Override
        float calculateFraction(Parallax source) {
            int lastIndex = 0;
            int lastValue = 0;
            int lastMarkerValue = 0;
            // go through all markerValues, find first markerValue that current value is less than.
            for (int i = 0; i <  mMarkerValues.size(); i++) {
                Parallax.IntPropertyMarkerValue k =  (Parallax.IntPropertyMarkerValue)
                        mMarkerValues.get(i);
                int index = k.getProperty().getIndex();
                int markerValue = k.getMarkerValue(source);
                int currentValue = source.getIntPropertyValue(index);

                float fraction;
                if (i == 0) {
                    if (currentValue >= markerValue) {
                        return 0f;
                    }
                } else {
                    if (lastIndex == index && lastMarkerValue < markerValue) {
                        throw new IllegalStateException("marker value of same variable must be "
                                + "descendant order");
                    }
                    if (currentValue == IntProperty.UNKNOWN_AFTER) {
                        // Implies lastValue is less than lastMarkerValue and lastValue is not
                        // UNKNWON_AFTER.  Estimates based on distance of two variables is screen
                        // size.
                        fraction = (float) (lastMarkerValue - lastValue)
                                / source.getMaxValue();
                        return getFractionWithWeightAdjusted(fraction, i);
                    } else if (currentValue >= markerValue) {
                        if (lastIndex == index) {
                            // same variable index,  same UI element at two different MarkerValues,
                            // e.g. UI element moves from lastMarkerValue=500 to markerValue=0,
                            // fraction moves from 0 to 1.
                            fraction = (float) (lastMarkerValue - currentValue)
                                    / (lastMarkerValue - markerValue);
                        } else if (lastValue != IntProperty.UNKNOWN_BEFORE) {
                            // e.g. UIElement_1 at 300 scroll to UIElement_2 at 400, figure out when
                            // UIElement_1 is at markerValue=300,  markerValue of UIElement_2 by
                            // adding delta of values to markerValue of UIElement_2.
                            lastMarkerValue = lastMarkerValue + (currentValue - lastValue);
                            fraction = (float) (lastMarkerValue - currentValue)
                                    / (lastMarkerValue - markerValue);
                        } else {
                            // Last variable is UNKNOWN_BEFORE.  Estimates based on assumption total
                            // travel distance from last variable to this variable is screen visible
                            // size.
                            fraction = 1f - (float) (currentValue - markerValue)
                                    / source.getMaxValue();
                        }
                        return getFractionWithWeightAdjusted(fraction, i);
                    }
                }
                lastValue = currentValue;
                lastIndex = index;
                lastMarkerValue = markerValue;
            }
            return 1f;
        }
    }

    /**
     * Implementation of {@link ParallaxEffect} for float type.
     */
    static final class FloatEffect extends ParallaxEffect {

        @Override
        Number calculateDirectValue(Parallax source) {
            if (mMarkerValues.size() != 2) {
                throw new RuntimeException("Must use two marker values for direct mapping");
            }
            if (mMarkerValues.get(0).getProperty() != mMarkerValues.get(1).getProperty()) {
                throw new RuntimeException(
                        "Marker value must use same Property for direct mapping");
            }
            float value1 = ((FloatPropertyMarkerValue) mMarkerValues.get(0))
                    .getMarkerValue(source);
            float value2 = ((FloatPropertyMarkerValue) mMarkerValues.get(1))
                    .getMarkerValue(source);
            if (value1 > value2) {
                float swapValue = value2;
                value2 = value1;
                value1 = swapValue;
            }

            Number currentValue = ((FloatProperty) mMarkerValues.get(0).getProperty()).get(source);
            if (currentValue.floatValue() < value1) {
                currentValue = value1;
            } else if (currentValue.floatValue() > value2) {
                currentValue = value2;
            }
            return currentValue;
        }

        @Override
        float calculateFraction(Parallax source) {
            int lastIndex = 0;
            float lastValue = 0;
            float lastMarkerValue = 0;
            // go through all markerValues, find first markerValue that current value is less than.
            for (int i = 0; i <  mMarkerValues.size(); i++) {
                FloatPropertyMarkerValue k = (FloatPropertyMarkerValue) mMarkerValues.get(i);
                int index = k.getProperty().getIndex();
                float markerValue = k.getMarkerValue(source);
                float currentValue = source.getFloatPropertyValue(index);

                float fraction;
                if (i == 0) {
                    if (currentValue >= markerValue) {
                        return 0f;
                    }
                } else {
                    if (lastIndex == index && lastMarkerValue < markerValue) {
                        throw new IllegalStateException("marker value of same variable must be "
                                + "descendant order");
                    }
                    if (currentValue == FloatProperty.UNKNOWN_AFTER) {
                        // Implies lastValue is less than lastMarkerValue and lastValue is not
                        // UNKNOWN_AFTER.  Estimates based on distance of two variables is screen
                        // size.
                        fraction = (float) (lastMarkerValue - lastValue)
                                / source.getMaxValue();
                        return getFractionWithWeightAdjusted(fraction, i);
                    } else if (currentValue >= markerValue) {
                        if (lastIndex == index) {
                            // same variable index,  same UI element at two different MarkerValues,
                            // e.g. UI element moves from lastMarkerValue=500 to markerValue=0,
                            // fraction moves from 0 to 1.
                            fraction = (float) (lastMarkerValue - currentValue)
                                    / (lastMarkerValue - markerValue);
                        } else if (lastValue != FloatProperty.UNKNOWN_BEFORE) {
                            // e.g. UIElement_1 at 300 scroll to UIElement_2 at 400, figure out when
                            // UIElement_1 is at markerValue=300,  markerValue of UIElement_2 by
                            // adding delta of values to markerValue of UIElement_2.
                            lastMarkerValue = lastMarkerValue + (currentValue - lastValue);
                            fraction = (float) (lastMarkerValue - currentValue)
                                    / (lastMarkerValue - markerValue);
                        } else {
                            // Last variable is UNKNOWN_BEFORE.  Estimates based on assumption total
                            // travel distance from last variable to this variable is screen visible
                            // size.
                            fraction = 1f - (float) (currentValue - markerValue)
                                    / source.getMaxValue();
                        }
                        return getFractionWithWeightAdjusted(fraction, i);
                    }
                }
                lastValue = currentValue;
                lastIndex = index;
                lastMarkerValue = markerValue;
            }
            return 1f;
        }
    }

}


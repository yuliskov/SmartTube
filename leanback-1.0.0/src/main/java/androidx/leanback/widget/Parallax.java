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

import android.util.Property;

import androidx.annotation.CallSuper;
import androidx.leanback.widget.ParallaxEffect.FloatEffect;
import androidx.leanback.widget.ParallaxEffect.IntEffect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parallax tracks a list of dynamic {@link Property}s typically representing foreground UI
 * element positions on screen. Parallax keeps a list of {@link ParallaxEffect} objects which define
 * rules to mapping property values to {@link ParallaxTarget}.
 *
 * <p>
 * Example:
 * <code>
 *     // when Property "var1" changes from 15 to max value, perform parallax effect to
 *     // change myView's translationY from 0 to 100.
 *     Parallax<IntProperty> parallax = new Parallax<IntProperty>() {...};
 *     p1 = parallax.addProperty("var1");
 *     parallax.addEffect(p1.at(15), p1.atMax())
 *             .target(myView, PropertyValuesHolder.ofFloat("translationY", 0, 100));
 * </code>
 * </p>
 *
 * <p>
 * To create a {@link ParallaxEffect}, user calls {@link #addEffect(PropertyMarkerValue[])} with a
 * list of {@link PropertyMarkerValue} which defines the range of {@link Parallax.IntProperty} or
 * {@link Parallax.FloatProperty}. Then user adds {@link ParallaxTarget} into
 * {@link ParallaxEffect}.
 * </p>
 * <p>
 * App may subclass {@link Parallax.IntProperty} or {@link Parallax.FloatProperty} to supply
 * additional information about how to retrieve Property value.  {@link RecyclerViewParallax} is
 * a great example of Parallax implementation tracking child view positions on screen.
 * </p>
 * <p>
 * <ul>Restrictions of properties
 * <li>FloatProperty and IntProperty cannot be mixed in one Parallax</li>
 * <li>Values must be in ascending order.</li>
 * <li>If the UI element is unknown above screen, use UNKNOWN_BEFORE.</li>
 * <li>if the UI element is unknown below screen, use UNKNOWN_AFTER.</li>
 * <li>UNKNOWN_BEFORE and UNKNOWN_AFTER are not allowed to be next to each other.</li>
 * </ul>
 * These rules will be verified at runtime.
 * </p>
 * <p>
 * Subclass must override {@link #updateValues()} to update property values and perform
 * {@link ParallaxEffect}s. Subclass may call {@link #updateValues()} automatically e.g.
 * {@link RecyclerViewParallax} calls {@link #updateValues()} in RecyclerView scrolling. App might
 * call {@link #updateValues()} manually when Parallax is unaware of the value change. For example,
 * when a slide transition is running, {@link RecyclerViewParallax} is unaware of translation value
 * changes; it's the app's responsibility to call {@link #updateValues()} in every frame of
 * animation.
 * </p>
 * @param <PropertyT> Subclass of {@link Parallax.IntProperty} or {@link Parallax.FloatProperty}
 */
public abstract class Parallax<PropertyT extends android.util.Property> {

    /**
     * Class holding a fixed value for a Property in {@link Parallax}.
     * @param <PropertyT> Class of the property, e.g. {@link IntProperty} or {@link FloatProperty}.
     */
    public static class PropertyMarkerValue<PropertyT> {
        private final PropertyT mProperty;

        public PropertyMarkerValue(PropertyT property) {
            mProperty = property;
        }

        /**
         * @return Associated property.
         */
        public PropertyT getProperty() {
            return mProperty;
        }
    }

    /**
     * IntProperty provide access to an index based integer type property inside
     * {@link Parallax}. The IntProperty typically represents UI element position inside
     * {@link Parallax}.
     */
    public static class IntProperty extends Property<Parallax, Integer> {

        /**
         * Property value is unknown and it's smaller than minimal value of Parallax. For
         * example if a child is not created and before the first visible child of RecyclerView.
         */
        public static final int UNKNOWN_BEFORE = Integer.MIN_VALUE;

        /**
         * Property value is unknown and it's larger than {@link Parallax#getMaxValue()}. For
         * example if a child is not created and after the last visible child of RecyclerView.
         */
        public static final int UNKNOWN_AFTER = Integer.MAX_VALUE;

        private final int mIndex;

        /**
         * Constructor.
         *
         * @param name Name of this Property.
         * @param index Index of this Property inside {@link Parallax}.
         */
        public IntProperty(String name, int index) {
            super(Integer.class, name);
            mIndex = index;
        }

        @Override
        public final Integer get(Parallax object) {
            return object.getIntPropertyValue(mIndex);
        }

        @Override
        public final void set(Parallax object, Integer value) {
            object.setIntPropertyValue(mIndex, value);
        }

        /**
         * @return Index of this Property in {@link Parallax}.
         */
        public final int getIndex() {
            return mIndex;
        }

        /**
         * Fast version of get() method that returns a primitive int value of the Property.
         * @param object The Parallax object that owns this Property.
         * @return Int value of the Property.
         */
        public final int getValue(Parallax object) {
            return object.getIntPropertyValue(mIndex);
        }

        /**
         * Fast version of set() method that takes a primitive int value into the Property.
         *
         * @param object The Parallax object that owns this Property.
         * @param value Int value of the Property.
         */
        public final void setValue(Parallax object, int value) {
            object.setIntPropertyValue(mIndex, value);
        }

        /**
         * Creates an {@link PropertyMarkerValue} object for the absolute marker value.
         *
         * @param absoluteValue The integer marker value.
         * @return A new {@link PropertyMarkerValue} object.
         */
        public final PropertyMarkerValue atAbsolute(int absoluteValue) {
            return new IntPropertyMarkerValue(this, absoluteValue, 0f);
        }

        /**
         * Creates an {@link PropertyMarkerValue} object for the marker value representing
         * {@link Parallax#getMaxValue()}.
         *
         * @return A new {@link PropertyMarkerValue} object.
         */
        public final PropertyMarkerValue atMax() {
            return new IntPropertyMarkerValue(this, 0, 1f);
        }

        /**
         * Creates an {@link PropertyMarkerValue} object for the marker value representing 0.
         *
         * @return A new {@link PropertyMarkerValue} object.
         */
        public final PropertyMarkerValue atMin() {
            return new IntPropertyMarkerValue(this, 0);
        }

        /**
         * Creates an {@link PropertyMarkerValue} object for a fraction of
         * {@link Parallax#getMaxValue()}.
         *
         * @param fractionOfMaxValue 0 to 1 fraction to multiply with
         *                                       {@link Parallax#getMaxValue()} for
         *                                       the marker value.
         * @return A new {@link PropertyMarkerValue} object.
         */
        public final PropertyMarkerValue atFraction(float fractionOfMaxValue) {
            return new IntPropertyMarkerValue(this, 0, fractionOfMaxValue);
        }

        /**
         * Create an {@link PropertyMarkerValue} object by multiplying the fraction with
         * {@link Parallax#getMaxValue()} and adding offsetValue to it.
         *
         * @param offsetValue                    An offset integer value to be added to marker
         *                                       value.
         * @param fractionOfMaxParentVisibleSize 0 to 1 fraction to multiply with
         *                                       {@link Parallax#getMaxValue()} for
         *                                       the marker value.
         * @return A new {@link PropertyMarkerValue} object.
         */
        public final PropertyMarkerValue at(int offsetValue,
                float fractionOfMaxParentVisibleSize) {
            return new IntPropertyMarkerValue(this, offsetValue, fractionOfMaxParentVisibleSize);
        }
    }

    /**
     * Implementation of {@link PropertyMarkerValue} for {@link IntProperty}.
     */
    static class IntPropertyMarkerValue extends PropertyMarkerValue<IntProperty> {
        private final int mValue;
        private final float mFactionOfMax;

        IntPropertyMarkerValue(IntProperty property, int value) {
            this(property, value, 0f);
        }

        IntPropertyMarkerValue(IntProperty property, int value, float fractionOfMax) {
            super(property);
            mValue = value;
            mFactionOfMax = fractionOfMax;
        }

        /**
         * @return The marker value of integer type.
         */
        final int getMarkerValue(Parallax source) {
            return mFactionOfMax == 0 ? mValue : mValue + Math.round(source
                    .getMaxValue() * mFactionOfMax);
        }
    }

    /**
     * FloatProperty provide access to an index based integer type property inside
     * {@link Parallax}. The FloatProperty typically represents UI element position inside
     * {@link Parallax}.
     */
    public static class FloatProperty extends Property<Parallax, Float> {

        /**
         * Property value is unknown and it's smaller than minimal value of Parallax. For
         * example if a child is not created and before the first visible child of RecyclerView.
         */
        public static final float UNKNOWN_BEFORE = -Float.MAX_VALUE;

        /**
         * Property value is unknown and it's larger than {@link Parallax#getMaxValue()}. For
         * example if a child is not created and after the last visible child of RecyclerView.
         */
        public static final float UNKNOWN_AFTER = Float.MAX_VALUE;

        private final int mIndex;

        /**
         * Constructor.
         *
         * @param name Name of this Property.
         * @param index Index of this Property inside {@link Parallax}.
         */
        public FloatProperty(String name, int index) {
            super(Float.class, name);
            mIndex = index;
        }

        @Override
        public final Float get(Parallax object) {
            return object.getFloatPropertyValue(mIndex);
        }

        @Override
        public final void set(Parallax object, Float value) {
            object.setFloatPropertyValue(mIndex, value);
        }

        /**
         * @return Index of this Property in {@link Parallax}.
         */
        public final int getIndex() {
            return mIndex;
        }

        /**
         * Fast version of get() method that returns a primitive int value of the Property.
         * @param object The Parallax object that owns this Property.
         * @return Float value of the Property.
         */
        public final float getValue(Parallax object) {
            return object.getFloatPropertyValue(mIndex);
        }

        /**
         * Fast version of set() method that takes a primitive float value into the Property.
         *
         * @param object The Parallax object that owns this Property.
         * @param value Float value of the Property.
         */
        public final void setValue(Parallax object, float value) {
            object.setFloatPropertyValue(mIndex, value);
        }

        /**
         * Creates an {@link PropertyMarkerValue} object for the absolute marker value.
         *
         * @param markerValue The float marker value.
         * @return A new {@link PropertyMarkerValue} object.
         */
        public final PropertyMarkerValue atAbsolute(float markerValue) {
            return new FloatPropertyMarkerValue(this, markerValue, 0f);
        }

        /**
         * Creates an {@link PropertyMarkerValue} object for the marker value representing
         * {@link Parallax#getMaxValue()}.
         *
         * @return A new {@link PropertyMarkerValue} object.
         */
        public final PropertyMarkerValue atMax() {
            return new FloatPropertyMarkerValue(this, 0, 1f);
        }

        /**
         * Creates an {@link PropertyMarkerValue} object for the marker value representing 0.
         *
         * @return A new {@link PropertyMarkerValue} object.
         */
        public final PropertyMarkerValue atMin() {
            return new FloatPropertyMarkerValue(this, 0);
        }

        /**
         * Creates an {@link PropertyMarkerValue} object for a fraction of
         * {@link Parallax#getMaxValue()}.
         *
         * @param fractionOfMaxParentVisibleSize 0 to 1 fraction to multiply with
         *                                       {@link Parallax#getMaxValue()} for
         *                                       the marker value.
         * @return A new {@link PropertyMarkerValue} object.
         */
        public final PropertyMarkerValue atFraction(float fractionOfMaxParentVisibleSize) {
            return new FloatPropertyMarkerValue(this, 0, fractionOfMaxParentVisibleSize);
        }

        /**
         * Create an {@link PropertyMarkerValue} object by multiplying the fraction with
         * {@link Parallax#getMaxValue()} and adding offsetValue to it.
         *
         * @param offsetValue                    An offset float value to be added to marker value.
         * @param fractionOfMaxParentVisibleSize 0 to 1 fraction to multiply with
         *                                       {@link Parallax#getMaxValue()} for
         *                                       the marker value.
         * @return A new {@link PropertyMarkerValue} object.
         */
        public final PropertyMarkerValue at(float offsetValue,
                float fractionOfMaxParentVisibleSize) {
            return new FloatPropertyMarkerValue(this, offsetValue, fractionOfMaxParentVisibleSize);
        }
    }

    /**
     * Implementation of {@link PropertyMarkerValue} for {@link FloatProperty}.
     */
    static class FloatPropertyMarkerValue extends PropertyMarkerValue<FloatProperty> {
        private final float mValue;
        private final float mFactionOfMax;

        FloatPropertyMarkerValue(FloatProperty property, float value) {
            this(property, value, 0f);
        }

        FloatPropertyMarkerValue(FloatProperty property, float value, float fractionOfMax) {
            super(property);
            mValue = value;
            mFactionOfMax = fractionOfMax;
        }

        /**
         * @return The marker value.
         */
        final float getMarkerValue(Parallax source) {
            return mFactionOfMax == 0 ? mValue : mValue + source.getMaxValue()
                    * mFactionOfMax;
        }
    }

    final List<PropertyT> mProperties = new ArrayList<PropertyT>();
    final List<PropertyT> mPropertiesReadOnly = Collections.unmodifiableList(mProperties);

    private int[] mValues = new int[4];
    private float[] mFloatValues = new float[4];

    private final List<ParallaxEffect> mEffects = new ArrayList<ParallaxEffect>(4);

    /**
     * Return the max value which is typically size of parent visible area, e.g. RecyclerView's
     * height if we are tracking Y position of a child. The size can be used to calculate marker
     * value using the provided fraction of FloatPropertyMarkerValue.
     *
     * @return Size of parent visible area.
     */
    public abstract float getMaxValue();

    /**
     * Get index based property value.
     *
     * @param index Index of the property.
     * @return Value of the property.
     */
    final int getIntPropertyValue(int index) {
        return mValues[index];
    }

    /**
     * Set index based property value.
     *
     * @param index Index of the property.
     * @param value Value of the property.
     */
    final void setIntPropertyValue(int index, int value) {
        if (index >= mProperties.size()) {
            throw new ArrayIndexOutOfBoundsException();
        }
        mValues[index] = value;
    }

    /**
     * Add a new IntProperty in the Parallax object. App may override
     * {@link #createProperty(String, int)}.
     *
     * @param name Name of the property.
     * @return Newly created Property object.
     * @see #createProperty(String, int)
     */
    public final PropertyT addProperty(String name) {
        int newPropertyIndex = mProperties.size();
        PropertyT property = createProperty(name, newPropertyIndex);
        if (property instanceof IntProperty) {
            int size = mValues.length;
            if (size == newPropertyIndex) {
                int[] newValues = new int[size * 2];
                for (int i = 0; i < size; i++) {
                    newValues[i] = mValues[i];
                }
                mValues = newValues;
            }
            mValues[newPropertyIndex] = IntProperty.UNKNOWN_AFTER;
        } else if (property instanceof FloatProperty) {
            int size = mFloatValues.length;
            if (size == newPropertyIndex) {
                float[] newValues = new float[size * 2];
                for (int i = 0; i < size; i++) {
                    newValues[i] = mFloatValues[i];
                }
                mFloatValues = newValues;
            }
            mFloatValues[newPropertyIndex] = FloatProperty.UNKNOWN_AFTER;
        } else {
            throw new IllegalArgumentException("Invalid Property type");
        }
        mProperties.add(property);
        return property;
    }

    /**
     * Verify sanity of property values, throws RuntimeException if fails. The property values
     * must be in ascending order. UNKNOW_BEFORE and UNKNOWN_AFTER are not allowed to be next to
     * each other.
     */
    void verifyIntProperties() throws IllegalStateException {
        if (mProperties.size() < 2) {
            return;
        }
        int last = getIntPropertyValue(0);
        for (int i = 1; i < mProperties.size(); i++) {
            int v = getIntPropertyValue(i);
            if (v < last) {
                throw new IllegalStateException(String.format("Parallax Property[%d]\"%s\" is"
                                + " smaller than Property[%d]\"%s\"",
                        i, mProperties.get(i).getName(),
                        i - 1, mProperties.get(i - 1).getName()));
            } else if (last == IntProperty.UNKNOWN_BEFORE && v == IntProperty.UNKNOWN_AFTER) {
                throw new IllegalStateException(String.format("Parallax Property[%d]\"%s\" is"
                                + " UNKNOWN_BEFORE and Property[%d]\"%s\" is UNKNOWN_AFTER",
                        i - 1, mProperties.get(i - 1).getName(),
                        i, mProperties.get(i).getName()));
            }
            last = v;
        }
    }

    final void verifyFloatProperties() throws IllegalStateException {
        if (mProperties.size() < 2) {
            return;
        }
        float last = getFloatPropertyValue(0);
        for (int i = 1; i < mProperties.size(); i++) {
            float v = getFloatPropertyValue(i);
            if (v < last) {
                throw new IllegalStateException(String.format("Parallax Property[%d]\"%s\" is"
                                + " smaller than Property[%d]\"%s\"",
                        i, mProperties.get(i).getName(),
                        i - 1, mProperties.get(i - 1).getName()));
            } else if (last == FloatProperty.UNKNOWN_BEFORE && v
                    == FloatProperty.UNKNOWN_AFTER) {
                throw new IllegalStateException(String.format("Parallax Property[%d]\"%s\" is"
                                + " UNKNOWN_BEFORE and Property[%d]\"%s\" is UNKNOWN_AFTER",
                        i - 1, mProperties.get(i - 1).getName(),
                        i, mProperties.get(i).getName()));
            }
            last = v;
        }
    }

    /**
     * Get index based property value.
     *
     * @param index Index of the property.
     * @return Value of the property.
     */
    final float getFloatPropertyValue(int index) {
        return mFloatValues[index];
    }

    /**
     * Set index based property value.
     *
     * @param index Index of the property.
     * @param value Value of the property.
     */
    final void setFloatPropertyValue(int index, float value) {
        if (index >= mProperties.size()) {
            throw new ArrayIndexOutOfBoundsException();
        }
        mFloatValues[index] = value;
    }

    /**
     * @return A unmodifiable list of properties.
     */
    public final List<PropertyT> getProperties() {
        return mPropertiesReadOnly;
    }

    /**
     * Create a new Property object. App does not directly call this method.  See
     * {@link #addProperty(String)}.
     *
     * @param index  Index of the property in this Parallax object.
     * @return Newly created Property object.
     */
    public abstract PropertyT createProperty(String name, int index);

    /**
     * Update property values and perform {@link ParallaxEffect}s. Subclass may override and call
     * super.updateValues() after updated properties values.
     */
    @CallSuper
    public void updateValues() {
        for (int i = 0; i < mEffects.size(); i++) {
            mEffects.get(i).performMapping(this);
        }
    }

    /**
     * Returns a list of {@link ParallaxEffect} object which defines rules to perform mapping to
     * multiple {@link ParallaxTarget}s.
     *
     * @return A list of {@link ParallaxEffect} object.
     */
    public List<ParallaxEffect> getEffects() {
        return mEffects;
    }

    /**
     * Remove the {@link ParallaxEffect} object.
     *
     * @param effect The {@link ParallaxEffect} object to remove.
     */
    public void removeEffect(ParallaxEffect effect) {
        mEffects.remove(effect);
    }

    /**
     * Remove all {@link ParallaxEffect} objects.
     */
    public void removeAllEffects() {
        mEffects.clear();
    }

    /**
     * Create a {@link ParallaxEffect} object that will track source variable changes within a
     * provided set of ranges.
     *
     * @param ranges A list of marker values that defines the ranges.
     * @return Newly created ParallaxEffect object.
     */
    public ParallaxEffect addEffect(PropertyMarkerValue... ranges) {
        ParallaxEffect effect;
        if (ranges[0].getProperty() instanceof IntProperty) {
            effect = new IntEffect();
        } else {
            effect = new FloatEffect();
        }
        effect.setPropertyRanges(ranges);
        mEffects.add(effect);
        return effect;
    }

}

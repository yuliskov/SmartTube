/*
 * Copyright (C) 2015 The Android Open Source Project
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
package androidx.leanback.graphics;

import android.graphics.Rect;

/**
 * This class contains the rules for updating the bounds of a
 * {@link CompositeDrawable.ChildDrawable}. It contains four rules, one for each value of the
 * rectangular bound - left/top/right/bottom.
 */
public class BoundsRule {

    /**
     * This class represents individual rules for updating the bounds.
     */
    public final static class ValueRule {
        float mFraction;
        int mAbsoluteValue;

        /**
         * Creates ValueRule using a fraction of parent size.
         *
         * @param fraction Percentage of parent.
         * @return Newly created ValueRule.
         */
        public static ValueRule inheritFromParent(float fraction) {
            return new ValueRule(0, fraction);
        }

        /**
         * Creates ValueRule using an absolute value.
         *
         * @param absoluteValue Absolute value.
         * @return Newly created ValueRule.
         */
        public static ValueRule absoluteValue(int absoluteValue) {
            return new ValueRule(absoluteValue, 0);
        }

        /**
         * Creates ValueRule of fraction and offset.
         *
         * @param fraction Percentage of parent.
         * @param value    Offset
         * @return Newly created ValueRule.
         */
        public static ValueRule inheritFromParentWithOffset(float fraction, int value) {
            return new ValueRule(value, fraction);
        }

        ValueRule(int absoluteValue, float fraction) {
            this.mAbsoluteValue = absoluteValue;
            this.mFraction = fraction;
        }

        ValueRule(ValueRule rule) {
            this.mFraction = rule.mFraction;
            this.mAbsoluteValue = rule.mAbsoluteValue;
        }

        /**
         * Sets the fractional value (percentage of parent) for this rule.
         *
         * @param fraction Percentage of parent.
         */
        public void setFraction(float fraction) {
            this.mFraction = fraction;
        }

        /**
         * @return The current fractional value.
         */
        public float getFraction() {
            return mFraction;
        }

        /**
         * Sets the absolute/offset value for rule.
         *
         * @param absoluteValue Absolute value.
         */
        public void setAbsoluteValue(int absoluteValue) {
            this.mAbsoluteValue = absoluteValue;
        }

        /**
         * @return The current absolute/offset value forrule.
         */
        public int getAbsoluteValue() {
            return mAbsoluteValue;
        }

    }

    /**
     * Takes in the current bounds and sets the final values based on the individual rules in the
     * result object.
     *
     * @param rect Represents the current bounds.
     * @param result Represents the final bounds.
     */
    public void calculateBounds(Rect rect, Rect result) {
        if (left == null) {
            result.left = rect.left;
        } else {
            result.left = doCalculate(rect.left, left, rect.width());
        }

        if (right == null) {
            result.right = rect.right;
        } else {
            result.right = doCalculate(rect.left, right, rect.width());
        }

        if (top == null) {
            result.top = rect.top;
        } else {
            result.top = doCalculate(rect.top, top, rect.height());
        }

        if (bottom == null) {
            result.bottom = rect.bottom;
        } else {
            result.bottom = doCalculate(rect.top, bottom, rect.height());
        }
    }

    public BoundsRule() {}

    public BoundsRule(BoundsRule boundsRule) {
        this.left = boundsRule.left != null ? new ValueRule(boundsRule.left) : null;
        this.right = boundsRule.right != null ? new ValueRule(boundsRule.right) : null;
        this.top = boundsRule.top != null ? new ValueRule(boundsRule.top) : null;
        this.bottom = boundsRule.bottom != null ? new ValueRule(boundsRule.bottom) : null;
    }

    private int doCalculate(int value, ValueRule rule, int size) {
        return value + rule.mAbsoluteValue + (int) (rule.mFraction * size);
    }

    /** {@link ValueRule} for left attribute of {@link BoundsRule} */
    public ValueRule left;

    /** {@link ValueRule} for top attribute of {@link BoundsRule} */
    public ValueRule top;

    /** {@link ValueRule} for right attribute of {@link BoundsRule} */
    public ValueRule right;

    /** {@link ValueRule} for bottom attribute of {@link BoundsRule} */
    public ValueRule bottom;
}

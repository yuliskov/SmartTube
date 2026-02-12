/*
 * Copyright (C) 2015 The Android Open Source Project
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
package androidx.leanback.widget;

import android.content.Context;
import android.os.Bundle;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Subclass of GuidedAction that can choose a date.  The Action is editable by default; to make it
 * read only, call hasEditableActivatorView(false) on the Builder.
 */
public class GuidedDatePickerAction extends GuidedAction {

    /**
     * Base Builder class to build GuidedDatePickerAction.  Subclass this BuilderBase when app needs
     * to subclass GuidedDatePickerAction, implement your build() which should call
     * {@link #applyDatePickerValues(GuidedDatePickerAction)}.  When using GuidedDatePickerAction
     * directly, use {@link Builder}.
     */
    public abstract static class BuilderBase<B extends BuilderBase>
            extends GuidedAction.BuilderBase<B> {

        private String mDatePickerFormat;
        private long mDate;
        private long mMinDate = Long.MIN_VALUE;
        private long mMaxDate = Long.MAX_VALUE;

        public BuilderBase(Context context) {
            super(context);
            Calendar c = Calendar.getInstance();
            mDate = c.getTimeInMillis();
            hasEditableActivatorView(true);
        }

        /**
         * Sets format of date Picker or null for default.  The format is a case insensitive String
         * containing the day ('d'), month ('m'), and year ('y').  When the format is not specified,
         * a default format of current locale will be used.
         * @param format Format of showing Date, e.g. "YMD".
         * @return This Builder object.
         */
        public B datePickerFormat(String format) {
            mDatePickerFormat = format;
            return (B) this;
        }

        /**
         * Sets a Date for date picker in milliseconds since January 1, 1970 00:00:00 in
         * {@link TimeZone#getDefault()} time zone.
         * @return This Builder Object.
         */
        public B date(long date) {
            mDate = date;
            return (B) this;
        }

        /**
         * Sets minimal Date for date picker in milliseconds since January 1, 1970 00:00:00 in
         * {@link TimeZone#getDefault()} time zone.
         * @return This Builder Object.
         */
        public B minDate(long minDate) {
            mMinDate = minDate;
            return (B) this;
        }

        /**
         * Sets maximum Date for date picker in milliseconds since January 1, 1970 00:00:00 in
         * {@link TimeZone#getDefault()} time zone.
         * @return This Builder Object.
         */
        public B maxDate(long maxDate) {
            mMaxDate = maxDate;
            return (B) this;
        }

        /**
         * Apply values to GuidedDatePickerAction.
         * @param action GuidedDatePickerAction to apply values.
         */
        protected final void applyDatePickerValues(GuidedDatePickerAction action) {
            super.applyValues(action);
            action.mDatePickerFormat = mDatePickerFormat;
            action.mDate = mDate;
            if (mMinDate > mMaxDate) {
                throw new IllegalArgumentException("MinDate cannot be larger than MaxDate");
            }
            action.mMinDate = mMinDate;
            action.mMaxDate = mMaxDate;
        }

    }

    /**
     * Builder class to build a GuidedDatePickerAction.
     */
    public final static class Builder extends BuilderBase<Builder> {
        public Builder(Context context) {
            super(context);
        }

        /**
         * Builds the GuidedDatePickerAction corresponding to this Builder.
         * @return The GuidedDatePickerAction as configured through this Builder.
         */
        public GuidedDatePickerAction build() {
            GuidedDatePickerAction action = new GuidedDatePickerAction();
            applyDatePickerValues(action);
            return action;
        }
    }

    String mDatePickerFormat;
    long mDate;
    long mMinDate = Long.MIN_VALUE;
    long mMaxDate = Long.MAX_VALUE;

    /**
     * Returns format of date Picker or null if not specified.  The format is a case insensitive
     * String containing the * day ('d'), month ('m'), and year ('y'). When the format is not
     * specified, a default format of current locale will
     * be used.
     * @return Format of showing Date, e.g. "YMD".  Returns null if using current locale's default.
     */
    public String getDatePickerFormat() {
        return mDatePickerFormat;
    }

    /**
     * Get current value of DatePicker in milliseconds since January 1, 1970 00:00:00 in
     * {@link TimeZone#getDefault()} time zone.
     * @return Current value of DatePicker Action.
     */
    public long getDate() {
        return mDate;
    }

    /**
     * Sets current value of DatePicker in milliseconds since January 1, 1970 00:00:00 in
     * {@link TimeZone#getDefault()} time zone.
     * @param date New value to update current value of DatePicker Action.
     */
    public void setDate(long date) {
        mDate = date;
    }

    /**
     * Get minimal value of DatePicker in milliseconds since January 1, 1970 00:00:00 in
     * {@link TimeZone#getDefault()} time zone.  -1 if not set.
     * @return Minimal value of DatePicker Action or Long.MIN_VALUE if not set.
     */
    public long getMinDate() {
        return mMinDate;
    }

    /**
     * Get maximum value of DatePicker in milliseconds since January 1, 1970 00:00:00 in
     * {@link TimeZone#getDefault()} time zone.
     * @return Maximum value of DatePicker Action or Long.MAX_VALUE if not set.
     */
    public long getMaxDate() {
        return mMaxDate;
    }

    @Override
    public void onSaveInstanceState(Bundle bundle, String key) {
        bundle.putLong(key, getDate());
    }

    @Override
    public void onRestoreInstanceState(Bundle bundle, String key) {
        setDate(bundle.getLong(key, getDate()));
    }
}

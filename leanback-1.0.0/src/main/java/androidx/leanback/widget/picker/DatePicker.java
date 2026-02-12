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

package androidx.leanback.widget.picker;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;

import androidx.annotation.RestrictTo;
import androidx.leanback.R;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * {@link DatePicker} is a directly subclass of {@link Picker}.
 * This class is a widget for selecting a date. The date can be selected by a
 * year, month, and day Columns. The "minDate" and "maxDate" from which dates to be selected
 * can be customized.  The columns can be customized by attribute "datePickerFormat" or
 * {@link #setDatePickerFormat(String)}.
 *
 * @attr ref R.styleable#lbDatePicker_android_maxDate
 * @attr ref R.styleable#lbDatePicker_android_minDate
 * @attr ref R.styleable#lbDatePicker_datePickerFormat
 * @hide
 */
@RestrictTo(LIBRARY_GROUP)
public class DatePicker extends Picker {

    static final String LOG_TAG = "DatePicker";

    private String mDatePickerFormat;
    PickerColumn mMonthColumn;
    PickerColumn mDayColumn;
    PickerColumn mYearColumn;
    int mColMonthIndex;
    int mColDayIndex;
    int mColYearIndex;

    final static String DATE_FORMAT = "MM/dd/yyyy";
    final DateFormat mDateFormat = new SimpleDateFormat(DATE_FORMAT);
    PickerUtility.DateConstant mConstant;

    Calendar mMinDate;
    Calendar mMaxDate;
    Calendar mCurrentDate;
    Calendar mTempDate;

    public DatePicker(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DatePicker(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        updateCurrentLocale();

        final TypedArray attributesArray = context.obtainStyledAttributes(attrs,
                R.styleable.lbDatePicker);
        String minDate = attributesArray.getString(R.styleable.lbDatePicker_android_minDate);
        String maxDate = attributesArray.getString(R.styleable.lbDatePicker_android_maxDate);
        mTempDate.clear();
        if (!TextUtils.isEmpty(minDate)) {
            if (!parseDate(minDate, mTempDate)) {
                mTempDate.set(1900, 0, 1);
            }
        } else {
            mTempDate.set(1900, 0, 1);
        }
        mMinDate.setTimeInMillis(mTempDate.getTimeInMillis());

        mTempDate.clear();
        if (!TextUtils.isEmpty(maxDate)) {
            if (!parseDate(maxDate, mTempDate)) {
                mTempDate.set(2100, 0, 1);
            }
        } else {
            mTempDate.set(2100, 0, 1);
        }
        mMaxDate.setTimeInMillis(mTempDate.getTimeInMillis());

        String datePickerFormat = attributesArray
                .getString(R.styleable.lbDatePicker_datePickerFormat);
        if (TextUtils.isEmpty(datePickerFormat)) {
            datePickerFormat = new String(
                    android.text.format.DateFormat.getDateFormatOrder(context));
        }
        setDatePickerFormat(datePickerFormat);
    }

    private boolean parseDate(String date, Calendar outDate) {
        try {
            outDate.setTime(mDateFormat.parse(date));
            return true;
        } catch (ParseException e) {
            Log.w(LOG_TAG, "Date: " + date + " not in format: " + DATE_FORMAT);
            return false;
        }
    }

    /**
     * Returns the best localized representation of the date for the given date format and the
     * current locale.
     *
     * @param datePickerFormat The date format skeleton (e.g. "dMy") used to gather the
     *                         appropriate representation of the date in the current locale.
     *
     * @return The best localized representation of the date for the given date format
     */
    String getBestYearMonthDayPattern(String datePickerFormat) {
        final String yearPattern;
        if (PickerUtility.SUPPORTS_BEST_DATE_TIME_PATTERN) {
            yearPattern = android.text.format.DateFormat.getBestDateTimePattern(mConstant.locale,
                    datePickerFormat);
        } else {
            final java.text.DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(
                    getContext());
            if (dateFormat instanceof SimpleDateFormat) {
                yearPattern = ((SimpleDateFormat) dateFormat).toLocalizedPattern();
            } else {
                yearPattern = DATE_FORMAT;
            }
        }
        return TextUtils.isEmpty(yearPattern) ? DATE_FORMAT : yearPattern;
    }

    /**
     * Extracts the separators used to separate date fields (including before the first and after
     * the last date field). The separators can vary based on the individual locale date format,
     * defined in the Unicode CLDR and cannot be supposed to be "/".
     *
     * See http://unicode.org/cldr/trac/browser/trunk/common/main
     *
     * For example, for Croatian in dMy format, the best localized representation is "d. M. y". This
     * method returns {"", ".", ".", "."}, where the first separator indicates nothing needs to be
     * displayed to the left of the day field, "." needs to be displayed tos the right of the day
     * field, and so forth.
     *
     * @return The ArrayList of separators to populate between the actual date fields in the
     * DatePicker.
     */
    List<CharSequence> extractSeparators() {
        // Obtain the time format string per the current locale (e.g. h:mm a)
        String hmaPattern = getBestYearMonthDayPattern(mDatePickerFormat);

        List<CharSequence> separators = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        char lastChar = '\0';
        // See http://www.unicode.org/reports/tr35/tr35-dates.html for date formats
        final char[] dateFormats = {'Y', 'y', 'M', 'm', 'D', 'd'};
        boolean processingQuote = false;
        for (int i = 0; i < hmaPattern.length(); i++) {
            char c = hmaPattern.charAt(i);
            if (c == ' ') {
                continue;
            }
            if (c == '\'') {
                if (!processingQuote) {
                    sb.setLength(0);
                    processingQuote = true;
                } else {
                    processingQuote = false;
                }
                continue;
            }
            if (processingQuote) {
                sb.append(c);
            } else {
                if (isAnyOf(c, dateFormats)) {
                    if (c != lastChar) {
                        separators.add(sb.toString());
                        sb.setLength(0);
                    }
                } else {
                    sb.append(c);
                }
            }
            lastChar = c;
        }
        separators.add(sb.toString());
        return separators;
    }

    private static boolean isAnyOf(char c, char[] any) {
        for (int i = 0; i < any.length; i++) {
            if (c == any[i]) {
                return true;
            }
        }
        return false;
    }

    /**
     * Changes format of showing dates.  For example "YMD".
     * @param datePickerFormat Format of showing dates.
     */
    public void setDatePickerFormat(String datePickerFormat) {
        if (TextUtils.isEmpty(datePickerFormat)) {
            datePickerFormat = new String(
                    android.text.format.DateFormat.getDateFormatOrder(getContext()));
        }
        if (TextUtils.equals(mDatePickerFormat, datePickerFormat)) {
            return;
        }
        mDatePickerFormat = datePickerFormat;
        List<CharSequence> separators = extractSeparators();
        if (separators.size() != (datePickerFormat.length() + 1)) {
            throw new IllegalStateException("Separators size: " + separators.size() + " must equal"
                    + " the size of datePickerFormat: " + datePickerFormat.length() + " + 1");
        }
        setSeparators(separators);
        mYearColumn = mMonthColumn = mDayColumn = null;
        mColYearIndex = mColDayIndex = mColMonthIndex = -1;
        String dateFieldsPattern = datePickerFormat.toUpperCase();
        ArrayList<PickerColumn> columns = new ArrayList<PickerColumn>(3);
        for (int i = 0; i < dateFieldsPattern.length(); i++) {
            switch (dateFieldsPattern.charAt(i)) {
            case 'Y':
                if (mYearColumn != null) {
                    throw new IllegalArgumentException("datePicker format error");
                }
                columns.add(mYearColumn = new PickerColumn());
                mColYearIndex = i;
                mYearColumn.setLabelFormat("%d");
                break;
            case 'M':
                if (mMonthColumn != null) {
                    throw new IllegalArgumentException("datePicker format error");
                }
                columns.add(mMonthColumn = new PickerColumn());
                mMonthColumn.setStaticLabels(mConstant.months);
                mColMonthIndex = i;
                break;
            case 'D':
                if (mDayColumn != null) {
                    throw new IllegalArgumentException("datePicker format error");
                }
                columns.add(mDayColumn = new PickerColumn());
                mDayColumn.setLabelFormat("%02d");
                mColDayIndex = i;
                break;
            default:
                throw new IllegalArgumentException("datePicker format error");
            }
        }
        setColumns(columns);
        updateSpinners(false);
    }

    /**
     * Get format of showing dates.  For example "YMD".  Default value is from
     * {@link android.text.format.DateFormat#getDateFormatOrder(Context)}.
     * @return Format of showing dates.
     */
    public String getDatePickerFormat() {
        return mDatePickerFormat;
    }

    private void updateCurrentLocale() {
        mConstant = PickerUtility.getDateConstantInstance(Locale.getDefault(),
                getContext().getResources());
        mTempDate = PickerUtility.getCalendarForLocale(mTempDate, mConstant.locale);
        mMinDate = PickerUtility.getCalendarForLocale(mMinDate, mConstant.locale);
        mMaxDate = PickerUtility.getCalendarForLocale(mMaxDate, mConstant.locale);
        mCurrentDate = PickerUtility.getCalendarForLocale(mCurrentDate, mConstant.locale);

        if (mMonthColumn != null) {
            mMonthColumn.setStaticLabels(mConstant.months);
            setColumnAt(mColMonthIndex, mMonthColumn);
        }
    }

    @Override
    public final void onColumnValueChanged(int column, int newVal) {
        mTempDate.setTimeInMillis(mCurrentDate.getTimeInMillis());
        // take care of wrapping of days and months to update greater fields
        int oldVal = getColumnAt(column).getCurrentValue();
        if (column == mColDayIndex) {
            mTempDate.add(Calendar.DAY_OF_MONTH, newVal - oldVal);
        } else if (column == mColMonthIndex) {
            mTempDate.add(Calendar.MONTH, newVal - oldVal);
        } else if (column == mColYearIndex) {
            mTempDate.add(Calendar.YEAR, newVal - oldVal);
        } else {
            throw new IllegalArgumentException();
        }
        setDate(mTempDate.get(Calendar.YEAR), mTempDate.get(Calendar.MONTH),
                mTempDate.get(Calendar.DAY_OF_MONTH));
        updateSpinners(false);
    }


    /**
     * Sets the minimal date supported by this {@link DatePicker} in
     * milliseconds since January 1, 1970 00:00:00 in
     * {@link TimeZone#getDefault()} time zone.
     *
     * @param minDate The minimal supported date.
     */
    public void setMinDate(long minDate) {
        mTempDate.setTimeInMillis(minDate);
        if (mTempDate.get(Calendar.YEAR) == mMinDate.get(Calendar.YEAR)
                && mTempDate.get(Calendar.DAY_OF_YEAR) != mMinDate.get(Calendar.DAY_OF_YEAR)) {
            return;
        }
        mMinDate.setTimeInMillis(minDate);
        if (mCurrentDate.before(mMinDate)) {
            mCurrentDate.setTimeInMillis(mMinDate.getTimeInMillis());
        }
        updateSpinners(false);
    }


    /**
     * Gets the minimal date supported by this {@link DatePicker} in
     * milliseconds since January 1, 1970 00:00:00 in
     * {@link TimeZone#getDefault()} time zone.
     * <p>
     * Note: The default minimal date is 01/01/1900.
     * <p>
     *
     * @return The minimal supported date.
     */
    public long getMinDate() {
        return mMinDate.getTimeInMillis();
    }

    /**
     * Sets the maximal date supported by this {@link DatePicker} in
     * milliseconds since January 1, 1970 00:00:00 in
     * {@link TimeZone#getDefault()} time zone.
     *
     * @param maxDate The maximal supported date.
     */
    public void setMaxDate(long maxDate) {
        mTempDate.setTimeInMillis(maxDate);
        if (mTempDate.get(Calendar.YEAR) == mMaxDate.get(Calendar.YEAR)
                && mTempDate.get(Calendar.DAY_OF_YEAR) != mMaxDate.get(Calendar.DAY_OF_YEAR)) {
            return;
        }
        mMaxDate.setTimeInMillis(maxDate);
        if (mCurrentDate.after(mMaxDate)) {
            mCurrentDate.setTimeInMillis(mMaxDate.getTimeInMillis());
        }
        updateSpinners(false);
    }

    /**
     * Gets the maximal date supported by this {@link DatePicker} in
     * milliseconds since January 1, 1970 00:00:00 in
     * {@link TimeZone#getDefault()} time zone.
     * <p>
     * Note: The default maximal date is 12/31/2100.
     * <p>
     *
     * @return The maximal supported date.
     */
    public long getMaxDate() {
        return mMaxDate.getTimeInMillis();
    }

    /**
     * Gets current date value in milliseconds since January 1, 1970 00:00:00 in
     * {@link TimeZone#getDefault()} time zone.
     *
     * @return Current date values.
     */
    public long getDate() {
        return mCurrentDate.getTimeInMillis();
    }

    private void setDate(int year, int month, int dayOfMonth) {
        mCurrentDate.set(year, month, dayOfMonth);
        if (mCurrentDate.before(mMinDate)) {
            mCurrentDate.setTimeInMillis(mMinDate.getTimeInMillis());
        } else if (mCurrentDate.after(mMaxDate)) {
            mCurrentDate.setTimeInMillis(mMaxDate.getTimeInMillis());
        }
    }

    /**
     * Update the current date.
     *
     * @param year The year.
     * @param month The month which is <strong>starting from zero</strong>.
     * @param dayOfMonth The day of the month.
     * @param animation True to run animation to scroll the column.
     */
    public void updateDate(int year, int month, int dayOfMonth, boolean animation) {
        if (!isNewDate(year, month, dayOfMonth)) {
            return;
        }
        setDate(year, month, dayOfMonth);
        updateSpinners(animation);
    }

    private boolean isNewDate(int year, int month, int dayOfMonth) {
        return (mCurrentDate.get(Calendar.YEAR) != year
                || mCurrentDate.get(Calendar.MONTH) != dayOfMonth
                || mCurrentDate.get(Calendar.DAY_OF_MONTH) != month);
    }

    private static boolean updateMin(PickerColumn column, int value) {
        if (value != column.getMinValue()) {
            column.setMinValue(value);
            return true;
        }
        return false;
    }

    private static boolean updateMax(PickerColumn column, int value) {
        if (value != column.getMaxValue()) {
            column.setMaxValue(value);
            return true;
        }
        return false;
    }

    private static final int[] DATE_FIELDS = {Calendar.DAY_OF_MONTH, Calendar.MONTH, Calendar.YEAR};

    // Following implementation always keeps up-to-date date ranges (min & max values) no matter
    // what the currently selected date is. This prevents the constant updating of date values while
    // scrolling vertically and thus fixes the animation jumps that used to happen when we reached
    // the endpoint date field values since the adapter values do not change while scrolling up
    // & down across a single field.
    void updateSpinnersImpl(boolean animation) {
        // set the spinner ranges respecting the min and max dates
        int dateFieldIndices[] = {mColDayIndex, mColMonthIndex, mColYearIndex};

        boolean allLargerDateFieldsHaveBeenEqualToMinDate = true;
        boolean allLargerDateFieldsHaveBeenEqualToMaxDate = true;
        for(int i = DATE_FIELDS.length - 1; i >= 0; i--) {
            boolean dateFieldChanged = false;
            if (dateFieldIndices[i] < 0)
                continue;

            int currField = DATE_FIELDS[i];
            PickerColumn currPickerColumn = getColumnAt(dateFieldIndices[i]);

            if (allLargerDateFieldsHaveBeenEqualToMinDate) {
                dateFieldChanged |= updateMin(currPickerColumn,
                        mMinDate.get(currField));
            } else {
                dateFieldChanged |= updateMin(currPickerColumn,
                        mCurrentDate.getActualMinimum(currField));
            }

            if (allLargerDateFieldsHaveBeenEqualToMaxDate) {
                dateFieldChanged |= updateMax(currPickerColumn,
                        mMaxDate.get(currField));
            } else {
                dateFieldChanged |= updateMax(currPickerColumn,
                        mCurrentDate.getActualMaximum(currField));
            }

            allLargerDateFieldsHaveBeenEqualToMinDate &=
                    (mCurrentDate.get(currField) == mMinDate.get(currField));
            allLargerDateFieldsHaveBeenEqualToMaxDate &=
                    (mCurrentDate.get(currField) == mMaxDate.get(currField));

            if (dateFieldChanged) {
                setColumnAt(dateFieldIndices[i], currPickerColumn);
            }
            setColumnValue(dateFieldIndices[i], mCurrentDate.get(currField), animation);
        }
    }

    private void updateSpinners(final boolean animation) {
        // update range in a post call.  The reason is that RV does not allow notifyDataSetChange()
        // in scroll pass.  UpdateSpinner can be called in a scroll pass, UpdateSpinner() may
        // notifyDataSetChange to update the range.
        post(new Runnable() {
            @Override
            public void run() {
                updateSpinnersImpl(animation);
            }
        });
    }
}
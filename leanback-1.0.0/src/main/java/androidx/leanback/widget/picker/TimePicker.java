/*
 * Copyright (C) 2017 The Android Open Source Project
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

package androidx.leanback.widget.picker;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.IntRange;
import androidx.leanback.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * {@link TimePicker} is a direct subclass of {@link Picker}.
 * <p>
 * This class is a widget for selecting time and displays it according to the formatting for the
 * current system locale. The time can be selected by hour, minute, and AM/PM picker columns.
 * The AM/PM mode is determined by either explicitly setting the current mode through
 * {@link #setIs24Hour(boolean)} or the widget attribute {@code is24HourFormat} (true for 24-hour
 * mode, false for 12-hour mode). Otherwise, TimePicker retrieves the mode based on the current
 * context. In 24-hour mode, TimePicker displays only the hour and minute columns.
 * <p>
 * This widget can show the current time as the initial value if {@code useCurrentTime} is set to
 * true. Each individual time picker field can be set at any time by calling {@link #setHour(int)},
 * {@link #setMinute(int)} using 24-hour time format. The time format can also be changed at any
 * time by calling {@link #setIs24Hour(boolean)}, and the AM/PM picker column will be activated or
 * deactivated accordingly.
 *
 * @attr ref R.styleable#lbTimePicker_is24HourFormat
 * @attr ref R.styleable#lbTimePicker_useCurrentTime
 */
public class TimePicker extends Picker {

    static final String TAG = "TimePicker";

    private static final int AM_INDEX = 0;
    private static final int PM_INDEX = 1;

    private static final int HOURS_IN_HALF_DAY = 12;
    PickerColumn mHourColumn;
    PickerColumn mMinuteColumn;
    PickerColumn mAmPmColumn;
    int mColHourIndex;
    int mColMinuteIndex;
    int mColAmPmIndex;

    private final PickerUtility.TimeConstant mConstant;

    private boolean mIs24hFormat;

    private int mCurrentHour;
    private int mCurrentMinute;
    private int mCurrentAmPmIndex;

    private String mTimePickerFormat;

    /**
     * Constructor called when inflating a TimePicker widget. This version uses a default style of
     * 0, so the only attribute values applied are those in the Context's Theme and the given
     * AttributeSet.
     *
     * @param context the context this TimePicker widget is associated with through which we can
     *                access the current theme attributes and resources
     * @param attrs the attributes of the XML tag that is inflating the TimePicker widget
     */
    public TimePicker(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /**
     * Constructor called when inflating a TimePicker widget.
     *
     * @param context the context this TimePicker widget is associated with through which we can
     *                access the current theme attributes and resources
     * @param attrs the attributes of the XML tag that is inflating the TimePicker widget
     * @param defStyleAttr An attribute in the current theme that contains a reference to a style
     *                     resource that supplies default values for the widget. Can be 0 to not
     *                     look for defaults.
     */
    public TimePicker(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mConstant = PickerUtility.getTimeConstantInstance(Locale.getDefault(),
                context.getResources());

        final TypedArray attributesArray = context.obtainStyledAttributes(attrs,
                R.styleable.lbTimePicker);
        mIs24hFormat = attributesArray.getBoolean(R.styleable.lbTimePicker_is24HourFormat,
                DateFormat.is24HourFormat(context));
        boolean useCurrentTime = attributesArray.getBoolean(R.styleable.lbTimePicker_useCurrentTime,
                true);

        // The following 2 methods must be called after setting mIs24hFormat since this attribute is
        // used to extract the time format string.
        updateColumns();
        updateColumnsRange();

        if (useCurrentTime) {
            Calendar currentDate = PickerUtility.getCalendarForLocale(null,
                    mConstant.locale);
            setHour(currentDate.get(Calendar.HOUR_OF_DAY));
            setMinute(currentDate.get(Calendar.MINUTE));
            setAmPmValue();
        }
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

    /**
     * @return The best localized representation of time for the current locale
     */
    String getBestHourMinutePattern() {
        final String hourPattern;
        if (PickerUtility.SUPPORTS_BEST_DATE_TIME_PATTERN) {
            hourPattern = DateFormat.getBestDateTimePattern(mConstant.locale, mIs24hFormat ? "Hma"
                    : "hma");
        } else {
            // Using short style to avoid picking extra fields e.g. time zone in the returned time
            // format.
            final java.text.DateFormat dateFormat =
                    SimpleDateFormat.getTimeInstance(SimpleDateFormat.SHORT, mConstant.locale);
            if (dateFormat instanceof SimpleDateFormat) {
                String defaultPattern = ((SimpleDateFormat) dateFormat).toPattern();
                defaultPattern = defaultPattern.replace("s", "");
                if (mIs24hFormat) {
                    defaultPattern = defaultPattern.replace('h', 'H').replace("a", "");
                }
                hourPattern = defaultPattern;
            } else {
                hourPattern = mIs24hFormat ? "H:mma" : "h:mma";
            }
        }
        return TextUtils.isEmpty(hourPattern) ? "h:mma" : hourPattern;
    }

    /**
     * Extracts the separators used to separate time fields (including before the first and after
     * the last time field). The separators can vary based on the individual locale and 12 or
     * 24 hour time format, defined in the Unicode CLDR and cannot be supposed to be ":".
     *
     * See http://unicode.org/cldr/trac/browser/trunk/common/main
     *
     * For example, for english in 12 hour format
     * (time pattern of "h:mm a"), this will return {"", ":", "", ""}, where the first separator
     * indicates nothing needs to be displayed to the left of the hour field, ":" needs to be
     * displayed to the right of hour field, and so forth.
     *
     * @return The ArrayList of separators to populate between the actual time fields in the
     * TimePicker.
     */
    List<CharSequence> extractSeparators() {
        // Obtain the time format string per the current locale (e.g. h:mm a)
        String hmaPattern = getBestHourMinutePattern();

        List<CharSequence> separators = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        char lastChar = '\0';
        // See http://www.unicode.org/reports/tr35/tr35-dates.html for hour formats
        final char[] timeFormats = {'H', 'h', 'K', 'k', 'm', 'M', 'a'};
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
                if (isAnyOf(c, timeFormats)) {
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
     *
     * @return the time picker format string based on the current system locale and the layout
     *         direction
     */
    private String extractTimeFields() {
        // Obtain the time format string per the current locale (e.g. h:mm a)
        String hmaPattern = getBestHourMinutePattern();

        boolean isRTL = TextUtils.getLayoutDirectionFromLocale(mConstant.locale) == View
                .LAYOUT_DIRECTION_RTL;
        boolean isAmPmAtEnd = (hmaPattern.indexOf('a') >= 0)
                ? (hmaPattern.indexOf("a") > hmaPattern.indexOf("m")) : true;
        // Hour will always appear to the left of minutes regardless of layout direction.
        String timePickerFormat = isRTL ? "mh" : "hm";

        if (is24Hour()) {
            return timePickerFormat;
        } else {
            return isAmPmAtEnd ? (timePickerFormat + "a") : ("a" + timePickerFormat);
        }
    }

    private void updateColumns() {
        String timePickerFormat = getBestHourMinutePattern();
        if (TextUtils.equals(timePickerFormat, mTimePickerFormat)) {
            return;
        }
        mTimePickerFormat = timePickerFormat;

        String timeFieldsPattern = extractTimeFields();
        List<CharSequence> separators = extractSeparators();
        if (separators.size() != (timeFieldsPattern.length() + 1)) {
            throw new IllegalStateException("Separators size: " + separators.size() + " must equal"
                    + " the size of timeFieldsPattern: " + timeFieldsPattern.length() + " + 1");
        }
        setSeparators(separators);
        timeFieldsPattern = timeFieldsPattern.toUpperCase();

        mHourColumn = mMinuteColumn = mAmPmColumn = null;
        mColHourIndex = mColMinuteIndex = mColAmPmIndex = -1;

        ArrayList<PickerColumn> columns = new ArrayList<>(3);
        for (int i = 0; i < timeFieldsPattern.length(); i++) {
            switch (timeFieldsPattern.charAt(i)) {
                case 'H':
                    columns.add(mHourColumn = new PickerColumn());
                    mHourColumn.setStaticLabels(mConstant.hours24);
                    mColHourIndex = i;
                    break;
                case 'M':
                    columns.add(mMinuteColumn = new PickerColumn());
                    mMinuteColumn.setStaticLabels(mConstant.minutes);
                    mColMinuteIndex = i;
                    break;
                case 'A':
                    columns.add(mAmPmColumn = new PickerColumn());
                    mAmPmColumn.setStaticLabels(mConstant.ampm);
                    mColAmPmIndex = i;
                    updateMin(mAmPmColumn, 0);
                    updateMax(mAmPmColumn, 1);
                    break;
                default:
                    throw new IllegalArgumentException("Invalid time picker format.");
            }
        }
        setColumns(columns);
    }

    private void updateColumnsRange() {
        // updateHourColumn(false);
        updateMin(mHourColumn, mIs24hFormat ? 0 : 1);
        updateMax(mHourColumn, mIs24hFormat ? 23 : 12);

        updateMin(mMinuteColumn, 0);
        updateMax(mMinuteColumn, 59);

        if (mAmPmColumn != null) {
            updateMin(mAmPmColumn, 0);
            updateMax(mAmPmColumn, 1);
        }
    }

    /**
     * Updates the value of AM/PM column for a 12 hour time format. The correct value should already
     * be calculated before this method is called by calling setHour.
     */
    private void setAmPmValue() {
        if (!is24Hour()) {
            setColumnValue(mColAmPmIndex, mCurrentAmPmIndex, false);
        }
    }

    /**
     * Sets the currently selected hour using a 24-hour time.
     *
     * @param hour the hour to set, in the range (0-23)
     * @see #getHour()
     */
    public void setHour(@IntRange(from = 0, to = 23) int hour) {
        if (hour < 0 || hour > 23) {
            throw new IllegalArgumentException("hour: " + hour + " is not in [0-23] range in");
        }
        mCurrentHour = hour;
        if (!is24Hour()) {
            if (mCurrentHour >= HOURS_IN_HALF_DAY) {
                mCurrentAmPmIndex = PM_INDEX;
                if (mCurrentHour > HOURS_IN_HALF_DAY) {
                    mCurrentHour -= HOURS_IN_HALF_DAY;
                }
            } else {
                mCurrentAmPmIndex = AM_INDEX;
                if (mCurrentHour == 0) {
                    mCurrentHour = HOURS_IN_HALF_DAY;
                }
            }
            setAmPmValue();
        }
        setColumnValue(mColHourIndex, mCurrentHour, false);
    }

    /**
     * Returns the currently selected hour using 24-hour time.
     *
     * @return the currently selected hour in the range (0-23)
     * @see #setHour(int)
     */
    public int getHour() {
        if (mIs24hFormat) {
            return mCurrentHour;
        }
        if (mCurrentAmPmIndex == AM_INDEX) {
            return mCurrentHour % HOURS_IN_HALF_DAY;
        }
        return (mCurrentHour % HOURS_IN_HALF_DAY) + HOURS_IN_HALF_DAY;
    }

    /**
     * Sets the currently selected minute.
     *
     * @param minute the minute to set, in the range (0-59)
     * @see #getMinute()
     */
    public void setMinute(@IntRange(from = 0, to = 59) int minute) {
        if (minute < 0 || minute > 59) {
            throw new IllegalArgumentException("minute: " + minute + " is not in [0-59] range.");
        }
        mCurrentMinute = minute;
        setColumnValue(mColMinuteIndex, mCurrentMinute, false);
    }

    /**
     * Returns the currently selected minute.
     *
     * @return the currently selected minute, in the range (0-59)
     * @see #setMinute(int)
     */
    public int getMinute() {
        return mCurrentMinute;
    }

    /**
     * Sets whether this widget displays a 24-hour mode or a 12-hour mode with an AM/PM picker.
     *
     * @param is24Hour {@code true} to display in 24-hour mode,
     *                 {@code false} ti display in 12-hour mode with AM/PM.
     * @see #is24Hour()
     */
    public void setIs24Hour(boolean is24Hour) {
        if (mIs24hFormat == is24Hour) {
            return;
        }
        // the ordering of these statements is important
        int currentHour = getHour();
        int currentMinute = getMinute();
        mIs24hFormat = is24Hour;
        updateColumns();
        updateColumnsRange();

        setHour(currentHour);
        setMinute(currentMinute);
        setAmPmValue();
    }

    /**
     * @return {@code true} if this widget displays time in 24-hour mode,
     *         {@code false} otherwise.
     *
     * @see #setIs24Hour(boolean)
     */
    public boolean is24Hour() {
        return mIs24hFormat;
    }

    /**
     * Only meaningful for a 12-hour time.
     *
     * @return {@code true} if the currently selected time is in PM,
     *         {@code false} if the currently selected time in in AM.
     */
    public boolean isPm() {
        return (mCurrentAmPmIndex == PM_INDEX);
    }

    @Override
    public void onColumnValueChanged(int columnIndex, int newValue) {
        if (columnIndex == mColHourIndex) {
            mCurrentHour = newValue;
        } else if (columnIndex == mColMinuteIndex) {
            mCurrentMinute = newValue;
        } else if (columnIndex == mColAmPmIndex) {
            mCurrentAmPmIndex = newValue;
        } else {
            throw new IllegalArgumentException("Invalid column index.");
        }
    }
}

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
package androidx.leanback.widget;

import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import java.util.ArrayList;

/**
 * An action contains one or two lines of text, an optional image and an optional id. It may also
 * be invoked by one or more keycodes.
 */
public class Action {

    /** Indicates that an id has not been set. */
    public static final long NO_ID = -1;

    private long mId = NO_ID;
    private Drawable mIcon;
    private CharSequence mLabel1;
    private CharSequence mLabel2;
    private ArrayList<Integer> mKeyCodes = new ArrayList<>();

    /**
     * Constructor for an Action.
     *
     * @param id The id of the Action.
     */
    public Action(long id) {
        this(id, "");
    }

    /**
     * Constructor for an Action.
     *
     * @param id The id of the Action.
     * @param label The label to display for the Action.
     */
    public Action(long id, CharSequence label) {
        this(id, label, null);
    }

    /**
     * Constructor for an Action.
     *
     * @param id The id of the Action.
     * @param label1 The label to display on the first line of the Action.
     * @param label2 The label to display on the second line of the Action.
     */
    public Action(long id, CharSequence label1, CharSequence label2) {
        this(id, label1, label2, null);
    }

    /**
     * Constructor for an Action.
     *
     * @param id The id of the Action.
     * @param label1 The label to display on the first line of the Action.
     * @param label2 The label to display on the second line of the Action.
     * @param icon The icon to display for the Action.
     */
    public Action(long id, CharSequence label1, CharSequence label2, Drawable icon) {
        setId(id);
        setLabel1(label1);
        setLabel2(label2);
        setIcon(icon);
    }

    /**
     * Sets the id for this Action.
     */
    public final void setId(long id) {
        mId = id;
    }

    /**
     * Returns the id for this Action.
     */
    public final long getId() {
        return mId;
    }

    /**
     * Sets the first line label for this Action.
     */
    public final void setLabel1(CharSequence label) {
        mLabel1 = label;
    }

    /**
     * Returns the first line label for this Action.
     */
    public final CharSequence getLabel1() {
        return mLabel1;
    }

    /**
     * Sets the second line label for this Action.
     */
    public final void setLabel2(CharSequence label) {
        mLabel2 = label;
    }

    /**
     * Returns the second line label for this Action.
     */
    public final CharSequence getLabel2() {
        return mLabel2;
    }

    /**
     * Sets the icon drawable for this Action.
     */
    public final void setIcon(Drawable icon) {
        mIcon = icon;
    }

    /**
     * Returns the icon drawable for this Action.
     */
    public final Drawable getIcon() {
        return mIcon;
    }

    /**
     * Adds a keycode used to invoke this Action.
     */
    public final void addKeyCode(int keyCode) {
        mKeyCodes.add(keyCode);
    }

    /**
     * Removes a keycode used to invoke this Action.
     */
    public final void removeKeyCode(int keyCode) {
        mKeyCodes.remove(keyCode);
    }

    /**
     * Returns true if the Action should respond to the given keycode.
     */
    public final boolean respondsToKeyCode(int keyCode) {
        return mKeyCodes.contains(keyCode);
    }

    @Override
    public String toString(){
        StringBuilder sb = new StringBuilder();
        if (!TextUtils.isEmpty(mLabel1)) {
            sb.append(mLabel1);
        }
        if (!TextUtils.isEmpty(mLabel2)) {
            if (!TextUtils.isEmpty(mLabel1)) {
                sb.append(" ");
            }
            sb.append(mLabel2);
        }
        if (mIcon != null && sb.length() == 0) {
            sb.append("(action icon)");
        }
        return sb.toString();
    }
}

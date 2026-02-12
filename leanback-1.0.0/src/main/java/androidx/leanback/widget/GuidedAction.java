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
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.InputType;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.leanback.R;

import java.util.List;

/**
 * A data class which represents an action within a {@link
 * androidx.leanback.app.GuidedStepFragment}. GuidedActions contain at minimum a title
 * and a description, and typically also an icon.
 * <p>
 * A GuidedAction typically represents a single action a user may take, but may also represent a
 * possible choice out of a group of mutually exclusive choices (similar to radio buttons), or an
 * information-only label (in which case the item cannot be clicked).
 * <p>
 * GuidedActions may optionally be checked. They may also indicate that they will request further
 * user input on selection, in which case they will be displayed with a chevron indicator.
 * <p>
 * GuidedAction recommends to use {@link Builder}. When application subclass GuidedAction, it
 * can subclass {@link BuilderBase}, implement its own builder() method where it should
 * call {@link BuilderBase#applyValues(GuidedAction)}.
 */
public class GuidedAction extends Action {

    private static final String TAG = "GuidedAction";

    /**
     * Special check set Id that is neither checkbox nor radio.
     */
    public static final int NO_CHECK_SET = 0;
    /**
     * Default checkset Id for radio.
     */
    public static final int DEFAULT_CHECK_SET_ID = 1;
    /**
     * Checkset Id for checkbox.
     */
    public static final int CHECKBOX_CHECK_SET_ID = -1;

    /**
     * When finishing editing, goes to next action.
     */
    public static final long ACTION_ID_NEXT = -2;
    /**
     * When finishing editing, stay on current action.
     */
    public static final long ACTION_ID_CURRENT = -3;

    /**
     * Id of standard OK action.
     */
    public static final long ACTION_ID_OK = -4;

    /**
     * Id of standard Cancel action.
     */
    public static final long ACTION_ID_CANCEL = -5;

    /**
     * Id of standard Finish action.
     */
    public static final long ACTION_ID_FINISH = -6;

    /**
     * Id of standard Finish action.
     */
    public static final long ACTION_ID_CONTINUE = -7;

    /**
     * Id of standard Yes action.
     */
    public static final long ACTION_ID_YES = -8;

    /**
     * Id of standard No action.
     */
    public static final long ACTION_ID_NO = -9;

    static final int EDITING_NONE = 0;
    static final int EDITING_TITLE = 1;
    static final int EDITING_DESCRIPTION = 2;
    static final int EDITING_ACTIVATOR_VIEW = 3;

    /**
     * Base builder class to build a {@link GuidedAction} object.  When subclass GuidedAction, you
     * can override this BuilderBase class, implements your build() method which should call
     * {@link #applyValues(GuidedAction)}.  When using GuidedAction directly, use {@link Builder}.
     */
    public abstract static class BuilderBase<B extends BuilderBase> {
        private Context mContext;
        private long mId;
        private CharSequence mTitle;
        private CharSequence mEditTitle;
        private CharSequence mDescription;
        private CharSequence mEditDescription;
        private String[] mAutofillHints;
        private Drawable mIcon;
        /**
         * The mActionFlags holds various action states such as whether title or description are
         * editable, or the action is focusable.
         *
         */
        private int mActionFlags;

        private int mEditable = EDITING_NONE;
        private int mInputType = InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        private int mDescriptionInputType = InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        private int mEditInputType = InputType.TYPE_CLASS_TEXT;
        private int mDescriptionEditInputType = InputType.TYPE_CLASS_TEXT;
        private int mCheckSetId = NO_CHECK_SET;
        private List<GuidedAction> mSubActions;
        private Intent mIntent;

        /**
         * Creates a BuilderBase for GuidedAction or its subclass.
         * @param context Context object used to build the GuidedAction.
         */
        public BuilderBase(Context context) {
            mContext = context;
            mActionFlags = PF_ENABLED | PF_FOCUSABLE | PF_AUTORESTORE;
        }

        /**
         * Returns Context of this Builder.
         * @return Context of this Builder.
         */
        public Context getContext() {
            return mContext;
        }

        private void setFlags(int flag, int mask) {
            mActionFlags = (mActionFlags & ~mask) | (flag & mask);
        }

        /**
         * Subclass of BuilderBase should call this function to apply values.
         * @param action GuidedAction to apply BuilderBase values.
         */
        protected final void applyValues(GuidedAction action) {
            // Base Action values
            action.setId(mId);
            action.setLabel1(mTitle);
            action.setEditTitle(mEditTitle);
            action.setLabel2(mDescription);
            action.setEditDescription(mEditDescription);
            action.setIcon(mIcon);

            // Subclass values
            action.mIntent = mIntent;
            action.mEditable = mEditable;
            action.mInputType = mInputType;
            action.mDescriptionInputType = mDescriptionInputType;
            action.mAutofillHints = mAutofillHints;
            action.mEditInputType = mEditInputType;
            action.mDescriptionEditInputType = mDescriptionEditInputType;
            action.mActionFlags = mActionFlags;
            action.mCheckSetId = mCheckSetId;
            action.mSubActions = mSubActions;
        }

        /**
         * Construct a clickable action with associated id and auto assign pre-defined title for the
         * action. If the id is not supported, the method simply does nothing.
         * @param id One of {@link GuidedAction#ACTION_ID_OK} {@link GuidedAction#ACTION_ID_CANCEL}
         * {@link GuidedAction#ACTION_ID_FINISH} {@link GuidedAction#ACTION_ID_CONTINUE}
         * {@link GuidedAction#ACTION_ID_YES} {@link GuidedAction#ACTION_ID_NO}.
         * @return The same BuilderBase object.
         */
        public B clickAction(long id) {
            if (id == ACTION_ID_OK) {
                mId = ACTION_ID_OK;
                mTitle = mContext.getString(android.R.string.ok);
            } else if (id == ACTION_ID_CANCEL) {
                mId = ACTION_ID_CANCEL;
                mTitle = mContext.getString(android.R.string.cancel);
            } else if (id == ACTION_ID_FINISH) {
                mId = ACTION_ID_FINISH;
                mTitle = mContext.getString(R.string.lb_guidedaction_finish_title);
            } else if (id == ACTION_ID_CONTINUE) {
                mId = ACTION_ID_CONTINUE;
                mTitle = mContext.getString(R.string.lb_guidedaction_continue_title);
            } else if (id == ACTION_ID_YES) {
                mId = ACTION_ID_YES;
                mTitle = mContext.getString(android.R.string.ok);
            } else if (id == ACTION_ID_NO) {
                mId = ACTION_ID_NO;
                mTitle = mContext.getString(android.R.string.cancel);
            }
            return (B) this;
        }

        /**
         * Sets the ID associated with this action.  The ID can be any value the client wishes;
         * it is typically used to determine what to do when an action is clicked.
         * @param id The ID to associate with this action.
         */
        public B id(long id) {
            mId = id;
            return (B) this;
        }

        /**
         * Sets the title for this action.  The title is typically a short string indicating the
         * action to be taken on click, e.g. "Continue" or "Cancel".
         * @param title The title for this action.
         */
        public B title(CharSequence title) {
            mTitle = title;
            return (B) this;
        }

        /**
         * Sets the title for this action.  The title is typically a short string indicating the
         * action to be taken on click, e.g. "Continue" or "Cancel".
         * @param titleResourceId The resource id of title for this action.
         */
        public B title(@StringRes int titleResourceId) {
            mTitle = getContext().getString(titleResourceId);
            return (B) this;
        }

        /**
         * Sets the optional title text to edit.  When TextView is activated, the edit title
         * replaces the string of title.
         * @param editTitle The optional title text to edit when TextView is activated.
         */
        public B editTitle(CharSequence editTitle) {
            mEditTitle = editTitle;
            return (B) this;
        }

        /**
         * Sets the optional title text to edit.  When TextView is activated, the edit title
         * replaces the string of title.
         * @param editTitleResourceId String resource id of the optional title text to edit when
         * TextView is activated.
         */
        public B editTitle(@StringRes int editTitleResourceId) {
            mEditTitle = getContext().getString(editTitleResourceId);
            return (B) this;
        }

        /**
         * Sets the description for this action.  The description is typically a longer string
         * providing extra information on what the action will do.
         * @param description The description for this action.
         */
        public B description(CharSequence description) {
            mDescription = description;
            return (B) this;
        }

        /**
         * Sets the description for this action.  The description is typically a longer string
         * providing extra information on what the action will do.
         * @param descriptionResourceId String resource id of the description for this action.
         */
        public B description(@StringRes int descriptionResourceId) {
            mDescription = getContext().getString(descriptionResourceId);
            return (B) this;
        }

        /**
         * Sets the optional description text to edit.  When TextView is activated, the edit
         * description replaces the string of description.
         * @param description The description to edit for this action.
         */
        public B editDescription(CharSequence description) {
            mEditDescription = description;
            return (B) this;
        }

        /**
         * Sets the optional description text to edit.  When TextView is activated, the edit
         * description replaces the string of description.
         * @param descriptionResourceId String resource id of the description to edit for this
         * action.
         */
        public B editDescription(@StringRes int descriptionResourceId) {
            mEditDescription = getContext().getString(descriptionResourceId);
            return (B) this;
        }

        /**
         * Sets the intent associated with this action.  Clients would typically fire this intent
         * directly when the action is clicked.
         * @param intent The intent associated with this action.
         */
        public B intent(Intent intent) {
            mIntent = intent;
            return (B) this;
        }

        /**
         * Sets the action's icon drawable.
         * @param icon The drawable for the icon associated with this action.
         */
        public B icon(Drawable icon) {
            mIcon = icon;
            return (B) this;
        }

        /**
         * Sets the action's icon drawable by retrieving it by resource ID from the specified
         * context. This is a convenience function that simply looks up the drawable and calls
         * {@link #icon(Drawable)}.
         * @param iconResourceId The resource ID for the icon associated with this action.
         * @param context The context whose resource ID should be retrieved.
         * @deprecated Use {@link #icon(int)}.
         */
        @Deprecated
        public B iconResourceId(@DrawableRes int iconResourceId, Context context) {
            return icon(ContextCompat.getDrawable(context, iconResourceId));
        }

        /**
         * Sets the action's icon drawable by retrieving it by resource ID from Builder's
         * context. This is a convenience function that simply looks up the drawable and calls
         * {@link #icon(Drawable)}.
         * @param iconResourceId The resource ID for the icon associated with this action.
         */
        public B icon(@DrawableRes int iconResourceId) {
            return icon(ContextCompat.getDrawable(getContext(), iconResourceId));
        }

        /**
         * Indicates whether this action title is editable. Note: Editable actions cannot also be
         * checked, or belong to a check set.
         * @param editable Whether this action is editable.
         */
        public B editable(boolean editable) {
            if (!editable) {
                if (mEditable == EDITING_TITLE) {
                    mEditable = EDITING_NONE;
                }
                return (B) this;
            }
            mEditable = EDITING_TITLE;
            if (isChecked() || mCheckSetId != NO_CHECK_SET) {
                throw new IllegalArgumentException("Editable actions cannot also be checked");
            }
            return (B) this;
        }

        /**
         * Indicates whether this action's description is editable
         * @param editable Whether this action description is editable.
         */
        public B descriptionEditable(boolean editable) {
            if (!editable) {
                if (mEditable == EDITING_DESCRIPTION) {
                    mEditable = EDITING_NONE;
                }
                return (B) this;
            }
            mEditable = EDITING_DESCRIPTION;
            if (isChecked() || mCheckSetId != NO_CHECK_SET) {
                throw new IllegalArgumentException("Editable actions cannot also be checked");
            }
            return (B) this;
        }

        /**
         * Indicates whether this action has a view can be activated to edit, e.g. a DatePicker.
         * @param editable Whether this action has view can be activated to edit.
         */
        public B hasEditableActivatorView(boolean editable) {
            if (!editable) {
                if (mEditable == EDITING_ACTIVATOR_VIEW) {
                    mEditable = EDITING_NONE;
                }
                return (B) this;
            }
            mEditable = EDITING_ACTIVATOR_VIEW;
            if (isChecked() || mCheckSetId != NO_CHECK_SET) {
                throw new IllegalArgumentException("Editable actions cannot also be checked");
            }
            return (B) this;
        }

        /**
         * Sets {@link InputType} of this action title not in editing.
         *
         * @param inputType InputType for the action title not in editing.
         */
        public B inputType(int inputType) {
            mInputType = inputType;
            return (B) this;
        }

        /**
         * Sets {@link InputType} of this action description not in editing.
         *
         * @param inputType InputType for the action description not in editing.
         */
        public B descriptionInputType(int inputType) {
            mDescriptionInputType = inputType;
            return (B) this;
        }


        /**
         * Sets {@link InputType} of this action title in editing.
         *
         * @param inputType InputType for the action title in editing.
         */
        public B editInputType(int inputType) {
            mEditInputType = inputType;
            return (B) this;
        }

        /**
         * Sets {@link InputType} of this action description in editing.
         *
         * @param inputType InputType for the action description in editing.
         */
        public B descriptionEditInputType(int inputType) {
            mDescriptionEditInputType = inputType;
            return (B) this;
        }


        private boolean isChecked() {
            return (mActionFlags & PF_CHECKED) == PF_CHECKED;
        }
        /**
         * Indicates whether this action is initially checked.
         * @param checked Whether this action is checked.
         */
        public B checked(boolean checked) {
            setFlags(checked ? PF_CHECKED : 0, PF_CHECKED);
            if (mEditable != EDITING_NONE) {
                throw new IllegalArgumentException("Editable actions cannot also be checked");
            }
            return (B) this;
        }

        /**
         * Indicates whether this action is part of a single-select group similar to radio buttons
         * or this action is a checkbox. When one item in a check set is checked, all others with
         * the same check set ID will be checked automatically.
         * @param checkSetId The check set ID, or {@link GuidedAction#NO_CHECK_SET} to indicate not
         * radio or checkbox, or {@link GuidedAction#CHECKBOX_CHECK_SET_ID} to indicate a checkbox.
         */
        public B checkSetId(int checkSetId) {
            mCheckSetId = checkSetId;
            if (mEditable != EDITING_NONE) {
                throw new IllegalArgumentException("Editable actions cannot also be in check sets");
            }
            return (B) this;
        }

        /**
         * Indicates whether the title and description are long, and should be displayed
         * appropriately.
         * @param multilineDescription Whether this action has a multiline description.
         */
        public B multilineDescription(boolean multilineDescription) {
            setFlags(multilineDescription ? PF_MULTI_LINE_DESCRIPTION : 0,
                PF_MULTI_LINE_DESCRIPTION);
            return (B) this;
        }

        /**
         * Indicates whether this action has a next state and should display a chevron.
         * @param hasNext Whether this action has a next state.
         */
        public B hasNext(boolean hasNext) {
            setFlags(hasNext ? PF_HAS_NEXT : 0, PF_HAS_NEXT);
            return (B) this;
        }

        /**
         * Indicates whether this action is for information purposes only and cannot be clicked.
         * @param infoOnly Whether this action has a next state.
         */
        public B infoOnly(boolean infoOnly) {
            setFlags(infoOnly ? PF_INFO_ONLY : 0, PF_INFO_ONLY);
            return (B) this;
        }

        /**
         * Indicates whether this action is enabled.  If not enabled, an action cannot be clicked.
         * @param enabled Whether the action is enabled.
         */
        public B enabled(boolean enabled) {
            setFlags(enabled ? PF_ENABLED : 0, PF_ENABLED);
            return (B) this;
        }

        /**
         * Indicates whether this action can take focus.
         * @param focusable
         * @return The same BuilderBase object.
         */
        public B focusable(boolean focusable) {
            setFlags(focusable ? PF_FOCUSABLE : 0, PF_FOCUSABLE);
            return (B) this;
        }

        /**
         * Sets sub actions list.
         * @param subActions
         * @return The same BuilderBase object.
         */
        public B subActions(List<GuidedAction> subActions) {
            mSubActions = subActions;
            return (B) this;
        }

        /**
         * Explicitly sets auto restore feature on the GuidedAction.  It's by default true.
         * @param autoSaveRestoreEnabled True if turn on auto save/restore of GuidedAction content,
         *                                false otherwise.
         * @return The same BuilderBase object.
         * @see GuidedAction#isAutoSaveRestoreEnabled()
         */
        public B autoSaveRestoreEnabled(boolean autoSaveRestoreEnabled) {
            setFlags(autoSaveRestoreEnabled ? PF_AUTORESTORE : 0, PF_AUTORESTORE);
            return (B) this;
        }

        /**
         * Sets autofill hints. See {@link android.view.View#setAutofillHints}
         * @param hints List of hints for autofill.
         * @return The same BuilderBase object.
         */
        public B autofillHints(String... hints) {
            mAutofillHints = hints;
            return (B) this;
        }
    }

    /**
     * Builds a {@link GuidedAction} object.
     */
    public static class Builder extends BuilderBase<Builder> {

        /**
         * @deprecated Use {@link GuidedAction.Builder#GuidedAction.Builder(Context)}.
         */
        @Deprecated
        public Builder() {
            super(null);
        }

        /**
         * Creates a Builder for GuidedAction.
         * @param context Context to build GuidedAction.
         */
        public Builder(Context context) {
            super(context);
        }

        /**
         * Builds the GuidedAction corresponding to this Builder.
         * @return The GuidedAction as configured through this Builder.
         */
        public GuidedAction build() {
            GuidedAction action = new GuidedAction();
            applyValues(action);
            return action;
        }

    }

    static final int PF_CHECKED = 0x00000001;
    static final int PF_MULTI_LINE_DESCRIPTION = 0x00000002;
    static final int PF_HAS_NEXT = 0x00000004;
    static final int PF_INFO_ONLY = 0x00000008;
    static final int PF_ENABLED = 0x00000010;
    static final int PF_FOCUSABLE = 0x00000020;
    static final int PF_AUTORESTORE = 0x00000040;
    int mActionFlags;

    private CharSequence mEditTitle;
    private CharSequence mEditDescription;
    int mEditable;
    int mInputType;
    int mDescriptionInputType;
    int mEditInputType;
    int mDescriptionEditInputType;
    String[] mAutofillHints;

    int mCheckSetId;

    List<GuidedAction> mSubActions;

    Intent mIntent;

    protected GuidedAction() {
        super(0);
    }

    private void setFlags(int flag, int mask) {
        mActionFlags = (mActionFlags & ~mask) | (flag & mask);
    }

    /**
     * Returns the title of this action.
     * @return The title set when this action was built.
     */
    public CharSequence getTitle() {
        return getLabel1();
    }

    /**
     * Sets the title of this action.
     * @param title The title set when this action was built.
     */
    public void setTitle(CharSequence title) {
        setLabel1(title);
    }

    /**
     * Returns the optional title text to edit.  When not null, it is being edited instead of
     * {@link #getTitle()}.
     * @return Optional title text to edit instead of {@link #getTitle()}.
     */
    public CharSequence getEditTitle() {
        return mEditTitle;
    }

    /**
     * Sets the optional title text to edit instead of {@link #setTitle(CharSequence)}.
     * @param editTitle Optional title text to edit instead of {@link #setTitle(CharSequence)}.
     */
    public void setEditTitle(CharSequence editTitle) {
        mEditTitle = editTitle;
    }

    /**
     * Returns the optional description text to edit.  When not null, it is being edited instead of
     * {@link #getDescription()}.
     * @return Optional description text to edit instead of {@link #getDescription()}.
     */
    public CharSequence getEditDescription() {
        return mEditDescription;
    }

    /**
     * Sets the optional description text to edit instead of {@link #setDescription(CharSequence)}.
     * @param editDescription Optional description text to edit instead of
     * {@link #setDescription(CharSequence)}.
     */
    public void setEditDescription(CharSequence editDescription) {
        mEditDescription = editDescription;
    }

    /**
     * Returns true if {@link #getEditTitle()} is not null.  When true, the {@link #getEditTitle()}
     * is being edited instead of {@link #getTitle()}.
     * @return true if {@link #getEditTitle()} is not null.
     */
    public boolean isEditTitleUsed() {
        return mEditTitle != null;
    }

    /**
     * Returns the description of this action.
     * @return The description of this action.
     */
    public CharSequence getDescription() {
        return getLabel2();
    }

    /**
     * Sets the description of this action.
     * @param description The description of the action.
     */
    public void setDescription(CharSequence description) {
        setLabel2(description);
    }

    /**
     * Returns the intent associated with this action.
     * @return The intent set when this action was built.
     */
    public Intent getIntent() {
        return mIntent;
    }

    /**
     * Sets the intent of this action.
     * @param intent New intent to set on this action.
     */
    public void setIntent(Intent intent) {
        mIntent = intent;
    }

    /**
     * Returns whether this action title is editable.
     * @return true if the action title is editable, false otherwise.
     */
    public boolean isEditable() {
        return mEditable == EDITING_TITLE;
    }

    /**
     * Returns whether this action description is editable.
     * @return true if the action description is editable, false otherwise.
     */
    public boolean isDescriptionEditable() {
        return mEditable == EDITING_DESCRIPTION;
    }

    /**
     * Returns if this action has editable title or editable description.
     * @return True if this action has editable title or editable description, false otherwise.
     */
    public boolean hasTextEditable() {
        return mEditable == EDITING_TITLE || mEditable == EDITING_DESCRIPTION;
    }

    /**
     * Returns whether this action can be activated to edit, e.g. a DatePicker.
     * @return true if the action can be activated to edit.
     */
    public boolean hasEditableActivatorView() {
        return mEditable == EDITING_ACTIVATOR_VIEW;
    }

    /**
     * Returns InputType of action title in editing; only valid when {@link #isEditable()} is true.
     * @return InputType of action title in editing.
     */
    public int getEditInputType() {
        return mEditInputType;
    }

    /**
     * Returns InputType of action description in editing; only valid when
     * {@link #isDescriptionEditable()} is true.
     * @return InputType of action description in editing.
     */
    public int getDescriptionEditInputType() {
        return mDescriptionEditInputType;
    }

    /**
     * Returns InputType of action title not in editing.
     * @return InputType of action title not in editing.
     */
    public int getInputType() {
        return mInputType;
    }

    /**
     * Returns InputType of action description not in editing.
     * @return InputType of action description not in editing.
     */
    public int getDescriptionInputType() {
        return mDescriptionInputType;
    }

    /**
     * Returns whether this action is checked.
     * @return true if the action is currently checked, false otherwise.
     */
    public boolean isChecked() {
        return (mActionFlags & PF_CHECKED) == PF_CHECKED;
    }

    /**
     * Sets whether this action is checked.
     * @param checked Whether this action should be checked.
     */
    public void setChecked(boolean checked) {
        setFlags(checked ? PF_CHECKED : 0, PF_CHECKED);
    }

    /**
     * Returns the check set id this action is a part of. All actions in the same list with the same
     * check set id are considered linked. When one of the actions within that set is selected, that
     * action becomes checked, while all the other actions become unchecked.
     *
     * @return an integer representing the check set this action is a part of, or
     *         {@link #CHECKBOX_CHECK_SET_ID} if this is a checkbox, or {@link #NO_CHECK_SET} if
     *         this action is not a checkbox or radiobutton.
     */
    public int getCheckSetId() {
        return mCheckSetId;
    }

    /**
     * Returns whether this action is has a multiline description.
     * @return true if the action was constructed as having a multiline description, false
     * otherwise.
     */
    public boolean hasMultilineDescription() {
        return (mActionFlags & PF_MULTI_LINE_DESCRIPTION) == PF_MULTI_LINE_DESCRIPTION;
    }

    /**
     * Returns whether this action is enabled.
     * @return true if the action is currently enabled, false otherwise.
     */
    public boolean isEnabled() {
        return (mActionFlags & PF_ENABLED) == PF_ENABLED;
    }

    /**
     * Sets whether this action is enabled.
     * @param enabled Whether this action should be enabled.
     */
    public void setEnabled(boolean enabled) {
        setFlags(enabled ? PF_ENABLED : 0, PF_ENABLED);
    }

    /**
     * Returns whether this action is focusable.
     * @return true if the action is currently focusable, false otherwise.
     */
    public boolean isFocusable() {
        return (mActionFlags & PF_FOCUSABLE) == PF_FOCUSABLE;
    }

    /**
     * Sets whether this action is focusable.
     * @param focusable Whether this action should be focusable.
     */
    public void setFocusable(boolean focusable) {
        setFlags(focusable ? PF_FOCUSABLE : 0, PF_FOCUSABLE);
    }

    /**
     * Returns autofill hints, see {@link android.view.View#setAutofillHints(String...)}.
     */
    public String[] getAutofillHints() {
        return mAutofillHints;
    }

    /**
     * Returns whether this action will request further user input when selected, such as showing
     * another GuidedStepFragment or launching a new activity. Configured during construction.
     * @return true if the action will request further user input when selected, false otherwise.
     */
    public boolean hasNext() {
        return (mActionFlags & PF_HAS_NEXT) == PF_HAS_NEXT;
    }

    /**
     * Returns whether the action will only display information and is thus not clickable. If both
     * this and {@link #hasNext()} are true, infoOnly takes precedence. The default is false. For
     * example, this might represent e.g. the amount of storage a document uses, or the cost of an
     * app.
     * @return true if will only display information, false otherwise.
     */
    public boolean infoOnly() {
        return (mActionFlags & PF_INFO_ONLY) == PF_INFO_ONLY;
    }

    /**
     * Change sub actions list.
     * @param actions Sub actions list to set on this action.  Sets null to disable sub actions.
     */
    public void setSubActions(List<GuidedAction> actions) {
        mSubActions = actions;
    }

    /**
     * @return List of sub actions or null if sub actions list is not enabled.
     */
    public List<GuidedAction> getSubActions() {
        return mSubActions;
    }

    /**
     * @return True if has sub actions list, even it's currently empty.
     */
    public boolean hasSubActions() {
        return mSubActions != null;
    }

    /**
     * Returns true if Action will be saved to instanceState and restored later, false otherwise.
     * The default value is true.  When isAutoSaveRestoreEnabled() is true and {@link #getId()} is
     * not {@link #NO_ID}:
     * <li>{@link #isEditable()} is true: save text of {@link #getTitle()}</li>
     * <li>{@link #isDescriptionEditable()} is true: save text of {@link #getDescription()}</li>
     * <li>{@link #getCheckSetId()} is not {@link #NO_CHECK_SET}: save {@link #isChecked()}}</li>
     * <li>{@link GuidedDatePickerAction} will be saved</li>
     * App may explicitly disable auto restore and handle by itself. App should override Fragment
     * onSaveInstanceState() and onCreateActions()
     * @return True if Action will be saved to instanceState and restored later, false otherwise.
     */
    public final boolean isAutoSaveRestoreEnabled() {
        return (mActionFlags & PF_AUTORESTORE) == PF_AUTORESTORE;
    }

    /**
     * Save action into a bundle using a given key. When isAutoRestoreEna() is true:
     * <li>{@link #isEditable()} is true: save text of {@link #getTitle()}</li>
     * <li>{@link #isDescriptionEditable()} is true: save text of {@link #getDescription()}</li>
     * <li>{@link #getCheckSetId()} is not {@link #NO_CHECK_SET}: save {@link #isChecked()}}</li>
     * <li>{@link GuidedDatePickerAction} will be saved</li>
     * Subclass may override this method.
     * @param bundle  Bundle to save the Action.
     * @param key Key used to save the Action.
     */
    public void onSaveInstanceState(Bundle bundle, String key) {
        if (needAutoSaveTitle() && getTitle() != null) {
            bundle.putString(key, getTitle().toString());
        } else if (needAutoSaveDescription() && getDescription() != null) {
            bundle.putString(key, getDescription().toString());
        } else if (getCheckSetId() != NO_CHECK_SET) {
            bundle.putBoolean(key, isChecked());
        }
    }

    /**
     * Restore action from a bundle using a given key. When isAutoRestore() is true:
     * <li>{@link #isEditable()} is true: save text of {@link #getTitle()}</li>
     * <li>{@link #isDescriptionEditable()} is true: save text of {@link #getDescription()}</li>
     * <li>{@link #getCheckSetId()} is not {@link #NO_CHECK_SET}: save {@link #isChecked()}}</li>
     * <li>{@link GuidedDatePickerAction} will be saved</li>
     * Subclass may override this method.
     * @param bundle  Bundle to restore the Action from.
     * @param key Key used to restore the Action.
     */
    public void onRestoreInstanceState(Bundle bundle, String key) {
        if (needAutoSaveTitle()) {
            String title = bundle.getString(key);
            if (title != null) {
                setTitle(title);
            }
        } else if (needAutoSaveDescription()) {
            String description = bundle.getString(key);
            if (description != null) {
                setDescription(description);
            }
        } else if (getCheckSetId() != NO_CHECK_SET) {
            setChecked(bundle.getBoolean(key, isChecked()));
        }
    }

    static boolean isPasswordVariant(int inputType) {
        final int variation = inputType & InputType.TYPE_MASK_VARIATION;
        return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;
    }

    final boolean needAutoSaveTitle() {
        return isEditable() && !isPasswordVariant(getEditInputType());
    }

    final boolean needAutoSaveDescription() {
        return isDescriptionEditable() && !isPasswordVariant(getDescriptionEditInputType());
    }

}

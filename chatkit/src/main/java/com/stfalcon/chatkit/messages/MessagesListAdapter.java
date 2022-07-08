/*******************************************************************************
 * Copyright 2016 stfalcon.com
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package com.stfalcon.chatkit.messages;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.Spannable;
import android.text.method.LinkMovementMethod;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.recyclerview.widget.RecyclerView;

import com.stfalcon.chatkit.R;
import com.stfalcon.chatkit.commons.ImageLoader;
import com.stfalcon.chatkit.commons.ViewHolder;
import com.stfalcon.chatkit.commons.models.IMessage;
import com.stfalcon.chatkit.utils.DateFormatter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Adapter for {@link MessagesList}.
 */
@SuppressWarnings("WeakerAccess")
public class MessagesListAdapter<MESSAGE extends IMessage>
        extends RecyclerView.Adapter<ViewHolder>
        implements RecyclerScrollMoreListener.OnLoadMoreListener {

    protected static boolean isSelectionModeEnabled;

    protected List<Wrapper> items;
    private MessageHolders holders;
    private String senderId;

    private int selectedItemsCount;
    private SelectionListener selectionListener;

    private OnLoadMoreListener loadMoreListener;
    private OnMessageClickListener<MESSAGE> onMessageClickListener;
    private OnMessageViewClickListener<MESSAGE> onMessageViewClickListener;
    private OnMessageLongClickListener<MESSAGE> onMessageLongClickListener;
    private OnMessageViewLongClickListener<MESSAGE> onMessageViewLongClickListener;
    private ImageLoader imageLoader;
    private RecyclerView.LayoutManager layoutManager;
    private MessagesListStyle messagesListStyle;
    private DateFormatter.Formatter dateHeadersFormatter;
    private SparseArray<OnMessageViewClickListener> viewClickListenersArray = new SparseArray<>();

    /**
     * For default list item layout and view holder.
     *
     * @param senderId    identifier of sender.
     * @param imageLoader image loading method.
     */
    public MessagesListAdapter(String senderId, ImageLoader imageLoader) {
        this(senderId, new MessageHolders(), imageLoader);
    }

    /**
     * For default list item layout and view holder.
     *
     * @param senderId    identifier of sender.
     * @param holders     custom layouts and view holders. See {@link MessageHolders} documentation for details
     * @param imageLoader image loading method.
     */
    public MessagesListAdapter(String senderId, MessageHolders holders,
                               ImageLoader imageLoader) {
        this.senderId = senderId;
        this.holders = holders;
        this.imageLoader = imageLoader;
        this.items = new ArrayList<>();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return holders.getHolder(parent, viewType, messagesListStyle);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Wrapper wrapper = items.get(position);
        holders.bind(holder, wrapper.item, wrapper.isSelected, imageLoader,
                getMessageClickListener(wrapper),
                getMessageLongClickListener(wrapper),
                dateHeadersFormatter,
                viewClickListenersArray);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public int getItemViewType(int position) {
        return holders.getViewType(items.get(position).item, senderId);
    }

    @Override
    public void onLoadMore(int page, int total) {
        if (loadMoreListener != null) {
            loadMoreListener.onLoadMore(page, total);
        }
    }

    @Override
    public int getMessagesCount() {
        int count = 0;
        for (Wrapper item : items) {
            if (item.item instanceof IMessage) {
                count++;
            }
        }
        return count;
    }

    /*
     * PUBLIC METHODS
     * */

    /**
     * Adds message to bottom of list and scroll if needed.
     *
     * @param message message to add.
     * @param scroll  {@code true} if need to scroll list to bottom when message added.
     */
    public void addToStart(MESSAGE message, boolean scroll) {
        boolean isNewMessageToday = !isPreviousSameDate(0, message.getCreatedAt());
        if (isNewMessageToday) {
            items.add(0, new Wrapper<>(message.getCreatedAt()));
        }
        Wrapper<MESSAGE> element = new Wrapper<>(message);
        items.add(0, element);
        notifyItemRangeInserted(0, isNewMessageToday ? 2 : 1);
        if (layoutManager != null && scroll) {
            layoutManager.scrollToPosition(0);
        }
    }

    /**
     * Adds messages list in chronological order. Use this method to add history.
     *
     * @param messages messages from history.
     * @param reverse  {@code true} if need to reverse messages before adding.
     */
    public void addToEnd(List<MESSAGE> messages, boolean reverse) {
        if (messages.isEmpty()) return;

        if (reverse) Collections.reverse(messages);

        if (!items.isEmpty()) {
            int lastItemPosition = items.size() - 1;
            Date lastItem = (Date) items.get(lastItemPosition).item;
            if (DateFormatter.isSameDay(messages.get(0).getCreatedAt(), lastItem)) {
                items.remove(lastItemPosition);
                notifyItemRemoved(lastItemPosition);
            }
        }

        int oldSize = items.size();
        generateDateHeaders(messages);
        notifyItemRangeInserted(oldSize, items.size() - oldSize);
    }

    /**
     * Updates message by its id.
     *
     * @param message updated message object.
     */
    public boolean update(MESSAGE message) {
        return update(message.getId(), message);
    }

    /**
     * Updates message by old identifier (use this method if id has changed). Otherwise use {@link #update(IMessage)}
     *
     * @param oldId      an identifier of message to update.
     * @param newMessage new message object.
     */
    public boolean update(String oldId, MESSAGE newMessage) {
        int position = getMessagePositionById(oldId);
        if (position >= 0) {
            Wrapper<MESSAGE> element = new Wrapper<>(newMessage);
            items.set(position, element);
            notifyItemChanged(position);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Moves the elements position from current to start
     *
     * @param newMessage new message object.
     */
    public void updateAndMoveToStart(MESSAGE newMessage) {
        int position = getMessagePositionById(newMessage.getId());
        if (position >= 0) {
            Wrapper<MESSAGE> element = new Wrapper<>(newMessage);
            items.remove(position);
            items.add(0, element);
            notifyItemMoved(position, 0);
            notifyItemChanged(0);
        }
    }

    /**
     * Updates message by its id if it exists, add to start if not
     *
     * @param message message object to insert or update.
     */
    public void upsert(MESSAGE message) {
        if (!update(message)) {
            addToStart(message, false);
        }
    }

    /**
     * Updates and moves to start if message by its id exists and if specified move to start, if not
     * specified the item stays at current position and updated
     *
     * @param message message object to insert or update.
     */
    public void upsert(MESSAGE message, boolean moveToStartIfUpdate) {
        if (moveToStartIfUpdate) {
            if (getMessagePositionById(message.getId()) > 0) {
                updateAndMoveToStart(message);
            } else {
                upsert(message);
            }
        } else {
            upsert(message);
        }
    }

    /**
     * Deletes message.
     *
     * @param message message to delete.
     */
    public void delete(MESSAGE message) {
        deleteById(message.getId());
    }

    /**
     * Deletes messages list.
     *
     * @param messages messages list to delete.
     */
    public void delete(List<MESSAGE> messages) {
        boolean result = false;
        for (MESSAGE message : messages) {
            int index = getMessagePositionById(message.getId());
            if (index >= 0) {
                items.remove(index);
                notifyItemRemoved(index);
                result = true;
            }
        }
        if (result) {
            recountDateHeaders();
        }
    }

    /**
     * Deletes message by its identifier.
     *
     * @param id identifier of message to delete.
     */
    public void deleteById(String id) {
        int index = getMessagePositionById(id);
        if (index >= 0) {
            items.remove(index);
            notifyItemRemoved(index);
            recountDateHeaders();
        }
    }

    /**
     * Deletes messages by its identifiers.
     *
     * @param ids array of identifiers of messages to delete.
     */
    public void deleteByIds(String[] ids) {
        boolean result = false;
        for (String id : ids) {
            int index = getMessagePositionById(id);
            if (index >= 0) {
                items.remove(index);
                notifyItemRemoved(index);
                result = true;
            }
        }
        if (result) {
            recountDateHeaders();
        }
    }

    /**
     * Returns {@code true} if, and only if, messages count in adapter is non-zero.
     *
     * @return {@code true} if size is 0, otherwise {@code false}
     */
    public boolean isEmpty() {
        return items.isEmpty();
    }

    /**
     * Clears the messages list. With notifyDataSetChanged
     */
    public void clear() {
        clear(true);
    }

    /**
     * Clears the messages list.
     */
    public void clear(boolean notifyDataSetChanged) {
        if (items != null) {
            items.clear();
            if (notifyDataSetChanged) {
                notifyDataSetChanged();
            }
        }
    }

    /**
     * Enables selection mode.
     *
     * @param selectionListener listener for selected items count. To get selected messages use {@link #getSelectedMessages()}.
     */
    public void enableSelectionMode(SelectionListener selectionListener) {
        if (selectionListener == null) {
            throw new IllegalArgumentException("SelectionListener must not be null. Use `disableSelectionMode()` if you want tp disable selection mode");
        } else {
            this.selectionListener = selectionListener;
        }
    }

    /**
     * Disables selection mode and removes {@link SelectionListener}.
     */
    public void disableSelectionMode() {
        this.selectionListener = null;
        unselectAllItems();
    }

    /**
     * Returns the list of selected messages.
     *
     * @return list of selected messages. Empty list if nothing was selected or selection mode is disabled.
     */
    @SuppressWarnings("unchecked")
    public ArrayList<MESSAGE> getSelectedMessages() {
        ArrayList<MESSAGE> selectedMessages = new ArrayList<>();
        for (Wrapper wrapper : items) {
            if (wrapper.item instanceof IMessage && wrapper.isSelected) {
                selectedMessages.add((MESSAGE) wrapper.item);
            }
        }
        return selectedMessages;
    }

    /**
     * Returns selected messages text and do {@link #unselectAllItems()} for you.
     *
     * @param formatter The formatter that allows you to format your message model when copying.
     * @param reverse   Change ordering when copying messages.
     * @return formatted text by {@link Formatter}. If it's {@code null} - {@code MESSAGE#toString()} will be used.
     */
    public String getSelectedMessagesText(Formatter<MESSAGE> formatter, boolean reverse) {
        String copiedText = getSelectedText(formatter, reverse);
        unselectAllItems();
        return copiedText;
    }

    /**
     * Copies text to device clipboard and returns selected messages text. Also it does {@link #unselectAllItems()} for you.
     *
     * @param context   The context.
     * @param formatter The formatter that allows you to format your message model when copying.
     * @param reverse   Change ordering when copying messages.
     * @return formatted text by {@link Formatter}. If it's {@code null} - {@code MESSAGE#toString()} will be used.
     */
    public String copySelectedMessagesText(Context context, Formatter<MESSAGE> formatter, boolean reverse) {
        String copiedText = getSelectedText(formatter, reverse);
        copyToClipboard(context, copiedText);
        unselectAllItems();
        return copiedText;
    }

    /**
     * Unselect all of the selected messages. Notifies {@link SelectionListener} with zero count.
     */
    public void unselectAllItems() {
        for (int i = 0; i < items.size(); i++) {
            Wrapper wrapper = items.get(i);
            if (wrapper.isSelected) {
                wrapper.isSelected = false;
                notifyItemChanged(i);
            }
        }
        isSelectionModeEnabled = false;
        selectedItemsCount = 0;
        notifySelectionChanged();
    }

    /**
     * Deletes all of the selected messages and disables selection mode.
     * Call {@link #getSelectedMessages()} before calling this method to delete messages from your data source.
     */
    public void deleteSelectedMessages() {
        List<MESSAGE> selectedMessages = getSelectedMessages();
        delete(selectedMessages);
        unselectAllItems();
    }

    /**
     * Sets click listener for item. Fires ONLY if list is not in selection mode.
     *
     * @param onMessageClickListener click listener.
     */
    public void setOnMessageClickListener(OnMessageClickListener<MESSAGE> onMessageClickListener) {
        this.onMessageClickListener = onMessageClickListener;
    }

    /**
     * Sets click listener for message view. Fires ONLY if list is not in selection mode.
     *
     * @param onMessageViewClickListener click listener.
     */
    public void setOnMessageViewClickListener(OnMessageViewClickListener<MESSAGE> onMessageViewClickListener) {
        this.onMessageViewClickListener = onMessageViewClickListener;
    }

    /**
     * Registers click listener for view by id
     *
     * @param viewId                     view
     * @param onMessageViewClickListener click listener.
     */
    public void registerViewClickListener(int viewId, OnMessageViewClickListener<MESSAGE> onMessageViewClickListener) {
        this.viewClickListenersArray.append(viewId, onMessageViewClickListener);
    }

    /**
     * Sets long click listener for item. Fires only if selection mode is disabled.
     *
     * @param onMessageLongClickListener long click listener.
     */
    public void setOnMessageLongClickListener(OnMessageLongClickListener<MESSAGE> onMessageLongClickListener) {
        this.onMessageLongClickListener = onMessageLongClickListener;
    }

    /**
     * Sets long click listener for message view. Fires ONLY if selection mode is disabled.
     *
     * @param onMessageViewLongClickListener long click listener.
     */
    public void setOnMessageViewLongClickListener(OnMessageViewLongClickListener<MESSAGE> onMessageViewLongClickListener) {
        this.onMessageViewLongClickListener = onMessageViewLongClickListener;
    }

    /**
     * Set callback to be invoked when list scrolled to top.
     *
     * @param loadMoreListener listener.
     */
    public void setLoadMoreListener(OnLoadMoreListener loadMoreListener) {
        this.loadMoreListener = loadMoreListener;
    }

    /**
     * Sets custom {@link DateFormatter.Formatter} for text representation of date headers.
     */
    public void setDateHeadersFormatter(DateFormatter.Formatter dateHeadersFormatter) {
        this.dateHeadersFormatter = dateHeadersFormatter;
    }

    /*
     * PRIVATE METHODS
     * */
    private void recountDateHeaders() {
        List<Integer> indicesToDelete = new ArrayList<>();

        for (int i = 0; i < items.size(); i++) {
            Wrapper wrapper = items.get(i);
            if (wrapper.item instanceof Date) {
                if (i == 0) {
                    indicesToDelete.add(i);
                } else {
                    if (items.get(i - 1).item instanceof Date) {
                        indicesToDelete.add(i);
                    }
                }
            }
        }

        Collections.reverse(indicesToDelete);
        for (int i : indicesToDelete) {
            items.remove(i);
            notifyItemRemoved(i);
        }
    }

    protected void generateDateHeaders(List<MESSAGE> messages) {
        for (int i = 0; i < messages.size(); i++) {
            MESSAGE message = messages.get(i);
            this.items.add(new Wrapper<>(message));
            if (messages.size() > i + 1) {
                MESSAGE nextMessage = messages.get(i + 1);
                if (!DateFormatter.isSameDay(message.getCreatedAt(), nextMessage.getCreatedAt())) {
                    this.items.add(new Wrapper<>(message.getCreatedAt()));
                }
            } else {
                this.items.add(new Wrapper<>(message.getCreatedAt()));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private int getMessagePositionById(String id) {
        for (int i = 0; i < items.size(); i++) {
            Wrapper wrapper = items.get(i);
            if (wrapper.item instanceof IMessage) {
                MESSAGE message = (MESSAGE) wrapper.item;
                if (message.getId().contentEquals(id)) {
                    return i;
                }
            }
        }
        return -1;
    }

    @SuppressWarnings("unchecked")
    private boolean isPreviousSameDate(int position, Date dateToCompare) {
        if (items.size() <= position) return false;
        if (items.get(position).item instanceof IMessage) {
            Date previousPositionDate = ((MESSAGE) items.get(position).item).getCreatedAt();
            return DateFormatter.isSameDay(dateToCompare, previousPositionDate);
        } else return false;
    }

    @SuppressWarnings("unchecked")
    private boolean isPreviousSameAuthor(String id, int position) {
        int prevPosition = position + 1;
        if (items.size() <= prevPosition) return false;
        else return items.get(prevPosition).item instanceof IMessage
                && ((MESSAGE) items.get(prevPosition).item).getUser().getId().contentEquals(id);
    }

    private void incrementSelectedItemsCount() {
        selectedItemsCount++;
        notifySelectionChanged();
    }

    private void decrementSelectedItemsCount() {
        selectedItemsCount--;
        isSelectionModeEnabled = selectedItemsCount > 0;

        notifySelectionChanged();
    }

    private void notifySelectionChanged() {
        if (selectionListener != null) {
            selectionListener.onSelectionChanged(selectedItemsCount);
        }
    }

    private void notifyMessageClicked(MESSAGE message) {
        if (onMessageClickListener != null) {
            onMessageClickListener.onMessageClick(message);
        }
    }

    private void notifyMessageViewClicked(View view, MESSAGE message) {
        if (onMessageViewClickListener != null) {
            onMessageViewClickListener.onMessageViewClick(view, message);
        }
    }

    private void notifyMessageLongClicked(MESSAGE message) {
        if (onMessageLongClickListener != null) {
            onMessageLongClickListener.onMessageLongClick(message);
        }
    }

    private void notifyMessageViewLongClicked(View view, MESSAGE message) {
        if (onMessageViewLongClickListener != null) {
            onMessageViewLongClickListener.onMessageViewLongClick(view, message);
        }
    }

    private View.OnClickListener getMessageClickListener(final Wrapper<MESSAGE> wrapper) {
        return view -> {
            if (selectionListener != null && isSelectionModeEnabled) {
                wrapper.isSelected = !wrapper.isSelected;

                if (wrapper.isSelected) incrementSelectedItemsCount();
                else decrementSelectedItemsCount();

                MESSAGE message = (wrapper.item);
                notifyItemChanged(getMessagePositionById(message.getId()));
            } else {
                notifyMessageClicked(wrapper.item);
                notifyMessageViewClicked(view, wrapper.item);
            }
        };
    }

    private View.OnLongClickListener getMessageLongClickListener(final Wrapper<MESSAGE> wrapper) {
        return view -> {
            if (selectionListener == null) {
                notifyMessageLongClicked(wrapper.item);
                notifyMessageViewLongClicked(view, wrapper.item);
            } else {
                isSelectionModeEnabled = true;
                view.performClick();
            }
            return true;
        };
    }

    private String getSelectedText(Formatter<MESSAGE> formatter, boolean reverse) {
        StringBuilder builder = new StringBuilder();

        ArrayList<MESSAGE> selectedMessages = getSelectedMessages();
        if (reverse) Collections.reverse(selectedMessages);

        for (MESSAGE message : selectedMessages) {
            builder.append(formatter == null
                    ? message.toString()
                    : formatter.format(message));
            builder.append("\n\n");
        }
        builder.replace(builder.length() - 2, builder.length(), "");

        return builder.toString();
    }

    private void copyToClipboard(Context context, String copiedText) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(copiedText, copiedText);
        clipboard.setPrimaryClip(clip);
    }

    void setLayoutManager(RecyclerView.LayoutManager layoutManager) {
        this.layoutManager = layoutManager;
    }

    void setStyle(MessagesListStyle style) {
        this.messagesListStyle = style;
    }

    /*
     * WRAPPER
     * */
    public static class Wrapper<DATA> {
        public DATA item;
        public boolean isSelected;

        Wrapper(DATA item) {
            this.item = item;
        }
    }

    /*
     * LISTENERS
     * */

    /**
     * Interface definition for a callback to be invoked when next part of messages need to be loaded.
     */
    public interface OnLoadMoreListener {

        /**
         * Fires when user scrolled to the end of list.
         *
         * @param page            next page to download.
         * @param totalItemsCount current items count.
         */
        void onLoadMore(int page, int totalItemsCount);
    }

    /**
     * Interface definition for a callback to be invoked when selected messages count is changed.
     */
    public interface SelectionListener {

        /**
         * Fires when selected items count is changed.
         *
         * @param count count of selected items.
         */
        void onSelectionChanged(int count);
    }

    /**
     * Interface definition for a callback to be invoked when message item is clicked.
     */
    public interface OnMessageClickListener<MESSAGE extends IMessage> {

        /**
         * Fires when message is clicked.
         *
         * @param message clicked message.
         */
        void onMessageClick(MESSAGE message);
    }

    /**
     * Interface definition for a callback to be invoked when message view is clicked.
     */
    public interface OnMessageViewClickListener<MESSAGE extends IMessage> {

        /**
         * Fires when message view is clicked.
         *
         * @param message clicked message.
         */
        void onMessageViewClick(View view, MESSAGE message);
    }

    /**
     * Interface definition for a callback to be invoked when message item is long clicked.
     */
    public interface OnMessageLongClickListener<MESSAGE extends IMessage> {

        /**
         * Fires when message is long clicked.
         *
         * @param message clicked message.
         */
        void onMessageLongClick(MESSAGE message);
    }

    /**
     * Interface definition for a callback to be invoked when message view is long clicked.
     */
    public interface OnMessageViewLongClickListener<MESSAGE extends IMessage> {

        /**
         * Fires when message view is long clicked.
         *
         * @param message clicked message.
         */
        void onMessageViewLongClick(View view, MESSAGE message);
    }

    /**
     * Interface used to format your message model when copying.
     */
    public interface Formatter<MESSAGE> {

        /**
         * Formats an string representation of the message object.
         *
         * @param message The object that should be formatted.
         * @return Formatted text.
         */
        String format(MESSAGE message);
    }

    /**
     * This class is deprecated. Use {@link MessageHolders} instead.
     */
    @Deprecated
    public static class HoldersConfig extends MessageHolders {

        /**
         * This method is deprecated. Use {@link MessageHolders#setIncomingTextConfig(Class, int)} instead.
         *
         * @param holder holder class.
         * @param layout layout resource.
         */
        @Deprecated
        public void setIncoming(Class<? extends BaseMessageViewHolder<? extends IMessage>> holder, @LayoutRes int layout) {
            super.setIncomingTextConfig(holder, layout);
        }

        /**
         * This method is deprecated. Use {@link MessageHolders#setIncomingTextHolder(Class)} instead.
         *
         * @param holder holder class.
         */
        @Deprecated
        public void setIncomingHolder(Class<? extends BaseMessageViewHolder<? extends IMessage>> holder) {
            super.setIncomingTextHolder(holder);
        }

        /**
         * This method is deprecated. Use {@link MessageHolders#setIncomingTextLayout(int)} instead.
         *
         * @param layout layout resource.
         */
        @Deprecated
        public void setIncomingLayout(@LayoutRes int layout) {
            super.setIncomingTextLayout(layout);
        }

        /**
         * This method is deprecated. Use {@link MessageHolders#setOutcomingTextConfig(Class, int)} instead.
         *
         * @param holder holder class.
         * @param layout layout resource.
         */
        @Deprecated
        public void setOutcoming(Class<? extends BaseMessageViewHolder<? extends IMessage>> holder, @LayoutRes int layout) {
            super.setOutcomingTextConfig(holder, layout);
        }

        /**
         * This method is deprecated. Use {@link MessageHolders#setOutcomingTextHolder(Class)} instead.
         *
         * @param holder holder class.
         */
        @Deprecated
        public void setOutcomingHolder(Class<? extends BaseMessageViewHolder<? extends IMessage>> holder) {
            super.setOutcomingTextHolder(holder);
        }

        /**
         * This method is deprecated. Use {@link MessageHolders#setOutcomingTextLayout(int)} instead.
         *
         * @param layout layout resource.
         */
        @Deprecated
        public void setOutcomingLayout(@LayoutRes int layout) {
            this.setOutcomingTextLayout(layout);
        }

        /**
         * This method is deprecated. Use {@link MessageHolders#setDateHeaderConfig(Class, int)} instead.
         *
         * @param holder holder class.
         * @param layout layout resource.
         */
        @Deprecated
        public void setDateHeader(Class<? extends ViewHolder<Date>> holder, @LayoutRes int layout) {
            super.setDateHeaderConfig(holder, layout);
        }
    }

    /**
     * This class is deprecated. Use {@link MessageHolders.BaseMessageViewHolder} instead.
     */
    @Deprecated
    public static abstract class BaseMessageViewHolder<MESSAGE extends IMessage>
            extends MessageHolders.BaseMessageViewHolder<MESSAGE> {

        private boolean isSelected;

        /**
         * Callback for implementing images loading in message list
         */
        protected ImageLoader imageLoader;

        public BaseMessageViewHolder(View itemView) {
            super(itemView);
        }

        /**
         * Returns whether is item selected
         *
         * @return weather is item selected.
         */
        public boolean isSelected() {
            return isSelected;
        }

        /**
         * Returns weather is selection mode enabled
         *
         * @return weather is selection mode enabled.
         */
        public boolean isSelectionModeEnabled() {
            return isSelectionModeEnabled;
        }

        /**
         * Getter for {@link #imageLoader}
         *
         * @return image loader interface.
         */
        public ImageLoader getImageLoader() {
            return imageLoader;
        }

        protected void configureLinksBehavior(final TextView text) {
            text.setLinksClickable(false);
            text.setMovementMethod(new LinkMovementMethod() {
                @Override
                public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
                    boolean result = false;
                    if (!isSelectionModeEnabled) {
                        result = super.onTouchEvent(widget, buffer, event);
                    }
                    itemView.onTouchEvent(event);
                    return result;
                }
            });
        }

    }

    /**
     * This class is deprecated. Use {@link MessageHolders.DefaultDateHeaderViewHolder} instead.
     */
    @Deprecated
    public static class DefaultDateHeaderViewHolder extends ViewHolder<Date>
            implements MessageHolders.DefaultMessageViewHolder {

        protected TextView text;
        protected String dateFormat;
        protected DateFormatter.Formatter dateHeadersFormatter;

        public DefaultDateHeaderViewHolder(View itemView) {
            super(itemView);
            text = itemView.findViewById(R.id.messageText);
        }

        @Override
        public void onBind(Date date) {
            if (text != null) {
                String formattedDate = null;
                if (dateHeadersFormatter != null) formattedDate = dateHeadersFormatter.format(date);
                text.setText(formattedDate == null ? DateFormatter.format(date, dateFormat) : formattedDate);
            }
        }

        @Override
        public void applyStyle(MessagesListStyle style) {
            if (text != null) {
                text.setTextColor(style.getDateHeaderTextColor());
                text.setTextSize(TypedValue.COMPLEX_UNIT_PX, style.getDateHeaderTextSize());
                text.setTypeface(text.getTypeface(), style.getDateHeaderTextStyle());
                text.setPadding(style.getDateHeaderPadding(), style.getDateHeaderPadding(),
                        style.getDateHeaderPadding(), style.getDateHeaderPadding());
            }
            dateFormat = style.getDateHeaderFormat();
            dateFormat = dateFormat == null ? DateFormatter.Template.STRING_DAY_MONTH_YEAR.get() : dateFormat;
        }
    }

    /**
     * This class is deprecated. Use {@link MessageHolders.IncomingTextMessageViewHolder} instead.
     */
    @Deprecated
    public static class IncomingMessageViewHolder<MESSAGE extends IMessage>
            extends MessageHolders.IncomingTextMessageViewHolder<MESSAGE>
            implements MessageHolders.DefaultMessageViewHolder {

        public IncomingMessageViewHolder(View itemView) {
            super(itemView);
        }
    }

    /**
     * This class is deprecated. Use {@link MessageHolders.OutcomingTextMessageViewHolder} instead.
     */
    @Deprecated
    public static class OutcomingMessageViewHolder<MESSAGE extends IMessage>
            extends MessageHolders.OutcomingTextMessageViewHolder<MESSAGE> {

        public OutcomingMessageViewHolder(View itemView) {
            super(itemView);
        }
    }
}

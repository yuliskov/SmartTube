package com.stfalcon.chatkit.messages;

import android.text.Spannable;
import android.text.method.LinkMovementMethod;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;

import com.stfalcon.chatkit.R;
import com.stfalcon.chatkit.commons.ImageLoader;
import com.stfalcon.chatkit.commons.ViewHolder;
import com.stfalcon.chatkit.commons.models.IMessage;
import com.stfalcon.chatkit.commons.models.MessageContentType;
import com.stfalcon.chatkit.utils.DateFormatter;
import com.stfalcon.chatkit.utils.RoundedImageView;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/*
 * Created by troy379 on 31.03.17.
 */
@SuppressWarnings("WeakerAccess")
public class MessageHolders {

    private static final short VIEW_TYPE_DATE_HEADER = 130;
    private static final short VIEW_TYPE_TEXT_MESSAGE = 131;
    private static final short VIEW_TYPE_IMAGE_MESSAGE = 132;

    private Class<? extends ViewHolder<Date>> dateHeaderHolder;
    private int dateHeaderLayout;

    private HolderConfig<IMessage> incomingTextConfig;
    private HolderConfig<IMessage> outcomingTextConfig;
    private HolderConfig<MessageContentType.Image> incomingImageConfig;
    private HolderConfig<MessageContentType.Image> outcomingImageConfig;

    private List<ContentTypeConfig> customContentTypes = new ArrayList<>();
    private ContentChecker contentChecker;

    public MessageHolders() {
        this.dateHeaderHolder = DefaultDateHeaderViewHolder.class;
        this.dateHeaderLayout = R.layout.item_date_header;

        this.incomingTextConfig = new HolderConfig<>(DefaultIncomingTextMessageViewHolder.class, R.layout.item_incoming_text_message);
        this.outcomingTextConfig = new HolderConfig<>(DefaultOutcomingTextMessageViewHolder.class, R.layout.item_outcoming_text_message);
        this.incomingImageConfig = new HolderConfig<>(DefaultIncomingImageMessageViewHolder.class, R.layout.item_incoming_image_message);
        this.outcomingImageConfig = new HolderConfig<>(DefaultOutcomingImageMessageViewHolder.class, R.layout.item_outcoming_image_message);
    }

    /**
     * Sets both of custom view holder class and layout resource for incoming text message.
     *
     * @param holder holder class.
     * @param layout layout resource.
     * @return {@link MessageHolders} for subsequent configuration.
     */
    public MessageHolders setIncomingTextConfig(
            @NonNull Class<? extends BaseMessageViewHolder<? extends IMessage>> holder,
            @LayoutRes int layout) {
        this.incomingTextConfig.holder = holder;
        this.incomingTextConfig.layout = layout;
        return this;
    }

    /**
     * Sets both of custom view holder class and layout resource for incoming text message.
     *
     * @param holder  holder class.
     * @param layout  layout resource.
     * @param payload custom data.
     * @return {@link MessageHolders} for subsequent configuration.
     */
    public MessageHolders setIncomingTextConfig(
            @NonNull Class<? extends BaseMessageViewHolder<? extends IMessage>> holder,
            @LayoutRes int layout,
            Object payload) {
        this.incomingTextConfig.holder = holder;
        this.incomingTextConfig.layout = layout;
        this.incomingTextConfig.payload = payload;
        return this;
    }

    /**
     * Sets custom view holder class for incoming text message.
     *
     * @param holder holder class.
     * @return {@link MessageHolders} for subsequent configuration.
     */
    public MessageHolders setIncomingTextHolder(
            @NonNull Class<? extends BaseMessageViewHolder<? extends IMessage>> holder) {
        this.incomingTextConfig.holder = holder;
        return this;
    }

    /**
     * Sets custom view holder class for incoming text message.
     *
     * @param holder  holder class.
     * @param payload custom data.
     * @return {@link MessageHolders} for subsequent configuration.
     */
    public MessageHolders setIncomingTextHolder(
            @NonNull Class<? extends BaseMessageViewHolder<? extends IMessage>> holder,
            Object payload) {
        this.incomingTextConfig.holder = holder;
        this.incomingTextConfig.payload = payload;
        return this;
    }

    /**
     * Sets custom layout resource for incoming text message.
     *
     * @param layout layout resource.
     * @return {@link MessageHolders} for subsequent configuration.
     */
    public MessageHolders setIncomingTextLayout(@LayoutRes int layout) {
        this.incomingTextConfig.layout = layout;
        return this;
    }

    /**
     * Sets custom layout resource for incoming text message.
     *
     * @param layout  layout resource.
     * @param payload custom data.
     * @return {@link MessageHolders} for subsequent configuration.
     */
    public MessageHolders setIncomingTextLayout(@LayoutRes int layout, Object payload) {
        this.incomingTextConfig.layout = layout;
        this.incomingTextConfig.payload = payload;
        return this;
    }

    /**
     * Sets both of custom view holder class and layout resource for outcoming text message.
     *
     * @param holder holder class.
     * @param layout layout resource.
     * @return {@link MessageHolders} for subsequent configuration.
     */
    public MessageHolders setOutcomingTextConfig(
            @NonNull Class<? extends BaseMessageViewHolder<? extends IMessage>> holder,
            @LayoutRes int layout) {
        this.outcomingTextConfig.holder = holder;
        this.outcomingTextConfig.layout = layout;
        return this;
    }

    /**
     * Sets both of custom view holder class and layout resource for outcoming text message.
     *
     * @param holder  holder class.
     * @param layout  layout resource.
     * @param payload custom data.
     * @return {@link MessageHolders} for subsequent configuration.
     */
    public MessageHolders setOutcomingTextConfig(
            @NonNull Class<? extends BaseMessageViewHolder<? extends IMessage>> holder,
            @LayoutRes int layout,
            Object payload) {
        this.outcomingTextConfig.holder = holder;
        this.outcomingTextConfig.layout = layout;
        this.outcomingTextConfig.payload = payload;
        return this;
    }

    /**
     * Sets custom view holder class for outcoming text message.
     *
     * @param holder holder class.
     * @return {@link MessageHolders} for subsequent configuration.
     */
    public MessageHolders setOutcomingTextHolder(
            @NonNull Class<? extends BaseMessageViewHolder<? extends IMessage>> holder) {
        this.outcomingTextConfig.holder = holder;
        return this;
    }

    /**
     * Sets custom view holder class for outcoming text message.
     *
     * @param holder  holder class.
     * @param payload custom data.
     * @return {@link MessageHolders} for subsequent configuration.
     */
    public MessageHolders setOutcomingTextHolder(
            @NonNull Class<? extends BaseMessageViewHolder<? extends IMessage>> holder,
            Object payload) {
        this.outcomingTextConfig.holder = holder;
        this.outcomingTextConfig.payload = payload;
        return this;
    }

    /**
     * Sets custom layout resource for outcoming text message.
     *
     * @param layout layout resource.
     * @return {@link MessageHolders} for subsequent configuration.
     */
    public MessageHolders setOutcomingTextLayout(@LayoutRes int layout) {
        this.outcomingTextConfig.layout = layout;
        return this;
    }

    /**
     * Sets custom layout resource for outcoming text message.
     *
     * @param layout  layout resource.
     * @param payload custom data.
     * @return {@link MessageHolders} for subsequent configuration.
     */
    public MessageHolders setOutcomingTextLayout(@LayoutRes int layout, Object payload) {
        this.outcomingTextConfig.layout = layout;
        this.outcomingTextConfig.payload = payload;
        return this;
    }

    /**
     * Sets both of custom view holder class and layout resource for incoming image message.
     *
     * @param holder holder class.
     * @param layout layout resource.
     * @return {@link MessageHolders} for subsequent configuration.
     */
    public MessageHolders setIncomingImageConfig(
            @NonNull Class<? extends BaseMessageViewHolder<? extends MessageContentType.Image>> holder,
            @LayoutRes int layout) {
        this.incomingImageConfig.holder = holder;
        this.incomingImageConfig.layout = layout;
        return this;
    }

    /**
     * Sets both of custom view holder class and layout resource for incoming image message.
     *
     * @param holder  holder class.
     * @param layout  layout resource.
     * @param payload custom data.
     * @return {@link MessageHolders} for subsequent configuration.
     */
    public MessageHolders setIncomingImageConfig(
            @NonNull Class<? extends BaseMessageViewHolder<? extends MessageContentType.Image>> holder,
            @LayoutRes int layout,
            Object payload) {
        this.incomingImageConfig.holder = holder;
        this.incomingImageConfig.layout = layout;
        this.incomingImageConfig.payload = payload;
        return this;
    }

    /**
     * Sets custom view holder class for incoming image message.
     *
     * @param holder holder class.
     * @return {@link MessageHolders} for subsequent configuration.
     */
    public MessageHolders setIncomingImageHolder(
            @NonNull Class<? extends BaseMessageViewHolder<? extends MessageContentType.Image>> holder) {
        this.incomingImageConfig.holder = holder;
        return this;
    }

    /**
     * Sets custom view holder class for incoming image message.
     *
     * @param holder  holder class.
     * @param payload custom data.
     * @return {@link MessageHolders} for subsequent configuration.
     */
    public MessageHolders setIncomingImageHolder(
            @NonNull Class<? extends BaseMessageViewHolder<? extends MessageContentType.Image>> holder,
            Object payload) {
        this.incomingImageConfig.holder = holder;
        this.incomingImageConfig.payload = payload;
        return this;
    }

    /**
     * Sets custom layout resource for incoming image message.
     *
     * @param layout layout resource.
     * @return {@link MessageHolders} for subsequent configuration.
     */
    public MessageHolders setIncomingImageLayout(@LayoutRes int layout) {
        this.incomingImageConfig.layout = layout;
        return this;
    }

    /**
     * Sets custom layout resource for incoming image message.
     *
     * @param layout  layout resource.
     * @param payload custom data.
     * @return {@link MessageHolders} for subsequent configuration.
     */
    public MessageHolders setIncomingImageLayout(@LayoutRes int layout, Object payload) {
        this.incomingImageConfig.layout = layout;
        this.incomingImageConfig.payload = payload;
        return this;
    }

    /**
     * Sets both of custom view holder class and layout resource for outcoming image message.
     *
     * @param holder holder class.
     * @param layout layout resource.
     * @return {@link MessageHolders} for subsequent configuration.
     */
    public MessageHolders setOutcomingImageConfig(
            @NonNull Class<? extends BaseMessageViewHolder<? extends MessageContentType.Image>> holder,
            @LayoutRes int layout) {
        this.outcomingImageConfig.holder = holder;
        this.outcomingImageConfig.layout = layout;
        return this;
    }

    /**
     * Sets both of custom view holder class and layout resource for outcoming image message.
     *
     * @param holder  holder class.
     * @param layout  layout resource.
     * @param payload custom data.
     * @return {@link MessageHolders} for subsequent configuration.
     */
    public MessageHolders setOutcomingImageConfig(
            @NonNull Class<? extends BaseMessageViewHolder<? extends MessageContentType.Image>> holder,
            @LayoutRes int layout,
            Object payload) {
        this.outcomingImageConfig.holder = holder;
        this.outcomingImageConfig.layout = layout;
        this.outcomingImageConfig.payload = payload;
        return this;
    }

    /**
     * Sets custom view holder class for outcoming image message.
     *
     * @param holder holder class.
     * @return {@link MessageHolders} for subsequent configuration.
     */
    public MessageHolders setOutcomingImageHolder(
            @NonNull Class<? extends BaseMessageViewHolder<? extends MessageContentType.Image>> holder) {
        this.outcomingImageConfig.holder = holder;
        return this;
    }

    /**
     * Sets custom view holder class for outcoming image message.
     *
     * @param holder  holder class.
     * @param payload custom data.
     * @return {@link MessageHolders} for subsequent configuration.
     */
    public MessageHolders setOutcomingImageHolder(
            @NonNull Class<? extends BaseMessageViewHolder<? extends MessageContentType.Image>> holder,
            Object payload) {
        this.outcomingImageConfig.holder = holder;
        this.outcomingImageConfig.payload = payload;
        return this;
    }

    /**
     * Sets custom layout resource for outcoming image message.
     *
     * @param layout layout resource.
     * @return {@link MessageHolders} for subsequent configuration.
     */
    public MessageHolders setOutcomingImageLayout(@LayoutRes int layout) {
        this.outcomingImageConfig.layout = layout;
        return this;
    }

    /**
     * Sets custom layout resource for outcoming image message.
     *
     * @param layout  layout resource.
     * @param payload custom data.
     * @return {@link MessageHolders} for subsequent configuration.
     */
    public MessageHolders setOutcomingImageLayout(@LayoutRes int layout, Object payload) {
        this.outcomingImageConfig.layout = layout;
        this.outcomingImageConfig.payload = payload;
        return this;
    }

    /**
     * Sets both of custom view holder class and layout resource for date header.
     *
     * @param holder holder class.
     * @param layout layout resource.
     * @return {@link MessageHolders} for subsequent configuration.
     */
    public MessageHolders setDateHeaderConfig(
            @NonNull Class<? extends ViewHolder<Date>> holder,
            @LayoutRes int layout) {
        this.dateHeaderHolder = holder;
        this.dateHeaderLayout = layout;
        return this;
    }

    /**
     * Sets custom view holder class for date header.
     *
     * @param holder holder class.
     * @return {@link MessageHolders} for subsequent configuration.
     */
    public MessageHolders setDateHeaderHolder(@NonNull Class<? extends ViewHolder<Date>> holder) {
        this.dateHeaderHolder = holder;
        return this;
    }

    /**
     * Sets custom layout reource for date header.
     *
     * @param layout layout resource.
     * @return {@link MessageHolders} for subsequent configuration.
     */
    public MessageHolders setDateHeaderLayout(@LayoutRes int layout) {
        this.dateHeaderLayout = layout;
        return this;
    }

    /**
     * Registers custom content type (e.g. multimedia, events etc.)
     *
     * @param type            unique id for content type
     * @param holder          holder class for incoming and outcoming messages
     * @param incomingLayout  layout resource for incoming message
     * @param outcomingLayout layout resource for outcoming message
     * @param contentChecker  {@link ContentChecker} for registered type
     * @return {@link MessageHolders} for subsequent configuration.
     */
    public <TYPE extends MessageContentType>
    MessageHolders registerContentType(
            byte type, @NonNull Class<? extends BaseMessageViewHolder<TYPE>> holder,
            @LayoutRes int incomingLayout,
            @LayoutRes int outcomingLayout,
            @NonNull ContentChecker contentChecker) {

        return registerContentType(type,
                holder, incomingLayout,
                holder, outcomingLayout,
                contentChecker);
    }

    /**
     * Registers custom content type (e.g. multimedia, events etc.)
     *
     * @param type            unique id for content type
     * @param incomingHolder  holder class for incoming message
     * @param outcomingHolder holder class for outcoming message
     * @param incomingLayout  layout resource for incoming message
     * @param outcomingLayout layout resource for outcoming message
     * @param contentChecker  {@link ContentChecker} for registered type
     * @return {@link MessageHolders} for subsequent configuration.
     */
    public <TYPE extends MessageContentType>
    MessageHolders registerContentType(
            byte type,
            @NonNull Class<? extends BaseMessageViewHolder<TYPE>> incomingHolder, @LayoutRes int incomingLayout,
            @NonNull Class<? extends BaseMessageViewHolder<TYPE>> outcomingHolder, @LayoutRes int outcomingLayout,
            @NonNull ContentChecker contentChecker) {

        if (type == 0)
            throw new IllegalArgumentException("content type must be greater or less than '0'!");

        customContentTypes.add(
                new ContentTypeConfig<>(type,
                        new HolderConfig<>(incomingHolder, incomingLayout),
                        new HolderConfig<>(outcomingHolder, outcomingLayout)));
        this.contentChecker = contentChecker;
        return this;
    }

    /**
     * Registers custom content type (e.g. multimedia, events etc.)
     *
     * @param type             unique id for content type
     * @param incomingHolder   holder class for incoming message
     * @param outcomingHolder  holder class for outcoming message
     * @param incomingPayload  payload for incoming message
     * @param outcomingPayload payload for outcoming message
     * @param incomingLayout   layout resource for incoming message
     * @param outcomingLayout  layout resource for outcoming message
     * @param contentChecker   {@link MessageHolders.ContentChecker} for registered type
     * @return {@link MessageHolders} for subsequent configuration.
     */
    public <TYPE extends MessageContentType>
    MessageHolders registerContentType(
            byte type,
            @NonNull Class<? extends MessageHolders.BaseMessageViewHolder<TYPE>> incomingHolder, Object incomingPayload, @LayoutRes int incomingLayout,
            @NonNull Class<? extends MessageHolders.BaseMessageViewHolder<TYPE>> outcomingHolder, Object outcomingPayload, @LayoutRes int outcomingLayout,
            @NonNull MessageHolders.ContentChecker contentChecker) {

        if (type == 0)
            throw new IllegalArgumentException("content type must be greater or less than '0'!");

        customContentTypes.add(
                new MessageHolders.ContentTypeConfig<>(type,
                        new HolderConfig<>(incomingHolder, incomingLayout, incomingPayload),
                        new HolderConfig<>(outcomingHolder, outcomingLayout, outcomingPayload)));
        this.contentChecker = contentChecker;
        return this;
    }

    /*
     * INTERFACES
     * */

    /**
     * The interface, which contains logic for checking the availability of content.
     */
    public interface ContentChecker<MESSAGE extends IMessage> {

        /**
         * Checks the availability of content.
         *
         * @param message current message in list.
         * @param type    content type, for which content availability is determined.
         * @return weather the message has content for the current message.
         */
        boolean hasContentFor(MESSAGE message, byte type);
    }

    /*
     * PRIVATE METHODS
     * */

    protected ViewHolder getHolder(ViewGroup parent, int viewType, MessagesListStyle messagesListStyle) {
        switch (viewType) {
            case VIEW_TYPE_DATE_HEADER:
                return getHolder(parent, dateHeaderLayout, dateHeaderHolder, messagesListStyle, null);
            case VIEW_TYPE_TEXT_MESSAGE:
                return getHolder(parent, incomingTextConfig, messagesListStyle);
            case -VIEW_TYPE_TEXT_MESSAGE:
                return getHolder(parent, outcomingTextConfig, messagesListStyle);
            case VIEW_TYPE_IMAGE_MESSAGE:
                return getHolder(parent, incomingImageConfig, messagesListStyle);
            case -VIEW_TYPE_IMAGE_MESSAGE:
                return getHolder(parent, outcomingImageConfig, messagesListStyle);
            default:
                for (ContentTypeConfig typeConfig : customContentTypes) {
                    if (Math.abs(typeConfig.type) == Math.abs(viewType)) {
                        if (viewType > 0)
                            return getHolder(parent, typeConfig.incomingConfig, messagesListStyle);
                        else
                            return getHolder(parent, typeConfig.outcomingConfig, messagesListStyle);
                    }
                }
        }
        throw new IllegalStateException("Wrong message view type. Please, report this issue on GitHub with full stacktrace in description.");
    }

    @SuppressWarnings("unchecked")
    protected void bind(final ViewHolder holder, final Object item, boolean isSelected,
                        final ImageLoader imageLoader,
                        final View.OnClickListener onMessageClickListener,
                        final View.OnLongClickListener onMessageLongClickListener,
                        final DateFormatter.Formatter dateHeadersFormatter,
                        final SparseArray<MessagesListAdapter.OnMessageViewClickListener> clickListenersArray) {

        if (item instanceof IMessage) {
            ((MessageHolders.BaseMessageViewHolder) holder).isSelected = isSelected;
            ((MessageHolders.BaseMessageViewHolder) holder).imageLoader = imageLoader;
            holder.itemView.setOnLongClickListener(onMessageLongClickListener);
            holder.itemView.setOnClickListener(onMessageClickListener);

            for (int i = 0; i < clickListenersArray.size(); i++) {
                final int key = clickListenersArray.keyAt(i);
                final View view = holder.itemView.findViewById(key);
                if (view != null) {
                    view.setOnClickListener(v -> clickListenersArray.get(key).onMessageViewClick(view, (IMessage) item));
                }
            }
        } else if (item instanceof Date) {
            ((MessageHolders.DefaultDateHeaderViewHolder) holder).dateHeadersFormatter = dateHeadersFormatter;
        }

        holder.onBind(item);
    }


    protected int getViewType(Object item, String senderId) {
        boolean isOutcoming = false;
        int viewType;

        if (item instanceof IMessage) {
            IMessage message = (IMessage) item;
            isOutcoming = message.getUser().getId().contentEquals(senderId);
            viewType = getContentViewType(message);

        } else viewType = VIEW_TYPE_DATE_HEADER;

        return isOutcoming ? viewType * -1 : viewType;
    }

    private ViewHolder getHolder(ViewGroup parent, HolderConfig holderConfig,
                                 MessagesListStyle style) {
        return getHolder(parent, holderConfig.layout, holderConfig.holder, style, holderConfig.payload);
    }

    private <HOLDER extends ViewHolder>
    ViewHolder getHolder(ViewGroup parent, @LayoutRes int layout, Class<HOLDER> holderClass,
                         MessagesListStyle style, Object payload) {

        View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        try {
            Constructor<HOLDER> constructor = null;
            HOLDER holder;
            try {
                constructor = holderClass.getDeclaredConstructor(View.class, Object.class);
                constructor.setAccessible(true);
                holder = constructor.newInstance(v, payload);
            } catch (NoSuchMethodException e) {
                constructor = holderClass.getDeclaredConstructor(View.class);
                constructor.setAccessible(true);
                holder = constructor.newInstance(v);
            }
            if (holder instanceof DefaultMessageViewHolder && style != null) {
                ((DefaultMessageViewHolder) holder).applyStyle(style);
            }
            return holder;
        } catch (Exception e) {
            throw new UnsupportedOperationException("Somehow we couldn't create the ViewHolder for message. Please, report this issue on GitHub with full stacktrace in description.", e);
        }
    }

    @SuppressWarnings("unchecked")
    private short getContentViewType(IMessage message) {
        if (message instanceof MessageContentType.Image
                && ((MessageContentType.Image) message).getImageUrl() != null) {
            return VIEW_TYPE_IMAGE_MESSAGE;
        }

        // other default types will be here

        if (message instanceof MessageContentType) {
            for (int i = 0; i < customContentTypes.size(); i++) {
                ContentTypeConfig config = customContentTypes.get(i);
                if (contentChecker == null) {
                    throw new IllegalArgumentException("ContentChecker cannot be null when using custom content types!");
                }
                boolean hasContent = contentChecker.hasContentFor(message, config.type);
                if (hasContent) return config.type;
            }
        }

        return VIEW_TYPE_TEXT_MESSAGE;
    }

    /*
     * HOLDERS
     * */

    /**
     * The base class for view holders for incoming and outcoming message.
     * You can extend it to create your own holder in conjuction with custom layout or even using default layout.
     */
    public static abstract class BaseMessageViewHolder<MESSAGE extends IMessage> extends ViewHolder<MESSAGE> {

        boolean isSelected;

        /**
         * For setting custom data to ViewHolder
         */
        protected Object payload;

        /**
         * Callback for implementing images loading in message list
         */
        protected ImageLoader imageLoader;

        @Deprecated
        public BaseMessageViewHolder(View itemView) {
            super(itemView);
        }

        public BaseMessageViewHolder(View itemView, Object payload) {
            super(itemView);
            this.payload = payload;
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
            return MessagesListAdapter.isSelectionModeEnabled;
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
                    if (!MessagesListAdapter.isSelectionModeEnabled) {
                        result = super.onTouchEvent(widget, buffer, event);
                    }
                    itemView.onTouchEvent(event);
                    return result;
                }
            });
        }
    }

    /**
     * Default view holder implementation for incoming text message
     */
    public static class IncomingTextMessageViewHolder<MESSAGE extends IMessage>
            extends BaseIncomingMessageViewHolder<MESSAGE> {

        protected ViewGroup bubble;
        protected TextView text;

        @Deprecated
        public IncomingTextMessageViewHolder(View itemView) {
            super(itemView);
            init(itemView);
        }

        public IncomingTextMessageViewHolder(View itemView, Object payload) {
            super(itemView, payload);
            init(itemView);
        }

        @Override
        public void onBind(MESSAGE message) {
            super.onBind(message);
            if (bubble != null) {
                bubble.setSelected(isSelected());
            }

            if (text != null) {
                text.setText(message.getText());
            }
        }

        @Override
        public void applyStyle(MessagesListStyle style) {
            super.applyStyle(style);
            if (bubble != null) {
                bubble.setPadding(style.getIncomingDefaultBubblePaddingLeft(),
                        style.getIncomingDefaultBubblePaddingTop(),
                        style.getIncomingDefaultBubblePaddingRight(),
                        style.getIncomingDefaultBubblePaddingBottom());
                ViewCompat.setBackground(bubble, style.getIncomingBubbleDrawable());
            }

            if (text != null) {
                text.setTextColor(style.getIncomingTextColor());
                text.setTextSize(TypedValue.COMPLEX_UNIT_PX, style.getIncomingTextSize());
                text.setTypeface(text.getTypeface(), style.getIncomingTextStyle());
                text.setAutoLinkMask(style.getTextAutoLinkMask());
                text.setLinkTextColor(style.getIncomingTextLinkColor());
                configureLinksBehavior(text);
            }
        }

        private void init(View itemView) {
            bubble = itemView.findViewById(R.id.bubble);
            text = itemView.findViewById(R.id.messageText);
        }
    }

    /**
     * Default view holder implementation for outcoming text message
     */
    public static class OutcomingTextMessageViewHolder<MESSAGE extends IMessage>
            extends BaseOutcomingMessageViewHolder<MESSAGE> {

        protected ViewGroup bubble;
        protected TextView text;

        @Deprecated
        public OutcomingTextMessageViewHolder(View itemView) {
            super(itemView);
            init(itemView);
        }

        public OutcomingTextMessageViewHolder(View itemView, Object payload) {
            super(itemView, payload);
            init(itemView);
        }

        @Override
        public void onBind(MESSAGE message) {
            super.onBind(message);
            if (bubble != null) {
                bubble.setSelected(isSelected());
            }

            if (text != null) {
                text.setText(message.getText());
            }
        }

        @Override
        public final void applyStyle(MessagesListStyle style) {
            super.applyStyle(style);
            if (bubble != null) {
                bubble.setPadding(style.getOutcomingDefaultBubblePaddingLeft(),
                        style.getOutcomingDefaultBubblePaddingTop(),
                        style.getOutcomingDefaultBubblePaddingRight(),
                        style.getOutcomingDefaultBubblePaddingBottom());
                ViewCompat.setBackground(bubble, style.getOutcomingBubbleDrawable());
            }

            if (text != null) {
                text.setTextColor(style.getOutcomingTextColor());
                text.setTextSize(TypedValue.COMPLEX_UNIT_PX, style.getOutcomingTextSize());
                text.setTypeface(text.getTypeface(), style.getOutcomingTextStyle());
                text.setAutoLinkMask(style.getTextAutoLinkMask());
                text.setLinkTextColor(style.getOutcomingTextLinkColor());
                configureLinksBehavior(text);
            }
        }

        private void init(View itemView) {
            bubble = itemView.findViewById(R.id.bubble);
            text = itemView.findViewById(R.id.messageText);
        }
    }

    /**
     * Default view holder implementation for incoming image message
     */
    public static class IncomingImageMessageViewHolder<MESSAGE extends MessageContentType.Image>
            extends BaseIncomingMessageViewHolder<MESSAGE> {

        protected ImageView image;
        protected View imageOverlay;

        @Deprecated
        public IncomingImageMessageViewHolder(View itemView) {
            super(itemView);
            init(itemView);
        }

        public IncomingImageMessageViewHolder(View itemView, Object payload) {
            super(itemView, payload);
            init(itemView);
        }

        @Override
        public void onBind(MESSAGE message) {
            super.onBind(message);
            if (image != null && imageLoader != null) {
                imageLoader.loadImage(image, message.getImageUrl(), getPayloadForImageLoader(message));
            }

            if (imageOverlay != null) {
                imageOverlay.setSelected(isSelected());
            }
        }

        @Override
        public final void applyStyle(MessagesListStyle style) {
            super.applyStyle(style);
            if (time != null) {
                time.setTextColor(style.getIncomingImageTimeTextColor());
                time.setTextSize(TypedValue.COMPLEX_UNIT_PX, style.getIncomingImageTimeTextSize());
                time.setTypeface(time.getTypeface(), style.getIncomingImageTimeTextStyle());
            }

            if (imageOverlay != null) {
                ViewCompat.setBackground(imageOverlay, style.getIncomingImageOverlayDrawable());
            }
        }

        /**
         * Override this method to have ability to pass custom data in ImageLoader for loading image(not avatar).
         *
         * @param message Message with image
         */
        protected Object getPayloadForImageLoader(MESSAGE message) {
            return null;
        }

        private void init(View itemView) {
            image = itemView.findViewById(R.id.image);
            imageOverlay = itemView.findViewById(R.id.imageOverlay);

            if (image instanceof RoundedImageView) {
                ((RoundedImageView) image).setCorners(
                        R.dimen.message_bubble_corners_radius,
                        R.dimen.message_bubble_corners_radius,
                        R.dimen.message_bubble_corners_radius,
                        0
                );
            }
        }
    }

    /**
     * Default view holder implementation for outcoming image message
     */
    public static class OutcomingImageMessageViewHolder<MESSAGE extends MessageContentType.Image>
            extends BaseOutcomingMessageViewHolder<MESSAGE> {

        protected ImageView image;
        protected View imageOverlay;

        @Deprecated
        public OutcomingImageMessageViewHolder(View itemView) {
            super(itemView);
            init(itemView);
        }

        public OutcomingImageMessageViewHolder(View itemView, Object payload) {
            super(itemView, payload);
            init(itemView);
        }

        @Override
        public void onBind(MESSAGE message) {
            super.onBind(message);
            if (image != null && imageLoader != null) {
                imageLoader.loadImage(image, message.getImageUrl(), getPayloadForImageLoader(message));
            }

            if (imageOverlay != null) {
                imageOverlay.setSelected(isSelected());
            }
        }

        @Override
        public final void applyStyle(MessagesListStyle style) {
            super.applyStyle(style);
            if (time != null) {
                time.setTextColor(style.getOutcomingImageTimeTextColor());
                time.setTextSize(TypedValue.COMPLEX_UNIT_PX, style.getOutcomingImageTimeTextSize());
                time.setTypeface(time.getTypeface(), style.getOutcomingImageTimeTextStyle());
            }

            if (imageOverlay != null) {
                ViewCompat.setBackground(imageOverlay, style.getOutcomingImageOverlayDrawable());
            }
        }

        /**
         * Override this method to have ability to pass custom data in ImageLoader for loading image(not avatar).
         *
         * @param message Message with image
         */
        protected Object getPayloadForImageLoader(MESSAGE message) {
            return null;
        }

        private void init(View itemView) {
            image = itemView.findViewById(R.id.image);
            imageOverlay = itemView.findViewById(R.id.imageOverlay);

            if (image instanceof RoundedImageView) {
                ((RoundedImageView) image).setCorners(
                        R.dimen.message_bubble_corners_radius,
                        R.dimen.message_bubble_corners_radius,
                        0,
                        R.dimen.message_bubble_corners_radius
                );
            }
        }
    }

    /**
     * Default view holder implementation for date header
     */
    public static class DefaultDateHeaderViewHolder extends ViewHolder<Date>
            implements DefaultMessageViewHolder {

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
     * Base view holder for incoming message
     */
    public abstract static class BaseIncomingMessageViewHolder<MESSAGE extends IMessage>
            extends BaseMessageViewHolder<MESSAGE> implements DefaultMessageViewHolder {

        protected TextView time;
        protected ImageView userAvatar;

        @Deprecated
        public BaseIncomingMessageViewHolder(View itemView) {
            super(itemView);
            init(itemView);
        }

        public BaseIncomingMessageViewHolder(View itemView, Object payload) {
            super(itemView, payload);
            init(itemView);
        }

        @Override
        public void onBind(MESSAGE message) {
            if (time != null) {
                time.setText(DateFormatter.format(message.getCreatedAt(), DateFormatter.Template.TIME));
            }

            if (userAvatar != null) {
                boolean isAvatarExists = imageLoader != null
                        && message.getUser().getAvatar() != null
                        && !message.getUser().getAvatar().isEmpty();

                userAvatar.setVisibility(isAvatarExists ? View.VISIBLE : View.GONE);
                if (isAvatarExists) {
                    imageLoader.loadImage(userAvatar, message.getUser().getAvatar(), null);
                }
            }
        }

        @Override
        public void applyStyle(MessagesListStyle style) {
            if (time != null) {
                time.setTextColor(style.getIncomingTimeTextColor());
                time.setTextSize(TypedValue.COMPLEX_UNIT_PX, style.getIncomingTimeTextSize());
                time.setTypeface(time.getTypeface(), style.getIncomingTimeTextStyle());
            }

            if (userAvatar != null) {
                userAvatar.getLayoutParams().width = style.getIncomingAvatarWidth();
                userAvatar.getLayoutParams().height = style.getIncomingAvatarHeight();
            }

        }

        private void init(View itemView) {
            time = itemView.findViewById(R.id.messageTime);
            userAvatar = itemView.findViewById(R.id.messageUserAvatar);
        }
    }

    /**
     * Base view holder for outcoming message
     */
    public abstract static class BaseOutcomingMessageViewHolder<MESSAGE extends IMessage>
            extends BaseMessageViewHolder<MESSAGE> implements DefaultMessageViewHolder {

        protected TextView time;

        @Deprecated
        public BaseOutcomingMessageViewHolder(View itemView) {
            super(itemView);
            init(itemView);
        }

        public BaseOutcomingMessageViewHolder(View itemView, Object payload) {
            super(itemView, payload);
            init(itemView);
        }

        @Override
        public void onBind(MESSAGE message) {
            if (time != null) {
                time.setText(DateFormatter.format(message.getCreatedAt(), DateFormatter.Template.TIME));
            }
        }

        @Override
        public void applyStyle(MessagesListStyle style) {
            if (time != null) {
                time.setTextColor(style.getOutcomingTimeTextColor());
                time.setTextSize(TypedValue.COMPLEX_UNIT_PX, style.getOutcomingTimeTextSize());
                time.setTypeface(time.getTypeface(), style.getOutcomingTimeTextStyle());
            }
        }

        private void init(View itemView) {
            time = itemView.findViewById(R.id.messageTime);
        }
    }

    /*
     * DEFAULTS
     * */

    interface DefaultMessageViewHolder {
        void applyStyle(MessagesListStyle style);
    }

    private static class DefaultIncomingTextMessageViewHolder
            extends IncomingTextMessageViewHolder<IMessage> {

        public DefaultIncomingTextMessageViewHolder(View itemView) {
            super(itemView, null);
        }
    }

    private static class DefaultOutcomingTextMessageViewHolder
            extends OutcomingTextMessageViewHolder<IMessage> {

        public DefaultOutcomingTextMessageViewHolder(View itemView) {
            super(itemView, null);
        }
    }

    private static class DefaultIncomingImageMessageViewHolder
            extends IncomingImageMessageViewHolder<MessageContentType.Image> {

        public DefaultIncomingImageMessageViewHolder(View itemView) {
            super(itemView, null);
        }
    }

    private static class DefaultOutcomingImageMessageViewHolder
            extends OutcomingImageMessageViewHolder<MessageContentType.Image> {

        public DefaultOutcomingImageMessageViewHolder(View itemView) {
            super(itemView, null);
        }
    }

    private static class ContentTypeConfig<TYPE extends MessageContentType> {

        private byte type;
        private HolderConfig<TYPE> incomingConfig;
        private HolderConfig<TYPE> outcomingConfig;

        private ContentTypeConfig(
                byte type, HolderConfig<TYPE> incomingConfig, HolderConfig<TYPE> outcomingConfig) {

            this.type = type;
            this.incomingConfig = incomingConfig;
            this.outcomingConfig = outcomingConfig;
        }
    }

    private static class HolderConfig<T extends IMessage> {

        protected Class<? extends BaseMessageViewHolder<? extends T>> holder;
        protected int layout;
        protected Object payload;

        HolderConfig(Class<? extends BaseMessageViewHolder<? extends T>> holder, int layout) {
            this.holder = holder;
            this.layout = layout;
        }

        HolderConfig(Class<? extends BaseMessageViewHolder<? extends T>> holder, int layout, Object payload) {
            this.holder = holder;
            this.layout = layout;
            this.payload = payload;
        }
    }
}

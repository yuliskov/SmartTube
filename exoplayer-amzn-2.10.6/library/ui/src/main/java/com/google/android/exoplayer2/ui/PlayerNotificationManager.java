/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.ui;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.DrawableRes;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.media.app.NotificationCompat.MediaStyle;
import android.support.v4.media.session.MediaSessionCompat;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ControlDispatcher;
import com.google.android.exoplayer2.DefaultControlDispatcher;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.PlaybackPreparer;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.NotificationUtil;
import com.google.android.exoplayer2.util.Util;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/**
 * A notification manager to start, update and cancel a media style notification reflecting the
 * player state.
 *
 * <p>The notification is cancelled when {@code null} is passed to {@link #setPlayer(Player)} or
 * when the notification is dismissed by the user.
 *
 * <p>If the player is released it must be removed from the manager by calling {@code
 * setPlayer(null)} which will cancel the notification.
 *
 * <h3>Action customization</h3>
 *
 * Standard playback actions can be shown or omitted as follows:
 *
 * <ul>
 *   <li><b>{@code useNavigationActions}</b> - Sets whether the navigation previous and next actions
 *       are displayed.
 *       <ul>
 *         <li>Corresponding setter: {@link #setUseNavigationActions(boolean)}
 *         <li>Default: {@code true}
 *       </ul>
 *   <li><b>{@code useNavigationActionsInCompactView}</b> - Sets whether the navigation previous and
 *       next actions should are displayed in compact view (including the lock screen notification).
 *       <ul>
 *         <li>Corresponding setter: {@link #setUseNavigationActionsInCompactView(boolean)}
 *         <li>Default: {@code false}
 *       </ul>
 *   <li><b>{@code usePlayPauseActions}</b> - Sets whether the play and pause actions are displayed.
 *       <ul>
 *         <li>Corresponding setter: {@link #setUsePlayPauseActions(boolean)}
 *         <li>Default: {@code true}
 *       </ul>
 *   <li><b>{@code useStopAction}</b> - Sets whether the stop action is displayed.
 *       <ul>
 *         <li>Corresponding setter: {@link #setUseStopAction(boolean)}
 *         <li>Default: {@code false}
 *       </ul>
 *   <li><b>{@code rewindIncrementMs}</b> - Sets the rewind increment. If set to zero the rewind
 *       action is not displayed.
 *       <ul>
 *         <li>Corresponding setter: {@link #setRewindIncrementMs(long)}
 *         <li>Default: {@link #DEFAULT_REWIND_MS} (5000)
 *       </ul>
 *   <li><b>{@code fastForwardIncrementMs}</b> - Sets the fast forward increment. If set to zero the
 *       fast forward action is not included in the notification.
 *       <ul>
 *         <li>Corresponding setter: {@link #setFastForwardIncrementMs(long)}
 *         <li>Default: {@link #DEFAULT_FAST_FORWARD_MS} (5000)
 *       </ul>
 * </ul>
 */
public class PlayerNotificationManager {

  /** An adapter to provide content assets of the media currently playing. */
  public interface MediaDescriptionAdapter {

    /**
     * Gets the content title for the current media item.
     *
     * <p>See {@link NotificationCompat.Builder#setContentTitle(CharSequence)}.
     *
     * @param player The {@link Player} for which a notification is being built.
     */
    String getCurrentContentTitle(Player player);

    /**
     * Creates a content intent for the current media item.
     *
     * <p>See {@link NotificationCompat.Builder#setContentIntent(PendingIntent)}.
     *
     * @param player The {@link Player} for which a notification is being built.
     */
    @Nullable
    PendingIntent createCurrentContentIntent(Player player);

    /**
     * Gets the content text for the current media item.
     *
     * <p>See {@link NotificationCompat.Builder#setContentText(CharSequence)}.
     *
     * @param player The {@link Player} for which a notification is being built.
     */
    @Nullable
    String getCurrentContentText(Player player);

    /**
     * Gets the content sub text for the current media item.
     *
     * <p>See {@link NotificationCompat.Builder#setSubText(CharSequence)}.
     *
     * @param player The {@link Player} for which a notification is being built.
     */
    @Nullable
    default String getCurrentSubText(Player player) {
      return null;
    }

    /**
     * Gets the large icon for the current media item.
     *
     * <p>When a bitmap initially needs to be asynchronously loaded, a placeholder (or null) can be
     * returned and the bitmap asynchronously passed to the {@link BitmapCallback} once it is
     * loaded. Because the adapter may be called multiple times for the same media item, the bitmap
     * should be cached by the app and whenever possible be returned synchronously at subsequent
     * calls for the same media item.
     *
     * <p>See {@link NotificationCompat.Builder#setLargeIcon(Bitmap)}.
     *
     * @param player The {@link Player} for which a notification is being built.
     * @param callback A {@link BitmapCallback} to provide a {@link Bitmap} asynchronously.
     */
    @Nullable
    Bitmap getCurrentLargeIcon(Player player, BitmapCallback callback);
  }

  /** Defines and handles custom actions. */
  public interface CustomActionReceiver {

    /**
     * Gets the actions handled by this receiver.
     *
     * <p>If multiple {@link PlayerNotificationManager} instances are in use at the same time, the
     * {@code instanceId} must be set as an intent extra with key {@link
     * PlayerNotificationManager#EXTRA_INSTANCE_ID} to avoid sending the action to every custom
     * action receiver. It's also necessary to ensure something is different about the actions. This
     * may be any of the {@link Intent} attributes considered by {@link Intent#filterEquals}, or
     * different request code integers when creating the {@link PendingIntent}s with {@link
     * PendingIntent#getBroadcast}. The easiest approach is to use the {@code instanceId} as the
     * request code.
     *
     * @param context The {@link Context}.
     * @param instanceId The instance id of the {@link PlayerNotificationManager}.
     * @return A map of custom actions.
     */
    Map<String, NotificationCompat.Action> createCustomActions(Context context, int instanceId);

    /**
     * Gets the actions to be included in the notification given the current player state.
     *
     * @param player The {@link Player} for which a notification is being built.
     * @return The actions to be included in the notification.
     */
    List<String> getCustomActions(Player player);

    /**
     * Called when a custom action has been received.
     *
     * @param player The player.
     * @param action The action from {@link Intent#getAction()}.
     * @param intent The received {@link Intent}.
     */
    void onCustomAction(Player player, String action, Intent intent);
  }

  /** A listener for changes to the notification. */
  public interface NotificationListener {

    /**
     * Called after the notification has been started.
     *
     * @param notificationId The id with which the notification has been posted.
     * @param notification The {@link Notification}.
     * @deprecated Use {@link #onNotificationPosted(int, Notification, boolean)} instead.
     */
    @Deprecated
    default void onNotificationStarted(int notificationId, Notification notification) {}

    /**
     * Called after the notification has been cancelled.
     *
     * @param notificationId The id of the notification which has been cancelled.
     * @deprecated Use {@link #onNotificationCancelled(int, boolean)}.
     */
    @Deprecated
    default void onNotificationCancelled(int notificationId) {}

    /**
     * Called after the notification has been cancelled.
     *
     * @param notificationId The id of the notification which has been cancelled.
     * @param dismissedByUser {@code true} if the notification is cancelled because the user
     *     dismissed the notification.
     */
    default void onNotificationCancelled(int notificationId, boolean dismissedByUser) {}

    /**
     * Called each time after the notification has been posted.
     *
     * <p>For a service, the {@code ongoing} flag can be used as an indicator as to whether it
     * should be in the foreground.
     *
     * @param notificationId The id of the notification which has been posted.
     * @param notification The {@link Notification}.
     * @param ongoing Whether the notification is ongoing.
     */
    default void onNotificationPosted(
        int notificationId, Notification notification, boolean ongoing) {}
  }

  /** Receives a {@link Bitmap}. */
  public final class BitmapCallback {
    private final int notificationTag;

    /** Create the receiver. */
    private BitmapCallback(int notificationTag) {
      this.notificationTag = notificationTag;
    }

    /**
     * Called when {@link Bitmap} is available.
     *
     * @param bitmap The bitmap to use as the large icon of the notification.
     */
    public void onBitmap(final Bitmap bitmap) {
      if (bitmap != null) {
        mainHandler.post(
            () -> {
              if (player != null
                  && notificationTag == currentNotificationTag
                  && isNotificationStarted) {
                startOrUpdateNotification(bitmap);
              }
            });
      }
    }
  }

  /** The action which starts playback. */
  public static final String ACTION_PLAY = "com.google.android.exoplayer.play";
  /** The action which pauses playback. */
  public static final String ACTION_PAUSE = "com.google.android.exoplayer.pause";
  /** The action which skips to the previous window. */
  public static final String ACTION_PREVIOUS = "com.google.android.exoplayer.prev";
  /** The action which skips to the next window. */
  public static final String ACTION_NEXT = "com.google.android.exoplayer.next";
  /** The action which fast forwards. */
  public static final String ACTION_FAST_FORWARD = "com.google.android.exoplayer.ffwd";
  /** The action which rewinds. */
  public static final String ACTION_REWIND = "com.google.android.exoplayer.rewind";
  /** The action which stops playback. */
  public static final String ACTION_STOP = "com.google.android.exoplayer.stop";
  /** The extra key of the instance id of the player notification manager. */
  public static final String EXTRA_INSTANCE_ID = "INSTANCE_ID";
  /**
   * The action which is executed when the notification is dismissed. It cancels the notification
   * and calls {@link NotificationListener#onNotificationCancelled(int, boolean)}.
   */
  private static final String ACTION_DISMISS = "com.google.android.exoplayer.dismiss";

  /**
   * Visibility of notification on the lock screen. One of {@link
   * NotificationCompat#VISIBILITY_PRIVATE}, {@link NotificationCompat#VISIBILITY_PUBLIC} or {@link
   * NotificationCompat#VISIBILITY_SECRET}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    NotificationCompat.VISIBILITY_PRIVATE,
    NotificationCompat.VISIBILITY_PUBLIC,
    NotificationCompat.VISIBILITY_SECRET
  })
  public @interface Visibility {}

  /**
   * Priority of the notification (required for API 25 and lower). One of {@link
   * NotificationCompat#PRIORITY_DEFAULT}, {@link NotificationCompat#PRIORITY_MAX}, {@link
   * NotificationCompat#PRIORITY_HIGH}, {@link NotificationCompat#PRIORITY_LOW }or {@link
   * NotificationCompat#PRIORITY_MIN}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    NotificationCompat.PRIORITY_DEFAULT,
    NotificationCompat.PRIORITY_MAX,
    NotificationCompat.PRIORITY_HIGH,
    NotificationCompat.PRIORITY_LOW,
    NotificationCompat.PRIORITY_MIN
  })
  public @interface Priority {}

  /** The default fast forward increment, in milliseconds. */
  public static final int DEFAULT_FAST_FORWARD_MS = 15000;
  /** The default rewind increment, in milliseconds. */
  public static final int DEFAULT_REWIND_MS = 5000;

  private static final long MAX_POSITION_FOR_SEEK_TO_PREVIOUS = 3000;

  private static int instanceIdCounter;

  private final Context context;
  private final String channelId;
  private final int notificationId;
  private final MediaDescriptionAdapter mediaDescriptionAdapter;
  @Nullable private final CustomActionReceiver customActionReceiver;
  private final Handler mainHandler;
  private final NotificationManagerCompat notificationManager;
  private final IntentFilter intentFilter;
  private final Player.EventListener playerListener;
  private final NotificationBroadcastReceiver notificationBroadcastReceiver;
  private final Map<String, NotificationCompat.Action> playbackActions;
  private final Map<String, NotificationCompat.Action> customActions;
  private final PendingIntent dismissPendingIntent;
  private final int instanceId;
  private final Timeline.Window window;

  @Nullable private NotificationCompat.Builder builder;
  @Nullable private ArrayList<NotificationCompat.Action> builderActions;
  @Nullable private Player player;
  @Nullable private PlaybackPreparer playbackPreparer;
  private ControlDispatcher controlDispatcher;
  private boolean isNotificationStarted;
  private int currentNotificationTag;
  @Nullable private NotificationListener notificationListener;
  @Nullable private MediaSessionCompat.Token mediaSessionToken;
  private boolean useNavigationActions;
  private boolean useNavigationActionsInCompactView;
  private boolean usePlayPauseActions;
  private boolean useStopAction;
  private long fastForwardMs;
  private long rewindMs;
  private int badgeIconType;
  private boolean colorized;
  private int defaults;
  private int color;
  @DrawableRes private int smallIconResourceId;
  private int visibility;
  @Priority private int priority;
  private boolean useChronometer;

  /**
   * @deprecated Use {@link #createWithNotificationChannel(Context, String, int, int, int,
   *     MediaDescriptionAdapter)}.
   */
  @Deprecated
  public static PlayerNotificationManager createWithNotificationChannel(
      Context context,
      String channelId,
      @StringRes int channelName,
      int notificationId,
      MediaDescriptionAdapter mediaDescriptionAdapter) {
    return createWithNotificationChannel(
        context,
        channelId,
        channelName,
        /* channelDescription= */ 0,
        notificationId,
        mediaDescriptionAdapter);
  }

  /**
   * Creates a notification manager and a low-priority notification channel with the specified
   * {@code channelId} and {@code channelName}.
   *
   * <p>If the player notification manager is intended to be used within a foreground service,
   * {@link #createWithNotificationChannel(Context, String, int, int, MediaDescriptionAdapter,
   * NotificationListener)} should be used to which a {@link NotificationListener} can be passed.
   * This way you'll receive the notification to put the service into the foreground by calling
   * {@link android.app.Service#startForeground(int, Notification)}.
   *
   * @param context The {@link Context}.
   * @param channelId The id of the notification channel.
   * @param channelName A string resource identifier for the user visible name of the notification
   *     channel. The recommended maximum length is 40 characters. The string may be truncated if
   *     it's too long.
   * @param channelDescription A string resource identifier for the user visible description of the
   *     notification channel, or 0 if no description is provided. The recommended maximum length is
   *     300 characters. The value may be truncated if it is too long.
   * @param notificationId The id of the notification.
   * @param mediaDescriptionAdapter The {@link MediaDescriptionAdapter}.
   */
  public static PlayerNotificationManager createWithNotificationChannel(
      Context context,
      String channelId,
      @StringRes int channelName,
      @StringRes int channelDescription,
      int notificationId,
      MediaDescriptionAdapter mediaDescriptionAdapter) {
    NotificationUtil.createNotificationChannel(
        context, channelId, channelName, channelDescription, NotificationUtil.IMPORTANCE_LOW);
    return new PlayerNotificationManager(
        context, channelId, notificationId, mediaDescriptionAdapter);
  }

  /**
   * @deprecated Use {@link #createWithNotificationChannel(Context, String, int, int, int,
   *     MediaDescriptionAdapter, NotificationListener)}.
   */
  @Deprecated
  public static PlayerNotificationManager createWithNotificationChannel(
      Context context,
      String channelId,
      @StringRes int channelName,
      int notificationId,
      MediaDescriptionAdapter mediaDescriptionAdapter,
      @Nullable NotificationListener notificationListener) {
    return createWithNotificationChannel(
        context,
        channelId,
        channelName,
        /* channelDescription= */ 0,
        notificationId,
        mediaDescriptionAdapter,
        notificationListener);
  }

  /**
   * Creates a notification manager and a low-priority notification channel with the specified
   * {@code channelId} and {@code channelName}. The {@link NotificationListener} passed as the last
   * parameter will be notified when the notification is created and cancelled.
   *
   * @param context The {@link Context}.
   * @param channelId The id of the notification channel.
   * @param channelName A string resource identifier for the user visible name of the channel. The
   *     recommended maximum length is 40 characters. The string may be truncated if it's too long.
   * @param channelDescription A string resource identifier for the user visible description of the
   *     channel, or 0 if no description is provided.
   * @param notificationId The id of the notification.
   * @param mediaDescriptionAdapter The {@link MediaDescriptionAdapter}.
   * @param notificationListener The {@link NotificationListener}.
   */
  public static PlayerNotificationManager createWithNotificationChannel(
      Context context,
      String channelId,
      @StringRes int channelName,
      @StringRes int channelDescription,
      int notificationId,
      MediaDescriptionAdapter mediaDescriptionAdapter,
      @Nullable NotificationListener notificationListener) {
    NotificationUtil.createNotificationChannel(
        context, channelId, channelName, channelDescription, NotificationUtil.IMPORTANCE_LOW);
    return new PlayerNotificationManager(
        context, channelId, notificationId, mediaDescriptionAdapter, notificationListener);
  }

  /**
   * Creates a notification manager using the specified notification {@code channelId}. The caller
   * is responsible for creating the notification channel.
   *
   * <p>When used within a service, consider using {@link #PlayerNotificationManager(Context,
   * String, int, MediaDescriptionAdapter, NotificationListener)} to which a {@link
   * NotificationListener} can be passed.
   *
   * @param context The {@link Context}.
   * @param channelId The id of the notification channel.
   * @param notificationId The id of the notification.
   * @param mediaDescriptionAdapter The {@link MediaDescriptionAdapter}.
   */
  public PlayerNotificationManager(
      Context context,
      String channelId,
      int notificationId,
      MediaDescriptionAdapter mediaDescriptionAdapter) {
    this(
        context,
        channelId,
        notificationId,
        mediaDescriptionAdapter,
        /* notificationListener= */ null,
        /* customActionReceiver */ null);
  }

  /**
   * Creates a notification manager using the specified notification {@code channelId} and {@link
   * NotificationListener}. The caller is responsible for creating the notification channel.
   *
   * @param context The {@link Context}.
   * @param channelId The id of the notification channel.
   * @param notificationId The id of the notification.
   * @param mediaDescriptionAdapter The {@link MediaDescriptionAdapter}.
   * @param notificationListener The {@link NotificationListener}.
   */
  public PlayerNotificationManager(
      Context context,
      String channelId,
      int notificationId,
      MediaDescriptionAdapter mediaDescriptionAdapter,
      @Nullable NotificationListener notificationListener) {
    this(
        context,
        channelId,
        notificationId,
        mediaDescriptionAdapter,
        notificationListener,
        /* customActionReceiver*/ null);
  }

  /**
   * Creates a notification manager using the specified notification {@code channelId} and {@link
   * CustomActionReceiver}. The caller is responsible for creating the notification channel.
   *
   * <p>When used within a service, consider using {@link #PlayerNotificationManager(Context,
   * String, int, MediaDescriptionAdapter, NotificationListener, CustomActionReceiver)} to which a
   * {@link NotificationListener} can be passed.
   *
   * @param context The {@link Context}.
   * @param channelId The id of the notification channel.
   * @param notificationId The id of the notification.
   * @param mediaDescriptionAdapter The {@link MediaDescriptionAdapter}.
   * @param customActionReceiver The {@link CustomActionReceiver}.
   */
  public PlayerNotificationManager(
      Context context,
      String channelId,
      int notificationId,
      MediaDescriptionAdapter mediaDescriptionAdapter,
      @Nullable CustomActionReceiver customActionReceiver) {
    this(
        context,
        channelId,
        notificationId,
        mediaDescriptionAdapter,
        /* notificationListener */ null,
        customActionReceiver);
  }

  /**
   * Creates a notification manager using the specified notification {@code channelId}, {@link
   * NotificationListener} and {@link CustomActionReceiver}. The caller is responsible for creating
   * the notification channel.
   *
   * @param context The {@link Context}.
   * @param channelId The id of the notification channel.
   * @param notificationId The id of the notification.
   * @param mediaDescriptionAdapter The {@link MediaDescriptionAdapter}.
   * @param notificationListener The {@link NotificationListener}.
   * @param customActionReceiver The {@link CustomActionReceiver}.
   */
  public PlayerNotificationManager(
      Context context,
      String channelId,
      int notificationId,
      MediaDescriptionAdapter mediaDescriptionAdapter,
      @Nullable NotificationListener notificationListener,
      @Nullable CustomActionReceiver customActionReceiver) {
    context = context.getApplicationContext();
    this.context = context;
    this.channelId = channelId;
    this.notificationId = notificationId;
    this.mediaDescriptionAdapter = mediaDescriptionAdapter;
    this.notificationListener = notificationListener;
    this.customActionReceiver = customActionReceiver;
    controlDispatcher = new DefaultControlDispatcher();
    window = new Timeline.Window();
    instanceId = instanceIdCounter++;
    mainHandler = new Handler(Looper.getMainLooper());
    notificationManager = NotificationManagerCompat.from(context);
    playerListener = new PlayerListener();
    notificationBroadcastReceiver = new NotificationBroadcastReceiver();
    intentFilter = new IntentFilter();
    useNavigationActions = true;
    usePlayPauseActions = true;
    colorized = true;
    useChronometer = true;
    color = Color.TRANSPARENT;
    smallIconResourceId = R.drawable.exo_notification_small_icon;
    defaults = 0;
    priority = NotificationCompat.PRIORITY_LOW;
    fastForwardMs = DEFAULT_FAST_FORWARD_MS;
    rewindMs = DEFAULT_REWIND_MS;
    badgeIconType = NotificationCompat.BADGE_ICON_SMALL;
    visibility = NotificationCompat.VISIBILITY_PUBLIC;

    // initialize actions
    playbackActions = createPlaybackActions(context, instanceId);
    for (String action : playbackActions.keySet()) {
      intentFilter.addAction(action);
    }
    customActions =
        customActionReceiver != null
            ? customActionReceiver.createCustomActions(context, instanceId)
            : Collections.emptyMap();
    for (String action : customActions.keySet()) {
      intentFilter.addAction(action);
    }
    dismissPendingIntent = createBroadcastIntent(ACTION_DISMISS, context, instanceId);
    intentFilter.addAction(ACTION_DISMISS);
  }

  /**
   * Sets the {@link Player}.
   *
   * <p>Setting the player starts a notification immediately unless the player is in {@link
   * Player#STATE_IDLE}, in which case the notification is started as soon as the player transitions
   * away from being idle.
   *
   * <p>If the player is released it must be removed from the manager by calling {@code
   * setPlayer(null)}. This will cancel the notification.
   *
   * @param player The {@link Player} to use, or {@code null} to remove the current player. Only
   *     players which are accessed on the main thread are supported ({@code
   *     player.getApplicationLooper() == Looper.getMainLooper()}).
   */
  public final void setPlayer(@Nullable Player player) {
    Assertions.checkState(Looper.myLooper() == Looper.getMainLooper());
    Assertions.checkArgument(
        player == null || player.getApplicationLooper() == Looper.getMainLooper());
    if (this.player == player) {
      return;
    }
    if (this.player != null) {
      this.player.removeListener(playerListener);
      if (player == null) {
        stopNotification(/* dismissedByUser= */ false);
      }
    }
    this.player = player;
    if (player != null) {
      player.addListener(playerListener);
      startOrUpdateNotification();
    }
  }

  /**
   * Sets the {@link PlaybackPreparer}.
   *
   * @param playbackPreparer The {@link PlaybackPreparer}.
   */
  public void setPlaybackPreparer(@Nullable PlaybackPreparer playbackPreparer) {
    this.playbackPreparer = playbackPreparer;
  }

  /**
   * Sets the {@link ControlDispatcher}.
   *
   * @param controlDispatcher The {@link ControlDispatcher}, or null to use {@link
   *     DefaultControlDispatcher}.
   */
  public final void setControlDispatcher(ControlDispatcher controlDispatcher) {
    this.controlDispatcher =
        controlDispatcher != null ? controlDispatcher : new DefaultControlDispatcher();
  }

  /**
   * Sets the {@link NotificationListener}.
   *
   * <p>Please note that you should call this method before you call {@link #setPlayer(Player)} or
   * you may not get the {@link NotificationListener#onNotificationStarted(int, Notification)}
   * called on your listener.
   *
   * @param notificationListener The {@link NotificationListener}.
   * @deprecated Pass the notification listener to the constructor instead.
   */
  @Deprecated
  public final void setNotificationListener(NotificationListener notificationListener) {
    this.notificationListener = notificationListener;
  }

  /**
   * Sets the fast forward increment in milliseconds.
   *
   * @param fastForwardMs The fast forward increment in milliseconds. A value of zero will cause the
   *     fast forward action to be disabled.
   */
  public final void setFastForwardIncrementMs(long fastForwardMs) {
    if (this.fastForwardMs == fastForwardMs) {
      return;
    }
    this.fastForwardMs = fastForwardMs;
    invalidate();
  }

  /**
   * Sets the rewind increment in milliseconds.
   *
   * @param rewindMs The rewind increment in milliseconds. A value of zero will cause the rewind
   *     action to be disabled.
   */
  public final void setRewindIncrementMs(long rewindMs) {
    if (this.rewindMs == rewindMs) {
      return;
    }
    this.rewindMs = rewindMs;
    invalidate();
  }

  /**
   * Sets whether the navigation actions should be used.
   *
   * @param useNavigationActions Whether to use navigation actions or not.
   */
  public final void setUseNavigationActions(boolean useNavigationActions) {
    if (this.useNavigationActions != useNavigationActions) {
      this.useNavigationActions = useNavigationActions;
      invalidate();
    }
  }

  /**
   * Sets whether navigation actions should be displayed in compact view.
   *
   * <p>If {@link #useNavigationActions} is set to {@code false} navigation actions are displayed
   * neither in compact nor in full view mode of the notification.
   *
   * @param useNavigationActionsInCompactView Whether the navigation actions should be displayed in
   *     compact view.
   */
  public final void setUseNavigationActionsInCompactView(
      boolean useNavigationActionsInCompactView) {
    if (this.useNavigationActionsInCompactView != useNavigationActionsInCompactView) {
      this.useNavigationActionsInCompactView = useNavigationActionsInCompactView;
      invalidate();
    }
  }

  /**
   * Sets whether the play and pause actions should be used.
   *
   * @param usePlayPauseActions Whether to use play and pause actions.
   */
  public final void setUsePlayPauseActions(boolean usePlayPauseActions) {
    if (this.usePlayPauseActions != usePlayPauseActions) {
      this.usePlayPauseActions = usePlayPauseActions;
      invalidate();
    }
  }

  /**
   * Sets whether the stop action should be used.
   *
   * @param useStopAction Whether to use the stop action.
   */
  public final void setUseStopAction(boolean useStopAction) {
    if (this.useStopAction == useStopAction) {
      return;
    }
    this.useStopAction = useStopAction;
    invalidate();
  }

  /**
   * Sets the {@link MediaSessionCompat.Token}.
   *
   * @param token The {@link MediaSessionCompat.Token}.
   */
  public final void setMediaSessionToken(MediaSessionCompat.Token token) {
    if (!Util.areEqual(this.mediaSessionToken, token)) {
      mediaSessionToken = token;
      invalidate();
    }
  }

  /**
   * Sets the badge icon type of the notification.
   *
   * <p>See {@link NotificationCompat.Builder#setBadgeIconType(int)}.
   *
   * @param badgeIconType The badge icon type.
   */
  public final void setBadgeIconType(@NotificationCompat.BadgeIconType int badgeIconType) {
    if (this.badgeIconType == badgeIconType) {
      return;
    }
    switch (badgeIconType) {
      case NotificationCompat.BADGE_ICON_NONE:
      case NotificationCompat.BADGE_ICON_SMALL:
      case NotificationCompat.BADGE_ICON_LARGE:
        this.badgeIconType = badgeIconType;
        break;
      default:
        throw new IllegalArgumentException();
    }
    invalidate();
  }

  /**
   * Sets whether the notification should be colorized. When set, the color set with {@link
   * #setColor(int)} will be used as the background color for the notification.
   *
   * <p>See {@link NotificationCompat.Builder#setColorized(boolean)}.
   *
   * @param colorized Whether to colorize the notification.
   */
  public final void setColorized(boolean colorized) {
    if (this.colorized != colorized) {
      this.colorized = colorized;
      invalidate();
    }
  }

  /**
   * Sets the defaults.
   *
   * <p>See {@link NotificationCompat.Builder#setDefaults(int)}.
   *
   * @param defaults The default notification options.
   */
  public final void setDefaults(int defaults) {
    if (this.defaults != defaults) {
      this.defaults = defaults;
      invalidate();
    }
  }

  /**
   * Sets the accent color of the notification.
   *
   * <p>See {@link NotificationCompat.Builder#setColor(int)}.
   *
   * @param color The color, in ARGB integer form like the constants in {@link Color}.
   */
  public final void setColor(int color) {
    if (this.color != color) {
      this.color = color;
      invalidate();
    }
  }

  /**
   * Sets the priority of the notification required for API 25 and lower.
   *
   * <p>See {@link NotificationCompat.Builder#setPriority(int)}.
   *
   * @param priority The priority which can be one of {@link NotificationCompat#PRIORITY_DEFAULT},
   *     {@link NotificationCompat#PRIORITY_MAX}, {@link NotificationCompat#PRIORITY_HIGH}, {@link
   *     NotificationCompat#PRIORITY_LOW} or {@link NotificationCompat#PRIORITY_MIN}. If not set
   *     {@link NotificationCompat#PRIORITY_LOW} is used by default.
   */
  public final void setPriority(@Priority int priority) {
    if (this.priority == priority) {
      return;
    }
    switch (priority) {
      case NotificationCompat.PRIORITY_DEFAULT:
      case NotificationCompat.PRIORITY_MAX:
      case NotificationCompat.PRIORITY_HIGH:
      case NotificationCompat.PRIORITY_LOW:
      case NotificationCompat.PRIORITY_MIN:
        this.priority = priority;
        break;
      default:
        throw new IllegalArgumentException();
    }
    invalidate();
  }

  /**
   * Sets the small icon of the notification which is also shown in the system status bar.
   *
   * <p>See {@link NotificationCompat.Builder#setSmallIcon(int)}.
   *
   * @param smallIconResourceId The resource id of the small icon.
   */
  public final void setSmallIcon(@DrawableRes int smallIconResourceId) {
    if (this.smallIconResourceId != smallIconResourceId) {
      this.smallIconResourceId = smallIconResourceId;
      invalidate();
    }
  }

  /**
   * Sets whether the elapsed time of the media playback should be displayed
   *
   * <p>See {@link NotificationCompat.Builder#setUsesChronometer(boolean)}.
   *
   * @param useChronometer Whether to use chronometer.
   */
  public final void setUseChronometer(boolean useChronometer) {
    if (this.useChronometer != useChronometer) {
      this.useChronometer = useChronometer;
      invalidate();
    }
  }

  /**
   * Sets the visibility of the notification which determines whether and how the notification is
   * shown when the device is in lock screen mode.
   *
   * <p>See {@link NotificationCompat.Builder#setVisibility(int)}.
   *
   * @param visibility The visibility which must be one of {@link
   *     NotificationCompat#VISIBILITY_PUBLIC}, {@link NotificationCompat#VISIBILITY_PRIVATE} or
   *     {@link NotificationCompat#VISIBILITY_SECRET}.
   */
  public final void setVisibility(@Visibility int visibility) {
    if (this.visibility == visibility) {
      return;
    }
    switch (visibility) {
      case NotificationCompat.VISIBILITY_PRIVATE:
      case NotificationCompat.VISIBILITY_PUBLIC:
      case NotificationCompat.VISIBILITY_SECRET:
        this.visibility = visibility;
        break;
      default:
        throw new IllegalStateException();
    }
    invalidate();
  }

  /** Forces an update of the notification if already started. */
  public void invalidate() {
    if (isNotificationStarted && player != null) {
      startOrUpdateNotification();
    }
  }

  @Nullable
  private Notification startOrUpdateNotification() {
    Assertions.checkNotNull(this.player);
    return startOrUpdateNotification(/* bitmap= */ null);
  }

  @RequiresNonNull("player")
  @Nullable
  private Notification startOrUpdateNotification(@Nullable Bitmap bitmap) {
    Player player = this.player;
    boolean ongoing = getOngoing(player);
    builder = createNotification(player, builder, ongoing, bitmap);
    if (builder == null) {
      stopNotification(/* dismissedByUser= */ false);
      return null;
    }
    Notification notification = builder.build();
    notificationManager.notify(notificationId, notification);
    if (!isNotificationStarted) {
      isNotificationStarted = true;
      context.registerReceiver(notificationBroadcastReceiver, intentFilter);
      if (notificationListener != null) {
        notificationListener.onNotificationStarted(notificationId, notification);
      }
    }
    NotificationListener listener = notificationListener;
    if (listener != null) {
      listener.onNotificationPosted(notificationId, notification, ongoing);
    }
    return notification;
  }

  private void stopNotification(boolean dismissedByUser) {
    if (isNotificationStarted) {
      isNotificationStarted = false;
      notificationManager.cancel(notificationId);
      context.unregisterReceiver(notificationBroadcastReceiver);
      if (notificationListener != null) {
        notificationListener.onNotificationCancelled(notificationId, dismissedByUser);
        notificationListener.onNotificationCancelled(notificationId);
      }
    }
  }

  /**
   * Creates the notification given the current player state.
   *
   * @param player The player for which state to build a notification.
   * @param builder The builder used to build the last notification, or {@code null}. Re-using the
   *     builder when possible can prevent notification flicker when {@code Util#SDK_INT} &lt; 21.
   * @param ongoing Whether the notification should be ongoing.
   * @param largeIcon The large icon to be used.
   * @return The {@link NotificationCompat.Builder} on which to call {@link
   *     NotificationCompat.Builder#build()} to obtain the notification, or {@code null} if no
   *     notification should be displayed.
   */
  @Nullable
  protected NotificationCompat.Builder createNotification(
      Player player,
      @Nullable NotificationCompat.Builder builder,
      boolean ongoing,
      @Nullable Bitmap largeIcon) {
    if (player.getPlaybackState() == Player.STATE_IDLE
        && (player.getCurrentTimeline().isEmpty() || playbackPreparer == null)) {
      builderActions = null;
      return null;
    }

    List<String> actionNames = getActions(player);
    ArrayList<NotificationCompat.Action> actions = new ArrayList<>(actionNames.size());
    for (int i = 0; i < actionNames.size(); i++) {
      String actionName = actionNames.get(i);
      NotificationCompat.Action action =
          playbackActions.containsKey(actionName)
              ? playbackActions.get(actionName)
              : customActions.get(actionName);
      if (action != null) {
        actions.add(action);
      }
    }

    if (builder == null || !actions.equals(builderActions)) {
      builder = new NotificationCompat.Builder(context, channelId);
      builderActions = actions;
      for (int i = 0; i < actions.size(); i++) {
        builder.addAction(actions.get(i));
      }
    }

    MediaStyle mediaStyle = new MediaStyle();
    if (mediaSessionToken != null) {
      mediaStyle.setMediaSession(mediaSessionToken);
    }
    mediaStyle.setShowActionsInCompactView(getActionIndicesForCompactView(actionNames, player));
    // Configure dismiss action prior to API 21 ('x' button).
    mediaStyle.setShowCancelButton(!ongoing);
    mediaStyle.setCancelButtonIntent(dismissPendingIntent);
    builder.setStyle(mediaStyle);

    // Set intent which is sent if the user selects 'clear all'
    builder.setDeleteIntent(dismissPendingIntent);

    // Set notification properties from getters.
    builder
        .setBadgeIconType(badgeIconType)
        .setOngoing(ongoing)
        .setColor(color)
        .setColorized(colorized)
        .setSmallIcon(smallIconResourceId)
        .setVisibility(visibility)
        .setPriority(priority)
        .setDefaults(defaults);

    // Changing "showWhen" causes notification flicker if SDK_INT < 21.
    if (Util.SDK_INT >= 21
        && useChronometer
        && player.isPlaying()
        && !player.isPlayingAd()
        && !player.isCurrentWindowDynamic()) {
      builder
          .setWhen(System.currentTimeMillis() - player.getContentPosition())
          .setShowWhen(true)
          .setUsesChronometer(true);
    } else {
      builder.setShowWhen(false).setUsesChronometer(false);
    }

    // Set media specific notification properties from MediaDescriptionAdapter.
    builder.setContentTitle(mediaDescriptionAdapter.getCurrentContentTitle(player));
    builder.setContentText(mediaDescriptionAdapter.getCurrentContentText(player));
    builder.setSubText(mediaDescriptionAdapter.getCurrentSubText(player));
    if (largeIcon == null) {
      largeIcon =
          mediaDescriptionAdapter.getCurrentLargeIcon(
              player, new BitmapCallback(++currentNotificationTag));
    }
    setLargeIcon(builder, largeIcon);
    builder.setContentIntent(mediaDescriptionAdapter.createCurrentContentIntent(player));

    return builder;
  }

  /**
   * Gets the names and order of the actions to be included in the notification at the current
   * player state.
   *
   * <p>The playback and custom actions are combined and placed in the following order if not
   * omitted:
   *
   * <pre>
   *   +------------------------------------------------------------------------+
   *   | prev | &lt;&lt; | play/pause | &gt;&gt; | next | custom actions | stop |
   *   +------------------------------------------------------------------------+
   * </pre>
   *
   * <p>This method can be safely overridden. However, the names must be of the playback actions
   * {@link #ACTION_PAUSE}, {@link #ACTION_PLAY}, {@link #ACTION_FAST_FORWARD}, {@link
   * #ACTION_REWIND}, {@link #ACTION_NEXT} or {@link #ACTION_PREVIOUS}, or a key contained in the
   * map returned by {@link CustomActionReceiver#createCustomActions(Context, int)}. Otherwise the
   * action name is ignored.
   */
  protected List<String> getActions(Player player) {
    boolean enablePrevious = false;
    boolean enableRewind = false;
    boolean enableFastForward = false;
    boolean enableNext = false;
    Timeline timeline = player.getCurrentTimeline();
    if (!timeline.isEmpty() && !player.isPlayingAd()) {
      timeline.getWindow(player.getCurrentWindowIndex(), window);
      enablePrevious = window.isSeekable || !window.isDynamic || player.hasPrevious();
      enableRewind = rewindMs > 0;
      enableFastForward = fastForwardMs > 0;
      enableNext = window.isDynamic || player.hasNext();
    }

    List<String> stringActions = new ArrayList<>();
    if (useNavigationActions && enablePrevious) {
      stringActions.add(ACTION_PREVIOUS);
    }
    if (enableRewind) {
      stringActions.add(ACTION_REWIND);
    }
    if (usePlayPauseActions) {
      if (shouldShowPauseButton(player)) {
        stringActions.add(ACTION_PAUSE);
      } else {
        stringActions.add(ACTION_PLAY);
      }
    }
    if (enableFastForward) {
      stringActions.add(ACTION_FAST_FORWARD);
    }
    if (useNavigationActions && enableNext) {
      stringActions.add(ACTION_NEXT);
    }
    if (customActionReceiver != null) {
      stringActions.addAll(customActionReceiver.getCustomActions(player));
    }
    if (useStopAction) {
      stringActions.add(ACTION_STOP);
    }
    return stringActions;
  }

  /**
   * Gets an array with the indices of the buttons to be shown in compact mode.
   *
   * <p>This method can be overridden. The indices must refer to the list of actions passed as the
   * first parameter.
   *
   * @param actionNames The names of the actions included in the notification.
   * @param player The player for which a notification is being built.
   */
  @SuppressWarnings("unused")
  protected int[] getActionIndicesForCompactView(List<String> actionNames, Player player) {
    int pauseActionIndex = actionNames.indexOf(ACTION_PAUSE);
    int playActionIndex = actionNames.indexOf(ACTION_PLAY);
    int skipPreviousActionIndex =
        useNavigationActionsInCompactView ? actionNames.indexOf(ACTION_PREVIOUS) : -1;
    int skipNextActionIndex =
        useNavigationActionsInCompactView ? actionNames.indexOf(ACTION_NEXT) : -1;

    int[] actionIndices = new int[3];
    int actionCounter = 0;
    if (skipPreviousActionIndex != -1) {
      actionIndices[actionCounter++] = skipPreviousActionIndex;
    }
    boolean shouldShowPauseButton = shouldShowPauseButton(player);
    if (pauseActionIndex != -1 && shouldShowPauseButton) {
      actionIndices[actionCounter++] = pauseActionIndex;
    } else if (playActionIndex != -1 && !shouldShowPauseButton) {
      actionIndices[actionCounter++] = playActionIndex;
    }
    if (skipNextActionIndex != -1) {
      actionIndices[actionCounter++] = skipNextActionIndex;
    }
    return Arrays.copyOf(actionIndices, actionCounter);
  }

  /** Returns whether the generated notification should be ongoing. */
  protected boolean getOngoing(Player player) {
    int playbackState = player.getPlaybackState();
    return (playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_READY)
        && player.getPlayWhenReady();
  }

  private void previous(Player player) {
    Timeline timeline = player.getCurrentTimeline();
    if (timeline.isEmpty() || player.isPlayingAd()) {
      return;
    }
    int windowIndex = player.getCurrentWindowIndex();
    timeline.getWindow(windowIndex, window);
    int previousWindowIndex = player.getPreviousWindowIndex();
    if (previousWindowIndex != C.INDEX_UNSET
        && (player.getCurrentPosition() <= MAX_POSITION_FOR_SEEK_TO_PREVIOUS
            || (window.isDynamic && !window.isSeekable))) {
      seekTo(player, previousWindowIndex, C.TIME_UNSET);
    } else {
      seekTo(player, windowIndex, /* positionMs= */ 0);
    }
  }

  private void next(Player player) {
    Timeline timeline = player.getCurrentTimeline();
    if (timeline.isEmpty() || player.isPlayingAd()) {
      return;
    }
    int windowIndex = player.getCurrentWindowIndex();
    int nextWindowIndex = player.getNextWindowIndex();
    if (nextWindowIndex != C.INDEX_UNSET) {
      seekTo(player, nextWindowIndex, C.TIME_UNSET);
    } else if (timeline.getWindow(windowIndex, window).isDynamic) {
      seekTo(player, windowIndex, C.TIME_UNSET);
    }
  }

  private void rewind(Player player) {
    if (player.isCurrentWindowSeekable() && rewindMs > 0) {
      seekToOffset(player, /* offsetMs= */ -rewindMs);
    }
  }

  private void fastForward(Player player) {
    if (player.isCurrentWindowSeekable() && fastForwardMs > 0) {
      seekToOffset(player, /* offsetMs= */ fastForwardMs);
    }
  }

  private void seekToOffset(Player player, long offsetMs) {
    long positionMs = player.getCurrentPosition() + offsetMs;
    long durationMs = player.getDuration();
    if (durationMs != C.TIME_UNSET) {
      positionMs = Math.min(positionMs, durationMs);
    }
    positionMs = Math.max(positionMs, 0);
    seekTo(player, player.getCurrentWindowIndex(), positionMs);
  }

  private void seekTo(Player player, int windowIndex, long positionMs) {
    controlDispatcher.dispatchSeekTo(player, windowIndex, positionMs);
  }

  private boolean shouldShowPauseButton(Player player) {
    return player.getPlaybackState() != Player.STATE_ENDED
        && player.getPlaybackState() != Player.STATE_IDLE
        && player.getPlayWhenReady();
  }

  private static Map<String, NotificationCompat.Action> createPlaybackActions(
      Context context, int instanceId) {
    Map<String, NotificationCompat.Action> actions = new HashMap<>();
    actions.put(
        ACTION_PLAY,
        new NotificationCompat.Action(
            R.drawable.exo_notification_play,
            context.getString(R.string.exo_controls_play_description),
            createBroadcastIntent(ACTION_PLAY, context, instanceId)));
    actions.put(
        ACTION_PAUSE,
        new NotificationCompat.Action(
            R.drawable.exo_notification_pause,
            context.getString(R.string.exo_controls_pause_description),
            createBroadcastIntent(ACTION_PAUSE, context, instanceId)));
    actions.put(
        ACTION_STOP,
        new NotificationCompat.Action(
            R.drawable.exo_notification_stop,
            context.getString(R.string.exo_controls_stop_description),
            createBroadcastIntent(ACTION_STOP, context, instanceId)));
    actions.put(
        ACTION_REWIND,
        new NotificationCompat.Action(
            R.drawable.exo_notification_rewind,
            context.getString(R.string.exo_controls_rewind_description),
            createBroadcastIntent(ACTION_REWIND, context, instanceId)));
    actions.put(
        ACTION_FAST_FORWARD,
        new NotificationCompat.Action(
            R.drawable.exo_notification_fastforward,
            context.getString(R.string.exo_controls_fastforward_description),
            createBroadcastIntent(ACTION_FAST_FORWARD, context, instanceId)));
    actions.put(
        ACTION_PREVIOUS,
        new NotificationCompat.Action(
            R.drawable.exo_notification_previous,
            context.getString(R.string.exo_controls_previous_description),
            createBroadcastIntent(ACTION_PREVIOUS, context, instanceId)));
    actions.put(
        ACTION_NEXT,
        new NotificationCompat.Action(
            R.drawable.exo_notification_next,
            context.getString(R.string.exo_controls_next_description),
            createBroadcastIntent(ACTION_NEXT, context, instanceId)));
    return actions;
  }

  private static PendingIntent createBroadcastIntent(
      String action, Context context, int instanceId) {
    Intent intent = new Intent(action).setPackage(context.getPackageName());
    intent.putExtra(EXTRA_INSTANCE_ID, instanceId);
    // MOD: fix crashes on api >= 23
    int flags = PendingIntent.FLAG_UPDATE_CURRENT;
    if (Build.VERSION.SDK_INT >= 23) {
      // IllegalArgumentException fix: Targeting S+ (version 31 and above) requires that one of FLAG_IMMUTABLE...
      flags |= PendingIntent.FLAG_IMMUTABLE;
    }
    return PendingIntent.getBroadcast(
        context, instanceId, intent, flags);
  }

  @SuppressWarnings("nullness:argument.type.incompatible")
  private static void setLargeIcon(NotificationCompat.Builder builder, @Nullable Bitmap largeIcon) {
    builder.setLargeIcon(largeIcon);
  }

  private class PlayerListener implements Player.EventListener {

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
      startOrUpdateNotification();
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
      startOrUpdateNotification();
    }

    @Override
    public void onTimelineChanged(Timeline timeline, @Nullable Object manifest, int reason) {
      startOrUpdateNotification();
    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
      startOrUpdateNotification();
    }

    @Override
    public void onPositionDiscontinuity(int reason) {
      startOrUpdateNotification();
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {
      startOrUpdateNotification();
    }
  }

  private class NotificationBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
      Player player = PlayerNotificationManager.this.player;
      if (player == null
          || !isNotificationStarted
          || intent.getIntExtra(EXTRA_INSTANCE_ID, instanceId) != instanceId) {
        return;
      }
      String action = intent.getAction();
      if (ACTION_PLAY.equals(action)) {
        if (player.getPlaybackState() == Player.STATE_IDLE) {
          if (playbackPreparer != null) {
            playbackPreparer.preparePlayback();
          }
        } else if (player.getPlaybackState() == Player.STATE_ENDED) {
          seekTo(player, player.getCurrentWindowIndex(), C.TIME_UNSET);
        }
        controlDispatcher.dispatchSetPlayWhenReady(player, /* playWhenReady= */ true);
      } else if (ACTION_PAUSE.equals(action)) {
        controlDispatcher.dispatchSetPlayWhenReady(player, /* playWhenReady= */ false);
      } else if (ACTION_PREVIOUS.equals(action)) {
        previous(player);
      } else if (ACTION_REWIND.equals(action)) {
        rewind(player);
      } else if (ACTION_FAST_FORWARD.equals(action)) {
        fastForward(player);
      } else if (ACTION_NEXT.equals(action)) {
        next(player);
      } else if (ACTION_STOP.equals(action)) {
        controlDispatcher.dispatchStop(player, /* reset= */ true);
      } else if (ACTION_DISMISS.equals(action)) {
        stopNotification(/* dismissedByUser= */ true);
      } else if (action != null
          && customActionReceiver != null
          && customActions.containsKey(action)) {
        customActionReceiver.onCustomAction(player, action, intent);
      }
    }
  }
}

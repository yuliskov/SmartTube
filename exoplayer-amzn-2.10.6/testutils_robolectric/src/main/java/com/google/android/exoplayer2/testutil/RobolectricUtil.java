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
package com.google.android.exoplayer2.testutil;

import static org.robolectric.Shadows.shadowOf;
import static org.robolectric.util.ReflectionHelpers.callInstanceMethod;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.MessageQueue;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.Util;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowLegacyLooper;
import org.robolectric.shadows.ShadowLegacyMessageQueue;
import org.robolectric.shadows.ShadowLooper;

/** Collection of shadow classes used to run tests with Robolectric which require Loopers. */
public final class RobolectricUtil {

  private static final AtomicLong sequenceNumberGenerator = new AtomicLong(0);
  private static final int ANY_MESSAGE = Integer.MIN_VALUE;

  private RobolectricUtil() {}

  /**
   * A custom implementation of Robolectric's ShadowLooper which runs all scheduled messages in the
   * loop method of the looper. Also ensures to correctly emulate the message order of the real
   * message loop and to avoid blocking caused by Robolectric's default implementation.
   *
   * <p>Only works in conjunction with {@link CustomMessageQueue}. Note that the test's {@code
   * SystemClock} is not advanced automatically.
   */
  @Implements(Looper.class)
  public static final class CustomLooper extends ShadowLegacyLooper {

    private final PriorityBlockingQueue<PendingMessage> pendingMessages;
    private final CopyOnWriteArraySet<RemovedMessage> removedMessages;

    public CustomLooper() {
      pendingMessages = new PriorityBlockingQueue<>();
      removedMessages = new CopyOnWriteArraySet<>();
    }

    @Implementation
    public static void loop() {
      Looper looper = Looper.myLooper();
      if (shadowOf(looper) instanceof CustomLooper) {
        ((CustomLooper) shadowOf(looper)).doLoop();
      }
    }

    @Implementation
    @Override
    public void quitUnchecked() {
      super.quitUnchecked();
      // Insert message at the front of the queue to quit loop as soon as possible.
      addPendingMessage(/* message= */ null, /* when= */ Long.MIN_VALUE);
    }

    private void addPendingMessage(@Nullable Message message, long when) {
      pendingMessages.put(new PendingMessage(message, when));
    }

    private void removeMessages(Handler handler, int what, Object object) {
      RemovedMessage newRemovedMessage = new RemovedMessage(handler, what, object);
      removedMessages.add(newRemovedMessage);
      for (RemovedMessage removedMessage : removedMessages) {
        if (removedMessage != newRemovedMessage
            && removedMessage.handler == handler
            && removedMessage.what == what
            && removedMessage.object == object) {
          removedMessages.remove(removedMessage);
        }
      }
    }

    private void doLoop() {
      boolean wasInterrupted = false;
      while (true) {
        try {
          PendingMessage pendingMessage = pendingMessages.take();
          if (pendingMessage.message == null) {
            // Null message is signal to end message loop.
            return;
          }
          // Call through to real {@code Message.markInUse()} and {@code Message.recycle()} to
          // ensure message recycling works. This is also done in Robolectric's own implementation
          // of the message queue.
          callInstanceMethod(pendingMessage.message, "markInUse");
          Handler target = pendingMessage.message.getTarget();
          if (target != null) {
            boolean isRemoved = false;
            for (RemovedMessage removedMessage : removedMessages) {
              if (removedMessage.handler == target
                  && (removedMessage.what == ANY_MESSAGE
                      || removedMessage.what == pendingMessage.message.what)
                  && (removedMessage.object == null
                      || removedMessage.object == pendingMessage.message.obj)
                  && pendingMessage.sequenceNumber < removedMessage.sequenceNumber) {
                isRemoved = true;
              }
            }
            if (!isRemoved) {
              try {
                if (wasInterrupted) {
                  wasInterrupted = false;
                  // Restore the interrupt status flag, so long-running messages will exit early.
                  Thread.currentThread().interrupt();
                }
                target.dispatchMessage(pendingMessage.message);
              } catch (Throwable t) {
                // Interrupt the main thread to terminate the test. Robolectric's HandlerThread will
                // print the rethrown error to standard output.
                Looper.getMainLooper().getThread().interrupt();
                throw t;
              }
            }
          }
          if (Util.SDK_INT >= 21) {
            callInstanceMethod(pendingMessage.message, "recycleUnchecked");
          } else {
            callInstanceMethod(pendingMessage.message, "recycle");
          }
        } catch (InterruptedException e) {
          wasInterrupted = true;
        }
      }
    }
  }

  /**
   * Custom implementation of Robolectric's ShadowMessageQueue which is needed to let {@link
   * CustomLooper} work as intended.
   */
  @Implements(MessageQueue.class)
  public static final class CustomMessageQueue extends ShadowLegacyMessageQueue {

    private final Thread looperThread;

    public CustomMessageQueue() {
      looperThread = Thread.currentThread();
    }

    @Implementation
    @Override
    public boolean enqueueMessage(Message msg, long when) {
      Looper looper = ShadowLooper.getLooperForThread(looperThread);
      if (shadowOf(looper) instanceof CustomLooper
          && shadowOf(looper) != shadowOf(Looper.getMainLooper())) {
        ((CustomLooper) shadowOf(looper)).addPendingMessage(msg, when);
      } else {
        super.enqueueMessage(msg, when);
      }
      return true;
    }

    @Implementation
    public void removeMessages(Handler handler, int what, Object object) {
      Looper looper = ShadowLooper.getLooperForThread(looperThread);
      if (shadowOf(looper) instanceof CustomLooper
          && shadowOf(looper) != shadowOf(Looper.getMainLooper())) {
        ((CustomLooper) shadowOf(looper)).removeMessages(handler, what, object);
      }
    }

    @Implementation
    public void removeCallbacksAndMessages(Handler handler, Object object) {
      Looper looper = ShadowLooper.getLooperForThread(looperThread);
      if (shadowOf(looper) instanceof CustomLooper
          && shadowOf(looper) != shadowOf(Looper.getMainLooper())) {
        ((CustomLooper) shadowOf(looper)).removeMessages(handler, ANY_MESSAGE, object);
      }
    }
  }

  private static final class PendingMessage implements Comparable<PendingMessage> {

    public final @Nullable Message message;
    public final long when;
    public final long sequenceNumber;

    public PendingMessage(@Nullable Message message, long when) {
      this.message = message;
      this.when = when;
      sequenceNumber = sequenceNumberGenerator.getAndIncrement();
    }

    @Override
    public int compareTo(@NonNull PendingMessage other) {
      int res = Util.compareLong(this.when, other.when);
      if (res == 0 && this != other) {
        res = Util.compareLong(this.sequenceNumber, other.sequenceNumber);
      }
      return res;
    }
  }

  private static final class RemovedMessage {

    public final Handler handler;
    public final int what;
    public final Object object;
    public final long sequenceNumber;

    public RemovedMessage(Handler handler, int what, Object object) {
      this.handler = handler;
      this.what = what;
      this.object = object;
      this.sequenceNumber = sequenceNumberGenerator.get();
    }
  }
}

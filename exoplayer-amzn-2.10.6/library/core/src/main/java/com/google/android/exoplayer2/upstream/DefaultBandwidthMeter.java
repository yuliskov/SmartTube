/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.upstream;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import android.util.SparseArray;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.EventDispatcher;
import com.google.android.exoplayer2.util.SlidingPercentile;
import com.google.android.exoplayer2.util.Util;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Estimates bandwidth by listening to data transfers.
 *
 * <p>The bandwidth estimate is calculated using a {@link SlidingPercentile} and is updated each
 * time a transfer ends. The initial estimate is based on the current operator's network country
 * code or the locale of the user, as well as the network connection type. This can be configured in
 * the {@link Builder}.
 */
public final class DefaultBandwidthMeter implements BandwidthMeter, TransferListener {

  /**
   * Country groups used to determine the default initial bitrate estimate. The group assignment for
   * each country is an array of group indices for [Wifi, 2G, 3G, 4G].
   */
  public static final Map<String, int[]> DEFAULT_INITIAL_BITRATE_COUNTRY_GROUPS =
      createInitialBitrateCountryGroupAssignment();

  /** Default initial Wifi bitrate estimate in bits per second. */
  public static final long[] DEFAULT_INITIAL_BITRATE_ESTIMATES_WIFI =
      new long[] {5_400_000, 3_400_000, 1_900_000, 1_100_000, 400_000};

  /** Default initial 2G bitrate estimates in bits per second. */
  public static final long[] DEFAULT_INITIAL_BITRATE_ESTIMATES_2G =
      new long[] {170_000, 139_000, 122_000, 107_000, 90_000};

  /** Default initial 3G bitrate estimates in bits per second. */
  public static final long[] DEFAULT_INITIAL_BITRATE_ESTIMATES_3G =
      new long[] {2_100_000, 1_300_000, 960_000, 770_000, 450_000};

  /** Default initial 4G bitrate estimates in bits per second. */
  public static final long[] DEFAULT_INITIAL_BITRATE_ESTIMATES_4G =
      new long[] {6_000_000, 3_400_000, 2_100_000, 1_400_000, 570_000};

  /**
   * Default initial bitrate estimate used when the device is offline or the network type cannot be
   * determined, in bits per second.
   */
  public static final long DEFAULT_INITIAL_BITRATE_ESTIMATE = 1_000_000;

  /** Default maximum weight for the sliding window. */
  public static final int DEFAULT_SLIDING_WINDOW_MAX_WEIGHT = 2000;

  /** Builder for a bandwidth meter. */
  public static final class Builder {

    @Nullable private final Context context;

    private SparseArray<Long> initialBitrateEstimates;
    private int slidingWindowMaxWeight;
    private Clock clock;
    private boolean resetOnNetworkTypeChange;

    /**
     * Creates a builder with default parameters and without listener.
     *
     * @param context A context.
     */
    public Builder(Context context) {
      // Handling of null is for backward compatibility only.
      this.context = context == null ? null : context.getApplicationContext();
      initialBitrateEstimates = getInitialBitrateEstimatesForCountry(Util.getCountryCode(context));
      slidingWindowMaxWeight = DEFAULT_SLIDING_WINDOW_MAX_WEIGHT;
      clock = Clock.DEFAULT;
      resetOnNetworkTypeChange = true;
    }

    /**
     * Sets the maximum weight for the sliding window.
     *
     * @param slidingWindowMaxWeight The maximum weight for the sliding window.
     * @return This builder.
     */
    public Builder setSlidingWindowMaxWeight(int slidingWindowMaxWeight) {
      this.slidingWindowMaxWeight = slidingWindowMaxWeight;
      return this;
    }

    /**
     * Sets the initial bitrate estimate in bits per second that should be assumed when a bandwidth
     * estimate is unavailable.
     *
     * @param initialBitrateEstimate The initial bitrate estimate in bits per second.
     * @return This builder.
     */
    public Builder setInitialBitrateEstimate(long initialBitrateEstimate) {
      for (int i = 0; i < initialBitrateEstimates.size(); i++) {
        initialBitrateEstimates.setValueAt(i, initialBitrateEstimate);
      }
      return this;
    }

    /**
     * Sets the initial bitrate estimate in bits per second that should be assumed when a bandwidth
     * estimate is unavailable and the current network connection is of the specified type.
     *
     * @param networkType The {@link C.NetworkType} this initial estimate is for.
     * @param initialBitrateEstimate The initial bitrate estimate in bits per second.
     * @return This builder.
     */
    public Builder setInitialBitrateEstimate(
        @C.NetworkType int networkType, long initialBitrateEstimate) {
      initialBitrateEstimates.put(networkType, initialBitrateEstimate);
      return this;
    }

    /**
     * Sets the initial bitrate estimates to the default values of the specified country. The
     * initial estimates are used when a bandwidth estimate is unavailable.
     *
     * @param countryCode The ISO 3166-1 alpha-2 country code of the country whose default bitrate
     *     estimates should be used.
     * @return This builder.
     */
    public Builder setInitialBitrateEstimate(String countryCode) {
      initialBitrateEstimates =
          getInitialBitrateEstimatesForCountry(Util.toUpperInvariant(countryCode));
      return this;
    }

    /**
     * Sets the clock used to estimate bandwidth from data transfers. Should only be set for testing
     * purposes.
     *
     * @param clock The clock used to estimate bandwidth from data transfers.
     * @return This builder.
     */
    public Builder setClock(Clock clock) {
      this.clock = clock;
      return this;
    }

    /**
     * Sets whether to reset if the network type changes. The default value is {@code true}.
     *
     * @param resetOnNetworkTypeChange Whether to reset if the network type changes.
     * @return This builder.
     */
    public Builder setResetOnNetworkTypeChange(boolean resetOnNetworkTypeChange) {
      this.resetOnNetworkTypeChange = resetOnNetworkTypeChange;
      return this;
    }

    /**
     * Builds the bandwidth meter.
     *
     * @return A bandwidth meter with the configured properties.
     */
    public DefaultBandwidthMeter build() {
      return new DefaultBandwidthMeter(
          context,
          initialBitrateEstimates,
          slidingWindowMaxWeight,
          clock,
          resetOnNetworkTypeChange);
    }

    private static SparseArray<Long> getInitialBitrateEstimatesForCountry(String countryCode) {
      int[] groupIndices = getCountryGroupIndices(countryCode);
      SparseArray<Long> result = new SparseArray<>(/* initialCapacity= */ 6);
      result.append(C.NETWORK_TYPE_UNKNOWN, DEFAULT_INITIAL_BITRATE_ESTIMATE);
      result.append(C.NETWORK_TYPE_WIFI, DEFAULT_INITIAL_BITRATE_ESTIMATES_WIFI[groupIndices[0]]);
      result.append(C.NETWORK_TYPE_2G, DEFAULT_INITIAL_BITRATE_ESTIMATES_2G[groupIndices[1]]);
      result.append(C.NETWORK_TYPE_3G, DEFAULT_INITIAL_BITRATE_ESTIMATES_3G[groupIndices[2]]);
      result.append(C.NETWORK_TYPE_4G, DEFAULT_INITIAL_BITRATE_ESTIMATES_4G[groupIndices[3]]);
      // Assume default Wifi bitrate for Ethernet to prevent using the slower fallback bitrate.
      result.append(
          C.NETWORK_TYPE_ETHERNET, DEFAULT_INITIAL_BITRATE_ESTIMATES_WIFI[groupIndices[0]]);
      return result;
    }

    private static int[] getCountryGroupIndices(String countryCode) {
      int[] groupIndices = DEFAULT_INITIAL_BITRATE_COUNTRY_GROUPS.get(countryCode);
      // Assume median group if not found.
      return groupIndices == null ? new int[] {2, 2, 2, 2} : groupIndices;
    }
  }

  private static final int ELAPSED_MILLIS_FOR_ESTIMATE = 2000;
  private static final int BYTES_TRANSFERRED_FOR_ESTIMATE = 512 * 1024;

  @Nullable private final Context context;
  private final SparseArray<Long> initialBitrateEstimates;
  private final EventDispatcher<EventListener> eventDispatcher;
  private final SlidingPercentile slidingPercentile;
  private final Clock clock;

  private int streamCount;
  private long sampleStartTimeMs;
  private long sampleBytesTransferred;

  @C.NetworkType private int networkType;
  private long totalElapsedTimeMs;
  private long totalBytesTransferred;
  private long bitrateEstimate;
  private long lastReportedBitrateEstimate;

  private boolean networkTypeOverrideSet;
  @C.NetworkType private int networkTypeOverride;

  /** @deprecated Use {@link Builder} instead. */
  @Deprecated
  public DefaultBandwidthMeter() {
    this(
        /* context= */ null,
        /* initialBitrateEstimates= */ new SparseArray<>(),
        DEFAULT_SLIDING_WINDOW_MAX_WEIGHT,
        Clock.DEFAULT,
        /* resetOnNetworkTypeChange= */ false);
  }

  private DefaultBandwidthMeter(
      @Nullable Context context,
      SparseArray<Long> initialBitrateEstimates,
      int maxWeight,
      Clock clock,
      boolean resetOnNetworkTypeChange) {
    this.context = context == null ? null : context.getApplicationContext();
    this.initialBitrateEstimates = initialBitrateEstimates;
    this.eventDispatcher = new EventDispatcher<>();
    this.slidingPercentile = new SlidingPercentile(maxWeight);
    this.clock = clock;
    // Set the initial network type and bitrate estimate
    networkType = context == null ? C.NETWORK_TYPE_UNKNOWN : Util.getNetworkType(context);
    bitrateEstimate = getInitialBitrateEstimateForNetworkType(networkType);
    // Register to receive connectivity actions if possible.
    if (context != null && resetOnNetworkTypeChange) {
      ConnectivityActionReceiver connectivityActionReceiver =
          ConnectivityActionReceiver.getInstance(context);
      connectivityActionReceiver.register(/* bandwidthMeter= */ this);
    }
  }

  /**
   * Overrides the network type. Handled in the same way as if the meter had detected a change from
   * the current network type to the specified network type internally.
   *
   * <p>Applications should not normally call this method. It is intended for testing purposes.
   *
   * @param networkType The overriding network type.
   */
  public synchronized void setNetworkTypeOverride(@C.NetworkType int networkType) {
    networkTypeOverride = networkType;
    networkTypeOverrideSet = true;
    onConnectivityAction();
  }

  @Override
  public synchronized long getBitrateEstimate() {
    return bitrateEstimate;
  }

  @Override
  @Nullable
  public TransferListener getTransferListener() {
    return this;
  }

  @Override
  public void addEventListener(Handler eventHandler, EventListener eventListener) {
    eventDispatcher.addListener(eventHandler, eventListener);
  }

  @Override
  public void removeEventListener(EventListener eventListener) {
    eventDispatcher.removeListener(eventListener);
  }

  @Override
  public void onTransferInitializing(DataSource source, DataSpec dataSpec, boolean isNetwork) {
    // Do nothing.
  }

  @Override
  public synchronized void onTransferStart(
      DataSource source, DataSpec dataSpec, boolean isNetwork) {
    if (!isNetwork) {
      return;
    }
    if (streamCount == 0) {
      sampleStartTimeMs = clock.elapsedRealtime();
    }
    streamCount++;
  }

  @Override
  public synchronized void onBytesTransferred(
      DataSource source, DataSpec dataSpec, boolean isNetwork, int bytes) {
    if (!isNetwork) {
      return;
    }
    sampleBytesTransferred += bytes;
  }

  @Override
  public synchronized void onTransferEnd(DataSource source, DataSpec dataSpec, boolean isNetwork) {
    if (!isNetwork) {
      return;
    }
    Assertions.checkState(streamCount > 0);
    long nowMs = clock.elapsedRealtime();
    int sampleElapsedTimeMs = (int) (nowMs - sampleStartTimeMs);
    totalElapsedTimeMs += sampleElapsedTimeMs;
    totalBytesTransferred += sampleBytesTransferred;
    if (sampleElapsedTimeMs > 0) {
      float bitsPerSecond = (sampleBytesTransferred * 8000) / sampleElapsedTimeMs;
      slidingPercentile.addSample((int) Math.sqrt(sampleBytesTransferred), bitsPerSecond);
      if (totalElapsedTimeMs >= ELAPSED_MILLIS_FOR_ESTIMATE
          || totalBytesTransferred >= BYTES_TRANSFERRED_FOR_ESTIMATE) {
        bitrateEstimate = (long) slidingPercentile.getPercentile(0.5f);
      }
      maybeNotifyBandwidthSample(sampleElapsedTimeMs, sampleBytesTransferred, bitrateEstimate);
      sampleStartTimeMs = nowMs;
      sampleBytesTransferred = 0;
    } // Else any sample bytes transferred will be carried forward into the next sample.
    streamCount--;
  }

  private synchronized void onConnectivityAction() {
    int networkType =
        networkTypeOverrideSet
            ? networkTypeOverride
            : (context == null ? C.NETWORK_TYPE_UNKNOWN : Util.getNetworkType(context));
    if (this.networkType == networkType) {
      return;
    }

    this.networkType = networkType;
    if (networkType == C.NETWORK_TYPE_OFFLINE
        || networkType == C.NETWORK_TYPE_UNKNOWN
        || networkType == C.NETWORK_TYPE_OTHER) {
      // It's better not to reset the bandwidth meter for these network types.
      return;
    }

    // Reset the bitrate estimate and report it, along with any bytes transferred.
    this.bitrateEstimate = getInitialBitrateEstimateForNetworkType(networkType);
    long nowMs = clock.elapsedRealtime();
    int sampleElapsedTimeMs = streamCount > 0 ? (int) (nowMs - sampleStartTimeMs) : 0;
    maybeNotifyBandwidthSample(sampleElapsedTimeMs, sampleBytesTransferred, bitrateEstimate);

    // Reset the remainder of the state.
    sampleStartTimeMs = nowMs;
    sampleBytesTransferred = 0;
    totalBytesTransferred = 0;
    totalElapsedTimeMs = 0;
    slidingPercentile.reset();
  }

  private void maybeNotifyBandwidthSample(
      int elapsedMs, long bytesTransferred, long bitrateEstimate) {
    if (elapsedMs == 0 && bytesTransferred == 0 && bitrateEstimate == lastReportedBitrateEstimate) {
      return;
    }
    lastReportedBitrateEstimate = bitrateEstimate;
    eventDispatcher.dispatch(
        listener -> listener.onBandwidthSample(elapsedMs, bytesTransferred, bitrateEstimate));
  }

  private long getInitialBitrateEstimateForNetworkType(@C.NetworkType int networkType) {
    Long initialBitrateEstimate = initialBitrateEstimates.get(networkType);
    if (initialBitrateEstimate == null) {
      initialBitrateEstimate = initialBitrateEstimates.get(C.NETWORK_TYPE_UNKNOWN);
    }
    if (initialBitrateEstimate == null) {
      initialBitrateEstimate = DEFAULT_INITIAL_BITRATE_ESTIMATE;
    }
    return initialBitrateEstimate;
  }

  /*
   * Note: This class only holds a weak reference to DefaultBandwidthMeter instances. It should not
   * be made non-static, since doing so adds a strong reference (i.e. DefaultBandwidthMeter.this).
   */
  private static class ConnectivityActionReceiver extends BroadcastReceiver {

    @MonotonicNonNull private static ConnectivityActionReceiver staticInstance;

    private final Handler mainHandler;
    private final ArrayList<WeakReference<DefaultBandwidthMeter>> bandwidthMeters;

    public static synchronized ConnectivityActionReceiver getInstance(Context context) {
      if (staticInstance == null) {
        staticInstance = new ConnectivityActionReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        context.registerReceiver(staticInstance, filter);
      }
      return staticInstance;
    }

    private ConnectivityActionReceiver() {
      mainHandler = new Handler(Looper.getMainLooper());
      bandwidthMeters = new ArrayList<>();
    }

    public synchronized void register(DefaultBandwidthMeter bandwidthMeter) {
      removeClearedReferences();
      bandwidthMeters.add(new WeakReference<>(bandwidthMeter));
      // Simulate an initial update on the main thread (like the sticky broadcast we'd receive if
      // we were to register a separate broadcast receiver for each bandwidth meter).
      mainHandler.post(() -> updateBandwidthMeter(bandwidthMeter));
    }

    @Override
    public synchronized void onReceive(Context context, Intent intent) {
      if (isInitialStickyBroadcast()) {
        return;
      }
      removeClearedReferences();
      for (int i = 0; i < bandwidthMeters.size(); i++) {
        WeakReference<DefaultBandwidthMeter> bandwidthMeterReference = bandwidthMeters.get(i);
        DefaultBandwidthMeter bandwidthMeter = bandwidthMeterReference.get();
        if (bandwidthMeter != null) {
          updateBandwidthMeter(bandwidthMeter);
        }
      }
    }

    private void updateBandwidthMeter(DefaultBandwidthMeter bandwidthMeter) {
      bandwidthMeter.onConnectivityAction();
    }

    private void removeClearedReferences() {
      for (int i = bandwidthMeters.size() - 1; i >= 0; i--) {
        WeakReference<DefaultBandwidthMeter> bandwidthMeterReference = bandwidthMeters.get(i);
        DefaultBandwidthMeter bandwidthMeter = bandwidthMeterReference.get();
        if (bandwidthMeter == null) {
          bandwidthMeters.remove(i);
        }
      }
    }
  }

  private static Map<String, int[]> createInitialBitrateCountryGroupAssignment() {
    HashMap<String, int[]> countryGroupAssignment = new HashMap<>();
    countryGroupAssignment.put("AD", new int[] {1, 0, 0, 1});
    countryGroupAssignment.put("AE", new int[] {1, 4, 4, 4});
    countryGroupAssignment.put("AF", new int[] {4, 4, 3, 3});
    countryGroupAssignment.put("AG", new int[] {3, 2, 1, 1});
    countryGroupAssignment.put("AI", new int[] {1, 0, 1, 3});
    countryGroupAssignment.put("AL", new int[] {1, 2, 1, 1});
    countryGroupAssignment.put("AM", new int[] {2, 2, 3, 2});
    countryGroupAssignment.put("AO", new int[] {3, 4, 2, 0});
    countryGroupAssignment.put("AQ", new int[] {4, 2, 2, 2});
    countryGroupAssignment.put("AR", new int[] {2, 3, 2, 2});
    countryGroupAssignment.put("AS", new int[] {3, 3, 4, 1});
    countryGroupAssignment.put("AT", new int[] {0, 2, 0, 0});
    countryGroupAssignment.put("AU", new int[] {0, 1, 1, 1});
    countryGroupAssignment.put("AW", new int[] {1, 1, 0, 2});
    countryGroupAssignment.put("AX", new int[] {0, 2, 1, 0});
    countryGroupAssignment.put("AZ", new int[] {3, 3, 2, 2});
    countryGroupAssignment.put("BA", new int[] {1, 1, 1, 2});
    countryGroupAssignment.put("BB", new int[] {0, 1, 0, 0});
    countryGroupAssignment.put("BD", new int[] {2, 2, 3, 2});
    countryGroupAssignment.put("BE", new int[] {0, 0, 0, 1});
    countryGroupAssignment.put("BF", new int[] {4, 4, 3, 1});
    countryGroupAssignment.put("BG", new int[] {0, 1, 0, 0});
    countryGroupAssignment.put("BH", new int[] {2, 1, 3, 4});
    countryGroupAssignment.put("BI", new int[] {4, 3, 4, 4});
    countryGroupAssignment.put("BJ", new int[] {4, 3, 4, 4});
    countryGroupAssignment.put("BL", new int[] {1, 0, 2, 3});
    countryGroupAssignment.put("BM", new int[] {1, 0, 0, 0});
    countryGroupAssignment.put("BN", new int[] {4, 2, 3, 3});
    countryGroupAssignment.put("BO", new int[] {2, 2, 3, 2});
    countryGroupAssignment.put("BQ", new int[] {1, 0, 3, 4});
    countryGroupAssignment.put("BR", new int[] {2, 3, 3, 2});
    countryGroupAssignment.put("BS", new int[] {2, 0, 1, 4});
    countryGroupAssignment.put("BT", new int[] {3, 0, 2, 1});
    countryGroupAssignment.put("BW", new int[] {4, 4, 1, 2});
    countryGroupAssignment.put("BY", new int[] {0, 1, 1, 2});
    countryGroupAssignment.put("BZ", new int[] {2, 2, 3, 1});
    countryGroupAssignment.put("CA", new int[] {0, 3, 3, 3});
    countryGroupAssignment.put("CD", new int[] {4, 4, 3, 2});
    countryGroupAssignment.put("CF", new int[] {4, 3, 3, 4});
    countryGroupAssignment.put("CG", new int[] {4, 4, 4, 4});
    countryGroupAssignment.put("CH", new int[] {0, 0, 1, 1});
    countryGroupAssignment.put("CI", new int[] {3, 4, 3, 3});
    countryGroupAssignment.put("CK", new int[] {2, 4, 1, 0});
    countryGroupAssignment.put("CL", new int[] {2, 2, 2, 3});
    countryGroupAssignment.put("CM", new int[] {3, 4, 2, 1});
    countryGroupAssignment.put("CN", new int[] {2, 2, 2, 3});
    countryGroupAssignment.put("CO", new int[] {2, 3, 2, 2});
    countryGroupAssignment.put("CR", new int[] {2, 2, 4, 4});
    countryGroupAssignment.put("CU", new int[] {4, 4, 3, 1});
    countryGroupAssignment.put("CV", new int[] {2, 3, 2, 4});
    countryGroupAssignment.put("CW", new int[] {1, 0, 0, 0});
    countryGroupAssignment.put("CX", new int[] {2, 2, 2, 2});
    countryGroupAssignment.put("CY", new int[] {1, 1, 1, 1});
    countryGroupAssignment.put("CZ", new int[] {0, 1, 0, 0});
    countryGroupAssignment.put("DE", new int[] {0, 2, 2, 2});
    countryGroupAssignment.put("DJ", new int[] {3, 3, 4, 0});
    countryGroupAssignment.put("DK", new int[] {0, 0, 0, 0});
    countryGroupAssignment.put("DM", new int[] {1, 0, 0, 3});
    countryGroupAssignment.put("DO", new int[] {3, 3, 4, 4});
    countryGroupAssignment.put("DZ", new int[] {3, 3, 4, 4});
    countryGroupAssignment.put("EC", new int[] {2, 4, 4, 2});
    countryGroupAssignment.put("EE", new int[] {0, 0, 0, 0});
    countryGroupAssignment.put("EG", new int[] {3, 4, 2, 2});
    countryGroupAssignment.put("EH", new int[] {2, 0, 3, 3});
    countryGroupAssignment.put("ER", new int[] {4, 2, 2, 2});
    countryGroupAssignment.put("ES", new int[] {0, 1, 1, 1});
    countryGroupAssignment.put("ET", new int[] {4, 4, 4, 0});
    countryGroupAssignment.put("FI", new int[] {0, 0, 1, 0});
    countryGroupAssignment.put("FJ", new int[] {3, 1, 3, 3});
    countryGroupAssignment.put("FK", new int[] {4, 2, 2, 3});
    countryGroupAssignment.put("FM", new int[] {4, 2, 4, 0});
    countryGroupAssignment.put("FO", new int[] {0, 0, 0, 0});
    countryGroupAssignment.put("FR", new int[] {1, 0, 3, 1});
    countryGroupAssignment.put("GA", new int[] {3, 3, 2, 1});
    countryGroupAssignment.put("GB", new int[] {0, 1, 3, 3});
    countryGroupAssignment.put("GD", new int[] {2, 0, 4, 4});
    countryGroupAssignment.put("GE", new int[] {1, 1, 0, 3});
    countryGroupAssignment.put("GF", new int[] {1, 2, 4, 4});
    countryGroupAssignment.put("GG", new int[] {0, 0, 0, 0});
    countryGroupAssignment.put("GH", new int[] {3, 3, 3, 2});
    countryGroupAssignment.put("GI", new int[] {0, 0, 0, 1});
    countryGroupAssignment.put("GL", new int[] {2, 2, 3, 4});
    countryGroupAssignment.put("GM", new int[] {4, 3, 3, 2});
    countryGroupAssignment.put("GN", new int[] {4, 4, 4, 0});
    countryGroupAssignment.put("GP", new int[] {2, 2, 1, 3});
    countryGroupAssignment.put("GQ", new int[] {4, 3, 3, 0});
    countryGroupAssignment.put("GR", new int[] {1, 1, 0, 1});
    countryGroupAssignment.put("GT", new int[] {3, 3, 3, 4});
    countryGroupAssignment.put("GU", new int[] {1, 2, 4, 4});
    countryGroupAssignment.put("GW", new int[] {4, 4, 4, 0});
    countryGroupAssignment.put("GY", new int[] {3, 4, 1, 0});
    countryGroupAssignment.put("HK", new int[] {0, 1, 4, 4});
    countryGroupAssignment.put("HN", new int[] {3, 3, 2, 2});
    countryGroupAssignment.put("HR", new int[] {1, 0, 0, 2});
    countryGroupAssignment.put("HT", new int[] {3, 4, 4, 3});
    countryGroupAssignment.put("HU", new int[] {0, 0, 1, 0});
    countryGroupAssignment.put("ID", new int[] {3, 2, 3, 4});
    countryGroupAssignment.put("IE", new int[] {0, 0, 3, 2});
    countryGroupAssignment.put("IL", new int[] {0, 1, 2, 3});
    countryGroupAssignment.put("IM", new int[] {0, 0, 0, 1});
    countryGroupAssignment.put("IN", new int[] {2, 2, 4, 4});
    countryGroupAssignment.put("IO", new int[] {4, 4, 2, 2});
    countryGroupAssignment.put("IQ", new int[] {3, 3, 4, 4});
    countryGroupAssignment.put("IR", new int[] {1, 0, 1, 0});
    countryGroupAssignment.put("IS", new int[] {0, 0, 0, 0});
    countryGroupAssignment.put("IT", new int[] {1, 0, 1, 1});
    countryGroupAssignment.put("JE", new int[] {1, 0, 0, 1});
    countryGroupAssignment.put("JM", new int[] {3, 2, 2, 1});
    countryGroupAssignment.put("JO", new int[] {1, 1, 1, 2});
    countryGroupAssignment.put("JP", new int[] {0, 2, 2, 2});
    countryGroupAssignment.put("KE", new int[] {3, 3, 3, 3});
    countryGroupAssignment.put("KG", new int[] {1, 1, 2, 3});
    countryGroupAssignment.put("KH", new int[] {2, 0, 4, 4});
    countryGroupAssignment.put("KI", new int[] {4, 4, 4, 4});
    countryGroupAssignment.put("KM", new int[] {4, 4, 3, 3});
    countryGroupAssignment.put("KN", new int[] {1, 0, 1, 4});
    countryGroupAssignment.put("KP", new int[] {1, 2, 0, 2});
    countryGroupAssignment.put("KR", new int[] {0, 3, 0, 2});
    countryGroupAssignment.put("KW", new int[] {2, 2, 1, 2});
    countryGroupAssignment.put("KY", new int[] {1, 1, 0, 2});
    countryGroupAssignment.put("KZ", new int[] {1, 2, 2, 2});
    countryGroupAssignment.put("LA", new int[] {2, 1, 1, 0});
    countryGroupAssignment.put("LB", new int[] {3, 2, 0, 0});
    countryGroupAssignment.put("LC", new int[] {2, 1, 0, 0});
    countryGroupAssignment.put("LI", new int[] {0, 0, 2, 2});
    countryGroupAssignment.put("LK", new int[] {1, 1, 2, 2});
    countryGroupAssignment.put("LR", new int[] {3, 4, 4, 1});
    countryGroupAssignment.put("LS", new int[] {3, 3, 2, 0});
    countryGroupAssignment.put("LT", new int[] {0, 0, 0, 0});
    countryGroupAssignment.put("LU", new int[] {0, 1, 0, 0});
    countryGroupAssignment.put("LV", new int[] {0, 0, 0, 0});
    countryGroupAssignment.put("LY", new int[] {4, 3, 4, 4});
    countryGroupAssignment.put("MA", new int[] {2, 1, 2, 2});
    countryGroupAssignment.put("MC", new int[] {1, 0, 1, 0});
    countryGroupAssignment.put("MD", new int[] {1, 1, 0, 0});
    countryGroupAssignment.put("ME", new int[] {1, 2, 2, 3});
    countryGroupAssignment.put("MF", new int[] {1, 4, 2, 1});
    countryGroupAssignment.put("MG", new int[] {3, 4, 1, 3});
    countryGroupAssignment.put("MH", new int[] {4, 0, 2, 3});
    countryGroupAssignment.put("MK", new int[] {1, 0, 0, 0});
    countryGroupAssignment.put("ML", new int[] {4, 4, 4, 3});
    countryGroupAssignment.put("MM", new int[] {2, 3, 1, 2});
    countryGroupAssignment.put("MN", new int[] {2, 3, 2, 4});
    countryGroupAssignment.put("MO", new int[] {0, 0, 4, 4});
    countryGroupAssignment.put("MP", new int[] {0, 2, 4, 4});
    countryGroupAssignment.put("MQ", new int[] {1, 1, 1, 3});
    countryGroupAssignment.put("MR", new int[] {4, 4, 4, 4});
    countryGroupAssignment.put("MS", new int[] {1, 4, 0, 3});
    countryGroupAssignment.put("MT", new int[] {0, 1, 0, 0});
    countryGroupAssignment.put("MU", new int[] {2, 2, 3, 4});
    countryGroupAssignment.put("MV", new int[] {3, 2, 1, 1});
    countryGroupAssignment.put("MW", new int[] {4, 2, 1, 1});
    countryGroupAssignment.put("MX", new int[] {2, 4, 3, 2});
    countryGroupAssignment.put("MY", new int[] {2, 2, 2, 3});
    countryGroupAssignment.put("MZ", new int[] {3, 4, 2, 2});
    countryGroupAssignment.put("NA", new int[] {3, 2, 2, 1});
    countryGroupAssignment.put("NC", new int[] {2, 1, 3, 2});
    countryGroupAssignment.put("NE", new int[] {4, 4, 4, 3});
    countryGroupAssignment.put("NF", new int[] {1, 2, 2, 2});
    countryGroupAssignment.put("NG", new int[] {3, 4, 3, 2});
    countryGroupAssignment.put("NI", new int[] {3, 3, 3, 4});
    countryGroupAssignment.put("NL", new int[] {0, 2, 4, 3});
    countryGroupAssignment.put("NO", new int[] {0, 1, 0, 0});
    countryGroupAssignment.put("NP", new int[] {3, 3, 2, 2});
    countryGroupAssignment.put("NR", new int[] {4, 0, 4, 0});
    countryGroupAssignment.put("NU", new int[] {2, 2, 2, 1});
    countryGroupAssignment.put("NZ", new int[] {0, 0, 0, 1});
    countryGroupAssignment.put("OM", new int[] {2, 2, 1, 3});
    countryGroupAssignment.put("PA", new int[] {1, 3, 3, 4});
    countryGroupAssignment.put("PE", new int[] {2, 3, 4, 4});
    countryGroupAssignment.put("PF", new int[] {3, 1, 0, 1});
    countryGroupAssignment.put("PG", new int[] {4, 3, 1, 1});
    countryGroupAssignment.put("PH", new int[] {3, 0, 4, 4});
    countryGroupAssignment.put("PK", new int[] {3, 3, 3, 3});
    countryGroupAssignment.put("PL", new int[] {1, 1, 1, 3});
    countryGroupAssignment.put("PM", new int[] {0, 2, 0, 0});
    countryGroupAssignment.put("PR", new int[] {2, 1, 3, 3});
    countryGroupAssignment.put("PS", new int[] {3, 3, 1, 4});
    countryGroupAssignment.put("PT", new int[] {1, 1, 0, 1});
    countryGroupAssignment.put("PW", new int[] {2, 2, 1, 1});
    countryGroupAssignment.put("PY", new int[] {3, 1, 3, 3});
    countryGroupAssignment.put("QA", new int[] {2, 3, 0, 1});
    countryGroupAssignment.put("RE", new int[] {1, 0, 2, 2});
    countryGroupAssignment.put("RO", new int[] {0, 1, 1, 2});
    countryGroupAssignment.put("RS", new int[] {1, 2, 0, 0});
    countryGroupAssignment.put("RU", new int[] {0, 1, 1, 1});
    countryGroupAssignment.put("RW", new int[] {3, 4, 2, 4});
    countryGroupAssignment.put("SA", new int[] {2, 2, 1, 2});
    countryGroupAssignment.put("SB", new int[] {4, 4, 3, 0});
    countryGroupAssignment.put("SC", new int[] {4, 2, 0, 1});
    countryGroupAssignment.put("SD", new int[] {4, 4, 4, 2});
    countryGroupAssignment.put("SE", new int[] {0, 1, 0, 0});
    countryGroupAssignment.put("SG", new int[] {1, 2, 3, 3});
    countryGroupAssignment.put("SH", new int[] {4, 4, 2, 3});
    countryGroupAssignment.put("SI", new int[] {0, 1, 0, 1});
    countryGroupAssignment.put("SJ", new int[] {0, 0, 2, 0});
    countryGroupAssignment.put("SK", new int[] {0, 1, 0, 1});
    countryGroupAssignment.put("SL", new int[] {4, 3, 2, 4});
    countryGroupAssignment.put("SM", new int[] {0, 0, 1, 3});
    countryGroupAssignment.put("SN", new int[] {4, 4, 4, 3});
    countryGroupAssignment.put("SO", new int[] {4, 4, 4, 4});
    countryGroupAssignment.put("SR", new int[] {3, 2, 2, 4});
    countryGroupAssignment.put("SS", new int[] {4, 2, 4, 2});
    countryGroupAssignment.put("ST", new int[] {3, 4, 2, 2});
    countryGroupAssignment.put("SV", new int[] {2, 3, 3, 4});
    countryGroupAssignment.put("SX", new int[] {2, 4, 1, 0});
    countryGroupAssignment.put("SY", new int[] {4, 4, 1, 0});
    countryGroupAssignment.put("SZ", new int[] {3, 4, 2, 3});
    countryGroupAssignment.put("TC", new int[] {1, 1, 3, 1});
    countryGroupAssignment.put("TD", new int[] {4, 4, 4, 3});
    countryGroupAssignment.put("TG", new int[] {3, 3, 1, 0});
    countryGroupAssignment.put("TH", new int[] {1, 3, 4, 4});
    countryGroupAssignment.put("TJ", new int[] {4, 4, 4, 4});
    countryGroupAssignment.put("TL", new int[] {4, 2, 4, 4});
    countryGroupAssignment.put("TM", new int[] {4, 1, 2, 3});
    countryGroupAssignment.put("TN", new int[] {2, 1, 1, 1});
    countryGroupAssignment.put("TO", new int[] {3, 3, 3, 1});
    countryGroupAssignment.put("TR", new int[] {1, 2, 0, 1});
    countryGroupAssignment.put("TT", new int[] {2, 3, 1, 2});
    countryGroupAssignment.put("TV", new int[] {4, 2, 2, 4});
    countryGroupAssignment.put("TW", new int[] {0, 0, 0, 1});
    countryGroupAssignment.put("TZ", new int[] {3, 3, 4, 3});
    countryGroupAssignment.put("UA", new int[] {0, 2, 1, 2});
    countryGroupAssignment.put("UG", new int[] {4, 3, 2, 3});
    countryGroupAssignment.put("US", new int[] {0, 1, 3, 3});
    countryGroupAssignment.put("UY", new int[] {2, 2, 2, 2});
    countryGroupAssignment.put("UZ", new int[] {3, 2, 2, 2});
    countryGroupAssignment.put("VA", new int[] {1, 2, 2, 2});
    countryGroupAssignment.put("VC", new int[] {2, 1, 0, 0});
    countryGroupAssignment.put("VE", new int[] {4, 4, 4, 3});
    countryGroupAssignment.put("VG", new int[] {2, 1, 1, 2});
    countryGroupAssignment.put("VI", new int[] {1, 0, 2, 4});
    countryGroupAssignment.put("VN", new int[] {0, 2, 4, 4});
    countryGroupAssignment.put("VU", new int[] {4, 1, 3, 1});
    countryGroupAssignment.put("WS", new int[] {3, 2, 3, 1});
    countryGroupAssignment.put("XK", new int[] {1, 2, 1, 0});
    countryGroupAssignment.put("YE", new int[] {4, 4, 4, 2});
    countryGroupAssignment.put("YT", new int[] {2, 0, 2, 3});
    countryGroupAssignment.put("ZA", new int[] {2, 3, 2, 2});
    countryGroupAssignment.put("ZM", new int[] {3, 3, 2, 1});
    countryGroupAssignment.put("ZW", new int[] {3, 3, 3, 1});
    return Collections.unmodifiableMap(countryGroupAssignment);
  }
}

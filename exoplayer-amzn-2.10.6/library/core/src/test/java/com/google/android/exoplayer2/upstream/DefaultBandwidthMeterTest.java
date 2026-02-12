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
package com.google.android.exoplayer2.upstream;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.Uri;
import android.telephony.TelephonyManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.testutil.FakeClock;
import com.google.android.exoplayer2.testutil.FakeDataSource;
import java.util.Random;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowNetworkInfo;

/** Unit test for {@link DefaultBandwidthMeter}. */
@RunWith(AndroidJUnit4.class)
public final class DefaultBandwidthMeterTest {

  private static final int SIMULATED_TRANSFER_COUNT = 100;
  private static final String FAST_COUNTRY_ISO = "EE";
  private static final String SLOW_COUNTRY_ISO = "PG";

  private TelephonyManager telephonyManager;
  private ConnectivityManager connectivityManager;
  private NetworkInfo networkInfoOffline;
  private NetworkInfo networkInfoWifi;
  private NetworkInfo networkInfo2g;
  private NetworkInfo networkInfo3g;
  private NetworkInfo networkInfo4g;
  private NetworkInfo networkInfoEthernet;

  @Before
  public void setUp() {
    connectivityManager =
        (ConnectivityManager)
            ApplicationProvider.getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
    telephonyManager =
        (TelephonyManager)
            ApplicationProvider.getApplicationContext().getSystemService(Context.TELEPHONY_SERVICE);
    Shadows.shadowOf(telephonyManager).setNetworkCountryIso(FAST_COUNTRY_ISO);
    networkInfoOffline =
        ShadowNetworkInfo.newInstance(
            DetailedState.DISCONNECTED,
            ConnectivityManager.TYPE_WIFI,
            /* subType= */ 0,
            /* isAvailable= */ false,
            /* isConnected= */ false);
    networkInfoWifi =
        ShadowNetworkInfo.newInstance(
            DetailedState.CONNECTED,
            ConnectivityManager.TYPE_WIFI,
            /* subType= */ 0,
            /* isAvailable= */ true,
            /* isConnected= */ true);
    networkInfo2g =
        ShadowNetworkInfo.newInstance(
            DetailedState.CONNECTED,
            ConnectivityManager.TYPE_MOBILE,
            TelephonyManager.NETWORK_TYPE_GPRS,
            /* isAvailable= */ true,
            /* isConnected= */ true);
    networkInfo3g =
        ShadowNetworkInfo.newInstance(
            DetailedState.CONNECTED,
            ConnectivityManager.TYPE_MOBILE,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            /* isAvailable= */ true,
            /* isConnected= */ true);
    networkInfo4g =
        ShadowNetworkInfo.newInstance(
            DetailedState.CONNECTED,
            ConnectivityManager.TYPE_MOBILE,
            TelephonyManager.NETWORK_TYPE_LTE,
            /* isAvailable= */ true,
            /* isConnected= */ true);
    networkInfoEthernet =
        ShadowNetworkInfo.newInstance(
            DetailedState.CONNECTED,
            ConnectivityManager.TYPE_ETHERNET,
            /* subType= */ 0,
            /* isAvailable= */ true,
            /* isConnected= */ true);
  }
  
  @Test
  public void defaultInitialBitrateEstimate_forWifi_isGreaterThanEstimateFor2G() {
    setActiveNetworkInfo(networkInfoWifi);
    DefaultBandwidthMeter bandwidthMeterWifi =
        new DefaultBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimateWifi = bandwidthMeterWifi.getBitrateEstimate();

    setActiveNetworkInfo(networkInfo2g);
    DefaultBandwidthMeter bandwidthMeter2g =
        new DefaultBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimate2g = bandwidthMeter2g.getBitrateEstimate();

    assertThat(initialEstimateWifi).isGreaterThan(initialEstimate2g);
  }

  @Test
  public void defaultInitialBitrateEstimate_forWifi_isGreaterThanEstimateFor3G() {
    setActiveNetworkInfo(networkInfoWifi);
    DefaultBandwidthMeter bandwidthMeterWifi =
        new DefaultBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimateWifi = bandwidthMeterWifi.getBitrateEstimate();

    setActiveNetworkInfo(networkInfo3g);
    DefaultBandwidthMeter bandwidthMeter3g =
        new DefaultBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimate3g = bandwidthMeter3g.getBitrateEstimate();

    assertThat(initialEstimateWifi).isGreaterThan(initialEstimate3g);
  }

  @Test
  public void defaultInitialBitrateEstimate_forEthernet_isGreaterThanEstimateFor2G() {
    setActiveNetworkInfo(networkInfoEthernet);
    DefaultBandwidthMeter bandwidthMeterEthernet =
        new DefaultBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimateEthernet = bandwidthMeterEthernet.getBitrateEstimate();

    setActiveNetworkInfo(networkInfo2g);
    DefaultBandwidthMeter bandwidthMeter2g =
        new DefaultBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimate2g = bandwidthMeter2g.getBitrateEstimate();

    assertThat(initialEstimateEthernet).isGreaterThan(initialEstimate2g);
  }

  @Test
  public void defaultInitialBitrateEstimate_forEthernet_isGreaterThanEstimateFor3G() {
    setActiveNetworkInfo(networkInfoEthernet);
    DefaultBandwidthMeter bandwidthMeterEthernet =
        new DefaultBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimateEthernet = bandwidthMeterEthernet.getBitrateEstimate();

    setActiveNetworkInfo(networkInfo3g);
    DefaultBandwidthMeter bandwidthMeter3g =
        new DefaultBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimate3g = bandwidthMeter3g.getBitrateEstimate();

    assertThat(initialEstimateEthernet).isGreaterThan(initialEstimate3g);
  }

  @Test
  public void defaultInitialBitrateEstimate_for4G_isGreaterThanEstimateFor2G() {
    setActiveNetworkInfo(networkInfo4g);
    DefaultBandwidthMeter bandwidthMeter4g =
        new DefaultBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimate4g = bandwidthMeter4g.getBitrateEstimate();

    setActiveNetworkInfo(networkInfo2g);
    DefaultBandwidthMeter bandwidthMeter2g =
        new DefaultBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimate2g = bandwidthMeter2g.getBitrateEstimate();

    assertThat(initialEstimate4g).isGreaterThan(initialEstimate2g);
  }

  @Test
  public void defaultInitialBitrateEstimate_for4G_isGreaterThanEstimateFor3G() {
    setActiveNetworkInfo(networkInfo4g);
    DefaultBandwidthMeter bandwidthMeter4g =
        new DefaultBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimate4g = bandwidthMeter4g.getBitrateEstimate();

    setActiveNetworkInfo(networkInfo3g);
    DefaultBandwidthMeter bandwidthMeter3g =
        new DefaultBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimate3g = bandwidthMeter3g.getBitrateEstimate();

    assertThat(initialEstimate4g).isGreaterThan(initialEstimate3g);
  }

  @Test
  public void defaultInitialBitrateEstimate_for3G_isGreaterThanEstimateFor2G() {
    setActiveNetworkInfo(networkInfo3g);
    DefaultBandwidthMeter bandwidthMeter3g =
        new DefaultBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimate3g = bandwidthMeter3g.getBitrateEstimate();

    setActiveNetworkInfo(networkInfo2g);
    DefaultBandwidthMeter bandwidthMeter2g =
        new DefaultBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimate2g = bandwidthMeter2g.getBitrateEstimate();

    assertThat(initialEstimate3g).isGreaterThan(initialEstimate2g);
  }

  @Test
  public void defaultInitialBitrateEstimate_forOffline_isReasonable() {
    setActiveNetworkInfo(networkInfoOffline);
    DefaultBandwidthMeter bandwidthMeter =
        new DefaultBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimate = bandwidthMeter.getBitrateEstimate();

    assertThat(initialEstimate).isGreaterThan(100_000L);
    assertThat(initialEstimate).isLessThan(50_000_000L);
  }

  @Test
  public void
      defaultInitialBitrateEstimate_forWifi_forFastCountry_isGreaterThanEstimateForSlowCountry() {
    setActiveNetworkInfo(networkInfoWifi);
    setNetworkCountryIso(FAST_COUNTRY_ISO);
    DefaultBandwidthMeter bandwidthMeterFast =
        new DefaultBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimateFast = bandwidthMeterFast.getBitrateEstimate();

    setNetworkCountryIso(SLOW_COUNTRY_ISO);
    DefaultBandwidthMeter bandwidthMeterSlow =
        new DefaultBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimateSlow = bandwidthMeterSlow.getBitrateEstimate();

    assertThat(initialEstimateFast).isGreaterThan(initialEstimateSlow);
  }

  @Test
  public void
      defaultInitialBitrateEstimate_forEthernet_forFastCountry_isGreaterThanEstimateForSlowCountry() {
    setActiveNetworkInfo(networkInfoEthernet);
    setNetworkCountryIso(FAST_COUNTRY_ISO);
    DefaultBandwidthMeter bandwidthMeterFast =
        new DefaultBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimateFast = bandwidthMeterFast.getBitrateEstimate();

    setNetworkCountryIso(SLOW_COUNTRY_ISO);
    DefaultBandwidthMeter bandwidthMeterSlow =
        new DefaultBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimateSlow = bandwidthMeterSlow.getBitrateEstimate();

    assertThat(initialEstimateFast).isGreaterThan(initialEstimateSlow);
  }

  @Test
  public void
      defaultInitialBitrateEstimate_for2G_forFastCountry_isGreaterThanEstimateForSlowCountry() {
    setActiveNetworkInfo(networkInfo2g);
    setNetworkCountryIso(FAST_COUNTRY_ISO);
    DefaultBandwidthMeter bandwidthMeterFast =
        new DefaultBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimateFast = bandwidthMeterFast.getBitrateEstimate();

    setNetworkCountryIso(SLOW_COUNTRY_ISO);
    DefaultBandwidthMeter bandwidthMeterSlow =
        new DefaultBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimateSlow = bandwidthMeterSlow.getBitrateEstimate();

    assertThat(initialEstimateFast).isGreaterThan(initialEstimateSlow);
  }

  @Test
  public void
      defaultInitialBitrateEstimate_for3G_forFastCountry_isGreaterThanEstimateForSlowCountry() {
    setActiveNetworkInfo(networkInfo3g);
    setNetworkCountryIso(FAST_COUNTRY_ISO);
    DefaultBandwidthMeter bandwidthMeterFast =
        new DefaultBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimateFast = bandwidthMeterFast.getBitrateEstimate();

    setNetworkCountryIso(SLOW_COUNTRY_ISO);
    DefaultBandwidthMeter bandwidthMeterSlow =
        new DefaultBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimateSlow = bandwidthMeterSlow.getBitrateEstimate();

    assertThat(initialEstimateFast).isGreaterThan(initialEstimateSlow);
  }

  @Test
  public void
      defaultInitialBitrateEstimate_for4g_forFastCountry_isGreaterThanEstimateForSlowCountry() {
    setActiveNetworkInfo(networkInfo4g);
    setNetworkCountryIso(FAST_COUNTRY_ISO);
    DefaultBandwidthMeter bandwidthMeterFast =
        new DefaultBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimateFast = bandwidthMeterFast.getBitrateEstimate();

    setNetworkCountryIso(SLOW_COUNTRY_ISO);
    DefaultBandwidthMeter bandwidthMeterSlow =
        new DefaultBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimateSlow = bandwidthMeterSlow.getBitrateEstimate();

    assertThat(initialEstimateFast).isGreaterThan(initialEstimateSlow);
  }

  @Test
  public void initialBitrateEstimateOverwrite_whileConnectedToNetwork_setsInitialEstimate() {
    setActiveNetworkInfo(networkInfoWifi);
    DefaultBandwidthMeter bandwidthMeter =
        new DefaultBandwidthMeter.Builder(ApplicationProvider.getApplicationContext())
            .setInitialBitrateEstimate(123456789)
            .build();
    long initialEstimate = bandwidthMeter.getBitrateEstimate();

    assertThat(initialEstimate).isEqualTo(123456789);
  }

  @Test
  public void initialBitrateEstimateOverwrite_whileOffline_setsInitialEstimate() {
    setActiveNetworkInfo(networkInfoOffline);
    DefaultBandwidthMeter bandwidthMeter =
        new DefaultBandwidthMeter.Builder(ApplicationProvider.getApplicationContext())
            .setInitialBitrateEstimate(123456789)
            .build();
    long initialEstimate = bandwidthMeter.getBitrateEstimate();

    assertThat(initialEstimate).isEqualTo(123456789);
  }

  @Test
  public void initialBitrateEstimateOverwrite_forWifi_whileConnectedToWifi_setsInitialEstimate() {
    setActiveNetworkInfo(networkInfoWifi);
    DefaultBandwidthMeter bandwidthMeter =
        new DefaultBandwidthMeter.Builder(ApplicationProvider.getApplicationContext())
            .setInitialBitrateEstimate(C.NETWORK_TYPE_WIFI, 123456789)
            .build();
    long initialEstimate = bandwidthMeter.getBitrateEstimate();

    assertThat(initialEstimate).isEqualTo(123456789);
  }

  @Test
  public void
      initialBitrateEstimateOverwrite_forWifi_whileConnectedToOtherNetwork_doesNotSetInitialEstimate() {
    setActiveNetworkInfo(networkInfo2g);
    DefaultBandwidthMeter bandwidthMeter =
        new DefaultBandwidthMeter.Builder(ApplicationProvider.getApplicationContext())
            .setInitialBitrateEstimate(C.NETWORK_TYPE_WIFI, 123456789)
            .build();
    long initialEstimate = bandwidthMeter.getBitrateEstimate();

    assertThat(initialEstimate).isNotEqualTo(123456789);
  }

  @Test
  public void
      initialBitrateEstimateOverwrite_forEthernet_whileConnectedToEthernet_setsInitialEstimate() {
    setActiveNetworkInfo(networkInfoEthernet);
    DefaultBandwidthMeter bandwidthMeter =
        new DefaultBandwidthMeter.Builder(ApplicationProvider.getApplicationContext())
            .setInitialBitrateEstimate(C.NETWORK_TYPE_ETHERNET, 123456789)
            .build();
    long initialEstimate = bandwidthMeter.getBitrateEstimate();

    assertThat(initialEstimate).isEqualTo(123456789);
  }

  @Test
  public void
      initialBitrateEstimateOverwrite_forEthernet_whileConnectedToOtherNetwork_doesNotSetInitialEstimate() {
    setActiveNetworkInfo(networkInfo2g);
    DefaultBandwidthMeter bandwidthMeter =
        new DefaultBandwidthMeter.Builder(ApplicationProvider.getApplicationContext())
            .setInitialBitrateEstimate(C.NETWORK_TYPE_WIFI, 123456789)
            .build();
    long initialEstimate = bandwidthMeter.getBitrateEstimate();

    assertThat(initialEstimate).isNotEqualTo(123456789);
  }

  @Test
  public void initialBitrateEstimateOverwrite_for2G_whileConnectedTo2G_setsInitialEstimate() {
    setActiveNetworkInfo(networkInfo2g);
    DefaultBandwidthMeter bandwidthMeter =
        new DefaultBandwidthMeter.Builder(ApplicationProvider.getApplicationContext())
            .setInitialBitrateEstimate(C.NETWORK_TYPE_2G, 123456789)
            .build();
    long initialEstimate = bandwidthMeter.getBitrateEstimate();

    assertThat(initialEstimate).isEqualTo(123456789);
  }

  @Test
  public void
      initialBitrateEstimateOverwrite_for2G_whileConnectedToOtherNetwork_doesNotSetInitialEstimate() {
    setActiveNetworkInfo(networkInfoWifi);
    DefaultBandwidthMeter bandwidthMeter =
        new DefaultBandwidthMeter.Builder(ApplicationProvider.getApplicationContext())
            .setInitialBitrateEstimate(C.NETWORK_TYPE_2G, 123456789)
            .build();
    long initialEstimate = bandwidthMeter.getBitrateEstimate();

    assertThat(initialEstimate).isNotEqualTo(123456789);
  }

  @Test
  public void initialBitrateEstimateOverwrite_for3G_whileConnectedTo3G_setsInitialEstimate() {
    setActiveNetworkInfo(networkInfo3g);
    DefaultBandwidthMeter bandwidthMeter =
        new DefaultBandwidthMeter.Builder(ApplicationProvider.getApplicationContext())
            .setInitialBitrateEstimate(C.NETWORK_TYPE_3G, 123456789)
            .build();
    long initialEstimate = bandwidthMeter.getBitrateEstimate();

    assertThat(initialEstimate).isEqualTo(123456789);
  }

  @Test
  public void
      initialBitrateEstimateOverwrite_for3G_whileConnectedToOtherNetwork_doesNotSetInitialEstimate() {
    setActiveNetworkInfo(networkInfoWifi);
    DefaultBandwidthMeter bandwidthMeter =
        new DefaultBandwidthMeter.Builder(ApplicationProvider.getApplicationContext())
            .setInitialBitrateEstimate(C.NETWORK_TYPE_3G, 123456789)
            .build();
    long initialEstimate = bandwidthMeter.getBitrateEstimate();

    assertThat(initialEstimate).isNotEqualTo(123456789);
  }

  @Test
  public void initialBitrateEstimateOverwrite_for4G_whileConnectedTo4G_setsInitialEstimate() {
    setActiveNetworkInfo(networkInfo4g);
    DefaultBandwidthMeter bandwidthMeter =
        new DefaultBandwidthMeter.Builder(ApplicationProvider.getApplicationContext())
            .setInitialBitrateEstimate(C.NETWORK_TYPE_4G, 123456789)
            .build();
    long initialEstimate = bandwidthMeter.getBitrateEstimate();

    assertThat(initialEstimate).isEqualTo(123456789);
  }

  @Test
  public void
      initialBitrateEstimateOverwrite_for4G_whileConnectedToOtherNetwork_doesNotSetInitialEstimate() {
    setActiveNetworkInfo(networkInfoWifi);
    DefaultBandwidthMeter bandwidthMeter =
        new DefaultBandwidthMeter.Builder(ApplicationProvider.getApplicationContext())
            .setInitialBitrateEstimate(C.NETWORK_TYPE_4G, 123456789)
            .build();
    long initialEstimate = bandwidthMeter.getBitrateEstimate();

    assertThat(initialEstimate).isNotEqualTo(123456789);
  }

  @Test
  public void initialBitrateEstimateOverwrite_forOffline_whileOffline_setsInitialEstimate() {
    setActiveNetworkInfo(networkInfoOffline);
    DefaultBandwidthMeter bandwidthMeter =
        new DefaultBandwidthMeter.Builder(ApplicationProvider.getApplicationContext())
            .setInitialBitrateEstimate(C.NETWORK_TYPE_OFFLINE, 123456789)
            .build();
    long initialEstimate = bandwidthMeter.getBitrateEstimate();

    assertThat(initialEstimate).isEqualTo(123456789);
  }

  @Test
  public void
      initialBitrateEstimateOverwrite_forOffline_whileConnectedToNetwork_doesNotSetInitialEstimate() {
    setActiveNetworkInfo(networkInfoWifi);
    DefaultBandwidthMeter bandwidthMeter =
        new DefaultBandwidthMeter.Builder(ApplicationProvider.getApplicationContext())
            .setInitialBitrateEstimate(C.NETWORK_TYPE_OFFLINE, 123456789)
            .build();
    long initialEstimate = bandwidthMeter.getBitrateEstimate();

    assertThat(initialEstimate).isNotEqualTo(123456789);
  }

  @Test
  public void initialBitrateEstimateOverwrite_forCountry_usesDefaultValuesForCountry() {
    setNetworkCountryIso(SLOW_COUNTRY_ISO);
    DefaultBandwidthMeter bandwidthMeterSlow =
        new DefaultBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimateSlow = bandwidthMeterSlow.getBitrateEstimate();

    setNetworkCountryIso(FAST_COUNTRY_ISO);
    DefaultBandwidthMeter bandwidthMeterFastWithSlowOverwrite =
        new DefaultBandwidthMeter.Builder(ApplicationProvider.getApplicationContext())
            .setInitialBitrateEstimate(SLOW_COUNTRY_ISO)
            .build();
    long initialEstimateFastWithSlowOverwrite =
        bandwidthMeterFastWithSlowOverwrite.getBitrateEstimate();

    assertThat(initialEstimateFastWithSlowOverwrite).isEqualTo(initialEstimateSlow);
  }

  @Test
  public void networkTypeOverride_updatesBitrateEstimate() {
    setActiveNetworkInfo(networkInfoEthernet);
    DefaultBandwidthMeter bandwidthMeter =
        new DefaultBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    long initialEstimateEthernet = bandwidthMeter.getBitrateEstimate();

    bandwidthMeter.setNetworkTypeOverride(C.NETWORK_TYPE_2G);
    long initialEstimate2g = bandwidthMeter.getBitrateEstimate();

    assertThat(initialEstimateEthernet).isGreaterThan(initialEstimate2g);
  }

  @Test
  public void networkTypeOverride_doesFullReset() {
    // Simulate transfers for an ethernet connection.
    setActiveNetworkInfo(networkInfoEthernet);
    FakeClock clock = new FakeClock(/* initialTimeMs= */ 0);
    DefaultBandwidthMeter bandwidthMeter =
        new DefaultBandwidthMeter.Builder(ApplicationProvider.getApplicationContext())
            .setClock(clock)
            .build();
    long[] bitrateEstimatesWithNewInstance = simulateTransfers(bandwidthMeter, clock);

    // Create a new instance and seed with some transfers.
    setActiveNetworkInfo(networkInfo2g);
    bandwidthMeter =
        new DefaultBandwidthMeter.Builder(ApplicationProvider.getApplicationContext())
            .setClock(clock)
            .build();
    simulateTransfers(bandwidthMeter, clock);

    // Override the network type to ethernet and simulate transfers again.
    bandwidthMeter.setNetworkTypeOverride(C.NETWORK_TYPE_ETHERNET);
    long[] bitrateEstimatesAfterReset = simulateTransfers(bandwidthMeter, clock);

    // If overriding the network type fully reset the bandwidth meter, we expect the bitrate
    // estimates generated during simulation to be the same.
    assertThat(bitrateEstimatesAfterReset).isEqualTo(bitrateEstimatesWithNewInstance);
  }

  @Test
  @SuppressWarnings("deprecation")
  public void defaultInitialBitrateEstimate_withoutContext_isReasonable() {
    DefaultBandwidthMeter bandwidthMeterWithBuilder =
        new DefaultBandwidthMeter.Builder(/* context= */ null).build();
    long initialEstimateWithBuilder = bandwidthMeterWithBuilder.getBitrateEstimate();

    DefaultBandwidthMeter bandwidthMeterWithoutBuilder = new DefaultBandwidthMeter();
    long initialEstimateWithoutBuilder = bandwidthMeterWithoutBuilder.getBitrateEstimate();

    assertThat(initialEstimateWithBuilder).isGreaterThan(100_000L);
    assertThat(initialEstimateWithBuilder).isLessThan(50_000_000L);
    assertThat(initialEstimateWithoutBuilder).isGreaterThan(100_000L);
    assertThat(initialEstimateWithoutBuilder).isLessThan(50_000_000L);
  }

  private void setActiveNetworkInfo(NetworkInfo networkInfo) {
    Shadows.shadowOf(connectivityManager).setActiveNetworkInfo(networkInfo);
  }

  private void setNetworkCountryIso(String countryIso) {
    Shadows.shadowOf(telephonyManager).setNetworkCountryIso(countryIso);
  }

  private static long[] simulateTransfers(DefaultBandwidthMeter bandwidthMeter, FakeClock clock) {
    long[] bitrateEstimates = new long[SIMULATED_TRANSFER_COUNT];
    Random random = new Random(/* seed= */ 0);
    DataSource dataSource = new FakeDataSource();
    DataSpec dataSpec = new DataSpec(Uri.parse("https://dummy.com"));
    for (int i = 0; i < SIMULATED_TRANSFER_COUNT; i++) {
      bandwidthMeter.onTransferStart(dataSource, dataSpec, /* isNetwork= */ true);
      clock.advanceTime(random.nextInt(/* bound= */ 5000));
      bandwidthMeter.onBytesTransferred(
          dataSource,
          dataSpec,
          /* isNetwork= */ true,
          /* bytes= */ random.nextInt(5 * 1024 * 1024));
      bandwidthMeter.onTransferEnd(dataSource, dataSpec, /* isNetwork= */ true);
      bitrateEstimates[i] = bandwidthMeter.getBitrateEstimate();
    }
    return bitrateEstimates;
  }
}

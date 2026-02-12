# Proguard rules specific to the main demo app.

# Constructor accessed via reflection in PlayerActivity
-dontnote com.google.android.exoplayer2.ext.ima.ImaAdsLoader
-keepclassmembers class com.google.android.exoplayer2.ext.ima.ImaAdsLoader {
  <init>(android.content.Context, android.net.Uri);
}

# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /usr/local/google/home/cartland/android-sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Begin Enable minification

#-ignorewarnings
-dontobfuscate

-keepattributes SourceFile,LineNumberTable
-keepattributes Signature
-keepattributes *Annotation*
#-keepattributes InnerClasses

# Fork: JSON responses are bound reflectively (@JsonPath / Gson). -dontobfuscate keeps names,
# so we only need to stop R8 removing reflectively-accessed members of these API/model classes.
-keep class com.liskovsoft.youtubeapi.** { *; }
-keep class com.liskovsoft.googleapi.** { *; }
-keep class com.liskovsoft.googlecommon.** { *; }
-keep class com.liskovsoft.mediaserviceinterfaces.** { *; }

# Retrofit builds API impls via reflection/proxies; Gson/JsonPath read fields reflectively
-keepclasseswithmembers,allowobfuscation interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keepclassmembers,allowobfuscation class * { @com.google.gson.annotations.SerializedName <fields>; }

# j2v8: JS engine invoked over JNI; its callbacks are resolved by name from native code
-keep class com.eclipsesource.v8.** { *; }

-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn com.jayway.jsonpath.**
-dontwarn retrofit2.**
-dontwarn org.slf4j.**

# End Enable minification

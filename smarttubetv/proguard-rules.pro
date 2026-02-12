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

-keep public class com.liskovsoft.youtubeapi.** { *; }
#-keep class okhttp3.**{*;}
#-keep interface okhttp3.** { *; }

-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn com.jayway.jsonpath.**
-dontwarn retrofit2.**
-dontwarn org.slf4j.**

# End Enable minification

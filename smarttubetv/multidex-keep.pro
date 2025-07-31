-keepclassmembers class androidx.room.RoomDatabase {
    androidx.sqlite.db.SupportSQLiteDatabase mDatabase;
    <init>(...);
}
-keepclassmembers class androidx.core.app.NotificationCompatBuilder {
    java.util.List mActionExtrasList;
    <init>(...);
}
-keep class com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.ContextMenuProvider { *; }
-keep class com.liskovsoft.smartyoutubetv2.tv.ui.main.MainApplication { *; }
-keep class com.google.android.exoplayer2.util.Util { *; }
-keep class com.bumptech.glide.request.RequestOptions { *; }
-keep class com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tooltips.TooltipCompatHandler { *; }
-keepclassmembers class android.view.View {
    void setZ(float);
    <init>(...);
}
-keep class androidx.leanback.widget.ItemBridgeAdapter { *; }
#-keep class com.liskovsoft.smartyoutubetv2.common.exoplayer.controller.ExoPlayerController { *; }
-keepclassmembers class com.liskovsoft.smartyoutubetv2.common.exoplayer.controller.ExoPlayerController {
    boolean containsMedia();
    <init>(...);
}
-keepclassmembers interface com.google.android.exoplayer2.Player$EventListener {
    void onPlaybackParametersChanged(com.google.android.exoplayer2.PlaybackParameters);
}
-keep class androidx.leanback.app.BrowseSupportFragment { *; }
-keep class com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.AutoFrameRateController { *; }
-keep class io.reactivex.internal.operators.observable.ObservableDelaySubscriptionOther { *; }
-keep class com.google.gson.internal.bind.TypeAdapters$7 { *; }
-keep class com.google.gson.internal.bind.TypeAdapters { *; }
-keep class androidx.leanback.widget.ItemAlignmentFacet$ItemAlignmentDef { *; }
-keep class com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData { *; }
-keep class com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory { *; }
-keep class com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelUploadsPresenter { *; }
-keep class com.liskovsoft.leanbackassistant.channels.UpdateChannelsReceiver { *; }
-keep class androidx.core.view.ViewCompat { *; }
-keep class androidx.work.impl.WorkManagerInitializer { *; }
-keep class **$r8$backportedMethods$** { *; }
-keep class kotlin.text.StringsKt__StringsJVMKt { *; }
-keep class kotlin.ranges.RangesKt___RangesKt { *; }
-keep class kotlin.jvm.functions.Function1 { *; }
-keep class io.reactivex.schedulers.Schedulers { *; }
#-keep class kotlin.collections.builders.* { *; }
#-keep class androidx.room.** { *; }
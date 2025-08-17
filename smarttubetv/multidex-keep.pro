-keepclassmembers class com.liskovsoft.mediaserviceinterfaces.ServiceManager {
    <init>(...);
    com.liskovsoft.mediaserviceinterfaces.MediaItemService getMediaItemService();
}
# IncompatibleClassChangeError: androidx.recyclerview.widget.RecyclerView$Recycler
-keepclassmembers class androidx.recyclerview.widget.RecyclerView$Recycler {
    <init>(...);
}
# NoSuchFieldError: Attempted read of 32-bit non-primitive on field 'int android.content.res.Configuration.mnc'
-keepclassmembers class io.reactivex.disposables.CompositeDisposable {
    <init>(...);
}
# NoSuchMethodError: android.graphics.Canvas.drawCircle
-keepclassmembers class com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.misc.SeekBar {
    <init>(...);
    void onDraw(android.graphics.Canvas);
    int mKnobx;
    android.graphics.Paint mKnobPaint;
}
-keepclassmembers class okhttp3.OkHttpClient {
    <init>(...);
}
-keepclassmembers class androidx.recyclerview.widget.RecyclerView {
    <init>(...);
    void setNestedScrollingEnabled(boolean);
}
-keepclassmembers class com.liskovsoft.smartyoutubetv2.common.utils.Utils {
    boolean isFormatSupported(com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track.MediaTrack);
}
-keepclassmembers class com.google.android.exoplayer2.video.VideoRendererEventListener {
    void onVideoDisabled(com.google.android.exoplayer2.decoder.DecoderCounters);
}
-keepclassmembers class com.liskovsoft.smartyoutubetv2.common.app.models.search.MediaServiceSearchTagProvider {
    <init>(...);
    boolean mIgnoreEmptyQuery;
    com.liskovsoft.mediaserviceinterfaces.ContentService mContentService;
}
-keepclassmembers class androidx.leanback.widget.GridLayoutManager {
    <init>(...);
}
-keepclassmembers class androidx.leanback.app.BaseRowSupportFragment {
    <init>(...);
    androidx.leanback.widget.VerticalGridView mVerticalGridView;
    androidx.leanback.widget.VerticalGridView getVerticalGridView();
}
-keepclassmembers class com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.VideoStateController {
    <init>(...);
}
-keepclassmembers class com.liskovsoft.smartyoutubetv2.tv.ui.browse.BrowseSectionFragmentFactory {
    <init>(...);
}
-keepclassmembers class androidx.room.RoomDatabase {
    <init>(...);
    androidx.sqlite.db.SupportSQLiteDatabase mDatabase;
}
-keepclassmembers class androidx.core.app.NotificationCompatBuilder {
    <init>(...);
    java.util.List mActionExtrasList;
}
-keep class com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.ContextMenuProvider { *; }
-keep class com.liskovsoft.smartyoutubetv2.tv.ui.main.MainApplication { *; }
-keep class com.google.android.exoplayer2.util.Util { *; }
-keep class com.bumptech.glide.request.RequestOptions { *; }
-keep class com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tooltips.TooltipCompatHandler { *; }
-keepclassmembers class android.view.View {
    <init>(...);
    void setZ(float);
}
-keep class androidx.leanback.widget.ItemBridgeAdapter { *; }
-keepclassmembers class com.liskovsoft.smartyoutubetv2.common.exoplayer.controller.ExoPlayerController {
    <init>(...);
    boolean containsMedia();
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
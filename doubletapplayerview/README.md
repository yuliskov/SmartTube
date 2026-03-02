# DoubleTapPlayerView

A simple library to include double tap behavior to ExoPlayer's PlayerView. 
Created to handle fast forward/rewind behavior like YouTube.

![teamwork-flip](github/preview_gif.gif)

# Sample app

If you would like to test the YouTube overlay, then you can either download the demo app,
which can be found under Assets of the release or build it yourself from code.
It provides all main modifications available.

The sample videos own by *Blender Foundation* and a full list can be found [here][videolist].

# Download

The Gradle dependency is available via [jitpack.io][jitpack].
To be able to load this library, you have to add the repository to your project's gradle file:

```gradle
allprojects {
  repositories {
    ...
    maven { url 'https://jitpack.io' }
  }
}
```

Then, in your app's directory, you can include it the same way like other libraries:

```gradle
android {
  ...
  // If you face problems during building you should try including the below lines if you
  // haven't already
  
  // compileOptions {
  //   sourceCompatibility JavaVersion.VERSION_1_8
  //   targetCompatibility JavaVersion.VERSION_1_8
  // }
}

dependencies {
  implementation 'com.github.vkay94:DoubleTapPlayerView:1.0.4'
}
```

The minimum API level supported by this library is API 16 as ExoPlayer does, but I can't 
verify versions below API level 21 (Lollipop) myself. So feedback is welcomed.

# Getting started

In order to start using the YouTube overlay, the easiest way is to include it directly 
into your XML layout, e.g. on top of `DoubleTapPlayerView` or inside ExoPlayer's controller:

```xml
<FrameLayout
    android:layout_width="match_parent"
    android:layout_height="match_parent" >
    
    <com.github.vkay94.dtpv.DoubleTapPlayerView
        android:id="@+id/playerView"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        
        app:dtpv_controller="@+id/youtube_overlay" />

    <com.github.vkay94.dtpv.youtube.YouTubeOverlay
        android:id="@+id/youtube_overlay"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:visibility="invisible"
        
        app:yt_playerView="@+id/playerView" />
</FrameLayout>
```

Then, inside your `Activity` or `Fragment`, you can specify which preparations should be done
before and after the animation, but at least, you have got to toggle the visibility of the 
overlay and reference the (Simple)ExoPlayer to it:

```kotlin
youtube_overlay
    .performListener(object : YouTubeOverlay.PerformListener {
        override fun onAnimationStart() {
            // Do UI changes when circle scaling animation starts (e.g. hide controller views)
            youtube_overlay.visibility = View.VISIBLE
        }

        override fun onAnimationEnd() {
            // Do UI changes when circle scaling animation starts (e.g. show controller views)
            youtube_overlay.visibility = View.GONE
        }
    })
    // Uncomment this line if you haven't set yt_playerView in XML
    // .playerView(playerView)

// Uncomment this line if you haven't set dtpv_controller in XML 
// playerView.controller(youtube_overlay)

// Call this method whenever the player is released and recreated
youtube_overlay.player(simpleExoPlayer)
```

This way, you have more control about the appearance, for example you could apply a fading 
animation to it. For a full initialization you can refer to the demo application's MainActivity 
and layout files.

---

# API documentation

The following sections provide detailed documentation for the components of the library.

## DoubleTapPlayerView

`DoubleTapPlayerView` is the core of this library. It recognizes specific gestures 
which provides more control for the double tapping gesture.
An overview about the added methods can be found in the [PlayerDoubleTapListener][PlayerDoubleTapListener] 
interface.

You can adjust how long the double tap mode remains after the last action,
the default value is 650 milliseconds.

## YouTubeOverlay

`YouTubeOverlay` is the reason for this library. It provides nearly the
same experience like the fast forward/rewind feature which is used by YouTube's
Android app. It is highly modifiable.

### XML attributes

If you add the view to your XML layout you can set some custom attributes 
to customize the view's look and behavior. 
Every attributes value can also be get and set programmatically.

| Attribute name | Description | Type |
| ------------- | ------------| ------|
| `yt_seekSeconds` | Fast forward/rewind seconds skip per tap. The text *xx seconds* will be changed where xx is `value`. | `int` |
| `yt_animationDuration` |  Speed of the circle scaling / time in millis to expand completely. When this time has passed, YouTubeOverlay's `PerformListener.onAnimationEnd()` will be called. | `int` |
| `yt_arcSize` | Arc of the background circle. The higher the value the more roundish the shape becomes. This attribute should be set dynamically depending on screen size and orientation. | `dimen` | 
| `yt_tapCircleColor` | Color of the scaling circle after tap. | `color` |
| `yt_backgroundCircleColor` | Color of the background shape. | `color` |
| `yt_iconAnimationDuration` | Time in millis to run through an full fade cycle. | `int` |
| `yt_icon` | One of the three forward icons. Will be multiplied by three and mirrored for rewind. | `drawable` |
| `yt_textAppearance` | Text appearance for the *xx seconds* text. | `style` |

I'd recommend the sample app to try out the different values for them.

### YouTubeOverlay.PerformListener

This interface listens to the *lifecycle* of the overlay.

```kotlin
// Obligatory: Called when the overlay is not visible and the first valid double tap event occurred.
// Visibility of the overlay should be set to VISIBLE within this interface method.
fun onAnimationStart()

// Obligatory: Called when the circle animation is finished.
// Visibility of the overlay should be set to GONE or INVISIBLE within this interface method.
fun onAnimationEnd()

// Optional: Determines whether the player should forward (true), rewind (false) or ignore (null) taps.
fun shouldForward(player: Player, playerView: DoubleTapPlayerView, posX: Float): Boolean?
```

### SeekListener

This interface reacts to the events during rewinding/forwarding.

```kotlin
// Called when the start of the video is reached
fun onVideoStartReached()

// Called when the end of the video is reached
fun onVideoEndReached()
```

[videolist]: https://gist.github.com/jsturgis/3b19447b304616f18657
[jitpack]: https://jitpack.io/#vkay94/DoubleTapPlayerView
[PlayerDoubleTapListener]: https://github.com/vkay94/DoubleTapPlayerView/blob/master/doubletapplayerview/src/main/java/com/github/vkay94/dtpv/PlayerDoubleTapListener.java

# SmartTubeNext 

<p align="center">
    <img src="images/app_banner.png" alt="Loading Card"/>
</p>

SmartTubeNext is an _unofficial_ YouTube client for Android TV.

Current release features:

- Run on Android 4.3 and above
- Browse News, Games, Music
- Browse Subscriptions, History, Playlists
- Play videos
- Like, Dislike or Subscribe for video
- Search for videos
- View subscribed channel content
- Change various settings for the video player
- Animated previews for videos
- Android TV channels and search support

# Screens

## [Browse Fragment](/smarttubetv/src/main/java/com/liskovsoft/smartyoutubetv2/tv/ui/browse/BrowseFragment.java)

The browse fragment is what is used to display the browseable categories and options card. The
[Icon Header Item Presenter](/smarttubetv/src/main/java/com/liskovsoft/smartyoutubetv2/tv/presenter/IconHeaderItemPresenter.java) is used
to setup and display the categories in the headers dock and  [Card Presenter](/smarttubetv/src/main/java/com/liskovsoft/smartyoutubetv2/tv/presenter/CardPresenter.java)
is used to display the Video cards.

<p align="center">
    <img src="images/browse.png" alt="Main"/>
</p>
<p align="center">
    <img src="images/browse_subscriptions.png" alt="Browse Subscriptions"/>
</p>


## [Playback Activity](/smarttubetv/src/main/java/com/liskovsoft/smartyoutubetv2/tv/ui/playback/PlaybackActivity.java)

The Playback Activity is used to play the video from categories. Which used the [PlaybackFragment](/smarttubetv/src/main/java/com/liskovsoft/smartyoutubetv2/tv/ui/playback/PlaybackFragment.java) to display the playback controls over the top of the PlaybackActivity.

<p align="center">
    <img src="images/video.png" alt="Video"/>
</p>

<p align="center">
    <img src="images/video_related.png" alt="Video Related"/>
</p>


## [Search Fragment](/smarttubetv/src/main/java/com.liskovsoft/smartyoutubetv2/tv/ui/search/SearchFragment.java)

The Search Fragment allows users to search for video by either tags or usernames.

<p align="center">
    <img src="images/search.png" alt="Search"/>
</p>

<p align="center">
    <img src="images/search_keyboard.png" alt="Search with keyboard"/>
</p>

## [Header Grid Fragment](/smarttubetv/src/main/java/com/liskovsoft/smartyoutubetv2/tv/ui/browse/grid/HeaderGridFragment.java)

The Header Grid Fragment is used to show a grid of videos from either a Subscriptions or History.

<p align="center">
    <img src="images/browse_history.png" alt="History grid"/>
</p>

# Custom Components

Many of the screens used some of these custom components created especially for the needs of this app:

## [Text Badge Image Card View](/smarttubetv/src/main/java/com/liskovsoft/smartyoutubetv2/tv/ui/widgets/textbadgecard/TextBadgeImageCardView.java)

<p align="center">
    <img src="images/browse_history.png" alt="Badge Card View"/>
</p>

This view extends the ImageCardView class so that we add custom functionality. The functionality
of this view is display a video preview of the video card that is currently in focus. It's made up
of the following components:

- [Text Badge Image View](/smarttubetv/src/main/java/com/liskovsoft/smartyoutubetv2/tv/ui/widgets/textbadgecard/TextBadgeImageView.java) - This is a custom VideoView that automatically loops a video without sound.

# Building

To build, install and run a debug version, run this from the root of the project:

```./gradlew assembleDebug```

# Unit Tests

To run the unit tests for the application:

```./gradlew testDebugUnitTest```

# User Interface Tests

To run the user interface tests for the application:

```./gradlew connectedDebugAndroidTest```

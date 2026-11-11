package studio.hypertext.curfew.enforcement

data class CuratedEntertainmentApp(
    val packageName: String,
    val displayName: String,
)

object CuratedEntertainmentApps {
    val ALL = listOf(
        CuratedEntertainmentApp("com.zhiliaoapp.musically", "TikTok"),
        CuratedEntertainmentApp("com.instagram.android", "Instagram"),
        CuratedEntertainmentApp("com.google.android.youtube", "YouTube"),
        CuratedEntertainmentApp("com.google.android.apps.youtube.music", "YouTube Music"),
        CuratedEntertainmentApp("com.twitter.android", "X (Twitter)"),
        CuratedEntertainmentApp("com.reddit.frontpage", "Reddit"),
        CuratedEntertainmentApp("com.facebook.katana", "Facebook"),
        CuratedEntertainmentApp("com.netflix.mediaclient", "Netflix"),
        CuratedEntertainmentApp("tv.twitch.android.app", "Twitch"),
    )
}

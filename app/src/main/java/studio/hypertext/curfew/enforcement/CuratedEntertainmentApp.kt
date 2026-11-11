package studio.hypertext.curfew.enforcement

/**
 * A suggested entertainment app, identified by its Android package and shown with a friendly
 * name. A suggestion does not authorize interception.
 */
data class CuratedEntertainmentApp(
    val packageName: String,
    val displayName: String,
)

/**
 * The fixed suggestions shown during protection setup. Only packages the user chooses enter
 * the session's allowed interception set.
 */
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

package studio.hypertext.curfew.account

internal fun canUseAccountSignIn(applicationId: String): Boolean =
    applicationId == "studio.hypertext.curfew"

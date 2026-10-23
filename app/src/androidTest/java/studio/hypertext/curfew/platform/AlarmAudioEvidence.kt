package studio.hypertext.curfew.platform

internal fun hasStartedAlarmPlayer(dump: String, uid: Int): Boolean {
    val players = dump.substringAfter("  players:", "").substringBefore("  ducked players")
    return players.lineSequence().any { line ->
        "u/pid:$uid/" in line && "state:started" in line && "usage=USAGE_ALARM" in line
    }
}

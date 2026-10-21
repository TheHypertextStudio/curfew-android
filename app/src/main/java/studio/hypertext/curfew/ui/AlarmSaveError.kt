package studio.hypertext.curfew.ui

internal fun alarmSaveError(failure: Exception): String =
    if (failure is IllegalArgumentException && failure.message == "Choose a future date and time.") {
        "Choose a future date and time."
    } else {
        "Couldn’t save the alarm. Try again."
    }

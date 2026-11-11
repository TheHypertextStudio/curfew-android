package studio.hypertext.curfew.ui

import androidx.compose.runtime.Composable

/**
 * Starts the alarm setup journey when the user chooses Get started. It does not arm an alarm
 * or grant any capabilities.
 */
@Composable
internal fun WelcomeRoute(
    appState: AlarmSetupJourney,
) {
    CurfewWelcomeScreen(
        onContinue = {
            appState.setupMode = true
            appState.navigate(CurfewDestination.Edit)
        },
    )
}

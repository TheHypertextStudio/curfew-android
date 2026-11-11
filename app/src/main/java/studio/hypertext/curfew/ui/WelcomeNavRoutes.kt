package studio.hypertext.curfew.ui

import androidx.compose.runtime.Composable

@Composable
internal fun WelcomeRoute(
    appState: CurfewAppState,
) {
    CurfewWelcomeScreen(
        onContinue = {
            appState.setupMode = true
            appState.navigate(CurfewDestination.Edit)
        },
    )
}

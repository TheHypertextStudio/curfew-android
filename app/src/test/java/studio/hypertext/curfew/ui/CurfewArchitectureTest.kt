package studio.hypertext.curfew.ui

import androidx.compose.runtime.mutableStateListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import studio.hypertext.curfew.alarm.AlarmSetupUiState
import studio.hypertext.curfew.readiness.AlarmPresentation
import studio.hypertext.curfew.readiness.AlarmReadiness

class CurfewArchitectureTest {

    @Test
    fun `CurfewDestination maps all known routes bidirectionally`() {
        val destinations = listOf(
            CurfewDestination.Daily to "daily",
            CurfewDestination.Edit to "edit",
            CurfewDestination.CheckIn to "check-in",
            CurfewDestination.Readiness to "readiness",
            CurfewDestination.Review to "review",
            CurfewDestination.Settings to "settings",
            CurfewDestination.Account to "account",
            CurfewDestination.Enforcement to "enforcement",
            CurfewDestination.Welcome to "welcome",
        )

        for ((destination, route) in destinations) {
            assertEquals(route, destination.route)
            assertEquals(destination, CurfewDestination.fromRoute(route))
        }

        // Unknown route falls back safely to Daily
        assertEquals(CurfewDestination.Daily, CurfewDestination.fromRoute("unknown_route"))
    }

    @Test
    fun `CurfewNavigationState manages backstack operations cleanly`() {
        val stack = mutableStateListOf<CurfewDestination>(CurfewDestination.Daily)
        val navState = CurfewNavigationState(stack)

        assertEquals(CurfewDestination.Daily, navState.currentDestination)

        // Navigate forward
        navState.navigate(CurfewDestination.Settings)
        assertEquals(CurfewDestination.Settings, navState.currentDestination)
        assertEquals(2, navState.backStack.size)

        navState.navigate(CurfewDestination.Enforcement)
        assertEquals(CurfewDestination.Enforcement, navState.currentDestination)
        assertEquals(3, navState.backStack.size)

        // Pop back
        val popped = navState.popBack()
        assertTrue(popped)
        assertEquals(CurfewDestination.Settings, navState.currentDestination)

        // Navigating to existing destination pops to it without duplicate entries
        navState.navigate(CurfewDestination.Daily)
        assertEquals(1, navState.backStack.size)
        assertEquals(CurfewDestination.Daily, navState.currentDestination)

        // Cannot pop the root destination
        assertFalse(navState.popBack())
        assertEquals(CurfewDestination.Daily, navState.currentDestination)

        // navigateToRoot resets stack
        navState.navigate(CurfewDestination.Account)
        navState.navigate(CurfewDestination.CheckIn)
        assertEquals(3, navState.backStack.size)
        navState.navigateToRoot()
        assertEquals(1, navState.backStack.size)
        assertEquals(CurfewDestination.Daily, navState.currentDestination)
    }

    @Test
    fun `AlarmSetupUiState evaluates canArm strictly`() {
        val notReady = AlarmSetupUiState(
            readiness = AlarmReadiness(
                canArm = false,
                requiredActions = emptySet(),
                disclosures = emptySet(),
                presentation = AlarmPresentation.FULL_SCREEN,
            ),
            callbackId = "cb-1",
            conditionLabel = "Morning check-in",
            conditionVerified = true,
        )
        assertFalse(notReady.canArm)

        val missingConnection = AlarmSetupUiState(
            readiness = AlarmReadiness(
                canArm = true,
                requiredActions = emptySet(),
                disclosures = emptySet(),
                presentation = AlarmPresentation.FULL_SCREEN,
            ),
            callbackId = null,
            conditionLabel = null,
            conditionVerified = false,
        )
        assertFalse(missingConnection.canArm)

        val unverifiedConnection = AlarmSetupUiState(
            readiness = AlarmReadiness(
                canArm = true,
                requiredActions = emptySet(),
                disclosures = emptySet(),
                presentation = AlarmPresentation.FULL_SCREEN,
            ),
            callbackId = "cb-1",
            conditionLabel = "Morning check-in",
            conditionVerified = false,
        )
        assertFalse(unverifiedConnection.canArm)

        val readyState = AlarmSetupUiState(
            readiness = AlarmReadiness(
                canArm = true,
                requiredActions = emptySet(),
                disclosures = emptySet(),
                presentation = AlarmPresentation.FULL_SCREEN,
            ),
            callbackId = "cb-1",
            conditionLabel = "Morning check-in",
            conditionVerified = true,
        )
        assertTrue(readyState.canArm)
    }

    @Test
    fun `PrimaryNavItems exposes core app destinations with valid resources`() {
        val destinations = PrimaryNavItems.map { it.destination }
        assertEquals(
            listOf(
                CurfewDestination.Daily,
                CurfewDestination.Enforcement,
                CurfewDestination.Settings,
                CurfewDestination.Account,
            ),
            destinations,
        )

        for (item in PrimaryNavItems) {
            assertTrue(item.labelRes != 0)
            assertTrue(item.iconRes != 0)
        }
    }

}

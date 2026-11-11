package studio.hypertext.curfew.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList

/**
 * Owns the ordered destinations in the app's back stack. Navigating to an existing destination
 * removes entries above it so each destination has one active back-stack identity.
 */
@Stable
class CurfewNavigationState(
    val backStack: SnapshotStateList<CurfewDestination>,
) {
    val currentDestination: CurfewDestination
        get() = backStack.lastOrNull() ?: CurfewDestination.Daily

    fun navigate(destination: CurfewDestination) {
        val existingIndex = backStack.indexOf(destination)
        if (existingIndex >= 0) {
            while (backStack.lastIndex > existingIndex) {
                backStack.removeLastOrNull()
            }
        } else {
            backStack.add(destination)
        }
    }

    fun popBack(): Boolean {
        if (backStack.size > 1) {
            backStack.removeLastOrNull()
            return true
        }
        return false
    }

    fun navigateToRoot() {
        backStack.clear()
        backStack.add(CurfewDestination.Daily)
    }

    companion object {
        val Saver = listSaver<CurfewNavigationState, String>(
            save = { state -> state.backStack.map { it.route } },
            restore = { routes ->
                val list = routes.map { CurfewDestination.fromRoute(it) }.toMutableStateList()
                if (list.isEmpty()) list.add(CurfewDestination.Daily)
                CurfewNavigationState(list)
            },
        )
    }
}

/**
 * Creates or restores the navigation back stack. On first use it inserts the editor when a
 * later setup task needs a meaningful Back destination.
 */
@Composable
fun rememberCurfewNavigationState(
    initialDestination: CurfewDestination = CurfewDestination.Daily,
    startSetup: Boolean = false,
    hasDraft: Boolean = false,
): CurfewNavigationState {
    return rememberSaveable(saver = CurfewNavigationState.Saver) {
        val initialList = mutableStateListOf<CurfewDestination>()
        initialList.add(CurfewDestination.Daily)
        if (startSetup && hasDraft && initialDestination != CurfewDestination.Edit) {
            initialList.add(CurfewDestination.Edit)
        }
        if (initialDestination != CurfewDestination.Daily) {
            initialList.add(initialDestination)
        }
        CurfewNavigationState(initialList)
    }
}

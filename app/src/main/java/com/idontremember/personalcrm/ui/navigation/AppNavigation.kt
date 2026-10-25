package com.idontremember.personalcrm.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.idontremember.personalcrm.R
import com.idontremember.personalcrm.ui.addevent.AddEventSheet
import com.idontremember.personalcrm.ui.contacts.ContactDetailScreen
import com.idontremember.personalcrm.ui.contacts.ContactsListScreen
import com.idontremember.personalcrm.ui.events.EventDetailScreen
import com.idontremember.personalcrm.ui.events.EventsListScreen
import com.idontremember.personalcrm.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable

// Route definitions — must be @Serializable and implement NavKey
@Serializable
data object ContactsRoute : NavKey

@Serializable
data class ContactDetailRoute(val contactId: Long) : NavKey

@Serializable
data object EventsRoute : NavKey

@Serializable
data class EventDetailRoute(val eventId: Long) : NavKey

@Serializable
data object AddEventRoute : NavKey

@Serializable
data object SettingsRoute : NavKey

/**
 * Determine which top-level tab is active based on the current back stack.
 * Returns null when viewing a detail screen (neither tab highlighted).
 */
private fun activeTab(backStack: List<NavKey>): NavKey? {
    val top = backStack.lastOrNull() ?: return null
    return when (top) {
        is ContactsRoute -> ContactsRoute
        is EventsRoute -> EventsRoute
        else -> null // detail screens — no tab highlighted
    }
}

@Composable
fun AppNavigation() {
    val backStack = rememberNavBackStack(ContactsRoute)
    var showAddEventSheet by remember { mutableStateOf(false) }

    val currentTab = activeTab(backStack)

    Scaffold(
        bottomBar = {
            // Hide bottom nav on detail screens to give more screen space
            if (currentTab != null) {
                NavigationBar(modifier = Modifier.testTag("bottom_navigation")) {
                    NavigationBarItem(
                        selected = currentTab is ContactsRoute,
                        onClick = {
                            // Clear backstack to Contacts top-level route
                            backStack.clear()
                            backStack.add(ContactsRoute)
                        },
                        // a11y: the label already names the tab — icon is decorative.
                        icon = { Icon(Icons.Default.Person, contentDescription = null) },
                        label = { Text(stringResource(R.string.title_contacts)) },
                        modifier = Modifier.testTag("nav_tab_contacts")
                    )
                    NavigationBarItem(
                        selected = currentTab is EventsRoute,
                        onClick = {
                            // Clear backstack to Events top-level route
                            backStack.clear()
                            backStack.add(EventsRoute)
                        },
                        // a11y: the label already names the tab — icon is decorative.
                        icon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                        label = { Text(stringResource(R.string.title_events)) },
                        modifier = Modifier.testTag("nav_tab_events")
                    )
                }
            }
        }
    ) { innerPadding ->
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.padding(innerPadding),
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator()
            ),
            entryProvider = fun(route: NavKey): NavEntry<NavKey> = when (route) {
                is ContactsRoute -> NavEntry(route) {
                    ContactsListScreen(
                        onContactClick = { contactId ->
                            backStack.add(ContactDetailRoute(contactId))
                        },
                        onSettingsClick = { backStack.add(SettingsRoute) }
                    )
                }

                is ContactDetailRoute -> NavEntry(route) {
                    ContactDetailScreen(
                        contactId = route.contactId,
                        onBack = { backStack.removeLastOrNull() },
                        onEventClick = { eventId ->
                            backStack.add(EventDetailRoute(eventId))
                        }
                    )
                }

                is EventsRoute -> NavEntry(route) {
                    EventsListScreen(
                        onEventClick = { eventId ->
                            backStack.add(EventDetailRoute(eventId))
                        },
                        onAddEventClick = {
                            showAddEventSheet = true
                        },
                        onSettingsClick = { backStack.add(SettingsRoute) }
                    )
                }

                is SettingsRoute -> NavEntry(route) {
                    SettingsScreen(
                        onBack = { backStack.removeLastOrNull() }
                    )
                }

                is EventDetailRoute -> NavEntry(route) {
                    EventDetailScreen(
                        eventId = route.eventId,
                        onBack = { backStack.removeLastOrNull() }
                    )
                }

                is AddEventRoute -> NavEntry(route) {
                    AddEventSheet(
                        onDismiss = { backStack.removeLastOrNull() },
                        onSaved = { backStack.removeLastOrNull() }
                    )
                }

                else -> NavEntry(route) { Text(stringResource(R.string.unknown_route)) }
            }
        )
    }

    // Show add event sheet as overlay when triggered from Events screen FAB
    if (showAddEventSheet) {
        AddEventSheet(
            onDismiss = { showAddEventSheet = false },
            onSaved = { showAddEventSheet = false }
        )
    }
}
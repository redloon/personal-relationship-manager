package com.idontremember.personalcrm.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.compose.material3.Text
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.SaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.idontremember.personalcrm.ui.addevent.AddEventSheet
import com.idontremember.personalcrm.ui.contacts.ContactDetailScreen
import com.idontremember.personalcrm.ui.contacts.ContactsListScreen
import com.idontremember.personalcrm.ui.events.EventDetailScreen
import com.idontremember.personalcrm.ui.events.EventsListScreen
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

@Composable
fun AppNavigation() {
    val backStack = rememberNavBackStack(ContactsRoute)
    var showAddEventSheet by remember { mutableStateOf(false) }

    NavDisplay(
        backStack = backStack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = fun(route: NavKey): NavEntry<NavKey> = when (route) {
            is ContactsRoute -> NavEntry(route) {
                ContactsListScreen(
                    onContactClick = { contactId ->
                        backStack.add(ContactDetailRoute(contactId))
                    }
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
                    }
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

            else -> NavEntry(route) { Text("Unknown route") }
        }
    )

    // Show add event sheet as overlay when triggered from Events screen FAB
    if (showAddEventSheet) {
        AddEventSheet(
            onDismiss = { showAddEventSheet = false },
            onSaved = { showAddEventSheet = false }
        )
    }
}
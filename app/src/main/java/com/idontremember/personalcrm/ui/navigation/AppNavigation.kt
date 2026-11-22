package com.idontremember.personalcrm.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.idontremember.personalcrm.ui.addevent.observeCaptureDraft
import com.idontremember.personalcrm.ui.followups.FollowUpEditor
import com.idontremember.personalcrm.ui.followups.FollowUpReader
import com.idontremember.personalcrm.ui.followups.FollowUpsOverlay
import com.idontremember.personalcrm.ui.pilot.Dashboard
import com.idontremember.personalcrm.ui.pilot.TagsScreen
import com.idontremember.personalcrm.ui.pilot.TagDetailScreen
import com.idontremember.personalcrm.ui.events.HistoryScreen
import com.idontremember.personalcrm.ui.reading.FactReaderSheet
import com.idontremember.personalcrm.ui.contacts.ContactDetailScreen
import com.idontremember.personalcrm.ui.contacts.ContactsListScreen
import com.idontremember.personalcrm.ui.events.EventDetailScreen
import com.idontremember.personalcrm.ui.events.EventsListScreen
import com.idontremember.personalcrm.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable

// Route definitions — must be @Serializable and implement NavKey
@Serializable
data object DashboardRoute : NavKey

@Serializable
data object TagsRoute : NavKey

@Serializable
data class TagDetailRoute(val tagId: Long) : NavKey

@Serializable
data object ContactsRoute : NavKey

@Serializable
data object HiddenPeopleRoute : NavKey

@Serializable
data class ContactDetailRoute(val contactId: Long, val initialQuery: String = "") : NavKey

@Serializable
data object EventsRoute : NavKey

@Serializable
data class EventDetailRoute(val eventId: Long) : NavKey

@Serializable
data object AddEventRoute : NavKey

@Serializable
data object SettingsRoute : NavKey

/** Independent top-level histories keep a search intact while browsing people. */
@Composable
fun AppNavigation() {
    val homeStack = rememberNavBackStack(DashboardRoute)
    val peopleStack = rememberNavBackStack(ContactsRoute)
    var selectedTab by rememberSaveable { mutableStateOf("home") }
    val backStack = if (selectedTab == "people") peopleStack else homeStack
    val stateHolder = rememberSaveableStateHolder()
    val homeDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator<NavKey>(), rememberViewModelStoreNavEntryDecorator<NavKey>())
    val peopleDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator<NavKey>(), rememberViewModelStoreNavEntryDecorator<NavKey>())
    var showAddEventSheet by rememberSaveable { mutableStateOf(false) }
    var editingFact by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingFollowUp by rememberSaveable { mutableStateOf<Long?>(null) }
    var readingFact by rememberSaveable { mutableStateOf<Long?>(null) }
    var readingFollowUp by rememberSaveable { mutableStateOf<Long?>(null) }
    var showFollowUps by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val hasDraft by remember(context) { observeCaptureDraft(context) }.collectAsState(initial = false)
    // Secondary screens retain easy access to the two primary destinations.
    Scaffold(bottomBar = {
        NavigationBar(modifier = Modifier.testTag("bottom_navigation")) {
            NavigationBarItem(selected = selectedTab == "home", onClick = { if (selectedTab == "home") while (homeStack.size > 1) homeStack.removeLastOrNull(); selectedTab = "home" }, icon = { Icon(Icons.Default.Home, null) }, label = { Text(stringResource(R.string.recall_home)) }, modifier = Modifier.testTag("nav_tab_home"))
            NavigationBarItem(selected = selectedTab == "people", onClick = { if (selectedTab == "people") while (peopleStack.size > 1) peopleStack.removeLastOrNull(); selectedTab = "people" }, icon = { Icon(Icons.Default.Person, null) }, label = { Text(stringResource(R.string.recall_people)) }, modifier = Modifier.testTag("nav_tab_contacts"))
        }
    }) { padding ->
        stateHolder.SaveableStateProvider(selectedTab) {
            NavDisplay(
                backStack = backStack,
                modifier = Modifier.padding(padding).consumeWindowInsets(padding),
                entryDecorators = if(selectedTab == "people") peopleDecorators else homeDecorators,
                entryProvider = fun(route: NavKey): NavEntry<NavKey> = when(route) {
                    DashboardRoute -> NavEntry(route) { Dashboard(
                        onContact = { homeStack.add(ContactDetailRoute(it)) },
                        onContacts = { selectedTab = "people" },
                        onEvents = { homeStack.add(EventsRoute) },
                        onTags = { homeStack.add(TagsRoute) },
                        onSettings = { homeStack.add(SettingsRoute) },
                        onAdd = { showAddEventSheet = true },
                        onEvent = { homeStack.add(EventDetailRoute(it)) },
                        onFact = { readingFact = it },
                        onFollowUp = { readingFollowUp = it },
                        onTag = { homeStack.add(TagDetailRoute(it)) },
                        onFollowUps = { showFollowUps = true },
                        onContactSearch = { id, query -> homeStack.add(ContactDetailRoute(id, query)) },
                        hasDraft = hasDraft
                    ) }
                    TagsRoute -> NavEntry(route) { TagsScreen(onBack = { backStack.removeLastOrNull() }, onContact = { backStack.add(ContactDetailRoute(it)) }, onTag = { backStack.add(TagDetailRoute(it)) }) }
                    is TagDetailRoute -> NavEntry(route) { TagDetailScreen(tagId = route.tagId, onBack = { backStack.removeLastOrNull() }, onContact = { backStack.add(ContactDetailRoute(it)) }, onEvent = { backStack.add(EventDetailRoute(it)) }) }
                    ContactsRoute -> NavEntry(route) { ContactsListScreen(onContactClick = { backStack.add(ContactDetailRoute(it)) }, onSettingsClick = { backStack.add(SettingsRoute) }, onContactSearch = { id, query -> backStack.add(ContactDetailRoute(id, query)) }, onHiddenPeople = { backStack.add(HiddenPeopleRoute) }) }
                    HiddenPeopleRoute -> NavEntry(route) { ContactsListScreen(hidden = true, onBack = { backStack.removeLastOrNull() }, onContactClick = { backStack.add(ContactDetailRoute(it)) }, onSettingsClick = { backStack.add(SettingsRoute) }) }
                    is ContactDetailRoute -> NavEntry(route) { ContactDetailScreen(contactId = route.contactId, initialQuery = route.initialQuery, onBack = { backStack.removeLastOrNull() }, onEventClick = { backStack.add(EventDetailRoute(it)) }, onContactClick = { backStack.add(ContactDetailRoute(it)) }) }
                    EventsRoute -> NavEntry(route) { HistoryScreen(onBack = { backStack.removeLastOrNull() }, onEvent = { backStack.add(EventDetailRoute(it)) }, onFact = { readingFact = it }, onFollowUp = { readingFollowUp = it }, onSettings = { backStack.add(SettingsRoute) }, onAdd = { showAddEventSheet = true }, onContact = { backStack.add(ContactDetailRoute(it)) }) }
                    SettingsRoute -> NavEntry(route) { SettingsScreen(onBack = { backStack.removeLastOrNull() }, onHiddenPeople = { backStack.add(HiddenPeopleRoute) }) }
                    is EventDetailRoute -> NavEntry(route) { EventDetailScreen(eventId = route.eventId, onBack = { backStack.removeLastOrNull() }, onContactClick = { backStack.add(ContactDetailRoute(it)) }) }
                    AddEventRoute -> NavEntry(route) { AddEventSheet(onDismiss = { backStack.removeLastOrNull() }, onSaved = { backStack.removeLastOrNull() }) }
                    else -> NavEntry(route) { Text(stringResource(R.string.unknown_route)) }
                }
            )
        }
    }
    if(showAddEventSheet || editingFact != null) AddEventSheet(onDismiss = { showAddEventSheet = false; editingFact = null }, onSaved = { showAddEventSheet = false; editingFact = null }, existingFactId = editingFact)
    editingFollowUp?.let { id -> FollowUpEditor(existingId = id, onDismiss = { editingFollowUp = null }) }
    readingFact?.let { id -> FactReaderSheet(factId = id, onDismiss = { readingFact = null }, onContact = { readingFact = null; backStack.add(ContactDetailRoute(it)) }, onEdit = { readingFact = null; editingFact = it }) }
    readingFollowUp?.let { id -> FollowUpReader(existingId = id, onDismiss = { readingFollowUp = null }, onContact = { readingFollowUp = null; backStack.add(ContactDetailRoute(it)) }, onEdit = { readingFollowUp = null; editingFollowUp = it }) }
    if (showFollowUps) FollowUpsOverlay(onDismiss = { showFollowUps = false }, onContact = { showFollowUps = false; backStack.add(ContactDetailRoute(it)) })
}

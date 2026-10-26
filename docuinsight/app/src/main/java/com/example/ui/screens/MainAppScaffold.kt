package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.DocuInsightTheme
import com.example.ui.viewmodel.AuthUiState
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.DocViewModel

@Composable
fun MainApp(
    authViewModel: AuthViewModel = viewModel()
) {
    val context = LocalContext.current
    val authUiState by authViewModel.uiState.collectAsStateWithLifecycle()

    // Attempt silent auto-sign-in on initial load
    LaunchedEffect(Unit) {
        authViewModel.attemptAutoSignIn(context)
    }

    val isUserSignedIn = authUiState is AuthUiState.Authenticated

    if (!isUserSignedIn) {
        DocuInsightTheme {
            AuthScreen(authViewModel = authViewModel)
        }
    } else {
        val docViewModel: DocViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return DocViewModel(context) as T
            }
        })

        val themeMode by docViewModel.themeMode.collectAsStateWithLifecycle()
        val isDark = when (themeMode) {
            "dark" -> true
            "light" -> false
            else -> isSystemInDarkTheme()
        }

        DocuInsightTheme(darkTheme = isDark) {
            MainContent(docViewModel = docViewModel, authViewModel = authViewModel)
        }
    }
}

@Composable
fun MainContent(
    docViewModel: DocViewModel,
    authViewModel: AuthViewModel
) {
    var selectedNavIndex by remember { mutableIntStateOf(0) }
    val selectedDoc by docViewModel.selectedDocument.collectAsStateWithLifecycle()
    val notifications by docViewModel.notifications.collectAsStateWithLifecycle()
    val statusMessage by docViewModel.statusMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            docViewModel.clearStatusMessage()
        }
    }

    if (selectedDoc != null) {
        DocumentDetailScreen(
            docViewModel = docViewModel,
            document = selectedDoc!!,
            onBack = { docViewModel.selectDocument(null) }
        )
    } else {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                NavigationBar(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .testTag("bottom_nav_bar")
                ) {
                    val navItems = listOf(
                        Triple(0, "Documents", Icons.Default.Description),
                        Triple(1, "Cloud Sync", Icons.Default.Cloud),
                        Triple(2, "Audit Trail", Icons.Default.History),
                        Triple(3, "Security", Icons.Default.Security)
                    )

                    navItems.forEach { (index, label, icon) ->
                        NavigationBarItem(
                            selected = selectedNavIndex == index,
                            onClick = { selectedNavIndex = index },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) },
                            modifier = Modifier.testTag("nav_item_$index")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (selectedNavIndex) {
                    0 -> DocumentListScreen(
                        docViewModel = docViewModel,
                        onDocumentClick = { doc -> docViewModel.selectDocument(doc) }
                    )
                    1 -> CloudStorageScreen(docViewModel = docViewModel)
                    2 -> AuditLogsScreen(docViewModel = docViewModel)
                    3 -> SecuritySettingsScreen(docViewModel = docViewModel, authViewModel = authViewModel)
                }

                // In-App Real-time Notification Banner
                val latestNotif = notifications.firstOrNull()
                AnimatedVisibility(
                    visible = latestNotif != null,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp)
                ) {
                    if (latestNotif != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Notifications,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = latestNotif.title,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = latestNotif.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                IconButton(
                                    onClick = { docViewModel.dismissNotification(latestNotif.id) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Dismiss Notification",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.TarotReadingEntity
import com.example.ui.viewmodel.TarotViewModel

data class SpreadOption(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val description: String,
    val label: String
)

val spreadOptions = listOf(
    SpreadOption(
        id = "Single Card Draw",
        title = "Daily Guidance",
        icon = Icons.Default.AutoAwesome,
        description = "Instant insight into your current cosmic energy and advice.",
        label = "Single Card"
    ),
    SpreadOption(
        id = "Three Card Spread",
        title = "Past, Present, Future",
        icon = Icons.Default.FilterDrama,
        description = "A deep journey through your timeline to understand cause and outcome.",
        label = "Three Cards"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TarotDashboardScreen(
    viewModel: TarotViewModel,
    onScanCard: (String) -> Unit,
    onNavToChat: () -> Unit,
    onNavToVirtualDraw: () -> Unit
) {
    val settingsState by viewModel.settingsState.collectAsState()
    val historyState by viewModel.historyState.collectAsState()
    val authError by viewModel.authError.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(authError) {
        authError?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearAuthError()
        }
    }

    var showSettingsDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "MYSTIC TAROT",
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        color = Color(0xFFD4AF37) // Gold
                    )
                },
                actions = {
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.testTag("oracle_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Oracle Info",
                            tint = Color(0xFFD4AF37)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F081D)
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF0F081D), Color(0xFF130A24))
                    )
                )
                .padding(padding),
            contentPadding = PaddingValues(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // User Header Profile Card
            item {
                val context = androidx.compose.ui.platform.LocalContext.current
                UserProfileCard(
                    settings = settingsState,
                    onSignOut = { viewModel.handleSignOut() },
                    onSignInGoogle = { viewModel.handleGoogleSignIn(context) }
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                Text(
                    "CHOOSE YOUR EXPERIENCE",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    ),
                    color = Color(0xFFD4AF37),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )
            }

            // Experience 1: Chat with Tarot Master
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2C1354)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, Color(0xFFD4AF37).copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                        .clickable { onNavToChat() }
                        .testTag("chat_master_card")
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF0F081D)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Forum,
                                contentDescription = "Chat",
                                tint = Color(0xFFD4AF37),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Chat with Tarot Master",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFFD4AF37))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "NEW",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.Black
                                    )
                                }
                            }
                            Text(
                                text = "Interact in real-time with our wise Tarot Master. Ask questions and upload images or videos of your card spreads for detailed analysis.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFB1A2C9),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Go",
                            tint = Color(0xFFD4AF37)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Experience 2: Draw a Virtual Card
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1238)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFD4AF37).copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                        .clickable { onNavToVirtualDraw() }
                        .testTag("virtual_draw_card")
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF0F081D)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Style,
                                contentDescription = "Virtual Draw",
                                tint = Color(0xFFD4AF37),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Draw Virtual Card",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Text(
                                text = "No physical deck handy? Align your thoughts, focus your energy, and draw an interactive, beautifully flipped card from our digital Major Arcana.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFB1A2C9),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Go",
                            tint = Color(0xFFD4AF37)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Experience 3: Scan Physical Card
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A0F2B)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFD4AF37).copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF0F081D)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Scan",
                                    tint = Color(0xFFD4AF37),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Scan Physical Card",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                Text(
                                    text = "Let the AI analyze your physical tarot layout. Select your desired spread pattern to launch the camera.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFFB1A2C9)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { onScanCard("Single Card Draw") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C1354)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Single Card", fontSize = 12.sp, color = Color(0xFFD4AF37), fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { onScanCard("Three Card Spread") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E0E3B)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("3-Card Spread", fontSize = 12.sp, color = Color(0xFFD4AF37), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // History Section
            if (historyState.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "YOUR READING HISTORY",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp
                            ),
                            color = Color(0xFFD4AF37)
                        )
                        TextButton(
                            onClick = { viewModel.clearHistory() },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFE57373))
                        ) {
                            Text("Clear All")
                        }
                    }
                }

                items(historyState) { reading ->
                    HistoryCard(
                        reading = reading,
                        onDelete = { viewModel.deleteReading(reading.id) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF241644)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(0.5.dp, Color(0xFFD4AF37).copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Intuition",
                                tint = Color(0xFFD4AF37)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Seeker's Wisdom",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD4AF37),
                                fontSize = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "The tarot is a mirror to your subconscious. Focus your mind, hold a specific question in your awareness, and let the cosmic patterns speak to you.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFD3C8E6),
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }
    }

    // Oracle Settings & Info Dialog
    if (showSettingsDialog) {
        var offlineState by remember(settingsState.offlineMode) { mutableStateOf(settingsState.offlineMode) }
        var apiKeyInput by remember(settingsState.customApiKey) { mutableStateOf(settingsState.customApiKey) }
        var proxyUrlInput by remember(settingsState.proxyUrl) { mutableStateOf(settingsState.proxyUrl) }

        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Text(
                    "Cosmic Oracle Settings",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFD4AF37)
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Tune your connection to the celestial frequencies and manage your AI oracle keys.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )

                    HorizontalDivider(color = Color(0xFFD4AF37).copy(alpha = 0.2f))

                    // Offline Mode Switch Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF2C1454).copy(alpha = 0.4f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Offline Mode",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                            Text(
                                "Use instant, local readings. No keys or network required.",
                                color = Color(0xFFB1A2C9),
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                        }
                        Switch(
                            checked = offlineState,
                            onCheckedChange = { offlineState = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFD4AF37),
                                checkedTrackColor = Color(0xFF2C1454),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.DarkGray
                            )
                        )
                    }

                    if (!offlineState) {
                        // Custom API Key Field
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Custom Gemini API Key",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD4AF37),
                                fontSize = 13.sp
                            )
                            OutlinedTextField(
                                value = apiKeyInput,
                                onValueChange = { apiKeyInput = it },
                                placeholder = { Text("AIzaSy...", color = Color.Gray) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFFD4AF37),
                                    unfocusedBorderColor = Color(0xFFD4AF37).copy(alpha = 0.3f),
                                    focusedContainerColor = Color(0xFF130A24),
                                    unfocusedContainerColor = Color(0xFF130A24),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                        }

                        // Custom Proxy URL Field
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Custom Proxy Endpoint URL",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD4AF37),
                                fontSize = 13.sp
                            )
                            OutlinedTextField(
                                value = proxyUrlInput,
                                onValueChange = { proxyUrlInput = it },
                                placeholder = { Text("https://...", color = Color.Gray) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFFD4AF37),
                                    unfocusedBorderColor = Color(0xFFD4AF37).copy(alpha = 0.3f),
                                    focusedContainerColor = Color(0xFF130A24),
                                    unfocusedContainerColor = Color(0xFF130A24),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "App Version: 2.1.0-Cosmic\nRoom Database Secure Local Store",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.4f),
                        lineHeight = 16.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveOfflineMode(offlineState)
                        viewModel.saveCustomApiKey(apiKeyInput)
                        viewModel.saveProxyUrl(proxyUrlInput)
                        showSettingsDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4AF37))
                ) {
                    Text("SAVE CHANGES", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSettingsDialog = false }
                ) {
                    Text("CANCEL", color = Color(0xFFB1A2C9), fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF1E0E3B),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.border(1.dp, Color(0xFFD4AF37).copy(alpha = 0.4f), RoundedCornerShape(24.dp))
        )
    }
}

@Composable
fun UserProfileCard(
    settings: com.example.data.TarotSettingsEntity,
    onSignOut: () -> Unit,
    onSignInGoogle: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1033)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFD4AF37).copy(alpha = 0.25f), RoundedCornerShape(20.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Rounded Avatar or Custom Letter Placeholders
                if (settings.signedInPhotoUrl.isNotEmpty()) {
                    AsyncImage(
                        model = settings.signedInPhotoUrl,
                        contentDescription = "Profile Photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .border(1.dp, Color(0xFFD4AF37), CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2C1354))
                            .border(1.dp, Color(0xFFD4AF37), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (settings.isGuest) "G" else if (settings.signedInName.isNotEmpty()) settings.signedInName.take(1).uppercase() else "S",
                            color = Color(0xFFD4AF37),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (settings.isGuest) "Guest Seeker" else if (settings.signedInName.isNotEmpty()) "Welcome, ${settings.signedInName}" else "Welcome, Seeker",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = if (settings.isGuest) "Temporary session (offline-first)" else if (settings.signedInEmail.isNotEmpty()) settings.signedInEmail else "Exploring cosmic paths",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB1A2C9)
                    )
                }

                IconButton(
                    onClick = onSignOut,
                    modifier = Modifier.testTag("signout_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Logout,
                        contentDescription = "Sign Out",
                        tint = Color(0xFFE57373)
                    )
                }
            }

            if (settings.isGuest) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFFD4AF37).copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Sign in to save your history permanently",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF9E8EBB),
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    Button(
                        onClick = onSignInGoogle,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4AF37)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp).testTag("guest_link_google_button")
                    ) {
                        Text("SIGN IN", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun SpreadCard(spread: SpreadOption, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF21113D))
            .border(1.dp, Color(0xFFD4AF37).copy(alpha = 0.2f), RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0F081D)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = spread.icon,
                    contentDescription = spread.title,
                    tint = Color(0xFFD4AF37),
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = spread.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Text(
                    text = spread.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB1A2C9)
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Begin spread",
                tint = Color(0xFFD4AF37)
            )
        }
    }
}

@Composable
fun HistoryCard(
    reading: TarotReadingEntity,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E0E3B)),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, Color(0xFFD4AF37).copy(alpha = 0.15f), RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFFD4AF37),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = reading.cardName,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD4AF37),
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF2C1354))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = reading.orientation,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFB1A2C9)
                        )
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Reading",
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = reading.summary,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = reading.spreadType,
                color = Color(0xFF9E8EBB),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

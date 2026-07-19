package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
    onSelectSpread: (String) -> Unit
) {
    val settingsState by viewModel.settingsState.collectAsState()
    val historyState by viewModel.historyState.collectAsState()

    var showSettingsDialog by remember { mutableStateOf(false) }
    var proxyInputText by remember { mutableStateOf(settingsState.proxyUrl) }

    // Synchronize dialog text input with settings state on load
    LaunchedEffect(settingsState.proxyUrl) {
        proxyInputText = settingsState.proxyUrl
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "MYSTIC LENS",
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        color = Color(0xFFD4AF37) // Gold
                    )
                },
                actions = {
                    // Quick Profile Details & Settings Action
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.testTag("oracle_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Oracle Settings",
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
                UserProfileCard(
                    settings = settingsState,
                    onSignOut = { viewModel.handleSignOut() }
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Mystical glowing icon or element
            item {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2C1354))
                        .border(1.5.dp, Color(0xFFD4AF37), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Cosmic Wisdom",
                        tint = Color(0xFFD4AF37),
                        modifier = Modifier.size(44.dp)
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            item {
                Text(
                    "Align Your Intentions",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp
                    ),
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Text(
                    "Prepare your physical cards, hold a question in your mind, and select your spread to begin.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFFB1A2C9),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp, bottom = 28.dp, start = 8.dp, end = 8.dp)
                )
            }

            // Dynamic Proxy Config Warning/Banner if empty
            if (settingsState.proxyUrl.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF241644)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp)
                            .border(1.dp, Color(0xFFD4AF37).copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .clickable { showSettingsDialog = true }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Shield",
                                tint = Color(0xFFD4AF37),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "No API Proxy Configured",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFD4AF37),
                                    fontSize = 14.sp
                                )
                                Text(
                                    "Tap to set up your secure GCP Cloud Function proxy and hide your API key.",
                                    color = Color(0xFFB1A2C9),
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Go",
                                tint = Color(0xFFD4AF37)
                            )
                        }
                    }
                }
            } else {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF112211)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp)
                            .border(1.dp, Color(0xFF22AA55).copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .clickable { showSettingsDialog = true }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = "Shield Verified",
                                tint = Color(0xFF33CC66),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "GCP API Proxy Active",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF33CC66),
                                    fontSize = 14.sp
                                )
                                Text(
                                    "Requests are securely routed. Your local secrets are completely shielded.",
                                    color = Color(0xFFAABBCC),
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Proxy",
                                tint = Color(0xFF33CC66),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    "CHOOSE A SPREAD",
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

            // Spread Options
            items(spreadOptions) { spread ->
                SpreadCard(spread = spread, onClick = { onSelectSpread(spread.id) })
                Spacer(modifier = Modifier.height(16.dp))
            }

            // History Section
            if (historyState.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
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
                            onClick = { viewModel.clearAllHistory() },
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
                Spacer(modifier = Modifier.height(32.dp))
                // Instruction Banner
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
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Camera Advice",
                                tint = Color(0xFFD4AF37)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "The Perfect Scan",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD4AF37),
                                fontSize = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Place your cards on a flat, well-lit surface. Align the card within the golden frame to capture its full detail for the AI oracle.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFD3C8E6),
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }
    }

    // Proxy Settings Dialog
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Text(
                    "Oracle Settings",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFD4AF37)
                )
            },
            text = {
                Column {
                    Text(
                        "Route queries through a custom GCP Cloud Function proxy to avoid storing API keys inside the client app.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = proxyInputText,
                        onValueChange = { proxyInputText = it },
                        label = { Text("GCP Proxy Endpoint URL", color = Color(0xFFB1A2C9)) },
                        placeholder = { Text("https://us-central1-...", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFD4AF37),
                            unfocusedBorderColor = Color(0xFFB1A2C9)
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("proxy_url_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Leave empty to fall back to the direct Gemini API Client configuration.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveProxyUrl(proxyInputText)
                        showSettingsDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4AF37))
                ) {
                    Text("SAVE CONFIG", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSettingsDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFB1A2C9))
                ) {
                    Text("CANCEL")
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
    onSignOut: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1033)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFD4AF37).copy(alpha = 0.25f), RoundedCornerShape(20.dp))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
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
                        text = if (settings.signedInName.isNotEmpty()) settings.signedInName.take(1).uppercase() else "S",
                        color = Color(0xFFD4AF37),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (settings.signedInName.isNotEmpty()) "Welcome, ${settings.signedInName}" else "Welcome, Seeker",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = if (settings.signedInEmail.isNotEmpty()) settings.signedInEmail else "Exploring cosmic paths",
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

package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.TarotReading
import com.example.ui.theme.NeonBlue
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.WarningRed
import com.example.ui.viewmodel.TarotUIState
import com.example.ui.viewmodel.TarotViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingResultScreen(
    viewModel: TarotViewModel,
    onRestart: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val scannedBitmap by viewModel.scannedBitmap.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cosmic Oracle", fontWeight = FontWeight.Bold, color = Color(0xFFD4AF37)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F081D)
                )
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E0E3B))
                    .padding(WindowInsets.navigationBars.asPaddingValues())
                    .padding(24.dp)
            ) {
                Button(
                    onClick = {
                        viewModel.reset()
                        onRestart()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4AF37)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        "Draw Another Card",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF0F081D), Color(0xFF1E0E3B))
                    )
                )
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            when (val state = uiState) {
                is TarotUIState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFFD4AF37))
                    }
                }
                is TarotUIState.Error -> {
                    ErrorState(message = state.message)
                }
                is TarotUIState.Success -> {
                    ReadingSuccessState(
                        reading = state.reading,
                        bitmap = scannedBitmap
                    )
                }
                else -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No active reading session.", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun OfflineReadingBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("offline_reading_banner")
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF2E2410))
            .border(1.dp, Color(0xFFD4AF37), RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = Color(0xFFD4AF37),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                "OFFLINE SAMPLE READING",
                color = Color(0xFFD4AF37),
                fontWeight = FontWeight.Black,
                fontSize = 12.sp,
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "This is the bundled offline interpretation, not an AI reading. " +
                    "Turn off Offline Mode in Settings for a live Gemini reading.",
                color = Color(0xFFE8DCC0),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun ErrorState(message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("reading_error_state")
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF2E1515))
            .border(1.dp, WarningRed, RoundedCornerShape(16.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = "Error",
            tint = WarningRed,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Cosmic Disconnection",
            fontWeight = FontWeight.Bold,
            color = Color.White,
            fontSize = 18.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            message,
            color = Color(0xFFFFCCCC),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun ReadingSuccessState(reading: TarotReading, bitmap: Bitmap?) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        // An offline/sample reading is NOT the AI's answer. Say so, loudly and
        // above the fold, so it can never be mistaken for a real Gemini reading.
        if (reading.isOffline) {
            OfflineReadingBanner()
        }
        // Scanned Card Preview
        bitmap?.let {
            Card(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .border(2.dp, Color(0xFFD4AF37), RoundedCornerShape(20.dp))
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Your Tarot Card Scan",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Visual scan gradient label overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                                )
                            )
                    )
                    Text(
                        text = "YOUR PHYSICAL DRAW",
                        color = Color(0xFFD4AF37),
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        letterSpacing = 1.5.sp,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                    )
                }
            }
        }

        // Card Title & Orientation Badge
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = reading.cardName,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp
                    ),
                    color = Color(0xFFD4AF37),
                    modifier = Modifier.weight(1f)
                )
                
                // Orientation badge
                val badgeColor = if (reading.orientation == "Upright") NeonGreen else NeonBlue
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeColor.copy(alpha = 0.15f))
                        .border(1.dp, badgeColor, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = reading.orientation.uppercase(),
                        color = badgeColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp
                    )
                }
            }
            
            Text(
                text = reading.summary,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        Divider(color = Color.White.copy(alpha = 0.1f))

        // Meaning Segment
        SectionCard(
            title = "GENERAL INTERPRETATION",
            icon = Icons.Default.MenuBook,
            iconTint = Color(0xFFD4AF37)
        ) {
            Text(
                text = reading.generalMeaning,
                color = Color(0xFFD3C8E6),
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 22.sp
            )
        }

        // Advice Segment
        SectionCard(
            title = "COSMIC ADVICE",
            icon = Icons.Default.Directions,
            iconTint = NeonGreen
        ) {
            Text(
                text = reading.advice,
                color = Color(0xFFD3C8E6),
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 22.sp
            )
        }

        // Warning Segment
        SectionCard(
            title = "PITFALLS TO AVOID",
            icon = Icons.Default.Info,
            iconTint = WarningRed
        ) {
            Text(
                text = reading.warning,
                color = Color(0xFFD3C8E6),
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 22.sp
            )
        }

        // Lucky Elements Segment
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "LUCKY ELEMENTS",
                color = Color(0xFFD4AF37),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                reading.luckyElements.forEach { element ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(30.dp))
                            .background(Color(0xFF2C1354))
                            .border(0.5.dp, Color(0xFFD4AF37).copy(alpha = 0.4f), RoundedCornerShape(30.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = element,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
        
        // Configuration Guidance Banner
        if (BuildConfig.GEMINI_API_KEY == "MY_GEMINI_API_KEY" || BuildConfig.GEMINI_API_KEY.isEmpty()) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF241644)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFD4AF37).copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Cosmic Tip",
                            tint = Color(0xFFD4AF37)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Prototyping Mode Active",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD4AF37),
                            fontSize = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "You are currently viewing a mock reading. To scan and analyze your real-world Tarot cards, configure your 'GEMINI_API_KEY' in the AI Studio Secrets panel on the left sidebar!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFD3C8E6),
                        lineHeight = 20.sp
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
fun SectionCard(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF21113D))
            .border(0.5.dp, Color(0xFFD4AF37).copy(alpha = 0.15f), RoundedCornerShape(16.dp))
            .padding(20.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    color = Color(0xFFD4AF37),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
            content()
        }
    }
}

// Simple FlowRow helper for elements to support simple wrap-around
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    // Standard Column with Row fallback for preview, or nested rows
    Column(modifier = modifier, verticalArrangement = verticalArrangement) {
        Row(horizontalArrangement = horizontalArrangement, modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

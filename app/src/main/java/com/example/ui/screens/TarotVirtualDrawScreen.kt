package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TarotCard
import com.example.ui.viewmodel.TarotUIState
import com.example.ui.viewmodel.TarotViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TarotVirtualDrawScreen(
    viewModel: TarotViewModel,
    onBack: () -> Unit,
    onNavToReading: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val drawnCard by viewModel.drawnCard.collectAsState()
    val drawnCardOrientation by viewModel.drawnCardOrientation.collectAsState()

    val scope = rememberCoroutineScope()
    var selectedCardIndex by remember { mutableStateOf<Int?>(null) }
    var isFlipped by remember { mutableStateOf(false) }
    var isDrawing by remember { mutableStateOf(false) }

    // Twinkling floating background stars state
    val infiniteTransition = rememberInfiniteTransition(label = "starfield_anim")
    val starsPhase1 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "stars_p1"
    )
    val starsPhase2 by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "stars_p2"
    )

    // Gentle magnetic floating offset for the card fan
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fan_float"
    )

    // Automatically navigate to reading outcome when loaded
    LaunchedEffect(uiState) {
        if (isDrawing && (uiState is TarotUIState.Success || uiState is TarotUIState.Error)) {
            // Give 500ms to savor the reveal before sliding to results
            delay(1200)
            onNavToReading()
            isDrawing = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Celestial Sanctuary", fontWeight = FontWeight.Bold, color = Color(0xFFD4AF37)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF0F081D), Color(0xFF1E0E3B))
                    )
                )
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            //Twinkling Starfield canvas
            TwinklingStarfield(starsPhase1, starsPhase2)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 24.dp, horizontal = 16.dp)
            ) {
                // Header details
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (selectedCardIndex == null) "Flicker of Fate" else "Destiny Selected",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = if (selectedCardIndex == null) 
                            "Clear your mind. Hover or gaze over the celestial fan below, feel the cosmic pull, and tap a card to draw your tarot guidance."
                        else 
                            "The celestial veil is parting. Hold your question in your conscious awareness...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFB1A2C9),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp)
                    )
                }

                // Interactive 3D Card Fan Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                        .testTag("virtual_tarot_card_container"),
                    contentAlignment = Alignment.Center
                ) {
                    // We render 5 card options fanned out.
                    val totalCards = 5
                    for (index in 0 until totalCards) {
                        val isSelected = selectedCardIndex == index
                        val hasSelection = selectedCardIndex != null

                        // Compute fanned variables
                        val fannedAngle = (index - 2) * 12f
                        val fannedX = ((index - 2) * 44).dp
                        val fannedY = (if (index == 2) 0 else if (index == 1 || index == 3) 8 else 20).dp

                        // Animate coordinates dynamically based on selection state!
                        val cardAngle by animateFloatAsState(
                            targetValue = if (isSelected) 0f else if (hasSelection) 0f else fannedAngle,
                            animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy)
                        )

                        val cardX by animateDpAsState(
                            targetValue = if (isSelected) 0.dp else if (hasSelection) (if (index < selectedCardIndex!!) (-250).dp else 250.dp) else fannedX,
                            animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy)
                        )

                        val cardY by animateDpAsState(
                            targetValue = if (isSelected) 0.dp else if (hasSelection) 300.dp else (fannedY + floatOffset.dp),
                            animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy)
                        )

                        val cardAlpha by animateFloatAsState(
                            targetValue = if (isSelected) 1.0f else if (hasSelection) 0.0f else 1.0f,
                            animationSpec = tween(durationMillis = 500)
                        )

                        val cardScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.15f else 1.0f,
                            animationSpec = spring(stiffness = Spring.StiffnessLow)
                        )

                        // 3D Flip Y Rotation (only occurs on the selected card!)
                        val rotationY by animateFloatAsState(
                            targetValue = if (isSelected && isFlipped) 180f else 0f,
                            animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
                            label = "CardFlipRotationY"
                        )

                        Box(
                            modifier = Modifier
                                .offset(x = cardX, y = cardY)
                                .graphicsLayer {
                                    this.rotationZ = cardAngle
                                    this.rotationY = rotationY
                                    this.scaleX = cardScale
                                    this.scaleY = cardScale
                                    cameraDistance = 15f * density
                                }
                                .alpha(cardAlpha)
                                .size(width = 200.dp, height = 320.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .clickable(enabled = !hasSelection) {
                                    selectedCardIndex = index
                                    isDrawing = true
                                    scope.launch {
                                        // Wait for the glide glide slide animation
                                        delay(500)
                                        isFlipped = true
                                        viewModel.drawVirtualCard()
                                    }
                                }
                        ) {
                            if (rotationY <= 90f) {
                                // Card Back Design (Mystery & Elegance)
                                CardBackDesign()
                            } else {
                                // Card Front Design (Revealed card content)
                                CardFrontDesign(
                                    card = drawnCard,
                                    orientation = drawnCardOrientation
                                )
                            }
                        }
                    }
                }

                // Interactive Bottom Buttons or progress indicator
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isDrawing) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(color = Color(0xFFD4AF37), modifier = Modifier.size(24.dp))
                                Text(
                                    "Consulting the Stars...",
                                    color = Color(0xFFD4AF37),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    letterSpacing = 0.5.sp
                                )
                            }
                            Text(
                                "Interpreting frequencies. Please remain centered...",
                                color = Color(0xFFB1A2C9),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else if (selectedCardIndex == null) {
                        Button(
                            onClick = {
                                // Automatically draw card index 2 (center) as standard action
                                selectedCardIndex = 2
                                isDrawing = true
                                scope.launch {
                                    delay(500)
                                    isFlipped = true
                                    viewModel.drawVirtualCard()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4AF37)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("draw_card_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color.Black
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "DRAW CELESTIAL CARD",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                selectedCardIndex = null
                                isFlipped = false
                                isDrawing = false
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD4AF37)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD4AF37)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Text(
                                text = "RESET DECK & RE-SHUFFLE",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TwinklingStarfield(p1: Float, p2: Float) {
    val stars = remember {
        List(25) {
            Offset(
                x = (2..98).random() / 100f,
                y = (2..98).random() / 100f
            ) to ((1..3).random().toFloat())
        }
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        stars.forEachIndexed { idx, (pos, starSize) ->
            val alpha = if (idx % 2 == 0) p1 else p2
            drawCircle(
                color = Color(0xFFD4AF37),
                radius = starSize * density,
                center = Offset(pos.x * size.width, pos.y * size.height),
                alpha = alpha
            )
        }
    }
}

@Composable
fun CardBackDesign() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F081D))
            .border(2.dp, Color(0xFFD4AF37), RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.img_tarot_card_back),
            contentDescription = "Cosmic Tarot Card Back",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(20.dp))
        )
        
        // Holographic gold moving shimmer layer on top
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, Color(0xFFD4AF37).copy(alpha = 0.3f), RoundedCornerShape(18.dp))
                .shimmer()
        )
    }
}

@Composable
fun CardFrontDesign(
    card: TarotCard?,
    orientation: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                // Prevent mirrored rendering when flipped 180 degrees
                rotationY = 180f
            }
            .background(Color(0xFF0F081D))
            .border(2.dp, Color(0xFFD4AF37), RoundedCornerShape(20.dp))
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, Color(0xFFD4AF37).copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                .shimmer()
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            if (card != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Header Sign & Element
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = card.astrologicalSign,
                            color = Color(0xFFB1A2C9),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = card.element,
                            color = Color(0xFFB1A2C9),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Card Image/Symbol representation
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(Color(0xFF1E0E3B), RoundedCornerShape(36.dp))
                                .border(1.dp, Color(0xFFD4AF37).copy(alpha = 0.4f), RoundedCornerShape(36.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = card.symbol,
                                fontSize = 38.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = card.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color(0xFFD4AF37),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF2C1354))
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = orientation.uppercase(),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    // Card Keywords footer
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(bottom = 2.dp)
                    ) {
                        Text(
                            text = "KEYWORDS",
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            color = Color(0xFFD4AF37),
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = card.keywords.take(2).joinToString(" • "),
                            fontSize = 10.sp,
                            color = Color(0xFFB1A2C9),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFFD4AF37))
                }
            }
        }
    }
}

@Composable
fun Modifier.shimmer(): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer_modifier")
    val translateAnim by transition.animateFloat(
        initialValue = -600f,
        targetValue = 1600f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "translate"
    )

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            Color.Transparent,
            Color(0xFFD4AF37).copy(alpha = 0.08f),
            Color.White.copy(alpha = 0.18f),
            Color(0xFFD4AF37).copy(alpha = 0.08f),
            Color.Transparent
        ),
        start = Offset(translateAnim, translateAnim),
        end = Offset(translateAnim + 200f, translateAnim + 200f)
    )

    return this.background(shimmerBrush)
}

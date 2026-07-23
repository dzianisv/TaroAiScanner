package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Gold = Color(0xFFD4AF37)
private val DeepPurple = Color(0xFF0F081D)
private val CardPurple = Color(0xFF1E0E3B)

private data class PremiumBenefit(val title: String, val subtitle: String)

private val premiumBenefits = listOf(
    PremiumBenefit("Unlimited Readings", "Draw and scan without limits — the cosmos never sleeps."),
    PremiumBenefit("Detailed Multi-Card Spreads", "Unlock deeper Past-Present-Future and Celtic-style insights."),
    PremiumBenefit("Priority Oracle", "Faster, richer answers from the Tarot Master.")
)

/**
 * Mystic Premium paywall. [priceText] is the localized price from ProductDetails
 * (may be null while loading). [onSubscribe] should launch the billing flow.
 */
@Composable
fun PaywallScreen(
    priceText: String?,
    onSubscribe: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(colors = listOf(DeepPurple, Color(0xFF130A24)))
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFFB1A2C9))
                }
            }

            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2C1354))
                    .border(2.dp, Gold, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Gold,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                "MYSTIC PREMIUM",
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
                fontSize = 26.sp,
                color = Gold
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Unveil the full power of the cosmos.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFB1A2C9)
            )

            Spacer(Modifier.height(28.dp))

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardPurple),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Gold.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    premiumBenefits.forEach { benefit ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Gold,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(
                                    benefit.title,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 15.sp
                                )
                                Text(
                                    benefit.subtitle,
                                    color = Color(0xFFB1A2C9),
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            Text(
                text = priceText?.let { "$it / month" } ?: "Loading price…",
                color = Gold,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onSubscribe,
                colors = ButtonDefaults.buttonColors(containerColor = Gold),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Text(
                    "SUBSCRIBE",
                    color = Color.Black,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp,
                    fontSize = 16.sp
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "Cancel anytime in Google Play. Subscription auto-renews monthly.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.4f),
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

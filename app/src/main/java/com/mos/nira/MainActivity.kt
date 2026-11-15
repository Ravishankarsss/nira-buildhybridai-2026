package com.mos.nira

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mos.nira.ui.theme.NIRATheme

private val Navy = Color(0xFF0B1020)
private val CardColor = Color(0xFF171F35)
private val Violet = Color(0xFF8B7CFF)
private val Cyan = Color(0xFF55D6E8)
private val White = Color(0xFFF5F6FF)
private val Muted = Color(0xFF9BA6C0)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            NIRATheme {
                NIRADashboard()
            }
        }
    }
}

@Composable
fun NIRADashboard(modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        containerColor = Navy,
        bottomBar = { NIRABottomBar() },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Navy)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(modifier = Modifier.height(22.dp))

            // Brand header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "NIRA",
                        color = White,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 3.sp,
                    )
                    Text(
                        text = "TEXTILE INTELLIGENCE",
                        color = Violet,
                        fontSize = 10.sp,
                        letterSpacing = 2.sp,
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = CardColor,
                ) {
                    Text(
                        text = "N",
                        modifier = Modifier.padding(14.dp),
                        color = Cyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Welcome section
            Text(
                text = "GOOD MORNING",
                color = Muted,
                fontSize = 11.sp,
                letterSpacing = 2.sp,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Your creative\nintelligence space.",
                color = White,
                fontSize = 30.sp,
                lineHeight = 38.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Transform garment artwork into textile intelligence.",
                color = Muted,
                fontSize = 14.sp,
                lineHeight = 21.sp,
            )

            Spacer(modifier = Modifier.height(26.dp))

            // Main artwork card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = CardColor,
                ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF302B63),
                                    Color(0xFF242044),
                                    Color(0xFF171F35),
                                ),
                            ),
                        )
                        .padding(22.dp),
                ) {
                    Column {
                        Text(
                            text = "✦  ARTWORK INTELLIGENCE",
                            color = Cyan,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp,
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Discover every\ncolour. Every detail.",
                            color = White,
                            fontSize = 23.sp,
                            lineHeight = 30.sp,
                            fontWeight = FontWeight.Bold,
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Start with your garment artwork.",
                            color = Muted,
                            fontSize = 13.sp,
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = { },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Violet,
                                contentColor = White,
                            ),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("Explore Artwork  →")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            Text(
                text = "INTELLIGENCE WORKSPACE",
                color = Muted,
                fontSize = 11.sp,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Workspace modules
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                WorkspaceCard(
                    symbol = "◈",
                    title = "Colour",
                    subtitle = "Extraction & separation",
                    accent = Violet,
                    modifier = Modifier.weight(1f),
                )
                WorkspaceCard(
                    symbol = "⚗",
                    title = "Recipe Lab",
                    subtitle = "Textile formulation",
                    accent = Cyan,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                WorkspaceCard(
                    symbol = "⌘",
                    title = "CAD Studio",
                    subtitle = "Pattern intelligence",
                    accent = Cyan,
                    modifier = Modifier.weight(1f),
                )
                WorkspaceCard(
                    symbol = "✧",
                    title = "Print AI",
                    subtitle = "Effects & machines",
                    accent = Violet,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(30.dp))

            // Project history
            Text(
                text = "YOUR WORKSPACE",
                color = Muted,
                fontSize = 11.sp,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = CardColor,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                ) {
                    Text(
                        text = "No projects yet",
                        color = White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Your artwork analyses and technical reports will appear here.",
                        color = Muted,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "NIRA  •  Intelligent Textile Creation",
                color = Muted,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun WorkspaceCard(
    symbol: String,
    title: String,
    subtitle: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.height(145.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardColor,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(15.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = symbol,
                color = accent,
                fontSize = 27.sp,
                fontWeight = FontWeight.Bold,
            )

            Column {
                Text(
                    text = title,
                    color = White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = Muted,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
fun NIRABottomBar(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color(0xFF11182A),
        tonalElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomItem("⌂", "Home", true)
            BottomItem("◈", "Artwork", false)
            BottomItem("⚗", "Lab", false)
            BottomItem("◎", "Profile", false)
        }
    }
}

@Composable
fun BottomItem(
    symbol: String,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = symbol,
            color = if (selected) Violet else Muted,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            color = if (selected) Violet else Muted,
            fontSize = 10.sp,
        )
    }
}

@Preview(showBackground = true)
@Composable
fun NIRADashboardPreview() {
    NIRATheme {
        NIRADashboard()
    }
}

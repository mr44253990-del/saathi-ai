package com.saathi.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saathi.ai.live.Phase
import com.saathi.ai.ui.theme.JimiThemeSpec
import com.saathi.ai.ui.theme.JimiThemes
import com.saathi.ai.ui.theme.LocalJimi
import com.saathi.ai.ui.theme.SaathiTheme
import com.saathi.ai.ui.theme.jimi

/**
 * Theme gallery. Each tile previews the real orb with that theme's palette, so the
 * choice is visual rather than a list of hex codes.
 */
@Composable
fun ThemePickerScreen(
    current: String,
    onPick: (String) -> Unit,
    onBack: () -> Unit
) {
    SaathiBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        SaathiIcons.ArrowBack, "ফিরে যাও",
                        tint = jimi.textPrimary, modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(Modifier.size(12.dp))
                Column {
                    Text("থিম", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = jimi.textPrimary)
                    Text(
                        "পছন্দমতো চেহারা বদলাও — সাথে সাথে পুরো অ্যাপ বদলে যাবে",
                        fontSize = 11.sp, color = jimi.textMuted
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(JimiThemes.all) { spec ->
                    ThemeTile(
                        spec = spec,
                        selected = spec.id == current,
                        onClick = { onPick(spec.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeTile(spec: JimiThemeSpec, selected: Boolean, onClick: () -> Unit) {
    val p = spec.palette
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.86f)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(listOf(p.bgTop, p.bgMid, p.bgBottom))
            )
            .border(
                width = if (selected) 2.dp else 1.dp,
                brush = if (selected) {
                    Brush.linearGradient(listOf(p.primary, p.secondary))
                } else {
                    Brush.linearGradient(listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.04f)))
                },
                shape = RoundedCornerShape(24.dp)
            )
            .clickable { onClick() }
    ) {
        // mini orb preview rendered with the *target* theme
        SaathiTheme(spec = spec) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    JimiOrb(phase = Phase.LISTENING, level = 0.35f, modifier = Modifier.size(104.dp))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        spec.emoji,
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.size(7.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            spec.name,
                            color = p.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(spec.bangla, color = p.textMuted, fontSize = 10.sp)
                    }
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(p.primary, p.secondary))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                SaathiIcons.Check, "নির্বাচিত",
                                tint = Color.White, modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

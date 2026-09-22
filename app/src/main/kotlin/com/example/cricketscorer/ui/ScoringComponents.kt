package com.example.cricketscorer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cricketscorer.*

@Composable
fun TeamColorPicker(
    selectedColorHex: String?,
    otherTeamColorsHex: List<String>,
    onColorSelected: (String?) -> Unit
) {
    Column {
        Text(
            "Team Color",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        // Multi-line wrap flow for chips
        BoxWithConstraints {
            val scope = this
            val chipSize = 44.dp
            val spacing = 8.dp
            val columns = (scope.maxWidth / (chipSize + spacing)).toInt().coerceAtLeast(1)
            
            Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
                TEAM_PALETTE.chunked(columns).forEach { rowColors ->
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                        rowColors.forEach { hex ->
                            val isSelected = selectedColorHex?.equals(hex, ignoreCase = true) == true
                            val isDisabled = otherTeamColorsHex.any { it.equals(hex, ignoreCase = true) }
                            
                            ColorChip(
                                hex = hex,
                                isSelected = isSelected,
                                isDisabled = isDisabled,
                                onClick = { if (!isDisabled) onColorSelected(hex) }
                            )
                        }
                    }
                }
                
                TextButton(
                    onClick = { onColorSelected(null) },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Clear (Default)", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
fun ColorChip(
    hex: String,
    isSelected: Boolean,
    isDisabled: Boolean,
    onClick: () -> Unit
) {
    val color = remember(hex) { Color(android.graphics.Color.parseColor(hex)) }
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val borderThickness = if (isSelected) 3.dp else 1.dp
    
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(
                if (isDisabled) color.copy(alpha = 0.2f) else color,
                CircleShape
            )
            .border(
                borderThickness,
                if (isSelected) borderColor else Color.Black.copy(alpha = 0.1f),
                CircleShape
            )
            .clickable(enabled = !isDisabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        if (isDisabled) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.5f), CircleShape)
            )
        }
    }
}

@Composable
fun CricketBallIcon(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(12.dp),
        shape = CircleShape,
        color = Color(0xFFC62828), // Cricket Red
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth().height(1.dp),
                color = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun RunButton(
    runs: Int,
    modifier: Modifier = Modifier,
    label: String? = null,
    containerColor: Color? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.aspectRatio(1f),
        contentPadding = PaddingValues(0.dp),
        shape = CircleShape,
        colors = if (containerColor != null) ButtonDefaults.filledTonalButtonColors(containerColor = containerColor) else ButtonDefaults.filledTonalButtonColors()
    ) {
        Text(label ?: "$runs", fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ExtraButton(label: String, type: ExtrasType, viewModel: ScoringViewModel, onShowExtraRuns: (ExtrasType) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = { 
            if (type == ExtrasType.WIDE) {
                viewModel.handleExtra(ExtrasType.WIDE, 0)
            } else {
                onShowExtraRuns(type)
            }
        },
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun BallBox(ball: Ball, onClick: () -> Unit) {
    val bgColor = when {
        ball.wicketType != WicketType.NONE && ball.wicketType != WicketType.RETIRED_HURT -> Color(0xFFD32F2F) // Wicket -> Red
        ball.extrasType == ExtrasType.WIDE || ball.extrasType == ExtrasType.NO_BALL -> Color(0xFFF57C00) // Wide/NB -> Orange
        ball.runs == 6 -> Color(0xFF7B1FA2) // 6 -> Purple
        ball.runs == 4 -> Color(0xFF1976D2) // 4 -> Blue
        else -> Color(0xFFE0E0E0) // Others -> Light Grey
    }
    
    val textColor = if (bgColor == Color(0xFFE0E0E0)) Color.Black else Color.White
    
    val text = when {
        ball.wicketType == WicketType.RETIRED_HURT -> "RH"
        ball.wicketType != WicketType.NONE -> "W"
        ball.isDroppedCatch -> "🤲${ball.runs}"
        ball.extrasType == ExtrasType.WIDE -> "${ball.extraRuns}wd"
        ball.extrasType == ExtrasType.NO_BALL -> {
            val total = ball.runs + ball.extraRuns
            if (total > 0) "${total}nb" else "nb"
        }
        ball.extrasType == ExtrasType.BYE -> "${ball.extraRuns}b"
        ball.extrasType == ExtrasType.LEG_BYE -> "${ball.extraRuns}lb"
        ball.extrasType == ExtrasType.GRANTED -> "${ball.runs}G"
        else -> if (ball.runs == 0) "•" else "${ball.runs}"
    }

    Box(
        modifier = Modifier
            .size(32.dp)
            .background(bgColor, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text, 
            color = textColor, 
            fontWeight = FontWeight.Black, 
            fontSize = if (text.length > 2) 9.sp else 11.sp,
            textAlign = TextAlign.Center
        )
    }
}

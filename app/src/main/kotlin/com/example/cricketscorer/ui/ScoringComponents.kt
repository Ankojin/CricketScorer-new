package com.example.cricketscorer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cricketscorer.*

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
        ball.wicketType != WicketType.NONE && ball.wicketType != WicketType.RETIRED_HURT -> Color(0xFFFFEBEE)
        ball.isDroppedCatch -> Color(0xFFFFF3E0)
        ball.extrasType != ExtrasType.NONE -> Color(0xFFFFF3E0)
        else -> Color.White
    }
    val textColor = when {
        ball.wicketType != WicketType.NONE && ball.wicketType != WicketType.RETIRED_HURT -> Color.Red
        ball.isDroppedCatch -> Color(0xFFFF9800)
        ball.extrasType != ExtrasType.NONE -> Color(0xFFE65100)
        else -> Color.Black
    }
    val text = when {
        ball.wicketType == WicketType.RETIRED_HURT -> "RH"
        ball.wicketType != WicketType.NONE -> "W"
        ball.isDroppedCatch -> "🤲${ball.runs}"
        ball.extrasType == ExtrasType.WIDE -> "${ball.extraRuns}wd"
        ball.extrasType == ExtrasType.NO_BALL -> "${ball.runs + ball.extraRuns}nb"
        ball.extrasType == ExtrasType.BYE -> "${ball.extraRuns}b"
        ball.extrasType == ExtrasType.LEG_BYE -> "${ball.extraRuns}lb"
        ball.extrasType == ExtrasType.GRANTED -> "${ball.runs}G"
        else -> "${ball.runs}"
    }

    Box(
        modifier = Modifier
            .size(36.dp)
            .background(bgColor, CircleShape)
            .border(1.dp, Color.LightGray, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = textColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

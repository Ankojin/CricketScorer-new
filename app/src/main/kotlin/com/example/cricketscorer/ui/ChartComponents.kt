package com.example.cricketscorer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cricketscorer.Ball
import com.example.cricketscorer.Match
import com.example.cricketscorer.WicketType
import java.util.Locale

data class ChartPoint(
    val over: Float,
    val cumulativeRuns: Int,
    val isWicket: Boolean
)

data class OverData(
    val overIndex: Int,
    val runs: Int,
    val wickets: Int
)

fun buildChartPoints(balls: List<Ball>): List<ChartPoint> {
    val points = mutableListOf<ChartPoint>()
    points.add(ChartPoint(0f, 0, false))
    var currentRuns = 0
    var physicalBallCount = 0
    balls.forEach { ball ->
        if (ball.isAdjustment) return@forEach
        currentRuns += ball.runs + ball.extraRuns
        val isWicket = ball.wicketType != WicketType.NONE && ball.wicketType != WicketType.RETIRED_HURT
        if (ball.isPhysicalBall) {
            physicalBallCount++
        }
        points.add(ChartPoint(physicalBallCount / 6f, currentRuns, isWicket))
    }
    return points
}

fun buildOverData(balls: List<Ball>): List<OverData> {
    val overMap = mutableMapOf<Int, OverData>()
    var physicalBallCount = 0
    balls.forEach { ball ->
        if (ball.isAdjustment) return@forEach
        val currentOver = physicalBallCount / 6
        val data = overMap.getOrPut(currentOver) { OverData(currentOver + 1, 0, 0) }
        val isWicket = ball.wicketType != WicketType.NONE && ball.wicketType != WicketType.RETIRED_HURT
        
        if (ball.isPhysicalBall) {
            physicalBallCount++
        }
        
        overMap[currentOver] = data.copy(
            runs = data.runs + ball.runs + ball.extraRuns,
            wickets = data.wickets + (if (isWicket) 1 else 0)
        )
    }
    return overMap.values.sortedBy { it.overIndex }
}

@Composable
fun ProgressChartCard(
    match: Match,
    i1Balls: List<Ball>,
    i2Balls: List<Ball>,
    i1TeamName: String,
    i2TeamName: String
) {
    val i1Points = buildChartPoints(i1Balls)
    val i2Points = buildChartPoints(i2Balls)

    val i1TotalRuns = i1Balls.sumOf { it.runs + it.extraRuns }
    val i1TotalWickets = i1Balls.count { it.wicketType != WicketType.NONE && it.wicketType != WicketType.RETIRED_HURT }
    val i2TotalRuns = i2Balls.sumOf { it.runs + it.extraRuns }
    val i2TotalWickets = i2Balls.count { it.wicketType != WicketType.NONE && it.wicketType != WicketType.RETIRED_HURT }

    val hasActiveSecondInnings = match.isSecondInningsStarted && i2Balls.isNotEmpty()

    val team1Color = Color(0xFFE53935)
    val team2Color = Color(0xFF1E88E5)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "PROGRESS",
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                color = Color(0xFF003366)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.width(16.dp).height(3.dp).background(team1Color))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$i1TeamName • $i1TotalRuns/$i1TotalWickets",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.DarkGray
                    )
                }
                if (hasActiveSecondInnings) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.width(16.dp).height(3.dp).background(team2Color))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "$i2TeamName • $i2TotalRuns/$i2TotalWickets",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.DarkGray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val textMeasurer = rememberTextMeasurer()
            val density = LocalDensity.current

            val paddingLeft = with(density) { 36.dp.toPx() }
            val paddingRight = with(density) { 16.dp.toPx() }
            val paddingTop = with(density) { 16.dp.toPx() }
            val paddingBottom = with(density) { 24.dp.toPx() }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                val chartWidth = size.width - paddingLeft - paddingRight
                val chartHeight = size.height - paddingTop - paddingBottom

                val maxOverData = maxOf(
                    i1Points.maxOfOrNull { it.over } ?: 0f,
                    if (hasActiveSecondInnings) (i2Points.maxOfOrNull { it.over } ?: 0f) else 0f
                )
                val totalOvers = maxOf(match.oversPerInnings.toFloat(), maxOverData, 1f)

                val maxRunsData = maxOf(
                    i1Points.maxOfOrNull { it.cumulativeRuns } ?: 0,
                    if (hasActiveSecondInnings) (i2Points.maxOfOrNull { it.cumulativeRuns } ?: 0) else 0
                )
                // Headroom padding of 10%
                val maxRuns = maxOf((maxRunsData * 1.1f).toInt(), 20)
                val maxRunsAxis = ((maxRuns + 19) / 20) * 20

                // Horizontal Grid & Y-Axis Labels
                val intervals = 4
                for (i in 0..intervals) {
                    val runVal = (maxRunsAxis * i) / intervals
                    val y = paddingTop + chartHeight - (runVal.toFloat() / maxRunsAxis) * chartHeight
                    drawLine(
                        color = Color.LightGray.copy(alpha = 0.4f),
                        start = Offset(paddingLeft, y),
                        end = Offset(paddingLeft + chartWidth, y),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawText(
                        textMeasurer = textMeasurer,
                        text = runVal.toString(),
                        style = TextStyle(color = Color.Gray, fontSize = 10.sp),
                        topLeft = Offset(paddingLeft - 30.dp.toPx(), y - 7.dp.toPx())
                    )
                }

                // Vertical Grid & X-Axis Labels
                val overStep = if (totalOvers <= 5f) 1 else if (totalOvers <= 10f) 2 else 5
                var o = 0
                while (o <= totalOvers) {
                    val x = paddingLeft + (o.toFloat() / totalOvers) * chartWidth
                    drawLine(
                        color = Color.LightGray.copy(alpha = 0.2f),
                        start = Offset(x, paddingTop),
                        end = Offset(x, paddingTop + chartHeight),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawText(
                        textMeasurer = textMeasurer,
                        text = o.toString(),
                        style = TextStyle(color = Color.Gray, fontSize = 10.sp),
                        topLeft = Offset(x - 5.dp.toPx(), paddingTop + chartHeight + 4.dp.toPx())
                    )
                    o += overStep
                }

                // Draw Innings 1 Line
                if (i1Points.isNotEmpty()) {
                    val path = Path()
                    i1Points.forEachIndexed { index, point ->
                        val x = paddingLeft + (point.over / totalOvers) * chartWidth
                        val y = paddingTop + chartHeight - (point.cumulativeRuns.toFloat() / maxRunsAxis) * chartHeight
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, color = team1Color, style = Stroke(width = 2.5f.dp.toPx(), cap = StrokeCap.Round))

                    i1Points.forEach { point ->
                        if (point.isWicket) {
                            val x = paddingLeft + (point.over / totalOvers) * chartWidth
                            val y = paddingTop + chartHeight - (point.cumulativeRuns.toFloat() / maxRunsAxis) * chartHeight
                            drawCircle(color = Color.White, radius = 5.dp.toPx(), center = Offset(x, y))
                            drawCircle(color = team1Color, radius = 3.dp.toPx(), center = Offset(x, y))
                        }
                    }
                }

                // Draw Innings 2 Line
                if (hasActiveSecondInnings && i2Points.isNotEmpty()) {
                    val path = Path()
                    i2Points.forEachIndexed { index, point ->
                        val x = paddingLeft + (point.over / totalOvers) * chartWidth
                        val y = paddingTop + chartHeight - (point.cumulativeRuns.toFloat() / maxRunsAxis) * chartHeight
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, color = team2Color, style = Stroke(width = 2.5f.dp.toPx(), cap = StrokeCap.Round))

                    i2Points.forEach { point ->
                        if (point.isWicket) {
                            val x = paddingLeft + (point.over / totalOvers) * chartWidth
                            val y = paddingTop + chartHeight - (point.cumulativeRuns.toFloat() / maxRunsAxis) * chartHeight
                            drawCircle(color = Color.White, radius = 5.dp.toPx(), center = Offset(x, y))
                            drawCircle(color = team2Color, radius = 3.dp.toPx(), center = Offset(x, y))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OverByOverChartCard(
    match: Match,
    i1Balls: List<Ball>,
    i2Balls: List<Ball>,
    i1TeamName: String,
    i2TeamName: String
) {
    val i1Overs = buildOverData(i1Balls)
    val i2Overs = buildOverData(i2Balls)

    val hasActiveSecondInnings = match.isSecondInningsStarted && i2Balls.isNotEmpty()

    val team1Color = Color(0xFFE53935)
    val team2Color = Color(0xFF1E88E5)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "OVER BY OVER",
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                color = Color(0xFF003366)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(12.dp).background(team1Color, RoundedCornerShape(2.dp)))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = i1TeamName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.DarkGray
                    )
                }
                if (hasActiveSecondInnings) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(12.dp).background(team2Color, RoundedCornerShape(2.dp)))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = i2TeamName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.DarkGray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val textMeasurer = rememberTextMeasurer()
            val density = LocalDensity.current

            val paddingLeft = with(density) { 36.dp.toPx() }
            val paddingRight = with(density) { 16.dp.toPx() }
            val paddingTop = with(density) { 16.dp.toPx() }
            val paddingBottom = with(density) { 24.dp.toPx() }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                val chartWidth = size.width - paddingLeft - paddingRight
                val chartHeight = size.height - paddingTop - paddingBottom

                val maxOverIdx = maxOf(
                    i1Overs.maxOfOrNull { it.overIndex } ?: 0,
                    if (hasActiveSecondInnings) (i2Overs.maxOfOrNull { it.overIndex } ?: 0) else 0,
                    match.oversPerInnings
                )
                val totalOversCount = maxOf(maxOverIdx, 1)

                val maxOverRuns = maxOf(
                    i1Overs.maxOfOrNull { it.runs } ?: 0,
                    if (hasActiveSecondInnings) (i2Overs.maxOfOrNull { it.runs } ?: 0) else 0
                )
                // Headroom padding of 10%
                val maxOverRunsAxis = maxOf((((maxOverRuns * 1.1f).toInt() + 5) / 6) * 6, 6)

                // Horizontal Grid & Y-Axis Labels
                val intervals = 3
                for (i in 0..intervals) {
                    val runVal = (maxOverRunsAxis * i) / intervals
                    val y = paddingTop + chartHeight - (runVal.toFloat() / maxOverRunsAxis) * chartHeight
                    drawLine(
                        color = Color.LightGray.copy(alpha = 0.4f),
                        start = Offset(paddingLeft, y),
                        end = Offset(paddingLeft + chartWidth, y),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawText(
                        textMeasurer = textMeasurer,
                        text = runVal.toString(),
                        style = TextStyle(color = Color.Gray, fontSize = 10.sp),
                        topLeft = Offset(paddingLeft - 30.dp.toPx(), y - 7.dp.toPx())
                    )
                }

                val segmentWidth = chartWidth / totalOversCount
                val groupPadding = segmentWidth * 0.12f
                val availableWidth = segmentWidth - (groupPadding * 2)
                val barWidth = if (hasActiveSecondInnings) availableWidth / 2f else availableWidth

                // Draw Innings 1 Bars
                i1Overs.forEach { overData ->
                    val idx = overData.overIndex - 1
                    if (idx < totalOversCount) {
                        val segmentLeft = paddingLeft + idx * segmentWidth
                        val barLeft = segmentLeft + groupPadding
                        val barHeightPx = (overData.runs.toFloat() / maxOverRunsAxis) * chartHeight
                        val barTop = paddingTop + chartHeight - barHeightPx

                        if (barHeightPx > 0) {
                            drawRect(
                                color = team1Color,
                                topLeft = Offset(barLeft, barTop),
                                size = Size(barWidth, barHeightPx)
                            )
                        }

                        if (overData.wickets > 0) {
                            val markerX = barLeft + barWidth / 2f
                            val markerY = barTop - 6.dp.toPx()
                            drawCircle(color = Color.White, radius = 4.5.dp.toPx(), center = Offset(markerX, markerY))
                            drawCircle(color = team1Color, radius = 2.5f.dp.toPx(), center = Offset(markerX, markerY))
                        }
                    }
                }

                // Draw Innings 2 Bars
                if (hasActiveSecondInnings) {
                    i2Overs.forEach { overData ->
                        val idx = overData.overIndex - 1
                        if (idx < totalOversCount) {
                            val segmentLeft = paddingLeft + idx * segmentWidth
                            val barLeft = segmentLeft + groupPadding + barWidth
                            val barHeightPx = (overData.runs.toFloat() / maxOverRunsAxis) * chartHeight
                            val barTop = paddingTop + chartHeight - barHeightPx

                            if (barHeightPx > 0) {
                                drawRect(
                                    color = team2Color,
                                    topLeft = Offset(barLeft, barTop),
                                    size = Size(barWidth, barHeightPx)
                                )
                            }

                            if (overData.wickets > 0) {
                                val markerX = barLeft + barWidth / 2f
                                val markerY = barTop - 6.dp.toPx()
                                drawCircle(color = Color.White, radius = 4.5.dp.toPx(), center = Offset(markerX, markerY))
                                drawCircle(color = team2Color, radius = 2.5f.dp.toPx(), center = Offset(markerX, markerY))
                            }
                        }
                    }
                }

                // X-Axis Labels
                val xLabelStep = if (totalOversCount <= 6) 1 else if (totalOversCount <= 12) 2 else 5
                for (idx in 0 until totalOversCount) {
                    val overNum = idx + 1
                    if (overNum == 1 || overNum % xLabelStep == 0 || overNum == totalOversCount) {
                        val segmentLeft = paddingLeft + idx * segmentWidth
                        val centerX = segmentLeft + segmentWidth / 2f
                        drawText(
                            textMeasurer = textMeasurer,
                            text = overNum.toString(),
                            style = TextStyle(color = Color.Gray, fontSize = 10.sp),
                            topLeft = Offset(centerX - 4.dp.toPx(), paddingTop + chartHeight + 4.dp.toPx())
                        )
                    }
                }
            }
        }
    }
}

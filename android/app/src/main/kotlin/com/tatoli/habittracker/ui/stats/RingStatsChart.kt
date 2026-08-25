package com.tatoli.habittracker.ui.stats

import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.cos
import kotlin.math.sin

// Geometrie 1:1 aus habit-tracker/index.html (circleSVG): Ringe = Habits,
// Sektoren = Tage/KWs auf einem offenen 290°-Bogen; die Öffnung oben links
// geht in einen waagerechten "Schwanz" mit je einer Zeile pro Ring über.
private const val VIEWPORT_W = 360f
private const val VIEWPORT_H = 336f
private const val CX = 206f
private const val CY = 186f
private const val OUTER = 122f
private const val INNER = 46f
private const val SWEEP_DEG = 290f
private const val TAIL_LEFT = CX - 150f
private const val OFF_ALPHA = 0.35f
private const val GAP_DEG = 0.6f
private const val RADIAL_INSET = 0.8f
private const val NAME_SPAN_DEG = 70f

@Composable
fun RingStatsChart(data: RingChartData, modifier: Modifier = Modifier) {
    val ringCount = data.ringNames.size
    val sectorCount = data.sectorLabels.size
    if (ringCount == 0 || sectorCount == 0) return
    if (data.ringColors.size < ringCount || data.states.size < ringCount) return
    if (data.states.any { it.size < sectorCount }) return
    val ringWidth = (OUTER - INNER) / ringCount
    val perSector = SWEEP_DEG / sectorCount

    Canvas(modifier = modifier.fillMaxWidth().aspectRatio(VIEWPORT_W / VIEWPORT_H)) {
        val scale = size.width / VIEWPORT_W
        val cx = CX * scale
        val cy = CY * scale
        val nativeCanvas = drawContext.canvas.nativeCanvas

        val tailNamePaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.parseColor("#EDF4F0")
            textSize = 11f * scale
            isFakeBoldText = true
        }
        val namePaint = Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            color = android.graphics.Color.parseColor("#EDF4F0")
            isFakeBoldText = true
            setShadowLayer(2.6f * scale, 0f, 0f, android.graphics.Color.argb(200, 10, 28, 30))
        }
        val outerLabelPaint = Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            textSize = 10f * scale
        }

        // 1. Schwanz (liegt wie im Web unter den Ringsegmenten)
        for (ringIndex in 0 until ringCount) {
            val yTop = cy - (INNER + (ringIndex + 1) * ringWidth - RADIAL_INSET) * scale
            val yBot = cy - (INNER + ringIndex * ringWidth + RADIAL_INSET) * scale
            val midY = (yTop + yBot) / 2
            val height = yBot - yTop
            val ringColor = parseRingColor(data.ringColors[ringIndex])

            drawRect(
                color = Color.White.copy(alpha = 0.06f),
                topLeft = Offset(TAIL_LEFT * scale, yTop),
                size = Size((CX - TAIL_LEFT) * scale, height)
            )
            drawRect(
                color = ringColor,
                topLeft = Offset(TAIL_LEFT * scale, yTop),
                size = Size(5f * scale, height)
            )
            nativeCanvas.drawText(
                truncateLabel(data.ringNames[ringIndex], 20),
                (TAIL_LEFT + 12f) * scale,
                midY + 3.6f * scale,
                tailNamePaint
            )
        }

        // 2. Ringsegmente
        for (ringIndex in 0 until ringCount) {
            val midRadius = (INNER + (ringIndex + 0.5f) * ringWidth) * scale
            val strokeWidth = (ringWidth - 2 * RADIAL_INSET) * scale
            val ringColor = parseRingColor(data.ringColors[ringIndex])
            for (sectorIndex in 0 until sectorCount) {
                val a0 = sectorIndex * perSector
                val a1 = (sectorIndex + 1) * perSector
                // Erster Sektor schließt bündig an den Schwanz an (keine Lücke bei 12 Uhr)
                val startDeg = if (sectorIndex == 0) a0 else a0 + GAP_DEG
                val sweepDeg = (a1 - GAP_DEG) - startDeg
                val state = data.states[ringIndex][sectorIndex]
                val fillColor = if (state == DayState.DONE) ringColor else Color.White.copy(alpha = 0.06f)
                val alpha = if (state == DayState.OFF) OFF_ALPHA else 1f
                drawArc(
                    color = fillColor,
                    startAngle = startDeg - 90f, // 0° = oben (wie polar() im Web)
                    sweepAngle = sweepDeg,
                    useCenter = false,
                    topLeft = Offset(cx - midRadius, cy - midRadius),
                    size = Size(midRadius * 2, midRadius * 2),
                    style = Stroke(width = strokeWidth),
                    alpha = alpha
                )
            }
        }

        // 3. Habit-Name entlang der Bogenmitte je Ring
        val midAngle = SWEEP_DEG / 2
        val lowerHalf = midAngle > 90 && midAngle < 270 // dort Text sonst kopfüber
        for (ringIndex in 0 until ringCount) {
            val mid = (INNER + (ringIndex + 0.5f) * ringWidth) * scale
            val aA = midAngle - NAME_SPAN_DEG / 2
            val aB = midAngle + NAME_SPAN_DEG / 2
            val startA = if (lowerHalf) aB else aA
            val endA = if (lowerHalf) aA else aB
            val path = Path()
            path.addArc(
                cx - mid, cy - mid, cx + mid, cy + mid,
                startA - 90f,
                endA - startA // im unteren Halbkreis negativ = gegen den Uhrzeigersinn
            )
            namePaint.textSize = (ringWidth * 0.6f).coerceIn(9f, 12f) * scale
            nativeCanvas.drawTextOnPath(
                truncateLabel(data.ringNames[ringIndex], 20),
                path,
                0f,
                0f,
                namePaint
            )
        }

        // 4. Tages-/KW-Zahlen außen
        data.sectorLabels.forEachIndexed { sectorIndex, label ->
            val a0 = sectorIndex * perSector
            val a1 = (sectorIndex + 1) * perSector
            val mid = (a0 + a1) / 2
            val point = polar(cx, cy, (OUTER + 9f) * scale, mid)
            outerLabelPaint.color = if (sectorIndex == data.highlightSectorIndex) {
                android.graphics.Color.parseColor("#F2B450")
            } else {
                android.graphics.Color.parseColor("#EDF4F0")
            }
            outerLabelPaint.isFakeBoldText = sectorIndex == data.highlightSectorIndex
            nativeCanvas.drawText(label, point.x, point.y + 3f * scale, outerLabelPaint)
        }
    }
}

private fun polar(cx: Float, cy: Float, r: Float, deg: Float): Offset {
    val angleRad = Math.toRadians((deg - 90).toDouble())
    return Offset(cx + r * cos(angleRad).toFloat(), cy + r * sin(angleRad).toFloat())
}

private fun truncateLabel(s: String, n: Int): String = if (s.length > n) s.take(n - 1) + "…" else s

private fun parseRingColor(hex: String): Color = Color(android.graphics.Color.parseColor(hex))

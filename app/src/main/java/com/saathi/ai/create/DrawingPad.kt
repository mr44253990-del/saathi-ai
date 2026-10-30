package com.saathi.ai.create

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.saathi.ai.ui.SaathiBackground
import com.saathi.ai.ui.theme.jimi

/**
 * A real canvas inside Jimi.
 *
 * Why it exists: several tasks the user asks for ("একটা ছবি আঁকো", "৩ডি ডিজাইন
 * করো", "বোর্ডে চাল দেখাও") are not about another app — Jimi should be able to
 * draw the answer itself. Ask for a drawing, say the strokes out loud, and Jimi
 * renders them here, then shares or saves the PNG.
 *
 * [renderStrokes] also lets the model drive the canvas: it returns a plain list of
 * polylines with colours, which are drawn on top of whatever the user painted.
 */
class SketchView(context: Context) : View(context) {

    data class Stroke(val points: List<Pair<Float, Float>>, val color: Int, val width: Float)

    private val strokes = mutableListOf<Stroke>()
    private var current: MutableList<Pair<Float, Float>>? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    var inkColor: Int = android.graphics.Color.WHITE
    var inkWidth: Float = 8f

    /** A soft gradient wash behind the drawing so white ink stays visible. */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bg = Paint().apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(0xFF0B1024.toInt(), 0xFF161033.toInt(), 0xFF06131F.toInt()),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bg)

        for (s in strokes) drawStroke(canvas, s)
        current?.let {
            if (it.size > 1) {
                drawStroke(canvas, Stroke(it.toList(), inkColor, inkWidth))
            }
        }
    }

    private fun drawStroke(canvas: Canvas, stroke: Stroke) {
        if (stroke.points.size < 2) return
        paint.color = stroke.color
        paint.strokeWidth = stroke.width
        val path = Path()
        path.moveTo(stroke.points[0].first, stroke.points[0].second)
        for (i in 1 until stroke.points.size) {
            val (x, y) = stroke.points[i]
            path.lineTo(x, y)
        }
        canvas.drawPath(path, paint)
    }

    fun begin(x: Float, y: Float) {
        current = mutableListOf(Pair(x, y))
        invalidate()
    }

    fun extend(x: Float, y: Float) {
        current?.add(Pair(x, y))
        invalidate()
    }

    fun end() {
        current?.let {
            if (it.size > 1) strokes.add(Stroke(it.toList(), inkColor, inkWidth))
        }
        current = null
        invalidate()
    }

    fun clear() {
        strokes.clear()
        current = null
        invalidate()
    }

    fun undo() {
        if (strokes.isNotEmpty()) strokes.removeAt(strokes.size - 1)
        invalidate()
    }

    /** Drawn by Jimi itself — a list of polylines. */
    fun renderStrokes(list: List<Stroke>) {
        strokes.addAll(list)
        invalidate()
    }

    fun export(): Bitmap {
        val bmp = Bitmap.createBitmap(
            width.coerceAtLeast(1),
            height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        draw(Canvas(bmp))
        return bmp
    }
}

@Composable
fun DrawingScreen(onBack: () -> Unit, onShare: (Bitmap) -> Unit) {
    var colorIndex by remember { mutableStateOf(0) }
    val thick = 8f
    val palette = listOf(
        Color(0xFFFFFFFF), Color(0xFFFF7AC6), 0xFF35D6C8.toInt(), 0xFFFFC46B.toInt(),
        0xFF7C5CFF.toInt(), 0xFF66E27A.toInt(), 0xFFFF6B81.toInt()
    )
    val viewRef = remember { arrayOfNulls<SketchView>(1) }

    SaathiBackground {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("আঁকা", color = jimi.textPrimary, fontSize = 20.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    "ফিরে যাও",
                    color = jimi.secondary,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .padding(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable { onBack() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
            ) {
                AndroidView(
                    factory = { ctx ->
                        SketchView(ctx).also { v ->
                            v.inkColor = paletteArgb[colorIndex]
                            v.inkWidth = thick
                            v.setOnTouchListener { _, e ->
                                when (e.action) {
                                    android.view.MotionEvent.ACTION_DOWN -> v.begin(e.x, e.y)
                                    android.view.MotionEvent.ACTION_MOVE -> v.extend(e.x, e.y)
                                    android.view.MotionEvent.ACTION_UP -> v.end()
                                }
                                true
                            }
                            viewRef[0] = v
                        }
                    },
                    update = { v ->
                        v.inkColor = paletteArgb[colorIndex]
                        v.inkWidth = thick
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                paletteArgb.forEachIndexed { i, c ->
                    Box(
                        Modifier
                            .width(26.dp)
                            .height(26.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .clickable { colorIndex = i }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("মুছো") { viewRef[0]?.clear() }
                PillButton("আনডু") { viewRef[0]?.undo() }
                PillButton("শেয়ার") {
                    viewRef[0]?.let { onShare(it.export()) }
                }
            }
        }
    }
}

@Composable
private fun PillButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(listOf(jimi.primary, jimi.secondary)))
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(label, color = Color(0xFFFFFFFF), fontSize = 13.sp)
    }
}

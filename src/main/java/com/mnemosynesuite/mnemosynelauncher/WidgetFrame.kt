package com.mnemosynesuite.mnemosynelauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.max

class WidgetFrame @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    var cellX = 0
    var cellY = 0
    var cellW = 2
    var cellH = 2

    var onRemoveRequested: (() -> Unit)? = null

    private val handleSize = (48 * resources.displayMetrics.density).toInt()
    private var gridCellPx = 0
    private var cols = 4
    private var rows = 6

    // Three mutually exclusive states
    private enum class Mode { IDLE, DRAGGING, RESIZING }
    private var mode = Mode.IDLE

    // Edit mode — shows resize handle, activated by long press
    private var editMode = false

    private var startRawX = 0f
    private var startRawY = 0f
    private var startMarginX = 0
    private var startMarginY = 0
    private var startW = 0
    private var startH = 0
    private var longPressRunnable: Runnable? = null

    private var lastTapTime = 0L

    var onLayoutChanged: (() -> Unit)? = null

    // 1. Setup a gesture detector inside your WidgetFrame properties block
    private val gestureDetector =
        GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                Log.d("","onDoubleTap")
                return true
            }

            override fun onDoubleTapEvent(e: MotionEvent): Boolean {
                Log.d("","onDoubleTapEvent")
                return false
            }

            override fun onLongPress(e: MotionEvent) {
                // Trigger the options menu or immediate dragging when a true long click occurs
                triggerLongClickActions()
            }
        })

    // Move your long-press popup code into a single reusable helper function
    private fun triggerLongClickActions() {
        if (!editMode) {
            val options = arrayOf("Move/Resize", "Remove")
            androidx.appcompat.app.AlertDialog.Builder(context)
                .setTitle("Widget")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> enterEditMode()
                        1 -> {
                            exitEditMode()
                            onRemoveRequested?.invoke()
                        }
                    }
                }
                .setOnCancelListener { exitEditMode() }
                .show()
        }
    }

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        alpha = 210
        style = Paint.Style.FILL
    }
    private val handleLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        strokeWidth = 3f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val editBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        alpha = 120
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    init {
        setWillNotDraw(false)
        isLongClickable = true
    }

    fun configure(gridCellPx: Int, cols: Int, rows: Int) {
        this.gridCellPx = gridCellPx
        this.cols = cols
        this.rows = rows
    }

    fun applyCell() {
        val lp = layoutParams as LayoutParams
        lp.leftMargin = cellX * gridCellPx
        lp.topMargin  = cellY * gridCellPx
        lp.width      = cellW * gridCellPx
        lp.height     = cellH * gridCellPx
        layoutParams  = lp
        invalidate()
    }

    private fun pulseGrowAnimate() {
        animate()
            .scaleX(1.1f).scaleY(1.1f)
            .setDuration(120)
            .withEndAction {
                animate()
                    .scaleX(1.0f).scaleY(1.0f)
                    .setDuration(120)
                    .start()
            }
            .start()
    }

    private fun enterEditMode() {
        editMode = true
        mode = Mode.DRAGGING
        pulseGrowAnimate();
        invalidate()
    }

    private fun exitEditMode() {
        editMode = false
        mode = Mode.IDLE
        pulseGrowAnimate()
        invalidate()
        onLayoutChanged?.invoke()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!editMode) return

        // Dashed border to show edit state
        canvas.drawRect(2f, 2f, width - 2f, height - 2f, editBorderPaint)

        // Resize handle in bottom-right corner
        val r = handleSize.toFloat()
        val hx = width - r
        val hy = height - r
        canvas.drawRect(hx, hy, width.toFloat(), height.toFloat(), handlePaint)

        // Grip lines inside handle
        val pad = r * 0.22f
        val step = (r - 2 * pad) / 2f
        for (i in 0..2) {
            val o = pad + i * step
            canvas.drawLine(hx + o, height - pad, width - pad, hy + o, handleLinePaint)
        }
    }

    private fun inResizeHandle(x: Float, y: Float) =
        editMode && x >= width - handleSize && y >= height - handleSize

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // Pass the gesture down to the detector passively on every frame pass
        gestureDetector.onTouchEvent(ev)

        // 1. If we are actively editing, dragging, or resizing, intercept everything!
        if (mode == Mode.DRAGGING || mode == Mode.RESIZING) {
            return true
        }

        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                // 2. Explicitly intercept DOWN ONLY if the finger falls within the resize handle region
                if (inResizeHandle(ev.x, ev.y)) {
                    mode = Mode.RESIZING
                    return true
                }
                // Return false for normal clicks! Let the widget buttons accept the click down state.
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                // 3. Hijack event distribution if a long press changed our mode state in the background
                return mode == Mode.DRAGGING || mode == Mode.RESIZING
            }
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startRawX = event.rawX
                startRawY = event.rawY
                val lp = layoutParams as LayoutParams
                startMarginX = lp.leftMargin
                startMarginY = lp.topMargin
                startW = width
                startH = height

                if (inResizeHandle(event.x, event.y)) {
                    mode = Mode.RESIZING
                    return true
                }

                longPressRunnable = Runnable {
                    if (!editMode) {

                    } else {
                        pulseGrowAnimate()
                        mode = Mode.DRAGGING
                        // Synthesize a CANCEL to hostView so it stops tracking this touch
                        val cancel = MotionEvent.obtain(
                            System.currentTimeMillis(), System.currentTimeMillis(),
                            MotionEvent.ACTION_CANCEL, startRawX, startRawY, 0
                        )
                        for (i in 0 until childCount) getChildAt(i).dispatchTouchEvent(cancel)
                        cancel.recycle()
                    }
                }
                postDelayed(longPressRunnable, 200L)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - startRawX).toInt()
                val dy = (event.rawY - startRawY).toInt()

                if (mode == Mode.IDLE && (abs(dx) > 10 || abs(dy) > 10)) {
                    longPressRunnable?.let { removeCallbacks(it) }
                    longPressRunnable = null
                    return false  // release to child
                }

                when (mode) {
                    Mode.RESIZING -> {
                        val lp = layoutParams as LayoutParams
                        lp.width  = max(gridCellPx, startW + dx)
                        lp.height = max(gridCellPx, startH + dy)
                        layoutParams = lp
                        onLayoutChanged?.invoke()
                        return true
                    }
                    Mode.DRAGGING -> {
                        val lp = layoutParams as LayoutParams
                        lp.leftMargin = startMarginX + dx
                        lp.topMargin  = startMarginY + dy
                        layoutParams = lp
                        onLayoutChanged?.invoke()
                        return true
                    }
                    else -> return false
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                longPressRunnable?.let { removeCallbacks(it) }
                longPressRunnable = null

                if (editMode) {
                    val now = System.currentTimeMillis()
                    Log.d("DEBUG","now - lastTapTime: ${now - lastTapTime}")
                    if (now - lastTapTime < 300) {
                        exitEditMode()
                    }
                    lastTapTime = now
                }

                when (mode) {
                    Mode.RESIZING -> {
                        cellW = max(1, ((width  + gridCellPx / 2) / gridCellPx).coerceAtMost(cols - cellX))
                        cellH = max(1, ((height + gridCellPx / 2) / gridCellPx).coerceAtMost(rows - cellY))
                        applyCell()
                        return true
                    }
                    Mode.DRAGGING -> {
                        val lp = layoutParams as LayoutParams
                        cellX = ((lp.leftMargin + gridCellPx / 2) / gridCellPx).coerceIn(0, cols - cellW)
                        cellY = ((lp.topMargin  + gridCellPx / 2) / gridCellPx).coerceIn(0, rows - cellH)
                        applyCell()
                        return true
                    }
                    else -> {
                        // Tap outside while in edit mode — exit it
                        if (editMode) {
                            exitEditMode()
                            return true
                        }
                    }
                }

                return false
            }
        }
        return false
    }
}
package com.pixelmunchers.game

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

@SuppressLint("ViewConstructor")
class GameView(context: Context) : View(context) {

    companion object {
        private const val KEY_LEVEL = "level"
        private const val KEY_UNLOCKED = "unlocked"
        private const val PER_PAGE = 30
    }

    private val prefs = context.getSharedPreferences("pixel_munchers", Context.MODE_PRIVATE)
    private val d = resources.displayMetrics.density
    private val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES

    // ---- Theme ----
    val cBg = if (dark) 0xFF10231E.toInt() else 0xFFCFE6D2.toInt()
    private val cSurface = if (dark) 0xFF1C3A31.toInt() else 0xFFF5FBF3.toInt()
    private val cSurface2 = if (dark) 0xFF21453A.toInt() else 0xFFE2EFDF.toInt()
    private val cInk = if (dark) 0xFFE8F3EA.toInt() else 0xFF17322B.toInt()
    private val cMuted = if (dark) 0xFF9BBBAE.toInt() else 0xFF56766A.toInt()
    private val cSlot = if (dark) 0xFF2D574A.toInt() else 0xFFBCD8C1.toInt()
    private val cHole = if (dark) 0xFF080504.toInt() else 0xFF3B2A20.toInt()
    private val cRim = if (dark) 0xFF5E4634.toInt() else 0xFF8C6A4F.toInt()
    private val cAccent = 0xFFF2A93B.toInt()
    private val cVeil = if (dark) 0x99000000.toInt() else 0x8C10231E.toInt()
    private val cBugDark = 0xFF2B2420.toInt()
    private val cMystery = 0xFF8A958F.toInt()

    // ---- State ----
    private enum class Screen { GAME, LEVELS }
    private var screen = Screen.GAME
    private var unlocked = max(prefs.getInt(KEY_UNLOCKED, 0), prefs.getInt(KEY_LEVEL, 0))
    private var game = Game(prefs.getInt(KEY_LEVEL, 0).coerceIn(0, unlocked))
    private var page = 0
    private val totalLevels get() = max(LevelGenerator.TOTAL_LEVELS, unlocked + 1)
    private val pageCount get() = (totalLevels + PER_PAGE - 1) / PER_PAGE
    private val thumbs = HashMap<Int, Bitmap>()
    private var downX = 0f
    private var downY = 0f
    private var speed = 1
    private var lastNanos = 0L
    private val startNanos = System.nanoTime()
    private var shakeT = 0f
    private var pressed = -1
    private var lastStatus = Status.PLAY
    private var shownProgress = 0f

    // ---- Paints ----
    private val boldFace = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val pixelPaint = Paint(0)  // no smoothing, keeps thumbnails crisp
    private val tmp = RectF()
    private val tmp2 = RectF()

    // ---- Layout ----
    private val rLevels = RectF()
    private val rRestart = RectF()
    private val rBack = RectF()
    private val rPrev = RectF()
    private val rNext = RectF()
    private val cellRects = Array(PER_PAGE) { RectF() }
    private val rSpeed = RectF()
    private val rBar = RectF()
    private val rBoard = RectF()
    private val rQueue = RectF()
    private val rBoostSlot = RectF()
    private val rBoostShuffle = RectF()
    private val stackRects = Array(Game.COLUMNS) { RectF() }
    private val rOvMain = RectF()
    private val rOvAlt = RectF()
    private var tileW = 0f
    private var tileH = 0f

    init {
        isHapticFeedbackEnabled = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        doLayout()
    }

    private fun doLayout() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val contentW = min(w, 440 * d)
        val left = (w - contentW) / 2
        val right = left + contentW
        val m = 14 * d
        val gap = 7 * d

        // Header
        var y = 10 * d
        rLevels.set(left + m, y, left + m + 52 * d, y + 44 * d)
        rSpeed.set(right - m - 52 * d, y, right - m, y + 44 * d)
        rRestart.set(rSpeed.left - 8 * d - 52 * d, y, rSpeed.left - 8 * d, y + 44 * d)
        y += 54 * d
        rBar.set(left + m, y, right - m, y + 8 * d)
        val boardTop = y + 18 * d

        // Bottom-up: boosters, stacks, queue
        var b = h - 14 * d
        val cx = (left + right) / 2
        rBoostSlot.set(cx - 5 * d - 120 * d, b - 48 * d, cx - 5 * d, b)
        rBoostShuffle.set(cx + 5 * d, b - 48 * d, cx + 5 * d + 120 * d, b)
        b -= 48 * d + 12 * d

        tileW = (contentW - 2 * m - 4 * gap) / Game.COLUMNS
        tileH = tileW / 1.12f
        val stacksH = tileH + 2 * (tileH * 0.86f + 5 * d) + 20 * d
        val stacksTop = b - stacksH
        for (i in 0 until Game.COLUMNS) {
            val x = left + m + i * (tileW + gap)
            stackRects[i].set(x, stacksTop, x + tileW, stacksTop + tileH)
        }
        b = stacksTop - 10 * d

        val n = game.slots.size
        val slotS = min(64 * d, (contentW - 2 * m - 16 * d - (n - 1) * gap) / n)
        rQueue.set(left + m, b - slotS - 16 * d, right - m, b)
        b = rQueue.top - 10 * d

        rBoard.set(left + m, boardTop, right - m, b)

        // Picture inside the board, with room for the hole below
        val pw = rBoard.width()
        val ph = rBoard.height() - 16 * d - 58 * d
        game.cell = max(3f, floor(min(pw * 0.84f / game.cols, ph / game.rows)))
        game.ox = rBoard.left + (pw - game.cell * game.cols) / 2
        game.oy = rBoard.top + 16 * d + max(0f, (ph - game.cell * game.rows) / 2)
        game.holeX = rBoard.centerX()
        game.holeY = rBoard.bottom - 28 * d
        game.density = d

        // Level select screen
        rBack.set(left + m, 10 * d, left + m + 52 * d, 54 * d)
        rPrev.set(left + m, h - 14 * d - 48 * d, left + m + 64 * d, h - 14 * d)
        rNext.set(right - m - 64 * d, h - 14 * d - 48 * d, right - m, h - 14 * d)
        val gridTop = 72 * d
        val gridBottom = rPrev.top - 14 * d
        val cw = (contentW - 2 * m - 4 * gap) / 5
        val chh = min(cw * 1.1f, (gridBottom - gridTop - 5 * gap) / 6)
        for (idx in 0 until PER_PAGE) {
            val r = idx / 5
            val c = idx % 5
            val x = left + m + c * (cw + gap)
            val yy = gridTop + r * (chh + gap)
            cellRects[idx].set(x, yy, x + cw, yy + chh)
        }
    }

    private fun loadLevel(i: Int) {
        game = Game(i)
        prefs.edit().putInt(KEY_LEVEL, i).apply()
        lastStatus = Status.PLAY
        shownProgress = 0f
        pressed = -1
        screen = Screen.GAME
        lastNanos = 0L
        doLayout()
        invalidate()
    }

    private fun openLevels() {
        screen = Screen.LEVELS
        page = (game.levelIndex / PER_PAGE).coerceIn(0, pageCount - 1)
        invalidate()
    }

    /** Called by the activity for the system back gesture. Returns true if handled. */
    fun onBack(): Boolean {
        if (screen == Screen.LEVELS) {
            screen = Screen.GAME
            lastNanos = 0L
            invalidate()
            return true
        }
        return false
    }

    // ---------------- Drawing ----------------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (screen == Screen.LEVELS) {
            canvas.drawColor(cBg)
            drawLevels(canvas)
            return  // static screen: redrawn only when touched
        }
        val now = System.nanoTime()
        val dt = if (lastNanos == 0L) 0f else min(0.05f, (now - lastNanos) / 1e9f)
        lastNanos = now
        val tMs = (((now - startNanos) / 1_000_000L) % 10_000_000L).toFloat()

        game.update(dt, speed)
        if (shakeT > 0f) shakeT = max(0f, shakeT - dt)
        shownProgress += (game.progress - shownProgress) * min(1f, dt * 10f)

        if (game.status != lastStatus) {
            when (game.status) {
                Status.WON -> {
                    unlocked = max(unlocked, game.levelIndex + 1)
                    prefs.edit().putInt(KEY_LEVEL, game.levelIndex + 1).putInt(KEY_UNLOCKED, unlocked).apply()
                    haptic(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)
                }
                Status.LOST -> haptic(HapticFeedbackConstants.LONG_PRESS)
                Status.PLAY -> {}
            }
            lastStatus = game.status
        }

        canvas.drawColor(cBg)
        drawHeader(canvas)
        drawBoard(canvas, tMs)
        drawQueue(canvas)
        drawStacks(canvas)
        drawBoosters(canvas)
        if (game.status != Status.PLAY) drawOverlay(canvas)

        postInvalidateOnAnimation()
    }

    private fun drawHeader(c: Canvas) {
        bevel(c, rLevels, cSurface, 14 * d, 4 * d)
        drawGridIcon(c, rLevels.centerX(), rLevels.centerY() - 2 * d, cInk)
        bevel(c, rRestart, cSurface, 14 * d, 4 * d)
        label(c, "↻", rRestart.centerX(), rRestart.centerY() - 2 * d, 22 * d, cInk)
        bevel(c, rSpeed, cSurface, 14 * d, 4 * d)
        label(c, "${speed}x", rSpeed.centerX(), rSpeed.centerY() - 2 * d, 16 * d, cInk)

        val cx = (rLevels.right + rRestart.left) / 2
        label(c, "Level ${game.levelIndex + 1}", cx, rRestart.top + 15 * d, 22 * d, cInk)
        label(c, game.level.name, cx, rRestart.top + 37 * d, 14 * d, cMuted, bold = false)

        fill.color = cSurface2
        c.drawRoundRect(rBar, 4 * d, 4 * d, fill)
        if (shownProgress > 0.001f) {
            tmp.set(rBar.left, rBar.top, rBar.left + rBar.width() * shownProgress, rBar.bottom)
            fill.color = cAccent
            c.drawRoundRect(tmp, 4 * d, 4 * d, fill)
        }
    }

    private fun drawBoard(c: Canvas, tMs: Float) {
        fill.color = cSurface
        c.drawRoundRect(rBoard, 24 * d, 24 * d, fill)

        val g = game
        val cs = g.cell
        val hi = max(1f, cs * 0.18f)
        val lo = max(1f, cs * 0.14f)
        for (r in 0 until g.rows) for (col in 0 until g.cols) {
            val v = g.grid[r][col]
            if (v == Game.EMPTY) continue
            val x = g.ox + col * cs
            val y = g.oy + r * cs
            fill.color = Levels.color(v)
            c.drawRect(x + 0.5f, y + 0.5f, x + cs - 0.5f, y + cs - 0.5f, fill)
            fill.color = 0x38FFFFFF
            c.drawRect(x + 0.5f, y + 0.5f, x + cs - 0.5f, y + 0.5f + hi, fill)
            fill.color = 0x24000000
            c.drawRect(x + 0.5f, y + cs - 0.5f - lo, x + cs - 0.5f, y + cs - 0.5f, fill)
        }

        // Hole
        fill.color = cRim
        c.drawOval(g.holeX - 30 * d, g.holeY - 13 * d, g.holeX + 30 * d, g.holeY + 13 * d, fill)
        fill.color = cHole
        c.drawOval(g.holeX - 24 * d, g.holeY - 8 * d, g.holeX + 24 * d, g.holeY + 10 * d, fill)

        // Crumbs
        for (p in g.crumbs) {
            fill.color = p.color
            fill.alpha = (255 * (p.life / 0.45f)).toInt().coerceIn(0, 255)
            c.drawRect(p.x, p.y, p.x + p.size, p.y + p.size, fill)
        }
        fill.alpha = 255

        // Munchers
        val s = (cs * 0.62f).coerceIn(6 * d, 11 * d)
        for (b in g.bugs) drawBug(c, b, s, tMs)
    }

    private fun drawBug(c: Canvas, b: Bug, s: Float, tMs: Float) {
        c.save()
        c.translate(b.x, b.y)
        c.rotate(Math.toDegrees(b.ang.toDouble()).toFloat())
        val picking = b.state == BugState.PICK
        val wig = sin(tMs * (if (picking) 0.05f else 0.02f) + b.seed) * s * (if (picking) 0.08f else 0.25f)

        // Legs
        line.color = cBugDark
        line.strokeWidth = 1.2f * d
        for (i in -1..1) {
            val lx = i * s * 0.42f
            val w = if (i % 2 != 0) wig else -wig
            c.drawLine(lx, 0f, lx + w, -s * 0.85f, line)
            c.drawLine(lx, 0f, lx - w, s * 0.85f, line)
        }
        // Body
        fill.color = Levels.color(b.color)
        tmp2.set(-s * 0.9f, -s * 0.55f, s * 0.6f, s * 0.55f)
        c.drawOval(tmp2, fill)
        line.color = 0x59000000
        line.strokeWidth = 1f * d
        c.drawOval(tmp2, line)
        // Head and eyes
        fill.color = cBugDark
        c.drawCircle(s * 0.62f, 0f, s * 0.33f, fill)
        fill.color = Color.WHITE
        c.drawCircle(s * 0.72f, -s * 0.13f, s * 0.1f, fill)
        c.drawCircle(s * 0.72f, s * 0.13f, s * 0.1f, fill)

        // The pixel it picked up: slides from the mouth onto its back, then rides along
        if (picking || b.carry) {
            val lift = if (picking) min(1f, b.t / Game.PICK_TIME) else 1f
            val e = lift * lift * (3 - 2 * lift)
            val bs = max(5 * d, game.cell * 0.78f) * (1 - 0.25f * e)
            val bx = s * 1.25f * (1 - e) + (-s * 0.15f) * e
            val bob = if (b.carry) sin(tMs * 0.02f + b.seed) * 0.8f * d else 0f
            tmp2.set(bx - bs / 2, -bs / 2 + bob, bx + bs / 2, bs / 2 + bob)
            fill.color = Levels.color(b.color)
            c.drawRect(tmp2, fill)
            line.color = 0x59000000
            c.drawRect(tmp2, line)
        }
        c.restore()
    }

    private fun drawQueue(c: Canvas) {
        fill.color = cSurface2
        c.drawRoundRect(rQueue, 18 * d, 18 * d, fill)
        val n = game.slots.size
        val pad = 8 * d
        val gap = 7 * d
        val slotS = min(64 * d, (rQueue.width() - 2 * pad - (n - 1) * gap) / n)
        val rowW = n * slotS + (n - 1) * gap
        val startX = rQueue.centerX() - rowW / 2
        val shake = if (shakeT > 0f) sin(shakeT * 40f) * 6 * d * (shakeT / 0.35f) else 0f

        for (i in 0 until n) {
            val x = startX + i * (slotS + gap) + shake
            tmp.set(x, rQueue.top + pad, x + slotS, rQueue.top + pad + slotS)
            fill.color = cSlot
            c.drawRoundRect(tmp, 12 * d, 12 * d, fill)
            val s = game.slots[i] ?: continue
            val col = Levels.color(s.color)
            bevel(c, tmp, col, 12 * d, 5 * d)
            label(c, s.remaining.toString(), tmp.centerX(), tmp.centerY() - 2 * d, 22 * d, textOn(col))
        }
    }

    private fun drawStacks(c: Canvas) {
        for (ci in 0 until Game.COLUMNS) {
            val col = game.stacks[ci]
            val fr = stackRects[ci]
            // Tiles waiting behind the front one
            var y = fr.bottom + 5 * d
            val bw = tileW * 0.86f
            val bh = tileH * 0.86f
            for (k in 1..min(2, col.size - 1)) {
                tmp.set(fr.centerX() - bw / 2, y, fr.centerX() + bw / 2, y + bh)
                drawTile(c, tmp, col[k], 18 * d, faded = true)
                y += bh + 5 * d
            }
            if (col.size > 3) label(c, "+${col.size - 3}", fr.centerX(), y + 8 * d, 13 * d, cMuted)

            if (col.isNotEmpty()) {
                c.save()
                if (pressed == ci) c.scale(0.93f, 0.93f, fr.centerX(), fr.centerY())
                drawTile(c, fr, col[0], 23 * d, faded = false)
                c.restore()
            }
        }
    }

    private fun drawTile(c: Canvas, r: RectF, t: Tile, size: Float, faded: Boolean) {
        val base = if (t.hidden) cMystery else Levels.color(t.color)
        var tc = if (t.hidden) Color.WHITE else textOn(base)
        var col = base
        if (faded) {
            col = mix(base, cBg, 0.55f)
            tc = mix(tc, cBg, 0.55f)
        }
        bevel(c, r, col, 13 * d, if (faded) 4 * d else 6 * d)
        label(c, if (t.hidden) "?" else t.count.toString(), r.centerX(), r.centerY() - 3 * d, size, tc)
    }

    private fun drawBoosters(c: Canvas) {
        booster(c, rBoostSlot, "+1 slot", !game.usedSlot)
        booster(c, rBoostShuffle, "Shuffle", !game.usedShuffle && game.stacks.any { it.isNotEmpty() })
    }

    private fun booster(c: Canvas, r: RectF, title: String, enabled: Boolean) {
        val bg = if (enabled) cSurface else mix(cSurface, cBg, 0.6f)
        val fg = if (enabled) cInk else mix(cInk, cBg, 0.6f)
        bevel(c, r, bg, 16 * d, 4 * d)
        label(c, title, r.centerX(), r.centerY() - 2 * d, 16 * d, fg)
    }

    private fun drawOverlay(c: Canvas) {
        fill.color = cVeil
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)

        val won = game.status == Status.WON
        val title = if (won) "Level cleared" else "Out of space"
        val lines = if (won) listOf("Every last pixel", "has been eaten.")
        else listOf("All slots are full and no", "muncher can reach its color.")
        val main = when {
            won -> "Next level"
            !game.usedSlot -> "Add a slot and continue"
            else -> "Restart level"
        }
        val alt: String? = when {
            won -> "Play again"
            !game.usedSlot -> "Restart level"
            else -> null
        }

        val cw = min(width - 48 * d, 320 * d)
        val ch = if (alt != null) 250 * d else 196 * d
        val left = (width - cw) / 2
        val top = (height - ch) / 2
        tmp.set(left, top, left + cw, top + ch)
        fill.color = cSurface
        c.drawRoundRect(tmp, 26 * d, 26 * d, fill)

        val cx = width / 2f
        label(c, title, cx, top + 42 * d, 28 * d, cInk)
        label(c, lines[0], cx, top + 76 * d, 15 * d, cMuted, bold = false)
        label(c, lines[1], cx, top + 96 * d, 15 * d, cMuted, bold = false)

        rOvMain.set(left + 22 * d, top + 122 * d, left + cw - 22 * d, top + 174 * d)
        bevel(c, rOvMain, cAccent, 16 * d, 5 * d)
        label(c, main, rOvMain.centerX(), rOvMain.centerY() - 2 * d, 18 * d, 0xFF2A1A05.toInt())

        if (alt != null) {
            rOvAlt.set(left + 22 * d, top + 184 * d, left + cw - 22 * d, top + 230 * d)
            bevel(c, rOvAlt, cSurface2, 16 * d, 4 * d)
            label(c, alt, rOvAlt.centerX(), rOvAlt.centerY() - 2 * d, 16 * d, cInk)
        } else {
            rOvAlt.setEmpty()
        }
    }

    // ---------------- Level select ----------------

    private fun drawLevels(c: Canvas) {
        bevel(c, rBack, cSurface, 14 * d, 4 * d)
        drawChevron(c, rBack.centerX(), rBack.centerY() - 2 * d, true, cInk)
        val cx = width / 2f
        label(c, "Levels", cx, rBack.top + 15 * d, 22 * d, cInk)
        label(c, "$unlocked of $totalLevels cleared", cx, rBack.top + 37 * d, 14 * d, cMuted, bold = false)

        val first = page * PER_PAGE
        for (idx in 0 until PER_PAGE) {
            val li = first + idx
            if (li >= totalLevels) break
            val r = cellRects[idx]
            when {
                li < unlocked -> {
                    bevel(c, r, cSurface, 14 * d, 4 * d)
                    tmp.set(r.left + 8 * d, r.top + 7 * d, r.right - 8 * d, r.bottom - 22 * d)
                    drawThumb(c, li, tmp)
                    label(c, "${li + 1}", r.centerX(), r.bottom - 13 * d, 12 * d, cMuted)
                }
                li == unlocked -> {
                    bevel(c, r, cAccent, 14 * d, 5 * d)
                    label(c, "${li + 1}", r.centerX(), r.centerY() - 8 * d, 20 * d, 0xFF2A1A05.toInt())
                    label(c, "Play", r.centerX(), r.centerY() + 12 * d, 12 * d, 0xFF2A1A05.toInt())
                }
                else -> {
                    fill.color = mix(cSurface, cBg, 0.6f)
                    c.drawRoundRect(r, 14 * d, 14 * d, fill)
                    label(c, "${li + 1}", r.centerX(), r.centerY(), 16 * d, mix(cInk, cBg, 0.65f))
                }
            }
            if (li == game.levelIndex && li != unlocked) {
                line.color = cInk
                line.strokeWidth = 2 * d
                c.drawRoundRect(r, 14 * d, 14 * d, line)
            }
        }

        val hasPrev = page > 0
        val hasNext = page < pageCount - 1
        booster(c, rPrev, "", hasPrev)
        drawChevron(c, rPrev.centerX(), rPrev.centerY() - 2 * d, true, if (hasPrev) cInk else mix(cInk, cBg, 0.6f))
        booster(c, rNext, "", hasNext)
        drawChevron(c, rNext.centerX(), rNext.centerY() - 2 * d, false, if (hasNext) cInk else mix(cInk, cBg, 0.6f))
        val last = min(totalLevels, first + PER_PAGE)
        label(c, "${first + 1}–$last", cx, rPrev.centerY() - 2 * d, 16 * d, cMuted)
    }

    /** Tiny picture of a cleared level, cached as a bitmap with one pixel per picture pixel. */
    private fun drawThumb(c: Canvas, li: Int, area: RectF) {
        val bmp = thumbs.getOrPut(li) {
            val art = LevelGenerator.level(li).art
            val rows = art.size
            val cols = art.maxOf { it.length }
            val b = Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888)
            for (r in 0 until rows) for (col in 0 until cols) {
                val ch = art[r].getOrElse(col) { '.' }
                b.setPixel(col, r, if (ch == '.') Color.TRANSPARENT else Levels.color(ch))
            }
            b
        }
        val px = min(area.width() / bmp.width, area.height() / bmp.height)
        val w = bmp.width * px
        val h = bmp.height * px
        tmp2.set(area.centerX() - w / 2, area.centerY() - h / 2, area.centerX() + w / 2, area.centerY() + h / 2)
        c.drawBitmap(bmp, null, tmp2, pixelPaint)
    }

    private fun drawChevron(c: Canvas, cx: Float, cy: Float, left: Boolean, color: Int) {
        val a = (if (left) 1 else -1) * 5 * d
        val b = 9 * d
        line.color = color
        line.strokeWidth = 3 * d
        c.drawLine(cx + a, cy - b, cx - a, cy, line)
        c.drawLine(cx - a, cy, cx + a, cy + b, line)
    }

    private fun drawGridIcon(c: Canvas, cx: Float, cy: Float, color: Int) {
        fill.color = color
        val s = 7 * d
        val g = 3 * d
        for (dx in 0..1) for (dy in 0..1) {
            val x = cx - s - g / 2 + dx * (s + g)
            val y = cy - s - g / 2 + dy * (s + g)
            tmp2.set(x, y, x + s, y + s)
            c.drawRoundRect(tmp2, 2 * d, 2 * d, fill)
        }
    }

    // ---------------- Drawing helpers ----------------

    /** Rounded block with a darker bottom edge, like a chunky game button. */
    private fun bevel(c: Canvas, r: RectF, color: Int, radius: Float, depth: Float) {
        fill.color = darken(color, 0.8f)
        c.drawRoundRect(r, radius, radius, fill)
        tmp2.set(r.left, r.top, r.right, r.bottom - depth)
        fill.color = color
        c.drawRoundRect(tmp2, radius, radius, fill)
    }

    private fun label(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean = true) {
        text.textSize = size
        text.color = color
        text.typeface = if (bold) boldFace else Typeface.DEFAULT
        c.drawText(s, x, y - (text.descent() + text.ascent()) / 2f, text)
    }

    private fun mix(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt()
    )

    private fun darken(a: Int, f: Float): Int =
        Color.rgb((Color.red(a) * f).toInt(), (Color.green(a) * f).toInt(), (Color.blue(a) * f).toInt())

    private fun textOn(color: Int): Int {
        val lum = 0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)
        return if (lum > 165) cBugDark else Color.WHITE
    }

    private fun haptic(type: Int) {
        performHapticFeedback(type)
    }

    // ---------------- Touch ----------------

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (screen == Screen.LEVELS) {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.x
                    downY = e.y
                }
                MotionEvent.ACTION_UP -> {
                    val dx = e.x - downX
                    if (abs(dx) > 60 * d && abs(dx) > abs(e.y - downY)) {
                        page = (page + if (dx < 0) 1 else -1).coerceIn(0, pageCount - 1)
                        invalidate()
                    } else {
                        handleLevelsTap(e.x, e.y)
                    }
                    performClick()
                }
            }
            return true
        }
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = if (game.status == Status.PLAY) {
                    stackRects.indexOfFirst { it.contains(e.x, e.y) }
                } else -1
            }
            MotionEvent.ACTION_UP -> {
                pressed = -1
                handleTap(e.x, e.y)
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> pressed = -1
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun handleLevelsTap(x: Float, y: Float) {
        when {
            rBack.contains(x, y) -> onBack()
            rPrev.contains(x, y) -> if (page > 0) page--
            rNext.contains(x, y) -> if (page < pageCount - 1) page++
            else -> {
                val idx = cellRects.indexOfFirst { it.contains(x, y) }
                val li = page * PER_PAGE + idx
                if (idx >= 0 && li <= unlocked && li < totalLevels) {
                    haptic(HapticFeedbackConstants.VIRTUAL_KEY)
                    loadLevel(li)
                    return
                }
            }
        }
        invalidate()
    }

    private fun handleTap(x: Float, y: Float) {
        if (game.status != Status.PLAY) {
            when {
                rOvMain.contains(x, y) -> when {
                    game.status == Status.WON -> loadLevel(game.levelIndex + 1)
                    !game.usedSlot -> {
                        game.continueAfterLose()
                        doLayout()
                    }
                    else -> loadLevel(game.levelIndex)
                }
                rOvAlt.contains(x, y) -> loadLevel(game.levelIndex)
            }
            return
        }
        when {
            rLevels.contains(x, y) -> openLevels()
            rRestart.contains(x, y) -> loadLevel(game.levelIndex)
            rSpeed.contains(x, y) -> speed = if (speed == 1) 2 else 1
            rBoostSlot.contains(x, y) -> if (!game.usedSlot) {
                game.addSlot()
                doLayout()
                haptic(HapticFeedbackConstants.VIRTUAL_KEY)
            }
            rBoostShuffle.contains(x, y) -> if (!game.usedShuffle) {
                game.shuffle()
                haptic(HapticFeedbackConstants.VIRTUAL_KEY)
            }
            else -> {
                val ci = stackRects.indexOfFirst { it.contains(x, y) }
                if (ci >= 0 && game.stacks[ci].isNotEmpty()) {
                    if (game.tapTile(ci)) {
                        haptic(HapticFeedbackConstants.VIRTUAL_KEY)
                    } else {
                        shakeT = 0.35f
                        haptic(HapticFeedbackConstants.LONG_PRESS)
                    }
                }
            }
        }
    }
}

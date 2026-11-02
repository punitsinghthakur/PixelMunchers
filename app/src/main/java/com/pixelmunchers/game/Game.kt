package com.pixelmunchers.game

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

enum class BugState { GO, PICK, LEAVE }
enum class Status { PLAY, WON, LOST }

class Tile(val color: Char, val count: Int, var hidden: Boolean, val pri: Float)

class Slot(val color: Char, var remaining: Int, var cool: Float = 0f)

class Bug(var x: Float, var y: Float, val r: Int, val c: Int, val color: Char) {
    var state = BugState.GO
    var ang = (-Math.PI / 2).toFloat()
    var v = 0f          // 0..1, eases in from standstill
    var t = 0f          // timer for the picking pause
    var ex = 0f
    var ey = 0f
    var carry = false
    val seed = Random.nextFloat() * 10f
}

class Crumb(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    val size: Float, val color: Int, var life: Float
)

/**
 * All game rules live here. Geometry (cell, ox, oy, hole, density) is set by GameView
 * so the simulation works in screen pixels.
 */
class Game(val levelIndex: Int) {

    companion object {
        const val EMPTY = ' '
        const val BUG_SPEED = 135f   // dp per second
        const val DISPATCH = 0.26f   // seconds between munchers leaving one slot
        const val PICK_TIME = 0.35f  // seconds spent lifting a pixel
        const val COLUMNS = 5
    }

    val level: Level = LevelGenerator.level(levelIndex)

    val rows = level.art.size
    val cols = level.art.maxOf { it.length }
    val grid: Array<CharArray> = Array(rows) { r ->
        CharArray(cols) { c ->
            val ch = level.art[r].getOrElse(c) { '.' }
            if (ch == '.') EMPTY else ch
        }
    }
    private val reserved = Array(rows) { BooleanArray(cols) }

    var alive = grid.sumOf { row -> row.count { it != EMPTY } }
        private set
    val total = alive

    val stacks: List<MutableList<Tile>> = List(COLUMNS) { mutableListOf() }
    val slots: MutableList<Slot?> = MutableList(5) { null }
    val bugs = mutableListOf<Bug>()
    val crumbs = mutableListOf<Crumb>()

    var status = Status.PLAY
        private set
    var usedSlot = false
        private set
    var usedShuffle = false
        private set
    private var endTimer = 0f

    // Geometry, in pixels, provided by the view
    var cell = 10f
    var ox = 0f
    var oy = 0f
    var holeX = 0f
    var holeY = 0f
    var density = 1f

    val progress: Float get() = 1f - alive.toFloat() / total

    init {
        buildTiles(level.mix, level.hidden)
    }

    // ---------------- Setup ----------------

    private fun isExposed(g: Array<CharArray>, r: Int, c: Int): Boolean {
        if (r == 0 || c == 0 || r == rows - 1 || c == cols - 1) return true
        return g[r - 1][c] == EMPTY || g[r + 1][c] == EMPTY ||
            g[r][c - 1] == EMPTY || g[r][c + 1] == EMPTY
    }

    /** Peel the picture from the outside in, so tiles can be dealt in a roughly solvable order. */
    private fun peelLayers(): Pair<Array<IntArray>, Int> {
        val g = Array(rows) { grid[it].copyOf() }
        val layer = Array(rows) { IntArray(cols) }
        var k = 0
        var left = alive
        while (left > 0) {
            val exposed = mutableListOf<Pair<Int, Int>>()
            for (r in 0 until rows) for (c in 0 until cols) {
                if (g[r][c] != EMPTY && isExposed(g, r, c)) exposed.add(r to c)
            }
            for ((r, c) in exposed) {
                layer[r][c] = k
                g[r][c] = EMPTY
                left--
            }
            k++
        }
        return layer to k
    }

    private fun buildTiles(mix: Float, hiddenRate: Float) {
        val (layer, maxLayer) = peelLayers()
        val byColor = linkedMapOf<Char, MutableList<Int>>()
        for (r in 0 until rows) for (c in 0 until cols) {
            val v = grid[r][c]
            if (v != EMPTY) byColor.getOrPut(v) { mutableListOf() }.add(layer[r][c])
        }
        val tiles = mutableListOf<Tile>()
        // Bigger pictures get bigger numbers on their tiles
        val maxSize = (10 + total / 25).coerceAtMost(40)
        for ((color, layers) in byColor) {
            layers.sort()
            var i = 0
            while (i < layers.size) {
                var size = min(layers.size - i, Random.nextInt(3, maxSize + 1))
                val rest = layers.size - i - size
                if (rest in 1..2) size = layers.size - i
                val avg = layers.subList(i, i + size).average().toFloat()
                tiles.add(Tile(color, size, false, avg + Random.nextFloat() * mix * maxLayer * 0.5f))
                i += size
            }
        }
        tiles.sortBy { it.pri }
        tiles.forEachIndexed { i, t ->
            if (i >= COLUMNS && Random.nextFloat() < hiddenRate) t.hidden = true
            stacks[i % COLUMNS].add(t)
        }
    }

    // ---------------- Player actions ----------------

    /** Returns false when the queue is full (the view shakes the queue). */
    fun tapTile(column: Int): Boolean {
        if (status != Status.PLAY) return true
        val col = stacks[column]
        if (col.isEmpty()) return true
        val idx = slots.indexOf(null)
        if (idx < 0) return false
        val t = col.removeAt(0)
        col.firstOrNull()?.hidden = false
        slots[idx] = Slot(t.color, t.count)
        return true
    }

    fun addSlot() {
        if (usedSlot) return
        usedSlot = true
        slots.add(null)
    }

    fun continueAfterLose() {
        addSlot()
        status = Status.PLAY
        endTimer = 0f
    }

    fun shuffle() {
        if (usedShuffle || stacks.all { it.isEmpty() }) return
        usedShuffle = true
        val lengths = stacks.map { it.size }
        val all = stacks.flatten().shuffled()
        stacks.forEach { it.clear() }
        var p = 0
        stacks.forEachIndexed { i, s ->
            s.addAll(all.subList(p, p + lengths[i]))
            p += lengths[i]
        }
        stacks.forEach { it.firstOrNull()?.hidden = false }
    }

    // ---------------- Simulation ----------------

    private fun rnd(a: Float, b: Float) = a + Random.nextFloat() * (b - a)

    private fun findTarget(color: Char): Pair<Int, Int>? {
        val cands = mutableListOf<Pair<Pair<Int, Int>, Float>>()
        for (r in 0 until rows) for (c in 0 until cols) {
            if (grid[r][c] == color && !reserved[r][c] && isExposed(grid, r, c)) {
                // Prefer pixels nearer the hole, with some randomness
                cands.add((r to c) to (r + rnd(-4f, 4f)))
            }
        }
        if (cands.isEmpty()) return null
        cands.sortByDescending { it.second }
        return cands[Random.nextInt(min(cands.size, 4))].first
    }

    private fun hasTarget(color: Char): Boolean {
        for (r in 0 until rows) for (c in 0 until cols) {
            if (grid[r][c] == color && !reserved[r][c] && isExposed(grid, r, c)) return true
        }
        return false
    }

    fun update(dtIn: Float, speed: Int) {
        val dt = dtIn * speed
        val d = density

        // Each filled slot sends out munchers one at a time
        if (status == Status.PLAY) {
            for (i in slots.indices) {
                val s = slots[i] ?: continue
                s.cool -= dt
                if (s.cool <= 0f && s.remaining > 0) {
                    val target = findTarget(s.color)
                    if (target != null) {
                        val (r, c) = target
                        reserved[r][c] = true
                        bugs.add(Bug(holeX + rnd(-12f, 12f) * d, holeY + rnd(-2f, 2f) * d, r, c, s.color))
                        s.remaining--
                        s.cool = DISPATCH
                        if (s.remaining == 0) slots[i] = null
                    } else {
                        s.cool = 0.15f
                    }
                }
            }
        }

        // Move munchers: go -> pick -> carry back to the hole
        val speedPx = BUG_SPEED * d
        var i = bugs.size - 1
        while (i >= 0) {
            val b = bugs[i]
            if (b.state == BugState.PICK) {
                b.t += dt
                if (b.t >= PICK_TIME) {
                    b.state = BugState.LEAVE
                    b.carry = true
                    b.v = 0f
                    b.ex = holeX + rnd(-12f, 12f) * d
                    b.ey = holeY + rnd(-2f, 2f) * d
                }
                i--
                continue
            }
            val tx = if (b.state == BugState.GO) ox + b.c * cell + cell / 2 else b.ex
            val ty = if (b.state == BugState.GO) oy + b.r * cell + cell / 2 else b.ey
            val dx = tx - b.x
            val dy = ty - b.y
            val dist = hypot(dx, dy)
            b.v = min(1f, b.v + dt * 2.5f)
            val near = 22f * d
            val slow = if (dist < near) max(0.4f, dist / near) else 1f
            val step = speedPx * dt * b.v * slow

            if (dist <= max(step, 0.8f * d)) {
                b.x = tx
                b.y = ty
                if (b.state == BugState.GO) {
                    val col = Levels.color(grid[b.r][b.c])
                    grid[b.r][b.c] = EMPTY
                    reserved[b.r][b.c] = false
                    alive--
                    repeat(2) {
                        crumbs.add(
                            Crumb(
                                tx, ty, rnd(-40f, 40f) * d, rnd(-60f, 0f) * d,
                                max(2f * d, cell * 0.18f), col, 0.45f
                            )
                        )
                    }
                    b.state = BugState.PICK
                    b.t = 0f
                } else {
                    bugs.removeAt(i)
                    i--
                    continue
                }
            } else {
                // Steer smoothly instead of snapping to the new direction
                val want = atan2(dy, dx)
                val diff = atan2(sin(want - b.ang), cos(want - b.ang))
                val turn = (6f + 160f * d / max(dist, 6f * d)) * dt
                b.ang += diff.coerceIn(-turn, turn)
                if (dist < 14f * d) b.ang = want
                b.x += cos(b.ang) * step
                b.y += sin(b.ang) * step
            }
            i--
        }

        for (p in crumbs) {
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.vy += 300f * d * dt
            p.life -= dt
        }
        crumbs.removeAll { it.life <= 0f }

        checkEnd(dt)
    }

    private fun checkEnd(dt: Float) {
        if (status != Status.PLAY) return
        if (alive == 0) {
            if (bugs.isEmpty()) {
                endTimer += dt
                if (endTimer > 0.4f) status = Status.WON
            }
            return
        }
        val full = slots.all { it != null }
        val eating = bugs.any { it.state == BugState.GO }
        if (full && !eating && slots.all { !hasTarget(it!!.color) }) {
            endTimer += dt
            if (endTimer > 0.5f) status = Status.LOST
        } else {
            endTimer = 0f
        }
    }
}

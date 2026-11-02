package com.pixelmunchers.game

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Builds every level from its number, so 600+ levels need no stored data.
 * The same level number always produces the same picture (seeded random).
 *
 * - Levels 1-4: tutorial pictures.
 * - Every 5th level: a hand-drawn picture (recolored in later levels).
 * - Others: a shape (heart, star, flower...) filled with a pattern, or a little creature.
 * - Pictures get bigger, use more colors, and deal tiles in a more mixed-up order as levels rise.
 */
object LevelGenerator {

    const val TOTAL_LEVELS = 600

    private val cache = HashMap<Int, Level>()

    fun level(index: Int): Level = cache.getOrPut(index) { build(index) }

    // Clearly different colors only, so tiles are easy to tell apart
    private val pool = charArrayOf('R', 'O', 'Y', 'G', 'B', 'V', 'P', 'N', 'S', 'A')

    private enum class Shape(val title: String, val extraWidth: Int = 0, val round: Boolean = false) {
        CIRCLE("Circle", round = true),
        RING("Ring"),
        HEART("Heart", round = true),
        STAR("Star"),
        DIAMOND("Diamond"),
        ROUNDED("Cushion", round = true),
        TRIANGLE("Pyramid"),
        HEXAGON("Hexagon", round = true),
        CROSS("Cross"),
        FLOWER("Flower"),
        MOON("Moon"),
        DROP("Drop", round = true),
        EGG("Egg", round = true),
        CLOUD("Cloud", extraWidth = 4, round = true),
        BUTTERFLY("Butterfly", extraWidth = 4),
        LEAF("Leaf")
    }

    private enum class Pattern { OUTLINE, RINGS, HBANDS, VBANDS, DIAGONAL, CHECKER, SPOTS, QUADRANTS, SECTORS }

    private val creatureNames = listOf("Critter", "Blob", "Gremlin", "Sprout", "Beastie", "Doodle", "Muncher", "Gizmo")

    private fun build(i: Int): Level {
        if (i < 4) return Levels.handmade[i]

        val rnd = Random(i * 7919 + 17)
        val mix = 0.4f + min(2.6f, i / 100f)
        val hidden = if (i < 12) 0f else min(0.5f, 0.08f + i / 600f)

        if (i % 5 == 4) {
            val h = Levels.handmade[4 + (i / 5) % (Levels.handmade.size - 4)]
            val art = if (i >= 100 && rnd.nextFloat() < 0.6f) recolor(h.art, rnd) else h.art
            return Level(h.name, mix, hidden, art)
        }

        repeat(20) {
            val (name, raw) = if (i >= 10 && rnd.nextFloat() < 0.3f) creature(i, rnd) else shapeArt(i, rnd)
            val art = trim(raw)
            val count = art.sumOf { row -> row.count { it != '.' } }
            if (count >= 24) return Level(name, mix, hidden, art)
        }
        val h = Levels.handmade[i % Levels.handmade.size]
        return Level(h.name, mix, hidden, h.art)
    }

    // ---------------- Shapes ----------------

    private fun shapeArt(i: Int, rnd: Random): Pair<String, List<String>> {
        val shapes = Shape.values()
        val shape = shapes[rnd.nextInt(shapes.size)]
        val n = (10 + min(14, i / 35) + rnd.nextInt(-1, 2)).coerceIn(9, 24)
        val w = n + shape.extraWidth
        val h = n
        val mask = Array(h) { y ->
            BooleanArray(w) { x ->
                val u = ((x + 0.5f) / w) * 2f - 1f
                val v = ((y + 0.5f) / h) * 2f - 1f
                inside(shape, u, v)
            }
        }
        val k = (2 + min(4, i / 50) - (if (rnd.nextFloat() < 0.25f) 1 else 0)).coerceAtLeast(2)
        val colors = pickColors(k, rnd)
        val layer = layers(mask)
        val patterns = Pattern.values()
        val pattern = patterns[rnd.nextInt(patterns.size)]

        val spots = List(2 + rnd.nextInt(4)) {
            Triple(rnd.nextFloat() * 1.6f - 0.8f, rnd.nextFloat() * 1.6f - 0.8f, 0.15f + rnd.nextFloat() * 0.2f)
        }
        val reps = 1 + rnd.nextInt(2)
        val ringW = 1 + rnd.nextInt(2)
        val block = 2 + rnd.nextInt(2)
        val rot = rnd.nextFloat() * 2f * PI.toFloat()

        val grid = Array(h) { CharArray(w) { '.' } }
        for (y in 0 until h) for (x in 0 until w) {
            if (!mask[y][x]) continue
            val u = ((x + 0.5f) / w) * 2f - 1f
            val v = ((y + 0.5f) / h) * 2f - 1f
            val idx = when (pattern) {
                Pattern.OUTLINE -> if (layer[y][x] == 0) 1 else if (k > 2 && layer[y][x] >= 3) 2 else 0
                Pattern.RINGS -> (layer[y][x] / ringW) % k
                Pattern.HBANDS -> floor((v + 1f) / 2f * k * reps).toInt() % k
                Pattern.VBANDS -> floor((u + 1f) / 2f * k * reps).toInt() % k
                Pattern.DIAGONAL -> floor((u + v + 2f) / 4f * k * reps * 1.5f).toInt() % k
                Pattern.CHECKER -> if (k > 2 && layer[y][x] == 0) 2 else ((x / block + y / block) % 2)
                Pattern.SPOTS -> {
                    val si = spots.indexOfFirst { (sx, sy, sr) -> (u - sx) * (u - sx) + (v - sy) * (v - sy) <= sr * sr }
                    if (si < 0) 0 else 1 + si % (k - 1)
                }
                Pattern.QUADRANTS -> ((if (u < 0f) 0 else 1) + (if (v < 0f) 0 else 2)) % k
                Pattern.SECTORS -> {
                    val a = ((atan2(v, u) + rot) % (2f * PI.toFloat()) + 2f * PI.toFloat()) % (2f * PI.toFloat())
                    floor(a / (2f * PI.toFloat()) * k).toInt() % k
                }
            }
            grid[y][x] = colors[idx.coerceIn(0, k - 1)]
        }
        if (shape.round && n >= 11 && rnd.nextFloat() < 0.35f) addFace(grid, mask)
        return shape.title to grid.map { String(it) }
    }

    private fun circle(u: Float, v: Float, cx: Float, cy: Float, r: Float) =
        (u - cx) * (u - cx) + (v - cy) * (v - cy) <= r * r

    private fun inside(s: Shape, u: Float, v: Float): Boolean {
        val r2 = u * u + v * v
        val th = atan2(v, u)
        val au = abs(u)
        val av = abs(v)
        return when (s) {
            Shape.CIRCLE -> r2 <= 0.92f
            Shape.RING -> r2 <= 0.95f && r2 >= 0.28f
            Shape.HEART -> {
                val x = u * 1.25f
                val y = -v * 1.3f + 0.3f
                val a = x * x + y * y - 1f
                a * a * a - x * x * y * y * y <= 0f
            }
            Shape.STAR -> sqrt(r2) <= 0.42f + 0.55f * ((cos(5f * (th + PI.toFloat() / 2f)) + 1f) / 2f).pow(1.6f)
            Shape.DIAMOND -> au + av <= 0.98f
            Shape.ROUNDED -> au <= 0.92f && av <= 0.92f &&
                (au < 0.55f || av < 0.55f || (au - 0.55f) * (au - 0.55f) + (av - 0.55f) * (av - 0.55f) <= 0.37f * 0.37f)
            Shape.TRIANGLE -> v >= -0.92f && v <= 0.92f && au <= (v + 0.92f) / 1.84f * 0.98f
            Shape.HEXAGON -> av <= 0.86f && au <= 1f - av * 0.58f
            Shape.CROSS -> (au <= 0.34f && av <= 0.95f) || (av <= 0.34f && au <= 0.95f)
            Shape.FLOWER -> sqrt(r2) <= 0.48f + 0.48f * abs(cos(2.5f * (th + PI.toFloat() / 2f)))
            Shape.MOON -> r2 <= 0.92f && (u - 0.42f) * (u - 0.42f) + (v + 0.2f) * (v + 0.2f) > 0.5f
            Shape.DROP -> if (v >= 0.25f) circle(u, v, 0f, 0.25f, 0.7f)
                else v >= -0.95f && au <= 0.7f * (v + 0.95f) / 1.2f
            Shape.EGG -> {
                val wd = 0.62f + 0.2f * (v + 1f) / 2f
                (u / wd) * (u / wd) + v * v <= 0.92f
            }
            Shape.CLOUD -> circle(u, v, -0.45f, 0.2f, 0.45f) || circle(u, v, 0f, -0.15f, 0.58f) ||
                circle(u, v, 0.48f, 0.15f, 0.45f) || (au <= 0.85f && v >= 0.15f && v <= 0.62f)
            Shape.BUTTERFLY -> circle(au, v, 0.48f, -0.32f, 0.45f) || circle(au, v, 0.42f, 0.4f, 0.36f) ||
                (au < 0.1f && av < 0.78f)
            Shape.LEAF -> au <= 0.85f * (1f - v * v).coerceAtLeast(0f).pow(0.7f)
        }
    }

    private fun addFace(grid: Array<CharArray>, mask: Array<BooleanArray>) {
        val h = grid.size
        val w = grid[0].size
        val ey = (h * 0.4f).toInt()
        val ex = w / 2 - (w * 0.18f).toInt().coerceAtLeast(2) - 1
        for (x in listOf(ex, w - 1 - ex)) {
            for (y in ey..ey + 1) if (y in 0 until h && x in 0 until w && mask[y][x]) grid[y][x] = 'K'
            if (h >= 14 && x in 0 until w && mask[ey][x]) grid[ey][x] = 'W'
        }
        val my = (h * 0.62f).toInt()
        val mx0 = w / 2 - 1
        val mx1 = if (w % 2 == 0) w / 2 else w / 2 + 1
        for (x in mx0..mx1) if (my in 0 until h && mask[my][x]) grid[my][x] = 'K'
    }

    // ---------------- Creatures ----------------

    private fun creature(i: Int, rnd: Random): Pair<String, List<String>> {
        val h = (10 + min(10, i / 50) + rnd.nextInt(-1, 2)).coerceIn(9, 20)
        val half = (h + 1) / 2
        var m = Array(h) { y ->
            BooleanArray(half) { x ->
                val dx = (half - 1 - x).toFloat() / half
                val dy = abs((y + 0.5f) / h * 2f - 1f)
                rnd.nextFloat() < 0.97f - dx * 0.45f - dy * 0.3f
            }
        }
        repeat(2) { m = smooth(m) }
        val w = half * 2
        val mask = Array(h) { y -> BooleanArray(w) { x -> if (x < half) m[y][x] else m[y][w - 1 - x] } }

        val k = (2 + min(3, i / 80)).coerceAtLeast(2)
        val colors = pickColors(k, rnd)
        val layer = layers(mask)
        val grid = Array(h) { CharArray(w) { '.' } }
        for (y in 0 until h) for (x in 0 until w) {
            if (!mask[y][x]) continue
            grid[y][x] = when {
                layer[y][x] == 0 -> colors[1]
                k > 2 && y > h * 0.55f && abs(x - (w - 1) / 2f) < w * 0.22f -> colors[2]
                k > 3 && layer[y][x] >= 2 && (min(x, w - 1 - x) + y) % 4 == 0 -> colors[3]
                else -> colors[0]
            }
        }
        // Eyes
        val ey = (h * 0.32f).toInt()
        val ex = half - 2
        for (x in listOf(ex, w - 1 - ex)) {
            if (mask[ey][x]) grid[ey][x] = 'W'
            if (ey + 1 < h && mask[ey + 1][x]) grid[ey + 1][x] = 'K'
        }
        return creatureNames[rnd.nextInt(creatureNames.size)] to grid.map { String(it) }
    }

    private fun smooth(m: Array<BooleanArray>): Array<BooleanArray> {
        val h = m.size
        val half = m[0].size
        fun at(y: Int, x: Int): Boolean {
            if (y < 0 || y >= h || x < 0) return false
            val xx = if (x >= half) 2 * half - 1 - x else x  // mirror across the center line
            return xx in 0 until half && m[y][xx]
        }
        return Array(h) { y ->
            BooleanArray(half) { x ->
                var n = 0
                for (dy in -1..1) for (dx in -1..1) if ((dy != 0 || dx != 0) && at(y + dy, x + dx)) n++
                n >= 5 || (m[y][x] && n >= 4)
            }
        }
    }

    // ---------------- Helpers ----------------

    /** Remove empty rows and columns around the picture. */
    private fun trim(art: List<String>): List<String> {
        val rows = art.indices.filter { r -> art[r].any { it != '.' } }
        if (rows.isEmpty()) return art
        val w = art.maxOf { it.length }
        val cols = (0 until w).filter { c -> art.any { it.getOrElse(c) { '.' } != '.' } }
        return (rows.first()..rows.last()).map { r ->
            String(CharArray(cols.last() - cols.first() + 1) { art[r].getOrElse(cols.first() + it) { '.' } })
        }
    }

    private fun pickColors(k: Int, rnd: Random): CharArray =
        pool.toList().shuffled(rnd).take(k).toCharArray()

    private fun recolor(art: List<String>, rnd: Random): List<String> {
        val used = art.flatMap { row -> row.toList() }.filter { it != '.' && it != 'K' && it != 'W' }.distinct()
        val fresh = pool.toList().shuffled(rnd)
        val map = used.mapIndexed { idx, ch -> ch to fresh[idx % fresh.size] }.toMap()
        return art.map { row -> String(row.map { map[it] ?: it }.toCharArray()) }
    }

    /** Distance of each filled pixel from the outside (0 = on the edge). */
    private fun layers(mask: Array<BooleanArray>): Array<IntArray> {
        val h = mask.size
        val w = mask[0].size
        val layer = Array(h) { IntArray(w) { -1 } }
        val queue = ArrayDeque<Int>()
        fun filled(y: Int, x: Int) = y in 0 until h && x in 0 until w && mask[y][x]
        for (y in 0 until h) for (x in 0 until w) {
            if (mask[y][x] && (!filled(y - 1, x) || !filled(y + 1, x) || !filled(y, x - 1) || !filled(y, x + 1))) {
                layer[y][x] = 0
                queue.addLast(y * w + x)
            }
        }
        while (queue.isNotEmpty()) {
            val p = queue.removeFirst()
            val y = p / w
            val x = p % w
            for ((ny, nx) in listOf(y - 1 to x, y + 1 to x, y to x - 1, y to x + 1)) {
                if (filled(ny, nx) && layer[ny][nx] < 0) {
                    layer[ny][nx] = layer[y][x] + 1
                    queue.addLast(ny * w + nx)
                }
            }
        }
        return layer
    }
}

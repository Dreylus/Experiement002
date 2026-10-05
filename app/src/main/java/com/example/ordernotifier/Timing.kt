package com.example.ordernotifier

import kotlin.math.abs
import kotlin.random.Random

/** Slider steps, presets and the maths behind the Timing tab. */
object Timing {
    /** Seconds between orders. Uneven steps so both "every few seconds" and "every hour" are easy to pick. */
    val GAP_STEPS = intArrayOf(
        1, 2, 3, 5, 8, 10, 15, 20, 30, 45, 60, 90, 120, 180, 240, 300, 420, 600, 900, 1200, 1800, 2700, 3600
    )

    /** Whole-currency order totals. */
    val PRICE_STEPS = intArrayOf(
        1, 2, 3, 5, 8, 10, 12, 15, 20, 25, 30, 40, 50, 60, 75, 100, 120, 150, 200, 250, 300, 400, 500,
        750, 1000, 1500, 2000, 3000, 5000, 10000
    )

    fun nearestIndex(steps: IntArray, value: Int): Int =
        steps.indices.minByOrNull { abs(steps[it] - value) } ?: 0

    data class Preset(val label: String, val minGap: Int, val maxGap: Int, val chance: Int, val burstMax: Int) {
        fun matches(s: Settings) = minOf(s.minGapSec, s.maxGapSec) == minGap &&
            maxOf(s.minGapSec, s.maxGapSec) == maxGap && s.burstChance == chance && s.burstMax == burstMax
    }

    // Every gap value here must be one of GAP_STEPS and every chance a multiple of 5.
    val PRESETS = listOf(
        Preset("😌 Chill", 120, 600, 10, 2),
        Preset("📦 Steady", 30, 180, 20, 3),
        Preset("🔥 Busy", 10, 60, 35, 4),
        Preset("🚀 Viral", 2, 20, 60, 6),
    )

    /** An order at [atSec] seconds from now; [stack] is 0 for a normal order, 1..n for burst extras. */
    data class Point(val atSec: Double, val stack: Int)

    /** Simulates the same schedule SimulatorService follows, for the timeline preview. */
    fun sample(minGap: Int, maxGap: Int, chance: Int, burstMax: Int, windowSec: Int, rnd: Random): List<Point> {
        val lo = minOf(minGap, maxGap).coerceAtLeast(1)
        val hi = maxOf(minGap, maxGap).coerceAtLeast(1)
        val out = mutableListOf<Point>()
        var t = 0.0
        while (out.size < 400) {
            t += rnd.nextLong(lo * 1000L, hi * 1000L + 1) / 1000.0
            if (t > windowSec) break
            out += Point(t, 0)
            if (burstMax > 1 && rnd.nextInt(100) < chance) {
                val extra = rnd.nextInt(1, burstMax)
                for (k in 1..extra) {
                    t += rnd.nextLong(1000, 5000) / 1000.0
                    if (t <= windowSec) out += Point(t, k)
                }
            }
        }
        return out
    }

    /** Expected orders per hour for these settings (bursts included). */
    fun ordersPerHour(minGap: Int, maxGap: Int, chance: Int, burstMax: Int): Double {
        val avgGap = (minOf(minGap, maxGap) + maxOf(minGap, maxGap)) / 2.0
        val p = if (burstMax > 1) chance / 100.0 else 0.0
        val meanExtra = burstMax / 2.0 // extras are uniform in 1..burstMax-1
        val cycle = avgGap.coerceAtLeast(1.0) + p * meanExtra * 3.0 // each extra waits 1-5 s (avg 3)
        return 3600.0 * (1 + p * meanExtra) / cycle
    }

    /** A window that shows roughly 6+ orders: 1 min … 1 day. */
    fun windowFor(minGap: Int, maxGap: Int): Int {
        val avg = (minOf(minGap, maxGap) + maxOf(minGap, maxGap)) / 2.0
        val windows = intArrayOf(60, 120, 300, 600, 1800, 3600, 7200, 14_400, 28_800, 86_400)
        return windows.firstOrNull { it >= avg * 6 } ?: 86_400
    }
}

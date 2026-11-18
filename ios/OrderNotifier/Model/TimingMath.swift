import Foundation

/// Small deterministic generator so the timing example doesn't jump around on every redraw.
struct SeededGenerator: RandomNumberGenerator {
    private var state: UInt64

    init(seed: UInt64) {
        state = seed == 0 ? 0x9E37_79B9_7F4A_7C15 : seed
    }

    mutating func next() -> UInt64 {
        state &+= 0x9E37_79B9_7F4A_7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58_476D_1CE4_E5B9
        z = (z ^ (z >> 27)) &* 0x94D0_49BB_1331_11EB
        return z ^ (z >> 31)
    }
}

struct TimingPreset: Identifiable, Equatable {
    let id: String
    let title: String
    let symbol: String
    let minGap: Int
    let maxGap: Int
    let chance: Int
    let burstMax: Int

    func matches(_ s: OrderSettings) -> Bool {
        min(s.minGap, s.maxGap) == minGap && max(s.minGap, s.maxGap) == maxGap
            && s.burstChance == chance && s.burstMax == burstMax
    }
}

/// An order in the timing example: minutes from now, and 0 for a normal order or 1+ for burst extras.
struct TimelinePoint: Equatable {
    let minutes: Double
    let stack: Int
}

enum TimingMath {
    /// Seconds between orders. Uneven steps so both "every few seconds" and "every hour" are easy to pick.
    static let gapSteps = [
        1, 2, 3, 5, 8, 10, 15, 20, 30, 45, 60, 90, 120, 180, 240, 300, 420, 600, 900, 1200, 1800, 2700, 3600,
    ]

    /// Whole-currency order totals.
    static let priceSteps = [
        1, 2, 3, 5, 8, 10, 12, 15, 20, 25, 30, 40, 50, 60, 75, 100, 120, 150, 200, 250, 300, 400, 500,
        750, 1000, 1500, 2000, 3000, 5000, 10000,
    ]

    /// Every gap here is one of `gapSteps`; "Steady" equals the defaults.
    static let presets: [TimingPreset] = [
        TimingPreset(id: "chill", title: "Chill", symbol: "leaf.fill", minGap: 120, maxGap: 600, chance: 10, burstMax: 2),
        TimingPreset(id: "steady", title: "Steady", symbol: "cart.fill", minGap: 20, maxGap: 120, chance: 25, burstMax: 4),
        TimingPreset(id: "busy", title: "Busy", symbol: "flame.fill", minGap: 10, maxGap: 60, chance: 40, burstMax: 5),
        TimingPreset(id: "viral", title: "Viral", symbol: "bolt.fill", minGap: 2, maxGap: 20, chance: 60, burstMax: 6),
    ]

    static func nearestIndex(_ steps: [Int], _ value: Int) -> Int {
        steps.indices.min { abs(steps[$0] - value) < abs(steps[$1] - value) } ?? 0
    }

    /// The same schedule the app hands to iOS, simulated for the example timeline.
    static func sample(minGap: Int, maxGap: Int, chance: Int, burstMax: Int, windowSeconds: Int, seed: UInt64) -> [TimelinePoint] {
        var rng = SeededGenerator(seed: seed)
        let lo = Double(max(1, min(minGap, maxGap)))
        let hi = Double(max(1, max(minGap, maxGap)))
        let window = Double(windowSeconds)
        var t = 0.0
        var out: [TimelinePoint] = []
        while out.count < 400 {
            t += Double.random(in: lo...hi, using: &rng)
            if t > window { break }
            out.append(TimelinePoint(minutes: t / 60, stack: 0))
            if burstMax > 1, Int.random(in: 0..<100, using: &rng) < chance {
                let extra = Int.random(in: 1...(burstMax - 1), using: &rng)
                for k in 1...extra {
                    t += Double.random(in: 1...5, using: &rng)
                    if t <= window { out.append(TimelinePoint(minutes: t / 60, stack: min(k, 4))) }
                }
            }
        }
        return out
    }

    /// Expected orders per hour, bursts included.
    static func ordersPerHour(minGap: Int, maxGap: Int, chance: Int, burstMax: Int) -> Double {
        let avgGap = Double(min(minGap, maxGap) + max(minGap, maxGap)) / 2
        let p = burstMax > 1 ? Double(chance) / 100 : 0
        let meanExtra = Double(burstMax) / 2 // extras are uniform in 1...burstMax-1
        let cycle = max(1, avgGap) + p * meanExtra * 3 // each extra waits 1-5 s (3 on average)
        return 3600 * (1 + p * meanExtra) / cycle
    }

    /// A window that shows roughly six or more orders: 1 minute … 1 day.
    static func windowSeconds(minGap: Int, maxGap: Int) -> Int {
        let avg = Double(min(minGap, maxGap) + max(minGap, maxGap)) / 2
        let windows = [60, 120, 300, 600, 1800, 3600, 7200, 14_400, 28_800, 86_400]
        return windows.first { Double($0) >= avg * 6 } ?? 86_400
    }
}

import Charts
import SwiftUI

struct TimingView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    PresetChips()
                    TimelineCard()
                    GapCard()
                    BurstCard()
                    HowItWorksCard()
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 24)
            }
            .background(AppBackground())
            .navigationTitle("Timing")
        }
    }
}

// MARK: - Presets

private struct PresetChips: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            GlassGroup(spacing: 8) {
                HStack(spacing: 8) {
                    ForEach(TimingMath.presets) { preset in
                        let active = model.activePreset == preset
                        Button {
                            withAnimation(.snappy) { model.apply(preset) }
                        } label: {
                            Label(preset.title, systemImage: preset.symbol)
                                .font(.subheadline.weight(.semibold))
                                .padding(.horizontal, 4)
                        }
                        .modifier(ChipStyle(active: active))
                        .accessibilityIdentifier("preset.\(preset.title)")
                        .accessibilityAddTraits(active ? .isSelected : [])
                        .sensoryFeedback(.selection, trigger: active)
                    }
                }
                .padding(.vertical, 4)
            }
        }
        .scrollClipDisabled()
    }
}

private struct ChipStyle: ViewModifier {
    let active: Bool

    func body(content: Content) -> some View {
        if active {
            content.prominentGlassButton(tint: .green)
        } else {
            content.glassButton()
        }
    }
}

// MARK: - Example timeline

private struct TimelineCard: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        let s = model.settings
        let window = TimingMath.windowSeconds(minGap: s.minGap, maxGap: s.maxGap)
        let points = TimingMath.sample(
            minGap: s.minGap, maxGap: s.maxGap, chance: s.burstChance,
            burstMax: s.burstMax, windowSeconds: window, seed: model.timelineSeed
        )
        let perHour = TimingMath.ordersPerHour(minGap: s.minGap, maxGap: s.maxGap, chance: s.burstChance, burstMax: s.burstMax)
        let midCents = (s.minPrice + s.maxPrice) * 50
        let rate = perHour >= 1
            ? "≈ \(Int(perHour.rounded())) orders an hour"
            : "≈ \(Int((perHour * 24).rounded())) orders a day"

        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Next \(Format.window(window))")
                        .font(.headline)
                    Text("\(rate) · ≈ \(Format.moneyShort(Int(perHour * Double(midCents)), s.currency)) an hour")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .contentTransition(.numericText())
                }
                Spacer()
                Button {
                    withAnimation(.snappy) { model.timelineSeed = .random(in: 1...UInt64.max) }
                } label: {
                    Image(systemName: "shuffle")
                        .font(.headline)
                        .frame(width: 22, height: 22)
                }
                .glassButton()
                .buttonBorderShape(.circle)
                .accessibilityLabel("Shuffle the example")
            }

            Chart {
                ForEach(Array(points.enumerated()), id: \.offset) { item in
                    PointMark(
                        x: .value("Minutes", item.element.minutes),
                        y: .value("Burst", Double(item.element.stack))
                    )
                    .foregroundStyle(item.element.stack == 0 ? Color.green : Color.pink)
                    .symbolSize(item.element.stack == 0 ? 110 : 60)
                }
            }
            .chartXScale(domain: 0...Double(window) / 60)
            .chartYScale(domain: -0.6...4.6)
            .chartYAxis(.hidden)
            .chartXAxis {
                AxisMarks(values: .automatic(desiredCount: 4)) { value in
                    AxisGridLine()
                    AxisValueLabel {
                        if let minutes = value.as(Double.self) {
                            Text(minutes == 0 ? "now" : "+" + Format.seconds(Int(minutes * 60)))
                        }
                    }
                }
            }
            .frame(height: 120)
            .animation(.snappy, value: points)
            .accessibilityLabel("Example: \(points.count) orders in the next \(Format.window(window))")

            Label("Each dot is an order. Pink stacks are bursts that land seconds apart.", systemImage: "info.circle")
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
        .padding(16)
        .glassSurface(cornerRadius: 22)
    }
}

// MARK: - Gaps

private struct GapCard: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        let lo = min(model.settings.minGap, model.settings.maxGap)
        let hi = max(model.settings.minGap, model.settings.maxGap)
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                CardTitle(title: "Time between orders", symbol: "hourglass")
                Text("\(Format.seconds(lo)) – \(Format.seconds(hi))")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.green)
                    .fixedSize()
            }
            Text("Every wait is picked at random between these two, so there's never a pattern.")
                .font(.footnote)
                .foregroundStyle(.secondary)
            StepSlider(title: "Shortest", steps: TimingMath.gapSteps, value: lo, format: Format.seconds) {
                model.setGap(min: $0)
            }
            StepSlider(title: "Longest", steps: TimingMath.gapSteps, value: hi, format: Format.seconds) {
                model.setGap(max: $0)
            }
        }
        .padding(16)
        .glassSurface(cornerRadius: 22)
    }
}

// MARK: - Bursts

private struct BurstCard: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        let chance = model.settings.burstChance
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                CardTitle(title: "Bursts", symbol: "sparkles")
                Text(chance == 0 ? "Off" : "\(chance)%")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.pink)
                    .fixedSize()
            }
            Text("Chance that an order is followed by a quick flurry of more, a few seconds apart.")
                .font(.footnote)
                .foregroundStyle(.secondary)
            Slider(
                value: Binding(
                    get: { Double(chance) },
                    set: { newValue in model.update { $0.burstChance = Int(newValue.rounded()) } }
                ),
                in: 0...100,
                step: 5
            )
            .tint(.pink)
            .accessibilityLabel("Burst chance")
            Stepper(value: model.binding(\.burstMax), in: 1...10) {
                LabeledContent(
                    "Burst size",
                    value: model.settings.burstMax <= 1 ? "Single orders" : "Up to \(model.settings.burstMax) orders"
                )
            }
            .disabled(chance == 0)
        }
        .padding(16)
        .glassSurface(cornerRadius: 22)
    }
}

// MARK: - How iPhone delivers them

private struct HowItWorksCard: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            CardTitle(title: "How iPhone delivers them", symbol: "iphone.gen3")
            Text("Orders are lined up ahead of time with iOS, so they arrive even when the app is closed. iPhone allows 64 at a time; the app tops the line back up whenever it runs. Turn on Always On in Alerts to keep it full around the clock.")
                .font(.footnote)
                .foregroundStyle(.secondary)
            if model.settings.running, let until = model.queuedUntil {
                Label("\(model.queuedCount) queued until \(Format.time(until))", systemImage: "checkmark.circle.fill")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(.green)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassSurface(cornerRadius: 22)
    }
}

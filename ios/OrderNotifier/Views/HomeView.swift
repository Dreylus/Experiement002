import Charts
import SwiftUI

struct HomeView: View {
    @Environment(AppModel.self) private var model
    @State private var confirmReset = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    ProfileHeader()
                    RevenueCard()
                    ActionButtons()
                    if model.settings.running { LiveStatusCard() }
                    if model.todayOrders > 0 { HourlyChartCard() }
                    RecentOrdersCard()
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 24)
            }
            .background(AppBackground())
            .navigationTitle("Today")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        Button {
                            Task { await model.sendTest(after: 0) }
                        } label: {
                            Label("Send Test Order", systemImage: "paperplane")
                        }
                        Button(role: .destructive) {
                            confirmReset = true
                        } label: {
                            Label("Reset Stats", systemImage: "arrow.counterclockwise")
                        }
                    } label: {
                        Image(systemName: "ellipsis")
                    }
                    .accessibilityLabel("More")
                }
            }
            .confirmationDialog("Reset today's numbers, all-time revenue and the recent orders?", isPresented: $confirmReset, titleVisibility: .visible) {
                Button("Reset Stats", role: .destructive) { model.resetStats() }
            }
        }
    }
}

// MARK: - Header

private struct ProfileHeader: View {
    @Environment(AppModel.self) private var model
    @State private var glow = false

    var body: some View {
        let live = model.settings.running
        VStack(spacing: 10) {
            ZStack(alignment: .bottom) {
                RoundedRectangle(cornerRadius: 104 * 0.2237 + 7, style: .continuous)
                    .strokeBorder(
                        AngularGradient(colors: [.green, .mint, .teal, .cyan, .green], center: .center),
                        lineWidth: 3
                    )
                    .frame(width: 104, height: 104)
                    .shadow(color: .green.opacity(glow ? 0.7 : 0.15), radius: glow ? 16 : 4)
                    .opacity(live ? 1 : 0)
                BrandIconView(size: 88)
                    .frame(width: 104, height: 104)
                if live {
                    HStack(spacing: 4) {
                        Circle().fill(.white).frame(width: 6, height: 6)
                        Text("LIVE").font(.caption2.weight(.heavy)).kerning(1)
                    }
                    .foregroundStyle(.white)
                    .padding(.horizontal, 9)
                    .padding(.vertical, 3)
                    .background(.green, in: Capsule())
                    .offset(y: 10)
                    .transition(.scale.combined(with: .opacity))
                }
            }
            .padding(.bottom, live ? 8 : 0)
            .onAppear { startGlow() }

            VStack(spacing: 2) {
                Text(model.settings.storeName)
                    .font(.title2.weight(.bold))
                Text(model.settings.fromText.isEmpty ? "Add a “from” text in Customize" : "from \(model.settings.fromText)")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 4)
        .animation(.spring(duration: 0.4), value: live)
    }

    private func startGlow() {
        withAnimation(.easeInOut(duration: 1.1).repeatForever(autoreverses: true)) { glow = true }
    }
}

// MARK: - Revenue

private struct RevenueCard: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        let currency = model.settings.currency
        VStack(spacing: 14) {
            VStack(spacing: 2) {
                Text("Revenue today")
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(.secondary)
                Text(Format.money(model.todayCents, currency))
                    .font(.system(size: 44, weight: .bold, design: .rounded))
                    .contentTransition(.numericText(value: Double(model.todayCents)))
                    .lineLimit(1)
                    .minimumScaleFactor(0.5)
                    .accessibilityIdentifier("home.revenue")
            }
            Divider().opacity(0.4)
            HStack {
                StatTile(value: Format.count(model.todayOrders), label: "Orders")
                StatTile(value: Format.moneyShort(model.averageCents, currency), label: "Avg order")
                StatTile(value: Format.moneyShort(model.log.totalCents, currency), label: "All time")
            }
        }
        .padding(18)
        .frame(maxWidth: .infinity)
        .glassSurface()
        .animation(.snappy, value: model.todayCents)
    }
}

// MARK: - Actions

private struct ActionButtons: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        let live = model.settings.running
        GlassGroup(spacing: 12) {
            VStack(spacing: 10) {
                Button {
                    Task { await model.toggleRunning() }
                } label: {
                    Label(live ? "Pause Orders" : "Go Live",
                          systemImage: live ? "pause.fill" : "dot.radiowaves.left.and.right")
                        .font(.headline)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 6)
                }
                .prominentGlassButton(tint: live ? Color.orange : Color.green)
                .controlSize(.large)
                .accessibilityIdentifier("home.goLive")
                .sensoryFeedback(.impact(weight: .medium), trigger: live)

                HStack(spacing: 10) {
                    Button {
                        Task { await model.sendTest(after: 0) }
                    } label: {
                        Label("Send Test", systemImage: "paperplane.fill")
                            .frame(maxWidth: .infinity)
                    }
                    .glassButton()
                    .controlSize(.large)
                    .accessibilityIdentifier("home.sendTest")

                    Button {
                        Task { await model.sendTest(after: 5) }
                    } label: {
                        Label("Test in 5s", systemImage: "timer")
                            .frame(maxWidth: .infinity)
                    }
                    .glassButton()
                    .controlSize(.large)
                    .accessibilityIdentifier("home.testDelayed")
                }
            }
        }
    }
}

// MARK: - Live status

private struct LiveStatusCard: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 10) {
                Image(systemName: "dot.radiowaves.left.and.right")
                    .font(.title3)
                    .foregroundStyle(.green)
                    .symbolEffect(.pulse, options: .repeating)
                VStack(alignment: .leading, spacing: 1) {
                    if let next = model.nextOrderDate {
                        Text("Next order in \(Text(timerInterval: Date()...max(next, Date()), countsDown: true))")
                            .font(.headline)
                            .monospacedDigit()
                    } else {
                        Text("Lining up orders…").font(.headline)
                    }
                    if let until = model.queuedUntil {
                        Text("\(model.queuedCount) orders queued until \(Format.time(until))")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            if !model.settings.alwaysOn {
                Text("iPhone lines up 64 alerts at a time. Open the app now and then to keep them coming, or turn on Always On in Alerts.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassSurface(cornerRadius: 22)
    }
}

// MARK: - Chart

private struct HourlyChartCard: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        let cents = model.hourCents
        VStack(alignment: .leading, spacing: 10) {
            CardTitle(title: "Sales by hour", symbol: "chart.bar.fill")
            Chart {
                ForEach(0..<24, id: \.self) { hour in
                    BarMark(
                        x: .value("Hour", hour),
                        y: .value("Revenue", Double(cents[hour]) / 100)
                    )
                    .foregroundStyle(Color.green.gradient)
                    .cornerRadius(3)
                }
            }
            .chartXAxis {
                AxisMarks(values: [0, 6, 12, 18]) { value in
                    AxisValueLabel {
                        if let hour = value.as(Int.self) { Text(Format.hour(hour)) }
                    }
                }
            }
            .chartYAxis(.hidden)
            .frame(height: 110)
        }
        .padding(16)
        .glassSurface(cornerRadius: 22)
    }
}

// MARK: - Recent orders

private struct RecentOrdersCard: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            CardTitle(title: "Recent Orders", symbol: "bag.fill")
                .padding(.horizontal, 16)
                .padding(.top, 14)
            if model.log.recent.isEmpty {
                ContentUnavailableView {
                    Label("No orders yet", systemImage: "bag")
                } description: {
                    Text("Tap Go Live or Send Test and watch them roll in.")
                }
                .padding(.vertical, 8)
            } else {
                TimelineView(.periodic(from: .now, by: 30)) { context in
                    LazyVStack(spacing: 0) {
                        ForEach(model.log.recent.prefix(25)) { order in
                            OrderRow(order: order, now: context.date)
                            if order.id != model.log.recent.prefix(25).last?.id {
                                Divider().padding(.leading, 64)
                            }
                        }
                    }
                }
                .padding(.bottom, 8)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassSurface(cornerRadius: 22)
    }
}

private struct OrderRow: View {
    @Environment(AppModel.self) private var model
    let order: LoggedOrder
    let now: Date

    var body: some View {
        HStack(spacing: 12) {
            BrandIconView(size: 36)
            VStack(alignment: .leading, spacing: 1) {
                Text(order.title)
                    .font(.body.weight(.semibold))
                    .lineLimit(1)
                Text(Format.items(order.items))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            Spacer(minLength: 8)
            VStack(alignment: .trailing, spacing: 1) {
                Text("+" + Format.money(order.cents, model.settings.currency))
                    .font(.body.weight(.semibold))
                    .foregroundStyle(.green)
                    .monospacedDigit()
                Text(Format.ago(order.date, now: now))
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 9)
        .accessibilityElement(children: .combine)
    }
}

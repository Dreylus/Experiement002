import SwiftUI

enum AppTab: String, Hashable {
    case home, customize, timing, alerts
}

struct RootView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.scenePhase) private var scenePhase
    @State private var tab: AppTab = .home

    var body: some View {
        tabs
            .sheet(isPresented: onboardingShown) {
                OnboardingView()
                    .environment(model)
                    .interactiveDismissDisabled()
            }
            .alert(
                "Something went wrong",
                isPresented: Binding(get: { model.lastError != nil }, set: { if !$0 { model.lastError = nil } })
            ) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(model.lastError ?? "")
            }
            .onChange(of: scenePhase, initial: true) { _, phase in
                switch phase {
                case .active: model.appBecameActive()
                case .background: model.appEnteredBackground()
                default: break
                }
            }
            .onOpenURL { url in
                if let target = AppTab(rawValue: url.host() ?? "") { tab = target }
            }
    }

    @ViewBuilder
    private var tabs: some View {
        if #available(iOS 18.0, *) {
            TabView(selection: $tab) {
                Tab("Home", systemImage: "house.fill", value: AppTab.home) { HomeView() }
                Tab("Customize", systemImage: "paintbrush.pointed.fill", value: AppTab.customize) { CustomizeView() }
                Tab("Timing", systemImage: "timer", value: AppTab.timing) { TimingView() }
                Tab("Alerts", systemImage: "bell.badge.fill", value: AppTab.alerts) { AlertsView() }
                    .badge(model.health.problems.count)
            }
            .modifier(LiquidTabBar(tab: $tab))
        } else {
            TabView(selection: $tab) {
                HomeView()
                    .tabItem { Label("Home", systemImage: "house.fill") }
                    .tag(AppTab.home)
                CustomizeView()
                    .tabItem { Label("Customize", systemImage: "paintbrush.pointed.fill") }
                    .tag(AppTab.customize)
                TimingView()
                    .tabItem { Label("Timing", systemImage: "timer") }
                    .tag(AppTab.timing)
                AlertsView()
                    .tabItem { Label("Alerts", systemImage: "bell.badge.fill") }
                    .tag(AppTab.alerts)
                    .badge(model.health.problems.count)
            }
        }
    }

    private var onboardingShown: Binding<Bool> {
        Binding(get: { !model.settings.onboarded }, set: { _ in })
    }
}

/// iOS 26: the tab bar shrinks while scrolling and a live "mini player" sits above it.
private struct LiquidTabBar: ViewModifier {
    @Binding var tab: AppTab

    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            content
                .tabBarMinimizeBehavior(.onScrollDown)
                .tabViewBottomAccessory { LiveAccessory(tab: $tab) }
        } else {
            content
        }
    }
}

/// Like Music's mini player: live status, a countdown and a play/pause button.
@available(iOS 26.0, *)
private struct LiveAccessory: View {
    @Environment(AppModel.self) private var model
    @Environment(\.tabViewBottomAccessoryPlacement) private var placement
    @Binding var tab: AppTab

    var body: some View {
        let live = model.settings.running
        HStack(spacing: 10) {
            // Next to the shrunken tab bar there's only room for the text and the button.
            if placement != .inline { BrandIconView(size: 30) }
            VStack(alignment: .leading, spacing: 0) {
                Text(live ? "Live" : "Paused")
                    .font(.subheadline.weight(.semibold))
                if live, let next = model.nextOrderDate {
                    Text("Next order in \(Text(timerInterval: Date()...max(next, Date()), countsDown: true))")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .monospacedDigit()
                } else {
                    Text(live ? "Lining up orders…" : "Tap play to go live")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            .lineLimit(1)
            Spacer(minLength: 4)
            Button {
                Task { await model.toggleRunning() }
            } label: {
                Image(systemName: live ? "pause.fill" : "play.fill")
                    .font(.title3)
                    .contentTransition(.symbolEffect(.replace))
                    .frame(width: 34, height: 34)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(live ? "Pause orders" : "Go live")
        }
        .padding(.horizontal, 14)
        .contentShape(Rectangle())
        .onTapGesture { tab = .home }
    }
}

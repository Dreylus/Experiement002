import SwiftUI

/// First-launch welcome screen. "Continue" asks for permission to show alerts.
struct OnboardingView: View {
    @Environment(AppModel.self) private var model
    @State private var working = false

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(spacing: 28) {
                    BrandIconView(size: 96)
                        .shadow(color: .black.opacity(0.15), radius: 12, y: 6)
                        .padding(.top, 48)
                    Text("Welcome to \(model.appName)")
                        .font(.largeTitle.weight(.bold))
                        .multilineTextAlignment(.center)
                    VStack(alignment: .leading, spacing: 22) {
                        FeatureRow(symbol: "bell.badge.fill", color: .red, title: "Real-looking order alerts",
                                   text: "Orders slide in at the top of the screen with your store's icon, name and sound.")
                        FeatureRow(symbol: "dice.fill", color: .orange, title: "Never predictable",
                                   text: "Random gaps and sudden bursts, tuned exactly how you like.")
                        FeatureRow(symbol: "chart.line.uptrend.xyaxis", color: .green, title: "Watch it add up",
                                   text: "Today's orders, revenue and a live feed, right on Home.")
                        FeatureRow(symbol: "lock.shield.fill", color: .blue, title: "Just for you",
                                   text: "Everything stays on this iPhone.")
                    }
                    .padding(.horizontal, 8)
                }
                .padding(.horizontal, 24)
            }

            Button {
                working = true
                Task {
                    await model.finishOnboarding()
                    working = false
                }
            } label: {
                Text("Continue")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
            }
            .prominentGlassButton(tint: Color.green)
            .controlSize(.large)
            .disabled(working)
            .padding(.horizontal, 24)
            .padding(.bottom, 24)
            .accessibilityIdentifier("onboarding.continue")
        }
        .background(AppBackground())
    }
}

private struct FeatureRow: View {
    let symbol: String
    let color: Color
    let title: String
    let text: String

    var body: some View {
        HStack(alignment: .top, spacing: 16) {
            Image(systemName: symbol)
                .font(.title2)
                .foregroundStyle(color)
                .frame(width: 36)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.headline)
                Text(text).font(.subheadline).foregroundStyle(.secondary)
            }
        }
    }
}

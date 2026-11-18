import SwiftUI
import UniformTypeIdentifiers

struct AlertsView: View {
    @Environment(AppModel.self) private var model
    @State private var importing = false

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    StatusRow()
                }

                Section {
                    Picker("Sound", selection: model.soundBinding) {
                        ForEach(SoundChoice.allCases) { choice in
                            Text(choice.title).tag(choice)
                        }
                    }
                    .pickerStyle(.segmented)
                    .accessibilityIdentifier("alerts.sound")

                    if model.settings.sound == .custom {
                        Button {
                            importing = true
                        } label: {
                            LabeledContent {
                                Text(model.hasCustomSound ? (model.settings.customSoundName ?? "Custom") : "None")
                                    .lineLimit(1)
                            } label: {
                                Label("Choose Sound File…", systemImage: "waveform")
                            }
                        }
                        if model.hasCustomSound {
                            Button {
                                model.togglePreview()
                            } label: {
                                Label(model.isPlayingPreview ? "Stop" : "Play Sound",
                                      systemImage: model.isPlayingPreview ? "stop.fill" : "play.fill")
                            }
                            Button(role: .destructive) {
                                model.removeSound()
                            } label: {
                                Label("Remove Sound", systemImage: "trash")
                            }
                        }
                    }
                } header: {
                    Text("Sound")
                } footer: {
                    Text(soundFooter)
                }

                Section {
                    Toggle(isOn: model.alwaysOnBinding) {
                        Label {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Always On")
                                Text(alwaysOnStatus)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        } icon: {
                            Image(systemName: "infinity")
                        }
                    }
                    .accessibilityIdentifier("alerts.alwaysOn")
                } header: {
                    Text("Background")
                } footer: {
                    Text("iPhone lets an app line up 64 alerts at a time. Always On keeps the app quietly running in the background (it plays silence, mixed with your music) so the line never runs out. Uses more battery. Swiping the app away stops it.")
                }

                Section("Tips") {
                    TipRow(symbol: "moon.fill", color: .indigo, title: "Focus and Do Not Disturb",
                           text: "They silence these alerts and your custom sound, like any other app.")
                    TipRow(symbol: "clock.badge.exclamationmark.fill", color: .orange, title: "Scheduled Summary",
                           text: "Keep this app on Immediate Delivery so orders pop up right away.")
                    TipRow(symbol: "square.stack.3d.up.fill", color: .blue, title: "Stacks",
                           text: "Orders group into one stack on the Lock Screen, like a real store app.")
                    TipRow(symbol: "wand.and.stars", color: .pink, title: "Siri and Shortcuts",
                           text: "Say “Go live in \(model.appName)”, put Go Live on the Action Button, or make a Shortcuts automation that starts orders every morning.")
                    TipRow(symbol: "lock.shield.fill", color: .green, title: "Private",
                           text: "Nothing leaves this iPhone. No account, no server.")
                }

                Section {
                    LabeledContent("Version", value: version)
                }
            }
            .scrollContentBackground(.hidden)
            .background(AppBackground())
            .navigationTitle("Alerts")
            .fileImporter(isPresented: $importing, allowedContentTypes: [.audio]) { result in
                if case .success(let url) = result {
                    Task { await model.importSound(from: url) }
                }
            }
            .task { await model.refreshHealth() }
        }
    }

    private var soundFooter: String {
        if model.settings.sound == .custom {
            var text = "Plays only for these order alerts. MP3, M4A, WAV, AIFF or CAF work; they're converted for iPhone and cut to 30 seconds."
            if let seconds = model.settings.customSoundSeconds, model.hasCustomSound {
                text += String(format: " This one is %.1f s.", seconds)
            }
            return text
        }
        return "Default uses the normal iPhone alert sound. Silent still shows the banner."
    }

    private var alwaysOnStatus: String {
        if model.isKeepingAlive { return "On · keeping \(model.queuedCount) orders lined up" }
        if model.settings.alwaysOn { return "Starts when orders are live" }
        return "Off"
    }

    private var version: String {
        let short = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0"
        let build = Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "1"
        return "\(short) (\(build))"
    }
}

// MARK: - Banner health

private struct StatusRow: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        let problems = model.health.problems
        let ok = problems.isEmpty
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: ok ? "checkmark.seal.fill" : "exclamationmark.triangle.fill")
                    .font(.title2)
                    .foregroundStyle(ok ? Color.green : Color.orange)
                    .symbolEffect(.bounce, value: ok)
                VStack(alignment: .leading, spacing: 3) {
                    Text(ok ? "Banners are ready" : "Banners might not show")
                        .font(.headline)
                    if ok {
                        Text("New orders slide down from the top, over other apps and on the Lock Screen.")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(problems, id: \.self) { problem in
                            Text("• \(problem)")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            }
            if model.health.status == .notDetermined {
                Button("Allow Notifications") {
                    Task { _ = await model.ensureAuthorized() }
                }
                .prominentGlassButton(tint: Color.green)
            } else {
                Button("Open Notification Settings") {
                    model.openNotificationSettings()
                }
                .glassButton()
            }
        }
        .padding(.vertical, 6)
        .accessibilityIdentifier("alerts.status")
    }
}

private struct TipRow: View {
    let symbol: String
    let color: Color
    let title: String
    let text: String

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: symbol)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.white)
                .frame(width: 30, height: 30)
                .background(color.gradient, in: RoundedRectangle(cornerRadius: 7, style: .continuous))
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.subheadline.weight(.semibold))
                Text(text)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 2)
    }
}

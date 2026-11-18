import AVFoundation
import BackgroundTasks
import Foundation
import Observation
import SwiftUI
import UIKit
import UserNotifications

/// Must match BGTaskSchedulerPermittedIdentifiers in Info.plist (see ios/scripts/prepare_branding.py).
let refreshTaskID = "app.ordernotifier.refresh"

/// The whole app state. iOS can't run an app forever in the background, so orders are planned
/// ahead: up to `maxQueued` alerts at random times are handed to iOS, and the queue is topped up
/// whenever the app gets to run (opening it, tapping an alert, background refresh, Always On).
@Observable
@MainActor
final class AppModel {
    static let shared = AppModel()

    /// iOS keeps at most 64 pending alerts per app; leave room for tests.
    static let maxQueued = 60

    private(set) var settings: OrderSettings
    private(set) var planned: [PlannedOrder]
    private(set) var log: OrderLog
    private(set) var health = NotificationHealth()
    private(set) var icons: [AppIconOption] = AppIconOption.load()
    private(set) var currentIconName: String? = nil
    private(set) var isPlayingPreview = false
    var timelineSeed: UInt64 = .random(in: 1...UInt64.max)
    var lastError: String? = nil

    @ObservationIgnored private let center = UNUserNotificationCenter.current()
    @ObservationIgnored private let keepAlive = KeepAlive()
    @ObservationIgnored private var replanTask: Task<Void, Never>?
    @ObservationIgnored private var ticker: Timer?
    @ObservationIgnored private var aliveTimer: Timer?
    @ObservationIgnored private var previewPlayer: AVAudioPlayer?
    @ObservationIgnored private var userSetNumber = false

    private init() {
        if ProcessInfo.processInfo.arguments.contains("-resetForUITests") {
            Storage.resetAll()
        }
        settings = Storage.loadSettings()
        planned = Storage.loadPlanned()
        log = Storage.loadLog()
        // UIApplication isn't ready yet when SwiftUI creates the model, so the icon and
        // Always On are picked up in appBecameActive().
        startTicker()
    }

    // MARK: - Derived values for the screens

    var appName: String {
        (Bundle.main.object(forInfoDictionaryKey: "CFBundleDisplayName") as? String) ?? "Store"
    }

    var isToday: Bool { log.dayKey == OrderLog.key(for: Date()) }
    var todayOrders: Int { isToday ? log.todayOrders : 0 }
    var todayCents: Int { isToday ? log.todayCents : 0 }
    var hourCents: [Int] { isToday ? log.hourCents : Array(repeating: 0, count: 24) }
    var averageCents: Int { todayOrders > 0 ? todayCents / todayOrders : 0 }

    var nextOrderDate: Date? {
        let now = Date()
        return planned.map(\.fireDate).filter { $0 > now }.min()
    }

    var queuedCount: Int { planned.count }
    var queuedUntil: Date? { planned.map(\.fireDate).max() }

    /// The number the next alert will show.
    var upcomingNumber: Int {
        planned.filter { !$0.isTest }.map(\.number).min() ?? settings.nextOrder
    }

    var currentIconPreview: String {
        icons.first { $0.iconName == currentIconName }?.preview ?? "BrandIcon"
    }

    func title(for number: Int) -> String { settings.titlePrefix + String(number) }

    var subtitle: String? {
        settings.showSubtitle && !settings.storeName.isEmpty ? "from \(settings.storeName)" : nil
    }

    func body(cents: Int, items: Int) -> String {
        var text = "\(Format.money(cents, settings.currency)), \(Format.items(items))"
        let from = settings.fromText.trimmingCharacters(in: .whitespaces)
        if !from.isEmpty { text += " from \(from)" }
        if settings.storeSuffix, !settings.storeName.isEmpty { text += " · \(settings.storeName)" }
        return text
    }

    /// Sample alert for the previews: a mid-range total and up to two items.
    var previewBody: String {
        let cents = (settings.minPrice + settings.maxPrice) * 50
        return body(cents: cents, items: min(2, max(1, settings.maxItems)))
    }

    // MARK: - Editing settings

    /// Changes settings; when orders are running, the queue is rebuilt shortly after so
    /// pending alerts pick up the new text, timing and sound.
    func update(replan: Bool = true, _ change: (inout OrderSettings) -> Void) {
        var copy = settings
        change(&copy)
        guard copy != settings else { return }
        settings = copy
        Storage.save(settings)
        if replan { scheduleReplan() }
    }

    func binding<Value>(_ keyPath: WritableKeyPath<OrderSettings, Value>, replan: Bool = true) -> Binding<Value> {
        Binding(
            get: { self.settings[keyPath: keyPath] },
            set: { newValue in self.update(replan: replan) { $0[keyPath: keyPath] = newValue } }
        )
    }

    /// Text field binding that trims to a maximum length.
    func textBinding(_ keyPath: WritableKeyPath<OrderSettings, String>, max: Int) -> Binding<String> {
        Binding(
            get: { self.settings[keyPath: keyPath] },
            set: { newValue in self.update { $0[keyPath: keyPath] = String(newValue.prefix(max)) } }
        )
    }

    var nextNumberBinding: Binding<Int> {
        Binding(
            get: { self.upcomingNumber },
            set: { newValue in self.setNextNumber(newValue) }
        )
    }

    func setNextNumber(_ number: Int) {
        let value = min(max(number, 0), 999_999_999)
        guard value != upcomingNumber else { return }
        userSetNumber = true
        update { $0.nextOrder = value }
    }

    func setPrice(min newMin: Int? = nil, max newMax: Int? = nil) {
        update { s in
            if let newMin {
                s.minPrice = newMin
                if s.maxPrice < newMin { s.maxPrice = newMin }
            }
            if let newMax {
                s.maxPrice = newMax
                if s.minPrice > newMax { s.minPrice = newMax }
            }
        }
    }

    func setGap(min newMin: Int? = nil, max newMax: Int? = nil) {
        update { s in
            if let newMin {
                s.minGap = newMin
                if s.maxGap < newMin { s.maxGap = newMin }
            }
            if let newMax {
                s.maxGap = newMax
                if s.minGap > newMax { s.minGap = newMax }
            }
        }
    }

    func apply(_ preset: TimingPreset) {
        update { s in
            s.minGap = preset.minGap
            s.maxGap = preset.maxGap
            s.burstChance = preset.chance
            s.burstMax = preset.burstMax
        }
    }

    var activePreset: TimingPreset? { TimingMath.presets.first { $0.matches(settings) } }

    // MARK: - Running

    func toggleRunning() async {
        if settings.running { stop() } else { await start() }
    }

    func start() async {
        guard await ensureAuthorized() else { return }
        update(replan: false) { $0.running = true }
        await fill()
        applyAlwaysOn()
    }

    func stop() {
        cancelFutureRegular(resetNumbering: true)
        update(replan: false) { $0.running = false }
        applyAlwaysOn()
    }

    /// Sends one order now, or after `delay` seconds (to see the banner from the Home Screen).
    func sendTest(after delay: TimeInterval) async {
        guard await ensureAuthorized() else { return }
        let wasRunning = settings.running
        if wasRunning { cancelFutureRegular(resetNumbering: true) }
        let order = makeOrder(at: Date().addingTimeInterval(delay), isTest: true)
        planned.append(order)
        saveQueue()
        await schedule(order)
        guard wasRunning else { return }
        // iOS works through the refilled queue before showing an alert that is due now,
        // so let the test banner appear first.
        if delay < 2 { try? await Task.sleep(for: .seconds(1.5)) }
        await fill()
    }

    func resetStats() {
        log = OrderLog()
        Storage.save(log)
    }

    // MARK: - Lifecycle

    func appBecameActive() {
        currentIconName = UIApplication.shared.alternateIconName
        Task {
            await refreshHealth()
            reconcile()
            if settings.running { await fill() }
            applyAlwaysOn()
        }
    }

    func appEnteredBackground() {
        scheduleBackgroundRefresh()
    }

    func openedFromNotification() {
        reconcile()
        Task { if settings.running { await fill() } }
    }

    func backgroundRefresh() async {
        reconcile()
        if settings.running { await fill() }
        applyAlwaysOn()
        scheduleBackgroundRefresh()
    }

    func finishOnboarding() async {
        _ = await ensureAuthorized()
        update(replan: false) { $0.onboarded = true }
    }

    private func scheduleBackgroundRefresh() {
        guard settings.running else { return }
        let request = BGAppRefreshTaskRequest(identifier: refreshTaskID)
        request.earliestBeginDate = Date().addingTimeInterval(15 * 60)
        try? BGTaskScheduler.shared.submit(request)
    }

    // MARK: - Always On

    func setAlwaysOn(_ on: Bool) {
        update(replan: false) { $0.alwaysOn = on }
        applyAlwaysOn()
    }

    var alwaysOnBinding: Binding<Bool> {
        Binding(get: { self.settings.alwaysOn }, set: { self.setAlwaysOn($0) })
    }

    var isKeepingAlive: Bool { settings.running && settings.alwaysOn }

    private func applyAlwaysOn() {
        if settings.running && settings.alwaysOn {
            keepAlive.start()
            if aliveTimer == nil {
                aliveTimer = Timer.scheduledTimer(withTimeInterval: 20, repeats: true) { [weak self] _ in
                    MainActor.assumeIsolated {
                        guard let self else { return }
                        Task { await self.fill() }
                    }
                }
            }
        } else {
            keepAlive.stop()
            aliveTimer?.invalidate()
            aliveTimer = nil
        }
    }

    // MARK: - Notifications permission and health

    func ensureAuthorized() async -> Bool {
        let current = await center.notificationSettings()
        switch current.authorizationStatus {
        case .authorized, .provisional, .ephemeral:
            await refreshHealth()
            return true
        case .notDetermined:
            let granted = (try? await center.requestAuthorization(options: [.alert, .sound, .badge])) ?? false
            await refreshHealth()
            return granted
        default:
            await refreshHealth()
            lastError = "Notifications are turned off for this app. Turn them on in Settings › Notifications."
            return false
        }
    }

    func refreshHealth() async {
        let s = await center.notificationSettings()
        health = NotificationHealth(
            status: s.authorizationStatus,
            alertsOn: s.alertSetting == .enabled,
            bannersOn: s.alertStyle != .none,
            soundsOn: s.soundSetting == .enabled,
            scheduledSummary: s.scheduledDeliverySetting == .enabled
        )
    }

    func openNotificationSettings() {
        if let url = URL(string: UIApplication.openNotificationSettingsURLString) {
            UIApplication.shared.open(url)
        }
    }

    // MARK: - Sound

    func setSound(_ choice: SoundChoice) {
        update { $0.sound = choice }
    }

    var soundBinding: Binding<SoundChoice> {
        Binding(get: { self.settings.sound }, set: { self.setSound($0) })
    }

    var hasCustomSound: Bool {
        guard let file = settings.customSoundFile else { return false }
        return SoundLibrary.exists(file)
    }

    func importSound(from url: URL) async {
        do {
            let imported = try await Task.detached { try SoundLibrary.importSound(from: url) }.value
            let old = settings.customSoundFile
            update { s in
                s.customSoundFile = imported.fileName
                s.customSoundName = imported.displayName
                s.customSoundSeconds = imported.seconds
                s.sound = .custom
            }
            if let old, old != imported.fileName { SoundLibrary.delete(old) }
        } catch {
            lastError = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
        }
    }

    func removeSound() {
        stopPreview()
        if let file = settings.customSoundFile { SoundLibrary.delete(file) }
        update { s in
            s.customSoundFile = nil
            s.customSoundName = nil
            s.customSoundSeconds = nil
            if s.sound == .custom { s.sound = .system }
        }
    }

    func togglePreview() {
        if isPlayingPreview { stopPreview(); return }
        guard let file = settings.customSoundFile, SoundLibrary.exists(file) else { return }
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .default, options: [.mixWithOthers])
        try? AVAudioSession.sharedInstance().setActive(true)
        previewPlayer = try? AVAudioPlayer(contentsOf: SoundLibrary.url(for: file))
        isPlayingPreview = previewPlayer?.play() == true
        if isPlayingPreview, let player = previewPlayer {
            let duration = player.duration
            Task {
                try? await Task.sleep(for: .seconds(duration + 0.1))
                // Only stop this playback, not one started again in the meantime.
                if previewPlayer === player { stopPreview() }
            }
        }
    }

    func stopPreview() {
        previewPlayer?.stop()
        previewPlayer = nil
        isPlayingPreview = false
    }

    private func notificationSound() -> UNNotificationSound? {
        switch settings.sound {
        case .system:
            return .default
        case .silent:
            return nil
        case .custom:
            if let file = settings.customSoundFile, SoundLibrary.exists(file) {
                return UNNotificationSound(named: UNNotificationSoundName(rawValue: file))
            }
            return .default
        }
    }

    // MARK: - App icon

    func setIcon(_ option: AppIconOption) async {
        guard UIApplication.shared.supportsAlternateIcons, option.iconName != currentIconName else { return }
        do {
            try await UIApplication.shared.setAlternateIconName(option.iconName)
            currentIconName = option.iconName
        } catch {
            lastError = "Couldn't change the icon: \(error.localizedDescription)"
        }
    }

    // MARK: - The queue

    /// Moves orders whose time has passed into the log.
    func reconcile() {
        let now = Date()
        guard planned.contains(where: { $0.fireDate <= now }) else { return }
        let fired = planned.filter { $0.fireDate <= now }.sorted { $0.fireDate < $1.fireDate }
        planned.removeAll { $0.fireDate <= now }
        let today = OrderLog.key(for: now)
        var newLog = log
        for order in fired { newLog.record(order, title: title(for: order.number), today: today) }
        log = newLog
        Storage.save(log)
        saveQueue()
    }

    /// Tops the queue back up to `maxQueued` alerts at random times after the last one.
    func fill() async {
        reconcile()
        guard settings.running else { return }
        let lo = Double(max(1, min(settings.minGap, settings.maxGap)))
        let hi = Double(max(1, max(settings.minGap, settings.maxGap)))
        let lastRegular = planned.filter { !$0.isTest }.map(\.fireDate).max() ?? .distantPast
        var cursor = max(lastRegular, Date())
        var added: [PlannedOrder] = []
        fillLoop: while planned.count + added.count < Self.maxQueued {
            cursor = cursor.addingTimeInterval(Double.random(in: lo...hi))
            added.append(makeOrder(at: cursor, isTest: false))
            if settings.burstMax > 1, Int.random(in: 0..<100) < settings.burstChance {
                let extra = Int.random(in: 1...(settings.burstMax - 1))
                for _ in 0..<extra {
                    if planned.count + added.count >= Self.maxQueued { break fillLoop }
                    cursor = cursor.addingTimeInterval(Double.random(in: 1...5))
                    added.append(makeOrder(at: cursor, isTest: false))
                }
            }
        }
        guard !added.isEmpty else { return }
        planned.append(contentsOf: added)
        saveQueue()
        for order in added { await schedule(order) }
    }

    private func scheduleReplan() {
        guard settings.running else {
            userSetNumber = false
            return
        }
        replanTask?.cancel()
        replanTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(1.2))
            guard !Task.isCancelled, let self else { return }
            self.cancelFutureRegular(resetNumbering: !self.userSetNumber)
            self.userSetNumber = false
            await self.fill()
        }
    }

    /// Takes back every regular alert that hasn't been shown yet.
    private func cancelFutureRegular(resetNumbering: Bool) {
        reconcile()
        let future = planned.filter { !$0.isTest }
        guard !future.isEmpty else { return }
        center.removePendingNotificationRequests(withIdentifiers: future.map(\.id))
        if resetNumbering, let lowest = future.map(\.number).min() {
            settings.nextOrder = lowest
        }
        planned.removeAll { !$0.isTest }
        saveQueue()
    }

    private func makeOrder(at date: Date, isTest: Bool) -> PlannedOrder {
        let lo = min(settings.minPrice, settings.maxPrice)
        let hi = max(settings.minPrice, settings.maxPrice)
        let cents = Int(((Double(lo) + Double.random(in: 0...1) * Double(hi - lo)) * 100).rounded())
        let items = Int.random(in: 1...max(1, settings.maxItems))
        let number = settings.nextOrder
        settings.nextOrder = number + 1
        return PlannedOrder(
            id: "order-\(number)-\(UUID().uuidString.prefix(8))",
            number: number, cents: cents, items: items, fireDate: date, isTest: isTest
        )
    }

    private func schedule(_ order: PlannedOrder) async {
        let content = UNMutableNotificationContent()
        content.title = title(for: order.number)
        if let subtitle { content.subtitle = subtitle }
        content.body = body(cents: order.cents, items: order.items)
        content.sound = notificationSound()
        content.threadIdentifier = "orders"
        content.interruptionLevel = .active
        let interval = order.fireDate.timeIntervalSinceNow
        let trigger: UNNotificationTrigger? = interval > 0.5
            ? UNTimeIntervalNotificationTrigger(timeInterval: interval, repeats: false)
            : nil
        let request = UNNotificationRequest(identifier: order.id, content: content, trigger: trigger)
        do {
            try await center.add(request)
            // The queue can be rebuilt while alerts are being handed over; take back any
            // alert that is no longer part of it so nothing fires twice or unlogged.
            if !planned.contains(where: { $0.id == order.id }) {
                center.removePendingNotificationRequests(withIdentifiers: [order.id])
            }
        } catch {
            lastError = "iPhone didn't accept an order alert: \(error.localizedDescription)"
        }
    }

    private func saveQueue() {
        Storage.save(planned)
        Storage.save(settings)
    }

    private func startTicker() {
        ticker = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in
            MainActor.assumeIsolated { self?.reconcile() }
        }
    }
}

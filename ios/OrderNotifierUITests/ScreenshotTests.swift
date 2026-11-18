import XCTest

/// Taps through the app on a simulator and saves screenshots: to $SCREENSHOT_DIR when it's set
/// (CI passes TEST_RUNNER_SCREENSHOT_DIR) and to the test results either way.
final class ScreenshotTests: XCTestCase {
    private let app = XCUIApplication()
    private let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")

    override func setUpWithError() throws {
        continueAfterFailure = true
        app.launchArguments += ["-resetForUITests"]
    }

    func testTour() throws {
        app.launch()

        // Welcome sheet, then the notification permission prompt.
        let welcome = app.buttons["onboarding.continue"]
        XCTAssertTrue(welcome.waitForExistence(timeout: 30), "The welcome screen didn't show")
        pause(1.5)
        snap("01-welcome")
        welcome.tap()
        allowNotificationsIfAsked()

        let goLive = app.buttons["home.goLive"]
        XCTAssertTrue(goLive.waitForExistence(timeout: 15), "Home didn't show")
        pause(1.5)
        snap("02-home")

        // Slow timing first, so only the test orders pop up while the screenshots are taken.
        openTab("Timing")
        tapIfExists(app.buttons["preset.Chill"])
        openTab("Home")

        // Go live, then a few test orders: the first one is caught as an in-app banner.
        // Going live hands 60 alerts to iOS first; a test order sent during that waits its turn.
        goLive.tap()
        pause(6)
        let sendTest = app.buttons["home.sendTest"]
        sendTest.tap()
        pause(2.8) // banners take ~2 s to slide in on the CI simulator and stay ~5 s
        snap("03-banner-in-app")
        for _ in 0..<3 {
            sendTest.tap()
            pause(0.9)
        }
        pause(14) // the banners come and go
        snap("04-home-live")
        app.swipeUp()
        pause(1.2)
        snap("05-home-feed")
        scrollToTop()

        // An order sliding in over the Home Screen.
        pause(8) // let the refill after the last test order finish
        app.buttons["home.testDelayed"].tap()
        XCUIDevice.shared.press(.home)
        pause(6.5)
        snap("06-banner-home-screen")

        // The stack in Notification Center: swipe down from the top left once the banner is gone.
        pause(7)
        let top = springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.2, dy: 0))
        let bottom = springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.2, dy: 0.7))
        top.press(forDuration: 0.05, thenDragTo: bottom, withVelocity: .fast, thenHoldForDuration: 0.1)
        pause(2)
        snap("07-notification-center")
        XCUIDevice.shared.press(.home)
        pause(1)
        app.activate()
        pause(1.5)

        openTab("Customize")
        snap("08-customize")
        app.swipeUp()
        pause(1.2)
        snap("09-customize-more")
        // Switch to another app icon (iOS confirms with an alert), then back.
        if tapIfExists(app.buttons["Bell icon"]) {
            if let ok = iconAlertOK() {
                snap("10-icon-switched")
                ok.tap()
                pause(1)
            }
            if tapIfExists(app.buttons["Default icon"]) {
                iconAlertOK()?.tap()
                pause(1)
            }
        }

        openTab("Timing")
        snap("11-timing")
        if tapIfExists(app.buttons["preset.Viral"]) {
            pause(1.5)
            snap("12-timing-viral")
            // Back to Chill: the chip row scrolled to Viral, so scroll it back first.
            app.buttons["preset.Viral"].swipeRight()
            pause(0.8)
            let chill = app.buttons["preset.Chill"]
            if chill.exists && chill.isHittable { chill.tap() }
            pause(0.8)
        }
        app.swipeUp()
        pause(1.2)
        snap("13-timing-more")

        openTab("Alerts")
        snap("14-alerts")
        app.swipeUp()
        app.swipeUp()
        pause(1.2)
        snap("15-alerts-more")
    }

    // MARK: - Helpers

    /// The "You have changed the icon" alert's OK button, from the app or from the system.
    private func iconAlertOK() -> XCUIElement? {
        for source in [app, springboard] {
            let ok = source.alerts.buttons["OK"]
            if ok.waitForExistence(timeout: 4) { return ok }
        }
        return nil
    }

    @discardableResult
    private func tapIfExists(_ element: XCUIElement) -> Bool {
        guard element.waitForExistence(timeout: 4) else { return false }
        element.tap()
        pause(0.8)
        return true
    }

    private func allowNotificationsIfAsked() {
        for label in ["Allow", "Allow Notifications"] {
            let button = springboard.alerts.buttons[label]
            if button.waitForExistence(timeout: label == "Allow" ? 8 : 1) {
                button.tap()
                return
            }
        }
    }

    /// Scrolling down shrinks the iOS 26 tab bar, so scroll back up before switching tabs.
    private func openTab(_ name: String) {
        scrollToTop()
        let button = app.tabBars.buttons[name]
        if !(button.exists && button.isHittable) {
            app.tabBars.buttons.firstMatch.tap()
            pause(0.8)
        }
        if button.waitForExistence(timeout: 5) {
            button.tap()
        } else {
            XCTFail("Couldn't find the \(name) tab")
        }
        pause(1.5)
    }

    private func scrollToTop() {
        app.swipeDown()
        app.swipeDown()
        pause(0.8)
    }

    private func pause(_ seconds: TimeInterval) {
        Thread.sleep(forTimeInterval: seconds)
    }

    private func snap(_ name: String) {
        let shot = XCUIScreen.main.screenshot()
        let attachment = XCTAttachment(screenshot: shot)
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
        guard let dir = ProcessInfo.processInfo.environment["SCREENSHOT_DIR"], !dir.isEmpty else { return }
        let folder = URL(fileURLWithPath: dir, isDirectory: true)
        try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        try? shot.pngRepresentation.write(to: folder.appendingPathComponent("\(name).png"))
    }
}

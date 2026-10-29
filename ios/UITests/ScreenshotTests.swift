import XCTest

/// CCRM-94 (iOS App): launches the demo states and saves a screenshot of each, in both orientations on iPad, so the
/// screens can be compared with design/2026-10-23-ios-wireframes.html. Doubles as a launch smoke test. Pass
/// SHOT_DIR (as TEST_RUNNER_SHOT_DIR to xcodebuild) to keep the PNGs; without it they are only attached to the result.
final class ScreenshotTests: XCTestCase {
    override func setUp() { continueAfterFailure = true }

    private func shoot(_ name: String, _ args: [String], landscape: Bool = false) {
        XCUIDevice.shared.orientation = landscape ? .landscapeLeft : .portrait
        let app = XCUIApplication()
        app.launchArguments = args
        app.launch()
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 10))
        sleep(2)
        let shot = XCUIScreen.main.screenshot()
        let attachment = XCTAttachment(screenshot: shot)
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
        if let dir = ProcessInfo.processInfo.environment["SHOT_DIR"] {
            try? shot.pngRepresentation.write(to: URL(fileURLWithPath: dir).appendingPathComponent("\(name).png"))
        }
        app.terminate()
    }

    func testDemoStates() {
        let pad = UIDevice.current.userInterfaceIdiom == .pad
        shoot("main", ["-demo", "standard"])
        if pad {
            shoot("main-landscape", ["-demo", "standard", "-tab", "product"], landscape: true)
            shoot("main-landscape-personal", ["-demo", "standard", "-tab", "personal"], landscape: true)
        }
        XCUIDevice.shared.orientation = .portrait
    }
}

import XCTest

/// CCRM-94 (iOS App) device pass: drives the real app on a connected iPad (real accounts, real settings) and attaches a
/// screenshot and the accessibility tree of every step, so the pass needs no one at the glass for the in-app parts. Skipped
/// unless TEST_RUNNER_DEVICE_PASS is set. It restores the settings it changes. Run it as docs/ios/ipad-pass-checklist.md §6
/// says; the screenshots show private data and stay out of the repo.
final class DevicePassTests: XCTestCase {
    var app: XCUIApplication!

    override func setUpWithError() throws {
        try XCTSkipUnless(ProcessInfo.processInfo.environment["DEVICE_PASS"] != nil, "device pass only")
        continueAfterFailure = true
        XCUIDevice.shared.orientation = .landscapeLeft   // the iPad's own orientation; C flips it and flips back
        app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 15))
        sleep(2)
    }

    // MARK: helpers

    func shot(_ name: String, tree: Bool = false) {
        sleep(1)
        let a = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        a.name = name
        a.lifetime = .keepAlways
        add(a)
        if tree {
            let t = XCTAttachment(string: app.debugDescription)
            t.name = "\(name).tree"
            t.lifetime = .keepAlways
            add(t)
        }
    }

    /// The first element whose label or title matches, among buttons, cells, static texts and switches.
    func find(_ label: String, contains: Bool = false) -> XCUIElement? {
        for q in [app.buttons, app.cells, app.staticTexts, app.switches, app.otherElements] {
            let predicate = NSPredicate(format: contains ? "label CONTAINS[c] %@" : "label ==[c] %@", label)
            let e = q.matching(predicate).firstMatch
            if e.exists { return e }
        }
        return nil
    }

    @discardableResult
    func tap(_ label: String, contains: Bool = false, wait: TimeInterval = 4) -> Bool {
        let end = Date().addingTimeInterval(wait)
        while Date() < end {
            if let e = find(label, contains: contains), e.isHittable { e.tap(); sleep(1); return true }
            usleep(300_000)
        }
        XCTFail("not found or not hittable: \(label)")
        return false
    }

    /// Swipes the front scroll view up until `label` exists (at most 8 times).
    func reveal(_ label: String, contains: Bool = false) -> Bool {
        for _ in 0..<8 {
            if let e = find(label, contains: contains), e.isHittable { return true }
            app.swipeUp()
        }
        return false
    }

    // MARK: 0 — explore

    func openSettings() { tap("Settings"); sleep(1) }
    func closeSheet() {
        for label in ["Done", "Back", "Cancel"] { if let e = find(label), e.isHittable { e.tap(); sleep(1); return } }
        XCTFail("nothing to close")
    }

    /// The Summary rows' combined labels, one per account (the app's "All" tab is where the iPad opens).
    var summaryRows: [String] {
        app.buttons.matching(NSPredicate(format: "label BEGINSWITH '5-hour window'")).allElementsBoundByIndex.map { $0.label }
    }

    // MARK: A — account page (§2 A2)

    func testA_accountPage() {
        openSettings()
        tap("Claude, Pro, Signed in")
        shot("A1-account", tree: true)
        tap("Name", contains: true)
        shot("A2-rename", tree: true)
        tap("Cancel")
        shot("A3-after-cancel")
        for label in ["Check now", "Refresh", "Check"] where find(label, contains: true) != nil {
            tap(label, contains: true); sleep(3); break
        }
        shot("A4-after-check", tree: true)
    }

    // MARK: B — Vs pace: signed figures, captions, press (CCRM-104)

    func testB_vsPace() {
        openSettings()
        XCTAssertTrue(reveal("Vs pace"))
        let used = find("Used"), left = find("Left"), vs = find("Vs pace")
        let was = (used?.isSelected ?? true) ? "Used" : (left?.isSelected ?? false) ? "Left" : "Vs pace"
        tap("Vs pace")
        closeSheet()
        shot("B1-summary-vspace", tree: true)
        let rows = summaryRows
        XCTAssertFalse(rows.isEmpty, "no summary rows")
        // Vs pace: the figure is the sixth label part — "+2%", "\u{2212}1%" (U+2212) or "0%"; never a bare "7%".
        for r in rows where !r.contains("Starts when") {
            let parts = r.components(separatedBy: ", ")
            let figure = parts.first { $0.hasSuffix("%") && !$0.contains("percent") && !$0.contains("pace") && !$0.contains("used") } ?? ""
            XCTAssertTrue(figure.hasPrefix("+") || figure.hasPrefix("\u{2212}") || figure == "0%", "figure not signed: \(r)")
            XCTAssertFalse(figure.contains("-"), "hyphen minus in the figure: \(r)")
        }
        // press the first account's figure: its caption underlines for 3 s
        if let first = app.buttons.matching(NSPredicate(format: "label BEGINSWITH '5-hour window'")).firstMatch as XCUIElement? {
            let f = first.coordinate(withNormalizedOffset: CGVector(dx: 0.94, dy: 0.12))
            f.tap(); usleep(500_000)
            shot("B2-caption-pressed")
        }
        tap("Claude")   // the sidebar entry: one account's tile
        shot("B3-tile-vspace", tree: true)
        tap("Settings"); XCTAssertTrue(reveal(was))
        tap(was); closeSheet()
        shot("B4-restored")
        _ = vs
    }

    // MARK: C — History at the iPad's widths (§2 C)

    func testC_history() {
        tap("Usage history")
        shot("C1-history", tree: true)
        XCUIDevice.shared.orientation = .portrait
        sleep(2)
        shot("C2-history-portrait")
        XCUIDevice.shared.orientation = .landscapeLeft
        sleep(2)
        shot("C3-history-landscape")
        closeSheet()
    }

    // MARK: C2 — chart orientation and the wash (§2 B6), restored

    func testC2_timeDown() {
        openSettings()
        XCTAssertTrue(reveal("Time down"))
        let wasDown = find("Time down")?.isSelected ?? false
        tap("Time down")
        closeSheet()
        tap("Claude")
        shot("C4-chart-time-down", tree: true)
        openSettings()
        XCTAssertTrue(reveal(wasDown ? "Time down" : "Time across"))
        tap(wasDown ? "Time down" : "Time across")
        closeSheet()
    }

    // MARK: D — share card (§2 D)

    func testD_shareCard() {
        tap("Claude")
        tap("More")
        tap("Share card")
        sleep(2)
        shot("D1-share", tree: true)
        closeSheet()
    }

    // MARK: E — sample data, then back (§2 E)

    func testE_sample() {
        openSettings()
        tap("Add account")
        sleep(1)
        shot("E0-add", tree: true)
        if tap("Try with sample data", contains: true, wait: 3) {
            sleep(2)
            shot("E1-sample", tree: true)
            if tap("Exit", wait: 3) { sleep(2) }
            shot("E2-after-exit", tree: true)
        }
    }

    // MARK: F — About (§2 F)

    func testF_about() {
        openSettings()
        _ = reveal("Privacy policy", contains: true)
        shot("F1-about", tree: true)
    }

    // MARK: L — Live Activity follows a Usage display change (CCBG-72): L1 sets Vs pace, L2 puts Used back

    func testL1_setVsPace() {
        openSettings()
        XCTAssertTrue(reveal("Vs pace"))
        tap("Vs pace")
        closeSheet()
        sleep(2)
    }

    func testL2_setUsed() {
        openSettings()
        XCTAssertTrue(reveal("Used"))
        tap("Used")
        closeSheet()
        sleep(2)
    }

    // MARK: N — offline: the "Couldn't reach …" notice (§2 B5). The person turns Wi-Fi off first and back on after.

    func testN_offline() {
        if let refresh = app.buttons.matching(NSPredicate(format: "label BEGINSWITH 'Checked'")).firstMatch as XCUIElement?, refresh.exists {
            refresh.tap()
        }
        sleep(10)
        shot("N1-all-offline", tree: true)
        tap("Claude")
        sleep(1)
        shot("N2-claude-offline", tree: true)
        tap("ChatGPT")
        sleep(1)
        shot("N3-chatgpt-offline", tree: true)
    }

    // MARK: R — after Sign in again: the list still has the same accounts, no duplicate (§2 A2)

    func testR_afterSignInAgain() {
        openSettings()
        shot("R1-settings", tree: true)
        let accounts = app.buttons.matching(NSPredicate(format: "label ENDSWITH 'Signed in'")).allElementsBoundByIndex.map { $0.label }
        XCTAssertEqual(accounts.count, 4, "accounts: \(accounts)")
        XCTAssertEqual(accounts.filter { $0.hasPrefix("Claude,") }.count, 1, "duplicate Claude: \(accounts)")
        tap("Claude, Pro, Signed in")
        shot("R2-claude", tree: true)
        _ = tap("Check now")
        sleep(3)
        shot("R3-checked", tree: true)
    }
}

import XCTest

/// RUNBOOK Step 3 (v2.0 device pass 2), iPad half: drives the real app on the connected iPad and attaches a screenshot and
/// the accessibility tree of every step. Skipped unless TEST_RUNNER_DEVICE_PASS is set. Captures show private data and stay
/// out of the repo. Tests that change a setting put it back, except that testP5 ends in dark mode (the iPad's starting appearance) and testL1_setUsed is paired with testL2_setVsPace (the starting Usage display): run both.
final class Step3PadTests: XCTestCase {
    var app: XCUIApplication!

    override func setUpWithError() throws {
        try XCTSkipUnless(ProcessInfo.processInfo.environment["DEVICE_PASS"] != nil, "device pass only")
        continueAfterFailure = true
        XCUIDevice.shared.orientation = .landscapeLeft
        app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 15))
        sleep(2)
    }

    func shot(_ name: String, tree: Bool = true) {
        sleep(1)
        let a = XCTAttachment(screenshot: app.windows.firstMatch.screenshot())
        a.name = name; a.lifetime = .keepAlways; add(a)
        if tree {
            let t = XCTAttachment(string: app.debugDescription)
            t.name = "\(name).tree"; t.lifetime = .keepAlways; add(t)
        }
    }

    func find(_ label: String, contains: Bool = false) -> XCUIElement? {
        for q in [app.buttons, app.cells, app.staticTexts, app.switches, app.otherElements] {
            let p = NSPredicate(format: contains ? "label CONTAINS[c] %@" : "label ==[c] %@", label)
            let e = q.matching(p).firstMatch
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

    func closeSheet() {
        for label in ["Done", "Back", "Cancel"] { if let e = find(label), e.isHittable { e.tap(); sleep(1); return } }
    }

    // S0 — the starting state: every Settings screen, scrolled one swipe at a time.
    func testS0_startingState() {
        tap("Settings"); sleep(1)
        for i in 0..<10 {
            shot("S0-settings-\(i)")
            app.swipeUp(velocity: .slow)
        }
        closeSheet()
        shot("S0-main")
    }

    // MARK: helpers for the passes

    func relaunch(_ args: [String] = [], landscape: Bool = true) {
        app.terminate()
        XCUIDevice.shared.orientation = landscape ? .landscapeLeft : .portrait
        app = XCUIApplication()
        app.launchArguments = args
        app.launch()
        _ = app.wait(for: .runningForeground, timeout: 15)
        sleep(2)
    }

    func rotate(_ landscape: Bool) {
        XCUIDevice.shared.orientation = landscape ? .landscapeLeft : .portrait
        sleep(2)
    }

    func reveal(_ label: String, contains: Bool = false) -> Bool {
        for _ in 0..<10 {
            if let e = find(label, contains: contains), e.isHittable { return true }
            app.swipeUp(velocity: .slow)
        }
        return false
    }

    func openSettings() { tap("Settings"); sleep(1) }

    // P1 — Inner Panes (CCRM-116), both orientations, real accounts: All, an account, History.
    func testP1_panes() {
        for land in [true, false] {
            let o = land ? "land" : "port"
            rotate(land)
            shot("P1-\(o)-all")
            if let e = find("Claude"), e.isHittable { e.tap(); sleep(1) }
            shot("P1-\(o)-claude")
            if let e = find("Work"), e.isHittable { e.tap(); sleep(1) }
            shot("P1-\(o)-work")
            if let e = find("Gemini"), e.isHittable { e.tap(); sleep(1) }
            shot("P1-\(o)-gemini")
            if let e = find("All"), e.isHittable { e.tap(); sleep(1) }
            tap("Usage history")
            shot("P1-\(o)-history")
            closeSheet()
        }
        rotate(true)
    }

    // P2 — Header Glyphs (CCRM-118): filter segments and the sort button, real accounts (all Personal).
    func testP2_filterSort() {
        shot("P2-0-all")
        if let e = find("Personal, ", contains: true), e.isHittable { e.tap(); sleep(1); shot("P2-1-person") }
        if let e = find("Work, ", contains: true) { shot("P2-2-work-state"); if e.isHittable { e.tap(); sleep(1); shot("P2-3-work") } }
        if let e = find("All, ", contains: true), e.isHittable { e.tap(); sleep(1) }
        if let e = app.buttons.matching(NSPredicate(format: "label CONTAINS[c] 'sort'")).firstMatch as XCUIElement?, e.exists {
            e.tap(); sleep(1); shot("P2-4-sort-menu")
            app.tap()   // dismiss
        } else { shot("P2-4-no-sort-label") }
    }

    // P3 — the sample states, which have both tags (CCRM-118) and the dense ten (CCBG-84): landscape and portrait.
    func testP3_demo() {
        for (name, args) in [("standard", ["-demo", "standard"]), ("fitdrop", ["-demo", "fitdrop"]), ("dense", ["-demo", "dense"]), ("max", ["-demo", "max"])] {
            for land in [true, false] {
                relaunch(args, landscape: land)
                shot("P3-\(name)-\(land ? "land" : "port")")
            }
        }
        relaunch(["-demo", "standard", "-tab", "teams"])
        shot("P3-standard-teams-land")
        rotate(false)
        shot("P3-standard-teams-port")
        tap("Usage history")
        shot("P3-history-port")
        rotate(true)
        shot("P3-history-land")
        closeSheet()
    }

    // P4 — Appearance › Tag marks, all four states (CCRM-118), real accounts; restored.
    func testP4_tagMarks() {
        openSettings()
        var found = false
        for _ in 0..<12 {
            let sw = app.switches["Personal"]
            if sw.exists && sw.isHittable { found = true; break }
            app.swipeUp(velocity: .slow)
        }
        XCTAssertTrue(found, "no Personal switch")
        shot("P4-0-settings")
        let personal = app.switches["Personal"], work = app.switches["Work"]
        let p0 = (personal.value as? String) == "1", w0 = (work.value as? String) == "1"
        func set(_ p: Bool, _ w: Bool) {
            for _ in 0..<8 where !(personal.exists && personal.isHittable) { app.swipeUp(velocity: .slow) }
            if ((personal.value as? String) == "1") != p { personal.coordinate(withNormalizedOffset: CGVector(dx: 0.92, dy: 0.5)).tap(); sleep(1) }
            if ((work.value as? String) == "1") != w { work.coordinate(withNormalizedOffset: CGVector(dx: 0.92, dy: 0.5)).tap(); sleep(1) }
        }
        for (p, w) in [(true, false), (false, true), (false, false), (true, true)] {
            set(p, w)
            let n = "per\(p ? "On" : "Off")-work\(w ? "On" : "Off")"
            shot("P4-\(n)-settings", tree: false)
            closeSheet()
            shot("P4-\(n)-main")
            if let e = find("Claude", contains: false), e.isHittable { e.tap(); sleep(1) }
            shot("P4-\(n)-claude")
            if let e = find("All"), e.isHittable { e.tap(); sleep(1) }
            openSettings()
        }
        set(p0, w0)
        closeSheet()
    }

    // P5 — light mode.
    func testP5_light() {
        XCUIDevice.shared.appearance = .light
        sleep(2)
        shot("P5-land-all")
        if let e = find("Claude"), e.isHittable { e.tap(); sleep(1) }
        shot("P5-land-claude")
        rotate(false)
        shot("P5-port-claude")
        if let e = find("All"), e.isHittable { e.tap(); sleep(1) }
        shot("P5-port-all")
        XCUIDevice.shared.appearance = .dark
        rotate(true)
    }

    // A1 — an account page in Settings (CCRM-99 Model Limits Toggle, CCRM-110 tag row, plan names CCBG-73, Gemini CCRM-55).
    func testA1_accountPages() {
        for name in ["Claude, Pro", "ChatGPT, Plus", "Gemini, Pro", "Work, Team Premium"] {
            openSettings()
            if let e = find(name + ", Tag", contains: true), e.isHittable { e.tap(); sleep(1) } else { XCTFail("no row \(name)"); closeSheet(); continue }
            let key = name.replacingOccurrences(of: ", ", with: "-").replacingOccurrences(of: " ", with: "")
            shot("A1-\(key)-0")
            app.swipeUp(velocity: .slow); shot("A1-\(key)-1")
            app.swipeUp(velocity: .slow); shot("A1-\(key)-2")
            closeSheet(); closeSheet()
        }
    }

    // E — the iOS sample: three accounts, banner, exit (real accounts and settings come back).
    func testE_sample() {
        openSettings()
        tap("Add account")
        sleep(1)
        shot("E0-add")
        if tap("Try with sample data", contains: true, wait: 3) {
            sleep(2)
            shot("E1-sample")
            if let e = find("Claude"), e.isHittable { e.tap(); sleep(1) }
            shot("E2-sample-account")
            if tap("Exit", wait: 3) { sleep(2) }
            shot("E3-after-exit")
        }
    }

    // W1 — the Home Screen (private: scratchpad only): press Home and page through.
    func testW1_homeScreen() {
        XCUIDevice.shared.press(.home)
        sleep(2)
        let sb = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        for _ in 0..<5 { sb.swipeRight(); usleep(600_000) }   // to the first page / Today View
        for i in 0..<3 {
            let a = XCTAttachment(screenshot: sb.windows.firstMatch.screenshot())
            a.name = "W1-home-\(i)"; a.lifetime = .keepAlways; add(a)
            sb.swipeLeft(); sleep(1)
        }
        XCUIDevice.shared.press(.home)
        app.activate()
    }

    // L — Live Activity follows a Usage display change (CCBG-72): L1 sets Used, L2 puts Vs pace back.
    func setDisplay(_ label: String) {
        openSettings()
        XCTAssertTrue(reveal(label))
        tap(label)
        shot("L-\(label)-settings", tree: false)
        closeSheet()
        sleep(2)
    }
    func testL1_setUsed() { setDisplay("Used") }
    func testL2_setVsPace() { setDisplay("Vs pace") }
}

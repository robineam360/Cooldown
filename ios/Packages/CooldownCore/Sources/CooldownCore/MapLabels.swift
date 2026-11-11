import Foundation

/// CCRM-111 (Map Layers), core §6.3 "Planned — v2.0 feedback": where the Pace map's names go, closing CCBG-79 (Leader
/// Reads as Trail) on iOS. Names go top-down (smallest y, then smallest x); each takes the first free of right, below,
/// left and above-left, then a short dotted leader 24, 36 or 48 from its dot in eight directions (E, SE, S, SW, W, NW,
/// N, NE). A spot is refused when it leaves the name area, comes near a fixed word, overlaps a dot's hit box, comes near
/// a placed name, sits on a ring or crosses its own projection tail; a leader also when its path crosses a placed name
/// or another dot. Nothing free: the name is not drawn (its dot stays and still opens its callout). Pure, in the
/// plot's own coordinates (origin top-left, x = week gone % × width, y = (100 − used) % × height); each family keeps
/// its own offsets in `Geometry` and tests the rule with the fixture's (`printed-v20.json` `mapLabels.placement`).
public enum MapLabels {

    public struct Rect: Sendable, Equatable {
        public var x, y, w, h: Double
        public init(_ x: Double, _ y: Double, _ w: Double, _ h: Double) {
            self.x = x
            self.y = y
            self.w = w
            self.h = h
        }
        func near(_ o: Rect, _ pad: Double) -> Bool {
            x < o.x + o.w + pad && o.x < x + w + pad && y < o.y + o.h + pad && o.y < y + h + pad
        }
    }

    public struct Geometry: Sendable, Equatable {
        public var dotHalf: Double
        public var nameGap: Double
        public var fixedGap: Double
        /// How far past the plot a name may sit: left, top, right, bottom.
        public var margin: (left: Double, top: Double, right: Double, bottom: Double)
        public var rightDx, belowDx, belowDy, leftDx, aboveDy: Double
        public var leaderDistances: [Double]

        public init(dotHalf: Double, nameGap: Double, fixedGap: Double,
                    margin: (left: Double, top: Double, right: Double, bottom: Double),
                    rightDx: Double, belowDx: Double, belowDy: Double, leftDx: Double, aboveDy: Double,
                    leaderDistances: [Double]) {
            self.dotHalf = dotHalf
            self.nameGap = nameGap
            self.fixedGap = fixedGap
            self.margin = margin
            self.rightDx = rightDx
            self.belowDx = belowDx
            self.belowDy = belowDy
            self.leftDx = leftDx
            self.aboveDy = aboveDy
            self.leaderDistances = leaderDistances
        }

        public static func == (a: Geometry, b: Geometry) -> Bool {
            a.dotHalf == b.dotHalf && a.nameGap == b.nameGap && a.fixedGap == b.fixedGap
                && a.margin.left == b.margin.left && a.margin.top == b.margin.top && a.margin.right == b.margin.right
                && a.margin.bottom == b.margin.bottom && a.rightDx == b.rightDx && a.belowDx == b.belowDx
                && a.belowDy == b.belowDy && a.leftDx == b.leftDx && a.aboveDy == b.aboveDy
                && a.leaderDistances == b.leaderDistances
        }
    }

    public struct Dot: Sendable, Equatable {
        public let key: String
        public let x: Double
        public let y: Double
        /// The label's size (the name, and with Figures on its figure).
        public let w: Double
        public let h: Double
        /// With Projection on: where this dot's tail ends.
        public let tailEnd: (x: Double, y: Double)?

        public init(key: String, x: Double, y: Double, w: Double, h: Double, tailEnd: (x: Double, y: Double)? = nil) {
            self.key = key
            self.x = x
            self.y = y
            self.w = w
            self.h = h
            self.tailEnd = tailEnd
        }

        public static func == (a: Dot, b: Dot) -> Bool {
            a.key == b.key && a.x == b.x && a.y == b.y && a.w == b.w && a.h == b.h
                && a.tailEnd?.x == b.tailEnd?.x && a.tailEnd?.y == b.tailEnd?.y
        }
    }

    public enum Spot: String, Sendable, Equatable { case right, below, left, aboveLeft, leader, none }

    public enum Direction: String, Sendable, Equatable, CaseIterable {
        case E, SE, S, SW, W, NW, N, NE
        var unit: (Double, Double) {
            switch self {
            case .E: return (1, 0)
            case .SE: return (1, 1)
            case .S: return (0, 1)
            case .SW: return (-1, 1)
            case .W: return (-1, 0)
            case .NW: return (-1, -1)
            case .N: return (0, -1)
            case .NE: return (1, -1)
            }
        }
    }

    public struct Leader: Sendable, Equatable {
        public let distance: Double
        public let direction: Direction
        /// The leader's end (the dot centre + distance along the direction); the box touches it.
        public let endX: Double
        public let endY: Double
    }

    public struct Placement: Sendable, Equatable {
        public let key: String
        public let spot: Spot
        /// nil when the name is not drawn.
        public let box: Rect?
        public let leader: Leader?
        /// The reasons met on the way, for reading a case.
        public let refusedFor: Set<String>
    }

    /// The order names are placed in: smallest y first, then smallest x.
    public static func order(_ dots: [Dot]) -> [Dot] {
        dots.enumerated().sorted { a, b in
            a.element.y != b.element.y ? a.element.y < b.element.y
                : a.element.x != b.element.x ? a.element.x < b.element.x : a.offset < b.offset
        }.map(\.element)
    }

    /// Places every name; the result is in the input's order.
    public static func place(dots: [Dot], plotWidth: Double, plotHeight: Double, geometry g: Geometry,
                             fixed: [Rect], rings: [Rect]) -> [Placement] {
        let area = Rect(-g.margin.left, -g.margin.top, plotWidth + g.margin.left + g.margin.right,
                        plotHeight + g.margin.top + g.margin.bottom)
        let hitBox: [String: Rect] = Dictionary(dots.map { ($0.key, Rect($0.x - g.dotHalf, $0.y - g.dotHalf, 2 * g.dotHalf, 2 * g.dotHalf)) },
                                                uniquingKeysWith: { a, _ in a })
        var placed: [Rect] = []
        var results: [String: Placement] = [:]

        func inside(_ r: Rect) -> Bool {
            r.x >= area.x && r.y >= area.y && r.x + r.w <= area.x + area.w && r.y + r.h <= area.y + area.h
        }

        func refuse(_ r: Rect, _ me: Dot) -> String? {
            if !inside(r) { return "leaves the plot" }
            if fixed.contains(where: { r.near($0, g.fixedGap) }) { return "overlaps a word" }
            for d in dots where r.near(hitBox[d.key]!, 0) {
                return d.key == me.key ? "overlaps its own dot" : "overlaps a dot"
            }
            if placed.contains(where: { r.near($0, g.nameGap) }) { return "overlaps a placed name" }
            if rings.contains(where: { r.near($0, 0) }) { return "sits on a ring" }
            if let t = me.tailEnd, segmentHits((me.x, me.y), (t.x, t.y), r) { return "sits on its own tail" }
            return nil
        }

        for d in order(dots) {
            let w = d.w, h = d.h
            let candidates: [(Spot, Rect)] = [
                (.right, Rect(d.x + g.rightDx, d.y - h / 2, w, h)),
                (.below, Rect(d.x + g.belowDx, d.y + h / 2 + g.belowDy, w, h)),
                (.left, Rect(d.x - g.leftDx - w, d.y - h / 2, w, h)),
                (.aboveLeft, Rect(d.x - g.leftDx - w, d.y - g.aboveDy - h, w, h)),
            ]
            var refused = Set<String>()
            var pick: Placement?
            for (spot, r) in candidates {
                if let why = refuse(r, d) { refused.insert(why); continue }
                pick = Placement(key: d.key, spot: spot, box: r, leader: nil, refusedFor: [])
                break
            }
            if pick == nil {
                search: for dist in g.leaderDistances {
                    for dir in Direction.allCases {
                        let (ux, uy) = dir.unit
                        let n = (ux * ux + uy * uy).squareRoot()
                        let ex = d.x + dist * ux / n, ey = d.y + dist * uy / n
                        let bx = ux > 0 ? ex : ux < 0 ? ex - w : ex - w / 2
                        let by = uy > 0 ? ey : uy < 0 ? ey - h : ey - h / 2
                        let r = Rect(bx, by, w, h)
                        var why = refuse(r, d)
                        if why == nil, placed.contains(where: { segmentHits((d.x, d.y), (ex, ey), $0) }) {
                            why = "path crosses a placed name"
                        }
                        if why == nil, dots.contains(where: { $0.key != d.key && segmentHits((d.x, d.y), (ex, ey), hitBox[$0.key]!) }) {
                            why = "path crosses a dot"
                        }
                        if let why { refused.insert(why); continue }
                        pick = Placement(key: d.key, spot: .leader, box: r,
                                         leader: Leader(distance: dist, direction: dir, endX: ex, endY: ey), refusedFor: [])
                        break search
                    }
                }
            }
            let final = pick ?? Placement(key: d.key, spot: .none, box: nil, leader: nil, refusedFor: [])
            if let box = final.box { placed.append(box) }
            results[d.key] = Placement(key: final.key, spot: final.spot, box: final.box, leader: final.leader, refusedFor: refused)
        }
        return dots.compactMap { results[$0.key] }
    }

    /// Does the segment p–q meet the rectangle r? Liang–Barsky clipping.
    public static func segmentHits(_ p: (Double, Double), _ q: (Double, Double), _ r: Rect) -> Bool {
        let dx = q.0 - p.0, dy = q.1 - p.1
        var t0 = 0.0, t1 = 1.0
        for (pv, qv) in [(-dx, p.0 - r.x), (dx, r.x + r.w - p.0), (-dy, p.1 - r.y), (dy, r.y + r.h - p.1)] {
            if pv == 0 {
                if qv < 0 { return false }
            } else {
                let t = qv / pv
                if pv < 0 { t0 = max(t0, t) } else { t1 = min(t1, t) }
                if t0 > t1 { return false }
            }
        }
        return true
    }
}

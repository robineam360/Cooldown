import CoreGraphics
import Foundation

/// CCRM-104 (Pace Line Parity) D7: where the Pace map's name labels go. Names label every dot (no rank numbers at any
/// count); each label takes the first of right of the dot, below it, left of it, above-left, and else a short leader line
/// to the plot's right edge. A spot is refused where it overlaps another label, a dot, an axis word or leaves the plot.
/// Labels are placed from the top down, so the lower of two colliding labels is the one that moves (Astra C1-1).
/// Pure, with the text size supplied, so the placement is tested without drawing.
enum PaceMapLayout {

    struct Dot: Equatable {
        let key: String
        let name: String
        let point: CGPoint
    }

    enum Spot: String, Equatable { case right, below, left, aboveLeft, leader }

    struct Placement: Equatable {
        let key: String
        let rect: CGRect
        let spot: Spot
        /// For a leader: from the dot to the label's edge.
        let leader: (from: CGPoint, to: CGPoint)?

        static func == (a: Placement, b: Placement) -> Bool {
            a.key == b.key && a.rect == b.rect && a.spot == b.spot
                && a.leader?.from == b.leader?.from && a.leader?.to == b.leader?.to
        }
    }

    static let dotRadius: CGFloat = 5
    static let gap: CGFloat = 3

    /// - Parameters:
    ///   - plot: the area labels may use (the drawn canvas).
    ///   - rightEdge: x of the plot's right edge, where a leader label is right-aligned.
    ///   - obstacles: axis words and the wedge words.
    static func place(dots: [Dot], plot: CGRect, rightEdge: CGFloat, obstacles: [CGRect],
                      size: (String) -> CGSize) -> [Placement] {
        let dotRects = dots.map { CGRect(x: $0.point.x - dotRadius, y: $0.point.y - dotRadius, width: 2 * dotRadius, height: 2 * dotRadius) }
        var placed: [Placement] = []
        let order = dots.indices.sorted { a, b in
            dots[a].point.y != dots[b].point.y ? dots[a].point.y < dots[b].point.y : a < b
        }
        for i in order {
            let dot = dots[i], s = size(dot.name)
            let candidates: [(Spot, CGRect)] = [
                (.right, CGRect(x: dot.point.x + dotRadius + gap, y: dot.point.y - s.height / 2, width: s.width, height: s.height)),
                (.below, CGRect(x: dot.point.x - s.width / 2, y: dot.point.y + dotRadius + 3, width: s.width, height: s.height)),
                (.left, CGRect(x: dot.point.x - dotRadius - gap - s.width, y: dot.point.y - s.height / 2, width: s.width, height: s.height)),
                (.aboveLeft, CGRect(x: dot.point.x - s.width + 2, y: dot.point.y - dotRadius - 1 - s.height, width: s.width, height: s.height)),
            ]
            func free(_ r: CGRect, ignoring own: Int?) -> Bool {
                guard plot.contains(r) else { return false }
                if obstacles.contains(where: { $0.intersects(r) }) { return false }
                if placed.contains(where: { $0.rect.insetBy(dx: -1, dy: -1).intersects(r) }) { return false }
                for (j, d) in dotRects.enumerated() where j != own && d.intersects(r) { return false }
                return !dotRects[own ?? 0].intersects(r) || own == nil
            }
            if let hit = candidates.first(where: { free($0.1, ignoring: i) }) {
                placed.append(Placement(key: dot.key, rect: hit.1, spot: hit.0, leader: nil))
                continue
            }
            // A leader: the label sits at the right edge, the first free height from the dot's own, stepping down then up.
            let x = rightEdge - s.width
            var chosen: CGRect?
            var step: CGFloat = 0
            while chosen == nil, step < plot.height {
                for dy in step == 0 ? [CGFloat(0)] : [step, -step] {
                    let r = CGRect(x: x, y: dot.point.y - s.height / 2 + dy, width: s.width, height: s.height)
                    if free(r, ignoring: nil) { chosen = r; break }
                }
                step += s.height + 1
            }
            let rect = chosen ?? CGRect(x: x, y: dot.point.y - s.height / 2, width: s.width, height: s.height)
            placed.append(Placement(key: dot.key, rect: rect, spot: .leader,
                                    leader: (from: dot.point, to: CGPoint(x: rect.minX - 2, y: rect.midY))))
        }
        return placed
    }
}

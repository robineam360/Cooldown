import CoreGraphics
import Foundation

/// CCRM-116 (Inner Panes), wireframe design/2026-11-12-iphone-duo-inner-screen.html §2: the one rule for every page that
/// has two panes (the main page's Usage | Pace, History's 5-hour | 7-day, the empty state's two groups). No pt
/// breakpoints: a regular-width window gets two panes; wider than tall they sit side by side, taller than wide they
/// stack. Flat, the content area is split evenly with a 16 pt gap; half open, the system's division region (the fold)
/// is the gap, so no pane crosses it. Pure, so a test can pin it.
public enum PaneGeometry {
    /// The gap between the panes when nothing is folded.
    public static let gap = 16.0
    /// A pane never shrinks below this along the split axis for the fold to be honoured; a fold that would leave less
    /// (a region far off centre, or one outside the area) falls back to the even split.
    public static let minimumPane = 120.0

    public enum Axis: Sendable, Equatable { case sideBySide, stacked }

    public struct Split: Sendable, Equatable {
        public let axis: Axis
        public let first: CGRect
        public let second: CGRect
        /// True when the divider follows a fold (half open), false for the even split.
        public let onFold: Bool
    }

    /// Side by side when wider than tall, else stacked (a square area stacks, as a portrait one does).
    public static func axis(for size: CGSize) -> Axis { size.width > size.height ? .sideBySide : .stacked }

    /// The two panes' frames inside an area of `size` (its own coordinates). `fold` is the division region in the same
    /// coordinates, margins included, or nil when flat.
    public static func split(size: CGSize, fold: CGRect? = nil) -> Split {
        let axis = axis(for: size)
        let length = axis == .sideBySide ? size.width : size.height
        if let fold {
            let (lo, hi) = axis == .sideBySide ? (fold.minX, fold.maxX) : (fold.minY, fold.maxY)
            // The band must run across the split axis (a vertical band for side by side) and leave both panes usable.
            let across = axis == .sideBySide ? fold.height >= fold.width : fold.width >= fold.height
            if across, lo >= minimumPane, length - hi >= minimumPane, hi > lo {
                return make(axis: axis, size: size, firstLength: lo, secondStart: hi, onFold: true)
            }
        }
        let half = max(0, (length - gap) / 2)
        return make(axis: axis, size: size, firstLength: half, secondStart: half + gap, onFold: false)
    }

    /// One pane alone (History for a plan with no 5-hour window): half open, the first pane's frame, so it never crosses
    /// the fold; flat, a pane of the even split's size centred in the area.
    public static func single(size: CGSize, fold: CGRect? = nil) -> CGRect {
        let s = split(size: size, fold: fold)
        if s.onFold { return s.first }
        switch s.axis {
        case .sideBySide:
            return CGRect(x: (size.width - s.first.width) / 2, y: 0, width: s.first.width, height: size.height)
        case .stacked:
            return CGRect(x: 0, y: (size.height - s.first.height) / 2, width: size.width, height: s.first.height)
        }
    }

    private static func make(axis: Axis, size: CGSize, firstLength: Double, secondStart: Double, onFold: Bool) -> Split {
        switch axis {
        case .sideBySide:
            return Split(axis: axis,
                         first: CGRect(x: 0, y: 0, width: firstLength, height: size.height),
                         second: CGRect(x: secondStart, y: 0, width: max(0, size.width - secondStart), height: size.height),
                         onFold: onFold)
        case .stacked:
            return Split(axis: axis,
                         first: CGRect(x: 0, y: 0, width: size.width, height: firstLength),
                         second: CGRect(x: 0, y: secondStart, width: size.width, height: max(0, size.height - secondStart)),
                         onFold: onFold)
        }
    }
}

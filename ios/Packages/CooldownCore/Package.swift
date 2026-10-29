// swift-tools-version: 6.0
// CCRM-94 (iOS App): the pure core of the iPhone/iPad app — models, parsers, projection, pace, history,
// plan tag and formatting — with no UIKit, no Keychain, no networking stack. Its tests run on the Mac
// in seconds against the shared spec in spec/core/fixtures (CCRM-92 (Shared Spec)).
import PackageDescription

let package = Package(
    name: "CooldownCore",
    platforms: [.iOS(.v18), .macOS(.v14)],
    products: [.library(name: "CooldownCore", targets: ["CooldownCore"])],
    targets: [
        .target(name: "CooldownCore"),
        .testTarget(name: "CooldownCoreTests", dependencies: ["CooldownCore"]),
    ]
)

import SwiftUI

@main
struct TourFundManagerApp: App {
    @StateObject private var tracker = LocationTracker()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(tracker)
        }
    }
}

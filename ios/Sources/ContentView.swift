import SwiftUI

struct ContentView: View {
    @EnvironmentObject private var tracker: LocationTracker
    @AppStorage("tourFundDisplayName") private var displayName = ""
    @AppStorage("tourFundUserId") private var userId = ""

    var body: some View {
        NavigationStack {
            Form {
                Section("Your tour profile") {
                    TextField("Display name", text: $displayName)
                        .textInputAutocapitalization(.words)
                    TextField("Existing user ID (UUID)", text: $userId)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    Text("Use the same user ID as your existing Tour Fund Manager account so the map can associate your location with the right person.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }

                Section("Location sharing") {
                    HStack {
                        Circle()
                            .fill(tracker.isSharing ? .green : .gray)
                            .frame(width: 10, height: 10)
                        Text(tracker.isSharing ? "Sharing location" : "Location sharing stopped")
                    }
                    if let status = tracker.statusMessage {
                        Text(status)
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                    Button(tracker.isSharing ? "Stop sharing" : "Start location sharing") {
                        if tracker.isSharing {
                            tracker.stopSharing(userId: userId)
                        } else {
                            tracker.startSharing(userId: userId, displayName: displayName)
                        }
                    }
                    .disabled(!tracker.isSharing && (displayName.trimmingCharacters(in: .whitespaces).isEmpty || userId.trimmingCharacters(in: .whitespaces).isEmpty))
                }

                Section("iPhone settings") {
                    Text("Choose Allow Always for location permission and enable Precise Location for the best results. iOS may still pause updates to save battery.")
                        .font(.footnote)
                    Text("Keep this app installed and location sharing switched on. A persistent iOS notification is not available for ordinary iPhone apps.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Tour Fund Manager")
        }
    }
}

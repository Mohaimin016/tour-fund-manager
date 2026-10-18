import Foundation
import CoreLocation

@MainActor
final class LocationTracker: NSObject, ObservableObject, CLLocationManagerDelegate {
    @Published var isSharing = false
    @Published var statusMessage: String?

    private let manager = CLLocationManager()
    private var activeUserId = ""
    private var activeDisplayName = ""
    private let supabaseURL = URL(string: "https://ixksktyqzjwudzaciwcr.supabase.co")!
    // Public publishable/anon key. Never put a Supabase service-role key in a client app.
    private let supabaseKey = "sb_publishable_JntqprSibYEaJIRWNjVrEw_5EwC7qvC"

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
        manager.distanceFilter = 25
        manager.allowsBackgroundLocationUpdates = true
        manager.pausesLocationUpdatesAutomatically = false
    }

    func startSharing(userId: String, displayName: String) {
        let cleanedId = userId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard UUID(uuidString: cleanedId) != nil else {
            statusMessage = "Enter a valid UUID matching your existing account."
            return
        }
        activeUserId = cleanedId
        activeDisplayName = displayName.trimmingCharacters(in: .whitespacesAndNewlines)
        let authorization = manager.authorizationStatus
        if authorization == .notDetermined {
            manager.requestAlwaysAuthorization()
        } else if authorization == .authorizedAlways || authorization == .authorizedWhenInUse {
            manager.startUpdatingLocation()
            isSharing = true
            statusMessage = authorization == .authorizedAlways
                ? "Location sharing is active. Background updates depend on iOS."
                : "Sharing started, but background updates need Allow Always permission."
        } else {
            statusMessage = "Enable location access in iPhone Settings."
            manager.requestAlwaysAuthorization()
        }
    }

    func stopSharing(userId: String) {
        manager.stopUpdatingLocation()
        isSharing = false
        statusMessage = "Location sharing stopped."
        let cleanedId = (userId.isEmpty ? activeUserId : userId).trimmingCharacters(in: .whitespacesAndNewlines)
        guard UUID(uuidString: cleanedId) != nil else { return }
        var request = URLRequest(url: supabaseURL.appendingPathComponent("rest/v1/tour_fund_locations")
            .appending(queryItems: [URLQueryItem(name: "user_id", value: "eq.\(cleanedId)")]))
        request.httpMethod = "DELETE"
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(supabaseKey)", forHTTPHeaderField: "Authorization")
        URLSession.shared.dataTask(with: request).resume()
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        if manager.authorizationStatus == .authorizedAlways || manager.authorizationStatus == .authorizedWhenInUse {
            if !activeUserId.isEmpty {
                manager.startUpdatingLocation()
                isSharing = true
                statusMessage = manager.authorizationStatus == .authorizedAlways
                    ? "Location sharing is active. Background updates depend on iOS."
                    : "For background sharing, change permission to Allow Always in Settings."
            }
        } else if manager.authorizationStatus == .denied || manager.authorizationStatus == .restricted {
            isSharing = false
            statusMessage = "Location access is denied or restricted. Check iPhone Settings."
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard isSharing, let location = locations.last, !activeUserId.isEmpty else { return }
        upload(location)
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        statusMessage = "Location error: \(error.localizedDescription)"
    }

    private func upload(_ location: CLLocation) {
        let payload: [String: Any] = [
            "user_id": activeUserId,
            "display_name": activeDisplayName,
            "latitude": location.coordinate.latitude,
            "longitude": location.coordinate.longitude,
            "accuracy_m": max(0, location.horizontalAccuracy),
            "sharing_enabled": true,
            "updated_at": ISO8601DateFormatter().string(from: Date())
        ]
        var request = URLRequest(url: supabaseURL.appendingPathComponent("rest/v1/tour_fund_locations")
            .appending(queryItems: [URLQueryItem(name: "on_conflict", value: "user_id")]))
        request.httpMethod = "POST"
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(supabaseKey)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("resolution=merge-duplicates,return=minimal", forHTTPHeaderField: "Prefer")
        do {
            request.httpBody = try JSONSerialization.data(withJSONObject: payload)
        } catch {
            statusMessage = "Could not prepare location update."
            return
        }
        URLSession.shared.dataTask(with: request) { [weak self] _, response, error in
            Task { @MainActor in
                if let error {
                    self?.statusMessage = "Upload failed: \(error.localizedDescription)"
                    return
                }
                guard let http = response as? HTTPURLResponse else {
                    self?.statusMessage = "No response from Supabase."
                    return
                }
                if (200...299).contains(http.statusCode) {
                    self?.statusMessage = "Last location sent: \(Date().formatted(date: .omitted, time: .shortened))"
                } else {
                    self?.statusMessage = "Supabase returned HTTP \(http.statusCode). Check table columns and RLS policies."
                }
            }
        }.resume()
    }
}

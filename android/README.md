# Tour Fund Manager — Android location app

This Android app shares a consenting user's location with the existing Tour Fund Manager web map using the existing Supabase `tour_fund_locations` table.

## Build APK

The repository's GitHub Actions workflow builds a debug APK whenever files under `android/` change, or manually from **Actions → Build Android APK → Run workflow**. When the run completes, open the run and download the `tour-fund-location-debug-apk` artifact.

## Install and use

1. Download and extract `app-debug.apk` on an Android phone.
2. Install it. Android may ask you to allow installation from that download source.
3. Open **Tour Fund Location**, enter the name you want displayed on the map, and tap **Start location sharing**.
4. Grant location permission. Keep the persistent notification visible while sharing.
5. Open the existing Tour Fund Manager website and its **Live Map** tab to see the location.
6. Tap **Stop sharing** in the app to stop updates and remove the last location record.

## Important privacy and platform notes

- Location sharing is optional. Start only if you agree to share your location.
- The current Supabase policies and web app make shared locations visible to anyone who has the app link. Do not use this for sensitive/private tracking.
- This is a debug APK for testing, not a Play Store release. It is not signed with a production release key.
- Android's foreground service and persistent notification allow location updates while the app is backgrounded or the screen is locked, subject to Android permissions, device settings, battery optimization, and vendor restrictions. It cannot guarantee uninterrupted GPS on every phone.
- This app uses the existing public Supabase publishable key. Access control depends on the database's Row Level Security policies; the publishable key is not a secret.

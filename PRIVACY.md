# Privacy Policy for SmartTube (F-Droid Build)

**Last Updated:** March 21, 2026

This Privacy Policy applies specifically to the **F-Droid flavor** of SmartTube (Package ID: `app.smarttube.fdroid`). This version is specifically hardened for user privacy and compliance with F-Droid’s inclusion policy.

## 1. Scope of this Policy
This document covers the **F-Droid build** only. Other versions or "flavors" of the application (e.g., Stable, Beta) may contain additional features such as self-update mechanisms or crash reporting that are removed in this specific F-Droid release.

## 2. Data Collection and Processing
* **No Backend:** The F-Droid build of SmartTube operates entirely as a client-side application. It does not communicate with any developer-controlled servers.
* **No Telemetry or Analytics:** This version contains no tracking code, telemetry, or analytics frameworks (e.g., no Firebase, no Crashlytics). We do not monitor how you use the app.
* **No Update Tracking:** The self-update mechanism is disabled in this flavor. Updates are handled exclusively through the F-Droid client.

## 3. Third-Party Services (YouTube/Google)
* **Authentication:** When you sign in, the application uses the official Google OAuth 2.0 flow. This is a secure method that allows you to authorize the app without sharing your password with the developer.
* **Local Storage:** Authentication tokens are stored exclusively on your device's secure local storage. They are never transmitted to, or stored by, the developer.
* **Data Flow:** Video data and account information are fetched directly from YouTube/Google servers to your device.

## 4. Community-Driven Services & Third-Party APIs
To provide core functionality and enhanced features, the application communicates with the following community-driven services:
* **SponsorBlock:** The app sends the ID of the video being viewed to the SponsorBlock API to retrieve crowd-sourced skip segments.
* **DeArrow:** The app sends the ID of the video being viewed to the DeArrow API to retrieve crowd-sourced titles.
* **Return YouTube Dislike (RYD):** The app sends the Video ID to the RYD API to retrieve estimated dislike counts.
* **Anonymous Metadata Requests:** These requests are "read-only" and do not include personal identifiers, Google account tokens, or user-specific profile data. 
* **General API Communication:** The application may communicate with other third-party infrastructure (such as official YouTube/Google endpoints) solely to fetch media content and metadata required for the application to function. These functional requests do not involve the collection or storage of personal user data by the developer.

## 5. Automated Decision-Making and Profiling
In accordance with **GDPR Article 15**, we confirm for this build:
* **No Profiling:** We do not track user behavior to build profiles.
* **No Automated Decisions:** The app does not use algorithms or AI to make decisions about users or to manipulate content ranking. Content is served as-is from the YouTube API.

## 6. External Links & Donations
The application contains static links and addresses for project documentation (GitHub) and donations (e.g., PayPal, Patreon, Bitcoin, Ethereum, and other cryptocurrencies).
* **Manual Action Only:** These links do not trigger automatically. No data is transmitted until the user manually clicks a link or copies an address.
* **Third-Party Policies:** Once you leave the application to a donation platform or use a third-party wallet, your data is governed by the privacy policy of those respective services.

## 7. Your Rights (GDPR / CCPA)
Because this specific build flavor does not store or transmit personal data to the developer, we hold no identifiable records (names, emails, IPs) associated with your identity. Therefore, we have no data to provide or delete upon request.

## 8. Contact
For technical inquiries regarding the privacy architecture of the F-Droid build, please open an issue on the official GitHub repository.
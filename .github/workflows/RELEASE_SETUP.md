# GitHub Release Action Setup Guide

This guide explains how to set up and use the GitHub Actions workflow for creating SmartTube releases.

## Prerequisites

1. A GitHub repository with this project
2. (Optional) A release keystore for signing APKs

## Setup Instructions

### 1. Configure GitHub Secrets (For Signed APKs)

To build signed APKs, you need to add the following secrets to your GitHub repository:

1. Go to your repository on GitHub
2. Navigate to **Settings** → **Secrets and variables** → **Actions**
3. Click **New repository secret** and add each of the following:

#### Required Secrets for Signing:

- **`KEYSTORE_BASE64`**: Your keystore file encoded in base64
  ```bash
  # Generate this on your local machine:
  base64 -i your-keystore.jks | pbcopy  # macOS
  base64 -w 0 your-keystore.jks         # Linux
  ```

- **`KEYSTORE_PASSWORD`**: The password for your keystore

- **`KEY_ALIAS`**: The alias of the key in your keystore

- **`KEY_PASSWORD`**: The password for the key

### 2. Using Your Existing Keystore

If you have a `debug.keystore` (as seen in the repo), you can use it:

```bash
# Encode your keystore
base64 -i debug.keystore | pbcopy
```

Then add to GitHub secrets:
- `KEYSTORE_BASE64`: (paste the base64 output)
- `KEYSTORE_PASSWORD`: `android`
- `KEY_ALIAS`: `androiddebugkey`
- `KEY_PASSWORD`: `android`

## Usage

### Method 1: Automatic Release (Tag Push)

This is the recommended method for production releases:

```bash
# Create and push a version tag
git tag v30.19
git push origin v30.19
```

This will:
- Build **all flavors** (beta, stable, orig, amazon, aptoide)
- Build **all architectures** (armeabi-v7a, x86, arm64-v8a)
- Sign APKs if keystore secrets are configured
- Create a GitHub release with all APKs
- Generate checksums for verification

### Method 2: Manual Workflow Trigger

For more control over the build:

1. Go to **Actions** tab in your GitHub repository
2. Select **Create Release** workflow
3. Click **Run workflow**
4. Configure options:
   - **version**: e.g., `30.19`
   - **flavors**: Choose which flavors to build:
     - `all` (default for tag push)
     - `stable` (single flavor)
     - `beta,stable` (multiple flavors)
     - `stable,orig,amazon` (custom combination)
   - **sign_apk**: Check to sign APKs (requires secrets configured)
5. Click **Run workflow**

## Flavor Information

The workflow supports all SmartTube flavors:

| Flavor | Prefix | Application ID | Notes |
|--------|--------|----------------|-------|
| **beta** | stbeta | `com.liskovsoft.smarttubetv.beta` | Beta testing version with Firebase |
| **stable** | ststable | `com.teamsmart.videomanager.tv` | Main stable release |
| **orig** | storig | `org.smartteam.smarttube.tv.orig` | Original package ID |
| **amazon** | stamazon | `com.amazon.firetv.youtube` | Fire TV optimized |
| **aptoide** | staptoide | `com.teamsmart.videomanager.tv` | Aptoide store version (versionCode +11000) |

## Output

### APK Files

The workflow generates APKs with this naming format:
```
SmartTube_{flavor}_{version}_{arch}.apk
```

Examples:
- `SmartTube_stable_30.19_armeabi-v7a.apk`
- `SmartTube_stable_30.19_arm64-v8a.apk`
- `SmartTube_stable_30.19_x86.apk`
- `SmartTube_beta_30.19_armeabi-v7a.apk`

### Checksums

A `checksums.txt` file is included with SHA256 hashes for all APKs:
```
sha256sum *.apk > checksums.txt
```

Users can verify downloads:
```bash
sha256sum -c checksums.txt
```

## Architectures

All builds include three architectures:
- **armeabi-v7a**: 32-bit ARM (main build, broad compatibility)
- **x86**: For x86 devices and WSL
- **arm64-v8a**: 64-bit ARM (Pixel Tablet, newer devices)

## Building Without Signing

If you don't configure keystore secrets:
- The workflow will build **unsigned APKs**
- These can still be installed but may show warnings
- Suitable for testing or personal use

## Troubleshooting

### Build Fails - Missing Keystore
- Make sure all four secrets are configured if signing
- Or disable signing in manual trigger

### Wrong APK Flavor
- Check the flavor name matches: beta, stable, orig, amazon, aptoide
- Flavor names are case-sensitive in the build command

### JDK Version Issues
- The workflow uses JDK 14 as required by the project
- If issues occur, check `gradle.properties` JDK configuration

### Submodules Not Found
- The workflow automatically checks out submodules
- Ensure SharedModules is properly configured

## Advanced Configuration

### Custom Build Types

To modify which build types are created, edit `.github/workflows/release.yml`:

```yaml
# Build only specific flavors by default
FLAVORS="stable,beta"

# Build debug builds
./gradlew assembleStbetaDebug
```

### Add Firebase google-services.json

For beta builds with Firebase Crashlytics:

1. Add `google-services.json` to repository secrets:
   ```bash
   base64 -i google-services.json | pbcopy
   ```
2. Add to workflow before build:
   ```yaml
   - name: Setup Firebase
     run: |
       echo "${{ secrets.GOOGLE_SERVICES_JSON }}" | base64 -d > smarttubetv/google-services.json
   ```

### Parallel Builds

To speed up builds, you can create separate jobs for each flavor:

```yaml
jobs:
  build-stable:
    # ... build stable flavor
  build-beta:
    # ... build beta flavor
```

## Release Notes

The workflow automatically generates release notes from commits between releases. To customize:

1. Use conventional commits: `feat:`, `fix:`, `docs:`
2. Or manually edit the release after creation

## Questions?

- Check the [GitHub Actions documentation](https://docs.github.com/en/actions)
- Review workflow runs in the **Actions** tab
- Check build logs for detailed error messages

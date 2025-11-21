# intellij-obsidian

![Build](https://github.com/mhlavac/intellij-obsidian/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)

<!-- Plugin description -->
Obsidian integration for IntelliJ IDEA that brings Obsidian-style markdown features to your IDE.

**Features:**
- **WikiLink Support**: Navigate between notes using `[[FileName]]`, `[[Path/To/File]]`, or `[[FileName|Display Text]]` syntax
- **Smart Navigation**: Cmd/Ctrl+Click on WikiLinks to jump to referenced files
- **Auto-completion**: Get suggestions for note names while typing WikiLinks
- **Syntax Highlighting**: Visual indicators for WikiLinks in markdown files
- **Daily Notes**: Quick access to daily notes with keyboard shortcut (Ctrl+Alt+D) following the `YYYY-MM-DD.md` format
- **Line Markers**: Visual navigation aids for daily note references

Perfect for developers who use Obsidian for note-taking and want seamless integration with their IDE workflow.

To keep everything working, do not remove `<!-- ... -->` sections.
<!-- Plugin description end -->

## Development Status

This plugin is in active development. Current features are stable, with more Obsidian integration features planned.

### Completed
- [x] Project setup and configuration
- [x] WikiLink reference resolution
- [x] Navigation and auto-completion
- [x] Daily note functionality
- [x] Syntax highlighting

### Before Publishing
- [ ] Review the [Legal Agreements](https://plugins.jetbrains.com/docs/marketplace/legal-agreements.html?from=IJPluginTemplate)
- [ ] Set up [Plugin Signing](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html?from=IJPluginTemplate) (see setup guide below)
- [ ] [Publish plugin manually](https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html?from=IJPluginTemplate) for the first time
- [ ] Update `MARKETPLACE_ID` in README badges after publication
- [ ] Set up [Deployment Token](https://plugins.jetbrains.com/docs/marketplace/plugin-upload.html?from=IJPluginTemplate) for automated publishing

## Plugin Signing Setup

To publish your plugin to the JetBrains Marketplace, you need to set up plugin signing. This ensures the integrity and authenticity of your plugin.

### Step 1: Generate Certificate Chain

You have two options:

**Option A: Using JetBrains Marketplace Hub (Recommended)**
1. Go to your [JetBrains Marketplace profile](https://plugins.jetbrains.com/author/me)
2. Navigate to the plugin signing section
3. Generate or upload your certificate through the web interface

**Option B: Generate Manually**
```bash
# Generate a new private key and certificate
openssl req -x509 -newkey rsa:4096 -keyout private.pem -out chain.crt -days 3650 -nodes
```

### Step 2: Set Up Environment Variables

You need to configure three secrets as environment variables or GitHub secrets:

1. **CERTIFICATE_CHAIN**: The contents of your certificate file (chain.crt)
   ```bash
   cat chain.crt | base64
   ```

2. **PRIVATE_KEY**: The contents of your private key file (private.pem)
   ```bash
   cat private.pem | base64
   ```

3. **PRIVATE_KEY_PASSWORD**: Password for the private key (empty string if no password)

### Step 3: Configure for GitHub Actions

Add these as secrets in your GitHub repository:

1. Go to: `Settings` → `Secrets and variables` → `Actions`
2. Click `New repository secret`
3. Add each secret:
   - `CERTIFICATE_CHAIN` (base64-encoded certificate)
   - `PRIVATE_KEY` (base64-encoded private key)
   - `PRIVATE_KEY_PASSWORD` (password or empty string)
   - `PUBLISH_TOKEN` (from JetBrains Marketplace - for automated publishing)

### Step 4: Local Signing (Optional)

For local testing, set environment variables:

```bash
export CERTIFICATE_CHAIN=$(cat chain.crt | base64)
export PRIVATE_KEY=$(cat private.pem | base64)
export PRIVATE_KEY_PASSWORD=""

# Test signing
./gradlew signPlugin
```

The signed plugin will be in `build/distributions/` with `-signed` suffix.

### Security Notes

- **Never commit** your private key or certificate to the repository
- Keep your private key secure and backed up
- The certificate should be valid for several years (3650 days = 10 years)
- Store your keys in a secure location (password manager, secrets vault, etc.)

For more details, see the [official Plugin Signing documentation](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html).

## Installation

- Using the IDE built-in plugin system:
  
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "intellij-obsidian"</kbd> >
  <kbd>Install</kbd>
  
- Using JetBrains Marketplace:

  Go to [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID) and install it by clicking the <kbd>Install to ...</kbd> button in case your IDE is running.

  You can also download the [latest release](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID/versions) from JetBrains Marketplace and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

- Manually:

  Download the [latest release](https://github.com/mhlavac/intellij-obsidian/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>


---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
[docs:plugin-description]: https://plugins.jetbrains.com/docs/intellij/plugin-user-experience.html#plugin-description-and-presentation

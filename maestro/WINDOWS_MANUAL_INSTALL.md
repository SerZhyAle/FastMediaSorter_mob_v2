# Maestro Installation for Windows - Manual Method

## Problem with Official Installer

The official PowerShell installer from maestro.mobile.dev may not work correctly on Windows.

## Alternative Installation Methods

### Method 1: Download from GitHub Releases (Recommended)

1. Visit: <https://github.com/mobile-dev-inc/maestro/releases/latest>

2. Download the Windows binary:
   - Look for `maestro-windows-x86_64.zip` (or similar)
   - Or download `maestro.zip` and extract

3. Extract to a permanent location:

   ```powershell
   # Example: Extract to C:\Tools\maestro
   Expand-Archive -Path "maestro-windows-x86_64.zip" -DestinationPath "C:\Tools\maestro"
   ```

4. Add to PATH:

   ```powershell
   # Option A: Current session only
   $env:PATH += ";C:\Tools\maestro\bin"
   
   # Option B: Permanent (as Administrator)
   [Environment]::SetEnvironmentVariable(
       "Path",
       [Environment]::GetEnvironmentVariable("Path", "Machine") + ";C:\Tools\maestro\bin",
       "Machine"
   )
   ```

5. Restart terminal and verify:

   ```powershell
   maestro --version
   ```

### Method 2: Use WSL (Windows Subsystem for Linux)

If you have WSL installed:

```bash
# Inside WSL
curl -Ls "https://get.maestro.mobile.dev" | bash

# Verify
maestro --version
```

Then run tests from WSL:

```bash
cd /mnt/c/GIT/FastMediaSorter_mob_v2
./maestro/run-tests.sh smoke
```

## ❌ Methods That DON'T Work

### WinGet

```powershell
winget search maestro
# Returns accounting software, NOT Maestro Mobile
```

Maestro Mobile is **NOT available** in WinGet.

### NPM

```bash
npm install -g maestro-cli
# Installs WRONG package (Node.js template generator)
```

### Scoop

Maestro Mobile is **NOT available** in official Scoop buckets.

## Verification

After installation:

```powershell
maestro --version
# Should output: "1.37.0" or similar

maestro --help
# Should show commands like: test, studio, record, etc.
```

## Run Tests

```powershell
.\scripts\run-maestro-smoke.ps1
```

## Current Status

### ✅ Working Methods

- **Manual GitHub releases download** - Most reliable
- **WSL (Windows Subsystem for Linux)** - Works perfectly

### ❌ NOT Working / NOT Available

- Official PowerShell installer - Has syntax errors
- WinGet - Package not available
- NPM (`npm install -g maestro-cli`) - Wrong package (Node.js templates)
- Scoop - Package not available

### 💡 Recommended Approach

**For Windows native**: Use GitHub releases (Method 1)  
**For developers with WSL**: Use WSL installation (Method 2)

## References

- GitHub Releases: <https://github.com/mobile-dev-inc/maestro/releases>
- Documentation: <https://maestro.mobile.dev>
- Known Issues: <https://github.com/mobile-dev-inc/maestro/issues>

# Maestro Installation Guide - Windows

## ⚠️ Important Warning

**DO NOT** use `npm install -g maestro-cli` - that installs a **completely different package** (a Node.js template generator), not the Maestro Mobile testing framework!

## Correct Installation for Windows

### ⚠️ Known Issue

The official PowerShell installer (`install.ps1`) may not work correctly on all Windows systems. If you encounter parsing errors, use Method 2 (Manual Installation) instead.

### Method 1: PowerShell Script

1. Open **PowerShell as Administrator**
2. Run the following commands:

```powershell
# Download and run installer
Invoke-WebRequest -Uri "https://get.maestro.mobile.dev/install.ps1" -OutFile install.ps1
.\install.ps1

# Clean up
Remove-Item install.ps1
```

1. Restart your terminal
2. Verify installation:

```powershell
maestro --version
```

**Note**: If you get syntax errors (e.g., "Missing '(' after 'if'"), the script may contain bash code. Use Method 2.

### Method 2: Manual Installation (Recommended)

1. Visit: <https://github.com/mobile-dev-inc/maestro/releases/latest>

2. Download the Windows binary (e.g., `maestro-windows-x86_64.zip`)

3. Extract to a permanent location:

```powershell
# Example: Extract to C:\Tools\maestro
Expand-Archive -Path "maestro-windows-x86_64.zip" -DestinationPath "C:\Tools\maestro"
```

1. Add to PATH (PowerShell as Administrator):

```powershell
[Environment]::SetEnvironmentVariable(
    "Path",
    [Environment]::GetEnvironmentVariable("Path", "Machine") + ";C:\Tools\maestro\bin",
    "Machine"
)
```

1. Restart terminal and verify:

```powershell
maestro --version
```

See [WINDOWS_MANUAL_INSTALL.md](WINDOWS_MANUAL_INSTALL.md) for detailed manual installation instructions.

## Verification

After installation, run:

```powershell
# Check Maestro version
maestro --version

# Should output something like: "1.37.0" or higher
```

## Next Steps

Once Maestro is installed, you can run tests:

```powershell
# Run smoke tests
.\scripts\utils\run-maestro-smoke.ps1

# Or run directly
.\maestro\run-tests.ps1 smoke
```

## Troubleshooting

### "maestro: command not found"

- Ensure you restarted your terminal after installation
- Check if Maestro is in your PATH: `$env:PATH -split ';' | Select-String maestro`
- Try reinstalling with the PowerShell script

### Wrong Package Installed

If you accidentally installed `maestro-cli` from npm:

```powershell
# Remove the wrong package
npm uninstall -g maestro-cli

# Install the correct Maestro
Invoke-WebRequest -Uri "https://get.maestro.mobile.dev/install.ps1" -OutFile install.ps1
.\install.ps1
Remove-Item install.ps1
```

## Official Documentation

- Homepage: <https://maestro.mobile.dev>
- GitHub: <https://github.com/mobile-dev-inc/maestro>
- Documentation: <https://maestro.mobile.dev/getting-started/installing-maestro>

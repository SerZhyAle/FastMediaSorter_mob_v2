# Maestro Installation - Quick Guide for Windows

## TL;DR

```powershell
# 1. Download from GitHub
Start-Process "https://github.com/mobile-dev-inc/maestro/releases/latest"

# 2. Extract maestro.zip to C:\Tools\maestro

# 3. Add to PATH (as Administrator)
[Environment]::SetEnvironmentVariable(
    "Path",
    [Environment]::GetEnvironmentVariable("Path", "Machine") + ";C:\Tools\maestro\bin",
    "Machine"
)

# 4. Restart terminal

# 5. Verify
maestro --version

# 6. Run tests
.\scripts\run-maestro-smoke.ps1
```

## Step-by-Step

1. **Download**: Visit <https://github.com/mobile-dev-inc/maestro/releases/latest>
   - Download the `.zip` file for Windows

2. **Extract**:

   ```powershell
   Expand-Archive -Path "maestro.zip" -DestinationPath "C:\Tools\maestro"
   ```

3. **Add to PATH** (Run PowerShell as Administrator):

   ```powershell
   [Environment]::SetEnvironmentVariable(
       "Path",
       [Environment]::GetEnvironmentVariable("Path", "Machine") + ";C:\Tools\maestro\bin",
       "Machine"
   )
   ```

4. **Restart terminal** (important!)

5. **Verify**:

   ```powershell
   maestro --version
   ```

6. **Run tests**:

   ```powershell
   .\scripts\run-maestro-smoke.ps1
   ```

## ⚠️ Common Mistakes

- ❌ DON'T use `npm install -g maestro-cli` (wrong package!)
- ❌ DON'T use `winget install maestro` (not available)
- ❌ DON'T use official PowerShell installer (broken)
- ✅ DO download from GitHub releases
- ✅ DO restart terminal after adding to PATH

## Troubleshooting

### "maestro: command not found"

- Did you restart the terminal?
- Check PATH: `$env:PATH -split ';' | Select-String maestro`

### Still not working?

- Try WSL method (see [WINDOWS_MANUAL_INSTALL.md](WINDOWS_MANUAL_INSTALL.md))
- Or ask for help with details of what you tried

## Full Documentation

- [WINDOWS_MANUAL_INSTALL.md](WINDOWS_MANUAL_INSTALL.md) - Detailed manual installation
- [INSTALLATION_WINDOWS.md](INSTALLATION_WINDOWS.md) - Complete installation guide
- [FIX_WRONG_PACKAGE.md](FIX_WRONG_PACKAGE.md) - If you installed wrong package

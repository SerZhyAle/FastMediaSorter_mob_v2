# ⚠️ IMPORTANT: Wrong Package Installed

## Problem

If you installed Maestro using:

```bash
npm install -g maestro-cli
```

You installed the **WRONG package**! The `maestro-cli` npm package is a Node.js template generator (not the Maestro Mobile testing framework).

## Fix: Remove Wrong Package and Install Correct Maestro

### Step 1: Remove Wrong Package

```powershell
npm uninstall -g maestro-cli
```

### Step 2: Install Correct Maestro

#### Windows (PowerShell as Administrator)

```powershell
Invoke-WebRequest -Uri "https://get.maestro.mobile.dev/install.ps1" -OutFile install.ps1
.\install.ps1
Remove-Item install.ps1
```

#### macOS/Linux (Homebrew)

```bash
brew tap mobile-dev-inc/tap
brew install maestro
```

#### Linux/macOS (curl)

```bash
curl -Ls "https://get.maestro.mobile.dev" | bash
```

### Step 3: Verify Installation

```powershell
maestro --version
# Should output something like: "1.37.0" or higher
```

### Step 4: Run Tests

```powershell
# Windows
.\scripts\utils\run-maestro-smoke.ps1

# Or directly
.\maestro\run-tests.ps1 smoke
```

## How to Identify Correct vs Wrong Package

### ❌ Wrong Package (maestro-cli from npm)

```bash
maestro-cli --help
# Output: "Criar template do Node..." (Portuguese text about Node templates)
```

### ✅ Correct Package (Maestro Mobile)

```bash
maestro --version
# Output: "1.37.0" (or similar version number)

maestro --help
# Output: Commands like "test", "studio", "record", etc.
```

## Official Resources

- **Maestro Mobile Homepage**: <https://maestro.mobile.dev>
- **Installation Guide**: <https://maestro.mobile.dev/getting-started/installing-maestro>
- **GitHub**: <https://github.com/mobile-dev-inc/maestro>
- **Documentation**: <https://maestro.mobile.dev/getting-started>

## More Details

See [INSTALLATION_WINDOWS.md](INSTALLATION_WINDOWS.md) for complete Windows installation guide.

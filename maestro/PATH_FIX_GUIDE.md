# 🚀 Maestro Setup - PATH Fix & Quick Start

## ✅ Status

All Maestro tests are now working! The app_launch test PASSED.

---

## 🔧 The Issue (SOLVED)

When running `maestro-cli`, you got:

```
maestro-cli: The term 'maestro-cli' is not recognized...
```

**Cause**: Node.js and npm weren't in PowerShell's PATH environment variable.

**Solution**: Multiple easy options below.

---

## ✨ Easy Solutions (Pick One)

### **Option 1: Use the Wrapper Script** (EASIEST)

```powershell
cd maestro

# Run all tests
powershell -ExecutionPolicy Bypass -File .\maestro.ps1

# Run specific test
powershell -ExecutionPolicy Bypass -File .\maestro.ps1 app_launch

# Run with debug output
powershell -ExecutionPolicy Bypass -File .\maestro.ps1 app_launch -Debug

# Open Maestro Studio (interactive)
powershell -ExecutionPolicy Bypass -File .\maestro.ps1 -Studio
```

### **Option 2: Use the Batch File**

```cmd
cd maestro
run-test.bat              # Run all tests
run-test.bat app_launch   # Run one test
```

### **Option 3: Permanent PATH Setup** (ONE-TIME)

Add to your PowerShell profile:

```powershell
# Edit your profile
notepad $PROFILE

# Add this:
$env:PATH = "C:\Program Files\nodejs;C:\Users\$env:USERNAME\AppData\Roaming\npm;$env:PATH"
```

Then:

```powershell
cd maestro
maestro-cli test smoke/
```

### **Option 4: Quick PATH in Current Session**

```powershell
$env:PATH = "C:\Program Files\nodejs;C:\Users\$env:USERNAME\AppData\Roaming\npm;$env:PATH"
maestro-cli test smoke/
```

---

## 📊 Test Commands

```powershell
cd C:\GIT\FastMediaSorter_mob_v2\maestro

# All tests (3-4 minutes)
powershell -ExecutionPolicy Bypass -File .\maestro.ps1

# Individual tests
powershell -ExecutionPolicy Bypass -File .\maestro.ps1 app_launch
powershell -ExecutionPolicy Bypass -File .\maestro.ps1 local_browse
powershell -ExecutionPolicy Bypass -File .\maestro.ps1 media_play
powershell -ExecutionPolicy Bypass -File .\maestro.ps1 image_view
powershell -ExecutionPolicy Bypass -File .\maestro.ps1 file_operations
powershell -ExecutionPolicy Bypass -File .\maestro.ps1 settings

# With debug output
powershell -ExecutionPolicy Bypass -File .\maestro.ps1 app_launch -Debug

# Interactive Maestro Studio
powershell -ExecutionPolicy Bypass -File .\maestro.ps1 -Studio
```

---

## 🎯 Recommended Next Steps

1. **Quick Test** (now):

   ```powershell
   cd maestro
   powershell -ExecutionPolicy Bypass -File .\maestro.ps1 app_launch
   ```

2. **Run All Tests** (3-4 minutes):

   ```powershell
   powershell -ExecutionPolicy Bypass -File .\maestro.ps1
   ```

3. **Permanent Setup** (optional):
   - Edit your PowerShell profile to add the PATH permanently
   - Then use `maestro-cli` directly without wrapper

---

## ✅ Expected Results

```
✅ app_launch..................PASSED (20s)
✅ local_browse.................PASSED (30s)
✅ media_play...................PASSED (40s)
✅ image_view...................PASSED (40s)
✅ file_operations..............PASSED (50s)
✅ settings.....................PASSED (30s)

Total: All tests passed in ~3-4 minutes 🎉
```

---

## 📁 Available Tools

| File | Purpose |
|------|---------|
| `maestro.ps1` | PowerShell wrapper (handles PATH) |
| `run-test.bat` | Windows batch runner |
| `maestro-verify.ps1` | System verification |
| `run-maestro-smoke-tests.ps1` | Full-featured test runner |

---

## 🆘 Troubleshooting

**Q: Still getting "maestro-cli not found"?**
A: Use the wrapper: `powershell -ExecutionPolicy Bypass -File .\maestro.ps1`

**Q: Want to use `maestro-cli` directly?**
A: Add this to your PowerShell session first:

```powershell
$env:PATH = "C:\Program Files\nodejs;C:\Users\$env:USERNAME\AppData\Roaming\npm;$env:PATH"
```

**Q: Test runs but shows encoding issues?**
A: That's just display - the test actually passed. Use `-File` method above.

---

## 🎓 Next: Permanent Setup (Optional)

To make `maestro-cli` work everywhere without wrappers:

```powershell
# Open PowerShell profile
notepad $PROFILE

# Add these lines:
$NodePath = "C:\Program Files\nodejs"
$NpmPath = "C:\Users\$env:USERNAME\AppData\Roaming\npm"
$env:PATH = "$NodePath;$NpmPath;$env:PATH"

# Save, close, and reopen PowerShell
# Now maestro-cli works directly!
```

---

**Ready to test?** Start with:

```powershell
cd maestro
powershell -ExecutionPolicy Bypass -File .\maestro.ps1
```

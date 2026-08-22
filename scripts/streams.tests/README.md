# Stream publisher tests

Run the deterministic publisher suite from the repository root with PowerShell 7 and the installed Pester 3 command:

```powershell
Invoke-Pester -Path scripts/streams.tests -PassThru
```

The suite covers schema and URL rules, prune/probe seams, atlas budgets, favicon-index pairing and ZIP entry contracts. It writes only to Pester's temporary test directory and does not call network services, ffmpeg, GDI+ or GitHub upload.

The production entry point remains `scripts/streams/collect-stream-candidates.ps1`. The tests dot-source the implementation modules directly so contract failures can be diagnosed without running a full catalog collection.

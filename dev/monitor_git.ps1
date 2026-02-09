
Write-Host "Starting Git Update Monitor..."
while ($true) {
    $timestamp = Get-Date -Format "HH:mm:ss"
    Write-Host "[$timestamp] Checking for updates..." -NoNewline
    
    try {
        git fetch origin
        $status = git status -sb
        
        if ($status -match "behind") {
            Write-Host " Updates found! Pulling..." -ForegroundColor Green
            git pull
        } else {
            Write-Host " Up to date." -ForegroundColor Gray
        }
    } catch {
        Write-Host " Error checking git status." -ForegroundColor Red
    }
    
    Start-Sleep -Seconds 30
}

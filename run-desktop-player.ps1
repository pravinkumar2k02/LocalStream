# Launch Local Stream Player Python Client
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host " Starting Local Stream Player (Python Client)" -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan

$pyDir = Join-Path $PSScriptRoot "PythonStreamPlayer"
Set-Location -Path $pyDir

$pythonExe = (Get-Command python -ErrorAction SilentlyContinue)?.Source
if (-not $pythonExe) {
    $fallbackPy = "$env:LOCALAPPDATA\Programs\Python\Python314\python.exe"
    if (Test-Path $fallbackPy) {
        $pythonExe = $fallbackPy
    } else {
        Write-Error "Python was not found in PATH or standard install directories."
        pause
        exit 1
    }
}

& $pythonExe main.py
if ($LASTEXITCODE -ne 0) {
    Write-Host "Checking / installing requirements..." -ForegroundColor Yellow
    & $pythonExe -m pip install -r requirements.txt
    & $pythonExe main.py
}

#Requires -Version 5.1

param(
    [string]$BackupFile = ""
)

$DataPath = "C:\scanner\server\data"
$BackupDir = "C:\scanner\backups"

if ($BackupFile -eq "") {
    $latest = Get-ChildItem -Path $BackupDir -Filter "scanner_backup_*.zip" |
              Sort-Object LastWriteTime -Descending |
              Select-Object -First 1
    if (-not $latest) {
        Write-Host "ERROR: No backup files found in $BackupDir"
        exit 1
    }
    $TargetZip = $latest.FullName
} else {
    if (Test-Path $BackupFile) {
        $TargetZip = $BackupFile
    } elseif (Test-Path (Join-Path $BackupDir $BackupFile)) {
        $TargetZip = Join-Path $BackupDir $BackupFile
    } else {
        Write-Host "ERROR: File not found: $BackupFile"
        exit 1
    }
}

Write-Host "Restoring from: $(Split-Path $TargetZip -Leaf)"

$SafetyTimestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$SafetyZip = Join-Path $BackupDir "pre_restore_$SafetyTimestamp.zip"
$DbFiles = Get-ChildItem -Path $DataPath -Filter "*.db" -ErrorAction SilentlyContinue
if ($DbFiles.Count -gt 0) {
    try {
        Compress-Archive -Path "$DataPath\*.db" -DestinationPath $SafetyZip -Force
        Write-Host "Safety backup created: $(Split-Path $SafetyZip -Leaf)"
    } catch {
        Write-Host "ERROR: Safety backup failed - $_"
        exit 1
    }
}

try {
    Expand-Archive -Path $TargetZip -DestinationPath $DataPath -Force
    Write-Host "Restore complete."
} catch {
    Write-Host "ERROR: Restore failed - $_"
    exit 1
}

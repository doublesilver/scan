#Requires -Version 5.1

$DataPath = "C:\scanner\server\data"
$BackupDir = "C:\scanner\backups"
$Timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$ZipName = "scanner_backup_$Timestamp.zip"
$ZipPath = Join-Path $BackupDir $ZipName

if (-not (Test-Path $BackupDir)) {
    New-Item -ItemType Directory -Path $BackupDir | Out-Null
}

$DbFiles = Get-ChildItem -Path $DataPath -Filter "*.db" -ErrorAction SilentlyContinue
if ($DbFiles.Count -eq 0) {
    Write-Host "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] ERROR: No .db files found in $DataPath"
    exit 1
}

try {
    Compress-Archive -Path "$DataPath\*.db" -DestinationPath $ZipPath -Force
    Write-Host "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] Backup created: $ZipName"
} catch {
    Write-Host "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] ERROR: Backup failed - $_"
    exit 1
}

$Cutoff = (Get-Date).AddDays(-7)
Get-ChildItem -Path $BackupDir -Filter "scanner_backup_*.zip" | Where-Object {
    $_.LastWriteTime -lt $Cutoff
} | ForEach-Object {
    try {
        Remove-Item $_.FullName -Force
        Write-Host "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] Deleted old backup: $($_.Name)"
    } catch {
        Write-Host "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] ERROR: Failed to delete $($_.Name) - $_"
    }
}

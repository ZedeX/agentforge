$ErrorActionPreference = 'Continue'
$base = 'C:\Users\Administrator\.m2\repository\io\gatling'
if (Test-Path $base) {
    Get-ChildItem $base -Recurse -Filter '*.lastUpdated' -File | ForEach-Object {
        $path = $_.FullName
        try {
            [System.IO.File]::Delete($path)
            Write-Host "Deleted: $path"
        } catch {
            Write-Host "Failed: $path - $_"
        }
    }
} else {
    Write-Host "Path not found: $base"
}
Write-Host "Done."

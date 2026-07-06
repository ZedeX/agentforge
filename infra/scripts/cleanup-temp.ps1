$ErrorActionPreference = 'Continue'
$files = @(
    'e:\git\Agent-Platform-Prototype\t1-commit-msg.txt',
    'e:\git\Agent-Platform-Prototype\t2-commit-msg.txt',
    'e:\git\Agent-Platform-Prototype\t3-commit-msg.txt',
    'e:\git\Agent-Platform-Prototype\t4-commit-msg.txt',
    'e:\git\Agent-Platform-Prototype\t5-commit-msg.txt',
    'e:\git\Agent-Platform-Prototype\t5-summary.txt',
    'e:\git\Agent-Platform-Prototype\t7-commit-msg.txt',
    'e:\git\Agent-Platform-Prototype\t8-commit-msg.txt',
    'e:\git\Agent-Platform-Prototype\t10-commit-msg.txt',
    'e:\git\Agent-Platform-Prototype\t11-commit-msg.txt',
    'e:\git\Agent-Platform-Prototype\t12-commit-msg.txt'
)
foreach ($f in $files) {
    if (Test-Path $f) {
        try {
            [System.IO.File]::Delete($f)
            Write-Host "Deleted: $f"
        } catch {
            Write-Host "Failed: $f - $_"
        }
    } else {
        Write-Host "Not found: $f"
    }
}
Write-Host "Done."

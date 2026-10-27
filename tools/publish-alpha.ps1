# Manually launch the main-only workflow for another auto-versioned alpha release.
$ErrorActionPreference = 'Stop'
$repo = 'CommitAndQuit/txn-sense'
& gh workflow run release.yml --repo $repo --ref main
if ($LASTEXITCODE -ne 0) { throw 'Could not start the alpha release workflow.' }
Write-Output "Release build started: https://github.com/$repo/actions/workflows/release.yml"
Write-Output 'The workflow calculates the next version, then publishes the APK and F-Droid repository update.'

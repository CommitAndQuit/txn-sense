# Launch the reviewed, main-only GitHub release workflow.
$ErrorActionPreference = 'Stop'
$repo = 'CommitAndQuit/txn-sense'
& gh workflow run release.yml --repo $repo --ref main
if ($LASTEXITCODE -ne 0) { throw 'Could not start the alpha release workflow.' }
Write-Output "Release build started: https://github.com/$repo/actions/workflows/release.yml"
Write-Output 'When it succeeds, review the published pre-release and verify the F-Droid deployment.'

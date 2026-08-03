# Upload family-library-app to GitHub, run CI, download debug APK.
# Requires: C:\Users\shujiewe\.github_token (classic PAT with repo scope)
param(
    [string]$ProjectDir = "C:\Users\shujiewe\family-library-app",
    [string]$Repo = "family-library-app",
    [string]$Branch = "main",
    [string]$OutApk = "C:\Users\shujiewe\family-library-app\family-library-debug.apk"
)

$ErrorActionPreference = "Stop"

function Get-GhHeaders([string]$Token) {
    @{
        Authorization        = "Bearer $Token"
        Accept               = "application/vnd.github+json"
        "X-GitHub-Api-Version" = "2022-11-28"
        "User-Agent"         = "family-library-ci"
    }
}

function Invoke-Gh([string]$Method, [string]$Uri, [hashtable]$Headers, $Body = $null) {
    $params = @{ Method = $Method; Uri = $Uri; Headers = $Headers }
    if ($null -ne $Body) {
        $params.Body = $Body
        $params.ContentType = "application/json"
    }
    Invoke-RestMethod @params
}

function Get-ContentSha([string]$RepoUri, [string]$RelPath, [string]$Branch, [hashtable]$Headers) {
    $encoded = [Uri]::EscapeDataString($RelPath).Replace("%2F", "/")
    try {
        $existing = Invoke-Gh "GET" "$RepoUri/contents/${encoded}?ref=$Branch" $Headers
        return $existing.sha
    } catch {
        return $null
    }
}

$tokenPath = "C:\Users\shujiewe\.github_token"
if (-not (Test-Path $tokenPath)) { throw "Missing token file: $tokenPath" }
$token = (Get-Content $tokenPath -Raw).Trim()
if ([string]::IsNullOrWhiteSpace($token)) { throw "Token file is empty" }

$H = Get-GhHeaders $token
$user = Invoke-Gh "GET" "https://api.github.com/user" $H
$owner = $user.login
Write-Host "GitHub user: $owner"

$repoUri = "https://api.github.com/repos/$owner/$Repo"
try {
    $repoInfo = Invoke-Gh "GET" $repoUri $H
    Write-Host "Repo exists: $($repoInfo.full_name)"
} catch {
    Write-Host "Repo '$Repo' not found. Create it first:" -ForegroundColor Yellow
    Write-Host "  https://github.com/new  (name: $Repo, Public, no README)" -ForegroundColor Yellow
    Write-Host "Then grant this token Contents (read/write) + Actions (read/write) on that repo." -ForegroundColor Yellow
    throw "Repository $owner/$Repo is missing or token cannot access it."
}

# Permission probe
try {
    $probe = @{
        message = "ci: permission probe"
        content = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("ok"))
        branch  = $Branch
    }
    $probePath = ".github/ci-probe-$([Guid]::NewGuid().ToString('N').Substring(0,8)).txt"
    Invoke-Gh "PUT" "$repoUri/contents/$probePath" $H ($probe | ConvertTo-Json) | Out-Null
} catch {
    throw "Token cannot write to $owner/$Repo. Use a classic PAT with 'repo' scope, or fine-grained PAT with Contents + Actions on this repo."
}

$excludeDirs = @(
    ".git", ".gradle", ".idea", "build", "app\build", "out", ".kotlin", "captures", ".cxx"
)
$excludeFiles = @("local.properties", "*.apk", "*.aab")

$files = Get-ChildItem -Path $ProjectDir -Recurse -File | Where-Object {
    $rel = $_.FullName.Substring($ProjectDir.Length + 1).Replace("\", "/")
    foreach ($d in $excludeDirs) {
        if ($rel -like "$d/*" -or $rel -eq $d) { return $false }
    }
    foreach ($f in $excludeFiles) {
        if ($rel -like $f) { return $false }
    }
    return $true
}

Write-Host "Uploading $($files.Count) files ..."
foreach ($file in $files) {
    $rel = $file.FullName.Substring($ProjectDir.Length + 1).Replace("\", "/")
    $bytes = [System.IO.File]::ReadAllBytes($file.FullName)
    $b64 = [Convert]::ToBase64String($bytes)
    $sha = Get-ContentSha $repoUri $rel $Branch $H
    $encoded = [Uri]::EscapeDataString($rel).Replace("%2F", "/")

    $bodyObj = [ordered]@{
        message = "ci: update $rel"
        content = $b64
        branch  = $Branch
    }
    if ($sha) { $bodyObj["sha"] = $sha }
    $body = $bodyObj | ConvertTo-Json -Compress
    Invoke-Gh "PUT" "$repoUri/contents/$encoded" $H $body | Out-Null
    Write-Host "  uploaded $rel"
}

Write-Host "Triggering workflow ..."
$wfBody = (@{ ref = $Branch } | ConvertTo-Json)
Invoke-Gh "POST" "$repoUri/actions/workflows/android-ci.yml/dispatches" $H $wfBody | Out-Null

Write-Host "Waiting for workflow run ..."
$run = $null
for ($i = 0; $i -lt 90; $i++) {
    Start-Sleep -Seconds 10
    $runs = Invoke-Gh "GET" "$repoUri/actions/workflows/android-ci.yml/runs?per_page=1" $H
    if ($runs.workflow_runs.Count -gt 0) {
        $run = $runs.workflow_runs[0]
        Write-Host "  status=$($run.status) conclusion=$($run.conclusion)"
        if ($run.status -eq "completed") { break }
    }
}
if (-not $run) { throw "No workflow run found" }
if ($run.conclusion -ne "success") {
    throw "Workflow failed: $($run.html_url)"
}

Write-Host "Downloading APK artifact ..."
$arts = Invoke-Gh "GET" "$repoUri/actions/runs/$($run.id)/artifacts" $H
$artifact = $arts.artifacts | Where-Object { $_.name -eq "family-library-debug-apk" } | Select-Object -First 1
if (-not $artifact) { throw "APK artifact not found" }

$zipPath = "$env:TEMP\family-library-debug-apk.zip"
Invoke-WebRequest -Uri $artifact.archive_download_url -Headers $H -OutFile $zipPath
Expand-Archive -Path $zipPath -DestinationPath "$env:TEMP\family-library-apk" -Force
$apk = Get-ChildItem "$env:TEMP\family-library-apk" -Filter "*.apk" -Recurse | Select-Object -First 1
if (-not $apk) { throw "No APK in artifact zip" }
Copy-Item $apk.FullName $OutApk -Force

Write-Host ""
Write-Host "SUCCESS"
Write-Host "APK: $OutApk"
Write-Host "Run: $($run.html_url)"
Write-Host "Repo: https://github.com/$owner/$Repo"

# Downloads the standalone Tailwind CSS CLI binary for Windows into rls-adapter-web/bin/.
# Idempotent: does nothing if the binary is already present. No Node/npm required.
$ErrorActionPreference = "Stop"

$binDir = Join-Path $PSScriptRoot "..\bin"
$binPath = Join-Path $binDir "tailwindcss.exe"

if (Test-Path $binPath) {
    exit 0
}

New-Item -ItemType Directory -Force -Path $binDir | Out-Null

$url = "https://github.com/tailwindlabs/tailwindcss/releases/latest/download/tailwindcss-windows-x64.exe"

Write-Host "Downloading Tailwind CLI (windows-x64)..."
Invoke-WebRequest -Uri $url -OutFile $binPath

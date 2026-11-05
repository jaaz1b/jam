# Jam: one-time setup so Spotify and Apple Music can play.
#
# Windows only plays protected (DRM) music in apps that carry a Widevine
# "VMP" signature. castLabs signs apps for free with your own EVS account.
# This script:
#   1. makes sure Python is installed (installs it for your user via winget if not),
#   2. installs castLabs' official signing tool (castlabs-evs, from PyPI),
#   3. signs in to (or creates) your free castLabs EVS account,
#   4. signs this copy of Jam and restarts it.
# Re-run it after installing a new version of Jam (it's quick the second time).

param(
  [Parameter(Mandatory = $true)][string]$AppDir,
  [Parameter(Mandatory = $true)][string]$ExePath,
  [Parameter(Mandatory = $true)][string]$Version
)

$ErrorActionPreference = 'Stop'
$Host.UI.RawUI.WindowTitle = 'Jam - Spotify & Apple Music setup'

function Say($text, $color = 'Gray') { Write-Host $text -ForegroundColor $color }
function Step($n, $text) { Write-Host ''; Write-Host "[$n/4] $text" -ForegroundColor Magenta }
function Fail($text) {
  Write-Host ''
  Write-Host $text -ForegroundColor Red
  Write-Host ''
  Read-Host 'Press Enter to close'
  exit 1
}

Clear-Host
Say ''
Say '  Jam - Spotify & Apple Music setup' 'White'
Say '  ----------------------------------'
Say '  Spotify and Apple Music only play in apps signed for protected playback.'
Say '  castLabs (the company behind Jam''s media engine) does this for free.'
Say '  You''ll need an email address for a free castLabs account. Takes ~2 minutes.'
Say ''

# ---------------------------------------------------------------- 1. Python
Step 1 'Checking for Python'
$pyExe = $null; $pyArgs = @()
foreach ($cand in 'py -3', 'python', 'python3') {
  $parts = $cand -split ' '
  $exe = $parts[0]
  $extra = @()
  if ($parts.Length -gt 1) { $extra = $parts[1..($parts.Length - 1)] }
  try {
    $out = & $exe @extra -c "import sys; print(sys.version_info[0] * 100 + sys.version_info[1])" 2>$null
    if ($LASTEXITCODE -eq 0 -and "$out".Trim() -match '^\d+$' -and [int]"$out".Trim() -ge 308) { $pyExe = $exe; $pyArgs = $extra; break }
  } catch {}
}
if (-not $pyExe) {
  Say 'Python not found. Installing Python 3.12 for your user (no admin needed)...'
  try {
    winget install -e --id Python.Python.3.12 --scope user --silent --accept-package-agreements --accept-source-agreements | Out-Host
  } catch {
    Fail 'Could not install Python automatically. Install it from https://www.python.org/downloads/ (tick "Add to PATH"), then run this setup again from Jam.'
  }
  $guess = Join-Path $env:LOCALAPPDATA 'Programs\Python\Python312\python.exe'
  if (Test-Path -LiteralPath $guess) { $pyExe = $guess; $pyArgs = @() }
  else { Fail 'Python was installed but could not be found. Restart your PC and run this setup again from Jam.' }
}
# Resolve the Python launcher to its full path, and give the helper a name that can't collide with
# it: PowerShell names are case-insensitive and functions win over programs, so a helper called
# "Py" calling "py" would call itself forever ("call depth overflow").
$pyPath = (Get-Command $pyExe -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1).Source
if (-not $pyPath) { $pyPath = $pyExe }
function Invoke-JamPython { & $pyPath @pyArgs @args }
Say "Using Python: $pyPath $pyArgs" 'DarkGray'

# ---------------------------------------------------------------- 2. castlabs-evs
Step 2 'Installing castLabs signing tool (castlabs-evs)'
Invoke-JamPython -m pip install --user --upgrade --disable-pip-version-check --quiet castlabs-evs
if ($LASTEXITCODE -ne 0) { Fail 'Could not install castlabs-evs with pip. Check your internet connection and try again.' }
Say 'Done.' 'Green'

# ---------------------------------------------------------------- 3. account
Step 3 'Signing in to your castLabs EVS account'
$ErrorActionPreference = 'Continue'
Invoke-JamPython -m castlabs_evs.account -n refresh *> $null
$haveSession = ($LASTEXITCODE -eq 0)
$ErrorActionPreference = 'Stop'
if ($haveSession) {
  Say 'Already signed in.' 'Green'
} else {
  $ans = Read-Host 'Do you already have a castLabs EVS account? (y/N)'
  if ($ans -match '^(y|yes)$') {
    Invoke-JamPython -m castlabs_evs.account reauth
  } else {
    Say ''
    Say 'Creating a free account. castLabs will email you a confirmation code;' 'White'
    Say 'type it in when asked. (Organization can be anything, e.g. "Personal".)' 'White'
    Say ''
    Invoke-JamPython -m castlabs_evs.account signup
  }
  if ($LASTEXITCODE -ne 0) { Fail 'Signing in to castLabs EVS did not finish. Run this setup again from Jam to retry.' }
}

# ---------------------------------------------------------------- 4. sign + restart
Step 4 'Signing Jam for protected playback'
Say 'Closing Jam for a moment...' 'DarkGray'
$name = [IO.Path]::GetFileNameWithoutExtension($ExePath)
Get-Process -Name $name -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 2
# -H: sign Jam.exe specifically (the folder also holds "Uninstall Jam.exe").
Invoke-JamPython -m castlabs_evs.vmp sign-pkg -f -H $name "$AppDir"
if ($LASTEXITCODE -ne 0) { Start-Process -FilePath $ExePath; Fail 'Signing failed. Jam has been restarted; you can retry from Settings > Sources.' }
Invoke-JamPython -m castlabs_evs.vmp verify-pkg -H $name "$AppDir"
if ($LASTEXITCODE -ne 0) { Start-Process -FilePath $ExePath; Fail 'The signature could not be verified. Jam has been restarted; you can retry from Settings > Sources.' }

@{ version = $Version; signedAt = (Get-Date).ToString('o') } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $AppDir 'jam-vmp.json') -Encoding ASCII

Say ''
Say 'All set! Spotify and Apple Music can now play in Jam. Restarting Jam...' 'Green'
Start-Process -FilePath $ExePath
Start-Sleep -Seconds 3

<#
.SYNOPSIS
  Starts the TenantFlow backend for local development: http://localhost:8095, profile "local".

.DESCRIPTION
  Checks the things that usually go wrong before handing over to Maven, and stops with a
  clear message instead of a long Spring stack trace:
    1. Java 21 or newer is available.
    2. Ports 8095 (API) and 8091 (management) are free. If something holds them, it says
       which process - and whether it is already a TenantFlow backend - so a second backend
       is never started next to the first.
    3. PostgreSQL accepts connections (localhost:5432 unless SPRING_DATASOURCE_URL says otherwise).
    4. TENANTFLOW_JWT_SECRET is present (environment, Windows user environment or the
       git-ignored .env file) and at least 32 characters. Only its length is checked; the
       value is never printed. Google OAuth stays optional and is not checked.
  Then it runs the Maven wrapper with the "local" profile (application-local.properties).

  Used by the "backend" entry in .claude/launch.json. From a terminal:
    powershell -NoProfile -File scripts\dev-backend.ps1
#>
$ErrorActionPreference = 'Stop'
$backendDir = Join-Path $PSScriptRoot '..\services\tenantflow-backend' | Resolve-Path

function Fail([string]$message) {
  Write-Host ""
  Write-Host "[dev-backend] $message" -ForegroundColor Red
  exit 1
}

# 1. Java -------------------------------------------------------------------------------------
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' }
$versionLine = ''
try {
  # java -version prints to stderr, which Windows PowerShell would otherwise treat as an error.
  $ErrorActionPreference = 'Continue'
  $versionLine = (& $java -version 2>&1 | Select-Object -First 1).ToString()
} catch {
  Fail "Java was not found. Install JDK 21 and set JAVA_HOME to its folder."
} finally {
  $ErrorActionPreference = 'Stop'
}
if ($versionLine -match '"(\d+)') {
  if ([int]$Matches[1] -lt 21) { Fail "Java 21 or newer is required; found: $versionLine. Point JAVA_HOME at a JDK 21." }
}

# 2. Ports ------------------------------------------------------------------------------------
foreach ($port in 8095, 8091) {
  $listener = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
  if (-not $listener) { continue }
  $owner = Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)" -ErrorAction SilentlyContinue
  $what = if ($owner -and $owner.CommandLine -match 'TenantflowBackendApplication') {
    'It is already a TenantFlow backend - use that one, or stop it first'
  } else {
    'It is not a TenantFlow backend - stop it, or free the port'
  }
  $name = if ($owner) { $owner.Name } else { 'unknown process' }
  Fail "Port $port is already in use by $name (PID $($listener.OwningProcess)). $what (Stop-Process -Id $($listener.OwningProcess))."
}

# 3. PostgreSQL -------------------------------------------------------------------------------
$dbHost = 'localhost'; $dbPort = 5432
if ($env:SPRING_DATASOURCE_URL -match '//([^:/]+):(\d+)/') { $dbHost = $Matches[1]; $dbPort = [int]$Matches[2] }
$client = New-Object System.Net.Sockets.TcpClient
try {
  $connected = $client.ConnectAsync($dbHost, $dbPort).Wait(2000)
} catch {
  $connected = $false
} finally {
  $client.Dispose()
}
if (-not $connected) {
  Fail "PostgreSQL is not reachable on ${dbHost}:${dbPort}. Start it (for the bundled database: docker compose up -d postgres) and try again."
}

# 4. JWT secret (checked, never printed) -----------------------------------------------------
# Same order as the backend: a real environment variable wins over the .env file.
# A user-level variable set after this window or the desktop app was opened is not inherited
# yet, so it is read directly and passed on to the backend.
$secretSource = $null
if (-not $env:TENANTFLOW_JWT_SECRET) {
  $userValue = [Environment]::GetEnvironmentVariable('TENANTFLOW_JWT_SECRET', 'User')
  if ($userValue) { $env:TENANTFLOW_JWT_SECRET = $userValue; $secretSource = 'your Windows user environment' }
  Remove-Variable userValue -ErrorAction SilentlyContinue
} else {
  $secretSource = 'the environment'
}
$secretLength = 0
if ($env:TENANTFLOW_JWT_SECRET) {
  $secretLength = $env:TENANTFLOW_JWT_SECRET.Length
} else {
  $dotEnv = Join-Path $PSScriptRoot '..\.env'
  if (Test-Path $dotEnv) {
    $line = Get-Content $dotEnv | Where-Object { $_ -match '^\s*TENANTFLOW_JWT_SECRET\s*=' } | Select-Object -Last 1
    if ($line) { $secretLength = ($line -replace '^\s*TENANTFLOW_JWT_SECRET\s*=\s*', '').TrimEnd().Length; $secretSource = 'the .env file' }
    Remove-Variable line -ErrorAction SilentlyContinue
  }
}
if ($secretLength -eq 0) {
  Fail "TENANTFLOW_JWT_SECRET is not set. Add TENANTFLOW_JWT_SECRET=<random value, 32+ characters> to the .env file at the repository root (copy .env.example), or set it as an environment variable."
}
if ($secretLength -lt 32) {
  Fail "TENANTFLOW_JWT_SECRET (from $secretSource) is too short: it must be at least 32 characters."
}

# 5. Run --------------------------------------------------------------------------------------
Write-Host "[dev-backend] Starting TenantFlow backend from $backendDir"
Write-Host "[dev-backend] API http://localhost:8095  database ${dbHost}:${dbPort}  profile local"
Set-Location $backendDir
& .\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local'
exit $LASTEXITCODE

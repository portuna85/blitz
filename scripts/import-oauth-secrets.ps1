param(
    [string]$GoogleCredentialsFile = "secret/google-oauth.json",
    [string]$NaverCredentialsFile = "secret/naver-oauth.json",
    [string]$EnvFile = ".env"
)

$ErrorActionPreference = "Stop"

function Read-OAuthCredential {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Provider,
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    $resolvedPath = (Resolve-Path -LiteralPath $Path).Path
    $file = Get-Item -LiteralPath $resolvedPath
    if ($file.Length -eq 0 -or $file.Length -gt 16KB) {
        throw "$Provider OAuth credential file has an invalid size: $resolvedPath"
    }

    try {
        $document = Get-Content -Raw -Encoding UTF8 -LiteralPath $resolvedPath | ConvertFrom-Json
    } catch {
        # Parser errors can include source fragments, so replace them with a
        # path-only message that cannot disclose credential values.
        throw "$Provider OAuth credential file could not be parsed: $resolvedPath"
    }
    $clientId = [string]$document.web.client_id
    $clientSecret = [string]$document.web.client_secret
    if ([string]::IsNullOrWhiteSpace($clientId) -or [string]::IsNullOrWhiteSpace($clientSecret)) {
        throw "$Provider OAuth credential file must contain web.client_id and web.client_secret"
    }
    if ($clientId -match '[\r\n]' -or $clientSecret -match '[\r\n]') {
        throw "$Provider OAuth credential values must be single-line strings"
    }

    [pscustomobject]@{
        ClientId = $clientId.Trim()
        ClientSecret = $clientSecret.Trim()
    }
}

function Set-EnvValues {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,
        [Parameter(Mandatory = $true)]
        [System.Collections.Specialized.OrderedDictionary]$Values
    )

    $absolutePath = [IO.Path]::GetFullPath((Join-Path (Get-Location) $Path))
    $parent = Split-Path -Parent $absolutePath
    if (-not (Test-Path -LiteralPath $parent -PathType Container)) {
        throw "Environment file directory does not exist: $parent"
    }

    $lines = if (Test-Path -LiteralPath $absolutePath) {
        [Collections.Generic.List[string]](Get-Content -Encoding UTF8 -LiteralPath $absolutePath)
    } else {
        [Collections.Generic.List[string]]::new()
    }

    $obsoleteKeys = @("GOOGLE_OAUTH_CREDENTIALS_FILE", "NAVER_OAUTH_CREDENTIALS_FILE")
    $result = [Collections.Generic.List[string]]::new()
    $written = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($line in $lines) {
        if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=') {
            $key = $matches[1]
            if ($obsoleteKeys -contains $key) {
                continue
            }
            if ($Values.Contains($key)) {
                if ($written.Add($key)) {
                    $result.Add("$key=$($Values[$key])")
                }
                continue
            }
        }
        $result.Add($line)
    }

    foreach ($key in $Values.Keys) {
        if ($written.Add([string]$key)) {
            $result.Add("$key=$($Values[$key])")
        }
    }

    $temporaryPath = Join-Path $parent ("." + [IO.Path]::GetFileName($absolutePath) + "." + [guid]::NewGuid().ToString("N") + ".tmp")
    try {
        [IO.File]::WriteAllLines($temporaryPath, $result, [Text.UTF8Encoding]::new($false))
        Move-Item -LiteralPath $temporaryPath -Destination $absolutePath -Force
    } finally {
        if (Test-Path -LiteralPath $temporaryPath) {
            Remove-Item -LiteralPath $temporaryPath -Force
        }
    }
}

$google = Read-OAuthCredential -Provider "Google" -Path $GoogleCredentialsFile
$naver = Read-OAuthCredential -Provider "Naver" -Path $NaverCredentialsFile

$values = [ordered]@{
    GOOGLE_CLIENT_ID = $google.ClientId
    GOOGLE_CLIENT_SECRET = $google.ClientSecret
    NAVER_CLIENT_ID = $naver.ClientId
    NAVER_CLIENT_SECRET = $naver.ClientSecret
}

Set-EnvValues -Path $EnvFile -Values $values
Write-Output "OAuth credentials imported into $EnvFile (values hidden)."

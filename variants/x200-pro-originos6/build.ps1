param(
    [Parameter(Mandatory = $true)][string]$AndroidJar,
    [Parameter(Mandatory = $true)][string]$BuildTools,
    [Parameter(Mandatory = $true)][string]$KeyStore,
    [string]$KeyAlias = 'gemini-local',
    [string]$JavaBin,
    [string]$OutputDirectory = (Join-Path $PSScriptRoot 'build')
)

$ErrorActionPreference = 'Stop'

function Invoke-BuildTool {
    param([string]$Tool, [string[]]$ToolArguments)
    & $Tool @ToolArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Ошибка сборки: $([System.IO.Path]::GetFileName($Tool)), код $LASTEXITCODE"
    }
}

if (-not $env:GEMINI_KEYSTORE_PASSWORD) {
    throw 'Задайте пароль ключа в переменной окружения GEMINI_KEYSTORE_PASSWORD'
}
$AndroidJar = (Resolve-Path -LiteralPath $AndroidJar).Path
$BuildTools = (Resolve-Path -LiteralPath $BuildTools).Path
$KeyStore = (Resolve-Path -LiteralPath $KeyStore).Path
if (-not $JavaBin) {
    $JavaBin = Split-Path -Parent (Get-Command javac.exe -ErrorAction Stop).Source
}
$java = Join-Path $JavaBin 'java.exe'
$javac = Join-Path $JavaBin 'javac.exe'
$aapt2 = Join-Path $BuildTools 'aapt2.exe'
$zipalign = Join-Path $BuildTools 'zipalign.exe'
$d8 = Join-Path $BuildTools 'lib\d8.jar'
$apksigner = Join-Path $BuildTools 'lib\apksigner.jar'
foreach ($toolPath in @($java, $javac, $aapt2, $zipalign, $d8, $apksigner)) {
    if (-not (Test-Path -LiteralPath $toolPath -PathType Leaf)) {
        throw "Не найден инструмент: $toolPath"
    }
}

New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$OutputDirectory = (Resolve-Path -LiteralPath $OutputDirectory).Path

# Отдельная папка исключает попадание устаревших классов в новую сборку
$workDirectory = Join-Path $OutputDirectory ('work-' + [Guid]::NewGuid().ToString('N'))
$generatedDirectory = Join-Path $workDirectory 'generated'
$classesDirectory = Join-Path $workDirectory 'classes'
$dexDirectory = Join-Path $workDirectory 'dex'
foreach ($directory in @($generatedDirectory, $classesDirectory, $dexDirectory)) {
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
}
$resourceArchive = Join-Path $workDirectory 'resources.zip'
$unsignedApk = Join-Path $workDirectory 'unsigned.apk'
$alignedApk = Join-Path $workDirectory 'aligned.apk'
$outputApk = Join-Path $OutputDirectory 'Gemini_X200_Pro_OriginOS6_v3.1.apk'

# Ресурсы и класс R создаются штатным компилятором Android
Invoke-BuildTool -Tool $aapt2 -ToolArguments @('compile', '--dir', (Join-Path $PSScriptRoot 'app\res'), '-o', $resourceArchive)
Invoke-BuildTool -Tool $aapt2 -ToolArguments @(
    'link', '-o', $unsignedApk, '-I', $AndroidJar, $resourceArchive,
    '--manifest', (Join-Path $PSScriptRoot 'app\AndroidManifest.xml'), '--java', $generatedDirectory
)
$javaSources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'app\src'), $generatedDirectory -Recurse -Filter '*.java' |
    Select-Object -ExpandProperty FullName)
Invoke-BuildTool -Tool $javac -ToolArguments (@('--release', '8', '-encoding', 'UTF-8', '-classpath', $AndroidJar, '-d', $classesDirectory) + $javaSources)
$classFiles = @(Get-ChildItem -LiteralPath $classesDirectory -Recurse -Filter '*.class' | Select-Object -ExpandProperty FullName)
Invoke-BuildTool -Tool $java -ToolArguments (@('-cp', $d8, 'com.android.tools.r8.D8', '--min-api', '30', '--lib', $AndroidJar, '--output', $dexDirectory) + $classFiles)

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$apkArchive = [System.IO.Compression.ZipFile]::Open($unsignedApk, [System.IO.Compression.ZipArchiveMode]::Update)
try {
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
        $apkArchive, (Join-Path $dexDirectory 'classes.dex'), 'classes.dex',
        [System.IO.Compression.CompressionLevel]::Optimal
    ) | Out-Null
} finally {
    $apkArchive.Dispose()
}
Invoke-BuildTool -Tool $zipalign -ToolArguments @('-f', '4', $unsignedApk, $alignedApk)
Invoke-BuildTool -Tool $java -ToolArguments @(
    '-jar', $apksigner, 'sign', '--ks', $KeyStore, '--ks-key-alias', $KeyAlias,
    '--ks-pass', 'env:GEMINI_KEYSTORE_PASSWORD', '--out', $outputApk, $alignedApk
)
Invoke-BuildTool -Tool $java -ToolArguments @('-jar', $apksigner, 'verify', $outputApk)
Write-Output "APK собран и подпись проверена: $outputApk"

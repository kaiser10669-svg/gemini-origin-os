param(
    [string]$Installer = (Join-Path $PSScriptRoot '..\Install_Gemini_X200_Pro_OriginOS6.bat')
)

$ErrorActionPreference = 'Stop'
$Installer = (Resolve-Path -LiteralPath $Installer).Path
$checkRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('Gemini проверка установщика ' + [Guid]::NewGuid().ToString('N'))
$mockDirectory = Join-Path $checkRoot 'mock'
New-Item -ItemType Directory -Path $mockDirectory -Force | Out-Null

# Подмены исполняются только в копии установщика и не обращаются к телефону
$mockSource = @'
using System;
using System.IO;
using System.Reflection;
using System.Text;

public class InstallerMock {
    public static int Main(string[] args) {
        string name = Path.GetFileNameWithoutExtension(Assembly.GetExecutingAssembly().Location);
        if (name.Equals("choice", StringComparison.OrdinalIgnoreCase)) {
            return Environment.GetEnvironmentVariable("GEMINI_CHECK_CHOICE") == "N" ? 2 : 1;
        }
        if (name.Equals("ping", StringComparison.OrdinalIgnoreCase)) return 0;
        string command = string.Join("\t", args);
        File.AppendAllText(Environment.GetEnvironmentVariable("GEMINI_CHECK_LOG"), command + "\n", new UTF8Encoding(false));
        if (command == "get-state" && Environment.GetEnvironmentVariable("GEMINI_CHECK_RECOVERY") == "Y") {
            int attempts = 0;
            foreach (string line in File.ReadAllLines(Environment.GetEnvironmentVariable("GEMINI_CHECK_LOG"))) {
                if (line == "get-state") attempts++;
            }
            if (attempts == 1) return 1;
        }
        string failure = Environment.GetEnvironmentVariable("GEMINI_CHECK_FAILURE");
        if (!string.IsNullOrEmpty(failure) && (command == failure || command.StartsWith(failure + "\t", StringComparison.Ordinal))) {
            Console.Error.WriteLine("Simulated ADB error");
            return 1;
        }
        if (command == "get-state") Console.WriteLine("device");
        if (command == "shell\tgetprop\tro.product.model") Console.WriteLine("X200 Pro");
        if (command == "shell\tgetprop\tro.build.display.id") Console.WriteLine("OriginOS 6");
        if (args.Length > 0 && args[0] == "install") Console.WriteLine("Success");
        return 0;
    }
}
'@
$mockAdb = Join-Path $mockDirectory 'adb.exe'
Add-Type -TypeDefinition $mockSource -OutputAssembly $mockAdb -OutputType ConsoleApplication
Copy-Item -LiteralPath $mockAdb -Destination (Join-Path $mockDirectory 'choice.exe')
Copy-Item -LiteralPath $mockAdb -Destination (Join-Path $mockDirectory 'ping.exe')

$cases = @(
    @{ Name = 'success_repository'; Exit = 0; Count = 12 },
    @{ Name = 'success_portable'; Exit = 0; Count = 12; Portable = $true },
    @{ Name = 'connection_recovers'; Exit = 0; Count = 13; Recovery = 'Y' },
    @{ Name = 'cancel'; Exit = 0; Count = 3; Choice = 'N' },
    @{ Name = 'missing_apk'; Exit = 1; Count = 0; MissingApk = $true },
    @{ Name = 'missing_adb'; Exit = 1; Count = 0; MissingAdb = $true },
    @{ Name = 'connection_error'; Exit = 1; Count = 10; Failure = 'get-state' },
    @{ Name = 'install_error'; Exit = 1; Count = 4; Failure = 'install' },
    @{ Name = 'grant_error'; Exit = 1; Count = 5; Failure = "shell`tpm`tgrant" },
    @{ Name = 'write_settings_error'; Exit = 1; Count = 6; Failure = "shell`tcmd`tappops`tset`t--user`t0`tcom.kaiserdev.geminitrigger`tWRITE_SETTINGS" },
    @{ Name = 'overlay_error'; Exit = 1; Count = 7; Failure = "shell`tcmd`tappops`tset`t--user`t0`tcom.kaiserdev.geminitrigger`tSYSTEM_ALERT_WINDOW" },
    @{ Name = 'power_setting_error'; Exit = 1; Count = 8; Failure = "shell`tsettings`t--user`t0`tput`tsystem" },
    @{ Name = 'launch_error'; Exit = 1; Count = 12; Failure = "shell`tam`tstart" }
)

foreach ($case in $cases) {
    $caseRoot = Join-Path $checkRoot $case.Name
    $variantDirectory = Join-Path $caseRoot 'repo\variants\x200-pro-originos6'
    New-Item -ItemType Directory -Path $variantDirectory -Force | Out-Null
    $scriptPath = Join-Path $variantDirectory 'Install_Gemini_X200_Pro_OriginOS6.bat'
    Copy-Item -LiteralPath $Installer -Destination $scriptPath
    $apkPath = Join-Path $variantDirectory 'Gemini_X200_Pro_OriginOS6_v3.1.apk'
    if (-not $case.MissingApk) {
        [System.IO.File]::WriteAllText($apkPath, 'APK placeholder for isolated checks')
    }
    if (-not $case.MissingAdb) {
        $adbDirectory = Join-Path $caseRoot 'repo\installer\platform-tools'
        if ($case.Portable) { $adbDirectory = Join-Path $variantDirectory 'platform-tools' }
        New-Item -ItemType Directory -Path $adbDirectory -Force | Out-Null
        Copy-Item -LiteralPath $mockAdb -Destination (Join-Path $adbDirectory 'adb.exe')
    }

    $logPath = Join-Path $caseRoot 'adb.log'
    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = Join-Path $env:SystemRoot 'System32\cmd.exe'
    $startInfo.Arguments = '/d /q /c ""' + $scriptPath + '""'
    $startInfo.WorkingDirectory = $variantDirectory
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardInput = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.StandardOutputEncoding = New-Object System.Text.UTF8Encoding($false)
    $startInfo.StandardErrorEncoding = New-Object System.Text.UTF8Encoding($false)
    $startInfo.EnvironmentVariables['PATH'] = $mockDirectory + ';' + $env:PATH
    $startInfo.EnvironmentVariables['GEMINI_CHECK_LOG'] = $logPath
    $startInfo.EnvironmentVariables['GEMINI_CHECK_FAILURE'] = $case.Failure
    $startInfo.EnvironmentVariables['GEMINI_CHECK_CHOICE'] = $case.Choice
    $startInfo.EnvironmentVariables['GEMINI_CHECK_RECOVERY'] = $case.Recovery
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $startInfo
    $process.Start() | Out-Null
    $outputTask = $process.StandardOutput.ReadToEndAsync()
    $errorTask = $process.StandardError.ReadToEndAsync()
    $process.StandardInput.WriteLine()
    $process.StandardInput.Close()
    if (-not $process.WaitForExit(10000)) {
        $process.Kill()
        throw "Установщик завис в сценарии $($case.Name)"
    }
    $output = $outputTask.Result + $errorTask.Result
    [System.IO.File]::WriteAllText((Join-Path $caseRoot 'output.log'), $output)
    $commands = @()
    if (Test-Path -LiteralPath $logPath) { $commands = @(Get-Content -LiteralPath $logPath -Encoding UTF8) }
    if ($process.ExitCode -ne $case.Exit -or $commands.Count -ne $case.Count) {
        throw "Ошибка сценария $($case.Name): код $($process.ExitCode), команд $($commands.Count); журнал: $caseRoot"
    }
    $installCommands = @($commands | Where-Object { $_.StartsWith("install`t") })
    if ($installCommands.Count -gt 1 -or ($installCommands.Count -eq 1 -and $installCommands[0] -ne ("install`t--user`t0`t-r`t" + $apkPath))) {
        throw "Неверный APK или нарушена обработка пробелов в пути: $caseRoot"
    }
    if (@($commands | Where-Object { $_ -match '^uninstall\t|\t-d\t|Gemini_by_kaiser_v3\.0\.apk' }).Count -ne 0) {
        throw "Установщик пытается удалить приложение или использовать исходный APK: $caseRoot"
    }
    if ($case.Exit -ne 0 -and $output.Contains('APK X200 PRO / ORIGINOS 6 УСТАНОВЛЕН')) {
        throw "Установщик сообщил об успехе после ошибки: $caseRoot"
    }
    Write-Output "OK: $($case.Name)"
    $process.Dispose()
}

Write-Output "Проверено сценариев: $($cases.Count)"
Write-Output "Журналы и изолированные копии: $checkRoot"

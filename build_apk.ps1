$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$buildTools = "C:\Users\kaiser\AppData\Local\Android\Sdk\build-tools\35.0.0"
$androidJar = "C:\Users\kaiser\AppData\Local\Android\Sdk\platforms\android-34\android.jar"
$keystore = "$env:USERPROFILE\.android\debug.keystore"
$adb = "c:\vivo\adb_vivo.cmd"

$outDir = "C:\vivo\gemini-origin-os\build_temp"
if (Test-Path $outDir) { Remove-Item -Recurse -Force $outDir }
New-Item -ItemType Directory -Force -Path "$outDir\classes" | Out-Null
New-Item -ItemType Directory -Force -Path "$outDir\res" | Out-Null

Write-Host "1. AAPT2 compile..."
& "$buildTools\aapt2.exe" compile --dir "C:\vivo\gemini-origin-os\app\res" -o "$outDir\res.zip"

Write-Host "2. AAPT2 link..."
& "$buildTools\aapt2.exe" link -o "$outDir\unsigned.apk" -I $androidJar "$outDir\res.zip" `
    --manifest "C:\vivo\gemini-origin-os\app\AndroidManifest.xml" `
    --java "$outDir\gen"

Write-Host "3. Javac..."
$javaSources = @(Get-ChildItem -Path "C:\vivo\gemini-origin-os\app\src", "$outDir\gen" -Recurse -Filter "*.java" | Select-Object -ExpandProperty FullName)
javac.exe -source 11 -target 11 -encoding UTF-8 -cp $androidJar -d "$outDir\classes" $javaSources

Write-Host "4. D8..."
$classFiles = @(Get-ChildItem -Path "$outDir\classes" -Recurse -Filter "*.class" | Select-Object -ExpandProperty FullName)
& "$buildTools\d8.bat" --min-api 30 --lib $androidJar --output "$outDir" $classFiles

Write-Host "5. Packaging..."
jar.exe uf "$outDir\unsigned.apk" -C "$outDir" classes.dex
& "$buildTools\zipalign.exe" -f 4 "$outDir\unsigned.apk" "$outDir\aligned.apk"

$apkRelease = "C:\vivo\gemini-origin-os\releases\Gemini_by_kaiser_v3.2.apk"
& "$buildTools\apksigner.bat" sign --ks $keystore --ks-pass pass:android --key-pass pass:android --out $apkRelease "$outDir\aligned.apk"

Copy-Item $apkRelease "C:\vivo\gemini-origin-os\installer\Gemini_by_kaiser_v3.2.apk" -Force
Copy-Item $apkRelease "C:\vivo\Gemini_by_kaiser_v3.2_4PDA.apk" -Force
Remove-Item -Recurse -Force $outDir

Write-Host "6. Installing onto phone via WiFi ADB..."
& $adb install -r $apkRelease
Write-Host "BUILD AND INSTALL COMPLETED SUCCESSFULLY!"

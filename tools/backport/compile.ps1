# Compiles the 26.1.2 port and sorts its errors (tools/backport/errors.py) into build/port-errors.txt.
#   powershell -File tools\backport\compile.ps1
$ErrorActionPreference = "Continue"
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $root
$env:JAVA_HOME = "$env:USERPROFILE\curseforge\minecraft\Install\runtime\java-runtime-epsilon\windows-x64\java-runtime-epsilon"
# Run it with PowerShell 7 (pwsh): Windows PowerShell 5.1 writes these logs as UTF-16 and wraps javac's lines.
.\gradlew.bat compileJava --console=plain -q *> build\port-compile.txt
"exit $LASTEXITCODE"
& "$root\..\Explosive Mod\tools\.venv\Scripts\python.exe" tools\backport\errors.py build\port-compile.txt 80 > build\port-errors.txt
Get-Content build\port-errors.txt -TotalCount 90

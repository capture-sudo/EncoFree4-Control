param([string]$Jdk=$env:JAVA_HOME)
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$classes=Join-Path $root 'build/session-test-classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$sources=@(Get-ChildItem $PSScriptRoot -Recurse -Filter '*.java' | ForEach-Object FullName)
$sources+=Get-ChildItem (Join-Path $root 'tools/controller-tests/android') -Recurse -Filter '*.java' | Where-Object { $_.Name -notin @('Context.java','Handler.java') } | ForEach-Object FullName
foreach($name in @('EncoController','EncoProtocol','PanelState','WidgetSnapshot','EncoSession')){$sources+=Join-Path $root "app/src/main/java/io/codex/encofree4/$name.java"}
& "$Jdk/bin/javac.exe" -encoding UTF-8 --release 17 -d $classes @sources
if($LASTEXITCODE -ne 0){throw 'Shared session tests failed to compile'}
& "$Jdk/bin/java.exe" -cp $classes io.codex.encofree4.SharedSessionTest
if($LASTEXITCODE -ne 0){throw 'Shared session tests failed'}

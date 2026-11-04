param([string]$JavaHome=$env:JAVA_HOME)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$classes=Join-Path $root 'build/widget-test-classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$base=Join-Path $root 'app/src/main/java/io/codex/encofree4'
& "$JavaHome/bin/javac.exe" -encoding UTF-8 --release 17 -d $classes "$base/EncoProtocol.java" "$base/PanelState.java" "$base/WidgetGeometry.java" "$base/WidgetSnapshot.java" "$base/WidgetViewport.java" "$PSScriptRoot/WidgetStateTest.java" "$PSScriptRoot/WidgetViewportTest.java"
if($LASTEXITCODE -ne 0){throw 'Widget tests failed to compile'}
& "$JavaHome/bin/java.exe" -cp $classes io.codex.encofree4.WidgetStateTest
if($LASTEXITCODE -ne 0){throw 'Widget state/layout tests failed'}
& "$JavaHome/bin/java.exe" -cp $classes io.codex.encofree4.WidgetViewportTest
if($LASTEXITCODE -ne 0){throw 'Widget aspect/target mapping tests failed'}

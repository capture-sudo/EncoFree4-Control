param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$classes = Join-Path $projectRoot 'build/interaction-test-classes'
New-Item -ItemType Directory -Path $classes -Force | Out-Null
$compiler = Join-Path $JavaHome 'bin/javac.exe'
$runtime = Join-Path $JavaHome 'bin/java.exe'
& $compiler -encoding UTF-8 --release 17 -d $classes `
    (Join-Path $projectRoot 'app/src/main/java/io/codex/encofree4/PanelSwipeGesture.java') `
    (Join-Path $projectRoot 'app/src/main/java/io/codex/encofree4/VolumeSyncPolicy.java') `
    (Join-Path $PSScriptRoot 'PanelInteractionTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Panel interaction test compilation failed.' }
& $runtime -cp $classes io.codex.encofree4.PanelInteractionTest
if ($LASTEXITCODE -ne 0) { throw 'Panel interaction tests failed.' }

param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$classes = Join-Path $projectRoot 'build/protocol-test-classes'
New-Item -ItemType Directory -Path $classes -Force | Out-Null
$compiler = if ($JavaHome) { Join-Path $JavaHome 'bin/javac.exe' } else { 'javac' }
$runtime = if ($JavaHome) { Join-Path $JavaHome 'bin/java.exe' } else { 'java' }
& $compiler -encoding UTF-8 --release 17 -d $classes `
    (Join-Path $projectRoot 'app/src/main/java/io/codex/encofree4/EncoProtocol.java') `
    (Join-Path $PSScriptRoot 'java-tests/EncoProtocolTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Protocol test compilation failed.' }
& $runtime -cp $classes io.codex.encofree4.EncoProtocolTest
if ($LASTEXITCODE -ne 0) { throw 'Production protocol tests failed.' }

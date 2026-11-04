param([string]$Jdk = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$testClasses = Join-Path $projectRoot 'build/controller-test-classes'
New-Item -ItemType Directory -Path $testClasses -Force | Out-Null
$javaSources = @(Get-ChildItem -LiteralPath $PSScriptRoot -Recurse -Filter '*.java' | ForEach-Object FullName)
$javaSources += Join-Path $projectRoot 'app/src/main/java/io/codex/encofree4/EncoController.java'
$javaSources += Join-Path $projectRoot 'app/src/main/java/io/codex/encofree4/EncoProtocol.java'
& (Join-Path $Jdk 'bin/javac.exe') -encoding UTF-8 -d $testClasses @javaSources
if ($LASTEXITCODE -ne 0) { throw 'Controller tests did not compile' }
& (Join-Path $Jdk 'bin/java.exe') -cp $testClasses io.codex.encofree4.ControllerLifecycleTest
if ($LASTEXITCODE -ne 0) { throw 'Controller tests failed' }

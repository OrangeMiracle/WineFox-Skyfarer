param([string]$Stage = 'build', [string[]]$Tasks = @('build'))
$ErrorActionPreference = 'Stop'
if (Test-Path 'C:\Program Files\Java\jdk-21.0.10') {
    $env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.10'
}
# Windows packaged-process AF_UNIX incompatibility: use the JDK's built-in TCP fallback.
$env:JAVA_TOOL_OPTIONS = '-Djdk.net.unixdomain.tmpdir=F:/wfs-nonexistent-socket-dir'
& "$PSScriptRoot\..\gradlew.bat" @Tasks --console=plain *> "$PSScriptRoot\$Stage.log"
$result = $LASTEXITCODE
Get-Content "$PSScriptRoot\$Stage.log" -Tail 65
exit $result

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$deps = Join-Path $root ".deps"
$build = Join-Path $root "build"
$classes = Join-Path $build "classes"
$libs = Join-Path $build "libs"
$sourcesFile = Join-Path $build "sources.txt"
$apiVersion = "1.21.4-R0.1-SNAPSHOT"
$metadataUrl = "https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/$apiVersion/maven-metadata.xml"
$apiJar = Join-Path $deps "paper-api-$apiVersion.jar"

function Get-MavenJar {
    param(
        [string]$Group,
        [string]$Artifact,
        [string]$Version
    )
    $groupPath = $Group.Replace('.', '/')
    $fileName = "$Artifact-$Version.jar"
    $target = Join-Path $deps $fileName
    if (-not (Test-Path $target)) {
        $url = "https://repo.papermc.io/repository/maven-public/$groupPath/$Artifact/$Version/$fileName"
        Write-Host "Downloading $Group`:$Artifact`:$Version..."
        Invoke-WebRequest -Uri $url -OutFile $target -UseBasicParsing
    }
    return $target
}

New-Item -ItemType Directory -Force -Path $deps, $classes, $libs | Out-Null

if (-not (Test-Path $apiJar)) {
    Write-Host "Downloading Paper API $apiVersion..."
    [xml]$metadata = (Invoke-WebRequest -Uri $metadataUrl -UseBasicParsing).Content
    $snapshot = $metadata.metadata.versioning.snapshotVersions.snapshotVersion |
        Where-Object { $_.extension -eq "jar" -and -not $_.classifier } |
        Select-Object -First 1
    if (-not $snapshot) {
        throw "Unable to find Paper API jar snapshot in metadata."
    }
    $jarName = "paper-api-$($snapshot.value).jar"
    $jarUrl = "https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/$apiVersion/$jarName"
    Invoke-WebRequest -Uri $jarUrl -OutFile $apiJar -UseBasicParsing
}

$compileDependencies = @(
    (Get-MavenJar "org.jetbrains" "annotations" "24.1.0"),
    (Get-MavenJar "org.jspecify" "jspecify" "1.0.0"),
    (Get-MavenJar "org.checkerframework" "checker-qual" "3.33.0"),
    (Get-MavenJar "com.google.guava" "guava" "33.3.1-jre"),
    (Get-MavenJar "com.google.code.gson" "gson" "2.11.0"),
    (Get-MavenJar "org.yaml" "snakeyaml" "2.2"),
    (Get-MavenJar "org.joml" "joml" "1.10.8"),
    (Get-MavenJar "com.googlecode.json-simple" "json-simple" "1.1.1"),
    (Get-MavenJar "it.unimi.dsi" "fastutil" "8.5.15"),
    (Get-MavenJar "org.apache.logging.log4j" "log4j-api" "2.17.1"),
    (Get-MavenJar "org.slf4j" "slf4j-api" "2.0.9"),
    (Get-MavenJar "com.mojang" "brigadier" "1.3.10"),
    (Get-MavenJar "net.md-5" "bungeecord-chat" "1.20-R0.2-deprecated+build.19"),
    (Get-MavenJar "org.apache.maven" "maven-resolver-provider" "3.9.6"),
    (Get-MavenJar "net.kyori" "examination-api" "1.3.0"),
    (Get-MavenJar "net.kyori" "adventure-api" "4.20.0"),
    (Get-MavenJar "net.kyori" "adventure-key" "4.20.0"),
    (Get-MavenJar "net.kyori" "adventure-text-minimessage" "4.20.0"),
    (Get-MavenJar "net.kyori" "adventure-text-serializer-gson" "4.20.0"),
    (Get-MavenJar "net.kyori" "adventure-text-serializer-json" "4.20.0"),
    (Get-MavenJar "net.kyori" "adventure-text-serializer-legacy" "4.20.0"),
    (Get-MavenJar "net.kyori" "adventure-text-serializer-plain" "4.20.0"),
    (Get-MavenJar "net.kyori" "adventure-text-logger-slf4j" "4.20.0")
)

if (Test-Path $classes) {
    Remove-Item -Recurse -Force $classes
}
New-Item -ItemType Directory -Force -Path $classes | Out-Null

$sources = Get-ChildItem -Path (Join-Path $root "src/main/java") -Recurse -Filter *.java |
    ForEach-Object { $_.FullName }
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllLines($sourcesFile, $sources, $utf8NoBom)

$classpath = @($apiJar) + $compileDependencies -join [IO.Path]::PathSeparator
javac --release 21 -encoding UTF-8 -cp $classpath -d $classes "@$sourcesFile"
if ($LASTEXITCODE -ne 0) {
    throw "javac failed with exit code $LASTEXITCODE"
}

$resources = Join-Path $root "src/main/resources"
if (Test-Path $resources) {
    Copy-Item -Path (Join-Path $resources "*") -Destination $classes -Recurse -Force
}

$jarPath = Join-Path $libs "Vote-1.0.1.jar"
if (Test-Path $jarPath) {
    Remove-Item -Force $jarPath
}
jar cf $jarPath -C $classes .
if ($LASTEXITCODE -ne 0) {
    throw "jar failed with exit code $LASTEXITCODE"
}
Write-Host "Built $jarPath"

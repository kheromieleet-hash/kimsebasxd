param(
    [string]$Version = "1.0.0",
    [int]$VersionCode = 1,
    [string]$Changelog = "Initial release",
    [string]$Repo = "kheromieallah/sestia",
    [switch]$Upload
)

$ErrorActionPreference = "Stop"

$repoRoot = $PSScriptRoot
$javac = "C:\Users\PC\AppData\Roaming\PrismLauncher\java\java-runtime-epsilon\bin\javac.exe"
$jarExe = "C:\Users\PC\AppData\Roaming\PrismLauncher\java\java-runtime-epsilon\bin\jar.exe"
$scratchDir = "C:\Users\PC\.gemini\antigravity\brain\57534eac-943f-403b-bc3c-8bec60cca594\scratch"

$buildTmp = Join-Path $repoRoot "build_tmp"
$classesDir = Join-Path $buildTmp "classes"
$coreStage = Join-Path $buildTmp "core"
$loaderStage = Join-Path $buildTmp "loader"
$distDir = Join-Path $repoRoot "dist"

foreach ($dir in @($buildTmp, $distDir)) {
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir | Out-Null }
}
foreach ($dir in @($classesDir, $coreStage, $loaderStage)) {
    if (Test-Path $dir) { Remove-Item $dir -Recurse -Force }
    New-Item -ItemType Directory -Path $dir | Out-Null
}

# Build unambiguous classpath prioritizing fabric-loader 0.20.1 + precompiled root dependencies (dlovin/)
$loomJars = @(Get-ChildItem -Path "C:\Users\PC\.gradle\caches\fabric-loom\26.3" -Recurse -Filter "*.jar" | Select-Object -ExpandProperty FullName)
$fapiJars = @(Get-ChildItem -Path "$scratchDir\fapi" -Recurse -Filter "*.jar" | Select-Object -ExpandProperty FullName)
$preferredLoader = "C:\Users\PC\.gradle\caches\modules-2\files-2.1\net.fabricmc\fabric-loader\0.20.1\7c7f9831e9f27b303d5d08decb5f4258a9f03296\fabric-loader-0.20.1.jar"
$libJars = @(Get-ChildItem -Path "C:\Users\PC\.gradle\caches\modules-2" -Recurse -Filter "*.jar" | Where-Object {
    $_.Name -notlike "*-sources.jar" -and
    $_.Name -notlike "*-javadoc.jar" -and
    $_.FullName -notlike "*\net.fabricmc\fabric-loader\*" -and
    ($_.FullName -notlike "*net.fabricmc.fabric-api*" -or $_.Name -like "*26.3*") -and
    $_.Length -gt 1024
} | Select-Object -ExpandProperty FullName)

$allCp = @($repoRoot, $preferredLoader, "C:\Users\PC\.gradle\caches\fabric-loom\26.3\minecraft-merged.jar") + $fapiJars + $loomJars + $libJars
$cpString = (($allCp | Select-Object -Unique) -join ";").Replace('\', '/')
$cpFile = Join-Path $buildTmp "javac_args.txt"
[IO.File]::WriteAllText($cpFile, "-cp `"$cpString`"", [Text.Encoding]::ASCII)

$sources = @(Get-ChildItem -Path (Join-Path $repoRoot "src\main\java") -Recurse -Filter "*.java" | Select-Object -ExpandProperty FullName)
Write-Host "Compiling $($sources.Count) Java sources for SCT Client v$Version (code=$VersionCode)..."
& $javac "@$cpFile" -encoding UTF-8 -d $classesDir $sources
if ($LASTEXITCODE -ne 0) {
    throw "Compilation failed with exit code $LASTEXITCODE"
}

# Stage Core JAR contents
$baseJar = "C:\Users\PC\Desktop\ClientMod-1.0.0.jar"
if (Test-Path $baseJar) {
    Push-Location $coreStage
    & $jarExe xf $baseJar
    Pop-Location
} else {
    foreach ($sub in @("dlovin", "assets", "META-INF")) {
        $srcSub = Join-Path $repoRoot $sub
        if (Test-Path $srcSub) { Copy-Item $srcSub $coreStage -Recurse -Force }
    }
}

# Strip loader-owned or unwanted legacy entries from Core stage
foreach ($prune in @("com\client", "fabric.mod.json", "META-INF\jars", "logo.png")) {
    $p = Join-Path $coreStage $prune
    if (Test-Path $p) { Remove-Item $p -Recurse -Force }
}
foreach ($oldTex in @(
    "assets\inventoryhud\video",
    "assets\inventoryhud\textures\gui\ngo_title_bg.*",
    "assets\inventoryhud\textures\gui\custom_title_bg.*",
    "assets\inventoryhud\textures\gui\custom_title_hover.*",
    "assets\inventoryhud\textures\gui\vortex",
    "assets\sestia"
)) {
    $p = Join-Path $coreStage $oldTex
    if (Test-Path $p) { Remove-Item $p -Recurse -Force }
}

# Overlay custom textures and assets cleanly
$guiTexDir = Join-Path $coreStage "assets\inventoryhud\textures\gui"
if (-not (Test-Path $guiTexDir)) { New-Item -ItemType Directory -Path $guiTexDir -Force | Out-Null }
if (Test-Path "$scratchDir\title_bg.png") {
    Copy-Item "$scratchDir\title_bg.png" (Join-Path $guiTexDir "title_bg.png") -Force
}

$editionSrc = Join-Path $repoRoot "src\main\resources\assets\inventoryhud\textures\gui\title\edition.png"
if (Test-Path $editionSrc) {
    foreach ($tDir in @(
        (Join-Path $coreStage "assets\inventoryhud\textures\gui\title"),
        (Join-Path $coreStage "assets\minecraft\textures\gui\title")
    )) {
        if (-not (Test-Path $tDir)) { New-Item -ItemType Directory -Path $tDir -Force | Out-Null }
        Copy-Item $editionSrc (Join-Path $tDir "edition.png") -Force
    }
}

$destroyGlob = "$scratchDir\load-breaking-extracted\assets\minecraft\textures\block\destroy_stage_*.png"
if (Test-Path $destroyGlob) {
    $blockDir = Join-Path $coreStage "assets\minecraft\textures\block"
    if (-not (Test-Path $blockDir)) { New-Item -ItemType Directory -Path $blockDir -Force | Out-Null }
    Copy-Item $destroyGlob $blockDir -Force
}

$crosshairSrc = "$scratchDir\cat-crosshair-extracted\assets\minecraft\textures\gui\sprites\hud\crosshair.png"
if (Test-Path $crosshairSrc) {
    $hudDir = Join-Path $coreStage "assets\minecraft\textures\gui\sprites\hud"
    if (-not (Test-Path $hudDir)) { New-Item -ItemType Directory -Path $hudDir -Force | Out-Null }
    Copy-Item $crosshairSrc (Join-Path $hudDir "crosshair.png") -Force
}

$iasLangSrc = Join-Path $repoRoot "src\main\resources\assets\ias"
if (Test-Path $iasLangSrc) {
    $iasStage = Join-Path $coreStage "assets\ias"
    if (-not (Test-Path $iasStage)) { New-Item -ItemType Directory -Path $iasStage -Force | Out-Null }
    Copy-Item "$iasLangSrc\*" $iasStage -Recurse -Force
}

$customFontSrc = Join-Path $repoRoot "src\main\resources\assets\inventoryhud\font"
if (Test-Path $customFontSrc) {
    $fontStage = Join-Path $coreStage "assets\inventoryhud\font"
    if (-not (Test-Path $fontStage)) { New-Item -ItemType Directory -Path $fontStage -Force | Out-Null }
    Copy-Item "$customFontSrc\*" $fontStage -Recurse -Force
}

# Copy fresh compiled com/client/mod & dlovin classes + mixin configs + pack.mcmeta
$modClassesDest = Join-Path $coreStage "com\client\mod"
New-Item -ItemType Directory -Path $modClassesDest -Force | Out-Null
Copy-Item (Join-Path $classesDir "com\client\mod\*") $modClassesDest -Recurse -Force
if (Test-Path (Join-Path $classesDir "dlovin")) {
    $dlovinDest = Join-Path $coreStage "dlovin"
    if (-not (Test-Path $dlovinDest)) { New-Item -ItemType Directory -Path $dlovinDest -Force | Out-Null }
    Copy-Item (Join-Path $classesDir "dlovin\*") $dlovinDest -Recurse -Force
}
Copy-Item (Join-Path $repoRoot "customclient.mixins.json") (Join-Path $coreStage "customclient.mixins.json") -Force
Copy-Item (Join-Path $repoRoot "inventoryhud.mixins.json") (Join-Path $coreStage "inventoryhud.mixins.json") -Force
Copy-Item (Join-Path $repoRoot "pack.mcmeta") (Join-Path $coreStage "pack.mcmeta") -Force

$metaContent = "version=$Version`nversionCode=$VersionCode`n"
[IO.File]::WriteAllText((Join-Path $coreStage "sct-core-meta.properties"), $metaContent, (New-Object System.Text.UTF8Encoding($false)))

$coreJarOut = Join-Path $distDir "sestia-core.jar"
if (Test-Path $coreJarOut) { Remove-Item $coreJarOut -Force }
Push-Location $coreStage
& $jarExe cf $coreJarOut *
Pop-Location
if ($LASTEXITCODE -ne 0) { throw "Failed to pack sestia-core.jar" }

$coreHash = (Get-FileHash -Path $coreJarOut -Algorithm SHA256).Hash.ToLowerInvariant()
$coreSize = (Get-Item $coreJarOut).Length

# Generate version.json (UTF-8 without BOM)
$manifestObj = [ordered]@{
    versionCode   = $VersionCode
    version       = $Version
    changelog     = $Changelog
    downloadUrl   = "https://github.com/$Repo/releases/latest/download/sestia-core.jar"
    sha256        = $coreHash
    sizeBytes     = $coreSize
    minLoaderCode = 1
    loaderUrl     = "https://github.com/$Repo/releases/latest"
}
$manifestJson = $manifestObj | ConvertTo-Json -Depth 4
$manifestOut = Join-Path $distDir "version.json"
[IO.File]::WriteAllText($manifestOut, $manifestJson, (New-Object System.Text.UTF8Encoding($false)))

# Stage Bootloader JAR (ONLY com/client/loader + fabric.mod.json + IAS JiJ + sestia/fallback-core.jar)
$loaderClassesDest = Join-Path $loaderStage "com\client\loader"
New-Item -ItemType Directory -Path $loaderClassesDest -Force | Out-Null
Copy-Item (Join-Path $classesDir "com\client\loader\*") $loaderClassesDest -Recurse -Force
Copy-Item (Join-Path $repoRoot "fabric.mod.json") (Join-Path $loaderStage "fabric.mod.json") -Force
Copy-Item (Join-Path $repoRoot "pack.mcmeta") (Join-Path $loaderStage "pack.mcmeta") -Force
if (Test-Path (Join-Path $repoRoot "logo.png")) {
    Copy-Item (Join-Path $repoRoot "logo.png") (Join-Path $loaderStage "logo.png") -Force
}

$loaderJarsDir = Join-Path $loaderStage "META-INF\jars"
New-Item -ItemType Directory -Path $loaderJarsDir -Force | Out-Null
$iasJarSrc = "$scratchDir\IAS-9.0.8+26.3-fabric.jar"
if (-not (Test-Path $iasJarSrc)) { $iasJarSrc = Join-Path $repoRoot "META-INF\jars\IAS-9.0.8+26.3-fabric.jar" }
Copy-Item $iasJarSrc (Join-Path $loaderJarsDir "IAS-9.0.8+26.3-fabric.jar") -Force

$fallbackDir = Join-Path $loaderStage "sestia"
New-Item -ItemType Directory -Path $fallbackDir -Force | Out-Null
Copy-Item $coreJarOut (Join-Path $fallbackDir "fallback-core.jar") -Force

$loaderJarOut = Join-Path $distDir "sestia.jar"
if (Test-Path $loaderJarOut) { Remove-Item $loaderJarOut -Force }
Push-Location $loaderStage
& $jarExe cf $loaderJarOut *
Pop-Location
if ($LASTEXITCODE -ne 0) { throw "Failed to pack sestia.jar" }

# Deploy clean bootloader JAR locally (handle active Minecraft lock cleanly)
$prismMods = "C:\Users\PC\AppData\Roaming\PrismLauncher\instances\26.3\minecraft\mods"
if (Test-Path $prismMods) {
    try {
        foreach ($oldJar in @("ClientMod-1.0.0.jar", "SCTClient.jar")) {
            $oldPath = Join-Path $prismMods $oldJar
            if (Test-Path $oldPath) { Remove-Item $oldPath -Force }
        }
        Copy-Item $loaderJarOut (Join-Path $prismMods "sestia.jar") -Force
        Write-Host "Updated PrismLauncher mods/sestia.jar"
    } catch {
        Write-Warning "Could not update PrismLauncher mods/ while Minecraft is running. Close Minecraft, delete ClientMod-1.0.0.jar / SCTClient.jar, and copy dist\sestia.jar to $prismMods"
    }
}
Copy-Item $loaderJarOut "C:\Users\PC\Desktop\sestia.jar" -Force
Copy-Item $loaderJarOut "C:\Users\PC\Downloads\sestia.jar" -Force
foreach ($oldCopy in @("C:\Users\PC\Desktop\SCTClient.jar", "C:\Users\PC\Downloads\SCTClient.jar", (Join-Path $distDir "SCTClient.jar"), (Join-Path $distDir "SCTClient-Core.jar"))) {
    if (Test-Path $oldCopy) { Remove-Item $oldCopy -Force }
}

# Clean temporary staging directories (keep dist/)
Remove-Item $buildTmp -Recurse -Force

Write-Host ""
Write-Host "Build complete!"
Write-Host "  Friend file (1-time mods/): $loaderJarOut"
Write-Host "  Release Core JAR:          $coreJarOut (SHA-256: $coreHash)"
Write-Host "  Release Manifest:          $manifestOut"

if ($Upload) {
    $tag = "v$Version"
    $ghCmd = Get-Command gh -ErrorAction SilentlyContinue
    if ($ghCmd) {
        Write-Host "Uploading release $tag to $Repo via GitHub CLI..."
        $exists = $false
        try { & gh release view $tag --repo $Repo *> $null; if ($LASTEXITCODE -eq 0) { $exists = $true } } catch {}
        if ($exists) {
            & gh release upload $tag $coreJarOut $manifestOut $loaderJarOut --repo $Repo --clobber
        } else {
            & gh release create $tag $coreJarOut $manifestOut $loaderJarOut --repo $Repo --title "SCT Client $tag" --notes $Changelog
        }
        if ($LASTEXITCODE -ne 0) { throw "gh release upload failed!" }
        Write-Host "Uploaded $tag to https://github.com/$Repo/releases/tag/$tag"
    } elseif ($env:GITHUB_TOKEN) {
        Write-Host "Uploading release $tag to $Repo via GitHub REST API..."
        $headers = @{
            Authorization = "Bearer $($env:GITHUB_TOKEN)"
            Accept        = "application/vnd.github+json"
            "User-Agent"  = "SCTClient-Publisher"
        }
        $relBody = @{ tag_name = $tag; name = "SCT Client $tag"; body = $Changelog; draft = $false; prerelease = $false } | ConvertTo-Json
        try {
            $rel = Invoke-RestMethod -Uri "https://api.github.com/repos/$Repo/releases/tags/$tag" -Headers $headers -Method Get
        } catch {
            $rel = Invoke-RestMethod -Uri "https://api.github.com/repos/$Repo/releases" -Headers $headers -Method Post -Body $relBody -ContentType "application/json"
        }
        $uploadBase = $rel.upload_url -replace '\{\?name,label\}$', ''
        foreach ($assetPath in @($coreJarOut, $manifestOut, $loaderJarOut)) {
            $name = [IO.Path]::GetFileName($assetPath)
            if ($rel.assets) {
                $existing = $rel.assets | Where-Object { $_.name -eq $name }
                foreach ($oldAsset in $existing) {
                    Invoke-RestMethod -Uri "https://api.github.com/repos/$Repo/releases/assets/$($oldAsset.id)" -Headers $headers -Method Delete | Out-Null
                }
            }
            $contentType = if ($name.EndsWith(".json")) { "application/json" } else { "application/java-archive" }
            $bytes = [IO.File]::ReadAllBytes($assetPath)
            Invoke-RestMethod -Uri "$uploadBase`?name=$name" -Headers $headers -Method Post -Body $bytes -ContentType $contentType | Out-Null
            Write-Host "  Uploaded $name"
        }
        Write-Host "Published $tag to https://github.com/$Repo/releases/tag/$tag"
    } else {
        throw "Upload requested, but neither authenticated 'gh' CLI nor `$env:GITHUB_TOKEN was found."
    }
}

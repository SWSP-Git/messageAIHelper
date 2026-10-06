# build_native.ps1
#
# 离线构建本地模型插件所需的原生库（arm64-v8a）：
#   1) libMNN.so        —— 开启 MNN_BUILD_LLM 的 MNN 引擎
#   2) liblocalllm.so   —— 本插件的 JNI 桥（llm_jni.cpp）
#   3) libc++_shared.so —— 两者共用的 C++ 运行时
# 三者最终拷入 localllm/src/main/jniLibs/arm64-v8a/。
#
# 用法（在 messageAIHelper 目录下）：
#   powershell -ExecutionPolicy Bypass -File .\localllm\build_native.ps1
#
# 前置：已安装 Android NDK；系统有 cmake(>=3.22) 与 ninja。

param(
    [string]$NdkRoot = "$env:LOCALAPPDATA\Android\Sdk\ndk\27.2.12479018",
    [string]$MnnRoot = "C:\Users\mfxq2\Desktop\WORK\MNN",
    [string]$Cmake = "cmake",
    [string]$Ninja = "ninja",
    [string]$Abi = "arm64-v8a",
    [string]$ApiLevel = "26",
    [switch]$Clean
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path          # ...\localllm
$jniLibs   = Join-Path $scriptDir "src\main\jniLibs\$Abi"
$cppDir    = Join-Path $scriptDir "src\main\cpp"
$mnnBuild  = Join-Path $MnnRoot "build_android_$Abi"
$jniBuild  = Join-Path $scriptDir "build_native_$Abi"

$toolchain = Join-Path $NdkRoot "build\cmake\android.toolchain.cmake"
if (-not (Test-Path $toolchain)) { throw "找不到 NDK toolchain: $toolchain" }
if (-not (Test-Path $MnnRoot))   { throw "找不到 MNN 源码: $MnnRoot" }

if ($Clean) {
    foreach ($d in @($mnnBuild, $jniBuild)) {
        if (Test-Path $d) { Remove-Item $d -Recurse -Force }
    }
}

function Invoke-Cmake([string[]]$CmakeArgs) {
    Write-Host ">> cmake $($CmakeArgs -join ' ')" -ForegroundColor Cyan
    & $Cmake @CmakeArgs
    if ($LASTEXITCODE -ne 0) { throw "cmake 执行失败（exit $LASTEXITCODE）" }
}

# ---------------- 1. 配置并构建 MNN ----------------
Write-Host "==== [1/3] 构建 libMNN.so (含 LLM 引擎) ====" -ForegroundColor Green
Invoke-Cmake @(
    "-S", $MnnRoot, "-B", $mnnBuild, "-G", "Ninja",
    "-DCMAKE_TOOLCHAIN_FILE=$toolchain",
    "-DCMAKE_BUILD_TYPE=Release",
    "-DANDROID_ABI=$Abi",
    "-DANDROID_PLATFORM=android-$ApiLevel",
    "-DANDROID_STL=c++_shared",
    "-DMNN_BUILD_SHARED_LIBS=ON",
    "-DMNN_SEP_BUILD=OFF",
    "-DMNN_BUILD_FOR_ANDROID_COMMAND=ON",
    "-DNATIVE_LIBRARY_OUTPUT=.",
    "-DNATIVE_INCLUDE_OUTPUT=.",
    "-DMNN_BUILD_LLM=ON",
    "-DMNN_LOW_MEMORY=ON",
    "-DMNN_SUPPORT_TRANSFORMER_FUSE=ON",
    "-DMNN_BUILD_TEST=OFF",
    "-DMNN_BUILD_BENCHMARK=OFF",
    "-DMNN_BUILD_TOOLS=OFF",
    "-DMNN_BUILD_DIFFUSION=OFF",
    "-DMNN_BUILD_OPENCV=OFF",
    "-DMNN_IMGCODECS=OFF",
    "-DMNN_OPENCL=OFF",
    "-DMNN_USE_SSE=OFF",
    "-DMNN_LLM_BUILD_DEMO=OFF",
    "-DMNN_BUILD_AUDIO=OFF"
)
Invoke-Cmake @("--build", $mnnBuild, "--target", "MNN", "-j")

# MNN 在 Android 下会把产物放到子目录（如 OFF/arm64-v8a），这里动态定位 libMNN.so
$libMnnSo = Get-ChildItem $mnnBuild -Recurse -Filter "libMNN.so" -File |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $libMnnSo) { throw "未找到构建产物 libMNN.so（目录：$mnnBuild）" }
$mnnLibDir = $libMnnSo.DirectoryName
Write-Host "libMNN.so -> $($libMnnSo.FullName)" -ForegroundColor Yellow

# ---------------- 2. 构建 liblocalllm.so ----------------
Write-Host "==== [2/3] 构建 liblocalllm.so (JNI) ====" -ForegroundColor Green
Invoke-Cmake @(
    "-S", $cppDir, "-B", $jniBuild, "-G", "Ninja",
    "-DCMAKE_TOOLCHAIN_FILE=$toolchain",
    "-DCMAKE_BUILD_TYPE=Release",
    "-DANDROID_ABI=$Abi",
    "-DANDROID_PLATFORM=android-$ApiLevel",
    "-DANDROID_STL=c++_shared",
    "-DMNN_ROOT=$MnnRoot",
    "-DMNN_LIB_DIR=$mnnLibDir"
)
Invoke-Cmake @("--build", $jniBuild, "-j")

# ---------------- 3. 汇总产物 ----------------
Write-Host "==== [3/3] 拷贝产物到 jniLibs ====" -ForegroundColor Green
New-Item -ItemType Directory -Force -Path $jniLibs | Out-Null

Copy-Item $libMnnSo.FullName $jniLibs -Force
Copy-Item (Join-Path $jniBuild "liblocalllm.so") $jniLibs -Force

$llvmStl = Get-ChildItem (Join-Path $NdkRoot "toolchains\llvm\prebuilt") -Directory |
    Select-Object -First 1
$cxxShared = Join-Path $llvmStl.FullName "sysroot\usr\lib\aarch64-linux-android\libc++_shared.so"
if (Test-Path $cxxShared) { Copy-Item $cxxShared $jniLibs -Force }
else { Write-Warning "未找到 libc++_shared.so: $cxxShared" }

Write-Host "完成，产物：" -ForegroundColor Green
Get-ChildItem $jniLibs | Select-Object Name, @{n = 'MB'; e = { [math]::Round($_.Length / 1MB, 2) } }

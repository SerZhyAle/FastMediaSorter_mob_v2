#requires -Version 7.0
<#
.SYNOPSIS
    Test suite for format-kotlin-imports.ps1.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$scriptDir = $PSScriptRoot
$sorterScript = Join-Path (Split-Path $scriptDir -Parent) 'format-kotlin-imports.ps1'
$tempDir = Join-Path $scriptDir 'temp_test_workspace'

if (Test-Path $tempDir) {
    Remove-Item -Recurse -Force $tempDir
}
New-Item -ItemType Directory -Path $tempDir | Out-Null

try {
    # Test 1: Unsorted imports across 5 groups (standard, java, javax, kotlin, aliases) + duplicate
    $testFile1 = Join-Path $tempDir 'Test1.kt'
    $input1 = @"
package com.sza.test

import kotlin.math.max
import java.io.File
import androidx.annotation.NonNull
import com.google.android.material.R as MaterialR
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import java.io.File
import java.io.InputStream
import kotlin.collections.List
import android.content.Context

class Test1 {
    val x = 1
}
"@
    [System.IO.File]::WriteAllText($testFile1, $input1, [System.Text.UTF8Encoding]::new($false))
    
    # Run formatter
    & pwsh -NoProfile -File $sorterScript -FilePath $testFile1
    if ($LASTEXITCODE -ne 0) {
        throw "Test 1 failed: sorter exited with $LASTEXITCODE"
    }
    
    $output1 = Get-Content -Path $testFile1 -Raw
    
    # Expected ordering:
    # Group 1: android.content.Context, androidx.annotation.NonNull, kotlinx.coroutines.flow.Flow
    # Group 2: java.io.File, java.io.InputStream
    # Group 3: javax.inject.Inject
    # Group 4: kotlin.collections.List, kotlin.math.max
    # Group 5: com.google.android.material.R as MaterialR (alias at end)
    $expected1 = @"
package com.sza.test

import android.content.Context
import androidx.annotation.NonNull
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import kotlin.collections.List
import kotlin.math.max
import com.google.android.material.R as MaterialR

class Test1 {
    val x = 1
}
"@
    
    $normActual = $output1.Replace("`r`n", "`n").Trim()
    $normExpected = $expected1.Replace("`r`n", "`n").Trim()
    
    if ($normActual -ne $normExpected) {
        Write-Host "ACTUAL:`n$normActual" -ForegroundColor Red
        Write-Host "EXPECTED:`n$normExpected" -ForegroundColor Yellow
        throw "Test 1 output does not match expected ordinal grouped ordering!"
    }
    
    # Test 2: Check mode on clean file returns 0
    & pwsh -NoProfile -File $sorterScript -FilePath $testFile1 -Check
    if ($LASTEXITCODE -ne 0) {
        throw "Test 2 failed: check on clean file should exit 0, got $LASTEXITCODE"
    }
    
    # Test 3: Refusal on comment inside import block
    $testFileComment = Join-Path $tempDir 'TestComment.kt'
    $inputComment = @"
package com.sza.test

import android.content.Context
// Forbidden comment inside imports
import java.io.File

class TestComment
"@
    [System.IO.File]::WriteAllText($testFileComment, $inputComment, [System.Text.UTF8Encoding]::new($false))
    
    & pwsh -NoProfile -File $sorterScript -FilePath $testFileComment -Check
    if ($LASTEXITCODE -ne 1) {
        throw "Test 3 failed: file with comment inside imports must be refused with exit 1, got $LASTEXITCODE"
    }
    
    # Ensure file was not modified
    $contentAfter = Get-Content -Path $testFileComment -Raw
    if ($contentAfter -ne $inputComment) {
        throw "Test 3 failed: file with comment should not have been modified!"
    }

    # Test 4: write mode reports the refusal on its own exit code, not as a silent 0
    & pwsh -NoProfile -File $sorterScript -FilePath $testFileComment
    if ($LASTEXITCODE -ne 2) {
        throw "Test 4 failed: write mode on a refused file must exit 2, got $LASTEXITCODE"
    }
    $contentAfterWrite = Get-Content -Path $testFileComment -Raw
    if ($contentAfterWrite -ne $inputComment) {
        throw "Test 4 failed: a refused file must stay byte-identical!"
    }

    Write-Host "ALL format-kotlin-imports UNIT TESTS PASSED." -ForegroundColor Green
    exit 0
}
finally {
    if (Test-Path $tempDir) {
        Remove-Item -Recurse -Force $tempDir
    }
}

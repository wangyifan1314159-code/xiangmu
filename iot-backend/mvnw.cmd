@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    https://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@REM ----------------------------------------------------------------------------
@REM Maven Wrapper startup script for Windows
@REM ----------------------------------------------------------------------------

@echo off
setlocal enabledelayedexpansion

set MAVEN_PROJECTBASEDIR=%CD%
set MAVEN_USER_HOME=%USERPROFILE%\.m2

if not defined MAVEN_HOME (
    set "MAVEN_HOME=%MAVEN_USER_HOME%\wrapper\dists\apache-maven-3.9.9"
)

if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
    echo.
    echo ========================================================================
    echo Maven is not installed and cannot be downloaded (network restricted).
    echo Please install Maven manually:
    echo   1. Download from https://maven.apache.org/download.cgi
    echo   2. Extract to a directory
    echo   3. Add MAVEN_HOME environment variable or add to PATH
    echo ========================================================================
    echo.
    echo If Maven IS installed, add it to your PATH and try again:
    echo   set PATH=C:\path\to\apache-maven-3.x.x\bin;%%PATH%%
    echo ========================================================================
    echo.
    exit /b 1
)

"%MAVEN_HOME%\bin\mvn.cmd" %*

@echo off
chcp 65001 >nul
echo === 물류창고 스캐너 서버 배포 ===

:: 1. 레포 클론 또는 업데이트
if exist "%USERPROFILE%\scan" (
    echo [1/4] 기존 레포 업데이트...
    cd /d "%USERPROFILE%\scan"
    git pull
) else (
    echo [1/4] 레포 클론...
    git clone https://github.com/doublesilver/scan.git "%USERPROFILE%\scan"
    cd /d "%USERPROFILE%\scan"
)

cd server

:: 2. venv 생성 및 의존성 설치
if not exist "venv" (
    echo [2/4] 가상환경 생성...
    python -m venv venv
)
echo [2/4] 의존성 설치...
venv\Scripts\pip install -r requirements.txt

:: 3. 기존 서버 종료
echo [3/4] 기존 서버 종료...
taskkill /F /FI "WINDOWTITLE eq scanner-server" 2>nul

:: 4. 서버 실행
echo [4/4] 서버 시작 (포트 8000)...
start "scanner-server" venv\Scripts\python -m app.main

:: IP 확인
echo.
echo === 배포 완료 ===
echo 아래 IPv4 주소를 PDA 앱에 입력하세요:
ipconfig | findstr /c:"IPv4"
echo.
pause

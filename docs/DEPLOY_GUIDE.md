# 배포 가이드

물류창고 스캐너 API를 미니PC에 배포하고 운영하는 방법입니다.

---

## 사전 준비

### 1. 미니PC 사양

- OS: Windows 10/11
- Python: 3.11 이상
- 네트워크: Tailscale 설치 (내부망 연결)

### 2. 필수 소프트웨어 설치

#### Python 3.11+ 설치

```bash
# Windows에서 python-3.11.msi 다운로드 후 설치
# https://www.python.org/downloads/

# 설치 확인
python --version
```

#### Tailscale 설치

```bash
# https://tailscale.com/download/windows에서 다운로드 후 설치
# 설정 > 네트워크에서 Tailscale IP 확인 (예: 100.125.17.60)
```

#### NSSM (Non-Sucking Service Manager) 설치

```bash
# https://nssm.cc/download에서 nssm-2.24.zip 다운로드
# C:\nssm\로 압축 해제

# 또는 scoop 사용
scoop install nssm
```

---

## 서버 코드 배치

### 1. 저장소 복제 또는 파일 다운로드

```bash
# SSH로 Windows 미니PC에 접속
ssh lenovo@100.125.17.60

# 또는 Tailscale로 관리되는 Windows 머신에서 직접 작업
```

### 2. 서버 파일 배치

```bash
# C:\scanner\ 디렉토리 생성
mkdir C:\scanner
cd C:\scanner

# 서버 코드 배치
# /server 폴더의 모든 파일을 C:\scanner\server\로 복사
# - app/
# - requirements.txt
# - 기타 설정 파일
```

### 3. 디렉토리 구조 확인

```
C:\scanner\
├── server/
│   ├── app/
│   │   ├── api/
│   │   ├── db/
│   │   ├── models/
│   │   ├── services/
│   │   ├── config.py
│   │   └── main.py
│   ├── requirements.txt
│   ├── data/
│   └── static/
```

---

## 의존성 설치

### 1. 가상 환경 생성

```bash
cd C:\scanner\server
python -m venv venv
```

### 2. 가상 환경 활성화

```bash
# Windows PowerShell
.\venv\Scripts\Activate.ps1

# Windows CMD
venv\Scripts\activate.bat
```

### 3. pip 업그레이드

```bash
python -m pip install --upgrade pip
```

### 4. 의존성 설치

```bash
pip install -r requirements.txt
```

### 5. 설치 확인

```bash
pip list
# fastapi, uvicorn, aiosqlite 등 확인
```

---

## 환경 설정

### 1. 설정 파일 확인

`app/config.py`에서 기본 설정:

```python
# 기본값
HOST = "0.0.0.0"
PORT = 8000
DB_PATH = "data/scanner.db"
CORS_ORIGINS = ["*"]  # 내부망 전용
```

### 2. .env 파일 (선택사항)

필요시 `C:\scanner\server\.env` 파일 생성:

```
DB_PATH=data/scanner.db
PORT=8000
DEBUG=false
```

### 3. APK 파일 배치

```bash
# 클라이언트 APK를 다음 위치에 배치
C:\scanner\server\apk\app-live-debug.apk

# API 엔드포인트: GET /api/app-version
# 다운로드 URL: /apk/app-live-debug.apk
```

---

## NSSM 서비스 등록

### 1. 서비스 등록

```bash
# PowerShell (관리자 권한)
cd C:\nssm\win64

# 서비스 등록
.\nssm install ScannerAPI "C:\scanner\server\venv\Scripts\python.exe" "-m uvicorn app.main:app --host 0.0.0.0 --port 8000"

# 또는 설정으로 진행
.\nssm install ScannerAPI
# GUI에서:
# Path: C:\scanner\server\venv\Scripts\python.exe
# Arguments: -m uvicorn app.main:app --host 0.0.0.0 --port 8000
# Startup directory: C:\scanner\server
```

### 2. 서비스 시작

```bash
# PowerShell
net start ScannerAPI

# 또는 NSSM으로
.\nssm start ScannerAPI
```

### 3. 서비스 상태 확인

```bash
# 서비스 목록
Get-Service ScannerAPI

# NSSM 상태 확인
.\nssm status ScannerAPI
```

### 4. 서비스 로그 확인

```bash
# NSSM 로그 위치
C:\Users\lenovo\AppData\Roaming\NSSM\ScannerAPI\log.txt

# 또는
.\nssm get ScannerAPI AppStdout
.\nssm get ScannerAPI AppStderr
```

### 5. 서비스 중지/재시작

```bash
# 중지
net stop ScannerAPI

# 재시작
net stop ScannerAPI && net start ScannerAPI

# 또는 NSSM으로
.\nssm stop ScannerAPI
.\nssm restart ScannerAPI
```

### 6. 서비스 제거

```bash
# 서비스 제거
net stop ScannerAPI
.\nssm remove ScannerAPI confirm
```

---

## 서버 접속 확인

### 1. 로컬 접속 테스트

```bash
# 미니PC에서 직접
curl http://localhost:8000/health

# 응답: {"status": "ok"}
```

### 2. 네트워크 접속 테스트

```bash
# 다른 PC에서 (같은 Tailscale 네트워크)
curl http://100.125.17.60:8000/health

# 또는 브라우저
http://100.125.17.60:8000/docs  # Swagger UI
http://100.125.17.60:8000/redoc  # ReDoc
```

### 3. 첫 바코드 스캔 테스트

```bash
curl "http://100.125.17.60:8000/api/scan/8801234567890"

# 응답: {"sku_id": "SKU001", "product_name": "...", ...}
```

---

## APK 배포

### 1. APK 파일 준비

클라이언트 앱 빌드 후:

```bash
# Android Studio에서 APK 빌드
# app/build/outputs/apk/debug/app-debug.apk 또는 release APK

# Windows에 SCP로 전송
scp app-live-debug.apk lenovo@100.125.17.60:C:\scanner\server\apk\
```

### 2. 버전 정보 업데이트

`app/api/routes.py`의 `/api/app-version` 엔드포인트:

```python
@router.get("/api/app-version")
async def app_version():
    return {
        "versionCode": 57,        # 빌드 번호
        "versionName": "4.3.0",   # 버전 이름
        "downloadUrl": "/apk/app-live-debug.apk",
        "releaseNotes": "최신 버전",
        "forceUpdate": False       # True면 필수 업데이트
    }
```

### 3. 서버 재시작 (선택)

APK 파일만 바뀌었으면 서버 재시작 불필요. 코드 변경 시에만 재시작:

```bash
net stop ScannerAPI
net start ScannerAPI
```

---

## 서버 코드 업데이트

### 1. 신규 코드 배포

```bash
# Windows에서 SCP로 전송
scp -r server/* lenovo@100.125.17.60:C:\scanner\server\

# 또는 SSH로 git pull
ssh lenovo@100.125.17.60
cd C:\scanner\server
git pull origin main
```

### 2. 의존성 업데이트

```bash
cd C:\scanner\server

# 가상 환경 활성화
venv\Scripts\activate.bat

# 의존성 재설치
pip install -r requirements.txt
```

### 3. 캐시 정리

```bash
# Python 캐시 삭제
rmdir /s /q app\__pycache__
rmdir /s /q app\api\__pycache__
rmdir /s /q app\db\__pycache__
rmdir /s /q app\services\__pycache__
```

### 4. 서버 재시작

```bash
net stop ScannerAPI
net start ScannerAPI

# 또는
.\nssm restart ScannerAPI
```

### 5. 업데이트 확인

```bash
curl http://100.125.17.60:8000/api/status

# 응답: {"server_uptime_seconds": 0, ...}
# server_uptime_seconds가 작으면 방금 재시작됨
```

---

## 데이터베이스 관리

### 1. 데이터베이스 위치

```
C:\scanner\server\data\scanner.db
```

### 2. 백업

```bash
# 정기 백업 (예: 일일 백업)
copy C:\scanner\server\data\scanner.db C:\scanner\backups\scanner_$(date).db
```

### 3. 데이터베이스 초기화 (주의)

```bash
# DB 파일 삭제 (서버가 중지된 상태)
net stop ScannerAPI
del C:\scanner\server\data\scanner.db
net start ScannerAPI

# 서버가 자동으로 DB 재생성
```

---

## 트러블슈팅

### 1. 서비스가 시작되지 않는 경우

**증상**: `net start ScannerAPI` 실패

**원인 및 해결**

```bash
# NSSM 로그 확인
type C:\Users\lenovo\AppData\Roaming\NSSM\ScannerAPI\log.txt

# 일반적인 원인:
# 1. Python 경로 오류 → 전체 경로 확인
# 2. 의존성 미설치 → pip install -r requirements.txt
# 3. 포트 충돌 → 다른 서비스가 8000 사용 중
#    netstat -ano | findstr :8000
```

### 2. 포트 충돌

**문제**: 다른 서비스가 8000 포트 사용 중

```bash
# 포트 사용 확인
netstat -ano | findstr :8000

# 해당 프로세스 확인
tasklist | findstr {PID}

# 포트 변경 (app/config.py)
PORT = 8001
```

### 3. 데이터베이스 잠금

**증상**: "database is locked" 에러

**해결**

```bash
# 서버 재시작
net stop ScannerAPI
net start ScannerAPI

# 또는 DB 파일 삭제 후 재시작 (마지막 수단)
```

### 4. 이미지 로드 실패

**증상**: `/api/image` 응답이 404

**원인 및 해결**

```bash
# 1. NAS 연결 확인
ping {NAS_IP}

# 2. WebDAV 설정 확인 (config.py)
NAS_URL = "http://{NAS_IP}:{port}/"
NAS_USER = "..."
NAS_PASSWORD = "..."

# 3. 서버 로그 확인
# C:\Users\lenovo\AppData\Roaming\NSSM\ScannerAPI\log.txt
```

### 5. 느린 응답 시간

**증상**: 바코드 스캔 > 1초

**원인 및 해결**

```bash
# 1. 네트워크 상태 확인
ping 100.125.17.60

# 2. 데이터베이스 크기 확인
# C:\scanner\server\data\scanner.db 파일 크기

# 3. 서버 부하 확인
# Task Manager에서 CPU, 메모리 사용률

# 4. SQLite 인덱스 최적화 (마지막 수단)
# python scripts/optimize_db.py
```

---

## 정기 유지보수

### 일일 점검

```bash
# 1. 서비스 상태
net start ScannerAPI

# 2. 헬스 체크
curl http://100.125.17.60:8000/health

# 3. 최근 로그 확인
tail -f C:\Users\lenovo\AppData\Roaming\NSSM\ScannerAPI\log.txt
```

### 주간 유지보수

```bash
# 1. 데이터베이스 백업
copy C:\scanner\server\data\scanner.db C:\scanner\backups\

# 2. 의존성 업데이트 확인
pip list --outdated

# 3. 로그 크기 확인
# 로그 파일이 커지면 rotate
```

### 월간 유지보수

```bash
# 1. 전체 시스템 재시작
shutdown /r /t 0

# 2. Python 업데이트 확인
python --version

# 3. 저장소 코드 업데이트
# github에서 최신 버전 확인 후 배포
```

---

## 성능 최적화

### 1. 데이터베이스 최적화

```bash
# SQLite VACUUM (디스크 정리)
# python 대화형 모드에서:
# import sqlite3
# conn = sqlite3.connect('data/scanner.db')
# conn.execute('VACUUM')
# conn.close()
```

### 2. 이미지 캐시 설정

`app/config.py`에서 캐시 시간 설정:

```python
IMAGE_CACHE_MAX_AGE = 86400  # 24시간
THUMBNAIL_MAX_WIDTH = 300
```

### 3. 데이터베이스 연결 풀

현재 aiosqlite 사용 중 (자동 관리됨)

### 4. 정적 파일 압축

NSSM 서비스 시작 시 `--use-colors`는 불필요하면 제거

---

## 보안 주의사항

### 1. 방화벽

```bash
# Windows Firewall에서 8000 포트 허용
# 또는 Tailscale이 자동 관리
```

### 2. 데이터 보호

```bash
# DB 백업 정기 실행
# 민감한 정보는 .env에 저장

# .env 파일 예시
DB_ENCRYPTION_KEY="..."
NAS_PASSWORD="..."
```

### 3. 접근 제어

```python
# CORS 설정 (내부망만 허용)
CORS_ORIGINS = ["http://100.125.17.60:8000"]
```

---

## 재시작 후 확인 체크리스트

```bash
# 1. 서비스 상태 확인
Get-Service ScannerAPI | select Status

# 2. 포트 수신 확인
netstat -ano | findstr :8000

# 3. API 응답 확인
curl http://100.125.17.60:8000/health

# 4. 데이터베이스 접근 확인
curl http://100.125.17.60:8000/api/status

# 5. 로그 확인
Get-Content "C:\Users\lenovo\AppData\Roaming\NSSM\ScannerAPI\log.txt" -Tail 20
```

---

## 비상 복구

### 서비스가 반복적으로 재시작되는 경우

```bash
# 1. NSSM 로그 확인
type C:\Users\lenovo\AppData\Roaming\NSSM\ScannerAPI\log.txt

# 2. 의존성 재설치
cd C:\scanner\server
venv\Scripts\activate.bat
pip install --force-reinstall -r requirements.txt

# 3. 캐시 정리
rmdir /s /q app\__pycache__
del /s /q *.pyc

# 4. 서비스 재시작
net stop ScannerAPI
net start ScannerAPI
```

### 데이터 손실 복구

```bash
# 1. 최근 백업에서 복구
copy C:\scanner\backups\scanner_latest.db C:\scanner\server\data\scanner.db

# 2. 서비스 재시작
net stop ScannerAPI
net start ScannerAPI
```

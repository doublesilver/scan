# Mini PC 서버 설치 체크리스트

클라이언트 AnyDesk 원격접속 시 수행할 작업 체크리스트입니다.
기존 scan13.exe와 공존하도록 설치합니다.

---

## 1단계: 환경 파악 (확인만, 수정 금지)

### OS 및 하드웨어

- [ ] OS 확인 (Windows 또는 Linux, 버전)

  ```bash
  # Windows (PowerShell에서)
  [System.Environment]::OSVersion.VersionString
  systeminfo | findstr /C:"System Boot Time"

  # Linux
  lsb_release -a
  uname -a
  ```

- [ ] CPU/RAM/디스크 사양

  ```bash
  # Windows (PowerShell에서)
  Get-WmiObject Win32_Processor | Select-Object Name, NumberOfCores
  Get-WmiObject Win32_LogicalMemory | Select-Object TotalPhysicalMemory
  Get-Volume | Select-Object DriveLetter, Size, SizeRemaining

  # Linux
  lscpu
  free -h
  df -h
  ```

### Python 설치 여부

- [ ] Python 3.11+ 설치 확인
  ```bash
  python --version
  python3 --version
  ```

### 기존 프로그램 상태

- [ ] scan13.exe 실행 상태 확인 (프로세스 관리자에서)
- [ ] 사용 중인 포트 확인

  ```bash
  # Windows (PowerShell에서)
  netstat -ano | findstr LISTENING

  # Linux
  netstat -tlnp | grep LISTEN
  # 또는
  ss -tlnp | grep LISTEN
  ```

  → 8000번 포트 가용 여부 확인

### NAS/RaiDrive 환경

- [ ] RaiDrive 설치 여부 및 드라이브 마운트 상태 확인
- [ ] NAS WebDAV 주소 기록: `_____________________`
- [ ] WebDAV 계정 (사용자명): `_____________________`
- [ ] WebDAV 암호: `_____________________`
- [ ] RaiDrive에서 마운트된 드라이브 문자 기록: `____` (예: Z)
- [ ] 실제 NAS 경로 확인
  ```
  [드라이브]:\물류부\scan\
  ├── codepath.xlsx
  ├── coupangmd00_sku_download_*.xlsx
  ├── img/
  └── real_image/
  ```

### 네트워크

- [ ] Mini PC 내부 IP 확인

  ```bash
  # Windows (PowerShell에서)
  ipconfig | findstr "IPv4"

  # Linux
  ip addr show
  ```

- [ ] WiFi AP 정보 확인 (2.4GHz 권장)
- [ ] 방화벽 설정 상태 확인

---

## 2단계: OS별 서버 설치

### Windows의 경우

#### 2-1. Python 설치

- [ ] Python 3.11+ 설치 (아직 없는 경우)
  - Microsoft Store 또는 [python.org](https://www.python.org) 다운로드
  - 설치 시 "Add Python to PATH" 체크

#### 2-2. 프로젝트 파일 준비

- [ ] 프로젝트 폴더 복사 (Git clone 또는 zip 압축 해제)

  ```bash
  # Git clone 방식
  git clone https://github.com/doublesilver/scan.git scan
  cd scan

  # 또는 zip 파일 방식
  # 다운로드한 zip 압축 해제 후 cmd/PowerShell에서 폴더로 이동
  cd scan
  ```

#### 2-3. 파이썬 의존성 설치

- [ ] requirements.txt 설치
  ```bash
  cd server
  pip install -r requirements.txt
  ```
  → "Successfully installed" 확인

#### 2-4. 환경 설정

- [ ] `.env` 파일 생성
  ```bash
  copy .env.example .env
  ```
- [ ] `.env` 파일 편집 (메모장 또는 VSCode)
  ```
  HOST=0.0.0.0
  PORT=8000
  DATABASE_URL=sqlite+aiosqlite:///data/scanner.db
  WEBDAV_BASE_URL=http://[NAS IP]:[포트]/물류부/scan
  WEBDAV_USERNAME=[확인된 사용자명]
  WEBDAV_PASSWORD=[확인된 암호]
  XLSX_WATCH_DIR=./data/xlsx
  CODEPATH_FILE=codepath.xlsx
  SKU_DOWNLOAD_PATTERN=coupangmd00_sku_download_*.xlsx
  IMAGE_CACHE_DIR=./data/cache
  IMAGE_CACHE_MAX_SIZE_MB=500
  CORS_ORIGINS=["*"]
  ```

#### 2-5. 수동 서버 실행 테스트

- [ ] 명령줄에서 서버 실행
  ```bash
  python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
  ```
  → "Application startup complete" 확인
- [ ] 다른 명령줄 창에서 API 응답 테스트
  ```bash
  curl http://localhost:8000/health
  ```
  → `{"status": "ok"}` 응답 확인
- [ ] 서버 중지 (Ctrl+C)

#### 2-6. 방화벽 설정

- [ ] Windows Defender 방화벽에서 8000번 포트 허용
  ```
  설정 → 개인정보 보호 및 보안 → Windows Defender 방화벽
  → 고급 설정 → 인바운드 규칙 → 새 규칙
  → 포트(Port) → TCP → 특정 로컬 포트(8000) → 연결 허용
  ```

#### 2-7. 자동 시작 설정 (NSSM 이용)

- [ ] NSSM 다운로드 및 설치
  - [nssm.cc](https://nssm.cc/download) 에서 최신 버전 다운로드
  - 압축 해제 후 `nssm.exe` 경로 기록

- [ ] 관리자 PowerShell에서 서비스 등록

  ```bash
  # nssm.exe 경로로 이동
  cd "C:\path\to\nssm\win64"

  # 서비스 설정 명령 (한 줄로)
  .\nssm install ScannerServer "C:\path\to\python" "-m uvicorn app.main:app --host 0.0.0.0 --port 8000" "C:\path\to\scan\server"

  # 예시:
  # .\nssm install ScannerServer "C:\Users\user\AppData\Local\Programs\Python\Python311\python.exe" "-m uvicorn app.main:app --host 0.0.0.0 --port 8000" "C:\Users\user\scan\server"
  ```

- [ ] 서비스 시작

  ```bash
  net start ScannerServer
  ```

  → "The ScannerServer service was started successfully" 확인

- [ ] 서비스 자동 시작 확인

  ```bash
  # 서비스 관리자에서 "ScannerServer" 찾기
  # → 시작 유형: "자동" 확인
  ```

- [ ] 서비스 중지/재시작 테스트
  ```bash
  net stop ScannerServer
  net start ScannerServer
  ```

### Linux의 경우

#### 2-1. Python 설치

- [ ] Python 3.11+ 설치 (아직 없는 경우)
  ```bash
  sudo apt update
  sudo apt install python3.11 python3.11-venv python3-pip
  ```

#### 2-2. 프로젝트 파일 준비

- [ ] 프로젝트 폴더 복사
  ```bash
  git clone https://github.com/doublesilver/scan.git scan
  cd scan
  ```

#### 2-3. 가상환경 및 의존성 설치

- [ ] venv 생성

  ```bash
  cd server
  python3.11 -m venv venv
  source venv/bin/activate
  ```

- [ ] requirements.txt 설치
  ```bash
  pip install -r requirements.txt
  ```

#### 2-4. 환경 설정

- [ ] `.env` 파일 생성

  ```bash
  cp .env.example .env
  nano .env  # 또는 vi .env
  ```

- [ ] `.env` 파일 편집
  ```
  [위 Windows 설정과 동일]
  ```

#### 2-5. 수동 서버 실행 테스트

- [ ] 서버 실행

  ```bash
  source venv/bin/activate
  python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
  ```

  → "Application startup complete" 확인

- [ ] API 테스트

  ```bash
  # 다른 터미널에서
  curl http://localhost:8000/health
  ```

- [ ] 서버 중지 (Ctrl+C)

#### 2-6. 방화벽 설정

- [ ] UFW 방화벽에서 8000번 포트 허용
  ```bash
  sudo ufw allow 8000/tcp
  ```

#### 2-7. Systemd 서비스 등록

- [ ] scanner 사용자 생성 (아직 없는 경우)

  ```bash
  sudo useradd -r -s /bin/bash -d /opt/scanner -m scanner
  ```

- [ ] 프로젝트 폴더 이동

  ```bash
  sudo mkdir -p /opt/scanner
  sudo cp -r /path/to/scan /opt/scanner/
  sudo chown -R scanner:scanner /opt/scanner
  ```

- [ ] systemd 서비스 파일 설치

  ```bash
  sudo cp /opt/scanner/server/scanner-server.service /etc/systemd/system/
  sudo systemctl daemon-reload
  ```

- [ ] systemd 백업 타이머 등록 (선택)

  ```bash
  sudo cp /opt/scanner/server/scanner-backup.service /etc/systemd/system/
  sudo cp /opt/scanner/server/scanner-backup.timer /etc/systemd/system/
  sudo systemctl daemon-reload
  sudo systemctl enable scanner-backup.timer
  sudo systemctl start scanner-backup.timer
  ```

- [ ] 서비스 시작

  ```bash
  sudo systemctl start scanner-server
  sudo systemctl status scanner-server
  ```

  → "active (running)" 확인

- [ ] 서비스 자동 시작 등록

  ```bash
  sudo systemctl enable scanner-server
  ```

- [ ] 서비스 재시작 테스트
  ```bash
  sudo systemctl restart scanner-server
  curl http://localhost:8000/health
  ```

---

## 3단계: 데이터 검증

### 3-1. NAS xlsx 파일 위치 확인

- [ ] 마운트된 NAS 드라이브에서 다음 파일 위치 확인
  ```
  [드라이브]:\물류부\scan\codepath.xlsx
  [드라이브]:\물류부\scan\coupangmd00_sku_download_*.xlsx
  ```

### 3-2. 수동 파싱 테스트 (선택)

- [ ] 파싱 CLI 실행 (선택사항, 서버 시작 시 자동 수행됨)

  ```bash
  # Windows의 경우 (server 디렉토리에서)
  python -m app.services.parse_cli

  # Linux의 경우 (venv 활성화 후)
  source venv/bin/activate
  python -m app.services.parse_cli
  ```

  → 로그에서 적재된 상품/바코드 건수 확인

### 3-3. DB 데이터 건수 확인 (선택)

- [ ] SQLite DB 파일 확인
  ```bash
  # DB 파일 위치
  server/data/scanner.db
  ```
  → 약 11,821건의 상품 데이터 포함

### 3-4. API 테스트

- [ ] 헬스 체크

  ```bash
  curl http://localhost:8000/health
  ```

  → `{"status": "ok"}` 응답

- [ ] 상품 스캔 조회 (실제 바코드로)

  ```bash
  curl http://localhost:8000/api/scan/8809461170008
  ```

  → JSON 응답 (sku_id, product_name, barcodes, images)

- [ ] 상품 검색
  ```bash
  curl http://localhost:8000/api/search?q=%EC%B2%AD%EC%86%8C%EA%B8%B0
  # 또는 (URL 인코딩 자동)
  curl "http://localhost:8000/api/search?q=청소기"
  ```
  → JSON 배열 응답

### 3-5. 이미지 프록시 테스트 (선택)

- [ ] 이미지 경로 조회
  ```bash
  curl http://localhost:8000/api/image/img/000000_07da1a9dfd.jpg
  ```
  → 이미지 바이너리 또는 mock 이미지

---

## 4단계: PDA 연동 테스트

### 4-1. PDA 네트워크 연결

- [ ] PDA가 같은 WiFi AP에 연결되었는지 확인
  - PDA "설정 → 네트워크 → WiFi" 에서 SSID 및 신호 강도 확인

### 4-2. PDA 앱 서버 URL 설정

- [ ] PDA 앱 실행 → 설정 화면
- [ ] 서버 IP 입력
  ```
  http://[Mini PC 내부 IP]:8000
  ```
  예: `http://192.168.1.100:8000`

### 4-3. 바코드 스캔 테스트

- [ ] PDA에서 실제 상품 바코드 스캔
  - DataWedge 버튼 눌러 스캔 모드 진입
  - 상품 바코드 스캔
  - 상품명 및 SKU ID 표시 확인 (0.5초 이내)

### 4-4. 이미지 로딩 확인

- [ ] 스캔 후 상품 이미지 로드 확인
  - 썸네일 이미지 (img/ 폴더) 표시 확인
  - 실사 이미지 (real_image/ 폴더) 슬라이드 확인

---

## 5단계: 안정성 확인

### 5-1. 기존 프로그램 공존 확인

- [ ] scan13.exe가 여전히 정상 동작하는지 확인
  - 프로세스 관리자에서 scan13.exe 실행 상태 확인
  - USB 바코드 스캐너 연결 및 스캔 테스트

### 5-2. 서버 자동 시작 확인 (OS별)

- [ ] Windows: Mini PC 재시작 후 ScannerServer 서비스 자동 시작 확인

  ```bash
  net start | findstr ScannerServer
  ```

- [ ] Linux: Mini PC 재시작 후 scanner-server 서비스 자동 시작 확인
  ```bash
  sudo systemctl status scanner-server
  ```

### 5-3. 백업 타이머 동작 확인 (Linux)

- [ ] 백업 스크립트가 매일 03:00에 실행되는지 확인
  ```bash
  sudo systemctl status scanner-backup.timer
  ls -lah /opt/scanner/server/data/backups/
  ```

### 5-4. 메모리 및 디스크 모니터링 (선택)

- [ ] 서버 1시간 실행 후 리소스 사용량 확인

  ```bash
  # Windows (PowerShell)
  Get-Process | Where-Object {$_.ProcessName -eq "python"} | Select-Object Name, WS

  # Linux
  ps aux | grep uvicorn
  ```

---

## 비상 대응

### 서버 문제 발생 시

- [ ] 문제 발생 시 서버만 중지하면 scan13.exe에 영향 없음

  ```bash
  # Windows
  net stop ScannerServer

  # Linux
  sudo systemctl stop scanner-server
  ```

### 롤백 (완전 제거)

- [ ] 프로젝트 폴더 삭제

  ```bash
  # Windows
  rmdir /s C:\path\to\scan

  # Linux
  sudo rm -rf /opt/scanner
  ```

- [ ] 서비스 제거

  ```bash
  # Windows
  net stop ScannerServer
  .\nssm remove ScannerServer confirm

  # Linux
  sudo systemctl disable scanner-server
  sudo rm /etc/systemd/system/scanner-server.service
  sudo systemctl daemon-reload
  ```

---

## 트러블슈팅

| 증상                   | 원인                            | 해결 방법                                       |
| ---------------------- | ------------------------------- | ----------------------------------------------- |
| 8000 포트 이미 사용 중 | 다른 애플리케이션에서 포트 사용 | 사용 중인 애플리케이션 중지 또는 포트 변경      |
| WebDAV 연결 실패       | NAS 주소/계정 오류              | .env 파일의 WEBDAV_URL/USERNAME/PASSWORD 재확인 |
| Excel 파일 적재 실패   | 파일 형식/헤더 문제             | DEV_NOTES.md "데이터 파일 분석" 참조            |
| PDA에서 서버 미응답    | 네트워크 격리                   | 방화벽 규칙 및 WiFi 연결 확인                   |
| 스캔 속도 느림 (>1초)  | 이미지 프록시 지연              | NAS 네트워크 대역폭 및 이미지 캐시 크기 확인    |

---

## 검증 완료

- [ ] 모든 체크리스트 항목 완료
- [ ] PDA 스캔 → 상품 조회 → 이미지 로드 정상 동작 확인
- [ ] scan13.exe 공존 확인
- [ ] 서버 자동 시작 설정 확인

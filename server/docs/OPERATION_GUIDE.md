# 물류창고 스캐너 운영 가이드

## 1. 서버 관리

### 1.1 서버 시작

시스템 부팅 후 자동으로 시작됩니다. 수동으로 시작해야 하는 경우 PowerShell(관리자)에서:

```powershell
nssm start ScannerAPI
```

### 1.2 서버 중지

```powershell
nssm stop ScannerAPI
```

### 1.3 서버 상태 확인

```powershell
nssm status ScannerAPI
```

정상 상태: `SERVICE_RUNNING`

### 1.4 서버 재시작

```powershell
nssm restart ScannerAPI
```

### 1.5 서버 IP 주소 확인

Mini PC에 접속하려면 Tailscale IP를 사용합니다:

```
100.125.17.60
```

같은 WiFi 내부에서 직접 접속 시, PowerShell에서:

```powershell
ipconfig
```

"IPv4 주소" 항목에서 확인합니다.

### 1.6 서버 연결 테스트

같은 WiFi에 연결된 기기나 Tailscale 연결된 기기에서:

```
http://100.125.17.60:8000/health
```

브라우저 주소창에 입력해 `{"status":"ok"}` 응답이 오면 정상입니다.

### 1.7 서버 로그 확인

로그 파일 위치:

```
C:\scanner\logs\
```

파일 탐색기에서 위 경로를 열거나, PowerShell에서 최근 50줄 확인:

```powershell
Get-Content C:\scanner\logs\stderr.log -Tail 50
```

실시간 확인:

```powershell
Get-Content C:\scanner\logs\stderr.log -Wait -Tail 20
```

또는 **이벤트 뷰어** (Win + R → `eventvwr`) → Windows 로그 → 응용 프로그램에서 ScannerAPI 항목 확인.

---

## 2. 데이터 관리

### 2.1 상품 데이터 갱신

PDA에서 "상품을 찾을 수 없습니다" 오류가 나타나면 데이터를 갱신해야 합니다.

#### NAS를 통한 자동 갱신 (권장)

1. NAS 폴더를 열기 (기존 시스템과 동일하게 접근)
2. `scan` 폴더로 이동
3. 다음 파일들을 업로드:
   - `codepath.xlsx` — 바코드 → 이미지 매핑 파일
   - `coupangmd00_sku_download_*.xlsx` — 쿠팡 SKU 데이터 파일

파일이 업로드되면 자동으로 처리됩니다 (처리 시간: 수십 초).

#### 서버 명령어를 통한 수동 갱신

PowerShell(관리자)에서 실행:

```powershell
cd C:\scanner\server
python -m app.services.parse_cli
```

### 2.2 파싱 로그 확인

최종 적재 결과를 확인하려면:

```powershell
sqlite3 C:\scanner\server\data\scanner.db "SELECT * FROM parse_log ORDER BY parsed_at DESC LIMIT 1;"
```

각 컬럼 설명:

- `record_count`: 읽은 행 수
- `added_count`: 신규 추가 상품 수
- `updated_count`: 갱신된 상품 수
- `error_count`: 오류 발생한 행 수
- `duration_ms`: 처리 소요 시간

### 2.3 DB 상태 확인

데이터베이스에 저장된 상품 건수 확인:

```powershell
sqlite3 C:\scanner\server\data\scanner.db "SELECT COUNT(*) FROM product;"
```

바코드 건수 확인:

```powershell
sqlite3 C:\scanner\server\data\scanner.db "SELECT COUNT(*) FROM barcode;"
```

이미지 건수 확인:

```powershell
sqlite3 C:\scanner\server\data\scanner.db "SELECT COUNT(*) FROM image;"
```

### 2.4 특정 상품 검색

PDA에서 검색하기 전에 서버에서 상품이 실제로 존재하는지 확인:

```powershell
sqlite3 C:\scanner\server\data\scanner.db "SELECT sku_id, product_name FROM product WHERE product_name LIKE '%상품명%' LIMIT 5;"
```

특정 바코드 조회:

```powershell
sqlite3 C:\scanner\server\data\scanner.db "SELECT barcode, sku_id FROM barcode WHERE barcode = '8809461170008';"
```

---

## 3. PDA 앱 관리

### 3.1 APK 파일 위치

```
C:\scanner\android\app-debug.apk
```

### 3.2 앱 설치

1. **첫 설치 또는 업데이트 시**: APK 파일을 PDA에 전송
   - 파일 관리자에서 APK 실행
   - "설치" 버튼 선택
   - 설치 완료 후 "열기" 선택

2. **설치 완료 후**: 홈 화면에서 "스캐너" 앱 아이콘 탭

### 3.3 서버 IP 설정

PDA 앱 실행 후:

1. 화면 오른쪽 상단 설정 아이콘 (⚙) 탭
2. "서버 주소" 입력란에 Mini PC 서버 IP 입력
   - Tailscale 사용 시: `http://100.125.17.60:8000`
   - 내부 WiFi 직접 연결 시: `http://192.168.x.x:8000`
3. "확인" 버튼으로 연결 테스트
4. 연결 성공하면 자동 저장됨

### 3.4 앱 업데이트

개발자로부터 새로운 APK를 받았을 때:

1. 기존 앱 제거: 설정 → 앱 관리 → 스캐너 → 제거
2. 새 APK 설치 (3.2 절차 따름)
3. 서버 IP 재설정

---

## 4. 백업 및 복구

### 4.1 자동 백업 확인

시스템은 매일 오전 3시에 자동 백업합니다. Windows 작업 스케줄러에서 상태 확인:

Win + R → `taskschd.msc` → 작업 스케줄러 라이브러리에서 `ScannerBackup` 항목 확인.

최근 백업 파일 확인:

```powershell
dir C:\scanner\server\data\backups\
```

백업은 최근 7일치만 유지됩니다 (자동 삭제).

### 4.2 수동 백업

```powershell
C:\scanner\scripts\backup.ps1
```

기본값: `C:\scanner\server\data\scanner.db` → `C:\scanner\server\data\backups\scanner_YYYYMMDD.db`

### 4.3 복구

복구가 필요한 경우 (데이터 손상, 실수로 삭제 등):

```powershell
C:\scanner\scripts\restore.ps1 C:\scanner\server\data\backups\scanner_20260319.db C:\scanner\server\data\scanner.db
```

- 첫 번째 인자: 복구할 백업 파일 경로
- 두 번째 인자: 복구 대상 DB 파일 경로

복구 후 서버 재시작:

```powershell
nssm restart ScannerAPI
```

### 4.4 자동 백업 작업 등록

처음 설치 시 또는 재등록이 필요할 때:

```powershell
C:\scanner\scripts\setup_backup_task.ps1
```

---

## 5. 문제 해결

### 5.1 "서버에 연결할 수 없습니다" 오류

#### 1단계: Mini PC 서버 상태 확인

```powershell
nssm status ScannerAPI
```

`SERVICE_RUNNING`이 아니면:

```powershell
nssm restart ScannerAPI
```

#### 2단계: WiFi 연결 확인

- PDA의 WiFi 설정 확인 (연결된 네트워크 확인)
- Mini PC와 동일한 WiFi 네트워크에 연결되었는지 확인
- 네트워크 신호 강도 확인

#### 3단계: 서버 IP 재확인

Tailscale IP: `100.125.17.60`

PDA 앱의 서버 주소가 일치하는지 확인하고, 필요하면 다시 설정 (3.3 절차).

#### 4단계: 로그 확인

```powershell
Get-Content C:\scanner\logs\stderr.log -Tail 20
```

오류 메시지가 있으면 기록하고 개발자에게 문의.

### 5.2 "상품을 찾을 수 없습니다" 오류

#### 1단계: 데이터 갱신

2.1 절의 "NAS를 통한 자동 갱신" 또는 "서버 명령어를 통한 수동 갱신" 중 하나 실행.

#### 2단계: 갱신 완료 확인

```powershell
Get-Content C:\scanner\logs\stderr.log -Tail 20
```

"codepath 결과" 및 "sku_download 결과" 메시지가 표시되면 완료.

#### 3단계: PDA 앱 재시작

PDA에서 앱을 완전히 종료했다가 다시 실행.

#### 4단계: 바코드 확인

스캔하려는 상품의 바코드가 실제로 존재하는지 확인 (2.4절):

```powershell
sqlite3 C:\scanner\server\data\scanner.db "SELECT barcode, sku_id FROM barcode WHERE barcode = '{스캔한바코드}';"
```

결과가 없으면 바코드 데이터가 누락되었을 가능성이 있습니다. 개발자에게 문의.

### 5.3 "이미지가 표시되지 않습니다" 오류

#### 1단계: NAS 연결 확인

파일 탐색기에서 NAS 드라이브가 정상적으로 연결되어 있는지 확인합니다.

연결이 끊겼으면 NAS 드라이브를 다시 연결하거나 Mini PC를 재시작합니다.

#### 2단계: 서버 로그 확인

```powershell
Get-Content C:\scanner\logs\stderr.log -Tail 50 | Select-String "image"
```

이미지 다운로드 오류가 있으면 기록하고 개발자에게 문의.

#### 3단계: PDA 앱 캐시 삭제

설정 → 앱 관리 → 스캐너 → 저장공간 → 캐시 삭제

### 5.4 서버 재시작 필요한 경우

다음 상황에서 서버를 재시작합니다:

- 데이터 갱신 후 상품이 여전히 검색되지 않는 경우
- PDA 앱이 자주 연결 해제되는 경우
- 기타 비정상 동작 시

```powershell
nssm restart ScannerAPI
```

재시작 후 약 5~10초 후에 PDA에서 다시 접속 시도.

---

## 6. 주의사항

### 6.1 .env 파일 수정 금지

`C:\scanner\server\.env` 파일은 시스템 설정 파일입니다. 임의로 수정하면 서버 오작동이 발생합니다.

설정 변경이 필요하면 개발자에게 문의하세요.

### 6.2 DB 파일 직접 수정 금지

`C:\scanner\server\data\scanner.db`는 서버에 의해 관리되는 파일입니다. 직접 수정하면 데이터 손상이 발생할 수 있습니다.

상품 데이터 변경은 항상 NAS의 xlsx 파일을 업데이트하고 자동 갱신을 통해 처리하세요.

### 6.3 NAS 폴더 구조 변경 금지

NAS의 `scan` 폴더 내 폴더 구조 (img/, real_image/ 등)는 서버와 기존 프로그램이 의존하는 구조입니다.

폴더를 이동하거나 이름을 변경하면 이미지가 표시되지 않습니다. 변경이 필요하면 개발자에게 사전 문의하세요.

### 6.4 xlsx 파일 포맷 유지

- `codepath.xlsx`: 헤더 없음, 바코드 (Column A) + 이미지 경로 (Column B)
- `coupangmd00_sku_download_*.xlsx`: 쿠팡 표준 형식 (Column A = SKU ID, Column D = 바코드, Column C = 상품명)

파일 구조를 변경하면 파싱에 실패합니다. 변경 전에 개발자에게 문의하세요.

---

## 7. 빠른 참고

| 상황             | 명령어 (PowerShell 관리자)                                                                                            |
| ---------------- | --------------------------------------------------------------------------------------------------------------------- |
| 서버 시작        | `nssm start ScannerAPI`                                                                                               |
| 서버 중지        | `nssm stop ScannerAPI`                                                                                                |
| 서버 상태        | `nssm status ScannerAPI`                                                                                              |
| 서버 재시작      | `nssm restart ScannerAPI`                                                                                             |
| 서버 IP 확인     | Tailscale: `100.125.17.60` / 내부망: `ipconfig`                                                                       |
| 서버 연결 테스트 | 브라우저에서 `http://100.125.17.60:8000/health`                                                                       |
| 서버 로그        | `Get-Content C:\scanner\logs\stderr.log -Tail 50`                                                                     |
| 데이터 갱신      | `cd C:\scanner\server` → `python -m app.services.parse_cli`                                                           |
| 상품 건수 조회   | `sqlite3 C:\scanner\server\data\scanner.db "SELECT COUNT(*) FROM product;"`                                           |
| 수동 백업        | `C:\scanner\scripts\backup.ps1`                                                                                       |
| 복구             | `C:\scanner\scripts\restore.ps1 C:\scanner\server\data\backups\scanner_YYYYMMDD.db C:\scanner\server\data\scanner.db` |
| 백업 파일 확인   | `dir C:\scanner\server\data\backups\`                                                                                 |

---

## 8. 기술 지원

문제가 해결되지 않으면 다음 정보를 기록하고 개발자에게 문의하세요:

1. 발생한 증상 (스크린샷이 있으면 더 좋습니다)
2. 서버 로그
   ```powershell
   Get-Content C:\scanner\logs\stderr.log -Tail 50
   ```
3. 데이터베이스 상태
   ```powershell
   sqlite3 C:\scanner\server\data\scanner.db "SELECT COUNT(*) FROM product; SELECT COUNT(*) FROM barcode; SELECT COUNT(*) FROM image;"
   ```
4. 서버 연결 테스트 결과 — 브라우저에서 `http://100.125.17.60:8000/health` 접속
5. PDA 앱의 설정된 서버 IP 주소

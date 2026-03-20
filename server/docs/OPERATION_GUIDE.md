# 물류창고 스캐너 운영 가이드

## 1. 서버 관리

### 1.1 서버 시작

시스템 부팅 후 자동으로 시작됩니다. 수동으로 시작해야 하는 경우:

```bash
sudo systemctl start scanner-server
```

### 1.2 서버 중지

```bash
sudo systemctl stop scanner-server
```

### 1.3 서버 상태 확인

```bash
sudo systemctl status scanner-server
```

정상 상태 표시: `active (running)`

### 1.4 서버 IP 주소 확인

```bash
hostname -I
```

또는 설정된 NAS 드라이브를 통해 Mini PC 네트워크 설정에서 IP 주소를 확인할 수 있습니다.

### 1.5 서버 연결 테스트

Mini PC와 같은 WiFi 네트워크에 연결된 다른 장치에서:

```bash
curl http://{서버IP}:8000/health
```

정상 응답: `{"status":"ok"}`

### 1.6 서버 로그 확인

```bash
sudo journalctl -u scanner-server -n 50 --no-pager
```

실시간 로그 확인:

```bash
sudo journalctl -u scanner-server -f
```

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

다음 명령어를 Mini PC에서 실행:

```bash
cd /opt/scanner/server
python3 -m app.services.parse_cli
```

### 2.2 파싱 로그 확인

최종 적재 결과를 확인하려면:

```bash
sudo sqlite3 /opt/scanner/server/data/scanner.db "SELECT * FROM parse_log ORDER BY parsed_at DESC LIMIT 1;"
```

각 컬럼 설명:

- `record_count`: 읽은 행 수
- `added_count`: 신규 추가 상품 수
- `updated_count`: 갱신된 상품 수
- `error_count`: 오류 발생한 행 수
- `duration_ms`: 처리 소요 시간

### 2.3 DB 상태 확인

데이터베이스에 저장된 상품 건수 확인:

```bash
sudo sqlite3 /opt/scanner/server/data/scanner.db "SELECT COUNT(*) FROM product;"
```

바코드 건수 확인:

```bash
sudo sqlite3 /opt/scanner/server/data/scanner.db "SELECT COUNT(*) FROM barcode;"
```

이미지 건수 확인:

```bash
sudo sqlite3 /opt/scanner/server/data/scanner.db "SELECT COUNT(*) FROM image;"
```

### 2.4 특정 상품 검색

PDA에서 검색하기 전에 서버에서 상품이 실제로 존재하는지 확인:

```bash
sudo sqlite3 /opt/scanner/server/data/scanner.db "SELECT sku_id, product_name FROM product WHERE product_name LIKE '%상품명%' LIMIT 5;"
```

특정 바코드 조회:

```bash
sudo sqlite3 /opt/scanner/server/data/scanner.db "SELECT barcode, sku_id FROM barcode WHERE barcode = '8809461170008';"
```

---

## 3. PDA 앱 관리

### 3.1 APK 파일 위치

```
/opt/scanner/android/app-debug.apk
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
   - 형식: `http://{IP}:8000` (예: `http://192.168.1.100:8000`)
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

시스템은 매일 오전 3시에 자동 백업합니다. 상태 확인:

```bash
sudo systemctl status scanner-backup.timer
```

최근 백업 파일 확인:

```bash
ls -lh /opt/scanner/server/data/backups/
```

백업은 최근 6일치만 유지됩니다 (자동 삭제).

### 4.2 수동 백업

```bash
/opt/scanner/scripts/backup.sh
```

기본값: `/opt/scanner/server/data/scanner.db` → `/opt/scanner/server/data/backups/scanner_YYYYMMDD.db`

### 4.3 복구

복구가 필요한 경우 (데이터 손상, 실수로 삭제 등):

```bash
/opt/scanner/scripts/restore.sh /opt/scanner/server/data/backups/scanner_20260319.db /opt/scanner/server/data/scanner.db
```

- 첫 번째 인자: 복구할 백업 파일 경로
- 두 번째 인자: 복구 대상 DB 파일 경로

복구 후 서버 재시작:

```bash
sudo systemctl restart scanner-server
```

---

## 5. 문제 해결

### 5.1 "서버에 연결할 수 없습니다" 오류

#### 1단계: Mini PC 서버 상태 확인

```bash
sudo systemctl status scanner-server
```

상태가 `inactive` 또는 `failed`이면:

```bash
sudo systemctl restart scanner-server
```

#### 2단계: WiFi 연결 확인

- PDA의 WiFi 설정 확인 (연결된 네트워크 확인)
- Mini PC와 동일한 WiFi 네트워크에 연결되었는지 확인
- 네트워크 신호 강도 확인

#### 3단계: 서버 IP 재확인

```bash
hostname -I
```

PDA 앱의 서버 주소가 현재 IP와 일치하는지 확인하고, 필요하면 다시 설정 (3.3 절차).

#### 4단계: 로그 확인

```bash
sudo journalctl -u scanner-server -n 20 --no-pager
```

오류 메시지가 있으면 기록하고 개발자에게 문의.

### 5.2 "상품을 찾을 수 없습니다" 오류

#### 1단계: 데이터 갱신

2.1 절의 "NAS를 통한 자동 갱신" 또는 "서버 명령어를 통한 수동 갱신" 중 하나 실행.

#### 2단계: 갱신 완료 확인

```bash
sudo journalctl -u scanner-server -n 20 --no-pager
```

"codepath 결과" 및 "sku_download 결과" 메시지가 표시되면 완료.

#### 3단계: PDA 앱 재시작

PDA에서 앱을 완전히 종료했다가 다시 실행.

#### 4단계: 바코드 확인

스캔하려는 상품의 바코드가 실제로 존재하는지 확인 (2.4절):

```bash
sudo sqlite3 /opt/scanner/server/data/scanner.db "SELECT barcode, sku_id FROM barcode WHERE barcode = '{스캔한바코드}';"
```

결과가 없으면 바코드 데이터가 누락되었을 가능성이 있습니다. 개발자에게 문의.

### 5.3 "이미지가 표시되지 않습니다" 오류

#### 1단계: NAS 연결 확인

```bash
mount | grep -i webdav
```

또는 NAS 폴더가 마운트되어 있는지 확인:

```bash
ls /mnt/nas/
```

NAS가 마운트되지 않았으면:

```bash
sudo mount -t davfs {NAS주소} /mnt/nas/
```

(NAS 주소와 마운트 경로는 시스템 설정에 따라 다를 수 있습니다.)

#### 2단계: 서버 로그 확인

```bash
sudo journalctl -u scanner-server -n 50 --no-pager | grep -i image
```

이미지 다운로드 오류가 있으면 기록하고 개발자에게 문의.

#### 3단계: PDA 앱 캐시 삭제

설정 → 앱 관리 → 스캐너 → 저장공간 → 캐시 삭제

### 5.4 서버 재시작 필요한 경우

다음 상황에서 서버를 재시작합니다:

- 데이터 갱신 후 상품이 여전히 검색되지 않는 경우
- PDA 앱이 자주 연결 해제되는 경우
- 기타 비정상 동작 시

```bash
sudo systemctl restart scanner-server
```

재시작 후 약 5~10초 후에 PDA에서 다시 접속 시도.

---

## 6. 주의사항

### 6.1 .env 파일 수정 금지

`/opt/scanner/server/.env` 파일은 시스템 설정 파일입니다. 임의로 수정하면 서버 오작동이 발생합니다.

설정 변경이 필요하면 개발자에게 문의하세요.

### 6.2 DB 파일 직접 수정 금지

`/opt/scanner/server/data/scanner.db`는 서버에 의해 관리되는 파일입니다. 직접 수정하면 데이터 손상이 발생할 수 있습니다.

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

| 상황             | 명령어                                                                                   |
| ---------------- | ---------------------------------------------------------------------------------------- |
| 서버 시작        | `sudo systemctl start scanner-server`                                                    |
| 서버 중지        | `sudo systemctl stop scanner-server`                                                     |
| 서버 상태        | `sudo systemctl status scanner-server`                                                   |
| 서버 재시작      | `sudo systemctl restart scanner-server`                                                  |
| 서버 IP 확인     | `hostname -I`                                                                            |
| 서버 연결 테스트 | `curl http://{IP}:8000/health`                                                           |
| 서버 로그        | `sudo journalctl -u scanner-server -f`                                                   |
| 데이터 갱신      | `cd /opt/scanner/server && python3 -m app.services.parse_cli`                            |
| 상품 건수 조회   | `sudo sqlite3 /opt/scanner/server/data/scanner.db "SELECT COUNT(*) FROM product;"`       |
| 수동 백업        | `/opt/scanner/scripts/backup.sh`                                                         |
| 복구             | `/opt/scanner/scripts/restore.sh /path/to/backup.db /opt/scanner/server/data/scanner.db` |
| 백업 파일 확인   | `ls -lh /opt/scanner/server/data/backups/`                                               |

---

## 8. 기술 지원

문제가 해결되지 않으면 다음 정보를 기록하고 개발자에게 문의하세요:

1. 발생한 증상 (스크린샷이 있으면 더 좋습니다)
2. 서버 로그
   ```bash
   sudo journalctl -u scanner-server -n 50 --no-pager
   ```
3. 데이터베이스 상태
   ```bash
   sudo sqlite3 /opt/scanner/server/data/scanner.db "SELECT COUNT(*) FROM product; SELECT COUNT(*) FROM barcode; SELECT COUNT(*) FROM image;"
   ```
4. 서버 연결 테스트 결과
   ```bash
   curl http://{서버IP}:8000/health
   ```
5. PDA 앱의 설정된 서버 IP 주소

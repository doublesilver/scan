#!/bin/bash
set -e

# 사용법: ./scripts/deploy.sh [major|minor|patch]
# 기본: patch

BUMP_TYPE=${1:-patch}
MINI_PC="lenovo@100.125.17.60"
SERVER_PATH="C:/scanner/server"

echo "🚀 피닉스앱 배포 시작..."

# 1. 현재 버전 읽기
GRADLE="android/app/build.gradle.kts"
CURRENT_CODE=$(grep "versionCode = " $GRADLE | grep -o '[0-9]*')
CURRENT_NAME=$(grep "versionName = " $GRADLE | grep -o '"[^"]*"' | tr -d '"')

echo "📌 현재: v${CURRENT_NAME} (code=${CURRENT_CODE})"

# 2. 버전 올리기
NEW_CODE=$((CURRENT_CODE + 1))
IFS='.' read -r MAJOR MINOR PATCH <<< "$CURRENT_NAME"
case $BUMP_TYPE in
  major) MAJOR=$((MAJOR + 1)); MINOR=0; PATCH=0 ;;
  minor) MINOR=$((MINOR + 1)); PATCH=0 ;;
  patch) PATCH=$((PATCH + 1)) ;;
esac
NEW_NAME="${MAJOR}.${MINOR}.${PATCH}"

echo "📦 새 버전: v${NEW_NAME} (code=${NEW_CODE})"

# 3. build.gradle.kts 업데이트
sed -i '' "s/versionCode = ${CURRENT_CODE}/versionCode = ${NEW_CODE}/" $GRADLE
sed -i '' "s/versionName = \"${CURRENT_NAME}\"/versionName = \"${NEW_NAME}\"/" $GRADLE

# 4. routes.py 업데이트
ROUTES="server/app/api/routes.py"
sed -i '' "s/APP_VERSION_CODE = ${CURRENT_CODE}/APP_VERSION_CODE = ${NEW_CODE}/" $ROUTES
sed -i '' "s/APP_VERSION_NAME = \"${CURRENT_NAME}\"/APP_VERSION_NAME = \"${NEW_NAME}\"/" $ROUTES

# 5. Android 빌드
echo "🔨 Android 빌드 중..."
cd android
./gradlew assembleLiveDebug --quiet
cd ..

# 6. 미니PC 배포
echo "📤 미니PC 배포 중..."
scp android/app/build/outputs/apk/live/debug/app-live-debug.apk ${MINI_PC}:"${SERVER_PATH}/apk/app-live-debug.apk"

# 서버 파일 배포
for f in app/api/routes.py app/api/map_routes.py app/api/shelf_routes.py app/api/warehouse_routes.py app/services/warehouse_service.py app/main.py app/config.py app/middleware/auth.py app/db/schema.py app/db/migrate_map.py app/models/schemas.py; do
  if [ -f "server/$f" ]; then
    scp "server/$f" "${MINI_PC}:${SERVER_PATH}/$f" 2>/dev/null
  fi
done

# 웹 에디터
scp server/static/map-editor.html ${MINI_PC}:"${SERVER_PATH}/static/map-editor.html" 2>/dev/null

# 7. 서버 재시작
echo "🔄 서버 재시작..."
ssh ${MINI_PC} "nssm restart ScannerAPI"

# 8. 확인
sleep 3
VERSION=$(curl -s http://100.125.17.60:8000/api/app-version)
echo ""
echo "✅ 배포 완료!"
echo "   ${VERSION}"
echo "   v${NEW_NAME} (code=${NEW_CODE})"

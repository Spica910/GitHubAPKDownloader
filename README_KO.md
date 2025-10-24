# GitHub APK Downloader

GitHub 저장소를 탐색하고 APK 파일을 다운로드할 수 있는 Android 앱입니다. 첫 화면에서 자동으로 APK 파일을 감지하여 표시합니다.

[English README](README.md)

## 주요 기능

### 핵심 기능
- **GitHub OAuth Device Flow** 인증 (클라이언트 시크릿 불필요)
- **APK 자동 감지**: 릴리스의 APK 파일을 자동으로 스캔하여 메인 화면에 표시
- **브랜치 선택기**: 드롭다운 메뉴로 저장소 브랜치 전환
- **저장소 관리**: 앱에서 직접 새 저장소 생성
- **파일 탐색기**: 저장소 파일 트리 탐색
- **파일/폴더 업로드**: 단일 파일 또는 전체 폴더를 모든 브랜치에 업로드

### 다운로드 및 설치
- 메인 화면에서 원클릭 APK 다운로드
- APK 파일이 있는 모든 릴리스 보기
- 알림과 함께 자동 다운로드 관리
- 다운로드 후 직접 설치 옵션

### 화면 표시
- 저장소 카드에 APK 위치, 파일명, 크기 표시
- APK 개수가 포함된 저장소 카운터 (예: "📦 6 repositories (2 with APKs)")
- Material Design 3의 다크 테마

## 설치 방법

### Termux에서 빌드하기

```bash
# 프로젝트 디렉토리로 이동
cd ~/GitHubAPKDownloader

# Gradle로 빌드
gradle assembleDebug

# APK를 다운로드 폴더로 복사
cp app/build/outputs/apk/debug/app-debug.apk /storage/emulated/0/Download/
```

APK 파일이 Download 폴더에 생성됩니다!

### Android Studio에서 빌드하기

1. Android Studio에서 프로젝트 열기
2. Gradle 동기화 완료 대기
3. "Run" 클릭 또는 Shift+F10

## 사용 방법

### 1. 첫 실행 및 로그인
1. 앱을 실행하고 "Login with GitHub" 클릭
2. 화면에 표시되는 코드를 복사
3. `https://github.com/login/device`에 접속하여 코드 입력
4. 앱으로 돌아오면 자동으로 로그인됩니다

### 2. APK 찾기 및 다운로드
1. 로그인하면 저장소 목록이 표시됩니다
2. **APK가 있는 저장소는 초록색 박스로 표시됩니다**
3. 박스에는 다음 정보가 표시됩니다:
   - APK 위치 (어떤 릴리스에 있는지)
   - 파일명과 크기
4. "Download APK" 버튼을 눌러 다운로드
5. 다운로드가 완료되면 알림을 눌러 설치

### 3. 저장소 생성
1. 상단의 "+" 버튼 클릭
2. 저장소 이름과 설명 입력
3. "Create" 버튼 클릭
4. 저장소가 생성됩니다 (공개 저장소로만 생성)

### 4. 파일 업로드
1. 저장소의 "Files" 버튼 클릭
2. 원하는 브랜치를 드롭다운에서 선택
3. 우측 하단의 "+" 버튼 클릭
4. "Browse Files" - 단일 파일 선택
5. "Browse Folders" - 폴더 전체 선택
6. 대상 경로 입력 후 "Upload" 클릭

### 5. 브랜치 전환
1. 파일 트리 화면에서 상단의 "Branch:" 드롭다운 클릭
2. 원하는 브랜치 선택
3. 파일 목록이 자동으로 업데이트됩니다

## 화면 구성

### 메인 화면 (저장소 목록)
```
┌─────────────────────────────────┐
│ Repositories          [🔍] [+]  │
├─────────────────────────────────┤
│ 📦 6 repositories (2 with APKs) │
├─────────────────────────────────┤
│ ┌─────────────────────────────┐ │
│ │ user/repo-name              │ │
│ │ Description here...         │ │
│ │ Language: Kotlin            │ │
│ │ ┌─────────────────────────┐ │ │
│ │ │ 📦 APK Available        │ │ │
│ │ │ Release: v1.0.0         │ │ │
│ │ │ app.apk (5.2 MB)        │ │ │
│ │ │ [Download APK]          │ │ │
│ │ └─────────────────────────┘ │ │
│ │ [Files]      [Releases]     │ │
│ └─────────────────────────────┘ │
└─────────────────────────────────┘
```

### 파일 트리 화면
```
┌─────────────────────────────────┐
│ Files: user/repo                │
├─────────────────────────────────┤
│ Branch: [main ▼]    [Upload]    │
│ /path/to/current                │
├─────────────────────────────────┤
│ 📁 folder1/                     │
│ 📄 file1.txt                    │
│ 📄 file2.kt                     │
│                           [+]   │
└─────────────────────────────────┘
```

## 필요한 권한

- **INTERNET**: GitHub API 접근
- **WRITE_EXTERNAL_STORAGE** (Android 6-9): 파일 저장
- Android 10+ 에서는 scoped storage 사용으로 권한 불필요

## 기술 스택

- **Kotlin**: 모던 Android 개발
- **Retrofit**: REST API 클라이언트
- **Coroutines**: 비동기 프로그래밍
- **Material Design 3**: 최신 UI 컴포넌트
- **GitHub API v3**: 저장소 및 릴리스 데이터
- **DownloadManager**: 시스템 다운로드 처리

## 문제 해결

### APK가 표시되지 않음
- 저장소에 릴리스가 있는지 확인
- 릴리스에 `.apk` 파일이 있는지 확인
- 앱을 재시작해 보세요

### 저장소가 6개만 표시됨
- OAuth 스코프 문제일 수 있습니다
- 앱에서 로그아웃 후 다시 로그인하세요
- GitHub에서 앱 권한을 철회하고 다시 인증하세요

### 저장소 생성 시 403 오류
- 현재 공개 저장소만 생성 가능합니다
- 비공개 저장소는 GitHub 웹사이트에서 생성 후 앱에서 확인하세요

### 파일 업로드 실패
- 파일 크기 제한 확인 (GitHub는 100MB 제한)
- 인터넷 연결 확인
- 올바른 브랜치가 선택되었는지 확인

## 라이센스

이 프로젝트는 교육 목적입니다. 자유롭게 수정하고 사용하세요.

## 기여

기여를 환영합니다! 이슈나 Pull Request를 자유롭게 제출해 주세요.

## 스크린샷

(여기에 앱 스크린샷을 추가할 수 있습니다)

## 개발 환경

- Android Studio Arctic Fox 이상
- JDK 11 이상
- Gradle 7.0 이상
- Android SDK 24 (Android 7.0) 이상

## 빌드된 APK

릴리스 페이지에서 빌드된 APK를 다운로드할 수 있습니다.

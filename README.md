# 고요 리더 · Goyo Reader

광고 없이 내 파일을 읽는 개인용 Android 뷰어입니다. Kotlin과 Jetpack Compose로 만들었으며, EPUB 레이아웃은 Readium을 사용합니다.

## 현재 상태 · 0.1.2

- 확장자가 없는 JPEG·PNG·WEBP도 파일 제공자의 MIME 형식으로 인식합니다. 개별 파일 열기, 여러 이미지 묶기, 이미지 폴더 가져오기에 동일하게 적용하며, 저장할 때 읽기 엔진이 인식하는 확장자를 붙입니다.
- 자동 읽기가 책 끝에 도달하면 재생 상태도 종료합니다. 앞쪽으로 이동한 뒤 다시 시작할 수 있습니다. TXT·이미지·PDF의 페이지/스크롤과 EPUB에 종료 처리를 연결했습니다.

## 읽을 수 있는 파일

| 형식 | 읽기 기능 |
| --- | --- |
| TXT | 한국어 인코딩 감지/선택, 페이지·스크롤, 본문 검색, 글꼴/크기/줄 간격/여백, 문자 위치로 이어 읽기 |
| ZIP / CBZ | 압축 내부 JPG·JPEG·PNG·WEBP, 숫자 파일명 정렬, 좌우 읽기, 확대, 긴 이미지 연속 읽기 |
| JPG / JPEG / PNG / WEBP | 개별 이미지, 여러 이미지 또는 이미지 폴더를 한 권으로 가져오기 |
| PDF | 페이지·세로 연속 읽기, 확대 후 다시 렌더링, 페이지 이동, 이어 읽기 |
| EPUB | Readium의 EPUB 2/3 렌더링, 목차·본문 검색, Locator 위치 복원. 재조판형 EPUB의 페이지·스크롤과 서체/색상 설정 |

모든 형식에 책갈피, 진행률 이동, 페이지/위치 번호 입력, 볼륨키 조작, 좌우 터치 영역, 자동 읽기, 화면 밝기·추가 어둡기 설정을 연결했습니다. TXT와 재조판형 EPUB은 글자 설정을 바꿀 수 있습니다. 고정 레이아웃 EPUB·이미지·PDF의 인쇄된 글자는 재조판하지 않습니다.

## 사용 방법

1. **책 가져오기**에서 파일이나 폴더를 고릅니다. 파일은 앱 서재로 복사하며 원본을 변경하지 않습니다.
2. 이미지만 여러 장 선택하면 하나의 CBZ로 묶습니다. 폴더는 하위 폴더까지 확인하고, 문서 개수와 이미지 폴더 개수를 보여 준 뒤 가져옵니다.
3. 책을 열고 화면 가운데를 누르면 도구가 나타납니다. `어디든 다음` 모드에서도 하단 도구 버튼으로 메뉴를 열 수 있습니다.
4. 하단에서 검색, 목차, 책갈피, 자동 읽기, 설정을 사용합니다. 진행률 바나 페이지 번호를 눌러 이동합니다.
5. **세로 스크롤**로 바꾸면 TXT는 연속 본문, 만화는 웹툰식 긴 화면, PDF는 연속 페이지로 읽습니다.
6. 볼륨 아래 버튼은 기본적으로 다음으로 이동합니다. 설정에서 켜고 끄거나 방향을 바꿀 수 있습니다.

설정은 기기에 저장됩니다. 책갈피와 마지막 읽던 위치는 책마다 따로 저장하며, 앱 종료 때 최신 위치를 저장합니다. 서재에서 책을 지우면 가져온 사본과 그 책의 책갈피만 삭제합니다. 앱을 삭제하면 앱 안의 서재·설정도 삭제되며, 원본 파일은 남습니다.

## 범위

- Android 8.0(API 26) 이상.
- 계정, 광고 SDK, 분석 SDK, NAS, 서버, 클라우드 동기화가 없습니다. 앱은 `INTERNET` 권한을 요청하지 않습니다.
- EPUB의 페이지 표시에는 글꼴과 무관한 **Readium 위치 번호**를 사용합니다. 고정 레이아웃 EPUB은 발행된 고정 페이지 방식으로 읽습니다.
- EPUB에 포함된 본문·이미지를 읽으며, 외부 URL의 자료와 웹페이지는 불러오지 않습니다.
- PDF/만화 본문 OCR·검색, RAR/7z, DRM으로 잠긴 EPUB은 지원하지 않습니다.
- 압축 만화는 ZIP/CBZ 안에 이미지가 있는 형식을 사용합니다. TXT가 들어 있는 ZIP은 먼저 TXT를 꺼내 가져옵니다.
- 자동 읽기는 다른 화면이나 설정을 보는 동안 멈추며, 책 끝에서는 종료됩니다. 속도와 한 장당 읽는 시간은 설정에서 조정합니다.
- 직접 지정한 배경·글자 색과 가져온 TTF/OTF 글꼴을 사용할 수 있습니다.

## 개발 환경과 빌드

- JDK 21
- Android SDK Platform 37.0, Build Tools 37.0.0
- Android Gradle Plugin 9.3.1 / Gradle Wrapper 9.7.0
- Kotlin 2.4.20 / Compose 1.12.0 / Material 3 1.4.0
- Readium Kotlin Toolkit 3.4.0

최근 Android Studio에서 이 폴더를 프로젝트로 열고 SDK를 설치합니다. `local.properties`는 Android Studio가 로컬 SDK 경로에 맞춰 만듭니다. 외부 서비스 키나 계정 설정은 필요하지 않습니다.

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
```

Windows에서는 `gradlew.bat`를 사용합니다. 설치할 파일은 `app/build/outputs/apk/debug/app-debug.apk`입니다. 연결한 휴대전화에는 Android Studio의 Run 또는 아래 명령으로 설치할 수 있습니다.

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

다른 환경에서 새로 빌드한 APK로 기존 앱을 업데이트하려면 같은 서명 키를 사용해야 합니다. 전달받은 개발용 APK의 키와 로컬 Android Studio의 기본 디버그 키는 서로 다를 수 있습니다. 계속 사용할 APK는 Android Studio의 **Generate Signed App Bundle or APK**로 본인 키를 만들어 빌드하고 그 키를 보관하세요.

이번에 전달하는 0.1.2 APK는 직전 APK와 앱 ID·서명이 같고 버전 코드는 3입니다. 기존 앱을 삭제하지 않고 업데이트 설치할 수 있습니다.

## 코드 구조

```text
app/src/main/java/kr/goyo/reader/
  model/                 독서 위치, 책, 설정, 뷰어 명령
  data/                  파일 가져오기, 서재·책갈피·설정 저장
  reader/text/           텍스트 디코딩과 네이티브 페이지 계산
  reader/visual/         이미지·압축파일, 자연 정렬과 영역 렌더링
  reader/epub/           Readium 파싱, 목차·검색, Fragment 연결
  ui/                    서재, 공통 독서 도구, 설정
```

설정과 서재 메타데이터는 AndroidX DataStore에 저장합니다. TXT는 문자 위치, 이미지/PDF는 페이지와 내부 위치, EPUB는 Readium Locator 전체를 보관합니다. PDF는 Android의 `PdfRenderer`, 이미지는 `BitmapFactory`/`BitmapRegionDecoder`를 사용합니다.

## 검증

단위·Robolectric 테스트는 한글 인코딩, 이모지/문자 위치, 실제 `StaticLayout` 페이지 크기, 자연 정렬·이미지 분할, 서재 직렬화·원본 보존, 중복 파일명 가져오기, 실제 EPUB 목차·검색·Locator를 확인합니다.

0.1.2 검증 결과:

- 이번 수정의 영향 범위에서 자동 검증 18개가 통과했습니다: 서재·파일 가져오기 7개, 자동 읽기 4개, TXT 동작 4개, 이미지 뷰어 동작 2개, 실제 앱 독서 흐름 1개입니다.
- 수정 전 확장자 없는 이미지 가져오기 3개 경로와 자동 읽기 4개 경로의 실패를 재현했고, 수정 후 모두 통과했습니다. 이미지 검증은 실제 JPEG·PNG·WEBP 바이트와 `content://` 파일을 사용해 가져오기부터 읽기까지 확인합니다.
- 실제 앱 UI에서 TXT·만화의 페이지/스크롤 모드별로 책 끝에서 자동 읽기 상태가 종료되는지, 앞쪽으로 이동한 뒤 다시 시작할 수 있는지 확인했습니다. 기존 페이지·진행률·볼륨키 이동, 문자 위치 보존, 책갈피와 이어 읽기 검사도 통과했습니다.
- `assembleDebug`와 `lintDebug`가 통과했습니다. Lint는 오류 0개, 권고 경고 4개입니다. 경고는 인라인 상수의 API 표기, 테스트 라이브러리 업데이트, 새 백업 설정 형식, Kotlin 편의 함수 사용에 관한 것입니다. 줄바꿈 상수는 정수 0으로 인라인되며, 이를 받는 `setBreakStrategy()`는 API 23부터 지원됩니다.
- 생성된 APK의 v2 서명이 유효하며, 최소 SDK 26과 `INTERNET` 권한이 없는 것을 APK에서 확인했습니다.

EPUB 자동 읽기의 종료 판단은 Readium 3.4.0의 페이지 콜백·읽기 방향·고정 레이아웃 동작을 기준으로 코드 검토와 빌드를 확인했습니다. WebView에서의 실제 EPUB 표시·자동 읽기·검색 중 백그라운드 전환, 휴대전화의 제스처·볼륨키·밤 밝기·사용자 글꼴은 실기기 검증이 남아 있습니다. JVM/Robolectric 테스트는 물리 기기의 렌더링·메모리 검증을 대신하지 않습니다.

## 참고한 공식 구현

- [Jetpack Compose](https://developer.android.com/compose)
- [Readium Kotlin 3.4.0](https://readium.org/kotlin-toolkit/3.4.0/)
- [Android PdfRenderer](https://developer.android.com/reference/android/graphics/pdf/PdfRenderer)
- [juniversalchardet](https://github.com/albfernandez/juniversalchardet)

Readium과 문자 인코딩 감지기의 라이선스 안내는 `app/src/main/assets/licenses/`에 포함합니다. 그 외 직접 사용하는 AndroidX/Kotlin 라이브러리는 Apache 2.0 라이선스로 배포됩니다.

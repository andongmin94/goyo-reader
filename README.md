# 고요 리더 · Goyo Reader

광고 없이 내 파일을 읽는 개인용 Android 뷰어입니다. Kotlin과 Jetpack Compose로 만들었으며, EPUB 레이아웃은 Readium을 사용합니다.

## 현재 상태 · 0.1.1

- TXT·이미지·PDF의 페이지 진행률을 첫 쪽 0%, 마지막 쪽 100%로 통일했습니다. 한 쪽짜리 책은 100%이며, 진행률로 이동했을 때 표시와 목표 페이지가 일치합니다. 슬라이더를 같은 페이지 범위에 놓아도 실제 진행률을 표시합니다.
- TXT의 글꼴 변경, 검색·책갈피 위치 이동, 앱 일시 중단 뒤에도 정확한 문자와 줄 내 위치를 보존합니다. 실제 화면 스크롤이 발생했을 때만 새 읽기 위치를 계산합니다.
- 손가락 드래그가 페이지 이동을 중단해도 다음 볼륨키·쪽수·진행률 이동을 계속 처리합니다. 같은 취소 처리 원칙을 자동 읽기에도 적용했습니다.
- EPUB 검색은 책 화면의 수명에 연결했습니다. 앱이 잠시 백그라운드로 가도 검색이 계속되며, 책 화면을 닫으면 취소됩니다.

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
- 자동 읽기는 다른 화면이나 설정을 보는 동안 멈춥니다. 속도와 한 장당 읽는 시간은 설정에서 조정합니다.
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

이번에 전달하는 0.1.1 APK는 직전 0.1.0 APK와 앱 ID·서명이 같고 버전 코드는 2입니다. 기존 앱을 삭제하지 않고 업데이트 설치할 수 있습니다.

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

0.1.1 검증 결과 (2026-11-14):

- 이번 수정의 영향 범위에서 자동 검증 10개가 통과했습니다: 진행률 변환 3개, TXT 동작 4개, 이미지 뷰어 동작 2개, 실제 앱 독서 흐름 1개입니다.
- 수정 전 진행률·문자 위치·제스처 중단·슬라이더 표시의 실패 6건을 재현했고, 수정 후 해당 검증이 통과했습니다. 책 끝부분에서 글자를 줄일 때 스크롤 범위가 제한되는 경계 조건도 확인했습니다.
- 실제 앱 UI 흐름에서 TXT 가져오기 → 페이지·진행률 이동 → 볼륨키 이동·반복 입력 처리 → 이름 있는 책갈피 저장 → 서재 복귀 → 읽던 쪽 재개 → 책갈피 목록 표시를 확인했습니다.
- `assembleDebug`와 `lintDebug`가 통과했습니다. Lint는 오류 0개, 권고 경고 4개입니다. 경고는 인라인 상수의 API 표기, 테스트 라이브러리 업데이트, 새 백업 설정 형식, Kotlin 편의 함수 사용에 관한 것입니다. 줄바꿈 상수는 정수 0으로 인라인되며, 이를 받는 `setBreakStrategy()`는 API 23부터 지원됩니다.
- 생성된 APK의 v2 서명이 유효하며, 최소 SDK 26과 `INTERNET` 권한이 없는 것을 APK에서 확인했습니다.

실제 휴대전화의 제스처 감각, 볼륨키, 밤 밝기, WebView EPUB 표시와 사용자 글꼴은 기기에서 확인해야 합니다. EPUB 검색 중 백그라운드 전환·복귀 변경은 코드와 빌드로 확인했으며, 해당 흐름의 실기기 실행 검증은 남아 있습니다. JVM/Robolectric 테스트는 물리 기기의 렌더링·메모리 검증을 대신하지 않습니다.

## 참고한 공식 구현

- [Jetpack Compose](https://developer.android.com/compose)
- [Readium Kotlin 3.4.0](https://readium.org/kotlin-toolkit/3.4.0/)
- [Android PdfRenderer](https://developer.android.com/reference/android/graphics/pdf/PdfRenderer)
- [juniversalchardet](https://github.com/albfernandez/juniversalchardet)

Readium과 문자 인코딩 감지기의 라이선스 안내는 `app/src/main/assets/licenses/`에 포함합니다. 그 외 직접 사용하는 AndroidX/Kotlin 라이브러리는 Apache 2.0 라이선스로 배포됩니다.

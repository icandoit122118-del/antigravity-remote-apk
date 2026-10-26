# Antigravity Remote (코끼리 나이트 에디션 APK)

안그래비티(Antigravity) 코딩 에이전트를 스마트폰에서 원격 제어하고 실시간 토큰 스트리밍을 모니터링할 수 있는 안드로이드 컴패니언 앱(`com.antigravity.remote`)입니다.

WWIT **코끼리(Elephant)** 앱의 고요한 밤 감성과 Hallmark **`Ribbon (two-in-the-morning)`** 다크 세이지 테마를 결합하여 어두운 환경에서도 눈부심 없이 에이전트와 호흡할 수 있도록 설계했습니다.

---

## 주요 기능

- **코끼리(Elephant) 나이트 세이지 UI**:
  - 배경(`#11161B`), 20dp 라운드 표면 카드(`#1A2229`), 1dp 미세 보더(`#2A3742`), 세이지 민트 포인트(`#68C5B2`), 램프 골드(`#E8B96D`).
  - 본문 명도 대비 `13.8:1`(AAA), 보조 텍스트 `6.4:1`(AA), 모든 터치 요소 최소 `48dp × 48dp` 보장.
- **실시간 에이전트 스트리밍 & 플로팅 미니바**:
  - 접고 펼칠 수 있는 **생각 과정(`ThoughtProcessAccordion`)** 및 **실행됨 명령/작업(`ToolExecutionAccordion`)** 카드.
  - 코끼리 앱의 오디오 미니 플레이어에서 영감을 받은 **하단 플로팅 라이브 스트림 바**(`tok/s` 속도, 경과 시간 `tnum` 고정폭 숫자, 즉시 중지/스트림 제어).
- **PC 브릿지 서버 연동 & 무중단 오프라인 시뮬레이터**:
  - 동일 Wi-Fi / LAN에서 `pc-bridge-server.mjs` 실행 시 실시간 연동(`http://<PC_IP>:8765`).
  - PC 서버가 꺼져 있을 때는 앱 내장 코루틴 스트리밍 엔진으로 자동 폴백되어 단독 실행 가능.
- **4대 데이터 상태 및 Break-UI 스트레스 모드 내장**:
  - 상단 모드 스트립에서 `기본 데모`, `최악 데이터 (Break-UI)`, `로딩 상태`, `빈 화면`, `연결 오류`를 1탭으로 즉시 전환 및 검증 가능.

---

## APK 설치 및 실행 방법

1. 빌드된 APK 파일 경로:
   - `AntigravityRemote-debug.apk` (또는 `app/build/outputs/apk/debug/app-debug.apk`)
2. 스마트폰으로 APK 파일을 전송하여 설치하거나, USB 디버깅 연결 상태에서 아래 명령어로 설치합니다:
   ```powershell
   & "C:\Users\icand\.android\bin\android-cli.exe" install AntigravityRemote-debug.apk
   ```
3. (선택) PC와 실시간 연동을 원할 경우 프로젝트 폴더에서 브릿지 서버를 실행합니다:
   ```powershell
   node pc-bridge-server.mjs --port 8765
   ```
   이후 앱 우측 상단 연결 배지를 눌러 콘솔에 표시된 PC의 LAN IP(예: `192.168.0.x`)를 입력하고 **저장 및 연결 확인**을 누릅니다.

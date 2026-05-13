```text
Agent-Harness/
├── .github/workflows/       # (성현) GitHub Actions CI/CD 파이프라인
│
├── backend/                 # 🚀 (성욱) Spring Boot 백엔드 전용 구역
│   ├── src/main/java/...    # ProcessBuilder, SSE 통신 등 코어 로직
│   └── build.gradle         # (또는 pom.xml)
│
├── frontend/                # 🎨 (시연) 대시보드 UI 프론트엔드 구역
│   ├── index.html           # 메인 대시보드 뼈대
│   ├── style.css            # Tailwind CSS
│   └── app.js               # 실시간 로그 렌더링 JS 로직
│
├── infra/                   # 🐳 (성현/민준) 도커 및 DB 초기화 구역
│   ├── Dockerfile           # 통합 컨테이너(Java+Python) 환경
│   ├── docker-compose.yml   # 서버 및 DB 실행 세팅
│   └── init.sql             # [민준] DB 테이블 생성 및 초기 데이터 쿼리
│
├── scripts/                 # 🐍 (민준) 파이썬 헬퍼 스크립트 구역
│   ├── parse_regex.py       # 로그(토큰, 성공여부) 파싱 정규식
│   └── run_wrapper.py       # pytest 자동 실행 래퍼 스크립트
│
├── tests/                   # 🧪 (민준) TADD 검증 파이프라인 구역
│   └── test_agent.py        # AI가 통과해야 할 pytest 시나리오
│
├── mock/                    # 📦 (민준) 프론트 UI 개발용 더미 데이터
│   ├── dummy_response.json  # 테스트 종료 후 결과 데이터
│   └── dummy_stream.json    # 터미널 실시간 스트림 데이터
│
└── README.md                # 📝 (민준) 프로젝트 전체 명세서 및 가이드
```
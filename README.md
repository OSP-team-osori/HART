# 🚀 Agent-Harness
> **TADD(Test + AI Driven Development) 기반 AI 에이전트 신뢰도 검증 파이프라인**

[![Java](https://img.shields.io/badge/Java-17-007396?logo=java&logoColor=white)](https://www.oracle.com/java/)
[![SpringBoot](https://img.shields.io/badge/SpringBoot-3.2.0-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-Latest-2496ED?logo=docker&logoColor=white)](https://www.docker.com/)
[![Python](https://img.shields.io/badge/Python-3.10-3776AB?logo=python&logoColor=white)](https://www.python.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

---

## 💡 Project Identity
**Agent-Harness**는 AI 코딩 에이전트의 산출물을 인간의 개입 없이 기계적으로 즉각 검증하는 **'자동화된 관제 및 평가 플랫폼'**입니다. 

AI가 작성한 코드를 맹신하지 않는 **Zero-Trust AI** 원칙에 따라, 개발자가 정의한 테스트 코드(RED)를 통과해야만 유효한 코드로 인정하는 **TADD(Test + AI Driven Development)** 파이프라인을 구축합니다.

---

## ✨ Key Functionalities

### 1. 실시간 프로세스 관제 (Real-time Monitoring)
- **SSE(Server-Sent Events) 기반 스트리밍**: `java.lang.ProcessBuilder`를 통해 실행되는 AI 프로세스의 Stdout/Stderr를 지연 없이 대시보드에 중계합니다.
- **가상 터미널 UI**: 사용자는 웹 UI에서 AI가 고민하고 코딩하는 과정을 실시간으로 모니터링할 수 있습니다.

### 2. TADD 자동 검증 파이프라인 (Automated Harness)
- **이벤트 기반 자동 실행**: AI 에이전트가 코드 작성을 마치고 종료(Exit Code 0)되는 즉시, 서버가 격리된 환경에서 `pytest` 등 단위 테스트를 자동 실행합니다.
- **환경 격리**: 도커(Docker) 컨테이너 내 `Volume Mount` 구조를 통해 호스트 환경 오염 없이 안전하게 테스트를 수행합니다.

### 3. 데이터 기반 신뢰도 지표 (Reliability Scoring)
- **평가 지표 수집**: 테스트 통과율(Pass/Fail), 수행 시간, API 토큰 소비량 데이터를 통합 수집합니다.
- **스코어링 시스템**: 수집된 데이터를 바탕으로 에이전트의 신뢰도를 점수화하여 대시보드에 시각화합니다.

---

## 🏗 System Architecture



- **Backend**: Spring Boot (Process Orchestration, SSE Emitter)
- **Frontend**: Vanilla JS & TailwindCSS (Real-time Log Rendering, Chart.js)
- **Infrastructure**: Unified Docker Image (Java + Node.js + Python + AI CLI)

---

## 🛠 Tech Stack

### 🖥 Backend & DB
- **Language**: Java 17
- **Framework**: Spring Boot 3.x, Spring Data JPA
- **Database**: PostgreSQL 15
- **Engine**: java.lang.ProcessBuilder (Process Lifecycle Management)

### 🎨 Frontend
- **Styling**: TailwindCSS
- **Real-time**: EventSource (SSE)
- **Visualization**: Chart.js 4.x
- **Framework-less**: 성능 최적화 및 SSE 연동 집중을 위해 Vanilla JS 사용

### 🐳 DevOps & Tools
- **Container**: Docker (Unified Runner)
- **CI/CD**: GitHub Actions
- **AI Agent**: Aider (Gemini 1.5/2.0 Pro 모델 연동)

---

## 📁 Project Structure
```text
Agent-Harness/
├── backend/          # Spring Boot 오케스트레이터 및 API 서버
├── frontend/         # 실시간 관제 대시보드 UI
├── infra/            # Dockerfile, docker-compose 및 DB init.sql
├── mock/             # UI/UX 테스트용 Mock JSON 데이터
├── docs/             # 요구사항 명세, WBS 및 시스템 아키텍처 문서
├── scripts/          # 로그 파싱 정규식 및 테스트 래퍼 스크립트
├── tests/            # TADD 검증용 단위 테스트 시나리오
└── CONTRIBUTING.md   # 팀 협업 규칙 및 개발 가이드라인
```

---

## 🤝 Team Osori (HART)
| 박민준 (PM) | 장성욱 (BE) | 김시연 (FE) | 김성현 (Infra) |
| :---: | :---: | :---: | :---: |
| 아키텍처 설계 & WBS | 프로세스 제어 엔진 | 실시간 UI & 시각화 | 도커 & CI/CD 구축 |

---

## 📝 Documentation
- [프로젝트 상세 개요](./docs/project-overview.md)
- [시스템 요구사항 및 아키텍처](./docs/project-requirements.md)
- [개발 일정 및 WBS](./docs/project-plan-and-wbs.md)
- [기여 가이드라인 (Git 전략)](./CONTRIBUTING.md)
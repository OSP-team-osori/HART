# 🤝 Team HART 프로젝트 기여 가이드 (Contributing Guide)

본 문서는 Team HART의 효율적인 협업을 위한 표준 개발 전략을 정의합니다. 모든 팀원은 작업 전 이 가이드를 숙지해 주시기 바랍니다.

## 🌿 1. 브랜치 전략 (Branching Strategy)
우리 팀은 안정적인 제품 배포를 위해 **Git Flow** 모델을 지향합니다.
* **`main`**: 오직 `dev`에서 안정성이 검증된 프로덕트만 이전됩니다. (사용자 배포 전용)
* **`dev`**: **기본 브랜치(Base Branch)**입니다. 모든 작업의 베이스이자 통합 구역입니다.
* **개발 브랜치**: 작업 성격에 따라 아래 접두사를 사용해 생성합니다.
  - `feat/이슈번호-설명` : 기능 개발
  - `bug/이슈번호-설명` : 버그 수정
  - `docs/이슈번호-설명` : 문서 작업
  - `chore/이슈번호-설명` : 설정 및 라이브러리 관리

## 📝 2. 이슈 및 커밋 컨벤션 (Conventions)
이슈 제목과 커밋 메시지는 동일한 접두사(Prefix) 규칙을 따릅니다. 콜론(` : `) 앞뒤 공백을 지켜주세요.
- `feat : ` 새로운 기능 개발
- `bug : ` 오류 수정
- `docs : ` 문서 작성 및 수정
- `chore : ` 빌드 업무, 패키지 설정 등
- `question : ` 코드/아키텍처 관련 질의

> 💡 **INFRA 가이드:** 규칙 준수가 원칙이나, 이슈 제목을 한글로 작성하시면 INFRA 담당이 추후 수정하겠습니다. 브랜치 이름은 정 힘들면 `feat/이슈번호-설명`으로만 만드셔도 무방합니다.

## 🚀 3. 전체 작업 프로세스 (Workflow)
1. **이슈 생성**: 작업 전 반드시 [Issues] 탭에서 이슈를 먼저 생성합니다.
2. **로컬 최신화**: 작업 전 `dev` 브랜치를 반드시 최신화하여 충돌을 방지합니다.
   `git checkout dev` -> `git pull origin dev`
3. **브랜치 생성**: 반드시 `dev` 브랜치로부터 분기해야 합니다.
   `git switch -c 브랜치명 dev`
4. **작업 및 푸시**: 작업 완료 후 자신의 브랜치에 푸시합니다.
   `git add .` -> `git commit -m "컨벤션 : 작업내용"` -> `git push -u origin 브랜치명`
5. **PR 생성 및 리뷰**: `dev`를 대상(Base)으로 PR을 생성합니다. **최소 1명 이상의 승인(Approve)**이 필수입니다.
6. **머지 및 자동 종료**: 승인 후 머지합니다. PR 본문에 아래 키워드를 사용하면 이슈가 자동 종료됩니다.
   - `Resolves #번호`, `Closes #번호`, `Fixes #번호`

## 🔗 4. 참고 레포지토리
- [Toss Slash Issues](https://github.com/toss/slash/issues)
- [Angular Pull Requests](https://github.com/angular/angular/pulls)

---
**문서 관리자:** 박민준 (PM) | **인프라 규칙 설계:** 김성현
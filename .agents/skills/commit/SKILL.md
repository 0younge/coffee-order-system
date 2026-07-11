---
name: commit
description: 커밋을 요청받거나 작업 흐름상 변경을 커밋하게 되면 사용한다. 변경 검토, 스테이징, 한국어 커밋 메시지 작성, gh CLI 기반 push 절차.
---

# 커밋

## 절차

0. 훅 활성화 확인: `git config core.hooksPath` 값이 `.githooks`가 아니면
   `git config core.hooksPath .githooks`를 실행한다 (훅 정본은 `.githooks/`, 클론마다 1회 필요).
1. `git status` + `git diff`로 변경 전체를 확인한다. 요청과 무관한 변경이 섞여 있으면 보고 후 분리한다.
2. 관련 파일만 스테이징한다. `git add -A`는 변경 전체가 한 단위일 때만.
3. 스테이징 전에 시크릿(.env, 키, 토큰) 포함 여부를 확인한다.
4. `test` 스킬의 완료 기준을 통과한 상태에서만 커밋한다.
5. 커밋 후 훅이 실패하면 원인을 보고한다. `--no-verify` 금지.

## 메시지 형식

- 제목: `<타입>: <한국어 요약>` (50자 이내) — 예: `feat: 로그인 기능 추가`
- 타입: feat / fix / refactor / docs / test / chore
- 본문(선택): "왜" 바꿨는지만 한국어로. 무엇을 바꿨는지는 diff가 보여준다.

## 푸시

- 커밋 후 push는 원격·브랜치 확인 뒤 `git push` (최초는 `git push -u origin <브랜치>`).
- GitHub 인증은 **gh CLI 기준**: push 인증 실패 시 `gh auth status` 확인 → 미인증이면
  재시도하지 말고 `gh auth login`(또는 `gh auth setup-git`)을 사용자에게 요청한다.
- gh조차 없는 샌드박스 환경이면 1회 시도 후 중단하고, 사용자가 로컬에서 실행할
  정확한 push 명령을 전달한다.

## 금지

- 사용자 요청 없이 `--amend`, rebase, force push 금지.
- 서로 다른 목적의 변경을 한 커밋에 섞지 않는다. 나누어 커밋한다.

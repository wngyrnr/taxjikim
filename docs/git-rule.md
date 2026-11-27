# Git 형상관리 규칙

혼자 개발하는 개인 저장소(`wngyrnr/taxjikim`) 기준이다. 하루 작업을 주제 브랜치에 모아 `develop` 대상 PR로
반영한다. PR 작성·머지 절차는 [PR 가이드](pr-guide.md)를 따른다.

> 이 저장소는 팀 프로젝트에서 시작했다. 히스토리에 남아 있는 `pr/{개인명}/TJK-{번호}` 브랜치와 TJK 번호가
> 붙은 커밋은 그 시절 기록이며, 지금 규칙과 다르다. 과거 이력은 그대로 두고 새 작업만 아래 규칙을 따른다.

## 1. 브랜치

| 브랜치 | 용도 |
| --- | --- |
| `develop` | 기본 브랜치. 모든 작업이 PR로 모이는 곳 |
| `main` | 동작이 확인된 안정 버전. 필요할 때만 `develop` → `main` PR로 갱신 |
| `fix/{주제}`, `feat/{주제}`, `docs/{주제}`, `chore/{주제}` | 그날 작업용 브랜치. 머지 후 삭제 |
| `work/{YYYY-MM-DD}` | 하루에 서로 무관한 작업이 섞여 주제를 못 정할 때만 |

- 브랜치 접두사는 커밋 타입과 같은 체계를 쓴다. 주제는 영문 소문자와 하이픈으로 적는다
  (예: `fix/chat-message-delivery`).
- 브랜치는 최신 `develop`에서 만든다.

```
git switch develop
git pull --ff-only origin develop
git switch -c fix/chat-message-delivery
```

- `develop`과 `main`에 force push하지 않는다.
- 사소한 변경(오타 수정, 설정값 한 줄 등)은 `develop`에 직접 커밋해도 된다. 그 외에는 브랜치를 거친다.

## 2. 커밋

커밋 형식: `타입(영역): 작업 내용`

| 타입 | 용도 |
| --- | --- |
| `feat` | 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 동작을 유지하는 구조 개선 |
| `test` | 테스트 추가·수정 |
| `docs` | 문서 추가·수정 |
| `chore` | 설정·빌드 등 기타 작업 |

```
fix(chat): 종료된 상담의 안읽은 메시지를 채팅 위젯에서 볼 수 있도록 수정
docs(chat): 채팅 기능 점검 기록 추가
```

- 티켓 번호(`[TJK-xxx]`)는 쓰지 않는다. 팀 티켓 시스템을 더 이상 쓰지 않는다.
- **서로 관련 없는 변경은 커밋을 나눈다.** 하루 PR 안에 여러 커밋이 들어가도 되지만, 커밋 하나에 무관한
  변경을 섞지 않는다. 되돌릴 때와 PR 본문을 쓸 때 커밋 단위가 그대로 쓰인다.
- 본문을 쓸 때는 **제목 다음에 반드시 빈 줄을 넣는다.** 빈 줄이 없으면 git이 전체를 한 문단으로 보고
  제목으로 합쳐 버려서 `git log --oneline`과 GitHub 목록이 깨진다. 본문 줄 앞에 공백을 넣지 않는다.

```
fix(chat): 제목 한 줄

여기부터 본문. 무엇을 왜 바꿨는지와 어떻게 확인했는지를 적는다.

- 변경 항목
- 변경 항목
```

- 터미널에서 긴 본문을 쓸 때는 `git commit`(에디터)이나 `git commit -F 파일경로`를 쓴다.
  `git commit -m "제목" -m "본문"`도 `-m` 사이에 빈 줄을 넣어 준다.

## 3. 반영 절차

```
최신 develop에서 브랜치 생성 → 작업·커밋 → push → PR 생성(본문 작성) → 셀프 머지
→ develop 최신화 → 작업 기록(Notion)에 PR 링크 남기기
```

- 하루 작업은 **PR 하나**로 모은다. 상세 내용은 PR 본문에 적는다([PR 가이드](pr-guide.md) 참고).
- 승인자가 없으므로 본인이 머지한다. `develop`에 브랜치 보호 규칙은 두지 않는다.
- **Merge commit 방식으로 머지한다. Squash merge와 Rebase merge는 사용하지 않는다** — 개별 커밋 이력을 남긴다.
- 머지 후 로컬을 최신화한다.

```
git switch develop
git pull --ff-only origin develop
```

## 4. 브랜치 정리

- 작업 브랜치는 머지 후 삭제한다(`gh pr merge --merge --delete-branch`가 원격 브랜치까지 정리한다).
- 로컬에 남은 브랜치는 `git branch -d {브랜치}`로 지운다. 삭제가 거부되면 미반영 커밋이 없는지 먼저 확인하고,
  강제 삭제하지 않는다.
- `develop`, `main`은 삭제하지 않는다.

## 5. 되돌리기

- 이미 push한 커밋은 `git revert`로 되돌린다. `main`·`develop`에 force push하지 않는다.
- push 전이라면 `git rebase -i`나 `git commit --amend`로 정리해도 된다. 작업 전에
  `git branch backup/{이름}`으로 되돌릴 지점을 만들어 두면 안전하다.

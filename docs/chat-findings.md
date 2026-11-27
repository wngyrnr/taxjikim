# 채팅 기능 점검 기록

1:1 상담 채팅(회원 위젯 `ChatWidget` + 관리자 `AdminChatsPage` + WebSocket)을 로컬에서 직접 실행해 점검한 결과다.
조사 2026-11-25, 수정 2026-11-27. 챗봇(`ChatbotPage`)은 대상이 아니다.

## 요약

| 항목 | 상태 |
| --- | --- |
| 1. 종료된 상담의 마지막 메시지를 회원 위젯에서 볼 수 없음 | 해결 (`a3f59c2`) |
| 1-B. 관리자 REST 폴백 전송이 실시간으로 안 가고 읽음 플래그가 덮임 | 해결 (`e90bdab`) |
| 2. 회원이 취소한 대기방 입장 시 잘못된 안내 | **남음** |
| 3. 종료된 방에도 WebSocket 핸드셰이크 성공 | **남음** |
| 4. `ChatMessageDto.isRead`가 JSON에서 `read`로 나감 | **남음** |
| 5. 첫 연결 중에도 "연결이 끊어졌습니다" 경고 | **남음** |
| 6. 관리자 목록 폴링이 방 수만큼 요청을 더 보냄 | **남음** |
| 7. 그 외 작은 것들 | **남음** |

## 실행 환경과 재현 방법

- 백엔드: Spring Boot 4 / Java 17, 로컬 MySQL(`taxjikim`) + 로컬 Redis, 포트 8080

  ```bash
  cd back
  DB_URL="jdbc:mysql://127.0.0.1:3306/taxjikim?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true" \
    DB_USERNAME=root DB_PASSWORD= sh gradlew bootRun
  ```

  `gradlew`에 실행 권한이 없어(Windows 환경에서 커밋됨) `sh gradlew`로 실행한다. 파일 모드는 바꾸지 않았다.
  팀 DB(192.168.10.16:3306)는 연결되지 않아 로컬 DB를 썼다. 새 로컬 DB는 `back/local/schema.sql` 참고.
- 프런트: `cd front && npm run dev` (5173). `/api`·`/ws`는 8080으로 프록시된다.
- 점검 방법: REST와 WebSocket을 프런트와 같은 경로·형식으로 직접 호출(`ws://localhost:8080/ws/chat/{roomId}`,
  쿠키 인증)하고, 회원 위젯은 브라우저에서 직접 확인했다. `npm run lint`·`npm run build`도 돌렸다.
- 테스트 계정은 로컬 DB에 임시로 넣고 점검 후 삭제했다. 재현할 때는 회원 1명·관리자 1명이 필요하다.

## 정상 동작 확인

| 항목 | 결과 |
| --- | --- |
| 첫 문의 → 대기방 자동 생성 | OK (`POST /chat/messages` → WAITING 방 생성) |
| 관리자 대기목록 조회 / 입장 | OK (WAITING → IN_PROGRESS, adminId 배정) |
| 양방향 실시간 송수신 | OK |
| 접속 시점 즉시 읽음처리 (`9281f10`) | OK — 관리자 미접속 중 쌓인 메시지가 접속 직후 `IS_READ=1` |
| 상담 종료 실시간 전달 | OK (`roomClosed:true` 프레임) |
| 종료된 방 전송 차단 | OK (`CHAT_ROOM_CLOSED`) |
| 하트비트 | OK (`ping` → `pong`) |
| 입력 검증 | OK (빈 내용 `VALIDATION_ERROR`, 깨진 JSON `INVALID_PAYLOAD`) |
| 핸드셰이크 인가 | OK — 비로그인 401 / 남의 방 403 / 없는 방 404 |
| 커서 페이징 | OK (`hasMore`, `nextCursor`) |
| lint / build | 통과 (에러 없음, 경고만) |

## 해결된 항목

### 1. 종료된 상담의 마지막 메시지를 회원이 채팅 위젯에서 볼 수 없었다 — `a3f59c2`

`GET /chat/rooms/me`는 열린 방(WAITING/IN_PROGRESS)만 돌려준다. 그래서 회원이 패널을 닫아 둔 사이에 상담이
종료되면 `room=null` → `roomIdForMessages=null` → `useRoomMessages`가 메시지를 비웠다. 배지를 눌러 패널을
열어도 "궁금한 점을 남겨 주세요"만 보이고 관리자의 마지막 답변과 종료 안내가 사라졌다. 종료 후 새로고침하면
배지조차 0이라 끝까지 알 수 없었다. 당시 확인 경로는 마이페이지 > 상담 내역뿐이었다.

수정은 `front/src/stores/useChatStore.js`의 `useMemberChat`에만 들어갔다. 열린 방이 없으면 종료 상담 최신
1건을 보고, 안읽은 메시지가 남아 있을 때만 표시 대상으로 잡은 뒤 그 자리에서 읽음 처리한다. 배지 기준도 그
방을 포함하도록 넓혔다. 패널을 닫으면 잊어버려서 다음에 열면 새 상담 화면으로 돌아간다.

브라우저 검증: 배지 2 → 패널 열면 답변·종료 안내 표시 → `IS_READ` 반영 → 배지 0 → 닫았다 열면 새 상담 화면.

### 1-B. 관리자의 REST 폴백 전송이 실시간으로 전달되지 않고 읽음 플래그가 덮였다 — `e90bdab`

`useRoomMessages.send`는 소켓이 `OPEN`이 아니면 REST로 폴백한다(`useChatSocket.js`의 `sendText`가 false).
입장 직후 핸드셰이크 전, 재연결 백오프 대기(1·2·4·8·16초, 최대 5회), 재연결 포기 후, 선택한 방이 바뀌는 순간이
해당된다. 회원 위젯은 끊김 중 보내기 버튼이 막혀 있어, 실질적으로 관리자 경로다.

그때 두 가지 문제가 있었다.

1. REST 전송 경로(`ChatApiController`·`AdminChatApiController`)는 `chatService.sendMessage`만 호출하고
   소켓 푸시(`deliverToCounterpart`)를 거치지 않았다. 회원 패널이 열려 있어도 화면에 뜨지 않았다.
   (패널이 닫혀 있으면 SSE `chat-unread`로 배지가 올라가 다음에 열 때 보였다.)
2. 이어서 소켓으로 다른 메시지가 전달되면 `markMessagesAsRead(roomId, readerId)`가 메시지 범위 없이 방 전체를
   읽음 처리해, 회원이 못 본 메시지까지 `IS_READ=1`이 됐다. 배지로 알 기회까지 사라진다.

수정은 전송 경로를 하나로 합쳤다. `ChatMessageSentEvent`를 추가하고 `ChatServiceImpl.sendMessage`가 커밋 후
발행하면 `ChatWebSocketHandler`가 전달한다. 소켓 핸들러의 직접 전달 호출은 제거했다(중복 전달 방지). 그리고
전달에 성공한 메시지 1건만 읽음 처리하도록 `markMessageAsRead`를 추가했다. 접속 시점의 backlog 일괄 처리는
그대로 뒀다 — 그 시점에는 클라이언트가 메시지를 전체 재조회하므로 방 전체를 읽음 처리하는 게 맞다.

**이 구조에서 주의할 점**

- `@TransactionalEventListener(AFTER_COMMIT)`는 트랜잭션이 없으면 이벤트를 조용히 버린다. 지금
  `sendMessage` 호출 경로는 모두 `@Transactional`이지만(`insertCloseNotice`도 같은 구조), 트랜잭션 밖에서
  호출하는 곳이 생기면 그 메시지는 전달되지 않는다.
- 전달이 커밋 이후 리스너로 옮겨가서, 전달 중 생긴 예외는 보낸 사람에게 `ERROR` 프레임으로 돌아가지 않는다
  (`handleTextMessage`의 try/catch 밖). 로그로만 남는다 — 의도한 절충이다. 저장 실패 에러는 그대로 돌아간다.

## 남은 항목

### 2. 회원이 취소한 대기방에 관리자가 입장하면 잘못된 안내가 뜬다 (런타임 확인)

```
회원이 WAITING 방 취소 → 관리자 입장 시도 → 409 ROOM_ALREADY_CLAIMED
서버 메시지: "이미 다른 상담으로 처리 중이거나 대기 상태가 아닙니다."
프런트 표시: "이미 다른 관리자가 입장한 상담방입니다."   ← 사실과 다름
```

`useChatStore.js`의 `useAdminChat.open`에서 `ROOM_ALREADY_CLAIMED`를 "다른 관리자가 입장"으로 단정한다.
목록은 8초 폴링이라 취소된 방이 화면에 남아 있는 시간이 있어 자주 밟을 수 있다.
고치는 방향: 서버 메시지를 그대로 쓰거나, 서버가 현재 상태를 보고 코드를 분리한다(예: `ROOM_NOT_WAITING`).

### 3. 종료된(CLOSED) 방에도 WebSocket 핸드셰이크가 성공한다 (런타임 확인)

`ChatHandshakeInterceptor`는 방 존재와 소유자만 보고 `STATUS`는 확인하지 않는다. CLOSED 방에 연결이 열리고
`afterConnectionEstablished`의 읽음 처리까지 실행된다. 프런트는 CLOSED 방에 연결하지 않아 화면 문제는 없지만
API 레벨에서는 열려 있다.

### 4. `ChatMessageDto.isRead`가 JSON에서 `read`로 나간다 (런타임 확인)

응답 실측: `{"content":"...","messageId":1344,"read":false,...}`. 반면 프런트의 낙관적 갱신 메시지
(`useChatStore.js`의 `useRoomMessages.send`)는 `isRead: false`로 만든다 → 키가 다르다. 지금은 이 값을 읽는
곳이 없어 증상이 없지만, 읽음 표시를 붙이는 순간 어긋난다.
고치는 방향: `@JsonProperty("isRead")`로 맞추거나, 쓰지 않는 필드를 양쪽에서 제거한다.

### 5. 첫 연결 중에도 "연결이 끊어졌습니다" 경고가 뜬다 (코드 기준, 실행으로는 미확인)

`ChatWidget.jsx`의 `disconnected = hasActiveRoom && !connected`는 패널을 연 순간부터 소켓이 열릴 때까지
true다 → 그 사이 빨간 경고가 깜빡인다. 같은 상태를 관리자 화면은 "연결 중입니다…"로 표시한다.
고치는 방향: 최초 연결과 끊김을 구분한다(한 번 연결된 뒤에만 경고).

### 6. 관리자 목록 폴링이 방 수만큼 요청을 더 보낸다 (코드 기준)

`useChatStore.js`의 `useAdminChat` — 8초마다 목록을 새로 받고, 그 결과로 `unreadByRoom`을 방마다 1건씩
조회한다. 담당 방 10개면 8초마다 11개 요청. 서버에 합계 API(`GET /api/v1/admin/chat/unread-count`)가 있지만
프런트는 호출하지 않는다(`api/chat.js`에 정의가 없다). 합계라 방별 배지를 대체하진 못하므로, 방별 안읽은
수를 한 번에 주는 엔드포인트가 필요하다.

### 7. 그 외 작은 것들

- 관리자 화면은 연결이 끊긴 동안에도 보내기를 막지 않고, 훅이 주는 `messagesError`도 표시하지 않는다
  (`AdminChatsPage.jsx`). 관리자가 전송 실패를 알 방법이 없다.
- `useChatSocket.js`: 렌더 중에 ref를 바꿔 oxlint가 `react(refs)` 경고를 낸다. 할당을 effect로 옮기는 게 맞다.
- 회원 입력창은 `maxLength={500}`인데 서버 한도는 1000자, 관리자 입력창은 1000이다.

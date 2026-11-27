package com.kh.back.chat.event;

import com.kh.back.chat.dto.ChatMessageDto;

// 메시지 저장 트랜잭션이 커밋된 뒤, 보낸 사람이 아닌 상대방에게 실시간으로 전달하기 위한 이벤트.
// REST(POST /messages)와 WebSocket 어느 쪽으로 보냈든 저장은 ChatServiceImpl.sendMessage를 거치므로,
// 전달도 이 이벤트 한 곳으로 모은다 — 예전에는 WebSocket으로 보낸 메시지만 전달돼서, 소켓이 아직
// 연결되기 전이나 재연결 대기 중에 REST로 보낸 메시지는 상대 화면에 실시간으로 뜨지 않았다.
// ChatServiceImpl(업무 규칙)과 ChatWebSocketHandler(실시간 전송)의 순환 의존을 피하려고 이벤트를 쓴다.
// 저장이 실제로 커밋된 뒤에만 전달해야 상대가 다시 조회했을 때 그 메시지가 보인다(AFTER_COMMIT).
public record ChatMessageSentEvent(Long roomId, Long senderId, ChatMessageDto message) {
}

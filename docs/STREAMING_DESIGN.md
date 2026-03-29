# 스트리밍 응답 설계

## 배경

대화 생성 API에서 `isStreaming=true` 시 실시간으로 LLM 응답을 전달해야 한다. 일반 응답과 스트리밍 응답은 HTTP 프로토콜 수준에서 근본적으로 다르다.

- 일반: `Content-Type: application/json` — 완성된 JSON 한 번에 반환
- 스트리밍: `Content-Type: text/event-stream` — SSE(Server-Sent Events)로 청크 단위 전송

## 설계 고민: 분기를 어디서 할 것인가

### 선택지

| 위치 | 방식 | 문제점 |
|------|------|--------|
| Controller | `if (isStreaming)` 분기 후 다른 Service 메서드 호출 | Controller에 비즈니스 판단이 들어감 |
| Service | 내부에서 분기, 두 개의 메서드로 분리 | Controller와 Service 양쪽에 분기 존재 |
| **Service + sealed class** | Service가 분기하고 결과 타입으로 표현 | Controller는 매핑만 수행 (채택) |

### 결정: sealed class 기반 결과 반환

```kotlin
sealed class ChatResult {
    data class Complete(val response: ChatResponse) : ChatResult()
    data class Stream(val chunks: Flux<String>) : ChatResult()
}
```

**Service**가 `isStreaming`을 판단하는 유일한 곳이 되고, Controller는 결과 타입에 따라 HTTP 응답만 매핑한다.

```kotlin
// Controller — 비즈니스 로직 없음
return when (val result = chatService.createChat(userId, request)) {
    is ChatResult.Complete -> ResponseEntity.status(201).body(result.response)
    is ChatResult.Stream -> ResponseEntity.ok()
        .contentType(MediaType.TEXT_EVENT_STREAM)
        .body(result.chunks)
}
```

## 왜 DTO 통합을 하지 않았는가

하나의 DTO로 일반/스트리밍 응답을 모두 표현하는 방안도 검토했으나, 두 응답은 HTTP 프로토콜 수준에서 다르기 때문에 억지로 합치면 오히려 불명확해진다. sealed class는 "결과가 둘 중 하나"라는 의미를 타입 시스템으로 명확히 표현한다.

## 구현 흐름

```
Client 요청 (isStreaming=true)
  → Controller: chatService.createChat()
    → Service: isStreaming 판단 → claudeClient.callStream()
      → ClaudeClient: Claude API에 "stream": true로 요청
        → SSE 이벤트 수신 → content_block_delta에서 텍스트 추출
      ← Flux<String> 반환
    ← ChatResult.Stream(chunks) 반환
  → Controller: when 매핑 → TEXT_EVENT_STREAM 응답
← SSE 스트림 전송

스트리밍 완료 시 (doOnComplete):
  → 전체 답변을 StringBuilder로 조합하여 DB에 저장
```

## 트레이드오프

- 스트리밍 시 `@Transactional` 범위 밖에서 DB 저장이 일어남 (`doOnComplete` 콜백). 현재 규모에서는 문제 없으나, 저장 실패 시 응답은 전달됐지만 DB에는 없는 상태가 발생할 수 있음.
- 향후 필요 시 `doOnComplete`에서 별도 트랜잭션으로 저장하거나, 실패 시 재시도 로직을 추가할 수 있음.

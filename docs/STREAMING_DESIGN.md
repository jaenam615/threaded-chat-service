# 스트리밍 응답 설계

## 배경

대화 생성 API에서 `isStreaming=true` 시 실시간으로 LLM 응답 전달 필요
일반 응답과 스트리밍 응답은 HTTP 프로토콜 수준에서 근본적으로 다르다

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

**Service**가 `isStreaming`을 판단하는 유일한 곳이 되고, Controller는 결과 타입에 따라 HTTP 응답만 매핑

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

하나의 DTO로 일반/스트리밍 응답을 모두 표현하는 방안도 검토했으나, 두 응답은 HTTP 프로토콜 수준에서 다르기 때문에 억지로 합치면 오히려 헷갈림
sealed class는 "결과가 둘 중 하나"라는 의미를 타입 시스템으로 명확히 표현

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

## @Transactional + 스트리밍 충돌 해결

### 문제

초기 구현에서 `createChat()` 전체에 `@Transactional`이 걸려있었다.

```kotlin
@Transactional
fun createChat(userId: Long, request: ChatCreateRequest): ChatResult {
    // ...
    if (request.isStreaming) {
        val chunks = claudeClient.callStream(messages)
            .doOnComplete {
                chatRepository.save(...)  // 여기서 DB 저장
            }
        return ChatResult.Stream(chunks)  // Flux를 즉시 반환
    }
}
```

`@Transactional`은 메서드 **리턴 시** 트랜잭션을 닫는데, `Flux`는 **lazy**하므로 리턴 시점에 아직 실행되지 않았다. 트랜잭션이 닫힌 후 `doOnComplete`에서 DB 저장을 시도하게 되는 구조적 문제.

```
1. createChat() 진입 → 트랜잭션 시작
2. Flux 객체 생성 (아직 실행 안 됨)
3. ChatResult.Stream(chunks) 반환 → 트랜잭션 커밋/종료
4. Controller가 Flux를 구독 → 스트리밍 시작
5. doOnComplete → chatRepository.save() 호출 → 트랜잭션 없음!
```

### 해결

트랜잭션 경계를 분리했다. `createChat()`에서 `@Transactional`을 제거하고, DB 접근이 필요한 부분만 별도 `@Transactional` 메서드로 추출.

```kotlin
// 트랜잭션 없음 — 오케스트레이션만 담당
fun createChat(userId: Long, request: ChatCreateRequest): ChatResult {
    val prepared = prepareChat(userId, request)  // @Transactional

    if (request.isStreaming) {
        val chunks = claudeClient.callStream(prepared.messages)
            .doOnComplete {
                saveChat(thread, question, answer)  // @Transactional
            }
        return ChatResult.Stream(chunks)
    }

    val answer = claudeClient.call(prepared.messages)
    val chat = saveChat(thread, question, answer)  // @Transactional
    return ChatResult.Complete(...)
}

@Transactional
fun prepareChat(...): PreparedChat { ... }  // 유저 조회 + 스레드 결정 + 이전 대화 조회

@Transactional
fun saveChat(...): ChatEntity { ... }  // DB 저장만
```

`doOnComplete`에서 `saveChat()`을 호출하면 Spring 프록시가 새로운 트랜잭션을 열어서 정상적으로 저장된다.

### 변경 전후 비교

| | 변경 전 | 변경 후 |
|---|---|---|
| 트랜잭션 범위 | 메서드 전체 (LLM 호출 포함) | DB 접근 부분만 |
| 스트리밍 시 DB 저장 | 트랜잭션 없이 실행 | 별도 트랜잭션에서 실행 |
| LLM 호출 중 DB 커넥션 | 점유 (낭비) | 반환 (효율적) |

### 부수적 개선: DB 커넥션 점유 시간 감소

기존에는 `@Transactional`이 메서드 전체를 감싸서 Claude API 호출 동안에도 DB 커넥션을 점유했다. LLM 호출은 최대 10초가 걸릴 수 있으므로, 그 동안 커넥션 풀에서 커넥션 하나가 불필요하게 잠겨있었다.

변경 후에는 `prepareChat()`이 끝나면 커넥션이 반환되고, LLM 호출 후 `saveChat()`에서 다시 가져간다. 동시 요청이 많을 때 커넥션 풀 고갈을 방지하는 효과가 있다.

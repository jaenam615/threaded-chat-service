# @Transactional + Streaming 트러블슈팅

## 증상

`isStreaming=true`로 대화 생성 시 DB에 채팅이 저장되지 않거나, 트랜잭션 관련 예외 발생.

## 원인

기존 `createChat()` 메서드 구조:

```kotlin
@Transactional
fun createChat(userId: Long, request: ChatCreateRequest): ChatResult {
    val thread = getOrCreateThread(userId, user)
    val messages = buildMessages(previousChats, request)

    if (request.isStreaming) {
        val chunks = claudeClient.callStream(messages)
            .doOnComplete {
                chatRepository.save(...)  // 여기서 DB 저장
            }
        return ChatResult.Stream(chunks)  // Flux를 즉시 반환
    }
    // ...
}
```

### 문제점

1. `@Transactional`은 메서드가 **리턴될 때** 커밋/롤백을 결정한다
2. 스트리밍 시 `Flux`는 **lazy**하다 — 메서드 리턴 시점에는 아직 실행되지 않았다
3. 메서드가 리턴되면 Spring이 트랜잭션을 **닫는다**
4. 이후 `doOnComplete`에서 `chatRepository.save()`가 호출되지만, 이미 트랜잭션 컨텍스트가 없다

```
시간순서:
1. createChat() 진입 → 트랜잭션 시작
2. Flux 객체 생성 (아직 실행 안 됨)
3. ChatResult.Stream(chunks) 반환 → 트랜잭션 커밋/종료
4. Controller가 Flux를 구독 → 스트리밍 시작
5. doOnComplete → chatRepository.save() 호출 → 트랜잭션 없음!
```

## 해결

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

### 변경 전후 비교

| | 변경 전 | 변경 후 |
|---|---|---|
| 트랜잭션 범위 | 메서드 전체 (LLM 호출 포함) | DB 접근 부분만 |
| 스트리밍 시 DB 저장 | 트랜잭션 없이 실행 | 별도 트랜잭션에서 실행 |
| LLM 호출 중 DB 커넥션 | 점유 (낭비) | 반환 (효율적) |

## 부수적 개선: DB 커넥션 점유 시간 감소

기존에는 `@Transactional`이 메서드 전체를 감싸서 Claude API 호출 동안에도 DB 커넥션을 점유했다. LLM 호출은 최대 30초가 걸릴 수 있으므로, 그 동안 커넥션 풀에서 커넥션 하나가 불필요하게 잠겨있었다.

변경 후에는 `prepareChat()`이 끝나면 커넥션이 반환되고, LLM 호출 후 `saveChat()`에서 다시 가져간다. 동시 요청이 많을 때 커넥션 풀 고갈을 방지하는 효과가 있다.

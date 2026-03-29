# 트러블슈팅

## 1. 대화 목록 조회 N+1 쿼리 문제

### 증상

`GET /api/chats` 호출 시 스레드 수만큼 추가 SELECT 쿼리가 발생하여, 페이지 사이즈가 20이면 총 21회의 DB 쿼리가 실행됨.

```
-- 1회: 스레드 목록 조회
SELECT * FROM threads WHERE user_id = ? LIMIT 20;

-- 20회: 각 스레드의 채팅 조회 (N+1)
SELECT * FROM chats WHERE thread_id = 1 ORDER BY created_at ASC;
SELECT * FROM chats WHERE thread_id = 2 ORDER BY created_at ASC;
...
```

### 원인

`ChatService.getChats()`에서 스레드를 페이지네이션으로 조회한 뒤, 각 스레드마다 개별적으로 `chatRepository.findByThreadIdOrderByCreatedAtAsc(thread.id)`를 호출하는 구조였음.

```kotlin
// 변경 전
return threads.map { thread ->
    val chats = chatRepository.findByThreadIdOrderByCreatedAtAsc(thread.id) // 스레드마다 1회 쿼리
    ThreadWithChatsResponse(...)
}
```

### 해결

스레드 ID 목록을 한 번에 모아서 `WHERE thread_id IN (...)` 쿼리 1회로 모든 채팅을 가져온 뒤, 메모리에서 threadId 기준으로 그룹화하는 방식으로 변경.

```kotlin
// 변경 후
val threadIds = threads.content.map { it.id }
val allChats = chatRepository.findByThreadIdInOrderByCreatedAtAsc(threadIds) // 1회 쿼리
val chatsByThreadId = allChats.groupBy { it.thread.id }

return threads.map { thread ->
    ThreadWithChatsResponse(
        threadId = thread.id,
        chats = (chatsByThreadId[thread.id] ?: emptyList()).map { ... },
    )
}
```

### 결과

- 변경 전: `1 + N` 회 쿼리 (N = 페이지 내 스레드 수)
- 변경 후: `2` 회 쿼리 (스레드 조회 1회 + 채팅 IN 조회 1회)

### 고려사항

- `@EntityGraph`나 `JOIN FETCH`를 사용하는 방법도 있으나, 스레드에 대한 페이지네이션과 함께 사용하면 Hibernate가 메모리에서 페이지네이션을 수행하는 문제(`HHH90003004` 경고)가 발생할 수 있음. 별도 IN 쿼리 방식이 페이지네이션과의 호환성이 더 좋음.
- 스레드당 채팅 수가 매우 많아질 경우 별도의 채팅 페이지네이션이 필요할 수 있으나, 현재 요구사항에서는 스레드 단위 그룹화가 목적이므로 충분함.

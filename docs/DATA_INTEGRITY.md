# 데이터 정합성 설계

## 배경

동시 요청 환경에서 데이터 정합성 보장 필요
- 피드백 중복 생성 방지
- 피드백 상태 변경 시 lost update 방지
- 스레드 30분 규칙의 race condition 대응

## 케이스별 대응

### 1. 피드백 중복 생성 방지

요구사항: 하나의 대화에 유저당 하나의 피드백만 생성 가능

**대응 전략: 애플리케이션 체크 + DB unique 제약 (이중 보호)**

```kotlin
// 1차: 애플리케이션 레벨 — 대부분의 정상적인 중복 요청을 깔끔한 에러 메시지로 차단
if (feedbackRepository.existsByUserIdAndChatId(userId, request.chatId)) {
    throw IllegalArgumentException("Feedback already exists for this chat")
}

// 2차: DB 레벨 — race condition 시 최후 방어선
@Table(uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "chat_id"])])
```

비관적 락(`SELECT FOR UPDATE`)은 불필요 - 같은 유저가 같은 대화에 밀리초 단위로 동시 피드백을 보내는 시나리오는 비현실적이고, 만약 발생해도 DB unique 제약이 하나를 거부  

### 2. 피드백 상태 변경 — 낙관적 락

요구사항: 관리자가 피드백 상태를 `pending` → `resolved`로 변경

**대응 전략: `@Version` 기반 낙관적 락**

```kotlin
@Entity
class FeedbackEntity(
    // ...
    var status: FeedbackStatus = FeedbackStatus.PENDING,
    @Version
    var version: Long = 0,
)
```

두 admin이 동시에 같은 피드백을 수정하면:  
1. 둘 다 `version = 0`인 엔티티를 조회
2. 먼저 커밋한 쪽이 성공, `version = 1`로 갱신
3. 늦은 쪽은 `UPDATE ... WHERE version = 0`이 0 rows affected → `ObjectOptimisticLockingFailureException`
4. `GlobalExceptionHandler`에서 `409 Conflict` 응답 반환

비관적 락 대신 낙관적 락을 선택한 이유:  
- 피드백 상태 변경은 빈번하지 않아 충돌 확률이 낮음
- 낙관적 락은 DB 락을 잡지 않아 성능 영향 없음
- 충돌 시 클라이언트가 재시도하면 되는 수준

### 3. 스레드 30분 규칙 — race condition

같은 유저가 동시에 두 요청을 보내면 `findLatestByUserId`가 둘 다 같은 결과를 반환하고, 스레드가 2개 생길 수 있다.  

**대응: 별도 처리하지 않음**  

이유:
- 채팅 API는 사람이 직접 호출하므로 밀리초 단위 동시 요청이 비현실적
- 스레드가 하나 더 생겨도 데이터 정합성이 깨지지 않음 (채팅은 각 스레드에 정상 저장)
- 비관적 락을 걸면 모든 대화 생성에 DB 락 비용이 추가되어 과한 대응

## 트랜잭션 격리 수준

PostgreSQL 기본값인 **READ COMMITTED**를 그대로 사용  

| 격리 수준 | 검토 결과 |
|-----------|-----------|
| READ COMMITTED (현재) | 충분. unique 제약 + 낙관적 락으로 필요한 정합성 확보 |
| REPEATABLE READ | 불필요. phantom read가 문제되는 시나리오 없음 |
| SERIALIZABLE | 과도한 성능 비용. 현재 동시성 요구사항 대비 이득 없음 |

격리 수준을 올리는 것보다 애플리케이션 레벨에서 정확히 필요한 지점에만 보호를 거는 것이 성능과 정합성 측면에서 유리  

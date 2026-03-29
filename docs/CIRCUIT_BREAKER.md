# Circuit Breaker 설계

## 배경

Claude API는 외부 의존성이다. 외부 서비스 장애 시 우리 서비스도 함께 다운되는 장애 전파(cascading failure)를 방지해야 한다.

기존에는 retry 로직(3회, 500ms 백오프)만 있었다. 이 방식의 문제:

- Claude API가 완전히 다운된 경우에도 매 요청마다 3회씩 재시도
- 30초 타임아웃 x 3회 = 최대 90초간 스레드 점유
- 동시 요청이 많으면 커넥션 풀 고갈 → 서비스 전체 장애

## 해결: Resilience4j Circuit Breaker

### 동작 방식

```
CLOSED (정상) → 실패율 50% 초과 → OPEN (차단)
                                      ↓ 30초 대기
                                  HALF_OPEN (시험)
                                      ↓ 3회 시험 요청
                                성공 → CLOSED / 실패 → OPEN
```

### 설정 값

| 설정 | 값 | 의미 |
|------|-----|------|
| sliding-window-size | 10 | 최근 10회 요청 기준으로 판단 |
| failure-rate-threshold | 50% | 10회 중 5회 실패 시 OPEN |
| wait-duration-in-open-state | 30s | OPEN 상태에서 30초 대기 후 HALF_OPEN |
| permitted-number-of-calls-in-half-open-state | 3 | HALF_OPEN에서 3회 시험 요청 허용 |
| slow-call-duration-threshold | 10s | 10초 넘는 응답은 slow call로 분류 |
| slow-call-rate-threshold | 80% | slow call 비율 80% 초과 시 OPEN |

### 적용 위치

```kotlin
@CircuitBreaker(name = "claudeApi", fallbackMethod = "callFallback")
fun call(messages: List<Map<String, String>>, model: String? = null): String { ... }

@CircuitBreaker(name = "claudeApi", fallbackMethod = "callStreamFallback")
fun callStream(messages: List<Map<String, String>>, model: String? = null): Flux<String> { ... }
```

`call()`과 `callStream()` 모두 동일한 `claudeApi` 인스턴스를 공유한다. 일반 요청과 스트리밍 요청 모두 같은 서킷 상태를 바라보므로, 한쪽에서 장애가 감지되면 양쪽 모두 차단된다.

### Fallback

서킷이 OPEN 상태일 때 즉시 fallback이 실행되어 `503 Service Unavailable` 응답을 반환한다. 클라이언트는 90초를 기다리는 대신 즉시 에러를 받고 재시도 판단을 할 수 있다.

## Retry와의 관계

Circuit Breaker와 기존 Retry는 다른 계층에서 동작한다:

- **Retry** (WebClient 레벨): 일시적 오류(503, 429)에 대한 즉각 재시도
- **Circuit Breaker** (서비스 레벨): 지속적 장애 시 요청 자체를 차단

Retry로 복구되지 않는 장애가 반복되면 Circuit Breaker가 열려서 불필요한 요청을 사전 차단한다.

## Rate Limiting을 적용하지 않은 이유

Rate Limiting은 인프라 계층(API Gateway, Nginx, Load Balancer)의 관심사다. 애플리케이션에 Rate Limiting을 넣으면:

- 인스턴스 간 상태 공유 필요 (Redis 등)
- 관심사 분리 위반
- 인프라 레벨 설정과 중복

프로덕션에서는 API Gateway에서 rate limiting을 적용하는 것이 적절하다.

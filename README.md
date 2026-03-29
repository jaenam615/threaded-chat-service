# AI 챗봇 서비스 API

VIP onboarding 팀을 위한 AI 챗봇 API 서비스입니다.

---

## 1. 과제 분석

### 핵심 요구사항 해석

이 과제는 단순한 LLM 연동이 아닌 **SaaS 백엔드 MVP 설계 능력**을 평가하는 과제로 해석했습니다.

- **인증/인가**: JWT 기반 사용자 인증 + RBAC (member/admin)
- **데이터 모델링**: User -> Thread -> Chat -> Feedback 관계 설계
- **상태 관리**: 30분 스레드 규칙 구현
- **API 설계**: RESTful API + 페이지네이션 + 필터링
- **확장성**: 지속적으로 확장 개발 가능한 구조 (요구사항 명시)

### 우선순위 판단

| 우선순위 | 기능 | 판단 근거 |
|---------|------|-----------|
| P0 | 인증 (JWT) | 모든 API의 기반 |
| P0 | 대화 생성 | 핵심 비즈니스 로직 (30분 규칙) |
| P1 | 대화/피드백 CRUD | 기본 기능 |
| P1 | 분석 및 보고 | 관리자 운영 기능 |
| P2 | 스트리밍 | 시간 부족 시 생략 가능 |

---

## 2. AI 활용

### 활용 방식

- **보일러플레이트 생성**: Entity, Repository, DTO 등 반복적인 코드 생성
- **설계 검증**: 아키텍처 구조 및 시간 배분 검토
- **코드 리뷰**: N+1 쿼리 문제, FK 제약 위반 버그 등 사전 발견
- **의사결정 논의**: 로그인 로그 저장 방식 (Redis vs DB) 등 트레이드오프 논의

### 어려웠던 점

1. **Claude API 연동**: 초기 400 에러 발생 -> Content-Type 헤더 누락 및 retry 로직 문제
2. **JWT 설정**: Spring Security 3.x의 변경된 설정 방식 적응
3. **시간 관리**: 모든 기능 구현 욕심 vs MVP 집중 사이의 균형

### AI 활용의 한계

- 전체 코드 생성 요청 시 컨텍스트 누락으로 오류 발생
- 작은 단위로 요청하고 직접 통합하는 방식이 효과적
- 설계 의사결정은 AI가 제시한 선택지를 기반으로 직접 판단

---

## 3. 어려웠던 기능

### 3.1 스레드 30분 규칙

**요구사항**: 마지막 질문 후 30분이 지나면 새 스레드 생성

**구현 방식**:
```kotlin
private fun getOrCreateThread(userId: Long, user: UserEntity): ThreadEntity {
    val latestThread = threadRepository.findLatestByUserId(userId)

    if (latestThread != null) {
        val latestChat = chatRepository.findLatestByThreadId(latestThread.id)

        if (latestChat != null) {
            val timeSinceLastChat = Duration.between(
                latestChat.createdAt,
                LocalDateTime.now()
            ).toMinutes()

            if (timeSinceLastChat < 30) {
                return latestThread  // 기존 스레드 유지
            }
        } else {
            return latestThread  // 스레드만 있고 채팅 없는 경우 재사용
        }
    }

    return threadRepository.save(ThreadEntity(user = user))  // 새 스레드 생성
}
```

**어려웠던 점**:
- 스레드의 마지막 채팅 시간을 기준으로 판단해야 함
- 스레드는 있지만 채팅이 없는 엣지 케이스 처리

### 3.2 대화 목록 조회 N+1 쿼리 해결

**문제**: 스레드 목록 조회 후 각 스레드마다 채팅을 개별 쿼리하면 `1 + N`회 쿼리 발생

**해결**: 스레드 ID 목록을 `IN` 절로 한 번에 조회 후 메모리에서 그룹화

```kotlin
val threadIds = threads.content.map { it.id }
val allChats = chatRepository.findByThreadIdInOrderByCreatedAtAsc(threadIds)
val chatsByThreadId = allChats.groupBy { it.thread.id }
```

**결과**: `1 + N`회 -> `2`회 쿼리로 감소

> 상세 내용: [docs/N+1_TROUBLESHOOTING.md](docs/N+1_TROUBLESHOOTING.md)

### 3.3 로그인 횟수 추적 설계

**문제**: 24시간 내 로그인 횟수를 어떻게 정확하게 카운트할 것인가

**검토한 방안**:
- `UserEntity.lastLoginAt`: 마지막 로그인만 기록되어 "횟수"가 아닌 "유저 수"만 카운트 가능
- Redis `INCR` 카운터: 숫자만 남아 이력 조회/확장 불가
- `LoginLog` 별도 테이블: 정확한 횟수 + 이력 보존 + 확장성

**결정**: `LoginLog` 테이블 채택. 향후 CDC나 비동기 이벤트 파이프라인 연결 시에도 데이터가 보존됨.

> 상세 내용: [docs/LOGIN_LOG.md](docs/LOGIN_LOG.md)

---

## 4. 구현/생략 트레이드오프

### 구현한 것

| 기능 | 구현 내용 |
|------|----------|
| 회원가입/로그인 | BCrypt 암호화, JWT 발급 |
| 관리자 계정 생성 | admin만 다른 admin 생성 가능 (`POST /api/auth/admin`) |
| 대화 생성 | 30분 규칙, LLM 호출, 스레드 자동 관리 |
| 모델 선택 | 요청 시 model 파라미터로 LLM 모델 지정 가능 |
| 스트리밍 응답 | `isStreaming=true` 시 SSE(Server-Sent Events)로 실시간 응답 |
| 대화 조회 | 페이지네이션, 정렬, 권한별 필터링, N+1 쿼리 해결 |
| 스레드 삭제 | 본인 것만 삭제 가능, 연관 데이터(Feedback -> Chat) 순서 보장 |
| 피드백 CRUD | 생성/조회/상태변경, 중복 방지, enum 기반 상태 관리 |
| 관리자 기능 | 활동 통계 (로그인 횟수 포함), CSV 보고서 |

### 생략한 것

| 기능 | 생략 이유 | 확장 방향 |
|------|----------|-----------|
| 테스트 코드 | 시간 부족 | 서비스 레이어 단위 테스트 + API 통합 테스트 |
| API 문서화 | 시간 부족 | SpringDoc OpenAPI (Swagger) |

### 시간이 더 있었다면

- 로그인 로그를 Spring ApplicationEvent -> Kafka 비동기 처리로 전환
- Redis 캐싱 (활동 통계 등 자주 조회되는 데이터)
- 테스트 코드 작성
- API 문서화 (Swagger/OpenAPI)

---

## 5. 실행 방법

### 사전 요구사항

- Docker & Docker Compose
- Anthropic API Key

### 실행

```bash
# 1. 환경 변수 설정
echo "ANTHROPIC_API_KEY=your-api-key" > .env

# 2. 빌드 및 실행
docker-compose up --build

# 3. 서버 확인
curl http://localhost:8080/api/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"1234","name":"테스트"}'
```

### 로컬 개발 (Docker 없이)

```bash
# PostgreSQL 실행 필요
./gradlew bootRun
```

### 최초 관리자 생성

Admin 계정 생성 API(`POST /api/auth/admin`)는 기존 admin만 호출 가능합니다. 최초 admin은 DB에 직접 생성해야 합니다.

```sql
INSERT INTO users (email, password, name, role, created_at)
VALUES ('admin@example.com', '$2a$10$...', 'Admin', 'ADMIN', now());
```

> BCrypt 해시는 `htpasswd -nbBC 10 "" "password" | cut -d: -f2` 또는 온라인 BCrypt 생성기로 만들 수 있습니다.

운영 환경에서는 애플리케이션 초기 구동 시 seed 데이터로 처리하거나, CLI 도구를 통해 생성하는 방식으로 확장할 수 있습니다.

---

## 6. API 문서

### 인증

| Method | Endpoint | 설명 | 권한 |
|--------|----------|------|------|
| POST | /api/auth/signup | 회원가입 | 없음 |
| POST | /api/auth/login | 로그인 (JWT 발급) | 없음 |
| POST | /api/auth/admin | 관리자 계정 생성 | admin만 |

**회원가입 요청**
```json
{
  "email": "user@example.com",
  "password": "password123",
  "name": "홍길동"
}
```

**로그인 응답**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "userId": 1,
  "email": "user@example.com",
  "name": "홍길동",
  "role": "member"
}
```

### 대화

| Method | Endpoint | 설명 | 권한 |
|--------|----------|------|------|
| POST | /api/chats | 대화 생성 | 인증 필요 |
| GET | /api/chats | 대화 목록 조회 | 인증 필요 (admin: 전체) |

**대화 생성 요청**
```json
{
  "question": "안녕하세요",
  "model": "claude-sonnet-4-20250514",
  "isStreaming": false
}
```

> `model`: 생략 시 기본 모델 사용. `isStreaming`: 현재 미구현 (향후 SSE로 확장 예정)

**대화 목록 조회**
```
GET /api/chats?page=0&size=20&sort=createdAt,desc
GET /api/chats?sort=createdAt,asc
```

### 스레드

| Method | Endpoint | 설명 | 권한 |
|--------|----------|------|------|
| DELETE | /api/threads/{id} | 스레드 삭제 | 본인만 |

### 피드백

| Method | Endpoint | 설명 | 권한 |
|--------|----------|------|------|
| POST | /api/feedbacks | 피드백 생성 | 인증 필요 (본인 대화 또는 admin) |
| GET | /api/feedbacks | 피드백 조회 | 인증 필요 (admin: 전체) |
| PATCH | /api/feedbacks/{id}/status | 상태 변경 | admin만 |

**피드백 생성 요청**
```json
{
  "chatId": 1,
  "isPositive": true
}
```

**피드백 상태 변경 요청**
```json
{
  "status": "resolved"
}
```

> 상태 값: `pending` (기본), `resolved`

**피드백 조회 (필터링)**
```
GET /api/feedbacks?isPositive=true&page=0&size=20&sort=createdAt,desc
```

### 관리자

| Method | Endpoint | 설명 | 권한 |
|--------|----------|------|------|
| GET | /api/admin/activity | 24시간 활동 통계 | admin만 |
| GET | /api/admin/report | CSV 보고서 다운로드 | admin만 |

**활동 통계 응답**
```json
{
  "signupCount": 5,
  "loginCount": 12,
  "chatCount": 34
}
```

---

## 7. 기술 스택

- **Language**: Kotlin 1.9.x
- **Framework**: Spring Boot 3.x
- **Database**: PostgreSQL 15.8
- **Auth**: JWT (jjwt 0.12.x)
- **LLM**: Claude API (Anthropic)
- **Build**: Gradle 8.x
- **Container**: Docker

---

## 8. 프로젝트 구조

```
src/main/kotlin/com/example/threadedchatservice/
├── config/
│   ├── GlobalExceptionHandler.kt
│   ├── JwtFilter.kt
│   ├── JwtUtil.kt
│   ├── SecurityConfig.kt
│   └── WebClientConfig.kt
├── controller/
│   ├── AdminController.kt
│   ├── AuthController.kt
│   ├── ChatController.kt
│   ├── FeedbackController.kt
│   └── ThreadController.kt
├── dto/
│   ├── request/
│   └── response/
├── entity/
│   ├── ChatEntity.kt
│   ├── FeedbackEntity.kt
│   ├── FeedbackStatus.kt
│   ├── LoginLogEntity.kt
│   ├── Role.kt
│   ├── ThreadEntity.kt
│   └── UserEntity.kt
├── repository/
│   ├── ChatRepository.kt
│   ├── FeedbackRepository.kt
│   ├── LoginLogRepository.kt
│   ├── ThreadRepository.kt
│   └── UserRepository.kt
├── service/
│   ├── admin/AdminService.kt
│   ├── auth/AuthService.kt
│   ├── chat/ChatService.kt
│   ├── feedback/FeedbackService.kt
│   └── thread/ThreadService.kt
└── client/
    ├── ClaudeClient.kt
    └── LlmClient.kt

docs/
├── CIRCUIT_BREAKER.md                # Circuit Breaker 설계 및 장애 전파 방지
├── LOGIN_LOG.md                      # 로그인 로그 설계 의사결정
├── N+1_TROUBLESHOOTING.md            # N+1 쿼리 트러블슈팅
├── STREAMING_DESIGN.md               # 스트리밍 응답 설계 및 sealed class 패턴
└── TRANSACTIONAL_TROUBLESHOOTING.md  # @Transactional + Streaming 충돌 해결
```

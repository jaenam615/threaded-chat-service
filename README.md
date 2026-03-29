# AI 챗봇 서비스 API

VIP onboarding 팀을 위한 AI 챗봇 API 서비스입니다.

---

## 1. 과제 분석

### 핵심 요구사항 해석

이 과제는 단순한 LLM 연동이 아닌 **SaaS 백엔드 MVP 설계 능력**을 평가하는 과제로 해석했습니다.

- **인증/인가**: JWT 기반 사용자 인증 + RBAC (member/admin)
- **데이터 모델링**: User → Thread → Chat → Feedback 관계 설계
- **상태 관리**: 30분 스레드 규칙 구현
- **API 설계**: RESTful API + 페이지네이션 + 필터링

### 우선순위 판단

| 우선순위 | 기능 | 판단 근거 |
|---------|------|-----------|
| P0 | 인증 (JWT) | 모든 API의 기반 |
| P0 | 대화 생성 | 핵심 비즈니스 로직 (30분 규칙) |
| P1 | 대화/피드백 CRUD | 기본 기능 |
| P2 | 스트리밍 | 시간 부족 시 생략 가능 |

---

## 2. AI 활용

### 활용 방식

- **보일러플레이트 생성**: Entity, Repository, DTO 등 반복적인 코드 생성
- **설계 검증**: 아키텍처 구조 및 시간 배분 검토
- **디버깅**: 컴파일 에러 및 API 오류 해결

### 어려웠던 점

1. **Claude API 연동**: 초기 400 에러 발생 → Content-Type 헤더 누락 및 retry 로직 문제
2. **JWT 설정**: Spring Security 3.x의 변경된 설정 방식 적응
3. **시간 관리**: 모든 기능 구현 욕심 vs MVP 집중 사이의 균형

### AI 활용의 한계

- 전체 코드 생성 요청 시 컨텍스트 누락으로 오류 발생
- 작은 단위로 요청하고 직접 통합하는 방식이 효과적

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
        }
    }

    return threadRepository.save(ThreadEntity(user = user))  // 새 스레드 생성
}
```

**어려웠던 점**:
- 스레드의 마지막 채팅 시간을 기준으로 판단해야 함
- 스레드는 있지만 채팅이 없는 엣지 케이스 처리

### 3.2 권한 기반 데이터 접근 (RBAC)

**요구사항**: member는 본인 데이터만, admin은 전체 데이터 접근

**구현 방식**:
- JWT에 role claim 포함
- Service 레이어에서 role 체크 후 쿼리 분기
- `@PreAuthorize` 어노테이션으로 admin 전용 API 보호

---

## 4. 구현/생략 트레이드오프

### 구현한 것

| 기능 | 구현 내용 |
|------|----------|
| 회원가입/로그인 | BCrypt 암호화, JWT 발급 |
| 대화 생성 | 30분 규칙, LLM 호출, 스레드 자동 관리 |
| 대화 조회 | 페이지네이션, 정렬, 권한별 필터링 |
| 스레드 삭제 | 본인 것만 삭제 가능 |
| 피드백 CRUD | 생성/조회/상태변경, 중복 방지 |
| 관리자 기능 | 활동 통계, CSV 보고서 |

### 생략한 것

| 기능 | 생략 이유 |
|------|----------|
| 스트리밍 응답 | SSE/WebFlux 필요, 시간 부족 |
| 모델 선택 | LLM 클라이언트 확장 필요 |
| 로그인 기록 | 별도 이벤트 로깅 시스템 필요 |

### 시간이 더 있었다면

- 스트리밍 응답 (Server-Sent Events)
- Redis 기반 세션/캐싱
- 테스트 코드 작성
- API 문서화 (Swagger/OpenAPI)

---

## 5. 실행 방법

### 사전 요구사항

- Docker & Docker Compose
- (선택) Anthropic API Key

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

---

## 6. API 문서

### 인증

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | /api/auth/signup | 회원가입 |
| POST | /api/auth/login | 로그인 (JWT 발급) |

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
| GET | /api/chats | 대화 목록 조회 | 인증 필요 |

**대화 생성 요청**
```json
{
  "question": "안녕하세요"
}
```

**대화 목록 조회**
```
GET /api/chats?page=0&size=20&sort=createdAt,desc
```

### 스레드

| Method | Endpoint | 설명 | 권한 |
|--------|----------|------|------|
| DELETE | /api/threads/{id} | 스레드 삭제 | 본인만 |

### 피드백

| Method | Endpoint | 설명 | 권한 |
|--------|----------|------|------|
| POST | /api/feedbacks | 피드백 생성 | 인증 필요 |
| GET | /api/feedbacks | 피드백 조회 | 인증 필요 |
| PATCH | /api/feedbacks/{id}/status | 상태 변경 | admin만 |

**피드백 생성 요청**
```json
{
  "chatId": 1,
  "isPositive": true
}
```

**피드백 조회 (필터링)**
```
GET /api/feedbacks?isPositive=true&page=0&size=20
```

### 관리자

| Method | Endpoint | 설명 | 권한 |
|--------|----------|------|------|
| GET | /api/admin/activity | 24시간 활동 통계 | admin만 |
| GET | /api/admin/report | CSV 보고서 다운로드 | admin만 |

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
src/main/kotlin/com/example/assignment/
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
│   ├── ThreadEntity.kt
│   └── UserEntity.kt
├── repository/
├── service/
│   ├── admin/
│   ├── auth/
│   ├── chat/
│   ├── feedback/
│   └── thread/
└── client/
    ├── ClaudeClient.kt
    └── LlmClient.kt
```

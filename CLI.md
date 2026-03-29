# 회원가입
```
curl -s -X POST http://localhost:8080/api/auth/signup -H "Content-Type: application/json" -d '{"email":"test@test.com","password":"1234","name":"테스트"}' | jq .
```

# 로그인 + 토큰 저장
```
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d '{"email":"test@test.com","password":"1234"}' | jq -r .token)
```

# 대화 생성
```
curl -s -X POST http://localhost:8080/api/chats -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" -d '{"question":"안녕하세요"}' | jq .
```

# 대화 목록 조회
```
curl -s http://localhost:8080/api/chats?page=0&size=20 -H "Authorization: Bearer $TOKEN" | jq .
```

# 스트리밍 대화
```
curl -N -X POST http://localhost:8080/api/chats -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" -d '{"question":"스트리밍 테스트","isStreaming":true}'
```

# 피드백 생성
```
curl -s -X POST http://localhost:8080/api/feedbacks -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" -d '{"chatId":1,"isPositive":true}' | jq .
```

# 피드백 조회
```
curl -s http://localhost:8080/api/feedbacks -H "Authorization: Bearer $TOKEN" | jq .
```

# 피드백 필터링
```
curl -s "http://localhost:8080/api/feedbacks?isPositive=true" -H "Authorization: Bearer $TOKEN" | jq .
```

# 스레드 삭제
```
curl -s -X DELETE http://localhost:8080/api/threads/1 -H "Authorization: Bearer $TOKEN" -w "\n%{http_code}"    
```
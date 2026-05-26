# 2026cap_server — 픽셀아트 플랫폼 백엔드

> 팀명: 익스팬션 조 | 팀장: 조성민 | 팀원: 원범석

AI 어시스턴트 기반 픽셀아트 일관성 관리 및 에셋 생태계 플랫폼의 Spring Boot 백엔드 서버.

---

## 기술 스택

| 분류 | 기술 |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.0.5 |
| Build | Gradle (Groovy) |
| DB | PostgreSQL 16 |
| ORM | Spring Data JPA (Hibernate 7.2.7) |
| Migration | Flyway |
| Auth | Spring Security + JWT + OAuth2 Client |
| Storage | Cloudflare R2 (S3 호환 API) |
| Util | Lombok, Validation, Spring Boot DevTools |

---

## 프로젝트 구조

```
backend/server/
├── src/main/java/com/expansion/server/
│   ├── domain/
│   │   ├── user/          # 사용자, 프로필, 팔로우, 차단
│   │   ├── gallery/       # 갤러리 게시글, 댓글, 좋아요, 태그
│   │   ├── asset/         # 에셋 스토어, 구매, 댓글, 버전
│   │   ├── editor/        # 프로젝트, 레이어
│   │   └── commission/    # 커미션, 파일, 이미지
│   └── global/
│       ├── config/        # Security, CORS, Swagger
│       ├── exception/     # GlobalExceptionHandler
│       ├── response/      # ApiResponse<T>
│       └── util/          # JwtUtil
└── src/main/resources/
    └── application.yml
```

---

## 로컬 개발 환경 설정

### 사전 조건

- Java 21 이상
- Docker Desktop 설치 및 실행 중

### 1. PostgreSQL 컨테이너 실행

기존 컨테이너가 있으면:
```bash
docker start pixelart-db
```

처음 클론한 경우 — 컨테이너 최초 1회 생성 (PowerShell):
```powershell
docker run -d `
  --name pixelart-db `
  -e POSTGRES_DB=pixelart `
  -e POSTGRES_USER=postgres `
  -e POSTGRES_PASSWORD=1234 `
  -p 5432:5432 `
  postgres:16
```

실행 확인:
```bash
docker ps   # pixelart-db 가 Up 상태이면 OK
```

### 2. 백엔드 실행

```powershell
# Windows PowerShell
cd backend/server
.\gradlew.bat bootRun
```

```bash
# Mac / Linux
cd backend/server
./gradlew bootRun
```

서버 기본 포트: `http://localhost:8080`

> 처음 실행 시 Flyway가 V1 이후 마이그레이션을 자동 실행하여 테이블 및 시드 데이터를 생성합니다.

---

## API 명세

Swagger UI: `http://localhost:8080/swagger-ui/index.html`

주요 엔드포인트:

| 도메인 | Base URL |
|---|---|
| 인증 | `/api/auth` |
| 사용자 | `/api/users` |
| 갤러리 | `/api/gallery` |
| 에셋 스토어 | `/api/assets` |
| 에디터 | `/api/projects` |
| 커미션 | `/api/commissions`, `/api/request-posts`, `/api/artist-services` |

---

## 인증

- JWT 기반 인증 (Access Token + Refresh Token)
- Refresh Token은 Redis 없이 DB(`refresh_tokens` 테이블)로 관리
- 비로그인 허용: 갤러리/에셋 조회, 공개 프로필 조회
- 로그인 필수: 게시글 작성, 에셋 업로드, 에디터, 커미션 등

---

## 응답 형식

모든 API 응답은 `ApiResponse<T>` 형식으로 통일:

```json
{
  "success": true,
  "message": "요청 성공",
  "data": { ... }
}
```

---

## 데이터베이스 초기화

Flyway 체크섬 에러 발생 시 (마이그레이션 SQL을 잘못 수정한 경우):

```bash
docker exec pixelart-db psql -U postgres -d pixelart -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
```

이후 Spring Boot 재시작 → Flyway가 처음부터 자동 재실행됩니다.

> ⚠️ 이미 DB에 적용된 Flyway 마이그레이션 파일은 절대 수정 금지. 변경사항은 반드시 새 버전(Vn+1)으로 작성.

---

## 테스트 계정

| 이메일 | 비밀번호 | 닉네임 |
|---|---|---|
| spriteknight@test.com | password123 | SpriteKnight |
| pixelwitch@test.com | password123 | PixelWitch |
| neonbrush@test.com | password123 | NeonBrush |

---

## 배포

- 서버: Ubuntu Server 24.04 LTS (192.168.55.229)
- 실행: systemd 서비스 (`pixelart-backend`)로 자동 실행
- 자동 배포: GitHub Actions Self-hosted Runner (main 브랜치 push 시)
- DB: Docker PostgreSQL 16 컨테이너

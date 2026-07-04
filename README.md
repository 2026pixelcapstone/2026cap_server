# 2026cap_server — PixelPilot 백엔드

> 팀명: 익스팬션 조 | 팀장: 조성민 | 팀원: 원범석

AI 어시스턴트 기반 픽셀아트 일관성 관리 및 에셋 생태계 플랫폼(**PixelPilot**)의 Spring Boot 백엔드 서버.

- 🌐 API: `https://api.pixelpilot.art` (프로덕션)

---

## 기술 스택

| 분류 | 기술 |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.0.5 |
| Build | Gradle (Groovy) |
| DB | PostgreSQL 16 |
| ORM | Spring Data JPA (Hibernate 7.2.7) |
| Migration | Flyway (최신 V27) |
| Auth | Spring Security + JWT + OAuth2 Client(Google) + 이메일 인증 |
| Realtime | WebSocket (STOMP) — 커미션 1:1 채팅 |
| Storage | Cloudflare R2 (S3 호환 API) |
| Mail | Brevo SMTP 릴레이 (JavaMailSender) |
| Security | AbuseIPDB IP 평판 차단/신고 · Thumbnailator(워터마크) · Caffeine(캐시) |
| Util | Lombok, Validation, Spring Boot DevTools |

---

## 프로젝트 구조

```
backend/server/
├── src/main/java/com/expansion/server/
│   ├── domain/
│   │   ├── user/          # 사용자, 프로필, 팔로우, 차단, 인증(Auth)
│   │   ├── gallery/       # 갤러리 게시글(.ppit 전용 포함), 댓글, 좋아요, 태그
│   │   ├── asset/         # 에셋 스토어, 구매, 평점/리뷰, 다운로드, 버전
│   │   ├── editor/        # 프로젝트, 레이어
│   │   ├── commission/    # 작가서비스/의뢰글, 지원, 거래, 미리보기/납품파일
│   │   ├── chat/          # 커미션 채팅(REST + WebSocket)
│   │   └── notification/  # 알림(댓글/팔로우/커미션 전이)
│   └── global/
│       ├── config/        # Security, CORS, OAuth2, JwtFilter
│       ├── security/      # abuseipdb(IP 평판 차단/신고 필터)
│       ├── websocket/     # STOMP 설정, 인증 인터셉터, presence
│       ├── mail/          # 메일 발송(Brevo)
│       ├── exception/     # GlobalExceptionHandler, ErrorCode
│       ├── response/      # ApiResponse<T>
│       └── util/          # JwtUtil, R2Uploader, ClientIpResolver
└── src/main/resources/
    ├── application.yml          # gitignore (예시: application.yml.example)
    └── db/migration/            # Flyway V1~V27
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

> 처음 실행 시 Flyway가 **V1~V27** 마이그레이션을 자동 실행하여 테이블 및 시드 데이터를 생성합니다.
> 외부 의존성은 로컬에서 **비활성 기본값**으로 동작합니다 — R2(`r2.enabled=false`), 메일(`mail.enabled=false`=콘솔 로그), OAuth(`oauth` 프로파일 미활성), AbuseIPDB(`abuseipdb.enabled=false`). 별도 키 없이 기동됩니다(설정은 `application.yml.example` 참고).

---

## API 명세

Swagger UI: `http://localhost:8080/swagger-ui/index.html`

주요 엔드포인트:

| 도메인 | Base URL |
|---|---|
| 인증 | `/api/auth` (signup·login·refresh·logout·email/verify·resend) |
| OAuth2 | `/oauth2/**`, `/login/oauth2/**` |
| 사용자 | `/api/users` |
| 갤러리 | `/api/gallery` (`/portfolios` 배치 포함) |
| 에셋 스토어 | `/api/assets` (rating-summary·download·categories·license-types) |
| 에디터 | `/api/editor/projects` |
| 커미션 | `/api/commissions`, `/api/request-posts`, `/api/artist-services`, `/api/applications` |
| 채팅 | REST `/api/commissions/{id}/messages` · WebSocket `/ws` + `/topic/commissions/{id}` |
| 알림 | `/api/notifications`, `/api/chat/unread-conversations` |
| 파일 | `/api/files` (R2 업로드) |

---

## 인증 / 보안

- **JWT** 기반 (Access 1h + Refresh 14d). Refresh는 Redis 없이 DB(`refresh_tokens`)에 SHA-256 해시·rotation 저장.
- **이메일 인증(소프트 게이트)**: 가입은 바로 되나 미인증 시 콘텐츠 생성 차단(갤러리/에셋/에디터/커미션). 메일은 Brevo.
- **Google OAuth2**: `oauth` 프로파일 + 키 주입 시 활성(동일 이메일 연결/자동가입).
- **AbuseIPDB**: 들어오는 IP 평판 조회 차단(Check, `score≥75`→403, fail-open) + 로그인 brute-force 신고(Report). `CF-Connecting-IP`로 실 IP 추출.
- **커미션 에스크로**: 납품 원본은 완료(COMPLETED) 전 마스킹, 의뢰자는 워터마크 미리보기로만 검토.
- 비로그인 허용: 갤러리/에셋 조회, 공개 프로필. 로그인 필수: 작성/업로드/에디터/커미션 등.

---

## 응답 형식

모든 API 응답은 `ApiResponse<T>` 형식으로 통일:

```json
{ "success": true, "message": "요청 성공", "data": { } }
```

---

## 데이터베이스 초기화

Flyway 체크섬 에러 발생 시 (마이그레이션 SQL을 잘못 수정한 경우):

```bash
docker exec pixelart-db psql -U postgres -d pixelart -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
```

이후 Spring Boot 재시작 → Flyway가 처음부터 자동 재실행됩니다.

> ⚠️ 이미 DB에 적용된 Flyway 마이그레이션 파일은 **절대 수정 금지**. 변경은 반드시 새 버전(Vn+1)으로 작성.

---

## 테스트 계정

| 이메일 | 비밀번호 | 닉네임 |
|---|---|---|
| spriteknight@test.com | password123 | SpriteKnight |
| pixelwitch@test.com | password123 | PixelWitch |
| neonbrush@test.com | password123 | NeonBrush |

---

## 배포

- 서버: Ubuntu Server 24.04 LTS (정적 IP)
- 실행: systemd 서비스 (`pixelart-backend`)로 자동 실행
- 자동 배포: GitHub Actions Self-hosted Runner (main 브랜치 push 시)
- 외부 노출: Cloudflare Tunnel(`cloudflared`) → `api.pixelpilot.art` (HTTPS는 Cloudflare가 종료 처리)
- DB: Docker PostgreSQL 16 컨테이너

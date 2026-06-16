# 🎵 Humix - Main API Server (Backend)

Humix 프로젝트의 메인 비즈니스 로직과 데이터 흐름을 총괄하는 컨트롤 타워(Main API Server)입니다. 클라이언트의 요청을 처리하고, 무거운 AI 음악 생성 작업을 독립된 워커 노드로 안전하게 위임하며 상태를 동기화합니다.

## 🛠 Tech Stack
* **Framework:** Java 21, Spring Boot 3.x
* **Database:** MariaDB (Self-hosted on Docker)
* **Infrastructure:** AWS EC2 (t4g.small, ARM64), Nginx, Docker
* **Storage:** AWS S3 (Object Storage)

## 🏗 Architecture & Core Features

### 1. 3-Tier 아키텍처 및 MSA 기반 워커 분리
* `Controller - Service - Repository` 계층으로 설계되어 명확한 관심사 분리를 달성했습니다.
* Nginx 리버스 프록시를 통해 안전하게 API 요청을 라우팅합니다.
* 고부하 생성형 AI 모델 구동 환경을 메인 서버에서 분리하여, 비동기 Job Queue 기반의 마이크로 서비스(MSA) 형태로 구축했습니다.

### 2. 핵심 비즈니스 데이터 플로우
1. **데이터 입력:** 클라이언트로부터 허밍 오디오(.wav) 및 곡 분위기 메타데이터 수신. 원본 파일은 AWS S3에 즉시 저장.
2. **비동기 작업 등록 (Job Queue):** DB에 상태를 `Pending`으로 기록하고 AI 워커에 작업 위임.
3. **실시간 상태 알림 (SSE):** AI 연산 중 발생하는 지연 시간 동안 클라이언트에게 `Server-Sent Events(SSE)`로 실시간 진행률 및 피드백 전송.
4. **결과 동기화 (Callback):** AI 워커 연산 완료 후 Webhook 콜백을 수신하여 DB 상태를 `Completed`로 업데이트하고 최종 S3 음원 URL을 클라이언트에 제공.

## 🗄 Database Schema (Core Tables)
시스템 관리를 위한 핵심 도메인 테이블 구조입니다.

| Table | Description | Key Columns |
|---|---|---|
| **Member** | 브라우저 세션을 독립된 사용자로 식별 | `uuid`(PK), `created_at` |
| **Humming** | 사용자가 녹음한 원본 음성 오디오 관리 | `humming_id`(PK), `uuid`(FK), `s3_file_url`, `duration_seconds` |
| **MelodyScore** | 허밍에서 파싱된 초기 데이터 및 수정된 악보 데이터 | `melody_id`(PK), `humming_id`(FK), `notes_data`(LONGTEXT) |
| **MusicGeneration** | 비동기 AI 편곡 작업 상태 및 최종 완성곡 메타데이터 | `generation_id`(PK), `parent_generation_id`(Self FK), `status`(PROCESSING/COMPLETED/FAILED), `result_s3_url` |
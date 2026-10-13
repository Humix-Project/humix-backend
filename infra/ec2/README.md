# EC2 서버 설정

운영 서버(EC2)에 직접 적용된 설정과 장애 대응 절차를 기록하는 폴더입니다.
이 폴더의 파일은 GitHub Actions 배포(`deploy.yml`)로 서버에 반영되지 **않으며**, 서버에 직접 설치해야 합니다.

## 서버 구조

```
사용자 ──HTTPS(443)──▶ Nginx (EC2 호스트) ──HTTP──▶ humix-backend-prod 컨테이너 (8080)
                         └ SSL 인증서: Let's Encrypt (certbot)          │
                                                                        ├──▶ humix-ai-cpu-prod 컨테이너 (8000, 호스트 8001)
                                                                        └──▶ humix-db-prod 컨테이너 (MariaDB, 3306)
```

- **OS**: Amazon Linux 2023
- **도메인**: `humix.my-project.cloud`
- **배포 경로**: `/home/ec2-user/humix-prod` (GitHub Actions가 jar, Dockerfile, docker-compose.yml, .env, ai-cpu를 복사)
- **인증서 경로**: `/etc/letsencrypt/live/humix.my-project.cloud/`
- **인증서 유효기간**: 90일 (만료 30일 전부터 자동 갱신)

### 서버 사양

| 항목 | 값 | 비고 |
|---|---|---|
| 메모리 | 1.8GB | |
| 스왑 | 2GB (`/swapfile`) | 2026-10-14 추가, `/etc/fstab`에 등록되어 재부팅 후에도 유지 |
| 디스크 (EBS) | 20GB | 2026-10-14 8GB → 20GB 확장 |
| Docker Compose | `docker-compose` (v1) | `docker compose`(v2)는 **설치되어 있지 않음** |

## HTTPS 인증서 자동 갱신

Amazon Linux 2023은 `dnf`로 certbot을 설치해도 자동 갱신 타이머가 생성되지 않고,
cron도 기본 설치되어 있지 않습니다. 따라서 systemd 타이머를 직접 등록해야 합니다.

### 설치 방법

```bash
# 1. certbot 경로 확인 (certbot-renew.service의 ExecStart와 같은지 확인)
which certbot

# 2. 파일 복사
sudo cp certbot-renew.service certbot-renew.timer /etc/systemd/system/

# 3. 타이머 등록 및 시작
sudo systemctl daemon-reload
sudo systemctl enable --now certbot-renew.timer
```

### 동작 확인

```bash
# 타이머 등록 여부와 다음 실행 시간 확인
systemctl list-timers | grep certbot

# 인증서 만료일 확인
sudo certbot certificates

# 갱신 시뮬레이션 (실제 발급 X)
sudo certbot renew --dry-run

# 갱신 로그 확인
sudo journalctl -u certbot-renew.service
```

갱신 후 Nginx 리로드는 certbot이 자동으로 처리합니다.

## 트러블슈팅

### 브라우저에 `net::ERR_CERT_DATE_INVALID` 경고가 뜰 때

인증서가 만료된 상태입니다.

```bash
sudo certbot certificates          # Expiry Date 확인
sudo certbot renew --dry-run       # 갱신 가능 여부 확인
sudo certbot renew                 # 실제 갱신
sudo nginx -t && sudo systemctl reload nginx
```

`--dry-run`이 실패한다면 EC2 보안그룹에서 **80번 포트 인바운드**가 열려 있는지 확인하세요.
Let's Encrypt는 80번 포트로 도메인 소유를 검증합니다.

### 배포 후 서버가 바뀌지 않을 때 (Actions는 성공, 컨테이너는 그대로)

Actions가 성공으로 표시되어도 서버의 컨테이너가 교체되지 않았을 수 있습니다.
아래 순서로 확인합니다.

**1. 컨테이너 상태 확인**

```bash
docker ps -a
```

- `humix-backend-prod`, `humix-ai-cpu-prod`의 `CREATED`가 배포 시각과 맞는지 확인
- `STATUS`가 `Created`이고 이름이 `0f7482c38e11_humix-backend-prod`처럼 **ID가 앞에 붙은 컨테이너**가 있다면,
  배포 두 건이 동시에 실행되다 꼬여서 남은 임시 컨테이너입니다. 이 컨테이너가 있으면 이후 배포가 이름 충돌로 계속 실패합니다.

```bash
# Created 상태의 임시 컨테이너만 삭제 (Up 상태 컨테이너, 특히 humix-db-prod는 건드리지 않음)
docker rm <CONTAINER ID>
```

**2. 파일이 서버에 도착했는지 확인**

```bash
ls -la ~/humix-prod ~/humix-prod/build/libs
```

`.env`, `docker-compose.yml`, jar의 수정 시각이 배포 시각과 같으면 파일 전송은 성공한 것이고,
컨테이너 실행 단계에서 실패한 것입니다.

**3. 배포 명령을 서버에서 직접 실행해 에러 확인**

```bash
cd ~/humix-prod
docker-compose --project-name humix-prod --env-file .env up --build -d
```

> ⚠️ 이 서버에는 `docker compose`(v2)가 없으므로 반드시 `docker-compose`(v1)를 사용합니다.

에러 메시지가 `No space left on device`면 아래 "디스크 공간이 부족할 때"를 따릅니다.

**4. 정상 동작 확인**

```bash
docker ps -a                                 # 컨테이너가 Up이고 CREATED가 방금인지
docker logs humix-backend-prod --tail 30     # Started ApiApplication 로그 확인
```

### 디스크 공간이 부족할 때 (`No space left on device`)

배포할 때마다 서버에서 백엔드와 AI 서버 이미지를 빌드하므로 약 **3GB 이상**의 여유 공간이 필요합니다.

**1. 사용량 확인**

```bash
df -h /             # 전체 디스크 여유 공간
docker system df    # Docker 이미지·컨테이너·볼륨·빌드 캐시 사용량
```

**2. Docker 정리**

```bash
docker image prune -a    # 사용 중이 아닌 이미지 삭제 (실행 중인 컨테이너의 이미지는 유지됨)
docker builder prune -a  # 빌드 캐시 삭제
sudo journalctl --vacuum-size=100M   # 오래된 시스템 로그 정리
```

> ⚠️ `docker volume prune`, `docker system prune --volumes`는 **절대 사용하지 마세요.**
> DB 데이터가 Docker 볼륨에 저장되어 있어 DB가 통째로 삭제됩니다.

**3. 그래도 부족하면 EBS 볼륨 확장**

서버를 끄지 않고 진행할 수 있습니다. EBS는 늘리기만 가능하고 줄일 수 없습니다.

1. EC2 콘솔 → 인스턴스 → 서버 선택 → **스토리지** 탭 → 볼륨 ID 클릭
2. 볼륨 목록에서 **체크박스로 볼륨 선택** → **작업 → 볼륨 수정** → 크기 변경 후 수정
   (체크박스를 선택하지 않으면 작업 메뉴가 비활성화되어 있음)
3. 볼륨 상태가 `optimizing` 또는 `completed`가 되면 서버에서 파일시스템 확장

```bash
lsblk                          # nvme0n1의 SIZE가 새 크기인지 확인
sudo growpart /dev/nvme0n1 1   # 파티션 확장
sudo xfs_growfs -d /           # 파일시스템 확장 (Amazon Linux 2023은 XFS)
df -h /                        # Size가 새 크기로 바뀌었는지 확인
```

### 메모리가 부족할 때 (스왑 설정)

메모리 1.8GB에서 이미지 빌드와 백엔드·AI 서버·DB를 함께 돌리면 메모리가 부족해질 수 있어 스왑 2GB를 추가해 두었습니다.
서버를 새로 만들었거나 스왑이 없다면 아래로 설정합니다. 스왑 파일은 디스크를 그만큼 차지하므로 여유 공간을 먼저 확인하세요.

```bash
sudo dd if=/dev/zero of=/swapfile bs=1M count=2048
sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile swap swap defaults 0 0' | sudo tee -a /etc/fstab
free -h   # Swap 줄에 2.0Gi가 보이면 성공
```

### DB 스키마 변경으로 DB를 초기화해야 할 때

`application-prod.yml`이 `ddl-auto: update`라서 빈 DB에는 테이블을 자동으로 만들지만,
**기존 컬럼의 타입 변경이나 기존 데이터 보정은 하지 않습니다.**
데이터를 보존할 필요가 없다면 DB를 초기화하고 재배포합니다.

```bash
# 1. 백업
docker exec humix-db-prod mariadb-dump -uroot -p<비밀번호> --all-databases > ~/backup_$(date +%Y%m%d).sql
ls -lh ~/backup_*.sql   # 0바이트가 아닌지 확인

# 2. 초기화 (DB 이름: humix)
docker exec -it humix-db-prod mariadb -uroot -p<비밀번호> \
  -e "DROP DATABASE humix; CREATE DATABASE humix CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 3. 바로 재배포 (초기화 후부터 배포 완료 전까지는 기존 서버의 API가 에러를 냄)

# 4. 테이블 생성 확인
docker exec -it humix-db-prod mariadb -uroot -p<비밀번호> -e "SHOW TABLES FROM humix;"
```

## 장애 기록

| 날짜 | 내용 | 원인 | 조치 |
|---|---|---|---|
| 2026-10-04 | HTTPS 인증서 만료로 Swagger 및 API 접속 불가 (2026-09-08 만료) | 자동 갱신 타이머 미설정 | 인증서 수동 갱신, systemd 타이머 등록 |
| 2026-10-14 | 10-12 이후 배포(#43, #45, #47)가 운영에 반영되지 않음 | 동시 배포로 `Created` 상태 임시 컨테이너가 남아 이름 충돌, 디스크 부족(8GB 중 여유 1.2GB)으로 이미지 빌드 실패. 배포 스크립트가 실패를 성공으로 표시해 발견이 늦어짐 | 임시 컨테이너 삭제, Docker 이미지 정리, EBS 8→20GB 확장, 스왑 2GB 추가, PR #40 스키마 반영을 위해 DB 초기화 후 수동 배포. 배포 워크플로 수정(#48) |

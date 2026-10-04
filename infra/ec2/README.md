# EC2 서버 설정

운영 서버(EC2)에 직접 적용된 설정을 기록하는 폴더입니다.
이 폴더의 파일은 GitHub Actions 배포(`deploy.yml`)로 서버에 반영되지 **않으며**, 서버에 직접 설치해야 합니다.

## 서버 구조

```
사용자 ──HTTPS(443)──▶ Nginx (EC2 호스트) ──HTTP──▶ humix-backend 컨테이너 (8080)
                         └ SSL 인증서: Let's Encrypt (certbot)
```

- **OS**: Amazon Linux 2023
- **도메인**: `humix.my-project.cloud`
- **인증서 경로**: `/etc/letsencrypt/live/humix.my-project.cloud/`
- **인증서 유효기간**: 90일 (만료 30일 전부터 자동 갱신)

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

## 장애 기록

| 날짜 | 내용 | 원인 | 조치 |
|---|---|---|---|
| 2026-10-04 | HTTPS 인증서 만료로 Swagger 및 API 접속 불가 (2026-09-08 만료) | 자동 갱신 타이머 미설정 | 인증서 수동 갱신, systemd 타이머 등록 |

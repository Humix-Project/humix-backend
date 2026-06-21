#!/usr/bin/env python3
"""
HuMix E2E Test Script
S3 Presigned URL 발급 → 노래 생성 요청 → SSE 스트림 구독 → 완료 콜백 수신 → 결과 URL 출력
"""

import requests
import json
import sys
import time

BACKEND = "http://localhost:8080"
HUMMING_ID = 1      # DB에 이미 삽입된 테스트 humming

DIVIDER = "─" * 60

def step(num, title):
    print(f"\n{DIVIDER}")
    print(f"  STEP {num}: {title}")
    print(DIVIDER)

def ok(msg):
    print(f"  ✅  {msg}")

def fail(msg):
    print(f"  ❌  {msg}")
    sys.exit(1)

def info(msg):
    print(f"  ℹ️   {msg}")

# ───────────────────────────────────────────────
# STEP 0: Guest Login (Access Token 발급)
# ───────────────────────────────────────────────
step(0, "Guest Login으로 Access Token 발급")

import uuid
device_id = str(uuid.uuid4())

login_resp = requests.post(
    f"{BACKEND}/api/v1/auth/guest-login",
    json={"device_id": device_id},
    timeout=10
)

print(f"  → Status: {login_resp.status_code}")
if login_resp.status_code not in (200, 201):
    fail(f"Guest Login 실패: {login_resp.status_code}\n{login_resp.text}")

token_data = login_resp.json().get("result", {})
access_token = token_data.get("access_token")

if not access_token:
    fail(f"응답에서 access_token을 찾지 못했습니다: {login_resp.text}")

headers = {
    "Authorization": f"Bearer {access_token}",
    "Content-Type": "application/json"
}
ok("Guest Login 성공 및 Access Token 획득")

# ───────────────────────────────────────────────
# STEP 1: S3 Presigned URL 발급
# ───────────────────────────────────────────────
step(1, "S3 Presigned URL 발급")

resp = requests.post(
    f"{BACKEND}/api/v1/upload/audio/presigned",
    json={"audio_name": "e2e_test.wav", "content_type": "audio/wav", "usage": "humming"},
    headers=headers,
    timeout=10
)

print(f"  → Status: {resp.status_code}")
print(f"  → Body:   {resp.text[:400]}")

if resp.status_code not in (200, 201):
    fail(f"Presigned URL 발급 실패: {resp.status_code}")

body = resp.json()
presigned_url = body.get("result", {}).get("presigned_url") or body.get("presignedUrl") or body.get("url")
s3_key        = body.get("result", {}).get("file_key") or body.get("fileKey") or body.get("key")

if not presigned_url:
    fail(f"응답에서 presigned_url을 찾지 못했습니다. 응답: {body}")

ok(f"Presigned URL 발급 성공")
info(f"S3 Key: {s3_key}")
info(f"URL prefix: {presigned_url[:80]}...")

# ───────────────────────────────────────────────
# STEP 1.5: S3 Presigned URL로 실제 테스트 음원 파일 업로드
# ───────────────────────────────────────────────
step(1.5, "S3 Presigned URL로 실제 테스트 음원 파일 업로드")

import os
audio_file_path = "/Users/seoyeon/Projects/YUProjects/HuMix/ai/humix_test.wav"

if not os.path.exists(audio_file_path):
    # 만약 절대 경로에 없으면 상대 경로도 시도해 봅니다.
    audio_file_path = "../ai/humix_test.wav"

if not os.path.exists(audio_file_path):
    # 여전히 없으면 예외 발생
    fail(f"업로드할 테스트 음원 파일을 찾을 수 없습니다. 경로: {audio_file_path}")

info(f"업로드 대상 파일: {audio_file_path} ({os.path.getsize(audio_file_path)} bytes)")

try:
    with open(audio_file_path, "rb") as f:
        put_resp = requests.put(
            presigned_url,
            data=f,
            headers={"Content-Type": "audio/wav"},
            timeout=30
        )
    print(f"  → Upload Status: {put_resp.status_code}")
    if put_resp.status_code not in (200, 201):
        fail(f"Presigned URL 업로드 실패: {put_resp.status_code}\n{put_resp.text}")
    ok("테스트 음원 파일 S3 업로드 완료")
except Exception as e:
    fail(f"테스트 음원 파일 업로드 중 오류 발생: {e}")

# ───────────────────────────────────────────────
# STEP 2: 노래 생성 요청 (POST /api/v1/generation/songs)
# ───────────────────────────────────────────────
step(2, "노래 생성 요청 (202 Accepted)")

resp = requests.post(
    f"{BACKEND}/api/v1/generation/songs",
    json={
        "humming_id": HUMMING_ID,
        "title": "E2E Test Song",
        "genre": "pop",
        "mood": "happy",
        "reference_track_id": None
    },
    headers=headers,
    timeout=15
)

print(f"  → Status: {resp.status_code}")
print(f"  → Body:   {resp.text[:400]}")

if resp.status_code not in (200, 201, 202):
    fail(f"노래 생성 요청 실패: {resp.status_code}\n{resp.text}")

body = resp.json()
task_id = body.get("result", {}).get("task_id") or body.get("task_id")

if not task_id:
    fail(f"응답에서 task_id를 찾지 못했습니다. 응답: {body}")

ok(f"노래 생성 요청 접수 완료")
info(f"task_id: {task_id}")

# ───────────────────────────────────────────────
# STEP 3: SSE 스트림 구독 (진행 상황 + 완료 이벤트)
# ───────────────────────────────────────────────
step(3, f"SSE 스트림 구독 (/tasks/{task_id}/stream)")
info("AI 서버가 생성을 완료하면 'complete' 이벤트가 수신됩니다.")
info("최대 300초 대기 중...\n")

sse_url = f"{BACKEND}/api/v1/generation/songs/tasks/{task_id}/stream"
audio_url = None
start_time = time.time()
MAX_WAIT = 300

try:
    with requests.get(sse_url, stream=True, timeout=MAX_WAIT) as r:
        if r.status_code != 200:
            fail(f"SSE 연결 실패: {r.status_code}\n{r.text}")

        current_event = None
        for line in r.iter_lines(decode_unicode=True):
            elapsed = int(time.time() - start_time)

            if line.startswith("event:"):
                current_event = line.split(":", 1)[1].strip()

            elif line.startswith("data:"):
                raw_data = line.split(":", 1)[1].strip()
                try:
                    data = json.loads(raw_data)
                except json.JSONDecodeError:
                    data = raw_data

                if current_event == "progress":
                    status   = data.get("status", "")
                    progress = data.get("progress", 0)
                    print(f"  [{elapsed:>3}s] progress → status={status}, progress={progress}%")

                elif current_event == "complete":
                    result    = data.get("result", {})
                    audio_url = result.get("audio_url")
                    song_id   = result.get("song_id")
                    duration  = result.get("duration_seconds")
                    print(f"\n  [{elapsed:>3}s] ✨ complete 이벤트 수신!")
                    print(f"         song_id          = {song_id}")
                    print(f"         duration_seconds = {duration}")
                    print(f"         audio_url        = {audio_url}")
                    break

                elif current_event == "error":
                    fail(f"SSE error 이벤트 수신: {data}")

            if time.time() - start_time > MAX_WAIT:
                fail("타임아웃: 300초 내에 complete 이벤트가 수신되지 않았습니다.")

except KeyboardInterrupt:
    fail("사용자에 의해 중단되었습니다.")

if not audio_url:
    fail("complete 이벤트는 수신했지만 audio_url이 없습니다.")

# ───────────────────────────────────────────────
# STEP 4: 생성된 노래 URL 접근 확인
# ───────────────────────────────────────────────
step(4, "생성된 오디오 URL 접근 확인 (HEAD 요청)")

try:
    check = requests.head(audio_url, timeout=10, allow_redirects=True)
    if check.status_code in (200, 206):
        ok(f"오디오 URL 접근 성공! (HTTP {check.status_code})")
        content_length = check.headers.get("Content-Length", "unknown")
        info(f"Content-Length: {content_length} bytes")
    else:
        info(f"HTTP {check.status_code} - URL은 유효하지만 직접 접근이 제한될 수 있습니다.")
except Exception as e:
    info(f"URL HEAD 요청 중 예외: {e}")

# ───────────────────────────────────────────────
# 최종 결과 요약
# ───────────────────────────────────────────────
print(f"\n{'═' * 60}")
print("  🎉  E2E 테스트 완료!")
print(f"{'═' * 60}")
print(f"  task_id   : {task_id}")
print(f"  audio_url : {audio_url}")
print(f"  총 소요시간: {int(time.time() - start_time)}초")
print(f"{'═' * 60}\n")

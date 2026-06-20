from pydantic import BaseModel

class MelodyExtractRequest(BaseModel):
    local_file_path: str  # 스프링부트로부터 인계받은 오디오 파일 저장 경로

class NoteElement(BaseModel):
    Note: str
    Pitch_Hz: float
    Duration_Seconds: float
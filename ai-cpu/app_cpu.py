from fastapi import FastAPI, HTTPException, status
from pydantic import BaseModel
from vector_processor import MelodyProcessor

app = FastAPI(
    title="HuMix AI CPU Melody Extraction Service",
    description="CPU-bound FastAPI microservice for audio preprocessing and melody vector extraction",
    version="1.0.0"
)

processor = MelodyProcessor()

class MelodyExtractRequest(BaseModel):
    s3_url: str

@app.post("/api/v1/ai/melody-extract", status_code=status.HTTP_200_OK)
def extract_melody(payload: MelodyExtractRequest):
    try:
        print(f"[*] Processing melody extraction for URL: {payload.s3_url}")
        signal = processor.preprocess_audio(payload.s3_url)
        f0_data = processor.extract_f0(signal)
        n_raw = processor.hz_to_midi(f0_data)
        result_vector = processor.quantize_and_map(n_raw)
        print(f"[+] Extraction complete. Result vector length: {len(result_vector)}")
        return result_vector
    except Exception as e:
        print(f"[!] Extraction failed: {str(e)}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"AI 멜로디 추출 엔진 연산 실패: {str(e)}"
        )

@app.get("/health", status_code=status.HTTP_200_OK)
def health():
    return {"status": "ok", "service": "humix-ai-cpu"}

import numpy as np
import librosa
import scipy.signal
from fastapi import HTTPException

class MelodyProcessor:
    @staticmethod
    def preprocess_audio(file_path: str) -> np.ndarray:
        """입력 파일 환경에 관계없이 16,000Hz 모노 오디오 데이터로 다운샘플링 수임 (UT-5)"""
        try:
            signal, _ = librosa.load(file_path, sr=16000, mono=True)
            return signal.astype(np.float32)
        except Exception as e:
            raise HTTPException(status_code=400, detail=f"오디오 파일 처리 실패: {str(e)}")

    @staticmethod
    def run_pyin(signal: np.ndarray) -> list[dict]:
        """pYIN 기반 주파수 추적 (C2~C7 바운더리 제한, 0.1초 단위 그리드 트래킹)"""
        fmin = librosa.note_to_hz('C2')
        fmax = librosa.note_to_hz('C7')

        # 16000Hz * 0.1s = 1600 samples (hop_length) 설정으로 0.1초 그리드 고정
        f0, voiced_flag, voiced_prob = librosa.pyin(
            signal,
            fmin=fmin,
            fmax=fmax,
            frame_length=6400,
            hop_length=1600,
            sr=16000,
            fill_na=0.0
        )

        timestamps = np.arange(len(f0)) * 0.1
        return [{"Timestamp": t, "F0_Frequency": f, "Voicing_Prob": p}
                for t, f, p in zip(timestamps, f0, voiced_prob)]

    @staticmethod
    def convert_hz_to_midi(f0_data: list[dict]) -> np.ndarray:
        """가창 확률 임계값(0.6) 미만 무음 처리 및 표준 평균율 수식 기반 선형 MIDI 스케일 변환 (UT-6)"""
        frequencies = np.array([d["F0_Frequency"] for d in f0_data])
        probabilities = np.array([d["Voicing_Prob"] for d in f0_data])

        # 유성음 확률 임계값 0.6 미만 노이즈 필터링 마스크
        invalid_mask = (probabilities < 0.6) | (frequencies <= 0) | np.isnan(frequencies)
        safe_freq = np.where(invalid_mask, 440.0, frequencies)

        # 선형 음계 수식: n_raw = 69 + 12 * log2(f / 440)
        n_raw = 69.0 + 12.0 * np.log2(safe_freq / 440.0)
        n_raw[invalid_mask] = 0.0  # 무음 구역 스냅

        # 급격한 음정 흔들림 보정을 위해 중간값 필터(Median Filter, Kernel=3) 적용
        return scipy.signal.medfilt(n_raw, kernel_size=3)

    @staticmethod
    def quantize_and_vectorize(n_raw: np.ndarray) -> list[dict]:
        """반올림 양자화 및 동일 음정 연속 시간 누적 가공 레이어"""
        midi_notes = np.round(n_raw).astype(int)
        melody_vector = []
        if len(midi_notes) == 0:
            return melody_vector

        current_note_num = midi_notes[0]
        duration = 0.1

        for i in range(1, len(midi_notes)):
            midi_num = midi_notes[i]
            if midi_num == current_note_num:
                duration += 0.1
            else:
                melody_vector.append(MelodyProcessor._build_note_obj(current_note_num, duration))
                current_note_num = midi_num
                duration = 0.1

        melody_vector.append(MelodyProcessor._build_note_obj(current_note_num, duration))
        return melody_vector

    @staticmethod
    def _build_note_obj(midi_num: int, duration: float) -> dict:
        if midi_num == 0:
            return {"Note": "Rest", "Pitch_Hz": 0.0, "Duration_Seconds": round(duration, 1)}

        note_name = librosa.midi_to_note(midi_num, unicode=False)
        pitch_hz = float(librosa.midi_to_frequency(midi_num))
        return {
            "Note": note_name,
            "Pitch_Hz": round(pitch_hz, 2),
            "Duration_Seconds": round(duration, 1)
        }
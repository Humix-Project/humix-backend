from processor import MelodyProcessor

class MelodyService:
    def __init__(self):
        self.processor = MelodyProcessor()

    def process_melody_extraction(self, file_path: str) -> list[dict]:
        """
        [Service 역할] 오디오 파일 경로 수임 후 전처리부터 최종 벡터 포맷팅까지의 파이프라인 수행
        """
        # 1. 다운샘플링 전처리
        signal = self.processor.preprocess_audio(file_path)

        # 2. 주파수 궤적 추정
        f0_data = self.processor.run_pyin(signal)

        # 3. 선형 MIDI 음정화 및 노이즈 필터링
        n_raw = self.processor.convert_hz_to_midi(f0_data)

        # 4. 정수형 양자화 및 최종 데이터 집계
        melody_vector = self.processor.quantize_and_vectorize(n_raw)

        return melody_vector
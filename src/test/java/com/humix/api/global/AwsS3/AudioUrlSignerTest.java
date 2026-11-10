package com.humix.api.global.AwsS3;

import com.humix.api.global.AwsS3.service.AudioUrlSigner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static com.humix.api.global.AwsS3.PresignedUrlAssertions.assertPresignedGet;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class AudioUrlSignerTest {

    private static final String KEY = "audio/ab857d02-362f-4707-8389-5c80bb729304_variation.wav";

    @Autowired
    private AudioUrlSigner signer;

    @Test
    void 리전이_있는_raw_URL에서_key를_뽑아_재생용_URL로_서명한다() {
        String raw = "https://test-bucket.s3.ap-northeast-2.amazonaws.com/" + KEY;

        assertPresignedGet(signer.forPlayback(raw), KEY, 3600);
    }

    @Test
    void 리전이_없는_raw_URL도_같은_key로_서명한다() {
        String raw = "https://test-bucket.s3.amazonaws.com/" + KEY;

        assertPresignedGet(signer.forPlayback(raw), KEY, 3600);
    }

    @Test
    void AI_서버용_URL은_30분_유효하다() {
        String raw = "https://test-bucket.s3.ap-northeast-2.amazonaws.com/" + KEY;

        assertPresignedGet(signer.forAiServer(raw), KEY, 1800);
    }

    @Test
    void 쿼리스트링이_붙은_URL이_저장돼_있어도_key만_사용한다() {
        String stored = "https://test-bucket.s3.ap-northeast-2.amazonaws.com/" + KEY
                + "?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Signature=old";

        String signed = signer.forPlayback(stored);

        assertPresignedGet(signed, KEY, 3600);
        assertThat(signed).doesNotContain("X-Amz-Signature=old");
    }

    @Test
    void 값이_없으면_그대로_돌려준다() {
        assertThat(signer.forPlayback(null)).isNull();
        assertThat(signer.forPlayback(" ")).isEqualTo(" ");
    }
}

package com.humix.api.global.AwsS3;

import static org.assertj.core.api.Assertions.assertThat;

/** 서명 없는 raw URL이 아니라 지정한 key를 가리키는 presigned GET URL인지 검증한다. */
public final class PresignedUrlAssertions {

    private PresignedUrlAssertions() {
    }

    public static void assertPresignedGet(String url, String expectedKey, int expectedExpiresSeconds) {
        assertThat(url)
                .startsWith("https://test-bucket.s3.ap-northeast-2.amazonaws.com/" + expectedKey + "?")
                .contains("X-Amz-Signature=")
                .contains("X-Amz-Expires=" + expectedExpiresSeconds);
    }
}

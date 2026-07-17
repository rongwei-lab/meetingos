package com.meetingos.invitation;

import com.meetingos.common.BusinessException;
import com.meetingos.meeting.Meeting;
import com.meetingos.meeting.MeetingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvitationAssetServiceTest {

    @TempDir
    Path uploadRoot;

    @Test
    void readsDimensionsWithoutKeepingDecodedPixels() throws Exception {
        Fixture fixture = fixture(12000, 12000, 40_000_000);

        var response = fixture.service().upload(1L, png(32, 24));

        assertThat(response.width()).isEqualTo(32);
        assertThat(response.height()).isEqualTo(24);
        verify(fixture.assetRepository()).save(any(InvitationAsset.class));
    }

    @Test
    void rejectsImageWhoseDeclaredDimensionsExceedConfiguredLimit() throws Exception {
        Fixture fixture = fixture(50, 50, 2_500);

        assertThatThrownBy(() -> fixture.service().upload(1L, png(100, 100)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("图片尺寸过大");
        verify(fixture.assetRepository(), never()).save(any());
    }

    @Test
    void readsWebpExtendedHeaderWithoutOptionalImageIoPlugin() {
        Fixture fixture = fixture(12000, 12000, 40_000_000);

        var response = fixture.service().upload(1L, webpVp8x(640, 360));

        assertThat(response.width()).isEqualTo(640);
        assertThat(response.height()).isEqualTo(360);
        verify(fixture.assetRepository()).save(any(InvitationAsset.class));
    }

    @Test
    void rejectsWebpWhoseHeaderDeclaresTooManyPixels() {
        Fixture fixture = fixture(12000, 12000, 1_000_000);

        assertThatThrownBy(() -> fixture.service().upload(1L, webpVp8x(2000, 1000)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("图片尺寸过大");
        verify(fixture.assetRepository(), never()).save(any());
    }

    private Fixture fixture(int maxWidth, int maxHeight, long maxPixels) {
        MeetingRepository meetingRepository = mock(MeetingRepository.class);
        InvitationAssetRepository assetRepository = mock(InvitationAssetRepository.class);
        Meeting meeting = new Meeting();
        meeting.setName("图片测试会议");
        when(meetingRepository.findById(1L)).thenReturn(Optional.of(meeting));
        when(assetRepository.save(any(InvitationAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        InvitationAssetService service = new InvitationAssetService(
                uploadRoot.toString(),
                meetingRepository,
                assetRepository,
                maxWidth,
                maxHeight,
                maxPixels
        );
        return new Fixture(service, assetRepository);
    }

    private MockMultipartFile png(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return new MockMultipartFile("file", "image.png", "image/png", output.toByteArray());
    }

    private MockMultipartFile webpVp8x(int width, int height) {
        byte[] bytes = new byte[30];
        writeAscii(bytes, 0, "RIFF");
        writeAscii(bytes, 8, "WEBP");
        writeAscii(bytes, 12, "VP8X");
        writeLittleEndian24(bytes, 24, width - 1);
        writeLittleEndian24(bytes, 27, height - 1);
        return new MockMultipartFile("file", "image.webp", "image/webp", bytes);
    }

    private void writeAscii(byte[] target, int offset, String value) {
        byte[] source = value.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(source, 0, target, offset, source.length);
    }

    private void writeLittleEndian24(byte[] target, int offset, int value) {
        target[offset] = (byte) value;
        target[offset + 1] = (byte) (value >>> 8);
        target[offset + 2] = (byte) (value >>> 16);
    }

    private record Fixture(InvitationAssetService service, InvitationAssetRepository assetRepository) {
    }
}

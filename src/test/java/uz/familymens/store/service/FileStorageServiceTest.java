package uz.familymens.store.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class FileStorageServiceTest {

    private static byte[] bytes(int... b) {
        byte[] r = new byte[Math.max(12, b.length)];
        for (int i = 0; i < b.length; i++) r[i] = (byte) b[i];
        return r;
    }

    @Test
    void acceptsRealImages() {
        assertThat(FileStorageService.detectImageExtension(bytes(0xFF, 0xD8, 0xFF, 0xE0))).isEqualTo(".jpg");
        assertThat(FileStorageService.detectImageExtension(bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A))).isEqualTo(".png");
        assertThat(FileStorageService.detectImageExtension(bytes('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'))).isEqualTo(".webp");
    }

    @Test
    void rejectsDisguisedFiles() {
        assertThat(FileStorageService.detectImageExtension(
                "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8))).isNull();
        assertThat(FileStorageService.detectImageExtension(
                "<svg xmlns='http://www.w3.org/2000/svg' onload='alert(1)'/>".getBytes(StandardCharsets.UTF_8))).isNull();
        assertThat(FileStorageService.detectImageExtension(bytes('G', 'I', 'F', '8', '9', 'a'))).isNull();
        assertThat(FileStorageService.detectImageExtension(new byte[3])).isNull();
        assertThat(FileStorageService.detectImageExtension(null)).isNull();
    }
}

package uz.familymens.store.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import uz.familymens.store.config.StoreProperties;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);
    public static final String URL_PREFIX = "/uploads/";
    private static final long MAX_DOWNLOAD_BYTES = 15L * 1024 * 1024;

    private final Path root;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public FileStorageService(StoreProperties props) throws IOException {
        this.root = Path.of(props.uploadDir()).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    public Path root() {
        return root;
    }

    /**
     * Admin yuklagan rasmni saqlaydi va ochiq URL qaytaradi (/uploads/uuid.jpg).
     * Fayl turi brauzer yuborgan Content-Type bo'yicha emas, faylning o'zi (magic bytes)
     * bo'yicha aniqlanadi — "rasm" nomi ostida HTML/skript yuklab bo'lmaydi.
     */
    public String store(MultipartFile file) {
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Rasmni o'qib bo'lmadi", e);
        }
        String ext = detectImageExtension(data);
        if (ext == null) {
            throw new IllegalArgumentException("Faqat JPG, PNG yoki WEBP rasm yuklash mumkin");
        }
        String filename = UUID.randomUUID() + ext;
        try {
            Files.write(root.resolve(filename), data);
        } catch (IOException e) {
            throw new IllegalStateException("Rasmni saqlab bo'lmadi", e);
        }
        return URL_PREFIX + filename;
    }

    /** JPEG / PNG / WEBP imzosini tekshiradi; boshqa har qanday fayl uchun null. */
    static String detectImageExtension(byte[] d) {
        if (d == null || d.length < 12) return null;
        if ((d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) return ".jpg";
        if ((d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G'
                && d[4] == 0x0D && d[5] == 0x0A && d[6] == 0x1A && d[7] == 0x0A) return ".png";
        if (d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') return ".webp";
        return null;
    }

    /**
     * Telegram CDN dagi rasmni yuklab oladi (Telegram havolalari vaqt o'tib eskiradi).
     *
     * @param baseName kengaytmasiz nom, masalan "tg/21086-0"
     */
    public String storeFromUrl(String url, String baseName) throws IOException, InterruptedException {
        URI uri = URI.create(url);
        String host = uri.getHost() == null ? "" : uri.getHost();
        if (!"https".equals(uri.getScheme()) || !(host.endsWith("telesco.pe") || host.endsWith("telegram.org"))) {
            throw new IllegalArgumentException("Unexpected image host: " + host);
        }
        HttpResponse<byte[]> res = http.send(
                HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        if (res.statusCode() != 200) throw new IOException("HTTP " + res.statusCode());
        byte[] body = res.body();
        if (body.length == 0 || body.length > MAX_DOWNLOAD_BYTES) throw new IOException("Bad image size " + body.length);

        String ext = detectImageExtension(body);
        if (ext == null) throw new IOException("Not an image");
        Path target = root.resolve(baseName + ext).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("Bad path");
        Files.createDirectories(target.getParent());
        Files.write(target, body);
        return URL_PREFIX + root.relativize(target).toString().replace('\\', '/');
    }

    public void deleteIfLocal(String url) {
        if (url == null || !url.startsWith(URL_PREFIX)) return;
        Path file = root.resolve(url.substring(URL_PREFIX.length())).normalize();
        if (!file.startsWith(root)) return;
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Could not delete {}", file, e);
        }
    }
}

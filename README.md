# Family store for men

Nike, Adidas, Puma: AQSH, Turkiya va Angliyadan original kiyim va poyabzal **buyurtma asosida**.
**Java 17+ · Spring Boot 3.5 (Web MVC) · Thymeleaf · Spring Data JPA · Spring Security · Jsoup**

- Telegram kanal: https://t.me/familystoreformen
- Instagram: https://www.instagram.com/family.store.men/
- Buyurtma: @familystoremen_admin, @familystoremen, +998 33 380 01 00

## Ishga tushirish

```bash
mvn spring-boot:run
```

- Sayt: http://localhost:8080
- Admin panel: http://localhost:8080/admin (login `admin` / `admin123`, albatta o'zgartiring)

Birinchi ishga tushganda sayt Telegram kanalning oxirgi ~100 postini avtomatik yuklaydi. Bu 1–2 daqiqa davom etadi.

## Telegram kanal → sayt sinxronizatsiyasi

Sayt kanalning ochiq sahifasini (`t.me/s/familystoreformen`) **har 1 daqiqada** o'qiydi. Bot yoki token kerak emas.

| Kanalda | Saytda |
|---|---|
| Yangi post: rasm(lar) + narx | Yangi mahsulot. Rasmlar serverga yuklab olinadi |
| Post tahrirlandi: narx o'zgardi | Mahsulot narxi va tavsifi yangilanadi |
| Post o'chirildi | Mahsulot saytdan yashiriladi |
| Narxsiz post (so'rovnoma, e'lon) | E'tiborsiz qoldiriladi |

**Post izohidan nima olinadi:**
- **Nom:** izohning birinchi qatori.
- **Brend:** matndagi brend nomi (Nike, Adidas, Puma, New Balance ...).
- **Joriy narx:** **qalin** yozilgan `$85`.
- **Asl narx:** ~~chizilgan~~ `$165`. Bo'lsa, mahsulotga "Chegirma" belgisi qo'yiladi.
- **Kategoriya:** matndagi kalit so'z (krossovka, kurtka, xudi, shim ...).

> **Maslahat:** izohning birinchi qatoriga modelni yozing, masalan `Nike Air Max 90 krossovka`. Shunda saytda mahsulot nomi aniq chiqadi va kategoriya ham avtomatik aniqlanadi.
> Hozirgi format (`Adidas original Amerika`) ham ishlaydi, faqat hamma mahsulot nomi bir xil bo'ladi.

**Admin panel → Mahsulotlar:**
- **"Hozir yangilash"** tugmasi 1 daqiqani kutmasdan kanalni tekshiradi.
- **"Eski postlarni yuklash"** kanal arxividagi eskiroq postlarni qo'shadi.
- Har bir mahsulotni tahrirlash mumkin: nom, o'lchamlar, kategoriya.
- Admin yashirgan yoki o'chirgan Telegram mahsuloti sinxronizatsiya tomonidan qayta ochilmaydi.

Sozlamalar `application.properties` faylida: `store.sync.*`.

## Instagram

Instagram ochiq API bermaydi. Postlarni avtomatik o'qish uchun Meta'ning Instagram Graph API'si kerak:
Business yoki Creator akkaunt, Facebook ilovasi va access token.
Hozircha saytda Instagram profiliga havolalar bor. Kanal va Instagram'ga bir xil postlar qo'yilsa, sayt Telegram orqali yangilanib turadi.

## Buyurtma jarayoni

1. Mijoz mahsulotni tanlaydi va o'lchamni yozadi yoki tanlaydi.
2. Savatchada jami summa, **20% oldindan to'lov** summasi va "kargo alohida" eslatmasi ko'rinadi.
3. Buyurtma bazaga yoziladi va admin panelda ko'rinadi.
4. Bot sozlangan bo'lsa, xabar Telegramga ham keladi. Xabarda kanal postiga havola bo'ladi.

**Telegram bot (ixtiyoriy):**
1. @BotFather'da bot yarating.
2. @familystoremen_admin akkauntidan botga `/start` yozing.
3. Chat ID'ni @userinfobot orqali bilib oling.
4. Muhit o'zgaruvchilarini o'rnating: `TELEGRAM_BOT_TOKEN=...` va `TELEGRAM_CHAT_ID=...`

## Tuzilish

```
src/main/java/uz/familymens/store/
├── config/       StoreProperties, Security, Web (til, /uploads, scheduler)
├── model/        Product, CustomerOrder, OrderItem, Category, Brands ...
├── repository/   Spring Data JPA
├── service/      Cart, CartService, ProductService, OrderService, TelegramNotifier, FileStorageService
├── telegram/     TelegramChannelClient (kanal sahifasini o'qish), TelegramSyncService (sinxronizatsiya)
└── web/          ShopController, CartController, admin/AdminController, dto/
src/main/resources/
├── templates/    Thymeleaf sahifalari
├── static/       css/style.css, js/app.js
└── messages*.properties   o'zbek / rus tarjimalar
```

## Ma'lumotlar bazasi va build

- **Standart:** H2 fayl bazasi (`./data/`). Rasmlar `./uploads/` papkasida saqlanadi. Ikkala papkani ham zaxiralab turing.
- **PostgreSQL:** `--spring.profiles.active=prod` va `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`.

```bash
mvn clean package
java -jar target/family-mens-store-1.0.0.jar
```

## Serverga joylash (Oracle Cloud Free Tier / istalgan Ubuntu)

1. Oracle Cloud'da **Ubuntu 24.04** server yarating: *VM.Standard.A1.Flex*, 2 OCPU, 12 GB RAM.
2. **Security List** da 80 va 443 TCP portlarini oching.
3. Kompyuterdan (Git Bash) yuboring:
   ```bash
   SSH_KEY=~/oracle.key SERVER=1.2.3.4 ADMIN_PASSWORD='kuchli-parol' \
   BOT_TOKEN=... CHAT_ID=... DOMAIN=familystore.duckdns.org EMAIL=you@mail.com \
   bash deploy/deploy.sh
   ```
   `DOMAIN` berilmasa, sayt `http://SERVER_IP` orqali ochiladi.
4. Keyingi yangilashlar uchun `SSH_KEY=... SERVER=... bash deploy/deploy.sh` yetarli. Serverdagi sozlamalar, baza va rasmlar saqlanib qoladi.

**`deploy/setup.sh` serverda quyidagilarni qiladi:**
- Java 21 va Nginx'ni o'rnatadi, firewall'ni sozlaydi.
- systemd servisini yaratadi: alohida foydalanuvchi, qayta yuklanganda o'zi ishga tushadi.
- Nginx reverse proxy'ni sozlaydi va Let's Encrypt HTTPS sertifikatini oladi.
- Kundalik zaxira nusxa yaratadi: `/opt/family-store/backups`, 14 kun saqlanadi.

**Serverdagi yo'llar:**
- Ilova: `/opt/family-store`
- Sozlamalar: `/opt/family-store/config/application.properties`
- Loglar: `sudo journalctl -u family-store -f`

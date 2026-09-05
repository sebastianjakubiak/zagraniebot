# Zagranie Typer

Niezależny importer historyczny dla zagranie.com.

## Wymagania

- Java 21
- Maven 3.9+
- PostgreSQL

## Baza

```sql
CREATE DATABASE zagranie_typer;
```

Domyślne połączenie:

```text
jdbc:postgresql://localhost:5432/zagranie_typer
user=postgres
password=postgres
```

Można nadpisać przez env:

```bash
export DB_URL='jdbc:postgresql://localhost:5432/zagranie_typer'
export DB_USER='postgres'
export DB_PASSWORD='postgres'
```

## Build

```bash
mvn clean test package
```

## Backfill 730 dni

```bash
java -jar target/zagranie-typer-0.1.0-SNAPSHOT.jar backfill \
  --author-id=8560 \
  --author-name='Patryk Domagala' \
  --days=730
```

## Jawny zakres

```bash
java -jar target/zagranie-typer-0.1.0-SNAPSHOT.jar backfill \
  --author-id=8560 \
  --author-name='Patryk Domagala' \
  --from=2024-08-13 \
  --to=2026-08-13
```


## Live polling nowych typów

Domyślna whitelista zawiera tylko Mateusza Domańskiego:

```bash
export ZAGRANIE_ALLOWED_AUTHOR_IDS='8033'
```

Kolejnych autorów można dodać bez zmiany kodu:

```bash
export ZAGRANIE_ALLOWED_AUTHOR_IDS='8033,52'
```

Bezpieczny dry-run. Pobiera i parsuje kandydatów, porównuje ich z aktualną bazą,
ale nie wykonuje żadnych zapisów:

```bash
java -jar target/zagranie-typer-0.1.0-SNAPSHOT.jar sync-new-tips \
  --dry-run=true
```

W poprawnym dry-runie podsumowanie kończy się:

```text
mode=DRY_RUN
DATABASE_WRITES=0
```

Jednorazowy, idempotentny live sync:

```bash
java -jar target/zagranie-typer-0.1.0-SNAPSHOT.jar sync-new-tips
```

Zagranie.com zwraca 404 dla REST query po `modified_after`, dlatego live sync
używa wspieranego lekkiego indeksu po dacie publikacji, filtruje autora lokalnie
i porównuje `modified_at` każdego kandydata z bazą. Przy normalnej pracy zawsze
ponownie skanuje ostatnie 72 godziny publikacji, żeby wychwycić edycje świeżych
artykułów. Po dłuższej przerwie nadrabia od ostatniego `modified_at` w DB.
Komenda jest celowo jednorazowa, żeby na Lenovo odpalać ją cyklicznie
przez `systemd timer` albo cron.

Opcjonalna konfiguracja:

```bash
export ZAGRANIE_POLL_BOOTSTRAP_LOOKBACK_HOURS='720'
export ZAGRANIE_POLL_RECENT_SCAN_HOURS='72'
export ZAGRANIE_POLL_OVERLAP_SECONDS='120'
```


### Telegram

Powiadomienia są domyślnie wyłączone. Na Lenovo użyj tego samego tokena bota
i tego samego `chat_id`/kanału co działający ZT bot:

```bash
export TELEGRAM_ENABLED='true'
export TELEGRAM_BOT_TOKEN='...ten sam token co ZT bot...'
export TELEGRAM_CHAT_ID='...ten sam chat/channel id co ZT bot...'
export TELEGRAM_TIPSTER_NAME='Mateusz Domański'
```

Na świeżej bazie historyczny bootstrap nie wysyła wiadomości, żeby nie zasypać
kanału starymi typami:

```bash
export TELEGRAM_NOTIFY_BOOTSTRAP='false'
```

Dry-run nigdy nie wysyła Telegrama, nawet jeśli `TELEGRAM_ENABLED=true`.

Format wiadomości:

```text
🔥 NOWY TYP — Mateusz Domański

Korona – Wisła Kraków: Typy i kursy
SINGLE @1.6
• Obie drużyny strzelą gole @1.6 — sts

https://zagranie.com/...
```

### Lenovo / systemd

Gotowe pliki są w `deploy/systemd/`:

- `zagranie-domanski-poller.service` — jednorazowy live sync,
- `zagranie-domanski-poller.timer` — uruchomienie co 60 sekund,
- `zagraniebot.env.example` — wzór env bez sekretów.

Domyślne ścieżki w unitach:

```text
/opt/zagraniebot
/etc/zagraniebot/zagraniebot.env
```

Unit zakłada użytkownika systemowego `zagraniebot`. Jeżeli istniejący ZT bot
na Lenovo działa pod innym użytkownikiem albo z innych ścieżek, przy wdrożeniu
wyrównaj `User`, `Group`, `WorkingDirectory`, `EnvironmentFile` i `ExecStart`
do istniejącej konfiguracji zamiast tworzyć drugi zestaw sekretów.

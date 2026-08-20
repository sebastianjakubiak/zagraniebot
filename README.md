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

# Music Service

Ein REST-basierter Musikverwaltungsservice, entwickelt mit Vert.x und MariaDB.

## Projektbeschreibung

Dieser Service ermöglicht die Verwaltung von Benutzern und Songs über eine RESTful API. Benutzer können Songs zugewiesen werden, und der Service bietet volle CRUD-Operationen für beide Entitäten.

## Technologie-Stack

- **Java** - Programmiersprache
- **Vert.x 5.0.6** - Asynchrones Web-Framework
- **MariaDB** - Datenbank
- **Maven** - Build-Management
- **JUnit 5** - Testing

## Voraussetzungen

- Java 11 oder höher
- MariaDB Datenbank
- Maven (oder verwenden Sie die mitgelieferten Maven Wrapper)

## Installation

### 1. Datenbank einrichten

Erstellen Sie eine MariaDB-Datenbank und führen Sie das SQL-Schema aus:

```bash
mysql -u your_user -p < src/main/java/musicService.sql
```

### 2. Konfiguration

Bearbeiten Sie die Datenbankverbindungsdetails in `MainVerticle.java`:

```java
private static final String DB_URL = "jdbc:mariadb://<your.database.url>";
private static final String DB_USER = "<your_db_user>";
private static final String DB_PASSWORD = "<your_db_password>";
```

### 3. Build

```bash
./mvnw clean package
```

## Ausführung

### Mit Maven Wrapper

```bash
./mvnw clean compile exec:java
```

### Mit JAR

```bash
java -jar target/musicService-1.0.0-SNAPSHOT-fat.jar
```

Der Server startet auf Port 8888.

## API Endpunkte

### Benutzer-Verwaltung

#### Benutzer anlegen
```http
POST /users
Content-Type: application/json

{
  "username": "MaxMusti",
  "email": "max@mustermann.de"
}
```

#### Alle Benutzer abrufen
```http
GET /users
```

#### Einzelnen Benutzer abrufen
```http
GET /users/:id
```

#### Benutzer löschen
```http
DELETE /users/:id
```

### Song-Verwaltung

#### Song anlegen
```http
POST /songs
Content-Type: application/json

{
  "title": "Song Title",
  "artist": "Artist Name"
}
```

#### Alle Songs abrufen
```http
GET /songs
```

#### Song löschen
```http
DELETE /songs/:id
```

#### Song einem Benutzer zuweisen
```http
PUT /users/:userId/songs/:songId
```

#### Song-Zuweisung entfernen
```http
DELETE /users/:userId/songs/:songId
```

## Projektstruktur

```
HA3/
├── src/
│   ├── main/
│   │   └── java/
│   │       ├── musicService.http      # HTTP-Testdatei
│   │       ├── musicService.sql       # Datenbank-Schema
│   │       └── de/thm/mni/pi2/musicService/
│   │           ├── MainVerticle.java  # Haupt-Server-Klasse
│   │           └── *Exception.java    # Exception-Klassen
│   └── test/
│       └── java/
│           └── de/thm/mni/pi2/musicService/
│               └── TestMainVerticle.java
├── pom.xml                            # Maven-Konfiguration
└── README.md                          # Diese Datei
```

## Testing

Führen Sie die Tests aus mit:

```bash
./mvnw test
```

Sie können auch die HTTP-Testdatei `musicService.http` verwenden, um die API manuell zu testen (mit VS Code REST Client Extension).

## Exception-Handling

Der Service verwendet spezifische Exception-Klassen für verschiedene Fehlerszenarien:

- `UserAlreadyExistsException` - Benutzer existiert bereits
- `UserNotExistsException` - Benutzer nicht gefunden
- `SongAlreadyExistsException` - Song existiert bereits
- `SongNotExistsException` - Song nicht gefunden
- `AssertionAlreadyExistsException` - Zuweisung existiert bereits
- `AssertionNotExistsException` - Zuweisung nicht gefunden
- `NoUsersFoundException` - Keine Benutzer vorhanden
- `NoSongsFoundException` - Keine Songs vorhanden

## Entwicklung

### Maven Wrapper

Das Projekt enthält Maven Wrapper-Skripte:
- `mvnw` (Linux/Mac)
- `mvnw.cmd` (Windows)

Diese ermöglichen das Ausführen von Maven-Befehlen ohne lokale Maven-Installation.

## Lizenz

Dieses Projekt wurde für Bildungszwecke an der Technischen Hochschule Mittelhessen (THM) entwickelt.


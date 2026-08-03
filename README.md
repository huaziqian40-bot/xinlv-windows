# Xinlv (心履) — Windows Desktop Client

A native Windows desktop client for the Xinlv mood-tracking and mental-health companion platform. Built with JavaFX and Maven. Works offline and syncs automatically when connected — shares the same REST API and sync rules as the web app and Android client.

## Features

- **Login / Register / Guest Mode** — Three ways to get started
- **Mood Calendar** — Month view with daily mood recording (including intensity slider)
- **Smart Recommendations** — Mood-based推 of music, activities, psychology tips
- **AI Confidant** — Anonymous chat with DeepSeek, crisis interception
- **Profile Page** — Streak/badges/total records, theme customization
- **Offline Sync** — Record moods offline, auto-sync when online
- **Theme System** — 4 preset themes + custom accent color
- **Mood Visuals** — Mood affects UI background color + rain effect

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 17 |
| UI Framework | JavaFX 17 |
| Build Tool | Maven |
| Local Database | SQLite (JDBC) |
| Networking | HttpURLConnection |
| Packaging | jpackage (generates exe installer) |

## Development Setup

### Requirements
- JDK 17+
- Maven 3.6+
- JavaFX SDK 17 (included automatically by jpackage)

### Build & Run

```bash
git clone <your-repo-url>
cd windows

# Compile and run
mvn clean javafx:run

# Package as exe installer
mvn clean package
jpackage --input target/ --name 心履 --main-jar moodtree-client-1.0.2.jar --main-class com.moodtree.client.Main --type exe
```

### Packaging Script
Double-click `package.bat` to run the full packaging workflow automatically.

## Project Structure

```
windows/
├── pom.xml
├── package.bat
├── run.bat
├── src/main/java/com/moodtree/client/
│   ├── Main.java              # Entry point
│   ├── AppContext.java        # Global context
│   ├── Config.java            # Config management
│   ├── api/ApiClient.java     # REST API client
│   ├── db/LocalDb.java        # SQLite database
│   ├── model/                 # MoodEntry, MoodMeta
│   ├── sync/SyncEngine.java   # Offline sync engine
│   └── ui/                    # JavaFX views
│       ├── LoginView.java
│       ├── MainShell.java
│       ├── CalendarView.java
│       ├── MoodDialog.java
│       ├── RecommendView.java
│       ├── ChatView.java
│       ├── MeView.java
│       └── Theme.java
```

## Sync Mechanism

Consistent with server and Android client:
- **UUID dedup** — Each record has a unique UUID
- **LWW (Last-Write-Wins)** — Latest timestamp wins
- **Tombstone** — Deletion marks `deleted=true`, never truly removed
- **Dirty flag** — Offline edits marked dirty, pushed on reconnect
- **Incremental pull** — Sync by `since` timestamp

## License

Personal learning and non-commercial use only.
# SkillsPlugin

A Minecraft Paper plugin for a comprehensive skills system.

## Project Structure

```
SkillsPlugin/
├── pom.xml                                    # Maven build configuration
├── build.sh                                   # Build script
├── README.md                                  # This file
└── src/
    └── main/
        ├── java/
        │   └── com/
        │       └── thornily/
        │           └── skills/
        │               ├── SkillsPlugin.java           # Main plugin class
        │               ├── commands/
        │               │   ├── SkillsCommand.java      # /skills command
        │               │   ├── SkillSetCommand.java    # /skillset command
        │               │   └── SkillResetCommand.java  # /skillreset command
        │               ├── listeners/
        │               │   └── SkillGainListener.java  # Event listeners for skill gains
        │               ├── managers/
        │               │   └── SkillManager.java       # Manages player skills data
        │               ├── models/
        │               │   ├── Skill.java              # Skill enum/types
        │               │   ├── SkillData.java          # Individual skill data
        │               │   └── PlayerSkills.java       # Player's all skills
        │               └── utils/
        │                   └── SkillUtils.java         # Utility methods
        └── resources/
            ├── plugin.yml                     # Plugin metadata
            └── config.yml                     # Configuration file

```

## Building

### Prerequisites
- Java 21 or higher
- Maven 3.6+

### Build Commands

```bash
# Using build script (recommended)
./build.sh

# Using Maven directly
mvn clean package
```

The compiled JAR will be in `target/SkillsPlugin-1.0.0.jar`

## Installation

1. Build the plugin using the build script or Maven
2. Copy `target/SkillsPlugin-1.0.0.jar` to your server's `plugins/` directory
3. Restart the server

## Development

### File Descriptions

- **SkillsPlugin.java**: Entry point, handles plugin lifecycle
- **commands/**: Command handlers for player and admin commands
- **listeners/**: Event listeners for gaining XP from player actions
- **managers/**: Business logic and data management
- **models/**: Data structures for skills, levels, and player data
- **utils/**: Helper methods and utilities

### Adding Dependencies

Edit `pom.xml` to add additional dependencies in the `<dependencies>` section.

### Testing

Test the plugin by:
1. Building with `./build.sh`
2. Copying to `../../plugins/`
3. Restarting the server
4. Checking logs for errors

## License

Private project

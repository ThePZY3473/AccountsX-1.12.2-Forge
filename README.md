# AccountsX Forge 1.12.2

AccountsX adapted for Minecraft Java 1.12.2 Forge.

## Build

Requirements:

- JDK 17
- Network access for Gradle dependency downloads

Build the reobfuscated Forge mod jar:

```sh
./gradlew reobfJar
```

The mod jar is generated at:

```text
build/libs/AccountsX-1.12.2-Forge.jar
```

## Notes

This branch targets Forge 1.12.2 only. Old multi-version adapter sources and local test files were removed from the working tree and backed up outside the repository before publishing.

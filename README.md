# AutoSell Mod

Client-side Fabric mod that adds an "Auto Sell" button to chest UIs and automates sending `/ah sell <count>` for each stack in the chest when triggered.

Features:
- Chest-side button that opens settings
- Sell every non-empty stack in the chest when you press "Sell"
- Configurable delay (default 500 ms)

Build & install
1. Install Java JDK 21 and ensure JAVA_HOME is set.
2. Clone this repo:
   git clone https://github.com/Gabezz7722/autosell-mod.git
3. Build the mod:
   ./gradlew build
4. Copy the generated JAR from `build/libs/` into your Minecraft `mods/` folder.
5. Run Minecraft with Fabric Loader 0.19.5 for Minecraft 26.2.

Warnings
- Client automation can be against server rules. Use only on servers that allow it and test on a private server first.

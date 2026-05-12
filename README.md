# TalungCommandSender

This Android application connects to a device using OOB NFC pairing and communicates using BLE NUS (Nordic UART Service). It uses a data frame protocol based on the ChameleonUltra dataframe.c implementation.

## Features
- OOB NFC pairing for secure BLE connection
- BLE NUS communication for sending/receiving commands
- Menu UI for selecting commands
- Data frame protocol for robust communication

## Project Structure
- `app/src/main/java/com/example/talungcommandsender/` — Kotlin source code
- `app/src/main/res/layout/` — UI layouts
- `app/src/main/res/values/` — String resources

## References
- Data frame protocol: [ChameleonUltra dataframe.c](https://github.com/RfidResearchGroup/ChameleonUltra/blob/main/firmware/application/src/utils/dataframe.c)

---

Replace this README with more details as the project evolves.

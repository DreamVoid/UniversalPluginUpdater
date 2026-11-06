# Backup upgrade strategy

## Scope and configuration

This is an optional fourth strategy using the existing update channels, downloader, commands, and platform adapters. The shipped default remains native. Java 17 compatibility is retained. Other strategies retain their previous behavior.

Set updater.strategy to backup and updater.backup.directory to .disabled (or another directory relative to the server's plugins directory). Absolute paths, parent traversal, symlinks in the backup directory chain, and different filesystems are rejected. Do not put backups in a location scanned recursively by another plugin loader.

The new filename is determined by the existing downloader and its filename / force-filename settings. With normal settings, it is the distribution filename when available; the downloader's existing fallback is unchanged. The **old backup filename** is always preserved. Nonportable JAR filenames, including separators, Windows device names and alternate-stream syntax, are rejected.

## Transaction sequence

1. Read the current platform's descriptor: Paper paper-plugin.yml (fallback plugin.yml), Bukkit plugin.yml, BungeeCord bungee.yml (fallback plugin.yml), or Velocity velocity-plugin.json.
2. Validate the downloaded JAR by reading every entry and checking CRCs. Metadata is limited to 1 MiB and expanded JAR contents to 1 GiB. Match the descriptor's name/id case-insensitively to the requested plugin ID.
3. Reject an occupied destination and another root-level JAR with the same platform plugin ID. Do not overwrite an unrelated file.
4. Copy the new JAR to .disabled/<timestamp>-<UUID>/new.bin on the same filesystem, verify it again, and force its contents to storage.
5. Persist journal.properties with plugin ID, descriptor type, old/new filenames, SHA-256 hashes, and transaction phase. Maintain an index in the UPU data directory's backup-journals/ so recovery can find transactions after backup.directory changes.
6. At execution, recheck the staged JAR, old JAR hash, and collisions. Persist MOVING_OLD before moving the old JAR to the transaction directory with its original filename.
7. Persist INSTALLING before moving new.bin into plugins without overwrite. Verify the installed hash, then persist COMMITTED.
8. On an ordinary replacement error, move back the transaction's new file only if its hash still matches, restore the old JAR without overwrite, and persist ROLLED_BACK.

Journal replacements require an atomic rename; if unavailable, the operation fails before continuing. Files and journal contents are forced to storage; this is process-interruption recovery, not a guarantee against every power-loss/filesystem failure.

## Scheduling and concurrency

Without --now, preparation happens immediately, but replacement waits for LifeCycle.unload(). This is a JVM shutdown/plugin unload callback, not a post-exit process. Disable/reload paths that invoke that callback may also apply reservations.

With --now and allow-upgrade-now enabled, the same transaction executes immediately. No unloadPlugin call, disable, reload, restart, or classloader manipulation is performed. Loaded code continues running, but moving a live JAR can affect lazy class/resource access and some operating systems lock JARs. A restart is required to load the update.

The existing command behavior for disallowed --now is retained: warn and schedule instead. Automatic update commands still use the existing command/manager path.

One UPU process permits only one pending or completed replacement per plugin ID and destination filename. A queued plugin cannot also be immediately replaced. This avoids using a running plugin's stale JAR path for a second update. Scheduling and shutdown execution are serialized; new backup work is rejected after shutdown execution starts.

Each plugin is an independent transaction. In a batch, a failed plugin does not undo another plugin's completed update. A transaction execution or recovery error blocks further backup preparation in that process; inspect the logs and journals before restarting/retrying.

On Paper, if the adapter returns a remapped cache JAR, the strategy searches the actual plugins directory for a unique matching descriptor ID. It refuses ambiguity or an installed plugin whose original root-level JAR cannot be located.

## Interrupted operations

At UPU startup, indexed journals are checked regardless of the currently selected update strategy:

- PREPARED: cancel an unexecuted reservation. It is not silently applied on a later shutdown. The original JAR remains and staged files are retained.
- MOVING_OLD / INSTALLING: restore a missing original only if the old backup hash matches and both original and destination paths are empty.
- INSTALLING with the exact installed new hash and expected old backup: finish recording COMMITTED.
- Ambiguous or modified paths: preserve all files, report original/backup/destination paths, and block backup updates. Never overwrite unknown files.
- Terminal journals are retained, as are backups. There is no automatic cleanup.

Recovery is idempotent. UPU startup is not guaranteed to run before other plugins load. Restoring a missing JAR can therefore require another restart. UPU cannot perform its own recovery if its JAR is missing or it cannot start; use the journal and preserved backup for manual restoration in that case.

An unavailable backup strategy never switches to native, including for scheduled operations. Existing strategies keep their existing fallback rules.

## Recovery boundaries

Automatic restoration covers file replacement failure, not a plugin's failure to enable on the next boot, dependency incompatibility, or configuration/database migrations. If restoration itself fails, preserve the transaction directory and index; the log reports the original, backup and destination paths. Stop the server before manual file recovery.

Manual rollback commands, backup cleanup, server restart, hot reload, and update-provider changes are outside this feature.

## Validation

The automated tests cover descriptors for all adapter families, renamed/same-name JARs, unique backups, wrong IDs, invalid JARs, filename/path rejection, occupied destinations, duplicate plugin IDs, immutable staging, revalidation, simulated locks/placement/restore failures, interruption before/after placement, ambiguous recovery, queued/immediate scheduling, duplicate reservations, and no native fallback.

Run mvnw.cmd -B package on Windows or ./mvnw -B package on Unix with JDK 21. The source target remains Java 17. Automated file tests do not substitute for live Paper/Folia/proxy tests or an operating-system-specific open-JAR test.

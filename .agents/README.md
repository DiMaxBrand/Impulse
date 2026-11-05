# Agent resources

Repository instructions live in the root `AGENTS.md`. Read `TODO.md` for the backlog.

`skills/` contains the existing Android skills in their standard `SKILL.md` format.
These third-party resources retain their original attribution and service names.

`scripts/` contains portable helpers, invoked explicitly when needed:

- `read-context.sh`: print `AGENTS.md` and `TODO.md` after starting or resuming work.
- `session-start.sh`: install the Android SDK if needed and resolve the compile task graph.
- `install-android-sdk.sh`: install SDK components into `SDK_ROOT`.
- `spotless-java.sh`: apply the project's Java formatter.

The former Claude-specific hook configuration was removed. Renaming that JSON would
not register hooks in Codex; required agent behavior is specified in `AGENTS.md`.
Use existing managed-environment toolchains and proxy settings before running setup.

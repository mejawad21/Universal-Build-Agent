
# Auto Build Agent

## One-time setup

Upload this repository to GitHub only once.

## Every future build

1. Open the repository's main page.
2. Tap **Add file → Upload files**.
3. Upload any Android or Windows source ZIP.
4. Tap **Commit changes**.

That is all.

You do not need to:

- Rename the ZIP.
- Delete the previous ZIP.
- Put it in a special folder.
- Edit a workflow.
- Run the workflow manually.

The agent automatically selects the ZIP uploaded in the latest commit.

## Output

Open the successful Actions run and download:

- `READY-APK`
- `READY-WINDOWS-EXE`
- `READY-APPLICATION`

## Supported source ZIPs

- Android Gradle projects.
- Python desktop applications.
- ZIPs already containing APK or EXE files.

The ZIP can contain one top-level project folder. The agent detects it.

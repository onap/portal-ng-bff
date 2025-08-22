<!--
SPDX-License-Identifier: Apache-2.0
SPDX-FileCopyrightText: 2025 The Linux Foundation
-->

# github2gerrit-python

Submit a GitHub pull request to a Gerrit repository, implemented in Python.

This action is a drop‑in replacement for the shell‑based
`lfit/github2gerrit` composite action. It mirrors the same inputs,
outputs, environment variables, and secrets so you can adopt it without
changing existing configuration in your organizations.

The tool expects a `.gitreview` file in the repository to derive Gerrit
connection details and the destination project. It uses `git` over SSH
and `git-review` semantics to push to `refs/for/<branch>` and relies on
Gerrit `Change-Id` trailers to create or update changes.

Note: the initial versions focus on compatibility and clear logging.
The behavior matches the existing action, and this implementation
refactors it to Python with typed modules and test support.

## How it works (high level)

- Discover pull request context and inputs.
- Read `.gitreview` for Gerrit host, port, and project.
- Set up `git` user config and SSH for Gerrit.
- Prepare commits:
  - one‑by‑one cherry‑pick with `Change-Id` trailers, or
  - squash into a single commit and keep or reuse `Change-Id`.
- Optionally replace the commit message with PR title and body.
- Push with a topic to `refs/for/<branch>` using `git-review` behavior.
- Query Gerrit for the resulting URL, change number, and patchset SHA.
- Add a back‑reference comment in Gerrit to the GitHub PR and run URL.
- Comment on the GitHub PR with the Gerrit change URL(s).
- Optionally close the PR (mirrors the shell action policy).

## Requirements

- Repository contains a `.gitreview` file. If you cannot provide it,
  you must pass `GERRIT_SERVER`, `GERRIT_SERVER_PORT`, and
  `GERRIT_PROJECT` via the reusable workflow interface.
- SSH key for Gerrit and known hosts are available to the workflow.
- The default `GITHUB_TOKEN` is available for PR metadata and comments.
- The workflow runs with `pull_request_target` or via
  `workflow_dispatch` using a valid PR context.

## Usage

This action runs as part of a workflow that triggers on
`pull_request_target` and also supports manual runs via
`workflow_dispatch`.

Minimal example:

```yaml
name: github2gerrit-python

on:
  pull_request_target:
    types: [opened, reopened, edited, synchronize]
    branches: [master, main]
  workflow_dispatch:

permissions:
  contents: read
  pull-requests: write

jobs:
  submit-to-gerrit:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          # Use the PR HEAD SHA to check out the correct content
          ref: ${{ github.event.pull_request.head.sha }}
          fetch-depth: 10

      - name: Install SSH key
        uses: shimataro/ssh-key-action@d4fffb50872869abe2d9a9098a6d9c5aa7d16be4
        with:
          key: ${{ secrets.GERRIT_SSH_PRIVKEY_G2G }}
          name: "id_rsa"
          known_hosts: ${{ vars.GERRIT_KNOWN_HOSTS }}
          config: |
            Host ${{ vars.GERRIT_SERVER }}
              User ${{ vars.GERRIT_SSH_USER_G2G }}
              Port 29418
              PubkeyAcceptedKeyTypes +ssh-rsa
              IdentityFile ~/.ssh/id_rsa

      - name: Submit PR to Gerrit
        id: g2g
        uses: lfit/github2gerrit-python@main
        with:
          SUBMIT_SINGLE_COMMITS: "false"
          USE_PR_AS_COMMIT: "false"
          FETCH_DEPTH: "10"
          GERRIT_KNOWN_HOSTS: ${{ vars.GERRIT_KNOWN_HOSTS }}
          GERRIT_SSH_PRIVKEY_G2G: ${{ secrets.GERRIT_SSH_PRIVKEY_G2G }}
          GERRIT_SSH_USER_G2G: ${{ vars.GERRIT_SSH_USER_G2G }}
          GERRIT_SSH_USER_G2G_EMAIL: ${{ vars.GERRIT_SSH_USER_G2G_EMAIL }}
          ORGANIZATION: ${{ github.repository_owner }}
          REVIEWERS_EMAIL: ""
```

The action reads `.gitreview`. If `.gitreview` is absent, you must
supply Gerrit connection details through a reusable workflow or by
setting the corresponding environment variables before invoking the
action. The shell action enforces `.gitreview` for the composite
variant; this Python action mirrors that behavior for compatibility.

## Inputs

All inputs are strings, matching the composite action.

- SUBMIT_SINGLE_COMMITS
  - Submit one commit at a time to Gerrit. Default: "false".
- USE_PR_AS_COMMIT
  - Use PR title and body as the commit message. Default: "false".
- FETCH_DEPTH
  - Depth used when checking out the repository. Default: "10".
- GERRIT_KNOWN_HOSTS
  - SSH known hosts content for the Gerrit host. Required.
- GERRIT_SSH_PRIVKEY_G2G
  - SSH private key for Gerrit. Required.
- GERRIT_SSH_USER_G2G
  - Gerrit SSH username. Required.
- GERRIT_SSH_USER_G2G_EMAIL
  - Gerrit SSH user email (used for commit identity). Required.
- ORGANIZATION
  - Organization name, defaults to `github.repository_owner`.
- REVIEWERS_EMAIL
  - Comma separated reviewer emails. If empty, defaults to
    `GERRIT_SSH_USER_G2G_EMAIL`.

Optional inputs when `.gitreview` is not present (parity with
the reusable workflow):

- GERRIT_SERVER
  - Gerrit host, e.g. `git.opendaylight.org`. Default: "".
- GERRIT_SERVER_PORT
  - Gerrit port, default "29418".
- GERRIT_PROJECT
  - Gerrit project name, e.g. `releng/builder`. Default: "".

## Outputs

- url
  - Gerrit change URL(s). Multi‑line when the action submits more than one change.
- change_number
  - Gerrit change number(s). Multi‑line when the action submits more than one change.

These outputs mirror the composite action. They are also exported into
the environment as:

- GERRIT_CHANGE_REQUEST_URL
- GERRIT_CHANGE_REQUEST_NUM (or GERRIT_CHANGE_REQUEST_NUMBER)

## Behavior details

- Branch resolution
  - Uses `GITHUB_BASE_REF` as the target branch for Gerrit, or defaults
    to `master` when unset, matching the existing workflow.
- Topic naming
  - Uses `GH-<repo>-<pr-number>` where `<repo>` replaces slashes with
    hyphens.
- Change‑Id handling
  - Single commits: the process amends each cherry‑picked commit to include a
    `Change-Id`. The tool collects these values for querying.
  - Squashed: collects trailers from original commits, preserves
    `Signed-off-by`, and reuses the `Change-Id` when PRs reopen or synchronize.
    synchronized.
- Reviewers
  - If empty, defaults to the Gerrit SSH user email.
- Comments
  - Adds a back‑reference comment in Gerrit with the GitHub PR and run
    URL. Adds a comment on the GitHub PR with the Gerrit change URL(s).
- Closing PRs
  - On `pull_request_target`, the workflow may close the PR after submission to
    match the shell action’s behavior.

## Security notes

- Do not hardcode secrets or keys. Provide the private key via the
  workflow secrets and known hosts via repository or org variables.
- SSH config respects `known_hosts` and the SSH identity set by the
  workflow step that installs the key.
- All external calls should use retries and clear error reporting.

## Development

This repository follows the guidelines in `CLAUDE.md`.

- Language and CLI
  - Python 3.11. The CLI uses Typer.
- Packaging
  - `pyproject.toml` with PDM backend. Use `uv` to install and run.
- Structure
  - `src/github2gerrit_python/cli.py` (CLI entrypoint)
  - `src/github2gerrit_python/core.py` (orchestration)
  - `src/github2gerrit_python/gitutils.py` (subprocess and git helpers)
- Linting and type checking
  - Ruff and MyPy use settings in `pyproject.toml`.
  - Run from pre‑commit hooks and CI.
- Tests
  - Pytest with coverage targets around 80%.
  - Add unit and integration tests for each feature.

### Local setup

- Install `uv` and run:
  - `uv pip install --system .`
  - `uv run github2gerrit --help`
- Run tests:
  - `uv run pytest -q`
- Lint and type check:
  - `uv run ruff check .`
  - `uv run black --check .`
  - `uv run mypy src`

### Notes on parity

- Inputs, outputs, and environment usage match the shell action.
- The action assumes the same GitHub variables and secrets are present.
- Where the shell action uses tools such as `jq` and `gh`, the Python
  version uses library calls and subprocess as appropriate, with retries
  and clear logging.

## License

Apache License 2.0. See `LICENSE` for details.

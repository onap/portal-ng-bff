# SPDX-License-Identifier: Apache-2.0
# SPDX-FileCopyrightText: 2025 The Linux Foundation

from __future__ import annotations

import json
import logging
import os
import sys

from .models import Inputs, GitHubContext
from . import models
from pathlib import Path
from typing import Any, cast
from typing import Optional

import typer
from urllib.parse import urlparse

def _parse_github_target(url: str) -> tuple[Optional[str], Optional[str], Optional[int]]:
    """
    Parse a GitHub repository or pull request URL.

    Returns:
      (org, repo, pr_number) where pr_number may be None for repo URLs.
    """
    try:
        u = urlparse(url)
        if u.netloc not in ("github.com", "www.github.com"):
            return None, None, None
        parts = [p for p in u.path.split("/") if p]
        if len(parts) >= 2:
            owner, repo = parts[0], parts[1]
            # Pull request URL format: /<org>/<repo>/pull/<number>
            if len(parts) >= 4 and parts[2] in ("pull", "pulls"):
                try:
                    pr = int(parts[3])
                except ValueError:
                    pr = None
                return owner, repo, pr
            # Repository URL
            return owner, repo, None
        return None, None, None
    except Exception:
        return None, None, None

from .core import Orchestrator
from .github_api import build_client, get_repo_from_env, iter_open_pulls
from .config import load_org_config, apply_config_to_env


APP_NAME = "github2gerrit"
app: typer.Typer = typer.Typer(add_completion=False, no_args_is_help=True)

@app.callback(invoke_without_command=True)  # type: ignore[misc]
def main(
    ctx: typer.Context,
    target_url: Optional[str] = typer.Argument(
        None, help="GitHub repository or PR URL"
    ),
    dry_run: bool = typer.Option(
        False, "--dry-run", help="Validate settings; do not write to Gerrit."
    ),
) -> None:
    """
    Default entrypoint to support calling:
      github2gerrit https://github.com/org/repo[/pull/<num>]
    """
    if target_url:
        org, repo, pr = _parse_github_target(target_url)
        if org:
            os.environ["ORGANIZATION"] = org
        if org and repo:
            os.environ["GITHUB_REPOSITORY"] = f"{org}/{repo}"
        if pr:
            os.environ["PR_NUMBER"] = str(pr)
            os.environ["SYNC_ALL_OPEN_PRS"] = "false"
        else:
            # Repository URL => process all open PRs
            os.environ["SYNC_ALL_OPEN_PRS"] = "true"
        # Mark that this was a direct URL invocation (not GH event)
        os.environ["G2G_TARGET_URL"] = "1"
        if dry_run:
            os.environ["DRY_RUN"] = "true"
        _process()
        return


def _setup_logging() -> logging.Logger:
    level_name = os.getenv("G2G_LOG_LEVEL", "INFO").upper()
    level = getattr(logging, level_name, logging.INFO)
    fmt = (
        "%(asctime)s %(levelname)-8s %(name)s "
        "%(filename)s:%(lineno)d | %(message)s"
    )
    logging.basicConfig(level=level, format=fmt)
    return logging.getLogger(APP_NAME)


log = _setup_logging()

def _env_str(name: str, default: str = "") -> str:
    val = os.getenv(name)
    return val if val is not None else default


def _env_bool(name: str, default: bool = False) -> bool:
    val = os.getenv(name)
    if val is None:
        return default
    s = val.strip().lower()
    return s in ("1", "true", "yes", "on")


def _build_inputs_from_env() -> Inputs:
    return Inputs(
        submit_single_commits=_env_bool("SUBMIT_SINGLE_COMMITS", False),
        use_pr_as_commit=_env_bool("USE_PR_AS_COMMIT", False),
        fetch_depth=int(_env_str("FETCH_DEPTH", "10") or "10"),
        gerrit_known_hosts=_env_str("GERRIT_KNOWN_HOSTS"),
        gerrit_ssh_privkey_g2g=_env_str("GERRIT_SSH_PRIVKEY_G2G"),
        gerrit_ssh_user_g2g=_env_str("GERRIT_SSH_USER_G2G"),
        gerrit_ssh_user_g2g_email=_env_str("GERRIT_SSH_USER_G2G_EMAIL"),
        organization=_env_str("ORGANIZATION", _env_str("GITHUB_REPOSITORY_OWNER")),
        reviewers_email=_env_str("REVIEWERS_EMAIL", ""),
        preserve_github_prs=_env_bool("PRESERVE_GITHUB_PRS", False),
        dry_run=_env_bool("DRY_RUN", False),
        gerrit_server=_env_str("GERRIT_SERVER", ""),
        gerrit_server_port=_env_str("GERRIT_SERVER_PORT", "29418"),
        gerrit_project=_env_str("GERRIT_PROJECT", ""),
    )


def _process() -> None:
    # Build inputs from environment (used by URL callback path)
    data = _build_inputs_from_env()

    # Load per-org configuration and apply to environment before validation
    org_for_cfg = data.organization or os.getenv("ORGANIZATION") or os.getenv("GITHUB_REPOSITORY_OWNER")
    cfg = load_org_config(org_for_cfg)
    apply_config_to_env(cfg)
    # Refresh inputs after applying configuration to environment
    data = _build_inputs_from_env()

    # Derive reviewers from local git config if running locally and unset
    if not os.getenv("REVIEWERS_EMAIL") and (os.getenv("G2G_TARGET_URL") or not os.getenv("GITHUB_EVENT_NAME")):
        try:
            from .gitutils import enumerate_reviewer_emails
            emails = enumerate_reviewer_emails()
            if emails:
                os.environ["REVIEWERS_EMAIL"] = ",".join(emails)
                data = Inputs(
                    submit_single_commits=data.submit_single_commits,
                    use_pr_as_commit=data.use_pr_as_commit,
                    fetch_depth=data.fetch_depth,
                    gerrit_known_hosts=data.gerrit_known_hosts,
                    gerrit_ssh_privkey_g2g=data.gerrit_ssh_privkey_g2g,
                    gerrit_ssh_user_g2g=data.gerrit_ssh_user_g2g,
                    gerrit_ssh_user_g2g_email=data.gerrit_ssh_user_g2g_email,
                    organization=data.organization,
                    reviewers_email=os.environ["REVIEWERS_EMAIL"],
                    preserve_github_prs=data.preserve_github_prs,
                    dry_run=data.dry_run,
                    gerrit_server=data.gerrit_server,
                    gerrit_server_port=data.gerrit_server_port,
                    gerrit_project=data.gerrit_project,
                )
                log.info("Derived reviewers: %s", data.reviewers_email)
        except Exception as exc:
            log.debug("Could not derive reviewers from git config: %s", exc)

    # Validate inputs
    try:
        _validate_inputs(data)
    except typer.BadParameter as exc:
        log.error("%s", exc)
        print(str(exc), file=sys.stderr)
        raise typer.Exit(code=2)

    gh = _read_github_context()
    _log_effective_config(data, gh)

    # Test mode: short-circuit after validation
    if _env_bool("G2G_TEST_MODE", False):
        log.info("Validation complete. Ready to execute submission pipeline.")
        print("Validation complete. Ready to execute submission pipeline.")
        return

    # Bulk mode for URL/workflow_dispatch
    sync_all = _env_bool("SYNC_ALL_OPEN_PRS", False)
    if sync_all and (gh.event_name == "workflow_dispatch" or os.getenv("G2G_TARGET_URL")):
        client = build_client()
        repo = get_repo_from_env(client)
        orch = Orchestrator(workspace=Path.cwd())

        all_urls: list[str] = []
        all_nums: list[str] = []

        for pr in iter_open_pulls(repo):
            pr_number = int(getattr(pr, "number", 0) or 0)
            if pr_number <= 0:
                continue

            per_ctx = models.GitHubContext(
                event_name=gh.event_name,
                event_action=gh.event_action,
                event_path=gh.event_path,
                repository=gh.repository,
                repository_owner=gh.repository_owner,
                server_url=gh.server_url,
                run_id=gh.run_id,
                sha=gh.sha,
                base_ref=gh.base_ref,
                head_ref=gh.head_ref,
                pr_number=pr_number,
            )

            result_multi = orch.execute(inputs=data, gh=per_ctx)
            if result_multi.change_urls:
                all_urls.extend(result_multi.change_urls)
            if result_multi.change_numbers:
                all_nums.extend(result_multi.change_numbers)

        if all_urls:
            os.environ["GERRIT_CHANGE_REQUEST_URL"] = "\n".join(all_urls)
        if all_nums:
            os.environ["GERRIT_CHANGE_REQUEST_NUM"] = "\n".join(all_nums)

        log.info("Submission pipeline complete (multi-PR).")
        return

    if not gh.pr_number:
        log.error(
            "PR_NUMBER is empty. This tool requires a valid pull request "
            "context. Current event: %s",
            gh.event_name,
        )
        print(
            "PR_NUMBER is empty. This tool requires a valid pull request "
            f"context. Current event: {gh.event_name}",
            file=sys.stderr,
        )
        raise typer.Exit(code=2)

    # Test mode handled earlier

    # Execute single-PR submission
    orch = Orchestrator(workspace=Path.cwd())
    result = orch.execute(inputs=data, gh=gh)
    if result.change_urls:
        os.environ["GERRIT_CHANGE_REQUEST_URL"] = "\n".join(result.change_urls)
    if result.change_numbers:
        os.environ["GERRIT_CHANGE_REQUEST_NUM"] = "\n".join(result.change_numbers)
    log.info("Submission pipeline complete.")
    return








def _mask_secret(value: str, keep: int = 4) -> str:
    if not value:
        return ""
    if len(value) <= keep:
        return "*" * len(value)
    return f"{value[:keep]}{'*' * (len(value) - keep)}"


def _load_event(path: Optional[Path]) -> dict[str, Any]:
    if not path or not path.exists():
        return {}
    try:
        return cast(dict[str, Any], json.loads(path.read_text(encoding="utf-8")))
    except Exception as exc:
        log.warning("Failed to parse GITHUB_EVENT_PATH: %s", exc)
        return {}


def _extract_pr_number(evt: dict[str, Any]) -> Optional[int]:
    # Try standard pull_request payload
    pr = evt.get("pull_request")
    if isinstance(pr, dict) and isinstance(pr.get("number"), int):
        return int(pr["number"])

    # Try issues payload (when used on issues events)
    issue = evt.get("issue")
    if isinstance(issue, dict) and isinstance(issue.get("number"), int):
        return int(issue["number"])

    # Try a direct number field
    if isinstance(evt.get("number"), int):
        return int(evt["number"])

    return None


def _read_github_context() -> GitHubContext:
    event_name = os.getenv("GITHUB_EVENT_NAME", "")
    event_action = ""
    event_path_str = os.getenv("GITHUB_EVENT_PATH")
    event_path = Path(event_path_str) if event_path_str else None

    evt = _load_event(event_path)
    if isinstance(evt.get("action"), str):
        event_action = evt["action"]

    repository = os.getenv("GITHUB_REPOSITORY", "")
    repository_owner = os.getenv("GITHUB_REPOSITORY_OWNER", "")
    server_url = os.getenv("GITHUB_SERVER_URL", "https://github.com")
    run_id = os.getenv("GITHUB_RUN_ID", "")
    sha = os.getenv("GITHUB_SHA", "")

    base_ref = os.getenv("GITHUB_BASE_REF", "")
    head_ref = os.getenv("GITHUB_HEAD_REF", "")

    pr_number = _extract_pr_number(evt)
    if pr_number is None:
        env_pr = os.getenv("PR_NUMBER")
        if env_pr and env_pr.isdigit():
            pr_number = int(env_pr)

    ctx = models.GitHubContext(
        event_name=event_name,
        event_action=event_action,
        event_path=event_path,
        repository=repository,
        repository_owner=repository_owner,
        server_url=server_url,
        run_id=run_id,
        sha=sha,
        base_ref=base_ref,
        head_ref=head_ref,
        pr_number=pr_number,
    )
    return ctx


def _validate_inputs(data: Inputs) -> None:
    if data.use_pr_as_commit and data.submit_single_commits:
        msg = (
            "USE_PR_AS_COMMIT and SUBMIT_SINGLE_COMMITS cannot be enabled at "
            "the same time"
        )
        raise typer.BadParameter(msg)

    # Presence checks for required fields used by existing action
    for field_name in (
        "gerrit_known_hosts",
        "gerrit_ssh_privkey_g2g",
        "gerrit_ssh_user_g2g",
        "gerrit_ssh_user_g2g_email",
    ):
        if not getattr(data, field_name):
            raise typer.BadParameter(f"Missing required input: {field_name}")


def _log_effective_config(data: Inputs, gh: GitHubContext) -> None:
    # Avoid logging sensitive values
    safe_privkey = _mask_secret(data.gerrit_ssh_privkey_g2g)
    log.info("Effective configuration (sanitized):")
    log.info("  SUBMIT_SINGLE_COMMITS: %s", data.submit_single_commits)
    log.info("  USE_PR_AS_COMMIT: %s", data.use_pr_as_commit)
    log.info("  FETCH_DEPTH: %s", data.fetch_depth)
    log.info("  GERRIT_KNOWN_HOSTS: %s", "<provided>" if
             data.gerrit_known_hosts else "<missing>")
    log.info("  GERRIT_SSH_PRIVKEY_G2G: %s", safe_privkey)
    log.info("  GERRIT_SSH_USER_G2G: %s", data.gerrit_ssh_user_g2g)
    log.info(
        "  GERRIT_SSH_USER_G2G_EMAIL: %s", data.gerrit_ssh_user_g2g_email
    )
    log.info("  ORGANIZATION: %s", data.organization)
    log.info("  REVIEWERS_EMAIL: %s", data.reviewers_email or "")
    log.info("  PRESERVE_GITHUB_PRS: %s", data.preserve_github_prs)
    log.info("  DRY_RUN: %s", data.dry_run)
    log.info("  GERRIT_SERVER: %s", data.gerrit_server or "")
    log.info("  GERRIT_SERVER_PORT: %s", data.gerrit_server_port or "")
    log.info("  GERRIT_PROJECT: %s", data.gerrit_project or "")
    log.info("GitHub context:")
    log.info("  event_name: %s", gh.event_name)
    log.info("  event_action: %s", gh.event_action)
    log.info("  repository: %s", gh.repository)
    log.info("  repository_owner: %s", gh.repository_owner)
    log.info("  pr_number: %s", gh.pr_number)
    log.info("  base_ref: %s", gh.base_ref)
    log.info("  head_ref: %s", gh.head_ref)
    log.info("  sha: %s", gh.sha)


def _resolve_org(default_org: Optional[str]) -> str:
    if default_org:
        return default_org
    gh_owner = os.getenv("GITHUB_REPOSITORY_OWNER")
    if gh_owner:
        return gh_owner
    # Fallback to empty string for compatibility with existing action
    return ""


@app.command()  # type: ignore[misc]
def run(
    submit_single_commits: bool = typer.Option(
        False,
        "--submit-single-commits",
        envvar="SUBMIT_SINGLE_COMMITS",
        help="Submit one commit at a time to the Gerrit repository.",
    ),
    use_pr_as_commit: bool = typer.Option(
        False,
        "--use-pr-as-commit",
        envvar="USE_PR_AS_COMMIT",
        help="Use PR title and body as the commit message.",
    ),
    fetch_depth: int = typer.Option(
        10,
        "--fetch-depth",
        envvar="FETCH_DEPTH",
        help="Fetch-depth for the clone.",
    ),
    gerrit_known_hosts: str = typer.Option(
        "",
        "--gerrit-known-hosts",
        envvar="GERRIT_KNOWN_HOSTS",
        help="Known hosts entries for Gerrit SSH.",
    ),
    gerrit_ssh_privkey_g2g: str = typer.Option(
        "",
        "--gerrit-ssh-privkey-g2g",
        envvar="GERRIT_SSH_PRIVKEY_G2G",
        help="SSH private key for Gerrit (string content).",
    ),
    gerrit_ssh_user_g2g: str = typer.Option(
        "",
        "--gerrit-ssh-user-g2g",
        envvar="GERRIT_SSH_USER_G2G",
        help="Gerrit SSH user.",
    ),
    gerrit_ssh_user_g2g_email: str = typer.Option(
        "",
        "--gerrit-ssh-user-g2g-email",
        envvar="GERRIT_SSH_USER_G2G_EMAIL",
        help="Email address for the Gerrit SSH user.",
    ),
    organization: Optional[str] = typer.Option(
        None,
        "--organization",
        envvar="ORGANIZATION",
        help=(
            "Organization (defaults to GITHUB_REPOSITORY_OWNER when unset)."
        ),
    ),
    reviewers_email: str = typer.Option(
        "",
        "--reviewers-email",
        envvar="REVIEWERS_EMAIL",
        help="Comma-separated list of reviewer emails.",
    ),
    preserve_github_prs: bool = typer.Option(
        False,
        "--preserve-github-prs",
        envvar="PRESERVE_GITHUB_PRS",
        help="Do not close GitHub PRs after pushing to Gerrit.",
    ),
    dry_run: bool = typer.Option(
        False,
        "--dry-run",
        envvar="DRY_RUN",
        help="Validate settings and PR metadata; do not write to Gerrit.",
    ),
    # Reusable workflow compatibility inputs (optional)
    gerrit_server: str = typer.Option(
        "",
        "--gerrit-server",
        envvar="GERRIT_SERVER",
        help="Gerrit server hostname (optional; .gitreview preferred).",
    ),
    gerrit_server_port: str = typer.Option(
        "29418",
        "--gerrit-server-port",
        envvar="GERRIT_SERVER_PORT",
        help="Gerrit SSH port (default: 29418).",
    ),
    gerrit_project: str = typer.Option(
        "",
        "--gerrit-project",
        envvar="GERRIT_PROJECT",
        help="Gerrit project (optional; .gitreview preferred).",
    ),
) -> None:
    """
    Prepare and submit a GitHub pull request to Gerrit as a change.

    This initial skeleton mirrors the inputs and environment variables used
    by the existing shell-based composite action, validates them, and
    captures the GitHub event context. The submission workflow will be
    implemented in subsequent iterations.
    """
    # Normalize CLI options into environment for unified processing, then delegate.
    if submit_single_commits:
        os.environ["SUBMIT_SINGLE_COMMITS"] = "true"
    if use_pr_as_commit:
        os.environ["USE_PR_AS_COMMIT"] = "true"
    os.environ["FETCH_DEPTH"] = str(fetch_depth)
    if gerrit_known_hosts:
        os.environ["GERRIT_KNOWN_HOSTS"] = gerrit_known_hosts
    if gerrit_ssh_privkey_g2g:
        os.environ["GERRIT_SSH_PRIVKEY_G2G"] = gerrit_ssh_privkey_g2g
    if gerrit_ssh_user_g2g:
        os.environ["GERRIT_SSH_USER_G2G"] = gerrit_ssh_user_g2g
    if gerrit_ssh_user_g2g_email:
        os.environ["GERRIT_SSH_USER_G2G_EMAIL"] = gerrit_ssh_user_g2g_email
    # Organization may be empty; resolve default from GITHUB_REPOSITORY_OWNER if needed
    resolved_org = _resolve_org(organization)
    if resolved_org:
        os.environ["ORGANIZATION"] = resolved_org
    if reviewers_email:
        os.environ["REVIEWERS_EMAIL"] = reviewers_email
    if preserve_github_prs:
        os.environ["PRESERVE_GITHUB_PRS"] = "true"
    if dry_run:
        os.environ["DRY_RUN"] = "true"
    if gerrit_server:
        os.environ["GERRIT_SERVER"] = gerrit_server
    if gerrit_server_port:
        os.environ["GERRIT_SERVER_PORT"] = gerrit_server_port
    if gerrit_project:
        os.environ["GERRIT_PROJECT"] = gerrit_project

    # Delegate to the common processing path to avoid OptionInfo leakage.
    _process()
    return



# Backwards-friendly alias so entry point can be either cli:app or cli:run.
# Expose the Typer app object for console_script usage.
app.command("submit")(run)

if __name__ == "__main__":
    # Invoke the Typer app when executed as a script.
    # Example:
    #   python -m github2gerrit_python.cli run --help
    app()

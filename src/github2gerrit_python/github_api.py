# SPDX-License-Identifier: Apache-2.0
# SPDX-FileCopyrightText: 2025 The Linux Foundation
#
# GitHub API wrapper using PyGithub with retries/backoff.
# - Centralized construction of the client
# - Helpers for common PR operations used by github2gerrit
# - Deterministic, typed interfaces with strict typing
# - Basic exponential backoff with jitter for transient failures
#
# Notes:
# - This module intentionally limits its surface area to the needs of the
#   orchestration flow: PR discovery, metadata, comments, and closing PRs.
# - Rate limit handling is best-effort. For heavy usage, consider honoring
#   the reset timestamp exposed by the API. Here we implement a capped
#   exponential backoff with jitter for simplicity.

from __future__ import annotations

import logging
import os
import random
import re
import time
from typing import Any
from typing import Callable
from typing import Generator
from typing import Optional
from typing import Tuple
from typing import TypeVar
from typing import TYPE_CHECKING
from typing import cast

from github import Github
from github.GithubException import GithubException
from github.GithubException import RateLimitExceededException

if TYPE_CHECKING:
    # These imports improve editor experience if stubs are present.
    from github.Repository import Repository as GhRepository
    from github.PullRequest import PullRequest as GhPullRequest
    from github.Issue import Issue as GhIssue
    from github.IssueComment import IssueComment as GhIssueComment
else:  # pragma: no cover - typing convenience
    # Provide runtime placeholders; type checkers ignore this branch.
    GhRepository = object
    GhPullRequest = object
    GhIssue = object
    GhIssueComment = object


__all__ = [
    "build_client",
    "get_repo_from_env",
    "get_pull",
    "iter_open_pulls",
    "get_pr_title_body",
    "get_recent_change_ids_from_comments",
    "create_pr_comment",
    "close_pr",
]

log = logging.getLogger("github2gerrit.github_api")

_T = TypeVar("_T")


def _getenv_str(name: str) -> str:
    val = os.getenv(name, "")
    return val.strip()


def _backoff_delay(attempt: int, base: float = 0.5, cap: float = 6.0) -> float:
    # Exponential backoff with jitter; cap prevents unbounded waits.
    delay: float = float(min(base * (2 ** max(0, attempt - 1)), cap))
    jitter: float = float(random.uniform(0.0, delay / 2.0))
    return float(delay + jitter)


def _should_retry(exc: BaseException) -> bool:
    # Retry on common transient conditions:
    # - RateLimitExceededException
    # - GithubException with 5xx codes
    # - GithubException with 403 and rate limit hints
    if isinstance(exc, RateLimitExceededException):
        return True
    if isinstance(exc, GithubException):
        status = getattr(exc, "status", None)
        if isinstance(status, int) and 500 <= status <= 599:
            return True
        data = getattr(exc, "data", "")
        if status == 403 and isinstance(data, (str, bytes)):
            try:
                text = data.decode("utf-8") if isinstance(data, bytes) else data
            except Exception:
                text = str(data)
            if "rate limit" in text.lower():
                return True
    return False


def _retry_on_github(
    attempts: int = 5,
) -> Callable[[Callable[..., _T]], Callable[..., _T]]:
    def decorator(func: Callable[..., _T]) -> Callable[..., _T]:
        def wrapper(*args: Any, **kwargs: Any) -> _T:
            last_exc: Optional[BaseException] = None
            for attempt in range(1, attempts + 1):
                try:
                    return func(*args, **kwargs)
                except BaseException as exc:  # noqa: BLE001
                    last_exc = exc
                    if not _should_retry(exc) or attempt == attempts:
                        log.debug(
                            "GitHub call failed (no retry) at attempt %d: %s",
                            attempt,
                            exc,
                        )
                        raise
                    delay = _backoff_delay(attempt)
                    log.warning(
                        "GitHub call failed (attempt %d): %s; retrying in "
                        "%.2fs",
                        attempt,
                        exc,
                        delay,
                    )
                    time.sleep(delay)
            # Should not reach here, but raise if it does.
            assert last_exc is not None
            raise last_exc
        return wrapper
    return decorator


@_retry_on_github()
def build_client(token: Optional[str] = None) -> Github:
    """Construct a PyGithub client from a token or environment.

    Order of precedence:
    - Provided 'token' argument
    - GITHUB_TOKEN environment variable

    Returns:
      Github client with sane defaults.
    """
    tok = token or _getenv_str("GITHUB_TOKEN")
    if not tok:
        raise ValueError(
            "GITHUB_TOKEN is required to access the GitHub API"
        )
    # per_page improves pagination; adjust as needed.
    return Github(login_or_token=tok, per_page=100)


@_retry_on_github()
def get_repo_from_env(client: Github) -> GhRepository:
    """Return the repository object based on GITHUB_REPOSITORY."""
    full = _getenv_str("GITHUB_REPOSITORY")
    if not full or "/" not in full:
        raise ValueError(
            "GITHUB_REPOSITORY environment must be set to 'owner/repo'"
        )
    repo = client.get_repo(full)
    return repo


@_retry_on_github()
def get_pull(repo: GhRepository, number: int) -> GhPullRequest:
    """Fetch a pull request by number."""
    pr = repo.get_pull(number)
    return pr


def iter_open_pulls(repo: GhRepository) -> Generator[GhPullRequest, None, None]:
    """Yield open pull requests in this repository."""
    prs = repo.get_pulls(state="open")
    for pr in prs:
        # Cast to improve type awareness for callers
        yield pr


def get_pr_title_body(pr: GhPullRequest) -> Tuple[str, str]:
    """Return PR title and body, replacing None with empty strings."""
    title = getattr(pr, "title", "") or ""
    body = getattr(pr, "body", "") or ""
    return str(title), str(body)


_CHANGE_ID_RE = re.compile(r"Change-Id:\s*([A-Za-z0-9._-]+)")


@_retry_on_github()
def _get_issue(pr: GhPullRequest) -> GhIssue:
    """Return the issue object corresponding to a pull request."""
    issue = pr.as_issue()
    return issue


@_retry_on_github()
def get_recent_change_ids_from_comments(
    pr: GhPullRequest,
    *,
    max_comments: int = 50,
) -> list[str]:
    """Scan recent PR comments for Change-Id trailers.

    Args:
      pr: Pull request.
      max_comments: Max number of most recent comments to scan.

    Returns:
      List of Change-Id values in order of appearance (oldest to newest)
      within the scanned window. Duplicates are preserved.
    """
    issue = _get_issue(pr)
    comments = issue.get_comments()
    # Collect last 'max_comments' by buffering and slicing at the end.
    buf: list[GhIssueComment] = []
    for c in comments:
        buf.append(c)
        # No early stop; PaginatedList can be large, we'll truncate after.
    # Truncate to the most recent 'max_comments'
    recent = buf[-max_comments:] if max_comments > 0 else buf
    found: list[str] = []
    for c in recent:
        body = getattr(c, "body", "") or ""
        for m in _CHANGE_ID_RE.finditer(body):
            cid = m.group(1).strip()
            if cid:
                found.append(cid)
    return found


@_retry_on_github()
def create_pr_comment(pr: GhPullRequest, body: str) -> None:
    """Create a new comment on the pull request."""
    if not body.strip():
        return
    issue = _get_issue(pr)
    issue.create_comment(body)


@_retry_on_github()
def close_pr(pr: GhPullRequest, *, comment: Optional[str] = None) -> None:
    """Close a pull request, optionally posting a comment first."""
    if comment and comment.strip():
        try:
            create_pr_comment(pr, comment)
        except Exception as exc:  # noqa: BLE001
            log.warning("Failed to add close comment to PR #%s: %s", pr.number, exc)
    pr.edit(state="closed")

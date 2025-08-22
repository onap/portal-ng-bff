# SPDX-License-Identifier: Apache-2.0
# Copyright:
#   2025 The Linux Foundation
#
# Subprocess and git helper utilities with logging and error handling.
# - Strict typing
# - Centralized logging
# - Secret masking in logs
# - Optional retries with exponential backoff for transient errors

from __future__ import annotations

import logging
import os
import shlex
import subprocess
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Callable
from typing import Dict
from typing import Iterable
from typing import List
from typing import Mapping
from typing import Optional
from typing import Sequence
from typing import Union


__all__ = [
    "CommandError",
    "GitError",
    "CommandResult",
    "run_cmd",
    "run_cmd_with_retries",
    "git",
    "git_config",
    "git_config_get",
    "git_config_get_all",
    "git_reset_soft",
    "git_cherry_pick",
    "git_commit_amend",
    "git_commit_new",
    "git_show",
    "git_log",
    "git_last_commit_trailers",
    "mask_text",
    "enumerate_reviewer_emails",
]


_LOGGER_NAME = "github2gerrit.git"
log = logging.getLogger(_LOGGER_NAME)
if not log.handlers:
    # Provide a minimal default if the app has not configured logging.
    level_name = os.getenv("G2G_LOG_LEVEL", "INFO").upper()
    level = getattr(logging, level_name, logging.INFO)
    fmt = (
        "%(asctime)s %(levelname)-8s %(name)s "
        "%(filename)s:%(lineno)d | %(message)s"
    )
    logging.basicConfig(level=level, format=fmt)


class CommandError(RuntimeError):
    """Raised when a subprocess command fails."""

    def __init__(
        self,
        message: str,
        *,
        cmd: Sequence[str] | None = None,
        returncode: int | None = None,
        stdout: str | None = None,
        stderr: str | None = None,
    ) -> None:
        super().__init__(message)
        self.cmd = list(cmd) if cmd is not None else None
        self.returncode = returncode
        self.stdout = stdout
        self.stderr = stderr


class GitError(CommandError):
    """Raised when a git command fails."""


@dataclass(frozen=True)
class CommandResult:
    returncode: int
    stdout: str
    stderr: str


def _to_str_opt(val: Optional[Union[str, bytes]]) -> Optional[str]:
    """Convert an optional bytes/str value to str safely."""
    if val is None:
        return None
    if isinstance(val, bytes):
        return val.decode("utf-8", errors="replace")
    return val


def mask_text(text: str, masks: Iterable[str]) -> str:
    """Replace each mask value in text with asterisks."""
    masked = text
    for token in masks:
        if not token:
            continue
        masked = masked.replace(token, "***")
    return masked


def _format_cmd_for_log(
    cmd: Sequence[str],
    masks: Iterable[str],
) -> str:
    quoted = [shlex.quote(x) for x in cmd]
    line = " ".join(quoted)
    return mask_text(line, masks)


def _merge_env(
    base: Optional[Mapping[str, str]],
    extra: Optional[Mapping[str, str]],
) -> Dict[str, str]:
    if base is None:
        out: Dict[str, str] = dict(os.environ)
    else:
        out = dict(base)
    if extra:
        out.update(extra)
    return out


def _is_transient_git_error(stderr: str) -> bool:
    """Heuristics for transient git/network errors suitable for retry."""
    s = stderr.lower()
    patterns = [
        "unable to access",
        "could not resolve host",
        "failed to connect",
        "connection timed out",
        "connection reset by peer",
        "early eof",
        "the remote end hung up unexpectedly",
        "http/2 stream",
        "transport endpoint is not connected",
        "network is unreachable",
        "temporary failure",
        "ssl: couldn't",
        "ssl: certificate",
    ]
    return any(pat in s for pat in patterns)


def _backoff_delay(attempt: int, base: float = 0.5, cap: float = 5.0) -> float:
    # Exponential backoff: base * 2^(attempt-1), capped
    delay = base * (2 ** max(0, attempt - 1))
    return min(delay, cap)


def run_cmd(
    cmd: Sequence[str],
    *,
    cwd: Optional[Path] = None,
    env: Optional[Mapping[str, str]] = None,
    timeout: Optional[float] = None,
    check: bool = True,
    masks: Optional[Iterable[str]] = None,
    stdin_data: Optional[str] = None,
) -> CommandResult:
    """Run a subprocess command and capture output.

    - Logs command line with secrets masked.
    - Returns stdout/stderr and return code.
    - Raises CommandError on failure when check=True.
    """
    masks = list(masks or [])
    env_full = _merge_env(None, env)

    log.debug("Executing: %s", _format_cmd_for_log(cmd, masks))
    try:
        proc = subprocess.run(
            list(cmd),
            cwd=str(cwd) if cwd else None,
            env=env_full,
            input=stdin_data,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=timeout,
            check=False,
        )
    except subprocess.TimeoutExpired as exc:
        msg = f"Command timed out: {cmd!r}"
        log.error(msg)
        # TimeoutExpired carries 'output' and 'stderr' attributes,
        # which may be bytes depending on invocation context.
        out = getattr(exc, "output", None)
        err = getattr(exc, "stderr", None)
        raise CommandError(
            msg,
            cmd=cmd,
            returncode=None,
            stdout=_to_str_opt(out),
            stderr=_to_str_opt(err),
        ) from exc
    except OSError as exc:
        msg = f"Failed to execute command: {cmd!r} ({exc})"
        log.error(msg)
        raise CommandError(msg, cmd=cmd) from exc

    result = CommandResult(
        returncode=proc.returncode,
        stdout=proc.stdout or "",
        stderr=proc.stderr or "",
    )

    if result.returncode != 0:
        log.debug(
            "Command failed (rc=%s): %s\nstderr: %s",
            result.returncode,
            _format_cmd_for_log(cmd, masks),
            mask_text(result.stderr, masks),
        )
        if check:
            raise CommandError(
                "Command failed",
                cmd=cmd,
                returncode=result.returncode,
                stdout=result.stdout,
                stderr=result.stderr,
            )
    else:
        if result.stdout:
            log.debug("stdout: %s", mask_text(result.stdout, masks))
        if result.stderr:
            log.debug("stderr: %s", mask_text(result.stderr, masks))

    return result


def run_cmd_with_retries(
    cmd: Sequence[str],
    *,
    cwd: Optional[Path] = None,
    env: Optional[Mapping[str, str]] = None,
    timeout: Optional[float] = None,
    check: bool = True,
    masks: Optional[Iterable[str]] = None,
    stdin_data: Optional[str] = None,
    retries: int = 2,
    retry_on: Optional[Callable[[CommandResult], bool]] = None,
) -> CommandResult:
    """Run a command with basic exponential backoff retries on transient errors.

    The default retry predicate considers common transient git errors.
    """
    masks = list(masks or [])

    def _default_retry_on(res: CommandResult) -> bool:
        return res.returncode != 0 and _is_transient_git_error(res.stderr)

    predicate = retry_on or _default_retry_on
    attempt = 0
    # removed unused variable 'last_res'

    while True:
        attempt += 1
        try:
            res = run_cmd(
                cmd,
                cwd=cwd,
                env=env,
                timeout=timeout,
                check=False,
                masks=masks,
                stdin_data=stdin_data,
            )
        except CommandError:
            # Non-exec or timeout errors are not retried here.
            raise

        if res.returncode == 0 or attempt > (retries + 1):
            if check and res.returncode != 0:
                raise CommandError(
                    "Command failed after retries",
                    cmd=cmd,
                    returncode=res.returncode,
                    stdout=res.stdout,
                    stderr=res.stderr,
                )
            return res

        if predicate(res):
            delay = _backoff_delay(attempt)
            log.warning(
                "Retrying (attempt %d) after transient error; delay %.1fs. "
                "cmd=%s",
                attempt,
                delay,
                _format_cmd_for_log(cmd, masks),
            )
            time.sleep(delay)
            continue

        # Non-transient failure; stop.
        if check:
            raise CommandError(
                "Command failed (non-retryable)",
                cmd=cmd,
                returncode=res.returncode,
                stdout=res.stdout,
                stderr=res.stderr,
            )
        return res


# ----------------------------
# Git helper functions
# ----------------------------


def git(
    args: Sequence[str],
    *,
    cwd: Optional[Path] = None,
    env: Optional[Mapping[str, str]] = None,
    timeout: Optional[float] = None,
    check: bool = True,
    masks: Optional[Iterable[str]] = None,
    retries: int = 2,
) -> CommandResult:
    """Run a git subcommand with retries on transient errors."""
    cmd = ["git", *args]
    return run_cmd_with_retries(
        cmd,
        cwd=cwd,
        env=env,
        timeout=timeout,
        check=check,
        masks=list(masks or []),
        retries=retries,
    )


def git_config(
    key: str,
    value: str,
    *,
    global_: bool = False,
    cwd: Optional[Path] = None,
) -> None:
    args = ["config"]
    if global_:
        args.append("--global")
    args.extend([key, value])
    try:
        git(args, cwd=cwd)
    except CommandError as exc:
        raise GitError(
            f"git config failed for {key}",
            cmd=exc.cmd,
            returncode=exc.returncode,
            stdout=exc.stdout,
            stderr=exc.stderr,
        ) from exc


def git_reset_soft(ref: str, *, cwd: Optional[Path] = None) -> None:
    try:
        git(["reset", "--soft", ref], cwd=cwd)
    except CommandError as exc:
        raise GitError(
            f"git reset --soft {ref} failed",
            cmd=exc.cmd,
            returncode=exc.returncode,
            stdout=exc.stdout,
            stderr=exc.stderr,
        ) from exc


def git_cherry_pick(
    commit: str,
    *,
    cwd: Optional[Path] = None,
    strategy_opts: Optional[Sequence[str]] = None,
) -> None:
    args: List[str] = ["cherry-pick"]
    if strategy_opts:
        args.extend(strategy_opts)
    args.append(commit)
    try:
        git(args, cwd=cwd)
    except CommandError as exc:
        raise GitError(
            f"git cherry-pick {commit} failed",
            cmd=exc.cmd,
            returncode=exc.returncode,
            stdout=exc.stdout,
            stderr=exc.stderr,
        ) from exc


def git_commit_amend(
    *,
    cwd: Optional[Path] = None,
    no_edit: bool = True,
    signoff: bool = True,
    author: Optional[str] = None,
    message: Optional[str] = None,
    message_file: Optional[Path] = None,
) -> None:
    """Amend the current commit.

    If message is provided, it takes precedence over message_file.
    """
    args: List[str] = ["commit", "--amend"]
    if no_edit and not message and not message_file:
        args.append("--no-edit")
    if signoff:
        args.append("-s")
    if author:
        args.extend(["--author", author])
    if message:
        args.extend(["-m", message])
    elif message_file:
        args.extend(["-F", str(message_file)])

    try:
        git(args, cwd=cwd)
    except CommandError as exc:
        raise GitError(
            "git commit --amend failed",
            cmd=exc.cmd,
            returncode=exc.returncode,
            stdout=exc.stdout,
            stderr=exc.stderr,
        ) from exc


def git_commit_new(
    *,
    cwd: Optional[Path] = None,
    message: Optional[str] = None,
    message_file: Optional[Path] = None,
    signoff: bool = True,
    author: Optional[str] = None,
    allow_empty: bool = False,
) -> None:
    """Create a new commit using message or message_file."""
    if not message and not message_file:
        raise ValueError("Either message or message_file must be provided")

    args: List[str] = ["commit"]
    if signoff:
        args.append("-s")
    if author:
        args.extend(["--author", author])
    if allow_empty:
        args.append("--allow-empty")

    if message:
        args.extend(["-m", message])
    else:
        args.extend(["-F", str(message_file)])

    try:
        git(args, cwd=cwd)
    except CommandError as exc:
        raise GitError(
            "git commit failed",
            cmd=exc.cmd,
            returncode=exc.returncode,
            stdout=exc.stdout,
            stderr=exc.stderr,
        ) from exc


def git_show(
    rev: str,
    *,
    cwd: Optional[Path] = None,
    fmt: Optional[str] = None,
) -> str:
    """Show a commit content or its formatted output."""
    args: List[str] = ["show", rev]
    if fmt:
        args.extend(["--format", fmt, "-s"])
    try:
        res = git(args, cwd=cwd)
        return res.stdout
    except CommandError as exc:
        raise GitError(
            f"git show {rev} failed",
            cmd=exc.cmd,
            returncode=exc.returncode,
            stdout=exc.stdout,
            stderr=exc.stderr,
        ) from exc


def git_log(
    *,
    cwd: Optional[Path] = None,
    n: int = 1,
    pretty: Optional[str] = None,
    additional_args: Optional[Sequence[str]] = None,
) -> str:
    """Run git log with common options."""
    args: List[str] = ["log", f"-n{n}"]
    if pretty:
        args.extend(["--pretty", pretty])
    if additional_args:
        args.extend(additional_args)
    try:
        res = git(args, cwd=cwd)
        return res.stdout
    except CommandError as exc:
        raise GitError(
            "git log failed",
            cmd=exc.cmd,
            returncode=exc.returncode,
            stdout=exc.stdout,
            stderr=exc.stderr,
        ) from exc


def _parse_trailers(text: str) -> Dict[str, List[str]]:
    """Parse trailers from a commit message body.

    Expects lines like 'Key: Value'. Multiple values per key are supported.
    """
    trailers: Dict[str, List[str]] = {}
    for raw in text.splitlines():
        line = raw.strip()
        if not line or ":" not in line:
            continue
        key, val = line.split(":", 1)
        k = key.strip()
        v = val.strip()
        if not k or not v:
            continue
        trailers.setdefault(k, []).append(v)
    return trailers


def git_last_commit_trailers(
    keys: Optional[Sequence[str]] = None,
    *,
    cwd: Optional[Path] = None,
) -> Dict[str, List[str]]:
    """Return trailers for the last commit, optionally filtered by keys."""
    try:
        # Use pretty format to print only body for robust parsing.
        body = git_show("HEAD", cwd=cwd, fmt="%B")
        # Trailers are usually at the end, but we parse all lines.
        trailers = _parse_trailers(body)
        if keys is None:
            return trailers
        subset: Dict[str, List[str]] = {}
        for k in keys:
            if k in trailers:
                subset[k] = trailers[k]
        return subset
    except GitError:
        # If HEAD is unavailable (fresh repo), return empty.
        return {}


def git_config_get(
    key: str,
    *,
    global_: bool = False,
) -> Optional[str]:
    """Get a git config value (single) from local or global config."""
    args = ["config"]
    if global_:
        args.append("--global")
    args.extend(["--get", key])
    try:
        res = git(args)
        value = res.stdout.strip()
        return value if value else None
    except CommandError:
        return None


def git_config_get_all(
    key: str,
    *,
    global_: bool = False,
) -> List[str]:
    """Get all git config values for a key (may return multiple lines)."""
    args = ["config"]
    if global_:
        args.append("--global")
    args.extend(["--get-all", key])
    try:
        res = git(args, check=False)
        values = [ln.strip() for ln in res.stdout.splitlines() if ln.strip()]
        return values
    except CommandError:
        return []


def enumerate_reviewer_emails() -> List[str]:
    """Return reviewer emails from local/global git config.

    Sources checked in order:
    - git config --get-all github2gerrit.reviewersEmail
    - git config --get-all g2g.reviewersEmail
    - git config --get-all reviewers.email
      (all may be comma-separated; values are split on commas)
    - git config user.email (local then global) as a fallback

    Returns:
      A de-duplicated list of emails (order preserved).
    """
    emails: List[str] = []

    def _add_email(e: str) -> None:
        v = e.strip()
        if v and v not in emails:
            emails.append(v)

    # Candidate keys that may hold reviewer emails
    candidate_keys = [
        "github2gerrit.reviewersEmail",
        "g2g.reviewersEmail",
        "reviewers.email",
    ]

    for key in candidate_keys:
        vals = git_config_get_all(key) + git_config_get_all(key, global_=True)
        for v in vals:
            # Support comma-separated lists within individual values
            for part in v.split(","):
                _add_email(part)

    # Fallback to user.email (local then global)
    ue = git_config_get("user.email")
    if ue:
        _add_email(ue)
    ue_g = git_config_get("user.email", global_=True)
    if ue_g:
        _add_email(ue_g)

    return emails

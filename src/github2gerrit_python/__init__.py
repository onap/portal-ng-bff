# SPDX-License-Identifier: Apache-2.0
# SPDX-FileCopyrightText: 2025 The Linux Foundation

"""
github2gerrit_python package initializer.

This file marks the directory as a Python package to ensure static type
checkers and runtime imports can resolve relative modules such as
`from . import models` used by the CLI and other modules.

It also exposes a best-effort __version__ attribute based on installed
package metadata when available.
"""

from __future__ import annotations

try:
    # Prefer stdlib importlib.metadata (Python 3.8+)
    from importlib.metadata import PackageNotFoundError, version as _pkg_version

    try:
        __version__ = _pkg_version("github2gerrit-python")
    except PackageNotFoundError:
        __version__ = "0.0.0"
except Exception:
    # Fallback if importlib.metadata is not available or any unexpected error occurs
    __version__ = "0.0.0"

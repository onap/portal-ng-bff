#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
import argparse
import configparser
import json
from pathlib import Path

def load_vars(json_path: Path) -> dict[str, str]:
    data = json.loads(json_path.read_text(encoding="utf-8"))
    variables = data.get("variables", []) if isinstance(data, dict) else []
    return {item["name"]: str(item.get("value", "")) for item in variables if "name" in item}

def write_org_config(
    org: str,
    vars_map: dict[str, str],
    config_path: Path,
) -> None:
    config_path.parent.mkdir(parents=True, exist_ok=True)
    cp = configparser.RawConfigParser()
    if config_path.exists():
        with config_path.open("r", encoding="utf-8") as fh:
            cp.read_file(fh)
    if not cp.has_section(org):
        cp.add_section(org)

    # Map GH Actions variables -> github2gerrit config keys (extend as needed)
    mapping = {
        "GERRIT_KNOWN_HOSTS": "GERRIT_KNOWN_HOSTS",
        "GERRIT_SSH_USER_G2G": "GERRIT_SSH_USER_G2G",
        "GERRIT_SSH_USER_G2G_EMAIL": "GERRIT_SSH_USER_G2G_EMAIL",
        "GERRIT_SERVER": "GERRIT_SERVER",
        "GERRIT_SERVER_PORT": "GERRIT_SERVER_PORT",
        "GERRIT_PROJECT": "GERRIT_PROJECT",
        "ORGANIZATION": "ORGANIZATION",
        "REVIEWERS_EMAIL": "REVIEWERS_EMAIL",
        "FETCH_DEPTH": "FETCH_DEPTH",
        "SUBMIT_SINGLE_COMMITS": "SUBMIT_SINGLE_COMMITS",
        "USE_PR_AS_COMMIT": "USE_PR_AS_COMMIT",
        # Add more if your org defines them as variables
    }

    # Merge mapped variables
    for gh_name, cfg_key in mapping.items():
        if gh_name in vars_map:
            cp.set(org, cfg_key, json.dumps(vars_map[gh_name]))

    # Ensure reasonable defaults for missing entries
    if not cp.has_option(org, "GERRIT_SERVER_PORT"):
        cp.set(org, "GERRIT_SERVER_PORT", json.dumps("29418"))

    # Placeholders for secrets (you’ll provide via env or set explicitly)
    if not cp.has_option(org, "GERRIT_HTTP_USER"):
        cp.set(org, "GERRIT_HTTP_USER", json.dumps("${ENV:GERRIT_HTTP_USER}"))
    if not cp.has_option(org, "GERRIT_HTTP_PASSWORD"):
        cp.set(org, "GERRIT_HTTP_PASSWORD", json.dumps("${ENV:GERRIT_HTTP_PASSWORD}"))

    # Persist
    with config_path.open("w", encoding="utf-8") as fh:
        cp.write(fh)
    print(f"Updated [{org}] in {config_path}")

def main() -> None:
    p = argparse.ArgumentParser(description="Populate github2gerrit config from GH org variables")
    p.add_argument("--org", required=True, help="GitHub organization name (e.g., onap)")
    p.add_argument(
        "--vars-json",
        required=True,
        type=Path,
        help="Path to gh/org variables JSON (e.g., output of: gh api /orgs/<org>/actions/variables)",
    )
    p.add_argument(
        "--config",
        type=Path,
        default=Path("~/.config/github2gerrit/configuration.txt").expanduser(),
        help="Path to github2gerrit configuration file",
    )
    args = p.parse_args()

    vars_map = load_vars(args.vars_json)
    write_org_config(args.org, vars_map, args.config)

if __name__ == "__main__":
    main()

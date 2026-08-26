# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| `main` (development) | ✅ Active development — security fixes land here first |
| Tagged releases (when published) | ✅ Latest tagged release |

AURA is currently **Development / Early Open Source**. There are no long-term stable branches yet. If you are running a tagged release, please check whether the fix is already on `main`.

## Reporting a Vulnerability

**Do not open a public GitHub issue for an exploitable vulnerability.**

AURA handles sensitive Android surfaces (launcher role, contacts via `READ_CONTACTS`, calendar via `READ_CALENDAR`, `AppWidgetHost`). Please give maintainers a chance to investigate before public disclosure.

**Preferred:** Use GitHub’s **“Report a vulnerability”** private reporting for this repository ( `Security` → `Report a vulnerability` ). This creates a private advisory visible only to maintainers.

**Alternative:** Open a GitHub issue with the `security` label and *without* exploit details, asking for a private contact. Do not paste exploit steps, notification content, or contact/message data in a public issue.

Include:

- AURA version / commit (`git log --oneline -1`)
- Android version / device / OEM
- Reproducible steps **without private data**
- Expected vs actual behavior
- Whether a permission, dependency, or design token is involved

We aim to acknowledge within **3 business days** and to provide a timeline after triage. We will credit reporters unless you prefer to remain anonymous.

## Disclosure

Please allow **90 days** or until a fix is released (whichever is sooner) before public disclosure, to give users time to update. We will coordinate disclosure with you.

## What not to report publicly

- Contact or message content, notification content, or any private data — strip it from logs.
- Issues that require a malicious app already holding system permissions outside AURA’s threat model — still report privately, but note the precondition.

## Maintainer configuration

This policy intentionally does not list a personal email. Repository owners should enable GitHub private vulnerability reporting and optionally add a `SECURITY_CONTACT` in the repository settings. Contributors should not invent a contact address.

If you are a maintainer reading this: configure a private contact (GitHub advisory or a dedicated security email) and update this file to point to it — do not leave reporters without a private channel.

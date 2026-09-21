# Security policy

## Supported version

Only the latest GitHub release is supported.

## Reporting a vulnerability

When the repository is public, use a private GitHub security advisory. Do not
post credentials, API keys, private prompts or exploit details in a public
issue.

## Security design

- Inference server and MCP bridge bind to loopback only.
- Web fetching is restricted to public HTTPS targets; private/local ranges and
  unsafe redirects are rejected.
- Web tools are disabled by default.
- Brave API keys are encrypted with Android Keystore with no plaintext fallback.
- Model assets are checked against publisher-pinned size and SHA-256 values.
- No production signing keystore or API secret belongs in Git.
- The app does not request broad filesystem access.

Models, prompts and fetched web pages are untrusted input. Remote pages may
contain prompt-injection content. Do not enable tool capabilities you do not
want the model to use.

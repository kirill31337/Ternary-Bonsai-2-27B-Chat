# Android release signing

Android updates require every release of a package to be signed by the same
private key. Losing or publishing that key breaks or compromises direct APK
updates.

## Public repository policy

- Never commit `.jks`, `.keystore`, private keys or passwords.
- Keep the long-lived release key backed up offline.
- Put CI signing material only in protected repository/app-store secrets.
- CI source-validation builds may use an ephemeral test key, but a public
  release APK should use the maintainer's long-lived release key.

The 1.0.0 APK distributed by the maintainer is signed with the historical
compatibility key so existing experimental installs can update. That private
key is deliberately absent from the public source tree.

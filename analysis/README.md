# Analysis record

Durable, non-proprietary part of the reverse-engineering pass over Nova Launcher 8.0.18
(`com.teslacoilsw.launcher`, versionCode 80018). Provenance and conclusions are in
[`docs/investigation.md`](../docs/investigation.md).

| File | What it is |
|---|---|
| `artifact.json` | REA `inspect_artifact` record: evidence id, subject digest, coverage, limitations |
| `manifest.xmltree` | `aapt2 dump xmltree` of the APK manifest — 42 activities, 16 receivers, 11 services, 6 providers |
| `classes.txt` | 11,242 class descriptors from `dexdump -f`, sorted — the obfuscated package map |
| `verify/*.xml` | `uiautomator` hierarchy dumps from the two verification devices (Nova on the S23, this launcher on both) |

Not committed: the pulled APK, the `jadx` source tree, the extracted `classes.dex`, raw
`dexdump` output (29 MB) and screenshots. Those stay on the analysis workstation under
`artifacts/`, `work/` and `evidence/`; they are third-party material or bulk derived output.

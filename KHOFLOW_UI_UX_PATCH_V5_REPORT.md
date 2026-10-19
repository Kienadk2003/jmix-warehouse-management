# KhoFlow UI/UX patch v5

Changes applied to the v4 source archive:
- Removed the CSS background-image magnifying glass that was still being reintroduced in Aura theme; explicitly disabled it for Aura and Lumo.
- Reset search input leading padding to prevent placeholder text overlap.
- Changed detail action bar from sticky overlay to normal flow so it does not cover the transfer item grid.
- Added Vietnamese and English labels for Unit.decimalScale and Partner.partnerType and active flags.

Not yet verified by a Windows Gradle build or browser runtime. Language switching may still expose other hard-coded labels/keys on pages not covered by these label additions; this patch does not claim complete application-wide translation. Business logic and security code were not changed.

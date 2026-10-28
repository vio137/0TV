# Recovery: what it can and cannot do

Backups and rollback are safeguards, not an unconditional undo guarantee.

- Normal cleanup disables selected packages. Restore uses recorded prior state to re-enable or restore supported changes.
- Separate removal paths have stronger limits. Permanently deleted app data cannot be recovered by this app.
- Keep 0TV installed on the phone. Uninstalling it can erase local recovery history. TV-side records are not a promise that all history will survive a lost phone or reinstall.
- Optional TizenTube/Cobalt installation is **not** rolled back by Restore.
- DNS undo depends on the saved prior values and a matching TV.
- Device-bound records require Android user 0 and a matching hash of the Android ID. A reset or firmware change may change that ID. Missing or corrupt records can refuse an action. An Android ID is not a perfect hardware-unique identifier; the check is not transactional.
- Full phone-side cleanup and restore have not been proven end to end on physical hardware for this build.

If Home fails, try the physical remote's Home or Settings key, select the old launcher where possible, or use the app's rollback. If those routes do not work, use the TV maker's recovery instructions for the exact model. A factory reset erases apps, accounts and settings. It is a last resort, not a routine troubleshooting step.

Do not bypass protection or TV identity checks to force a restore. If the state is uncertain, stop and inspect it before making further changes.

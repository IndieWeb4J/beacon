# Changelog

## [0.3.0](https://github.com/jacobsandersen/beacon/compare/v0.2.0...v0.3.0) (2026-10-06)


### Features

* configurable JetStream stream replicas ([a738625](https://github.com/jacobsandersen/beacon/commit/a738625ef21e3a13c8081fc7f709a8da208dfadf))
* make JetStream stream replicas configurable ([1c7ee47](https://github.com/jacobsandersen/beacon/commit/1c7ee4790e321f3519d9d027c13e4fd40675871d))


### Tests

* cover the reconciliation sweep ([12fe610](https://github.com/jacobsandersen/beacon/commit/12fe6105387aaddaf08fbc68fc093174fd1d1a21))
* cover the reconciliation sweep ([01ad392](https://github.com/jacobsandersen/beacon/commit/01ad3928de6ed82858bb756bffec24fd62e86975))

## [0.2.0](https://github.com/jacobsandersen/beacon/compare/v0.1.0...v0.2.0) (2026-10-06)


### Features

* add the reconciliation sweep ([1dc28bb](https://github.com/jacobsandersen/beacon/commit/1dc28bb8ce2726f98acb648c65f3e205291c2819))
* Beacon webmention service ([543d02f](https://github.com/jacobsandersen/beacon/commit/543d02f7abfe0ed929ef44a535307a7bba2d3e82))
* extract webmention into a multi-module Beacon service ([e144642](https://github.com/jacobsandersen/beacon/commit/e14464245188a491bf5d11b282f290f6d26f086c))
* webmention receiver wired to content-client ([b99c2da](https://github.com/jacobsandersen/beacon/commit/b99c2dacd14cda0c492d7ad064b7524e78212ee7))
* webmention send service + conduit scaffold ([956c374](https://github.com/jacobsandersen/beacon/commit/956c374089f81d4d2686517af2df1a2159b7f52b))


### Bug Fixes

* extend the shared DISTRIBUTION stream instead of clobbering it ([927963d](https://github.com/jacobsandersen/beacon/commit/927963dce423b8613ef10b91e98a629f1e9b1eb2))


### Code Refactoring

* own the WEBMENTION stream (stream per producer) ([e4a53a3](https://github.com/jacobsandersen/beacon/commit/e4a53a399bf28a4a1f8d6de58296855477553e08))


### Build System

* consume content-client from Bastion packages ([336205d](https://github.com/jacobsandersen/beacon/commit/336205d6cf2b4e77c075894d45c54c6b752df434))


### Continuous Integration

* add a workflow to publish beacon-client to GitHub Packages ([1d3451c](https://github.com/jacobsandersen/beacon/commit/1d3451c1640c70598f398391d1907b366bb87af7))
* add lint and test workflow ([a30b371](https://github.com/jacobsandersen/beacon/commit/a30b371865c102ce419db6e3f5ee0475e501da33))
* add release-please and a distroless image pipeline ([237fb75](https://github.com/jacobsandersen/beacon/commit/237fb757fd7e078d5280997843c7845193253c66))
* authenticate package reads with a dedicated PACKAGES_TOKEN ([cc485c4](https://github.com/jacobsandersen/beacon/commit/cc485c4e27cb40dfea18b921cb6206404d1d34bc))
* read private content-client via a packages token ([32510d7](https://github.com/jacobsandersen/beacon/commit/32510d7a7994d1708045058cff6208893cbf9144))
* release-please + distroless image pipeline ([5d8b504](https://github.com/jacobsandersen/beacon/commit/5d8b50486656deee2674de82ace54ea3f044eedb))

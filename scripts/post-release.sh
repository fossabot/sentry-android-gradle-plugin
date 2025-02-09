#!/bin/bash
set -euo pipefail

GRADLE_FILEPATH="plugin-build/gradle.properties"

git checkout main

# Add a new unreleased entry in the changelog
perl -pi -e 's/# Changelog/# Changelog\n\n## Unreleased/' CHANGELOG.md

# Increment `version` and make it a snapshot
# Incrementing the version before the release (`bump-version.sh`) sets a
# fixed version until the next release it's made. For testing purposes, it's
# interesting to have a different version name that doesn't match the
# name of the version in production.
# Note that the version must end with a number: `1.2.3-alpha` is a semantic
# version but not compatible with this post-release script, and `1.2.3-alpha.0`
# should be used instead.
VERSION_PATTERN="version"
version="$( awk "/$VERSION_PATTERN/" $GRADLE_FILEPATH | egrep -o '[0-9].*$' )" # from the first digit until the end
version_digit_to_bump="$( awk "/$VERSION_PATTERN/" $GRADLE_FILEPATH | egrep -o '[0-9]+$')"
((version_digit_to_bump++))
# Using `*` instead of `+` for compatibility. The result is the same,
# since the version to be bumped is extracted using `+`.
new_version="$( echo $version | sed "s/[0-9]*$/$version_digit_to_bump/g" )"
perl -pi -e "s/$VERSION_PATTERN = .*$/$VERSION_PATTERN = $new_version-SNAPSHOT/g" $GRADLE_FILEPATH

git add CHANGELOG.md $GRADLE_FILEPATH
git commit -m "Prepare $new_version"
git push

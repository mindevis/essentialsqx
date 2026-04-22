/** @type {import('semantic-release').Options} */
module.exports = {
  branches: ['main'],
  tagFormat: 'v${version}',
  plugins: [
    '@semantic-release/commit-analyzer',
    '@semantic-release/release-notes-generator',
    ['@semantic-release/changelog', { changelogFile: 'CHANGELOG.md' }],
    [
      '@semantic-release/exec',
      {
        prepareCmd:
          'node scripts/bump-gradle-mod-version.mjs "${nextRelease.version}"',
      },
    ],
    [
      '@semantic-release/git',
      {
        assets: ['CHANGELOG.md', 'gradle.properties'],
        message: 'chore(release): ${nextRelease.version} [skip ci]',
      },
    ],
    [
      '@semantic-release/exec',
      {
        publishCmd:
          'chmod +x gradlew && ./gradlew clean build --no-daemon && bash scripts/collect-release-jars.sh',
      },
    ],
    [
      '@semantic-release/exec',
      {
        publishCmd: 'bash scripts/git-push-main-retry.sh',
      },
    ],
    [
      '@semantic-release/github',
      {
        assets: [{ path: 'release-jars/*.jar' }],
        successCommentCondition: false,
        failCommentCondition: false,
        releasedLabels: false,
      },
    ],
  ],
}

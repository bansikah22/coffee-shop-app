# Manual GitHub Sync

GitLab remains the source of truth. The optional `github:sync` GitLab CI job pushes the selected pipeline's commit on the protected default branch to the same branch in [bansikah22/coffee-shop-app](https://github.com/bansikah22/coffee-shop-app).

## One-Time Setup

1. Sign in to GitHub as `bansikah22`. Open your profile menu > **Settings > Developer settings > Personal access tokens > Fine-grained tokens > Generate new token**. You can also open [the new token form](https://github.com/settings/personal-access-tokens/new).
2. Name it `gitlab-coffee-shop-sync`, choose a short expiration appropriate for your rotation schedule (for example, 90 days), and select `bansikah22` as the resource owner.
3. Under **Repository access**, select **Only select repositories**, then choose `coffee-shop-app`.
4. Under **Repository permissions**, grant **Contents: Read and write** and **Workflows: Read and write**. The latter is required because synchronization includes `.github/workflows/verify.yml`. Metadata read access is implicit. No account-wide or Actions write permissions are needed for this Git push.
5. Generate the token. GitHub displays its value only once. Enter it directly into GitLab's variable form; do not paste it into chat, a command, or a repository file.
6. In GitLab, open **Settings > CI/CD > Variables > Add variable**. Set the key to `GITHUB_SYNC_TOKEN`, type to **Variable** (not File), and value to the generated token. Mark it **Masked**, **Protected**, and **Hidden** if available. Disable variable-reference expansion and save. Never put the token in a Git URL or pipeline input.
7. Ensure the GitLab default branch (`main` currently) is protected under the repository's branch rules. Otherwise the sync job will not appear. GitHub branch rules must also permit the token owner's normal push; do not grant broad bypass permissions to work around a rejection.

## Run the Sync

Open a default-branch pipeline in GitLab and click the play button for `github:sync`. Alternatively, use **Build > Pipelines > New pipeline**, select the default branch, then start its `github:sync` job. The job is always manual; creating a pipeline does not automatically sync GitHub.

The job can run independently of builds and tests. Choose a pipeline whose commit you have already verified. It pushes that exact commit, not whichever commit happens to be newest when the job starts. GitLab retains its existing `origin` remote; no local GitHub remote is required.

## Safety and Scope

- Only the protected default branch is eligible. Merge-request and feature-branch pipelines cannot run this job.
- The token is supplied through a process-local Git credential helper, not embedded in the remote URL or stored in Git configuration. Do not enable shell tracing or `CI_DEBUG_TRACE` for this job.
- Full Git history is fetched so existing ancestors are available for a normal push.
- Only the default branch is pushed. Tags, other branches, releases, issues, secrets, and container images are not synchronized.
- There is no `--force`, `--mirror`, or branch deletion. GitHub-only divergent commits cause a non-fast-forward rejection and must be reviewed and reconciled deliberately. An older pipeline cannot rewind the GitHub branch.
- A resource group serializes sync jobs. Failure of this optional job does not block the application's pipeline; inspect the job log to confirm synchronization succeeded.
- Rotate the token before expiry, revoke it immediately if exposed, and restrict who can change protected-branch CI configuration and run protected jobs.

## Verification

After a successful job, compare the GitHub default-branch commit SHA with the selected GitLab pipeline's `CI_COMMIT_SHA`. A rejected push is not a successful sync: check token expiry/permissions, branch rules, and divergent history without force-pushing.

## GitHub Verification Workflow

The repository includes `.github/workflows/verify.yml`, which runs on pushes to `main`, pull requests targeting `main`, and manual dispatch. It verifies the frontend with Node.js 26 (`npm ci`, lint, and production build) and the backend with Java 25 and the Maven wrapper (`clean verify`, including tests and packaging). The payment service is still documentation and placeholders, so it has no build job yet.

GitHub Actions must be enabled for the repository under **Settings > Actions > General**, with the referenced GitHub-maintained actions allowed. After the workflow reaches the default branch, open **Actions > Verify > Run workflow** to start it manually. Normal sync pushes authenticated by the personal access token also trigger it automatically. The workflow itself uses only `contents: read`; it does not use `GITHUB_SYNC_TOKEN`, publish images, or deploy anything.

## Dependabot

`.github/dependabot.yml` schedules weekly Monday checks in the `Africa/Douala` timezone for backend Maven dependencies, frontend npm dependencies, both Dockerfiles, and GitHub Actions. No payment-service manifest exists yet, so there is nothing to check there. Docker Compose service image tags are not covered by the Dockerfile entries and still need separate review.

After synchronization, check GitHub **Settings > Advanced Security** (or **Code security**, depending on the interface) and enable Dependabot alerts and Dependabot security updates where available. The configuration schedules version updates; it does not itself enable those repository security settings. No auto-merge is configured, and the verification workflow needs no secrets to test Dependabot pull requests.

GitLab is still the source of truth. Review Dependabot pull requests on GitHub, apply the proposed changes in a GitLab branch, and merge through GitLab CI before syncing the default branch back to GitHub. Close the corresponding Dependabot pull request once the update is present. Do not merge it directly into GitHub's default branch: that creates GitHub-only history and makes the next non-force sync fail.
# coffee-shop-app



## Getting started

To make it easy for you to get started with GitLab, here's a list of recommended next steps.

Already a pro? Just edit this README.md and make it your own. Want to make it easy? [Use the template at the bottom](#editing-this-readme)!

## Add your files

* [Create](https://docs.gitlab.com/user/project/repository/web_editor/#create-a-file) or [upload](https://docs.gitlab.com/user/project/repository/web_editor/#upload-a-file) files
* [Add files using the command line](https://docs.gitlab.com/topics/git/add_files/#add-files-to-a-git-repository) or push an existing Git repository with the following command:

```
cd existing_repo
git remote add origin https://gitlab.com/ntech3431234/coffee-shop-app.git
git branch -M main
git push -uf origin main
```

## Integrate with your tools

* [Set up project integrations](https://gitlab.com/ntech3431234/coffee-shop-app/-/settings/integrations)

## Collaborate with your team

* [Invite team members and collaborators](https://docs.gitlab.com/user/project/members/)
* [Create a new merge request](https://docs.gitlab.com/user/project/merge_requests/creating_merge_requests/)
* [Automatically close issues from merge requests](https://docs.gitlab.com/user/project/issues/managing_issues/#closing-issues-automatically)
* [Enable merge request approvals](https://docs.gitlab.com/user/project/merge_requests/approvals/)
* [Set auto-merge](https://docs.gitlab.com/user/project/merge_requests/auto_merge/)

## Test and Deploy

Use the built-in continuous integration in GitLab.

* [Get started with GitLab CI/CD](https://docs.gitlab.com/ci/quick_start/)
* [Analyze your code for known vulnerabilities with Static Application Security Testing (SAST)](https://docs.gitlab.com/user/application_security/sast/)
* [Deploy to Kubernetes, Amazon EC2, or Amazon ECS using Auto Deploy](https://docs.gitlab.com/topics/autodevops/requirements/)
* [Use pull-based deployments for improved Kubernetes management](https://docs.gitlab.com/user/clusters/agent/)
* [Set up protected environments](https://docs.gitlab.com/ci/environments/protected_environments/)

***

# Editing this README

When you're ready to make this README your own, just edit this file and use the handy template below (or feel free to structure it however you want - this is just a starting point!). Thanks to [makeareadme.com](https://www.makeareadme.com/) for this template.

# Coffee Shop GitOps POC

Coffee Shop is a React frontend and Spring Boot API backed by PostgreSQL. It is the application source repository for the GitOps proof of concept; environment manifests and promotion history will live in a separate Helm repository.

## Architecture

The browser receives the React application from Nginx. Nginx routes `/api/` to the Spring Boot API, so deployed users use one origin and do not need a browser-facing backend URL. The API owns the PostgreSQL connection.

```text
Browser -> Nginx frontend -> /api -> Spring Boot API -> PostgreSQL
```

`CORS_ALLOWED_ORIGINS` is an explicit API allowlist for cases where a browser accesses the API from another origin. The Compose path is same-origin, so CORS is not needed for normal application requests.

## Local Stack

Prerequisites: Docker Compose, Java 25, Maven, Node.js 26, and npm.

```sh
cd backend
./mvnw test
./mvnw package -DskipTests
cd ..
docker compose up --build --detach
```

Open `http://localhost:8088`. The API is available at `http://localhost:18080`; health is at `/actuator/health`. Stop the stack with `docker compose down`; add `--volumes` to reset local database data.

The backend Dockerfile intentionally packages the previously built jar. This prevents an image build from repeating tests and dependency resolution. CI runs Maven tests and package before building the API runtime image.

## CI/CD

The GitLab pipeline performs these stages:

1. `frontend:verify`: locked dependency installation, lint, and production build.
2. `backend:test`: Maven test suite.
3. `backend:package`: package a Spring Boot jar artifact.
4. Component-specific container verification jobs build only changed images for non-default branches.
5. Component-specific publish jobs publish changed images on the default branch with the full commit SHA tag.

GitLab runners must support Docker-in-Docker with privileged mode for the container jobs. The project registry is accessed only by the default-branch publish job using GitLab-provided registry credentials. Deployment promotion is intentionally not in this repository yet: the next phase will update immutable image digests in the separate Helm GitOps repository through a merge request.
## Project status

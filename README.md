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

Coffee Shop is a React frontend and Spring Boot API backed by PostgreSQL. Customers can browse coffee, sign in, place orders, and see their own order history. Administrators can manage the catalog and review all orders. It is the application source repository for the GitOps proof of concept; environment manifests and promotion history live in a separate Helm repository.

## Architecture

See the [application flow diagram](docs/architecture/application-flow.svg) and its [editable PlantUML source](docs/architecture/application-flow.puml). It shows the current local runtime, authentication and authorization boundaries, catalog/order processing, and future integrations.

Read the diagrams in this order:

1. **A1 - Application overview:** follow steps 1-6 across the browser, proxy, API and database. Blue identifies frontend delivery, green backend logic, and amber persisted data.
2. **A2 - Customer journey:** [numbered sequence diagram](docs/architecture/customer-journey.svg) ([PlantUML source](docs/architecture/customer-journey.puml)) covering sign-in, role-based redirects, price calculation, order persistence, and access control. Return proxy hops are condensed for readability.

The browser receives the React application from Nginx. Nginx routes `/api/` to the Spring Boot API, so deployed users use one origin and do not need a browser-facing backend URL. The API owns the PostgreSQL connection.

```text
Browser -> Nginx frontend -> /api -> Spring Boot API -> PostgreSQL
```

`CORS_ALLOWED_ORIGINS` is an explicit API allowlist for cases where a browser accesses the API from another origin. The Compose path is same-origin, so CORS is not needed for normal application requests.

## Source Layout

The backend groups similar classes by technical role: `domain` contains persisted business models, `repository` contains persistence interfaces, `service` contains application rules, and `web` contains HTTP controllers and request/response contracts. Each package has a `package-info.java` description, and each type documents its responsibility. Cross-cutting Spring configuration lives in `configuration`.

The frontend uses feature ownership: `features/catalog` owns catalog types, data operations, and catalog UI; `features/orders` owns order submission and history; `features/auth` owns account state and authentication screens; `shared/api` contains the CSRF-aware same-origin request client; `shared/navigation` contains responsive navigation and the site footer. React Router provides `/`, `/login`, `/register`, `/orders`, and `/admin` routes. Lucide React supplies the shopping-bag order icon.

The original `public/ember-bean-mark.svg` is reused as the browser favicon and visible site mark.


Authentication uses Spring Security sessions and CSRF protection. Local email/password login remains available; with the optional SSO Compose profile, Keycloak provides verified self-registration, sign-in, and password reset, while the backend provisions the app's customer account on first verified sign-in. Order confirmations are sent after placement, and administrators can mark orders ready to trigger a second customer email. Successful authentication routes administrators to `/admin` and customers to the coffee board. Authorization is enforced by the API, not only by hiding controls: coffee writes and the all-orders endpoint require `ADMIN`; creating and listing personal orders requires a signed-in account. See [docs/keycloak.md](docs/keycloak.md) for local SSO setup and the production boundary.

```text
backend/src/main/java/.../coffeeshop/
	domain/                shared account, catalog, and order models
	repository/            persistence interfaces
	configuration/auth/    security, Keycloak, and account initialization
	service/auth/          local and OIDC account use cases
	service/catalog/       coffee catalog rules
	service/orders/        placement, fulfillment, events, and customer emails
	web/auth/              authentication endpoints/contracts
	web/catalog/           catalog endpoints/contracts
	web/orders/            customer and administrator order endpoints/contracts

frontend/src/
	features/catalog/
	features/orders/
	features/auth/
	shared/api/
	shared/navigation/
	App.tsx
```

Backend class documentation describes each type's responsibility. Introduction dates are based on the first Git commit containing the class, not filesystem timestamps; the current backend types were introduced in the initial commit on 2026-10-13. Frontend components and hooks are documented by their feature and file names, while their addition dates remain available through Git history.

## Engineering Guidance

This project uses the relevant, tailored practices from [engineering-skills](https://github.com/bansikah22/engineering-skills): focused behavior-preserving refactors, feature-first ownership, minimal abstractions, and validation with the narrowest relevant checks. The shared guidance is a reference, not a substitute for this repository's architecture and constraints.

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

The local Compose stack activates the `dev` Spring profile and seeds disposable demo accounts:

| Role | Email | Password |
| --- | --- | --- |
| Admin | `admin@example.com` | `CoffeeAdmin123!` |
| Customer | `customer@example.com` | `CoffeeCustomer123!` |

These known credentials are only for local development. Do not activate the `dev` profile in a shared or production environment. Production admin provisioning uses `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD`; keep them in deployment secret storage and use a unique strong password. Customer passwords are BCrypt-hashed; registration requires at least 12 characters.

The current coffee board maps the bundled assets in `frontend/src/assets` to Americano, Cappuccino, Flat White, and Seasonal Mocha. Coffee cards prefer an API-provided `imageUrl` when present, then fall back to these static images. Additional products without an image keep the image-free card layout. Admin-managed uploads can later replace the static map by persisting image references from object storage.

For the optional local Keycloak SSO provider, realm fixture, and production boundary notes, see [docs/keycloak.md](docs/keycloak.md). Keycloak is not started by the default Compose stack.

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

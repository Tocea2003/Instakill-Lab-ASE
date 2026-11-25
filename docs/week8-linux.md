# Week 8: Running the Instakill backend on Linux

## Goal

Build and run the backend on a Linux machine with PostgreSQL, and use it from the Vue frontend running on the host
machine. The assignment is written for WSL on Windows. This repo is developed on an Apple-silicon Mac, which has no
WSL, so the Linux machine is an Ubuntu Server virtual machine in UTM. It plays the same role: a separate Linux box
with its own IP address that the host reaches over a virtual network.

## Setup

| Item | Value |
|---|---|
| Host | Apple-silicon Mac (macOS), runs the frontend (`npm run dev`) |
| Linux | UTM virtual machine, **Virtualize** mode (not emulation), Ubuntu Server 24.04.5 LTS **arm64** (`ubuntu-24.04.5-live-server-arm64.iso`), 4+ GB RAM, 4 CPUs, 25 GB disk, OpenSSH server installed |
| Network | UTM "Shared Network": the VM gets an address like `192.168.64.x` that the Mac can reach directly |
| Java | `openjdk-21-jdk` from the Ubuntu archive |
| Database | PostgreSQL 16 from the Ubuntu archive, used through the existing `postgres` Spring profile |

## Changes in the repository

| File | Before | After | Why |
|---|---|---|---|
| `backend/build.gradle.kts` | toolchain `JavaLanguageVersion.of(23)` | `JavaLanguageVersion.of(21)` | Ubuntu 24.04 only packages JDK 21. The code uses no Java 22/23 features: `main` and `test` both compile with `javac --release 21`. |
| `frontend/vite.config.js` | proxy targets hard-coded to `http://localhost:8080` | `process.env.BACKEND_URL ?? 'http://localhost:8080'` | Point the dev server at the VM without editing (and committing) its IP. The default stays the same, so local development is unchanged. |
| `README.md`, `CLAUDE.md` | "Java 23" | "Java 21" | Keep the docs in sync with the toolchain. |

No backend configuration change was needed. `application-postgres.yaml` (added in Week 6) already defaults to
`jdbc:postgresql://localhost:5432/instakill`, user `instakill`, password `instakill_password`, which are the values
the assignment creates. Spring Boot listens on all interfaces by default, so the VM's port 8080 is reachable from the
Mac.

Because the frontend talks to the backend through the Vite proxy, the browser only ever sees `localhost:5173`. The
`ACCESS_TOKEN` cookie stays first-party and no CORS configuration is needed.

## Steps

### 1. Tools in the VM

```bash
sudo apt update && sudo apt upgrade -y
sudo apt install git openjdk-21-jdk unzip postgresql postgresql-contrib -y
git --version
java -version
```

### 2. PostgreSQL

```bash
sudo service postgresql status      # "active (exited)" is normal: the real cluster runs as postgresql@16-main
sudo service postgresql start       # only if it is not running
sudo -u postgres psql
```

```sql
CREATE DATABASE instakill;
CREATE USER instakill WITH PASSWORD 'instakill_password';
GRANT ALL PRIVILEGES ON DATABASE instakill TO instakill;
\c instakill
GRANT ALL ON SCHEMA public TO instakill;
ALTER SCHEMA public OWNER TO instakill;
\q
```

```bash
psql -h localhost -U instakill -d instakill    # check the login works, then \q
```

### 3. Clone and build

```bash
cd ~
git clone -b Week8_Linux https://github.com/Tocea2003/Instakill-Lab-ASE.git
ls -al
cd Instakill-Lab-ASE
./scripts/bootstrap-gradle.sh
cd backend
chmod +x gradlew          # only if "permission denied"
./gradlew build           # compiles, runs all tests, produces build/libs/instakill-0.1.0-SNAPSHOT.jar
ls -l build/libs
```

### 4. Run the backend on PostgreSQL

```bash
SPRING_PROFILES_ACTIVE=postgres ./gradlew bootRun
```

In a second terminal (or SSH session):

```bash
curl http://localhost:8080/api/health
hostname -I               # the VM's IP, e.g. 192.168.64.5
```

On the first start Hibernate creates the schema in PostgreSQL and `DataInitializer` seeds the users `alice` and
`bob` (password `password`).

### 5. Frontend on the Mac

```bash
curl http://<VM_IP>:8080/api/health
cd frontend
npm install
BACKEND_URL=http://<VM_IP>:8080 npm run dev
```

Open http://localhost:5173, log in as `alice` / `password`, create a post and like it.

## Proof of completion

1. Linux terminal with `./gradlew build` ending in `BUILD SUCCESSFUL`.
2. Linux terminal with the backend running (`Started InstakillApplication`) and `curl .../api/health` answering.
3. The frontend on the Mac, logged in, showing the feed served by the VM.

## Reflection

_To be written after the run: Linux commands used and the problems met while connecting the systems._

## Files touched

- `backend/build.gradle.kts`
- `frontend/vite.config.js`
- `README.md`
- `CLAUDE.md`
- `docs/week8-linux.md`

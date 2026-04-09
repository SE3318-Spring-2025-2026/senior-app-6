# Local Development Setup

## Prerequisites

- Java 21
- MySQL 8.0 (https://dev.mysql.com/downloads/installer)
- Node.js 18+ or Bun (https://bun.sh)
- Maven (via `mvnw` wrapper, no separate install needed)

---

## MySQL Setup

### Installation

1. Download the MySQL Installer from https://dev.mysql.com/downloads/installer
2. Run the installer and select **Full** installation type
3. During configuration, set a root password — remember it, you will need it once
4. Complete the installation and make sure the MySQL service is set to start automatically

### Creating the Database and User

1. Open **MySQL Workbench** (installed alongside MySQL) or open a terminal and run:
   ```bash
   mysql -u root -p
   ```
   Enter your root password when prompted

2. You are now in the MySQL shell. Run the following commands one by one:

   ```sql
   CREATE DATABASE spm;
   CREATE USER 'spm'@'localhost' IDENTIFIED BY 'yourpassword';
   GRANT ALL PRIVILEGES ON spm.* TO 'spm'@'localhost';
   FLUSH PRIVILEGES;
   ```

   Replace `yourpassword` with a password of your choice — this is what goes into `application.properties`, not the root password

3. Verify it worked:
   ```sql
   SHOW DATABASES;
   SHOW GRANTS FOR 'spm'@'localhost';
   ```

   You should see `spm` in the database list and the grant for the `spm` user

4. Exit the shell:
   ```sql
   EXIT;
   ```

---

## Backend

1. Navigate to the `backend/` folder

2. Create `application.properties` file inside of `src/main/resources` and fill in your values — refer to the example file `src/main/resources/application.properties.example` for all required keys.

3. Run the backend:

**Windows:**
```
.\mvnw.cmd spring-boot:run
```

**macOS / Linux:**
```bash
./mvnw spring-boot:run
```

Backend runs on `http://localhost:8080`

---

## Frontend

1. Navigate to the `frontend/` folder

2. Install dependencies:

```bash
bun install
```

> (Alternative) You can also use `npm install` if you don't have Bun

3. Create a `.env` file from `env.example` and fill in your values.

4. Run the frontend:

```bash
bun run dev
```

> (Alternative) You can also use `npm run dev`

Frontend runs on `http://localhost:3000`

---

## IMPORTANT Notes

- Make sure MySQL is running before starting the backend
- Backend must be running before the frontend can fetch data
- !! Dont forget to create `application.properties` inside `src/main/resources/` and fill it with your credentials
- You need to fill `backend\src\main\resources\application.properties` and `frontend\.env`
- `application.properties` and `.env` are gitignored — never commit them
- If you get error related to java run `java -version` and `./mvnw --version` codes inside of backend folder in terminal, be sure both of them use java 21
- jwt.token needs a base64 encoded string which is generated from a string that is at least 32 characters long
- for testing purposes the passwords of the admin, coordinator and professor are `test`, dev only

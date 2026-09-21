# Resource Backend (Spring Boot + MySQL)

Backend for the `login-resource-app` Angular frontend. Stores every resource
(name + role) added from the "Add Resource" popup in a MySQL database table
called `RESOURCES`, so the Resource page always shows everything ever saved —
not just what was added in the current browser session.

## 1. Configure the MySQL connection

Make sure MySQL is running locally (or point at a remote instance), then edit
`src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/resource_db?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC
spring.datasource.username=your_db_username
spring.datasource.password=your_db_password
```

- `createDatabaseIfNotExist=true` means you don't need to manually create the
  `resource_db` schema first — MySQL will create it on first connection.
- No need to create the `RESOURCES` table yourself either —
  `spring.jpa.hibernate.ddl-auto=update` makes Hibernate create it automatically
  the first time the app starts, based on the `Resource` entity (with an
  auto-increment `ID` column).

## 2. Run the backend

```bash
mvn spring-boot:run
```

It starts on `http://localhost:8080`.

## 3. API endpoints

| Method | Path                     | Purpose                                   |
|--------|--------------------------|--------------------------------------------|
| GET    | /api/resources           | List all saved resources                   |
| GET    | /api/resources/{id}      | Get one resource                           |
| POST   | /api/resources           | Add a resource — body: `{"name","role"}`   |
| PUT    | /api/resources/{id}      | Update a resource                          |
| DELETE | /api/resources/{id}      | Delete a resource                          |
| GET    | /api/tasks               | List all saved tasks                       |
| POST   | /api/tasks                | Add a task — body: `{"taskName","taskCode"}` |
| DELETE | /api/tasks/{id}           | Delete a task                              |
| GET    | /api/assignments          | List all saved assignments                 |
| POST   | /api/assignments          | Add an assignment — body: `{"resourceName","taskName","taskCode","month","year"}` |
| DELETE | /api/assignments/{id}     | Delete an assignment                       |

Hibernate auto-creates three tables on first startup: `RESOURCES`, `TASKS`, and `ASSIGNMENTS`.

CORS is pre-enabled for `http://localhost:4200` (the Angular dev server).

## 4. Frontend wiring (already done in the Angular project)

- After login, you land on a Home page (`/home`) with two buttons: **Resource** and **User**.
- **Resource** goes to `/resources` — the original resource management page.
- **User** goes to `/user` — pick a Resource, a Task, a Month, and a Year, then
  save to create an Assignment record. Tasks are added via their own "+ Add Task"
  button on this same page.
- `ResourceService`, `TaskService`, and `AssignmentService` (in `src/app/services/`)
  each call their matching backend endpoints.
- All list-driven components use Angular **signals**, since this project runs
  without Zone.js (zoneless change detection) — signals make sure the UI
  updates immediately when data loads or changes, without needing an extra
  click first.

Just run `ng serve` for the frontend and `mvn spring-boot:run` for the backend
at the same time — the Login page still just checks the format of the
username/password locally, then the Resource page talks to MySQL through
this API for anything you add.

## Troubleshooting the Save button

If clicking Save doesn't persist data:
1. Open browser DevTools (F12) -> Console and Network tabs, click Save again,
   and check for errors or a failed request to /api/resources.
2. Make sure the Spring Boot console shows the app started with no MySQL
   connection errors.
3. Double check the MySQL username/password/URL in application.properties.
4. A CORS error in the console usually means the frontend isn't running on
   http://localhost:4200.

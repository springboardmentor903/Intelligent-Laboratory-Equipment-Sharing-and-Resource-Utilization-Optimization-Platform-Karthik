\# Intelligent Laboratory Equipment Sharing and Resource Utilization Optimization Platform



\## Overview



The Intelligent Laboratory Equipment Sharing and Resource Utilization Optimization Platform is a web application designed to support laboratory equipment management, equipment booking, and resource utilization tracking.



The project aims to provide a centralized platform for managing laboratory equipment and booking requests, while supporting maintenance records and utilization tracking.



\## Technology Stack



\### Backend

\- Java

\- Spring Boot

\- Spring Security

\- JWT-based authentication

\- Spring Data JPA

\- Maven



\### Frontend

\- React

\- Vite

\- JavaScript

\- HTML and CSS



\### Database

\- PostgreSQL



\### API Development

\- REST APIs

\- Postman for API testing



\## Implemented Modules



\- \*\*Authentication:\*\* User registration, login, JWT authentication, and password-reset functionality.

\- \*\*Role-Based Access Control:\*\* Role-based authorization for supported operations.

\- \*\*Institution Management:\*\* Institution-related APIs.

\- \*\*Equipment Management:\*\* Equipment records and CRUD operations.

\- \*\*Booking Management:\*\* Equipment booking requests, status management, and overlapping-booking checks.

\- \*\*Utilization Tracking:\*\* Records of equipment usage and utilization summaries.

\- \*\*Maintenance Management:\*\* Maintenance records, assignment, status updates, and maintenance summaries.



\## Project Structure



```text

lab-resource-platform/

├── backend/

│   └── lab-resource-platform/

│       ├── src/

│       ├── pom.xml

│       └── mvnw.cmd

├── frontend/

│   ├── src/

│   ├── public/

│   ├── package.json

│   └── vite.config.js

├── .gitignore

└── README.md

```



\## Prerequisites



Install the following software before running the application:



\- Java JDK compatible with the backend configuration

\- Maven, or use the included Maven Wrapper

\- Node.js and npm

\- PostgreSQL

\- Git



\## Backend Setup



1\. Create a PostgreSQL database named `labdb`.

2\. Configure the database connection and required environment variables.

3\. Open a terminal in the backend directory:



&#x20;  ```bash

&#x20;  cd backend/lab-resource-platform

&#x20;  ```



4\. Run the Spring Boot application on Windows:



&#x20;  ```cmd

&#x20;  mvnw.cmd spring-boot:run

&#x20;  ```



The configured backend port is `8080`.



\## Frontend Setup



1\. Open a separate terminal in the frontend directory:



&#x20;  ```bash

&#x20;  cd frontend

&#x20;  ```



2\. Install dependencies:



&#x20;  ```bash

&#x20;  npm install

&#x20;  ```



3\. Start the Vite development server:



&#x20;  ```bash

&#x20;  npm run dev

&#x20;  ```



The default frontend URL is:



`http://localhost:5173/`



\## Configuration and Security



Configure the database password and JWT signing secret using environment variables. Do not commit actual credentials, tokens, or private keys to the repository.



The backend configuration also includes a password-reset token exposure setting intended for controlled local development.



\## Current Development Status



The repository contains the backend and frontend project source code. Authentication, equipment management, booking workflows, utilization tracking, and maintenance management have been implemented at the application-code level.



Further work may include comprehensive automated testing, security hardening, email delivery for password resets, and additional validation and access-control checks.



\## Project Repository



\[GitHub Repository](https://github.com/springboardmentor903/Intelligent-Laboratory-Equipment-Sharing-and-Resource-Utilization-Optimization-Platform-Karthik)



\## Disclaimer



This project is under development as part of an academic and internship project. Features and setup instructions may change as development progresses.




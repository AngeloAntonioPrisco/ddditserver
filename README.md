# Dddit Server

<p align="center"><img src='https://i.postimg.cc/QxnvK4LL/dddit-upscaled.png' alt="Quixel_Texel_Logo" height="400"></p>

## 👋 Author

**Angelo Antonio Prisco** - [AngeloAntonioPrisco](https://github.com/AngeloAntonioPrisco)

**Pasquale Sorrentino** - [PasqualeSorrentino](https://github.com/PasqualeSorrentino)

At the moment, I am the main contributor to this project.  
I am a student at **University of Salerno (UNISA)**, currently enrolled in the Master's program in **Software Engineering**.

For the Software Dependability course **Pasquale Sorrentino** helped me to refactor the code to resolve several vulnerabilities and security issues and implement several test cases to ensure the project's stability and reliability. 

## 📌 What is it?

**Dddit Server** is a **Java Spring** project designed to provide *versioning services* for **3D resources**.  
Currently, it supports:

- **FBX models** versioning
- **Materials** versioning, understood as sets of PNG textures  

Main features include:
- Creating and managing **repositories**  
- Handling **resources**, **branches**, and **versions**  
- Each version includes **author**, **comment** and additional metadata  
- A **repository invitations system** to enable collaboration among multiple users on the same repo

## 🚀 How to try it

The server is intended to run with **Docker**, but it can also be executed locally using **IntelliJ IDEA**, **MinIO**, **JanusGraph** and **MongoDB**.

### Run locally
1. Clone the repo:
   ```bash
   git clone https://github.com/AngeloAntonioPrisco/ddditserver.git
   ```

2. Open the project in IntelliJ.

3. Install and run **MinIO**, **MongoDB** and **JanusGraph** locally.

4. Get an **app password** through a Google account to let your app access to Gmail services.

3. Edit the Run/Debug configuration to load environment variables from a custom *.env* file, like:
    ```bash
    MINIO_HOST=minio_host
    MINIO_PORT=minio_port
    MINIO_ROOT_USERNAME=minio_root_username
    MINIO_ROOT_PASSWORD=minio_root_password
    BLOB_STORAGE_BUCKET_MESHES=blob_storage_bucket_meshes
    BLOB_STORAGE_BUCKET_MATERIALS=blob_storage_bucket_materials
    
    MONGO_HOST=mongo_host
    MONGO_PORT=mongo_port
    MONGODB_ROOT_USERNAME=mongo_root_username
    MONGODB_ROOT_PASSWORD=mongo_root_password
    MONGODB_NAME=mongo_db_name
    MONGODB_COLLECTION_VERSIONS=mongo_db_collection_versions
    MONGODB_COLLECTION_TOKEN_BLACKLIST=mongo_db_collection_token_blacklist
    SPRING_DATA_MONGODB_URI=spring_data_mongodb_uri
    
    JANUS_HOST=janus_host
    JANUS_PORT=janus_port
    JANUS_USERNAME=janus_username
    JANUS_PASSWORD=janus_password
    
    MODELS_FOLDER_PATH=models_folder_path
    JWT_SECRET=jwt_secret
    FROM_EMAIL=from_email@example.com
    TO_EMAIL=to_email@example.com
    APP_PASSWORD=app_password
    ```

4. Start the application from IntelliJ.

### Run with Docker in IntelliJ IDEA (Suggested)

1. Install and run **Docker Desktop** on your machine.

2. Clone the repo:
   ```bash
   git clone https://github.com/AngeloAntonioPrisco/ddditserver.git
   ```

3. Open the project in IntelliJ. 

4. Install **Docker plugin** for IntelliJ IDEA.

5. Connect **Docker plugin** to **Docker Desktop**.

6. Create a custom *.env* file directly in the project root, `ddditserver > .env`, like:
     ```bash
     MINIO_HOST=minio_host
     MINIO_PORT=minio_port
     MINIO_ROOT_USERNAME=minio_root_username
     MINIO_ROOT_PASSWORD=minio_root_password
     BLOB_STORAGE_BUCKET_MESHES=blob_storage_bucket_meshes
     BLOB_STORAGE_BUCKET_MATERIALS=blob_storage_bucket_materials
     
     MONGO_HOST=mongo_host
     MONGO_PORT=mongo_port
     MONGODB_ROOT_USERNAME=mongo_root_username
     MONGODB_ROOT_PASSWORD=mongo_root_password
     MONGODB_NAME=mongo_db_name
     MONGODB_COLLECTION_VERSIONS=mongo_db_collection_versions
     MONGODB_COLLECTION_TOKEN_BLACKLIST=mongo_db_collection_token_blacklist
     
     JANUS_HOST=janus_host
     JANUS_PORT=janus_port
     JANUS_USERNAME=janus_username
     JANUS_PASSWORD=janus_password
     
     MODELS_FOLDER_PATH=models_folder_path
     JWT_SECRET=jwt_secret
     FROM_EMAIL=from_email@example.com
     TO_EMAIL=to_email@example.com
     APP_PASSWORD=app_password
     ```

7. Go to the files **jvm-8.options** and **jvm-11.options** in `ddditserver > janusgraph-conf` and in the lower right corner change from **CRLF** to **LF**.

8. If you are on Windows (on Mac and Linux this step has not been tested), you need to go in the folder `C:\Users\your-user` and create a file named **.wslconfig**, like:
     ```bash
    [wsl2]
    memory=4GB
    processors=4
    swap=4GB
     ```

9. Go to the file **docker-compose.yml** and run it.

## 🔎 Run Tests

For **JUnit** tests you can run them directly from IntelliJ IDEA or through `mvn clean test`.

For **JaCoCo** coverage you can compute it through `mvn clean verify` and then check the report in `ddditserver > target > site > jacoco > index.html`.

For **Pitest** mutation testing you can run it through `mvn pitest:mutationCoverage` and then check the report in `ddditserver > target > pit-reports > index.html`.

For **JMH** benchmarks you can run them through `java -cp target/benchmarks.jar org.openjdk.jmh.Main ".*Benchmark"` after a `mvn clean package`.

## 🧱 Built With

- [Java](https://www.oracle.com/java/) – Programming language used for the server implementation.
- [Spring Framework](https://spring.io/projects/spring-framework) – Provides dependency injection, REST APIs, and overall application structure.
- [JUnit](https://junit.org/junit5/) – Framework for unit testing Java code.
- [Mockito](https://site.mockito.org/) – Library for mocking objects in unit tests.
- [MongoDB](https://www.mongodb.com/) – NoSQL document database used for storing resources and versioned data.
- [JanusGraph](https://janusgraph.org/) – Distributed graph database used for modeling relationships between entities.
- [MinIO](https://min.io/) – High-performance object storage service used for storing 3D models and texture files.
- [JWT (JSON Web Tokens)](https://jwt.io/) – Used for authentication and securing API access.
- [Jakarta Mail](https://eclipse-ee4j.github.io/mail/) – Library used to send email notifications for AI module monitoring and alerts.
- [Eclipse Angus](https://projects.eclipse.org/projects/ee4j.angus) – Reference implementation of the Jakarta Mail and Activation APIs.
- [Snyk](https://snyk.io/) – Security platform used to detect and fix vulnerabilities in dependencies and container images.
- [SonarCloud](https://sonarcloud.io/) – Cloud-based service for continuous code quality inspection and static analysis.
- [Docker](https://www.docker.com/) – Containerization platform used to package and deploy the application and its services.
- [GitGuardian](https://www.gitguardian.com/) – Security tool used to detect secrets and sensitive information in source code repositories.
- [JMH](https://openjdk.org/projects/code-tools/jmh/) – Java Microbenchmark Harness used to measure the performance of Java code.
- [PIT (Pitest)](https://pitest.org/) – Mutation testing framework used to evaluate the effectiveness of unit tests.
- [JaCoCo](https://www.jacoco.org/jacoco/) – Code coverage library used to measure test coverage of Java applications.
- [JML (Java Modeling Language)](https://www.openjml.org/) – Behavioral interface specification language used to formally specify Java program behavior.


## 🔗 Related resources
- [Dddit Client](https://github.com/AngeloAntonioPrisco/ddditclient): The official Python client to interact with the Dddit Server APIs, useful for testing and consuming the server's functionalities.
- [Dddit AI](https://github.com/AngeloAntonioPrisco/ddditai): The official project to manage Dddit AI module loaded on Dddit Server

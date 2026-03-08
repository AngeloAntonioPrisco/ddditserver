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

## 🔬 Run JML Analysis

On a linux machine you can run the analysis following the steps below:

### Prerequisites
- Java JDK 21 (or compatible).
- Download OpenJML from [https://www.openjml.org](https://www.openjml.org).
- Unzip the archive and add the JML folder to the PATH.
- On Ubuntu do do not forget to install **libgomp1** using `sudo apt-get install libgomp1`.

### Installing Java

#### Ubuntu

On Ubuntu, you can install OpenJDK 21 using the following commands:

1. Update the package list:
    ```bash
    sudo apt update
    ```
2. Install OpenJDK:
    ```bash
    sudo apt install openjdk-21-jdk
    ```    

#### macOS

##### Option 1: Using Homebrew (Recommended)
1. Install Homebrew if you haven't already:
    ```bash
    /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
    ```

2. Install OpenJDK 21:
   ```bash
   brew install openjdk@21
   ```
3. Add Java to your PATH by adding this line to your shell configuration file (`~/.zshrc` or `~/.bash_profile`):
   ```bash
   export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"
   export JAVA_HOME="/opt/homebrew/opt/openjdk@21"
   ```
4. Reload your shell configuration:
   ```bash
   source ~/.zshrc  # or source ~/.bash_profile
   ```

##### Option 2: Manual Installation
1. Download OpenJDK 21 from [Adoptium](https://adoptium.net/temurin/releases/?version=21)
2. Install the `.pkg` file
3. The installer will automatically configure the PATH

### Verify Java Installation
After installation, verify that Java is correctly installed:

1. Check the version of Java:
    ```bash
    java -version
    ```
2. Check the version of javac:
    ```bash
    javac -version
    ```
   
You should see output indicating Java 21 (or your chosen version).

### Download OpenJML
Download the latest release of OpenJML from: [https://github.com/OpenJML/OpenJML/releases/tag/21-0.16](https://github.com/OpenJML/OpenJML/releases/tag/21-0.16)

### Adding OpenJML to PATH

#### Ubuntu
On Ubuntu you can add OpenJML to your PATH by adding the following line to your shell configuration file:

1. Open your terminal

2. Edit your shell configuration file:
    - For bash: `nano ~/.bashrc` or `nano ~/.bash_profile`
    - For zsh: `nano ~/.zshrc`

3. Add the following line at the end of the file:
   ```bash
   export PATH="$PATH:/path/to/openjml/folder"
   ```

4. Reload your shell configuration:
   ```bash
   source ~/.bashrc  # or ~/.zshrc for zsh users
   ```

#### macOS
For macOS, you can add OpenJML to your PATH by adding the following line to your shell configuration file:

1. Open Terminal

2. Edit your shell configuration file:
    - For bash: `nano ~/.bash_profile`
    - For zsh (default on macOS Catalina+): `nano ~/.zshrc`

3. Add the following line at the end of the file:
   ```bash
   export PATH="$PATH:/path/to/openjml/folder"
   ```

4. Reload your shell configuration:
   ```bash
   source ~/.zshrc  # or ~/.bash_profile for bash users
   ```

### Verify installation
After adding OpenJML to your PATH, verify that it is correctly installed by running the following commands:

1. Check the version of Java:
    ```bash
        java -version
    ```

2. Check the version of OpenJML:
    ```bash
        openjml -version
    ```
### Running the Analysis

1. Clone the repo:
   ```bash
   git clone https://github.com/AngeloAntonioPrisco/ddditserver.git
   ```
   
2. In the folder containing the project, run:
   ```bash
   nano jml-check.sh
   ```
   
3. In the file write: 
    ```bash
    #!/bin/bash
    set -e
    
    PROJECT_DIR=~/ddditserver
    
    echo "================================================"
    echo " OpenJML Analysis Script"
    echo "================================================"
    
    cd "$PROJECT_DIR"
    
    # --- Variables ---
    LOMBOK_JAR=$(find ~/.m2 -name "lombok-*.jar" | grep -v sources | sort -V | tail -1)
    SPRING_JAR=$(find ~/.m2 -name "spring-context-*.jar" | grep -v sources | tail -1)
    SPRING_BEANS_JAR=$(find ~/.m2 -name "spring-beans-*.jar" | grep -v sources | tail -1)
    SPRING_WEB_JAR=$(find ~/.m2 -name "spring-web-*.jar" | grep -v sources | tail -1)
    SPRING_CORE_JAR=$(find ~/.m2 -name "spring-core-*.jar" | grep -v sources | tail -1)
    COMMONS_LANG_JAR=$(find ~/.m2 -name "commons-lang3-*.jar" | grep -v sources | tail -1)
    CLASSPATH_FILE=/tmp/cp.txt
    SOURCES_FILE=/tmp/sources.txt
    DELOMBOK_DIR="$PROJECT_DIR/delombok-src"
    THREADS=$(nproc)  # Number of available CPU cores
    
    echo "[1/5] Building project..."
    mvn clean install -DskipTests -q
    echo "      OK"
    
    echo "[2/5] Delombok (entire src/main/java)..."
    rm -rf "$DELOMBOK_DIR"
    mkdir -p "$DELOMBOK_DIR"
    
    # Generate classpath once and reuse it
    CLASSPATH=$(mvn dependency:build-classpath -q -DforceStdout)
    
    # Delombok the entire source tree so all dependencies are resolved correctly
    java -jar "$LOMBOK_JAR" delombok src/main/java \
      -d "$DELOMBOK_DIR" \
      --classpath "$CLASSPATH"
    
    echo "      OK"
    
    echo "[3/5] Generating classpath..."
    echo "$CLASSPATH" > "$CLASSPATH_FILE"
    echo "      OK"
    
    echo "[4/5] Searching for files with JML annotations..."
    # Only pass to OpenJML the files that actually contain JML specifications
    grep -rl "/\*@" "$DELOMBOK_DIR" --include="*.java" > "$SOURCES_FILE"
    COUNT=$(wc -l < "$SOURCES_FILE")
    echo "      Found $COUNT files with JML specifications:"
    cat "$SOURCES_FILE" | sed 's/^/        /'
    
    echo "[5/5] Running OpenJML (parallel on $THREADS threads)..."
    echo "------------------------------------------------"
    
    # Split source file list into chunks, one per thread
    split -n "l/$THREADS" "$SOURCES_FILE" /tmp/jml_chunk_
    
    # Launch one OpenJML process per chunk in background
    PIDS=()
    for chunk in /tmp/jml_chunk_*; do
      # Skip empty chunks that split may produce
      if [ -s "$chunk" ]; then
        openjml -esc -nowarn \
          -classpath "$(cat $CLASSPATH_FILE):$DELOMBOK_DIR:$PROJECT_DIR/target/classes:$LOMBOK_JAR:$SPRING_JAR:$SPRING_BEANS_JAR:$SPRING_WEB_JAR:$SPRING_CORE_JAR:$COMMONS_LANG_JAR" \
          @"$chunk" &
        PIDS+=($!)
      fi
    done
    
    # Wait for all background processes and collect exit codes
    FAILED=0
    for pid in "${PIDS[@]}"; do
      wait "$pid" || FAILED=1
    done
    
    # Clean up temporary chunk files
    rm -f /tmp/jml_chunk_*
    
    echo "------------------------------------------------"
    if [ $FAILED -ne 0 ]; then
      echo " Analysis completed with verification errors."
    else
      echo " Analysis completed successfully."
    fi
    echo "================================================"
    ```

4. Update the permissions of the file.
    ```bash
   chmod +x jml-check.sh
   ```

5. Then run the file :

   ```bash
    ./jml-check.sh
   ```

*Note*: Since the entire project is built with **Spring** and **Lombok**, **OpenJML** has limited visibility into dependency injection and generated code. As a result, the analysis may take several minutes and will report a number of warnings (e.g. `NullField`, `ArithmeticOperationRange`, `CharSequence invariant violations`). These are known limitations of **OpenJML** when used with **Spring** and **Lombok**, and do not reflect actual specification errors.   


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

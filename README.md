# PlayStation 5 Remote JAR Loader
This project uses vulnerabilities discovered in BD-J layer of PS5 firmware version 7.61 and earlier to deploy a loader that is able to listen to JAR files and execute their main class.
This makes it easy to burn the BD-R disc with the loader just once and then keep on running new versions of the experimental code.
This repository provides all the necessary setup needed to create both the loader BD-R disc filesystem and the JAR to send to the PS5.

## Quickstart
1. Download the JAR Loader ISO release.
2. Burn it to a BD-R(E) disc and run it from the PS5 "Media" tab.
3. Download one of the pre-compiled JARs or compile your own by reading the steps below.
4. Send the JAR to the JAR Loader using NetCat, or using the JAR file itself, if the machine has Java installed: `java -jar [jarfile].jar [ip] [host]`.

## Prerequisites
* JDK 11 (PS5 uses Java 11 runtime)
* Apache Maven
* IntelliJ IDEA Community Edition (optional, but recommended)

## Structure
The project comprises the following components:
* Root `pom.xml` defines the common properties and Maven plugin configuration for all the projects.
* `assembly` subproject creates the directory that should be burned to a BD-R disc. I recommend `ImgBurn` software to do this. Make sure to use the UDF 2.50 filesystem, then simply drag the contents of `assembly/target/assembly-[version]` directory to in the disc layout editor.
* `bdj-tools` subproject does not need to be touched. These are the utilities from HD Cookbook, adapted to run on JDK 11 and integrated into the build process of the BD-R disc filesystem.
* `stubs` subproject contains the build script to download BD-J class files from HD Cookbook and organize them for use with local JDK 11. It's also a place where PS5-specific stub files should be declared so that they can be used in the Xlet and the remote JAR.
* `sdk` subproject contains helper classes that simplify native invocation in the executed code. The classes in this module are embedded in the final JAR that will be sent to PS5 for execution.
* `xlet` subproject contains the code of the Xlet that starts when BD-R disc is launched on PS5. It simply starts the JAR loader (by default on port 9025).
* `xploit` subproject contains the code to be sent for execution on PS5. The code can reference classes from `xlet`, such as the [Status](xlet/src/main/java/org/ps5jb/loader/Status.java) class to output on screen. The project produces a JAR that is able to send itself for execution.

## Configuration
The following properties in [xlet/pom.xml](xlet/pom.xml) can be adjusted before compiling and burning the JAR Loader to disk:
* `loader.port` - Port on which JAR loader will listen for data.
* `loader.resolution.width`, `loader.resolution.height` - Screen resolution to set in various files. Not sure how this affects anything, I did not experiment with this enough.
* `loader.logger.host` - IP address where to echo the messages shown on screen. If blank, remote logging will not be used. This host can also receive binary data, see [RemoteLogger#sendBytes](xlet/src/main/java/org/ps5jb/loader/RemoteLogger.java).
* `loader.logger.port` - Port on which remote logger will send the status messages.
* `loader.logger.timeout` - Number of milliseconds to wait before abandoning attempts to connect to the remote logging host. If host is down after this timeout on the first send attempt, no further tries to do remote logging will be done.

Either modify the POM directly, or pass the new values from command line, example: `mvn clean package -Dloader.port=9025 -loader.logger.host=192.168.1.100`. To listen for messages on the remote machine when remote logger is activated, use `socat udp-recv:[remote.logger.port] stdout`.

Even if the remote logger is not active by default in the Xlet burned on disc, it is possible to change the remote server configuration using one of the two approaches:
1. Specify `xploit.logger.host` and optionally `xploit.logger.port` properties when compiling the JAR. These can be set in [xploit/pom.xml](xploit/pom.xml) or on command line `mvn clean package -Dxploit.logger.host=192.168.1.110`.
2. Programmatically in the JAR payload by calling [Status#resetLogger](xlet/src/main/java/org/ps5jb/loader/Status.java).

## Usage
1. Make sure environment variable `JAVA_HOME` points to the root of JDK 11. Add `${JAVA_HOME}/bin` directory to `${PATH}`.
2. Also make sure that `MAVEN_HOME` points to the root of Apache Maven installation. Add `${MAVEN_HOME}/bin` directory to `${PATH}`.
3. Create a payload to execute on PS5 by adding the implementation to the `xploit` submodule. There is no need to modify any existing files (though you are welcome if you want). Simply add your payload class in [org.ps5jb.client.payloads](xploit/src/main/java/org/ps5jb/client/payloads) package and specify its name as a parameter when compiling the project (see the next step). A few sample payloads are provided in this package already.
4. Execute `mvn clean package -Dxploit.payload=[payload classname]` from the root of the project. It should produce the following artifacts:
    a. Directory `assembly/target/assembly-[version]` contains all the files that should be burned to a BD-R disc.
    b. File `xploit/target/xploit-[version].jar` contains the code that can be sent repeatedly to the PS5 once the loader is deployed.
    To avoid having to specify the payload every time with a `-D` switch (in step 9 as well), you can also change the property `xploit.payload` in [pom.xml](xploit/pom.xml) of the [xploit](xploit) project.
5. Burn the BD-R (better yet BD-RE) with the contents from the directory mentioned in the step 4a. Note that re-burning the JAR loader disc is only necessary when the source of [xlet](xlet) or [assembly](assembly) modules is changed.
6. Insert the disc into the PS5 and launch "PS5 JAR Loader" from Media / Disc Player.
7. A message on screen should inform about loader waiting for JAR.
8. Send the JAR using the command:
    ```shell
    java -jar xploit/target/xploit-[version].jar <ps5 ip address>`
    ```
    PS5 should inform on screen about the status of the upload and the execution.
9. Once execution is complete, the loader will wait for a new JAR. Do the necessary modifications in `xploit` project, recompile using `mvn package` and re-execute step 8 to retry as many times as necessary.

## Notes
1. To use with IntelliJ, point `File -> Open` dialog to the root of the project. Maven import will occur. Then follow manual steps in [IntelliJ Project Structure](#intellij-project-structure) to adjust the dependencies so that IntelliJ sees BD-J classes ahead of JDK classes.
2. If any of POMs are modified, it's necessary to do `Maven -> Reload Project` in IntelliJ to sync the project files.
3. To generate Javadocs, use `mvn verify` rather than `mvn package`. The Javadocs are enabled for [sdk](sdk), [xlet](xlet) and [xploit](xploit) modules and are generated in the `target/site/apidocs` directory of each module.
4. The JAR in the `xploit` module accesses some internal JDK classes by reflection. This will result in warnings which can be safely ignored. To mute the warnings, add the following switch after `java` executable when sending the JAR: `--add-opens java.base/jdk.internal.loader=ALL-UNNAMED`.
5. If the `xploit` JAR does not have PS5 specific dependencies, it can be tested locally. The important part is to have `xlet`, `stubs` and `xploit` JARs all in the same folder. If the payload refers to GEM, BD-J or Java TV API, the corresponding JAR files generated in [lib](lib) directory should also be present in the same folder. Maven build automatically creates this arrangement in `xploit/target` directory so the command to run the payload on development machine is very similar to the one that sends the JAR to PS5:
    ```shell
    java -jar xploit/target/xploit-[version].jar
    ```
    When running locally, the `Status` class prints to standard output/error, rather than on `Screen`.
6. There are currently two separate version numbers in use by the project:
    * The `xlet` version is independent and will only be incremented when new disc needs to be burned with the updated JAR loader classes. If the PS5 shows a version different from the one produced by the code of this repo, payloads are not guaranteed to be compatible, so it's best to burn a new loader disc. This version is not expected to be incremented often as the loader is pretty stable. To increment this version, change the value of `xlet.version` property in [pom.xml](pom.xml).
    * The rest of the modules use the version from the parent POM. This version will be incremented with the new release and reflects that either the SDK or the payloads have changed. If the loader version remained the same, these new versions of payloads can still be sent to the JAR loader without re-burning the disc. This version can be incremented by executing `mvn versions:set -DnewVersion=[version]`, then refreshing the IntelliJ Maven project as described in bullet point number 2.

## IntelliJ Project Structure
IntelliJ Maven project files are located in a private local folder of IntelliJ. Initial opening and the following reloads of the Maven project incorrectly import some of the settings. In particular, BD-J stack JARs are completely ignored or are imported with a wrong scope. Unfortunately, due to this fact, the following steps need to be performed every time a Maven project reload occurs:
* Syncing Maven project modifies [.idea/compiler.xml](.idea/compiler.xml) to contain absolute system paths. Simply replace those with `$PROJECT_DIR$` macro again.
* Go to `Project Structure` window and switch to `Modules` tab. Go through every module and make sure that the modules `bdj-api`, `javatv-api` and `gem-api` have "Provided" scope.
* In addition, for all the modules that have the above-mentioned dependencies, click on `+ (Add) -> Library` button and add `bdjstack` library dependency. Make sure it is moved in the top position above SDK 11 entry. This setting used to be commited to version control and could be simply reverted, but in recent updates, it has to be performed every time. 

## Credits
There are so many who decided to share the knowledge with the community to make this project possible.
- [Andy "theflow" Nguyen](https://github.com/theofficialflow) for discovering and sharing BD-J vulnerabilities without which none of the work in this repo would be possible.
- Specter for his Webkit implementations of PS5 kernel access which served as a base for Java implementation: [IPV6](https://github.com/Cryptogenic/PS5-IPV6-Kernel-Exploit) and [UMTX](https://github.com/PS5Dev/PS5-UMTX-Jailbreak/).
- [Flat_z](https://github.com/flatz) for pretty much everything of significance that happened in PlayStation scene since as far back as PS3, including the UMTX exploitation strategy contained in this repo.
- [Cheburek3000](https://github.com/cheburek3000) for contributing an alternative implementation of UMTX exploitation. 
- [bigboss](https://github.com/psxdev) and [John Törnblom](https://github.com/john-tornblom) for their work specifically in BD-J area.
- All the other contributors to Specter's Webkit implementations: [ChendoChap](https://github.com/ChendoChap), [Znullptr](https://twitter.com/Znullptr), [sleirsgoevy](https://twitter.com/sleirsgoevy), [zecoxao](https://twitter.com/notnotzecoxao), [SocracticBliss](https://twitter.com/SocraticBliss), SlidyBat, [idlesauce](https://github.com/idlesauce).

Sample BD-J payloads in this repositories are adaptations of the following work:
- FTP server by [pReya](https://github.com/pReya/ftpServer). 
- Mini Tennis by [Edu4Java](http://www.edu4java.com/en/game/game0-en.html).

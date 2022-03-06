/*
 * Maintained by brightSPARK Labs.
 * www.brightsparklabs.com
 *
 * Refer to LICENSE at repository root for license details.
 */

package com.brightsparklabs.gradle.docker

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option

/**
 * The custom Gradle task for building Docker images. Allows instances to
 * specify command line arguments when executing the task.
 */
class BuildDockerImagesTask extends DefaultTask {

    // ------------------------------------------------------------------------
    // INSTANCE VARIABLES
    // ------------------------------------------------------------------------

    /** the plugin configuration */
    private DockerImagePluginExtension config

    /** an optional Docker image name to filter by */
    private String imageName

    @Internal
    DockerImagePluginExtension getConfig() {
        return this.config
    }

    void setConfig(final DockerImagePluginExtension config) {
        this.config = config
    }

    @Input
    @Optional
    String getImageName() {
        return this.imageName
    }

    @Option(option = "imageName", description = "Builds only the specific image")
    void setImageName(final String imageName) {
        this.imageName = imageName
    }

    // -------------------------------------------------------------------------
    // IMPLEMENTATION: DefaultTask
    // -------------------------------------------------------------------------

    @TaskAction
    void buildDockerImages() {
        project.delete config.imageTagDir
        config.imageTagDir.mkdirs()

        def repoGitTag = getRepositoryGitTag()
        def dockerFileDefinitions = config.dockerFileDefinitions

        // Filter building by Docker image name if specified
        if (imageName?.trim()) {
            logger.info("Filtering Dockerfile definitions with image name [${imageName}]")
            dockerFileDefinitions = dockerFileDefinitions.findAll { it.name == imageName }
        }

        if (dockerFileDefinitions.size() == 0) {
            throw new GradleException("No Dockerfile definitions configured for name [${imageName}]")
        }

        def requireLogin = config.privateDockerUsername?.trim() &&
                config.privateDockerPassword?.trim() &&
                config.privateDockerServer?.trim()

        // Perform Docker login if Docker username and password specified
        if (requireLogin) {
            logger.info("Logging into private docker server [${config.privateDockerServer}] " +
                    "as [${config.privateDockerUsername}]")
            def output = dockerLogin(
                    config.privateDockerUsername,
                    config.privateDockerPassword,
                    config.privateDockerServer)
            logger.info(output)
        }

        dockerFileDefinitions.each { definition ->
            // Add default tags for 'latest' and repo git tag
            def imageName = definition.name ?: definition.repository
            def latestTag = "${imageName}:latest"
            def gitTag = "${imageName}:${repoGitTag}"
            def command = [
                'docker',
                'build',
                '-t',
                latestTag,
                '-t',
                gitTag
            ]

            // Add tag based on git commit of the folder containing dockerfile
            File parentFolder = definition.dockerfile.parentFile
            def folderCommit = getLastCommitHash(parentFolder)
            if (folderCommit.isEmpty()) {
                folderCommit = 'UNKNOWN-COMMIT'
            }
            def folderTag = "${imageName}:${folderCommit}"
            command << '-t'
            command << folderTag

            // Add any custom tags defined in code
            def customTags = definition.tags ?: []
            if (!customTags.isEmpty()) {
                def tags = customTags.collect { "${imageName}:${it}" }
                tags = tags.join(',-t,').split(',')
                command << '-t'
                command.addAll(tags)
            }

            // Add version tag from the version property in the build script
            command << '-t'
            command << "${imageName}:${project.version}"

            // Add timestamp tag so we can differentiate builds easily
            def timestamp = new Date().toInstant().toString()
            command << '-t'
            command << "${imageName}:${timestamp.replace(':', '')}"

            // add build-args which can be referenced within Dockerfile
            command << '--build-arg'
            command << "BUILD_DATE=${timestamp}"
            command << '--build-arg'
            command << "VCS_REF=${folderCommit}"
            command << '--build-arg'
            command << "APP_VERSION=${project.version}"

            // specific file
            command << '-f'
            command << definition.dockerfile

            // folder to build
            File contextDir = definition.contextDir ?: parentFolder
            command << contextDir

            // multi-stage target
            if (definition.target) {
                command << '--target'
                command << definition.target
            }

            // custom build arguments
            if (definition.buildArgs) {
                command.addAll(definition.buildArgs)
            }

            def oldLevel = logging.standardOutputCaptureLevel
            logging.captureStandardOutput LogLevel.INFO
            logger.lifecycle("Building image [${imageName}] from [${definition.dockerfile}] ...")

            def buildResult = project.exec {
                commandLine command
                // do not prevent other docker builds if one fails
                ignoreExitValue true
            }
            logging.captureStandardOutput oldLevel

            if (buildResult.getExitValue() == 0) {
                // store image tag
                def friendlyImageName = imageName.replaceAll('/', '-')
                def imageTagFile = new File(config.imageTagDir, "VERSION.DOCKER-IMAGE.${friendlyImageName}")
                imageTagFile.text = repoGitTag

                // Delete older images if configured
                if (config.deleteOlderImages) {
                    def output = removeImagesOlderThan(imageName)
                    logger.info("Deleted old [${imageName}] images:\n${output}")
                }
            } else {
                def error = "Could not build docker file [${definition.dockerfile}]"
                if (config.continueOnFailure) {
                    project.ext.failures << error
                } else {
                    throw new GradleException(error)
                }
            }
        }

        if (config.removeDanglingImages) {
            def output = removeDanglingImages()
            logger.info("Removed dangling images:\n${output}")
        }

        // Perform Docker logout if Docker username and password specified
        if (requireLogin) {
            logger.info("Logging out of private docker server [${config.privateDockerServer}]")
            def output = dockerLogout(config.privateDockerServer)
            logger.info(output)
        }
    }

    // -------------------------------------------------------------------------
    // PRIVATE METHODS
    // -------------------------------------------------------------------------

    /**
     * Returns the git tag of the repository.
     *
     * @return the git tag of the repository or '0.0.0-UNKNOWN' if no tags
     *         exist
     */
    @Internal
    def getRepositoryGitTag() {
        def tag = shell("git describe --dirty")
        if (tag.isEmpty()) {
            tag = '0.0.0-UNKNOWN'
        }
        return tag
    }

    /**
     * Returns the last git commit hash of the specified file/folder.
     *
     * @return the last git commit hash of the specified file/folder
     */
    def getLastCommitHash(File file) {
        def tag = shell("git log -n 1 --pretty=format:%h -- ${file}")
        return tag
    }

    /**
     * Executes a shell command and returns the stdout result.
     *
     * @param command
     *          the command to execute (cannot contain pipes)
     *
     * @return the trimmed result from stdout
     */
    def shell(String command) {
        return command.execute().text.trim()
    }

    /**
     * Removes docker images older than the latest image tag
     *
     * @param imageName
     *          the name of the Docker image
     *
     * @return the trimmed result from stdout
     */
    def removeImagesOlderThan(imageName) {
        def command = "docker images -f before=${imageName}:latest -f reference=${imageName} -q"
        def imageIds = shell(command).split('\n').join(' ')
        return shell("docker rmi -f ${imageIds}")
    }

    /**
     * Removes dangling images
     *
     * @return the trimmed result from stdout
     */
    def removeDanglingImages() {
        def command = "docker images -f dangling=true -q"
        def danglingImageIds = shell(command).split('\n').join(' ')
        return shell("docker rmi -f ${danglingImageIds}")
    }

    /**
     * Logs into the private Docker server
     *
     * @param username
     *          the Docker username
     * @param password
     *          the Docker password
     * @param server
     *          the private Docker server
     * @return the trimmed result from stdout
     */
    def dockerLogin(String username, String password, String server) {
        def process = "docker login -u ${username} -p ${password} ${server}".execute()
        process.waitFor()
        if (process.exitValue() != 0) {
            throw new GradleException("Docker login to [${server}] as [${username}] failed - " +
            "${process.err.text}")
        }
        return process.text.trim()
    }

    /**
     * Logs out of the private Docker server
     *
     * @param server
     *          the private Docker server
     * @return the trimmed result from stdout
     */
    def dockerLogout(String server) {
        return shell("docker logout ${server}")
    }
}

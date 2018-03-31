/*
 * Created by brightSPARK Labs
 * www.brightsparklabs.com
 */

package com.brightsparklabs.gradle.docker

import org.gradle.api.GradleException
import org.gradle.api.logging.LogLevel
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Simplifies managing Docker images.
 */
class DockerImagePlugin implements Plugin<Project> {

    // ------------------------------------------------------------------------
    // INSTANCE VARIABLES
    // ------------------------------------------------------------------------

    /** any failures which occurred with running docker commands */
    def failures = []

    // -------------------------------------------------------------------------
    // IMPLEMENTATION: Plugin<Project>
    // -------------------------------------------------------------------------

    void apply(Project project) {
        // Create plugin configuration object.
        def config = project.extensions.create('dockerImagePluginConfig', DockerImagePluginExtension)
        // set default values for configuration based on project
        config.imagesDir = config.imagesDir ?: new File(project.buildDir, 'images')
        config.imageTagDir = config.imageTagDir ?: new File(project.buildDir, 'imageTags')

        // Add all tasks once project configuration has been read.
        // This allows us to use the plugin extension block in task
        // configuration.
        project.afterEvaluate {
            addBuildDockerImageTask(project, config)
            addSaveDockerImageTask(project, config)
            addPublishDockerImageTask(project, config)
        }

        project.gradle.buildFinished() {
            if (!failures.isEmpty()) {
                throw new GradleException("Failed to build the following Dockerfiles:\n- " + failures.join('\n- '))
            }
        }
    }

    // -------------------------------------------------------------------------
    // METHODS
    // -------------------------------------------------------------------------

    /**
     * Add a 'buildDockerImages' to the supplied project.
     *
     * @param project
     *          project to add the task to.
     *
     * @param config
     *          plugin configuration block.
     */
    def addBuildDockerImageTask(Project project, DockerImagePluginExtension config) {
        project.task('buildDockerImages') {
            group = "brightSPARK Labs - Docker"
            description = "Builds docker images from Dockerfiles"

            inputs.files config.dockerFileDefinitions.collect { it.dockerfile.parentFile }
            outputs.dir config.imageTagDir

            doLast {
                project.delete config.imageTagDir
                config.imageTagDir.mkdirs()

                def repoGitTag = getRepositoryGitTag()
                config.dockerFileDefinitions.each { definition ->
                    // Add default tags for 'latest' and repo git tag
                    def latestTag = "${definition.repository}:latest"
                    def gitTag = "${definition.repository}:${repoGitTag}"
                    def command = ['docker', 'build', '-t', latestTag, '-t', gitTag]

                    // Add tag based on git commit of the folder containing dockerfile
                    File folder = definition.dockerfile.parentFile
                    def folderTag = getLastCommitHash(folder)
                    if (! folderTag.isEmpty()) {
                        folderTag = "${definition.repository}:g${folderTag}"
                        command << '-t'
                        command << folderTag
                    }

                    // Add any custom tags defined in code
                    def customTags = definition.tags ?: []
                    if (! customTags.isEmpty()) {
                        def tags = customTags.collect { "${definition.repository}:${it}" }
                        tags = tags.join(',-t,').split(',')
                        command << '-t'
                        command.addAll(tags)
                    }
                    command << '.'

                    def oldLevel = logging.standardOutputCaptureLevel
                    logging.captureStandardOutput LogLevel.INFO
                    logger.lifecycle("Building image [${definition.repository}] from [${definition.dockerfile}] ...")

                    def buildResult = project.exec {
                        commandLine command
                        workingDir definition.dockerfile.parentFile
                        // do not prevent other docker builds if one fails
                        ignoreExitValue true
                    }
                    logging.captureStandardOutput oldLevel

                    if (buildResult.getExitValue() == 0) {
                        // store image tag
                        def friendlyImageName = definition.repository.replaceAll('/', '-')
                        def imageTagFile = new File(config.imageTagDir, "VERSION.DOCKER-IMAGE.${friendlyImageName}")
                        imageTagFile.text = repoGitTag
                    }
                    else {
                        def error = "Could not build docker file [${definition.dockerfile}]"
                        if (config.continueOnFailure) {
                            failures << error
                        }
                        else {
                            throw new GradleException(error)
                        }
                    }
                }
            }
        }
    }

    /**
     * Add a 'saveDockerImages' to the supplied project.
     *
     * @param project
     *          project to add the task to.
     *
     * @param config
     *          plugin configuration block.
     */
    def addSaveDockerImageTask(Project project, DockerImagePluginExtension config) {
        project.task('saveDockerImages') {
            group = "brightSPARK Labs - Docker"
            description = "Saves docker images to TAR files"
            dependsOn project.buildDockerImages

            inputs.files config.dockerFileDefinitions.collect { it.dockerfile.parentFile }
            outputs.dir config.imagesDir

            doLast {
                project.delete config.imagesDir
                config.imagesDir.mkdirs()

                def imageVersion = getRepositoryGitTag()
                config.dockerFileDefinitions.each { definition ->
                    def imageTag = "${definition.repository}:${imageVersion}"
                    def friendlyImageName = definition.repository.replaceAll('/', '-')
                    def imageFilename = "docker-image-${friendlyImageName}-${imageVersion}.tar"
                    def imageFile = new File(config.imagesDir, imageFilename)

                    logger.lifecycle("Saving image [${definition.repository}] ...")
                    def buildResult = project.exec {
                        commandLine 'docker', 'save', imageTag
                        standardOutput = new FileOutputStream(imageFile)
                        workingDir config.imagesDir
                        // do not prevent other docker builds if one fails
                        ignoreExitValue true
                    }
                    if (buildResult.getExitValue() != 0) {
                        def error = "Could not save docker image [${imageTag}]"
                        if (config.continueOnFailure) {
                            failures << error
                        }
                        else {
                            throw new GradleException(error)
                        }
                    }
                }
            }
        }
    }

    /**
     * Add a 'publishDockerImages' to the supplied project.
     *
     * @param project
     *          project to add the task to.
     *
     * @param config
     *          plugin configuration block.
     */
    def addPublishDockerImageTask(Project project, DockerImagePluginExtension config) {
        project.task('publishDockerImages') {
            group = "brightSPARK Labs - Docker"
            description = "Publishes docker images to the docker registry. Login session must already be established via `docker login`."
            dependsOn project.buildDockerImages

            doLast {
                config.dockerFileDefinitions.each { definition ->
                    logger.lifecycle("Publishing image [${definition.repository}] ...")
                    def buildResult = project.exec {
                        commandLine 'docker', 'push', definition.repository
                        // do not prevent other docker builds if one fails
                        ignoreExitValue true
                    }
                    if (buildResult.getExitValue() != 0) {
                        def error = "Could not push docker image [${definition.repository}]"
                        if (config.continueOnFailure) {
                            failures << error
                        }
                        else {
                            throw new GradleException(error)
                        }
                    }
                }
            }
        }
    }

    /**
     * Returns the git tag of the repository.
     *
     * @return the git tag of the repository or '0.0.0-UNKNOWN' if no tags
     *         exist
     */
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
}


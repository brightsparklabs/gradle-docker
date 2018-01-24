/*
 * Created by brightSPARK Labs
 * www.brightsparklabs.com
 */

package com.brightsparklabs.gradle.docker

import java.util.logging.Logger
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Simplifies managing Docker images.
 */
class DockerImagePlugin implements Plugin<Project> {

    // -------------------------------------------------------------------------
    // IMPLEMENTATION: Plugin<Project>
    // -------------------------------------------------------------------------

    void apply(Project project) {
        // Create plugin configuration object.
        def config = project.extensions.create('dockerImagePluginConfig', DockerImagePluginExtension)
        // set default values for configuration based on project
        config.imageTagDir = config.imageTagDir ?: new File(project.buildDir, 'imageTags')

        // Add all tasks once project configuration has been read.
        // This allows us to use the plugin extension block in task
        // configuration.
        project.afterEvaluate {
            addBuildDockerImageTask(project, config)
            addSaveDockerImageTask(project, config)
            addPublishDockerImageTask(project, config)
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

            // always run task as any source files may have changed
            outputs.upToDateWhen { false }

            doLast {
                config.imageTagDir.mkdirs()

                config.dockerFileDefinitions.each { definition ->
                    // Set the default tag as "latest"
                    def imageTag = "${definition.repository}:latest"
                    def command = ['docker', 'build', '-t', imageTag]

                    // Add tag based on git tag of the folder in the repository (if it exists)
                    def imageVersion = getRepositoryGitTag(definition.dockerfile.getParent())
                    if (! imageVersion.isEmpty()) {
                        def gitTag = "${definition.repository}:g${imageVersion}"
                        command << ',-t'
                        command << gitTag
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

                    project.exec {
                       commandLine command
                       workingDir definition.dockerfile.getParentFile()
                    }

                    // store image tag
                    def friendlyImageName = definition.repository.replaceAll('/', '-')
                    def imageTagFile = new File(config.imageTagDir, "VERSION.DOCKER-IMAGE.${friendlyImageName}")
                    imageTagFile.text = imageTag
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

            inputs.files config.dockerFileDefinitions.collect { it.dockerfile }
            def imagesDir = new File(project.buildDir, '/images')
            outputs.dir imagesDir

            doLast {
                imagesDir.mkdirs()

                config.dockerFileDefinitions.each { definition ->
                    def imageVersion = getRepositoryGitTag(definition.dockerfile)
                    def imageTag = "${definition.repository}:g${imageVersion}"
                    def friendlyImageName = definition.repository.replaceAll('/', '-')
                    def imageFilename = "docker-image-${friendlyImageName}-g${imageVersion}.tar"
                    def imageFile = new File(imagesDir, imageFilename)

                    project.exec {
                        commandLine 'docker', 'save', imageTag
                        standardOutput = new FileOutputStream(imageFile)
                        workingDir imagesDir
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
                    project.exec {
                        commandLine 'docker', 'push', definition.repository
                    }
                }
            }
        }
    }

    /**
     * Returns the git tag of the repository.
     *
     * @return the git tag of the repository
     */
    def getRepositoryGitTag(File dockerFile) {
        def dockerFilename = dockerFile.getName()
        def workingDir = dockerFile.getParentFile()
        def tag = shell "git describe --dirty", workingDir
        return tag
    }

    /**
     * Executes a shell command and returns the stdout result.
     * Working directory is always the projectDir.
     *
     * @param command
     *          the command to execute (cannot contain pipes)
     *
     * @return the result from stdout
     */
    def shell(String command, File workingDir) {
        return command.execute(null, workingDir).text.trim()
    }
}

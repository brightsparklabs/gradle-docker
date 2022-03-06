/*
 * Maintained by brightSPARK Labs.
 * www.brightsparklabs.com
 *
 * Refer to LICENSE at repository root for license details.
 */

package com.brightsparklabs.gradle.docker

import org.gradle.api.GradleException
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
        /** any failures which occurred with running docker commands */
        project.ext.failures = []

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
            if (!project.ext.failures.isEmpty()) {
                throw new GradleException("Failed to build the following Dockerfiles:\n- " + project.ext.failures.join('\n- '))
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
    def addBuildDockerImageTask(Project project, DockerImagePluginExtension pluginConf) {
        project.task('buildDockerImages', type: BuildDockerImagesTask) {
            group = "brightSPARK Labs - Docker"
            description = "Builds docker images from Dockerfiles"

            inputs.files pluginConf.dockerFileDefinitions.collect { it.dockerfile.parentFile }
            outputs.dir pluginConf.imageTagDir

            config = pluginConf
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

                def imageVersion = project.version
                config.dockerFileDefinitions.each { definition ->
                    def imageName = definition.name ?: definition.repository
                    def imageTag = "${imageName}:${imageVersion}"
                    def friendlyImageName = imageName.replaceAll('/', '-')
                    def imageFilename = "docker-image-${friendlyImageName}-${imageVersion}.tar"
                    def imageFile = new File(config.imagesDir, imageFilename)

                    logger.lifecycle("Saving image [${imageName}] ...")
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
                            project.ext.failures << error
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
                    def imageName = definition.name ?: definition.repository
                    logger.lifecycle("Publishing image [${imageName}] ...")
                    def buildResult = project.exec {
                        commandLine 'docker', 'push', imageName
                        // do not prevent other docker builds if one fails
                        ignoreExitValue true
                    }
                    if (buildResult.getExitValue() != 0) {
                        def error = "Could not push docker image [${imageName}]"
                        if (config.continueOnFailure) {
                            project.ext.failures << error
                        }
                        else {
                            throw new GradleException(error)
                        }
                    }
                }
            }
        }
    }
}


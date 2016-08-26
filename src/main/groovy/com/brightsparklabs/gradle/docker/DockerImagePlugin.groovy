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

        // Add all tasks once project configuration has been read.
        // This allows us to use the plugin extension block in task
        // configuration.
        project.afterEvaluate {
            addBuildDockerImageTask(project, config)
            addSaveDockerImageTask(project, config)
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

            doLast {
                config.dockerFileDefinitions.each { definition ->
                    def dockerDir = definition.dockerFile.getParentFile()
                    // add default tag based on git version of the Dockerfile
                    def imageVersion = getDockerImageVersion(definition.dockerFile)
                    def imageTag = "${definition.imageName}:g${imageVersion}"
                    def command = ['docker', 'build', '-t', imageTag]
                    // add any specified tags
                    if (! definition.tags.isEmpty()) {
                        def tags = definition.tags.collect { "${definition.imageName}:${it}" }
                        tags = tags.join(',-t,').split(',')
                        command << '-t'
                        command.addAll(tags)
                    }
                    command << '.'

                    project.exec {
                       commandLine command
                       workingDir dockerDir
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

            inputs.files config.dockerFileDefinitions.collect { it.dockerFile }
            def imagesDir = new File(project.buildDir, '/images')
            outputs.dir imagesDir

            doLast {
                imagesDir.mkdirs()
                config.imageTagDir.mkdirs()

                config.dockerFileDefinitions.each { definition ->
                    def dockerDir = definition.dockerFile.getParentFile()
                    def imageVersion = getDockerImageVersion(definition.dockerFile)
                    def imageTag = "${definition.imageName}:g${imageVersion}"
                    def friendlyImageName = definition.imageName.replaceAll('/', '-')
                    def imageFilename = "docker-image-${friendlyImageName}-g${imageVersion}.tar"
                    def imageFile = new File(imagesDir, imageFilename)

                    project.exec {
                        commandLine 'docker', 'save', imageTag
                        standardOutput = new FileOutputStream(imageFile)
                        workingDir imagesDir
                    }

                    // store image tag
                    def imageTagFile = new File(config.imageTagDir, "VERSION.DOCKER-IMAGE.${friendlyImageName}")
                    imageTagFile.text = imageTag
                }
            }
        }
    }

    /**
     * Returns the version to use for this docker image.
     *
     * This is derived as the first git tag after the docker file's commit hash.
     * If there is no tag after the hash, then it reverts to a
     * 'git describe --dirty <commit>'. If the Dockerfile has been modified then
     * '-dirty' will be appended.
     *
     * @return the version for the docker image
     */
    def getDockerImageVersion(File dockerFile) {
        def dockerFilename = dockerFile.getName()
        def workingDir = dockerFile.getParentFile()

        // get the commit of the docker file
        def commit = shell "git --no-pager log -n 1 --pretty=format:%h -- ${dockerFilename}", workingDir

        // set version to the tag after commit
        def version = shell "git describe --contains ${commit}", workingDir
        version = version.replaceFirst(~/(\d+\.\d+.\d+).*/, '$1')
        // if no tag after commit, use preceding tag with offset
        version = version ?: shell("git describe ${commit}", workingDir)

        // mark as dirty if currently modified
        def dirtyFlag = shell "git status --porcelain ${dockerFilename}", workingDir
        dirtyFlag = dirtyFlag.isEmpty() ? '' : '-dirty'

        return version + dirtyFlag
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


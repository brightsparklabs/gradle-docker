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
    // INSTANCE VARIABLES
    // -------------------------------------------------------------------------

    /** The project directory */
    def File projectDir

    // -------------------------------------------------------------------------
    // IMPLEMENTATION: Plugin<Project>
    // -------------------------------------------------------------------------

    void apply(Project project) {
        projectDir = project.projectDir
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
                config.dockerFiles.each { imageName, dockerFilePath ->
                    def dockerFile = new File(dockerFilePath)
                    def dockerDir = dockerFile.getParentFile()
                    def imageVersion = getDockerImageVersion(dockerFile)
                    def imageTag = "${imageName}:${imageVersion}"
                    project.exec {
                       commandLine 'docker', 'build', '-t', imageTag, '.'
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

            inputs.files config.dockerFiles.values()
            def imagesDir = new File(project.buildDir, '/images')
            outputs.dir imagesDir

            doLast {
                imagesDir.mkdirs()

                config.dockerFiles.each { imageName, dockerFilePath ->
                    def dockerFile = new File(dockerFilePath)
                    def dockerDir = dockerFile.getParentFile()
                    def imageVersion = getDockerImageVersion(dockerFile)
                    def imageTag = "${imageName}:${imageVersion}"
                    def imageFilename = "docker-image-" +
                        imageName.replaceAll('/', '-') +
                        "-${imageVersion}.tar"
                    def imageFile = new File(imagesDir, imageFilename)

                    project.exec {
                        commandLine 'docker', 'save', imageTag
                        standardOutput = new FileOutputStream(imageFile)
                        workingDir imagesDir
                    }

                    // store image tag in distribution
                    //versionFile.text = imageTag
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
        def dockerFilePath = dockerFile.getAbsolutePath()

        // get the commit of the docker file
        def commit = shell "git --no-pager log -n 1 --pretty=format:%h -- ${dockerFilePath}"

        // set version to the tag after commit
        def version = shell "git describe --contains ${commit}"
        version = version.replaceFirst(~/(\d+\.\d+.\d+).*/, '$1')
        // if no tag after commit, use preceding tag with offset
        version = version ?: shell("git describe ${commit}")

        // mark as dirty if currently modified
        def dirtyFlag = shell "git status --porcelain ${dockerFilePath}"
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
    def shell(String command) {
        return command.execute(null, projectDir).text.trim()
    }
}


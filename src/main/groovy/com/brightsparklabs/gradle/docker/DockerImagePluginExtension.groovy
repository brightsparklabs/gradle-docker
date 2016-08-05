/*
 * Created by brightSPARK Labs
 * www.brightsparklabs.com
 */

package com.brightsparklabs.gradle.docker

/**
 * Configuration object for the DockerImage plugin.
 */
class DockerImagePluginExtension {
    /** Dockerfiles to create images from mapped as: image_name => Dockerfile. */
    Map dockerFiles = [:]

    /** Directory to store image tags in */
    File imageTagDir = new File('build/imageTags')
}


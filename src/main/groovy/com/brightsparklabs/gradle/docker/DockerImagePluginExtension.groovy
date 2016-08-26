/*
 * Created by brightSPARK Labs
 * www.brightsparklabs.com
 */

package com.brightsparklabs.gradle.docker

/**
 * Configuration object for the DockerImage plugin.
 */
class DockerImagePluginExtension {
    /**
     * Definitions of the Dockerfiles to build. This is an array of Maps. Each
     * Map has the following format:
     *    [
     *        // path to dockerfile
     *        'dockerFile' : file('src/acme/Dockerfile'),
     *        // name for the image
     *        'imageName'  : 'brightsparklabs/acme',
     *        // custom tags for the image
     *        'tags'       : ['v1.0.1', 'blue']
     *    ]
     */
    List dockerFileDefinitions = []

    /** Directory to store image tags in */
    File imageTagDir = new File('build/imageTags')
}


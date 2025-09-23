package com.greg.respiroc.fullstacktest

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication


private val log = LoggerFactory.getLogger(FullstackTestApplication::class.java)


@SpringBootApplication
class FullstackTestApplication

fun main(args: Array<String>) {
    log.info("JVM version is {}", System.getProperty("java.version"))
    runApplication<FullstackTestApplication>(*args)
}

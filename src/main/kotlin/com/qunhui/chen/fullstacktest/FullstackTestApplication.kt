package com.qunhui.chen.fullstacktest

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling


/**
 * @author Qunhui Chen
 * @date 2025/9/23 22:58
 */

private val log = LoggerFactory.getLogger(FullstackTestApplication::class.java)

@EnableScheduling
@SpringBootApplication
class FullstackTestApplication

fun main(args: Array<String>) {
    log.info("JVM version is {}", System.getProperty("java.version"))
    runApplication<FullstackTestApplication>(*args)
}

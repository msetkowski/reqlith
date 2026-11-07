package io.reqlith

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class ReqlithApplication


fun main(args: Array<String>) {
    // kotlin-logging would otherwise print an "initializing..." line to stdout, outside the log format.
    System.setProperty("kotlin-logging.logStartupMessage", "false")
    runApplication<ReqlithApplication>(*args)
}

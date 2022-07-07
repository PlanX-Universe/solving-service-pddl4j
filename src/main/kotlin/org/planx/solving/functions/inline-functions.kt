package org.planx.solving.functions

import org.slf4j.LoggerFactory

inline fun <reified T> getLoggerFor() = LoggerFactory.getLogger(T::class.java)!!
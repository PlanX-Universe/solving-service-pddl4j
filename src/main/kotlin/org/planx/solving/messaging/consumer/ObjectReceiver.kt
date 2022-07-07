package org.planx.solving.messaging.consumer

import org.planx.solving.functions.getLoggerFor
import org.planx.solving.services.SolvingService
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.annotation.RabbitHandler
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component
import org.planx.common.models.endpoint.solving.Planner
import org.planx.common.models.transforming.converting.encoding.pddl.PddlEncodingResponseBody

@Component
@RabbitListener(queues = ["\${planx.queues.reply.object.name}"])
class ObjectReceiver(private var solvingService: SolvingService) {
    var logger = getLoggerFor<ObjectReceiver>()

    @RabbitHandler
    fun handleMessage(message: PddlEncodingResponseBody, row: Message) {
        logger.info("Request received! (RequestID: ${message.requestId})")
        val currentElement = message.callStack.peek()
        val planner: Planner = when (currentElement.state?.get("planner")) {
            Planner.HSP.value -> Planner.HSP
            Planner.FF.value -> Planner.FF
            Planner.FFAnytime.value -> Planner.FFAnytime
            Planner.HCAnytime.value -> Planner.HCAnytime
            else -> {
                logger.error("Switch to default Planner!")
                Planner.HSP
            }
        }

        solvingService.solveByEncodedProblem(
            encodedProblem = message.content?.result!!,
            plannerName = planner,
            requestId = message.requestId,
            callStack = message.callStack
        )
    }
}

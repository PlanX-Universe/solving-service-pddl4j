package org.planx.solving.services

import org.planx.solving.functions.getLoggerFor
import org.planx.solving.messaging.producer.Sender
import org.planx.solving.services.solvers.PddlSolvingService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.planx.common.models.CallStack
import org.planx.common.models.FunctionalityType
import org.planx.common.models.MutableStack
import org.planx.common.models.endpoint.solving.Language
import org.planx.common.models.endpoint.solving.Planner
import org.planx.common.models.endpoint.solving.SolvingRequest
import org.planx.common.models.endpoint.solving.encoded.EncodedProblem
import org.planx.common.models.endpoint.solving.encoded.pddl4j.PddlEncodedProblem
import org.planx.common.models.endpoint.solving.plan.Plan
import org.planx.common.models.transforming.converting.encoding.pddl.PddlEncodingResponseBody
import org.planx.common.models.transforming.parsing.pddl.PddlParsingRequest

@Service
class SolvingService(
    private val pddlSolvingService: PddlSolvingService,
    private val sender: Sender
) {
    private val logger = getLoggerFor<SolvingService>()

    @Value("\${planx.queues.reply.object.key}")
    private val objectReplyKey: String = ""

    fun solveProblem(
        content: SolvingRequest,
        requestId: String,
        callStack: MutableStack<CallStack>
    ) {
        logger.info("Start Planning for $requestId")

        if (!callStack.isEmpty()) {
            // remove current step
            callStack.pop()
        }

        // add reply step
        callStack.push(
            CallStack(
                topic = FunctionalityType.Solving.topic,
                routingKey = objectReplyKey,
                replyClass = PddlEncodingResponseBody::class.java.name,
                state = mapOf(
                    "planner" to content.planner?.value!!
                )
            )
        )

        // add remote call
        callStack.push(
            CallStack(
                topic = FunctionalityType.Parsing.topic,
                routingKey = FunctionalityType.Parsing.routingKey,
                replyClass = PddlEncodingResponseBody::class.java.name,
                state = mapOf(
                    "language" to Language.PDDL.name
                )
            )
        )

        parseProblemAndDomain(
            content.problem!!,
            content.domain!!,
            lang = content.language,
            callStack = callStack,
            requestId = requestId
        )
    }

    /**
     * @param encodedProblem  of type [EncodedProblem]. The encoded problem contains also domain information.
     * @param plannerName as [Planner] enum (Only PDDL4j solver supported yet!)
     * @param requestId as correlation ID to identify errors and solutions
     * @param callStack as stack implementation for dynamical routing (cf. Routing-Slip-Pattern)
     *
     *
     * @throws [NotImplementedError] for [TODO] content
     */
    fun <T> solveByEncodedProblem(
        encodedProblem: T,
        plannerName: Planner,
        requestId: String,
        callStack: MutableStack<CallStack> = MutableStack()
    ) where T : EncodedProblem {
        when (encodedProblem) {
            is PddlEncodedProblem -> {
                logger.info("Run solving for ${Language.PDDL.name}")

                // call plan solving
                val plan: Plan<*>? = pddlSolvingService.createPlan(
                    encodedProblem = encodedProblem,
                    plannerName = plannerName
                )

                // remove current step
                if (!callStack.isEmpty()) {
                    callStack.pop()
                }

                // send response
                sendPlan(
                    plan = plan,
                    requestId = requestId,
                    callStack = callStack
                )
            }
            else -> TODO("Not contained on prototype!")
        }
    }

    /**
     * @param problem is a base64 encoded [String]
     * @param domain is a base64 encoded [String]
     * @param lang is the language of the Problem and the Domain as [Language].
     *        The default value for lang is Language.PDDL
     */
    private fun parseProblemAndDomain(
        problem: String,
        domain: String,
        lang: Language? = Language.PDDL,
        callStack: MutableStack<CallStack>,
        requestId: String
    ) {
        logger.info("Request parsing functionality")
        when (lang) {
            Language.PDDL -> sender.sendPddlParsingRequest(
                parsingRequest = PddlParsingRequest(domain, problem),
                requestId = requestId,
                callStack = callStack
            )
            null -> throw Exception("Illegal language exception!")
            else -> TODO("Not implemented on the prototype")
        }
    }

    /**
     * sends response of planning
     */
    private fun <T> sendPlan(plan: T?, callStack: MutableStack<CallStack>, requestId: String) where T : Plan<*> {
        sender.sendPlan(plan, requestId, callStack)
    }
}

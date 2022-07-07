package org.planx.solving.services.solvers

import fr.uga.pddl4j.encoding.CodedProblem
import fr.uga.pddl4j.planners.statespace.AbstractStateSpacePlanner
import fr.uga.pddl4j.planners.statespace.StateSpacePlannerFactory
import org.planx.solving.functions.bitOp2Action
import org.planx.solving.functions.getLoggerFor
import org.planx.solving.services.SolvingService
import org.springframework.stereotype.Service
import org.planx.common.models.endpoint.solving.Planner
import org.planx.common.models.endpoint.solving.encoded.EncodedProblem
import org.planx.common.models.endpoint.solving.encoded.pddl4j.PddlEncodedProblem
import org.planx.common.models.endpoint.solving.plan.Plan
import org.planx.common.models.endpoint.solving.plan.PlanXAction
import org.planx.common.models.endpoint.solving.plan.sequential.SequentialPlan
import org.planx.common.models.pddl4j.PDDL4jConverter
import fr.uga.pddl4j.util.ParallelPlan as PddlParallelPlan
import fr.uga.pddl4j.util.Plan as PddlPlan
import fr.uga.pddl4j.util.SequentialPlan as PddlSequentialPlan

/**
 * Service implementation for PDDL using PDDL4J (see fr.uga.pddl4j)
 * This service is limited to StateSpacePlanner.
 * TODO: check if PDDL4J has a new Release version (> 3.8). Probably more Planners are supported.
 */
@Service
class PddlSolvingService {
    private val logger = getLoggerFor<SolvingService>()

    // PDDL4j requires an StateSpacePlannerFactory instance
    private val stateSpacePlannerFactory: StateSpacePlannerFactory = StateSpacePlannerFactory.getInstance()

    /**
     * Solving method. Currently only supported for [PddlSequentialPlan]s.
     *
     * @param encodedProblem  of type [EncodedProblem]. The encoded problem contains also domain information.
     * @param plannerName as [Planner] enum (Only PDDL4j solver supported yet!)
     *
     * @return returns a generic plan. If no plan is found this method returns `null`.
     *
     * @throws [NotImplementedError] for [TODO] content
     */
    fun <P> createPlan(
        encodedProblem: PddlEncodedProblem,
        plannerName: Planner
    ): P? where P : Plan<*> {
        logger.info("Creating a new Plan")
        // map stateSpacePlanner to correct datatype
        val stateSpacePlanner: fr.uga.pddl4j.planners.Planner.Name = when (plannerName) {
            Planner.HSP -> fr.uga.pddl4j.planners.Planner.Name.HSP
            Planner.FF -> fr.uga.pddl4j.planners.Planner.Name.FF
            Planner.FFAnytime -> fr.uga.pddl4j.planners.Planner.Name.FFAnytime
            Planner.HCAnytime -> fr.uga.pddl4j.planners.Planner.Name.HCAnytime
        }
        logger.info("Planner: ${stateSpacePlanner.name}")
        val planner: AbstractStateSpacePlanner = stateSpacePlannerFactory.getPlanner(stateSpacePlanner)
        val codedProblem: CodedProblem = PDDL4jConverter.convertEncodedProblem2Pddl4j(encodedProblem)

        // Searches for a solution plan
        val plan: PddlPlan? = planner.search(codedProblem)

        logger.info("took {}ms to search for a plan", planner.statistics.timeToSearch)

        var responsePlan: P? = null
        /**
         * switch depending on the resulting plan
         */
        when (plan) {
            null -> {
                logger.info("no plan found!")
            }
            is PddlSequentialPlan -> {
                logger.error("plan found!")
                val sb = StringBuilder()
                sb.appendln(String.format("%nfound plan as follows:%n%n"))
                sb.append(codedProblem.toString(plan))
                sb.appendln(String.format("%nplan total cost: %4.2f%n%n", plan.cost()))
                logger.debug(sb.toString())

                val sequentialPlan: SequentialPlan<PlanXAction> = convertToSequentialPlan(plan, codedProblem)

                responsePlan = sequentialPlan as P
            }
            is PddlParallelPlan -> {
                TODO("PddlParallelPlan is not implement yet!")
            }
        }

        return responsePlan
    }

    /**
     * Converts PDDL4J plan to custom data structure to a sequential plan.
     *
     * @param pddl4jPlan of type [PddlSequentialPlan].
     * @param codedProblem of type [CodedProblem]
     */
    private fun convertToSequentialPlan(
        pddl4jPlan: PddlSequentialPlan,
        codedProblem: CodedProblem
    ): SequentialPlan<PlanXAction> {
        val actions: MutableList<PlanXAction> = emptyList<PlanXAction>().toMutableList()

        pddl4jPlan.timeSpecifiers().forEach { moment: Int ->
            pddl4jPlan.getActionSet(moment).forEach { action ->
                // TODO:  verify action moment
                actions.add(bitOp2Action(action, codedProblem, moment))
            }
        }

        return SequentialPlan(
            actions = actions,
            cost = pddl4jPlan.cost()
        )
    }
}
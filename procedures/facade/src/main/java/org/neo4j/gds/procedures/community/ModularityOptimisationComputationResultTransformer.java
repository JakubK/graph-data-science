/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.gds.procedures.community;

import org.neo4j.gds.algorithms.community.specificfields.ModularityOptimizationSpecificFields;
import org.neo4j.gds.algorithms.NodePropertyMutateResult;
import org.neo4j.gds.algorithms.NodePropertyWriteResult;
import org.neo4j.gds.algorithms.StatsResult;
import org.neo4j.gds.algorithms.StreamComputationResult;
import org.neo4j.gds.algorithms.community.CommunityResultCompanion;
import org.neo4j.gds.api.IdMap;
import org.neo4j.gds.modularityoptimization.ModularityOptimizationMutateConfig;
import org.neo4j.gds.modularityoptimization.ModularityOptimizationResult;
import org.neo4j.gds.modularityoptimization.ModularityOptimizationStatsConfig;
import org.neo4j.gds.modularityoptimization.ModularityOptimizationStreamConfig;
import org.neo4j.gds.procedures.community.modularityoptimization.ModularityOptimizationMutateResult;
import org.neo4j.gds.procedures.community.modularityoptimization.ModularityOptimizationStatsResult;
import org.neo4j.gds.procedures.community.modularityoptimization.ModularityOptimizationStreamResult;
import org.neo4j.gds.procedures.community.modularityoptimization.ModularityOptimizationWriteResult;

import java.util.stream.LongStream;
import java.util.stream.Stream;

final class ModularityOptimisationComputationResultTransformer {

    private ModularityOptimisationComputationResultTransformer() {}

    static Stream<ModularityOptimizationStreamResult> toStreamResult(
        StreamComputationResult<ModularityOptimizationResult> computationResult,
        ModularityOptimizationStreamConfig config
    ) {
        return computationResult.result()
            .map(result -> {
                var graph = computationResult.graph();
                var nodePropertyValues = CommunityResultCompanion.nodePropertyValues(
                    config.consecutiveIds(),
                    result.asNodeProperties(),
                    config.minCommunitySize(),
                    config.concurrency()
                );
                return LongStream
                    .range(IdMap.START_NODE_ID, graph.nodeCount())
                    .filter(nodePropertyValues::hasValue)
                    .mapToObj(nodeId -> new ModularityOptimizationStreamResult(
                        graph.toOriginalNodeId(nodeId),
                        nodePropertyValues.longValue(nodeId)
                    ));
            }).orElseGet(Stream::empty);
    }

    static ModularityOptimizationMutateResult toMutateResult(
        NodePropertyMutateResult<ModularityOptimizationSpecificFields> computationResult,
        ModularityOptimizationMutateConfig mutateConfig
    ) {
        return new ModularityOptimizationMutateResult(
            computationResult.preProcessingMillis(),
            computationResult.computeMillis(),
            computationResult.postProcessingMillis(),
            computationResult.mutateMillis(),
            computationResult.algorithmSpecificFields().nodes(),
            computationResult.algorithmSpecificFields().didConverge(),
            computationResult.algorithmSpecificFields().ranIterations(),
            computationResult.algorithmSpecificFields().modularity(),
            computationResult.algorithmSpecificFields().communityCount(),
            computationResult.algorithmSpecificFields().communityDistribution(),
            mutateConfig.toMap()
        );
    }

    static ModularityOptimizationStatsResult toStatsResult(
        StatsResult<ModularityOptimizationSpecificFields> computationResult,
        ModularityOptimizationStatsConfig configuration
    ) {
        return new ModularityOptimizationStatsResult(
            computationResult.preProcessingMillis(),
            computationResult.computeMillis(),
            computationResult.postProcessingMillis(),
            computationResult.algorithmSpecificFields().nodes(),
            computationResult.algorithmSpecificFields().didConverge(),
            computationResult.algorithmSpecificFields().ranIterations(),
            computationResult.algorithmSpecificFields().modularity(),
            computationResult.algorithmSpecificFields().communityCount(),
            computationResult.algorithmSpecificFields().communityDistribution(),
            configuration.toMap()
        );
    }

    static ModularityOptimizationWriteResult toWriteResult(NodePropertyWriteResult<ModularityOptimizationSpecificFields> computationResult) {
        return new ModularityOptimizationWriteResult(
            computationResult.preProcessingMillis(),
            computationResult.computeMillis(),
            computationResult.postProcessingMillis(),
            computationResult.writeMillis(),
            computationResult.algorithmSpecificFields().nodes(),
            computationResult.algorithmSpecificFields().didConverge(),
            computationResult.algorithmSpecificFields().ranIterations(),
            computationResult.algorithmSpecificFields().modularity(),
            computationResult.algorithmSpecificFields().communityCount(),
            computationResult.algorithmSpecificFields().communityDistribution(),
            computationResult.configuration().toMap()
        );
    }
}

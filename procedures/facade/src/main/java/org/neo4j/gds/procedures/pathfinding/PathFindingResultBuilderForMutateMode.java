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
package org.neo4j.gds.procedures.pathfinding;

import org.neo4j.gds.api.Graph;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.applications.algorithms.pathfinding.AlgorithmProcessingTimings;
import org.neo4j.gds.applications.algorithms.pathfinding.ResultBuilder;
import org.neo4j.gds.config.ToMapConvertible;
import org.neo4j.gds.paths.dijkstra.PathFindingResult;

import java.util.Optional;

class PathFindingResultBuilderForMutateMode extends ResultBuilder<PathFindingResult, PathFindingMutateResult> {
    private final ToMapConvertible configuration;

    PathFindingResultBuilderForMutateMode(ToMapConvertible configuration) {
        this.configuration = configuration;
    }

    @Override
    public PathFindingMutateResult build(
        Graph graph,
        GraphStore graphStore,
        Optional<PathFindingResult> pathFindingResult,
        AlgorithmProcessingTimings timings
    ) {
        return new PathFindingMutateResult(
            timings.preProcessingMillis,
            timings.computeMillis,
            0, // yeah, I don't understand it either :shrug:
            timings.postProcessingMillis,
            relationshipsWritten,
            configuration.toMap()
        );
    }
}

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
package org.neo4j.gds.applications.algorithms.pathfinding;

import org.neo4j.gds.api.GraphName;
import org.neo4j.gds.paths.astar.AStarMemoryEstimateDefinition;
import org.neo4j.gds.paths.astar.config.ShortestPathAStarStreamConfig;
import org.neo4j.gds.paths.dijkstra.DijkstraMemoryEstimateDefinition;
import org.neo4j.gds.paths.dijkstra.PathFindingResult;
import org.neo4j.gds.paths.dijkstra.config.AllShortestPathsDijkstraStreamConfig;
import org.neo4j.gds.paths.dijkstra.config.ShortestPathDijkstraStreamConfig;
import org.neo4j.gds.paths.yens.YensMemoryEstimateDefinition;
import org.neo4j.gds.paths.yens.config.ShortestPathYensStreamConfig;

import java.util.Optional;

import static org.neo4j.gds.applications.algorithms.pathfinding.AlgorithmLabels.A_STAR;
import static org.neo4j.gds.applications.algorithms.pathfinding.AlgorithmLabels.DIJKSTRA;
import static org.neo4j.gds.applications.algorithms.pathfinding.AlgorithmLabels.YENS;

/**
 * Here is the top level business facade for all your path finding stream needs.
 * It will have all pathfinding algorithms on it, in stream mode.
 */
public class PathFindingAlgorithmsStreamModeBusinessFacade {
    private final AlgorithmProcessingTemplate algorithmProcessingTemplate;

    private final PathFindingAlgorithms pathFindingAlgorithms;

    public PathFindingAlgorithmsStreamModeBusinessFacade(
        AlgorithmProcessingTemplate algorithmProcessingTemplate,
        PathFindingAlgorithms pathFindingAlgorithms
    ) {
        this.algorithmProcessingTemplate = algorithmProcessingTemplate;
        this.pathFindingAlgorithms = pathFindingAlgorithms;
    }

    public <RESULT> RESULT singlePairShortestPathAStarStream(
        GraphName graphName,
        ShortestPathAStarStreamConfig configuration,
        ResultBuilder<PathFindingResult, RESULT> resultBuilder
    ) {
        return algorithmProcessingTemplate.processAlgorithm(
            graphName,
            configuration,
            A_STAR,
            () -> new AStarMemoryEstimateDefinition().memoryEstimation(configuration),
            graph -> pathFindingAlgorithms.singlePairShortestPathAStar(graph, configuration),
            Optional.empty(),
            resultBuilder
        );
    }

    public <RESULT> RESULT singlePairShortestPathDijkstraStream(
        GraphName graphName,
        ShortestPathDijkstraStreamConfig configuration,
        ResultBuilder<PathFindingResult, RESULT> resultBuilder
    ) {
        return algorithmProcessingTemplate.processAlgorithm(
            graphName,
            configuration,
            DIJKSTRA,
            () -> new DijkstraMemoryEstimateDefinition().memoryEstimation(configuration),
            graph -> pathFindingAlgorithms.singlePairShortestPathDijkstra(graph, configuration),
            Optional.empty(),
            resultBuilder
        );
    }

    public <RESULT> RESULT singlePairShortestPathYensStream(
        GraphName graphName,
        ShortestPathYensStreamConfig configuration,
        ResultBuilder<PathFindingResult, RESULT> resultBuilder
    ) {
        return algorithmProcessingTemplate.processAlgorithm(
            graphName,
            configuration,
            YENS,
            () -> new YensMemoryEstimateDefinition().memoryEstimation(configuration),
            graph -> pathFindingAlgorithms.singlePairShortestPathYens(graph, configuration),
            Optional.empty(),
            resultBuilder
        );
    }

    public <RESULT> RESULT singleSourceShortestPathDijkstraStream(
        GraphName graphName,
        AllShortestPathsDijkstraStreamConfig configuration,
        ResultBuilder<PathFindingResult, RESULT> resultBuilder
    ) {
        return algorithmProcessingTemplate.processAlgorithm(
            graphName,
            configuration,
            DIJKSTRA,
            () -> new DijkstraMemoryEstimateDefinition().memoryEstimation(configuration),
            graph -> pathFindingAlgorithms.singleSourceShortestPathDijkstra(graph, configuration),
            Optional.empty(),
            resultBuilder
        );
    }
}

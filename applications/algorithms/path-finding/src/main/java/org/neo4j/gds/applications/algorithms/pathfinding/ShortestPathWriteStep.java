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

import org.neo4j.gds.api.DatabaseId;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.api.IdMap;
import org.neo4j.gds.api.nodeproperties.ValueType;
import org.neo4j.gds.config.WriteRelationshipConfig;
import org.neo4j.gds.core.utils.progress.TaskRegistryFactory;
import org.neo4j.gds.core.utils.progress.tasks.TaskProgressTracker;
import org.neo4j.gds.core.write.ImmutableExportedRelationship;
import org.neo4j.gds.core.write.RelationshipStreamExporter;
import org.neo4j.gds.core.write.RelationshipStreamExporterBuilder;
import org.neo4j.gds.logging.Log;
import org.neo4j.gds.paths.PathResult;
import org.neo4j.gds.paths.WritePathOptionsConfig;
import org.neo4j.gds.paths.dijkstra.PathFindingResult;
import org.neo4j.gds.termination.TerminationFlag;
import org.neo4j.values.storable.Value;
import org.neo4j.values.storable.Values;

import java.util.List;

import static org.neo4j.gds.paths.dijkstra.config.ShortestPathDijkstraWriteConfig.COSTS_KEY;
import static org.neo4j.gds.paths.dijkstra.config.ShortestPathDijkstraWriteConfig.NODE_IDS_KEY;
import static org.neo4j.gds.paths.dijkstra.config.ShortestPathDijkstraWriteConfig.TOTAL_COST_KEY;

/**
 * This is relationship writes as needed by path finding algorithms (for now).
 */
class ShortestPathWriteStep<CONFIGURATION extends WriteRelationshipConfig & WritePathOptionsConfig> implements MutateOrWriteStep<PathFindingResult> {
    private final Log log;

    private final RelationshipStreamExporterBuilder exporterBuilder;
    private final TaskRegistryFactory taskRegistryFactory;
    private final TerminationFlag terminationFlag;

    private final CONFIGURATION configuration;

    ShortestPathWriteStep(
        Log log,
        RelationshipStreamExporterBuilder exporterBuilder,
        TaskRegistryFactory taskRegistryFactory,
        TerminationFlag terminationFlag,
        CONFIGURATION configuration
    ) {
        this.log = log;
        this.exporterBuilder = exporterBuilder;
        this.taskRegistryFactory = taskRegistryFactory;
        this.terminationFlag = terminationFlag;
        this.configuration = configuration;
    }

    /**
     * Here we translate and write relationships from path finding algorithms back to the database.
     * We do it synchronously, time it, and gather metadata about how many relationships we wrote.
     */
    @Override
    public <RESULT_TO_CALLER> void execute(
        Graph graph,
        GraphStore graphStore,
        PathFindingResult result,
        ResultBuilder<PathFindingResult, RESULT_TO_CALLER> resultBuilder
    ) {
        var writeNodeIds = configuration.writeNodeIds();
        var writeCosts = configuration.writeCosts();

        /*
         * We have to ensure the stream closes, so that progress tracker closes.
         * It is abominable that we have to do this. To be fixed in the future, somehow.
         * The problem is that apparently progress tracker is keyed off of ths stream,
         * and that we cannot rely on whatever plugged in exporter comes along takes responsibility for these things.
         * Ergo we need this little block, but really we should engineer it all better.
         */
        try (
            var relationshipStream = result
                .mapPaths(
                    pathResult -> ImmutableExportedRelationship.of(
                        pathResult.sourceNode(),
                        pathResult.targetNode(),
                        createValues(graph, pathResult, writeNodeIds, writeCosts)
                    )
                )
        ) {
            var progressTracker = new TaskProgressTracker(
                RelationshipStreamExporter.baseTask("Write shortest Paths"),
                (org.neo4j.logging.Log) log.getNeo4jLog(),
                1,
                taskRegistryFactory
            );

            // configure the exporter
            var relationshipStreamExporter = exporterBuilder
                .withConcurrency(configuration.writeConcurrency())
                .withArrowConnectionInfo(
                    configuration.arrowConnectionInfo(),
                    graphStore.databaseInfo().remoteDatabaseId().map(DatabaseId::databaseName)
                )
                .withIdMappingOperator(graph::toOriginalNodeId)
                .withProgressTracker(progressTracker)
                .withRelationships(relationshipStream)
                .withTerminationFlag(terminationFlag)
                .build();

            var writeRelationshipType = configuration.writeRelationshipType();

            /*
             * The actual export.
             * Notice that originally we had a CloseableResourceRegistry thing going on here - no longer.
             * Because all we are doing is, processing a stream using the exporter, synchronously.
             * We are not handing it out to upper layers for sporadic consumption.
             * It is done right here, and when we complete, the stream is exhausted.
             * We still explicitly close the stream tho because, yeah, confusion I guess.
             */
            var keys = createKeys(writeNodeIds, writeCosts);
            var types = createTypes(writeNodeIds, writeCosts);

            var relationshipsWritten = relationshipStreamExporter.write(writeRelationshipType, keys, types);

            // the final result is the side effect of writing to the database, plus this metadata
            resultBuilder.withRelationshipsWritten(relationshipsWritten);
        }
    }

    private Value[] createValues(IdMap idMap, PathResult pathResult, boolean writeNodeIds, boolean writeCosts) {
        if (writeNodeIds && writeCosts) {
            return new Value[]{
                Values.doubleValue(pathResult.totalCost()),
                Values.longArray(toOriginalIds(idMap, pathResult.nodeIds())),
                Values.doubleArray(pathResult.costs())
            };
        }
        if (writeNodeIds) {
            return new Value[]{
                Values.doubleValue(pathResult.totalCost()),
                Values.longArray(toOriginalIds(idMap, pathResult.nodeIds())),
            };
        }
        if (writeCosts) {
            return new Value[]{
                Values.doubleValue(pathResult.totalCost()),
                Values.doubleArray(pathResult.costs())
            };
        }
        return new Value[]{
            Values.doubleValue(pathResult.totalCost()),
        };
    }

    // Replaces the ids in the given array with the original ids
    private long[] toOriginalIds(IdMap idMap, long[] internalIds) {
        for (int i = 0; i < internalIds.length; i++) {
            internalIds[i] = idMap.toOriginalNodeId(internalIds[i]);
        }
        return internalIds;
    }

    private List<String> createKeys(boolean writeNodeIds, boolean writeCosts) {
        if (writeNodeIds && writeCosts) {
            return List.of(
                TOTAL_COST_KEY,
                NODE_IDS_KEY,
                COSTS_KEY
            );
        }
        if (writeNodeIds) {
            return List.of(
                TOTAL_COST_KEY,
                NODE_IDS_KEY
            );
        }
        if (writeCosts) {
            return List.of(
                TOTAL_COST_KEY,
                COSTS_KEY
            );
        }
        return List.of(TOTAL_COST_KEY);
    }

    private List<ValueType> createTypes(boolean writeNodeIds, boolean writeCosts) {
        if (writeNodeIds && writeCosts) {
            return List.of(
                ValueType.DOUBLE,
                ValueType.LONG_ARRAY,
                ValueType.DOUBLE_ARRAY
            );
        }
        if (writeNodeIds) {
            return List.of(
                ValueType.DOUBLE,
                ValueType.LONG_ARRAY
            );
        }
        if (writeCosts) {
            return List.of(
                ValueType.DOUBLE,
                ValueType.DOUBLE_ARRAY
            );
        }
        return List.of(ValueType.DOUBLE);
    }
}

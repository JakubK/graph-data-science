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
package org.neo4j.gds.procedures.integration;

import org.neo4j.function.ThrowingFunction;
import org.neo4j.gds.logging.Log;
import org.neo4j.gds.metrics.procedures.DeprecatedProceduresMetricService;
import org.neo4j.gds.procedures.GraphDataScience;
import org.neo4j.gds.procedures.GraphDataScienceBuilder;
import org.neo4j.internal.kernel.api.exceptions.ProcedureException;
import org.neo4j.kernel.api.procedure.Context;

/**
 * We use this at request time to construct the facade that the procedures call.
 */
public class GraphDataScienceProvider implements ThrowingFunction<Context, GraphDataScience, ProcedureException> {
    private final Log log;
    private final CatalogFacadeProvider catalogFacadeProvider;
    private final AlgorithmFacadeFactoryProvider algorithmFacadeFactoryProvider;
    private final DeprecatedProceduresMetricService deprecatedProceduresMetricService;

    GraphDataScienceProvider(
        Log log,
        CatalogFacadeProvider catalogFacadeProvider,
        AlgorithmFacadeFactoryProvider algorithmFacadeFactoryProvider,
        DeprecatedProceduresMetricService deprecatedProceduresMetricService
    ) {
        this.log = log;
        this.catalogFacadeProvider = catalogFacadeProvider;
        this.algorithmFacadeFactoryProvider = algorithmFacadeFactoryProvider;
        this.deprecatedProceduresMetricService = deprecatedProceduresMetricService;
    }

    @Override
    public GraphDataScience apply(Context context) throws ProcedureException {
        var catalogFacade = catalogFacadeProvider.createCatalogFacade(context);

        var algorithmFacadeFactory = algorithmFacadeFactoryProvider.createAlgorithmFacadeFactory(context);
        var centralityProcedureFacade = algorithmFacadeFactory.createCentralityProcedureFacade();
        var communityProcedureFacade = algorithmFacadeFactory.createCommunityProcedureFacade();
        var pathFindingProcedureFacade = algorithmFacadeFactory.createPathFindingProcedureFacade();
        var similarityProcedureFacade = algorithmFacadeFactory.createSimilarityProcedureFacade();
        var nodeEmbeddingsProcedureFacade = algorithmFacadeFactory.createNodeEmbeddingsProcedureFacade();

        return new GraphDataScienceBuilder(log)
            .with(catalogFacade)
            .with(centralityProcedureFacade)
            .with(communityProcedureFacade)
            .with(nodeEmbeddingsProcedureFacade)
            .with(similarityProcedureFacade)
            .with(pathFindingProcedureFacade)
            .with(deprecatedProceduresMetricService)
            .build();
    }
}

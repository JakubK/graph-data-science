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
package org.neo4j.gds.paths.traverse;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.neo4j.gds.assertions.MemoryEstimationAssert;
import org.neo4j.gds.core.GraphDimensions;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BFSMemoryEstimateDefinitionTest {

    @ParameterizedTest
    @CsvSource(
        {
            "10_000,100_000,1,402_988,402_988",
            "10_000,100_000,2,402_988,482_972",
            "100_000,1_000_000,1,4_026_890,4_026_890",
            "100_000,1_000_000,2,4_026_890,4_826_874"
        }
    )
    void testMemoryEstimation(
        long nodeCount,
        long relationshipCount,
        int concurrency,
        long expectedMin,
        long expectedMax
    ) {

        var dimensions = GraphDimensions.builder().nodeCount(nodeCount).relCountUpperBound(relationshipCount).build();

        var config = mock(BfsBaseConfig.class);
        when(config.concurrency()).thenReturn(concurrency);

        var memoryEstimation = new BfsMemoryEstimateDefinition();

        MemoryEstimationAssert.assertThat(memoryEstimation.memoryEstimation(config))
            .memoryRange(dimensions, concurrency)
            .hasMin(expectedMin)
            .hasMax(expectedMax);
    }
}

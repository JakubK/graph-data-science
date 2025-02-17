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
package org.neo4j.gds.approxmaxkcut;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.neo4j.gds.TestSupport;
import org.neo4j.gds.approxmaxkcut.config.ApproxMaxKCutBaseConfigImpl;
import org.neo4j.gds.compat.Neo4jProxy;
import org.neo4j.gds.compat.TestLog;
import org.neo4j.gds.core.concurrency.DefaultPool;
import org.neo4j.gds.core.concurrency.ParallelUtil;
import org.neo4j.gds.core.utils.progress.EmptyTaskRegistryFactory;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.extension.GdlExtension;
import org.neo4j.gds.extension.GdlGraph;
import org.neo4j.gds.extension.Inject;
import org.neo4j.gds.extension.TestGraph;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.neo4j.gds.utils.StringFormatting.formatWithLocale;

@GdlExtension
final class ApproxMaxKCutTest {

    // The maximum 2-cut of this graph is:
    //     {a, b, c}, {d, e, f, g} if the graph is unweighted.
    //     {a, c}, {b, d, e, f, g} if the graph is weighted.
    @GdlGraph(graphNamePrefix = "max")
    private static final String DB_CYPHER =
        "CREATE" +
        "  (a:Label1)" +
        ", (b:Label1)" +
        ", (c:Label1)" +
        ", (d:Label1)" +
        ", (e:Label1)" +
        ", (f:Label1)" +
        ", (g:Label1)" +

        ", (a)-[:TYPE1 {weight: 81.0}]->(b)" +
        ", (a)-[:TYPE1 {weight: 7.0}]->(d)" +
        ", (b)-[:TYPE1 {weight: 1.0}]->(d)" +
        ", (b)-[:TYPE1 {weight: 1.0}]->(e)" +
        ", (b)-[:TYPE1 {weight: 1.0}]->(f)" +
        ", (b)-[:TYPE1 {weight: 1.0}]->(g)" +
        ", (c)-[:TYPE1 {weight: 45.0}]->(b)" +
        ", (c)-[:TYPE1 {weight: 3.0}]->(e)" +
        ", (d)-[:TYPE1 {weight: 3.0}]->(c)" +
        ", (d)-[:TYPE1 {weight: 1.0}]->(b)" +
        ", (e)-[:TYPE1 {weight: 1.0}]->(b)" +
        ", (f)-[:TYPE1 {weight: 3.0}]->(a)" +
        ", (f)-[:TYPE1 {weight: 1.0}]->(b)" +
        ", (g)-[:TYPE1 {weight: 1.0}]->(b)" +
        ", (g)-[:TYPE1 {weight: 4.0}]->(c)" +
        ", (g)-[:TYPE1 {weight: 999.0}]->(g)";

    // The minimum 2-cut of this graph is:
    //     {a}, {b, c, d} if the graph is unweighted.
    //     {a, b, c}, {d} if the graph is weighted.
    @GdlGraph(graphNamePrefix = "min")
    private static final String DB_CYPHER_MIN =
        "CREATE" +
        "  (a:Label1)" +
        ", (b:Label1)" +
        ", (c:Label1)" +
        ", (d:Label1)" +

        ", (a)-[:TYPE1 {weight: 81.0}]->(b)" +
        ", (b)-[:TYPE1 {weight: 1.0}]->(d)" +
        ", (c)-[:TYPE1 {weight: 45.0}]->(b)" +
        ", (d)-[:TYPE1 {weight: 3.0}]->(c)" +
        ", (d)-[:TYPE1 {weight: 1.0}]->(b)";

    @Inject
    private TestGraph maxGraph;

    @Inject
    private TestGraph minGraph;

    private static Stream<Arguments> kCutParameters() {
        return TestSupport.crossArguments(
            () -> Stream.of(
                Arguments.of(
                    false,
                    false,
                    Map.of("a", 0L, "b", 0L, "c", 0L, "d", 1L, "e", 1L, "f", 1L, "g", 1L),
                    10.0D // 13.0 is the optimal
                ),
                Arguments.of(
                    false,
                    true,
                    Map.of("a", 0L, "b", 1L, "c", 0L, "d", 1L, "e", 1L, "f", 1L, "g", 1L),
                    100.0D // 146.0 is the optimal
                ),
                Arguments.of(
                    true,
                    false,
                    Map.of("a", 0L, "b", 1L, "c", 1L, "d", 1L),
                    2.0D // 1.0 is the optimal
                ),
                Arguments.of(
                    true,
                    true,
                    Map.of("a", 0L, "b", 0L, "c", 0L, "d", 1L),
                    48.0D // 5.0 is the optimal
                )
            ),
            () -> Stream.of(Arguments.of(0), Arguments.of(4)), // VNS max neighborhood order (0 means VNS not used)
            () -> Stream.of(Arguments.of(1), Arguments.of(4))  // concurrency
        );
    }

    @ParameterizedTest
    @MethodSource("kCutParameters")
    void computeCorrectResults(
        boolean minimize,
        boolean weighted,
        Map<String, Long> expectedMapping,
        double expectedCost,
        int vnsMaxNeighborhoodOrder,
        int concurrency
    ) {
        var k = (byte) 2;
        // We should not need as many iterations if we do VNS.
        var iterations = vnsMaxNeighborhoodOrder > 0 ? 100 : 25;
        var minBatchSize = concurrency > 1 ? 1 : ParallelUtil.DEFAULT_BATCH_SIZE;
        var randomSeed = concurrency > 1 ? Optional.<Long>empty() : Optional.of(42L);
        var minCommunitySizes = minimize ? Collections.nCopies(k, 1L) : Collections.nCopies(k, 0L);
        var graph = minimize ? minGraph : maxGraph;

        var approxMaxKCut = new ApproxMaxKCut(
            graph,
            DefaultPool.INSTANCE,
            k,
            iterations,
            vnsMaxNeighborhoodOrder,
            concurrency,
            minBatchSize,
            randomSeed,
            minCommunitySizes,
            weighted,
            minimize,
            ProgressTracker.NULL_TRACKER
        );

        var result = approxMaxKCut.compute();
        if (minimize) {
            assertThat(result.cutCost()).isLessThanOrEqualTo(expectedCost);
        } else {
            assertThat(result.cutCost()).isGreaterThanOrEqualTo(expectedCost);
        }

        var setFunction = result.candidateSolution();

        expectedMapping.forEach((outerVar, outerExpectedSet) -> {
            long outerNodeId = graph.toMappedNodeId(outerVar);

            expectedMapping.forEach((innerVar, innerExpectedSet) -> {
                long innerNodeId = graph.toMappedNodeId(innerVar);

                if (outerExpectedSet.equals(innerExpectedSet)) {
                    assertThat(setFunction.get(outerNodeId)).isEqualTo(setFunction.get(innerNodeId));
                } else {
                    assertThat(setFunction.get(outerNodeId)).isNotEqualTo(setFunction.get(innerNodeId));
                }
            });
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 4})
    void respectMinCommunitySizes(int concurrency) {
        var minCommunitySizes = LongStream.of(1, 6).boxed().collect(Collectors.toList());
        var minBatchSize = concurrency > 1 ? 1 : ParallelUtil.DEFAULT_BATCH_SIZE;
        var k = (byte) 2;

        var approxMaxKCut = new ApproxMaxKCut(
            maxGraph,
            DefaultPool.INSTANCE,
            k,
            8,
            0,
            concurrency,
            minBatchSize,
            Optional.empty(),
            minCommunitySizes,
            false,
            false,
            ProgressTracker.NULL_TRACKER
        );

        var cardinalities = new long[2];
        var solution = approxMaxKCut.compute().candidateSolution();
        for (int i = 0; i < maxGraph.nodeCount(); i++) {
            cardinalities[solution.get(i)]++;
        }

        assertThat(cardinalities[0]).isIn(1L, 6L);
        assertThat(cardinalities[1]).isIn(1L, 6L);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void progressLogging(int vnsMaxNeighborhoodOrder) {
        var config = ApproxMaxKCutBaseConfigImpl.builder()
            .vnsMaxNeighborhoodOrder(vnsMaxNeighborhoodOrder)
            .build();

        var log = Neo4jProxy.testLog();
        var approxMaxKCut = new ApproxMaxKCutAlgorithmFactory<>().build(
            maxGraph,
            config,
            log,
            EmptyTaskRegistryFactory.INSTANCE
        );
        approxMaxKCut.compute();

        assertThat(log.containsMessage(TestLog.INFO, ":: Start")).isTrue();
        assertThat(log.containsMessage(TestLog.INFO, ":: Finish")).isTrue();

        for (int i = 1; i <= config.iterations(); i++) {
            assertThat(log.containsMessage(
                TestLog.INFO,
                formatWithLocale("place nodes randomly %s of %s :: Start", i, config.iterations())
            )).isTrue();
            assertThat(log.containsMessage(
                TestLog.INFO,
                formatWithLocale("place nodes randomly %s of %s 100%%", i, config.iterations())
            )).isTrue();
            assertThat(log.containsMessage(
                TestLog.INFO,
                formatWithLocale("place nodes randomly %s of %s :: Finished", i, config.iterations())
            )).isTrue();

            if (vnsMaxNeighborhoodOrder == 0) {
                assertThat(log.containsMessage(
                    TestLog.INFO,
                    formatWithLocale("local search %s of %s :: Start", i, config.iterations())
                )).isTrue();
                assertThat(log.containsMessage(
                    TestLog.INFO,
                    formatWithLocale("local search %s of %s :: Finished", i, config.iterations())
                )).isTrue();
                assertThat(log.containsMessage(
                    TestLog.INFO,
                    formatWithLocale("local search %s of %s :: improvement loop :: Start", i, config.iterations())
                )).isTrue();
                assertThat(log.containsMessage(
                    TestLog.INFO,
                    formatWithLocale("local search %s of %s :: improvement loop :: Finished", i, config.iterations())
                )).isTrue();

                // May occur several times but we don't know.
                assertThat(log.containsMessage(
                    TestLog.INFO,
                    formatWithLocale(
                        "local search %s of %s :: improvement loop :: compute node to community weights 1 :: Start",
                        i,
                        config.iterations()
                    )
                )).isTrue();
                assertThat(log.containsMessage(
                    TestLog.INFO,
                    formatWithLocale(
                        "local search %s of %s :: improvement loop :: compute node to community weights 1 100%%",
                        i,
                        config.iterations()
                    )
                )).isTrue();
                assertThat(log.containsMessage(
                    TestLog.INFO,
                    formatWithLocale(
                        "local search %s of %s :: improvement loop :: compute node to community weights 1 :: Finished",
                        i,
                        config.iterations()
                    )
                )).isTrue();
                assertThat(log.containsMessage(
                    TestLog.INFO,
                    formatWithLocale(
                        "local search %s of %s :: improvement loop :: swap for local improvements 1 :: Start",
                        i,
                        config.iterations()
                    )
                )).isTrue();
                assertThat(log.containsMessage(
                    TestLog.INFO,
                    formatWithLocale(
                        "local search %s of %s :: improvement loop :: swap for local improvements 1 100%%",
                        i,
                        config.iterations()
                    )
                )).isTrue();
                assertThat(log.containsMessage(
                    TestLog.INFO,
                    formatWithLocale(
                        "local search %s of %s :: improvement loop :: swap for local improvements 1 :: Finished",
                        i,
                        config.iterations()
                    )
                )).isTrue();

                assertThat(log.containsMessage(
                    TestLog.INFO,
                    formatWithLocale(
                        "local search %s of %s :: compute current solution cost :: Start",
                        i,
                        config.iterations()
                    )
                )).isTrue();
                assertThat(log.containsMessage(
                    TestLog.INFO,
                    formatWithLocale(
                        "local search %s of %s :: compute current solution cost 100%%",
                        i,
                        config.iterations()
                    )
                )).isTrue();
                assertThat(log.containsMessage(
                    TestLog.INFO,
                    formatWithLocale(
                        "local search %s of %s :: compute current solution cost :: Finished",
                        i,
                        config.iterations()
                    )
                )).isTrue();
            } else {
                // We merely check that VNS is indeed run. The rest is very similar to the non-VNS case.
                assertThat(log.containsMessage(
                    TestLog.INFO,
                    formatWithLocale("variable neighborhood search %s of %s :: Start", i, config.iterations())
                )).isTrue();
                assertThat(log.containsMessage(
                    TestLog.INFO,
                    formatWithLocale("variable neighborhood search %s of %s :: Finished", i, config.iterations())
                )).isTrue();
            }
        }
    }

}

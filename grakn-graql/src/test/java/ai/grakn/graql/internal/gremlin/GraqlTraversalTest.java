/*
 * Grakn - A Distributed Semantic Database
 * Copyright (C) 2016  Grakn Labs Limited
 *
 * Grakn is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Grakn is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Grakn. If not, see <http://www.gnu.org/licenses/gpl.txt>.
 */

package ai.grakn.graql.internal.gremlin;

import ai.grakn.concept.ConceptId;
import ai.grakn.graql.Pattern;
import ai.grakn.graql.Var;
import ai.grakn.graql.VarName;
import ai.grakn.graql.admin.Conjunction;
import ai.grakn.graql.admin.VarAdmin;
import ai.grakn.graql.internal.gremlin.fragment.Fragment;
import ai.grakn.graql.internal.pattern.Patterns;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.junit.Test;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static ai.grakn.graql.Graql.eq;
import static ai.grakn.graql.Graql.or;
import static ai.grakn.graql.Graql.var;
import static ai.grakn.graql.internal.gremlin.fragment.Fragments.distinctCasting;
import static ai.grakn.graql.internal.gremlin.fragment.Fragments.id;
import static ai.grakn.graql.internal.gremlin.fragment.Fragments.inCasting;
import static ai.grakn.graql.internal.gremlin.fragment.Fragments.inHasRole;
import static ai.grakn.graql.internal.gremlin.fragment.Fragments.inIsa;
import static ai.grakn.graql.internal.gremlin.fragment.Fragments.inRolePlayer;
import static ai.grakn.graql.internal.gremlin.fragment.Fragments.name;
import static ai.grakn.graql.internal.gremlin.fragment.Fragments.outCasting;
import static ai.grakn.graql.internal.gremlin.fragment.Fragments.outHasRole;
import static ai.grakn.graql.internal.gremlin.fragment.Fragments.outIsa;
import static ai.grakn.graql.internal.gremlin.fragment.Fragments.outRolePlayer;
import static ai.grakn.graql.internal.gremlin.fragment.Fragments.shortcut;
import static ai.grakn.graql.internal.gremlin.fragment.Fragments.value;
import static ai.grakn.graql.internal.pattern.Patterns.varName;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class GraqlTraversalTest {

    private static final VarName x = varName("x");
    private static final VarName y = varName("y");
    private static final VarName z = varName("z");
    private static final Fragment xId = id(x, ConceptId.of("Titanic"));
    private static final Fragment xValue = value(x, eq("hello").admin());
    private static final Fragment yId = id(y, ConceptId.of("movie"));
    private static final Fragment xIsaY = outIsa(x, y);
    private static final Fragment yTypeOfX = inIsa(y, x);
    private static final Fragment xShortcutY = shortcut(Optional.empty(), Optional.empty(), Optional.empty(), x, y);
    private static final Fragment yShortcutX = shortcut(Optional.empty(), Optional.empty(), Optional.empty(), y, x);

    private static final GraqlTraversal fastIsaTraversal = traversal(yId, yTypeOfX);

    @Test
    public void testComplexityIndexVsIsa() {
        GraqlTraversal indexTraversal = traversal(xId);
        assertFaster(indexTraversal, fastIsaTraversal);
    }

    @Test
    public void testComplexityFastIsaVsSlowIsa() {
        GraqlTraversal slowIsaTraversal = traversal(xIsaY, yId);
        assertFaster(fastIsaTraversal, slowIsaTraversal);
    }

    @Test
    public void testComplexityConnectedVsDisconnected() {
        GraqlTraversal connectedDoubleIsa = traversal(xIsaY, outIsa(y, z));
        GraqlTraversal disconnectedDoubleIsa = traversal(xIsaY, inIsa(z, y));
        assertFaster(connectedDoubleIsa, disconnectedDoubleIsa);
    }

    @Test
    public void testGloballyOptimalIsFasterThanLocallyOptimal() {
        GraqlTraversal locallyOptimalSpecificInstance = traversal(yId, yTypeOfX, xId);
        GraqlTraversal globallyOptimalSpecificInstance = traversal(xId, xIsaY, yId);
        assertFaster(globallyOptimalSpecificInstance, locallyOptimalSpecificInstance);
    }

    @Test
    public void testHasRoleFasterFromRoleType() {
        GraqlTraversal hasRoleFromRelationType = traversal(yId, outHasRole(y, x), xId);
        GraqlTraversal hasRoleFromRoleType = traversal(xId, inHasRole(x, y), yId);
        assertFaster(hasRoleFromRoleType, hasRoleFromRelationType);
    }

    @Test
    public void testResourceWithTypeFasterFromType() {
        GraqlTraversal fromInstance =
                traversal(outIsa(x, x), id(x, ConceptId.of("_")), makeShortcut(x, y), outIsa(y, y), id(y, ConceptId.of("_")));
        GraqlTraversal fromType =
                traversal(id(x, ConceptId.of("_")), inIsa(x, x), makeShortcut(x, y), outIsa(y, y), id(y, ConceptId.of("_")));
        assertFaster(fromType, fromInstance);
    }

    @Test
    public void testCheckDistinctCastingEarlyFaster() {
        VarName c1 = varName("c1");
        VarName c2 = varName("c2");
        VarName r = varName("r");

        Fragment distinctCasting = distinctCasting(c2, c1);
        Fragment inRolePlayer = inRolePlayer(x, c1);
        Fragment inCasting = inCasting(c1, r);
        Fragment outCasting = outCasting(r, c2);
        Fragment outRolePlayer = outRolePlayer(c2, y);

        GraqlTraversal distinctEarly =
                traversal(xId, inRolePlayer, inCasting, outCasting, distinctCasting, outRolePlayer);
        GraqlTraversal distinctLate =
                traversal(xId, inRolePlayer, inCasting, outCasting, outRolePlayer, distinctCasting);

        assertFaster(distinctEarly, distinctLate);
    }

    @Test
    public void testAllTraversalsSimpleQuery() {
        Var pattern = Patterns.var(x).id(ConceptId.of("Titanic")).isa(Patterns.var(y).id(ConceptId.of("movie")));
        Set<GraqlTraversal> traversals = allGraqlTraversals(pattern).collect(toSet());

        assertEquals(12, traversals.size());

        Set<GraqlTraversal> expected = ImmutableSet.of(
                traversal(xId, xIsaY, yId),
                traversal(xId, yTypeOfX, yId),
                traversal(xId, yId, xIsaY),
                traversal(xId, yId, yTypeOfX),
                traversal(xIsaY, xId, yId),
                traversal(xIsaY, yId, xId),
                traversal(yTypeOfX, xId, yId),
                traversal(yTypeOfX, yId, xId),
                traversal(yId, xId, xIsaY),
                traversal(yId, xId, yTypeOfX),
                traversal(yId, xIsaY, xId),
                traversal(yId, yTypeOfX, xId)
        );

        assertEquals(expected, traversals);
    }

    @Test
    public void testAllTraversalsDisjunction() {
        Pattern pattern = or(Patterns.var(x).id(ConceptId.of("Titanic")).value("hello"), Patterns.var().rel("x").rel("y"));
        Set<GraqlTraversal> traversals = allGraqlTraversals(pattern).collect(toSet());

        // Expect all combinations of both disjunctions
        Set<GraqlTraversal> expected = ImmutableSet.of(
                traversal(ImmutableList.of(xId, xValue), ImmutableList.of(xShortcutY)),
                traversal(ImmutableList.of(xId, xValue), ImmutableList.of(yShortcutX)),
                traversal(ImmutableList.of(xValue, xId), ImmutableList.of(xShortcutY)),
                traversal(ImmutableList.of(xValue, xId), ImmutableList.of(yShortcutX))
        );

        assertEquals(expected, traversals);
    }

    @Test
    public void testOptimalShortQuery() {
        assertNearlyOptimal(var(x).isa(var(y).id(ConceptId.of("movie"))));
    }

    @Test
    public void testOptimalBothId() {
        assertNearlyOptimal(var(x).id(ConceptId.of("Titanic")).isa(var(y).id(ConceptId.of("movie"))));
    }

    @Test
    public void testOptimalByValue() {
        assertNearlyOptimal(var(x).value("hello").isa(var(y).id(ConceptId.of("movie"))));
    }

    @Test
    public void testOptimalAttachedResource() {
        assertNearlyOptimal(var()
                .rel(var(x).isa(var(y).id(ConceptId.of("movie"))))
                .rel(var(z).value("Titanic").isa(var("a").id(ConceptId.of("title")))));
    }

    @Test
    public void testShortcutOptimisationPlain() {
        Pattern rel = var().rel("x").rel("y");

        GraqlTraversal graqlTraversal = semiOptimal(rel);

        assertThat(graqlTraversal, anyOf(
                is(traversal(xShortcutY)),
                is(traversal(yShortcutX))
        ));
    }

    @Test
    public void testShortcutOptimisationWithType() {
        VarName marriageName = Patterns.varName("m");

        Var marriage = var(marriageName).name("marriage");

        Var rel = var().rel("x").rel("y").isa(marriage);

        GraqlTraversal graqlTraversal = semiOptimal(rel);

        Fragment xMarriesY = shortcut(Optional.of("marriage"), Optional.empty(), Optional.empty(), x, y);
        Fragment yMarriesX = shortcut(Optional.of("marriage"), Optional.empty(), Optional.empty(), y, x);
        Fragment marriageFragment = name(marriageName, "marriage");

        assertThat(graqlTraversal, anyOf(
                is(traversal(xMarriesY, marriageFragment)),
                is(traversal(yMarriesX, marriageFragment)),
                is(traversal(marriageFragment, xMarriesY)),
                is(traversal(marriageFragment, yMarriesX))
        ));
    }

    @Test
    public void testShortcutOptimisationWithRoles() {
        VarName wifeName = Patterns.varName("w");

        Var wife = var(wifeName).name("wife");

        Var rel = var().rel("x").rel(wife, "y");

        GraqlTraversal graqlTraversal = semiOptimal(rel);

        Fragment xMarriesY = shortcut(Optional.empty(), Optional.empty(), Optional.of("wife"), x, y);
        Fragment yMarriesX = shortcut(Optional.empty(), Optional.of("wife"), Optional.empty(), y, x);
        Fragment wifeFragment = name(wifeName, "wife");

        assertThat(graqlTraversal, anyOf(
                is(traversal(xMarriesY, wifeFragment)),
                is(traversal(yMarriesX, wifeFragment)),
                is(traversal(wifeFragment, xMarriesY)),
                is(traversal(wifeFragment, yMarriesX))
        ));
    }

    private static GraqlTraversal semiOptimal(Pattern pattern) {
        return GraqlTraversal.semiOptimal(pattern.admin());
    }

    private static GraqlTraversal traversal(Fragment... fragments) {
        return traversal(ImmutableList.copyOf(fragments));
    }

    @SafeVarargs
    private static GraqlTraversal traversal(ImmutableList<Fragment>... fragments) {
        ImmutableSet<ImmutableList<Fragment>> fragmentsSet = ImmutableSet.copyOf(fragments);
        return GraqlTraversal.create(fragmentsSet);
    }

    private static Stream<GraqlTraversal> allGraqlTraversals(Pattern pattern) {
        Collection<Conjunction<VarAdmin>> patterns = pattern.admin().getDisjunctiveNormalForm().getPatterns();

        List<Set<List<Fragment>>> collect = patterns.stream()
                .map(ConjunctionQuery::new)
                .map(ConjunctionQuery::allFragmentOrders)
                .collect(toList());

        Set<List<List<Fragment>>> lists = Sets.cartesianProduct(collect);

        return lists.stream().map(list -> GraqlTraversal.create(Sets.newHashSet(list)));
    }

    private static Fragment makeShortcut(VarName x, VarName y) {
        return shortcut(Optional.empty(), Optional.empty(), Optional.empty(), x, y);
    }

    private static void assertNearlyOptimal(Pattern pattern) {
        GraqlTraversal traversal = semiOptimal(pattern);

        //noinspection OptionalGetWithoutIsPresent
        GraqlTraversal globalOptimum = allGraqlTraversals(pattern).min(comparing(GraqlTraversal::getComplexity)).get();

        double globalComplexity = globalOptimum.getComplexity();
        double complexity = traversal.getComplexity();

        assertTrue(
                "Expected\n " +
                        complexity + ":\t" + traversal + "\nto be similar speed to\n " +
                        globalComplexity + ":\t" + globalOptimum,
                complexity < globalComplexity * 2
        );
    }

    private static void assertFaster(GraqlTraversal fast, GraqlTraversal slow) {
        double fastComplexity = fast.getComplexity();
        double slowComplexity = slow.getComplexity();
        boolean condition = fastComplexity < slowComplexity;

        assertTrue(
                "Expected\n" + fastComplexity + ":\t" + fast + "\nto be faster than\n" + slowComplexity + ":\t" + slow,
                condition
        );
    }
}

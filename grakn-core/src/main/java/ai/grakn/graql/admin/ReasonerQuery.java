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

package ai.grakn.graql.admin;

import ai.grakn.GraknGraph;
import ai.grakn.concept.Concept;
import ai.grakn.concept.Type;
import ai.grakn.graql.VarName;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 *
 * <p>
 * Interface for conjunctive reasoner queries.
 * </p>
 *
 * @author Kasper Piskorski
 *
 */
public interface ReasonerQuery{
    /**
     * @return GraknGraph associated with this reasoner query
     */
    GraknGraph graph();

    /**
     * @return conjunctive pattern corresponding to this reasoner query
     */
    Conjunction<PatternAdmin> getPattern();

    /**
     * @return set of select variables of this reasoner query
     */
    Set<VarName> getSelectedNames();

    /**
     * @return atom set constituting this reasoner query
     */
    Set<Atomic> getAtoms();

    /**
     * @return true if any of the atoms constituting the query can be resolved through a rule
     */
    boolean isRuleResolvable();

    /**
     * change each variable occurrence in the query (apply unifier [from/to])
     * @param from variable name to be changed
     * @param to new variable name
     */
    void unify(VarName from, VarName to);

    /**
     * change each variable occurrence according to provided mappings (apply unifiers {[from, to]_i})
     * @param unifiers contain unifiers (variable mappings) to be applied
     */
    void unify(Map<VarName, VarName> unifiers);

    /**
     * resolves the query
     * @param materialise materialisation flag
     * @return stream of answers
     */
    Stream<Map<VarName, Concept>> resolve(boolean materialise);

    /**
     * @return map of variable name - corresponding type pairs
     */
    Map<VarName, Type> getVarTypeMap();
}

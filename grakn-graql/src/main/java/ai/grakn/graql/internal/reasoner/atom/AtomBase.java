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

package ai.grakn.graql.internal.reasoner.atom;

import ai.grakn.GraknGraph;
import ai.grakn.graql.VarName;
import ai.grakn.graql.admin.Atomic;
import ai.grakn.graql.admin.PatternAdmin;
import ai.grakn.graql.admin.ReasonerQuery;
import ai.grakn.graql.admin.VarAdmin;
import ai.grakn.graql.internal.pattern.Patterns;
import ai.grakn.util.ErrorMessage;
import com.google.common.collect.Sets;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static ai.grakn.graql.internal.reasoner.Utility.capture;


/**
 *
 * <p>
 * Base atom implementation providing basic functionalities.
 * </p>
 *
 * @author Kasper Piskorski
 *
 */
public abstract class AtomBase implements Atomic {

    protected VarName varName = null;
    protected PatternAdmin atomPattern = null;
    private ReasonerQuery parent = null;

    protected AtomBase(VarAdmin pattern, ReasonerQuery par) {
        this.atomPattern = pattern;
        this.varName = pattern.getVarName();
        this.parent = par;
    }

    protected AtomBase(AtomBase a) {
        this.atomPattern = Patterns.copyOf(a.atomPattern.asVar());
        this.varName = atomPattern.asVar().getVarName();
    }

    @Override
    public abstract Atomic clone();

    @Override
    public String toString(){ return atomPattern.toString(); }

    @Override
    public boolean containsVar(VarName name){ return getVarNames().contains(name);}

    @Override
    public boolean isUserDefinedName(){ return atomPattern.asVar().isUserDefinedName();}

    @Override
    public VarName getVarName(){ return varName;}

    @Override
    public Set<VarName> getVarNames(){
        return Sets.newHashSet(varName);
    }

    public Set<VarName> getSelectedNames(){
        Set<VarName> vars = getParentQuery().getSelectedNames();
        vars.retainAll(getVarNames());
        return vars;
    }

    public void resetNames(){
        Map<VarName, VarName> unifiers = new HashMap<>();
        getVarNames().forEach(var -> unifiers.put(var, Patterns.varName()));
        unify(unifiers);
    }

    /**
     * @return true if the value variable is user defined
     */
    public boolean isValueUserDefinedName(){ return false;}

    /**
     * @return pattern corresponding to this atom
     */
    public PatternAdmin getPattern(){ return atomPattern;}
    public PatternAdmin getCombinedPattern(){ return getPattern();}

    /**
     * @return the query the atom is contained in
     */
    public ReasonerQuery getParentQuery(){ return parent;}

    /**
     * @param q query this atom is supposed to belong to
     */
    public void setParentQuery(ReasonerQuery q){ parent = q;}
    public GraknGraph graph(){ return getParentQuery().graph();}

    private void setVarName(VarName var){
        varName = var;
        atomPattern.asVar().setVarName(var);
    }

    /**
     * perform unification on the atom by applying unifiers
     * @param unifiers contain variable mappings to be applied
     */
    public void unify(Map<VarName, VarName> unifiers){
        VarName var = getVarName();
        if (unifiers.containsKey(var)) {
            setVarName(unifiers.get(var));
        } else if (unifiers.containsValue(var)) {
            setVarName(capture(var));
        }
    }

    /**
     * get unifiers by comparing this atom with parent
     * @param parentAtom atom defining variable names
     * @return map of unifiers
     */
    public Map<VarName, VarName> getUnifiers(Atomic parentAtom) {
        if (parentAtom.getClass() != this.getClass()) {
            throw new IllegalArgumentException(ErrorMessage.UNIFICATION_ATOM_INCOMPATIBILITY.getMessage());
        }
        Map<VarName, VarName> map = new HashMap<>();
        if (!this.getVarName().equals(parentAtom.getVarName())) {
            map.put(this.getVarName(), parentAtom.getVarName());
        }
        return map;
    }
}


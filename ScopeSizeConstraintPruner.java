package edu.jhu.maxent_classifier;

import edu.jhu.features.*;
import edu.jhu.fsm.*;
import edu.jhu.maxent.*;

import java.util.*;

public class ScopeSizeConstraintPruner implements ConstraintPruner
{

NaturalClass[] naturalClasses = null;

public ScopeSizeConstraintPruner(NaturalClass[] naturalClasses) { 
    this.naturalClasses = naturalClasses;
}

public void prune(List<ConTrieNode> L) {
    /*
    // straightforward sequential algorithm from:
    //      Leiserson, Charles E., et al. "Parallel computation of the minimal elements of a poset."
    //      Proceedings of the 4th International Workshop on Parallel and Symbolic Computation. ACM, 2010.
    ConTrieNode nodei = null;
    ConTrieNode nodej = null;
    int comp = 0;
    int len = L.size();
    i: for (int i=0; i<(len-1); i++) {
        nodei = L.get(i);
        if (nodei.blocked)
            continue;
        j: for (int j=i; j<len; j++) {
            nodej = L.get(j);
            if (nodej.blocked)
                continue;
            comp = compareScopes(nodei, nodej);
            if (comp == -1) { // scope(nodei) > scope(nodej)
                nodej.blocked = true;
            }
            if (comp == +1) { // scope(nodej) > scope(nodei)
                nodei.blocked = true;
                continue i;
            }
        }
    }
    */
    // linear algorithm (possible because scope size induces a total order)
    ConTrieNode nodei   = null;
    double scope        = 0.0;
    double maxScope     = 0.0;
    for (Iterator<ConTrieNode> iter = L.iterator(); iter.hasNext(); ) {
        nodei = iter.next();
        scope = scopeSize(nodei);
        if (scope>maxScope)
            maxScope = scope;
        if (scope<maxScope)
            nodei.blocked = true;
    }
    for (Iterator<ConTrieNode> iter = L.iterator(); iter.hasNext(); ) {
        nodei = iter.next();
        if (!nodei.blocked && scopeSize(nodei)<maxScope)    // NB. retains ties
            nodei.blocked = true;
    }
}


public double scopeSize(ConTrieNode nodei) {
    double scopeSize = 1.0;
    for (int i=(nodei.depth-1); i>=0; i--) {
        scopeSize *= naturalClasses[nodei.id].size;
        nodei = (ConTrieNode) nodei.pred;
    }
    return scopeSize;
}


public int compareScopes(ConTrieNode nodei, ConTrieNode nodej) {
    ConTrieNode nodei_ = nodei;
    ConTrieNode nodej_ = nodej;
    double scopeSizei   = 1.0;
    double scopeSizej   = 1.0;
    for (int i=(nodei.depth-1); i>=0; i--) {
        scopeSizei *= naturalClasses[nodei_.id].size;
        scopeSizej *= naturalClasses[nodej_.id].size;
        nodei_ = (ConTrieNode) nodei_.pred;
        nodej_ = (ConTrieNode) nodej_.pred;
    }
    if (scopeSizei > scopeSizej)
        return -1;
    if (scopeSizei < scopeSizej)
        return +1;
    return 0;
}

}
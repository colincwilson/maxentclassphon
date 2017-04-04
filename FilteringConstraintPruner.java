package edu.jhu.maxent_classifier;

import edu.jhu.fsm.*;
import edu.jhu.features.*;
import edu.jhu.maxent.*;

import java.util.*;

public class FilteringConstraintPruner implements ConstraintPruner
{

NaturalClass[] naturalClasses = null;

public FilteringConstraintPruner(NaturalClass[] naturalClasses) { 
    this.naturalClasses = naturalClasses;
}

// prune constraints that do not satisfy the filter
// hard-coded: final class must be [+wb]
public void prune(List<ConTrieNode> L) {
    ConTrieNode nodei   = null;
    for (Iterator<ConTrieNode> iter = L.iterator(); iter.hasNext(); ) {
        nodei = iter.next();
        if (naturalClasses[nodei.id].ftrs[0]!=1)
            nodei.blocked = true;
    }
}

// return constant
public int compareScopes(ConTrieNode nodei, ConTrieNode nodej) {
    return 0;
}
}
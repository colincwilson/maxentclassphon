package edu.jhu.maxent_classifier;

import edu.jhu.features.*;
import edu.jhu.fsm.*;
import edu.jhu.maxent.*;

import java.util.*;

public class DummyConstraintPruner implements ConstraintPruner
{

NaturalClass[] naturalClasses = null;

public DummyConstraintPruner(NaturalClass[] naturalClasses) { 
    this.naturalClasses = naturalClasses;
}

// don't prune anything
public void prune(List<ConTrieNode> L) {
    /*
    ConTrieNode nodei   = null;
    for (Iterator<ConTrieNode> iter = L.iterator(); iter.hasNext(); ) {
        nodei = iter.next();
        nodei.blocked = false;
    }
    */
}

// return constant
public int compareScopes(ConTrieNode nodei, ConTrieNode nodej) {
    return 0;
}
}
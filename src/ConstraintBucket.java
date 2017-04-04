package edu.jhu.maxent_classifier;

import edu.jhu.util.*;
import edu.jhu.maxent.*;
import java.util.*;

public class ConstraintBucket
{

public LinkedList<ConTrieNode> list    = null;     // list of constraints in this bucket
public boolean pruned                   = false;    // flag indicating whether bucket is pruned


public ConstraintBucket() {
    list    = new LinkedList<ConTrieNode>();
    pruned  = false;
}


// add a constraint to this bucket
public void add(ConTrieNode X) {
    list.add(X);
    pruned = false;
}

}
package edu.jhu.maxent_classifier;

import java.io.*;
import java.util.*;
import edu.jhu.util.*;
import edu.jhu.features.*;
import edu.jhu.fsm.*;

public class GrammarFileReader
{

// fields set by read()
static ClassifierConstraint[] constraints   = null;
static double[] lambda                 = null;


public GrammarFileReader() { }


// read list of constraints of the form
// 		projection<tab>constraint<tab>weight
// todo: add support for reading binary constraints
public static void read(String grammarFile, Alphabet alphabet, Projection[] projections) {
    String[] grammar_ = null;
    try {
        grammar_ = StringFileReader.read(new File(grammarFile));
    } catch (Exception e) { System.out.println(e); System.exit(1); }
    
    LinkedList<LinkedList<String>> grammar = new LinkedList<LinkedList<String>>();
    for (String constraint_ : grammar_) {
        LinkedList<String> constraint = new LinkedList<String>(Arrays.asList(constraint_.split("\\s+")));
        grammar.add(constraint);
    }

    ArrayList<ClassifierConstraint> constraintList   = new ArrayList<ClassifierConstraint>();
    ArrayList<Double> lambdaList                = new ArrayList<Double>();
    
    for (int i=0; i<grammar.size(); i++) {
        LinkedList<String> constrainti = grammar.get(i);
        System.out.println(i +" "+ constrainti);

        // parse projection
        Projection projection = null;
        for (Projection proj : projections) {
            if (!proj.name.equals(constrainti.get(0))) continue;
            projection = proj; break;
        }
        String[] patternAttribCategoryi = constrainti.get(1).split("->");
        patternAttribCategoryi[0] = patternAttribCategoryi[0].replace("*","");
        
        String[] patternAttribi = patternAttribCategoryi[0].split("<");
        
        // parse natural-class sequence
		// todo: add support for constraints stated directly over segments
        NaturalClass[] pattern = null;
        if (patternAttribi[0].equals("*[null]")) {
            pattern = null;
        } else {
            pattern = NaturalClassUtil.fromString(projection, patternAttribi[0].split("]|}"));
        }

		// parse attribute
        String attrib = null;
        if (patternAttribi.length>1 && patternAttribi[1].contains(">")) {
            attrib = patternAttribi[1].replace(">", "");
        }
		
		// parse category
        int category = -1;
        try {
            category = Integer.parseInt(patternAttribCategoryi[1]);
        } catch (NumberFormatException e) {
            for (int j=0; j<MaxentClassifier.categoryNames.length; j++) {
                if (MaxentClassifier.categoryNames[j].equals(patternAttribCategoryi[1])) {
                    category = j;
                }
            }
        }

		// parse weight
        double weight = (constrainti.size()>2) ? Double.parseDouble(constrainti.get(2)) : 1.0;
		if (weight < 0.0) {
			System.out.println("Constraint weights must be >=0, replacing negative weight w with -w");
			weight = -weight;
		}
		
        constraintList.add(new ClassifierConstraint(projection,pattern,attrib,category));
        lambdaList.add(weight);
    }
    
    constraints = constraintList.toArray(new ClassifierConstraint[0]);
    lambda = new double[lambdaList.size()];
    for (int i=0; i<lambdaList.size(); i++) {
        lambda[i] = lambdaList.get(i);
    }
}

}

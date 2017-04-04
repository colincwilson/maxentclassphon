// Maxent classifier for morphophonology with gain-based feature selection

// todo:
//  - add complement natural classes (see Zwicky 1970)
//  - add minimum scope threshold / scope-based adjustments (see Mikheev 1997, Albright & Hayes 2003)
//  - allow multiple attributes on data points and constraints (e.g., full morphological feature specs)
package edu.jhu.maxent_classifier;

import java.io.*;
import java.util.*;
import pal.math.*;
import edu.jhu.util.*;
import edu.jhu.features.*;
import edu.jhu.fsm.*;
import edu.jhu.maxent.*;

public class MaxentClassifier implements MFWithGradient
{

static String[] categoryNames			= null;		// names of categories
static int nCategories					= 0;		// number of categories

static Alphabet alphabet				= null;		// symbols, feature names, feature matrix
static String WORD_BEGIN			 	= "<#"; 	// initial word boundary (must be in feature file)
static String WORD_END					= "#>"; 	// final word boundary (must be in feature file)

static Projection[] projection			= null;		// projections
static int nProjections                 = 0;

public static LinkedList<String> attribs = null;    // attributes (determined by inspection from training data)

public static DataPoint[] train 		= null;		// examples with classes (and weights for scaling likelihood term)
static int nDataPoints                  = 0;

public static DataPoint[] test			= null;		// input-(null) output pairs for testing
static int nTestPoints					= 0;

static ArrayList<Double> negLogProbs	= new ArrayList<Double>();	// trace of -log P(D) over the course of learning
static double negLogProb				= 0.0;		// -log P(D) given the current grammar

static LinkedList<ClassifierConstraint> constraints = new LinkedList<ClassifierConstraint>();
static double[] lambda					= null;		// constraint weight vector
static int nConstraints					= 0;		// number of constraints in the model

MultivariateMinimum optimizer			= null;
static double OPTIMIZER_MIN				= 1.0e-3;	// tolerance during learning
public static double l1					= 0.0;      // 1.0e-2;	// multiplier of l1 (lasso) regularizer [default]
public static double l2					= 1.0e-2;	// multiplier of l2 (Gaussian) regularizer [default]

static int MAX_VIOLATION				= 20;		// maximum # of violations of a constraint
                                                    // (should be max length of data incl. boundaries)

static int maxConLength                 = 3;        // maximum length of learned constraint [default]
static int maxConSize                   = 1000;     // maximum number of constraints in a grammar [default]

static double minGain					= 3.841459;	// minimum acceptable gain improvement
                                                    // (default: critical value of chi-square test on 1 df with \alpha = 0.05)
static boolean binaryConstraints        = false;	// allow constraints that do not accumulate violations

static String enc                       = "UTF-8";  // "8859_2"; // "8859_1"  // file encoding (corpus, classes, features)

/* * * * * * * * * * *
 * CONSTRUCTOR
*/
public MaxentClassifier(String[] args) {
	// process commandline arguments
	CommandlineParser commandline = new CommandlineParser();
    commandline.addOption("categories",     true,   "file containing category names");
	commandline.addOption("features",       true,	"file containing feature matrix");
    commandline.addOption("projections",    true,   "file containing projection descriptions");
	commandline.addOption("data",           true,	"	file containing training examples with correct categories (and optional weights)");
	commandline.addOption("grammar",        true,	"file containing constraints and weights");
	commandline.addOption("train",          false,	"	retrain weights of a grammar (prior to further learning or testing)");
	commandline.addOption("test",           true,	"	file containing testing forms");
	commandline.addOption("learn",          false,	"	learn constraints and weights");
	commandline.addOption("l1",             true,	"	coefficient of l1 (lasso) regularizer [default = "+ l1 +"]");
	commandline.addOption("l2",             true,	"	coefficient of l2 (Gaussian) regularizer [default = "+ l2 +"]");
	commandline.addOption("maxConLength",	true,	"maximum length of constraint [default = "+ maxConLength +"]");
    commandline.addOption("maxConSize",     true,   "maximum number of constraints [default = "+ maxConSize +"]");
    commandline.addOption("minGain",        true,   "minimum (estimated) gain for new constraint [default = "+ minGain +"]");
    commandline.addOption("binaryConstraints", false, "consider binary constraints during learning [default = "+ binaryConstraints +"]");
	try {
		String[] argv = SimpleFileReader.read(args[0]);
		System.out.println("args="+ java.util.Arrays.toString(argv));
		commandline.parse(argv);
	} catch (Exception e) {
        System.out.println(commandline);
        System.exit(1);
    }
	
    // learning parameters
    if (commandline.hasOption("maxConLength")) maxConLength = Integer.parseInt(commandline.getOptionValue("maxConLength"));
    if (commandline.hasOption("maxConSize")) maxConSize = Integer.parseInt(commandline.getOptionValue("maxConSize"));
	l1 = (commandline.hasOption("l1")) ? Double.parseDouble(commandline.getOptionValue("l1")) : 1.0;
	l2 = (commandline.hasOption("l2")) ? Double.parseDouble(commandline.getOptionValue("l2")) : 1.0;
    if (commandline.hasOption("minGain")) minGain = Double.parseDouble(commandline.getOptionValue("minGain"));
    if (commandline.hasOption("binaryConstraints")) binaryConstraints = true;
    
    // classes
    if (commandline.hasOption("categories")) {
        try {
            categoryNames = SimpleFileReader.read(commandline.getOptionValue("categories"));
        } catch (Exception e) { System.out.println(e); }
        nCategories = categoryNames.length;
        System.out.println("categories: "+ Arrays.toString(categoryNames));
    } else {
        System.out.println("Parameters must specify file containing category names");
        System.exit(1);
    }

	// segments and features
	if (commandline.hasOption("features")) {
		try { 
			alphabet = FeatureMatrixReader.apply(commandline.getOptionValue("features"));
		} catch (Exception e) { System.out.println(e); }
        alphabet.syms.setWordBegin(WORD_BEGIN);
        alphabet.syms.setWordEnd(WORD_END);
	} else {
        System.out.println("Parameters must specify file containing features specifications");
        System.exit(1);
    }
	System.out.println("number of segments "+ alphabet.nSegments);
    
    // projections
    if (!commandline.hasOption("projections")) {
        projection = new Projection[] { new Projection(alphabet,false).setMaxConLength(maxConLength) };
    } else {
        LinkedList<Projection> projectionList = new LinkedList<Projection>();
        // xxx always add default projection:
        // xxx projectionList.add( new Projection(alphabet,false).setMaxConLength(maxConLength) );
        try {
            LinkedList<LinkedList<String>> projs = DelimitedFileReader.apply(commandline.getOptionValue("projections"));
            for (int i=0; i<projs.size(); i++) {
                projectionList.add( new Projection(alphabet,projs.get(i),false) );
            }
        } catch (Exception e) { System.out.println(e); System.exit(1); }
		projection = projectionList.toArray(new Projection[0]);
    }
    nProjections = projection.length;
    
    // data points
	if (commandline.hasOption("data")) {
        DataFileReader.read(commandline.getOptionValue("data"), alphabet, WORD_BEGIN, WORD_END);
		train        = DataFileReader.data;
        nDataPoints = train.length;
		System.out.println("number of training examples: "+ nDataPoints);
        for (DataPoint data_i : train)
            data_i.init(nCategories);
	}
	
	// test points
	if (commandline.hasOption("test")) {
        DataFileReader.read(commandline.getOptionValue("test"), alphabet, WORD_BEGIN, WORD_END);
        test        = DataFileReader.data;
        nTestPoints = test.length;
        for (DataPoint test_i : test)
            test_i.init(nCategories);
	}
    
    // attributes in the data set
    HashSet<String> attribSet = new HashSet<String>();
	if (commandline.hasOption("train")) {
		for (DataPoint xi : train) {
			if (xi.attrib!=null) attribSet.add(xi.attrib);
		}
	}
	if (commandline.hasOption("test")) {
		for (DataPoint xi : test) {
			if (xi.attrib!=null) attribSet.add(xi.attrib);
		}
	}
    attribs = new LinkedList<String>(attribSet);
    System.out.println("attributes found in the data: "+ ((attribs.size()==0) ? "none" : attribs));
    attribs.addFirst(null);
    
    // preselected constraints and weights
	if (commandline.hasOption("grammar")) {
        GrammarFileReader.read(commandline.getOptionValue("grammar"), alphabet, projection);
        for (int i=0; i<GrammarFileReader.constraints.length; i++)
            addConstraint(GrammarFileReader.constraints[i], GrammarFileReader.lambda[i], false);
    }
	
	// optimizer for weight training with gradient
	optimizer = new ConjugateGradientSearch(2);			// 0 -> Fletcher-Reeves, 1 -> Polak-Ribiere, 2 -> Beale-Sorenson, 3 -> Hestenes-Stiefel
	((ConjugateGradientSearch) optimizer).defaultStep   = 0.50;
	optimizer.maxFun                                    = 500;
	optimizer.numFuncStops                              = 500;	// pal default is 4
	((ConjugateGradientSearch) optimizer).prin          = 0;

	// learn grammar
	if (commandline.hasOption("learn")) {
		learn();
	}
	
	// train weights
	if (commandline.hasOption("train")) {
		trainWeights();
        writeGrammar("grammar_out.txt");
        misclassified();
        double negLogProb = -dataLogProb();
        System.out.println("-log P(D) = " + negLogProb);
	}
    
    // test on training data
    test("train");
    
	// test
	if (commandline.hasOption("test")) {
		test("test");
	}
}


/* * * * * * * * * * *
 * LEARNING
 */
 // learn constraints and weights
ClassifierConstraintGainEvaluator gainCalculator = new ClassifierConstraintGainEvaluator(l1,l2);
ClassifierConstraint bestConstraints[]           = null;
ClassifierConstraint bestConstraint              = null;
double[] bestGains                          = null;
double bestGain                             = minGain;
int maxLen                                  = 1;

public void learn() {
    // make observed segment sequence trie on each projection
    for (int i=0; i<nProjections; i++) {
        IntTrie segSeqTrie_i = new IntTrie(projection[i].maxConLength+10);
        for (int j=0; j<nDataPoints; j++) {
            projection[i].project(train[j].x);
            segSeqTrie_i.update(projection[i].projectedForm, projection[i].projectedLength, j, 1);
        }
        projection[i].corpusIndexer = segSeqTrie_i;
        projection[i].sampleIndexer = null;
    }

    // make pruned constraint trie on each projection
    // note: DummyConstraintPruner applies no pruning
    ExtendingConstraintEnumerator enumerator = new ExtendingConstraintEnumerator();
    for (int i=(nProjections-1); i>=0; i--) {
        System.out.println("pruning constraints on projection "+ projection[i].name);
        projection[i].conTrie = enumerator.apply(
            projection[i].getAlphabet(),
            projection[i].naturalClasses.naturalClasses,
            projection[i].corpusIndexer,
            (false) ? new ScopeSizeConstraintPruner(projection[i].naturalClasses.naturalClasses) 
            		: new DummyConstraintPruner(projection[i].naturalClasses.naturalClasses),
            projection[i].maxConLength   );
    }

    dataLogProb();
    printGrammar();
    
    ClassifierConstraintGainEvaluator eval = new ClassifierConstraintGainEvaluator(l1,l2);

    GainBasedConstraintSearch[] search = new GainBasedConstraintSearch[nProjections];
    for (int i=0; i<nProjections; i++) {
        search[i] = new GainBasedConstraintSearch(this, projection[i], eval, nCategories);
    }
	
    // iteratively learn constraints
    int[] maxConLength = new int[nProjections];
    Arrays.fill(maxConLength,1);
    boolean continueSelection = true;
    bestConstraints = new ClassifierConstraint[nProjections];
    bestGains = new double[nProjections];
    
	while (nConstraints<maxConSize && continueSelection) {
        // reset search results
        Arrays.fill(bestConstraints, null);
        Arrays.fill(bestGains, 0.0);
        continueSelection = false;

        // find the best constraint on each project
        for (int i=(nProjections-1); i>=0; i--) {
            if (maxConLength[i]>projection[i].maxConLength)
                continue;
			continueSelection = true;
			
            System.out.println("Searching for constraints on projection "+ projection[i].name
                + " (max. constraint length = "+ maxConLength[i] +")");
            bestConstraint = search[i].apply(maxConLength[i], attribs);
            if (bestConstraint!=null && search[i].bestGain > bestGains[i]) {
                bestConstraints[i] = bestConstraint;
                bestGains[i] = search[i].bestGain;
            } else {
                maxConLength[i]++;
            }
        }
        
        // find the best constraint across all projections
        int bestConstraintIndx = -1;
        double bestGain = 0.0;
        for (int i=(nProjections-1); i>=0; i--) {
            if (bestGains[i] > bestGain) {
                bestGain = bestGains[i];
                bestConstraintIndx = i;
            }
        }
        
        // add the new constraint (if any)
        if (bestConstraintIndx != -1) {
        	addConstraint(bestConstraints[bestConstraintIndx], 1.0, true);
            for (int i=(nProjections-1); i>=0; i--) {
                if (maxConLength[i]>projection[i].maxConLength)
                    maxConLength[i] -= 1;
            }
        }
    }
    
    // write learned grammar to screen and file
    System.out.println("\nLearned grammar (-logP(D) = "+ -dataLogProb() +")");
    writeGrammar("grammar_out.csv");
	System.out.println("\ntrain -logP(D) trace ="+ negLogProbs);
	
    // report predictions for training forms
    misclassified();
}


// add constraint to this classifier with the given initial weight, 
// and if desired train the weights of all constraints
public void addConstraint(ClassifierConstraint C, double initialWeight, boolean trainWeights) {
    if (C==null) return;

	// add the constraint
	C.id = nConstraints;
	constraints.add(C);
	lambda = (lambda==null) ? new double[1] : Arrays.copyOf(lambda,nConstraints+1);
	lambda[nConstraints] = initialWeight;
	nConstraints++;

	System.out.println("Adding constraint: "+ C +" with initial weight "+ initialWeight);

	// update violation vectors of all data points
	for (DataPoint data_i : train) {
		data_i.addViolation(C);
		//System.out.print(data_i.violns.get(C.id));
	}
    
    // update violation vectors of all test points
    if (test!=null) {
        for (DataPoint test_i : test) {
            test_i.addViolation(C);
        }
    }
	
	// retrain weights of all constraints if requested
	if (trainWeights) {
		trainWeights();
	}
	
	// report the current grammar and the total data log prob
	printGrammar();
	negLogProbs.add(-dataLogProb());
}


// print the current grammar and logP(D)
public void printGrammar() {
    if (nConstraints==0) return;
	System.out.println();
	System.out.println("Current grammar (-logP(D) = "+ -dataLogProb() +")");
	for (int i=0; i<nConstraints; i++) {
		System.out.println(constraints.get(i) +"\t"+ lambda[i] +"\t"+ constraints.get(i).toExtentString());
	}
	System.out.println();
}


// write the current grammar to a file
// xxx scope and violn calculations should be delegated to constraint class
public void writeGrammar(String filename) {
    LinkedList<String> grammarString = new LinkedList<String>();
    ClassifierConstraint C = null;
    double empScopeSize = 0.0;  // number of forms that match the constraint's pattern (at least once) and class
    double empViolnSize = 0.0;  // number of forms in the scope that violate the constraint
    int eval = 0;
    for (int i=0; i<nConstraints; i++) {
        C = constraints.get(i);
        empScopeSize = 0;
        empViolnSize = 0;
        for (DataPoint x : train) {
            eval = C.evaluate(x);
            if (eval==0) continue;
            empScopeSize += x.weight;
            if (x.category!=C.category) continue;
            empViolnSize += x.weight;
        }
        String grammarStringi = new String(C +"\t"+ lambda[i] +"\t"+ empScopeSize +"\t"+ empViolnSize +"\t"+ C.toExtentString());
        System.out.println(grammarStringi);
        grammarString.add(grammarStringi);
    }
    SimpleFileWriter writer = new SimpleFileWriter(filename, true);
    writer.write(grammarString);
}


/* * * * * * * * * * *
 * WEIGHT OPTIMIZATION
 */
public void trainWeights() {
    System.out.println("Training weights ...");
	double[] w = new double[nConstraints];
	System.arraycopy(lambda,0,w,0,nConstraints);    // start from previous weights; wrong way: Arrays.fill(w,-1.0);
    optimizer.optimize(this, w, OPTIMIZER_MIN, OPTIMIZER_MIN, null);

	System.arraycopy(w,0,lambda,0,nConstraints);
	//System.out.println(Arrays.toString(lambda));	// report result of training weights
}


/* * * * * * * * * * *
 * TRAINING DATA LOG PROBABILITY
 */
public double dataLogProb() {
	double logProb      = 0.0;
	double logProb_i    = 0.0;
    DataPoint data_i    = null;
    for (int i=0; i<nDataPoints; i++) {
        data_i = train[i];

		data_i.pCategory(constraints,lambda);
		logProb += data_i.weight*data_i.logpcat[data_i.category];
    }
	return logProb;
}


/* * * * * * * * * * *
 * CLASSIFIER EVALUATION
 */
public void misclassified() {
	System.out.println("\nPredictions for training examples");
	double nMisclassified   = 0.0;
    double nTotal           = 0.0;
	double threshold        = 1.0 / (double) nCategories;
    System.out.print("form,category,weight");
    for (int k=0; k<nCategories; k++) {
        System.out.print(","+ categoryNames[k]);
    }
    System.out.println(","+ "status");
    dataLogProb();
    int     cat_i       = -1;
    double  pmax_i      = 0.0;
    boolean correct_i   = true;
    for (DataPoint data_i : train) {
        System.out.print(data_i);
        cat_i       = data_i.category;
        pmax_i      = data_i.pcat[cat_i];
        correct_i   = true;
        for (int k=0; k<nCategories; k++) {
            System.out.print(","+ data_i.pcat[k]);
            if (k != cat_i && data_i.pcat[k] >= pmax_i) {
                pmax_i  = data_i.pcat[k];
                correct_i = false;
            }
        }
        if (correct_i) {
            System.out.println(","+ "correct");
        } else {
            nMisclassified += data_i.weight;
            System.out.println(","+ "error");
        }
        nTotal += data_i.weight;
    }
	System.out.println("nMisclassified = "+ nMisclassified +" ("+ ((nMisclassified / nTotal) * 100.0) +"%) at threshold="+ threshold);
}


/* * * * * * * * * * *
 * OBJECTIVE VALUE
 * [-logP(training data | w)] + [\sum_i l1*I(wi!=0)] + [\sum_i l2*wi^2]
 */
public double objectiveValue() {
    double negLogProbData = -dataLogProb();
    double reg = 0.0;
    for (int i=(nConstraints-1); i>=0; i--) {
        if (lambda[i]!=0.0) reg += l1;
        reg += l2*lambda[i]*lambda[i];
    }
    double value = negLogProbData + reg;
    return value;
}

    
/* * * * * * * * * * *
 * PREDICTED CATEGORY PROBABILITIES
 */
public void predProbs(DataPoint[] forms) {
    for (DataPoint form_i : forms) {
        form_i.pCategory(constraints,lambda);
    }
}


/* * * * * * * * * * *
 * TESTING
 */
public void test(String testType) {
	String fname = testType+"_out.csv";
    System.out.println("\nWriting predictions for "+ testType +"ing forms to "+ fname);
    DataPoint[] testForms = null;
    if (testType=="train") testForms = train;
    if (testType=="test") testForms = test;
    predProbs(testForms);
    LinkedList<String> testStrings = new LinkedList<String>();
    String testStringi = "form,category,weight";
    for (int k=0; k<nCategories; k++)
        testStringi += ","+ categoryNames[k];
    testStrings.add(testStringi);
    for (DataPoint test_i : testForms) {
        testStringi = test_i.toString();
        for (double pred_ik : test_i.pcat)
            testStringi += ","+ pred_ik;
        testStrings.add(testStringi);
    }
    SimpleFileWriter writer = new SimpleFileWriter(fname, true);
    writer.write(testStrings);
}


/* * * * * * * * * * *
 * METHODS REQUIRED TO IMPLEMENT MFWithGradient
 */
public int getNumArguments()                { return nConstraints; }
public double getLowerBound(int arg)        { return 0.0; }
public double getUpperBound(int arg)        { return 20.0; }
public OrthogonalHints getOrthogonalHints() { return null; }
public double evaluate(double[] w)          { return 0.0; } // return objectiveValue();

// computes gradient only (because function value is not needed for optimization, as in macopt)
public double evaluate(double[] w, double[] grad) {
	computeGradient(w,grad);
	return 0.0; // return objectiveValue();
}

public void computeGradient(double[] w, double[] grad) {
	System.arraycopy(w,0,lambda,0,nConstraints);	//System.out.println(Arrays.toString(lambda));
	Arrays.fill(grad,0.0);
    DataPoint data_i    = null;
	ClassifierConstraint C   = null;
    for (int i=0; i<nDataPoints; i++) {
        data_i = train[i];
		data_i.pCategory(constraints,lambda);
		for (Map.Entry<Integer,Integer> e : data_i.violns.entrySet()) {
			C               = constraints.get(e.getKey());	// xxx needed only for constraint category
			int fxi         = e.getValue();                 // number of violations of constraint C by form x_i
			int k           = C.category;
			grad[C.id]      += data_i.weight * (((data_i.category==k) ? fxi : 0) - data_i.pcat[k]*fxi);
								// weight[i] * (I[i==k]*f(x_i) - p(k|x_i)*f(x_i));
		}
	}
	// negate derivative (for minimization) and add contributions of l1 and l2 regularizers
	for (int i=(nConstraints-1); i>=0; i--) {
		grad[i] += l1 + 2.0*l2*lambda[i];
	}
}


/* * * * * * * * * * *
 * MAIN
 */
public static void main(String[] args) throws Exception {
	MaxentClassifier maxent = new MaxentClassifier(args);
}

}

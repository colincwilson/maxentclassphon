package edu.jhu.maxent_classifier;

import java.io.*;
import java.util.*;
import edu.jhu.util.*;
import edu.jhu.features.*;
import edu.jhu.fsm.*;

public class DataFileReader
{

// fields set by read() below
static DataPoint[] data = null;

public DataFileReader() { }


// read list of data points of the form
//      wordform,attrib,category[,weight[,semicolon-delimited list of possible categories]]
// where wordform is a sequence of space-separated segments and category is either an integer or the name of a category
public static void read(String dataFile, edu.jhu.features.Alphabet alphabet, String WORD_BEGIN, String WORD_END) {
    String[] wordArray = null;
    try {
        wordArray = StringFileReader.read(new File(dataFile));
    } catch (Exception e) { System.out.println(e); }
    int N = wordArray.length;

    ArrayList<DataPoint> dataList = new ArrayList<DataPoint>();
    data            = null;
    for (int i=0; i<N; i++) {
        String[] dataPoint = wordArray[i].split(",");
        int indx = 0;

        String formAttrib = dataPoint[indx];
        
        // parse form
        String form = formAttrib.replaceAll("<.*", "");
        if (form.equals("")) {
            System.out.println("Empty form (row "+ i +")");
            System.exit(1);
        }
        int[] x = alphabet.encodeString(form, true);
        for (int j=0; j<x.length; j++) {
            if (x[j]<0) {
                String[] x_ = form.split(" ");
                System.out.println("Bad segment in data point "+ form + " = "+ Arrays.toString(x_));
                System.out.println(" @ segment "+ x_[j]);
                System.exit(1);
            }
        }
        
        // parse attribute
        String attrib = null;
        if (formAttrib.contains("<")) {
            attrib = formAttrib.replaceAll(".*<", "");
            attrib = attrib.replace(">", "");
        }
        indx++;

        // parse correct category (if present)
        String categoryName = dataPoint[indx];
        int category = -1;
        try {
            category = Integer.parseInt(categoryName);
        } catch (NumberFormatException e) {
            for (int j=0; j<MaxentClassifier.categoryNames.length; j++) {
                if (MaxentClassifier.categoryNames[j].equals(categoryName)) {
                    category = j;
                }
            }
            indx++;
        }
        if (category==-1) {
            System.out.println("Bad category in data point "+ dataPoint[0]);
            System.exit(1);
        }
        
        
        // parse weight (if present)
        double weight = 1.0;
        if (dataPoint.length>indx) {
            weight = Double.parseDouble(dataPoint[indx]);
            indx++;
        }
        if (weight==0.0) continue;  // ignore data points with 0 weights

        // parse semicolon-delimited list of possible categories for this data point
        boolean[] cats = null;
        if (dataPoint.length>indx) {
            cats = new boolean[MaxentClassifier.categoryNames.length];
            String[] catNames = dataPoint[indx].split(";");
            for (String catName : catNames) {
                for (int j=0; j<MaxentClassifier.categoryNames.length; j++) {
                    if (MaxentClassifier.categoryNames[j].equals(catName))
                        cats[j] = true;
					else
						System.out.println("unknown category: "+ catName);
				}
			}
        }

        DataPoint data_i = new DataPoint(form, x, attrib, category, weight, cats);
        dataList.add(data_i);
        //System.out.println(data_i);
    }

    data = dataList.toArray(new DataPoint[0]);
}

}

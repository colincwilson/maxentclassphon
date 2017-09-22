for i in 0 1 2 3 4 5 6 7 8 9
do
	sed "s/XXX/$i/g" < params.txt > params$i.txt
	java edu.jhu.maxent_classifier.MaxentClassifier params$i.txt
	mv grammar_out.txt crossval/grammar_out$i.txt
	mv train_out.csv crossval/train_out$i.csv
	mv test_out.csv crossval/test_out$i.csv
	rm params$i.txt
done
The data in this example consists of ~1000 verb stems from the dictionary of Aoki (1994), categorized as s-class (VS) vs. c-class (VC) [see Aoki 1994:xiv-xv] and randomly divided into 10 folds.

To learn grammars that classify verb stems on the basis of phonological form, and test the grammars on held-out stems, run the following command in the shell/terminal:

sh 00crossval.sh

The performance of the learned grammars can be evaluated with the R script 00crossval.R (the script requires the tidyverse package and minor editing for your machine).

As recently as Baerman et al. (2017:45) the distinction between s-class and v-class verb stems in Nez Perce has been presented as an instance of arbitrary (or 'unmotivated') classification. This view is supported by the fact that Aoki reports some homophonous verbs that belong to different classes:

	cʼóːq VS 'to pound (meat), hammer (something to eat, not a nail); to forge, hammer heated iron'
	cʼóːq VC 'to suck (e.g., marrow out of a bone, or blood out of a victim)'

	líːk VC 'to move, proceed walk. to shoot, hunt (with tukw)'
	líːk VS 'to do, act'

	típ VS 'to lick' / 'to eat a meal'
	típ VC 'to throb (of heart), pulsate'

The cross-validation results suggest, however, that this classification is somewhat predictable from the form of the verb stem. The verbs in the data are roughly evenly split between VS (513 stems) and VC (480 stems), and classification on the basis of relative frequency would have an accuracy of .50. But the learned grammars achieve a much higher accuracy on held-out forms (~.72 overall, ~.69 for VS stems and ~.76 for VC stems). Therefore, the classification of verb stems in Nez Perce can be analyzed as partly 'motivated' by phonological form.

==========

Aoki, H. (1994). Nez Perce Dictionary. University of California Publications in Linguistics (vol. 122). Los Angeles: University of California Press.

Baerman, M., Brown, D., & Corbett, G.C. Morphological Comlpexity. Cambridge Studies in Linguistics (vol. 153). Cambridge: Cambridge University Press.
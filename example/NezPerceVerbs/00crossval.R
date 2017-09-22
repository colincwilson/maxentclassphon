require(tidyr)

setwd('<your path here>/maxentclassphon/example/NezPerceVerbs/')

# process train / test results
train.results <- NULL
test.results <- NULL
for (i in 0:9) {
    train.i <- read_csv(paste('crossval/train_out', i, '.csv', sep=''))
    test.i <- read_csv(paste('crossval/test_out', i, '.csv', sep=''))
    train.i %>% mutate(fold=i) -> train.i
    test.i %>% mutate(fold=i) -> test.i
    train.results <- rbind(train.results, train.i)
    test.results <- rbind(test.results, test.i)
}

train.results %>% mutate(pred_category=ifelse(VC>VS, 'VC', 'VS')) -> train.results
test.results %>% mutate(pred_category=ifelse(VC>VS, 'VC', 'VS')) -> test.results

train.results %>% group_by(fold) %>% summarise(mu1=mean(pred_category==category)) %>% summarise(mu=mean(mu1))

test.results %>% group_by(fold) %>% summarise(mu1=mean(pred_category==category)) %>% summarise(mu=mean(mu1))

train.results %>% group_by(category, fold) %>%
    summarise(mu1=mean(pred_category==category)) %>%
    summarise(mu=mean(mu1), sd=sd(mu1))

test.results %>% group_by(category, fold) %>%
    summarise(mu1=mean(pred_category==category)) %>%
    summarise(mu=mean(mu1), sd=sd(mu1))
